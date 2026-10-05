'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const P = require('../control/protocol.js');
const config = { deviceId: 'a'.repeat(32), apiKey: 'fixture_'.repeat(4) };
const id = 'fixture_request_01', now = 1791237000000;
test('all supported commands have bounded identities and explicit start confirmation', () => {
  for (const command of Object.keys(P.COMMANDS)) {
    const payload = P.envelope(command, id, now, P.starts(command));
    assert.equal(payload, command + '|' + id + '|' + now + '|' + (P.starts(command) ? '1' : '0'));
  }
  for (const command of ['ignition_on', 'ignition_on_cold', 'ignition_on_hot']) assert.throws(() => P.envelope(command, id, now, false));
  assert.throws(() => P.envelope('lock', id, now, true));
});
test('injected unknown commands and malformed identities are rejected', () => {
  for (const command of ['horn', 'lights', 'lock;unlock', '__proto__', 'constructor', 'forget']) assert.throws(() => P.envelope(command, id, now, false));
  for (const requestId of ['', 'a', 'a|b', 'x'.repeat(65), 'valid\ncommand']) assert.throws(() => P.envelope('lock', requestId, now, false));
  for (const time of [NaN, Infinity, -1, now + 0.1]) assert.throws(() => P.envelope('lock', id, time, false));
});
test('Join URL targets one fixed phone and URL-encodes the command', () => {
  const payload = P.envelope('ignition_on_cold', id, now, true), url = new URL(P.request(config, payload));
  assert.equal(url.origin + url.pathname, P.ENDPOINT);
  assert.equal(url.searchParams.get('text'), 'hyundai=:=' + payload);
  assert.equal(url.searchParams.get('deviceId'), config.deviceId);
  assert.equal(url.searchParams.get('apikey'), config.apiKey);
  assert.deepEqual(Array.from(url.searchParams.keys()), ['text', 'deviceId', 'apikey']);
});
test('paste link imports only validated private settings', () => {
  assert.deepEqual(P.fromLink(P.ENDPOINT + '?text=hyundai=:=lock&deviceId=' + config.deviceId + '&&apikey=' + config.apiKey), config);
  for (const link of ['javascript:alert(1)', 'http://joinjoaomgcd.appspot.com/_ah/api/messaging/v1/sendPush', 'https://attacker.invalid/?apikey=' + config.apiKey]) assert.throws(() => P.fromLink(link));
  for (const deviceId of ['group.all', 'group.android', config.deviceId + ',' + config.deviceId]) assert.throws(() => P.settings({ ...config, deviceId }));
});
test('accepted Join push means SENT rather than car completion', async () => {
  let count = 0;
  const result = await P.send(config, P.envelope('lock', id, now, false), async (url, options) => {
    count++; assert.equal(options.method, 'GET'); assert.equal(options.credentials, 'omit'); assert.equal(options.redirect, 'error'); assert.equal(options.referrerPolicy, 'no-referrer');
    return { ok: true, status: 200, text: async () => '{"success":true}' };
  });
  assert.equal(count, 1); assert.equal(result.state, 'SENT'); assert.match(result.message, /Check Tasker/);
});
test('Join rejection does not echo a private error body', async () => {
  const result = await P.send(config, 'ping|' + id + '|' + now + '|0', async () => ({ ok: true, status: 200, text: async () => JSON.stringify({ success: false, errorMessage: config.apiKey, requestUrl: P.request(config, 'ping') }) }));
  assert.equal(result.state, 'REJECTED'); assert.ok(!JSON.stringify(result).includes(config.apiKey)); assert.ok(!JSON.stringify(result).includes(config.deviceId));
});
test('HTTP failures make one request and are not success', async () => {
  for (const status of [401, 403, 429, 502]) {
    let count = 0;
    const result = await P.send(config, 'ping', async () => { count++; return { ok: false, status }; });
    assert.equal(result.state, 'REJECTED'); assert.equal(result.http, status); assert.equal(count, 1);
  }
});
test('network failures and unreadable replies never retry', async () => {
  for (const value of [null, 'bad JSON', '{"success":"true"}', '{}', 'x'.repeat(65537)]) {
    let count = 0;
    await assert.rejects(P.send(config, 'ping', async () => { count++; if (value === null) throw new Error(config.apiKey); return { ok: true, status: 200, text: async () => value }; }));
    assert.equal(count, 1);
  }
});
