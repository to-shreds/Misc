/* Santa Fe Control 1.0. Local bundled dashboard. Only the native HTTPS bridge talks to Hyundai. */
(() => {
  'use strict';
  const base='https://api.telematics.hyundaiusa.com', api=base+'/ac/v2/', loginUrl=base+'/v2/ac/oauth/token';
  const $=id=>document.getElementById(id);
  const native=window.SantaFeNative;
  let session=null,vehicle=null,stored=null,lastStatus=null,busy=false,queuedAction=null,command=null,externalAction=null;
  const pending=new Map();
  const STATUS_CHECK_MS=3000;
  const MAX_POST_COMMAND_CHECKS=8;
  let pageActive=true;
  function observationMillis(value) {
    const stamp=String(value?.dateTime??'');
    if(/^\d{14}$/.test(stamp)){
      const date=Date.UTC(+stamp.slice(0,4),+stamp.slice(4,6)-1,+stamp.slice(6,8),
        +stamp.slice(8,10),+stamp.slice(10,12),+stamp.slice(12,14));
      return Number.isFinite(date)?date:null;
    }
    const date=Date.parse(stamp);
    return Number.isFinite(date)?date:null;
  }
  function actionAppearsInStatus(action,status,previousObservation) {
    const timestamp=observationMillis(status);
    if(timestamp===null||previousObservation===null||timestamp<=previousObservation)return false;
    if(action==='lock')return status.doorLock===true;
    if(action==='unlock')return status.doorLock===false;
    if(action==='normal'||action==='cool'||action==='warm')
      return status.airCtrlOn===true || status.engine===true;
    if(action==='stop')
      return status.airCtrlOn===false || (status.airCtrlOn==null&&status.engine===false);
    return false;
  }
  function observedDescription(action) {
    if(action==='lock')return 'doors locked';
    if(action==='unlock')return 'doors unlocked';
    if(action==='stop')return 'remote climate off';
    return 'remote climate on';
  }
  function progressFromStatus(action,observationBefore,status) {
    const reflected=actionAppearsInStatus(action,status,observationBefore);
    $('statusLabel').textContent=reflected?'Updated':'Syncing';
    $('age').textContent=reflected
      ? 'Hyundai now reports '+observedDescription(action)+'.'
      : 'Waiting for a newer vehicle observation. Hyundai may still return cached data.';
    return reflected;
  }
  const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
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
    const oldObservation=observationMillis(lastStatus), incoming=observationMillis(s);
    if(oldObservation!==null&&incoming!==null&&incoming<oldObservation)return;
    lastStatus=s;
    const doors=s.doorLock===true?'Locked':s.doorLock===false?'Unlocked':'Unknown';
    const engine=s.engine===true?'On':s.engine===false?'Off':'Unknown';
    $('doors').textContent=doors;$('engine').textContent=engine;
    $('fuel').textContent=Number.isFinite(+s.fuelLevel)&&s.fuelLevel!==null&&s.fuelLevel!==undefined?Number(s.fuelLevel)+'%':'Unknown';
    $('miles').textContent=Number.isFinite(+s.odometer)&&s.odometer!==null&&s.odometer!==undefined?Math.round(+s.odometer).toLocaleString()+' mi':'Unknown';
    $('updated').textContent=prettyTimestamp(s.dateTime);
    $('statusLabel').textContent=source;
    $('age').textContent=(s.airCtrlOn===true?'Remote climate on. ':s.airCtrlOn===false?'Remote climate off. ':'')+
      'Hyundai status may be cached.';
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
  async function readStatus({quiet=false,fresh=false}={}){
    if(!session||!vehicle)throw new Error('Connect to Hyundai first.');
    // Cached status is safe to check repeatedly. A forced wake is allowed only once
    // later in the bounded follow-up, never at every three-second interval.
    const r=await call('GET','rcs/rvs/vehicleStatus',null,true,true,fresh?{'refresh':'true'}:{});
    if(!r.data?.vehicleStatus||typeof r.data.vehicleStatus!=='object')throw new Error('Hyundai did not return vehicle status.');
    renderStatus(r.data.vehicleStatus);
    if(!quiet)message('Status loaded. The observation time may be older than the HTTP response.');
    return lastStatus;
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
  const OLD_GUARD='santafe-pending-command-v1';
  function legacyPending(){try{return localStorage.getItem(OLD_GUARD)!==null;}catch{return true;}}
  function showCommand(action) {
    if(busy)return;
    if(!session||!vehicle||!lastStatus){message('Read vehicle status before sending a command.');return;}
    if(legacyPending()||(native.isCommandBlocked&&native.isCommandBlocked())){message('An earlier command has an unknown result. Check the vehicle before trying again.');return;}
    queuedAction=action;
    const words={lock:'Lock doors',unlock:'Unlock doors',stop:'Stop remote start',cool:'Cool to 62°F',normal:'Start at 72°F',warm:'Warm to 81°F'};
    $('confirmTitle').textContent=words[action]||'Confirm command';
    $('confirmText').textContent='Send this command to the actual Santa Fe? Hyundai may take some time to complete it.';
    $('commandDialog').showModal();
  }
  async function issueCommand(action) {
    if(busy)return;
    if(!session||!vehicle||!lastStatus){message('Reconnect and check the vehicle status first.');return;}
    let spec;
    try{spec=commandSpec(action);}catch(e){message(e.message||'Command cannot be prepared.');return;}
    if(legacyPending()||!native.reserveCommand||!native.reserveCommand(action)){
      message('A command is already pending or unresolved. Check the vehicle first.');return;
    }
    const before=observationMillis(lastStatus);
    let cleared=false, statusReadsStopped=false, statusErrors=0;
    setBusy(true,'Sending');
    $('commandDialog').close();$('startDialog').close();
    message('Submitting one command to Hyundai. No retries.');
    async function checkReportedStatus(fresh=false){
      if(statusReadsStopped||!pageActive)return false;
      try {
        const observed=await readStatus({quiet:true,fresh});
        statusErrors=0;
        return progressFromStatus(action,before,observed);
      }catch(e){
        statusErrors++;
        if(/rate limit|login expired/i.test(String(e.message))||statusErrors>=2)statusReadsStopped=true;
        $('statusLabel').textContent='Status pending';
        $('age').textContent='Hyundai has not returned an updated observation yet.';
        return false; // A status read failing does not undo a confirmed command.
      }
    }
    try {
      const submitted=await call('POST',spec.path,spec.body,true,true,spec.extra);
      const tid=submitted.headers.tmstid||submitted.headers.transactionid||submitted.headers.xid;
      if(!tid)throw new Error('Request accepted without transaction ID. Outcome unknown.');
      let reflected=false;
      for(let i=0;i<5;i++){
        if(i)await delay(2500);
        if(!pageActive)throw new Error('App closed during command. Check the car before retrying.');
        const poll=await call('GET','rmt/getRunningStatus',null,true,true,
          {tid,login_id:session.username,service_type:'REMOTE_POLL'});
        const state=poll.data?.status;
        if(state==='SUCCESS'||state==='ERROR'){
          if(!native.completeCommand(state==='SUCCESS'))
            throw new Error('Could not save the command result. Check the car before another command.');
          cleared=true;
        }
        if(state==='ERROR'){
          message('Hyundai reported that the command failed. No retry was sent.');
          return;
        }
        // Refresh the visible dashboard while the transaction is still pending.
        // These are read-only GET requests; no vehicle control is resent.
        reflected=(await checkReportedStatus())||reflected;
        if(state==='SUCCESS'){
          if(!reflected&&!statusReadsStopped){
            message('Hyundai confirmed the command. Checking for updated vehicle status…');
            for(let attempt=0;attempt<MAX_POST_COMMAND_CHECKS&&pageActive;attempt++){
              await delay(STATUS_CHECK_MS);
              if(!pageActive)break;
              // One forced refresh after several cached reads, never repeated.
              reflected=await checkReportedStatus(attempt===2);
              if(reflected||statusReadsStopped)break;
            }
          }
          if(reflected){
            message('Hyundai confirmed the command. Updated status: '+observedDescription(action)+'.');
          }else if(statusReadsStopped){
            message('Hyundai confirmed the command, but status updates are temporarily unavailable.');
          }else{
            $('statusLabel').textContent='Cached';
            $('age').textContent='Hyundai has not returned a newer observation. Last reported status is shown.';
            message('Hyundai confirmed the command, but the displayed status may still be old.');
          }
          return;
        }
      }
      throw new Error('Hyundai has not confirmed the command result. Check the car before another command.');
    }catch(e){
      message(e.message+' No automatic retry.');
    }finally{
      setBusy(false);
      if(!cleared)message($('notice').textContent+' Command locked pending physical check.');
    }
  }
  function triggerExternal(action){if(['normal','cool','warm'].includes(action)){$('startDialog').showModal();}else if(['lock','unlock','stop'].includes(action))showCommand(action);}
  window.SantaFeExternal=(action)=>{if(!session||!lastStatus){externalAction=action;return;}triggerExternal(action);};
  function navigateDiagnostic(){location.assign('/index.html');}
  function resolve(){
    const blockedNative=!!(native.isCommandBlocked&&native.isCommandBlocked());
    const blockedLegacy=legacyPending();
    if(!blockedNative&&!blockedLegacy){message('There is no unresolved command.');return;}
    if(confirm('Have you physically checked the vehicle and verified its present condition? This does not establish whether the previous command succeeded.')){
      if(blockedNative&&(!native.acknowledgeCommand||!native.acknowledgeCommand())){message('Could not clear the native pending command.');return;}
      if(blockedLegacy)try{localStorage.removeItem(OLD_GUARD);if(legacyPending())throw Error();}catch{message('Could not clear the older pending command.');return;}
      message('The previous command was acknowledged after your physical check. No command was sent.');
    }
  }
  document.querySelectorAll('[data-cmd]').forEach(button=>button.addEventListener('click',()=>showCommand(button.dataset.cmd)));
  $('start').addEventListener('click',()=>{if(busy)return;$('startDialog').showModal();});
  document.querySelectorAll('[data-preset]').forEach(b=>b.addEventListener('click',()=>{$('startDialog').close();showCommand(b.dataset.preset);}));
  $('sendCommand').addEventListener('click',()=>{const a=queuedAction;queuedAction=null;if(a)issueCommand(a);});
  $('refresh').addEventListener('click',refresh);
  $('settings').addEventListener('click',()=>$('settingsDialog').showModal());
  $('more').addEventListener('click',()=>$('settingsDialog').showModal());
  $('diagnostics').addEventListener('click',navigateDiagnostic);
  function monitorLabel(){
    try{$('toggleMonitor').firstChild.textContent='Background monitoring: '+(native.monitorEnabled()?'On (15 min)':'Off')+' ';}catch{}
  }
  $('toggleMonitor').addEventListener('click',()=>{
    try{
      const next=!native.monitorEnabled();
      if(!native.enableMonitor(next))throw new Error('Android rejected the monitoring change.');
      monitorLabel();message(next?'15-minute monitoring enabled. Hyundai reports may remain stale.':'Background monitoring disabled.');
    }catch(e){message(e.message||'Could not change monitoring.');}
  });
  $('settings').addEventListener('click',monitorLabel);
  $('more').addEventListener('click',monitorLabel);
  $('taskerInfo').addEventListener('click',()=>{try{if(!native.notificationPermission())native.askNotificationPermission();}catch{}alert('In Tasker, choose Plugin → Santa Fe Action, Santa Fe Event, or Santa Fe State. Command actions require notification permission and visible confirmation. Vehicle status comes from cached observations, not live Digital Key events.');});
  $('widgetInfo').addEventListener('click',()=>{alert('Long-press a blank space on the Android home screen, select Widgets, then choose one of the Santa Fe widgets.');});
  $('checkCar').addEventListener('click',resolve);
  window.addEventListener('pagehide',()=>{pageActive=false;session=null;stored=null;vehicle=null;lastStatus=null;});
  if(native&&typeof native.loadAccount==='function')connect();else{message('The Android bridge is unavailable.');$('statusLabel').textContent='Unavailable';}
})();
