# Current benchmark: Santa Fe Direct 1.2.0

The browser controller sends one Join push to Tasker; the phone performs the previously confirmed Hyundai USA API calls. SFD Open renders the same compact HTML locally with correlated results. Native fallback screens, the API Lab and prior APK/bridge files are preserved. Amazfit remains separate.

The release XML is Santa_Fe_Direct_1_2_0_JOIN_HTML.prj.xml: 229,938 bytes, SHA256 97b2e5322ee10e033c8aba6c25b51ac073e2ec0e5cade2731e67c6ebca493c21. It has 65 tasks, 137 executable actions, nine scenes and two opt-in profiles. All 41 original task IDs and account variables remain.

## Verified release

- Actual official Tasker 6.6.20 on Android 15/API 35 passed all 90 fresh-import checks in run 37413012569, tested source 7caa1e82179c65e2c31a0534ba81f4203442cefb. The imported XML has the release hash. It retained every task/action/scene/profile and unchanged embedded source; saved synthetic account settings survived process restart. Native navigation, regular/cold/hot editors and location settings opened and cancelled offline. The actual WebView opened its masked account editor, closed it, returned to controls, dispatched an offline ping and displayed its correlated PHONE REACHED result.
- Replacement from the 41-task 1.1.0 project passed 14 actual Tasker checks in run 37386233350, source f6e2bcc97cbefe991950befa1d131340ce3d701f. That source generated the identical release XML. The test reproduced duplicate-name import rejection, removed only that old project with contents, imported 1.2.0 and verified saved synthetic account retention, secret masking, version, parser and WebView. Do not instruct an in-place import or a replace-if-offered flow.
- All 101 Python regressions, 78 actual BeanShell/OkHttp scenarios with 7,625 assertions, eight Node protocol tests and 14 actual Chromium scenarios passed in run 37413012569. The browser tests intercept Join responses; BeanShell tests intercept Hyundai replies. They verify one submission, confirmation, replay/expiry guards, durable unknown outcomes, independent presets, GPS freshness/bounds, opt-in schedules, safe logs and local settings routing. All 23 changed implementation/test/workflow files were compared with their tested Git blob hashes and matched.
- The preserved Android API Lab workflow passed both jobs in run 37413012579 on the same tested source. Its signed public APK and client assets are unchanged. The prior 64 client tests remain part of that preserved benchmark.

Earlier native failures exposed a WebView that covered the account editor and test observations that missed button captions. SFD Web Settings now closes the scene, runs the native editor and reopens the bundled HTML. Complementary screenshot/OCR modes identify the actual rendered captions in the test; OCR is not a phone dependency. The successful run verifies the full editor round trip and offline receiver result.

Reports: verification/tasker-direct-emulator.json, tasker-direct-upgrade-verification.json, tasker-direct-verification.json and controller-browser.json. Fresh-import artifact 11390082142 retains screenshots and raw observations; upgrade artifact 11379921400 retains the replacement evidence.

## Device acceptance remaining

Automation used synthetic settings, sent no live Join push, used no real Hyundai account and operated no car. Jon already confirmed standalone Tasker login/selection/lock/unlock and API Lab start/stop. Official Tasker 6.6.20 differs from his installed 6.7.6-beta. After installation, enable Join, save browser settings and test live reception; then check the desired preset physically. The upstream USA GPS endpoint needs its first real-car response, precise location permissions and an elapsed background interval. Periodic comparison stays off until a valid lookup and explicit opt-in, and never operates the car.

Browser SENT means Join accepted the push. Actual vehicle results are shown on the phone, not returned to an ordinary external browser. Regular/cold/hot use the confirmed temperature/defrost recipe; seat and steering heat remain off. No extra diagnostic log is required to use the confirmed controls.

# Previous benchmark: Santa Fe Direct 1.1.0 native scenes and status display

Jon confirmed login, correct vehicle selection, lock and unlock physically from the standalone Tasker project. Its three status flags showed Unknown even with a returned timestamp. The already supplied tester log has Boolean fields, so no new export was requested. The older host interpreter rendered Boolean fixtures correctly; the exact phone runtime difference was not reproduced there. The updated reader directly calls JSONObject.getBoolean, with missing/null/malformed fields kept Unknown.

