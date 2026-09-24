#!/usr/bin/env python3
"""Deterministic Chromium tests; external players are deliberately not contacted.
Use --online-pictures only for the optional visual proof capture, not assertions.
"""
from __future__ import annotations
import argparse, json, pathlib, shutil
from playwright.sync_api import sync_playwright, expect
from build import ROOT, load

parser=argparse.ArgumentParser();parser.add_argument('--online-pictures',action='store_true');args=parser.parse_args()
html=(ROOT/'knots.html').read_text(encoding='utf-8');knots=load();out=ROOT/'knots'/'proof';out.mkdir(exist_ok=True)
checks=[]
def check(condition, message):
    assert condition,message
    checks.append(message)

with sync_playwright() as p:
    executable=shutil.which('chromium') or shutil.which('google-chrome') or shutil.which('chromium-browser')
    browser=p.chromium.launch(executable_path=executable,args=['--no-sandbox'])
    context=browser.new_context(viewport={'width':1440,'height':1050},reduced_motion='reduce')
    def route(r):
        u=r.request.url
        if u.startswith('http://knotbook.test/'):
            if u.endswith('favicon.ico'):r.fulfill(status=204)
            else:r.fulfill(status=200,content_type='text/html; charset=utf-8',body=html)
        elif args.online_pictures and u.startswith('https://i.ytimg.com/'):
            r.continue_()
        else:r.abort()
    context.route('**/*',route)
    page=context.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
    page.goto('http://knotbook.test/knots.html',wait_until='domcontentloaded')
    expect(page.locator('#grid .card')).to_have_count(100)
    check(page.evaluate('Knotbook.count')==100,'100 unique published tutorials')
    check(page.locator('iframe').count()==0,'No third-party video loads before play')
    page.locator('#search').fill('reef')
    expect(page.locator('#grid .card')).to_have_count(1)
    check(page.locator('#grid .card').get_attribute('data-id')=='square','Alias search finds Square Knot')
    page.locator('#clear').click()
    page.locator('[data-category="Fishing"]').click()
    count=page.locator('#grid .card').count()
    check(count==sum('Fishing' in k['activities'] for k in knots),'Activity filter returns the expected fishing tutorials')
    page.locator('#difficulty').select_option('1')
    check(page.locator('#grid .card').count()==sum('Fishing' in k['activities'] and k['level']==1 for k in knots),'Difficulty and activity filters combine')
    page.locator('#clear').click()
    page.locator('#search').fill('zzzz-no-such-knot')
    expect(page.locator('#empty')).to_be_visible();check(True,'No-results state is visible')
    page.locator('#empty-reset').click()
    page.locator('#beginner-btn').click()
    expect(page.locator('#grid .card')).to_have_count(8)
    check(True,'Beginner course contains exactly eight tutorials')
    page.locator('#end-path').click()
    page.locator('[data-save="bowline"]').click()
    page.locator('[data-view="saved"]').first.click()
    expect(page.locator('#grid .card')).to_have_count(1)
    check(True,'Saving creates a My knots collection')
    page.locator('#grid .card-main').click()
    expect(page.locator('#knot-title')).to_have_text('Bowline')
    check(page.locator('#steps .step:visible').count()==1,'Reader initially shows one written step')
    page.locator('#next-step').click();expect(page.locator('#step-position')).to_have_text('STEP 2 OF 5')
    page.keyboard.press('ArrowRight');expect(page.locator('#step-position')).to_have_text('STEP 3 OF 5')
    check(True,'Next and keyboard controls advance steps')
    page.locator('#prev-step').click();expect(page.locator('#step-position')).to_have_text('STEP 2 OF 5')
    page.locator('#show-all').click();check(page.locator('#steps .step:visible').count()==5,'All-steps mode reveals the full sequence')
    page.locator('#learned').click();expect(page.locator('#learned')).to_have_attribute('aria-pressed','true')
    page.reload(wait_until='domcontentloaded')
    expect(page.locator('#learned')).to_have_attribute('aria-pressed','true')
    expect(page.locator('#detail-save')).to_have_attribute('aria-pressed','true')
    check(True,'Saved knots and practice records survive reload')
    page.locator('#play-video').click()
    expect(page.locator('#media iframe')).to_have_attribute('src','https://www.youtube-nocookie.com/embed/YXRnPES0Qec?autoplay=1&playsinline=1&rel=0')
    check(True,'Play opens the correct privacy-enhanced knot embed')
    page.locator('#back').click();expect(page.locator('iframe')).to_have_count(0)
    check(True,'Leaving a tutorial stops and removes its video')
    page.locator('#settings-btn').click();expect(page.locator('#settings')).to_be_visible()
    page.locator('#large-type').check();check('large-type' in page.locator('body').get_attribute('class'),'Large-type preference applies')
    page.locator('#load-images').uncheck()
    page.locator('#all-default').check()
    with page.expect_download() as download:
        page.locator('#export-progress').click()
    payload=json.loads(pathlib.Path(download.value.path()).read_text())
    check(payload['app']=='knotbook' and 'bowline' in payload['learned'],'Progress export contains validated practice data')
    page.locator('#import-progress').set_input_files({'name':'progress.json','mimeType':'application/json','buffer':json.dumps({'app':'knotbook','version':1,'saved':['square','not-a-knot'],'learned':['square']}).encode()})
    expect(page.locator('#saved-count')).to_have_text('2')
    check('not-a-knot' not in page.evaluate('JSON.parse(localStorage.getItem("misc-knotbook-v1")).saved'),'Progress import merges known IDs and rejects unknown IDs')
    page.keyboard.press('Escape');expect(page.locator('#settings')).not_to_be_visible()
    # Open every entry to check that its heading, steps, source and media map agree.
    for k in knots:
        page.evaluate('(id)=>{location.hash="knot/"+id}',k['id'])
        expect(page.locator('#knot-title')).to_have_text(k['name'])
        check(page.locator('#steps .step').count()==len(k['steps']),f'{k["id"]}: complete reader')
        check(page.locator('#source-link').get_attribute('href')==k['source'],f'{k["id"]}: canonical reference')
        check(page.locator('#youtube-link').get_attribute('href').endswith(k['video']),f'{k["id"]}: matching demonstration ID')
    # Reset preferences for layout proof, without discarding the functional tests.
    page.evaluate('localStorage.removeItem("misc-knotbook-v1")')
    for width,height,label in [(1440,1050,'desktop'),(768,1024,'tablet'),(390,844,'phone'),(320,740,'small-phone')]:
        page.set_viewport_size({'width':width,'height':height})
        page.goto('http://knotbook.test/knots.html',wait_until='domcontentloaded')
        check(page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'),f'{label}: library has no horizontal overflow')
        if args.online_pictures:page.wait_for_timeout(2200)
        page.screenshot(path=str(out/f'{label}-library.png'),full_page=False)
        page.evaluate('location.hash="knot/bowline"');expect(page.locator('#knot-title')).to_have_text('Bowline')
        check(page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'),f'{label}: tutorial has no horizontal overflow')
        if args.online_pictures:page.wait_for_timeout(900)
        page.screenshot(path=str(out/f'{label}-tutorial.png'),full_page=True)
    page.emulate_media(media='print');check(page.locator('#steps .step:visible').count()==5,'Printed tutorial includes all five steps')
    page.emulate_media(media='screen')
    check(not errors,'No JavaScript exceptions: '+repr(errors))
    # A storage-blocked browser must still allow learning.
    restricted=browser.new_context(viewport={'width':390,'height':844});restricted.route('**/*',route)
    restricted.add_init_script('Object.defineProperty(window,"localStorage",{get(){throw new Error("storage blocked")}})')
    rp=restricted.new_page();rp.goto('http://knotbook.test/knots.html',wait_until='domcontentloaded')
    expect(rp.locator('#grid .card')).to_have_count(100)
    check(True,'Guide remains usable with browser storage blocked')
    restricted.close();context.close();browser.close()
(out/'browser-results.json').write_text(json.dumps({'passed':len(checks),'checks':checks,'external_video_playback':'Not asserted. Native YouTube playback can be blocked by the viewer network or provider.'},indent=2))
print(f'PASS: {len(checks)} Chromium assertions, all 100 tutorials, desktop/tablet/phone layouts, storage and print.')
