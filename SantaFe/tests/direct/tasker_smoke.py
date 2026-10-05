#!/usr/bin/env python3
"""Import the delivered XML into an isolated, official Tasker trial on Android.

Only offline verification and cancelled forms run. No account details are entered
and no control task runs. Failures retain observed UI evidence for diagnosis.
"""
import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as E

PACKAGE = "net.dinglisch.android.taskerm"
ROOT = pathlib.Path(__file__).resolve().parents[2]
RESULTS = None
SEQUENCE = 0
REPORT = {"tasker_version": "6.6.20", "android_api": 35, "real_account_used": False, "vehicle_commands_run": False, "passed": False, "checks": []}

def adb(*args, check=True, timeout=30):
    result = subprocess.run(["adb", *args], capture_output=True, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError(result.stderr.decode(errors="replace")[-1000:])
    return result.stdout

def screen():
    global SEQUENCE
    # Window transitions can leave UiAutomator with no root or an empty file.
    # Preserve each raw result and retry the observation, not the user action.
    for attempt in range(4):
        SEQUENCE += 1
        adb("shell", "rm", "-f", "/sdcard/sfd-window.xml")
        dump = adb("shell", "uiautomator", "dump", "/sdcard/sfd-window.xml", check=False, timeout=40)
        data = adb("exec-out", "cat", "/sdcard/sfd-window.xml", check=False)
        (RESULTS / f"ui-{SEQUENCE:03d}.xml").write_bytes(data)
        (RESULTS / f"ui-{SEQUENCE:03d}-dump.txt").write_bytes(dump)
        try:
            root = E.fromstring(data)
            if root.tag == "hierarchy" and nodes(root):
                return root
        except E.ParseError:
            pass
        if attempt < 3:
            time.sleep(1)
    raise RuntimeError("UiAutomator could not observe a window: " + dump.decode(errors="replace")[-500:] + " / " + data.decode(errors="replace")[-500:])

def nodes(root):
    return list(root.iter("node"))

def matching(root, text):
    return next((n for n in nodes(root) if n.get("text", "").casefold() == text.casefold() or n.get("content-desc", "").casefold() == text.casefold()), None)

def tap(node):
    bounds = [int(v) for v in re.findall(r"\d+", node.get("bounds", ""))]
    if len(bounds) != 4:
        raise RuntimeError("No usable bounds for observed control")
    adb("shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))
    time.sleep(0.7)

def click(root, candidates):
    for text in candidates:
        node = matching(root, text)
        if node is not None and node.get("enabled") == "true":
            tap(node)
            return True
    return False

def check(value, message):
    if not value:
        raise AssertionError(message)
    REPORT["checks"].append(message)
    print("PASS", message, flush=True)

def visible(root):
    return [n.get("text") or n.get("content-desc") or n.get("resource-id") for n in nodes(root) if n.get("text") or n.get("content-desc") or n.get("clickable") == "true"]

def startup():
    adb("shell", "am", "start", "-n", PACKAGE + "/.Tasker")
    time.sleep(2)
    for _ in range(40):
        root = screen()
        if any(n.get("package") == "com.android.chrome" for n in nodes(root)):
            # Tasker's vendor-help link opened Chrome. No browser setup or
            # account is needed for this test; return to Tasker's onboarding.
            adb("shell", "input", "keyevent", "4")
            adb("shell", "am", "start", "-n", PACKAGE + "/.Tasker")
            time.sleep(1)
            continue
        if matching(root, "Placeholder - Please Disable") is not None and any(n.get("package") == "com.android.settings" for n in nodes(root)):
            # Tasker's checklist opens this particular notification channel and
            # explicitly asks for it to be disabled on a fresh Android install.
            switch = next((n for n in nodes(root) if n.get("resource-id") == "android:id/switch_widget" and n.get("checkable") == "true"), None)
            if switch is None:
                raise RuntimeError("No observed placeholder notification switch")
            if switch.get("checked") == "true":
                tap(switch)
            adb("shell", "input", "keyevent", "4")
            adb("shell", "am", "start", "-n", PACKAGE + "/.Tasker")
            time.sleep(1)
            continue
        if any("Pixel Launcher isn't responding" in n.get("text", "") for n in nodes(root)):
            click(root, ["Close app"])
            adb("shell", "am", "start", "-n", PACKAGE + "/.Tasker")
            time.sleep(2)
            continue
        if matching(root, "Tasks") is not None or matching(root, "TASKS") is not None:
            return root
        if any(n.get("text", "").casefold().startswith(("loading", "checking ", "just a moment")) for n in nodes(root)):
            time.sleep(1)
            continue
        if matching(root, "Before We Get Started") is not None:
            unchecked = [n for n in nodes(root) if n.get("checkable") == "true" and n.get("checked") == "false" and n.get("enabled") == "true"]
            if unchecked:
                left, top, right, bottom = [int(v) for v in re.findall(r"\d+", unchecked[0].get("bounds", ""))]
                # A clipped square checkbox can sit behind the bottom button.
                # Scroll it fully into view before activating it.
                if bottom - top >= right - left:
                    tap(unchecked[0])
                    continue
            if click(root, ["Proceed", "Get Started", "Continue", "Next"]):
                continue
            # The fresh-install permission checklist extends below the screen.
            # Move only within the observed scrollable panel to expose the rest.
            panels = [n for n in nodes(root) if n.get("scrollable") == "true"]
            if panels:
                left, top, right, bottom = [int(v) for v in re.findall(r"\d+", panels[0].get("bounds", ""))]
                x = (left + right) // 2
                adb("shell", "input", "swipe", str(x), str(top + (bottom - top) * 3 // 4), str(x), str(top + (bottom - top) // 4), "300")
                time.sleep(0.5)
                continue
        if not click(root, ["Tasker", "Accept", "I Agree", "I accept", "I understand", "Agree", "Start Trial", "Proceed", "Continue", "Get Started", "Next", "OK", "Got it", "Allow", "Skip", "Cancel", "Later", "STOP REMINDING", "No"]):
            # Re-observe an intermediate frame instead of failing while Tasker
            # switches its loading and onboarding activities. The loop is bound.
            time.sleep(1)
    raise RuntimeError("Tasker startup did not finish: " + str(visible(root)))

def import_project():
    adb("shell", "mkdir", "-p", "/sdcard/Tasker/projects")
    adb("push", str(ROOT / "tasker/Santa_Fe_Direct.prj.xml"), "/sdcard/Download/Santa_Fe_Direct.prj.xml")
    adb("push", str(ROOT / "tasker/Santa_Fe_Direct.prj.xml"), "/sdcard/Tasker/projects/Santa_Fe_Direct.prj.xml")
    root = screen()
    project = matching(root, "Default Project")
    if project is None:
        raise RuntimeError("No observed project tab for normal Import Project: " + str(visible(root)))
    left, top, right, bottom = [int(v) for v in re.findall(r"\d+", project.get("bounds", ""))]
    x, y = str((left + right) // 2), str((top + bottom) // 2)
    adb("shell", "input", "swipe", x, y, x, y, "1000")
    time.sleep(0.8)
    root = screen()
    if not click(root, ["Import Project", "Import"]):
        raise RuntimeError("No observed Import Project menu: " + str(visible(root)))
    for _ in range(20):
        root = screen()
        if any(n.get("text", "").startswith("SFD ") for n in nodes(root)):
            return root
        if matching(root, "Tasks") is not None:
            if matching(root, "Apply") is not None:
                click(root, ["Apply"])
                root = screen()
            # Tasker selects the newly imported project. Tapping that already
            # selected tab opens its context menu rather than selecting Tasks.
            click(root, ["Tasks"])
            time.sleep(0.8)
            root = screen()
            if any(n.get("text", "").startswith("SFD ") for n in nodes(root)):
                return root
            explanation = next((n for n in nodes(root) if n.get("text", "").startswith("Profiles link contexts")), None)
            if explanation is not None:
                left, top, right, bottom = [int(v) for v in re.findall(r"\d+", explanation.get("bounds", ""))]
                y = str((top + bottom) // 2)
                adb("shell", "input", "swipe", str(left + (right - left) * 3 // 4), y, str(left + (right - left) // 4), y, "350")
                time.sleep(0.8)
            # Import/apply can replace the pager after a tab tap. Re-observe and
            # select Tasks again within the bound instead of treating a main
            # screen transition as an XML failure.
            continue
        if click(root, ["Santa_Fe_Direct.prj.xml", "Santa_Fe_Direct", "Download", "Downloads", "projects"]):
            continue
        if not click(root, ["Allow", "Allow all", "Yes", "Import", "OK", "Continue", "Done", "No"]):
            raise RuntimeError("Unhandled import UI: " + str(visible(root)))
    raise RuntimeError("Import did not expose tasks")

def open_task(name):
    # Each search starts at the top, rather than inheriting another task's scroll.
    for _ in range(4):
        adb("shell", "input", "swipe", "500", "450", "500", "1500", "150")
    root = screen()
    for _ in range(12):
        node = matching(root, name)
        if node is not None:
            tap(node)
            return screen()
        adb("shell", "input", "swipe", "500", "1500", "500", "450", "300")
        time.sleep(0.5)
        root = screen()
    raise RuntimeError("Imported task not found: " + name)

def play(root):
    candidates = [n for n in nodes(root) if n.get("clickable") == "true" and any(word in (n.get("content-desc", "") + " " + n.get("resource-id", "")).lower() for word in ["play", "run_task", "runtask", "action_run"])]
    if candidates:
        tap(candidates[0])
        return
    if click(root, ["Run", "Play", "Run Task"]):
        return
    raise RuntimeError("No observed task Run control: " + str(visible(root)))

def back_to_tasks():
    adb("shell", "input", "keyevent", "4")
    time.sleep(0.8)
    root = screen()
    if matching(root, "Tasks") is not None:
        click(root, ["Tasks"])

def save_and_read():
    # Leaving Tasker commits its own parsed configuration. Root belongs solely
    # to this disposable emulator and is never used on Jon's phone.
    adb("shell", "input", "keyevent", "4")
    time.sleep(2)
    listing = adb("shell", "find", "/data/data/" + PACKAGE + "/files", "-maxdepth", "3", "-type", "f").decode()
    candidates = []
    for path in listing.splitlines():
        if path.endswith(".xml"):
            data = adb("exec-out", "cat", path, check=False)
            if b"Santa Fe Direct" in data and b"<Task" in data:
                try:
                    root = E.fromstring(data)
                except E.ParseError:
                    continue
                candidates.append((path, root, data))
    if not candidates:
        raise RuntimeError("Could not read Tasker's parsed configuration; files: " + listing)
    path, root, data = candidates[0]
    (RESULTS / "tasker-roundtrip.xml").write_bytes(data)
    REPORT["roundtrip_source"] = path
    tasks = [t for t in root.iter("Task") if t.findtext("nme", "").startswith("SFD ")]
    counts = {t.findtext("nme"): len(t.findall("Action")) for t in tasks}
    check(len(counts) == 17 and all(count == 1 for count in counts.values()), "Actual Tasker import retains all 17 tasks and 17 executable actions")
    REPORT["imported_action_counts"] = counts
    return root, data

def read_result_variables():
    # Inspect actual saved global variable records, never a phrase in the
    # embedded Java source. Source text alone cannot prove a task executed.
    listing = adb("shell", "find", "/data/data/" + PACKAGE + "/files", "/data/data/" + PACKAGE + "/shared_prefs", "-maxdepth", "3", "-type", "f").decode()
    found = {}
    for path in listing.splitlines():
        if not path.endswith(".xml"):
            continue
        try:
            root = E.fromstring(adb("exec-out", "cat", path, check=False))
        except E.ParseError:
            continue
        for record in root.iter("Variable"):
            fields = {child.tag.casefold(): child.text or "" for child in record}
            name = fields.get("name", fields.get("nme", "")).lstrip("%")
            if name in ["SFDState", "SFDResult"]:
                found[name] = fields.get("val", fields.get("value", ""))
        for record in root.iter("string"):
            name = record.get("name", "").lstrip("%")
            if name in ["SFDState", "SFDResult"]:
                found[name] = record.text or ""
    REPORT["observed_result_variables"] = found
    return found

def capture_native_configuration():
    listing = adb("shell", "find", "/data/data/" + PACKAGE + "/files", "-maxdepth", "3", "-type", "f", check=False).decode()
    (RESULTS / "native-files.txt").write_text(listing)
    for index, path in enumerate(listing.splitlines()):
        if not path.endswith(".xml"):
            continue
        data = adb("exec-out", "cat", path, check=False)
        if path.endswith("autobackup.xml"):
            (RESULTS / "native-autobackup.data").write_bytes(data)
            REPORT["native_backup_bytes"] = len(data)
            REPORT["native_backup_prefix"] = data[:80].decode(errors="replace")
        try:
            root = E.fromstring(data)
        except E.ParseError:
            continue
        if root.tag == "TaskerData":
            (RESULTS / f"native-config-{index}.xml").write_bytes(data)

def run():
    REPORT["adb_root"] = adb("root", check=False).decode().strip()
    adb("wait-for-device", timeout=60)
    for operation in ["SYSTEM_ALERT_WINDOW", "WRITE_SETTINGS", "MANAGE_EXTERNAL_STORAGE"]:
        adb("shell", "appops", "set", PACKAGE, operation, "allow", check=False)
    adb("shell", "pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS", check=False)
    adb("shell", "pm", "grant", PACKAGE, "android.permission.READ_MEDIA_AUDIO", check=False)
    adb("shell", "dumpsys", "deviceidle", "whitelist", "+" + PACKAGE, check=False)
    startup()
    import_project()
    root = open_task("SFD Core")
    check(any("Java Code" in n.get("text", "") for n in nodes(root)), "Tasker editor displays the Core Java Code action")
    play(root)  # Core defaults to offline verification.
    time.sleep(2)
    back_to_tasks()
    root, data = save_and_read()
    result = read_result_variables()
    check(result.get("SFDState") == "READY" and result.get("SFDResult", "").startswith("Santa Fe Direct 1.0.0 loaded. Core has 1 executable action(s)."), "Actual Tasker executes offline Core verification successfully")
    startup()
    root = open_task("SFD Setup")
    check(any("Perform Task" in n.get("text", "") for n in nodes(root)), "Tasker editor displays the executable setup wrapper")
    play(root)
    time.sleep(1)
    root = screen()
    check(matching(root, "Santa Fe account") is not None, "Actual Tasker opens the native account form")
    editable = [n for n in nodes(root) if n.get("class") == "android.widget.EditText"]
    check(len(editable) == 4 and sum(n.get("password") == "true" for n in editable) == 2, "Account form contains four inputs and masks password and PIN")
    check(click(root, ["Cancel"]), "Account setup can be cancelled without credentials or network calls")
    time.sleep(1)
    back_to_tasks()
    root = open_task("SFD Climate Settings")
    play(root)
    time.sleep(1)
    root = screen()
    check(matching(root, "Remote start settings") is not None and matching(root, "72") is not None and matching(root, "10") is not None, "Actual Tasker climate form shows temperature and duration defaults")
    check(click(root, ["Cancel"]), "Climate settings can be cancelled without operating the vehicle")
    time.sleep(1)
    back_to_tasks()
    root = open_task("SFD Controls")
    play(root)
    time.sleep(1)
    root = screen()
    check(matching(root, "Santa Fe controls") is not None and matching(root, "Remote start") is not None, "Actual Tasker opens the control menu")
    check(not any("horn" in n.get("text", "").lower() or "lights" in n.get("text", "").lower() for n in nodes(root)), "Control menu contains no horn or lights option")
    check(click(root, ["Cancel"]), "Control menu cancels without any vehicle operation")
    REPORT["passed"] = True

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--results", type=pathlib.Path, required=True)
    args = parser.parse_args()
    RESULTS = args.results
    RESULTS.mkdir(parents=True, exist_ok=True)
    REPORT["project_sha256"] = hashlib.sha256((ROOT / "tasker/Santa_Fe_Direct.prj.xml").read_bytes()).hexdigest()
    try:
        run()
    except Exception as error:
        REPORT["error"] = str(error)
        try:
            last = screen()
            REPORT["last_ui"] = visible(last)
            REPORT["last_ui_nodes"] = [dict(n.attrib) for n in nodes(last)]
        except Exception:
            pass
        try:
            capture_native_configuration()
        except Exception:
            pass
        raise
    finally:
        (RESULTS / "result.json").write_text(json.dumps(REPORT, indent=2) + "\n")
