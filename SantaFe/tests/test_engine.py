import copy, datetime as dt, json, math, os, time, uuid
from pathlib import Path
import pytest
from bridge import model as m
from bridge.engine import Engine
from bridge.adapters import Simulator

class Clock:
    def __init__(self):self.now=dt.datetime(2026,10,4,16,0,tzinfo=dt.timezone.utc).timestamp()
    def __call__(self):return self.now
    def advance(self,t):self.now+=t

@pytest.fixture
def engine(tmp_path):
    c=Clock();e=Engine(tmp_path,c)
    yield e,c
    e.close()

def command(e,action='lock',**extra):
    return e.request_command(dict(action=action,request_id=uuid.uuid4().hex,expected_mode=e.mode,epoch=e.epoch,**extra))
def wait(e):
    end=time.monotonic()+3
    while e.busy and time.monotonic()<end:time.sleep(.01)
    assert not e.busy
    return list(e.jobs.values())[-1]
def event(e,kind='manual',**extra):return e.event(dict(event=kind,id=uuid.uuid4().hex,at=e.clock(),**extra))
def rule(e,**extra):
    data=dict(id=uuid.uuid4().hex,name='Test routine',enabled=True,event='manual',scope=e.mode,actions=[{'type':'notify','text':'Test complete'}]);data.update(extra)
    data=m.rule(data,{'comfort','summer','winter'})
    e.cfg['rules'].append(data);e.cfg['settings']['rules_enabled']=True
    return data

def live(e):
    class FakeLive(Simulator):
        selected='unit-test';name='fake-live'
    e.live=FakeLive(e.clock);e.set_mode('live');return e.live

@pytest.mark.parametrize('v',[True,float('nan'),float('inf'),float('-inf'),'68',None])
def test_reject_invalid_temperature(v):
    with pytest.raises(m.Problem):m.preset({'name':'Bad','temperature_f':v})
@pytest.mark.parametrize('changes',[{'rear_left':'high_cool'},{'duration_minutes':11},{'duration_minutes':1.5},{'temperature_f':61},{'driver':'unknown'}])
def test_preset_limits(changes):
    with pytest.raises(m.Problem):m.preset({'name':'Bad',**changes})
@pytest.mark.parametrize('op',['eq','ne','known'])
def test_unknown_boolean_fails_closed(engine,op):
    e,c=engine;s=e.sim.snapshot();s['locked']=None
    assert m.predicate({'field':'locked','op':op,'value':False},s,{}, {},e.cfg['settings'],c()) is False
@pytest.mark.parametrize('stamp',[None,float('nan'),float('inf'),True,'yesterday'])
def test_invalid_timestamp_unknown(engine,stamp):
    e,c=engine;s=e.sim.snapshot();s['updated_at']=stamp;assert m.age(s,c()) is None

def test_stale_and_future_data(engine):
    e,c=engine;s=e.sim.snapshot();s['updated_at']=c()-1000
    pred={'field':'locked','op':'eq','value':True}
    assert not m.predicate(pred,s,{}, {},e.cfg['settings'],c())
    s['updated_at']=c()+100;assert m.age(s,c()) is None

def test_revision_backup_and_restore_disables(engine):
    e,c=engine;r=rule(e);data=copy.deepcopy(e.cfg)
    e.save_config(data,1)
    with pytest.raises(m.Problem):e.save_config(data,1)
    e.save_config(data,2,True)
    assert not e.cfg['settings']['rules_enabled'] and not e.cfg['rules'][0]['enabled']
    assert (e.root/'config.previous.json').exists()
    assert os.stat(e.root/'config.json').st_mode & 0o777==0o600

def test_no_multiple_vehicle_operations(engine):
    e,c=engine
    with pytest.raises(m.Problem):rule(e,actions=[{'type':'refresh'},{'type':'lock'}])
@pytest.mark.parametrize('variable',['Password','SF','%SFUserTest','SFUser','SFUserA;evil()'])
def test_output_variable_namespace(variable):
    with pytest.raises(m.Problem):m.rule({'name':'bad','actions':[{'type':'set_variable','variable':variable,'value':'x'}]},set())

def test_simulation_command_success_and_idempotency(engine):
    e,c=engine;b=dict(action='lock',request_id='same-id',expected_mode=e.mode,epoch=e.epoch)
    one=e.request_command(b);two=e.request_command(b);assert one['id']==two['id']
    assert wait(e)['state']=='succeeded' and len(e.jobs)==1
    with pytest.raises(m.Problem):e.request_command({**b,'action':'lights'})

def test_busy_command_not_queued(engine):
    e,c=engine;command(e)
    with pytest.raises(m.Problem):command(e,'lights')
    assert len(e.jobs)==1;wait(e)

