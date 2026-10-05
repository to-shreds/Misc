import pathlib
import re
import subprocess
import sys
import xml.etree.ElementTree as E

ROOT = pathlib.Path(__file__).resolve().parents[1]
PROJECT = ROOT / "tasker/Santa_Fe_Direct.prj.xml"

def test_native_action_shapes_and_membership():
    root = E.parse(PROJECT).getroot()
    fixture = E.parse(ROOT / "tests/fixtures/tasker/native-action-shapes.xml").getroot()
    shapes = {a.findtext("code"): [(c.tag, c.get("sr")) for c in a if c.get("sr", "").startswith("arg")] for a in fixture.iter("Action")}
    tasks = root.findall("Task")
    assert len(tasks) == 61 and len(list(root.iter("Action"))) == 128
    assert len(root.findall("Profile")) == 1 and len(root.findall("Scene")) == 8
    assert root.find("Project").findtext("name") == "Santa Fe Direct"
    assert set(root.find("Project").findtext("tids").split(",")) == {t.findtext("id") for t in tasks}
    names = {t.findtext("nme") for t in tasks}
    assert len(names) == len(tasks)
    for task in tasks:
        actions = task.findall("Action")
        assert actions and [a.get("sr") for a in actions] == ["act" + str(i) for i in range(len(actions))]
        for action in actions:
            assert list(action.attrib) == ["sr", "ve"]
            if action.findtext("code") in shapes:
                assert [(c.tag, c.get("sr")) for c in action if c.get("sr", "").startswith("arg")] == shapes[action.findtext("code")]
            if action.findtext("code") == "130":
                assert action.findtext('Str[@sr="arg0"]') in names
    for node in root.iter():
        if node.get("sr") is not None:
            assert next(iter(node.attrib)) == "sr"
        slots = [c.get("sr") for c in node if c.get("sr") is not None]
        assert slots == sorted(slots)

def test_embedded_source_and_reproducible_export(tmp_path):
    expected = "\n\n".join((ROOT / "tasker/direct" / name).read_text() for name in ["api.java", "ui.java", "watch.java", "location.java", "core.java"])
    root = E.parse(PROJECT).getroot()
    core = root.find('Task/Action[code="474"]')
    assert core.findtext('Str[@sr="arg0"]') == expected
    output = tmp_path / "direct.prj.xml"
    subprocess.run([sys.executable, str(ROOT / "tools/build_tasker_direct.py"), "--output", str(output)], check=True)
    assert output.read_bytes() == PROJECT.read_bytes()

def test_fixed_origin_confirmed_controls_and_secret_handling():
    content = PROJECT.read_text()
    assert "https://api.telematics.hyundaiusa.com" in content
    for prohibited in ["127.0.0.1", "com.termux", "SantaFeNative", "AutoInput", "Shizuku", "/rcs/rhl/", "/evc/fatc/"]:
        assert prohibited not in content
    assert 'getVariable("SFDPassword")' not in content  # getVariable is centralized, never substituted into source text
    assert 'sfValue("SFDPassword")' in content
    assert not re.search(r'tasker\.setVariable\("[^" ]+",\s*(token|id)\)', content)
    assert 'tasker.setJavaVariable("sfDirectSession"' in content
    assert "retryOnConnectionFailure(false)" in content and "followRedirects(false)" in content
    assert "santa-fe-direct-pending.json" in content

def test_preserved_bridge_project_has_distinct_identifiers():
    old = E.parse(ROOT / "tasker/Santa_Fe_Control_Center.prj.xml").getroot()
    new = E.parse(PROJECT).getroot()
    assert old.find("Project").findtext("id") != new.find("Project").findtext("id")
    assert not {t.findtext("id") for t in old.findall("Task")} & {t.findtext("id") for t in new.findall("Task")}
    assert not {t.findtext("nme") for t in old.findall("Task")} & {t.findtext("nme") for t in new.findall("Task")}


