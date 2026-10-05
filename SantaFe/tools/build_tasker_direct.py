#!/usr/bin/env python3
"""Build standalone direct API tasks and native Tasker scene navigation."""
import argparse
import pathlib
import xml.etree.ElementTree as E

ROOT = pathlib.Path(__file__).resolve().parents[1]
VERSION = "1.2.0"
STAMP = "1791223200000"
TASKS = [
    ("SFD Verify Actions", "verify"), ("SFD Setup", "setup"),
    ("SFD Connect", "connect"), ("SFD Choose Vehicle", "choose"),
    ("SFD Status", "status"), ("SFD Refresh Status", "refresh"),
    ("SFD Lock", "lock"), ("SFD Unlock", "unlock"),
    ("SFD Remote Start", "start"), ("SFD Remote Stop", "stop"),
    ("SFD Check Command", "poll"), ("SFD Resolve Unknown", "resolve"),
    ("SFD Climate Settings", "climate"), ("SFD Controls", "controls"),
    ("SFD Clear Session", "clear"), ("SFD Forget Account", "forget"),
]
BASE_PAGES = ["Home", "Controls", "Status", "Account", "Climate", "Command", "Help"]
PAGES = BASE_PAGES + ["Location"]
# Every network/settings button closes the scene, runs the existing guarded
# core once and reopens an updated display. Navigation itself is offline.
OPERATIONS = [
    ("Connect", "connect", "Status"), ("Read status", "status", "Status"),
    ("Refresh status", "refresh", "Status"), ("Lock", "lock", "Command"),
    ("Unlock", "unlock", "Command"), ("Start", "start", "Command"),
    ("Stop", "stop", "Command"), ("Check command", "poll", "Command"),
    ("Resolve unknown", "resolve", "Command"), ("Edit account", "setup", "Account"),
    ("Edit climate", "climate", "Climate"), ("Choose vehicle", "choose", "Account"),
    ("Clear session", "clear", "Account"), ("Forget account", "forget", "Account"),
]

def field(parent, name, value):
    child = E.SubElement(parent, name)
    child.text = str(value)
    return child

def arg(parent, index, value, integer=False):
    if integer:
        return E.SubElement(parent, "Int", sr="arg" + str(index), val=str(value))
    child = E.SubElement(parent, "Str", sr="arg" + str(index), ve="3")
    child.text = str(value)
    return child

def native_order(node):
    node[:] = sorted(node, key=lambda child: (1, child.get("sr")) if child.get("sr") is not None else (0, child.tag))
    for child in node:
        native_order(child)

def action(code):
    node = E.Element("Action", sr="", ve="7")
    field(node, "code", code)
    return node

def call(name, operation="", mode=""):
    node = action(130)
    arg(node, 0, name)
    priority = E.SubElement(node, "Int", sr="arg1")
    field(priority, "var", "%priority")
    for index, value in [(2, operation), (3, mode), (4, "%sfd_return"), (7, "")]:
        arg(node, index, value)
    for index, value in [(5, 0), (6, 0), (8, 0), (9, 0), (10, 1)]:
        arg(node, index, value, True)
    return node

def java(code):
    node = action(474)
    arg(node, 0, code)
    arg(node, 1, "%sfd_return")
    arg(node, 2, 1, True)
    return node

def destroy(name):
    node = action(49)
    field(node, "se", "false")
    arg(node, 0, name)
    return node

def show(name):
    node = action(47)
    arg(node, 0, name)
    for index, value in [(1, 2), (2, 100), (3, 100), (4, 0), (5, 0), (6, 0), (7, 1), (8, 0), (9, 0), (10, 0)]:
        arg(node, index, value, True)
    return node

