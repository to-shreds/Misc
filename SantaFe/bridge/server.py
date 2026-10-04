"""Loopback-only JSON server. No authentication bypass, CORS, proxy or telemetry."""
from __future__ import annotations
import argparse
import collections
import http.cookies
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import os
from pathlib import Path
import signal
import threading
import time
from urllib.parse import urlsplit
from .engine import Engine
from .model import Problem, VERSION, EVENTS, FIELDS, ACTIONS

ROOT=Path(__file__).resolve().parent.parent
MAX_BODY=1048576

class Server(ThreadingHTTPServer):
    daemon_threads=True
    request_queue_size=12
    def __init__(self,addr,engine):
        if addr[0]!='127.0.0.1':raise ValueError('This service only binds to IPv4 loopback.')
        self.engine=engine;self.attempts=collections.deque();self.rate_lock=threading.Lock()
        super().__init__(addr,Handler)
        self.canonical='http://127.0.0.1:'+str(self.server_address[1])
    def handle_error(self,request,client_address):
        # Standard traceback handling can expose user-provided request data.
        with self.engine.lock:self.engine.log('warning','A local connection ended unexpectedly.')

class Handler(BaseHTTPRequestHandler):
    server_version='SantaFeBridge/'+VERSION
    sys_version=''
    protocol_version='HTTP/1.0'
    def setup(self):super().setup();self.connection.settimeout(10)
    def log_message(self,*args):pass
    def _send(self,status,data,ctype='application/json',cookie=None):
        if ctype=='application/json':raw=json.dumps(data,allow_nan=False,ensure_ascii=False).encode()
        else:raw=data if isinstance(data,bytes) else data.encode()
        self.send_response(status)
        for key,value in {'Content-Type':ctype,'Content-Length':str(len(raw)),
            'Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer',
            'X-Frame-Options':'DENY','Cross-Origin-Resource-Policy':'same-origin',
            'Content-Security-Policy':"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'"}.items():self.send_header(key,value)
        if cookie:self.send_header('Set-Cookie',cookie)
        self.end_headers()
        try:self.wfile.write(raw)
        except (BrokenPipeError,ConnectionResetError):pass
    def _gate(self):
        if self.headers.get('Host')!=self.server.canonical.removeprefix('http://'):raise Problem('Invalid local host.',403)
        origin=self.headers.get('Origin')
        if origin is not None and origin!=self.server.canonical:raise Problem('Cross-origin requests are not accepted.',403)
        if self.headers.get('Sec-Fetch-Site')=='cross-site':raise Problem('Cross-site request rejected.',403)
    def _role(self):
        auth=self.headers.get('Authorization','')
        token=auth[7:] if auth.startswith('Bearer ') else ''
        if not token:
            try:
                cookies=http.cookies.SimpleCookie(self.headers.get('Cookie',''))
                token=cookies['sf_session'].value if 'sf_session' in cookies else ''
            except http.cookies.CookieError:token=''
        role=self.server.engine.authenticate(token)
        if not role:raise Problem('Pair this device with the bridge first.',401)
        return role
    def _body(self):
        if self.headers.get('Transfer-Encoding'):raise Problem('Chunked requests are not accepted.',400)
        if self.headers.get('Content-Type','').split(';')[0]!='application/json':raise Problem('JSON content type is required.',415)
        if self.headers.get('X-SF-Client') not in ['ui','tasker']:raise Problem('Missing local client header.',403)
        try:n=int(self.headers.get('Content-Length','0'))
        except ValueError:raise Problem('Invalid body length.')
        if not 0<n<=MAX_BODY:raise Problem('Request is empty or too large.',413)
        raw=self.rfile.read(n)
        if len(raw)!=n:raise Problem('Incomplete request.')
        def reject_constant(value):raise ValueError('Nonfinite JSON')
        try:body=json.loads(raw,parse_constant=reject_constant)
        except (ValueError,UnicodeError):raise Problem('Invalid JSON.')
        if not isinstance(body,dict):raise Problem('JSON object required.')
        return body
    def _run(self,fn):
        try:self._gate();fn()
        except Problem as e:self._send(e.status,{'error':e.message})
        except Exception:
            self._send(500,{'error':'Internal error. No vehicle command was retried. Raw details omitted.'})
            with self.server.engine.lock:self.server.engine.log('error','Local API request failed; raw input omitted.')
    def do_OPTIONS(self):self._send(405,{'error':'CORS is not supported.'})
    def do_GET(self):self._run(self._get)
    def do_POST(self):self._run(self._post)
    def _get(self):
        path=urlsplit(self.path).path;e=self.server.engine
        if path=='/health':return self._send(200,{'app':'Santa Fe Control Center','version':VERSION})
        static={'/':'index.html','/app.js':'app.js','/app.css':'app.css','/favicon.ico':None}
        if path in static:
            if static[path] is None:return self._send(204,b'','image/x-icon')
            f=ROOT/'ui'/static[path];types={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8'}
            return self._send(200,f.read_bytes(),types[f.suffix])
        role=self._role()
        if path=='/api/snapshot':return self._send(200,e.status())
        if path=='/api/catalog':return self._send(200,{'events':EVENTS,'fields':FIELDS,'actions':ACTIONS,'role':role})
        if path=='/api/effects':return self._send(200,{'effects':e.get_effects()})
        if path=='/api/backup':
            if role!='owner':raise Problem('Owner access required.',403)
            with e.lock:return self._send(200,e.cfg)
        raise Problem('Not found.',404)
    def _post(self):
        path=urlsplit(self.path).path;b=self._body();e=self.server.engine
        if path=='/api/pair':
            with self.server.rate_lock:
                now=time.monotonic()
                while self.server.attempts and now-self.server.attempts[0]>60:self.server.attempts.popleft()
                if len(self.server.attempts)>=5:raise Problem('Too many pairing attempts. Wait one minute.',429)
                self.server.attempts.append(now)
            result=e.pair(b.get('code'))
            cookie='sf_session='+result['token']+'; HttpOnly; SameSite=Strict; Path=/'
            # Session cookie is intentionally not marked Secure on plain loopback HTTP.
            return self._send(200,result,cookie=cookie)
        role=self._role()
        common=['/api/context','/api/event','/api/command','/api/effects/ack','/api/pause']
        if role!='owner' and path not in common:raise Problem('Owner access required.',403)
        if path=='/api/context':result=e.update_context(b)
        elif path=='/api/event':result=e.event(b)
        elif path=='/api/command':result=e.request_command(b)
        elif path=='/api/effects/ack':result=e.ack(b.get('ids'))
        elif path=='/api/pause':result=e.pause()
        elif path=='/api/pairing-code':result={'code':e.code('tasker'),'expires_in':300}
        elif path=='/api/config':result=e.save_config(b.get('config'),b.get('revision'))
        elif path=='/api/restore':result=e.save_config(b.get('config'),b.get('revision'),True)
        elif path=='/api/mode':result=e.set_mode(b.get('mode'))
        elif path=='/api/arm':result=e.arm(b.get('phrase'),b.get('auto_lock',False))
        elif path=='/api/login':result=e.login(b)
        elif path=='/api/select-vehicle':result=e.select_vehicle(b.get('vehicle_id'))
        elif path=='/api/disconnect':result=e.disconnect()
        elif path=='/api/confirm':result=e.confirm(b.get('id'),b.get('outdoors',False))
        elif path=='/api/confirmation/cancel':result=e.cancel_confirmation(b.get('id'))
        elif path=='/api/rule/test':result=e.evaluate(b.get('id'),b.get('event',{}))
        elif path=='/api/simulate':result=e.simulate(b.get('state'))
        elif path=='/api/revoke-clients':
            e.revoke();return self._send(200,{'revoked':True},cookie='sf_session=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0')
        elif path=='/api/logout':return self._send(200,{'logged_out':True},cookie='sf_session=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0')
        else:raise Problem('Not found.',404)
        self._send(200,result)

def main():
    parser=argparse.ArgumentParser(description='Santa Fe Control Center loopback bridge')
    parser.add_argument('--data-dir',default=str(Path.home()/'.local/share/santa-fe-control-center'))
    parser.add_argument('--port',type=int,default=8293)
    args=parser.parse_args()
    if not 1024<=args.port<=65535:parser.error('Use an unprivileged port from 1024 to 65535.')
    # An advisory lock prevents two processes mutating the same private store.
    import fcntl
    root=Path(args.data_dir);root.mkdir(parents=True,exist_ok=True,mode=0o700)
    lockfile=open(root/'bridge.lock','a')
    try:fcntl.flock(lockfile,fcntl.LOCK_EX|fcntl.LOCK_NB)
    except BlockingIOError:raise SystemExit('The bridge is already using this private data folder.')
    engine=Engine(root)
    try:server=Server(('127.0.0.1',args.port),engine)
    except OSError:
        engine.close();raise SystemExit('Local port is occupied. The existing service was left unchanged.')
    code=engine.code()
    print('Santa Fe Control Center '+VERSION+' | SIMULATION | no Hyundai connection',flush=True)
    print('Open '+server.canonical+' in Tasker or your browser.',flush=True)
    print('Owner pairing code (valid five minutes): '+code,flush=True)
    print('Leave this Termux session running. Ctrl+C stops it. Restart creates a new owner pairing code.',flush=True)
    threading.Thread(target=engine.loop,daemon=True).start()
    try:server.serve_forever(poll_interval=.5)
    except KeyboardInterrupt:pass
    finally:server.server_close();engine.close();lockfile.close()

if __name__=='__main__':main()
