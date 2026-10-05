'use strict';
// Exercise the shipped browser scripts through their UI, without exposing private
// implementation functions or making any real account/network request.
const assert = require('node:assert/strict');
const { test, after } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { randomUUID } = require('node:crypto');
const ROOT = path.resolve(__dirname, '..');
const BASE = 'https://api.telematics.hyundaiusa.com';
const API = BASE + '/ac/v2/';
const ORIGIN = 'https://to-shreds.github.io';
const findings = [];
const creds = { username: 'jon.test+private@example.com', password: 'P@ssw"ord\\private#2026', pin: '7359' };
const vehicle = { vin: '5NMP54G18TH123456', regid: 'private-registration-827', nickName: 'Private Santa Fe', modelCode: 'SantaFe', evStatus: 'P', vehicleGeneration: 3, enrollmentStatus: 'ACTIVE' };

class Element {
  constructor(tag = 'div') { this.tagName = tag.toUpperCase(); this.children = []; this.listeners = new Map(); this.value = ''; this.disabled = false; this.checked = false; this._text = ''; }
  set textContent(v) { this._text = String(v); this.children = []; }
  get textContent() { return this._text + this.children.map(c => c.textContent).join(''); }
  get firstChild() { return this.children[0] || null; }
  append(...children) { this.children.push(...children); }
  prepend(...children) { this.children.unshift(...children); }
  replaceChildren(...children) { this.children = children; this._text = ''; }
  addEventListener(type, fn) { if (!this.listeners.has(type)) this.listeners.set(type, []); this.listeners.get(type).push(fn); }
  emit(type, extra = {}) { for (const fn of this.listeners.get(type) || []) fn({ preventDefault() {}, ...extra }); }
  click() { if (!this.disabled) this.emit('click'); }
}
function makeWindow() {
  const listeners = new Map();
  const window = { messages: [], confirm: () => true,
    addEventListener(type, fn) { if (!listeners.has(type)) listeners.set(type, []); listeners.get(type).push(fn); },
    postMessage(data, origin) { this.messages.push({ data, origin }); this.onPost?.(data, origin); },
    emit(type, event) { for (const fn of listeners.get(type) || []) fn(event); }
  };
  return window;
}
function harness(responses = [], { mode = 'direct', native = false, origin = native ? 'https://santafe.local' : ORIGIN, pathname = '/index.html', nativeOverrides = {}, initialStorage = null, storageDisabled = false } = {}) {
  const elements = new Map();
  const html = fs.readFileSync(path.join(ROOT, 'index.html'), 'utf8');
  for (const m of html.matchAll(/<([a-z]+)\b([^>]*\bid="([^"]+)"[^>]*)>/gi)) {
    const e = new Element(m[1]); e.disabled = /\bdisabled(?:\s|>)/.test(m[2] + '>');
    e.value = m[2].match(/\bvalue="([^"]*)"/)?.[1] || ''; elements.set(m[3], e);
  }
  elements.get('transportMode').value = mode; elements.get('commandSelect').value = 'lock';
  const window = makeWindow(), requests = [], storage = initialStorage || new Map(), timers = new Map(), nativeExports = [], nativeCancels = [];
  if(native)window.SantaFeNative={
    request(json) {
      const envelope=JSON.parse(json);requests.push({...envelope.request,id:envelope.id});
      const next=responses.shift();if(!next)throw new Error('Unexpected native fixture request');
      if(typeof next==='function'){next(envelope,window);return;}
      queueMicrotask(()=>window.SantaFeAndroid.onResponse(envelope.id,{status:next.status || 200,text:next.text===undefined?JSON.stringify(next.data || {}):next.text,headers:next.headers || {}},next.error || null));
    },
    cancel(id){nativeCancels.push(id);},
    exportLog(json){nativeExports.push(json);return true;},
    ...nativeOverrides
  };
  const context = { window, document: { getElementById: id => elements.get(id), createElement: tag => new Element(tag) },
    location: { origin, pathname }, crypto: { randomUUID }, AbortController, URL, Blob, performance, Date,
    localStorage: { setItem: (k, v) => {if(storageDisabled)throw new Error('Storage disabled');storage.set(k, v);}, getItem: k => storage.get(k) ?? null, removeItem: k => {if(storageDisabled)throw new Error('Storage disabled');storage.delete(k);} },
    navigator: { clipboard: { writeText: async v => { context.copied = v; } } },
    setTimeout(fn, ms) { const timer = setTimeout(fn, ms); timer.unref(); timers.set(timer,{fn,ms}); return timer; },
    clearTimeout(timer) { clearTimeout(timer); timers.delete(timer); },
    fetch: async (url, opts) => {
      requests.push({ url, ...opts, headers: { ...opts.headers } });
      const next = responses.shift(); if (!next) throw new Error('Unexpected fixture request');
      if (typeof next === 'function') return next(url, opts);
      if (next instanceof Error) throw next;
      return response(next);
    }
  };
  vm.runInNewContext(fs.readFileSync(path.join(ROOT, 'diagnostic.js'), 'utf8'), context, { filename: 'diagnostic.js' });
  const get = id => { const e = elements.get(id); assert.ok(e, `Missing UI id ${id}`); return e; };
  return { get, window, requests, responses, context, storage, nativeExports, nativeCancels,
    async action(id, type = 'click') { get(id).emit(type); await idle(); },
    async idle() { await idle(); },
    report() { return JSON.parse(storage.get('santafe-diagnostics-v1') || '[]'); },
    async exported() { if(native && origin==='https://santafe.local'){await this.action('downloadLogBtn');return JSON.parse(nativeExports.at(-1));}await this.action('copyLogBtn'); return JSON.parse(context.copied); },
    fireTimeout(ms){for(const [timer,item]of timers)if(item.ms===ms){clearTimeout(timer);timers.delete(timer);item.fn();return;}throw new Error('No matching timeout');},
    close() { for (const timer of timers.keys()) clearTimeout(timer); },
  };
  async function idle() {
    for (let i = 0; i < 100; i++) { await new Promise(r => setImmediate(r)); if (get('stopBtn').disabled) return; }
    throw new Error('UI remained busy beyond fixture completion');
  }
}
function response({ status = 200, data = null, text, headers = {} } = {}) {
  return { status, text: async () => text === undefined ? (data === null ? '' : JSON.stringify(data)) : text,
    headers: { entries: () => Object.entries(headers) } };
}
function loginFixtures(extra = {}) {
  return [ { data: { access_token: 'private-access-987654', refresh_token: 'private-refresh-456789', expires_in: 1800 } },
    { data: { enrolledVehicleDetails: [{ vehicleDetails: { ...vehicle, ...extra } }] } },
    { data: { vehicleStatus: { dateTime: '2026-10-04T19:01:00-04:00', doorLock: true, engine: false, airCtrlOn: false, fuelLevel: 44, dte: { value: 210, unit: 1 }, location: { latitude: 42.04684, longitude: -71.11242 } } } } ];
}
const enrollmentFailure = { status: 502, data: { errorCode: 502, errorMessage: 'Service error', errorSubCode: 'C500', errorSubMessage: 'NO DATA FOUND TO PERFORM THIS OPERATION', functionName: 'getEnrollmentDetailsByUser' } };
const wrongPassword = { status: 502, data: { errorCode: 502, errorMessage: 'Incorrect username or password', errorSubCode: 'IDM_401_1', errorSubMessage: 'Username or password is incorrect' } };
const literalEnrollmentUrl = username => API + 'enrollment/details/' + encodeURIComponent(username).replace(/%40/g, '@');
async function login(h) {
  h.get('email').value = creds.username; h.get('password').value = creds.password; h.get('pin').value = creds.pin;
  await h.action('loginForm', 'submit');
}
function check(name, fn) {
  test(name, async t => { const started = performance.now(); try { await fn(t); findings.push({ test: name, passed: true, elapsed_ms: Math.round(performance.now() - started) }); }
    catch (e) { findings.push({ test: name, passed: false, error: e.message }); throw e; } });
}
after(() => {
  const out = path.join(ROOT, 'docs/verification/diagnostic-tests.json'); fs.mkdirSync(path.dirname(out), { recursive: true });
  fs.writeFileSync(out, JSON.stringify({ generated_at: new Date().toISOString(), method: 'Node VM UI fixture execution of shipped scripts; all HTTP responses mocked; no Hyundai account or vehicle used', results: findings }, null, 2) + '\n');
});

