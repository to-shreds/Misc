/* MIT. A presentation-only Glk adapter for the unmodified ifvms.js Z-machine. */
(function (root) {
'use strict';
class RefBox { constructor(value=0){this.value=value;} get_value(){return this.value;} set_value(v){this.value=v;} }
class RefStruct { constructor(){this.fields=[];} get_field(i){return this.fields[i]||0;} set_field(i,v){this.fields[i]=v;} push_field(v){this.fields.push(v);} }
const copy = x => JSON.parse(JSON.stringify(x));
class ZorkEngine {
 constructor(ZVMClass, bytes) {
  this.output=''; this.pending=null; this.windows=[]; this.streams=[]; this.error=null; this.ended=false; this.onupdate=()=>{}; this.onfile=()=>{}; this.onclosefile=()=>{};
  const self=this, noop=()=>{}, glk={RefBox,RefStruct,
   glk_gestalt:()=>0,
   glk_stylehint_set:noop, glk_stylehint_clear:noop, glk_set_style:noop,
   glk_window_open(split,method,size,type,rock){const w={rock,type,size,str:{kind:'window',rock}};self.windows.push(w);return w;},
   glk_window_close(w){self.windows=self.windows.filter(x=>x!==w);},
   glk_window_get_parent:w=>w, glk_window_set_arrangement(w,method,size){if(w)w.size=size;},
   glk_window_get_size(w,width,height){if(width)width.set_value(96);if(height)height.set_value(w.type===3?32:w.size);},
   glk_set_window(w){self.window=w;},
   glk_window_get_stream:w=>w.str,
   glk_window_move_cursor:noop, glk_window_clear:noop,
   glk_put_jstring(text){if(self.window?.rock===201)self.output+=text;},
   glk_put_jstring_stream(str,text){if(str?.kind==='window'){if(str.rock===201)self.output+=text;}else if(str){str.text=(str.text||'')+text;}},
   glk_put_char_stream_uni(str,code){this.glk_put_jstring_stream(str,String.fromCodePoint(code));},
   glk_request_line_event_uni(win,buffer){self.pending={type:'line',win,buffer};},
   glk_request_char_event_uni(win){self.pending={type:'char',win};},
   glk_select(event){self.event=event;},
   glk_exit(){self.ended=true;self.pending=null;},
   glk_fileref_create_by_prompt(usage,mode,rock){self.pending={type:'file',usage,mode,rock};},
   glk_fileref_destroy:noop,
   glk_stream_open_file(ref,mode,rock){const str={kind:'file',mode,rock,bytes:ref.bytes||[],text:ref.text||'',name:ref.name||'Save',pos:0};self.streams.push(str);return str;},
   glk_stream_open_file_uni(ref,mode,rock){return this.glk_stream_open_file(ref,mode,rock);},
   glk_put_buffer_stream(str,bytes){str.bytes=Array.from(bytes);},
   glk_get_buffer_stream(str,buffer){const n=Math.min(buffer.length,str.bytes.length);buffer.set(str.bytes.slice(0,n));return n;},
   glk_get_char_stream_uni(str){return str.pos<str.text.length?str.text.charCodeAt(str.pos++):-1;},
   glk_get_line_stream_uni(str,buffer){let n=0;while(n<buffer.length&&str.pos<str.text.length){const c=str.text.charCodeAt(str.pos++);buffer[n++]=c;if(c===10)break;}return n;},
   glk_stream_close(str){self.onclosefile(str);self.streams=self.streams.filter(x=>x!==str);},
   update(){self.onupdate(self.drain());if(self.pending?.type==='file')self.onfile(self.pending);},
   fatal_error(error){self.error=error instanceof Error?error:new Error(String(error));self.pending=null;},
  };
  this.glk=glk; this.vm=new ZVMClass(); this.vm.prepare(new Uint8Array(bytes),{Glk:glk});
 }
 start(){this.vm.init();if(this.error)throw this.error;}
 drain(){const text=this.output;this.output='';return text;}
 send(command){
  if(this.error)throw this.error;
  const p=this.pending;if(!p||p.type==='file'||this.ended)throw new Error('The game is not waiting for a command.');
  this.pending=null;
  if(p.type==='line'){
   const text=String(command).replace(/[\r\n]/g,' ').slice(0,p.buffer.length);
   for(let i=0;i<text.length;i++)p.buffer[i]=text.charCodeAt(i);
   this.event.set_field(0,3);this.event.set_field(1,p.win);this.event.set_field(2,text.length);this.event.set_field(3,0);
  }else{this.event.set_field(0,2);this.event.set_field(1,p.win);this.event.set_field(2,String(command).charCodeAt(0)||13);}
  this.vm.resume();if(this.error)throw this.error;
 }
 file(ref){if(this.pending?.type!=='file')throw new Error('No file dialog is open.');this.pending=null;this.vm.resume(ref||null);if(this.error)throw this.error;}
 name(id){if(!id)return '';const v=this.vm,p=v.m.getUint16(v.objects+id*9+7);return String(v.decode(p+1,v.m.getUint8(p)*2));}
 state(){const v=this.vm,m=v.m;let score=m.getUint16(v.globals+2);if(score>32767)score-=65536;return {room:m.getUint16(v.globals),name:this.name(m.getUint16(v.globals)),score,moves:m.getUint16(v.globals+4),ended:this.ended};}
 objects(){const v=this.vm,list=[];let limit=v.staticmem;for(let id=1;id<=255;id++){const addr=v.objects+id*9;if(addr+9>limit)break;const props=v.m.getUint16(addr+7);limit=Math.min(limit,props);if(props<addr+9||props>=v.staticmem)break;list.push({id,name:this.name(id),parent:v.get_parent(id),child:v.get_child(id),sibling:v.get_sibling(id),flags:v.m.getUint32(addr)});}return list;}
 snapshot(){
  if(this.pending?.type!=='line'||this.ended)throw new Error('A resume point can only be saved at a command prompt.');
  const v=this.vm;return {version:1,signature:v.signature,ram:Array.from(new Uint8Array(v.save_file(v.pc))),read:copy(v.read_data),io:copy(v.io),seed:v.xorshift_seed};
 }
 restore(snapshot){
  const v=this.vm;if(snapshot?.version!==1||snapshot.signature!==v.signature||!Array.isArray(snapshot.ram)||snapshot.ram.length>131072)throw new Error('This resume file does not belong to this edition of Zork.');
  if(v.restore_file(Uint8Array.from(snapshot.ram).buffer)!==2)throw new Error('The resume file could not be read.');
  v.read_data=copy(snapshot.read);v.io=copy(snapshot.io);v.xorshift_seed=snapshot.seed;v.quit=0;this.ended=false;this.error=null;
  this.glk.glk_set_window(v.mainwin);this.glk.glk_request_line_event_uni(v.mainwin,v.read_data.buffer,0);
  v.glk_event=new RefStruct();this.glk.glk_select(v.glk_event);this.output='';
 }
}
root.ZorkEngine=ZorkEngine;if(typeof module!=='undefined')module.exports=ZorkEngine;
})(typeof window==='undefined'?globalThis:window);
