// ==UserScript==
// @name         Santa Fe API Lab network helper
// @namespace    https://to-shreds.github.io/Misc/SantaFe/
// @version      1.0.0
// @description  Sends the API Lab's allowlisted requests directly from your browser to Hyundai. No cloud relay or saved credentials.
// @match        https://to-shreds.github.io/Misc/SantaFe/*
// @match        https://to-shreds.github.io/Misc/santafe/*
// @connect      api.telematics.hyundaiusa.com
// @grant        GM_xmlhttpRequest
// @run-at       document-start
// ==/UserScript==
(() => {
  'use strict';
  const allowed=new Map([
    ['/v2/ac/oauth/token',['GET','POST']],
    ['/ac/v2/rcs/rvs/vehicleStatus',['GET']],
    ['/ac/v2/rmt/getRunningStatus',['GET']],
    ...['rcs/rdo/off','rcs/rdo/on','rcs/rhl/light','rcs/rhl/hnl','rcs/rsc/start','rcs/rsc/stop','evc/fatc/start','evc/fatc/stop'].map(p=>['/ac/v2/'+p,['POST']])
  ]);
  const headerNames=new Set(['content-type','accept','from','to','language','offset','refresh','encryptflag','brandindicator','client_id','clientsecret','origin','referer','username','accesstoken','bluelinkservicepin','registrationid','gen','vin','appcloud-vin','tid','login_id','service_type']);
  const requests=new Map();let channel=null;
  function reply(msg,rest){window.postMessage({type:'SF_HELPER_RESPONSE',channel:msg.channel,id:msg.id,...rest},location.origin);}
  window.addEventListener('message',event=>{
    const msg=event.data;
    if(event.source!==window||event.origin!==location.origin||!msg||msg.type!=='SF_HELPER_REQUEST'||typeof msg.channel!=='string'||typeof msg.id!=='string')return;
    if(msg.hello){if(!channel)channel=msg.channel;if(channel===msg.channel)reply(msg,{version:'1'});return;}
    if(msg.channel!==channel)return;
    if(msg.cancel){const r=requests.get(msg.id);requests.delete(msg.id);r?.abort();return;}
    if(requests.size>=2){reply(msg,{error:'Too many simultaneous requests. No command was retried.'});return;}
    try {
      const r=msg.request,u=new URL(r.url);
      const enrollment=u.pathname.startsWith('/ac/v2/enrollment/details/')&&u.pathname.length>'/ac/v2/enrollment/details/'.length;
      if(u.origin!=='https://api.telematics.hyundaiusa.com'||u.username||u.password||u.search||u.hash||!(allowed.get(u.pathname)?.includes(r.method)||(enrollment&&r.method==='GET')))throw new Error('Request endpoint is outside the Hyundai test allowlist.');
      if(r.body!==null&&(typeof r.body!=='string'||r.body.length>8192))throw new Error('Invalid request body.');
      if(r.method==='GET'&&r.body!==null)throw new Error('GET bodies are not supported.');
      const headers={};for(const [k,v]of Object.entries(r.headers||{})){if(!headerNames.has(k.toLowerCase())||typeof v!=='string'||v.length>4096||/[\r\n]/.test(v))throw new Error('Invalid request header.');headers[k]=v;}
      const finish=rest=>{requests.delete(msg.id);reply(msg,rest);};
      const handle=GM_xmlhttpRequest({method:r.method,url:u.href,headers,data:r.body===null?undefined:r.body,anonymous:true,timeout:45000,redirect:'error',
        onload:res=>{
          if(res.finalUrl&&new URL(res.finalUrl).origin!==u.origin){finish({error:'Unexpected redirect. No response was used.'});return;}
          const parsed={};for(const line of (res.responseHeaders||'').split(/\r?\n/)){const i=line.indexOf(':');if(i>0)parsed[line.slice(0,i).trim().toLowerCase()]=line.slice(i+1).trim();}
          finish({response:{status:res.status,text:(res.responseText||'').slice(0,1048576),headers:parsed}});
        },
        onerror:()=>finish({error:'Browser helper could not reach Hyundai. Check your connection or VPN; the command was not retried.'}),
        ontimeout:()=>finish({error:'Hyundai request timed out. Outcome unknown; no command was retried.'}),
        onabort:()=>finish({error:'Request stopped locally. A submitted command may still run; no retry was sent.'})
      });requests.set(msg.id,handle);
    }catch{reply(msg,{error:'Browser helper rejected an invalid request. No request was sent.'});}
  });
})();
