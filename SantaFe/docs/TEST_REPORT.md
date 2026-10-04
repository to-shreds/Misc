# Verification report: version 0.1.0

Verified in the build environment on October 4, 2026. This report distinguishes local checks from Android and Hyundai checks.

## Passed

**89 pytest tests** passed in the final run, covering configuration validation, simulation, command state, rule evaluation, actual local HTTP, private installer behavior, generated Tasker XML structure, and a fake-upstream-library adapter contract.

This includes malformed and nonfinite values; unknown and stale input handling; safe configuration restore; conflicting edits; role-separated pairing; expired and reused confirmations; outdoor acknowledgement; duplicate requests; rejected and timed-out commands; live-arming checks with a test double; automatic-lock guards; reconnect cancellation; expired delays; repeated local-time scheduling; namespaced variables; task allowlists; quiet hours; restart behavior; invalid context retry; and preservation of existing code and user state during installation.

The HTTP tests actually run the local Python service and make HTTP requests, including 100 parallel cache reads. They check authentication, role restrictions, JSON and size validation, Host/Origin restrictions, and security headers. The installed-launcher test starts a copied build from an isolated Linux home, receives the actual health response, and shuts it down cleanly. This is not an Android/Termux installation test.

**15 Chromium/Playwright browser checks** passed with no uncaught JavaScript errors. They cover pairing, eight sections, mobile layout, preset editing, routine editing/execution, confirmation, unknown-data display, escaped user text, a Tasker pairing code, backup restore, concurrent refreshes, stale settings forms, and desktop layout. Screenshots from these checks are in `docs/verification/`.

Browser URL navigation is blocked by a managed environment policy. The screen tests therefore load this project's HTML/CSS/JavaScript into an in-memory DOM and use an explicit model-transport test double. They do not test browser-to-HTTP connectivity, cookies in a Tasker WebView, or on-phone execution. HTTP was tested separately rather than bypassing the browser restriction.

**Tasker XML checks** validate 40 uniquely named tasks, 8 profiles, 10 Scenes, all membership/reference links, consecutive action indices, fixed local UI routes, and the 6.7.6-beta export label. The new XML contains no copied original task names, Hyundai credentials, or Termux:Tasker dependency.

**BeanShell 2.0b4 parsing** passed for all five unique embedded Java snippets in the generated XML. Python compilation, JavaScript syntax checking, and shell syntax checking also passed. BeanShell parsing proves syntax acceptance by that parser, not Android class availability, Tasker method behavior, or successful Scene import.

## Bugs found and corrected during verification

The U.S. library constructor now receives numeric region 3 and brand 2 instead of similarly named string constants. Malformed upstream boolean/numeric data is normalized to unknown. A bad simulation patch no longer partly mutates failure settings. Invalid phone context no longer consumes the event ID. Simulator access is synchronized.

Live rule-generated confirmations now allow five minutes for the two-minute native heartbeat; manual confirmations remain sixty seconds. In-flight refreshes no longer suppress a post-save refresh. Navigation renders a target page once rather than replacing a newly edited form a second time. A stale settings form cannot overwrite another editor's changes. The installer preserves a locally edited same-version directory and an unrelated existing launcher.

## Not verified

No APK was installed or executed here. Tasker project import, Scene sizing on the phone, dropdowns and confirmation dialogs inside its WebView, Java-action execution, native notifications/speech, Bluetooth/Wi-Fi/alarm profiles, permissions, Android Auto hooks, and screen-off/reboot/background survival remain phone checks.

The optional Hyundai library could not be downloaded into this environment. It was not installed or run. The adapter's public interface was source-reviewed and tested against a fake library, not the real package. No account was logged in, no VIN was enrolled, no vehicle was contacted, and no real command was sent. Actual per-vehicle capability discovery remains incomplete.

## Reproduction

```sh
python tools/build_tasker.py
python -m pytest -q tests
node --check ui/app.js
bash -n Install.sh Start.sh Enable-Live.sh
```

The browser test additionally needs Playwright and Chromium. `tests/browser_check.py` records its transport limitation explicitly. BeanShell parsing uses `tools/ParseBeanShell.java` with a separately installed BeanShell JAR; no JAR or Android/Tasker binary is bundled.

## Release decision

The package is suitable for a supervised simulation/Tasker-import test. It is not marked production-ready for real-car automation. Start with SF Open, SF Pair Tasker, SF Sync, and one simulator routine. Keep every real-vehicle routine disabled until the car and account have been verified.