def test_confirmation_is_one_use_and_start_needs_outdoors(engine):
    e,c=engine;r=command(e,'climate_start',preset_id='summer');assert r['state']=='confirmation_required'
    assert not e.sim.snapshot()['engine_running']
    with pytest.raises(m.Problem):e.confirm(r['id'])
    e.confirm(r['id'],True);assert wait(e)['state']=='succeeded'
    assert e.sim.snapshot()['engine_running']
    with pytest.raises(m.Problem):e.confirm(r['id'],True)
    c.advance(301);assert not e.sim.snapshot()['engine_running']

def test_confirmation_expiry_and_preset_binding(engine):
    e,c=engine;r=command(e,'unlock');c.advance(61)
    with pytest.raises(m.Problem):e.confirm(r['id'])
    r=command(e,'climate_start',preset_id='comfort');e.cfg['presets'][0]['temperature_f']=72
    with pytest.raises(m.Problem):e.confirm(r['id'],True)

def test_old_epoch_cannot_command(engine):
    e,c=engine;b=dict(action='lock',request_id='old',expected_mode=e.mode,epoch=e.epoch);e.set_mode('simulation')
    with pytest.raises(m.Problem):e.request_command(b)

def test_failure_outcomes(engine):
    e,c=engine;e.sim.patch({'failure':'rejected'});command(e);assert wait(e)['state']=='failed'
    e.sim.patch({'failure':'timeout'});command(e);assert wait(e)['state']=='unknown'
    assert len(e.jobs)==2

def test_simulator_validation_is_atomic(engine):
    e,c=engine;before=e.sim.snapshot()
    with pytest.raises(m.Problem):e.sim.patch({'failure':'timeout','fuel_percent':999})
    assert e.sim.fail_next=='' and e.sim.snapshot()==before

def test_live_requires_arm(engine):
    e,c=engine;live(e)
    with pytest.raises(m.Problem):command(e)
    e.arm('ENABLE LIVE COMMANDS');command(e);assert wait(e)['state']=='succeeded'
    e.pause()
    with pytest.raises(m.Problem):command(e)

def test_live_automatic_lock_needs_fresh_disconnect_and_closed_car(engine):
    e,c=engine;live(e);e.arm('ENABLE LIVE COMMANDS',True)
    b=dict(action='lock',request_id='auto',expected_mode=e.mode,epoch=e.epoch)
    with pytest.raises(m.Problem):e.request_command(b,automatic=True)
    event(e,'bluetooth_disconnected');e.live.patch({'hood_open':None})
    with pytest.raises(m.Problem):e.request_command(b,automatic=True)
    e.live.patch({'hood_open':False});c.advance(601)
    with pytest.raises(m.Problem):e.request_command(b,automatic=True)
    event(e,'bluetooth_disconnected');e.live.patch({'updated_at':c()})
    e.request_command(b,automatic=True);assert wait(e)['state']=='succeeded'

@pytest.mark.parametrize('action',['unlock','climate_start','climate_stop','lights','horn_lights'])
def test_live_rule_commands_require_confirmation(engine,action):
    e,c=engine;live(e);e.arm('ENABLE LIVE COMMANDS')
    a={'type':action}
    if action=='climate_start':a['preset_id']='comfort'
    rule(e,actions=[a]);event(e);e.tick()
    assert not e.jobs and len(e.confirmations)==1

def test_live_start_unknown_door_rejected(engine):
    e,c=engine;live(e);e.arm('ENABLE LIVE COMMANDS');e.live.patch({'driver_door_open':None})
    with pytest.raises(m.Problem):command(e,'climate_start',preset_id='comfort')

def test_delayed_rule_cancelled_on_reconnect(engine):
    e,c=engine;rule(e,event='bluetooth_disconnected',delay_seconds=120)
    event(e,'bluetooth_disconnected');assert len(e.pending)==1
    event(e,'bluetooth_connected');assert not e.pending
    c.advance(121);e.tick();assert not e.notices

def test_delayed_conditions_rechecked(engine):
    e,c=engine;e.sim.patch({'locked':False});rule(e,delay_seconds=60,conditions=[{'field':'locked','op':'eq','value':False}])
    event(e);e.sim.patch({'locked':True});c.advance(60);e.tick();assert not e.notices

def test_sleep_expiration_no_catchup(engine):
    e,c=engine;rule(e,delay_seconds=10);event(e);c.advance(101);e.tick();assert not e.notices

def test_rule_cooldown_and_duplicate_event(engine):
    e,c=engine;rule(e,cooldown_seconds=300);data=dict(event='manual',id='event1',at=c())
    e.event(data);e.event(data);e.tick();assert len(e.notices)==1
    event(e);e.tick();assert len(e.notices)==1
    c.advance(301);event(e);e.tick();assert len(e.notices)==2

