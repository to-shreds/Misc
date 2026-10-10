# Santa Fe handoff

## Free Render emergency web build, 2026-10-10

Jon selected a free Render service for occasional emergency web control without his phone, Join or Tasker. He accepts the roughly one-minute wake delay. He requested a password gate before the control page loads; the implementation uses a separate website password of at least 12 characters and optional per-command Bluelink PIN.

The new implementation is under SantaFe/web. It serves only a login page publicly; controller HTML, assets and all Hyundai APIs are protected server-side. It reuses the already-confirmed Hyundai USA request recipes and supports Lock, Unlock, Regular start (72 F), Cool cabin (62 F), Warm cabin (81 F with defrost), and Stop. Starts last ten minutes and require outdoor confirmation. Account credentials may be provided inside the authenticated page or protected Render environment settings; browser storage/cookies/logs do not contain them. Tokens remain in server memory.

Fifty-four offline tests passed, including independent adversarial checks and actual local HTTP cookie/control-flow tests. JavaScript syntax, Gunicorn configuration and Blueprint YAML parsing passed. All Hyundai replies in these offline checks were synthetic. The deployed password gate was subsequently verified over real HTTPS, and the public sign-in layout was checked in the cloud browser. No live Hyundai or physical-car verification is claimed. The full Playwright controller fixture could not run because this container has no Chromium executable and its browser download was unusable; the optional test is included. See web/TEST_REPORT.md.

Jon confirmed My Workspace (tea-dakujgmk1f9s73d2v8ng), and the service is live at https://santa-fe-emergency.onrender.com/. Render service srv-db591kt9fdbs73c1fdcg runs on the Free Python plan in Virginia, with manual deployments and no paid dependencies. Deployment dep-db591ld9fdbs73c1ffh0 is live on source commit 2d302454235f40a14b14ec95c355462606e34507. A unique WEBSITE_PASSWORD and random SESSION_SECRET are configured only in protected Render environment settings and are never recorded in git. No Hyundai credentials are configured. Runtime account entry is available inside the signed-in page.

Command preparation delivers a signed HttpOnly pending cookie before execution; single-use intents and an owner-wide lock prevent silent replay. A timeout or restart requires explicit checked-car acknowledgement, with no automatic command retry. Free hosting does not provide a durable global command ledger across browsers, so the server begins conservatively after every process restart. Keep Gunicorn at one worker. Opening/waking the page never operates the car.

Preserve the old Tasker projects, API Lab APKs, bridge and working recipes below. Jon reports that the full web-to-Tasker version never reliably worked; previous component checks do not establish that end-to-end path's reliability.

## Preserved phone implementation

Santa Fe Direct 1.2.0 implements Jon's browser-to-Join-to-phone architecture. GitHub Pages serves the controller at lowercase santafe/. The browser sends one Join push; Tasker on the phone executes the existing Hyundai USA API calls using its saved account. Amazfit is explicitly a separate future project and has no task, page or app in this release.

The project contains 65 tasks, 137 executable actions, nine scenes and two opt-in profiles. SFD Open opens SFD Web, a local bundle generated from control/index.html, control.css, protocol.js and control.js. It invokes SFD Web Receive and reads only safe SFDWebState/SFDWebLog snapshots. The compact interface has Controls, Status, Location, Settings and Log. SFD Web Settings closes the scene before a native editor and returns to the bundled page afterward; this corrects the overlapping WebView/settings behavior caught in native testing. SFD Open Home preserves eight smaller native fallback pages. All original 41 task IDs/names and account variables remain.

Jon reports that the previous scene project works but its text is too large. This supersedes the earlier missing-scenes benchmark. Standalone Tasker login, vehicle selection, lock and unlock are physically confirmed. Regular remote start/stop are physically confirmed in the API Lab. No new logs are required for those recipes. New Join delivery, preset operation and real car GPS remain account-holder acceptance checks. Horn and lights are absent.

Regular/cold/hot starts use the confirmed hybrid climate recipe. Defaults are 72 F, 62 F and 81 F for ten minutes; hot enables defrost. Each is independently editable. Seat and steering heat stay off. These do not reproduce MyHyundai HI/LO or seat ventilation settings.

