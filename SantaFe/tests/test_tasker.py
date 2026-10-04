import pathlib, re, xml.etree.ElementTree as E
ROOT=pathlib.Path(__file__).resolve().parents[1]

def test_tasker_membership_and_reference_integrity():
    r=E.parse(ROOT/'tasker/Santa_Fe_Control_Center.prj.xml').getroot()
    tasks={t.findtext('id'):t for t in r.findall('Task')};names={t.findtext('nme') for t in tasks.values()}
    profiles={p.findtext('id'):p for p in r.findall('Profile')};scenes={s.findtext('nme'):s for s in r.findall('Scene')}
    assert len(tasks)==len(r.findall('Task')) and len(names)==len(tasks)
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
        actions=task.findall('Action');assert [a.get('sr') for a in actions]==['act'+str(i) for i in range(len(actions))]
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