Update path correction: Jon reported that the update still had no SFD Open or scenes and all flags remained Unknown. Actual Tasker reproduced the problem when importing over 1.0.0: 'a project with that name already exists'. The old replacement instructions were incorrect; direct import was blocked. The new distinctly named XML copy is byte-identical to the 60-check release. The cleanup/import account-retention test is queued in run 37368933070 during GitHub's runner-assignment incident, and has not passed. Fresh-import GUI verification is separate from that pending upgrade check. See verification/tasker-direct-upgrade-verification.json.

The update adds seven native scenes, 41 tasks and 89 executable actions. SFD Open launches Home; Controls, Status, Account, Climate, Command and Help provide offline navigation and the existing guarded operation/settings tasks. Account/climate buttons close the scene, open the masked native form and return to the scene. All original API tasks, saved credential names, command rules, API Lab assets and bridge exports are preserved.

Verification: all 43 actual BeanShell scenarios pass with 2,553 assertions; all 97 Python regressions pass. New tests cover correct status labels for both states, string Booleans, missing/invalid data, offline GUI preparation, no credential/session secrets in scene summaries, retained pending markers, button task wiring and portrait/landscape element bounds. Actual official Tasker 6.6.20 on Android 15 passed 60 checks in run 37358500720, source 59ca5f9e25d8405dfb25bae1231d017051d2373d. All 41 tasks, 89 actions and seven scenes survived import; the offline status reader passed, every scene opened and returned Home, account/climate edits returned to their page, and Close dismissed the GUI. Screenshots are retained in the run artifact. No real account, Hyundai request or vehicle operation was used. The exact-hash report is verification/tasker-direct-emulator.json.

Project SHA256: 851c4cbac713570e194805692bb17bcfddac83e711c8dd098c9f95d161ecbb83, 119,057 bytes. Updated phone status and scene acceptance remain, as do Tasker remote start/stop checks. Those API recipes were already physically confirmed in the tester.

# Previous benchmark: Santa Fe Direct 1.0.0

Jon confirmed lock/unlock and remote start/stop physically in the tester. The subsequent start/stop confirmation comes from his report, not a new exported log. He explicitly requested standalone Tasker API calls and accepted ordinary Tasker credential variables. The new project includes those four controls, reads, account/climate settings and command follow-up. Horn, lights, APK linkage and automatic profiles are excluded.

The delivered XML contains 17 tasks and 17 native executable actions. Its deterministic generator embeds the complete Java core and uses the native action structure from Jon's 6.7.6-beta export. The existing 40-task bridge project and all prior APKs remain unchanged.

Verification:

