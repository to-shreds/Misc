# Santa Fe handoff

## Current state

Santa Fe Direct 1.0.0 is the new standalone Tasker project. Jon confirmed lock, unlock, remote start and remote stop physically in the tester, then explicitly authorized direct Tasker API calls with credentials retained in ordinary Tasker variables. His report establishes start/stop confirmation without requiring another log export. The APK, Termux and loopback bridge are not required for this project. Horn, lights and automatic profiles are excluded.

`tasker/Santa_Fe_Direct.prj.xml` contains 17 tasks and 17 executable native actions, with a shared Java Code core and Perform Task wrappers. It embeds all Java source and preserves the old bridge project under its original names and IDs. SFD Setup saves email/password/PIN/VIN once; SFD Connect authenticates and reads status; SFD Controls presents the four confirmed controls and read/settings operations. SFD Climate Settings defaults to 72 F, ten minutes and defrost off. Start requires outdoor/safe-to-start acknowledgement.

Tasker credentials are ordinary persistent variables by Jon's explicit choice, superseding the earlier prohibition for this direct project only. Password/PIN fields are masked and blank when revisiting setup; blank retains the saved value. Credentials can appear in Tasker backups and must not be shared. Access tokens, vehicle registration details and transaction IDs remain in a volatile Java object. Live login is reused and renewed before an operation after expiry/process loss. A login/enrollment failure blocks repeated automatic password attempts until explicit Connect or changed settings; HTTP 429 imposes a five-minute cooldown.

The requests match the working tester: exact Hyundai origin/headers, literal-@ enrollment, lock/unlock bodies, gen3 non-EV start with registration ID in its body, empty stop body and transaction polling. Responses are bounded and use normal TLS, no redirects/cookies/cache and no connection retry. A command is submitted once. Acceptance and completion are distinct; fixed ten-second polling is bounded to ten polls during a roughly two-minute wait. Pending/unknown results persist a minimal private action/time guard before POST. The guard survives process loss and blocks another command; Check Command polls only the existing transaction and Resolve Unknown requires a physical check before local acknowledgement. No API command is resubmitted.

Host verification executes the delivered BeanShell with real OkHttp requests and synthetic intercepted responses: 37 scenarios and 2,291 assertions pass. All 96 Python regressions and the preserved 64 JavaScript tests pass. An existing oversized-body HTTP test was made deterministic by advertising the oversized Content-Length with a small body, preserving the server's early-rejection check without racing a client broken pipe. Actual Tasker 6.6.20 on Android 15/API 35 passed 20 native checks in run 37286734266: all 17 tasks retained their executable actions and complete core source, offline verification ran, native password/PIN masking worked, synthetic account settings survived process restart, and climate/control dialogs opened and cancelled without a vehicle operation. The observed report is docs/verification/tasker-direct-emulator.json. The native test caught and verified a password/PIN masking fix before release. Final Hyundai operation on Jon's installed Tasker 6.7.6-beta remains an account-holder check.

## Preserved API Lab state

API Lab 0.3.3 bundles the canonical HTML/JS/CSS in a signed Android app. Its restricted native HTTPS component connects directly to Hyundai; the static GitHub page links the APK and lowercase santafe still redirects there. The preserved 0.1.0 loopback bridge/simulator remains intact.

Jon requested remembered account settings after repeatedly losing his session. This explicitly supersedes the earlier memory-only credential choice. Email/password/PIN can now be encrypted with Android Keystore AES-256-GCM in app-private no-backup storage. Tokens remain memory-only. Startup restores saved-account availability without network activity; Connect loads saved details. Explicit Save/Update ends the old session, Disconnect retains the saved account, and Forget clears current credentials while preserving unresolved-command guards. Storage errors are generic and make no unverified claim about partial writes/deletion. Browser mode never saves credentials.

The confirmed usability cause was MainActivity.onStop destroying the WebView, including during file-picker export. Ordinary backgrounding now pauses and retains the live page/session, and return resumes it. Activity/process destruction still closes transport and loses live state. There is no automatic login, fallback or command retry. Expiry requires Connect using the remembered account.

The supplied 0.3.2 native log confirms three login/enrollment/read sessions, two HTTP 200 unlock submissions and one HTTP 200 lock submission. Lock polling reached SUCCESS; the sole unlock poll was PENDING. Jon reports both locking and unlocking worked physically and subsequently confirmed remote start and stop. Later cached timestamps advanced, while the earlier explicit refresh still returned an unchanged sample. Lights and horn remain untested. The raw private log is not republished; safe summaries and placeholder recipes are under docs/verification/. The derived recipe document's wrong owners.hyundaiusa.com origin was corrected to the actual api.telematics.hyundaiusa.com runtime; no working endpoint changed.