check('Helper-required setup blocks credentials and every programmatic login without direct fallback', async t => {
  const html = fs.readFileSync(path.join(ROOT, 'index.html'), 'utf8');
  assert.ok(html.indexOf('class="setup-details"') < html.indexOf('<form id="loginForm"'), 'Connection setup must precede the account form');
  for (const mode of ['auto', 'helper']) {
    const h = harness([], { mode }); t.after(() => h.close());
    for (const id of ['email', 'password', 'pin', 'loginBtn']) assert.equal(h.get(id).disabled, true, `${mode}: ${id} was usable without the required helper`);
    await login(h); await h.action('loginForm', 'submit'); await h.action('connectionTestBtn');
    assert.equal(h.requests.length, 0, `${mode} must not send a credential request or fall back to direct fetch`);
    assert.equal(h.get('sessionStatus').textContent, 'Signed out');
    assert.match(h.get('notice').textContent, /Android app/i);
  }
});

check('Only a valid same-window helper hello enables credentials and Automatic uses the helper', async t => {
  const h = harness(loginFixtures(), { mode: 'auto' }); t.after(() => h.close());
  const hello = h.window.messages.find(m => m.data.type === 'SF_HELPER_REQUEST' && m.data.id === 'hello').data;
  const signal = (version, extra = {}) => h.window.emit('message', { source: h.window, origin: ORIGIN, data: { type: 'SF_HELPER_RESPONSE', channel: hello.channel, id: 'hello', version }, ...extra });
  signal('1', { origin: 'https://evil.example' }); assert.equal(h.get('loginBtn').disabled, true);
  signal('0'); assert.equal(h.get('loginBtn').disabled, true);
  signal('1'); for (const id of ['email', 'password', 'pin', 'loginBtn']) assert.equal(h.get(id).disabled, false, `${id} stayed disabled after a valid helper hello`);
  h.window.onPost = data => {
    if (!data.request) return;
    h.requests.push({ ...data.request }); const next = h.responses.shift(); assert.ok(next, 'Helper fixture queue exhausted');
    queueMicrotask(() => h.window.emit('message', { source: h.window, origin: ORIGIN, data: { type: 'SF_HELPER_RESPONSE', channel: data.channel, id: data.id, response: { status: next.status || 200, text: next.text === undefined ? JSON.stringify(next.data) : next.text, headers: next.headers || {} } } }));
  };
  h.context.fetch = () => { throw new Error('Automatic unexpectedly fell back to direct fetch'); };
  await login(h); assert.equal(h.requests.length, 3); assert.equal(h.get('sessionStatus').textContent, 'Signed in for this tab');
  assert.ok(h.report().filter(r => r.transport).every(r => r.transport === 'browser helper'));
});

check('Bundled Android page enables login immediately and exclusively uses native Hyundai transport', async t => {
  const h=harness(loginFixtures(),{native:true,mode:'auto'});t.after(()=>h.close());
  assert.equal(h.get('transportStatus').textContent,'Direct Hyundai connection');
  for(const id of ['email','password','pin','loginBtn'])assert.equal(h.get(id).disabled,false);
  for(const id of ['connectionSetup','appDownload','copyLogBtn'])assert.equal(h.get(id).hidden,true);
  assert.equal(h.window.messages.length,0,'Native app must not ask a browser extension for a handshake');
  h.context.fetch=()=>{throw new Error('Native transport must never fall back to fetch');};
  await login(h);assert.equal(h.requests.length,3);assert.equal(h.get('sessionStatus').textContent,'Signed in for this app');
  assert.equal(h.get('password').value,'');assert.equal(h.get('pin').value,'');
  assert.ok(h.report().filter(r=>r.transport).every(r=>r.transport==='native Android'));
  assert.match(h.get('privacyNote').textContent,/leave the app/);
});

check('Native interface cannot be used on a public origin or an unexpected bundled path', async t => {
  for(const options of [{native:true,origin:ORIGIN,mode:'direct'},{native:true,pathname:'/untrusted.html',mode:'direct'}]){
    let nativeCalls=0;const h=harness(loginFixtures(),{...options,nativeOverrides:{request(){nativeCalls++;}}});t.after(()=>h.close());
    await login(h);assert.equal(nativeCalls,0);assert.equal(h.requests.length,3);assert.equal(h.get('sessionStatus').textContent,'Signed in for this tab');
  }
});

check('Absent or incomplete native interface keeps bundled credentials locked even in Direct mode', async t => {
  for(const options of [{origin:'https://santafe.local',mode:'direct'},{native:true,mode:'direct',nativeOverrides:{cancel:undefined}},{native:true,mode:'auto',nativeOverrides:{exportLog:undefined}}]){
    const h=harness([],{...options});t.after(()=>h.close());
    for(const id of ['email','password','pin','loginBtn'])assert.equal(h.get(id).disabled,true);
    await login(h);await h.action('connectionTestBtn');assert.equal(h.requests.length,0);assert.match(h.get('notice').textContent,/unavailable/);
  }
});