- 37 actual BeanShell runtime scenarios pass with 2,291 assertions. The interpreter executes the delivered source with real OkHttp request construction and synthetic intercepted HTTP responses. This covers every control's exact path, headers and body, credential escaping, token reuse/expiry, failed login/enrollment, rate limiting, pending/unknown results, process-loss guards, no command retries, transaction mismatch, multiple vehicles and concurrent execution. No real account or vehicle is contacted. Host dialog functions are fixture substitutes.
- All 96 Python regressions pass, including native action shapes, membership, deterministic embedded source and separation from the preserved bridge project. An existing oversized-upload test now advertises its oversized Content-Length with a small body to observe the early 413 reliably, avoiding a client broken-pipe race without changing the server.
- The preserved 64 JavaScript client regressions pass. API Lab assets, the signed 0.3.3 APK and the old Tasker project retain their hashes.
- Actual official Tasker 6.6.20 on Android 15/API 35 passed 20 native checks in [run 37286734266](https://github.com/to-shreds/Misc/actions/runs/37286734266), tested source 80e1073e7f11f9d6f8cf7fb8c53a788e3e5f0c48. The imported project retained all 17 tasks, 17 executable actions and the complete delivered Java source; offline verification executed. Native account setup masks password/PIN, saves synthetic credentials, restores them after a full Tasker process restart and accepts blank fields to retain saved secrets. Climate settings and the four-control menu opened and cancelled correctly. No login or vehicle command ran. Evidence: verification/tasker-direct-emulator.json. Tasker's private autobackup omits spaces between attributes; the evidence parser inserts spaces inside tags only and compares the unchanged decoded core source. The official trial differs from Jon's 6.7.6-beta.
- The actual native form test caught a password/PIN masking issue caused by the order of Android input setup. Explicit password transformation and corrected setup order resolved it, and the final native test verified both fields.
- The existing Android workflow also passed in [run 37286734304](https://github.com/to-shreds/Misc/actions/runs/37286734304) on the same source. Public API Lab assets and APK bytes were preserved.

Project: tasker/Santa_Fe_Direct.prj.xml, 50,279 bytes; SHA256 251ba44febe56d06acd9ad6dc4268078d081fd10a7cae3c0aced7d2deec91e0d. Machine-readable host evidence: verification/tasker-direct-verification.json. Reproduce with `python3 SantaFe/tools/build_tasker_direct.py`, `PYTHONPATH=. python3 -m pytest tests -q` from SantaFe and `bash SantaFe/tests/direct/run-runtime-tests.sh` with JDK 17 from the repository root.

Final operation on Jon's installed Tasker and desired automation profiles remain acceptance/design steps. Automated tests never submit a real car command.

# Earlier benchmark: API Lab 0.3.3 saved accounts

The latest private 0.3.2 phone export confirms lock submission followed by SUCCESS polling. Unlock submissions returned HTTP 200; the recorded unlock poll was PENDING. Jon reports that both controls worked physically. Later cached timestamps advanced. Only safe derived evidence and placeholder recipes are published; the raw account response is private.

Account settings can now remember email/password/PIN encrypted natively with Android Keystore AES-256-GCM in private no-backup storage. Access tokens remain memory-only. Startup restores account availability without logging in; Connect reuses saved details. Explicit Save/Update ends the previous session. Disconnect retains the saved account; Forget clears current credentials and preserves any unresolved-command warning, including on a storage-removal failure. Saved details never enter WebStorage or log exports. The WebView/session now survive ordinary app switching and export; activity/process destruction clears the live session.

Verification:

- 64 client regressions and 25 actual Chromium scenarios pass, with zero unexpected requests. They cover saved credentials, opt-out, reconnect, update, forget, generic failure handling, redaction and unresolved-command gates. All account/API responses in these suites are fixtures.
- 83 host encryption/store checks pass using genuine JCE AES/GCM, including fresh IVs, authenticated corruption rejection, validation, failure points and deletion.
- 137 native policy and 61 native transport checks pass. The signed Android release and separate instrumentation package compile; APK alignment, v2/v3 signatures, version 6/0.3.3, retained signing certificate and Internet-only permission verify. All three bundled assets match source. No signing material or fixture code is included.
- Mobile saved-account and unsaved reconnect screens were checked without clipping or horizontal overflow. The final Chromium check caught a collapsed required-field reconnect defect; the two-line fix was followed by successful final suites and APK rebuilding.
- The cloud workflow's invalid expression, missing SDK initialization and non-executable script assumptions were fixed. Actionlint 1.7.12 and all 17 Bash blocks pass syntax checks. The isolated Android 15/API 35 emulator ran 56 instrumentation checks successfully, including genuine Keystore encryption, recreation/restoration, corruption rejection and deletion.

APK: android/dist/SantaFe-API-Lab-0.3.3.apk, 62,296 bytes. SHA-256: fa176ff122be5948fb4ece2c49d0bd08032386f05985ed36fbddf7bfb62f3c4a. All three prior APKs remain byte-for-byte unchanged.

Cloud run: https://github.com/to-shreds/Misc/actions/runs/37262643353 on 7d41c9078fef89ac84e64687150d6058fe61ff5e. Both jobs passed, including 92 Python/Tasker, 64 client, 137 policy, 61 transport and 83 encrypted-store checks. The emulator used a CI-only signing identity; the private release key and user credentials were absent. Machine-readable proof: verification/android-cloud-verification-v0.3.3.json.

The 0.3.3 phone update, climate/light/horn controls and Tasker import/runtime remain acceptance checks. Account-holder physical reports and fixture results are recorded separately.

# Earlier benchmark: API Lab 0.3.2 confirmed vehicle reads

The latest supplied 0.3.1 native phone export confirms HTTP 200 login, literal-@ enrollment with one active vehicle, cached status and refresh:true status. The encoded-@ enrollment failed C500 in the same session. Both status responses contained the identical earlier vehicle sample and timestamp, so fresh physical data remains unconfirmed. No remote control request ran. Only derived evidence is published; the private response and its identifiers are not republished.

0.3.2 uses the confirmed lookup by default, retains a manual alternate lookup after failure, warns when a refresh timestamp is unchanged, and scrubs additional identifier fields and opaque echoes from new responses and previously saved logs. All other email delimiters remain encoded. Failed, malformed or cancelled comparisons cannot select a format or run a command.

Verification:

- 52 client regressions and 20 actual Chromium headless-shell scenarios pass with zero unexpected requests. They cover both lookup directions, confirmed-only adoption, session/cancellation/command guards, fresh and saved log redaction, and unchanged versus changed timestamps. All API responses are fixtures.
- The 412px success and enrollment-failure screens were visually inspected without overflow or clipping.
- Android 35 compilation/packaging, ZIP alignment, v2/v3 signatures and version 5/0.3.2 metadata verify. All three bundled assets exactly match source. Only Internet permission is requested, the existing signing identity is preserved, and no private key or test fixture is included.
- Native policy/transport were unchanged and retain the previous 137/61-check benchmark; the Python/Tasker implementation retains its earlier 92 tests. Those suites and the instrumentation compilation were not rerun for this client-only update.

APK: android/dist/SantaFe-API-Lab-0.3.2.apk, 54,104 bytes. SHA-256: a2fa3efdf2977fc915f16f90d284acf9871935d20c98b1830fc17e0ae43995b4. Both prior APKs remain byte-for-byte unchanged. Evidence: verification/android-verification.json, account-log-review.json and confirmed-api-recipes.json.

The supplied phone log establishes 0.3.1 callback/export and real read behavior. New 0.3.2 phone execution and independent Android lifecycle instrumentation remain pending. Fresh physical status, remote controls, transaction completion and actual Tasker phone import/runtime are not established.

# Earlier benchmark: API Lab 0.3.1 enrollment diagnostics

The inspected phone log establishes three successful token responses and three failed enrollment reads (HTTP 502/API 502, C500, getEnrollmentDetailsByUser, NO DATA FOUND TO PERFORM THIS OPERATION). Wrong-password tests returned the separate IDM_401_1 error, also using HTTP 502. No cached status or vehicle command ran. Jon reports that the official MyHyundai app shows his Santa Fe. Only the derived summary is published in verification/account-log-review.json.

The update displays the failed request stage, subcode and sanitized service submessage. A separate **Test vehicle lookup** button becomes available after enrollment failure. It makes one same-session read-only comparison, replacing only the email path's %40 with literal @. All other encoding and authenticated headers stay unchanged. Expected enrollment schema is required before the alternate form is used for this session; cached status follows only when a vehicle is selected. This is a source-backed diagnostic hypothesis, not a verified remedy.

Verification:

- 137 native policy checks and 61 transport fixture checks pass, including exact two-form path validation, unchanged headers, no extra requests and retained backend errors.
- 47 client regressions pass, covering the observed C500 and IDM_401_1 responses, manual comparison, failure/malformed schema, confirmed-only format choice, expiration, duplicate actions, cancellation, late callbacks, structural privacy scrubbing and stale command gating.
- 19 actual Chromium headless-shell scenarios pass with zero unexpected network requests. Mobile enrollment error screenshot is readable at 412px with no overflow. The normal Chrome executable could not create its process-singleton socket; the existing headless shell completed the same full suite successfully.
- Android 35 release compilation/packaging, ZIP alignment, v2/v3 signatures and version 4/0.3.1 metadata verify. All three bundled asset bytes match current source. The signing certificate remains identical to 0.3.0. No private key or test fixture is packaged. The separate instrumentation APK compiles, but has not been executed on Android.

APK: android/dist/SantaFe-API-Lab-0.3.1.apk, 54,104 bytes, SHA-256: 83ba8c3d40d41ad2d920d7aacf31312ceceee830e6064004cb71f4c000e83cba. The original 0.3.0 APK is preserved byte-for-byte. Machine-readable verification: verification/android-verification.json. Screenshot: verification/diagnostic-enrollment-502.png.

0.3.0 phone launch, native callbacks and log export are supported by the supplied export. 0.3.1 phone behavior and a successful alternate lookup remain pending. Successful VIN-specific reads, physical vehicle commands and actual Tasker import are unverified. The preserved Python/Tasker implementation remains unchanged with its earlier 92-test benchmark.

To run current browser checks in this environment, set SANTAFE_CHROMIUM=/tmp/santafe-headless/chrome-headless-shell-linux64/chrome-headless-shell. The reproduction commands in the earlier benchmark still apply.

# Earlier benchmark: API Lab 0.3.0 Android tester

The HTML tester is now bundled inside a signed Android APK. The native component connects directly to Hyundai through an exact host/endpoint/header allowlist. No browser extension, public proxy, hosted credential service, or runtime library download is needed. Only Internet permission is requested. The older bridge, simulator, Tasker XML and browser helper are preserved.

Verification completed:

- JDK 17 / Android 35 compilation, AAPT2 packaging, D8, ZIP alignment and v2/v3 APK signature verification passed. All three bundled asset bytes match the canonical tester files. Private signing material and fixture code are absent from the release APK.
- 114 independent request-policy checks and 52 native-transport fixture checks pass. They cover destination rejection before connection, HTTPS/redirect/cache/streaming settings, body/header limits, immutability, concurrent requests, deadlines, cancellation, safe errors and suppressed callbacks after closure.
- 37 adversarial client checks and 15 actual headless Chromium UI scenarios pass. These cover native-only transport, missing/invalid bridge state, no browser fallback, request/response validation, sanitized exports, file-picker status messaging, read/command separation, restored unknown-command gating, transaction completion, cancellation and privacy. No unexpected network requests occurred. All Hyundai and Android transport responses in these suites are explicit fixtures.
- Independent source review found and corrected two important exit cases: interrupted commands now persist a sanitized warning before transmission, and cancellation logging retains redaction values until completion. Enrollment URLs are structurally scrubbed even after session clearing.
- The separate Android instrumentation APK compiles and is preserved under `android/tests/device/`. It is not included in the release.

**Android execution remains unverified.** The Android 30 software emulator did not complete boot within the final 180-second bound on this host without KVM; earlier attempts encountered stale AVD locks. No Android launch, native bridge, actual lifecycle, or document-picker result was observed. Chromium fixtures do not substitute for those checks. The first phone launch and log export remain acceptance checks.

No real Hyundai login, API availability, VIN-specific support or physical command has been tested. This is not an independent security audit. The signed APK is available for the account holder's supervised testing, not a verified automation release. The unchanged Python/Tasker files retain their earlier 92-test benchmark.

APK: `android/dist/SantaFe-API-Lab-0.3.0.apk`, 54,104 bytes. SHA-256: `cc83c31a078a792042b56355ee853bfd5f2c80f754c946d6b80f9b0b08cfcc70`. Machine-readable evidence: `verification/android-verification.json`; fixture browser screenshot: `verification/diagnostic-android-fixture.png`.

Reproduce app checks:

```sh
# From SantaFe/android, with the Android SDK configured:
./build.sh
./tests/run-policy-tests.sh
JSON_JAR=/path/to/json-20240303.jar ./tests/transport/run-transport-tests.sh
# From SantaFe, with Playwright and Chromium available:
node --test tests/test_diagnostic.cjs
SANTAFE_CHROMIUM=/path/to/chromium node tests/diagnostic_browser.cjs
```

Restore the privately preserved signing identity before rebuilding updates. Test instrumentation belongs on an isolated test device/emulator; it never submits credentials or contacts a real vehicle.

# Earlier benchmark: API Lab 0.2.1 helper setup gate

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