SFD Join Commands uses the native Join Received Push event from Jon's example, filtered on hyundai=:=, passing %joincomm through SFD Join Event to SFD Join Receive. SFD Join Settings enables it; default is off. By Jon's explicit request, the external browser controller hard-codes the current Join phone device ID and asks only for the Join API key. The API key remains browser-local and is not in git or the XML. The browser never asks for Hyundai credentials.

The external browser now sends only a bare semantic command after the original prefix, for example `hyundai=:=unlock` or `hyundai=:=ignition_on`. Tasker maps that command to the existing phone-side operation and owns Hyundai authentication, request construction, submission and polling. Bare starts keep the phone-side outdoors confirmation. The older structured four-field format remains accepted by the receiver for compatibility, but the external website no longer uses it. Join cannot clear unknown outcomes, change accounts or select vehicles.

SENT in an external browser means Join accepted the push. There is no browser return channel; vehicle results, status and GPS appear on the phone and in its notification. The local Tasker WebView shows correlated completion and sanitized activity. A network/unreadable browser response or HTTP 408/5xx remains delivery unknown across reload. There is no automatic retry. Phone receivers retain the mutex, one control submission, bounded polling and durable unresolved-command guard. Check command follows the original transaction; Resolve unknown remains manual after checking the car.

The privileged phone WebView bundles local HTML rather than loading a mutable website with Tasker's powerful JavaScript interface. One UI source generates both copies. Website updates change the browser; a new XML import updates the local phone copy. API Lab assets/APKs and the old bridge remain preserved.

Location uses the pinned upstream USA GET /ac/v2/rcs/rfc/findMyCar recipe. Car coordinates are compared with the Tasker phone's GPS. Times, phone accuracy and approximate distance are private; exported logs omit coordinates. A verdict requires car data within 15 minutes, a phone fix within two minutes and accuracy at most 100 m. Missing/stale data stays Unknown; an accuracy boundary overlap is Uncertain. The upstream compact time is UTC. Actual car coordinate/timestamp shape needs its first lookup because prior logs redacted that object.

Periodic checks default off. After a valid car lookup, settings can enable 1 to 24 hour checks. Android location permission and background execution remain device setup. Account/vehicle changes clear location cache and consent; a lookup failure pauses checks; an unresolved command skips them. Periodic tasks never operate the car.

## Verification and delivery

### Browser fixed Join phone target, 2026-10-06

After live troubleshooting showed that a direct Join API call to the current phone works while the website's accepted push did not arrive, Jon requested that the website stop accepting a configurable device ID. The external browser controller now hard-codes the current Join phone device ID in its browser-only join-browser.js layer and exposes only the API-key field. The optional Join-link and device-ID inputs remain in the shared HTML solely so the unchanged Tasker phone bundle can keep using the same source, but the browser-only layer hides them and ignores any substituted device ID. Existing saved browser settings are normalized to the fixed phone ID on load.

The API key remains user-entered and may be remembered only in that browser's local storage under the existing setting. No personal Join API key was added to git. The browser now sends only `text=hyundai=:=COMMAND`, using the fixed phone target. Remote-start safety is delegated to Tasker on the phone. The browser-only script URL is v=4 for cache invalidation, and build_tasker_direct.py strips that exact browser-only script tag so the installed Tasker 1.2.0 HTML/export remains logically unchanged.

Targeted verification on current main: join-browser.js, control_browser.cjs and test_join_browser.cjs parse as JavaScript; a browser-layer runtime harness confirmed API-key-only setup, rejection of a substituted phone target, bare `hyundai=:=unlock` transport with no request ID/timestamp/safety fields, and browser delegation of start safety to the phone. Source inspection confirmed the Tasker receiver already accepts one-field bare commands, maps them to the existing operations, and prompts on the phone before a bare remote start. Full live browser-to-phone acceptance remains for Jon to confirm from the published page. No real Hyundai request was made by these checks.


### Browser Join diagnostics correction, 2026-10-06

Jon reported the website's generic "Join rejected the push" message with no explanation in Settings. The browser-only join-browser.js extension now shows the sanitized Join error reason and opens Settings after a definitive rejection. Check saved Join settings makes one read-only request to the officially documented listDevices endpoint, distinguishing a rejected API key from a phone ID absent from that account. It sends no push, operates no car and never saves device-list responses. Keys, device IDs, URLs, emails, VIN-shaped identifiers and coordinate-shaped values are removed from provider errors before display or logging. A rejected push is not retried; ambiguous delivery retains the original guard.

