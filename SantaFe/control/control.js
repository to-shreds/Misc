(function () {
  'use strict';
  const P = window.SFControl, $ = id => document.getElementById(id);
  const KEYS = { settings: 'santa-fe.join.settings.v1', log: 'santa-fe.join.log.v1', pending: 'santa-fe.join.pending.v1' };
  const store = {
    read(key, fallback) { try { const raw = localStorage.getItem(key); return raw && raw.length <= 65536 ? JSON.parse(raw) : fallback; } catch (_) { return fallback; } },
    write(key, value) { try { localStorage.setItem(key, JSON.stringify(value)); return true; } catch (_) { return false; } },
    remove(key) { try { localStorage.removeItem(key); } catch (_) {} }
  };
  let config = null, entries = [], phoneEntries = [], busy = false, phoneBusy = false, activeId = '', selectedCommand = '', timeout;
  let unknown = store.read(KEYS.pending, null), snapshot = {};
  const bridge = (() => {
    if (window.tk && typeof window.tk.global === 'function' && typeof window.tk.performTask === 'function')
      return { get: name => window.tk.global(name), run: (task, payload) => window.tk.performTask(task, 6, payload || '', '', '', false, false, '', false) };
    if (typeof window.global === 'function' && typeof window.performTask === 'function')
      return { get: name => window.global(name), run: (task, payload) => window.performTask(task, 6, payload || '', '', '', false, false, '', false) };
    return null;
  })();
  const nativeTasks = new Set(['SFD Open Home', 'SFD Close GUI', 'SFD Setup', 'SFD Connect', 'SFD Choose Vehicle', 'SFD Join Settings', 'SFD Climate Settings', 'SFD Cold Settings', 'SFD Hot Settings', 'SFD Location Settings']);
  const safeText = value => String(value == null ? '' : value).slice(0, 3000);
  const dateText = value => Number.isFinite(Number(value)) && Number(value) > 0 ? new Date(Number(value)).toLocaleString() : 'Not read yet';
  function cleanEntry(entry) {
    return { time: Number(entry.time) || Date.now(), command: Object.hasOwn(P.COMMANDS, entry.command) ? entry.command : 'phone',
      state: safeText(entry.state).slice(0, 30), message: safeText(entry.message), http: /^\d{3}$/.test(String(entry.http)) ? Number(entry.http) : null, source: entry.source === 'phone' ? 'phone' : 'browser' };
  }
  const savedLog = store.read(KEYS.log, []);
  entries = Array.isArray(savedLog) ? savedLog.filter(e => e && typeof e === 'object').slice(-100).map(cleanEntry) : [];
  try { const saved = store.read(KEYS.settings, null); if (saved) config = P.settings(saved); } catch (_) { store.remove(KEYS.settings); }
  function notice(message, error) { $('notice').textContent = message; $('notice').classList.toggle('error', Boolean(error)); }
  function tab(name) {
    document.querySelectorAll('.page').forEach(page => { page.hidden = page.id !== name; });
    document.querySelectorAll('[data-tab]').forEach(button => { const chosen = button.dataset.tab === name; button.classList.toggle('selected', chosen); chosen ? button.setAttribute('aria-current', 'page') : button.removeAttribute('aria-current'); });
  }
  function buttons() {
    document.querySelectorAll('[data-command]').forEach(button => {
      button.disabled = busy || phoneBusy || (!bridge && !config) || (unknown && P.controls(button.dataset.command)) || (snapshot.pending && P.controls(button.dataset.command));
    });
    document.querySelectorAll('[data-task]').forEach(button => { button.disabled = busy || phoneBusy; });
    $('delivery-unknown').hidden = !unknown;
  }
  function renderLog() {
    const list = $('activity'); list.replaceChildren();
    const all = entries.concat(phoneEntries).sort((a, b) => b.time - a.time).slice(0, 100);
    if (!all.length) { const li = document.createElement('li'); li.textContent = 'No activity yet.'; list.append(li); }
    for (const entry of all) {
      const li = document.createElement('li'), title = document.createElement('strong'), time = document.createElement('time'), detail = document.createElement('p');
      title.textContent = (P.COMMANDS[entry.command] || 'Phone operation') + ' · ' + entry.state;
      time.textContent = dateText(entry.time) + ' · ' + entry.source + (entry.http ? ' · HTTP ' + entry.http : '');
      detail.textContent = entry.message; li.append(title, time, detail); list.append(li);
    }
  }
  function record(command, result, source) {
    const entry = cleanEntry({ ...result, time: Date.now(), command, source }); entries.push(entry); entries = entries.slice(-100);
    store.write(KEYS.log, entries); renderLog();
    $('last-time').textContent = dateText(entry.time); $('last-state').textContent = entry.state; $('last-message').textContent = entry.message;
  }
  function settingsView() {
    $('device-id').value = config ? config.deviceId : ''; $('api-key').value = ''; $('join-link').value = '';
    $('api-key').placeholder = config ? 'Saved; leave blank to keep it' : 'Your Join API key';
    $('saved-settings').textContent = config ? 'Join settings ready. The key stays masked.' : 'No Join settings saved.';
    $('remember').checked = Boolean(store.read(KEYS.settings, null)); buttons();
  }
  function identity() {
    const bytes = new Uint8Array(16); window.crypto.getRandomValues(bytes); return Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
  }
  async function send(command, safe) {
    if (busy || phoneBusy || (unknown && P.controls(command)) || (snapshot.pending && P.controls(command))) return;
    let id, payload;
    try { id = identity(); payload = P.envelope(command, id, Date.now(), safe); } catch (_) { notice('Could not prepare a command. Nothing was sent.', true); return; }
    busy = true; activeId = id; buttons();
    if (bridge) {
      try {
        const accepted = bridge.run('SFD Web Receive', payload);
        if (accepted === false || accepted === 'false') throw new Error('Task refused');
        record(command, { state: 'PHONE', message: 'Tasker is processing this request.' }); notice('Tasker is processing ' + P.COMMANDS[command] + '.');
        timeout = setTimeout(() => { if (activeId !== id) return; busy = false; activeId = ''; record(command, { state: 'UNKNOWN', message: 'No final phone result received yet. Check Tasker. Nothing was resent.' }); notice('Check Tasker for the current result. Nothing was resent.', true); buttons(); }, 240000);
        pollPhone();
      } catch (_) { busy = false; activeId = ''; record(command, { state: 'REJECTED', message: 'Tasker could not start the receiver. Run SFD Verify Actions.' }); notice('Tasker could not start the receiver.', true); buttons(); }
      return;
    }
    if (!config) { busy = false; activeId = ''; tab('settings'); notice('Save your Join settings first.', true); buttons(); return; }
    const pending = { id, command, time: Date.now() };
    if (!store.write(KEYS.pending, pending)) { busy = false; activeId = ''; notice('This browser cannot save the delivery guard. Enable local storage before sending.', true); buttons(); return; }
    record(command, { state: 'SENDING', message: 'Sending one push to Join.' });
    const abort = new AbortController(), timer = setTimeout(() => abort.abort(), 15000);
    try {
      const result = await P.send(config, payload, window.fetch.bind(window), abort.signal);
      store.remove(KEYS.pending); unknown = null; record(command, result); notice(result.message, result.state === 'REJECTED');
    } catch (_) {
      unknown = pending; record(command, { state: 'UNKNOWN', message: 'The browser could not confirm Join delivery. Check the phone before another vehicle command. No retry was sent.' });
      notice('Delivery unknown. Check your phone. Nothing was resent.', true);
    } finally { clearTimeout(timer); busy = false; activeId = ''; buttons(); }
  }
  function confirm(command) {
    if (!P.controls(command)) { send(command, false); return; }
    selectedCommand = command; const start = P.starts(command);
    $('confirm-title').textContent = P.COMMANDS[command] + '?';
    $('confirm-message').textContent = start ? 'Use the corresponding saved climate preset on your phone.' : 'Send this command to your Santa Fe through your phone?';
    $('safe-label').hidden = !start; $('safe').checked = false; $('confirm-send').disabled = start;
    $('confirmation').showModal();
  }
  function parseGlobal(name) {
    try { const value = bridge.get(name); return typeof value === 'string' && value.length <= 65536 && value[0] !== '%' ? JSON.parse(value) : null; } catch (_) { return null; }
  }
  function pollPhone() {
    if (!bridge || document.hidden) return;
    const data = parseGlobal('SFDWebState');
    if (data && data.version === 1) {
      snapshot = data; phoneBusy = data.busy === true;
      for (const [id, value] of Object.entries({ doors: data.doors, engine: data.engine, 'climate-state': data.climate, 'vehicle-time': data.vehicleTime, 'status-read': dateText(data.statusReadAt), 'vehicle-label': data.vehicle || 'From your phone', proximity: data.proximity || 'Not compared yet', distance: data.distance ? data.distance + ' m (approx.)' : 'Unknown', 'car-coordinates': data.carLat !== '' && data.carLon !== '' && data.carLat != null && data.carLon != null ? data.carLat + ', ' + data.carLon : 'Not read yet', 'car-time': data.carTime || 'Unknown', 'phone-accuracy': data.phoneAccuracy ? data.phoneAccuracy + ' m' : 'Unknown', 'comparison-time': data.comparedAt || 'Never', 'location-schedule': data.autoLocation ? 'Every ' + data.locationHours + ' hour(s)' : 'Off' })) $(id).textContent = safeText(value || 'Unknown');
      for (const [key, id] of [['regular', 'preset-regular'], ['cold', 'preset-cold'], ['hot', 'preset-hot']]) if (data.presets && data.presets[key]) $(id).textContent = safeText(data.presets[key]);
      if (data.result) {
        $('last-state').textContent = safeText(data.result.state); $('last-message').textContent = safeText(data.result.message); $('last-time').textContent = dateText(data.result.time);
        if (activeId && data.result.id === activeId && !phoneBusy) { clearTimeout(timeout); busy = false; activeId = ''; notice(data.result.message, ['FAILED', 'UNKNOWN', 'BUSY'].includes(data.result.state)); }
      }
    }
    const logs = parseGlobal('SFDWebLog'); if (Array.isArray(logs)) { phoneEntries = logs.slice(-60).map(e => cleanEntry({ ...e, source: 'phone' })); renderLog(); }
    buttons();
  }
  document.querySelectorAll('[data-tab]').forEach(button => button.addEventListener('click', () => tab(button.dataset.tab)));
  document.querySelectorAll('[data-command]').forEach(button => button.addEventListener('click', () => confirm(button.dataset.command)));
  document.querySelectorAll('[data-task]').forEach(button => button.addEventListener('click', () => { if (!bridge || busy || phoneBusy || !nativeTasks.has(button.dataset.task)) return; try { bridge.run(button.dataset.task); } catch (_) { notice('Open this setting from the native Tasker screens.', true); } }));
  $('safe').addEventListener('change', () => { $('confirm-send').disabled = !$('safe').checked; });
  $('confirmation').addEventListener('close', () => { if ($('confirmation').returnValue === 'send' && selectedCommand) send(selectedCommand, P.starts(selectedCommand) && $('safe').checked); selectedCommand = ''; });
  $('settings-form').addEventListener('submit', event => {
    event.preventDefault();
    try {
      config = $('join-link').value.trim() ? P.fromLink($('join-link').value) : P.settings({ apiKey: $('api-key').value.trim() || (config && config.apiKey), deviceId: $('device-id').value });
      if ($('remember').checked) { if (!store.write(KEYS.settings, config)) throw new Error('Could not remember settings.'); } else store.remove(KEYS.settings);
      settingsView(); notice('Join settings saved. Test connection sends no car command.');
    } catch (error) { notice(error.message, true); }
    finally { $('join-link').value = ''; $('api-key').value = ''; }
  });
  $('forget').addEventListener('click', () => { config = null; store.remove(KEYS.settings); settingsView(); notice('Join settings removed from this browser.'); });
  $('acknowledge').addEventListener('click', () => { unknown = null; store.remove(KEYS.pending); notice('Delivery warning acknowledged. The next tap creates a new request.'); buttons(); });
  $('clear-log').addEventListener('click', () => { entries = []; store.remove(KEYS.log); renderLog(); });
  $('export-log').addEventListener('click', () => {
    const blob = new Blob([JSON.stringify({ format: 'santa-fe-control-log-v1', controller: '1.2.0', exportedAt: new Date().toISOString(), entries: entries.concat(phoneEntries).map(cleanEntry) }, null, 2)], { type: 'application/json' });
    const link = document.createElement('a'), url = URL.createObjectURL(blob); link.href = url; link.download = 'SantaFe-control-log-' + new Date().toISOString().replace(/[:.]/g, '-') + '.json'; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
  });
  settingsView(); renderLog();
  if (entries.length) { const last = entries[entries.length - 1]; $('last-time').textContent = dateText(last.time); $('last-state').textContent = last.state; $('last-message').textContent = last.message; }
  if (bridge) {
    $('mode').textContent = 'Tasker phone interface';
    $('connection-title').textContent = 'Phone connection';
    document.querySelector('footer').textContent = 'Phone interface → Tasker → Hyundai';
    document.querySelectorAll('.native-only').forEach(element => { element.hidden = false; });
    document.querySelectorAll('.external-note').forEach(element => { element.hidden = true; });
    $('settings-form').hidden = true; $('saved-settings').hidden = true;
    notice('Phone interface ready. Vehicle results appear here.'); pollPhone(); setInterval(pollPhone, 750);
    document.addEventListener('visibilitychange', pollPhone);
  } else if (unknown) notice('An earlier delivery was not confirmed. Check your phone before another vehicle command.', true);
  else if (config) notice('Join settings ready. Test connection checks the phone without contacting Hyundai.');
  else { $('remember').checked = true; tab('settings'); }
})();
