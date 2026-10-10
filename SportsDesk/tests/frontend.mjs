import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';

const html=readFileSync(new URL('../index.html',import.meta.url),'utf8');
const script=html.match(/<script>([\s\S]*?)<\/script>/)[1];
function setup(){
 const nodes=new Map();
 const doc={getElementById(id){if(!nodes.has(id))nodes.set(id,{disabled:false,textContent:'',value:'',open:true,addEventListener(){},close(){this.open=false;},showModal(){this.open=true;}});return nodes.get(id);},querySelectorAll(){return[];}};
 const saved=new Map();const pending=[];
 const context=vm.createContext({URL,AbortController,console,setTimeout,clearTimeout,document:doc,window:{addEventListener(){},scrollY:33},location:{assign(url){context.opened=url;}},localStorage:{getItem:k=>saved.get(k)||null,setItem:(k,v)=>saved.set(k,v)},fetch:(url,options)=>new Promise((resolve,reject)=>pending.push({url:String(url),options,resolve,reject}))});
 vm.runInContext(script.split('restore();render();if')[0],context);
 vm.runInContext('render=()=>{};notify=(text,error)=>{globalThis.lastMessage={text,error}};',context);
 return {context,pending,run:code=>vm.runInContext(code,context)};
}
const catalog=source=>({source,fetchedAt:new Date().toISOString(),events:[{id:'nfl:123',title:'Team A vs Team B',sport:'NFL',eventUrl:new URL('/nfl/a-b/123',source).href,live:true,sourceTime:''}]});
const response=data=>({ok:true,headers:{get:()=> 'application/json'},json:async()=>data});

test('bad replacement preserves the old source and catalog',async()=>{const s=setup();s.context.old=catalog('https://old.example.com/');s.run('state.config={source:old.source,helper:"https://helper.example.com/"};state.catalog=old');const p=s.run('loadCatalog({source:"https://new.example.com/",helper:"https://helper.example.com/"})');s.pending[0].resolve(response({events:[]}));await p;assert.equal(s.run('state.config.source'),'https://old.example.com/');assert.equal(s.run('state.catalog.events.length'),1);assert.match(s.context.lastMessage.text,/unchanged/);});
test('valid replacement commits effective source and events together',async()=>{const s=setup();const p=s.run('loadCatalog({source:"https://moved.example.com/",helper:"https://helper.example.com/"})');s.pending[0].resolve(response(catalog('https://new.example.com/home6')));await p;assert.equal(s.run('state.config.source'), 'https://new.example.com/home6');assert.equal(s.run('state.catalog.source'),s.run('state.config.source'));});
test('late earlier response cannot overwrite a newer source',async()=>{const s=setup();const a=s.run('loadCatalog({source:"https://one.example.com/",helper:"https://helper.example.com/"})');const b=s.run('loadCatalog({source:"https://two.example.com/",helper:"https://helper.example.com/"})');s.pending[1].resolve(response(catalog('https://two.example.com/')));await b;s.pending[0].resolve(response(catalog('https://one.example.com/')));await a;assert.equal(s.run('state.catalog.source'),'https://two.example.com/');assert.equal(s.pending[0].options.signal.aborted,true);});
test('foreign event links are rejected',()=>{const s=setup();s.context.bad=catalog('https://source.example.com/');s.context.bad.events[0].eventUrl='https://evil.example.com/nfl/a-b/123';assert.throws(()=>s.run('validateCatalog(bad)'),/outside/);});
test('script URL cannot become a playback destination',async()=>{const s=setup();s.context.event=catalog('https://source.example.com/').events[0];s.context.button={};s.run('state.config={source:"https://source.example.com/",helper:"https://helper.example.com/"}');const p=s.run('watchEvent(event,button)');s.pending[0].resolve(response({playerUrl:'javascript:alert(1)'}));await p;assert.equal(s.context.opened,undefined);assert.equal(s.context.lastMessage.error,true);});
test('Watch navigates directly to source player with no video request to helper',async()=>{const s=setup();s.context.event=catalog('https://source.example.com/').events[0];s.context.button={};s.run('state.config={source:"https://source.example.com/",helper:"https://helper.example.com/"}');const p=s.run('watchEvent(event,button)');s.pending[0].resolve(response({playerUrl:'https://player.example.com/embed/123'}));await p;assert.equal(s.context.opened,'https://player.example.com/embed/123');assert.equal(s.pending.length,1);assert.equal(s.pending[0].url,'https://helper.example.com/api/player');assert.equal(s.run('state.scroll'),33);});
test('cancelled source change cannot commit later',async()=>{const s=setup();const p=s.run('loadCatalog({source:"https://new.example.com/",helper:"https://helper.example.com/"},true)');s.run('cancelSettings()');s.pending[0].resolve(response(catalog('https://new.example.com/')));await p;assert.equal(s.run('state.catalog'),null);});