def test_scene_navigation_and_control_wiring():
    root = E.parse(PROJECT).getroot()
    tasks = {t.findtext("nme"): t for t in root.findall("Task")}
    by_id = {t.findtext("id"): t.findtext("nme") for t in tasks.values()}
    scenes = {s.findtext("nme"): s for s in root.findall("Scene")}
    assert set(scenes) == {"SFD " + p for p in ["Home", "Controls", "Status", "Account", "Climate", "Command", "Help", "Location"]}
    assert set(root.find("Project").findtext("scenes").split(",")) == set(scenes)
    assert tasks["SFD Open"].findtext('Action/Str[@sr="arg0"]') == "SFD Open Home"
    assert not list(root.iter("WebElement"))
    for name, scene in scenes.items():
        texts = [e.findtext('Str[@sr="arg1"]', "") for e in scene if e.tag in ["TextElement", "ButtonElement"]]
        assert not any(secret in text for text in texts for secret in ["%SFDPassword", "%SFDPin", "%SFDEmail", "%SFDVin"])
        for button in scene.findall("ButtonElement"):
            assert button.findtext("clickTask") in by_id
        for element in list(scene.findall("ButtonElement")) + list(scene.findall("TextElement")):
            x,y,w,h,lx,ly,lw,lh = map(int, element.findtext("geom").split(","))
            assert min(x,y,lx,ly) >= 0 and min(w,h,lw,lh) > 0
            assert x+w <= 1320 and y+h <= 2200 and lx+lw <= 2200 and ly+lh <= 1180
    for title, operation in [("Lock", "lock"), ("Unlock", "unlock"), ("Start", "start"), ("Stop", "stop"), ("Start cold", "start_cold"), ("Start hot", "start_hot")]:
        actions = tasks["SFD GUI " + title].findall("Action")
        assert actions[0].findtext('Str[@sr="arg0"]') == "SFD Close GUI"
        assert actions[1].findtext('Str[@sr="arg0"]') == "SFD Core"
        assert actions[1].findtext('Str[@sr="arg2"]') == operation
        assert actions[1].findtext('Str[@sr="arg3"]') == "gui"
        assert actions[2].findtext('Str[@sr="arg0"]') == "SFD Open Command"

def test_update_preserves_existing_task_ids_and_reduces_scene_type():
    old = E.parse(ROOT / "tasker/Santa_Fe_Direct_1_1_0_SCENES.prj.xml").getroot()
    new = E.parse(PROJECT).getroot()
    tasks = {t.findtext("nme"): t.findtext("id") for t in new.findall("Task")}
    for task in old.findall("Task"):
        assert tasks[task.findtext("nme")] == task.findtext("id")
    for scene in new.findall("Scene"):
        for element in list(scene.findall("TextElement")) + list(scene.findall("ButtonElement")):
            size = int(element.find('Int[@sr="arg2"]').get("val"))
            assert size <= (20 if element.findtext('Str[@sr="arg0"]') == "Title" else 14)
    texts = [b.findtext('Str[@sr="arg1"]') for s in new.findall("Scene") for b in s.findall("ButtonElement")]
    assert {"Start regular", "Start cold", "Start hot", "Edit cold", "Edit hot", "Watch settings"} <= set(texts)

def test_join_event_uses_signed_receiver_without_guessed_plugin_xml():
    root = E.parse(PROJECT).getroot()
    tasks = {t.findtext("nme"): t for t in root.findall("Task")}
    receive = tasks["SFD Join Event"].find("Action")
    assert receive.findtext('Str[@sr="arg0"]') == "SFD Core"
    assert receive.findtext('Str[@sr="arg2"]') == "watch"
    assert receive.findtext('Str[@sr="arg3"]') == "%jointext"
    assert "SFDWatchKey" not in " ".join(s.findtext('Str[@sr="arg1"]', "") for s in root.iter("TextElement"))

def test_periodic_location_has_an_opt_in_guard_and_existing_task_target():
    root = E.parse(PROJECT).getroot()
    profile = root.find("Profile")
    assert root.find("Project").findtext("pids") == profile.findtext("id")
    target = next(t for t in root.findall("Task") if t.findtext("nme") == "SFD Periodic Location")
    assert profile.findtext("mid0") == target.findtext("id")
    assert profile.findtext("Time/rep") == "3" and profile.findtext("Time/repval") == "1"
    assert profile.findtext("State/ConditionList/Condition/lhs") == "%SFDAutoLocation"
    assert profile.findtext("State/ConditionList/Condition/rhs") == "1"
