'use strict';
// Actual Chromium UI verification with fixture HTTP and GM responses only.
// This deliberately does not authenticate to Hyundai or operate any vehicle.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { chromium } = require(path.join(process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES || '/opt/codex/runtimes/codex-primary-runtime/dependencies/node/node_modules', 'playwright'));
const ROOT = path.resolve(__dirname, '..');
const OUT = path.join(ROOT, 'docs/verification');
const EXE = process.env.SANTAFE_CHROMIUM || '/tmp/santafe-browser/chrome-linux64/chrome';
const API = 'https://api.telematics.hyundaiusa.com';
const USER = 'fixture.user+privacy@example.com', PASSWORD = 'Fixture-pass-!"\\2026', PIN = '4628';
const V1 = { vin: '5NMP54G18TH123456', regid: 'fixture-reg-123', nickName: 'Fixture Santa Fe', evStatus: 'P', vehicleGeneration: 3, enrollmentStatus: 'ACTIVE' };
const V2 = { ...V1, vin: '5NMP54G18TH987654', regid: 'fixture-reg-456', nickName: 'Second fixture Santa Fe' };
const results = [], unexpected = [];
const mime = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css' };
const server = http.createServer((req, res) => {
  const pathname = new URL(req.url, 'http://localhost').pathname;
  if (!['/SantaFe/index.html', '/SantaFe/diagnostic.js', '/SantaFe/diagnostic.css', '/SantaFe/santafe-network.user.js'].includes(pathname)) { res.writeHead(404); res.end(); return; }
  const file = path.join(ROOT, path.basename(pathname)); res.writeHead(200, { 'content-type': mime[path.extname(file)] }); res.end(fs.readFileSync(file));
});
const basics = (two = false) => [{ data: { access_token: 'fixture-access-secret', refresh_token: 'fixture-refresh-secret', expires_in: 1800 } }, { data: { enrolledVehicleDetails: [V1, ...(two ? [V2] : [])].map(vehicleDetails => ({ vehicleDetails })) } }, { data: { vehicleStatus: { dateTime: '2026-10-04T19:00:00-04:00', doorLock: true, engine: false, fuelLevel: 42, location: { latitude: 42.04684, longitude: -71.11242 } } } }];
const enrollmentFailure = { status: 502, data: { errorCode: 502, errorMessage: 'Service error', errorSubCode: 'C500', errorSubMessage: 'NO DATA FOUND TO PERFORM THIS OPERATION', functionName: 'getEnrollmentDetailsByUser' } };
const wrongPassword = { status: 502, data: { errorCode: 502, errorMessage: 'Incorrect username or password', errorSubCode: 'IDM_401_1', errorSubMessage: 'Username or password is incorrect' } };
const encodedEnrollmentUrl = () => API + '/ac/v2/enrollment/details/' + encodeURIComponent(USER);
const literalEnrollmentUrl = () => encodedEnrollmentUrl().replace(/%40/g, '@');
async function scenario(name, fn) { const start = performance.now(); try { await fn(); results.push({ test: name, passed: true, elapsed_ms: Math.round(performance.now() - start) }); } catch (e) { results.push({ test: name, passed: false, error: e.message }); process.stderr.write(`${name}: ${e.message}\n`); } }
let browser, url;
async function pageFixture(fixtures, opts = {}) {
  const context = await browser.newContext({ viewport: opts.mobile ? { width: 412, height: 915 } : { width: 1440, height: 1100 }, deviceScaleFactor: 1, permissions: ['clipboard-read', 'clipboard-write'] });
  const page = await context.newPage(), calls = [], errors = [];
  page.on('pageerror', error => errors.push(error.message)); page.on('dialog', dialog => dialog.accept());
  await page.route('**/*', async route => {
    const req = route.request();
    if(opts.native && req.url().startsWith('https://santafe.local/')){
      const pathname=new URL(req.url()).pathname;if(!['/index.html','/diagnostic.js','/diagnostic.css'].includes(pathname)){unexpected.push(req.url());return route.abort();}
      const file=path.join(ROOT,path.basename(pathname));return route.fulfill({status:200,headers:{'content-type':mime[path.extname(file)],'content-security-policy':"default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'"},body:fs.readFileSync(file)});
    }
    if (req.url().startsWith(url.replace('/SantaFe/index.html', ''))) return route.continue();
    if (!req.url().startsWith(API + '/')) { unexpected.push(req.url()); return route.abort(); }
    if (opts.helper || opts.native) { unexpected.push('Bridge mode unexpectedly used fetch: ' + req.url()); return route.abort(); }
    if (req.method() === 'OPTIONS') return route.fulfill({ status: 204, headers: { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*', 'access-control-allow-methods': 'GET, POST, OPTIONS' } });
    calls.push({ url: req.url(), method: req.method(), headers: req.headers(), body: req.postData() });
    const next = fixtures.shift(); if (!next) { unexpected.push('Fixture queue exhausted: ' + req.url()); return route.abort(); }
    if (next.abort) return route.abort('failed');
    if (next.delay) await new Promise(resolve => { next.release = resolve; });
    return route.fulfill({ status: next.status || 200, contentType: 'application/json', headers: { 'access-control-allow-origin': '*', 'access-control-expose-headers': '*', ...next.headers }, body: next.text !== undefined ? next.text : JSON.stringify(next.data || {}) });
  });
  if (opts.helper) {
    const shim = `window.__testRequests=[]; window.__testFixtures=${JSON.stringify(fixtures)};
      window.GM_xmlhttpRequest = opts => { window.__testRequests.push({url:opts.url,method:opts.method,headers:opts.headers,body:opts.data});
        const next = window.__testFixtures.shift(); let cancelled=false;
        const timer=setTimeout(()=>{ if(cancelled)return; if(!next){opts.onerror();return;}
          if(next.abort){opts.onerror();return;}
          opts.onload({status:next.status||200,finalUrl:opts.url,responseText:next.text!==undefined?next.text:JSON.stringify(next.data||{}),responseHeaders:Object.entries(next.headers||{}).map(([k,v])=>k+': '+v).join('\\r\\n')}); },5);
        return {abort:()=>{cancelled=true;clearTimeout(timer);opts.onabort();}};};\n`;
    await page.addInitScript({ content: shim + fs.readFileSync(path.join(ROOT, 'santafe-network.user.js'), 'utf8') });
  }
  if(opts.native)await page.addInitScript({content:`window.__testRequests=[];window.__testFixtures=${JSON.stringify(fixtures)};window.__testExports=[];window.__testCancels=[];window.__testNativeCallbacks={};window.__testAccountCalls={loads:0,saves:[],forgets:0};
    window.SantaFeNative={request(json){const envelope=JSON.parse(json),next=window.__testFixtures.shift();window.__testRequests.push({...envelope.request,id:envelope.id});
      const respond=()=>{if(!next){window.SantaFeAndroid.onResponse(envelope.id,null,'Fixture queue exhausted.');return;}
        const response=next.rawResponse || {status:next.status || 200,text:next.text!==undefined?next.text:JSON.stringify(next.data || {}),headers:next.headers || {}};
        window.SantaFeAndroid.onResponse(envelope.id,response,next.error || null);};window.__testNativeCallbacks[envelope.id]=respond;if(!next?.delay)setTimeout(respond,5);},
      cancel(id){window.__testCancels.push(id);},exportLog(json){window.__testExports.push(JSON.parse(json));return true;}};
    ${opts.account ? `
      if(!sessionStorage.getItem('fixture-account-initialized')){sessionStorage.setItem('fixture-account-initialized','1');${opts.account.value ? `sessionStorage.setItem('fixture-native-account',${JSON.stringify(JSON.stringify(opts.account.value))});` : ''}}
      Object.assign(window.SantaFeNative,{
        loadAccount(){window.__testAccountCalls.loads++;return ${opts.account.loadError ? `JSON.stringify({saved:false,error:${JSON.stringify(opts.account.loadError)}})` : `(sessionStorage.getItem('fixture-native-account')?JSON.stringify({saved:true,...JSON.parse(sessionStorage.getItem('fixture-native-account'))}):JSON.stringify({saved:false}))`};},
        saveAccount(json){window.__testAccountCalls.saves.push(JSON.parse(json));${opts.account.saveFailure ? 'return false;' : `sessionStorage.setItem('fixture-native-account',json);return true;`}},
        forgetAccount(){window.__testAccountCalls.forgets++;${opts.account.forgetFailure ? 'return false;' : `sessionStorage.removeItem('fixture-native-account');return true;`}}
      });` : ''}`});
  await page.goto(opts.native?'https://santafe.local/index.html':url); await page.waitForFunction(() => document.getElementById('sessionStatus')?.textContent === 'Signed out');
  if(opts.native)await page.waitForFunction(()=>document.getElementById('transportStatus').textContent==='Direct Hyundai connection');
  else if (opts.helper) await page.waitForFunction(() => document.getElementById('transportStatus').textContent === 'Browser helper ready');
  else if ((opts.mode || 'direct') !== 'auto') {await page.locator('#manualBrowserSetup').evaluate(el=>{el.open=true;});await page.selectOption('#transportMode', opts.mode || 'direct');}
  return { page, context, calls, errors, async requests() { return opts.helper || opts.native ? page.evaluate(() => window.__testRequests) : calls; }, async close() { await context.close(); } };
}
async function idle(page) { await page.waitForFunction(() => document.getElementById('stopBtn').disabled); }
async function login(page) { await page.locator('#accountSettings').evaluate(el=>{el.open=true;}); await page.fill('#email', USER); await page.fill('#password', PASSWORD); await page.fill('#pin', PIN); await page.click('#loginBtn'); await idle(page); }
async function controls(page, action = 'lock') { await page.locator('.command-panel').evaluate(el => { el.open = true; }); await page.selectOption('#commandSelect', action); await page.check('#confirmCommand'); await page.click('#commandBtn'); await idle(page); }
async function exported(page) {
  const pending = page.waitForEvent('download'); await page.click('#downloadLogBtn'); const download = await pending; return JSON.parse(fs.readFileSync(await download.path(), 'utf8'));
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true }); await new Promise(resolve => server.listen(0, '127.0.0.1', resolve)); url = `http://127.0.0.1:${server.address().port}/SantaFe/index.html`;
  browser = await chromium.launch({ executablePath: EXE, headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'] });
  await scenario('Default Automatic setup precedes login and blocks Enter/programmatic submits without network', async () => {
    const h = await pageFixture([], { mode: 'auto' }); try {
      assert.equal(await h.page.inputValue('#transportMode'), 'auto');
      assert.equal(await h.page.locator('#connectionSetup').evaluate(el => el.open), true);
      assert.equal(await h.page.locator('#connectionSetup').evaluate(el => Boolean(el.compareDocumentPosition(document.getElementById('loginForm')) & Node.DOCUMENT_POSITION_FOLLOWING)), true);
      for (const id of ['email', 'password', 'pin', 'loginBtn']) assert.equal(await h.page.isEnabled('#' + id), false, `${id} enabled before helper installation`);
      await h.page.keyboard.press('Enter');
      await h.page.evaluate(({ user, password, pin }) => {
        document.getElementById('email').value = user; document.getElementById('password').value = password; document.getElementById('pin').value = pin;
        document.getElementById('loginForm').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
        document.getElementById('loginForm').requestSubmit();
      }, { user: USER, password: PASSWORD, pin: PIN });
      await idle(h.page); assert.equal((await h.requests()).length, 0); assert.equal(await h.page.isEnabled('#loginBtn'), false); assert.deepEqual(h.errors, []);
      await h.page.locator('#manualBrowserSetup').evaluate(el=>{el.open=true;});
      await h.page.selectOption('#transportMode', 'direct');
      for (const id of ['email', 'password', 'pin', 'loginBtn']) assert.equal(await h.page.isEnabled('#' + id), true, `${id} stayed disabled after explicit direct diagnostics`);
    } finally { await h.close(); }
  });
  await scenario('Direct desktop login, safe cache reads, sanitized download and disconnect', async () => {
    const h = await pageFixture(basics()); try {
      await login(h.page); assert.equal((await h.requests()).length, 3); assert.equal(await h.page.inputValue('#password'), ''); assert.equal(await h.page.inputValue('#pin'), ''); assert.equal(await h.page.isEnabled('#vehicleSelect'), true); assert.equal(await h.page.isEnabled('#commandBtn'), true);
      const raw = JSON.stringify(await exported(h.page)); for (const value of [USER, encodeURIComponent(USER), PASSWORD, PIN, V1.vin, V1.regid, 'fixture-access-secret', 'fixture-refresh-secret', '42.04684', '-71.11242']) assert.equal(raw.includes(value), false, `Export leaked ${value}`);
      assert.equal((await h.requests()).filter(r => r.method === 'POST').length, 1);
      await h.page.screenshot({ path: path.join(OUT, 'diagnostic-desktop.png'), fullPage: true });
      await h.page.click('#disconnectBtn'); assert.equal(await h.page.inputValue('#email'), ''); assert.equal(await h.page.isEnabled('#commandBtn'), false); assert.deepEqual(h.errors, []);
    } finally { await h.close(); }
  });
  await scenario('Bundled Android mobile tester uses native bridge under connect-src none and exports sanitized log',async()=>{
    const h=await pageFixture(basics(),{native:true,mobile:true});try{
      for(const id of ['connectionSetup','appDownload','copyLogBtn'])assert.equal(await h.page.locator('#'+id).isVisible(),false,`${id} should be hidden inside app`);
      for(const id of ['email','password','pin','loginBtn'])assert.equal(await h.page.isEnabled('#'+id),true);
      await login(h.page);assert.equal((await h.requests()).length,3);assert.equal((await h.requests())[1].url,literalEnrollmentUrl());assert.equal((await h.requests())[1].url.includes('%2B'),true);assert.equal(h.calls.length,0);assert.equal(await h.page.locator('#sessionStatus').innerText(),'Signed in for this app');
      await h.page.click('#downloadLogBtn');assert.match(await h.page.locator('#notice').innerText(),/Choose where to save/);
      const report=await h.page.evaluate(()=>window.__testExports.at(-1)),raw=JSON.stringify(report);
      for(const value of [USER,encodeURIComponent(USER),PASSWORD,PIN,V1.vin,V1.regid,'fixture-access-secret','fixture-refresh-secret','42.04684','-71.11242'])assert.equal(raw.includes(value),false,`Native export leaked ${value}`);
      assert.equal(report.version,'0.3.3');assert.ok(report.requests.filter(r=>r.transport).every(r=>r.transport==='native Android'));
      await h.page.evaluate(()=>window.SantaFeAndroid.onExportResult(true,null));assert.equal(await h.page.locator('#notice').innerText(),'Sanitized log saved.');
      const layout=await h.page.evaluate(()=>({client:document.documentElement.clientWidth,scroll:document.documentElement.scrollWidth}));assert.ok(layout.scroll<=layout.client+1);
      await h.page.screenshot({path:path.join(OUT,'diagnostic-android-fixture.png'),fullPage:true});
      await h.page.click('#disconnectBtn');assert.equal(await h.page.inputValue('#email'),'');assert.deepEqual(h.errors,[]);
    }finally{await h.close();}
  });
  await scenario('Native account settings save once, reload masked details, then connect and reconnect without retyping', async () => {
    const h = await pageFixture([...basics(), ...basics()], { native: true, mobile: true, account: { value: null } }); try {
      assert.equal((await h.requests()).length, 0); assert.equal(await h.page.locator('#savedAccountControls').isVisible(), true);
      await h.page.fill('#email', USER); await h.page.fill('#password', PASSWORD); await h.page.fill('#pin', PIN);
      await h.page.click('#saveAccountBtn');
      assert.equal((await h.requests()).length, 0); assert.equal(await h.page.inputValue('#password'), ''); assert.equal(await h.page.inputValue('#pin'), '');
      assert.equal(await h.page.locator('#accountSettings').evaluate(el=>el.open), false);
      await h.page.reload(); await h.page.waitForFunction(()=>document.getElementById('sessionStatus').textContent==='Signed out');
      assert.equal((await h.requests()).length, 0); assert.equal(await h.page.inputValue('#email'), USER);
      assert.equal(await h.page.inputValue('#password'), ''); assert.equal(await h.page.inputValue('#pin'), '');
      assert.equal(await h.page.locator('#accountSettings').evaluate(el=>el.open), false);
      await h.page.click('#loginBtn'); await idle(h.page); assert.equal((await h.requests()).length, 3);
      assert.deepEqual(JSON.parse((await h.requests())[0].body), { username: USER, password: PASSWORD });
      await h.page.click('#disconnectBtn'); assert.equal(await h.page.inputValue('#email'), USER); assert.equal((await h.requests()).length, 3);
      await h.page.click('#loginBtn'); await idle(h.page); assert.equal((await h.requests()).length, 6);
      assert.equal(await h.page.locator('#sessionStatus').innerText(), 'Signed in for this app');
      await h.page.click('#downloadLogBtn');
      const raw=await h.page.evaluate(()=>JSON.stringify({logs:window.__testExports.at(-1),storage:Object.fromEntries(Object.entries(localStorage))}));
      for(const value of [USER, PASSWORD, PIN, 'fixture-access-secret', V1.vin])assert.equal(raw.includes(value),false,'Saved-account flow leaked a secret into exported log or app web storage');
      const layout=await h.page.evaluate(()=>({client:document.documentElement.clientWidth,scroll:document.documentElement.scrollWidth}));assert.ok(layout.scroll<=layout.client+1);
      await h.page.screenshot({path:path.join(OUT,'diagnostic-saved-account-mobile.png'),fullPage:true});assert.deepEqual(h.errors,[]);
    } finally { await h.close(); }
  });
  await scenario('Saved native account supports consecutive deliberate commands and export without another login', async () => {
    const h=await pageFixture([...basics(),{text:'',headers:{TmsTid:'unlock-fixture-tx'}},{data:{status:'SUCCESS'}},{text:'',headers:{TmsTid:'lock-fixture-tx'}},{data:{status:'SUCCESS'}}],{native:true,mobile:true,account:{value:{username:USER,password:PASSWORD,pin:PIN}}});try{
      assert.equal((await h.requests()).length,0);await h.page.click('#loginBtn');await idle(h.page);
      await controls(h.page,'unlock');await h.page.click('#pollBtn');await idle(h.page);
      await h.page.click('#downloadLogBtn');await h.page.evaluate(()=>window.SantaFeAndroid.onExportResult(true,null));
      assert.equal(await h.page.locator('#sessionStatus').innerText(),'Signed in for this app');
      await controls(h.page,'lock');await h.page.click('#pollBtn');await idle(h.page);
      const requests=await h.requests();assert.equal(requests.length,7);assert.equal(requests.filter(r=>r.url.endsWith('/oauth/token')).length,1);
      assert.equal(requests[3].url,API+'/ac/v2/rcs/rdo/on');assert.equal(requests[5].url,API+'/ac/v2/rcs/rdo/off');
      assert.equal(await h.page.inputValue('#password'),'');assert.equal(await h.page.inputValue('#pin'),'');assert.deepEqual(h.errors,[]);
    }finally{await h.close();}
  });
  await scenario('Native saved-account load and save failures stay generic and never cause automatic requests', async () => {
    const load=await pageFixture([],{native:true,account:{value:null,loadError:'private native failure '+PASSWORD}});try{
      assert.equal((await load.requests()).length,0);assert.match(await load.page.locator('#notice').innerText(),/saved account.*read/i);
      assert.equal((await load.page.locator('#notice').innerText()).includes(PASSWORD),false);
      await load.page.click('#loginBtn');assert.equal((await load.requests()).length,0);assert.deepEqual(load.errors,[]);
    }finally{await load.close();}
    const save=await pageFixture(basics(),{native:true,account:{value:null,saveFailure:true}});try{
      await login(save.page);assert.equal((await save.requests()).length,3);assert.equal(await save.page.locator('#sessionStatus').innerText(),'Signed in for this app');
      assert.match(await save.page.locator('#notice').innerText(),/could not confirm saving/i);assert.equal(await save.page.inputValue('#password'),'');assert.equal(await save.page.inputValue('#pin'),'');
      assert.equal(await save.page.evaluate(()=>sessionStorage.getItem('fixture-native-account')),null);assert.deepEqual(save.errors,[]);
    }finally{await save.close();}
  });
  await scenario('Forgetting native account removes saved details and keeps the interrupted-command guard', async () => {
    const h=await pageFixture([],{native:true,account:{value:{username:USER,password:PASSWORD,pin:PIN}}});try{
      await h.page.evaluate(()=>localStorage.setItem('santafe-pending-command-v1',JSON.stringify({action:'unlock',at:'2026-10-05T04:00:00Z'})));
      await h.page.reload();await h.page.waitForFunction(()=>!document.getElementById('unknownCommandPanel').hidden);
      await h.page.locator('#accountSettings').evaluate(el=>{el.open=true;});await h.page.click('#forgetAccountBtn');
      assert.equal((await h.requests()).length,0);assert.equal(await h.page.inputValue('#email'),'');assert.equal(await h.page.inputValue('#password'),'');assert.equal(await h.page.inputValue('#pin'),'');
      assert.equal(await h.page.locator('#unknownCommandPanel').isVisible(),true);assert.equal(await h.page.isEnabled('#commandBtn'),false);
      assert.equal(await h.page.evaluate(()=>sessionStorage.getItem('fixture-native-account')),null);assert.notEqual(await h.page.evaluate(()=>localStorage.getItem('santafe-pending-command-v1')),null);assert.deepEqual(h.errors,[]);
    }finally{await h.close();}
  });
  await scenario('Native Remember account unchecked keeps the connection transient and saves no credentials',async()=>{
    const h=await pageFixture(basics(),{native:true,account:{value:null}});try{
      await h.page.uncheck('#rememberAccount');await login(h.page);assert.equal((await h.requests()).length,3);
      assert.equal(await h.page.evaluate(()=>window.__testAccountCalls.saves.length),0);assert.equal(await h.page.evaluate(()=>sessionStorage.getItem('fixture-native-account')),null);
      await h.page.click('#disconnectBtn');assert.equal(await h.page.inputValue('#email'),'');
      await h.page.click('#loginBtn');assert.equal((await h.requests()).length,3);assert.equal(await h.page.locator('#accountSettings').evaluate(el=>el.open),true,'Connect must expose required unsaved credentials for manual reentry');assert.deepEqual(h.errors,[]);
    }finally{await h.close();}
  });
  await scenario('Native enrollment and persisted old logs scrub device identifiers while retaining model and protocol fields', async () => {
    const privateFields = { enrollmentId: 'browser-fixture-enrollment-741', nadid: 'browser-fixture-nad-153', mit: 'browser-fixture-mit-284', imat: 'browser-fixture-imat-395', hataTID: 'browser-fixture-hata-416', guid: 'browser-fixture-guid-527', packageId: 'browser-fixture-package-638', assetNumber: 'browser-fixture-asset-749', billingAccountNumber: 'browser-fixture-billing-851', idmId: 'browser-fixture-idm-962' };
    const visibleFields = { ccuCCS2ProtocolSupport: true, trim: 'CALLIGRAPHY', modelYear: '2026' };
    const fixture = basics(); Object.assign(fixture[1].data.enrolledVehicleDetails[0].vehicleDetails, privateFields, visibleFields, { debugEcho: Object.values(privateFields).join(' ') });
    const h = await pageFixture(fixture, { native: true, mobile: true }); try {
      await login(h.page); assert.equal((await h.requests())[1].url, literalEnrollmentUrl());
      await h.page.click('#downloadLogBtn'); let report = await h.page.evaluate(() => window.__testExports.at(-1));
      const record = report.requests.find(r => r.label === 'Vehicle enrollment').response.enrolledVehicleDetails[0].vehicleDetails;
      for (const [key, value] of Object.entries(privateFields)) { assert.equal(record[key], '[REDACTED]'); assert.equal(JSON.stringify(report).includes(value), false, `${key} leaked`); }
      for (const [key, value] of Object.entries(visibleFields)) assert.equal(record[key], value);
      assert.equal(report.requests.some(r => r.label === 'Reported capability fields' && r.fields.some(f => f.field === 'vehicleDetails.ccuCCS2ProtocolSupport' && f.value === true)), true);
      await h.page.evaluate(fields => localStorage.setItem('santafe-diagnostics-v1', JSON.stringify([{ label: 'Old enrollment', debugEcho: Object.values(fields.privateFields).join(' '), response: { ...fields.privateFields, ...fields.visibleFields } }])), { privateFields, visibleFields });
      await h.page.reload(); await h.page.waitForFunction(() => document.getElementById('sessionStatus').textContent === 'Signed out');
      await h.page.click('#downloadLogBtn'); report = await h.page.evaluate(() => window.__testExports.at(-1));
      for (const [key, value] of Object.entries(privateFields)) { assert.equal(report.requests[0].response[key], '[REDACTED]'); assert.equal(JSON.stringify(report).includes(value), false); }
      const saved = await h.page.evaluate(() => localStorage.getItem('santafe-diagnostics-v1'));
      for (const value of Object.values(privateFields)) assert.equal(saved.includes(value), false, 'Previously persisted identifier was not removed');
      for (const [key, value] of Object.entries(visibleFields)) assert.equal(report.requests[0].response[key], value);
      assert.equal((await h.requests()).length, 0); assert.deepEqual(h.errors, []);
    } finally { await h.close(); }
  });
  await scenario('Native Stop waiting cancels exactly once and ignores a late success callback',async()=>{
    const h=await pageFixture([{...basics()[0],delay:true}],{native:true});try{
      await h.page.locator('#accountSettings').evaluate(el=>{el.open=true;});await h.page.fill('#email',USER);await h.page.fill('#password',PASSWORD);await h.page.fill('#pin',PIN);await h.page.click('#loginBtn');
      await h.page.waitForFunction(()=>window.__testRequests.length===1);await h.page.click('#stopBtn');await idle(h.page);
      await h.page.evaluate(()=>window.__testNativeCallbacks[window.__testRequests[0].id]());await idle(h.page);
      assert.equal((await h.requests()).length,1);assert.equal(await h.page.evaluate(()=>window.__testCancels.length),1);assert.equal(await h.page.locator('#sessionStatus').innerText(),'Signed out');assert.equal(await h.page.inputValue('#password'),'');assert.deepEqual(h.errors,[]);
    }finally{await h.close();}
  });
  await scenario('Mobile native login succeeds before enrollment C500 and a deliberate lookup reuses the token once', async () => {
    const fixture = basics(), h = await pageFixture([fixture[0], enrollmentFailure, { ...fixture[1], delay: true }, fixture[2], fixture[1], fixture[2]], { native: true, mobile: true }); try {
      assert.equal(await h.page.isEnabled('#referenceEnrollmentBtn'), false); await login(h.page);
      assert.equal((await h.requests()).length, 2); assert.equal(await h.page.locator('#sessionStatus').innerText(), 'Signed in for this app');
      assert.equal(await h.page.isEnabled('#referenceEnrollmentBtn'), true); assert.equal(await h.page.isEnabled('#commandBtn'), false);
      for (const fragment of [/Vehicle enrollment/i, /HTTP 502/, /C500/, /NO DATA FOUND TO PERFORM THIS OPERATION/i]) assert.match(await h.page.locator('#notice').innerText(), fragment);
      await h.page.screenshot({ path: path.join(OUT, 'diagnostic-enrollment-502.png'), fullPage: true });
      await h.page.click('#referenceEnrollmentBtn'); await h.page.waitForFunction(() => window.__testRequests.length === 3);
      for (const id of ['referenceEnrollmentBtn', 'runReadTestsBtn', 'loginBtn', 'disconnectBtn', 'commandBtn']) assert.equal(await h.page.isEnabled('#' + id), false);
      await h.page.evaluate(() => { document.getElementById('referenceEnrollmentBtn').dispatchEvent(new Event('click')); document.getElementById('runReadTestsBtn').dispatchEvent(new Event('click')); });
      assert.equal((await h.requests()).length, 3, 'Programmatic competing clicks cannot bypass the busy guard');
      await h.page.evaluate(() => window.__testNativeCallbacks[window.__testRequests[2].id]()); await idle(h.page);
      let requests = await h.requests(); assert.equal(requests.length, 4); assert.equal(requests[2].url, encodedEnrollmentUrl()); assert.equal(requests[2].method, 'GET');
      assert.equal(requests[2].url.includes('%2B'), true);
      for (const key of ['username', 'accessToken', 'blueLinkServicePin']) assert.equal(requests[2].headers[key], requests[1].headers[key]);
      assert.equal(requests[3].headers.refresh, 'false'); assert.equal(await h.page.isEnabled('#commandBtn'), true); assert.equal(await h.page.isEnabled('#referenceEnrollmentBtn'), false);
      await h.page.click('#runReadTestsBtn'); await idle(h.page); requests = await h.requests(); assert.equal(requests.length, 6); assert.equal(requests[4].url, encodedEnrollmentUrl());
      assert.equal(requests.filter(r => r.method === 'POST').length, 1); assert.equal(h.calls.length, 0);
      await h.page.click('#downloadLogBtn'); const report = await h.page.evaluate(() => window.__testExports.at(-1)), raw = JSON.stringify(report);
      assert.deepEqual(report.requests.filter(r => /Vehicle enrollment/.test(r.label)).map(r => r.enrollment_path_format), ['literal-at', 'encoded-at', 'encoded-at']);
      for (const value of [USER, encodeURIComponent(USER), encodeURIComponent(USER).replace(/%40/g, '@'), PASSWORD, PIN, V1.vin, V1.regid, 'fixture-access-secret', 'fixture-refresh-secret']) assert.equal(raw.includes(value), false, `Lookup export leaked ${value}`);
      assert.equal(report.requests.some(r => r.label === 'Reported capability fields'), true); assert.match(raw, /enrollment\/details\/\[REDACTED\]/);
      const layout = await h.page.evaluate(() => ({ client: document.documentElement.clientWidth, scroll: document.documentElement.scrollWidth })); assert.ok(layout.scroll <= layout.client + 1);
      assert.deepEqual(h.errors, []);
    } finally { await h.close(); }
  });
  await scenario('Native wrong-password 502 preserves IDM_401_1 and the original message without a lookup or retry', async () => {
    const h = await pageFixture([wrongPassword], { native: true }); try {
      await login(h.page); assert.equal((await h.requests()).length, 1); assert.equal(await h.page.locator('#sessionStatus').innerText(), 'Signed out');
      for (const fragment of [/Login/i, /HTTP 502/, /IDM_401_1/, /Username or password is incorrect/]) assert.match(await h.page.locator('#notice').innerText(), fragment);
      for (const id of ['referenceEnrollmentBtn', 'runReadTestsBtn', 'commandBtn']) assert.equal(await h.page.isEnabled('#' + id), false);
      await h.page.evaluate(() => document.getElementById('referenceEnrollmentBtn').dispatchEvent(new Event('click'))); await idle(h.page); assert.equal((await h.requests()).length, 1);
      assert.deepEqual(h.errors, []);
    } finally { await h.close(); }
  });
  await scenario('Native failed or malformed alternate lookup keeps commands disabled and ordinary reads use the original format', async () => {
    for (const failure of [enrollmentFailure, { data: { vehicles: [V1] } }]) {
      const fixture = basics(), h = await pageFixture([fixture[0], enrollmentFailure, failure, fixture[1], fixture[2]], { native: true }); try {
        await login(h.page); await h.page.click('#referenceEnrollmentBtn'); await idle(h.page);
        assert.equal((await h.requests()).length, 3); assert.equal((await h.requests())[2].url, encodedEnrollmentUrl());
        assert.equal(await h.page.locator('#sessionStatus').innerText(), 'Signed in for this app'); assert.equal(await h.page.isEnabled('#commandBtn'), false); assert.equal(await h.page.isEnabled('#referenceEnrollmentBtn'), true);
        await h.page.click('#runReadTestsBtn'); await idle(h.page); const requests = await h.requests(); assert.equal(requests.length, 5);
        assert.equal(requests[3].url, literalEnrollmentUrl()); assert.equal(requests.filter(r => r.method === 'POST').length, 1);
        await h.page.click('#downloadLogBtn'); const report = await h.page.evaluate(() => window.__testExports.at(-1)); assert.deepEqual(report.requests.filter(r => /Vehicle enrollment/.test(r.label)).map(r => r.enrollment_path_format), ['literal-at', 'encoded-at', 'literal-at']);
        assert.deepEqual(h.errors, []);
      } finally { await h.close(); }
    }
  });
  await scenario('Native cached-status 502 displays its own request label and cannot enable enrollment fallback', async () => {
    const fixture = basics(), h = await pageFixture([fixture[0], fixture[1], enrollmentFailure], { native: true }); try {
      await login(h.page); assert.equal((await h.requests()).length, 3); assert.equal(await h.page.locator('#sessionStatus').innerText(), 'Signed in for this app');
      assert.match(await h.page.locator('#notice').innerText(), /Cached vehicle status/i); assert.match(await h.page.locator('#notice').innerText(), /C500/);
      assert.equal(await h.page.isEnabled('#referenceEnrollmentBtn'), false); assert.equal(await h.page.isEnabled('#commandBtn'), false); assert.deepEqual(h.errors, []);
    } finally { await h.close(); }
  });
  await scenario('Malformed native response and native TLS error do not authenticate, fall back or retry',async()=>{
    for(const fixture of [{rawResponse:{status:'200',text:'{}',headers:{}}},{error:'Hyundai TLS request failed.'}]){
      const h=await pageFixture([fixture],{native:true});try{
        await login(h.page);assert.equal((await h.requests()).length,1);assert.equal(h.calls.length,0);assert.equal(await h.page.isEnabled('#runReadTestsBtn'),false);assert.equal(await h.page.inputValue('#password'),'');assert.match(await h.page.locator('#notice').innerText(),/invalid response|TLS/);assert.deepEqual(h.errors,[]);
      }finally{await h.close();}
    }
  });
  await scenario('Native vehicle commands preserve transaction headers and require SUCCESS before releasing command gate',async()=>{
    const h=await pageFixture([...basics(),{text:'',headers:{TmsTid:'native-fixture-private-tx'}},{data:{status:'SUCCESS'}}],{native:true});try{
      await login(h.page);await controls(h.page,'horn_lights');assert.equal(await h.page.isEnabled('#commandBtn'),false);
      await h.page.click('#pollBtn');await idle(h.page);const requests=await h.requests();assert.equal(requests[4].headers.tid,'native-fixture-private-tx');assert.equal(requests[4].headers.service_type,'HORN_AND_LIGHTS');assert.equal(await h.page.isEnabled('#commandBtn'),true);assert.match(await h.page.locator('#notice').innerText(),/confirmed command completion/);assert.equal(await h.page.evaluate(()=>localStorage.getItem('santafe-pending-command-v1')),null);assert.deepEqual(h.errors,[]);
    }finally{await h.close();}
  });
  await scenario('Native reload restores a sanitized unknown-command guard and requires physical acknowledgement',async()=>{
    const h=await pageFixture([...basics(),{text:''}],{native:true});try{
      await login(h.page);await controls(h.page);const marker=await h.page.evaluate(()=>JSON.parse(localStorage.getItem('santafe-pending-command-v1')));assert.deepEqual(Object.keys(marker).sort(),['action','at']);assert.equal(marker.action,'lock');
      await h.page.reload();await h.page.waitForFunction(()=>document.getElementById('sessionStatus').textContent==='Signed out');assert.equal(await h.page.locator('#unknownCommandPanel').isVisible(),true);assert.equal(await h.page.inputValue('#email'),'');
      await login(h.page);assert.equal((await h.requests()).length,3);assert.equal(await h.page.isEnabled('#commandBtn'),false);
      await h.page.evaluate(()=>{document.getElementById('confirmCommand').checked=true;document.getElementById('commandBtn').dispatchEvent(new Event('click'));});await idle(h.page);assert.equal((await h.requests()).length,3);
      await h.page.click('#resolveUnknownBtn');assert.equal((await h.requests()).length,3);assert.equal(await h.page.isEnabled('#commandBtn'),true);assert.equal(await h.page.locator('#unknownCommandPanel').isVisible(),false);assert.equal(await h.page.evaluate(()=>localStorage.getItem('santafe-pending-command-v1')),null);
      assert.match(await h.page.locator('#notice').innerText(),/No command was sent or retried/);assert.deepEqual(h.errors,[]);
    }finally{await h.close();}
  });
  await scenario('Real postMessage helper handshake, automatic transport, raw transaction headers and SUCCESS', async () => {
    const h = await pageFixture([...basics(), { text: '', headers: { TmsTid: 'fixture-raw-header-id' } }, { data: { status: 'SUCCESS' } }, { text: '', headers: { transactionId: 'second-id' } }], { helper: true }); try {
      assert.equal(await h.page.inputValue('#transportMode'), 'auto'); await login(h.page); assert.equal((await h.requests()).length, 3); await controls(h.page, 'horn_lights'); let report = await exported(h.page); assert.equal(report.evidence.some(e => /reported SUCCESS/.test(e.scope)), false); assert.equal(await h.page.isEnabled('#commandBtn'), false);
      await h.page.click('#pollBtn'); await idle(h.page); const reqs = await h.requests(); assert.equal(reqs[4].headers.service_type, 'HORN_AND_LIGHTS'); assert.equal(reqs[4].headers.tid, 'fixture-raw-header-id'); report = await exported(h.page); assert.equal(report.evidence.some(e => /reported SUCCESS/.test(e.scope)), true); assert.equal(await h.page.isEnabled('#commandBtn'), true);
      await controls(h.page, 'lights'); assert.equal((await h.requests()).length, 6); assert.equal(report.requests.every(r => r.transport === 'browser helper' || !r.transport), true); assert.deepEqual(h.errors, []);
    } finally { await h.close(); }
  });
  await scenario('Two-vehicle picker can select the second record and uses its identifiers', async () => {
    const fixtures = basics(true); fixtures.splice(2, 1); fixtures.push(basics()[2]); const h = await pageFixture(fixtures); try {
      await login(h.page); assert.equal((await h.requests()).length, 2); assert.equal(await h.page.isEnabled('#vehicleSelect'), true); assert.equal(await h.page.isEnabled('#commandBtn'), false); await h.page.selectOption('#vehicleSelect', '1'); await h.page.click('#selectVehicleBtn'); await idle(h.page);
      const reqs = await h.requests(); assert.equal(reqs.length, 3); assert.equal(reqs[2].headers.vin, V2.vin); assert.equal(reqs[2].headers.registrationid, V2.regid); assert.equal(await h.page.isEnabled('#commandBtn'), true); assert.deepEqual(h.errors, []);
    } finally { await h.close(); }
  });
  await scenario('HTTP 200 without transaction ID is unresolved and cannot poll or resubmit', async () => {
    const h = await pageFixture([...basics(), { text: '' }]); try { await login(h.page); await controls(h.page); assert.equal((await h.requests()).length, 4); assert.equal(await h.page.isEnabled('#pollBtn'), false); assert.equal(await h.page.isEnabled('#commandBtn'), false); assert.match(await h.page.locator('#notice').innerText(), /unknown/); const report = await exported(h.page); assert.equal(report.evidence.some(e => /reported SUCCESS/.test(e.scope)), false); assert.deepEqual(h.errors, []); } finally { await h.close(); }
  });
  await scenario('429 produces one request and actionable rate-limit response', async () => {
    const h = await pageFixture([{ status: 429, data: { errorMessage: 'Rate limited' } }]); try { await login(h.page); assert.equal((await h.requests()).length, 1); assert.match(await h.page.locator('#notice').innerText(), /rate limited/); assert.equal(await h.page.inputValue('#password'), ''); assert.deepEqual(h.errors, []); } finally { await h.close(); }
  });
  await scenario('Direct blocked network response offers helper setup without retry', async () => {
    const h = await pageFixture([{ abort: true }]); try { await login(h.page); assert.equal((await h.requests()).length, 1); assert.match(await h.page.locator('#notice').innerText(), /CORS/); assert.match(await h.page.locator('#notice').innerText(), /helper/); assert.deepEqual(h.errors, []); } finally { await h.close(); }
  });
  await scenario('Empty login response cannot enable authenticated or command buttons', async () => {
    const h = await pageFixture([{ text: '' }]); try { await login(h.page); assert.equal((await h.requests()).length, 1); assert.match(await h.page.locator('#notice').innerText(), /No access token/); assert.equal(await h.page.isEnabled('#runReadTestsBtn'), false); assert.equal(await h.page.isEnabled('#commandBtn'), false); assert.deepEqual(h.errors, []); } finally { await h.close(); }
  });
  await scenario('Rapid double submit is gated while login is in flight', async () => {
    const fixture = { ...basics()[0], delay: true }, h = await pageFixture([fixture, basics()[1], basics()[2]]); try {
      await h.page.locator('#accountSettings').evaluate(el=>{el.open=true;});await h.page.fill('#email', USER); await h.page.fill('#password', PASSWORD); await h.page.fill('#pin', PIN); await h.page.evaluate(() => { document.getElementById('loginForm').requestSubmit(); document.getElementById('loginForm').requestSubmit(); });
      await h.page.waitForFunction(() => document.getElementById('loginBtn').disabled); assert.equal(h.calls.length, 1); fixture.release(); await idle(h.page); assert.equal(h.calls.length, 3); assert.deepEqual(h.errors, []);
    } finally { fixture.release?.(); await h.close(); }
  });
  await scenario('412px mobile layout avoids horizontal overflow and supports helper read tests', async () => {
    const h = await pageFixture(basics(), { helper: true, mobile: true }); try {
      await login(h.page); const layout = await h.page.evaluate(() => ({ client: document.documentElement.clientWidth, scroll: document.documentElement.scrollWidth })); assert.ok(layout.scroll <= layout.client + 1, `Mobile horizontal overflow: ${layout.scroll}/${layout.client}`); assert.equal((await h.requests()).length, 3); await h.page.screenshot({ path: path.join(OUT, 'diagnostic-mobile.png'), fullPage: true }); assert.deepEqual(h.errors, []);
    } finally { await h.close(); }
  });
})().catch(e => { results.push({ test: 'Browser harness', passed: false, error: e.message }); process.stderr.write(e.stack + '\n'); }).finally(async () => {
  if (browser) await browser.close(); await new Promise(resolve => server.close(resolve));
  fs.mkdirSync(OUT, { recursive: true }); fs.writeFileSync(path.join(OUT, 'diagnostic-browser-results.json'), JSON.stringify({ generated_at: new Date().toISOString(), browser: 'Actual headless Chromium', method: 'HTTP UI execution; Hyundai routes, GM transport and Android bridge use fixtures. Native UI uses HTTPS bundled origin with connect-src none. No actual Android WebView, real account login, API availability validation, or physical vehicle command', blocked_unexpected_requests: unexpected, results }, null, 2) + '\n');
  process.stdout.write(JSON.stringify({ passed: results.filter(r => r.passed).length, failed: results.filter(r => !r.passed).length, blocked_unexpected_requests: unexpected.length }) + '\n');
  process.exitCode = results.some(r => !r.passed) || unexpected.length ? 1 : 0;
});
