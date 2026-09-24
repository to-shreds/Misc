/* MIT. Modern UI for the unmodified Zork I release 119 / 880429. */
'use strict';
(() => {
const $=id=>document.getElementById(id), NS='misc-zork119-v1:', dialog=$('dialog');
let engine,view,bytes,metadata,entries=[],activeEntry=null,selected=null,current=null,nativeMode=null,toastTimer,history=[],historyAt=0,slots={},preferences={},storageWarning=false;
const store={get(key,fallback=null){try{return JSON.parse(localStorage.getItem(NS+key))??fallback;}catch{return fallback;}},set(key,value){try{localStorage.setItem(NS+key,JSON.stringify(value));return true;}catch{storageWarning=true;$('resume-status').textContent='Browser storage is unavailable. Export a save file to keep progress.';$('resume-status').classList.add('storage-warning');return false;}},remove(key){try{localStorage.removeItem(NS+key);}catch{}}};
function toast(text){$('toast').textContent=text;$('toast').hidden=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').hidden=true,4000);}
function el(tag,className,text){const node=document.createElement(tag);if(className)node.className=className;if(text!==undefined)node.textContent=text;return node;}
function button(text,fn,className=''){const b=el('button',className,text);b.type='button';b.addEventListener('click',fn);return b;}
function download(name,content,type){const url=URL.createObjectURL(new Blob([content],{type})),a=el('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),2000);}
function applyPreferences(){document.body.dataset.theme=preferences.theme||'night';document.documentElement.style.setProperty('--prose',(preferences.size||19)+'px');document.body.classList.toggle('reduce-motion',!!preferences.reduced);}
function makeEntry(entry){const node=el('article','entry');if(entry.command)node.append(el('div','entry-command',entry.command));node.append(el('div','entry-text',entry.text));return node;}
function renderJournal(){const frag=document.createDocumentFragment();entries.forEach(x=>frag.append(makeEntry(x)));$('journal').replaceChildren(frag);scrollLatest();}
function scrollLatest(){requestAnimationFrame(()=>$('journal').scrollTop=$('journal').scrollHeight);$('latest-button').hidden=true;}
function entryText(text){return text.replace(/\n?>\s*$/,'').replace(/^\n+/,'').trimEnd();}
function enabled(){const ready=!!engine&&!engine.ended&&!engine.error&&['line','char'].includes(engine.pending?.type);$('command').disabled=!ready;$('submit').disabled=!ready;document.querySelectorAll('[data-command]').forEach(b=>b.disabled=!ready);}
function saveResume(){if(engine.pending?.type!=='line'||engine.error)return;try{const data={version:1,vm:engine.snapshot(),entries:entries.slice(-500),known:view.known,dark:view.dark,time:Date.now()};if(store.set('resume',data)&&!storageWarning)$('resume-status').textContent='Progress kept on this device · '+new Date().toLocaleTimeString([],{hour:'numeric',minute:'2-digit'});}catch{}}
function onOutput(raw){
 const text=entryText(raw);
 if(text){if(!activeEntry){activeEntry={command:'',text:''};entries.push(activeEntry);}activeEntry.text+=(activeEntry.text?'\n\n':'')+text;renderJournal();}
 current=view.update(text);renderState();enabled();
 if(engine.ended){store.remove('resume');$('resume-status').textContent='The session has ended. Restore a save or start a new game.';}
 else saveResume();
}
function icon(name){if(/lantern|lamp|candl|torch/.test(name))return '☼';if(/sword|knife|axe/.test(name))return '†';if(/leaflet|book|map|paper/.test(name))return '▤';if(/bottle|water/.test(name))return '♧';if(/door|window|house/.test(name))return '⌂';if(/sack|bag|case|box/.test(name))return '▣';return '◇';}
function itemList(id,items,empty){const box=$(id);box.replaceChildren();if(!items.length){box.append(el('p','empty-state',empty));return;}for(const item of items){const b=button('',()=>select(item),'item-card'+(selected?.id===item.id?' selected':''));b.dataset.objectId=item.id;b.setAttribute('aria-label','Select '+item.name);b.append(el('span','item-icon',icon(item.name)),el('span','item-name',item.name),el('span','item-arrow','›'));box.append(b);}}
function renderState(){
 if(!current)return;
 $('location').textContent=current.name;$('score').textContent=current.score;$('moves').textContent=current.moves;
 $('scene-label').textContent=current.dark?'NO LIGHT. WATCH YOUR STEP.':current.moves===0?'YOUR ADVENTURE BEGINS':'THE GREAT UNDERGROUND EMPIRE';
 const name=current.name.toLowerCase();let mood=current.dark?'dark':/house|kitchen|living room|attic/.test(name)?'house':/forest|clearing|tree|path/.test(name)?'forest':/reservoir|river|dam|stream|shore|falls|beach/.test(name)?'water':'cave';
 // House outline is a title motif, not an interactive or literal room illustration.
 if(/kitchen|living room|attic/.test(name))mood='cave';
 $('scene').dataset.mood=mood;
 $('here-count').textContent=current.here.length;$('inventory-count').textContent=current.inventory.length;
 itemList('here-items',current.here,current.dark?'It is too dark to see.':'Objects appear here as Zork reveals them.');
 itemList('inventory-items',current.inventory,'You are not carrying anything visible.');
 if(selected&&!current.here.concat(current.inventory).some(o=>o.id===selected.id))clearSelection();
}
function select(item){selected=item;$('selection').hidden=false;$('selected-name').textContent=item.name;$('verbs').replaceChildren();for(const [label,verb] of [['Examine','examine'],['Take','take'],['Drop','drop'],['Open','open'],['Close','close'],['Read','read']])$('verbs').append(button(label,()=>send(verb+' '+selected.word)));$('verbs').append(button('More actions…',showBuilder));delete document.body.dataset.panel;renderState();}
function clearSelection(){selected=null;$('selection').hidden=true;document.querySelectorAll('.item-card.selected').forEach(b=>b.classList.remove('selected'));}
function send(command){command=String(command).trim();if(!command)return;if(!engine||engine.error){toast('The game is not ready. Reload to try again.');return;}if(engine.ended){toast('This session has ended. Use Restore or start a new game.');return;}
 if(!['line','char'].includes(engine.pending?.type)){toast('Finish or cancel the open dialog first.');return;}
 if(command.length>78){toast('This edition accepts at most 78 characters at a time.');return;}
 history.push(command);historyAt=history.length;activeEntry={command,text:''};entries.push(activeEntry);$('command').value='';renderJournal();
 try{engine.send(command);}catch(err){handleError(err);}
}
function handleError(error){console.error(error);enabled();toast('The interpreter reported an error. Your saved games are still available.');$('resume-status').textContent=String(error.message||error);}
function openDialog(title){if(dialog.open)dialog.close();nativeMode=null;$('dialog-title').textContent=title;$('dialog-body').replaceChildren();dialog.showModal();return $('dialog-body');}
function closeDialog(cancel=true){const wasNative=nativeMode;nativeMode=null;if(dialog.open)dialog.close();if(cancel&&wasNative&&engine?.pending?.type==='file'){try{engine.file(null);}catch(err){handleError(err);}}}
function showSave(mode){
 if(!engine)return;
 delete document.body.dataset.panel;
 if(engine.ended&&mode==='restore'){boot(null,false).then(()=>send('restore'));return;}
 send(mode);
}
function validateSave(data){
 const a=Uint8Array.from(data);if(a.length<24||a.length>131072||String.fromCharCode(...a.slice(0,4))!=='FORM'||String.fromCharCode(...a.slice(8,12))!=='IFZS')throw new Error('Choose a valid Zork I Quetzal save (.sav).');
 const test=new ZorkEngine(ZVM,bytes);test.start();if(test.vm.restore_file(a.buffer)!==2)throw new Error('This save belongs to a different story or release.');return a;
}
function filePrompt(p){
 const mode=engine.vm.fileref_data.func;
 if(mode!=='save'&&mode!=='restore'){
  const body=openDialog(mode==='output_stream'?'Transcript recording':'Command recording');nativeMode=mode;
  body.append(el('p','',mode==='output_stream'?'Zork can record this session. A text file will be offered when you turn recording off. You can also export the visible transcript in Settings.':'Select a plain-text command recording to replay through the original parser.'));
  if(mode==='output_stream'){body.append(button('Start recording',()=>{closeDialog(false);engine.file({name:'zork-transcript',text:''});},'primary'));}
  else{const input=el('input');input.type='file';input.accept='.txt,text/plain';input.onchange=async()=>{const file=input.files[0];if(file&&file.size<1024*1024){const text=await file.text();closeDialog(false);engine.file({name:'commands',text});}};body.append(input);}return;
 }
 const body=openDialog(mode==='save'?'Save your adventure':'Restore an adventure');nativeMode=mode;
 body.append(el('p','',mode==='save'?'Choose a slot. Saves stay in this browser. Use the ↓ button beside a saved slot to keep a portable file.':'Choose a saved position, or import a Zork I release 119 save file. Restoring replaces the current position.'));
 for(let i=1;i<=6;i++){
  const key=String(i),saved=slots[key],row=el('div','slot');
  const choose=button('',()=>{
   if(mode==='save'){
    if(saved&&!confirm('Replace the save in slot '+i+'?'))return;
    closeDialog(false);engine.file({name:key});
   }else if(saved){
    try{validateSave(saved.bytes);entries=Array.isArray(saved.entries)?structuredClone(saved.entries):[];view.known=saved.known||{};activeEntry={command:'restore',text:''};entries.push(activeEntry);closeDialog(false);engine.file({name:key,bytes:saved.bytes});clearSelection();renderJournal();toast('Saved adventure restored.');}catch(error){toast(error.message);}
   }
  },'slot-main');choose.append(el('span','',saved?'Slot '+i+' · '+saved.room:'Slot '+i+' · Empty'));choose.append(el('small','',saved?saved.score+' points · '+saved.moves+' moves · '+new Date(saved.time).toLocaleString():'No saved game'));choose.disabled=mode==='restore'&&!saved;row.append(choose);
  if(saved){const exp=button('↓',()=>download('zork1-slot-'+i+'.sav',Uint8Array.from(saved.bytes),'application/octet-stream'),'slot-export');exp.title='Export slot '+i;exp.setAttribute('aria-label','Export slot '+i);row.append(exp);}body.append(row);
 }
 if(mode==='restore'){
  const label=el('label','text-button','Import a .sav file '),input=el('input');input.type='file';input.accept='.sav,.qzl,application/octet-stream';input.style.maxWidth='100%';input.onchange=async()=>{const f=input.files[0];if(!f)return;try{if(f.size>131072)throw new Error('That file is too large to be a Zork save.');const data=validateSave(new Uint8Array(await f.arrayBuffer()));entries=[];view.known={};activeEntry={command:'restore imported save',text:''};entries.push(activeEntry);closeDialog(false);engine.file({name:'import',bytes:Array.from(data)});clearSelection();renderJournal();toast('Imported save restored.');}catch(err){toast(err.message);}};label.append(input);body.append(label);
 }
 body.append(el('p','microcopy','Closing this dialog cancels the original SAVE or RESTORE command.'));
}
function closedFile(str){
 if(str.mode===1&&str.bytes?.length){const s=engine.state();slots[str.name]={bytes:str.bytes,room:s.name,score:s.score,moves:s.moves,time:Date.now(),entries:structuredClone(entries.slice(-500)),known:structuredClone(view.known)};
  if(store.set('slots',slots))toast('Saved to slot '+str.name+'.');else{download('zork1-save.sav',Uint8Array.from(str.bytes),'application/octet-stream');toast('Browser storage failed. Your save was exported instead.');}
 }else if(str.text&&str.mode!==2)download('zork-transcript.txt',str.text,'text/plain');
}
function showBuilder(){
 if(!selected)return;const body=openDialog('Choose an action'),form=el('form','builder'),verb=el('select'),subject=el('input'),target=el('input'),preview=el('div','builder-preview');
 const patterns=[['Examine','examine {a}'],['Look inside','look in {a}'],['Look underneath','look under {a}'],['Move','move {a}'],['Push','push {a}'],['Pull','pull {a}'],['Turn on','turn on {a}'],['Turn off','turn off {a}'],['Open','open {a}'],['Close','close {a}'],['Unlock with…','unlock {a} with {b}'],['Lock with…','lock {a} with {b}'],['Put inside…','put {a} in {b}'],['Put on…','put {a} on {b}'],['Give to…','give {a} to {b}'],['Throw at…','throw {a} at {b}'],['Tie to…','tie {a} to {b}'],['Untie','untie {a}'],['Attack with…','attack {a} with {b}'],['Cut with…','cut {a} with {b}'],['Break with…','break {a} with {b}'],['Dig with…','dig {a} with {b}'],['Fill with…','fill {a} with {b}'],['Pour on…','pour {a} on {b}'],['Light with…','light {a} with {b}'],['Extinguish','extinguish {a}'],['Eat','eat {a}'],['Drink','drink {a}'],['Smell','smell {a}'],['Listen to','listen to {a}'],['Touch','touch {a}'],['Enter','enter {a}'],['Climb','climb {a}']];
 patterns.forEach(([label,value])=>{const o=el('option','',label);o.value=value;verb.append(o);});subject.value=selected.word;subject.maxLength=50;target.maxLength=50;target.placeholder='Another object…';target.setAttribute('list','target-nouns');const list=el('datalist');list.id='target-nouns';for(const o of current.here.concat(current.inventory)){const option=el('option');option.value=o.word;option.label=o.name;list.append(option);}const targetLabel=el('label','', 'With / in / to');targetLabel.append(target);
 for(const [title,input] of [['Action',verb],['Object',subject]]){const label=el('label','',title);label.append(input);form.append(label);}form.append(targetLabel,list,preview);
 const update=()=>{targetLabel.hidden=!verb.value.includes('{b}');preview.textContent=verb.value.replace('{a}',subject.value.trim()).replace('{b}',target.value.trim()||'…');};form.addEventListener('input',update);update();
 const execute=button('Send to Zork',()=>{} ,'primary');execute.type='submit';form.append(execute);form.onsubmit=event=>{event.preventDefault();if(!subject.value.trim()||(verb.value.includes('{b}')&&!target.value.trim())){target.focus();return;}const command=verb.value.replace('{a}',subject.value.trim()).replace('{b}',target.value.trim());closeDialog();send(command);};body.append(el('p','', 'These are general commands, not suggested solutions. The original parser decides what happens.'),form);
}
function showNotes(){const body=openDialog('Your field notes'),text=el('textarea','notes');text.value=store.get('notes','');text.placeholder='Keep your own map, remember clues, or note something to try. No hints are added automatically.';text.setAttribute('aria-label','Your adventure notes');text.oninput=()=>store.set('notes',text.value);body.append(text,el('p','microcopy','Notes are stored on this device. Opening this panel does not take a turn.'),button('Export notes',()=>download('zork-notes.txt',text.value,'text/plain')));}
function showHelp(){const body=openDialog('Welcome, adventurer');
 body.innerHTML='<p>This is <strong>the original Zork I</strong>, not a reimagining or an AI-generated adventure. The original program runs locally in your browser.</p><h3>Explore your own way</h3><p>Tap the compass to move. Tap an object under <strong>In view</strong> or <strong>Inventory</strong>, then choose an action. <strong>More actions</strong> lets you combine objects or build a longer command.</p><p>You can always type directly. The parser accepts directions, actions, multiple objects, and more complicated sentences. It may ask a follow-up question. Answer in the command box.</p><h3>The old rules still apply</h3><p>There are no automatic solutions, highlighted exits, added quests, or softened hazards. The original timing, inventory limits, randomness, deaths, and possible unwinnable situations remain. Save deliberately.</p><p>Opening panels and selecting objects never sends a hidden command. Pressing an action or direction sends exactly the command shown in the transcript. The center compass button sends LOOK.</p><h3>Saving</h3><p><strong>Save</strong> and <strong>Restore</strong> use the game’s own save mechanism. Six browser slots are available, with portable .sav export and import. Export important saves, because clearing browser data removes local progress.</p><p>The current command-prompt state is also kept locally so that refreshing can resume it. No account, server, API key, or Internet connection is used for gameplay after the page assets load.</p><h3>Keyboard</h3><p>Enter sends a command. Up and Down recall your command history. Escape closes a panel or deselects an object. On a physical keyboard, Alt + an arrow sends a cardinal direction. Those movement shortcuts are disabled while typing.</p><p class="fidelity-note">Independent interface. Zork I release 119 / serial 880429. Original game © Microsoft, MIT license; interpreter © the ifvms.js team, MIT license. Zork trademarks remain with their owners. No endorsement is implied.<br><a href="data/LICENSE-zork.txt" target="_blank" rel="noopener">Game license</a> · <a href="vendor/LICENSE-ifvms.txt" target="_blank" rel="noopener">Interpreter license</a> · <a href="https://github.com/to-shreds/Misc/tree/main/zork" target="_blank" rel="noopener">Source</a></p>';
}
function showSettings(){
 const body=openDialog('Make yourself at home');
 const theme=el('select');for(const [v,t]of[['night','Night forest'],['paper','Paper & ink']]){const o=el('option','',t);o.value=v;theme.append(o);}theme.value=preferences.theme||'night';theme.onchange=()=>{preferences.theme=theme.value;applyPreferences();store.set('preferences',preferences);};
 const size=el('input');size.type='range';size.min=16;size.max=26;size.value=preferences.size||19;size.oninput=()=>{preferences.size=Number(size.value);applyPreferences();store.set('preferences',preferences);};
 const motion=el('input');motion.type='checkbox';motion.checked=!!preferences.reduced;motion.onchange=()=>{preferences.reduced=motion.checked;applyPreferences();store.set('preferences',preferences);};
 for(const [label,input]of[['Theme',theme],['Reading size',size],['Reduce motion',motion]]){const row=el('label','setting-row',label);row.append(input);body.append(row);}
 const actions=el('div','dialog-actions');actions.append(button('How to play',showHelp),button('Field notes',showNotes),button('Export transcript',()=>download('zork-transcript.txt',entries.map(e=>(e.command?'> '+e.command+'\n':'')+e.text).join('\n\n'),'text/plain')),button('Start new game',()=>{if(confirm('Start a new adventure? Your current unsaved position will be replaced. Manual saves and notes will remain.')){closeDialog();store.remove('resume');boot(null,false);}},'danger'));body.append(actions,el('p','microcopy','Interface 1.0.1 · Original Zork I release 119 · No analytics or external services.'));
}
async function boot(resume=undefined,notify=true){
 $('loading').hidden=false;$('loading').textContent='Opening the Great Underground Empire…';$('command').disabled=true;
 try{
  if(!bytes&&window.ZORK_EMBEDDED){bytes=Uint8Array.from(atob(window.ZORK_EMBEDDED.story),c=>c.charCodeAt(0));metadata=window.ZORK_EMBEDDED.objects;}
  if(!bytes){const responses=await Promise.all([fetch('data/zork1.z3'),fetch('data/objects.json')]);if(responses.some(r=>!r.ok))throw new Error('The game assets could not be loaded. Reload this page.');bytes=new Uint8Array(await responses[0].arrayBuffer());metadata=await responses[1].json();}
  if(bytes.length!==86838||bytes[0]!==3)throw new Error('Unexpected game file. The original Zork asset is missing or damaged.');
  // Check the actual game asset before starting any code.
  if(globalThis.crypto?.subtle){const digest=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes)),b=>b.toString(16).padStart(2,'0')).join('');if(digest!==STORY_SHA256)throw new Error('The game file checksum does not match the original.');}
  entries=[];activeEntry=null;clearSelection();engine=new ZorkEngine(ZVM,bytes);view=new ZorkView(engine,metadata);engine.onclosefile=closedFile;engine.onfile=filePrompt;
  let initial='';engine.onupdate=t=>initial+=t;engine.start();
  const saved=resume===undefined?store.get('resume'):resume;
  if(saved){try{engine.restore(saved.vm);entries=Array.isArray(saved.entries)?saved.entries:[];view.known=saved.known||{};view.dark=!!saved.dark;current=view.update();renderJournal();renderState();if(notify)toast('Resumed your adventure on this device.');}catch(error){store.set('resume-recovery',saved);onOutput(initial);toast('The previous resume point could not be loaded. Manual saves are unchanged.');}}
  else onOutput(initial);
  engine.onupdate=onOutput;current=view.update();renderState();enabled();saveResume();$('loading').hidden=true;
  // Expose the real engine for automated regression checks; no game-state edits are performed by the UI.
  window.zork={engine,view,send,get entries(){return entries;},get state(){return current;}};
 }catch(error){console.error(error);$('loading').textContent=error.message||'The adventure could not be loaded.';$('loading').append(button('Retry',()=>boot(undefined,false),'retry-button'));}
}
const STORY_SHA256='37084966477dff679282de42974b2077156b1bd68fad92a65d4ea94d8eb64d79';
preferences=store.get('preferences',{});slots=store.get('slots',{});applyPreferences();
$('command-form').onsubmit=event=>{event.preventDefault();send($('command').value);};
$('command').onkeydown=event=>{if(event.key==='ArrowUp'||event.key==='ArrowDown'){event.preventDefault();historyAt=Math.max(0,Math.min(history.length,historyAt+(event.key==='ArrowUp'?-1:1)));$('command').value=history[historyAt]||'';}};
document.addEventListener('click',event=>{const b=event.target.closest('[data-command]');if(b&&!b.disabled)send(b.dataset.command);});
document.addEventListener('keydown',event=>{if(event.key==='Escape'&&!dialog.open){delete document.body.dataset.panel;clearSelection();}if(event.altKey&&!dialog.open&&!/INPUT|TEXTAREA|SELECT/.test(document.activeElement.tagName)){const dir={ArrowUp:'north',ArrowDown:'south',ArrowLeft:'west',ArrowRight:'east'}[event.key];if(dir){event.preventDefault();send(dir);}}});
$('clear-selection').onclick=clearSelection;$('save-button').onclick=$('mobile-save').onclick=()=>showSave('save');$('restore-button').onclick=()=>showSave('restore');$('notes-button').onclick=showNotes;$('help-button').onclick=$('mobile-help').onclick=showHelp;$('settings-button').onclick=showSettings;
$('close-dialog').onclick=()=>closeDialog();dialog.addEventListener('cancel',event=>{event.preventDefault();closeDialog();});
$('move-panel').onclick=()=>{document.body.dataset.panel==='move'?delete document.body.dataset.panel:document.body.dataset.panel='move';};$('items-panel').onclick=()=>{document.body.dataset.panel==='items'?delete document.body.dataset.panel:document.body.dataset.panel='items';};$('close-panel').onclick=()=>delete document.body.dataset.panel;
$('journal').onscroll=()=>{$('latest-button').hidden=$('journal').scrollHeight-$('journal').clientHeight-$('journal').scrollTop<180;};$('latest-button').onclick=scrollLatest;
window.addEventListener('pagehide',saveResume);
boot();
})();