check('Native exports pass only sanitized data and distinguish choosing a file from saving it', async t => {
  const fixtures=loginFixtures();fixtures[2].data.vehicleStatus.message=Object.values(creds).join(' ')+' '+vehicle.vin;
  const h=harness(fixtures,{native:true});t.after(()=>h.close());await login(h);
  const report=await h.exported(),raw=JSON.stringify(report);
  for(const value of [...Object.values(creds),vehicle.vin,vehicle.regid,'private-access-987654','private-refresh-456789','42.04684','-71.11242'])assert.equal(raw.includes(value),false,`Native export leaked ${value}`);
  assert.equal(report.version,'0.3.1');assert.match(h.get('notice').textContent,/Choose where to save/);
  h.window.SantaFeAndroid.onExportResult(true,null);assert.equal(h.get('notice').textContent,'Sanitized log saved.');
  h.window.SantaFeAndroid.onExportResult(false,'Log export cancelled.');assert.match(h.get('notice').textContent,/cancelled/);
  await h.action('copyLogBtn');assert.equal(h.context.copied,undefined,'Native app must not copy secrets through a separate clipboard path');
});

check('Native export rejection does not claim a file was written', async t => {
  const h=harness([],{native:true,nativeOverrides:{exportLog(){return false;}}});t.after(()=>h.close());
  await h.action('downloadLogBtn');assert.match(h.get('notice').textContent,/unavailable/);assert.doesNotMatch(h.get('notice').textContent,/saved\./);
});

check('Native stop cancels once, ignores late responses and never retries login', async t => {
  let envelope;const h=harness([(request)=>{envelope=request;}],{native:true});t.after(()=>h.close());
  h.get('email').value=creds.username;h.get('password').value=creds.password;h.get('pin').value=creds.pin;h.get('loginForm').emit('submit');
  assert.equal(h.requests.length,1);h.get('stopBtn').click();await h.idle();assert.deepEqual(h.nativeCancels,[envelope.id]);
  h.window.SantaFeAndroid.onResponse(envelope.id,{status:200,text:JSON.stringify(loginFixtures()[0].data),headers:{}},null);
  await h.idle();assert.equal(h.requests.length,1);assert.equal(h.get('sessionStatus').textContent,'Signed out');assert.equal(h.get('password').value,'');
});

check('Native pagehide during enrollment redacts encoded email after secret memory is cleared',async t=>{
  let held;const h=harness([loginFixtures()[0],envelope=>{held=envelope;}],{native:true});t.after(()=>h.close());
  h.get('email').value=creds.username;h.get('password').value=creds.password;h.get('pin').value=creds.pin;h.get('loginForm').emit('submit');
  for(let i=0;i<30 && !held;i++)await new Promise(resolve=>setImmediate(resolve));assert.ok(held,'Enrollment request was not pending');
  h.window.emit('pagehide');await h.idle();const saved=JSON.stringify(h.report()),exported=JSON.stringify(await h.exported());
  for(const text of [saved,exported])for(const value of [creds.username,encodeURIComponent(creds.username),creds.password,creds.pin,'private-access-987654'])assert.equal(text.includes(value),false,`Aborted enrollment leaked ${value}`);
  assert.match(saved,/enrollment\/details\/\[REDACTED\]/);assert.equal(h.requests.length,2);assert.deepEqual(h.nativeCancels,[held.id]);
  h.window.SantaFeAndroid.onResponse(held.id,{status:200,text:JSON.stringify(loginFixtures()[1].data),headers:{}},null);await h.idle();assert.equal(h.get('sessionStatus').textContent,'Signed out');assert.equal(h.requests.length,2);
});

check('Pagehide retains redaction values until an aborted request logs opaque echoed secrets',async t=>{
  let waiting=false;
  const h=harness([loginFixtures()[0],(_url,opts)=>new Promise((_resolve,reject)=>{waiting=true;opts.signal.addEventListener('abort',()=>reject(new Error('Connection cancelled '+creds.password+' '+opts.headers.accessToken)),{once:true});})]);t.after(()=>h.close());
  h.get('email').value=creds.username;h.get('password').value=creds.password;h.get('pin').value=creds.pin;h.get('loginForm').emit('submit');
  for(let i=0;i<30 && !waiting;i++)await new Promise(resolve=>setImmediate(resolve));assert.equal(waiting,true);
  h.window.emit('pagehide');await h.idle();const text=JSON.stringify(h.report())+' '+JSON.stringify(await h.exported())+' '+h.get('notice').textContent;
  for(const value of [creds.password,'private-access-987654',encodeURIComponent(creds.username)])assert.equal(text.includes(value),false,`Pagehide cancellation exposed ${value}`);
  assert.equal(h.get('email').value,'');assert.equal(h.get('password').value,'');assert.equal(h.requests.length,2);
});

check('Native timeout cancels the request and does not fall back or retry', async t => {
  const h=harness([()=>{}],{native:true,mode:'direct'});t.after(()=>h.close());
  h.get('email').value=creds.username;h.get('password').value=creds.password;h.get('pin').value=creds.pin;h.get('loginForm').emit('submit');
  h.fireTimeout(46000);await h.idle();assert.equal(h.requests.length,1);assert.equal(h.nativeCancels.length,1);assert.match(h.get('notice').textContent,/timed out|unknown/);assert.equal(h.get('password').value,'');
});

check('Native callbacks ignore unknown identifiers and reject malformed or failed responses', async t => {
  for(const malformed of [{status:'200',text:'{}',headers:{}},{status:200,text:'{}',headers:[]},{status:200,text:'x'.repeat(1048577),headers:{}},{status:200,text:'{}',headers:{'bad\r\nheader':'x'}}]){
    const h=harness([(envelope,window)=>{window.SantaFeAndroid.onResponse('unrelated-id',{status:200,text:'{}',headers:{}},null);window.SantaFeAndroid.onResponse(envelope.id,malformed,null);}],{native:true});t.after(()=>h.close());
    await login(h);assert.equal(h.requests.length,1);assert.match(h.get('notice').textContent,/invalid response/);assert.equal(h.get('sessionStatus').textContent,'Signed out');
  }
  const h=harness([{error:'Native TLS connection failed.'}],{native:true});t.after(()=>h.close());await login(h);assert.equal(h.requests.length,1);assert.match(h.get('notice').textContent,/TLS/);
});

