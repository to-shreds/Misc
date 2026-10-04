import copy, pathlib, re, runpy, shutil, subprocess, sys, xml.etree.ElementTree as E
import pytest
ROOT=pathlib.Path(__file__).resolve().parents[1]

def test_tasker_membership_and_reference_integrity():
    r=E.parse(ROOT/'tasker/Santa_Fe_Control_Center.prj.xml').getroot()
    tasks={t.findtext('id'):t for t in r.findall('Task')};names={t.findtext('nme') for t in tasks.values()}
    profiles={p.findtext('id'):p for p in r.findall('Profile')};scenes={s.findtext('nme'):s for s in r.findall('Scene')}
    assert len(tasks)==40 and len(tasks)==len(r.findall('Task')) and len(names)==len(tasks)
    assert len(r.findall('.//Action'))==113
    assert len(scenes)==10
    p=r.find('Project');assert set(p.findtext('tids').split(','))==set(tasks)
    assert set(p.findtext('pids').split(','))==set(profiles)
    assert set(p.findtext('scenes').split(','))==set(scenes)
    for profile in profiles.values():
        for tag in ['mid0','mid1']:
            if profile.find(tag) is not None:assert profile.findtext(tag) in tasks
    for scene in scenes.values():
        for e in scene.iter('clickTask'):assert e.text in tasks
    for task in tasks.values():
        actions=task.findall('Action');assert actions,task.findtext('nme')
        assert {a.get('sr') for a in actions}=={'act'+str(i) for i in range(len(actions))}
        for a in actions:
            code=a.findtext('code')
            if code=='130':assert a.findtext('Str[@sr="arg0"]') in names
            if code in ['46','47','49']:assert a.findtext('Str[@sr="arg0"]') in scenes
    assert r.get('tv')=='6.7.6-beta'

def test_native_code_and_xml_no_secrets_or_plugins():
    s=(ROOT/'tasker/Santa_Fe_Control_Center.prj.xml').read_text()
    assert not re.search(r'pplx-[A-Za-z0-9]{20}|AIza[0-9A-Za-z_-]{30}',s)
    assert 'com.termux.tasker' not in s
    assert 'getNoBackupFilesDir()' in s and 'setInstanceFollowRedirects(false)' in s
    assert 'getVariable("%"' not in s
    assert 'X-goog-api-key' not in s
    assert all(t.findtext('nme').startswith('SF ') for t in E.fromstring(s).findall('Task'))

def test_all_web_scenes_fixed_local_origin_and_correct_routes():
    r=E.parse(ROOT/'tasker/Santa_Fe_Control_Center.prj.xml').getroot()
    valid={'dashboard','controls','climate','automations','simulator','settings','account','diagnostics'}
    routes=set()
    for el in r.iter('WebElement'):
        u=el.findtext('Str[@sr="arg2"]');assert u.startswith('http://127.0.0.1:8293/#');routes.add(u.split('#')[1])
        assert el.find('Int[@sr="arg1"]').get('val')=='0'
    assert routes==valid

def native_serialization_errors(root):
    """Check observed native export conventions, not an Android import emulator."""
    errors=[]
    for node in root.iter():
        if node.get('sr') is not None and next(iter(node.attrib))!='sr':
            errors.append(f'{node.tag}: sr must be the first attribute')
        slots=[child.get('sr') for child in node if child.get('sr') is not None]
        if slots!=sorted(slots):errors.append(f'{node.tag}: serialized child slots are out of order')
    return errors

def test_export_matches_native_action_shapes_and_serialization():
    # Synthetic values only. Slot types/order were checked against a native
    # Tasker 6.7.6-beta export, including the new Java Code action (474).
    native=E.parse(ROOT/'tests/fixtures/tasker/native-action-shapes.xml').getroot()
    current=E.parse(ROOT/'tasker/Santa_Fe_Control_Center.prj.xml').getroot()
    assert not native_serialization_errors(native)
    assert not native_serialization_errors(current)
    shapes={a.findtext('code'):[(c.tag,c.get('sr')) for c in a if c.get('sr','').startswith('arg')] for a in native.iter('Action')}
    for action in current.iter('Action'):
        code=action.findtext('code')
        if code in shapes:
            assert [(c.tag,c.get('sr')) for c in action if c.get('sr','').startswith('arg')]==shapes[code]
            assert list(action.attrib)==['sr','ve']
    broken=E.parse(ROOT/'tasker/archive/Santa_Fe_Control_Center.zero-actions-2026-10-04.prj.xml').getroot()
    # This catches the real archived deliverable, which passed the old tests.
    assert native_serialization_errors(broken)
    assert all(list(a.attrib)==['ve','sr'] for a in broken.iter('Action'))

def semantic_payload(root):
    root=copy.deepcopy(root)
    for node in root.iter():
        for child in list(node):
            if child.tag in ('cdate','edate'):node.remove(child)
        node[:]=sorted(node,key=lambda c:(c.get('sr') is not None,c.get('sr') or c.tag))
    return E.canonicalize(E.tostring(root,encoding='unicode'),strip_text=True)

def test_serialization_repair_preserves_existing_actions_and_references():
    current=E.parse(ROOT/'tasker/Santa_Fe_Control_Center.prj.xml').getroot()
    archived=E.parse(ROOT/'tasker/archive/Santa_Fe_Control_Center.zero-actions-2026-10-04.prj.xml').getroot()
    assert semantic_payload(current)==semantic_payload(archived)

def test_generator_emits_native_structure_and_rejects_empty_tasks(tmp_path):
    (tmp_path/'tasker').mkdir();(tmp_path/'tools').mkdir()
    for name in ('core.java','speak.java'):shutil.copyfile(ROOT/'tasker'/name,tmp_path/'tasker'/name)
    script=tmp_path/'tools/build_tasker.py';shutil.copyfile(ROOT/'tools/build_tasker.py',script)
    subprocess.run([sys.executable,str(script)],check=True,capture_output=True,text=True)
    rebuilt=E.parse(tmp_path/'tasker/Santa_Fe_Control_Center.prj.xml').getroot()
    assert not native_serialization_errors(rebuilt)
    assert semantic_payload(rebuilt)==semantic_payload(E.parse(ROOT/'tasker/Santa_Fe_Control_Center.prj.xml').getroot())
    builder=runpy.run_path(str(script))
    with pytest.raises(ValueError,match='at least one action'):
        builder['task']('SF Empty',[])
