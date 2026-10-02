'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const base=path.join(__dirname,'..');
const game={id:'2026020001',gameDate:'2026-10-10',startUTC:'2026-10-10T23:00:00.000Z',homeTeam:'BUF',awayTeam:'BOS'};
const variant={gameId:game.id,gameDate:game.gameDate,startUTC:game.startUTC,homeTeam:'BUF',awayTeam:'BOS',eventId:'event1',variantId:'asset1',route:'https://www.dazn.com/en-GB/home/event1/asset1',feed:'unknown',kind:'replay'};
function setup(raw={games:[]}) {
  let handler,removed;const stored={},requested=[];
  const browser={runtime:{id:'test-addon',onMessage:{addListener:fn=>handler=fn}},
    storage:{session:{get:async key=>({[key]:stored[key]}),set:async data=>Object.assign(stored,data),remove:async key=>{delete stored[key];}}},
    tabs:{onRemoved:{addListener:fn=>removed=fn},create:async()=>{}},action:{onClicked:{addListener:()=>{}}}};
  const sandbox={browser,URL,URLSearchParams,AbortSignal,Date,Map,console,
    fetch:async(url)=>{requested.push(url);return {ok:true,text:async()=>JSON.stringify(raw)};}};
  vm.createContext(sandbox);
  for(const file of ['content/schedule.js','content/route-resolver.js','background.js'])vm.runInContext(fs.readFileSync(path.join(base,'src',file),'utf8'),sandbox);
  const sender={id:'test-addon',frameId:0,url:'https://www.dazn.com/en-GB/home',tab:{id:10}};
  return {call:(body,who=sender)=>handler(body,who),stored,requested,sender,removed};
}
test('background refuses foreign senders and child frames',async()=>{
  const h=setup();for(const sender of [{...h.sender,id:'foreign'},{...h.sender,frameId:1},{...h.sender,url:'https://www.dazn.com.evil.test/'}])assert.equal(await h.call({type:'SCHEDULE',season:'20262027'},sender),undefined);
  assert.equal(h.requested.length,0);
});
test('background schedule response excludes score, state, title and playoff fields',async()=>{
  const raw={games:[{id:2026020001,gameType:2,gameDate:game.gameDate,startTimeUTC:game.startUTC,homeTeam:{abbrev:'BUF',score:99},awayTeam:{abbrev:'BOS',score:88},gameState:'FINAL',title:'outcome'},
    {id:2026030001,gameType:3,gameDate:game.gameDate,startTimeUTC:game.startUTC,homeTeam:{abbrev:'BUF'},awayTeam:{abbrev:'BOS'}}]};
  const h=setup(raw),result=await h.call({type:'SCHEDULE',season:'20262027'});
  assert.equal(result.ok,true);assert.equal(result.games.length,1);
  assert.deepEqual(JSON.parse(JSON.stringify(result.games[0])),game);
  await h.call({type:'SCHEDULE',season:'20262027'});assert.equal(h.requested.length,1);
});
test('season injection cannot turn the background into an arbitrary fetch proxy',async()=>{
  const h=setup();assert.equal((await h.call({type:'SCHEDULE',season:'https://example.test/'})).ok,false);assert.equal(h.requested.length,0);
});
test('pending state strips unknown data and isolates tabs',async()=>{
  const h=setup();assert.equal((await h.call({type:'PENDING_SET',game:{...game,score:99},variant:{...variant,description:'outcome',token:'secret'},target:0})).ok,true);
  const result=await h.call({type:'PENDING_GET'});assert.ok(result.pending);assert.equal(JSON.stringify(result).includes('secret'),false);assert.equal(JSON.stringify(result).includes('score'),false);
  assert.equal((await h.call({type:'PENDING_GET'},{...h.sender,tab:{id:11}})).pending,null);
});
test('pending state rejects wrong game, altered matchup, live and nonfinite targets',async()=>{
  const h=setup();
  for(const bad of [{...variant,gameId:'2026020002'},{...variant,awayTeam:'CBJ'},{...variant,kind:'live'},{...variant,route:'https://evil.test/'}])assert.equal((await h.call({type:'PENDING_SET',game,variant:bad,target:0})).ok,false);
  assert.equal((await h.call({type:'PENDING_SET',game,variant,target:Infinity})).ok,false);
  assert.deepEqual(h.stored,{});
});
test('redirect retries are bounded and tab close removes pending identity',async()=>{
  const h=setup();await h.call({type:'PENDING_SET',game,variant,target:0});
  assert.equal((await h.call({type:'PENDING_REDIRECT'})).ok,true);assert.equal((await h.call({type:'PENDING_REDIRECT'})).ok,true);assert.equal((await h.call({type:'PENDING_REDIRECT'})).ok,false);
  await h.removed(10);assert.equal((await h.call({type:'PENDING_GET'})).pending,null);
});
test('expired pending selections cannot reopen a stale game',async()=>{
  const h=setup();await h.call({type:'PENDING_SET',game,variant,target:0});h.stored['pending:10'].at=Date.now()-3600001;
  assert.equal((await h.call({type:'PENDING_GET'})).pending,null);
});
test('production manifest has narrow hosts, MV3 event page and early user CSS',()=>{
  const m=JSON.parse(fs.readFileSync(path.join(base,'manifest.json'),'utf8'));
  assert.equal(m.manifest_version,3);assert.deepEqual(m.permissions,['storage']);assert.ok(m.background.scripts);assert.equal(m.background.service_worker,undefined);
  assert.ok(Object.hasOwn(m.browser_specific_settings,'gecko_android'));assert.deepEqual(m.browser_specific_settings.gecko.data_collection_permissions.required,['none']);
  assert.ok(m.content_scripts.every(c=>c.run_at==='document_start'));assert.equal(m.content_scripts[1].css_origin,'user');assert.ok(!JSON.stringify(m).includes('<all_urls>'));
});