check('Native transaction headers reach polling and command acceptance remains distinct from completion', async t => {
  const h=harness([...loginFixtures(),{text:'',headers:{TmsTid:'native-private-transaction'}},{data:{status:'SUCCESS'}}],{native:true});t.after(()=>h.close());await login(h);
  h.get('confirmCommand').checked=true;await h.action('commandBtn');assert.equal(h.get('commandBtn').disabled,true);assert.equal((await h.exported()).evidence.some(e=>/reported SUCCESS/.test(e.scope)),false);
  await h.action('pollBtn');assert.equal(h.requests[4].headers.tid,'native-private-transaction');assert.equal((await h.exported()).evidence.some(e=>/reported SUCCESS/.test(e.scope)),true);assert.equal(h.get('commandBtn').disabled,false);
});

check('Native command guard and sanitized start record are persisted before transmission',async t=>{
  let observedMarker,observedLog;
  const h=harness([...loginFixtures(),(envelope,window)=>{observedMarker=JSON.parse(h.storage.get('santafe-pending-command-v1'));observedLog=h.report().find(r=>r.outcome==='Submission starting; outcome unresolved');window.SantaFeAndroid.onResponse(envelope.id,{status:200,text:'',headers:{TmsTid:'private-start-id'}},null);}],{native:true});t.after(()=>h.close());
  await login(h);h.get('confirmCommand').checked=true;await h.action('commandBtn');
  assert.equal(observedMarker.action,'lock');assert.match(observedMarker.at,/^\d{4}-/);assert.deepEqual(Object.keys(observedMarker).sort(),['action','at']);
  assert.equal(observedLog.request.path,'/ac/v2/rcs/rdo/off');assert.equal(observedLog.request.method,'POST');
  for(const value of [...Object.values(creds),vehicle.vin,vehicle.regid,'private-access-987654','private-start-id'])assert.equal(JSON.stringify(observedMarker).includes(value),false);
});

check('Native restart preserves unknown-command gate while allowing reads and explicit physical acknowledgement',async t=>{
  const first=harness([...loginFixtures(),{text:''}],{native:true});t.after(()=>first.close());await login(first);first.get('confirmCommand').checked=true;await first.action('commandBtn');first.window.emit('pagehide');
  const resumed=harness([...loginFixtures(),{text:'',headers:{TmsTid:'next-private-id'}}],{native:true,initialStorage:first.storage});t.after(()=>resumed.close());
  assert.equal(resumed.get('unknownCommandPanel').hidden,false);assert.match(resumed.get('notice').textContent,/unresolved/);assert.equal(resumed.get('email').value,'');
  await login(resumed);assert.equal(resumed.requests.length,3);assert.equal(resumed.get('commandBtn').disabled,true);
  resumed.get('confirmCommand').checked=true;await resumed.action('commandBtn');assert.equal(resumed.requests.length,3);
  resumed.window.confirm=()=>false;await resumed.action('resolveUnknownBtn');assert.equal(resumed.get('commandBtn').disabled,true);
  resumed.window.confirm=()=>true;await resumed.action('resolveUnknownBtn');assert.equal(resumed.requests.length,3,'Acknowledgement must not transmit a command');assert.equal(resumed.get('commandBtn').disabled,false);assert.equal(resumed.get('unknownCommandPanel').hidden,true);assert.equal(resumed.storage.has('santafe-pending-command-v1'),false);
  resumed.get('confirmCommand').checked=true;await resumed.action('commandBtn');assert.equal(resumed.requests.length,4);
});

check('Definitive native SUCCESS or ERROR clears guard, while pending and unknown polling preserve it',async t=>{
  for(const state of ['SUCCESS','ERROR','PENDING',null]){
    const h=harness([...loginFixtures(),{text:'',headers:{TmsTid:'guard-poll-id'}},{data:state?{status:state}:{}}],{native:true});t.after(()=>h.close());await login(h);h.get('confirmCommand').checked=true;await h.action('commandBtn');await h.action('pollBtn');
    assert.equal(h.storage.has('santafe-pending-command-v1'),!['SUCCESS','ERROR'].includes(state));
  }
});

check('Cancelled confirmation, invalid climate, and unavailable storage never save or send a command',async t=>{
  const declined=harness(loginFixtures(),{native:true});t.after(()=>declined.close());await login(declined);declined.window.confirm=()=>false;declined.get('confirmCommand').checked=true;await declined.action('commandBtn');assert.equal(declined.requests.length,3);assert.equal(declined.storage.has('santafe-pending-command-v1'),false);
  const invalid=harness(loginFixtures(),{native:true});t.after(()=>invalid.close());await login(invalid);invalid.get('commandSelect').value='climate_start';invalid.get('confirmCommand').checked=true;invalid.get('outdoor').checked=false;await invalid.action('commandBtn');assert.equal(invalid.requests.length,3);assert.equal(invalid.storage.has('santafe-pending-command-v1'),false);
  const blocked=harness(loginFixtures(),{native:true,storageDisabled:true});t.after(()=>blocked.close());await login(blocked);blocked.get('confirmCommand').checked=true;await blocked.action('commandBtn');assert.equal(blocked.requests.length,3);assert.match(blocked.get('notice').textContent,/guard|storage/);
});

check('Malformed persisted guard remains conservative and acknowledgement cannot bypass a failed storage clear',async t=>{
  const saved=new Map([['santafe-pending-command-v1','{malformed']]);
  const h=harness(loginFixtures(),{native:true,initialStorage:saved,storageDisabled:true});t.after(()=>h.close());await login(h);assert.equal(h.get('commandBtn').disabled,true);assert.match(h.get('unknownCommandNotice').textContent,/previous vehicle command/);
  await h.action('resolveUnknownBtn');assert.equal(h.get('commandBtn').disabled,true);assert.match(h.get('notice').textContent,/could not be cleared/);assert.equal(h.requests.length,3);
});

check('Login, enrollment and cached status run in order and clear credentials', async t => {
  const h = harness(loginFixtures()); t.after(() => h.close()); await login(h);
  assert.equal(h.requests.length, 3); assert.deepEqual(h.requests.map(r => r.url), [BASE + '/v2/ac/oauth/token', API + 'enrollment/details/' + encodeURIComponent(creds.username), API + 'rcs/rvs/vehicleStatus']);
  assert.equal(h.get('password').value, ''); assert.equal(h.get('pin').value, '');
  assert.equal(h.get('sessionStatus').textContent, 'Signed in for this tab');
  assert.equal(h.get('vehicleSelect').disabled, false, 'A signed-in user must be able to select an enrolled vehicle');
  assert.equal(h.get('commandBtn').disabled, false); assert.match(h.get('vehicleSummary').textContent, /cached|timestamp/i);
  assert.equal(h.requests.filter(r => r.method === 'POST').length, 1, 'Login must never trigger remote commands');
});

