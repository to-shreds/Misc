"""Simulation plus an optional, explicitly enabled U.S. Hyundai adapter.
No network is used by the simulator. No API key is embedded in this project.
"""
from __future__ import annotations
import copy
import datetime as dt
import logging
import math
import threading
import functools
import time
from .model import Problem, FIELDS, SEATS, CLOSURES

def synchronized(method):
    @functools.wraps(method)
    def wrapped(self, *args, **kwargs):
        with self.lock:
            return method(self, *args, **kwargs)
    return wrapped


def finite_number(value, lo=None, hi=None):
    if type(value) not in (int, float) or not math.isfinite(value):return None
    if lo is not None and value < lo:return None
    if hi is not None and value > hi:return None
    return value


class Simulator:
    name='simulation'
    def __init__(self,clock=time.time):
        self.clock=clock;self.lock=threading.RLock()
        self.state={k:None for k in FIELDS if not k.startswith(('phone.','event.')) and k!='age_seconds'}
        self.state.update(dict(locked=True,engine_running=False,climate_running=False,
                              fuel_percent=72,range_miles=361,odometer_miles=24,
                              battery_percent=87,outside_f=65,tire_warning=False,
                              updated_at=self.clock(),latitude=None,longitude=None))
        for k in self.state:
            if k.endswith('_open'):self.state[k]=False
        self.fail_next='';self.engine_until=0
    @synchronized
    def snapshot(self):
        if self.engine_until and self.clock()>=self.engine_until:
            self.state.update(engine_running=False,climate_running=False,updated_at=self.clock());self.engine_until=0
        return copy.deepcopy(self.state)
    @synchronized
    def patch(self,p):
        if not isinstance(p,dict):raise Problem('Simulation values must be an object.')
        allowed=set(self.state)|{'failure'}
        if set(p)-allowed:raise Problem('Unknown simulated field.')
        for k,v in p.items():
            if k=='failure':
                if v not in ['','rejected','timeout']:raise Problem('Invalid failure type.')
                continue
            if v is not None:
                typ=FIELDS.get(k)
                if typ=='boolean' and type(v) is not bool:raise Problem(k+' must be boolean.')
                if typ=='number' or k in ['updated_at','latitude','longitude']:
                    from .model import number
                    ranges={'fuel_percent':(0,100),'battery_percent':(0,100),'latitude':(-90,90),'longitude':(-180,180),'outside_f':(-100,180)}
                    lo,hi=ranges.get(k,(0,100000000000))
                    number(v,lo,hi,k)
        for k,v in p.items():
            if k=='failure':self.fail_next=v
            else:self.state[k]=v
        if p.get('engine_running') is False:self.engine_until=0
        if 'updated_at' not in p:self.state['updated_at']=self.clock()
    @synchronized
    def perform(self, action, preset=None):
        time.sleep(.15)
        fail=self.fail_next;self.fail_next=''
        if fail=='rejected':raise Problem('Simulated vehicle rejected this command.',409)
        if fail=='timeout':return {'result':'unknown','message':'Simulated timeout. Outcome is unknown; no automatic retry.'}
        if action=='lock':self.state['locked']=True
        if action=='unlock':self.state['locked']=False
        if action=='climate_start':
            self.state.update(engine_running=True,climate_running=True,climate_preset=copy.deepcopy(preset))
            self.engine_until=self.clock()+preset['duration_minutes']*60
        if action=='climate_stop':self.state.update(engine_running=False,climate_running=False);self.engine_until=0
        if action=='locate':
            # Fictional coordinates, not the user's home or actual vehicle.
            self.state.update(latitude=42.36,longitude=-71.06,location_at=self.clock())
        self.state['updated_at']=self.clock()
        return {'result':'succeeded','message':'Simulated vehicle confirmed the operation.'}

