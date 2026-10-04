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
async function scenario(name, fn) { const start = performance.now(); try { await fn(); results.push({ test: name, passed: true, elapsed_ms: Math.round(performance.now() - start) }); } catch (e) { results.push({ test: name, passed: false, error: e.message }); process.stderr.write(`${name}: ${e.message}\n`); } }
let browser, url;
async function pageFixture(fixtures, opts = {}) {
  const context = await browser.newContext({ viewport: opts.mobile ? { width: 412, height: 915 } : { width: 1440, height: 1100 }, deviceScaleFactor: 1, permissions: ['clipboard-read', 'clipboard-write'] });
  const page = await context.newPage(), calls = [], errors = [];
  page.on('pageerror', error => errors.push(error.message)); page.on('dialog', dialog => dialog.accept());
  await page.route('**/*', async route => {
    const req = route.request();
    if (req.url().startsWith(url.replace('/SantaFe/index.html', ''))) return route.continue();
    if (!req.url().startsWith(API + '/')) { unexpected.push(req.url()); return route.abort(); }
    if (opts.helper) { unexpected.push('Helper mode unexpectedly used fetch: ' + req.url()); return route.abort(); }
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
  await page.goto(url); await page.waitForFunction(() => !!document.getElementById('loginBtn') && !document.getElementById('loginBtn').disabled);
  if (opts.helper) await page.waitForFunction(() => document.getElementById('transportStatus').textContent === 'Browser helper ready');
  else await page.selectOption('#transportMode', 'direct');
  return { page, context, calls, errors, async requests() { return opts.helper ? page.evaluate(() => window.__testRequests) : calls; }, async close() { await context.close(); } };
}
async function idle(page) { await page.waitForFunction(() => !document.getElementById('loginBtn').disabled); }
async function login(page) { await page.fill('#email', USER); await page.fill('#password', PASSWORD); await page.fill('#pin', PIN); await page.click('#loginBtn'); await idle(page); }
async function controls(page, action = 'lock') { await page.locator('.command-panel').evaluate(el => { el.open = true; }); await page.selectOption('#commandSelect', action); await page.check('#confirmCommand'); await page.click('#commandBtn'); await idle(page); }
async function exported(page) {
  const pending = page.waitForEvent('download'); await page.click('#downloadLogBtn'); const download = await pending; return JSON.parse(fs.readFileSync(await download.path(), 'utf8'));
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true }); await new Promise(resolve => server.listen(0, '127.0.0.1', resolve)); url = `http://127.0.0.1:${server.address().port}/SantaFe/index.html`;
  browser = await chromium.launch({ executablePath: EXE, headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'] });
  await scenario('Direct desktop login, safe cache reads, sanitized download and disconnect', async () => {
    const h = await pageFixture(basics()); try {
      await login(h.page); assert.equal((await h.requests()).length, 3); assert.equal(await h.page.inputValue('#password'), ''); assert.equal(await h.page.inputValue('#pin'), ''); assert.equal(await h.page.isEnabled('#vehicleSelect'), true); assert.equal(await h.page.isEnabled('#commandBtn'), true);
      const raw = JSON.stringify(await exported(h.page)); for (const value of [USER, encodeURIComponent(USER), PASSWORD, PIN, V1.vin, V1.regid, 'fixture-access-secret', 'fixture-refresh-secret', '42.04684', '-71.11242']) assert.equal(raw.includes(value), false, `Export leaked ${value}`);
      assert.equal((await h.requests()).filter(r => r.method === 'POST').length, 1);
      await h.page.screenshot({ path: path.join(OUT, 'diagnostic-desktop.png'), fullPage: true });
      await h.page.click('#disconnectBtn'); assert.equal(await h.page.inputValue('#email'), ''); assert.equal(await h.page.isEnabled('#commandBtn'), false); assert.deepEqual(h.errors, []);
    } finally { await h.close(); }
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
      await h.page.fill('#email', USER); await h.page.fill('#password', PASSWORD); await h.page.fill('#pin', PIN); await h.page.evaluate(() => { document.getElementById('loginForm').requestSubmit(); document.getElementById('loginForm').requestSubmit(); });
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
  fs.mkdirSync(OUT, { recursive: true }); fs.writeFileSync(path.join(OUT, 'diagnostic-browser-results.json'), JSON.stringify({ generated_at: new Date().toISOString(), browser: 'Actual headless Chromium', method: 'HTTP UI execution; Hyundai routes and GM transport use fixtures; no real account login, API availability validation, or physical vehicle command', blocked_unexpected_requests: unexpected, results }, null, 2) + '\n');
  process.stdout.write(JSON.stringify({ passed: results.filter(r => r.passed).length, failed: results.filter(r => !r.passed).length, blocked_unexpected_requests: unexpected.length }) + '\n');
  process.exitCode = results.some(r => !r.passed) || unexpected.length ? 1 : 0;
});
