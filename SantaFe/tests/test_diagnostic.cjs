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
function harness(responses = [], { mode = 'direct' } = {}) {
  const elements = new Map();
  const html = fs.readFileSync(path.join(ROOT, 'index.html'), 'utf8');
  for (const m of html.matchAll(/<([a-z]+)\b([^>]*\bid="([^"]+)"[^>]*)>/gi)) {
    const e = new Element(m[1]); e.disabled = /\bdisabled(?:\s|>)/.test(m[2] + '>');
    e.value = m[2].match(/\bvalue="([^"]*)"/)?.[1] || ''; elements.set(m[3], e);
  }
  elements.get('transportMode').value = mode; elements.get('commandSelect').value = 'lock';
  const window = makeWindow(), requests = [], storage = new Map(), timers = new Set();
  const context = { window, document: { getElementById: id => elements.get(id), createElement: tag => new Element(tag) },
    location: { origin: ORIGIN }, crypto: { randomUUID }, AbortController, URL, Blob, performance, Date,
    localStorage: { setItem: (k, v) => storage.set(k, v), getItem: k => storage.get(k), removeItem: k => storage.delete(k) },
    navigator: { clipboard: { writeText: async v => { context.copied = v; } } },
    setTimeout(fn, ms) { const timer = setTimeout(fn, ms); timer.unref(); timers.add(timer); return timer; },
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
  return { get, window, requests, responses, context, storage,
    async action(id, type = 'click') { get(id).emit(type); await idle(); },
    async idle() { await idle(); },
    report() { return JSON.parse(storage.get('santafe-diagnostics-v1') || '[]'); },
    async exported() { await this.action('copyLogBtn'); return JSON.parse(context.copied); },
    close() { for (const timer of timers) clearTimeout(timer); },
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
    assert.match(h.get('notice').textContent, /helper|setup/i);
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

check('Login, enrollment and cached status run in order and clear credentials', async t => {
  const h = harness(loginFixtures()); t.after(() => h.close()); await login(h);
  assert.equal(h.requests.length, 3); assert.deepEqual(h.requests.map(r => r.url), [BASE + '/v2/ac/oauth/token', API + 'enrollment/details/' + encodeURIComponent(creds.username), API + 'rcs/rvs/vehicleStatus']);
  assert.equal(h.get('password').value, ''); assert.equal(h.get('pin').value, '');
  assert.equal(h.get('sessionStatus').textContent, 'Signed in for this tab');
  assert.equal(h.get('vehicleSelect').disabled, false, 'A signed-in user must be able to select an enrolled vehicle');
  assert.equal(h.get('commandBtn').disabled, false); assert.match(h.get('vehicleSummary').textContent, /cached|timestamp/i);
  assert.equal(h.requests.filter(r => r.method === 'POST').length, 1, 'Login must never trigger remote commands');
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
