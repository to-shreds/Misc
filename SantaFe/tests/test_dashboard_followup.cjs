'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const {randomUUID}=require('node:crypto');
const src=fs.readFileSync(path.join(__dirname,'../dashboard.js'),'utf8');
const html=fs.readFileSync(path.join(__dirname,'../dashboard.html'),'utf8');

class Item {
  constructor(){this.textContent='';this.disabled=false;this.listeners={};this.firstChild={textContent:''};this.dataset={};this.open=false;}
  addEventListener(name,handler){(this.listeners[name]??=[]).push(handler);}
  click(){for(const handler of (this.listeners.click||[]))handler();}
  showModal(){this.open=true;}
  close(){this.open=false;}
}
function makeHarness({initial,following,transactions=['SUCCESS'],command='lock',statusError=false}){
  const map=new Map(),presets=[],commands=[];
  for(const m of html.matchAll(/\bid="([^"]+)"/g))map.set(m[1],new Item());
  for(const value of ['cool','normal','warm']){const e=new Item();e.dataset.preset=value;presets.push(e);}
  for(const value of ['lock','unlock','stop']){const e=new Item();e.dataset.cmd=value;commands.push(e);}
  const get=id=>{assert.ok(map.has(id),'Expected dashboard element '+id);return map.get(id);};
  const requestLog=[], reported=[],completed=[];
  let guard=false,polls=[...transactions], statuses=[initial,...following], nextError=statusError;
  const win={SantaFeNative:{
    loadAccount(){return JSON.stringify({saved:true,username:'fake@example.test',password:'fixture-only',pin:'1234'});},
    request(text){
      const envelope=JSON.parse(text), req=envelope.request;requestLog.push(req);
      let data={},headers={},error=null,status=200;
      if(req.url.endsWith('/v2/ac/oauth/token')) data={access_token:'fixture-token',expires_in:1800};
      else if(req.url.includes('enrollment/details/')) data={enrolledVehicleDetails:[{vehicleDetails:{vin:'FIXTUREVIN',regid:'fixture-regid',evStatus:'P',vehicleGeneration:3,enrollmentStatus:'ACTIVE'}}]};
      else if(req.url.endsWith('rcs/rvs/vehicleStatus')){
        if(nextError && statuses.length===1){status=429;data={errorCode:'RATE_LIMIT'};nextError=false;}
        else {const next=statuses.length>1?statuses.shift():statuses[0];data={vehicleStatus:next};}
      } else if(req.url.endsWith('/rmt/getRunningStatus')) data={status:polls.length>1?polls.shift():polls[0]};
      else if(req.method==='POST'&&req.url.includes('/rcs/')) headers={tmstid:'fixture-transaction'};
      else throw new Error('Unexpected Hyundai request: '+req.url);
      queueMicrotask(()=>win.SantaFeAndroid.onResponse(envelope.id,{status,text:JSON.stringify(data),headers},error));
    },
    cancel(){},
    reportStatus(v){reported.push(JSON.parse(v));return true;},
    reserveCommand(){if(guard)return false;guard=true;return true;},
    completeCommand(success){completed.push(success);guard=false;return true;},
    isCommandBlocked(){return guard;},
    acknowledgeCommand(){guard=false;return true;},
    monitorEnabled(){return false;},
    enableMonitor(){return true;},
    notificationPermission(){return true;}
  },addEventListener(){},};
  const localStorage={getItem(){return null;},removeItem(){}};
  const document={getElementById:get,querySelectorAll(q){
    if(q==='[data-cmd]')return commands;
    if(q==='[data-preset]')return presets;
    if(q==='.control,[data-preset],#refresh')return [...commands,...presets,get('refresh')];
    return [];
  }};
  const context={window:win,document,location:{assign(){}},localStorage,crypto:{randomUUID},
    setTimeout(fn,ms){if(ms>=45000)return 0;return setImmediate(fn);},clearTimeout(){},
    console,Date,Promise,alert(){},confirm(){return true;}
  };
  vm.runInNewContext(src,context,{filename:'dashboard.js'});
  async function until(predicate){
    for(let i=0;i<1500;i++){if(predicate())return;await new Promise(resolve=>setImmediate(resolve));}
    throw new Error('Fixture did not reach expected state: '+get('notice').textContent);
  }
  return {
    async connect(){await until(()=>get('statusLabel').textContent==='Hyundai' && !get('refresh').disabled);},
    async send(action=command){
      if(['normal','cool','warm'].includes(action)){
        get('start').click();
        presets.find(e=>e.dataset.preset===action).click();
      }else commands.find(e=>e.dataset.cmd===action).click();
      assert.ok(get('commandDialog').open,'Command confirmation must open');
      get('sendCommand').click();
      await until(()=>!get('refresh').disabled);
    },
    get,requestLog,reported,completed,
    get guard(){return guard;}
  };
}
const state=(time,{locked=false,engine=false,climate=false}={})=>
  ({dateTime:time,doorLock:locked,engine,airCtrlOn:climate,fuelLevel:70,odometer:12345});

