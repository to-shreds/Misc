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
@pytest.mark.parametrize('variable',['Password','SF','%SFUserTest','SFUser','SFUserA;eval()'])
def test_output_variable_namespace(variable):
    with pytest.raises(m.Problem):m.rule({'name':'bad','actions':[{'type':'set_variable','variable':variable,'value':'x'}]},set())

def test_simulation_command_success_and_idempotency(engine):
    e,c=engine;b=dict(action='lock',request_id='saYKZIr4uNDXo1325R-id',idempotency_mode=e.mode,epoch=e.epoch)