check('Logged login success followed by enrollment C500 preserves session and enables only a manual lookup test', async t => {
  const h = harness([loginFixtures()[0], enrollmentFailure], { native: true }); t.after(() => h.close());
  assert.equal(h.get('referenceEnrollmentBtn').disabled, true);
  await login(h);
  assert.equal(h.requests.length, 2, 'An enrollment failure must not trigger a fallback, second login or cached status');
  assert.equal(h.get('sessionStatus').textContent, 'Signed in for this app');
  assert.equal(h.get('runReadTestsBtn').disabled, false); assert.equal(h.get('referenceEnrollmentBtn').disabled, false);
  for (const id of ['commandBtn', 'cachedBtn', 'refreshBtn']) assert.equal(h.get(id).disabled, true);
  assert.equal(h.get('password').value, ''); assert.equal(h.get('pin').value, '');
  for (const fragment of [/Vehicle enrollment/i, /HTTP 502/, /C500/, /NO DATA FOUND TO PERFORM THIS OPERATION/i]) assert.match(h.get('notice').textContent, fragment);
  const report = await h.exported();
  assert.equal(report.evidence.some(e => e.test === 'Login' && e.http_status === 200), true);
  assert.equal(report.requests.find(r => r.label === 'Vehicle enrollment').response.errorSubCode, 'C500');
  assert.equal(report.requests.filter(r => r.method === 'POST').length, 1);
});

check('HTTP 502 with IDM_401_1 identifies incorrect credentials and never enables the post-login lookup', async t => {
  const h = harness([wrongPassword], { native: true }); t.after(() => h.close()); await login(h);
  assert.equal(h.requests.length, 1); assert.equal(h.get('sessionStatus').textContent, 'Signed out');
  for (const id of ['runReadTestsBtn', 'referenceEnrollmentBtn', 'commandBtn']) assert.equal(h.get(id).disabled, true);
  for (const fragment of [/Login/i, /HTTP 502/, /IDM_401_1/, /Username or password is incorrect/]) assert.match(h.get('notice').textContent, fragment);
  await h.action('referenceEnrollmentBtn'); assert.equal(h.requests.length, 1, 'A programmatic lookup click must not bypass the missing-session guard');
});

check('Manual literal-at lookup reuses token and PIN once and remembers only a confirmed enrollment format', async t => {
  const fixtures = loginFixtures();
  const h = harness([fixtures[0], enrollmentFailure, fixtures[1], fixtures[2], fixtures[1], fixtures[2]], { native: true }); t.after(() => h.close());
  await login(h); await h.action('referenceEnrollmentBtn');
  assert.equal(h.requests.length, 4, 'The click sends one enrollment GET and then cached status for the returned vehicle');
  assert.equal(h.requests[2].method, 'GET'); assert.equal(h.requests[2].url, literalEnrollmentUrl(creds.username));
  assert.equal(h.requests[2].url.includes('%2B'), true, 'Only the at-sign changes; plus signs remain escaped');
  for (const key of ['username', 'accessToken', 'blueLinkServicePin']) assert.equal(h.requests[2].headers[key], h.requests[1].headers[key], `${key} changed during the comparison`);
  assert.equal(h.requests[3].url, API + 'rcs/rvs/vehicleStatus'); assert.equal(h.requests[3].headers.refresh, 'false');
  assert.equal(h.get('commandBtn').disabled, false); assert.equal(h.get('referenceEnrollmentBtn').disabled, true);
  await h.action('runReadTestsBtn');
  assert.equal(h.requests.length, 6); assert.equal(h.requests[4].url, literalEnrollmentUrl(creds.username));
  assert.equal(h.requests.filter(r => r.method === 'POST').length, 1, 'Lookup tests must never repeat login or send a vehicle command');
  const report = await h.exported(), raw = JSON.stringify(h.report()) + JSON.stringify(report);
  assert.deepEqual(report.requests.filter(r => /Vehicle enrollment/.test(r.label)).map(r => r.enrollment_path_format), ['encoded-at', 'literal-at', 'literal-at']);
  for (const value of [...Object.values(creds), encodeURIComponent(creds.username), encodeURIComponent(creds.username).replace(/%40/g, '@'), vehicle.vin, vehicle.regid, 'private-access-987654', 'private-refresh-456789']) assert.equal(raw.includes(value), false, `Lookup export leaked ${value}`);
  assert.match(raw, /enrollment\/details\/\[REDACTED\]/);
  assert.equal(h.report().some(r => r.label === 'Reported capability fields'), true);
});

check('Failed or malformed alternate enrollment never retries or promotes the unconfirmed email format', async t => {
  for (const failed of [enrollmentFailure, { data: { vehicles: [vehicle] } }]) {
    const fixtures = loginFixtures(), h = harness([fixtures[0], enrollmentFailure, failed, fixtures[1], fixtures[2]], { native: true }); t.after(() => h.close());
    await login(h); await h.action('referenceEnrollmentBtn');
    assert.equal(h.requests.length, 3); assert.equal(h.requests[2].url, literalEnrollmentUrl(creds.username));
    assert.equal(h.get('sessionStatus').textContent, 'Signed in for this app'); assert.equal(h.get('commandBtn').disabled, true);
    assert.equal(h.get('referenceEnrollmentBtn').disabled, false, 'Only a deliberate click may test again after failure');
    await h.action('runReadTestsBtn');
    assert.equal(h.requests.length, 5); assert.equal(h.requests[3].url, API + 'enrollment/details/' + encodeURIComponent(creds.username));
    assert.equal(h.requests.filter(r => r.method === 'POST').length, 1);
    const report = await h.exported(); assert.deepEqual(report.requests.filter(r => /Vehicle enrollment/.test(r.label)).map(r => r.enrollment_path_format), ['encoded-at', 'literal-at', 'encoded-at']);
  }
});

