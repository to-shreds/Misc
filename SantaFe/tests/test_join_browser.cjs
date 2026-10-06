'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const P = require('../control/protocol.js'), D = require('../control/join-browser.js');
const config = { apiKey: 'fixture_join_private_key_12345678', deviceId: 'a'.repeat(32) };
const reply = (body, status = 200) => ({ ok: status >= 200 && status < 300, status, text: async () => typeof body === 'string' ? body : JSON.stringify(body) });
test('push rejection preserves a useful reason and still sends exactly once', async () => {
  let calls = 0;
  const result = await D.send(config, 'ping', async (url, options) => {
    calls++; assert.equal(new URL(url).origin + new URL(url).pathname, P.ENDPOINT); assert.equal(options.credentials, 'omit');
    return reply({ success: false, errorMessage: 'No device to send message to' });
  });
  assert.equal(calls, 1); assert.equal(result.state, 'REJECTED');
  assert.match(result.message, /Reason: No device to send message to/); assert.match(result.message, /could not find the selected phone/);
});
test('only sanitized error text enters the result, never raw body fields', async () => {
  const vin = '1HGBH41JXMN109186';
  const result = await D.send(config, 'lock', async () => reply({ success: false, userAuthError: true,
    errorMessage: 'Invalid API key: ' + config.apiKey + ', phone ' + config.deviceId + ', user jon@example.invalid, VIN ' + vin + ', coordinates 42.123456 -71.456789, URL ' + P.request(config, 'lock'),
    requestUrl: P.request(config, 'lock'), password: 'do-not-echo-this' }));
  for (const secret of [config.apiKey, config.deviceId, 'jon@example.invalid', vin, '42.123456', '-71.456789', 'do-not-echo-this', 'https://']) assert.ok(!JSON.stringify(result).includes(secret), secret);
  assert.match(result.message, /did not accept the API key/); assert.match(result.message, /No retry was sent/);
});
test('non-JSON definitive HTTP rejection remains rejected with a settings action', async () => {
  const result = await D.send(config, 'ping', async () => reply('Forbidden', 403));
  assert.equal(result.state, 'REJECTED'); assert.equal(result.http, 403); assert.match(result.message, /Join refused access/);
});
test('unconfirmed server and network outcomes still throw and never retry', async () => {
  for (const fixture of [async () => reply('Bad Gateway', 502), async () => { throw new Error(config.apiKey); }, async () => reply('bad JSON')]) {
    let calls = 0; await assert.rejects(D.send(config, 'lock', async (...args) => { calls++; return fixture(...args); })); assert.equal(calls, 1);
  }
});
test('read-only check verifies membership with one listDevices call and no push', async () => {
  let calls = 0;
  const result = await D.inspect(config, async (url, options) => {
    calls++; const parsed = new URL(url);
    assert.equal(parsed.origin + parsed.pathname, D.DEVICES); assert.deepEqual([...parsed.searchParams.keys()], ['apikey']);
    assert.equal(parsed.searchParams.get('apikey'), config.apiKey); assert.equal(options.referrerPolicy, 'no-referrer');
    return reply({ success: true, records: [{ deviceId: config.deviceId.toUpperCase(), userAccount: 'secret@example.invalid', regId: 'private-token' }] });
  });
  assert.equal(calls, 1); assert.equal(result.state, 'SETTINGS OK'); assert.match(result.message, /found the selected phone/);
  assert.ok(!JSON.stringify(result).includes(config.deviceId)); assert.ok(!JSON.stringify(result).includes('private-token'));
});
test('valid key paired with the wrong phone gets a specific correction', async () => {
  const result = await D.inspect(config, async () => reply({ success: true, records: [{ deviceId: 'b'.repeat(32) }] }));
  assert.equal(result.state, 'REJECTED'); assert.match(result.message, /saved phone ID is not in that account/);
});
test('authentication flag distinguishes an API key rejection without echoing a key', async () => {
  const result = await D.inspect(config, async () => reply({ success: false, userAuthError: true, errorMessage: config.apiKey }));
  assert.equal(result.state, 'REJECTED'); assert.match(result.message, /did not accept the API key/); assert.ok(!result.message.includes(config.apiKey));
});
test('incomplete or unreadable device list never pretends settings are valid', async () => {
  for (const body of ['not JSON', {}, { success: true }, { success: 'true', records: [] }, 'x'.repeat(65537)]) await assert.rejects(D.inspect(config, async () => reply(body)));
  await assert.rejects(D.inspect(config, async () => reply({}, 503)));
});
test('invalid local settings are rejected before any device-list request', async () => {
  let calls = 0; await assert.rejects(D.inspect({ ...config, deviceId: 'group.all' }, async () => { calls++; })); assert.equal(calls, 0);
});
test('rate limiting has its own action and never suggests a blind retry', async () => {
  const result = await D.send(config, 'ping', async () => reply({ success: false, errorMessage: 'Too many requests' }, 429));
  assert.match(result.message, /Wait before testing again/); assert.match(result.message, /No retry was sent/);
});
