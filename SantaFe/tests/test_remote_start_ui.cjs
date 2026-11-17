'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const html=fs.readFileSync(path.join(__dirname,'../dashboard.html'),'utf8');
const js=fs.readFileSync(path.join(__dirname,'../dashboard.js'),'utf8');

test('every remote-start preset remains available without an outdoors checkbox',()=>{
  assert.match(html,/id="startDialog"/);
  for(const preset of ['cool','normal','warm'])assert.match(html,new RegExp('data-preset="'+preset+'"'));
  assert.doesNotMatch(html,/id="outdoors"|parked outdoors|enclosed spaces|type="checkbox"/i);
  assert.doesNotMatch(js,/outdoors|certify.*outside/i);
});
test('preset selection still opens the existing confirmation and not a direct car command',()=>{
  assert.match(js,/querySelectorAll\('\[data-preset\]'\)/);
  assert.match(js,/showCommand\(b\.dataset\.preset\)/);
  assert.match(js,/\$\('commandDialog'\)\.showModal\(\)/);
  assert.match(js,/\$\('sendCommand'\)\.addEventListener/);
  assert.match(js,/native\.reserveCommand\(action\)/);
  assert.match(js,/legacyPending\(\)/);
});
