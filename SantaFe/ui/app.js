'use strict';
const $=s=>document.querySelector(s), esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const pages=['Dashboard','Controls','Climate','Automations','Simulator','Settings','Account','Diagnostics'];
let snap=null,catalog=null,editing=null,editKind='',current='',paired=false,pollBusy=false,refreshPromise=null,toastTimer;
const labels={locked:'Door locks',engine_running:'Engine running',climate_running:'Remote climate',fuel_percent:'Fuel level',range_miles:'Estimated range',odometer_miles:'Odometer',battery_percent:'12V battery report',outside_f:'Outside temperature',tire_warning:'Tire warning',hood_open:'Hood open',trunk_open:'Tailgate open',sunroof_open:'Sunroof open',driver_door_open:'Driver door open',passenger_door_open:'Passenger door open',rear_left_door_open:'Rear-left door open',rear_right_door_open:'Rear-right door open',driver_window_open:'Driver window open',passenger_window_open:'Passenger window open',rear_left_window_open:'Rear-left window open',rear_right_window_open:'Rear-right window open','phone.car_connected':'Phone connected to car','phone.home':'Phone at home','phone.wifi':'Phone on Wi-Fi','phone.charging':'Phone charging','phone.android_auto':'Android Auto connected',age_seconds:'Vehicle data age (seconds)','event.value':'Event value'};
const friendly=s=>labels[s]||String(s??'').replace(/_/g,' ').replace(/^./,c=>c.toUpperCase());
const uid=()=>crypto.randomUUID?crypto.randomUUID().replace(/-/g,''):Date.now().toString(36)+Math.random().toString(36).slice(2);
const timefmt=t=>typeof t==='number'?new Date(t*1000).toLocaleString(): 'Not reported';
const agefmt=s=>s==null?'No timestamp':s<60?Math.floor(s)+' seconds ago':s<3600?Math.floor(s/60)+' minutes ago':Math.floor(s/3600)+' hours ago';
function toast(t){$('#toast').textContent=t;$('#toast').hidden=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('#toast').hidden=true,6500)}
async function api(path,data){
 const options={cache:'no-store',headers:{'X-SF-Client':'ui'}};
 if(data!==undefined){options.method='POST';options.headers['Content-Type']='application/json';options.body=JSON.stringify(data)}
 const response=await fetch('/api/'+path,options);let out;try{out=await response.json()}catch{throw new Error('The bridge returned an unreadable response.')}
 if(!response.ok){if(response.status===401&&path!=='pair'){paired=false;renderPair()}throw new Error(out.error||'Request failed.')}
 return out;
}
const button=(text,action,id='',cls='')=>`<button type="button" class="${esc(cls)}" data-action="${esc(action)}" data-id="${esc(id)}">${esc(text)}</button>`;
const opt=(value,title,selected)=>`<option value="${esc(value)}"${String(value)===String(selected)?' selected':''}>${esc(title)}</option>`;
const field=(name,title,value,type='text',extra='')=>`<label>${esc(title)}<input name="${esc(name)}" type="${esc(type)}" value="${esc(value)}" ${extra}></label>`;
const select=(name,title,options,selected)=>`<label>${esc(title)}<select name="${esc(name)}">${options.map(x=>opt(Array.isArray(x)?x[0]:x,Array.isArray(x)?x[1]:friendly(x),selected)).join('')}</select></label>`;
const check=(name,title,value)=>`<label class="check"><input type="checkbox" name="${esc(name)}"${value?' checked':''}>${esc(title)}</label>`;
function display(k,v){
 if(v===null||v===undefined)return 'Unknown';
 if(k==='locked')return v?'Locked':'Unlocked';
 if(typeof v==='boolean')return v?'Yes':'No';
 if(k.endsWith('_percent'))return v+'%';
 if(k.endsWith('_miles'))return snap.config.settings.units==='metric'?Math.round(v*1.609344).toLocaleString()+' km':Math.round(v).toLocaleString()+' mi';
 if(k==='outside_f')return snap.config.settings.units==='metric'?Math.round((v-32)*5/9)+' °C':Math.round(v)+' °F';
 return esc(v);
}
function renderPair(){
 $('#nav').hidden=true;$('#pause').hidden=true;$('#mode').textContent='LOCAL BRIDGE';
 $('#main').innerHTML=`<section class="card pair"><div class="eyebrow">ONE-TIME SETUP</div><h1>Your car.<br>Your control center.</h1><p>Enter the owner pairing code printed by the bridge in Termux. This is not your Hyundai password or PIN.</p><form id="pairForm"><label>Bridge pairing code<input name="code" required maxlength="8" autocomplete="off" autocapitalize="characters" placeholder="8-character code"></label><button class="primary">Pair this dashboard</button></form><div class="info note">The first launch is a simulated Santa Fe. It cannot reach or control a real vehicle.</div><p class="note">Code expired? Stop the bridge with Ctrl+C and run <span class="code">sf-start</span> again. This does not erase settings.</p></section>`;
}
async function refresh(render=true){
 // Serialize reads. A save/command must not skip its post-mutation read merely
 // because a background snapshot was already in flight.
 while(refreshPromise){try{await refreshPromise}catch{}}
 pollBusy=true;
 const work=(async()=>{
  const next=await api('snapshot');
  if(!catalog)catalog=await api('catalog');
  snap=next;paired=true;chrome();if(render&&!editing)renderPage();
 })();
 refreshPromise=work;
 try{await work}finally{if(refreshPromise===work){refreshPromise=null;pollBusy=false}}
}
function chrome(){
 document.body.classList.toggle('light',snap.config.settings.theme==='light');
 $('#brandName').textContent=snap.config.settings.name;
 $('#mode').textContent=snap.mode==='simulation'?'SIMULATION':snap.live_armed?'LIVE: ARMED':'LIVE: READ ONLY';
 $('#mode').className='badge'+(snap.mode==='live'?' warn':'');$('#pause').hidden=false;$('#nav').hidden=false;
 $('#nav').innerHTML=pages.map(p=>`<button type="button" data-page="${p.toLowerCase()}" class="${current===p.toLowerCase()?'active':''}">${p}</button>`).join('');
}
function heading(title,sub='',action=''){return `<div class="sectionhead"><div><h1>${esc(title)}</h1><p>${esc(sub)}</p></div>${action}</div>`}
function confirmations(){return snap.confirmations.map(c=>`<section class="card confirm"><span class="badge warn">CONFIRMATION REQUIRED</span><h3 class="spaced">${esc(friendly(c.action))}${c.preset?' · '+esc(c.preset.name):''}</h3><p>${c.mode==='simulation'?'This changes only the simulator.':'This will send a real remote command.'} Expires ${esc(timefmt(c.expires))}.</p>${c.preset?`<p class="note">${esc(c.preset.temperature_f)} °F · ${esc(c.preset.duration_minutes)} min · driver ${esc(friendly(c.preset.driver))}</p>`:''}${c.action==='climate_start'?`<label class="check"><input type="checkbox" id="outdoors-${esc(c.id)}">I have confirmed the car is parked outdoors, not in a garage or enclosed space.</label>`:''}<div class="actions">${button('Confirm '+friendly(c.action),'confirm',c.id,'primary')}${button('Cancel','cancelConfirm',c.id)}</div></section>`).join('')}
function dashboard(){
 const s=snap.state,p=snap.config.presets.find(p=>p.id===snap.config.settings.default_preset);
 return `<section class="hero"><div><div class="eyebrow">${snap.mode==='simulation'?'YOUR PRACTICE VEHICLE':'CONNECTED THROUGH HYUNDAI'}</div><h1>Your Santa Fe.<br>Your rules.</h1><p>${snap.mode==='simulation'?'Explore controls, build routines, and test failures before the car arrives. Everything here is simulated.':'Commands travel through Hyundai. The dashboard shows the last reported state, not a continuous live feed.'}</p><span class="badge${snap.stale?' warn':''}">${snap.stale?'STALE / UNKNOWN':'LAST VEHICLE REPORT'} · ${esc(agefmt(snap.age_seconds))}</span></div><div><h3>${p?esc(p.name):'Default preset'}</h3><p>${p?esc(p.temperature_f)+' °F · '+esc(p.duration_minutes)+' min · '+esc(friendly(p.driver)):'Choose a preset in Settings.'}</p><div class="actions">${button('Get car ready','command','climate_start','primary')}${button('Lock','command','lock')}${button('Stop remote climate','command','climate_stop')}</div><p class="note spaced">${snap.busy?'A vehicle operation is in progress.':'Dashboard updates read only the local cache.'}</p></div></section>${confirmations()}<div class="tiles">${snap.config.settings.tiles.map(k=>`<article class="tile${snap.stale?' stale':''}"><small>${esc(friendly(k))}</small><strong>${display(k,k==='age_seconds'?snap.age_seconds:s[k])}</strong></article>`).join('')}</div><div class="grid"><section class="card"><div class="row"><h3>Automations</h3><span class="badge${snap.config.settings.rules_enabled?'':' warn'}">${snap.config.settings.rules_enabled?'ENABLED':'PAUSED'}</span></div><p>${snap.config.rules.filter(r=>r.enabled&&r.scope===snap.mode).length} enabled rules for ${esc(snap.mode)}. ${snap.pending_rules.length} waiting on a delay.</p>${button('Build a routine','page','automations')}${snap.pending_rules.map(r=>`<p class="note spaced">${esc(r.name)} · due ${esc(timefmt(r.due))}</p>`).join('')}</section><section class="card"><h3>Most recent activity</h3>${snap.jobs.length?`<span class="badge">${esc(snap.jobs[0].state)}</span><p class="spaced">${esc(snap.jobs[0].message)}</p>`:'<p>No commands have been sent in this session.</p>'}${button('View diagnostics','page','diagnostics')}</section></div><section class="card spaced"><h3>Recent notices</h3>${snap.notices.length?snap.notices.slice(0,4).map(n=>`<div class="log"><small>${esc(timefmt(n.at))}</small><p>${esc(n.message)}</p></div>`).join(''):'<p>Rule alerts and command results will appear here.</p>'}</section>`;
}
function controls(){
 const commands=[['lock','Lock doors'],['unlock','Unlock doors'],['climate_start','Start default climate preset'],['climate_stop','Stop remote climate'],['lights','Flash lights'],['horn_lights','Horn and lights'],['refresh','Read Hyundai cached status'],['force_refresh','Request a fresh vehicle report'],['locate','Get reported vehicle location']];
 return heading('Remote controls','Every request has a visible outcome. Accepted is not the same as completed.')+confirmations()+`<div class="grid">${commands.map(([id,name])=>`<section class="card"><h3>${esc(name)}</h3><p class="note">${['refresh','locate'].includes(id)?'May return an older Hyundai report. Subject to the cloud-read cooldown.':id==='force_refresh'?'May wake the vehicle. Limited separately from dashboard refreshes.':['climate_start','unlock','horn_lights'].includes(id)?'Requires confirmation in this dashboard.':'Availability must be verified on the actual vehicle.'}</p>${button(snap.mode==='simulation'?'Simulate':'Send request','command',id,'primary')}</section>`).join('')}</div><section class="card spaced"><h2>Detailed vehicle status</h2><p>Missing is not the same as closed, off, or healthy.</p><div class="statusgrid">${Object.keys(labels).filter(k=>!k.includes('.')&&k!=='age_seconds').map(k=>`<div class="statusline"><span>${esc(friendly(k))}</span><strong>${display(k,snap.state[k])}</strong></div>`).join('')}</div><h3 class="spaced">Reported location</h3><p class="code">${snap.state.latitude==null||snap.state.longitude==null?'Not reported':esc(snap.state.latitude)+', '+esc(snap.state.longitude)}</p><p class="note">Location report: ${esc(timefmt(snap.state.location_at))}. Simulator coordinates are fictional.</p></section>`;
}
function climate(){return heading('Climate presets','Named presets, editable without changing Tasker actions.',button('New preset','newPreset','','primary'))+`<div class="warning note">Presets are stored in Fahrenheit. Optional seat controls are API candidates until tested on your VIN. Steering-wheel heat is not guessed or enabled in this build.</div><div class="grid">${snap.config.presets.map(p=>`<section class="card"><div class="row"><h2>${esc(p.name)}</h2>${p.id===snap.config.settings.default_preset?'<span class="badge">DEFAULT</span>':''}</div><h3>${esc(p.temperature_f)} °F · ${esc(p.duration_minutes)} min</h3><p>Driver ${esc(friendly(p.driver))}<br>Passenger ${esc(friendly(p.passenger))}<br>Rear left ${esc(friendly(p.rear_left))} · rear right ${esc(friendly(p.rear_right))}<br>Defrost ${p.defrost?'on':'off'}</p><div class="actions">${button('Start','startPreset',p.id,'primary')}${button('Edit','editPreset',p.id)}${button('Duplicate','copyPreset',p.id)}${button('Delete','deletePreset',p.id,'small danger')}</div></section>`).join('')}</div>`}
function renderPreset(){
 const p=editing,seats=['off','low_cool','medium_cool','high_cool','low_heat','medium_heat','high_heat'];
 $('#main').innerHTML=heading('Edit climate preset','The preset changes settings only. Saving does not start anything.')+`<form id="presetForm" class="card"><div class="formgrid">${field('name','Preset name',p.name)}${field('temperature_f','Temperature (°F)',p.temperature_f,'number','min="62" max="82" step="1"')}${field('duration_minutes','Remote runtime (minutes)',p.duration_minutes,'number','min="1" max="10"')}${select('driver','Driver seat',seats,p.driver)}${select('passenger','Passenger seat',seats,p.passenger)}${select('rear_left','Rear-left seat',seats.filter(s=>!s.includes('cool')),p.rear_left)}${select('rear_right','Rear-right seat',seats.filter(s=>!s.includes('cool')),p.rear_right)}${check('defrost','Windshield defrost',p.defrost)}</div><div class="footeractions"><button class="primary">Save preset</button>${button('Cancel','cancelEdit')}</div></form>`;
}
function ruleSummary(r){return `When ${friendly(r.event)}${r.event==='schedule'?' at '+r.time:''}${r.delay_seconds?' · wait '+r.delay_seconds+' sec':''} → ${r.conditions.length+' condition'+(r.conditions.length===1?'':'s')} → ${r.actions.map(a=>friendly(a.type)).join(', ')}`}
function automations(){return heading('Your automations','Rules are data, not a maze of copied Tasker profiles.',button('New rule','newRule','','primary'))+`<section class="card"><div class="row"><div><h3>Automation master switch</h3><p>${snap.config.settings.rules_enabled?'Enabled rules run only in their selected mode.':'Rules are paused. Your definitions are preserved.'}</p></div>${button(snap.config.settings.rules_enabled?'Pause rules':'Enable rules','toggleRules','','primary')}</div><p class="note">Live ignition, unlock, horn, lights and climate-stop rules ask for a tap. Automatic live locking is a separate opt-in and requires fresh safe-state checks.</p></section><div class="chiprow">${button('After parking','template','parking','small')}${button('Bedtime check','template','bedtime','small')}${button('Low fuel','template','fuel','small')}${button('Cold-weather reminder','template','cold','small')}</div>${snap.config.rules.length?snap.config.rules.map(r=>`<article class="rule"><div class="row start"><div><span class="badge${r.scope==='live'?' warn':''}">${esc(r.scope)}</span><h3>${esc(r.name)}</h3></div>${button(r.enabled?'Enabled':'Disabled','toggleRule',r.id,'small')}</div><p class="summary">${esc(ruleSummary(r))}</p><div class="actions">${button('Edit','editRule',r.id)}${button('Dry run','testRule',r.id)}${button('Duplicate','copyRule',r.id)}${button('Delete','deleteRule',r.id,'small danger')}</div></article>`).join(''):'<section class="card empty"><h2>Start with one useful routine.</h2><p>Choose a template above or build from scratch. New rules start disabled.</p></section>'}<section class="card spaced"><h3>Where triggers come from</h3><p class="note">Schedules and vehicle changes run in the bridge. Tasker supplies phone events. The project includes Bluetooth, home Wi-Fi and charging profiles; NFC, alarms, calendars, Android Auto, geofences and custom variables can call the included <span class="code">SF Send Event</span> task from any native Tasker profile. These extra contexts are not auto-detected by this dashboard.</p>${button('Try a simulated event','page','simulator')}</section>`}
function newRule(){return {id:uid(),name:'New routine',enabled:false,event:'manual',scope:'simulation',match:'all',time:'08:00',start:'00:00',end:'00:00',days:[0,1,2,3,4,5,6],delay_seconds:0,cooldown_seconds:300,conditions:[],actions:[{type:'notify',text:'Santa Fe: {range_miles} miles of range.'}]}}
function renderRule(){
 const r=editing;
 const cf=catalog.fields,fields=Object.keys(cf).map(f=>[f,friendly(f)]);
 $('#main').innerHTML=heading('Build a routine','Choose a trigger, add conditions, then decide what happens.')+`<form id="ruleForm" class="card"><div class="formgrid">${field('name','Routine name',r.name)}${select('scope','Applies to',[['simulation','Simulation only'],['live','Real vehicle only']],r.scope)}${select('event','When',catalog.events,r.event)}${field('time','Schedule time (for schedule trigger)',r.time,'time')}${field('delay_seconds','Wait before acting (seconds)',r.delay_seconds,'number','min="0" max="86400"')}${field('cooldown_seconds','Minimum time between runs (seconds)',r.cooldown_seconds,'number','min="10" max="604800"')}</div><fieldset><legend>Only during</legend><div class="days">${['Mon','Tue','Wed','Thu','Fri','Sat','Sun'].map((d,i)=>`<label><input type="checkbox" name="day${i}"${r.days.includes(i)?' checked':''}>${d}</label>`).join('')}</div><div class="formgrid spaced">${field('start','From',r.start,'time')}${field('end','Until (same time means all day)',r.end,'time')}</div></fieldset><fieldset><legend>Conditions</legend>${select('match','Match',[['all','All conditions (AND)'],['any','Any condition (OR)']],r.match)}${r.conditions.map((c,i)=>`<div class="condition" data-condition="${i}">${select('cf'+i,'Field',fields.concat(c.field.startsWith('user.')?[[c.field,c.field]]:[]),c.field)}${select('co'+i,'Comparison',[['eq','Equals'],['ne','Does not equal'],['lt','Below'],['le','At or below'],['gt','Above'],['ge','At or above'],['contains','Contains'],['known','Is reported']],c.op)}${cf[c.field]==='boolean'?select('cv'+i,'Value',[['true','Yes / true'],['false','No / false']],String(c.value)):field('cv'+i,'Value',c.value,cf[c.field]==='number'?'number':'text','step="any"')}${button('Remove','removeCondition',i,'small')}</div>`).join('')}<div class="actions">${button('Add condition','addCondition')}${button('Custom variable','userCondition')}</div><p class="note spaced">Unknown or stale inputs fail the condition, including “does not equal.” Custom fields use <span class="code">user.name</span> from SF Send Event context.</p></fieldset><fieldset><legend>Then</legend>${r.actions.map((a,i)=>`<div class="ruleaction" data-ruleaction="${i}">${select('at'+i,'Action',catalog.actions,a.type)}${actionEditor(a,i)}${button('Remove','removeAction',i,'small')}</div>`).join('')}${button('Add action','addAction')}<p class="note spaced">Actions run in order. Vehicle operations are asynchronous; use one vehicle operation per rule rather than assuming a later action waits for completion.</p></fieldset>${check('enabled','Enable this routine after saving',r.enabled)}<div class="footeractions"><button class="primary">Save routine</button>${button('Cancel','cancelEdit')}</div></form>`;
}
function actionEditor(a,i){
 if(a.type==='climate_start')return select('ap'+i,'Preset',snap.config.presets.map(p=>[p.id,p.name]),a.preset_id||snap.config.settings.default_preset);
 if(a.type==='notify'||a.type==='speak')return `<label>Message<textarea name="ax${i}">${esc(a.text||'Santa Fe check')}</textarea></label>`;
 if(a.type==='run_task')return field('ak'+i,'Allowed Tasker task',a.task||'');
 if(a.type==='set_variable')return `<div>${field('av'+i,'Variable (SFUser...)',a.variable||'SFUserResult')}${field('az'+i,'Value',a.value||'')}</div>`;
 return '<p class="note">Uses the current mode and shared command safeguards.</p>';
}
function readRule(){
 const form=$('#ruleForm');if(!form)return;const fd=new FormData(form),r=editing;
 for(const k of ['name','scope','event','time','start','end','match'])r[k]=fd.get(k);
 for(const k of ['delay_seconds','cooldown_seconds'])r[k]=Number(fd.get(k));
 r.enabled=fd.has('enabled');r.days=[0,1,2,3,4,5,6].filter(i=>fd.has('day'+i));
 r.conditions=r.conditions.map((c,i)=>{const f=fd.get('cf'+i),op=fd.get('co'+i),v=fd.get('cv'+i);return {field:f,op,value:op==='known'?true:catalog.fields[f]==='boolean'?v==='true':catalog.fields[f]==='number'?Number(v):v}});
 r.actions=r.actions.map((a,i)=>{const t=fd.get('at'+i),out={type:t};if(t==='climate_start')out.preset_id=fd.get('ap'+i)||snap.config.settings.default_preset;if(['notify','speak'].includes(t))out.text=fd.get('ax'+i)||'Santa Fe check';if(t==='run_task')out.task=fd.get('ak'+i)||'';if(t==='set_variable'){out.variable=fd.get('av'+i)||'SFUserResult';out.value=fd.get('az'+i)||''}return out});
}
function simulator(){
 if(snap.mode!=='simulation')return heading('Simulator')+'<section class="card"><p>Simulation controls are unavailable in live mode. Switching back disarms all real-car controls and clears pending actions.</p>'+button('Switch to simulation','mode','simulation','primary')+'</section>';
 const states=['locked','engine_running','climate_running',...Object.keys(labels).filter(k=>k.endsWith('_open')),'tire_warning'];
 return heading('Practice without the car','Try ordinary situations, missing data, rejection, and timeout.')+`<section class="card"><h3>Scenarios</h3><div class="actions">${button('Parked but unlocked','scenario','unlocked')}${button('Window left open','scenario','window')}${button('Low fuel','scenario','fuel')}${button('Stale report','scenario','stale')}${button('Missing status','scenario','unknown')}${button('Reset vehicle','scenario','reset')}</div></section><form id="simForm" class="card"><div class="formgrid">${states.map(k=>select(k,friendly(k),[['unknown','Unknown'],['true','Yes / true'],['false','No / false']],snap.state[k]==null?'unknown':String(snap.state[k]))).join('')}${['fuel_percent','range_miles','battery_percent','outside_f'].map(k=>field(k,friendly(k),snap.state[k]??'','number','step="any"')).join('')}${select('failure','Next operation',['','rejected','timeout'],'')}</div><div class="footeractions"><button class="primary">Apply simulated state</button></div></form><form id="eventForm" class="card"><h2>Send a test trigger</h2><p>Only enabled simulation rules can respond. This never changes a real car.</p><div class="formgrid">${select('event','Trigger',catalog.events.filter(e=>e!=='schedule'),'bluetooth_disconnected')}${field('value','Optional event value','')}</div><button class="primary spaced">Send trigger</button></form>`;
}
function settings(){
 const s=snap.config.settings;
 return heading('Settings','Preferences are stored separately from the imported Tasker project.')+`<form id="settingsForm" data-revision="${snap.revision}" class="card"><div class="formgrid">${field('name','Vehicle display name',s.name)}${field('timezone','Schedule time zone',s.timezone)}${select('theme','Appearance',['dark','light'],s.theme)}${select('units','Dashboard units',[['us','Miles and Fahrenheit'],['metric','Kilometers and Celsius']],s.units)}${select('default_preset','Default preset',snap.config.presets.map(p=>[p.id,p.name]),s.default_preset)}${field('bluetooth_name','Exact car Bluetooth name',s.bluetooth_name)}${field('home_wifi','Exact home Wi-Fi name',s.home_wifi)}</div><div class="info note">After saving Bluetooth or Wi-Fi names, run <span class="code">SF Sync</span> in Tasker. It updates the native profiles' matching variables. Names are not Hyundai credentials.</div><fieldset><legend>Freshness and cooldowns</legend><div class="formgrid">${field('stale_seconds','Treat car data as stale after (seconds)',s.stale_seconds,'number','min="60" max="3600"')}${field('phone_context_ttl','Phone context expires after (seconds)',s.phone_context_ttl,'number','min="60" max="3600"')}${field('cloud_read_interval','Minimum Hyundai read interval (seconds)',s.cloud_read_interval,'number','min="60" max="3600"')}${field('force_refresh_interval','Minimum car wake-up interval (seconds)',s.force_refresh_interval,'number','min="300" max="7200"')}${field('command_cooldown','Minimum remote command interval (seconds)',s.command_cooldown,'number','min="10" max="600"')}</div><p class="note spaced">These are conservative limits chosen for this project, not claims about Hyundai's official quotas. They do not create automatic polling.</p></fieldset><fieldset><legend>Quiet hours</legend>${check('quiet_enabled','Suppress routine notifications during quiet hours',s.quiet_enabled)}<div class="formgrid spaced">${field('quiet_start','From',s.quiet_start,'time')}${field('quiet_end','Until',s.quiet_end,'time')}</div><p class="note spaced">Confirmation prompts and failures still notify. In-app history remains available.</p></fieldset><fieldset><legend>Dashboard tiles</legend><div class="checkboxgrid">${Object.keys(labels).filter(k=>!k.includes('.')).map(k=>check('tile_'+k,friendly(k),s.tiles.includes(k))).join('')}</div></fieldset><label>Tasker tasks rules are allowed to run (one exact task name per line)<textarea name="task_allowlist">${esc(s.task_allowlist.join('\n'))}</textarea></label><div class="footeractions"><button class="primary">Save settings</button></div></form><section class="card"><h2>Pair Tasker's background tasks</h2><p>The dashboard session and Tasker's native transport pair separately. Generate a code, then run <span class="code">SF Pair Tasker</span>. The token is kept in Tasker's private no-backup directory, not its exported variables.</p>${button('Generate Tasker pairing code','pairCode','','primary')}<div id="pairCode"></div></section><section class="card"><h2>Back up and restore</h2><p>Presets and rules only. No passwords, PINs, pairing tokens or location history. Restoring always disables rules and disarms real commands.</p><div class="actions">${button('Show backup JSON','backup')}${button('Restore pasted JSON','restore','','danger')}</div><label class="spaced">Backup JSON<textarea id="backupText" spellcheck="false" placeholder="Generate a backup or paste one here."></textarea></label>${button('Copy text','copyBackup','','small')}<p class="note spaced">The bridge also keeps the previous configuration before each save. Keep a separate copy before reinstalling Termux.</p></section>`;
}
function account(){return heading('Connect the real vehicle','Skip this until your Santa Fe is enrolled in your U.S. MyHyundai account.')+`<section class="card"><span class="badge">${esc(snap.account.status)}</span><div class="warning note">The unofficial adapter has not been tested against your account. Unsupported fields stay unknown. A high trim level does not guarantee every remote function.</div><form id="loginForm"><div class="formgrid">${field('username','MyHyundai email','','email','autocomplete="username"')}${field('password','MyHyundai password','','password','autocomplete="off"')}${field('pin','Bluelink PIN','','password','autocomplete="off"')}</div><button class="primary spaced">Connect account (read only)</button></form><p class="note spaced">Credentials stay in the running Python process and are forgotten when it stops. They are not saved to a file. The optional adapter must first be installed with <span class="code">Enable-Live.sh</span>.</p>${snap.account.vehicles.length?`<form id="vehicleForm" class="spaced">${select('vehicle_id','Select vehicle',snap.account.vehicles.map(v=>[v.id,v.name+' · VIN ending '+v.vin_last4]),'')}<button class="primary spaced">Read this vehicle</button></form>`:''}</section><section class="card"><h2>Operating mode</h2><div class="actions">${button('Simulation','mode','simulation')}${button('Live, read only','mode','live')}${button('Disconnect Hyundai account','disconnect','','danger')}</div><p class="note spaced">Every mode change clears pending actions and resets live arming. A bridge restart always returns to simulation.</p></section>${snap.mode==='live'?`<form id="armForm" class="card confirm"><h2>Arm live controls</h2><p>Only after verifying the account, selected vehicle and returned status. This permission lasts only for this running bridge session.</p>${field('phrase','Type ENABLE LIVE COMMANDS','','text','id="livePhrase" autocomplete="off"')}${check('auto_lock','Also allow explicitly enabled live lock rules',false)}<button class="danger spaced">Arm this session</button><p class="note spaced">Autonomous ignition and unlock are not enabled by this switch. Those actions still require a current confirmation. Pause all revokes arming immediately, but cannot recall an already-sent command.</p></form>`:''}`}
function diagnostics(){return heading('Diagnostics','No passwords, PINs, bearer tokens or raw Hyundai responses in these logs.')+`<div class="grid"><section class="card"><h2>Connection</h2><div class="statusline"><span>Bridge</span><strong>Online · ${esc(snap.version)}</strong></div><div class="statusline"><span>Mode</span><strong>${esc(snap.mode)}</strong></div><div class="statusline"><span>Vehicle timestamp</span><strong>${esc(timefmt(snap.state.updated_at))}</strong></div><div class="statusline"><span>Vehicle freshness</span><strong>${snap.stale?'Stale or unknown':'Within configured limit'}</strong></div><div class="statusline"><span>Command worker</span><strong>${snap.busy?'Busy':'Idle'}</strong></div><p class="note spaced">Phone notifications require Tasker pairing and the SF Local Heartbeat profile. Dashboard refreshes do not contact Hyundai.</p></section><section class="card"><h2>Capability honesty</h2><p>${esc(snap.capabilities.note)}</p><p class="note">${esc(snap.capabilities.command_availability)}</p><p class="note">Not implemented: ${esc(snap.capabilities.not_implemented.join(', '))}.</p>${button('View reported fields','page','controls')}</section></div><section class="card spaced"><h2>Command history</h2>${snap.jobs.length?snap.jobs.map(j=>`<div class="log"><div class="row"><strong>${esc(friendly(j.action))}</strong><span class="badge${j.state==='unknown'||j.state==='failed'?' warn':''}">${esc(j.state)}</span></div><small>${esc(timefmt(j.at))} · ${esc(j.mode||'account')}</small><p>${esc(j.message)}</p></div>`).join(''):'<p>No operations in this session.</p>'}</section><section class="card"><h2>Bridge activity</h2>${snap.logs.slice(0,60).map(l=>`<div class="log${l.level==='warning'?' warningline':''}"><small>${esc(timefmt(l.at))} · ${esc(l.level)}</small><p>${esc(l.message)}</p></div>`).join('')}</section>`}
function renderPage(){if(!snap||!paired)return;current=location.hash.slice(1)||'dashboard';if(!pages.map(p=>p.toLowerCase()).includes(current))current='dashboard';chrome();const fn={dashboard,controls,climate,automations,simulator,settings,account,diagnostics}[current];$('#main').innerHTML=fn()}
async function saveCfg(cfg,revision=snap.revision){await api('config',{config:cfg,revision});editing=null;editKind='';await refresh();toast('Saved. Pending rules and confirmations were cleared.')}
function cloneCfg(){return structuredClone(snap.config)}
async function command(action,preset){
 await refresh(false);
 const r=await api('command',{action,preset_id:preset||snap.config.settings.default_preset,request_id:uid(),expected_mode:snap.mode,epoch:snap.epoch});
 toast(r.message||r.state);location.hash='dashboard';await refresh();
}
function navigate(page){const hash='#'+page;if(location.hash===hash)renderPage();else location.hash=page}
function leaveEdit(){if(editing&&!window.confirm('Discard unsaved edits?'))return false;editing=null;editKind='';return true}
window.addEventListener('hashchange',()=>{if(editing){editing=null;editKind=''}renderPage();window.scrollTo(0,0)});
$('#pause').addEventListener('click',async()=>{try{await api('pause',{});editing=null;await refresh();toast('Rules paused. Live controls disarmed. Already-sent commands cannot be recalled.')}catch(e){toast(e.message)}});
document.addEventListener('click',async event=>{
 const b=event.target.closest('button');if(!b)return;
 if(b.dataset.page){if(leaveEdit())navigate(b.dataset.page);return}
 const action=b.dataset.action,id=b.dataset.id;if(!action)return;
 try{
  if(action==='page'){if(leaveEdit())navigate(id);return}
  if(action==='command')return await command(id);
  if(action==='startPreset')return await command('climate_start',id);
  if(action==='confirm'){const out=await api('confirm',{id,outdoors:$('#outdoors-'+id)?.checked||false});toast(out.message);await refresh();return}
  if(action==='cancelConfirm'){await api('confirmation/cancel',{id});await refresh();return}
  if(action==='cancelEdit'){editing=null;editKind='';renderPage();return}
  if(action==='newPreset'||action==='editPreset'||action==='copyPreset'){
   editing=action==='newPreset'?{id:uid(),name:'New preset',temperature_f:70,duration_minutes:5,defrost:false,driver:'off',passenger:'off',rear_left:'off',rear_right:'off'}:structuredClone(snap.config.presets.find(p=>p.id===id));
   if(action==='copyPreset'){editing.id=uid();editing.name+=' copy'}editKind='preset';renderPreset();window.scrollTo(0,0);return;
  }
  if(action==='deletePreset'){
   if(!window.confirm('Delete this preset? It cannot be deleted while used by a rule or as the default.'))return;
   const cfg=cloneCfg();cfg.presets=cfg.presets.filter(p=>p.id!==id);await saveCfg(cfg);return;
  }
  if(['newRule','editRule','copyRule','template'].includes(action)){
   editing=action==='editRule'||action==='copyRule'?structuredClone(snap.config.rules.find(r=>r.id===id)):newRule();editKind='rule';
   if(action==='copyRule'){editing.id=uid();editing.name+=' copy';editing.enabled=false}
   if(action==='template'){
    const t={parking:{name:'After parking: unlocked reminder',event:'bluetooth_disconnected',delay_seconds:180,conditions:[{field:'locked',op:'eq',value:false}],actions:[{type:'notify',text:'Santa Fe still reports unlocked. Open the dashboard to check and lock it.'}]},bedtime:{name:'Bedtime lock check',event:'schedule',time:'22:00',conditions:[{field:'locked',op:'eq',value:false}],actions:[{type:'notify',text:'Bedtime check: the latest fresh report says the Santa Fe is unlocked.'}]},fuel:{name:'Low-range reminder',event:'vehicle_changed',cooldown_seconds:21600,conditions:[{field:'range_miles',op:'lt',value:60}],actions:[{type:'notify',text:'Fuel reminder: {range_miles} miles of reported range.'}]},cold:{name:'Cold-weather suggestion',event:'schedule',time:'07:30',conditions:[{field:'outside_f',op:'lt',value:40}],actions:[{type:'notify',text:'It is cold outside. Open the dashboard to confirm outdoor parking and start the Winter preset.'}]}}[id];Object.assign(editing,t);
   }
   renderRule();window.scrollTo(0,0);return;
  }
  if(action==='toggleRules'){const cfg=cloneCfg();cfg.settings.rules_enabled=!cfg.settings.rules_enabled;await saveCfg(cfg);return}
  if(action==='toggleRule'){const cfg=cloneCfg();const r=cfg.rules.find(r=>r.id===id);r.enabled=!r.enabled;await saveCfg(cfg);return}
  if(action==='deleteRule'){if(window.confirm('Delete this routine?')){const cfg=cloneCfg();cfg.rules=cfg.rules.filter(r=>r.id!==id);await saveCfg(cfg)}return}
  if(action==='testRule'){const r=await api('rule/test',{id});toast(`Dry run: conditions ${r.conditions_match?'match':'do not match'}; mode ${r.mode_matches?'matches':'does not match'}; routine ${r.enabled?'enabled':'disabled'}; master ${r.master_enabled?'on':'off'}. Nothing executed.`);return}
  if(['addCondition','removeCondition','addAction','removeAction','userCondition'].includes(action)){
   readRule();
   if(action==='addCondition')editing.conditions.push({field:'locked',op:'eq',value:false});
   if(action==='userCondition'){const name=window.prompt('Custom context name (letters, numbers and underscore):','tennis');if(name&&/^[A-Za-z0-9_]{1,40}$/.test(name))editing.conditions.push({field:'user.'+name,op:'eq',value:''})}
   if(action==='removeCondition')editing.conditions.splice(Number(id),1);
   if(action==='addAction')editing.actions.push({type:'notify',text:'Santa Fe check'});
   if(action==='removeAction')editing.actions.splice(Number(id),1);
   renderRule();return;
  }
  if(action==='scenario'){
   let state={};
   if(id==='unlocked')state={locked:false,engine_running:false,climate_running:false};
   if(id==='window')state={driver_window_open:true};
   if(id==='fuel')state={fuel_percent:9,range_miles:38};
   if(id==='stale')state={updated_at:Date.now()/1000-7200};
   if(id==='unknown')state={locked:null,hood_open:null,updated_at:null};
   if(id==='reset'){
    for(const [k,t] of Object.entries(catalog.fields))if(!k.includes('.')&&t==='boolean')state[k]=false;
    Object.assign(state,{locked:true,fuel_percent:72,range_miles:361,odometer_miles:24,battery_percent:87,outside_f:65});
   }
   await api('simulate',{state});await refresh();toast('Simulator updated.');return;
  }
  if(action==='pairCode'){const r=await api('pairing-code',{});$('#pairCode').innerHTML=`<div class="paircode">${esc(r.code)}</div><p class="note">Valid five minutes. Run SF Pair Tasker and enter this code.</p>`;return}
  if(action==='backup'){$('#backupText').value=JSON.stringify(await api('backup'),null,2);toast('Backup contains configuration only. Copy it to a safe place.');return}
  if(action==='copyBackup'){const t=$('#backupText');try{await navigator.clipboard.writeText(t.value)}catch{t.select();if(!document.execCommand('copy'))throw new Error('Select the backup text and copy it manually.')}toast('Backup text copied.');return}
  if(action==='restore'){const cfg=JSON.parse($('#backupText').value);if(!window.confirm('Restore this backup? All restored rules will be disabled.'))return;await api('restore',{config:cfg,revision:snap.revision});await refresh();toast('Restored with rules disabled and live controls disarmed.');return}
  if(action==='mode'){if(snap.mode===id)return;if(!window.confirm('Switch to '+id+'? Pending actions will be discarded and live controls disarmed.'))return;await api('mode',{mode:id});await refresh();return}
  if(action==='disconnect'){await api('disconnect',{});await refresh();toast('Account disconnected. Returned to simulation.');return}
 }catch(e){toast(e.message||'The operation failed. No automatic retry was attempted.')}
});
document.addEventListener('change',event=>{
 if(editKind==='rule'&&event.target.name&&/^(cf|at)\d+$/.test(event.target.name)){
  const n=event.target.name,i=Number(n.slice(2));readRule();
  if(n.startsWith('cf')){const c=editing.conditions[i];c.op='eq';c.value=catalog.fields[c.field]==='boolean'?false:catalog.fields[c.field]==='number'?0:''}
  renderRule();
 }
});
document.addEventListener('submit',async event=>{
 event.preventDefault();const form=event.target,fd=new FormData(form),submit=form.querySelector('button:not([type=button])');if(submit)submit.disabled=true;
 try{
  if(form.id==='pairForm'){await api('pair',{code:fd.get('code')});paired=true;catalog=null;await refresh();toast('Dashboard paired. You are in simulation.');return}
  if(form.id==='presetForm'){
   const p={...editing};for(const k of ['name','driver','passenger','rear_left','rear_right'])p[k]=fd.get(k);p.temperature_f=Number(fd.get('temperature_f'));p.duration_minutes=Number(fd.get('duration_minutes'));p.defrost=fd.has('defrost');
   const cfg=cloneCfg(),i=cfg.presets.findIndex(x=>x.id===p.id);if(i<0)cfg.presets.push(p);else cfg.presets[i]=p;await saveCfg(cfg);return;
  }
  if(form.id==='ruleForm'){readRule();const cfg=cloneCfg(),r=structuredClone(editing),i=cfg.rules.findIndex(x=>x.id===r.id);if(i<0)cfg.rules.push(r);else cfg.rules[i]=r;await saveCfg(cfg);return}
  if(form.id==='settingsForm'){
   const cfg=cloneCfg(),s=cfg.settings;for(const k of ['name','timezone','theme','units','default_preset','bluetooth_name','home_wifi','quiet_start','quiet_end'])s[k]=fd.get(k);
   for(const k of ['stale_seconds','phone_context_ttl','cloud_read_interval','force_refresh_interval','command_cooldown'])s[k]=Number(fd.get(k));s.quiet_enabled=fd.has('quiet_enabled');s.task_allowlist=String(fd.get('task_allowlist')).split('\n').map(t=>t.trim()).filter(Boolean);s.tiles=Object.keys(labels).filter(k=>fd.has('tile_'+k));await saveCfg(cfg,Number(form.dataset.revision));return;
  }
  if(form.id==='simForm'){
   const state={};for(const [k,v] of fd.entries()){if(k==='failure'){state.failure=v;continue}if(catalog.fields[k]==='boolean')state[k]=v==='unknown'?null:v==='true';else state[k]=v===''?null:Number(v)}await api('simulate',{state});await refresh();toast('Simulated report updated.');return;
  }
  if(form.id==='eventForm'){const r=await api('event',{event:fd.get('event'),value:fd.get('value'),id:uid(),at:Date.now()/1000});await refresh();toast('Trigger accepted; '+r.pending_rules+' delayed rules pending.');return}
  if(form.id==='loginForm'){
   const body={username:fd.get('username'),password:fd.get('password'),pin:fd.get('pin')};form.reset();const r=await api('login',body);body.password='';body.pin='';toast(r.message);await refresh();return;
  }
  if(form.id==='vehicleForm'){const r=await api('select-vehicle',{vehicle_id:fd.get('vehicle_id')});toast(r.message);await refresh();return}
  if(form.id==='armForm'){await api('arm',{phrase:fd.get('phrase'),auto_lock:fd.has('auto_lock')});form.reset();await refresh();toast('Live session armed.');return}
 }catch(e){toast(e.message||'Could not save. Your inputs have not been discarded.')}
 finally{if(submit)submit.disabled=false}
});
async function init(){try{await refresh()}catch(e){if(!paired)renderPair();toast(e.message.includes('fetch')?'Bridge unavailable. Run sf-start in Termux.':e.message)}}
init();
setInterval(async()=>{
 if(!paired||document.hidden||pollBusy||editing)return;
 // Never replace a form, pairing code or backup textarea while someone is editing it.
 const dynamic=['dashboard','controls','automations','diagnostics'];
 try{const wasBusy=snap.busy; const inputFocused=$('#main').contains(document.activeElement)&&['INPUT','TEXTAREA','SELECT'].includes(document.activeElement.tagName); await refresh(dynamic.includes(current)&&!inputFocused); if(current==='account'&&wasBusy&&!snap.busy&&!inputFocused)renderPage()}
 catch(e){if(paired)toast('Local bridge is unreachable. No command was retried.')}
},4000);