The app loads only its three installed assets, needs only Internet permission, and has no extension, hosted credential proxy, computer or Termux requirement. Native host/endpoint/header restrictions, normal system TLS, no redirects/cookies/cache, bounded requests/responses and fixed-length POST streaming remain. Earlier userscript/browser modes are preserved for regressions.

A sanitized unresolved-command action/time marker is persisted before transmission. Pending commands block account updates. Forget keeps the warning even if current credentials must be removed. Unknown outcomes require physical acknowledgement and never trigger retry. Sanitized logs persist and export through Android's document picker.

The original Tasker XML imported task names with zero actions on Tasker 6.7.6-beta. The corrected candidate matches native action attribute/child ordering and retains 40 tasks, 113 actions, eight profiles and ten scenes. It still needs actual phone import/runtime verification and currently uses the preserved loopback bridge. The APK has no Tasker integration. The broken export is archived.

## Controlling sources

Implementation: to-shreds/Misc/SantaFe on main. Readiness: to-shreds/ProjectStatus/projects/santa-fe-control-center/STATUS.md. Main evidence: docs/verification/account-log-review.json and confirmed-api-recipes.json. Hyundai USA source remains pinned to 82801884bdf619c5f2a35ff6bbae1693d2e1a3e8. Platform and Tasker sources are in docs/SOURCES.md; private native Tasker export is not published.

## Completed and verified

64 client regressions and 25 actual Chromium fixture scenarios pass with zero unexpected requests; the mobile saved-account layout is readable. 83 real JCE encrypted-store checks, 137 policy checks and 61 transport fixtures pass. Android release and separate instrumentation compile. Alignment, v2/v3 signature, version 6/0.3.3, Internet-only permission and all bundled asset bytes verify. Certificate remains 90ba911d370453290457ea862344a7cbc4aca6f6f49931725b76a91b599c952d. APK is 62,296 bytes; SHA256 fa176ff122be5948fb4ece2c49d0bd08032386f05985ed36fbddf7bfb62f3c4a. Prior 0.3.0/0.3.1/0.3.2 APKs are unchanged. The preserved Python/bridge suite originally passed 92 tests; the current suite now passes 96 with standalone Tasker coverage.

The cloud workflow was repaired after actual failures: invalid job-level runner context, missing SDK tools, and non-executable script assumptions. It now initializes SDK tools, invokes scripts with bash, builds a CI-key-only APK, installs it on an isolated API 35 emulator and runs credential-free instrumentation. Actionlint 1.7.12 and 17 Bash blocks validate. Cloud run 37262643353 on 7d41c9078fef89ac84e64687150d6058fe61ff5e passed both jobs: 56 actual Android 15/API 35 Keystore/lifecycle checks and all regression suites. The emulator used the same release sources with an ephemeral CI signing key, not the public APK's exact signed bytes. No real account or car was used. See docs/TEST_REPORT.md and docs/verification/android-verification.json.

## Do not break

Preserve settings, prior APKs, bridge/simulator, Tasker candidate and unrelated projects. No personal credentials in git, WebStorage or logs. The API Lab keeps remembered credentials in its native encrypted store. Santa Fe Direct keeps credentials in ordinary Tasker variables because Jon explicitly requested that architecture; do not undo this authorized choice or require APK linkage. Tokens remain volatile in both. Private release signing identity is preserved outside git; restore SantaFe-API-Lab-private-update-key.zip and set SF_SIGNING_DIR before updates. Never publish its keystore/password/archive or private native exports/Hyundai APK.

No guessed endpoints, challenge bypass, public credential relay, Termux:Tasker, AutoInput or Shizuku dependency. Equipment does not prove API capability. Accepted requests are distinct from completion; vehicle timestamps determine freshness. Real accounts and real car commands are never exercised by automated tests.

## Unresolved / next action

Import Santa_Fe_Direct.prj.xml as a separate project, run SFD Verify Actions, SFD Setup, SFD Connect and SFD Controls. Check the first Tasker control's physical outcome. The tester's successful four controls establish the recipes; actual execution on Jon's installed Tasker is the remaining manual acceptance step. No new logs or APK installation is required for this Tasker setup.

Keep horn/lights absent until Jon elects to test them. Define automatic profiles only after the standalone manual Tasker controls are confirmed and Jon specifies the desired trigger behavior.