The shared controller/protocol, native phone source and every existing XML remain unchanged. The generator deliberately removes the browser-only extension so the installed phone interface and its reproducible export retain their exact verified bytes. No Tasker reimport is needed for this website correction. The actual cause of Jon's rejected push is still unknown until the new settings check or safe provider reason is read on his browser.

Correction verification: 18 Node transport/diagnostic tests and 19 intercepted Chromium scenarios pass. Nine Tasker source/export regressions pass; a fresh export is byte-for-byte identical to the existing 1.2.0 XML. An unauthenticated OPTIONS probe of listDevices returned HTTP 200 and explicitly allowed the GitHub Pages origin and GET. No real key, account, push or car was exercised.

The exact XML is 229,938 bytes, SHA256 97b2e5322ee10e033c8aba6c25b51ac073e2ec0e5cade2731e67c6ebca493c21. The distinct attachment/source copy is Santa_Fe_Direct_1_2_0_JOIN_HTML.prj.xml.

Host tests pass 78 actual BeanShell scenarios / 7,625 assertions, 101 Python regressions, eight Node protocol tests and 14 Chromium scenarios. Replies are intercepted; no real account, Join push or car operation is used. An unauthenticated OPTIONS probe confirms Join permits the GitHub Pages origin. Actual Tasker 6.6.20 on Android 15 passed 90 fresh-import/UI checks in run 37413012569 on source 7caa1e82179c65e2c31a0534ba81f4203442cefb. The exact 65-task XML imported, its masked account editor opened from HTML and returned to the page, and its offline ping displayed the correlated phone result. Replacement from 1.1.0 passed 14 checks in run 37386233350 on source f6e2bcc97cbefe991950befa1d131340ce3d701f, using the same XML hash. Native evidence is in docs/verification/tasker-direct-emulator.json and tasker-direct-upgrade-verification.json. The preserved Android workflow passed run 37413012579. No real account, Join push or car was used. Source fixtures and tests match the tested Git blobs.

Tasker rejects importing a project while that name already exists. Back up Tasker, remove only the old Santa Fe Direct project with contents while retaining globals, then import the distinct 1.2.0 file. Verify version and check the saved account. The replacement check validates synthetic account retention. Do not give replace-if-offered instructions.

## Preserved API Lab state

API Lab 0.3.3 bundles the canonical HTML/JS/CSS in a signed Android app. Its restricted native HTTPS component connects directly to Hyundai; the static GitHub page links the APK and the original capitalized SantaFe/index.html remains the API Lab. The preserved 0.1.0 loopback bridge/simulator remains intact.

Jon requested remembered account settings after repeatedly losing his session. This explicitly supersedes the earlier memory-only credential choice. Email/password/PIN can now be encrypted with Android Keystore AES-256-GCM in app-private no-backup storage. Tokens remain memory-only. Startup restores saved-account availability without network activity; Connect loads saved details. Explicit Save/Update ends the old session, Disconnect retains the saved account, and Forget clears current credentials while preserving unresolved-command guards. Storage errors are generic and make no unverified claim about partial writes/deletion. Browser mode never saves credentials.

The confirmed usability cause was MainActivity.onStop destroying the WebView, including during file-picker export. Ordinary backgrounding now pauses and retains the live page/session, and return resumes it. Activity/process destruction still closes transport and loses live state. There is no automatic login, fallback or command retry. Expiry requires Connect using the remembered account.

The supplied 0.3.2 native log confirms three login/enrollment/read sessions, two HTTP 200 unlock submissions and one HTTP 200 lock submission. Lock polling reached SUCCESS; the sole unlock poll was PENDING. Jon reports both locking and unlocking worked physically and subsequently confirmed remote start and stop. Later cached timestamps advanced, while the earlier explicit refresh still returned an unchanged sample. Lights and horn remain untested. The raw private log is not republished; safe summaries and placeholder recipes are under docs/verification/. The derived recipe document's wrong owners.hyundaiusa.com origin was corrected to the actual api.telematics.hyundaiusa.com runtime; no working endpoint changed.