check('Alternate lookup error text and its literal-at URL are sanitized before display, storage and export', async t => {
  const echoed = { ...enrollmentFailure, data: { ...enrollmentFailure.data, errorSubMessage: 'NO DATA FOUND TO PERFORM THIS OPERATION ' + literalEnrollmentUrl(creds.username) + ' ' + Object.values(creds).join(' ') + ' private-access-987654' } };
  const h = harness([loginFixtures()[0], enrollmentFailure, echoed], { native: true }); t.after(() => h.close()); await login(h); await h.action('referenceEnrollmentBtn');
  const raw = h.get('notice').textContent + JSON.stringify(h.report()) + JSON.stringify(await h.exported());
  for (const value of [...Object.values(creds), encodeURIComponent(creds.username), encodeURIComponent(creds.username).replace(/%40/g, '@'), 'private-access-987654']) assert.equal(raw.includes(value), false, `Alternate error exposed ${value}`);
  assert.match(raw, /NO DATA FOUND TO PERFORM THIS OPERATION/); assert.match(raw, /C500/); assert.match(raw, /enrollment\/details\/\[REDACTED\]/);
  assert.equal(h.requests.length, 3);
});

check('Busy manual lookup disables competing actions and duplicate clicks produce only one GET', async t => {
  let held; const fixtures = loginFixtures(), h = harness([fixtures[0], enrollmentFailure, envelope => { held = envelope; }, fixtures[2]], { native: true }); t.after(() => h.close());
  await login(h); h.get('referenceEnrollmentBtn').emit('click'); h.get('referenceEnrollmentBtn').emit('click'); h.get('runReadTestsBtn').emit('click');
  assert.ok(held); assert.equal(h.requests.length, 3);
  for (const id of ['referenceEnrollmentBtn', 'runReadTestsBtn', 'loginBtn', 'disconnectBtn', 'commandBtn']) assert.equal(h.get(id).disabled, true);
  h.window.SantaFeAndroid.onResponse(held.id, { status: 200, text: JSON.stringify(fixtures[1].data), headers: {} }, null); await h.idle();
  assert.equal(h.requests.length, 4); assert.equal(h.requests.filter(r => r.method === 'POST').length, 1);
});

check('An expired session prevents the alternate lookup from sending a request and disables its button', async t => {
  const h = harness([loginFixtures()[0], enrollmentFailure], { native: true }); t.after(() => h.close()); await login(h);
  assert.equal(h.get('referenceEnrollmentBtn').disabled, false);
  const future = Date.now() + 1801000; h.context.Date = class extends Date { static now() { return future; } };
  await h.action('referenceEnrollmentBtn');
  assert.equal(h.requests.length, 2); assert.equal(h.get('referenceEnrollmentBtn').disabled, true); assert.equal(h.get('commandBtn').disabled, true);
  assert.match(h.get('notice').textContent, /login has expired/i); assert.equal(h.requests.filter(r => r.method === 'POST').length, 1);
});

check('Stopping or leaving a delayed alternate lookup rejects its late success without adopting the format or exposing identity', async t => {
  for (const action of ['stop', 'pagehide']) {
    let held; const fixtures = loginFixtures(), h = harness([fixtures[0], enrollmentFailure, envelope => { held = envelope; }], { native: true }); t.after(() => h.close());
    await login(h); h.get('referenceEnrollmentBtn').emit('click'); assert.ok(held); assert.equal(h.requests.length, 3);
    if (action === 'stop') h.get('stopBtn').click(); else h.window.emit('pagehide'); await h.idle();
    assert.deepEqual(h.nativeCancels, [held.id]); assert.equal(h.get('commandBtn').disabled, true);
    h.window.SantaFeAndroid.onResponse(held.id, { status: 200, text: JSON.stringify(fixtures[1].data), headers: {} }, null); await h.idle();
    assert.equal(h.requests.length, 3, 'The cancelled lookup must not trigger a cached-status request');
    const report = await h.exported(), raw = JSON.stringify(h.report()) + JSON.stringify(report);
    for (const value of [...Object.values(creds), encodeURIComponent(creds.username), encodeURIComponent(creds.username).replace(/%40/g, '@'), vehicle.vin, vehicle.regid, 'private-access-987654']) assert.equal(raw.includes(value), false, `${action} leaked ${value}`);
    assert.deepEqual(report.requests.filter(r => /Vehicle enrollment/.test(r.label)).map(r => r.enrollment_path_format), ['encoded-at', 'literal-at']);
    if (action === 'pagehide') {
      assert.equal(h.get('sessionStatus').textContent, 'Signed out'); assert.equal(h.get('referenceEnrollmentBtn').disabled, true); assert.equal(h.get('email').value, '');
      h.responses.push(...loginFixtures()); await login(h); assert.equal(h.requests[4].url, API + 'enrollment/details/' + encodeURIComponent(creds.username)); assert.equal(h.requests.length, 6);
    } else {
      assert.equal(h.get('sessionStatus').textContent, 'Signed in for this app'); h.responses.push(fixtures[1], fixtures[2]); await h.action('runReadTestsBtn');
      assert.equal(h.requests[3].url, API + 'enrollment/details/' + encodeURIComponent(creds.username)); assert.equal(h.requests.length, 5); assert.equal(h.requests.filter(r => r.method === 'POST').length, 1);
    }
  }
});

check('Cached-status failure cannot offer an unrelated alternate enrollment lookup', async t => {
  const fixtures = loginFixtures(), h = harness([fixtures[0], fixtures[1], enrollmentFailure], { native: true }); t.after(() => h.close()); await login(h);
  assert.equal(h.requests.length, 3); assert.equal(h.get('sessionStatus').textContent, 'Signed in for this app');
  assert.equal(h.get('referenceEnrollmentBtn').disabled, true); assert.equal(h.get('commandBtn').disabled, true);
  assert.match(h.get('notice').textContent, /Cached vehicle status/i); assert.match(h.get('notice').textContent, /C500/);
});

check('A later enrollment failure invalidates stale read status and withholds new commands', async t => {
  const h = harness([...loginFixtures(), enrollmentFailure], { native: true }); t.after(() => h.close()); await login(h); assert.equal(h.get('commandBtn').disabled, false);
  await h.action('runReadTestsBtn'); assert.equal(h.requests.length, 4); assert.equal(h.get('sessionStatus').textContent, 'Signed in for this app');
  assert.equal(h.get('commandBtn').disabled, true); assert.equal(h.get('referenceEnrollmentBtn').disabled, false);
  h.get('confirmCommand').checked = true; await h.action('commandBtn'); assert.equal(h.requests.length, 4, 'A programmatic command cannot use stale success after enrollment failure');
});

