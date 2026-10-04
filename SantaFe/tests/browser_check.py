"""Chromium DOM checks using a model transport double, not browser HTTP or Android tests."""
import json, pathlib, sys, tempfile, threading, time, re
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
from bridge.engine import Engine
from bridge.server import Server
from bridge import model as m
from playwright.sync_api import sync_playwright, expect
out=ROOT/'docs/verification';out.mkdir(parents=True,exist_ok=True)
checks=[];errors=[]
with tempfile.TemporaryDirectory() as temp:
    e=Engine(temp);s=Server(('127.0.0.1',0),e)
    thread=threading.Thread(target=s.serve_forever,daemon=True);thread.start()
    scheduler=threading.Thread(target=e.loop,daemon=True);scheduler.start()
    try:
        with sync_playwright() as p:
            browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
            context=browser.new_context(viewport={'width':412,'height':915},device_scale_factor=1)
            page=context.new_page()
            page.on('pageerror',lambda err:errors.append(str(err)))
            page.on('dialog',lambda d:d.accept())
            # Managed Chromium blocks URL navigation. Exercise our own UI in memory,
            # with an explicit model test double; HTTP/auth is tested separately.
            model_session={"paired":False}
            def model_transport(path, body):
                try:
                    if path!='pair' and not model_session['paired']:raise m.Problem('Pair this dashboard',401)
                    if path=='snapshot':result=e.status()
                    elif path=='catalog':result={'events':m.EVENTS,'fields':m.FIELDS,'actions':m.ACTIONS,'role':'owner'}
                    elif path=='pair':result=e.pair(body.get('code'));model_session['paired']=True
                    elif path=='config':result=e.save_config(body['config'],body['revision'])
                    elif path=='restore':result=e.save_config(body['config'],body['revision'],True)
                    elif path=='simulate':result=e.simulate(body['state'])
                    elif path=='event':result=e.event(body)
                    elif path=='command':result=e.request_command(body)
                    elif path=='confirm':result=e.confirm(body['id'],body.get('outdoors',False))
                    elif path=='confirmation/cancel':result=e.cancel_confirmation(body['id'])
                    elif path=='pairing-code':result={'code':e.code('tasker')}
                    elif path=='backup':result=e.cfg
                    elif path=='pause':result=e.pause()
                    elif path=='rule/test':result=e.evaluate(body['id'])
                    else:raise AssertionError('Unsupported mock route: '+path)
                    return {'status':200,'data':result}
                except m.Problem as ex:return {'status':ex.status,'data':{'error':ex.message}}
            page.expose_function('testModelTransport',model_transport)
            html=(ROOT/'ui/index.html').read_text()
            html=re.sub(r'<link[^>]+>', '', html)
            html=re.sub(r'<script[^>]*>.*?</script>', '', html,flags=re.S)
            page.set_content(html)
            page.add_style_tag(content=(ROOT/'ui/app.css').read_text())
            page.evaluate("""() => { window.fetch=async (path,options={})=>{
                if(!path.startsWith('/api/'))throw new Error('No browser networking in this test');
                const r=await window.testModelTransport(path.slice(5), options.body?JSON.parse(options.body):null);
                return {ok:r.status<400,status:r.status,json:async()=>r.data};
            }; }""")
            page.add_script_tag(content=(ROOT/'ui/app.js').read_text())
            expect(page.locator('#pairForm')).to_be_visible();checks.append('Unpaired dashboard offers owner pairing')
            page.locator('[name=code]').fill(e.code());page.locator('#pairForm button').click()
            expect(page.locator('#mode')).to_have_text('SIMULATION');checks.append('Owner pairing opens simulation')
            expect(page.get_by_text('Your Santa Fe.',exact=False)).to_be_visible()
            page.wait_for_timeout(6700)
            page.screenshot(path=str(out/'dashboard-mobile.png'),full_page=True)
            # All sections load and fit a narrow viewport.
            for section in ['controls','climate','automations','simulator','settings','account','diagnostics','dashboard']:
                page.locator(f'[data-page="{section}"]').click();page.wait_for_timeout(80)
                assert page.locator('#main').inner_text().strip()
                assert page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'), section+' overflows'
            checks.append('Eight sections render without mobile horizontal overflow')
            page.locator('[data-page=climate]').click();page.locator('[data-action=newPreset]').click()
            page.locator('#presetForm [name=name]').fill('After tennis')
            page.locator('#presetForm [name=temperature_f]').fill('68')
            page.locator('#presetForm [name=driver]').select_option('high_cool')
            page.locator('#presetForm button[type="submit"], #presetForm button.primary').click()
            expect(page.get_by_text('After tennis',exact=True)).to_be_visible();assert any(x['name']=='After tennis' for x in e.cfg['presets'])
            checks.append('Custom climate preset saves and returns to list')
            page.locator('[data-page=automations]').click();page.locator('[data-action=newRule]').click()
            page.locator('#ruleForm [name=name]').fill('Fuel test')
            page.locator('#ruleForm [name=event]').select_option('manual')
            page.locator('[data-action=addCondition]').click()
            page.locator('[name=cf0]').select_option('range_miles');page.locator('[name=co0]').select_option('lt')
            page.locator('[name=cv0]').fill('60');page.locator('[name=ax0]').fill('Fuel test: {range_miles} miles')
            page.locator('[name=enabled]').check();page.screenshot(path=str(out/'rule-builder-mobile.png'),full_page=True)
            page.locator('#ruleForm button.primary').click();expect(page.get_by_text('Fuel test',exact=True)).to_be_visible()
            page.locator('[data-action=toggleRules]').click();page.wait_for_timeout(150)
            assert e.cfg['settings']['rules_enabled'];checks.append('Rule editor saves typed conditions and enables master separately')
            page.locator('[data-page=simulator]').click();page.locator('[data-action=scenario][data-id=fuel]').click();page.wait_for_timeout(100)
            page.locator('#eventForm [name=event]').select_option('manual');page.locator('#eventForm button').click()
            page.wait_for_timeout(1400)
            assert any('Fuel test: 38' in n['message'] for n in e.notices);checks.append('Simulator trigger executes matching enabled rule')
            page.locator('[data-page=controls]').click();page.locator('[data-action=command][data-id=unlock]').click()
            expect(page.locator('[data-action=confirm]')).to_be_visible();assert e.sim.snapshot()['locked']
            page.locator('[data-action=confirm]').click();page.wait_for_timeout(350)
            assert e.sim.snapshot()['locked'] is False;checks.append('Unlock waits for explicit confirmation')
            page.locator('[data-page=simulator]').click();page.locator('[data-action=scenario][data-id=unknown]').click();page.wait_for_timeout(100)
            page.locator('[data-page=dashboard]').click();expect(page.get_by_text('STALE / UNKNOWN',exact=False)).to_be_visible()
            checks.append('Missing timestamps displayed as stale/unknown')
            page.locator('[data-page=settings]').click();page.locator('[name=name]').fill('<img src=x onerror=alert(1)>')
            page.locator('#settingsForm button.primary').click();page.wait_for_timeout(150)
            expect(page.locator('#brandName')).to_have_text('<img src=x onerror=alert(1)>')
            assert page.locator('#brandName img').count()==0;checks.append('User text is escaped, not interpreted as HTML')
            page.locator('[name=name]').fill('Santa Fe');page.locator('#settingsForm button.primary').click();page.wait_for_timeout(150)
            page.locator('[data-action=pairCode]').click();expect(page.locator('.paircode')).to_be_visible()
            assert len(page.locator('.paircode').inner_text())==8;checks.append('Tasker pairing-code generation')
            page.locator('[data-action=backup]').click();backup=page.locator('#backupText').input_value()
            assert json.loads(backup)['schema']==1 and 'token' not in backup
            page.locator('[data-action=restore]').click();page.wait_for_timeout(150)
            assert not e.cfg['settings']['rules_enabled'] and not any(r['enabled'] for r in e.cfg['rules'])
            checks.append('Backup restore disables rules')
            # A completed mutation must be visible even when another read is in flight.
            page.evaluate("""async()=>{
                const first=refresh(false);
                const cfg=structuredClone(snap.config);cfg.settings.name='Concurrent save';
                await api('config',{config:cfg,revision:snap.revision});
                await refresh(false);await first;
            }""")
            expect(page.locator('#brandName')).to_have_text('Concurrent save')
            checks.append('Post-mutation refresh waits for in-flight reads')
            page.locator('[data-page=settings]').click()
            page.locator('[name=name]').fill('My unsaved edit')
            external=json.loads(json.dumps(e.cfg));external['settings']['name']='External change'
            e.save_config(external,e.revision)
            page.evaluate('refresh(false)')
            page.locator('#settingsForm button.primary').click()
            expect(page.locator('#toast')).to_contain_text('changed elsewhere')
            assert e.cfg['settings']['name']=='External change'
            assert page.locator('[name=name]').input_value()=='My unsaved edit'
            checks.append('Stale settings form cannot overwrite another editor')
            clean=json.loads(json.dumps(e.cfg));clean['settings']['name']='Santa Fe'
            e.save_config(clean,e.revision)
            e.sim.patch({'locked':True,'hood_open':False,'fuel_percent':72,'range_miles':361})
            page.evaluate('refresh(false)')
            page.wait_for_timeout(6700)

            page.set_viewport_size({'width':1440,'height':1000});page.locator('[data-page=dashboard]').click()
            page.screenshot(path=str(out/'dashboard-desktop.png'),full_page=True)
            checks.append('Desktop layout renders')
            assert not errors, errors
            checks.append('No uncaught JavaScript errors with model transport')
            browser.close()
    finally:
        s.shutdown();s.server_close();e.close();thread.join(2);scheduler.join(2)
(out/'browser-results.json').write_text(json.dumps({'checks_passed':len(checks),'checks':checks,'page_errors':errors,'engine':'Chromium via Playwright, in-memory DOM and model-transport test double. Managed URL navigation blocked; no browser network was used. HTTP/auth tested separately.','not_tested':'Android Tasker WebView, import behavior, on-device notifications, Hyundai account'},indent=2))
print(json.dumps({'checks_passed':len(checks),'page_errors':errors},indent=2))
