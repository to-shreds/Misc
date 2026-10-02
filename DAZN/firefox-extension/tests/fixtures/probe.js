/* Test-only last-before-bootstrap isolated content script. Never packaged. */
(function () {
  'use strict';
  const NS = globalThis.NHLUK;
  const create = NS.shield.create;
  let installedShield;
  NS.shield.create = function (...args) {
    const shield = create.apply(this, args);
    if (!installedShield) installedShield = shield;
    return shield;
  };
  let uiShadow;
  const createUI = NS.appShell.create;
  NS.appShell.create = function(root, ...args) {
    const attach = root.attachShadow;
    root.attachShadow = function(options) { uiShadow = attach.call(root, options); return uiShadow; };
    try { return createUI.call(this, root, ...args); } finally { delete root.attachShadow; }
  };
  function ui(action, args) {
    require(uiShadow, 'Shell not created');
    if(action === 'ui-read') return {text:uiShadow.textContent,status:uiShadow.getElementById('status').textContent,games:uiShadow.querySelectorAll('.game').length,variants:uiShadow.querySelectorAll('#variants button').length,playbackVisible:!uiShadow.getElementById('playback').classList.contains('hidden'),resumeVisible:!uiShadow.getElementById('resume').classList.contains('hidden'),elapsed:uiShadow.getElementById('elapsed').textContent};
    const node = uiShadow.querySelector(args.selector);
    require(node, 'Missing fixture UI selector');
    if(action === 'ui-check') {node.checked = true;node.dispatchEvent(new Event('change'));}
    if(action === 'ui-click') {require(!node.disabled,'Fixture UI control disabled');node.click();}
    return true;
  }
  const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
  const until = async (predicate, ms = 10000) => {
    const deadline = performance.now() + ms;
    do { if (predicate()) return true; await delay(20); } while (performance.now() < deadline);
    throw new Error('Fixture timed out waiting for observable state');
  };
  const require = (value, message) => { if (!value) throw new Error(message); };
  function media(suffix = 'a') {
    const video = document.createElement('video');
    video.preload = 'auto'; video.muted = true; video.__events=[];
    for(const e of ['playing','pause','seeking','seeked','loadedmetadata','error','timeupdate','emptied','loadstart','abort','loadeddata','canplay'])video.addEventListener(e,()=>{if(video.__events.length<50)video.__events.push([e,video.currentTime,video.readyState]);});
    video.src = `/replay.webm?${suffix}`;
    document.body.append(video);
    return video;
  }
  async function scenario(name) {
    await until(() => installedShield);
    installedShield.cover();
    for (const node of document.querySelectorAll('video,audio')) node.remove();
    const states = [];
    let ctl;
    installedShield.setInvalidationHandler(() => ctl?.close());
    ctl = new NS.player.PlayerController({
      getMedia: () => document.querySelectorAll('video'), shield: installedShield,
      onState: (state, meta) => states.push({ state, reason: meta.reason }),
      config: { prepareTimeout: 7000, playTimeout: 3000 }
    });
    let first = null, timers = [];
    const later = (fn, ms) => timers.push(setTimeout(fn, ms));
    try {
      if (name === 'ambiguous-media') {
        media('one'); media('two');
        require(await ctl.prepare() === false, 'Ambiguous media unexpectedly prepared');
        require(ctl.getSnapshot().reason === 'MEDIA_AMBIGUOUS', 'Wrong ambiguity failure');
        require(document.documentElement.getAttribute('data-nhluk-playback') === 'covered', 'Ambiguous media exposed');
        return { pass: true, states };
      }
      if (name === 'delayed-media') later(() => { first = media(); }, 180);
      else first = media();
      if (name === 'missing-dvr-beginning') {
        await until(() => first.readyState >= 1);
        Object.defineProperty(first, 'seekable', { configurable: true, get: () => ({ length: 1, start: () => 3, end: () => 12 }) });
        require(await ctl.prepare() === false, 'Unavailable beginning unexpectedly prepared');
        require(document.documentElement.getAttribute('data-nhluk-playback') === 'covered', 'Unavailable beginning exposed');
        return { pass: true, states };
      }
      if (name === 'provider-seeks') {
        let seeks = 0;
        first.addEventListener('playing', () => { if (seeks++ < 2) first.currentTime = 4 + seeks; });
      }
      if (name === 'source-replacement') {
        first.addEventListener('playing', () => { if (!first.dataset.changed) { first.dataset.changed = 'true'; first.src = '/replay.webm?replacement'; first.load(); } }, { once: true });
      }
      if (name === 'video-replacement') {
        first.addEventListener('playing', () => { first.remove(); first = media('replacement-element'); }, { once: true });
      }
      const target = name === 'resume' ? 5 : 0;
      const prepared = await ctl.prepare({ target });
      require(prepared, `Preparation failed: ${JSON.stringify({states,events:first?.__events,time:first?.currentTime,paused:first?.paused,ready:first?.readyState,quality:first?.getVideoPlaybackQuality?.(),frames:ctl.frames,frameHandle:ctl.frameHandle,src:first?.src,currentSrc:first?.currentSrc,error:first?.error?.code,network:first?.networkState,visibility:first&&getComputedStyle(first).visibility})}`);
      require(ctl.getSnapshot().state === 'READY_COVERED', 'Wrong prepared state');
      require(first.muted && first.paused, 'Preparation did not finish paused and muted');
      require(document.documentElement.getAttribute('data-nhluk-playback') === 'covered', 'Preparation exposed video');
      require(Math.abs(first.currentTime - target) <= 0.45, 'Preparation target mismatch');
      require(first.controls === false && !first.hasAttribute('poster'), 'Unsafe native UI retained');
      const played = await ctl.play();
      require(played, `Explicit Play failed: ${JSON.stringify(states)}`);
      require(ctl.getSnapshot().state === 'PLAYING_EXPOSED', 'Wrong playing state');
      require(document.documentElement.getAttribute('data-nhluk-playback') === 'exposed', 'Verified playback not exposed');
      require(getComputedStyle(first).visibility === 'visible', 'Selected video remains invisible');
      require(getComputedStyle(document.querySelector('#provider-rail')).visibility === 'hidden', 'Provider UI exposed');
      if (name === 'exposed-seek') {
        first.currentTime = 8;
        await until(() => ['FAILED_COVERED', 'IDLE'].includes(ctl.getSnapshot().state));
        require(first.muted, 'Unexpected seek left audio enabled');
        require(document.documentElement.getAttribute('data-nhluk-playback') === 'covered', 'Unexpected seek exposed');
      } else {
        require(ctl.pause(), 'Pause failed');
        require(first.paused, 'Video not paused');
        require(await ctl.play(), 'Resume from pause failed');
      }
      return { pass: true, states };
    } finally {
      for (const timer of timers) clearTimeout(timer);
      ctl.destroy();
      for (const node of document.querySelectorAll('video,audio')) { node.pause(); node.remove(); }
    }
  }
  document.addEventListener('nhluk:fixture-command', async event => {
    let request;
    try {
      request = JSON.parse(event.detail);
      const value = request.action === 'scenario' ? await scenario(request.args.scenario) : request.action.startsWith('ui-') ? ui(request.action,request.args) : null;
      document.documentElement.setAttribute('data-fixture-result', JSON.stringify({ id: request.id, value }));
    } catch (error) {
      document.documentElement.setAttribute('data-fixture-result', JSON.stringify({ id: request?.id, error: error.message }));
    }
  });
  function ready() { if (document.documentElement) document.documentElement.setAttribute('data-fixture-ready', 'true'); }
  ready();
  if (!document.documentElement) new MutationObserver(function () { ready(); if (document.documentElement) this.disconnect(); }).observe(document, { childList: true });
})();