The app loads only its three installed assets, needs only Internet permission, and has no extension, hosted credential proxy, computer or Termux requirement. Native host/endpoint/header restrictions, normal system TLS, no redirects/cookies/cache, bounded requests/responses and fixed-length POST streaming remain. Earlier userscript/browser modes are preserved for regressions.

A sanitized unresolved-command action/time marker is persisted before transmission. Pending commands block account updates. Forget keeps the warning even if current credentials must be removed. Unknown outcomes require physical acknowledgement and never trigger retry. Sanitized logs persist and export through Android's document picker.

The original Tasker XML imported task names with zero actions on Tasker 6.7.6-beta. The corrected candidate matches native action attribute/child ordering and retains 40 tasks, 113 actions, eight profiles and ten scenes. It still needs actual phone import/runtime verification and currently uses the preserved loopback bridge. The APK has no Tasker integration. The broken export is archived.

## Controlling sources

Implementation: to-shreds/Misc/SantaFe on main. Readiness: to-shreds/ProjectStatus/projects/santa-fe-control-center/STATUS.md. Main evidence: docs/verification/account-log-review.json and confirmed-api-recipes.json. Hyundai USA source remains pinned to 82801884bdf619c5f2a35ff6bbae1693d2e1a3e8. Platform and Tasker sources are in docs/SOURCES.md; private native Tasker export is not published.

## Completed and verified

64 client regressions and 25 actual Chromium fixture scenarios pass with zero unexpected requests; the mobile saved-account layout is readable. 83 real JCE encrypted-store checks, 137 policy checks and 61 transport fixtures pass. Android release and separate instrumentation compile. Alignment, v2/v3 signature, version 6/0.3.3, Internet-only permission and all bundled asset bytes verify. Certificate remains 90ba911d370453290457ea862344a7cbc4aca6f6f49931725b76a91b599c952d. APK is 62,296 bytes; SHA256 fa176ff122be5948fb4ece2c49d0bd08032386f05985ed36fbddf7bfb62f3c4a. Prior 0.3.0/0.3.1/0.3.2 APKs are unchanged. The preserved Python/bridge suite originally passed 92 tests; the current suite now passes 101 with standalone Tasker coverage.

The cloud workflow was repaired after actual failures: invalid job-level runner context, missing SDK tools, and non-executable script assumptions. It now initializes SDK tools, invokes scripts with bash, builds a CI-key-only APK, installs it on an isolated API 35 emulator and runs credential-free instrumentation. Actionlint 1.7.12 and 17 Bash blocks validate. Cloud run 37262643353 on 7d41c9078fef89ac84e64687150d6058fe61ff5e passed both jobs: 56 actual Android 15/API 35 Keystore/lifecycle checks and all regression suites. The emulator used the same release sources with an ephemeral CI signing key, not the public APK's exact signed bytes. No real account or car was used. See docs/TEST_REPORT.md and docs/verification/android-verification.json.

## Do not break

Preserve settings, prior APKs, bridge/simulator, Tasker candidate and unrelated projects. No personal credentials in git, WebStorage or logs. The API Lab keeps remembered credentials in its native encrypted store. Santa Fe Direct keeps credentials in ordinary Tasker variables because Jon explicitly requested that architecture; do not undo this authorized choice or require APK linkage. Tokens remain volatile in both. Private release signing identity is preserved outside git; restore SantaFe-API-Lab-private-update-key.zip and set SF_SIGNING_DIR before updates. Never publish its keystore/password/archive or private native exports/Hyundai APK.

No guessed endpoints, challenge bypass, public credential relay, Termux:Tasker, AutoInput or Shizuku dependency. Equipment does not prove API capability. Accepted requests are distinct from completion; vehicle timestamps determine freshness. Real accounts and real car commands are never exercised by automated tests.



## Next action

Open https://santa-fe-emergency.onrender.com/, sign in with the separately supplied website password, and connect Jon's Hyundai account inside the protected page. Verify login, enrollment and cached status from Render before testing an individually requested control and its physical result. The hosted password gate is verified; real Hyundai authentication and vehicle operation remain account-holder acceptance checks. Free hosting can take about a minute to wake.

Preserve API Lab assets/APKs, prior downloads, bridge/simulator and unrelated projects. The old phone implementation stays unchanged. Keep private credentials and signing material out of git, browser storage and logs.
