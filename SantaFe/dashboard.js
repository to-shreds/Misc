/* Santa Fe Control 1.0. Local bundled dashboard. Only the native HTTPS bridge talks to Hyundai. */
(() => {
  'use strict';
  const base='https://api.telematics.hyundaiusa.com', api=base+'/ac/v2/', loginUrl=base+'/v2/ac/oauth/token';
  const $=id=>document.getElementById(id);
  const native=window.SantaFeNative;
  let session=null,vehicle=null,stored=null,lastStatus=null,busy=false,queuedAction=null,command=null,externalAction=null;
  const pending=new Map();
  const message=(s)=>{$('notice').textContent=String(s||'').slice(0,240);};
  function setBusy(b,s='') {
    busy=b;$('busyLabel').textContent=b?s||'Working…':'';
    for(const n of document.querySelectorAll('.control,[data-preset],#refresh'))n.disabled=b;
  }
  function safe(value,fallback='Unknown'){return value===undefined||value===null||value===''?fallback:String(value);}
  function prettyTimestamp(stamp){
    if(!stamp)return 'Vehicle timestamp unavailable';
    let d;
    if(/^\d{14}$/.test(String(stamp))){const s=String(stamp);d=new Date(Date.UTC(+s.slice(0,4),+s.slice(4,6)-1,+s.slice(6,8),+s.slice(8,10),+s.slice(10,12),+s.slice(12,14)));}
    else d=new Date(stamp);
    return !isNaN(d?.getTime())?'Observed '+d.toLocaleString():'Observed '+String(stamp).slice(0,28);
  }
  function renderStatus(s,source='Hyundai') {
    if(!s||typeof s!=='object')return;
    lastStatus=s;
    const doors=s.doorLock===true?'Locked':s.doorLock===false?'Unlocked':'Unknown';
    const engine=s.engine===true?'On':s.engine===false?'Off':'Unknown';
    $('doors').textContent=doors;$('engine').textContent=engine;
    $('fuel').textContent=Number.isFinite(+s.fuelLevel)&&s.fuelLevel!==null&&s.fuelLevel!==undefined?Number(s.fuelLevel)+'%':'Unknown';
    $('miles').textContent=Number.isFinite(+s.odometer)&&s.odometer!==null&&s.odometer!==undefined?Math.round(+s.odometer).toLocaleString()+' mi':'Unknown';
    $('updated').textContent=prettyTimestamp(s.dateTime);
    $('statusLabel').textContent=source;
    $('age').textContent='Vehicle observation, not a live sensor feed. Refresh does not guarantee new data.';
    try{native.reportStatus(JSON.stringify({locked:s.doorLock,engine_running:s.engine,fuel_percent:s.fuelLevel,odometer:s.odometer,updated_at:s.dateTime,source:'hyundai_direct'}));}catch{}
  }
  function request(spec) {
    return new Promise((resolve,reject)=>{
      if(!native||typeof native.request!=='function'){reject(new Error('Android connection unavailable.'));return;}
      const id=crypto.randomUUID();
      const timer=setTimeout(()=>{pending.delete(id);try{native.cancel(id);}catch{}reject(new Error('Request timed out. No command was retried.'));},46000);
      pending.set(id,{resolve,reject,timer});
      try{native.request(JSON.stringify({id,request:spec}));}
      catch(e){clearTimeout(timer);pending.delete(id);reject(new Error('Android could not submit the request.'));}
    });
  }
  window.SantaFeAndroid=Object.freeze({onResponse(id,r,error) {
    const p=pending.get(id);if(!p)return;pending.delete(id);clearTimeout(p.timer);
    if(error){p.reject(new Error(String(error)));return;}
    if(!r||!Number.isInteger(r.status)||typeof r.text!=='string'||!r.headers){p.reject(new Error('Invalid Hyundai response.'));return;}
    p.resolve(r);
  }});
  function headers(includeVehicle=false,auth=true,extra={}) {
    const h={'Content-Type':'application/json;charset=UTF-8','Accept':'application/json, text/plain, */*',
      'from':'SPA','to':'ISS','language':'0','offset':String(-new Date().getTimezoneOffset()/60),
      'refresh':'false','encryptFlag':'false','brandIndicator':'H',
      'client_id':'m66129Bb-em93-SPAHYN-bZ91-am4540zp19920',
      'clientSecret':'v558o935-6nne-423i-baa8','Origin':base,'Referer':base+'/login'};
    if(auth){if(!session||Date.now()>=session.expires)throw new Error('Login expired. Reopen the dashboard to reconnect.');h.username=session.username;h.accessToken=session.token;h.blueLinkServicePin=session.pin;}
    if(includeVehicle){if(!vehicle)throw new Error('Choose a vehicle in diagnostics.');h.registrationId=vehicle.regid;h.gen=String(vehicle.vehicleGeneration||2);h.vin=vehicle.vin;}
    return {...h,...extra};
  }
  async function call(method,path,body=null,vehicleHeaders=false,auth=true,extra={}) {
    const r=await request({url:path.startsWith('https://')?path:api+path,method,headers:headers(vehicleHeaders,auth,extra),body:body===null?null:JSON.stringify(body)});
    let data={};if(r.text.trim()){try{data=JSON.parse(r.text);}catch{throw new Error('Hyundai returned an unreadable response.');}}
    if(r.status===429)throw new Error('Hyundai rate limit reached. Do not retry.');
    if(r.status<200||r.status>=300||data?.errorCode!==undefined||data?.error)throw new Error('Hyundai rejected the '+(method==='POST'?'command':'request')+' (HTTP '+r.status+').');
    return {data,headers:Object.fromEntries(Object.entries(r.headers).map(([k,v])=>[k.toLowerCase(),v]))};
  }
  async function connect() {
    if(busy)return;setBusy(true,'Connecting');
    try{
      const a=JSON.parse(native.loadAccount());
      if(!a?.saved||!a.username||!a.password){$('statusLabel').textContent='Setup required';message('Open Settings → Account & diagnostics and save your Hyundai account.');return;}
      stored={username:a.username,pin:a.pin||''};
      const r=await call('POST',loginUrl,{username:a.username,password:a.password},false,false);
      if(!r.data?.access_token)throw new Error('Hyundai did not return a login token.');
      session={username:a.username,pin:a.pin||'',token:r.data.access_token,expires:Date.now()+Math.max(0,+r.data.expires_in||1800)*1000};
      const url=api+'enrollment/details/'+encodeURIComponent(a.username).replace(/%40/g,'@');
      const result=await call('GET',url);
      const cars=result.data?.enrolledVehicleDetails?.map(x=>x?.vehicleDetails).filter(x=>x&&x.regid&&x.vin&&x.enrollmentStatus!=='CANCELLED')||[];
      if(cars.length!==1){message('Vehicle selection is needed. Open Account & diagnostics.');$('statusLabel').textContent='Select vehicle';return;}
      vehicle=cars[0];
      await readStatus();
      if(externalAction){const action=externalAction;externalAction=null;triggerExternal(action);}
    }catch(e){message(e.message||'Could not connect.');$('statusLabel').textContent='Unavailable';}
    finally{setBusy(false);}
  }
  async function readStatus(){
    if(!session||!vehicle)throw new Error('Connect to Hyundai first.');
    const r=await call('GET','rcs/rvs/vehicleStatus',null,true);
    if(!r.data?.vehicleStatus||typeof r.data.vehicleStatus!=='object')throw new Error('Hyundai did not return vehicle status.');
    renderStatus(r.data.vehicleStatus);
    message('Status loaded. The observation time may be older than the HTTP response.');
  }
  async function refresh(){
    if(busy)return;
    if(!session||Date.now()>=session.expires){await connect();return;}
    setBusy(true,'Refreshing');
    try{await readStatus();}catch(e){message(e.message);}
    finally{setBusy(false);}
  }
  function commandSpec(action){
    const body={userName:session.username,vin:vehicle.vin};
    if(action==='lock'||action==='unlock')return {path:action==='lock'?'rcs/rdo/off':'rcs/rdo/on',body,extra:{'APPCLOUD-VIN':vehicle.vin}};
    if(action==='stop')return {path:vehicle.evStatus==='E'?'evc/fatc/stop':'rcs/rsc/stop',body:null,extra:{}};
    if(!['normal','cool','warm'].includes(action))throw new Error('Unknown action.');
    if(!['N','P','E'].includes(vehicle.evStatus))throw new Error('Vehicle type not confirmed. Remote start withheld.');
    const temperature=action==='cool'?62:action==='warm'?81:72;
    const defrost=action==='warm',duration=10;
    const seat={drvSeatHeatState:0,astSeatHeatState:0,rlSeatHeatState:0,rrSeatHeatState:0};
    if(vehicle.evStatus==='E')return {path:'evc/fatc/start',body:{airCtrl:1,airTemp:{unit:1,value:String(temperature)},defrost,heating1:0,igniOnDuration:duration,seatHeaterVentInfo:seat},extra:{}};
    return {path:'rcs/rsc/start',body:{Ims:0,airCtrl:1,airTemp:{unit:1,value:temperature},defrost,heating1:0,igniOnDuration:duration,seatHeaterVentInfo:seat,username:session.username,vin:vehicle.regid},extra:{}};
  }
  function showCommand(action) {
    if(busy)return;
    if(!session||!vehicle||!lastStatus){message('Read vehicle status before sending a command.');return;}
    if(native.isCommandBlocked&&native.isCommandBlocked()){message('An earlier command has an unknown result. Check the vehicle before trying again.');return;}
    queuedAction=action;
    const words={lock:'Lock doors',unlock:'Unlock doors',stop:'Stop remote start',cool:'Cool to 62°F',normal:'Start at 72°F',warm:'Warm to 81°F'};
    $('confirmTitle').textContent=words[action]||'Confirm command';
    $('confirmText').textContent='Send this command to the actual Santa Fe? Hyundai may take some time to complete it.';
    $('commandDialog').showModal();
  }
  async function issueCommand(action) {
    if(busy)return;
    if(!session||!vehicle||!lastStatus){message('Reconnect and check the vehicle status first.');return;}
    if((action==='cool'||action==='normal'||action==='warm')&&!$('outdoors').checked){message('Confirm the vehicle is outdoors before remote starting.');return;}
    if(!native.reserveCommand||!native.reserveCommand(action)){message('A command is already pending or unresolved. Check the vehicle first.');return;}
    let cleared=false;setBusy(true,'Sending');
    $('commandDialog').close();$('startDialog').close();message('Submitting one command to Hyundai. No retries.');
    try{
      const spec=commandSpec(action),r=await call('POST',spec.path,spec.body,true,true,spec.extra);
      const tid=r.headers.tmstid||r.headers.transactionid||r.headers.xid;
      if(!tid)throw new Error('Request accepted without transaction ID. Outcome unknown.');
      const service='REMOTE_POLL';
      for(let i=0;i<5;i++){
        if(i)await new Promise(res=>setTimeout(res,2500));
        const poll=await call('GET','rmt/getRunningStatus',null,true,true,{tid,login_id:session.username,service_type:service});
        if(poll.data.status==='SUCCESS'){native.completeCommand(true);cleared=true;message('Hyundai confirmed completion. Refresh status to see the reported state.');return;}
        if(poll.data.status==='ERROR'){native.completeCommand(false);cleared=true;message('Hyundai reported that the command failed.');return;}
      }
      throw new Error('Hyundai has not confirmed the result. Check the car before another command.');
    }catch(e){message(e.message+' No automatic retry.');}
    finally{setBusy(false);if(!cleared)message($('notice').textContent+' Command locked pending physical check.');}
  }
  function triggerExternal(action){if(['normal','cool','warm'].includes(action)){$('outdoors').checked=false;$('startDialog').showModal();}else if(['lock','unlock','stop'].includes(action))showCommand(action);}
  window.SantaFeExternal=(action)=>{if(!session||!lastStatus){externalAction=action;return;}triggerExternal(action);};
  function navigateDiagnostic(){location.assign('/index.html');}
  function resolve(){
    if(!native.isCommandBlocked||!native.isCommandBlocked()){message('There is no unresolved dashboard command.');return;}
    if(confirm('Have you physically checked the vehicle and verified its present condition? This does not establish whether the previous command succeeded.')){
      if(native.acknowledgeCommand())message('The previous command was acknowledged after your physical check.');else message('Could not clear the pending command.');
    }
  }
  document.querySelectorAll('[data-cmd]').forEach(button=>button.addEventListener('click',()=>showCommand(button.dataset.cmd)));
  $('start').addEventListener('click',()=>{if(busy)return;$('outdoors').checked=false;$('startDialog').showModal();});
  document.querySelectorAll('[data-preset]').forEach(b=>b.addEventListener('click',()=>{if(!$('outdoors').checked){message('Check the outdoors confirmation to remote start.');return;}$('startDialog').close();showCommand(b.dataset.preset);}));
  $('sendCommand').addEventListener('click',()=>{const a=queuedAction;queuedAction=null;if(a)issueCommand(a);});
  $('refresh').addEventListener('click',refresh);
  $('settings').addEventListener('click',()=>$('settingsDialog').showModal());
  $('more').addEventListener('click',()=>$('settingsDialog').showModal());
  $('diagnostics').addEventListener('click',navigateDiagnostic);
  $('taskerInfo').addEventListener('click',()=>{alert('In Tasker, choose Plugin → Santa Fe Action, Santa Fe Event, or Santa Fe State. Background status updates are cached observations, not live vehicle pushes.');});
  $('widgetInfo').addEventListener('click',()=>{alert('Long-press a blank space on the Android home screen, select Widgets, then choose one of the Santa Fe widgets.');});
  $('checkCar').addEventListener('click',resolve);
  window.addEventListener('pagehide',()=>{session=null;stored=null;vehicle=null;lastStatus=null;});
  if(native&&typeof native.loadAccount==='function')connect();else{message('The Android bridge is unavailable.');$('statusLabel').textContent='Unavailable';}
})();
