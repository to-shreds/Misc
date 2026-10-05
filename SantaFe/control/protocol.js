/* Shared command contract. No Hyundai credentials or API code in this client. */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.SFControl = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  const ENDPOINT = 'https://joinjoaomgcd.appspot.com/_ah/api/messaging/v1/sendPush';
  const COMMANDS = Object.freeze({
    ignition_on: 'Start regular', ignition_on_cold: 'Start cold', ignition_on_hot: 'Start hot',
    ignition_off: 'Stop', lock: 'Lock', unlock: 'Unlock', ping: 'Test connection',
    status: 'Read status', refresh: 'Refresh status', location: 'Read car GPS',
    compare_location: 'Compare phone GPS', poll: 'Check command'
  });
  const starts = command => /^ignition_on(?:_cold|_hot)?$/.test(command);
  const controls = command => starts(command) || ['ignition_off', 'lock', 'unlock'].includes(command);
  function settings(value) {
    const apiKey = String(value.apiKey || '').trim();
    const deviceId = String(value.deviceId || '').trim();
    if (!/^[a-f0-9]{32}$/i.test(deviceId)) throw new Error('Enter the ID of one Join phone, rather than a group.');
    if (!/^[a-zA-Z0-9_-]{16,128}$/.test(apiKey)) throw new Error('Enter your Join API key.');
    return { apiKey, deviceId };
  }
  function fromLink(link) {
    let url;
    try { url = new URL(String(link).trim()); } catch (_) { throw new Error('Paste a valid Join sendPush link.'); }
    if (url.origin !== 'https://joinjoaomgcd.appspot.com' || url.pathname !== '/_ah/api/messaging/v1/sendPush' || url.username || url.password)
      throw new Error('Use a Join sendPush link for one phone.');
    return settings({ apiKey: url.searchParams.get('apikey'), deviceId: url.searchParams.get('deviceId') });
  }
  function envelope(command, id, time, safe) {
    if (!Object.hasOwn(COMMANDS, command)) throw new Error('Unsupported command.');
    if (!/^[a-z0-9_-]{12,64}$/.test(id) || !Number.isSafeInteger(time) || !/^[0-9]{13}$/.test(String(time))) throw new Error('Invalid request identity.');
    if (starts(command) !== Boolean(safe)) throw new Error('Confirm that the car is outdoors before starting.');
    return command + '|' + id + '|' + time + '|' + (safe ? '1' : '0');
  }
  function request(config, payload) {
    const clean = settings(config);
    const url = new URL(ENDPOINT);
    url.searchParams.set('text', 'hyundai=:=' + payload);
    url.searchParams.set('deviceId', clean.deviceId);
    url.searchParams.set('apikey', clean.apiKey);
    return url.href;
  }
  async function send(config, payload, fetcher, signal) {
    const response = await fetcher(request(config, payload), {
      method: 'GET', credentials: 'omit', cache: 'no-store', redirect: 'error',
      referrerPolicy: 'no-referrer', signal
    });
    const http = response.status;
    if (!response.ok) return { state: 'REJECTED', http, message: 'Join returned HTTP ' + http + '. No retry was sent.' };
    const text = await response.text();
    if (text.length > 65536) throw new Error('Unreadable Join response.');
    let body;
    try { body = JSON.parse(text); } catch (_) { throw new Error('Unreadable Join response.'); }
    if (body.success === true) return { state: 'SENT', http, message: 'Join accepted the push. Check Tasker for the car result.' };
    if (body.success === false || body.error) return { state: 'REJECTED', http, message: 'Join rejected the push. Check your Join settings. No retry was sent.' };
    throw new Error('Unknown Join response.');
  }
  return Object.freeze({ ENDPOINT, COMMANDS, starts, controls, settings, fromLink, envelope, request, send });
});
