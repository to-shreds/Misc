/* Browser-only Join diagnostics. No Hyundai requests and no automatic retry. */
(function (root, factory) {
  'use strict';
  if (typeof module === 'object' && module.exports) {
    module.exports = factory(require('./protocol.js'));
    return;
  }
  const P = root.SFControl, diagnostics = factory(P), document = root.document;
  const FIXED_DEVICE_ID = '77ebc6e220414eada086692685d25353';
  for (const id of ['join-link', 'device-id']) {
    const input = document.getElementById(id), label = document.querySelector('label[for="' + id + '"]');
    if (input) { input.hidden = true; input.value = ''; }
    if (label) label.hidden = true;
  }
  const connectionHint = document.querySelector('#settings > p.hint.external-note');
  if (connectionHint) connectionHint.textContent = 'Enter only your Join API key. This controller is locked to your phone ID. Your Hyundai account stays in Tasker.';
  const card = document.createElement('div'), title = document.createElement('h2');
  const detail = document.createElement('p'), button = document.createElement('button');
  const instructions = document.createElement('p'), link = document.createElement('a');
  card.id = 'join-diagnostics'; card.className = 'card';
  title.textContent = 'Join connection check'; detail.id = 'join-diagnostic-result';
  detail.setAttribute('role', 'status'); detail.setAttribute('aria-live', 'polite');
  button.id = 'check-join-settings'; button.type = 'button'; button.className = 'secondary';
  button.textContent = 'Check saved Join settings'; button.disabled = true;
  instructions.className = 'hint';
  link.href = 'https://joinjoaomgcd.appspot.com/'; link.target = '_blank'; link.rel = 'noopener noreferrer';
  link.textContent = 'Open Join';
  instructions.append(link, ' if you need a new API key. This controller is already locked to your phone ID; enter only the API key above and save.');
  card.append(title, detail, button, instructions);
  document.getElementById('settings-form').after(card);
  let configured = null, checking = false, sending = false, revision = 0;
  function enabled() { button.disabled = !configured || checking || sending; }
  function display(result) { detail.textContent = result.message; card.classList.toggle('warning', !['SETTINGS OK', 'SENT'].includes(result.state)); }
  function capture(value) {
    if (!configured || configured.apiKey !== value.apiKey || configured.deviceId !== value.deviceId) {
      revision++; detail.textContent = 'Saved Join settings have not been checked. This check sends no push and operates no car.'; card.classList.remove('warning');
    }
    configured = value; enabled(); return value;
  }
  detail.textContent = 'Save your Join API key first. The phone ID is fixed in this controller. This check sends no push and operates no car.';
  root.SFControl = Object.freeze({ ...P,
    // External-browser Join commands are intentionally tiny. Tasker owns all
    // Hyundai translation, authentication, submission and polling.
    envelope: command => {
      if (!Object.hasOwn(P.COMMANDS, command)) throw new Error('Unsupported command.');
      return command;
    },
    settings: value => capture(P.settings({ ...value, deviceId: FIXED_DEVICE_ID })),
    fromLink: value => {
      const parsed = P.fromLink(value);
      return capture(P.settings({ ...parsed, deviceId: FIXED_DEVICE_ID }));
    },
    async send(config, payload, fetcher, signal) {
      capture(config); const current = revision; sending = true; enabled();
      try {
        const result = await diagnostics.send(config, payload, fetcher, signal);
        if (current === revision) {
          display(result);
          if (result.state === 'REJECTED') document.querySelector('[data-tab="settings"]').click();
        }
        return result;
      } finally { sending = false; enabled(); }
    }
  });
  button.addEventListener('click', async () => {
    if (!configured || checking || sending) return;
    const config = { ...configured }, current = revision, abort = new AbortController();
    checking = true; enabled(); detail.textContent = 'Checking the API key and this controller\'s fixed phone ID with Join. No push is being sent.';
    const timer = setTimeout(() => abort.abort(), 15000);
    try { const result = await diagnostics.inspect(config, root.fetch.bind(root), abort.signal); if (current === revision) display(result); }
    catch (_) { if (current === revision) display({ state: 'UNKNOWN', message: 'The browser could not complete the settings check. Check your Internet connection and try this check again. No push was sent.' }); }
    finally { clearTimeout(timer); checking = false; enabled(); }
  });
  document.getElementById('forget').addEventListener('click', () => {
    revision++; configured = null; enabled(); detail.textContent = 'Join API key removed. Enter the API key above.'; card.classList.remove('warning');
  });
})(typeof globalThis !== 'undefined' ? globalThis : this, function (P) {
  'use strict';
  const DEVICES = 'https://joinjoaomgcd.appspot.com/_ah/api/registration/v1/listDevices';
  const freshLink = 'Open Join, select this phone and create a new Join API link, then enter that link\'s API key in Settings.';
  function redact(value, config) {
    if (typeof value !== 'string') return '';
    let text = value;
    for (const secret of [config.apiKey, config.deviceId]) {
      if (secret) text = text.split(secret).join('[hidden]').split(secret.toUpperCase()).join('[hidden]');
    }
    return text.replace(/https?:\/\/[^\s<>"']+/gi, '[URL hidden]')
      .replace(/\b[^\s@]+@[^\s@]+\.[^\s@]+\b/g, '[email hidden]')
      .replace(/\b(?:apikey|api[_ -]?key|token|password|pin|deviceid)\s*[:=]\s*[^\s,;]+/gi, '[private value hidden]')
      .replace(/\b[a-zA-Z0-9_-]{16,}\b/g, '[identifier hidden]')
      .replace(/-?\b\d{1,3}\.\d{3,}\b/g, '[location hidden]')
      .replace(/[\u0000-\u001f\u007f\u200b-\u200f\u202a-\u202e\u2066-\u2069]/g, ' ')
      .replace(/\s+/g, ' ').trim().slice(0, 400);
  }
  function rejection(body, http, config, noun) {
    const raw = body && (body.errorMessage || (typeof body.error === 'string' ? body.error : body.error && body.error.message));
    const reason = redact(raw, config);
    let action = freshLink;
    if ((body && body.userAuthError === true) || /(?:invalid|incorrect|expired|unauthori[sz]ed|not valid).*(?:api.?key|auth)|(?:api.?key).*(?:invalid|incorrect|expired|not valid)/i.test(reason))
      action = 'Join did not accept the API key. ' + freshLink;
    else if (/no device|device.*(?:not found|not registered|does not exist|unavailable)|invalid.*device/i.test(reason))
      action = 'Join could not find the selected phone. ' + freshLink;
    else if (http === 429 || /rate.?limit|too many requests|quota/i.test(reason))
      action = 'Join is limiting requests. Wait before testing again.';
    else if (http === 401 || http === 403)
      action = 'Join refused access. ' + freshLink;
    return { state: 'REJECTED', http, message: 'Join rejected ' + noun + (reason ? '. Reason: ' + reason : ' without a readable reason (HTTP ' + http + ')') + '. ' + action + (noun === 'the push' ? ' No retry was sent.' : ' No push was sent.') };
  }
  async function read(response) {
    const text = await response.text();
    if (text.length > 65536) throw new Error('Unreadable Join response.');
    let body;
    try { body = JSON.parse(text); } catch (_) { body = null; }
    return { text, body };
  }
  async function send(config, payload, fetcher, signal) {
    let body = null;
    const result = await P.send(config, payload, async (url, options) => {
      const response = await fetcher(url, options);
      if (response.ok) {
        const reply = await read(response); body = reply.body;
        return { ok: response.ok, status: response.status, text: async () => reply.text };
      }
      if (response.status < 500 && response.status !== 408) {
        try { body = (await read(response)).body; } catch (_) {}
      }
      return response;
    }, signal);
    return result.state === 'REJECTED' ? rejection(body, result.http, config, 'the push') : result;
  }
  async function inspect(config, fetcher, signal) {
    const clean = P.settings(config), url = new URL(DEVICES); url.searchParams.set('apikey', clean.apiKey);
    const response = await fetcher(url.href, { method: 'GET', credentials: 'omit', cache: 'no-store', redirect: 'error', referrerPolicy: 'no-referrer', signal });
    const http = response.status;
    if (http >= 500 || http === 408) throw new Error('Join settings check unavailable.');
    let body;
    try { body = (await read(response)).body; } catch (error) { if (response.ok) throw error; }
    if (!response.ok || (body && (body.success === false || body.userAuthError === true || body.error))) return rejection(body, http, clean, 'the settings check');
    if (!body || body.success !== true || !Array.isArray(body.records)) throw new Error('Unreadable Join device list.');
    if (!body.records.some(device => device && typeof device.deviceId === 'string' && device.deviceId.toLowerCase() === clean.deviceId.toLowerCase()))
      return { state: 'REJECTED', http, message: 'Join accepted the API key, but the fixed phone ID is not in that account. ' + freshLink + ' No push was sent.' };
    return { state: 'SETTINGS OK', http, message: 'Join accepted the API key and found the selected phone. Tap Test connection to check delivery to Tasker. This check sent no push.' };
  }
  return Object.freeze({ DEVICES, redact, rejection, send, inspect });
});
