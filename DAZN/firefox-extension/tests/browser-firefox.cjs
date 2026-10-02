'use strict';
// Actual installed-addon tests. web-ext installs the addon over Firefox RDP;
// Playwright drives the real Firefox page, preserving isolated extension worlds.
// No production script is evaluated/injected by the browser driver.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const https = require('node:https');
const net = require('node:net');
const { execFileSync } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const reports = path.join(root, 'reports');
const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'nhluk-firefox-'));
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const checks = [];
let server, proxy, browser, page, remote;
let browserVersion;

function firefoxBinary() {
  if (process.env.FIREFOX_BINARY) return process.env.FIREFOX_BINARY;
  for (const base of [process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES, null].filter(Boolean)) {
    try { return require(path.join(base, 'playwright')).firefox.executablePath(); } catch (_) {}
  }
  try { return require('playwright').firefox.executablePath(); } catch (_) {}
  return process.platform === 'win32' ? 'C:\\Program Files\\Mozilla Firefox\\firefox.exe' : 'firefox';
}
function playwright() {
  try { return require('playwright'); } catch (_) {}
  if (process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES) return require(path.join(process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES, 'playwright'));
  throw new Error('Install Playwright and its Firefox browser to run this suite.');
}
async function evaluate(expression) { return page.evaluate(`(async()=>(${expression}))()`); }
async function eventually(read, accept = Boolean, timeout = 10000) {
  const until = Date.now() + timeout;
  let result;
  do { result = await read(); if (accept(result)) return result; await delay(40); } while (Date.now() < until);
  throw new Error(`Condition not met: ${JSON.stringify(result)}`);
}
async function checked(name, work) {
  await work(); checks.push(name); console.log(`PASS ${name}`);
}
async function navigate(route = '/en-GB/home') {
  await page.goto(`https://www.dazn.com${route}`, {waitUntil:'load'});
  await eventually(() => evaluate(`document.documentElement.hasAttribute('data-fixture-ready')`));
}
let probeID = 0;
async function probe(action, args = {}) {
  const id = ++probeID;
  await evaluate(`(() => { document.dispatchEvent(new CustomEvent('nhluk:fixture-command', {detail:${JSON.stringify(JSON.stringify({ id, action, args }))}})); return true; })()`);
  return eventually(() => evaluate(`(() => { const raw=document.documentElement.getAttribute('data-fixture-result'); return raw ? JSON.parse(raw) : null; })()`), result => result?.id === id, 25000).then(result => {
    if (result.error) throw new Error(result.error); return result.value;
  });
}
async function screenshot(name) { await page.screenshot({path: path.join(reports, name)}); }
async function unusedPort() {
  const listener = net.createServer(); await new Promise(resolve => listener.listen(0, '127.0.0.1', resolve));
  const port = listener.address().port; await new Promise(resolve => listener.close(resolve)); return port;
}
async function main() {
  fs.mkdirSync(reports, { recursive: true });
  const stage = path.join(temp, 'extension'); fs.mkdirSync(stage);
  fs.cpSync(path.join(root, 'src'), path.join(stage, 'src'), { recursive: true });
  const manifest = JSON.parse(fs.readFileSync(path.join(root, 'manifest.json'), 'utf8'));
  const isolated = manifest.content_scripts.find(entry => entry.world !== 'MAIN');
  isolated.js.splice(isolated.js.indexOf('src/content/bootstrap.js'), 0, 'fixture-probe.js');
  fs.copyFileSync(path.join(__dirname, 'fixtures', 'probe.js'), path.join(stage, 'fixture-probe.js'));
  fs.writeFileSync(path.join(stage, 'manifest.json'), JSON.stringify(manifest, null, 2));
  execFileSync('openssl', ['req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-keyout', path.join(temp, 'key.pem'), '-out', path.join(temp, 'cert.pem'), '-days', '1', '-subj', '/CN=www.dazn.com', '-addext', 'subjectAltName=DNS:www.dazn.com,DNS:dazn.com,DNS:api-web.nhle.com,DNS:search.discovery.indazn.com'], { stdio: 'ignore' });
  const media = fs.readFileSync(path.join(__dirname, 'fixtures', 'replay.webm'));
  const provider = fs.readFileSync(path.join(__dirname, 'fixtures', 'provider.html'));
  server = https.createServer({ key: fs.readFileSync(path.join(temp, 'key.pem')), cert: fs.readFileSync(path.join(temp, 'cert.pem')) }, (req, res) => {
    if (req.url === '/favicon.ico') {res.writeHead(204);return res.end();}
    if (req.url.startsWith('/replay')) {
      if(process.env.NHLUK_DEBUG) console.log('MEDIA REQUEST',req.url,req.headers.range||'full');
      const match = /bytes=(\d+)-(\d*)/.exec(req.headers.range || '');
      const start = match ? Number(match[1]) : 0, end = match?.[2] ? Number(match[2]) : media.length - 1;
      res.writeHead(match ? 206 : 200, { 'Content-Type': 'video/webm', 'Accept-Ranges': 'bytes', 'Content-Length': end - start + 1, ...(match ? { 'Content-Range': `bytes ${start}-${end}/${media.length}` } : {}) });
      return res.end(media.subarray(start, end + 1));
    }
    if (req.headers.host === 'api-web.nhle.com') {
      res.writeHead(200, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
      return res.end(JSON.stringify({games:[{id:2026020001,gameType:2,startTimeUTC:'2026-10-01T23:00:00Z',gameDate:'2026-10-01',awayTeam:{abbrev:'BUF',score:99},homeTeam:{abbrev:'BOS',score:98},gameState:'SYNTHETIC_TAINT'}]}));
    }
    if (req.headers.host === 'search.discovery.indazn.com') { res.writeHead(200, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin':'*' }); return res.end(JSON.stringify({Results:[{Tiles:[{Title:'Buffalo Sabres vs Boston Bruins',Start:'2026-10-01T23:00:00Z',Type:'Replay',EventId:'event1',AssetId:'asset1',Description:'SYNTHETIC_TAINT_PROVIDER',Image:{Url:'/unsafe-image.png'}}]}]})); }
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' }); res.end(provider);
  });
  // Local fixture CONNECT tunnel preserves the exact HTTPS production origin.
  // Every accepted hostname terminates at our fixture; no traffic is forwarded externally.
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
  proxy = require('node:http').createServer((_req, res) => { res.writeHead(502); res.end(); });
  proxy.on('connect', (req, client, head) => {
    if (!/^(www\.dazn\.com|dazn\.com|api-web\.nhle\.com|search\.discovery\.indazn\.com):443$/.test(req.url)) { client.end('HTTP/1.1 502 Bad Gateway\r\n\r\n'); return; }
    const upstream = net.connect(server.address().port, '127.0.0.1', () => { client.write('HTTP/1.1 200 Connection Established\r\n\r\n'); if (head.length) upstream.write(head); client.pipe(upstream); upstream.pipe(client); });
    upstream.on('error', () => client.destroy()); client.on('error', () => upstream.destroy()); client.on('close', () => upstream.destroy());
  });
  await new Promise(resolve => proxy.listen(0, '127.0.0.1', resolve));
  const fixtureProxyPort = proxy.address().port;
  const port = await unusedPort();
  const { pathToFileURL } = require('node:url');
  const prefsModule = await import(pathToFileURL(path.join(root, 'node_modules/web-ext/lib/firefox/preferences.js')));
  const remoteModule = await import(pathToFileURL(path.join(root, 'node_modules/web-ext/lib/firefox/remote.js')));
  const browserContext = await playwright().firefox.launchPersistentContext(path.join(temp, 'profile'), {headless: true, ignoreHTTPSErrors:true, viewport:{width:1280,height:800}, executablePath: firefoxBinary(), args: ['-start-debugger-server', String(port)], firefoxUserPrefs: {...prefsModule.getPrefs(),
    'network.proxy.type': 1, 'network.proxy.ssl': '127.0.0.1', 'network.proxy.ssl_port': fixtureProxyPort,
    'network.proxy.no_proxies_on': '', 'media.autoplay.default': 0
  }});
  browser = browserContext.browser();
  browserVersion = browser.version();
  page = browserContext.pages()[0] || await browserContext.newPage();
  remote = await remoteModule.connectWithMaxRetries({port});
  await remote.installTemporaryAddon(stage, false);
  remote.disconnect(); remote = null;
  page.on('pageerror', error => console.log('PAGE ERROR',error.message));
  await runChecks();
  fs.writeFileSync(path.join(reports, 'firefox-synthetic.json'), JSON.stringify({ browser: `Firefox ${browserVersion}`, installedBy: 'web-ext RemoteFirefox.installTemporaryAddon', driver: 'Playwright Firefox', productionCodeModified: false, testOnlyProbeAdded: true, operatingSystem: process.platform, browserSandboxOverrides: {contentDisabled:process.env.MOZ_DISABLE_CONTENT_SANDBOX==='1',mediaDecoderDisabled:process.env.MOZ_DISABLE_RDD_SANDBOX==='1'}, localHTTPSFixtures: true, viewportOnlyMobile: true, physicalAndroidTested: false, realDAZNPlaybackTested: false, checks }, null, 2) + '\n');
  console.log(`${checks.length} synthetic installed-extension checks passed in Firefox ${browserVersion}. Real DAZN and Android acceptance remains pending.`);
}
async function runChecks() {
  await navigate();
  await checked('document-start cover before first provider script and sampled paints', async () => {
    assert.equal(await evaluate('window.fixtureBeforePaint'), 'hidden');
    const paints = await evaluate('window.fixturePaints'); assert(paints.length); assert(paints.every(paint => paint.visible === 'hidden'));
    assert.equal(await evaluate('!!document.querySelector("#nhluk-root")'), true);
    await screenshot('firefox-desktop.png');
  });
  await checked('responsive mobile viewport remains covered and fits the viewport', async () => {
    await page.setViewportSize({width:390,height:844});
    const size = await evaluate('({width:innerWidth,scroll:document.documentElement.scrollWidth,visibility:getComputedStyle(document.querySelector("#provider-rail")).visibility})');
    assert.equal(size.visibility, 'hidden'); assert(size.scroll <= size.width);
    await screenshot('firefox-mobile-viewport.png');
    await page.setViewportSize({width:1280,height:800});
  });
  await checked('recognized authentication form opens, other provider content stays covered', async () => {
    await navigate('/en-GB/signin');
    await eventually(() => evaluate('document.documentElement.getAttribute("data-nhluk-auth")'), value => value === 'open');
    assert.equal(await evaluate('getComputedStyle(document.querySelector("#email")).visibility'), 'visible');
    assert.equal(await evaluate('getComputedStyle(document.querySelector("#provider-rail")).visibility'), 'hidden');
    await screenshot('firefox-auth-fixture.png');
  });
  await checked('authentication SPA navigation recloses synchronously', async () => {
    const after = await evaluate('(() => {history.pushState({},"","/en-GB/home");return {auth:document.documentElement.getAttribute("data-nhluk-auth"),visible:getComputedStyle(document.querySelector("#email")).visibility,rail:getComputedStyle(document.querySelector("#provider-rail")).visibility};})()');
    assert.notEqual(after.auth, 'open'); assert.equal(after.visible, 'hidden'); assert.equal(after.rail, 'hidden');
  });
  await navigate();
  await checked('provider media-session metadata is neutralized', async () => {
    const metadata = await evaluate('(() => {if(!navigator.mediaSession || typeof MediaMetadata!=="function")return {supported:false};navigator.mediaSession.metadata=new MediaMetadata({title:"Synthetic provider metadata",artist:"Untrusted provider",artwork:[{src:"https://www.dazn.com/example.png"}]});return {supported:true,title:navigator.mediaSession.metadata?.title,artist:navigator.mediaSession.metadata?.artist,artwork:navigator.mediaSession.metadata?.artwork.length};})()');
    assert(metadata.supported); assert.notEqual(metadata.title, 'Synthetic provider metadata'); assert.notEqual(metadata.artist, 'Untrusted provider'); assert.equal(metadata.artwork, 0);
  });
  await checked('full shell schedule, catalogue, pending route, prepare, play, pause and stored resume', async () => {
    await eventually(()=>probe('ui-read'), value=>value.games===1);
    let ui = await probe('ui-read');
    assert(!ui.text.includes('SYNTHETIC_TAINT')); assert(!ui.text.includes('99')); assert(!ui.text.includes('98'));
    await screenshot('firefox-desktop.png');
    await page.setViewportSize({width:390,height:844}); await screenshot('firefox-mobile-viewport.png');
    await page.setViewportSize({width:1280,height:800});
    await probe('ui-click',{selector:'.game'});
    await probe('ui-check',{selector:'#watched'});
    await probe('ui-click',{selector:'#resolve'});
    await eventually(()=>probe('ui-read'),value=>value.variants===1);
    await probe('ui-click',{selector:'#variants button'});
    await page.waitForURL('https://www.dazn.com/en-GB/home/event1/asset1');
    await eventually(()=>probe('ui-read'),value=>value.playbackVisible);
    await probe('ui-click',{selector:'#prepare'});
    await eventually(()=>probe('ui-read'),value=>value.status.startsWith('Ready.'),20000);
    assert.equal(await evaluate('document.documentElement.getAttribute("data-nhluk-playback")'),'covered');
    await probe('ui-click',{selector:'#play'});
    await eventually(()=>probe('ui-read'),value=>value.status==='Playing.');
    await probe('ui-click',{selector:'#pause'});
    await eventually(()=>probe('ui-read'),value=>value.status==='Paused.');
    await page.reload();
    await eventually(()=>probe('ui-read'),value=>value.resumeVisible);
    await probe('ui-click',{selector:'#resume'});
    await eventually(()=>probe('ui-read'),value=>value.status.startsWith('Ready.'),20000);
    assert.equal(await evaluate('document.documentElement.getAttribute("data-nhluk-playback")'),'covered');
    await probe('ui-click',{selector:'#close'});
    await navigate();
  });
  for (const scenario of ['delayed-media', 'provider-seeks', 'source-replacement', 'video-replacement', 'resume', 'exposed-seek', 'ambiguous-media', 'missing-dvr-beginning']) {
    await checked(`actual decoded-video controller: ${scenario}`, async () => { const result = await probe('scenario', { scenario }); assert.equal(result.pass, true, JSON.stringify(result)); });
  }
}
main().catch(error => { console.error(error.stack); process.exitCode = 1; }).finally(async () => {
  try { remote?.disconnect(); await browser?.close(); } catch (_) {}
  server?.closeAllConnections(); proxy?.closeAllConnections();
  await Promise.race([Promise.all([new Promise(resolve => server ? server.close(resolve) : resolve()), new Promise(resolve => proxy ? proxy.close(resolve) : resolve())]), delay(1000)]);
  fs.rmSync(temp, { recursive: true, force: true });
});
