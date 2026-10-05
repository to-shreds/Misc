import json, threading, concurrent.futures, urllib.request, urllib.error
import pytest
from bridge.engine import Engine
from bridge.server import Server, MAX_BODY

@pytest.fixture
def srv(tmp_path):
    e=Engine(tmp_path);s=Server(('127.0.0.1',0),e);t=threading.Thread(target=s.serve_forever,daemon=True);t.start()
    yield e,s
    s.shutdown();s.server_close();e.close();t.join(2)

def req(s,path='/api/snapshot',body=None,token=None,headers=None):
    h={'X-SF-Client':'tasker','Content-Type':'application/json',**(headers or {})}
    if token:h['Authorization']='Bearer '+token
    raw=None if body is None else (body if isinstance(body,bytes) else json.dumps(body).encode())
    r=urllib.request.Request(s.canonical+path,data=raw,headers=h)
    try:response=urllib.request.urlopen(r,timeout=4)
    except urllib.error.HTTPError as ex:response=ex
    data=response.read();return response.status,data,response.headers

def test_unauthenticated_status_blocked(srv):
    e,s=srv;assert req(s)[0]==401;assert req(s,'/health')[0]==200

def test_pair_cookie_auth_and_tasker_role(srv):
    e,s=srv;status,raw,h=req(s,'/api/pair',{'code':e.code('tasker')});r=json.loads(raw)
    assert status==200 and 'HttpOnly' in h['Set-Cookie'] and 'SameSite=Strict' in h['Set-Cookie']
    assert req(s,token=r['token'])[0]==200
    assert req(s,'/api/config',{'config':e.cfg,'revision':e.revision},r['token'])[0]==403
    assert req(s,'/api/arm',{'phrase':'ENABLE LIVE COMMANDS'},r['token'])[0]==403
    assert req(s,'/api/confirm',{'id':'bad'},r['token'])[0]==403
    assert req(s,headers={'Cookie':h['Set-Cookie'].split(';')[0]})[0]==200

@pytest.mark.parametrize('headers',[{'Host':'evil.example'},{'Origin':'https://evil.example'},{'Sec-Fetch-Site':'cross-site'}])
def test_cross_origin_and_dns_rebinding_rejected(srv,headers):
    e,s=srv;token=e.pair(e.code())['token'];assert req(s,token=token,headers=headers)[0]==403

def test_input_guards(srv):
    e,s=srv;token=e.pair(e.code())['token']
    for raw in [b'{bad',b'[]',b'{"x":NaN}',b'{"x":Infinity}']:
        assert req(s,'/api/event',raw,token)[0]==400
    # The server rejects an oversized declared length before reading the body.
    # Sending a megabyte races that early close and can hide the 413 behind a
    # client BrokenPipeError. A small body still exercises the same wire guard.
    assert req(s,'/api/event',b'{}',token,{'Content-Length':str(MAX_BODY+1)})[0]==413
    assert req(s,'/api/event',{},token,{'Content-Type':'text/plain'})[0]==415
    assert req(s,'/api/event',{},token,{'X-SF-Client':'bad'})[0]==403

def test_pairing_rate_limit(srv):
    e,s=srv
    for _ in range(5):assert req(s,'/api/pair',{'code':'wrong'})[0]==401
    assert req(s,'/api/pair',{'code':e.code()})[0]==429

def test_cache_and_security_headers(srv):
    e,s=srv;token=e.pair(e.code())['token'];status,raw,h=req(s,token=token)
    assert status==200 and h['Cache-Control']=='no-store'
    assert "script-src 'self'" in h['Content-Security-Policy']
    assert not h.get('Access-Control-Allow-Origin')
    assert token.encode() not in raw
    assert req(s,'/api/backup',token=token)[0]==200
    assert req(s,'/../../bridge/engine.py')[0] in [401,404]

def test_parallel_local_reads_do_not_queue_vehicle_operations(srv):
    e,s=srv;token=e.pair(e.code())['token']
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        results=list(pool.map(lambda _:req(s,token=token)[0],range(100)))
    assert results==[200]*100 and not e.jobs

def test_unknown_route_and_method(srv):
    e,s=srv;token=e.pair(e.code())['token']
    assert req(s,'/api/unknown',token=token)[0]==404
    assert req(s,'/api/unknown',{},token)[0]==404

def test_not_exposed_to_lan(tmp_path):
    e=Engine(tmp_path)
    try:
        with pytest.raises(ValueError):Server(('0.0.0.0',0),e)
    finally:e.close()
