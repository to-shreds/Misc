/* MIT. Read-only presentation model. Never executes or probes a game command. */
(function(root){
'use strict';
class ZorkView {
 constructor(engine,meta){this.engine=engine;this.meta=meta;this.known={};this.dark=false;}
 update(text=''){
  const e=this.engine,s=e.state(),objects=e.objects(),byId=new Map(objects.map(o=>[o.id,o]));
  const flag=(o,n)=>!!(o&&(o.flags&(1<<(31-n))));
  const physical=(o,target,depth=0)=>{
   if(depth>30||!o||!o.parent)return false;
   if(o.parent===target)return true;
   const p=byId.get(o.parent);
   if(!p||flag(p,7))return false;
   return (flag(p,10)||flag(p,11)||flag(p,12))&&physical(p,target,depth+1);
  };
  const held=o=>physical(o,this.meta.player);
  const local=o=>physical(o,s.room);
  const hasLight=flag(byId.get(s.room),19)||objects.some(o=>!flag(o,7)&&flag(o,19)&&(held(o)||local(o)));
  this.dark=!hasLight;
  // The original sometimes permits sight after death without a physical lamp.
  // Only override darkness when the actual narration explicitly describes the room.
  if(text.split('\n').includes(s.name)&&!text.includes('It is pitch black.'))this.dark=false;
  if(text.includes('It is pitch black.'))this.dark=true;
  const roomMeta=this.meta.objects[s.room]||{};
  const globals=new Set(roomMeta.globals||[]),known=new Set(this.known[s.room]||[]);
  const norm=' '+text.toLowerCase().replace(/[^a-z0-9\s'-]/g,' ').replace(/\s+/g,' ')+' ';
  const inScope=o=>!flag(o,7)&&o.id!==this.meta.player&&!this.meta.objects[o.id]?.room&&(local(o)||held(o)||globals.has(o.id));
  for(const o of objects){if(!inScope(o))continue;const aliases=this.meta.objects[o.id]?.aliases||[o.name.toLowerCase()];if(aliases.some(a=>a.length>=3&&norm.includes(' '+a+' ')))known.add(o.id);}
  this.known[s.room]=Array.from(known);
  const wrap=o=>({...o,word:this.meta.objects[o.id]?.word||o.name.toLowerCase(),held:held(o)});
  const inventory=objects.filter(o=>inScope(o)&&held(o)&&(!flag(o,14)||known.has(o.id))).map(wrap);
  const here=this.dark?[]:objects.filter(o=>inScope(o)&&!held(o)&&(known.has(o.id)||(!flag(o,14)&&local(o)))).map(wrap);
  this.current={...s,dark:this.dark,here,inventory};return this.current;
 }
}
root.ZorkView=ZorkView;if(typeof module!=='undefined')module.exports=ZorkView;
})(typeof window==='undefined'?globalThis:window);
