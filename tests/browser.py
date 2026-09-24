"""Browser regressions on real HTTP pages and the standalone build.
Run: pip install playwright==1.56.0; playwright install chromium;
     python tests/browser.py
BROWSER_EXECUTABLE may point to a separately installed Chromium.
"""
from pathlib import Path
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from functools import partial
import os, threading, json, re
from playwright.sync_api import sync_playwright, expect
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'test-results';OUT.mkdir(exist_ok=True)
class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self,*args): pass
server=ThreadingHTTPServer(('127.0.0.1',0),partial(QuietHandler,directory=str(ROOT)))
threading.Thread(target=server.serve_forever,daemon=True).start()
BASE=f'http://127.0.0.1:{server.server_port}'
checks=[]; errors=[]
def ok(label):checks.append(label);print('PASS',label,flush=True)
def ready(page):page.wait_for_function('window.zork && !document.getElementById("command").disabled')
def command(page,text):page.locator('#command').fill(text);page.locator('#command').press('Enter')
def state(page):return page.evaluate('({room:zork.state.room,moves:zork.state.moves,score:zork.state.score})')
def memory(page):return page.evaluate('Array.from(zork.engine.vm.ram.getUint8Array(0,zork.engine.vm.staticmem))')
def shot(page,name):page.wait_for_timeout(250);page.screenshot(path=str(OUT/name),full_page=True)
def watch(page):page.on('pageerror',lambda error:errors.append(str(error)))
try:
 with sync_playwright() as p:
  launch={'headless':True}
  if os.environ.get('BROWSER_EXECUTABLE'):launch['executable_path']=os.environ['BROWSER_EXECUTABLE']
  browser=p.chromium.launch(**launch)
  context=browser.new_context(viewport={'width':1440,'height':1000},accept_downloads=True)
  page=context.new_page();watch(page);requests=[]
  page.on('request',lambda request:requests.append(request.url))
  page.goto(BASE+'/zork/');ready(page)
  expect(page.locator('#location')).to_have_text('West of House')
  assert state(page)['moves']==0
  assert page.get_by_role('button',name='Select leaflet',exact=True).count()==0
  shot(page,'desktop-opening.png');ok('original opening, hidden leaflet, desktop rendering')
  before=memory(page)
  page.get_by_role('button',name='Select small mailbox',exact=True).click()
  assert memory(page)==before
  page.locator('#verbs').get_by_role('button',name='More actions…').click()
  page.locator('#close-dialog').click()
  page.locator('#notes-button').click();page.get_by_label('Your adventure notes').fill('Remember the boarded door.')
  page.locator('#close-dialog').click();assert memory(page)==before
  ok('selection, action builder, notes and dialogs cost zero hidden turns')
  page.locator('#verbs').get_by_role('button',name='Open',exact=True).click()
  expect(page.get_by_role('button',name='Select leaflet',exact=True)).to_be_visible()
  assert state(page)['moves']==1
  ok('clickable object executes exactly one original command')
  saved_state=state(page)
  page.locator('#save-button').click();page.get_by_role('button',name=re.compile(r'^Slot 1 · Empty')).click()
  command(page,'north');expect(page.locator('#location')).to_have_text('North of House')
  page.locator('#restore-button').click();page.get_by_role('button',name=re.compile(r'^Slot 1 · West of House')).click()
  assert state(page)==saved_state
  page.reload();ready(page);assert state(page)==saved_state
  page.locator('#notes-button').click();expect(page.get_by_label('Your adventure notes')).to_have_value('Remember the boarded door.');page.locator('#close-dialog').click()
  ok('native save/restore, browser reload resume and persisted notes')
  page.locator('#restore-button').click()
  with page.expect_download() as event:page.get_by_role('button',name='Export slot 1',exact=True).click()
  save_path=OUT/'exported.sav';event.value.save_as(str(save_path))
  assert save_path.read_bytes()[:4]==b'FORM'
  page.locator('#close-dialog').click();command(page,'north')
  page.locator('#restore-button').click();page.locator('#dialog input[type=file]').set_input_files(str(save_path))
  expect(page.locator('#dialog')).not_to_be_visible()
  assert state(page)==saved_state;ok('portable Quetzal export and import')
  page.locator('#restore-button').click()
  page.locator('#dialog input[type=file]').set_input_files({'name':'broken.sav','mimeType':'application/octet-stream','buffer':b'not a save'})
  expect(page.locator('#toast')).to_have_text('Choose a valid Zork I Quetzal save (.sav).')
  expect(page.locator('#dialog')).to_be_visible();page.locator('#close-dialog').click()
  assert state(page)==saved_state;ok('invalid import cannot replace current game')
  page.locator('#settings-button').click();page.get_by_label(re.compile(r'^Theme')).select_option('paper');page.locator('#close-dialog').click()
  assert state(page)==saved_state;shot(page,'desktop-paper.png');ok('paper theme leaves game state unchanged')
  for cmd in ['north','east','open window','enter window','west']:
   command(page,cmd)
  assert page.get_by_role('button',name='Select trap door',exact=True).count()==0
  for cmd in ['take lamp','move rug','open trap door','down']:command(page,cmd)
  assert page.evaluate('zork.state.dark && zork.state.here.length===0')
  command(page,'turn on lamp');assert not page.evaluate('zork.state.dark')
  shot(page,'cellar.png');ok('hidden trapdoor, dark room and genuine lamp visibility')
  assert all(url.startswith(BASE) for url in requests),requests
  ok('no external network dependency during gameplay')
  for width,height,label in [(393,852,'phone'),(360,740,'small-phone'),(844,390,'landscape')]:
   mobile=browser.new_context(viewport={'width':width,'height':height},is_mobile=True,has_touch=True)
   phone=mobile.new_page();watch(phone);phone.goto(BASE+'/zork/');ready(phone)
   if width<=680:
    quick=phone.locator('.quick-move')
    expect(quick).to_be_visible()
    for direction in ['west','north','south','east']:
     expect(quick.locator(f'[data-command="{direction}"]')).to_be_in_viewport()
    command(phone,'look');expect(quick).to_be_visible()
    phone.locator('#items-panel').click();shot(phone,label+'-items.png')
   phone.get_by_role('button',name='Select small mailbox',exact=True).click()
   expect(phone.locator('#verbs').get_by_role('button',name='More actions…')).to_be_in_viewport()
   phone.locator('#verbs').get_by_role('button',name='Open',exact=True).click()
   assert state(phone)['moves']==1
   dimensions=phone.evaluate('({w:innerWidth,h:innerHeight,sw:document.documentElement.scrollWidth,sh:document.documentElement.scrollHeight})')
   assert dimensions['sw']<=dimensions['w'],dimensions
   assert dimensions['sh']<=dimensions['h']+1,dimensions
   shot(phone,label+'-action.png');mobile.close();ok(label+' touch controls and viewport containment')
  standalone=browser.new_context();sp=standalone.new_page();watch(sp)
  requested=[];sp.on('request',lambda req:requested.append(req.url))
  sp.goto(BASE+'/zork.html');ready(sp);command(sp,'open mailbox')
  assert state(sp)['moves']==1
  assert not any('/zork/' in url for url in requested),requested
  ok('single-file build runs without separate game, script or stylesheet requests')
  offline=browser.new_context(offline=True);op=offline.new_page();watch(op)
  op.goto((ROOT/'zork.html').as_uri());ready(op);command(op,'open mailbox')
  assert state(op)['moves']==1
  op.reload();ready(op);assert state(op)['moves']==1
  ok('standalone opens directly from disk with networking disabled and resumes after reload')
  assert not errors,errors
  ok('no uncaught browser errors')
  browser.close()
finally:
 server.shutdown()
 (OUT/'browser-results.json').write_text(json.dumps({'passed':checks,'browser_errors':errors},indent=2))
print(f'{len(checks)} browser checks passed.')