check('Saved and copied logs redact credentials, identifiers, coordinates and echoed secrets', async t => {
  const secretId = 'private-tms-id-394385';
  const fixtures = loginFixtures(); fixtures[2].data.vehicleStatus.message = `Echo ${creds.username} ${creds.password} ${creds.pin} ${vehicle.vin} ${vehicle.regid} private-access-987654 private-refresh-456789 coordinates 42.04684,-71.11242`;
  fixtures.push({ status: 400, headers: { TmsTid: secretId, transactionId: 'private-txid', 'set-cookie': 'private-cookie=hello', 'x-debug': secretId },
    data: { errorCode: 900, errorMessage: `Error URL /${encodeURIComponent(creds.username)}?password=${creds.password}; PIN ${creds.pin}; VIN ${vehicle.vin}; tid ${secretId}`, contact: { phone: '5551234567', postalCode: '02356', city: 'Easton', firstName: 'PrivateFirst' } } });
  const h = harness(fixtures); t.after(() => h.close()); await login(h); await h.action('cachedBtn'); const saved = JSON.stringify(h.report()), exported = JSON.stringify(await h.exported());
  for (const value of [creds.username, encodeURIComponent(creds.username), creds.password, creds.pin, vehicle.vin, vehicle.regid, 'private-access-987654', 'private-refresh-456789', '42.04684', '-71.11242', secretId, 'private-txid', 'private-cookie=hello', '5551234567', '02356', 'Easton', 'PrivateFirst']) {
    assert.equal(saved.includes(value), false, `Saved log leaked ${value}`); assert.equal(exported.includes(value), false, `Export leaked ${value}`);
  }
  assert.equal(exported.includes('[REDACTED]'), true); assert.equal(h.get('notice').textContent.includes(creds.username), false);
});

check('Non-JSON authentication errors retain status without raw response text', async t => {
  const h = harness([{ status: 502, text: `<html>upstream echoed ${creds.password} and unclassified-private-token</html>` }]); t.after(() => h.close()); await login(h);
  assert.equal(h.requests.length, 1); const text = JSON.stringify(h.report()); assert.equal(text.includes('unclassified-private-token'), false); assert.match(text, /Non-JSON response body omitted/); assert.match(text, /502/);
});

check('Connection check after login sends no account identity, access token or service PIN', async t => {
  const h = harness([...loginFixtures(), { status: 405, text: '' }]); t.after(() => h.close()); await login(h); await h.action('connectionTestBtn');
  assert.equal(h.requests.length, 4); assert.equal(h.requests[3].url, BASE + '/v2/ac/oauth/token'); assert.equal(h.requests[3].headers.username, undefined); assert.equal(h.requests[3].headers.accessToken, undefined); assert.equal(h.requests[3].headers.blueLinkServicePin, undefined);
});

check('Empty HTTP 200 login does not invent an authenticated session or vehicle data', async t => {
  const h = harness([{ status: 200, text: '' }]); t.after(() => h.close()); await login(h);
  assert.equal(h.requests.length, 1); assert.equal(h.get('sessionStatus').textContent, 'Signed out'); assert.equal(h.get('commandBtn').disabled, true); assert.match(h.get('notice').textContent, /No access token/);
  const r = await h.exported(); assert.equal(r.evidence.some(e => /confirmed|SUCCESS/.test(e.scope)), false);
});

check('Malformed enrollment and status response shapes cannot enable vehicle commands', async t => {
  for (const fixtures of [[loginFixtures()[0], { data: { vehicles: [vehicle] } }], [loginFixtures()[0], loginFixtures()[1], { data: { status: 'OK' } }]]) {
    const h = harness(fixtures); t.after(() => h.close()); await login(h); assert.equal(h.get('commandBtn').disabled, true); assert.match(h.get('notice').textContent, /expected vehicle list|no vehicleStatus/);
  }
});

check('HTTP 429 stops the read sequence and never retries', async t => {
  const h = harness([{ status: 429, data: { errorMessage: 'Rate limit' }, headers: { 'retry-after': '60' } }]); t.after(() => h.close()); await login(h);
  assert.equal(h.requests.length, 1); assert.match(h.get('notice').textContent, /rate limited|no retries/); assert.equal(h.get('password').value, '');
});

check('CORS failure offers actionable helper instructions and sends no retry', async t => {
  const h = harness([new TypeError('Failed to fetch')]); t.after(() => h.close()); await login(h);
  assert.equal(h.requests.length, 1); assert.match(h.get('notice').textContent, /CORS/); assert.match(h.get('notice').textContent, /helper/); assert.equal(h.get('password').value, '');
});

check('Duplicate login submissions while busy produce only one request', async t => {
  let release; const h = harness([() => new Promise(r => { release = () => r(response(loginFixtures()[0])); }), loginFixtures()[1], loginFixtures()[2]]); t.after(() => h.close());
  h.get('email').value = creds.username; h.get('password').value = creds.password; h.get('pin').value = creds.pin;
  h.get('loginForm').emit('submit'); h.get('loginForm').emit('submit'); assert.equal(h.requests.length, 1); release(); await h.idle(); assert.equal(h.requests.length, 3);
});

