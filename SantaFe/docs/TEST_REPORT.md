# Current benchmark: API Lab 0.2.1 helper setup gate

The user's initial browser screenshot showed no helper detected and a failed direct response read. That is a transport/setup failure, not proof of bad credentials. Connection setup now precedes credentials. Automatic/helper modes disable the account form until a valid helper handshake, refuse Enter/programmatic submissions without the helper, and never fall back to direct requests. Explicit Direct mode remains available for diagnostics.

21 adversarial diagnostic tests and ten actual Chromium scenarios pass, including the new missing-helper gate. No unexpected network requests occurred, and all Hyundai responses remain fixtures. Updated results and desktop/mobile screenshots are under `verification/diagnostic-*`. Python and Tasker implementation are unchanged from the 92-test benchmark below; real Firefox/Hyundai login and actual Tasker phone import remain unverified.

## Earlier benchmark: API Lab 0.2.0 and Tasker serialization repair

Verified October 4, 2026:

- 92 Python tests pass, including all preserved bridge/engine/HTTP/installer checks and six native-format Tasker regressions.
- 19 adversarial diagnostic client/helper tests pass through the shipped scripts' UI event handlers.
- Nine actual Chromium scenarios pass through HTTP-loaded HTML/CSS/JavaScript, with fixture Hyundai responses and a fixture GM transport. They cover login/export/disconnect, real postMessage handshake, readable transaction headers, SUCCESS versus submission, two-vehicle selection, missing transaction IDs, rate limiting, CORS failures, empty login bodies, duplicate submission gating and 412-pixel mobile layout. No unexpected network request occurred.
- JavaScript syntax checks and `git diff --check` pass. Desktop/mobile screenshots and machine-readable results are under `verification/diagnostic-*`.
- A real credential-free OPTIONS probe of Hyundai's login endpoint returned HTTP 500 without CORS permission. This confirms that particular plain-browser access problem, not login correctness.

The original Tasker file is archived, and its corrected replacement preserves all 40 tasks, 113 actions, eight profiles and ten scenes. Native action attributes and serialized child order now match a privately inspected known-good 6.7.6-beta export. The fixture regression detects the archived defect and verifies unchanged semantic payloads. Actual Tasker deserialization is still untested; ordering differences are a likely cause, not an importer-confirmed diagnosis.

No real account was logged in and no vehicle was operated. The browser helper is not yet tested in Firefox for Android. Real-account responses must establish VIN-specific support before live Tasker behavior is built. Nothing in the fixture suites upgrades those claims.

Reproduce from `SantaFe/`:

```sh
PYTHONPATH=. python -m pytest tests -q
node --test tests/test_diagnostic.cjs
node --check diagnostic.js
node --check santafe-network.user.js
# Browser check needs Playwright and a Chromium executable:
SANTAFE_CHROMIUM=/path/to/chromium node tests/diagnostic_browser.cjs
```

The browser script uses `CODEX_PRIMARY_RUNTIME_NODE_MODULES` for the runtime Playwright installation; outside that runtime, adjust the module resolution to your own installation. The following sections retain the historical 0.1.0 verification record.

## Historical post-build phone result

On October 4, 2026, the user imported the original Tasker project into Tasker 6.7.6-beta. Task names appeared, but every imported task had zero actions. This real phone failure supersedes any implication that the old structural tests established importability. That XML is now archived; the current correction is described above.

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