test('lock confirms once and automatically refreshes until a newer locked observation arrives',async()=>{
  const h=makeHarness({initial:state('2026-11-12T20:00:00Z'),
    following:[state('2026-11-12T20:00:00Z'),state('2026-11-12T20:00:00Z'),state('2026-11-12T20:02:00Z',{locked:true})],
    transactions:['PENDING','SUCCESS']});
  await h.connect();await h.send('lock');
  const carPosts=h.requestLog.filter(r=>r.method==='POST'&&r.url.includes('/rcs/'));
  const reads=h.requestLog.filter(r=>r.url.endsWith('/rcs/rvs/vehicleStatus'));
  assert.equal(carPosts.length,1,'No command POST retry');
  assert.ok(reads.length>=4,'Repeated read-only status checks after command');
  assert.equal(h.get('doors').textContent,'Locked');
  assert.equal(h.get('statusLabel').textContent,'Updated');
  assert.match(h.get('notice').textContent,/Hyundai confirmed.*Updated status/i);
  assert.deepEqual(h.completed,[true]);
});
test('hybrid remote start accepts reported climate-on while engine stays off',async()=>{
  const h=makeHarness({initial:state('2026-11-12T20:00:00Z'),following:[state('2026-11-12T20:01:00Z',{climate:true})],transactions:['SUCCESS'],command:'warm'});
  await h.connect();await h.send('warm');
  assert.equal(h.get('engine').textContent,'Off');
  assert.match(h.get('age').textContent,/remote climate on/i);
  assert.match(h.get('notice').textContent,/Updated status: remote climate on/i);
  assert.equal(h.requestLog.filter(r=>r.method==='POST'&&r.url.includes('/rcs/')).length,1);
});
test('stale status never pretends the car changed, even if transaction succeeded',async()=>{
  const initial=state('2026-11-12T20:00:00Z',{locked:false});
  const h=makeHarness({initial,following:[initial],transactions:['SUCCESS']});
  await h.connect();await h.send('lock');
  const reads=h.requestLog.filter(r=>r.url.endsWith('/rcs/rvs/vehicleStatus'));
  assert.ok(reads.length>=5,'Continue bounded checks after SUCCESS');
  assert.ok(reads.length<=14,'Bound repeated status requests');
  assert.equal(h.get('doors').textContent,'Unlocked');
  assert.equal(h.get('statusLabel').textContent,'Cached');
  assert.match(h.get('notice').textContent,/displayed status may still be old/i);
  assert.deepEqual(h.completed,[true]);
  assert.equal(h.requestLog.filter(r=>r.method==='POST'&&r.url.includes('/rcs/')).length,1);
  assert.equal(reads.filter(r=>r.headers.refresh==='true').length,1,'At most one forced refresh');
});
test('a read-only status failure does not convert Hyundai command SUCCESS into an unresolved guard',async()=>{
  const initial=state('2026-11-12T20:00:00Z',{locked:false});
  const h=makeHarness({initial,following:[initial],transactions:['SUCCESS'],statusError:true});
  await h.connect();await h.send('lock');
  assert.deepEqual(h.completed,[true]);
  assert.equal(h.guard,false);
  assert.match(h.get('notice').textContent,/Hyundai confirmed.*status updates are temporarily unavailable/i);
  assert.equal(h.requestLog.filter(r=>r.method==='POST'&&r.url.includes('/rcs/')).length,1);
});