check('Stop waiting aborts the request and never retries or preserves password', async t => {
  const h = harness([(_url, opts) => new Promise((_resolve, reject) => opts.signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')), { once: true }))]); t.after(() => h.close());
  h.get('email').value = creds.username; h.get('password').value = creds.password; h.get('pin').value = creds.pin;
  h.get('loginForm').emit('submit'); assert.equal(h.requests.length, 1); h.get('stopBtn').click(); await h.idle(); assert.equal(h.requests.length, 1); assert.match(h.get('notice').textContent, /stopped|unknown/i); assert.equal(h.get('password').value, '');
});

check('Accepted command without transaction ID stays unresolved and cannot be retried', async t => {
  const h = harness([...loginFixtures(), { status: 200, text: '' }]); t.after(() => h.close()); await login(h);
  h.get('confirmCommand').checked = true; await h.action('commandBtn');
  assert.equal(h.requests.length, 4); assert.equal(h.get('commandBtn').disabled, true); assert.equal(h.get('pollBtn').disabled, true, 'No readable transaction ID means there is nothing to poll'); assert.match(h.get('notice').textContent, /unknown/);
  const report = await h.exported(); assert.equal(report.evidence.some(e => /confirmed|SUCCESS/.test(e.scope)), false);
  h.get('confirmCommand').checked = true; await h.action('commandBtn'); assert.equal(h.requests.length, 4);
});

check('Command is observed first and confirmed only after SUCCESS transaction result', async t => {
  const h = harness([...loginFixtures(), { status: 200, text: '', headers: { TmsTid: 'private-confirm-id' } }, { data: { status: 'SUCCESS' } }]); t.after(() => h.close()); await login(h);
  h.get('confirmCommand').checked = true; await h.action('commandBtn'); let r = await h.exported(); assert.equal(r.evidence.some(e => /reported SUCCESS/.test(e.scope)), false); assert.equal(h.get('commandBtn').disabled, true);
  await h.action('pollBtn'); r = await h.exported(); assert.equal(r.evidence.some(e => /reported SUCCESS/.test(e.scope)), true);
  assert.equal(h.get('commandBtn').disabled, false, 'Completed command should release the one-command gate'); assert.equal(h.get('pollBtn').disabled, true); assert.equal(h.requests.length, 5);
});

check('Lights and horn/lights poll with their specific service type', async t => {
  for (const [action, suffix, service] of [['lights', 'rcs/rhl/light', 'LIGHTS_ONLY'], ['horn_lights', 'rcs/rhl/hnl', 'HORN_AND_LIGHTS']]) {
    const h = harness([...loginFixtures(), { headers: { transactionId: 'private-horn-tx' } }, { data: { status: 'SUCCESS' } }]); t.after(() => h.close()); await login(h); h.get('commandSelect').value = action; h.get('confirmCommand').checked = true; await h.action('commandBtn'); await h.action('pollBtn');
    assert.equal(h.requests[3].url, API + suffix); assert.equal(h.requests[4].headers.service_type, service); assert.equal(h.requests[4].headers.tid, 'private-horn-tx');
  }
});

check('Rereading enrollment after a command preserves polling for the same vehicle identifiers', async t => {
  const h = harness([...loginFixtures(), { headers: { TmsTid: 'fixture-reread-txid' } }, loginFixtures()[1], loginFixtures()[2], { data: { status: 'SUCCESS' } }]); t.after(() => h.close()); await login(h); h.get('confirmCommand').checked = true; await h.action('commandBtn'); await h.action('runReadTestsBtn'); await h.action('pollBtn');
  assert.equal(h.requests.length, 7); assert.equal(h.requests[6].url, API + 'rmt/getRunningStatus'); assert.equal(h.requests.filter(r => r.method === 'POST' && r.url !== BASE + '/v2/ac/oauth/token').length, 1); assert.match(h.get('notice').textContent, /confirmed command completion/);
});

check('Climate validation blocks unsafe or unknown engine settings before transmission', async t => {
  const scenarios = [{ vehicle: {}, outdoor: false, temp: '72', message: /outdoors/ }, { vehicle: {}, outdoor: true, temp: '82', message: /62 to 81/ }, { vehicle: { evStatus: 'unknown' }, outdoor: true, temp: '72', message: /engine type/ }];
  for (const s of scenarios) {
    const h = harness(loginFixtures(s.vehicle)); t.after(() => h.close()); await login(h); h.get('commandSelect').value = 'climate_start'; h.get('confirmCommand').checked = true; h.get('outdoor').checked = s.outdoor; h.get('temperature').value = s.temp; await h.action('commandBtn'); assert.equal(h.requests.length, 3); assert.match(h.get('notice').textContent, s.message);
  }
});

function helperHarness() {
  const window = makeWindow(), calls = [], aborts = []; const context = { window, location: { origin: ORIGIN }, URL,
    GM_xmlhttpRequest: opts => { calls.push(opts); return { abort: () => aborts.push(opts.url) }; } };
  vm.runInNewContext(fs.readFileSync(path.join(ROOT, 'santafe-network.user.js'), 'utf8'), context, { filename: 'santafe-network.user.js' });
  const send = (data, overrides = {}) => window.emit('message', { source: window, origin: ORIGIN, data: { type: 'SF_HELPER_REQUEST', channel: 'test-session-channel', id: randomUUID(), ...data }, ...overrides });
  send({ hello: true, id: 'hello' });
  return { window, calls, aborts, send, request: (url, method = 'GET', headers = {}, body = null) => ({ request: { url, method, headers, body } }) };
}

check('Browser helper rejects non-Hyundai, unknown endpoints, invalid methods and injectable headers', async () => {
  const h = helperHarness();
  const invalid = [h.request('https://evil.example/ac/v2/rcs/rvs/vehicleStatus'), h.request(BASE + '/ac/v2/unknown'), h.request(BASE + '/ac/v2/rcs/rvs/vehicleStatus?proxy=evil'), h.request(BASE + '/ac/v2/rcs/rvs/vehicleStatus#fragment'), h.request('https://api.telematics.hyundaiusa.com.evil.example/ac/v2/rcs/rvs/vehicleStatus'), h.request('https://a:b@api.telematics.hyundaiusa.com/ac/v2/rcs/rvs/vehicleStatus'), h.request(API + 'rcs/rdo/off', 'GET'), h.request(API + 'rcs/rvs/vehicleStatus', 'GET', { Authorization: 'secret' }), h.request(API + 'rcs/rvs/vehicleStatus', 'GET', { accept: 'ok\r\nEvil: value' }), h.request(API + 'rcs/rvs/vehicleStatus', 'GET', {}, '{}'), h.request(BASE + '/v2/ac/oauth/token', 'POST', {}, 'a'.repeat(8193))];
  for (const x of invalid) h.send(x); assert.equal(h.calls.length, 0); assert.equal(h.window.messages.filter(m => m.data.error).length, invalid.length);
});

check('Browser helper ignores messages from other origins, windows or session channels', async () => {
  const h = helperHarness(), valid = h.request(API + 'rcs/rvs/vehicleStatus'); h.send(valid, { origin: 'https://evil.example' }); h.send(valid, { source: {} }); h.send({ ...valid, channel: 'unauthorized-channel' }); assert.equal(h.calls.length, 0);
  h.send(valid); assert.equal(h.calls.length, 1); assert.equal(h.calls[0].anonymous, true); assert.equal(h.calls[0].redirect, 'error');
});

check('Browser helper honors cancellation and rejects third concurrent request', async () => {
  const h = helperHarness(), valid = h.request(API + 'rcs/rvs/vehicleStatus'); h.send({ ...valid, id: 'first' }); h.send({ ...valid, id: 'second' }); h.send({ ...valid, id: 'third' }); assert.equal(h.calls.length, 2); assert.match(h.window.messages.at(-1).data.error, /simultaneous/);
  h.send({ id: 'first', cancel: true }); assert.equal(h.aborts.length, 1); h.send({ ...valid, id: 'fourth' }); assert.equal(h.calls.length, 3);
});

check('Browser helper suppresses response bodies after an unexpected cross-origin redirect', async () => {
  const h = helperHarness(); h.send(h.request(API + 'rcs/rvs/vehicleStatus')); h.calls[0].onload({ status: 200, finalUrl: 'https://evil.example/', responseText: 'PRIVATE REDIRECT CONTENT', responseHeaders: '' });
  const msg = h.window.messages.at(-1).data; assert.match(msg.error, /redirect/); assert.equal(msg.response, undefined);
});
