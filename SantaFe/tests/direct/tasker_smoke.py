#!/usr/bin/env python3
"""Import the delivered XML into an isolated, official Tasker trial on Android.

Only offline verification, synthetic account save/restore and cancelled forms
run. No real account is entered and no control task runs. Failures retain UI
evidence for diagnosis.
"""
import argparse
import csv
import hashlib
import io
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

def native_xml(data):
    # Tasker's internal autobackup omits whitespace between quoted attributes.
    # Normalize tag spacing only; preserve every stored value and code string.
    source = data.decode("utf-8")
    source = re.sub(r"<[^>]+>", lambda match: re.sub(r"(?<=[\"'])(?=[A-Za-z_][\w.:-]*\s*=)", " ", match.group()), source)
    return E.fromstring(source)

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
    selected = False
    for _ in range(20):
        root = screen()
        REPORT.setdefault("import_windows", []).append(visible(root))
        if selected and matching(root, "SFD Core") is not None:
            return root
        if selected and matching(root, "Tasks") is not None:
            if matching(root, "Apply") is not None:
                click(root, ["Apply"])
                root = screen()
            # Tasker selects the newly imported project. Tapping that already
            # selected tab opens its context menu rather than selecting Tasks.
            click(root, ["Tasks"])
            time.sleep(0.8)
            root = screen()
            if matching(root, "SFD Core") is not None:
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
        if matching(root, "Import Task") is not None and matching(root, "Set Sort") is not None:
            adb("shell", "input", "keyevent", "4")
            continue
        if click(root, ["Santa_Fe_Direct.prj.xml", "Santa_Fe_Direct"]):
            selected = True
            continue
        if click(root, ["Download", "Downloads", "projects"]):
            continue
        if not selected and matching(root, "Tasks") is not None:
            time.sleep(1)
            continue
        if not click(root, ["Allow", "Allow all", "Yes", "Import", "OK", "Continue", "Done", "No"]):
            raise RuntimeError("Unhandled import UI: " + str(visible(root)))
    raise RuntimeError("Import did not expose tasks")

def open_task(name):
    # Each search starts at the top, rather than inheriting another task's scroll.
    for _ in range(4):
        adb("shell", "input", "swipe", "500", "450", "500", "1500", "150")
    root = screen()
    for _ in range(20):
        node = matching(root, name)
        if node is not None:
            bounds = [int(v) for v in re.findall(r"\d+", node.get("bounds", ""))]
            if len(bounds) == 4 and (bounds[1] + bounds[3]) // 2 > 1800:
                # A visible label can overlap the bottom project bar. Move it
                # into the middle of the task list before tapping it.
                adb("shell", "input", "swipe", "500", "1600", "500", "1000", "300")
                time.sleep(0.5)
                root = screen()
                continue
            tap(node)
            root = screen()
            if matching(root, "Task Edit") is not None or any(
                word in n.get("text", "") for n in nodes(root)
                for word in ["Java Code", "Perform Task", "Destroy Scene"]
            ):
                return root
            # Re-observe a task-list transition; never try Run on the list.
            time.sleep(0.5)
            root = screen()
            continue
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

