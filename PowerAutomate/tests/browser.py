"""Chromium interaction tests. In this environment navigation is policy-blocked,
so the tested bundled HTML is loaded using set_content, not a fake hosted URL."""
import json, re, zipfile
from pathlib import Path
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1]
HTML=(ROOT/'standalone.html').read_text()
results=[]
def check(name, value):
    assert value, name
    results.append(name)
def node_name(page, kind):
    return page.evaluate("k=>FlowCore.walk(Flowcraft.snapshot().definition).find(r=>FlowCore.kind(r.a)===k)?.name",kind)
def fill_recipient(page):
    page.get_by_label(re.compile(r'^To')).fill('reader@example.com')
    page.get_by_label('Subject',exact=False).press('Tab')
def choose_template(page, pattern):
    page.locator('#recipesTab').click()
    match=page.get_by_role('button',name=re.compile(pattern))
    if match.count()==0:
        page.get_by_role('button',name=re.compile(r'More templates')).click()
        match=page.get_by_role('button',name=re.compile(pattern))
    match.click()
with sync_playwright() as pw:
    browser=pw.chromium.launch(executable_path='/usr/bin/chromium', headless=True, args=['--no-sandbox'])
    context=browser.new_context(accept_downloads=True,viewport={'width':1440,'height':1000})
    page=context.new_page();errors=[];requests=[]
    page.on('pageerror',lambda e:errors.append(str(e)))
    page.on('request',lambda r:requests.append(r.url))
    page.on('dialog',lambda d:d.accept())
    page.set_content(HTML,wait_until='load')
    check('initial recipe and visible inspector',page.locator('.node').count()==3 and page.locator('#inspector').is_visible())
    page.locator('#exportBtn').click()
    check('incomplete recipient blocks ZIP download',page.locator('#downloadZip').is_disabled())
    page.locator('#closeModal').click();fill_recipient(page)
    check('recipient removes blocking error',not [e for e in page.evaluate('Flowcraft.checks()') if e['severity']=='error'])
    check('stale error is removed from inspector','To is empty' not in page.locator('#inspector').inner_text())
    page.get_by_text('Preview the email',exact=True).click()
    check('static email preview is sandboxed',page.locator('#emailPreview').get_attribute('sandbox')=='')
    check('static Compose HTML preview is shown','Hello, automation' in page.locator('#emailPreview').get_attribute('srcdoc'))
    page.locator('#closeModal').click();page.locator('#exportBtn').click()
    with page.expect_download() as di:page.locator('#downloadZip').click()
    export=ROOT/'tests/browser-export.zip';di.value.save_as(export)
    with zipfile.ZipFile(export) as z:
        check('browser ZIP passes independent Python CRC check',z.testzip() is None)
        check('browser ZIP contains five package files',len(z.namelist())==5)
        definition=json.loads(z.read(next(n for n in z.namelist() if n.endswith('definition.json'))))
        check('actual recipient is wired into exported flow',definition['properties']['definition']['actions']['Email_me_the_result']['inputs']['parameters']['emailMessage/To']=='reader@example.com')
    page.locator('#closeModal').click()
    with page.expect_download() as di:page.locator('#saveBtn').click()
    projectfile=ROOT/'tests/browser-project.flowcraft.json';di.value.save_as(projectfile)
    original=page.evaluate('Flowcraft.snapshot()')
    page.locator('#openFile').set_input_files(str(export));page.wait_for_function('() => Flowcraft.snapshot().imported===true')
    check('browser imports its ZIP and preserves workflow',page.evaluate('Flowcraft.snapshot().definition')==original['definition'])
    # A native DEFLATE package made independently, not by the app's STORE exporter.
    deflated=ROOT/'tests/deflated.zip'
    with zipfile.ZipFile(export) as z,zipfile.ZipFile(deflated,'w',zipfile.ZIP_DEFLATED) as out:
        for name in z.namelist():out.writestr(name,z.read(name))
    page.locator('#openFile').set_input_files(str(deflated));page.wait_for_timeout(180)
    check('DEFLATE import preserves workflow',page.evaluate('Flowcraft.snapshot().definition')==original['definition'])
    page.locator('#openFile').set_input_files(str(projectfile));page.wait_for_function('() => Flowcraft.snapshot().imported!==true')
    check('saved project reopens exactly',page.evaluate('Flowcraft.snapshot()')==original)
    # Insert a value into a JSON-mode loop field: must become a real expression.
    page.locator('#addRoot').click()
    page.get_by_role('button',name='Show more step types',exact=True).click();page.get_by_role('button',name=re.compile(r'Go through a list and save the answers')).click()
    loop=node_name(page,'loop')
    page.locator('.field').filter(has=page.locator('label',has_text='Which list should I go through?')).get_by_role('button',name='Use an earlier answer').click()
    page.locator('#refList').get_by_role('button',name='Value',exact=True).first.click()
    check('reference picker creates valid expression from JSON mode',page.evaluate("n=>FlowCore.locate(Flowcraft.snapshot(),n).a.foreach",loop).startswith('@outputs('))
    check('pattern adds variable and sequential nested actions',page.evaluate('FlowCore.walk(Flowcraft.snapshot().definition).length')==6)
    page.locator('#undoBtn').click();check('undo restores empty loop input',page.evaluate("n=>FlowCore.locate(Flowcraft.snapshot(),n).a.foreach",loop)==[])
    page.locator('#redoBtn').click();check('redo restores selected reference',isinstance(page.evaluate("n=>FlowCore.locate(Flowcraft.snapshot(),n).a.foreach",loop),str))
    # Routing recipe, condition helper, and ordinary branch creation.
    choose_template(page,'Only act when something matches')
    cond=node_name(page,'condition');page.locator(f'[data-node="{cond}"]').click()
    page.get_by_role('button',name='Set the rule',exact=True).click()
    page.get_by_label('Left value source',exact=True).select_option("@triggerBody()?['subject']")
    page.locator('#conditionOperator').select_option('contains')
    page.get_by_label('Right value',exact=True).fill('urgent')
    page.locator('#applyCondition').click()
    check('condition helper emits typed native condition',page.evaluate("n=>FlowCore.locate(Flowcraft.snapshot(),n).a.expression",cond)=={'contains':["@triggerBody()?['subject']",'urgent']})
    # Schedule: friendly time picker, without needing JSON.
    choose_template(page,'Email me a morning inbox summary');page.locator('.node').first.click()
    page.locator('#scheduleAt').fill('09:35');page.locator('#scheduleAt').press('Tab')
    check('friendly schedule controls set hour and minute',page.evaluate('Object.values(Flowcraft.snapshot().definition.triggers)[0].recurrence.schedule')=={'hours':[9],'minutes':[35]})
    # Invalid JSON remains unapplied and blocks export.
    choose_template(page,'Turn a list into an email report')
    comp=node_name(page,'compose');page.locator(f'[data-node="{comp}"]').click()
    val=page.locator('#inspector textarea').first;val.fill('[not json');val.press('Tab')
    page.locator('#exportBtn').click();check('invalid draft JSON blocks export',page.locator('#downloadZip').is_disabled());page.locator('#closeModal').click()
    # Discard on navigation, then confirm the original data was not overwritten.
    page.locator('.node').first.click();check('invalid edit does not overwrite valid source',len(page.evaluate("n=>FlowCore.locate(Flowcraft.snapshot(),n).a.inputs",comp))==3)
    # Solution rejection preserves the current project.
    before=page.evaluate('Flowcraft.snapshot()');sol=ROOT/'tests/unsupported-solution.zip'
    with zipfile.ZipFile(sol,'w') as z:z.writestr('solution.xml','<Solution/>')
    page.locator('#openFile').set_input_files(str(sol));page.wait_for_timeout(150)
    check('Solution ZIP rejection is explicit and non-destructive','Solution ZIP' in page.locator('#toast').inner_text() and page.evaluate('Flowcraft.snapshot()')==before)
    # XSS and raw preservation; never insert imported user content as executable HTML.
    hostile=json.loads(json.dumps(before));hostile['name']='<img src=x onerror="window.pwned=true">';hostile['labels'][comp]='<script>window.pwned=true</script>'
    page.locator('#openFile').set_input_files({'name':'hostile.json','mimeType':'application/json','buffer':json.dumps(hostile).encode()});page.wait_for_timeout(150)
    check('imported labels cannot execute script',page.evaluate('window.pwned!==true'))
    check('no runtime requests from user data or controls',requests==[])
    check('no uncaught browser errors',errors==[])
    # Final desktop and mobile visual checks use a representative nested flow.
    choose_template(page,'Ask AI about every item in a list')
    page.locator('#flowTree').screenshot(path=str(ROOT/'tests/workflow.png'))
    page.screenshot(path=str(ROOT/'tests/desktop.png'),full_page=True)
    mobile=context.new_page()
    mobile.set_viewport_size({'width':390,'height':844});mobile.set_content(HTML,wait_until='load')
    check('mobile page has no horizontal overflow',mobile.evaluate('document.documentElement.scrollWidth<=innerWidth'))
    check('mobile header retains ZIP export',mobile.locator('#exportBtn').is_visible())
    mobile.locator('[data-node="Email_me_the_result"]').click()
    check('mobile step opens full inspector',mobile.locator('#inspector').is_visible())
    mobile.locator('#backToFlow').click();check('mobile can return to the canvas',not mobile.locator('#inspector').is_visible())
    mobile.locator('#menuBtn').click();mobile.locator('#menuHelp').click();check('help is reachable on mobile','From idea to a real flow' in mobile.locator('#modalTitle').inner_text())
    mobile.locator('#closeModal').click();mobile.screenshot(path=str(ROOT/'tests/mobile.png'),full_page=True)
    browser.close()
(ROOT/'tests/browser-results.json').write_text(json.dumps({'passed':len(results),'checks':results,'environment':'Chromium, in-memory bundled HTML; OS policy prevents URL navigation. This is not a tenant import or hosted-site browser test.'},indent=2))
print(json.dumps({'passed':len(results),'checks':results},indent=2))