class HyundaiUSA:
    name='live'
    def __init__(self,username,password,pin):
        try:
            from hyundai_kia_connect_api import VehicleManager
        except ImportError:raise Problem('Install the optional live adapter using Enable-Live.sh in Termux.',503)
        # The upstream library can log credentials at DEBUG. Disable its loggers.
        logging.getLogger('hyundai_kia_connect_api').disabled=True
        logging.getLogger('hyundai_kia_connect_api').setLevel(logging.CRITICAL)
        self.vm=VehicleManager(region=3,brand=2,username=username,password=password,pin=pin)
        for name in list(logging.Logger.manager.loggerDict):
            if name.startswith('hyundai_kia_connect_api'):logging.getLogger(name).disabled=True
        # Every upstream request gets bounded connect/read timeouts. Never disable TLS validation.
        session=getattr(self.vm.api,'session',None)
        if session is not None:
            request=session.request
            def bounded(*args,**kwargs):
                if kwargs.get('timeout') is None:kwargs['timeout']=(10,30)
                return request(*args,**kwargs)
            session.request=bounded
        result=self.vm.login()
        if result is not True:raise Problem('This account requires an authentication step not supported by this build. Use MyHyundai; no bypass attempted.',401)
        self.selected=None
        self.state={k:None for k in FIELDS if not k.startswith(('phone.','event.'))}
        self.state['updated_at']=None
    def vehicles(self):
        return [{'id':str(k),'name':v.name or v.model or 'Hyundai','vin_last4':(v.VIN or '')[-4:]} for k,v in self.vm.vehicles.items()]
    def select(self,vid):
        if vid not in self.vm.vehicles:raise Problem('Select a vehicle returned by your account.')
        self.selected=vid;self.read(False)
    def snapshot(self):return copy.deepcopy(self.state)
    @staticmethod
    def miles(v,value,unit):
        n=finite_number(getattr(v,value,None),0);u=getattr(v,unit,None)
        if n is None or u is None:return None
        u=str(u).lower()
        if u in ['mi','mile','miles']:return n
        if u in ['km','kms','kilometers']:return n/1.609344
        return None
    def read(self,force=False):
        if not self.selected:raise Problem('Select your vehicle first.',409)
        self.vm.check_and_refresh_token()
        if force:self.vm.force_refresh_vehicle_state(self.selected)
        else:self.vm.update_vehicle_with_cached_state(self.selected)
        v=self.vm.get_vehicle(self.selected)
        mapping={'locked':'is_locked','engine_running':'engine_is_running','climate_running':'air_control_is_on',
                 'fuel_percent':'fuel_level','battery_percent':'car_battery_percentage',
                 'tire_warning':'tire_pressure_all_warning_is_on','hood_open':'hood_is_open','trunk_open':'trunk_is_open',
                 'sunroof_open':'sunroof_is_open','driver_door_open':'front_left_door_is_open','passenger_door_open':'front_right_door_is_open',
                 'rear_left_door_open':'back_left_door_is_open','rear_right_door_open':'back_right_door_is_open',
                 'driver_window_open':'front_left_window_is_open','passenger_window_open':'front_right_window_is_open',
                 'rear_left_window_open':'back_left_window_is_open','rear_right_window_open':'back_right_window_is_open',
                 'latitude':'location_latitude','longitude':'location_longitude'}
        # Rebuild from the library result. Library-level field freshness is not independently verified.
        s={k:getattr(v,a,None) for k,a in mapping.items()}
        s['range_miles']=self.miles(v,'total_driving_range','total_driving_range_unit')
        s['odometer_miles']=self.miles(v,'odometer','odometer_unit')
        t=finite_number(getattr(v,'outside_temperature',None));u=str(getattr(v,'outside_temperature_unit','')).lower()
        s['outside_f']=t if t is not None and u in ['f','°f','fahrenheit'] else t*9/5+32 if t is not None and u in ['c','°c','celsius'] else None
        stamp=getattr(v,'last_updated_at',None)
        s['updated_at']=stamp.timestamp() if isinstance(stamp,dt.datetime) and stamp.tzinfo else None
        stamp=getattr(v,'location_last_set_time',None)
        s['location_at']=stamp.timestamp() if isinstance(stamp,dt.datetime) and stamp.tzinfo else None
        # Never coerce strings such as "false" into a misleading truthy status.
        for key, kind in FIELDS.items():
            if key not in s:continue
            if kind=='boolean' and type(s[key]) is not bool:s[key]=None
            elif kind=='number':
                bounds={'fuel_percent':(0,100),'battery_percent':(0,100),'outside_f':(-100,180)}
                lo,hi=bounds.get(key,(0,None));s[key]=finite_number(s[key],lo,hi)
        s['latitude']=finite_number(s.get('latitude'),-90,90)
        s['longitude']=finite_number(s.get('longitude'),-180,180)
        self.state=s
    def perform(self,action,preset=None):
        if not self.selected:raise Problem('Select your vehicle first.',409)
        self.vm.check_and_refresh_token()
        if action in ['refresh','force_refresh','locate']:
            # Upstream cached-state update may retrieve location. No direct private endpoint assumed.
            self.read(action=='force_refresh')
            if action=='locate' and (self.state.get('latitude') is None or self.state.get('longitude') is None):
                raise Problem('Hyundai did not return a vehicle location. Nothing was fabricated.',409)
            return {'result':'succeeded','message':'Hyundai data retrieved. Check the vehicle timestamp; it may still be cached.'}
        methods={'lock':'lock','unlock':'unlock','climate_stop':'stop_climate','lights':'start_hazard_lights','horn_lights':'start_hazard_lights_and_horn'}
        if action=='climate_start':
            from hyundai_kia_connect_api.ApiImpl import ClimateRequestOptions
            opts=ClimateRequestOptions(set_temp=preset['temperature_f'],duration=preset['duration_minutes'],
                climate=True,defrost=preset['defrost'],front_left_seat=SEATS[preset['driver']],
                front_right_seat=SEATS[preset['passenger']],rear_left_seat=SEATS[preset['rear_left']],rear_right_seat=SEATS[preset['rear_right']])
            tx=self.vm.start_climate(self.selected,opts)
        else:tx=getattr(self.vm,methods[action])(self.selected)
        if not tx:return {'result':'unknown','message':'Hyundai returned no transaction ID. The outcome is unknown; no retry was sent.'}
        result=self.vm.check_action_status(self.selected,tx,synchronous=True,timeout=120)
        value=str(getattr(result,'name',getattr(result,'value',result))).upper()
        if value=='SUCCESS':return {'result':'succeeded','message':'Hyundai confirmed command completion. Refresh separately to update the cached dashboard.'}
        if value in ['FAILED','FAIL','ERROR']:return {'result':'failed','message':'Hyundai reported command failure.'}
        return {'result':'unknown','message':'Hyundai did not confirm completion within the waiting period. No retry was sent.'}
