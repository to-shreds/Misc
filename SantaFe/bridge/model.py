"""Validated, versioned configuration and fail-closed rule predicates."""
from __future__ import annotations
import copy
import datetime as dt
import json
import math
import re
import time
import uuid
from zoneinfo import ZoneInfo

VERSION = '0.1.0'
EVENTS = ['manual', 'schedule', 'bluetooth_connected', 'bluetooth_disconnected',
          'android_auto_connected', 'android_auto_disconnected', 'home_enter', 'home_exit',
          'wifi_connected', 'wifi_disconnected', 'power_connected', 'power_disconnected',
          'alarm', 'nfc', 'variable', 'vehicle_changed', 'weather_changed']
COMMANDS = ['refresh', 'force_refresh', 'lock', 'unlock', 'climate_start', 'climate_stop',
            'lights', 'horn_lights', 'locate']
ACTIONS = COMMANDS + ['notify', 'speak', 'run_task', 'set_variable']
FIELDS = {
    'locked': 'boolean', 'engine_running': 'boolean', 'climate_running': 'boolean',
    'fuel_percent': 'number', 'range_miles': 'number', 'odometer_miles': 'number',
    'battery_percent': 'number', 'outside_f': 'number', 'tire_warning': 'boolean',
    'hood_open': 'boolean', 'trunk_open': 'boolean', 'sunroof_open': 'boolean',
    'driver_door_open': 'boolean', 'passenger_door_open': 'boolean',
    'rear_left_door_open': 'boolean', 'rear_right_door_open': 'boolean',
    'driver_window_open': 'boolean', 'passenger_window_open': 'boolean',
    'rear_left_window_open': 'boolean', 'rear_right_window_open': 'boolean',
    'phone.car_connected': 'boolean', 'phone.home': 'boolean',
    'phone.wifi': 'boolean', 'phone.charging': 'boolean', 'phone.android_auto': 'boolean',
    'age_seconds': 'number', 'event.value': 'text',
}
CLOSURES = ['hood_open', 'trunk_open', 'driver_door_open', 'passenger_door_open',
            'rear_left_door_open', 'rear_right_door_open']
SEATS = {'off': 0, 'low_cool': 3, 'medium_cool': 4, 'high_cool': 5,
         'low_heat': 6, 'medium_heat': 7, 'high_heat': 8}
SETTINGS = {
    'name': 'Santa Fe', 'timezone': 'America/New_York', 'units': 'us', 'theme': 'dark',
    'rules_enabled': False, 'stale_seconds': 900, 'command_cooldown': 30,
    'cloud_read_interval': 300, 'force_refresh_interval': 900,
    'phone_context_ttl': 600, 'quiet_start': '22:00', 'quiet_end': '07:00',
    'quiet_enabled': False, 'task_allowlist': [], 'bluetooth_name': '',
    'home_wifi': '', 'default_preset': 'comfort',
    'tiles': ['locked', 'fuel_percent', 'range_miles', 'battery_percent',
              'engine_running', 'tire_warning'],
}

class Problem(Exception):
    def __init__(self, message: str, status: int = 400):
        self.message, self.status = message, status
        super().__init__(message)

def now_iso(t: float | None = None) -> str:
    return dt.datetime.fromtimestamp(time.time() if t is None else t, dt.timezone.utc).isoformat()

def number(value, minimum, maximum, label, integer=False):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        raise Problem(f'{label} must be a finite number.')
    if not minimum <= value <= maximum or (integer and value != int(value)):
        raise Problem(f'{label} must be {minimum} to {maximum}' + (' (whole numbers).' if integer else '.'))
    return int(value) if integer else value

def text(value, label, maximum=120, empty=False):
    if not isinstance(value, str) or len(value) > maximum or (not empty and not value.strip()):
        raise Problem(f'{label} must be text of 1 to {maximum} characters.')
    if any(ord(x) < 32 for x in value if x not in '\n\t'):
        raise Problem(f'{label} contains a control character.')
    return value.strip()

def flag(value, label):
    if type(value) is not bool:
        raise Problem(f'{label} must be true or false.')
    return value

def clock(value):
    if not isinstance(value, str) or not re.fullmatch(r'(?:[01]\d|2[0-3]):[0-5]\d', value):
        raise Problem('Time must use HH:MM in 24-hour format.')
    return value

def ident(value):
    if not isinstance(value, str) or not re.fullmatch(r'[A-Za-z0-9_-]{1,64}', value):
        raise Problem('Invalid record ID.')
    return value