def test_rules_are_mode_scoped(engine):
    e,c=engine;rule(e,scope='live');event(e);e.tick();assert not e.pending and not e.notices

def test_schedule_runs_only_matching_rule_once(engine):
    e,c=engine;rule(e,event='schedule',time='12:00');rule(e,event='schedule',time='13:00')
    e.tick();e.tick();assert len(e.notices)==1

def test_dst_repeated_hour_not_repeated(engine):
    e,c=engine;c.now=dt.datetime(2026,11,1,5,30,tzinfo=dt.timezone.utc).timestamp()
    rule(e,event='schedule',time='01:30',cooldown_seconds=10)
    e.tick();c.advance(3600);e.tick();assert len(e.notices)==1

def test_time_windows_and_days(engine):
    e,c=engine;r=rule(e,start='22:00',end='07:00')
    assert not m.matches(r,e.sim.snapshot(),{}, {},e.cfg['settings'],c())
    assert m.in_window('23:30','22:00','07:00') and m.in_window('06:00','22:00','07:00')
    assert not m.in_window('12:00','22:00','07:00')

def test_custom_task_allowlist(engine):
    e,c=engine;rule(e,actions=[{'type':'run_task','task':'MyTask'}]);event(e);e.tick();assert not e.effects
    e.cfg['settings']['task_allowlist']=['MyTask'];c.advance(301);event(e);e.tick()
    assert list(e.effects.values())[0]['task']=='MyTask'

def test_quiet_hours_retain_in_app_history(engine):
    e,c=engine;e.cfg['settings'].update(quiet_enabled=True,quiet_start='00:00',quiet_end='00:00')
    e.notice('ordinary');assert len(e.notices)==1 and not e.effects
    e.notice('urgent',important=True);assert len(e.effects)==1

def test_pairing_token_hash_only_and_single_use(engine):
    e,c=engine;code=e.code('tasker');paired=e.pair(code)
    assert e.authenticate(paired['token'])=='tasker'
    assert paired['token'] not in (e.root/'clients.json').read_text()
    with pytest.raises(m.Problem):e.pair(code)
    assert paired['token'] not in json.dumps(e.status())
    e.revoke();assert e.authenticate(paired['token']) is None

def test_pairing_code_expiry(engine):
    e,c=engine;code=e.code();c.advance(301)
    with pytest.raises(m.Problem):e.pair(code)

def test_context_update_no_trigger_and_unknown_ttl(engine):
    e,c=engine;rule(e,event='variable')
    e.update_context({'at':c(),'context':{'home':True}});e.tick();assert not e.notices
    pred={'field':'phone.home','op':'eq','value':True}
    assert m.predicate(pred,e.sim.snapshot(),e.phone,{},e.cfg['settings'],c())
    c.advance(601);assert not m.predicate(pred,e.sim.snapshot(),e.phone,{},e.cfg['settings'],c())

def test_restart_preserves_settings_but_not_live_commands(engine):
    e,c=engine;r=rule(e,scope='live');e.save_config(e.cfg,e.revision)
    live(e);e.arm('ENABLE LIVE COMMANDS',True);e.effect({'type':'run_task','task':'Untrusted'})
    other=Engine(e.root,c)
    try:
        assert other.mode=='simulation' and not other.live_armed and not other.effects and not other.pending
        assert other.cfg['rules'][0]['id']==r['id']
    finally:other.close()

def test_corrupt_settings_not_overwritten(tmp_path):
    p=tmp_path/'config.json';p.write_text('{broken')
    with pytest.raises(m.Problem):Engine(tmp_path)
    assert p.read_text()=='{broken'

def test_scheduled_confirmation_survives_local_heartbeat(engine):
    e,c=engine
    # Explicit rule-origin confirmation gets five minutes for a two-minute phone heartbeat.
    result=e.request_command({'action':'lights','request_id':'scheduled-light',
        'expected_mode':e.mode,'epoch':e.epoch},require_confirmation=True)
    assert e.confirmations[result['id']]['expires']-c.now==300
    c.now+=121
    assert e.confirm(result['id'])['state']=='queued'
    wait(e)

def test_invalid_context_does_not_consume_event_id(engine):
    e,c=engine;body={'id':'retry-context','at':c.now,'event':'manual','context':{'home':'bad'}}
    with pytest.raises(m.Problem):e.event(body)
    body['context']={'home':True}
    assert e.event(body)['accepted']

def test_pairing_role_is_validated(engine):
    e,c=engine
    with pytest.raises(m.Problem):e.code('admin')
