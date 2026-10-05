#!/usr/bin/env python3
"""Observe the real Tasker 1.1.0 -> 1.2.0 import flow with synthetic settings."""
import argparse
import hashlib
import json
import pathlib
import re
import time
import tasker_smoke as m

def configuration(label):
    m.adb('shell','input','keyevent','4')
    time.sleep(2)
    listing=m.adb('shell','find','/data/data/'+m.PACKAGE+'/files','-maxdepth','3','-type','f').decode()
    for p in listing.splitlines():
        if not p.endswith('.xml'): continue
        b=m.adb('exec-out','cat',p,check=False)
        if b'Santa Fe Direct' not in b or b'<Task' not in b: continue
        try: r=m.native_xml(b)
        except Exception: continue
        (m.RESULTS/(label+'.data')).write_bytes(b)
        tasks=[t for t in r.iter('Task') if t.findtext('nme','').startswith('SFD ')]
        result={'tasks':len(tasks),'actions':sum(len(t.findall('Action')) for t in tasks),'scenes':len([s for s in r.iter('Scene') if s.findtext('nme','').startswith('SFD ')])}
        m.REPORT[label]=result
        return result
    raise RuntimeError('No saved native Tasker configuration')

def import_update():
    name='Santa_Fe_Direct_1_2_0_PRESETS_WATCH_GPS.prj.xml'
    for directory in ['/sdcard/Tasker/projects/','/sdcard/Download/']:
        m.adb('push',str(m.ROOT/'tasker/Santa_Fe_Direct.prj.xml'),directory+name)
    root=m.screen(); node=m.matching(root,'Default Project')
    if node is None: raise RuntimeError('No observed project tab: '+str(m.visible(root)))
    left,top,right,bottom=[int(x) for x in re.findall(r'\d+',node.get('bounds',''))]
    x,y=str((left+right)//2),str((top+bottom)//2)
    m.adb('shell','input','swipe',x,y,x,y,'1000');time.sleep(.8)
    m.check(m.click(m.screen(),['Import Project','Import']),'Existing project can open Import Project')
    selected=False
    for _ in range(24):
        root=m.screen(); texts=m.visible(root)
        m.REPORT.setdefault('import_windows',[]).append(texts)
        if selected and m.matching(root,'Tasks') is not None and m.matching(root,'Import Project') is None:
            if m.matching(root,'Apply') is not None: m.click(root,['Apply']);root=m.screen()
            m.click(root,['Tasks']);time.sleep(.8)
            return
        if any('already exists' in t.lower() or 'project exists' in t.lower() for t in texts):
            raise RuntimeError('Tasker refused the existing project update: '+str(texts))
        if m.click(root,[name,name[:-8],name[:-4]]): selected=True;continue
        if m.click(root,['projects','Download','Downloads']):continue
        if m.click(root,['Replace All','Replace','Overwrite','Yes','Import','OK','Continue','Done','Allow']):continue
        raise RuntimeError('Unhandled update UI: '+str(texts))
    raise RuntimeError('Update import did not return to Tasks')

def remove_old_project():
    root=m.screen();m.click(root,['OK']);root=m.screen()
    node=m.matching(root,'Santa Fe Direct')
    if node is None: raise RuntimeError('Old project tab is not visible for scoped removal: '+str(m.visible(root)))
    left,top,right,bottom=[int(x) for x in re.findall(r'\d+',node.get('bounds',''))]
    x,y=str((left+right)//2),str((top+bottom)//2)
    m.adb('shell','input','swipe',x,y,x,y,'1000');time.sleep(.8)
    root=m.screen();m.REPORT.setdefault('removal_windows',[]).append(m.visible(root))
    m.check(m.click(root,['Delete']),'Only the existing Santa Fe Direct project is selected for removal')
    for _ in range(12):
        root=m.screen();texts=m.visible(root);m.REPORT['removal_windows'].append(texts)
        if m.matching(root,'Santa Fe Direct') is None and m.matching(root,'Tasks') is not None and m.matching(root,'Yes') is None:
            m.REPORT['update_requires_removal']=True
            return
        contents=next((n for n in m.nodes(root) if 'contents' in n.get('text','').lower() and not any(w in n.get('text','').lower() for w in ['without','keep'])),None)
        if contents is not None:
            m.tap(contents);continue
        if m.click(root,['Yes','OK','Delete','Confirm']):continue
        raise RuntimeError('Unhandled scoped project removal: '+str(texts))
    raise RuntimeError('Old project removal did not complete')

def run(old):
    new=m.ROOT/'tasker/Santa_Fe_Direct.prj.xml'; original=new.read_bytes()
    m.REPORT['project_sha256']=hashlib.sha256(original).hexdigest()
    m.adb('root',check=False);m.adb('wait-for-device',timeout=60)
    for op in ['SYSTEM_ALERT_WINDOW','WRITE_SETTINGS','MANAGE_EXTERNAL_STORAGE']:
        m.adb('shell','appops','set',m.PACKAGE,op,'allow',check=False)
    m.adb('shell','pm','grant',m.PACKAGE,'android.permission.POST_NOTIFICATIONS',check=False)
    m.startup()
    try:
        new.write_bytes(old.read_bytes());m.import_project()
    finally: new.write_bytes(original)
    before=configuration('before_update')
    m.check(before=={'tasks':41,'actions':89,'scenes':7},'Baseline actually has 41 old tasks and seven scenes')
    m.startup();m.play(m.open_task('SFD Setup'));time.sleep(1)
    for i,value in enumerate(['fixture@example.invalid','fixture-test-only','1357']):
        root=m.screen();inputs=[n for n in m.nodes(root) if n.get('class')=='android.widget.EditText']
        m.tap(inputs[i]);m.adb('shell','input','text',value)
        keyboard=m.adb('shell','dumpsys','input_method').decode()
        if 'mInputShown=true' in keyboard or 'isInputViewShown=true' in keyboard:
            m.adb('shell','input','keyevent','4');time.sleep(.5)
    m.check(m.click(m.screen(),['Save']),'Old project saves synthetic account before update')
    time.sleep(2);m.back_to_tasks();configuration('saved_old_account');m.startup()
    try:
        import_update()
    except RuntimeError as error:
        if 'a project with that name already exists' not in str(error): raise
        m.REPORT['existing_project_import_error']=str(error)
        remove_old_project()
        import_update()
    after=configuration('after_update')
    m.check(after=={'tasks':61,'actions':128,'scenes':8},'Updating existing project imports all 61 tasks, 128 actions and eight scenes')
    m.startup();m.play(m.open_task('SFD Verify Actions'))
    root=m.wait_for('Santa Fe Direct verification')
    m.check(any('1.2.0' in n.get('text','') for n in m.nodes(root)),'Updated verification task actually reports 1.2.0')
    m.check(any('Status parser verified: Locked / Off / Off' in n.get('text','') for n in m.nodes(root)),'Updated native status reader executes after replacement')
    m.click(root,['OK']);m.back_to_tasks();m.play(m.open_task('SFD Setup'))
    root=m.wait_for('Santa Fe account');inputs=[n for n in m.nodes(root) if n.get('class')=='android.widget.EditText']
    m.check(len(inputs)==4 and inputs[0].get('text')=='fixture@example.invalid','Existing account survives project replacement')
    m.check(sum(n.get('password')=='true' for n in inputs)==2,'Updated account form masks both secret fields')
    m.check(m.click(root,['Save']),'Blank secret fields retain existing synthetic account after replacement')
    time.sleep(2);m.check(m.matching(m.screen(),'Santa Fe account') is None,'Retained synthetic password and PIN validate after replacement')
    m.back_to_tasks();m.play(m.open_task('SFD Open'));root=m.wait_for('Santa Fe / Home')
    m.check(m.matching(root,'Santa Fe / Home') is not None,'Updated SFD Open runs the native Home scene')
    (m.RESULTS/'updated-home.png').write_bytes(m.adb('exec-out','screencap','-p'))
    m.click(root,['Close']);m.REPORT['passed']=True

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--old',type=pathlib.Path,required=True);p.add_argument('--results',type=pathlib.Path,required=True);args=p.parse_args()
    m.RESULTS=args.results;m.RESULTS.mkdir(parents=True,exist_ok=True)
    try:run(args.old)
    except Exception as e:
        m.REPORT['error']=str(e)
        try:m.REPORT['last_ui']=m.visible(m.screen())
        except Exception:pass
        raise
    finally:(m.RESULTS/'result.json').write_text(json.dumps(m.REPORT,indent=2)+'\n')