def build(destination):
    root = E.Element("TaskerData", sr="", dvi="1", tv="6.7.6-beta")
    field(root, "dmetric", "1440.0,3120.0")
    ids = {}
    def task(name, actions):
        task_id = 34001 + len(ids)
        ids[name] = task_id
        node = E.SubElement(root, "Task", sr="task" + str(task_id))
        for tag, value in [("cdate", STAMP), ("edate", STAMP), ("id", task_id), ("nme", name), ("pri", 6), ("rty", 0)]:
            field(node, tag, value)
        for index, item in enumerate(actions):
            item.set("sr", "act" + str(index))
            node.append(item)
    core = "\n\n".join((ROOT / "tasker/direct" / name).read_text() for name in ["api.java", "ui.java", "watch.java", "location.java", "core.java"])
    task("SFD Core", [java(core)])
    for name, operation in TASKS:
        task(name, [call("SFD Core", operation)])
    task("SFD GUI Prepare", [java((ROOT / "tasker/direct/gui.java").read_text())])
    task("SFD Close GUI", [destroy("SFD " + page) for page in PAGES])
    for page in BASE_PAGES:
        task("SFD Open " + page, [call("SFD Close GUI"), call("SFD GUI Prepare"), show("SFD " + page)])
    task("SFD Open", [call("SFD Open Home")])
    for title, operation, page in OPERATIONS:
        task("SFD GUI " + title, [call("SFD Close GUI"), call("SFD Core", operation, "gui"), call("SFD Open " + page)])
    # Append additions after the original 41 tasks so existing task IDs stay stable.
    for name, operation in [("SFD Start Cold", "start_cold"), ("SFD Start Hot", "start_hot"), ("SFD Cold Settings", "climate_cold"), ("SFD Hot Settings", "climate_hot"), ("SFD Watch Settings", "watch_settings")]:
        task(name, [call("SFD Core", operation)])
    task("SFD Watch Receive", [call("SFD Core", "watch", "%par1")])
    task("SFD Join Event", [call("SFD Core", "watch", "%jointext")])
    for title, operation, page in [("Start cold", "start_cold", "Command"), ("Start hot", "start_hot", "Command"), ("Edit cold", "climate_cold", "Climate"), ("Edit hot", "climate_hot", "Climate"), ("Watch settings", "watch_settings", "Account")]:
        task("SFD GUI " + title, [call("SFD Close GUI"), call("SFD Core", operation, "gui"), call("SFD Open " + page)])
    task("SFD Open Location", [call("SFD Close GUI"), call("SFD GUI Prepare"), show("SFD Location")])
    for name, operation in [("SFD Read Location", "location"), ("SFD Compare Locations", "compare_location"), ("SFD Location Settings", "location_settings"), ("SFD Periodic Location", "periodic_location")]:
        task(name, [call("SFD Core", operation)])
    for title, operation in [("Read car GPS", "location"), ("Compare GPS", "compare_location"), ("Location settings", "location_settings")]:
        task("SFD GUI " + title, [call("SFD Close GUI"), call("SFD Core", operation, "gui"), call("SFD Open Location")])

    def scene(page):
        node = E.SubElement(root, "Scene", sr="sceneSFD " + page)
        for tag, value in [("cdate", STAMP), ("edate", STAMP), ("heightLand", 1180), ("heightPort", 2200), ("nme", "SFD " + page), ("widthLand", 2200), ("widthPort", 1320)]:
            field(node, tag, value)
        return node
    def geometry(x, y, w, h, lx, ly, lw, lh):
        return ",".join(map(str, [x, y, w, h, lx, ly, lw, lh]))
    def label(node, name, text, y, height, size=12, color="#FFF3F6F7", landscape_y=None, landscape_h=None):
        index = len(list(node.findall("TextElement"))) + len(list(node.findall("ButtonElement")))
        item = E.SubElement(node, "TextElement", sr="elements" + str(index), ve="3")
        field(item, "flags", 4)
        field(item, "geom", geometry(50, y, 1220, height, 50, y if landscape_y is None else landscape_y, 2100, height if landscape_h is None else landscape_h))
        for n, value in [(0, name), (1, text), (4, color), (5, "")]:
            arg(item, n, value)
        for n, value in [(2, size), (3, 100), (6, 0), (7, 0), (8, 0)]:
            arg(item, n, value, True)
    def buttons(node, items, start=950, landscape_start=590):
        for i, (title, target) in enumerate(items):
            index = len(list(node.findall("TextElement"))) + len(list(node.findall("ButtonElement")))
            item = E.SubElement(node, "ButtonElement", sr="elements" + str(index), ve="3")
            field(item, "clickTask", ids[target])
            field(item, "flags", 4)
            field(item, "geom", geometry(50 + i % 2 * 620, start + i // 2 * 170, 600, 140, 50 + i % 4 * 530, landscape_start + i // 4 * 150, 510, 130))
            for n, value in [(0, "Button" + str(i)), (1, title), (4, "#FFDBFAE9"), (5, "")]:
                arg(item, n, value)
            for n, value in [(2, 13), (3, 100), (6, 0)]:
                arg(item, n, value, True)
            E.SubElement(item, "Img", sr="arg7", ve="2")
    def finish(node, page):
        prop = E.SubElement(node, "PropertiesElement", sr="props")
        arg(prop, 0, 1, True); arg(prop, 1, 0, True); arg(prop, 2, "#FF102024"); arg(prop, 3, 0, True)
        arg(prop, 4, "SFD " + page); arg(prop, 5, "")
        E.SubElement(prop, "Img", sr="arg6", ve="2"); arg(prop, 7, "")
    navigation = [("Home", "SFD Open Home"), ("Close", "SFD Close GUI")]
    for page in PAGES:
        node = scene(page)
        label(node, "Title", "Santa Fe / " + page, 35, 115, 20, "#FFDBFAE9")
        if page == "Home":
            label(node, "Status", "%SFDGuiStatus", 180, 380, 14, landscape_y=170, landscape_h=230)
            label(node, "State", "Last result: %SFDGuiState\n%SFDGuiPending", 590, 260, 12, landscape_y=415, landscape_h=145)
            items = [(name, "SFD Open " + name) for name in ["Controls", "Status", "Account", "Climate", "Command", "Location", "Help"]] + [("Connect", "SFD GUI Connect"), ("Close", "SFD Close GUI")]
        elif page == "Controls":
            label(node, "Status", "%SFDGuiStatus", 180, 380, 14, landscape_y=170, landscape_h=230)
            label(node, "Notice", "Regular uses saved climate settings. Cold/hot use their own presets. Starts ask you to confirm the car is outdoors.\n%SFDGuiPending", 590, 270, 12, landscape_y=415, landscape_h=145)
            items = [("Lock", "SFD GUI Lock"), ("Unlock", "SFD GUI Unlock"), ("Start regular", "SFD GUI Start"), ("Remote stop", "SFD GUI Stop"), ("Start cold", "SFD GUI Start cold"), ("Start hot", "SFD GUI Start hot"), ("Climate presets", "SFD Open Climate"), ("Command result", "SFD Open Command")] + navigation
        elif page == "Status":
            label(node, "Status", "%SFDGuiStatus", 180, 410, 14, landscape_y=170, landscape_h=250)
            label(node, "Notice", "This is the last status sample Hyundai returned. A refresh request can still return cached data. Unknown means no recognized value.\n%SFDGuiVehicle", 610, 260, 12, landscape_y=435, landscape_h=135)
            items = [("Read status", "SFD GUI Read status"), ("Request refresh", "SFD GUI Refresh status"), ("Connect", "SFD GUI Connect"), ("Choose vehicle", "SFD GUI Choose vehicle")] + navigation
        elif page == "Account":
            label(node, "Account", "%SFDGuiAccount\n\n%SFDGuiVehicle", 180, 380, 14, landscape_y=170, landscape_h=230)
            label(node, "Notice", "Edit account opens the masked settings form. Blank password/PIN retain saved values. Clear session keeps the saved account. Forget removes it. Tasker backups can contain credentials.", 590, 270, 12, landscape_y=415, landscape_h=155)
            items = [("Edit account", "SFD GUI Edit account"), ("Connect", "SFD GUI Connect"), ("Choose vehicle", "SFD GUI Choose vehicle"), ("Clear session", "SFD GUI Clear session"), ("Forget account", "SFD GUI Forget account"), ("Watch settings", "SFD GUI Watch settings"), ("Help", "SFD Open Help")] + navigation
        elif page == "Climate":
            label(node, "Climate", "%SFDGuiClimate", 180, 410, 14, landscape_y=170, landscape_h=250)
            label(node, "Notice", "Cold defaults to 62 F without defrost; hot to 81 F with defrost. Each preset is editable. Seats and steering-wheel heat stay off. Editing sends no car command.\n%SFDGuiPending", 610, 260, 12, landscape_y=435, landscape_h=135)
            items = [("Start regular", "SFD GUI Start"), ("Remote stop", "SFD GUI Stop"), ("Start cold", "SFD GUI Start cold"), ("Start hot", "SFD GUI Start hot"), ("Edit regular", "SFD GUI Edit climate"), ("Edit cold", "SFD GUI Edit cold"), ("Edit hot", "SFD GUI Edit hot"), ("Command result", "SFD Open Command")] + navigation
        elif page == "Command":
            label(node, "Result", "State: %SFDGuiState\n%SFDGuiResult\n\nHTTP: %SFDGuiHttp", 180, 410, 14, landscape_y=170, landscape_h=250)
            label(node, "Notice", "%SFDGuiPending\nAccepted is different from completed. Check command follows the existing transaction. Resolve unknown requires checking the car and never resends it.", 610, 280, 12, landscape_y=435, landscape_h=145)
            items = [("Check command", "SFD GUI Check command"), ("Resolve unknown", "SFD GUI Resolve unknown"), ("Vehicle status", "SFD Open Status"), ("Controls", "SFD Open Controls")] + navigation
        elif page == "Location":
            label(node, "Location", "%SFDGuiLocation", 180, 1100, 12, landscape_y=160, landscape_h=580)
            label(node, "Schedule", "%SFDGuiLocationSchedule", 1300, 180, 12, landscape_y=750, landscape_h=85)
            items = [("Read car GPS", "SFD GUI Read car GPS"), ("Compare phone GPS", "SFD GUI Compare GPS"), ("Location settings", "SFD GUI Location settings"), ("Status", "SFD Open Status")] + navigation
        else:
            label(node, "Help", "FIRST USE\nAccount > Edit account > Connect. Choose your car if asked.\n\nDAILY USE\nControls has lock, unlock, regular/cold/hot start and stop. Climate edits each preset independently.\n\nWATCH\nAccount > Watch settings enables signed Join commands. Test connection on the watch first. Join receipt means sent to the phone; Command shows the car result.\n\nRESULTS\nStatus is the last vehicle sample. An unresolved outcome blocks another command. Check the command or the car before resolving it.\n\nOpening pages does not contact Hyundai. Horn and lights are absent.", 180, 1200, 12, landscape_y=160, landscape_h=590)
            items = [("Account", "SFD Open Account"), ("Command result", "SFD Open Command")] + navigation
        buttons(node, items, start=1510 if page == "Location" else 1470 if page == "Help" else 980, landscape_start=850 if page == "Location" else 785 if page == "Help" else 625)
        finish(node, page)
    profile = E.SubElement(root, "Profile", sr="prof34501", ve="2")
    for tag, value in [("cdate", STAMP), ("edate", STAMP), ("clp", "true"), ("id", 34501), ("mid0", ids["SFD Periodic Location"]), ("nme", "SFD Location Heartbeat")]:
        field(profile, tag, value)
    repeat = E.SubElement(profile, "Time", sr="con0")
    for tag, value in [("fh", -1), ("fm", -1), ("rep", 3), ("repval", 1), ("th", -1), ("tm", -1)]:
        field(repeat, tag, value)
    state = E.SubElement(profile, "State", sr="con1", ve="2")
    field(state, "code", 165)
    conditions = E.SubElement(state, "ConditionList", sr="if")
    condition = E.SubElement(conditions, "Condition", sr="c0", ve="3")
    field(condition, "lhs", "%SFDAutoLocation"); field(condition, "op", 2); field(condition, "rhs", "1")
    project = E.SubElement(root, "Project", sr="proj0", ve="2")
    for tag, value in [("cdate", STAMP), ("id", "6a226f09-759f-44e2-8bf8-92f407b7b021"), ("name", "Santa Fe Direct"), ("psort", "Alpha"), ("scenes", ",".join("SFD " + page for page in PAGES)), ("tids", ",".join(map(str, ids.values())))]:
        field(project, tag, value)
    field(project, "pids", "34501")
    native_order(root)
    E.indent(root, space="\t")
    destination.parent.mkdir(parents=True, exist_ok=True)
    E.ElementTree(root).write(destination, encoding="utf-8", xml_declaration=False)
    print(f"Santa Fe Direct {VERSION}: {len(ids)} tasks, {len(list(root.iter('Action')))} actions, {len(PAGES)} native scenes. {destination}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=pathlib.Path, default=ROOT / "tasker/Santa_Fe_Direct.prj.xml")
    build(parser.parse_args().output)
