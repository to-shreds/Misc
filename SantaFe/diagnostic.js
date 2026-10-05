/* Hyundai USA diagnostic client. No account values leave this tab except to Hyundai. */
(() => {
  'use strict';
  const BASE = 'https://api.telematics.hyundaiusa.com';
  const API = BASE + '/ac/v2/';
  const LOGIN = BASE + '/v2/ac/oauth/token';
  const LOG_KEY = 'santafe-diagnostics-v1';
  const COMMAND_KEY = 'santafe-pending-command-v1';
  const commandActions = new Set(['lock','unlock','climate_start','climate_stop','lights','horn_lights']);
  const VERSION = '0.3.3';
  const SOURCE = '82801884bdf619c5f2a35ff6bbae1693d2e1a3e8';
  const $ = id => document.getElementById(id);
  const sensitive = /password|secret|token|authorization|cookie|pin|vin|regid|registration.?id|enrollment.?id|nad.?id|package.?id|asset.?number|account.?number|billing.?account|idm.?id|hata.?tid|^guid$|^mit$|^imat$|username|user.?name|user.?id|login.?id|email|e.?mail|address|phone|mobile|contact|customer|subscriber|owner|first.?name|last.?name|full.?name|nick.?name|birth|latitude|longitude|coord|location|gps|tms.?tid|transaction.?id|^tid$|^xid$|^name$|image|photo|postal|zip.?code|^city$|^state$|street|license|licence|account.?id|person/i;
  const privateValues = new Set();
  let session = null, vehicles = [], selected = null, enrollment = null, lastStatus = null, enrollmentFailed = false;
  let busy = false, stopped = false, controller = null, helperAvailable = false, pageLeaving = false;
  let transaction = null, logs = [], evidence = [], currentTransport = '', statusRead = false, restoredCommand = null;
  const pending = new Map();
  const nativePending = new Map();
  const nativePage = location.origin === 'https://santafe.local' && location.pathname === '/index.html';
  const nativeBridge = nativePage ? window.SantaFeNative : null;
  const nativeAvailable = Boolean(nativeBridge && ['request','cancel','exportLog'].every(name => typeof nativeBridge[name] === 'function'));
  const accountAvailable = Boolean(nativeAvailable && ['loadAccount','saveAccount','forgetAccount'].every(name => typeof nativeBridge[name] === 'function'));
  let accountSaved = false;
  let accountLoadProblem = false;
  const channel = crypto.randomUUID();

  function remember(value) {
    if (typeof value === 'string' && value.length >= 3) {
      privateValues.add(value); privateValues.add(encodeURIComponent(value));
    }
  }
  function collect(value, key = '') {
    if (value && typeof value === 'object') {
      for (const [k,v] of Object.entries(value)) collect(v,k);
    } else if (sensitive.test(key)) remember(value === undefined || value === null ? '' : String(value));
  }
  function readSavedAccount() {
    if(!accountAvailable)return null;
    let stored;
    try{stored=JSON.parse(nativeBridge.loadAccount());}catch{throw new Error('The saved account could not be read. Enter your details in Account settings.');}
    if(!stored || typeof stored!=='object' || Array.isArray(stored) || typeof stored.saved!=='boolean' || stored.error)throw new Error('The saved account could not be read. Enter your details in Account settings.');
    if(!stored.saved)return null;
    if(typeof stored.username!=='string' || !stored.username.trim() || typeof stored.password!=='string' || !stored.password || typeof stored.pin!=='string' || !/^\d{4}$/.test(stored.pin))throw new Error('The saved account is incomplete. Enter your details in Account settings.');
    const account={username:stored.username.trim(),password:stored.password,pin:stored.pin};
    collect(account);
    return account;
  }
  function accountFields() {
    const account={username:$('email').value.trim(),password:$('password').value,pin:$('pin').value.trim()};
    if(accountSaved && (!account.password || !account.pin)) {
      const saved=readSavedAccount();
      if(saved && (!account.username || account.username===saved.username)) {
        account.username=account.username || saved.username;
        account.password=account.password || saved.password;account.pin=account.pin || saved.pin;
      }
    }
    if(!account.username || !account.password || !/^\d{4}$/.test(account.pin))throw new Error('Enter your MyHyundai email, password, and four-digit Bluelink service PIN in Account settings.');
    collect(account);return account;
  }
  function updateAccountSettings(collapse=false) {
    $('savedAccountControls').hidden=!accountAvailable;
    $('password').required=!accountSaved;
    $('password').placeholder=accountSaved?'Saved password. Leave blank to keep it.':'';
    $('pin').placeholder=accountSaved?'Saved PIN. Leave blank to keep it.':'4 digits';
    $('accountState').textContent=accountLoadProblem?'Check saved account':accountSaved?'Saved on this phone':session?'Connected':'Enter your account';
    $('saveAccountBtn').textContent=accountSaved?'Update saved account':'Save account';
    $('saveAccountBtn').disabled=busy || !accountAvailable || Boolean(transaction && !transaction.done);
    $('forgetAccountBtn').disabled=busy || !accountAvailable || (!accountSaved && !accountLoadProblem);
    $('rememberAccount').disabled=busy || !accountAvailable;
    $('accountStorageHelp').textContent=accountAvailable?(accountLoadProblem?'The app could not confirm the saved account state. Try Save account or Forget saved account again.':accountSaved?'Your account is saved on this phone. Open these settings to change it. Disconnect keeps it saved; Forget saved account removes it.':'Save your email, password, and PIN on this phone, or leave Remember account unchecked to use them only for this session.'):nativeAvailable?'Update the Android app to save your account on this phone. These details are currently kept only for this session.':'The Android app can remember your account. This browser page keeps credentials only for the current session.';
    if(collapse)$('accountSettings').open=false;
  }
  function storeAccount(account,keepSession=false) {
    if(!accountAvailable)throw new Error('Saved accounts require the updated Android app.');
    if(transaction && !transaction.done)throw new Error('Check the current command outcome before changing saved account settings.');
    let saved=false;try{saved=nativeBridge.saveAccount(JSON.stringify(account))===true;}catch{}
    if(!saved)throw new Error('The app could not confirm saving this account. Check Account settings before reconnecting.');
    if(session && !keepSession){resetSession();collect(account);}
    accountSaved=true;accountLoadProblem=false;$('rememberAccount').checked=true;$('email').value=account.username;
    $('password').value='';$('pin').value='';updateAccountSettings(true);
  }
  function saveAccountSettings() {
    if(busy)return;
    try{storeAccount(accountFields());updateSession();notice('Account saved on this phone. Tap Connect when you want to sign in.','success');}
    catch(e){$('accountSettings').open=true;notice(e.message,'error');}
  }
  function resetSession() {
    session=null;selected=null;vehicles=[];enrollment=null;lastStatus=null;statusRead=false;enrollmentFailed=false;transaction=null;
    for(const id of ['email','password','pin'])$(id).value='';$('vehicleSelect').replaceChildren(node('option','Sign in first'));$('vehicleSummary').replaceChildren();
    privateValues.clear();
  }
  function forgetAccountSettings() {
    if(busy || !accountAvailable)return;
    let forgotten=false;try{forgotten=nativeBridge.forgetAccount()===true;}catch{}
    if(transaction && !transaction.done)restoredCommand={action:transaction.action,at:transaction.submitted_at};
    resetSession();accountSaved=false;accountLoadProblem=!forgotten;$('rememberAccount').checked=false;$('accountSettings').open=true;
    if(!forgotten){updateSession();notice('Signed out locally, but the app could not confirm removing the saved account. Tap Forget saved account to try again.','error');return;}
    updateSession();notice('Saved account removed and signed out. Enter account details to connect again.'+(restoredCommand?' The previous command still has an unresolved outcome.':''));
  }
  function scrubString(value) {
    let s = String(value);
    for (const v of [...privateValues].sort((a,b) => b.length-a.length)) s = s.split(v).join('[REDACTED]');
    return s.replace(/(\/ac\/v2\/enrollment\/details\/)[^/?#\s"'<>]+/gi,'$1[REDACTED]')
      .replace(/\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/gi,'[REDACTED EMAIL]')
      .replace(/\b[A-HJ-NPR-Z0-9]{17}\b/gi,'[REDACTED VIN]')
      .replace(/\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+(?:\.[A-Za-z0-9_-]+)?/g,'[REDACTED TOKEN]')
      .replace(/((?:access[_-]?token|refresh[_-]?token|password|blueLinkServicePin|clientSecret)\s*[:=]\s*)[^\s,;]+/gi,'$1[REDACTED]');
  }
  function redact(value, key = '') {
    if (sensitive.test(key)) return value === null ? null : '[REDACTED]';
    if (Array.isArray(value)) return value.map(v=>redact(v));
    if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([k,v])=>[k,redact(v,k)]));
    return typeof value === 'string' ? scrubString(value) : value;
  }
  function node(tag,text,cls) { const n=document.createElement(tag); if(text!==undefined)n.textContent=text; if(cls)n.className=cls; return n; }
  function notice(text,kind='') { $('notice').textContent=scrubString(text); $('notice').className='notice '+kind; }
  function log(record) {
    const clean = redact(record); logs.push(clean); logs=logs.slice(-150);
    try { localStorage.setItem(LOG_KEY,JSON.stringify(logs)); } catch { /* Session log remains downloadable. */ }
    renderLog();
  }
  function renderLog() {
    $('logCount').textContent=String(logs.length); $('logList').replaceChildren();
    for(const record of [...logs].reverse()) {
      const d=node('details',undefined,'log-entry');
      d.append(node('summary',`${record.at || ''} | ${record.label || 'Session'} | ${record.outcome || record.status || ''}`),node('pre',JSON.stringify(record,null,2)));
      $('logList').append(d);
    }
  }
  function result(name,message,state='success') {
    const placeholder=$('results').querySelector?.('.empty-state');if(placeholder)placeholder.remove();
    const d=node('div',undefined,'result-card '+state); d.append(node('strong',name),node('p',scrubString(message))); $('results').prepend(d);
  }
  function setBusy(value) {
    busy=value;
    for(const id of ['loginBtn','runReadTestsBtn','referenceEnrollmentBtn','selectVehicleBtn','cachedBtn','refreshBtn','capabilitiesBtn','commandBtn','pollBtn','connectionTestBtn','disconnectBtn','transportMode','resolveUnknownBtn']) $(id).disabled=value;
    $('stopBtn').disabled=!value; updateSession();
  }
  function updateSession() {
    $('sessionStatus').textContent=session?(nativeAvailable?'Signed in for this app':'Signed in for this tab'):'Signed out';
    $('transportStatus').textContent=nativeAvailable?'Direct Hyundai connection':nativePage?'App connection unavailable':helperAvailable?'Browser helper ready':'Browser helper not detected';
    const unavailable=nativePage?!nativeAvailable:!helperAvailable && $('transportMode').value!=='direct';
    for(const id of ['email','password','pin','loginBtn'])$(id).disabled=busy || unavailable;
    $('loginBtn').textContent=unavailable?(nativePage?'Connection unavailable':'Open Android app'):'Connect';
    if(!busy) {
      $('vehicleSelect').disabled=!session || vehicles.length===0;
      for(const id of ['runReadTestsBtn','selectVehicleBtn']) $(id).disabled=!session;
      $('referenceEnrollmentBtn').disabled=!session || Date.now()>=session.expires || !enrollmentFailed || Boolean(transaction && !transaction.done);
      for(const id of ['cachedBtn','refreshBtn','capabilitiesBtn']) $(id).disabled=!selected;
      $('commandBtn').disabled=!selected || !statusRead || Boolean(restoredCommand) || Boolean(transaction && !transaction.done);
      $('pollBtn').disabled=!transaction?.id || transaction.done;
      $('disconnectBtn').disabled=!session;
    }
    $('unknownCommandPanel').hidden=!restoredCommand;
    if(restoredCommand)$('unknownCommandNotice').textContent='A previous '+restoredCommand.action.replaceAll('_',' ')+' command has an unknown result'+(restoredCommand.at?' (submitted '+restoredCommand.at+')':'')+'. Leaving the app cannot cancel a submitted command. Check the vehicle before allowing another command.';
    updateAccountSettings();
  }
  function setupNotice() {
    if(session || busy)return;
    if(restoredCommand)notice('A previous vehicle command has an unresolved outcome. Read tests remain available; check the vehicle before allowing another command.','warning');
    else if(nativeAvailable && accountLoadProblem)notice('Your saved account could not be read. Enter your details in Account settings to connect.','warning');
    else if(nativeAvailable)notice(accountSaved?'Account saved on this phone. Tap Connect to sign in and run the initial read tests.':'Ready. Enter your Bluelink details once in Account settings, then tap Connect. This app sends requests directly to Hyundai.','success');
    else if(nativePage)notice('The app connection is unavailable. Close and reopen the app before entering credentials. No request can be sent.','error');
    else if(helperAvailable)notice('Browser helper ready. Enter your Bluelink details and tap Connect.','success');
    else if($('transportMode').value==='direct')notice('Direct mode is for diagnostics. Hyundai blocked this browser request in testing. Use the Android app for login.','warning');
    else notice('Download and open the Android app below before entering your login details. It connects directly to Hyundai without Tampermonkey.','warning');
  }
  window.addEventListener('message',event=>{
    const msg=event.data;
    if(event.source!==window || event.origin!==location.origin || !msg || msg.type!=='SF_HELPER_RESPONSE' || msg.channel!==channel) return;
    if(msg.id==='hello') {helperAvailable=msg.version==='1';updateSession();setupNotice();return;}
    const item=pending.get(msg.id); if(!item)return;
    pending.delete(msg.id); clearTimeout(item.timer); item.cleanup();
    if(msg.error)item.reject(new Error(msg.error));else item.resolve(msg.response);
  });
  function ping() {if(!nativePage)window.postMessage({type:'SF_HELPER_REQUEST',channel,id:'hello',hello:true},location.origin);}
  if(!nativePage){ping();setTimeout(ping,350);setTimeout(ping,1400);}

  function settleNative(id,response,error) {
    if(typeof id!=='string')return;
    const item=nativePending.get(id);if(!item)return;
    nativePending.delete(id);clearTimeout(item.timer);item.cleanup();
    if(error){item.reject(new Error(typeof error==='string'?error:'The app request failed. Outcome unknown; no automatic retry.'));return;}
    if(!response || !Number.isInteger(response.status) || response.status<100 || response.status>599 || typeof response.text!=='string' || response.text.length>1048576 || !response.headers || typeof response.headers!=='object' || Array.isArray(response.headers) || Object.entries(response.headers).some(([key,value])=>typeof value!=='string' || /[\r\n]/.test(key))) {
      item.reject(new Error('The app returned an invalid response. Outcome unknown; no automatic retry.'));return;
    }
    item.resolve(response);
  }
  if(nativeAvailable)window.SantaFeAndroid=Object.freeze({onResponse:settleNative,onExportResult:(success,message)=>notice(success?'Sanitized log saved.':scrubString(message || 'Log export cancelled or unavailable.'),success?'success':'warning')});

  function sendNative(spec,signal) {
    currentTransport='native Android';
    return new Promise((resolve,reject)=>{
      const id=crypto.randomUUID();
      const finish=(message)=>{const item=nativePending.get(id);if(!item)return;nativePending.delete(id);clearTimeout(item.timer);item.cleanup();try{nativeBridge.cancel(id);}catch{}reject(new Error(message));};
      const cancel=()=>finish('Stopped locally. A submitted vehicle command may still run; it was not retried.');
      const timer=setTimeout(()=>finish('Request timed out. Outcome unknown; no automatic retry.'),46000);
      nativePending.set(id,{resolve,reject,timer,cleanup:()=>signal.removeEventListener('abort',cancel)});
      signal.addEventListener('abort',cancel,{once:true});
      if(signal.aborted){cancel();return;}
      try{nativeBridge.request(JSON.stringify({id,request:spec}));}catch{finish('The app could not send the request. Outcome unknown; no automatic retry.');}
    });
  }

  async function send(spec,signal) {
    if(nativePage){if(!nativeAvailable)throw new Error('The app connection is unavailable. No request was sent.');return sendNative(spec,signal);}
    const mode=$('transportMode').value;
    if(mode!=='direct' && !helperAvailable)throw new Error('Open the Android app to connect directly to Hyundai. Manual browser testing requires a configured helper. No request was sent.');
    if(mode==='helper' || (mode==='auto' && helperAvailable)) {
      if(!helperAvailable)throw new Error('Install the browser helper, reload this page, and check that Browser helper ready appears.');
      currentTransport='browser helper';
      return new Promise((resolve,reject)=>{
        const id=crypto.randomUUID();
        const cancel=()=>{pending.delete(id);clearTimeout(timer);window.postMessage({type:'SF_HELPER_REQUEST',channel,id,cancel:true},location.origin);reject(new Error('Stopped locally. A submitted vehicle command may still run; it was not retried.'));};
        const timer=setTimeout(()=>{pending.delete(id);signal.removeEventListener('abort',cancel);window.postMessage({type:'SF_HELPER_REQUEST',channel,id,cancel:true},location.origin);reject(new Error('Request timed out. Outcome unknown; no automatic retry.'));},46000);
        pending.set(id,{resolve,reject,timer,cleanup:()=>signal.removeEventListener('abort',cancel)});
        signal.addEventListener('abort',cancel,{once:true});
        if(signal.aborted){cancel();return;}
        window.postMessage({type:'SF_HELPER_REQUEST',channel,id,request:spec},location.origin);
      });
    }
    currentTransport='direct browser';
    const local=new AbortController();const abort=()=>local.abort();signal.addEventListener('abort',abort,{once:true});
    const timer=setTimeout(abort,45000);
    try {
      const headers={...spec.headers};delete headers.Origin;delete headers.Referer;
      const r=await fetch(spec.url,{method:spec.method,headers,body:spec.body===null?undefined:spec.body,credentials:'omit',cache:'no-store',redirect:'error',signal:local.signal});
      return {status:r.status,text:(await r.text()).slice(0,1048576),headers:Object.fromEntries(r.headers.entries())};
    } catch(e) {
      if(local.signal.aborted)throw new Error('Request stopped or timed out. Outcome unknown; no automatic retry.');
      throw new Error('The browser could not read Hyundai\'s response. CORS, network, TLS, or a redirect may be responsible. Use the Android app, or your configured browser helper; do not keep retrying your password.');
    } finally {clearTimeout(timer);signal.removeEventListener('abort',abort);}
  }
  function headers(vehicle=false,auth=true) {
    const h={'Content-Type':'application/json;charset=UTF-8','Accept':'application/json, text/plain, */*','from':'SPA','to':'ISS','language':'0','offset':String(-new Date().getTimezoneOffset()/60),'refresh':'false','encryptFlag':'false','brandIndicator':'H','client_id':'m66129Bb-em93-SPAHYN-bZ91-am4540zp19920','clientSecret':'v558o935-6nne-423i-baa8','Origin':BASE,'Referer':BASE+'/login'};
    if(session && auth){h.username=session.username;h.accessToken=session.token;h.blueLinkServicePin=session.pin;}
    if(vehicle){if(!selected)throw new Error('Select your vehicle first.');h.registrationId=selected.regid;h.gen=String(selected.vehicleGeneration || 2);h.vin=selected.vin;}
    return h;
  }
  function needSession() {
    if(!session)throw new Error('Sign in first.');
    if(Date.now()>=session.expires)throw new Error(accountSaved?'This login has expired. Tap Connect to sign in with your saved account. No command was sent.':'This login has expired. Enter your account details and tap Connect again. No command was sent.');
  }
  async function request(label,method,url,body=null,extra={},vehicle=false,auth=true,allowError=false) {
    if(stopped)throw new Error('Stopped.');if(auth)needSession();
    const spec={url,method,headers:{...headers(vehicle,auth),...extra},body:body===null?null:JSON.stringify(body)};
    const lookupMetadata=url.startsWith(API+'enrollment/details/')?{enrollment_path_format:url.includes('%40')?'encoded-at':'literal-at'}:{};
    const started=performance.now(),at=new Date().toISOString();let logged=false;
    try {
      const r=await send(spec,controller.signal);
      const responseHeaders=Object.fromEntries(Object.entries(r.headers || {}).map(([k,v])=>[k.toLowerCase(),v]));
      collect(responseHeaders);let data=null;
      if(r.text && r.text.trim()) {try{data=JSON.parse(r.text);}catch{data={non_json:true,message:'Non-JSON response body omitted. HTTP status and response headers retained.'};}}
      collect(data);
      const success=r.status>=200&&r.status<300&& !(data && (data.errorCode!==undefined || data.error));
      log({at,label,method,url,...lookupMetadata,transport:currentTransport,elapsed_ms:Math.round(performance.now()-started),request:{headers:spec.headers,body},status:r.status,outcome:success?'HTTP response received':'API rejected or unexpected response',response_headers:responseHeaders,response:data===null?'[EMPTY BODY]':data});logged=true;
      if(r.status===429)throw new Error('Hyundai rate limited this request. Stop testing and try later; no retries were sent.');
      if(!success&&!allowError) {
        const codes=[data?.errorCode,data?.errorSubCode].filter(v=>typeof v==='string'||typeof v==='number').map(v=>scrubString(v));
        const message=[data?.errorSubMessage,data?.errorMessage].find(v=>typeof v==='string'&&v.trim());
        const failure=new Error(`${label}: Hyundai returned HTTP ${r.status}${codes.length?' (API '+codes.join(', ')+')':''}.${message?' '+scrubString(message):''}${r.status===401||r.status===403?' Use MyHyundai to check your account or authentication requirements.':''}`);
        failure.requestLabel=label;throw failure;
      }
      if(success)evidence.push({test:label,at,http_status:r.status,method,path:redact(url.replace(BASE,'')),scope:'Observed API response, not proof of physical vehicle state'});
      return {data,headers:responseHeaders,status:r.status};
    } catch(e) {
      if(!logged)log({at,label,method,url,...lookupMetadata,transport:currentTransport,elapsed_ms:Math.round(performance.now()-started),request:{headers:spec.headers,body},outcome:'Connection failed or stopped',error:scrubString(e.message)});
      throw e;
    }
  }
  async function task(fn) {
    if(busy)return;stopped=false;controller=new AbortController();setBusy(true);
    try {await fn();}catch(e){notice(e.message,'error');result(e.requestLabel?e.requestLabel+' failed':'Test stopped',e.message,'error');}
    finally{controller=null;setBusy(false);if(pageLeaving){privateValues.clear();pageLeaving=false;}}
  }
  function enrollmentUrl(literalAt=true) {
    const email=encodeURIComponent(session.username);
    // Only the @ representation varies. Other delimiters stay encoded.
    return API+'enrollment/details/'+(literalAt?email.replace(/%40/g,'@'):email);
  }
  function applyEnrollment(data) {
    if(!Array.isArray(data?.enrolledVehicleDetails))throw new Error('The enrollment response did not contain the expected vehicle list. The response is logged; no features were assumed.');
    enrollment=data;vehicles=data.enrolledVehicleDetails.map(v=>v?.vehicleDetails).filter(v=>v&&v.regid&&v.vin);
    const previous=selected?.regid;
    $('vehicleSelect').replaceChildren(node('option','Select a vehicle'));
    $('vehicleSelect').firstChild.value='';
    vehicles.forEach((v,i)=>{const opt=node('option',`${v.nickName || v.modelName || v.modelCode || 'Hyundai'} | VIN ending ${String(v.vin).slice(-4)}${v.enrollmentStatus==='CANCELLED'?' | cancelled':''}`);opt.value=String(i);$('vehicleSelect').append(opt);});
    selected=vehicles.find(v=>v.regid===previous)||null;
    if(selected)$('vehicleSelect').value=String(vehicles.indexOf(selected));
    else if(vehicles.length===1&&vehicles[0].enrollmentStatus!=='CANCELLED'){selected=vehicles[0];$('vehicleSelect').value='0';}
    result('Vehicle enrollment',`${vehicles.length} vehicle record(s) returned. ${selected?'Vehicle selected.':'Choose the Santa Fe before running vehicle tests.'}`);
    updateSession();return selected;
  }
  async function getEnrollment() {
    enrollmentFailed=false;
    try {
      const r=await request('Vehicle enrollment','GET',enrollmentUrl(Boolean(session?.enrollmentLiteralAt)));
      return applyEnrollment(r.data);
    } catch(e) {
      enrollmentFailed=true;statusRead=false;
      if(session && Date.now()<session.expires && !stopped) e.message+=' The login step succeeded. Tap Test alternate lookup to compare the email format, or export the log.';
      throw e;
    }
  }
  async function testAlternateEnrollment() {
    needSession();
    if(!enrollmentFailed || (transaction&&!transaction.done))throw new Error('This lookup test is available after a vehicle-list failure, while no command is awaiting an outcome.');
    statusRead=false;
    const alternateLiteralAt=!session.enrollmentLiteralAt;
    const r=await request('Vehicle enrollment (alternate URL test)','GET',enrollmentUrl(alternateLiteralAt));
    applyEnrollment(r.data);
    session.enrollmentLiteralAt=alternateLiteralAt;enrollmentFailed=false;
    if(selected){await cached();capabilities();}
    notice('The alternate vehicle lookup returned a vehicle list. This session will use that format. '+(selected?'Read tests finished; check the vehicle timestamp and export the log.':'Choose your vehicle and export the log.'),'success');
  }
  function showStatus(data) {
    const s=data?.vehicleStatus;if(!s||typeof s!=='object')throw new Error('Hyundai returned no vehicleStatus object. This test is not confirmed.');
    lastStatus=s;statusRead=true;
    const rows=[['Vehicle timestamp',s.dateTime ?? 'Unknown'],['Door lock',s.doorLock===true?'Locked':s.doorLock===false?'Unlocked':s.doorLock??'Unknown'],['Engine running',s.engine??'Unknown'],['Climate on',s.airCtrlOn??'Unknown'],['Fuel',s.fuelLevel===undefined?'Unknown':String(s.fuelLevel)+'%'],['Range',s.dte?.value===undefined?'Unknown':String(s.dte.value)+' (API unit '+String(s.dte.unit??'unknown')+')'],['Odometer',s.odometer??'Unknown'],['12V battery',s.battery?.batSoc??'Unknown']];
    const table=node('table');const tb=node('tbody');for(const [label,value]of rows){const tr=node('tr');tr.append(node('th',label),node('td',String(value)));tb.append(tr);}table.append(tb);
    $('vehicleSummary').replaceChildren(node('p','Returned vehicle data may be cached. Its vehicle timestamp, rather than this test time, controls freshness.'),table);
  }
  async function cached(refresh=false) {
    const previousTimestamp=lastStatus?.dateTime;
    const r=await request(refresh?'Requested fresh vehicle status':'Cached vehicle status','GET',API+'rcs/rvs/vehicleStatus',null,{'refresh':refresh?'true':'false'},true);
    showStatus(r.data);
    const unchanged=refresh && previousTimestamp!==undefined && previousTimestamp===r.data.vehicleStatus.dateTime;
    result(refresh?'Refresh request':'Cached vehicle status',unchanged?'Hyundai returned the same vehicle timestamp. A new vehicle observation is not confirmed.':'Status data returned. Check the vehicle timestamp; a refresh request does not guarantee a new observation.',unchanged?'warning':'success');
  }
  function capabilities() {
    if(!selected)throw new Error('Select a vehicle first.');
    const paths=[];const walk=(v,p)=>{if(v&&typeof v==='object'){for(const [k,x]of Object.entries(v))walk(x,p?p+'.'+k:k);}else if(/capab|support|avail|feature|remote|service|seat|heat|vent|steer|window|generation|evStatus|enrollmentStatus/i.test(p))paths.push({field:p,value:redact(v,p.split('.').pop())});};
    walk(selected,'vehicleDetails');if(lastStatus)walk(lastStatus,'vehicleStatus');
    log({at:new Date().toISOString(),label:'Reported capability fields',outcome:'Inspected returned data',fields:paths,interpretation:'Reported fields are clues. Only an observed successful request confirms API access; only completed transaction status or physical observation confirms the command outcome.'});
    result('Capability inspection',`${paths.length} reported fields logged. Trim equipment and returned fields do not prove remote command support.`);
  }
  async function login() {
    if(nativePage?!nativeAvailable:$('transportMode').value!=='direct' && !helperAvailable){$('password').value='';$('pin').value='';throw new Error(nativePage?'The app connection is unavailable. No login request was sent.':'Open the Android app before signing in. Manual browser testing requires a configured helper. No login request was sent.');}
    if(transaction&&!transaction.done)throw new Error('A command is awaiting confirmation of its outcome. Check its result before starting another session.');
    session=null;selected=null;vehicles=[];enrollment=null;lastStatus=null;statusRead=false;enrollmentFailed=false;evidence=[];
    const account=accountFields(),{username,password,pin}=account;
    let r;try{r=await request('Login','POST',LOGIN,{username,password}, {}, false, false);}finally{$('password').value='';}
    if(!r.data?.access_token)throw new Error('No access token was returned. This tester does not implement extra authentication challenges. Check MyHyundai and send the sanitized log.');
    session={username,pin,token:r.data.access_token,enrollmentLiteralAt:true,expires:Date.now()+Math.max(0,Number(r.data.expires_in)||1800)*1000};
    let saveFailure='';
    if(accountAvailable && $('rememberAccount').checked)try{storeAccount(account,true);}catch(e){saveFailure=' '+e.message;result('Account settings',e.message,'warning');}
    updateAccountSettings(true);
    $('pin').value='';notice('Signed in. Reading enrolled vehicles; no vehicle command will run.','success');result('Login',nativeAvailable?'Access token received and kept only in this app session.':'Access token received and kept only in this tab.');
    await getEnrollment();if(selected){await cached();capabilities();notice('Connected. Initial read tests finished. Review the results or download the sanitized log.'+saveFailure,saveFailure?'warning':'success');}
    else notice('Connected. Select your vehicle to continue the read tests.'+saveFailure,saveFailure?'warning':'success');
  }
  async function readTests() {needSession();if(await getEnrollment()){await cached();capabilities();notice('Read tests finished. Download the sanitized log to use these results for Tasker.','success');}}
  function buildCommand(action) {
    const extra={},body={userName:session.username,vin:selected.vin};let path;
    const map={lock:'rcs/rdo/off',unlock:'rcs/rdo/on',lights:'rcs/rhl/light',horn_lights:'rcs/rhl/hnl'};
    if(map[action]){path=map[action];extra['APPCLOUD-VIN']=selected.vin;return {path,body,extra};}
    if(!['N','P','E'].includes(selected.evStatus))throw new Error('Hyundai did not identify the vehicle engine type. Climate command withheld until the returned enrollment record can be reviewed.');
    if(action==='climate_stop')return {path:selected.evStatus==='E'?'evc/fatc/stop':'rcs/rsc/stop',body:null,extra};
    if(action!=='climate_start')throw new Error('Unknown command.');
    const temperature=Number($('temperature').value),duration=Number($('duration').value);
    if(!Number.isInteger(temperature)||temperature<62||temperature>81||!Number.isInteger(duration)||duration<1||duration>10)throw new Error('Use a whole-number temperature from 62 to 81°F and duration from 1 to 10 minutes. VIN-specific limits still need confirmation.');
    const seat={drvSeatHeatState:0,astSeatHeatState:0,rlSeatHeatState:0,rrSeatHeatState:0};
    if(selected.evStatus==='E') {
      const b={airCtrl:1,airTemp:{unit:1,value:String(temperature)},defrost:$('defrost').checked,heating1:0};
      if(Number(selected.vehicleGeneration)===3){b.igniOnDuration=duration;b.seatHeaterVentInfo=seat;}
      return {path:'evc/fatc/start',body:b,extra};
    }
    return {path:'rcs/rsc/start',body:{Ims:0,airCtrl:1,airTemp:{unit:1,value:temperature},defrost:$('defrost').checked,heating1:0,igniOnDuration:duration,seatHeaterVentInfo:seat,username:session.username,vin:selected.regid},extra};
  }
  async function command() {
    needSession();if(!selected||!statusRead)throw new Error('Select the vehicle and retrieve its status before testing a command.');
    if(restoredCommand)throw new Error('A previous command outcome is unresolved. Check the vehicle and acknowledge the warning before sending another command.');
    if(selected.enrollmentStatus==='CANCELLED')throw new Error('This vehicle enrollment is cancelled.');
    if(transaction&&!transaction.done)throw new Error('The previous command outcome is unresolved. Check it before submitting another command.');
    const action=$('commandSelect').value;
    if(!$('confirmCommand').checked)throw new Error('Confirm that you intend to send this individual command to your real vehicle.');
    if(action==='climate_start'&&!$('outdoor').checked)throw new Error('Confirm the vehicle is parked outdoors before starting climate.');
    const spec=buildCommand(action);$('confirmCommand').checked=false;
    if(!window.confirm(`Send ${action.replaceAll('_',' ')} to ${selected.nickName||selected.modelCode||'the selected vehicle'}, VIN ending ${String(selected.vin).slice(-4)}?`))return;
    const submittedAt=new Date().toISOString(),marker=JSON.stringify({action,at:submittedAt});
    try{localStorage.setItem(COMMAND_KEY,marker);if(localStorage.getItem(COMMAND_KEY)!==marker)throw new Error();}catch{throw new Error('The app could not save the pending-command guard. No command was sent. Enable local storage before testing controls.');}
    log({at:submittedAt,label:'Command: '+action,outcome:'Submission starting; outcome unresolved',request:{method:'POST',path:'/ac/v2/'+spec.path},interpretation:'This record is saved before transmission. It does not confirm acceptance or completion. No automatic retry.'});
    transaction={id:null,action,vehicle:selected,done:false,submitted_at:submittedAt,service:action==='lights'?'LIGHTS_ONLY':action==='horn_lights'?'HORN_AND_LIGHTS':'REMOTE_POLL'};
    let r;
    try{r=await request('Command: '+action,'POST',API+spec.path,spec.body,spec.extra,true);}catch(e){
      transaction.uncertain=true;
      notice('Command outcome is unresolved. It may have been sent. No retry will occur. Sign out only after checking the vehicle.','warning');throw e;
    }
    transaction.id=r.headers.tmstid||r.headers.transactionid||r.headers.xid||null;remember(transaction.id);
    if(!transaction.id){result('Command submitted','No transaction ID was readable. The physical outcome is unknown. Check the vehicle; no retry was sent.','warning');notice('Command outcome unknown. No transaction ID was returned or exposed.','warning');return;}
    result('Command submitted','Hyundai accepted the request and returned a transaction ID. This does not confirm the car performed it.','warning');
    notice('Use Check command result to ask Hyundai whether it completed. No command is automatically retried.','warning');
  }
  async function poll() {
    if(!transaction?.id)throw new Error('There is no readable transaction ID. Check the vehicle physically.');
    if(transaction.vehicle.regid!==selected?.regid || transaction.vehicle.vin!==selected?.vin)throw new Error('Return to the vehicle that received the command before checking the result.');
    const r=await request('Command result: '+transaction.action,'GET',API+'rmt/getRunningStatus',null,{tid:transaction.id,login_id:session.username,service_type:transaction.service},true);
    const state=r.data?.status;
    if(state==='SUCCESS'){transaction.done=true;clearCommandMarker();result('Command confirmed','Hyundai reported SUCCESS. Refresh status separately or observe the vehicle to confirm its current state.');notice('Hyundai confirmed command completion.','success');evidence.push({test:transaction.action,at:new Date().toISOString(),scope:'Hyundai transaction reported SUCCESS; current physical state not independently observed'});}
    else if(state==='ERROR'){transaction.done=true;clearCommandMarker();result('Command failed','Hyundai reported ERROR. No retry was sent.','error');}
    else {result('Command pending or unknown',`Hyundai returned ${state || 'an empty/unknown result'}. No new command was sent.`,'warning');}
  }
  function disconnect() {
    if(transaction&&!transaction.done&&!window.confirm('A command outcome is unresolved. Signing out cannot cancel it. Have you checked the vehicle and want to end this session?'))return;
    if(transaction&&!transaction.done){clearCommandMarker();log({at:new Date().toISOString(),label:'Unresolved command acknowledged',outcome:'User confirmed checking the vehicle before signing out',interpretation:'The API outcome remains unknown. This is a local acknowledgement, not command completion.'});}
    resetSession();
    if(accountSaved)try{const saved=readSavedAccount();if(saved)$('email').value=saved.username;else accountSaved=false;}catch{accountLoadProblem=true;}
    if(!accountSaved)$('accountSettings').open=true;
    notice(accountSaved?'Signed out. Your saved account remains on this phone. Tap Connect to sign in again.':nativeAvailable?'Signed out locally. Account values were removed from this app session; sanitized logs remain.':'Signed out locally. Account values were removed from this tab; sanitized logs remain.');updateSession();
  }
  function exportData() {return {app:'Santa Fe API Lab',version:VERSION,exported_at:new Date().toISOString(),source_commit:SOURCE,privacy:'Credentials, tokens, vehicle identifiers, contact details and location values removed. Review any service error text before sharing.',evidence:redact(evidence),requests:redact(logs)};}
  function report() {return JSON.stringify(exportData(),null,2);}
  function clearCommandMarker() {try{localStorage.removeItem(COMMAND_KEY);return localStorage.getItem(COMMAND_KEY)===null;}catch{return false;}}
  $('loginForm').addEventListener('submit',e=>{e.preventDefault();task(login);});
  $('loginBtn').addEventListener('click',()=>{if(!accountSaved)$('accountSettings').open=true;});
  $('runReadTestsBtn').addEventListener('click',()=>task(readTests));
  $('referenceEnrollmentBtn').addEventListener('click',()=>task(testAlternateEnrollment));
  $('selectVehicleBtn').addEventListener('click',()=>task(async()=>{if(transaction&&!transaction.done)throw new Error('Check the current command outcome before switching vehicles.');const v=vehicles[Number($('vehicleSelect').value)];if($('vehicleSelect').value===''||!v)throw new Error('Choose a vehicle.');selected=v;statusRead=false;lastStatus=null;await cached();capabilities();}));
  $('cachedBtn').addEventListener('click',()=>task(()=>cached()));
  $('refreshBtn').addEventListener('click',()=>{if(window.confirm('Request a fresh status from Hyundai? This can wake the vehicle and use a remote request.'))task(()=>cached(true));});
  $('capabilitiesBtn').addEventListener('click',()=>task(async()=>capabilities()));
  $('commandBtn').addEventListener('click',()=>task(command));$('pollBtn').addEventListener('click',()=>task(poll));
  $('disconnectBtn').addEventListener('click',disconnect);
  $('saveAccountBtn').addEventListener('click',saveAccountSettings);
  $('forgetAccountBtn').addEventListener('click',forgetAccountSettings);
  $('resolveUnknownBtn').addEventListener('click',()=>{
    if(busy || !restoredCommand)return;
    if(!window.confirm('Have you physically checked the vehicle and want to allow a new command? The previous API outcome remains unknown, and it will not be retried automatically.'))return;
    if(!clearCommandMarker()){notice('The saved pending-command guard could not be cleared. No new command is allowed.','error');return;}
    restoredCommand=null;log({at:new Date().toISOString(),label:'Interrupted command acknowledged',outcome:'User confirmed physically checking the vehicle',interpretation:'The previous API outcome remains unknown. This acknowledgement allows deliberate new commands; it does not confirm completion.'});updateSession();notice('Acknowledged. No command was sent or retried.','warning');
  });
  $('connectionTestBtn').addEventListener('click',()=>{ping();if(nativePage?!nativeAvailable:!helperAvailable && $('transportMode').value!=='direct'){setupNotice();return;}task(async()=>{const r=await request('Connection check (no login)','GET',LOGIN,null,{},false,false,true);result('Connection response',`Hyundai returned HTTP ${r.status}. This checks whether a response is readable, not whether your credentials work.`);notice('Connection check logged.');});});
  $('stopBtn').addEventListener('click',()=>{stopped=true;controller?.abort();notice('Stopped locally. A submitted remote command cannot be recalled; no retry will run.','warning');});
  $('downloadLogBtn').addEventListener('click',()=>{if(nativePage){try{if(!nativeAvailable || nativeBridge.exportLog(JSON.stringify(exportData()))!==true)throw new Error();notice('Choose where to save the sanitized log.');}catch{notice('Log export unavailable. Close and reopen the app, then try again.','warning');}return;}const url=URL.createObjectURL(new Blob([report()],{type:'application/json'}));const a=node('a');a.href=url;a.download='SantaFe-test-log-'+new Date().toISOString().replaceAll(':','-')+'.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);});
  $('copyLogBtn').addEventListener('click',async()=>{if(nativePage){notice('Use Export log to save the sanitized log.','warning');return;}try{await navigator.clipboard.writeText(report());notice('Sanitized log copied.');}catch{notice('Clipboard unavailable. Use Download log instead.','warning');}});
  $('clearLogBtn').addEventListener('click',()=>{if(!window.confirm('Delete the saved sanitized test log?'))return;logs=[];evidence=[];try{localStorage.removeItem(LOG_KEY);}catch{}renderLog();notice('Saved test log cleared.');});
  $('transportMode').addEventListener('change',()=>{updateSession();setupNotice();});
  try{const saved=JSON.parse(localStorage.getItem(LOG_KEY)||'[]');if(Array.isArray(saved)){collect(saved);logs=saved.slice(-150).map(v=>redact(v));localStorage.setItem(LOG_KEY,JSON.stringify(logs));}}catch{}
  try{const raw=localStorage.getItem(COMMAND_KEY);if(raw!==null){let marker;try{marker=JSON.parse(raw);}catch{}restoredCommand={action:commandActions.has(marker?.action)?marker.action:'vehicle',at:typeof marker?.at==='string' && /^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d{3}Z$/.test(marker.at)?marker.at:null};}}catch{ /* Commands independently require a writable persistent guard. */ }
  window.addEventListener('pagehide',()=>{pageLeaving=true;session=null;selected=null;vehicles=[];enrollment=null;lastStatus=null;for(const id of ['email','password','pin'])$(id).value='';controller?.abort();if(!busy){privateValues.clear();pageLeaving=false;}});
  if(nativePage){$('connectionSetup').hidden=true;$('appDownload').hidden=true;$('copyLogBtn').hidden=true;$('introCopy').textContent='Connect directly to Bluelink, inspect your vehicle, and keep a sanitized test log. Start with read tests, then try individual controls when you are ready.';$('privacyNote').textContent=accountAvailable?'Saved account details are encrypted on this phone and sent directly to Hyundai. Disconnect ends the current session; Forget saved account removes stored details. Logs remove passwords, PINs, tokens, and vehicle identifiers.':'Credentials are kept only for this app session and sent directly to Hyundai. Saved logs remove passwords, PINs, tokens, and vehicle identifiers.';$('logHelp').textContent='Saved locally in this app. Exporting the sanitized log and switching apps keep your current session available while the app remains open. Review exported content before sharing.';$('downloadLogBtn').textContent='Export log';}
  if(accountAvailable)try{const saved=readSavedAccount();if(saved){accountSaved=true;$('email').value=saved.username;updateAccountSettings(true);}}catch{accountLoadProblem=true;}
  renderLog();setBusy(false);setupNotice();
})();