def scene_screen():
    # Tasker's scene window is visible and tappable, but UiAutomator reports
    # the editor underneath it. Read the actual rendered title/button bounds
    # from a retained screenshot instead of inventing fixed tap coordinates.
    root = screen()
    picture = RESULTS / f"scene-observation-{SEQUENCE:03d}.png"
    picture.write_bytes(adb("exec-out", "screencap", "-p"))
    result = subprocess.run(["tesseract", str(picture), "stdout", "--psm", "11", "tsv"], capture_output=True, text=True, check=True, timeout=20)
    picture.with_suffix(".tsv").write_text(result.stdout)
    lines = {}
    for item in csv.DictReader(io.StringIO(result.stdout), delimiter="\t"):
        if not item.get("text", "").strip() or float(item["conf"]) < 35:
            continue
        key = tuple(item[k] for k in ["block_num", "par_num", "line_num"])
        lines.setdefault(key, []).append(item)
    rendered = E.Element("hierarchy")
    for words in lines.values():
        text = " ".join(w["text"] for w in words)
        left=min(int(w["left"]) for w in words); top=min(int(w["top"]) for w in words)
        right=max(int(w["left"])+int(w["width"]) for w in words); bottom=max(int(w["top"])+int(w["height"]) for w in words)
        E.SubElement(rendered, "node", text=text, enabled="true", clickable="true", bounds=f"[{left},{top}][{right},{bottom}]")
        # HTML navigation can share a single OCR line. Preserve observed word
        # bounds too, so tapping Settings uses evidence rather than fixed pixels.
        if len(words) > 1:
            for word in words:
                x, y, w, h = [int(word[k]) for k in ["left", "top", "width", "height"]]
                E.SubElement(rendered, "node", text=word["text"], enabled="true", clickable="true", bounds=f"[{x},{y}][{x+w},{y+h}]")
    if any(n.get("text", "").startswith("Santa Fe / ") for n in nodes(rendered)) or any(matching(rendered, text) is not None for text in ["Climate start", "Tasker phone interface", "Phone connection"]):
        REPORT["scene_observation"] = "Retained screenshots and Tesseract OCR of rendered titles/button bounds; UiAutomator for native forms."
        return rendered
    return root

