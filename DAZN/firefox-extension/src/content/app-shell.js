(() => {
  'use strict';
  const N = globalThis.NHLUK = globalThis.NHLUK || {};
  const STATES = {
    IDLE:'Choose a game to prepare.', WAITING_FOR_MEDIA:'Waiting for the player.',
    WAITING_FOR_TIMELINE:'Waiting for a safe beginning.', POSITIONING:'Preparing.',
    SETTLING:'Checking the playback position.', READY_COVERED:'Ready. Press Play when you are ready.',
    PLAYING_EXPOSED:'Playing.', PAUSED_EXPOSED:'Paused.',
    FAILED_COVERED:'Playback could not be verified. The picture and sound remain covered.'
  };
  function elapsed(value) {
    const seconds = Math.max(0,Math.floor(Number.isFinite(value)?value:0));
    const h = Math.floor(seconds/3600), m = Math.floor(seconds/60)%60, s = seconds%60;
    return h ? `${h}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}` : `${m}:${String(s).padStart(2,'0')}`;
  }
  function create(root, actions) {
    const shadow = root.attachShadow({mode:'closed'});
    const css = document.createElement('link');
    css.rel='stylesheet'; css.href=browser.runtime.getURL('src/styles/app.css'); shadow.append(css);
    const surface=document.createElement('main'); surface.className='surface';
    // Constant markup only. No provider or schedule strings enter HTML.
    surface.innerHTML=`<div class="content"><header><div class="brand">NHL UK<small>BUFFALO SABRES</small></div><button id="signin">DAZN sign in</button></header>
      <section class="setup"><h1 id="heading">Your games. No scores.</h1>
      <p id="subtitle">Connect your VPN to the UK before opening a replay.</p>
      <p class="note">Development build. Test only with a game you have already watched.</p>
      <div class="row" id="schedule-tools"><label>Season <select id="season"></select></label><button id="refresh">Load games</button></div>
      <div id="games" class="list"></div>
      <section id="selection" class="hidden"><label class="check"><input id="watched" type="checkbox">I have already watched this game.</label>
      <div class="row"><label>Catalogue connection <select id="transport"><option value="page">DAZN page</option><option value="background">Extension connection</option></select></label></div>
      <div class="row"><button id="resolve" class="primary" disabled>Find replay</button><button id="back">Back to games</button></div>
      <div id="variants" class="row"></div></section></section>
      <section class="player-panel"><p id="status" class="status" role="status" aria-live="polite">Loading games.</p>
      <div id="playback" class="hidden"><div id="elapsed" class="elapsed">Watched 0:00</div><div class="controls">
      <button id="prepare">Prepare beginning</button><button id="resume" class="hidden">Prepare resume</button>
      <button id="play" class="primary" disabled>Play</button><button id="pause" disabled>Pause</button>
      <button id="backward" disabled>Back 10s</button><button id="forward" disabled>Forward 30s</button>
      <button id="restart" disabled>Restart</button><button id="fullscreen" disabled>Fullscreen</button><button id="close">Close player</button>
      </div></div></section><div class="setup"><button id="diagnostic">Show diagnostic</button><pre id="diagnostic-output" class="hidden"></pre></div></div>`;
    shadow.append(surface);
    const $=id=>shadow.getElementById(id);
    let selected=null, currentVariants=[];
    const click=(id,fn)=>$(id).addEventListener('click',()=>Promise.resolve().then(fn).catch(()=>status('This action is unavailable. The page remains covered.')));
    function status(text) { $('status').textContent=text; }
    const dateLabel=game=>new Intl.DateTimeFormat(undefined,{month:'short',day:'numeric',hour:'numeric',minute:'2-digit'}).format(new Date(game.startUTC));
    function select(game) {
      actions.cancelResolve();
      selected=game; $('heading').textContent=`${game.awayTeam} at ${game.homeTeam}`; $('subtitle').textContent=dateLabel(game);
      $('games').classList.add('hidden');$('schedule-tools').classList.add('hidden');$('selection').classList.remove('hidden');
      $('watched').checked=false;$('resolve').disabled=true;$('variants').replaceChildren();status('Find the replay while DAZN remains covered.');
    }
    function games(items) {
      $('games').replaceChildren();
      // Show recent dates first without using played/live/final states.
      const now=Date.now(), ordered=[...items].sort((a,b)=>Math.abs(Date.parse(a.startUTC)-now)-Math.abs(Date.parse(b.startUTC)-now));
      for(const game of ordered) {
        const b=document.createElement('button'); b.className='game';
        const teams=document.createElement('strong');teams.textContent=`${game.awayTeam} at ${game.homeTeam}`;
        const time=document.createElement('time');time.textContent=dateLabel(game);time.dateTime=game.startUTC;
        b.append(teams,time);b.addEventListener('click',()=>select(game));$('games').append(b);
      }
      status(items.length?'Choose a game.':'No eligible games were returned. Playoff games remain hidden.');
    }
    function home() {
      actions.cancelResolve();
      selected=null;$('heading').textContent='Your games. No scores.';$('subtitle').textContent='Connect your VPN to the UK before opening a replay.';
      $('selection').classList.add('hidden');$('games').classList.remove('hidden');$('schedule-tools').classList.remove('hidden');$('playback').classList.add('hidden');
      status('Choose a game.');
    }
    function variants(values) {
      currentVariants=values;$('variants').replaceChildren();
      values.forEach((variant,i)=>{const b=document.createElement('button');
        b.textContent=`Open replay ${i+1}${variant.feed==='home'?' (home feed)':variant.feed==='away'?' (away feed)':''}`;
        b.addEventListener('click',()=>{b.disabled=true;actions.open(selected,currentVariants[i]).catch(()=>{b.disabled=false;status('The replay could not be opened.');});});$('variants').append(b);});
      status(values.length?'Choose a replay. Its picture and sound will stay covered.':'No exact replay could be verified.');
    }
    function player(game,bookmark) {
      selected=game;$('heading').textContent=`${game.awayTeam} at ${game.homeTeam}`;$('subtitle').textContent=dateLabel(game);
      $('games').classList.add('hidden');$('schedule-tools').classList.add('hidden');$('selection').classList.add('hidden');$('playback').classList.remove('hidden');
      $('resume').classList.toggle('hidden',!bookmark);$('resume').textContent=bookmark?`Prepare resume (${elapsed(bookmark.elapsedSeconds)})`:'Prepare resume';
      state('IDLE');
    }
    function state(value) {
      status(STATES[value]||'The page remains covered.');
      const active=['READY_COVERED','PLAYING_EXPOSED','PAUSED_EXPOSED'].includes(value);
      const preparing=['WAITING_FOR_MEDIA','WAITING_FOR_TIMELINE','POSITIONING','SETTLING'].includes(value);
      $('play').disabled=!['READY_COVERED','PAUSED_EXPOSED'].includes(value);$('pause').disabled=value!=='PLAYING_EXPOSED';
      for(const id of ['backward','forward','restart']) $(id).disabled=!active;
      $('fullscreen').disabled=!['PLAYING_EXPOSED','PAUSED_EXPOSED'].includes(value);
      $('prepare').disabled=preparing;$('resume').disabled=preparing;
    }
    const year=new Date().getUTCFullYear(), start=new Date().getUTCMonth()<6?year-1:year;
    for(let y=start;y>=start-2;y--) {const option=document.createElement('option');option.value=`${y}${y+1}`;option.textContent=`${y}/${y+1}`;$('season').append(option);}
    $('watched').addEventListener('change',()=>{$('resolve').disabled=!$('watched').checked;});
    click('refresh',()=>actions.schedule($('season').value));click('signin',actions.signIn);
    click('resolve',async()=>{if(!selected||!$('watched').checked)return;$('resolve').disabled=true;status('Finding the exact replay.');try{await actions.resolve(selected,$('transport').value);}finally{$('resolve').disabled=!$('watched').checked;}});
    click('back',home);click('prepare',()=>actions.prepare(0));click('resume',actions.resume);
    click('play',actions.play);click('pause',actions.pause);click('backward',()=>actions.skip(-10));click('forward',()=>actions.skip(30));
    click('restart',()=>actions.prepare(0));click('fullscreen',actions.fullscreen);click('close',async()=>{await actions.close();home();});
    click('diagnostic',()=>{$('diagnostic-output').textContent=actions.diagnostic();$('diagnostic-output').classList.toggle('hidden');});
    return {status,games,variants,player,state,home,season:()=>$('season').value,progress:value=>{$('elapsed').textContent=`Watched ${elapsed(value)}`;}};
  }
  N.appShell={create,elapsed};
  if(typeof module!=='undefined'&&module.exports) module.exports=N.appShell;
})();