def preset(data):
    if not isinstance(data, dict): raise Problem('Preset must be an object.')
    out = {'id': ident(data.get('id', uuid.uuid4().hex)),
           'name': text(data.get('name'), 'Preset name', 60),
           'temperature_f': number(data.get('temperature_f', 70), 62, 82, 'Temperature'),
           'duration_minutes': number(data.get('duration_minutes', 5), 1, 10, 'Runtime', True),
           'defrost': flag(data.get('defrost', False), 'Defrost')}
    for seat in ['driver', 'passenger', 'rear_left', 'rear_right']:
        v = data.get(seat, 'off')
        if v not in SEATS or (seat.startswith('rear') and 'cool' in v):
            raise Problem('Rear-seat ventilation is not supported on this target vehicle.')
        out[seat] = v
    return out

def defaults():
    p = [preset({'id':'comfort', 'name':'Comfort', 'temperature_f':70}),
         preset({'id':'summer', 'name':'Summer', 'temperature_f':68, 'driver':'high_cool'}),
         preset({'id':'winter', 'name':'Winter', 'temperature_f':74, 'defrost':True, 'driver':'medium_heat'})]
    return {'schema': 1, 'settings': copy.deepcopy(SETTINGS), 'presets': p, 'rules': []}

def settings(data):
    if not isinstance(data, dict) or set(data) - set(SETTINGS): raise Problem('Unknown settings field.')
    s = copy.deepcopy(SETTINGS); s.update(data)
    text(s['name'], 'Vehicle display name', 60)
    try: ZoneInfo(s['timezone'])
    except (ValueError, KeyError, TypeError): raise Problem('Unknown time zone.')
    if s['units'] not in ['us', 'metric'] or s['theme'] not in ['dark','light']: raise Problem('Invalid display setting.')
    for k, lo, hi in [('stale_seconds',60,3600),('command_cooldown',10,600),
                      ('cloud_read_interval',60,3600),('force_refresh_interval',300,7200),
                      ('phone_context_ttl',60,3600)]: number(s[k],lo,hi,k,True)
    for k in ['rules_enabled','quiet_enabled']: flag(s[k],k)
    clock(s['quiet_start']); clock(s['quiet_end'])
    if not isinstance(s['task_allowlist'], list) or len(s['task_allowlist']) > 50: raise Problem('At most 50 allowed tasks.')
    for k in s['task_allowlist']: text(k,'Task name',100)
    for k in ['bluetooth_name','home_wifi']: text(s[k],k,100,True)
    ident(s['default_preset'])
    if not isinstance(s['tiles'],list) or not 1 <= len(s['tiles']) <= 16 or any(k not in FIELDS or k.startswith(('event.','phone.')) for k in s['tiles']):
        raise Problem('Choose 1 to 16 supported dashboard tiles.')
    return s

def rule(data, preset_ids):
    if not isinstance(data,dict): raise Problem('Rule must be an object.')
    r = {'id': ident(data.get('id',uuid.uuid4().hex)), 'name':text(data.get('name'),'Rule name',80),
         'enabled':flag(data.get('enabled',False),'Enabled'),
         'event':data.get('event','manual'), 'scope':data.get('scope','simulation'),
         'match':data.get('match','all'),
         'delay_seconds':number(data.get('delay_seconds',0),0,86400,'Delay',True),
         'cooldown_seconds':number(data.get('cooldown_seconds',300),10,604800,'Cooldown',True),
         'time':clock(data.get('time','08:00')),
         'start':clock(data.get('start','00:00')), 'end':clock(data.get('end','00:00')),
         'days':data.get('days',[0,1,2,3,4,5,6]), 'conditions':[], 'actions':[]}
    if r['event'] not in EVENTS or r['scope'] not in ['simulation','live'] or r['match'] not in ['all','any']:
        raise Problem('Invalid trigger, scope or condition logic.')
    if not isinstance(r['days'],list) or not r['days'] or any(type(d) is not int or d not in range(7) for d in r['days']): raise Problem('Choose valid weekdays.')
    conds=data.get('conditions',[]); acts=data.get('actions',[])
    if not isinstance(conds,list) or len(conds)>20: raise Problem('At most 20 conditions.')
    if not isinstance(acts,list) or not 1<=len(acts)<=10: raise Problem('Choose 1 to 10 actions.')
    for c in conds:
        if not isinstance(c,dict): raise Problem('Condition must be an object.')
        field=c.get('field'); op=c.get('op'); value=c.get('value')
        if field not in FIELDS and not (isinstance(field,str) and re.fullmatch(r'user\.[A-Za-z0-9_]{1,40}',field)):
            raise Problem('Unknown condition field.')
        if op not in ['eq','ne','lt','le','gt','ge','contains','known']: raise Problem('Unknown comparison.')
        typ=FIELDS.get(field,'text')
        if op!='known':
            if typ=='boolean':
                flag(value,'Condition value')
                if op not in ['eq','ne']: raise Problem('Boolean fields need equals or not-equals.')
            elif typ=='number':
                number(value,-1000000,100000000,'Condition value')
                if op=='contains': raise Problem('Contains needs a text field.')
            else:
                text(value,'Condition value',200,True)
                if op not in ['eq','ne','contains']: raise Problem('Text fields need equals, not-equals, or contains.')
        else: value=True
        r['conditions'].append({'field':field,'op':op,'value':value})
    for a in acts:
        if not isinstance(a,dict) or a.get('type') not in ACTIONS: raise Problem('Unknown action.')
        out={'type':a['type']}
        if a['type']=='climate_start':
            out['preset_id']=a.get('preset_id','comfort')
            if out['preset_id'] not in preset_ids: raise Problem('Climate preset no longer exists.')
        if a['type'] in ['notify','speak']: out['text']=text(a.get('text','Santa Fe check'),'Message',400)
        if a['type']=='run_task': out['task']=text(a.get('task'),'Task name',100)
        if a['type']=='set_variable':
            out['variable']=text(a.get('variable'),'Variable name',48)
            if not re.fullmatch(r'SFUser[A-Za-z0-9_]{1,40}',out['variable']): raise Problem('Output variables must begin SFUser.')
            out['value']=text(a.get('value',''),'Variable value',400,True)
        r['actions'].append(out)
    if sum(a['type'] in COMMANDS for a in r['actions']) > 1:
        raise Problem('Use at most one vehicle command per rule. Later actions do not wait for vehicle completion.')
    return r

