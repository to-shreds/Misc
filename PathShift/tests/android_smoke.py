#!/usr/bin/env python3
"""Exercise read-only Find, then regex Preview and Save on a disposable Android emulator."""
import pathlib
import atexit
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT=pathlib.Path(__file__).resolve().parents[1]
OUTPUT=ROOT/'device-proof'
OUTPUT.mkdir(exist_ok=True)

@atexit.register
def gather_diagnostics():
    try:
        with (OUTPUT/'android-runtime-all.txt').open('w') as log:
            subprocess.run(['adb','logcat','-d','-s','AndroidRuntime:E','ActivityTaskManager:W','WindowManager:W'],
                           stdout=log,stderr=subprocess.DEVNULL,timeout=20)
        with (OUTPUT/'last-screen.png').open('wb') as screenshot:
            subprocess.run(['adb','exec-out','screencap','-p'],
                           stdout=screenshot,stderr=subprocess.DEVNULL,timeout=20)
    except Exception:
        pass


def adb(*args, check=True):
    x=subprocess.run(['adb',*args],capture_output=True,text=True,check=check,timeout=180 if args[0]=='install' else 60)
    return x.stdout.strip()


def xml():
    adb('shell','uiautomator','dump','/sdcard/window.xml')
    raw=adb('exec-out','cat','/sdcard/window.xml')
    (OUTPUT/'ui-hierarchy.xml').write_text(raw)
    return ET.fromstring(raw)


def nodes(root): return list(root.iter('node'))


def center(n):
    nums=list(map(int,re.findall(r'\d+',n.attrib['bounds'])))
    assert len(nums)==4,nums
    return ((nums[0]+nums[2])//2,(nums[1]+nums[3])//2)


def tap(n):
    x,y=center(n)
    adb('shell','input','tap',str(x),str(y))
    time.sleep(0.4)


def by_text(text,wait=18):
    end=time.time()+wait
    while time.time()<end:
        root=xml()
        visible=[n.attrib.get('text','') for n in nodes(root)]
        hits=[n for n in nodes(root) if n.attrib.get('text','').casefold()==text.casefold()]
        if hits:return hits[0]
        if any(('Launcher' in t or 'System UI' in t) and 'responding' in t for t in visible):
            wait=[n for n in nodes(root) if n.attrib.get('text')=='Wait']
            if wait: tap(wait[0]); continue
        time.sleep(1)
    text_seen=[n.attrib.get('text','') for n in nodes(xml()) if n.attrib.get('text')]
    raise AssertionError(f'Could not find {text!r}. Visible text: {text_seen[:50]}')


def capture(name):
    with (OUTPUT/name).open('wb') as f:
        subprocess.run(['adb','exec-out','screencap','-p'],check=True,stdout=f,timeout=25)


print('Install/launch')
adb('install','-r',str(ROOT/'dist'/'PathShift-0.2.0.apk'))
adb('shell','appops','set','com.pathshift.app','MANAGE_EXTERNAL_STORAGE','allow')
adb('shell','mkdir','-p','/sdcard/Download/PathShiftSmoke')
fixture=OUTPUT/'ps_test_file.txt'
fixture.write_text('test-fixture')
adb('push',str(fixture),'/sdcard/Download/PathShiftSmoke/ps_test_file.txt')
adb('shell','am','start','-W','-n','com.pathshift.app/.MainActivity')
by_text('Rename by regex')
capture('01-editor.png')

print('Select a small test folder')
tap(by_text('Browse'))
# Select Download, then PathShiftSmoke, and use it.
by_text('Download/')
tap(by_text('Download/'))
tap(by_text('PathShiftSmoke/'))
tap(by_text('Use this folder'))

print('Search without changing files')
tap(by_text('Find'))
by_text('Find files & folders')
page=xml()
edits=[n for n in nodes(page) if n.attrib.get('class')=='android.widget.EditText']
assert len(edits)>=1, 'Expected Find expression field'
tap(edits[0]);adb('shell','input','text','ps_test')
adb('shell','input','keyevent','4')
capture('02-find-editor.png')
tap(by_text('Find matches'))
by_text('Search results')
by_text('ps_test_file.txt')
assert not any(n.attrib.get('text','') == 'Save changes' for n in nodes(xml())), 'Find unexpectedly shows Save'
assert 'ps_test_file.txt' in adb('shell','ls','/sdcard/Download/PathShiftSmoke'), 'Find modified files!'
capture('02-find.png')

print('Transfer regex to Rename, then preview')
tap(by_text('Use in Rename'))
by_text('Rename by regex')
page=xml()
edits=[n for n in nodes(page) if n.attrib.get('class')=='android.widget.EditText']
assert len(edits)>=2, f'Expected 2 rename edit fields; found {len(edits)}'
assert edits[0].attrib.get('text') == 'ps_test', 'Find expression was not transferred to Rename'
tap(edits[1]);adb('shell','input','text','renamed')
adb('shell','input','keyevent','4')
tap(by_text('Preview changes'))
by_text('Review changes')
by_text('View all 1 affected paths')
by_text('→  renamed_file.txt')
assert 'ps_test_file.txt' in adb('shell','ls','/sdcard/Download/PathShiftSmoke'), 'Preview modified files!'
capture('03-preview.png')

print('Confirm Save')
tap(by_text('Save changes'))
tap(by_text('Save now'))
by_text('Saved')
files=adb('shell','ls','/sdcard/Download/PathShiftSmoke')
assert 'renamed_file.txt' in files, files
assert 'ps_test_file.txt' not in files, files
capture('04-saved.png')
tap(by_text('Done'))

print('Check AndroidRuntime fatal errors')
log=adb('logcat','-d','-s','AndroidRuntime:E')
(OUTPUT/'android-runtime.txt').write_text(log)
assert 'FATAL EXCEPTION' not in log, log[-5000:]

print('Preset: choose destination outside source root; preview without changing files')
fixture2=OUTPUT/'preset_file.txt'
fixture2.write_text('separate-test-fixture')
adb('push',str(fixture2),'/sdcard/Download/PathShiftSmoke/preset_file.txt')
by_text('Rename by regex')
tap(by_text('Presets'))
by_text('Quick actions')
tap(by_text('Move into a new folder'))
by_text('Move into a new folder')
tap(by_text('Choose folder'))
tap(by_text('..   Go up'))
tap(by_text('Use this folder'))
capture('05-preset-editor.png')
tap(by_text('Preview changes'))
by_text('Review preset changes')
assert 'preset_file.txt' in adb('shell','ls','/sdcard/Download/PathShiftSmoke'), 'Preset preview moved a file!'
capture('06-preset-preview.png')
tap(by_text('Save changes'))
tap(by_text('Save now'))
by_text('Saved')
assert 'preset_file.txt' not in adb('shell','ls','/sdcard/Download/PathShiftSmoke'), 'Preset Save did not move file'
new_path=adb('shell','find','/sdcard/Download','-name','preset_file.txt')
assert 'New folder/preset_file.txt' in new_path, new_path
capture('07-preset-saved.png')
print('PASS: read-only Find, regex Rename, and external-destination folder preset with preview and Save')