def wait_for(text):
    # First Java-action compilation and scene transitions can outlast the
    # fixture's short delay. Observe until the target exists; never retap.
    for _ in range(15):
        root = scene_screen() if text.startswith("Santa Fe / ") or text in ["Climate start", "Tasker phone interface", "Phone connection", "PHONE REACHED"] else screen()
        if matching(root, text) is not None:
            return root
        time.sleep(1)
    return root

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
                    root = native_xml(data)
                except E.ParseError:
                    continue
                candidates.append((path, root, data))
    if not candidates:
        raise RuntimeError("Could not read Tasker's parsed configuration; files: " + listing)
    path, root, data = candidates[0]
    (RESULTS / "tasker-roundtrip.data").write_bytes(data)
    E.ElementTree(root).write(RESULTS / "tasker-roundtrip.xml", encoding="utf-8")
    REPORT["roundtrip_source"] = path
    tasks = [t for t in root.iter("Task") if t.findtext("nme", "").startswith("SFD ")]
    counts = {t.findtext("nme"): len(t.findall("Action")) for t in tasks}
    check(len(counts) == 64 and sum(counts.values()) == 134 and all(count > 0 for count in counts.values()), "Actual Tasker import retains all 64 tasks and 134 executable actions")
    scenes = [s for s in root.iter("Scene") if s.findtext("nme", "").startswith("SFD ")]
    check(len(scenes) == 9, "Actual Tasker import retains all nine scenes")
    profiles = [p for p in root.iter("Profile") if p.findtext("nme") == "SFD Location Heartbeat"]
    check(len(profiles) == 1 and profiles[0].findtext("Time/rep") == "3" and profiles[0].findtext("Time/repval") == "1", "Actual Tasker retains the hourly location profile")
    check(profiles[0].findtext("State/ConditionList/Condition/lhs") == "%SFDAutoLocation", "Periodic profile retains its opt-in condition")
    join = next(p for p in root.iter("Profile") if p.findtext("nme") == "SFD Join Commands")
    check(join.findtext("Event/Bundle/Vals/FilterText") == "hyundai=:=", "Actual Tasker retains the supplied Join filter")
    web = next(s for s in scenes if s.findtext("nme") == "SFD Web").find("WebElement")
    check(web.find('Int[@sr="arg1"]').get("val") == "2" and "SFD Web Receive" in web.findtext('Str[@sr="arg2"]'), "Actual Tasker retains the bundled HTML and local receiver")
    REPORT["imported_scene_names"] = [s.findtext("nme") for s in scenes]
    REPORT["imported_action_counts"] = counts
    delivered = E.parse(ROOT / "tasker/Santa_Fe_Direct.prj.xml").getroot()
    original = next(t for t in delivered.iter("Task") if t.findtext("nme") == "SFD Core").findtext('Action/Str[@sr="arg0"]')
    imported = next(t for t in root.iter("Task") if t.findtext("nme") == "SFD Core").findtext('Action/Str[@sr="arg0"]')
    check(original.strip() == imported.strip(), "Tasker retains the complete delivered Java core without changing its source")
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
    for _ in range(15):
        root = screen()
        if matching(root, "Santa Fe Direct verification") is not None:
            break
        time.sleep(1)
    check(matching(root, "Santa Fe Direct verification") is not None and any("Core has 1 executable action(s)" in n.get("text", "") for n in nodes(root)), "Actual Tasker executes offline Core verification and reports one executable action")
    check(any("Status parser verified: Locked / Off / Off" in n.get("text", "") for n in nodes(root)), "Actual Tasker JSON reader maps boolean status flags correctly")
    check(click(root, ["OK"]), "Offline verification dialog closes normally")
    time.sleep(1)
    back_to_tasks()
    root, data = save_and_read()
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
    root = open_task("SFD Setup")
    play(root)
    time.sleep(1)
    for index, value in enumerate(["fixture@example.invalid", "fixture-test-only", "1357"]):
        root = screen()
        editable = [n for n in nodes(root) if n.get("class") == "android.widget.EditText"]
        if len(editable) <= index:
            raise RuntimeError("Synthetic account input not visible")
        tap(editable[index])
        adb("shell", "input", "text", value)
        # Hide the input view so the next field retains its observed geometry.
        keyboard = adb("shell", "dumpsys", "input_method").decode()
        if "mInputShown=true" in keyboard or "isInputViewShown=true" in keyboard:
            adb("shell", "input", "keyevent", "4")
            time.sleep(0.5)
    check(click(screen(), ["Save"]), "Synthetic account can be saved without a login or vehicle command")
    time.sleep(2)
    check(matching(screen(), "Santa Fe account") is None, "Account save validates the entered fields and closes normally")
    back_to_tasks()
    adb("shell", "input", "keyevent", "4")
    time.sleep(1)
    adb("shell", "am", "force-stop", PACKAGE)
    startup()
    root = open_task("SFD Setup")
    play(root)
    time.sleep(1)
    root = screen()
    editable = [n for n in nodes(root) if n.get("class") == "android.widget.EditText"]
    check(len(editable) == 4 and editable[0].get("text") == "fixture@example.invalid", "Saved Tasker account is restored after actual process restart")
    check(editable[1].get("text") in ["", "Password"] and editable[2].get("text") in ["", "Four-digit Bluelink PIN"], "Saved password and PIN remain hidden and are not visibly prefilled")
    check(click(root, ["Save"]), "Blank password and PIN can retain the saved synthetic credentials")
    time.sleep(2)
    check(matching(screen(), "Santa Fe account") is None, "Saved password and PIN survive process restart and pass setup validation")
    REPORT["synthetic_account_saved"] = True
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
    check(matching(root, "Santa Fe controls") is not None and matching(root, "Start regular") is not None, "Actual Tasker opens the control menu")
    check(not any("horn" in n.get("text", "").lower() or "lights" in n.get("text", "").lower() for n in nodes(root)), "Control menu contains no horn or lights option")
    check(click(root, ["Cancel"]), "Control menu cancels without any vehicle operation")
    time.sleep(1)
    back_to_tasks()
    root = open_task("SFD Open Home")
    play(root)
    root = wait_for("Santa Fe / Home")
    check(matching(root, "Santa Fe / Home") is not None, "Actual Tasker opens the native Home scene")
    check(not any("%SFDGui" in n.get("text", "") for n in nodes(root)), "Scene labels resolve their display variables")
    (RESULTS / "scene-home.png").write_bytes(adb("exec-out", "screencap", "-p"))
    for page in ["Controls", "Status", "Account", "Climate", "Command", "Help", "Location"]:
        check(click(root, [page]), "Home navigation opens " + page)
        root = wait_for("Santa Fe / " + page)
        check(matching(root, "Santa Fe / " + page) is not None, "Native " + page + " scene is visible")
        (RESULTS / ("scene-" + page.lower() + ".png")).write_bytes(adb("exec-out", "screencap", "-p"))
        if page == "Controls":
            check(all(matching(root, text) is not None for text in ["Lock", "Unlock", "Start regular", "Start cold", "Start hot", "Remote stop"]), "Control scene displays all six controls including presets")
            check(not any("horn" in n.get("text", "").lower() or "lights" in n.get("text", "").lower() for n in nodes(root)), "Control scene contains no unconfirmed controls")
        if page == "Account":
            check(click(root, ["Edit account"]), "Scene account button runs the saved settings form")
            root = wait_for("Santa Fe account")
            inputs = [n for n in nodes(root) if n.get("class") == "android.widget.EditText"]
            check(len(inputs) == 4 and inputs[0].get("text") == "fixture@example.invalid" and sum(n.get("password") == "true" for n in inputs) == 2, "Account scene retains the saved synthetic account and masks secrets")
            check(click(root, ["Cancel"]), "Scene account edit can be cancelled")
            root = wait_for("Santa Fe / Account")
            check(matching(root, "Santa Fe / Account") is not None, "Cancelled account form returns to its scene")
        if page == "Climate":
            check(click(root, ["Edit regular"]), "Scene climate button opens settings without a command")
            root = wait_for("Remote start settings")
            check(matching(root, "Remote start settings") is not None, "Scene opens the native climate form")
            check(click(root, ["Cancel"]), "Scene climate edit can be cancelled")
            root = wait_for("Santa Fe / Climate")
            check(matching(root, "Santa Fe / Climate") is not None, "Cancelled climate form returns to its scene")
            for label, title, temperature in [("Edit cold", "Cold start settings", "62"), ("Edit hot", "Hot start settings", "81")]:
                check(click(root, [label]), "Scene opens " + title)
                root = wait_for(title)
                check(matching(root, temperature) is not None, title + " has the independent default")
                check(click(root, ["Cancel"]), title + " cancels without a command")
                root = wait_for("Santa Fe / Climate")
        if page == "Location":
            check(click(root, ["Location settings"]), "Scene opens offline location settings")
            root = wait_for("Santa Fe location settings")
            check(matching(root, "150") is not None, "Location settings show the distance threshold")
            check(click(root, ["Cancel"]), "Location settings cancel without a GPS or Hyundai request")
            root = wait_for("Santa Fe / Location")
        check(click(root, ["Home"]), "Native scene returns to Home from " + page)
        root = wait_for("Santa Fe / Home")
        check(matching(root, "Santa Fe / Home") is not None, "Home is restored after " + page)
    check(click(root, ["Close"]), "Native Close button dismisses the GUI")
    check(matching(scene_screen(), "Santa Fe / Home") is None, "GUI closes without any vehicle operation")
    back_to_tasks(); play(open_task("SFD Open"))
    root = wait_for("Climate start")
    check(matching(root, "Climate start") is not None, "SFD Open renders the bundled HTML interface offline")
    check(matching(root, "Tasker phone interface") is not None, "WebView detects the real Tasker JavaScript interface")
    (RESULTS / "scene-web.png").write_bytes(adb("exec-out", "screencap", "-p"))
    check(click(root, ["Settings"]), "HTML navigation opens phone settings")
    root = wait_for("Phone connection")
    check(click(root, ["Test connection"]), "Actual HTML button dispatches an offline request to Tasker")
    root = scene_screen(); check(click(root, ["Controls"]), "HTML returns to Controls after the connection test")
    root = wait_for("PHONE REACHED")
    check(matching(root, "PHONE REACHED") is not None, "Actual Tasker WebView shows the correlated offline receiver result")
    (RESULTS / "scene-web-ping.png").write_bytes(adb("exec-out", "screencap", "-p"))
    check(click(root, ["Native screens"]), "Bundled HTML returns to native Home without network")
    root = wait_for("Santa Fe / Home"); click(root, ["Close"])
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
