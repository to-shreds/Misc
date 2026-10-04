"""Single command queue, independent UI reads, ephemeral live session and safe rules."""
from __future__ import annotations
import collections
import copy
import datetime as dt
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import secrets
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from zoneinfo import ZoneInfo
from . import model as m
from .adapters import Simulator, HyundaiUSA


def atomic_json(path:Path,data):
    path.parent.mkdir(mode=0o700,parents=True,exist_ok=True)
    tmp=path.with_name(path.name+'.tmp-'+secrets.token_hex(6))
    fd=os.open(tmp,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    try:
        with os.fdopen(fd,'w',encoding='utf-8') as f:
            json.dump(data,f,ensure_ascii=False,allow_nan=False);f.flush();os.fsync(f.fileno())
        os.replace(tmp,path);os.chmod(path,0o600)
    finally:
        if tmp.exists():tmp.unlink()

def read_json(path,default):
    if not path.exists():return copy.deepcopy(default)
    try:return json.loads(path.read_text())
    except (OSError,ValueError):raise m.Problem('A private data file is unreadable. It was not overwritten. Restore your backup.',503)

class Engine:
    def __init__(self,root,clock=time.time):
        self.root=Path(root);self.root.mkdir(parents=True,exist_ok=True,mode=0o700)
        os.chmod(self.root,0o700)
        self.clock=clock;self.lock=threading.RLock()
        self.cfg=m.config(read_json(self.root/'config.json',m.defaults()))
        self.revision=1
        # Always start in simulation and disarmed. No stale live commands after restart.
        self.mode='simulation';self.epoch=uuid.uuid4().hex;self.live_armed=False;self.auto_lock=False
        self.sim=Simulator(clock=self.clock);self.live=None;self.live_login_status='not connected'
        self.account_vehicles=[];self.adapter=self.sim;self.phone={}
        self.jobs=collections.OrderedDict();self.logs=collections.deque(maxlen=300)
        self.effects=collections.OrderedDict();self.notices=collections.deque(maxlen=100)
        self.pending={};self.confirmations={};self.receipts={};self.codes={}
        self.last_rule=read_json(self.root/'cooldowns.json',{})
        if not isinstance(self.last_rule,dict):raise m.Problem('Invalid cooldown storage.',503)
        self.events=collections.OrderedDict();self.schedule_seen={};self.last_cloud=0;self.last_force=0;self.last_command=0
        self.tokens=read_json(self.root/'clients.json',{})
        if not isinstance(self.tokens,dict):raise m.Problem('Invalid paired-client storage.',503)
        self.executor=ThreadPoolExecutor(max_workers=1,thread_name_prefix='sf-command')
        self.stop=threading.Event();self.started=self.clock();self.busy=False
        self.log('info','Bridge started in simulation. Live commands are disarmed.')
    def close(self):self.stop.set();self.executor.shutdown(wait=True,cancel_futures=True)
    def log(self,level,message):
        self.logs.appendleft({'id':uuid.uuid4().hex,'at':self.clock(),'level':level,'message':str(message)[:500]})
    def code(self,role='owner'):
        with self.lock:
            if role not in ['owner','tasker']:raise m.Problem('Invalid pairing role.')
            self.codes={k:v for k,v in self.codes.items() if v['expires']>=self.clock()}
            if len(self.codes)>=10:raise m.Problem('Too many outstanding pairing codes.',429)
            code=''.join(secrets.choice('23456789ABCDEFGHJKLMNPQRSTUVWXYZ') for _ in range(8))
            self.codes[code]={'role':role,'expires':self.clock()+300,'attempts':0}
            return code
    def pair(self,code):
        with self.lock:
            if not isinstance(code,str):raise m.Problem('Invalid pairing code.',401)
            record=self.codes.pop(code.upper().strip(),None)
            if not record or record['expires']<self.clock():raise m.Problem('Pairing code is invalid or expired.',401)
            token=secrets.token_urlsafe(32);digest=hashlib.sha256(token.encode()).hexdigest()
            self.tokens[digest]={'role':record['role'],'created':self.clock()}
            if len(self.tokens)>20:
                oldest=min(self.tokens,key=lambda k:self.tokens[k]['created']);del self.tokens[oldest]
            atomic_json(self.root/'clients.json',self.tokens)
            return {'token':token,'role':record['role']}
    def authenticate(self,token):
        if not isinstance(token,str) or len(token)>200:return None
        digest=hashlib.sha256(token.encode()).hexdigest()
        with self.lock:return self.tokens.get(digest,{}).get('role')
    def revoke(self):
        with self.lock:
            self.tokens={};self.live_armed=False;self.auto_lock=False;self.confirmations={};self.pending={}
            atomic_json(self.root/'clients.json',self.tokens)
            self.log('warning','All paired clients revoked. Commands disarmed.')
    def status(self):
        with self.lock:
            state=self.adapter.snapshot();now=self.clock();age=m.age(state,now)
            observed={k:('reported' if v is not None else 'unreported') for k,v in state.items() if k in m.FIELDS}
            return {'app':'Santa Fe Control Center','version':m.VERSION,'mode':self.mode,'epoch':self.epoch,
                    'live_armed':self.live_armed,'auto_lock':self.auto_lock,'busy':self.busy,
                    'state':state,'age_seconds':age,'stale':age is None or age>self.cfg['settings']['stale_seconds'],
                    'phone':copy.deepcopy(self.phone),'observed':observed,'config':copy.deepcopy(self.cfg),'revision':self.revision,
                    'jobs':list(copy.deepcopy(self.jobs).values())[-30:][::-1],
                    'logs':list(self.logs),'notices':list(self.notices),
                    'confirmations':[{'id':k,**v} for k,v in self.confirmations.items() if v['expires']>now],
                    'pending_rules':[{'id':k,'due':v['due'],'name':v['rule']['name']} for k,v in self.pending.items()],
                    'account':{'status':self.live_login_status,'vehicles':copy.deepcopy(self.account_vehicles)},
                    'capabilities':{'command_availability':'simulated' if self.mode=='simulation' else 'API candidate, not VIN-verified',
                     'commands':m.COMMANDS,'not_implemented':['Digital Key','window control','remote camera capture','steering-wheel heat control','EV charging'],
                     'note':'Hardware equipment does not establish remote command support.'}}
    def save_config(self,data,revision,restoring=False):
        clean=m.config(data,restoring)
        with self.lock:
            if type(revision) is not int or revision!=self.revision:raise m.Problem('Settings changed elsewhere. Reload before saving.',409)
            atomic_json(self.root/'config.previous.json',self.cfg)
            atomic_json(self.root/'config.json',clean)
            self.cfg=clean;self.revision+=1;self.pending={};self.confirmations={}
            if restoring:self.live_armed=False;self.auto_lock=False
            self.log('info','Backup restored with rules disabled.' if restoring else 'Settings saved. Pending rules and confirmations cleared.')
            return {'revision':self.revision}
    def set_mode(self,mode):
        if mode not in ['simulation','live']:raise m.Problem('Invalid mode.')
        with self.lock:
            if self.busy:raise m.Problem('Wait for the current operation before switching modes.',409)
            if mode=='live' and (not self.live or not self.live.selected):raise m.Problem('Connect and select your real vehicle first.',409)
            self.mode=mode;self.adapter=self.sim if mode=='simulation' else self.live
            self.epoch=uuid.uuid4().hex;self.live_armed=False;self.auto_lock=False;self.pending={};self.confirmations={}
            self.receipts={};self.effects.clear();self.phone={};self.schedule_seen={}
            self.last_cloud=self.last_force=self.last_command=0
            self.log('warning',f'Switched to {mode}. Live commands are disarmed; delayed actions cleared.')
            return {'mode':mode}
    def arm(self,phrase,auto_lock=False):
        with self.lock:
            if self.mode!='live':raise m.Problem('Arming is unnecessary in simulation.',409)
            if phrase!='ENABLE LIVE COMMANDS':raise m.Problem('Type ENABLE LIVE COMMANDS to arm this session.')
            m.flag(auto_lock,'Automatic lock')
            self.live_armed=True;self.auto_lock=auto_lock
            self.log('warning','Live controls armed for this running session. Automatic ignition/unlock remains confirmation-only.')
            return {'armed':True}
    def pause(self):
        with self.lock:
            self.live_armed=False;self.auto_lock=False;self.pending={};self.confirmations={}
            self.cfg['settings']['rules_enabled']=False;self.revision+=1
            atomic_json(self.root/'config.json',self.cfg)
            self.log('warning','All rules paused and live commands disarmed. Already-sent commands cannot be recalled.')
            return {'paused':True}
    def _account_job(self,fn,label):
        with self.lock:
            if self.busy:raise m.Problem('Another operation is already running.',409)
            self.busy=True;self.live_login_status=label
            self.live_armed=False;self.auto_lock=False
            job={'id':uuid.uuid4().hex,'action':'account','state':'queued','at':self.clock(),'message':label}
            self.jobs[job['id']]=job
        def run():
            try:
                fn()
                with self.lock:job.update(state='succeeded',message='Account step completed. Live commands remain disarmed.')
            except Exception as e:
                with self.lock:
                    msg=e.message if isinstance(e,m.Problem) else 'Hyundai account step failed ('+type(e).__name__+'). Credentials and raw responses were not logged.'
                    job.update(state='failed',message=msg);self.live_login_status=msg
            finally:
                with self.lock:self.busy=False
        self.executor.submit(run);return copy.deepcopy(job)
    def login(self,body):
        for k in ['username','password','pin']:m.text(body.get(k),k,256)
        with self.lock:
            if self.mode!='simulation':raise m.Problem('Switch to simulation before connecting an account.',409)
        def connect():
            adapter=HyundaiUSA(body['username'],body['password'],body['pin'])
            with self.lock:
                self.live=adapter;self.account_vehicles=adapter.vehicles();self.live_login_status='connected; select a vehicle'
        return self._account_job(connect,'connecting')
    def select_vehicle(self,vid):
        with self.lock:
            if self.mode!='simulation' or not self.live:raise m.Problem('Connect your account in simulation first.',409)
        def select():
            self.live.select(vid)
            with self.lock:self.live_login_status='vehicle selected; live mode available'
        return self._account_job(select,'reading selected vehicle')
    def disconnect(self):
        with self.lock:
            if self.busy:raise m.Problem('An operation is running.',409)
            self.set_mode('simulation');self.live=None;self.account_vehicles=[];self.live_login_status='not connected'
            return {'disconnected':True}
    def _safety(self,action,automatic):
        if self.mode!='live':return
        if action in ['refresh','force_refresh','locate']:return
        if not self.live_armed:raise m.Problem('Live remote commands are disarmed.',403)
        s=self.adapter.snapshot();now=self.clock()
        if action=='climate_start' or (automatic and action=='lock'):
            a=m.age(s,now)
            if a is None or a>self.cfg['settings']['stale_seconds']:raise m.Problem('Fresh vehicle data is required for this action.',409)
            if s.get('engine_running') is not False:raise m.Problem('The vehicle must explicitly report its engine off.',409)
            if any(s.get(k) is not False for k in m.CLOSURES):raise m.Problem('All doors, hood and trunk must explicitly report closed.',409)
        if action=='climate_start' and s.get('locked') is not True:raise m.Problem('Lock the vehicle and refresh status before remote start.',409)
        if automatic:
            if action!='lock' or not self.auto_lock:raise m.Problem('This live action requires confirmation.',403)
            entry=self.phone.get('car_connected',{})
            if entry.get('value') is not False or not 0<=now-entry.get('at',0)<=self.cfg['settings']['phone_context_ttl']:
                raise m.Problem('Automatic lock needs a recent explicit car-disconnected report.',409)
    def request_command(self,body,automatic=False,confirmed=False,require_confirmation=False):
        action=body.get('action')
        if action not in m.COMMANDS:raise m.Problem('Unknown vehicle command.')
        with self.lock:
            if body.get('expected_mode')!=self.mode or body.get('epoch')!=self.epoch:
                raise m.Problem('Mode/session changed. Reload before sending this action.',409)
            key=m.ident(body.get('request_id',''))
            now=self.clock();self.receipts={k:v for k,v in self.receipts.items() if v['until']>=now}
            signature=json.dumps({'action':action,'preset_id':body.get('preset_id'), 'epoch':self.epoch},sort_keys=True)
            if key in self.receipts:
                r=self.receipts[key]
                if r['signature']!=signature:raise m.Problem('Request ID was reused for different content.',409)
                return copy.deepcopy(r['response'])
            if len(self.receipts)>=500:raise m.Problem('Too many requests. Wait before trying again.',429)
            p=None
            if action=='climate_start':
                p=next((copy.deepcopy(p) for p in self.cfg['presets'] if p['id']==body.get('preset_id')),None)
                if not p:raise m.Problem('Select an existing climate preset.')
            self._safety(action,False if not automatic else automatic)
            if not confirmed and (require_confirmation or action in ['unlock','climate_start','horn_lights']):
                cid=uuid.uuid4().hex
                lifetime=300 if require_confirmation else 60
                c={'action':action,'preset_id':body.get('preset_id'),'preset':p,'expires':now+lifetime,
                   'mode':self.mode,'epoch':self.epoch,'request_id':key,'automatic':automatic}
                self.confirmations[cid]=c
                answer={'state':'confirmation_required','id':cid,'message':'Confirm this specific action in the dashboard within '+str(lifetime)+' seconds.'}
                self.receipts[key]={'signature':signature,'response':answer,'until':now+300}
                self.notice('Confirmation required: '+action.replace('_',' ')+'. Open the dashboard.',important=True)
                return answer
            if self.busy:raise m.Problem('A vehicle operation is already running. No additional command was queued.',409)
            if self.mode=='live':
                setting=self.cfg['settings']
                if action in ['refresh','locate','force_refresh']:
                    if now-self.last_cloud<setting['cloud_read_interval']:raise m.Problem('Cloud read cooldown is active. The dashboard itself uses the local cache.',429)
                    if action=='force_refresh' and now-self.last_force<setting['force_refresh_interval']:raise m.Problem('Vehicle wake-up cooldown is active.',429)
                    self.last_cloud=now
                    if action=='force_refresh':self.last_force=now
                else:
                    if now-self.last_command<setting['command_cooldown']:raise m.Problem('Command cooldown is active.',429)
                    self.last_command=now
            self.busy=True
            job={'id':uuid.uuid4().hex,'action':action,'state':'queued','at':now,'mode':self.mode,'message':'Operation queued locally.'}
            self.jobs[job['id']]=job
            while len(self.jobs)>100:self.jobs.popitem(last=False)
            answer=copy.deepcopy(job)
            self.receipts[key]={'signature':signature,'response':answer,'until':now+300}
            adapter=self.adapter;epoch=self.epoch
            def execute():
                try:
                    with self.lock:
                        if epoch!=self.epoch:raise m.Problem('Session changed; operation cancelled.',409)
                        self._safety(action,automatic)
                        job.update(state='sent',message='Waiting for vehicle confirmation.' if self.mode=='live' else 'Waiting for simulated vehicle.')
                    result=adapter.perform(action,p)
                    with self.lock:
                        job.update(state=result['result'],message=result['message'],completed_at=self.clock())
                        self.log('info' if result['result']=='succeeded' else 'warning',action+': '+result['message'])
                        self.notice(action.replace('_',' ')+': '+result['message'])
                        if action in ['refresh','force_refresh','locate'] or self.mode=='simulation':
                            self.event({'event':'vehicle_changed','id':uuid.uuid4().hex,'at':self.clock(),'value':action})
                except Exception as e:
                    with self.lock:
                        # An arbitrary exception may happen after the server accepted a command.
                        state='failed' if isinstance(e,m.Problem) else 'unknown'
                        message=e.message if isinstance(e,m.Problem) else 'Operation ended without confirmed completion ('+type(e).__name__+'). No automatic retry. Raw response omitted.'
                        job.update(state=state,message=message,completed_at=self.clock());self.log('warning',action+': '+message)
                        self.notice(action.replace('_',' ')+': '+message,important=True)
                finally:
                    with self.lock:self.busy=False
            self.executor.submit(execute)
            return answer
    def confirm(self,cid,outdoors=False):
        with self.lock:
            c=self.confirmations.get(cid)
            if not c or c['expires']<self.clock() or c['epoch']!=self.epoch:raise m.Problem('Confirmation expired. Request the action again.',409)
            if c['action']=='climate_start' and outdoors is not True:raise m.Problem('Confirm that the car is parked outdoors, clear of enclosed spaces.')
            if c['preset'] is not None:
                current=next((p for p in self.cfg['presets'] if p['id']==c['preset_id']),None)
                if current!=c['preset']:raise m.Problem('Preset changed. Request a new confirmation.',409)
            self.confirmations.pop(cid);self.receipts.pop(c['request_id'],None)
            return self.request_command({'action':c['action'],'preset_id':c['preset_id'],'request_id':c['request_id'],
                'expected_mode':c['mode'],'epoch':c['epoch']},automatic=False,confirmed=True)
    def cancel_confirmation(self,cid):
        with self.lock:self.confirmations.pop(cid,None);return {'cancelled':True}
    def notice(self,message,important=False):
        now=self.clock();s=self.cfg['settings'];hm=dt.datetime.fromtimestamp(now,ZoneInfo(s['timezone'])).strftime('%H:%M')
        item={'id':uuid.uuid4().hex,'at':now,'message':message[:500]};self.notices.appendleft(item)
        if not important and s['quiet_enabled'] and m.in_window(hm,s['quiet_start'],s['quiet_end']):return
        self.effect({'type':'notify','text':message[:500]})
    def effect(self,e):
        eid=uuid.uuid4().hex;self.effects[eid]={'id':eid,'expires':self.clock()+300,**e}
        while len(self.effects)>100:self.effects.popitem(last=False)
    def get_effects(self):
        with self.lock:
            self.effects=collections.OrderedDict((k,v) for k,v in self.effects.items() if v['expires']>=self.clock())
            return list(copy.deepcopy(self.effects).values())[:20]
    def ack(self,ids):
        if not isinstance(ids,list) or len(ids)>100:raise m.Problem('Invalid acknowledgement.')
        with self.lock:
            for i in ids:self.effects.pop(i,None)
        return {'acknowledged':len(ids)}
    def update_context(self,body):
        with self.lock:
            now=self.clock();stamp=body.get('at');m.number(stamp,0,100000000000,'Context timestamp')
            if abs(stamp-now)>90:raise m.Problem('Phone context is too old.',409)
            context=body.get('context',{})
            if not isinstance(context,dict) or len(context)>30:raise m.Problem('Invalid phone context.')
            for k,v in context.items():
                if k in ['car_connected','home','wifi','charging','android_auto']:
                    if v is not None:m.flag(v,k)
                elif isinstance(k,str) and re.fullmatch(r'user\.[A-Za-z0-9_]{1,40}',k):m.text(v,k,200,True)
                else:raise m.Problem('Unknown phone context field.')
            for k,v in context.items():self.phone[k]={'value':v,'at':now}
            if context.get('car_connected') is True or context.get('android_auto') is True:
                self.pending={k:v for k,v in self.pending.items() if v['event'].get('event') not in ['bluetooth_disconnected','android_auto_disconnected']}
            return {'updated':True}
    def event(self,body):
        with self.lock:
            now=self.clock();kind=body.get('event');eid=m.ident(body.get('id',''))
            if kind not in m.EVENTS or kind=='schedule':raise m.Problem('Invalid external trigger.')
            stamp=body.get('at');m.number(stamp,0,100000000000,'Event timestamp')
            if abs(stamp-now)>90:raise m.Problem('Event is too old or the phone clock differs by more than 90 seconds.',409)
            self.events=collections.OrderedDict((k,v) for k,v in self.events.items() if now-v<300)
            if eid in self.events:return {'duplicate':True}
            context=body.get('context',{})
            if not isinstance(context,dict) or len(context)>30:raise m.Problem('Invalid phone context.')
            for k,v in context.items():
                if k in ['car_connected','home','wifi','charging','android_auto']:
                    if v is not None:m.flag(v,k)
                elif re.fullmatch(r'user\.[A-Za-z0-9_]{1,40}',k):m.text(v,k,200,True)
                else:raise m.Problem('Unknown phone context field.')
            self.events[eid]=now
            while len(self.events)>500:self.events.popitem(last=False)
            for k,v in context.items():self.phone[k]={'value':v,'at':now}
            mapping={'bluetooth_connected':('car_connected',True),'bluetooth_disconnected':('car_connected',False),
                'android_auto_connected':('android_auto',True),'android_auto_disconnected':('android_auto',False),
                'home_enter':('home',True),'home_exit':('home',False),'wifi_connected':('wifi',True),
                'wifi_disconnected':('wifi',False),'power_connected':('charging',True),'power_disconnected':('charging',False)}
            if kind in mapping:
                k,v=mapping[kind];self.phone[k]={'value':v,'at':now}
            if kind in ['bluetooth_connected','android_auto_connected'] or context.get('car_connected') is True:
                self.pending={k:v for k,v in self.pending.items() if v['event'].get('event') not in ['bluetooth_disconnected','android_auto_disconnected']}
            self._trigger(kind,body,now)
            return {'accepted':True,'pending_rules':len(self.pending)}
    def _trigger(self,kind,event,now):
        if not self.cfg['settings']['rules_enabled']:return
        s=self.cfg['settings'];state=self.adapter.snapshot()
        for r in self.cfg['rules']:
            if not r['enabled'] or r['event']!=kind or r['scope']!=self.mode:continue
            if kind=='schedule' and event.get('value')!=r['id']:continue
            if now-self.last_rule.get(r['id'],0)<r['cooldown_seconds'] or r['id'] in self.pending:continue
            if not m.matches(r,state,self.phone,event,s,now):continue
            self.pending[r['id']]={'rule':copy.deepcopy(r),'due':now+r['delay_seconds'],'event':copy.deepcopy(event),'epoch':self.epoch}
    def evaluate(self,rid,event=None):
        with self.lock:
            r=next((r for r in self.cfg['rules'] if r['id']==rid),None)
            if not r:raise m.Problem('Unknown rule.')
            s=self.cfg['settings'];state=self.adapter.snapshot();now=self.clock();event=event or {}
            return {'conditions_match':m.matches(r,state,self.phone,event,s,now),
                'conditions':[{'field':c['field'],'matches':m.predicate(c,state,self.phone,event,s,now)} for c in r['conditions']],
                'mode_matches':r['scope']==self.mode,'enabled':r['enabled'],'master_enabled':s['rules_enabled'],
                'message':'Dry run only. No action was executed.'}
    def tick(self):
        with self.lock:
            now=self.clock();s=self.cfg['settings'];local=dt.datetime.fromtimestamp(now,ZoneInfo(s['timezone']))
            slot=local.strftime('%Y-%m-%d %H:%M')  # One execution in the repeated DST hour.
            self.confirmations={k:v for k,v in self.confirmations.items() if v['expires']>=now}
            for r in self.cfg['rules']:
                if r['event']=='schedule' and r['time']==local.strftime('%H:%M') and self.schedule_seen.get(r['id'])!=slot:
                    self.schedule_seen[r['id']]=slot
                    self._trigger('schedule',{'event':'schedule','at':now,'value':r['id']},now)
            due=[(k,v) for k,v in self.pending.items() if v['due']<=now]
            for rid,item in due:
                self.pending.pop(rid,None);r=item['rule']
                current=next((q for q in self.cfg['rules'] if q['id']==rid),None)
                if not current or not current['enabled'] or not s['rules_enabled'] or item['epoch']!=self.epoch:continue
                if r['scope']!=self.mode or not m.matches(r,self.adapter.snapshot(),self.phone,item['event'],s,now):
                    self.log('info',r['name']+': conditions no longer match. Skipped.');continue
                # Never execute a badly delayed action after device sleep/recovery.
                if now-item['due']>90:self.log('warning',r['name']+': expired after sleep; not replayed.');continue
                self.last_rule[rid]=now;atomic_json(self.root/'cooldowns.json',self.last_rule)
                self.log('info','Rule fired: '+r['name'])
                for a in r['actions']:
                    try:self._rule_action(a)
                    except m.Problem as e:self.log('warning',r['name']+': '+e.message);break
    def _rule_action(self,a):
        kind=a['type'];state=self.adapter.snapshot()
        if kind=='notify':self.notice(m.render_message(a['text'],state))
        elif kind=='speak':self.effect({'type':'speak','text':m.render_message(a['text'],state)})
        elif kind=='run_task':
            if a['task'] not in self.cfg['settings']['task_allowlist']:raise m.Problem('Task is not in the allowed-task list.',403)
            self.effect({'type':'run_task','task':a['task']})
        elif kind=='set_variable':self.effect({'type':'set_variable','variable':a['variable'],'value':m.render_message(a['value'],state)})
        else:
            body={'action':kind,'preset_id':a.get('preset_id'),'request_id':uuid.uuid4().hex,'epoch':self.epoch,'expected_mode':self.mode}
            # Dangerous automatic actions become user confirmation requests, never autonomous actuation.
            if self.mode=='live' and kind in ['unlock','climate_start','climate_stop','lights','horn_lights']:
                self.request_command(body,automatic=False,require_confirmation=True)
            else:self.request_command(body,automatic=(kind=='lock' and self.mode=='live'))
    def simulate(self,data):
        with self.lock:
            if self.mode!='simulation':raise m.Problem('Simulation controls are unavailable in live mode.',403)
            self.sim.patch(data)
            self.event({'event':'vehicle_changed','id':uuid.uuid4().hex,'at':self.clock(),'value':'simulation change'})
            return {'updated':True}
    def loop(self):
        while not self.stop.wait(1):
            try:self.tick()
            except Exception:
                with self.lock:self.log('error','Scheduler error. No automatic retry of vehicle commands.')
