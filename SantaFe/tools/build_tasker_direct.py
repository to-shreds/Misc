#!/usr/bin/env python3
"""Generate the direct project without changing the preserved bridge export."""
import argparse
import pathlib
import xml.etree.ElementTree as E

ROOT = pathlib.Path(__file__).resolve().parents[1]
VERSION = "1.0.0"
STAMP = "1791172800000"
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

def field(parent, name, value):
    child = E.SubElement(parent, name)
    child.text = str(value)
    return child

def arg(parent, index, value, integer=False):
    if integer:
        return E.SubElement(parent, "Int", sr="arg"+str(index), val=str(value))
    child = E.SubElement(parent, "Str", sr="arg"+str(index), ve="3")
    child.text = str(value)
    return child

def native_order(node):
    node[:] = sorted(node, key=lambda child: (1, child.get("sr")) if child.get("sr") is not None else (0, child.tag))
    for child in node:
        native_order(child)

def build(destination):
    root = E.Element("TaskerData", sr="", dvi="1", tv="6.7.6-beta")
    field(root, "dmetric", "1440.0,3120.0")
    tasks = [("SFD Core", None)] + TASKS
    ids = []
    for index, (name, operation) in enumerate(tasks):
        task_id = 34001 + index
        ids.append(task_id)
        task = E.SubElement(root, "Task", sr="task"+str(task_id))
        for tag, value in [("cdate", STAMP), ("edate", STAMP), ("id", task_id), ("nme", name), ("pri", 6), ("rty", 0)]:
            field(task, tag, value)
        action = E.SubElement(task, "Action", sr="act0", ve="7")
        if operation is None:
            field(action, "code", 474)
            code = "\n\n".join((ROOT/"tasker/direct"/file).read_text() for file in ["api.java", "ui.java", "core.java"])
            arg(action, 0, code)
            arg(action, 1, "%sfd_return")
            arg(action, 2, 1, True)
        else:
            field(action, "code", 130)
            arg(action, 0, "SFD Core")
            priority = E.SubElement(action, "Int", sr="arg1")
            field(priority, "var", "%priority")
            for number, value in [(2, operation), (3, ""), (4, "%sfd_return"), (7, "")]:
                arg(action, number, value)
            for number, value in [(5, 0), (6, 0), (8, 0), (9, 0), (10, 1)]:
                arg(action, number, value, True)
    project = E.SubElement(root, "Project", sr="proj0", ve="2")
    for tag, value in [("cdate", STAMP), ("id", "6a226f09-759f-44e2-8bf8-92f407b7b021"), ("name", "Santa Fe Direct"), ("psort", "Alpha"), ("tids", ",".join(map(str, ids)))]:
        field(project, tag, value)
    native_order(root)
    E.indent(root, space="\t")
    destination.parent.mkdir(parents=True, exist_ok=True)
    E.ElementTree(root).write(destination, encoding="utf-8", xml_declaration=False)
    print(f"Santa Fe Direct {VERSION}: {len(tasks)} tasks, {len(list(root.iter('Action')))} actions, no profiles. {destination}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=pathlib.Path, default=ROOT/"tasker/Santa_Fe_Direct.prj.xml")
    build(parser.parse_args().output)
