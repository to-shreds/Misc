"""Contract tests using a fake upstream library. These are NOT Hyundai integration tests."""
import datetime as dt
import sys
import types
from types import SimpleNamespace as N
import pytest
from bridge.adapters import HyundaiUSA, Simulator
from bridge.model import defaults, Problem

@pytest.fixture
def adapter(monkeypatch):
    calls=[]
    vehicle=N(name='Test vehicle', model='Test', VIN='REDACTED1234', is_locked=True,
              engine_is_running=False, total_driving_range=160.9344, total_driving_range_unit='km',
              odometer=42, odometer_unit='mi', outside_temperature=10, outside_temperature_unit='°C',
              last_updated_at=dt.datetime(2026,10,4,tzinfo=dt.timezone.utc),
              fuel_level=0, car_battery_percentage=87, location_latitude=42.36, location_longitude=-71.06)
    class Manager:
        def __init__(self, **kwargs):
            calls.append(('constructor',kwargs))
            self.vehicles={'vehicle-1':vehicle};self.api=N(session=N(request=self.request))
            self.tx='transaction-1';self.result=N(name='SUCCESS')
        def request(self,*args,**kwargs):calls.append(('request',kwargs));return kwargs
        def login(self):return True
        def check_and_refresh_token(self):calls.append(('token',))
        def get_vehicle(self,vid):assert vid=='vehicle-1';return vehicle
        def update_vehicle_with_cached_state(self,vid):calls.append(('cached',vid))
        def force_refresh_vehicle_state(self,vid):calls.append(('force',vid))
        def lock(self,vid):calls.append(('lock',vid));return self.tx
        def unlock(self,vid):calls.append(('unlock',vid));return self.tx
        def start_climate(self,vid,options):calls.append(('climate',vid,options));return self.tx
        def check_action_status(self,vid,tx,**kwargs):calls.append(('poll',vid,tx,kwargs));return self.result
    lib=types.ModuleType('hyundai_kia_connect_api');lib.VehicleManager=Manager
    options=types.ModuleType('hyundai_kia_connect_api.ApiImpl');options.ClimateRequestOptions=lambda **kw:N(**kw)
    monkeypatch.setitem(sys.modules,'hyundai_kia_connect_api',lib)
    monkeypatch.setitem(sys.modules,'hyundai_kia_connect_api.ApiImpl',options)
    a=HyundaiUSA('local-user','not-a-real-password','0000');a.select('vehicle-1')
    return a,vehicle,calls

def test_region_brand_are_integer_ids(adapter):
    a,v,calls=adapter;kwargs=calls[0][1]
    assert kwargs['region']==3 and type(kwargs['region']) is int
    assert kwargs['brand']==2 and type(kwargs['brand']) is int

def test_timeout_added_without_disabling_tls(adapter):
    a,v,calls=adapter
    kw=a.vm.api.session.request('GET','unused',timeout=None)
    assert kw['timeout']==(10,30) and 'verify' not in kw
    assert a.vm.api.session.request('GET','unused',timeout=5)['timeout']==5

def test_units_and_zero_values(adapter):
    a,v,calls=adapter;s=a.snapshot()
    assert s['range_miles']==pytest.approx(100)
    assert s['odometer_miles']==42 and s['outside_f']==50 and s['fuel_percent']==0
    assert s['updated_at']==v.last_updated_at.timestamp()

def test_malformed_upstream_values_are_unknown(adapter):
    a,v,calls=adapter
    v.is_locked='false';v.engine_is_running=0;v.fuel_level=float('nan');v.car_battery_percentage=200
    v.location_latitude=200;v.outside_temperature='hot';v.total_driving_range=float('inf')
    a.read();s=a.snapshot()
    for field in ['locked','engine_running','fuel_percent','battery_percent','latitude','outside_f','range_miles']:
        assert s[field] is None,field

def test_naive_timestamp_is_unknown(adapter):
    a,v,calls=adapter;v.last_updated_at=dt.datetime(2026,10,4);a.read()
    assert a.snapshot()['updated_at'] is None

def test_cached_and_forced_reads_call_correct_methods(adapter):
    a,v,calls=adapter;a.perform('refresh');a.perform('force_refresh')
    assert ('cached','vehicle-1') in calls and ('force','vehicle-1') in calls

def test_transaction_outcomes_not_retried(adapter):
    a,v,calls=adapter
    assert a.perform('lock')['result']=='succeeded'
    a.vm.result=N(name='FAILED');assert a.perform('unlock')['result']=='failed'
    a.vm.result=N(name='TIMEOUT');assert a.perform('unlock')['result']=='unknown'
    a.vm.tx=None;assert a.perform('lock')['result']=='unknown'
    assert len([c for c in calls if c[0] in ['lock','unlock']])==4
    assert all(c[3]=={'synchronous':True,'timeout':120} for c in calls if c[0]=='poll')

def test_seat_preset_mapping(adapter):
    a,v,calls=adapter
    preset=dict(defaults()['presets'][0]);preset.update(driver='high_cool',passenger='medium_heat')
    a.perform('climate_start',preset)
    opts=next(c[2] for c in calls if c[0]=='climate')
    assert opts.front_left_seat==5 and opts.front_right_seat==7
    assert opts.set_temp==preset['temperature_f'] and opts.duration==preset['duration_minutes']

def test_vehicle_listing_minimizes_identifiers(adapter):
    a,v,calls=adapter;listing=a.vehicles()
    assert listing[0]['vin_last4']=='1234' and 'VIN' not in listing[0]

def test_simulator_invalid_patch_is_atomic():
    s=Simulator();before=s.snapshot()
    with pytest.raises(Problem):s.patch({'failure':'timeout','fuel_percent':float('nan')})
    assert not s.fail_next and s.snapshot()==before