def config(data, restoring=False):
    if not isinstance(data,dict) or data.get('schema')!=1: raise Problem('Unsupported backup schema.')
    if set(data) - {'schema','settings','presets','rules'}: raise Problem('Backup contains unrecognized fields.')
    s=settings(data.get('settings',{}))
    pp=data.get('presets',[]); rr=data.get('rules',[])
    if not isinstance(pp,list) or not 1<=len(pp)<=50: raise Problem('Choose 1 to 50 presets.')
    if not isinstance(rr,list) or len(rr)>100: raise Problem('At most 100 rules.')
    pp=[preset(p) for p in pp]; ids=[p['id'] for p in pp]
    if len(set(ids))!=len(ids): raise Problem('Duplicate preset ID.')
    if s['default_preset'] not in ids: raise Problem('Default preset must exist.')
    rr=[rule(r,set(ids)) for r in rr]
    if len({r['id'] for r in rr})!=len(rr): raise Problem('Duplicate rule ID.')
    if restoring:
        s['rules_enabled']=False
        for r in rr:r['enabled']=False
    return {'schema':1,'settings':s,'presets':pp,'rules':rr}

def in_window(hm, start, end):
    return True if start==end else (start<=hm<end if start<end else hm>=start or hm<end)

def age(state, now):
    stamp=state.get('updated_at')
    if not isinstance(stamp,(int,float)) or isinstance(stamp,bool) or not math.isfinite(stamp) or stamp>now+30: return None
    return max(0,now-stamp)

def predicate(c, state, phone, event, s, now):
    f=c['field']; a=age(state,now)
    if f=='age_seconds': v=a
    elif f=='event.value': v=event.get('value')
    elif f.startswith('phone.'):
        entry=phone.get(f[6:],{})
        v=entry.get('value') if 0<=now-entry.get('at',0)<=s['phone_context_ttl'] else None
    elif f.startswith('user.'):
        entry=phone.get(f,{})
        v=entry.get('value') if 0<=now-entry.get('at',0)<=s['phone_context_ttl'] else None
    else: v=state.get(f) if a is not None and a<=s['stale_seconds'] else None
    if c['op']=='known': return v is not None
    if v is None: return False  # Unknown != false. Even 'not equals' fails closed.
    w=c['value']; op=c['op']
    if type(v) is bool or type(w) is bool:
        return type(v) is type(w) and ((v==w) if op=='eq' else (v!=w) if op=='ne' else False)
    if op in ['lt','le','gt','ge']:
        if not isinstance(v,(int,float)) or not math.isfinite(v):return False
        return {'lt':v<w,'le':v<=w,'gt':v>w,'ge':v>=w}[op]
    if op=='eq':return v==w
    if op=='ne':return v!=w
    if op=='contains':return isinstance(v,str) and isinstance(w,str) and w in v
    return False

def matches(r,state,phone,event,s,now):
    local=dt.datetime.fromtimestamp(now,ZoneInfo(s['timezone']))
    if local.weekday() not in r['days'] or not in_window(local.strftime('%H:%M'),r['start'],r['end']):return False
    results=[predicate(c,state,phone,event,s,now) for c in r['conditions']]
    return (all(results) if r['match']=='all' else any(results)) if results else True

def render_message(message,state):
    def sub(m):
        key=m.group(1)
        value=state.get(key)
        if key not in FIELDS or value is None:return 'unknown'
        return str(round(value,1) if type(value) is float else value)
    return re.sub(r'\{([a-z_]+)\}',sub,message)[:500]
