(function boot() {
  'use strict';
  if(!document.documentElement){
    const ready=new MutationObserver(()=>{if(document.documentElement){ready.disconnect();boot();}});
    ready.observe(document,{childList:true});return;
  }
  const N=globalThis.NHLUK;
  const shield=N.shield.create();
  N.mediaSession.install();
  // Child DAZN frames get the guard only; they cannot authorize playback or render UI.
  if(window.top!==window) return;
  const diagnostics=N.diagnostics.createRecorder();
  const bookmarks=N.resume.createStore(browser.storage.local);
  let ui, controller, auth, pending=null, bookmark=null, lastSaved=0, lastAuthorized=null, scheduleGeneration=0, resolveGeneration=0, recoveryGeneration=0;
  const message=payload=>browser.runtime.sendMessage(payload);
  async function save() {
    if(!pending||lastAuthorized===null)return;
    const clean=N.resume.createBookmark(pending.game,pending.variant,lastAuthorized);
    if(!clean)return;
    try {await bookmarks.save(clean);bookmark=clean;diagnostics.record('resume','none','resume-saved');}
    catch {diagnostics.record('resume','storage-unavailable');ui?.status('The playback position could not be saved.');}
  }
  controller=new N.player.PlayerController({
    getMedia:()=>[...document.querySelectorAll('video')],shield,
    onState:(state,detail)=>{
      ui?.state(state);
      const stage={WAITING_FOR_MEDIA:'media',WAITING_FOR_TIMELINE:'timeline',POSITIONING:'positioning',SETTLING:'settling',READY_COVERED:'ready',PLAYING_EXPOSED:'playback',PAUSED_EXPOSED:'playback',FAILED_COVERED:'playback',IDLE:'idle'}[state];
      const codes={SOURCE_CHANGED:'source-changed',TARGET_UNAVAILABLE:'target-unavailable',FRAME_VERIFICATION_UNAVAILABLE:'frame-unverified',MEDIA_AMBIGUOUS:'multiple-media',PROTECTION_UNAVAILABLE:'shield-failed'};
      diagnostics.record(stage||'idle',state==='FAILED_COVERED'?(codes[detail.reason]||'playback-unverified'):'none',state==='READY_COVERED'?'ready-covered':state==='PLAYING_EXPOSED'?'play-verified':state==='FAILED_COVERED'?'failed-covered':undefined);
      if(state==='PAUSED_EXPOSED')void save();
    },
    onProgress:value=>{
      // Only frames delivered during authorized playback count as a bookmark.
      if(!['PLAYING_EXPOSED','PAUSED_EXPOSED'].includes(controller.state))return;
      lastAuthorized=value;ui?.progress(value);
      if(Date.now()-lastSaved>5000){lastSaved=Date.now();void save();}
    }
  });
  shield.setInvalidationHandler(()=>{void save();controller.invalidate('PROTECTION_UNAVAILABLE');});
  async function loadSchedule(season) {
    const token=++scheduleGeneration;ui.status('Loading games.');diagnostics.record('schedule');
    const result=await message({type:'SCHEDULE',season});
    if(token!==scheduleGeneration)return;
    if(!result?.ok){diagnostics.record('schedule','schedule-unavailable');ui.status('The schedule is unavailable. Try Load games again.');return;}
    const games=result.games.map(N.schedule.validateGame).filter(Boolean);
    ui.games(games);if(pending)ui.state(controller.state);diagnostics.record('schedule','none','schedule-loaded');
  }
  async function resolve(game,transport) {
    const token=++resolveGeneration;diagnostics.record('catalogue','none','catalogue-requested');
    const result=transport==='background'?(await message({type:'DISCOVER',game}))?.result:await N.transport.discover(game);
    if(token!==resolveGeneration)return;
    const variants=result?.status==='matched'?N.routeResolver.rank(result.variants):[];
    ui.variants(variants);
    const errorCode={unsupported:'catalogue-unsupported',timeout:'catalogue-timeout',http:'catalogue-http',network:'catalogue-network',ambiguous:'ambiguous'}[result?.status];
    diagnostics.record('catalogue',variants.length?'none':errorCode||'not-matched',variants.length?'catalogue-matched':undefined);
  }
  async function open(game,variant) {
    void save();controller.close();
    const result=await message({type:'PENDING_SET',game,variant,target:0});
    if(!result?.ok)throw new Error('route-unavailable');
    diagnostics.record('route','none','route-selected');
    window.location.assign(variant.route);
  }
  async function close() {
    const saving=save();controller.close();pending=null;bookmark=null;lastAuthorized=null;resolveGeneration++;recoveryGeneration++;
    await saving;
    await message({type:'PENDING_CLEAR'});
    if(document.fullscreenElement)await document.exitFullscreen().catch(()=>{});
  }
  ui=N.appShell.create(shield.root,{
    schedule:loadSchedule,resolve,open,cancelResolve:()=>{resolveGeneration++;},
    signIn:()=>auth.signIn(),
    prepare:target=>{if(!pending)return;lastAuthorized=null;ui.progress(target);return controller.prepare({target,beginning:0});},
    resume:()=>{if(!pending||!bookmark||!N.resume.compatible(bookmark,pending.variant))return;lastAuthorized=null;return controller.prepare({target:bookmark.elapsedSeconds,beginning:0});},
    play:()=>controller.play(),pause:()=>{controller.pause();return save();},
    skip:delta=>{void save();lastAuthorized=null;return controller.skip(delta);},close,
    fullscreen:async()=>{
      if(!['PLAYING_EXPOSED','PAUSED_EXPOSED'].includes(controller.state))return;
      // Fullscreen the protected document, never the provider's native video controls.
      if(document.fullscreenElement)await document.exitFullscreen();else await document.documentElement.requestFullscreen();
    },diagnostic:()=>diagnostics.export()
  });
  auth=N.auth.create({shield,onChange:({state})=>{
    if(state==='signing-in'){diagnostics.record('auth','auth-required','auth-opened');ui.status('Sign in using the DAZN form.');}
  }});
  async function recoverPending() {
    const token=++recoveryGeneration, routeAtStart=location.href;
    if(N.auth.isAuthURL(location.href))return;
    const result=await message({type:'PENDING_GET'});
    if(token!==recoveryGeneration||location.href!==routeAtStart)return;
    if(!result?.ok||!result.pending)return;
    const next=result.pending;
    if(!N.schedule.validateGame(next.game)||!N.routeResolver.sanitizeVariant(next.variant))return;
    if(N.routeResolver.validateRoute(location.href)!==next.variant.route){
      const allowed=await message({type:'PENDING_REDIRECT'});
      if(token!==recoveryGeneration||location.href!==routeAtStart)return;
      if(allowed?.ok){shield.cover();location.assign(next.variant.route);}
      else {diagnostics.record('route','route-unavailable');ui.status('DAZN did not open the requested replay. Check your sign-in and UK connection.');}
      return;
    }
    let nextBookmark=null;
    try{const saved=await bookmarks.load(next.game.id);if(saved&&N.resume.compatible(saved,next.variant))nextBookmark=saved;}
    catch{diagnostics.record('resume','storage-unavailable');}
    if(token!==recoveryGeneration||location.href!==routeAtStart)return;
    pending=next;bookmark=nextBookmark;
    ui.player(next.game,bookmark);
  }
  let previousURL=location.href;
  setInterval(()=>{
    if(location.href!==previousURL){previousURL=location.href;void save();controller.close();auth.refresh();void recoverPending().catch(()=>ui.status('The page remains covered.'));}
  },100);
  document.addEventListener('visibilitychange',()=>{
    if(document.hidden){void save();controller.pause();shield.cover();}
  });
  window.addEventListener('pagehide',()=>{void save();controller.close();});
  // Keep terminal failures neutral. Raw exceptions are never logged or displayed.
  loadSchedule(ui.season()).catch(()=>{ui.status('The schedule is unavailable. Try Load games again.');});
  recoverPending().catch(()=>{
    diagnostics.record('shield','shield-failed');shield.cover();ui.status('Setup could not be completed. The page remains covered.');
  });
})();
