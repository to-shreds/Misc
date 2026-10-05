# Santa Fe handoff

## Current state

API Lab 0.3.3 bundles the canonical HTML/JS/CSS in a signed Android app. Its restricted native HTTPS component connects directly to Hyundai; the static GitHub page links the APK and lowercase santafe still redirects there. The preserved 0.1.0 loopback bridge/simulator remains intact.

Jon requested remembered account settings after repeatedly losing his session. This explicitly supersedes the earlier memory-only credential choice. Email/password/PIN can now be encrypted with Android Keystore AES-256-GCM in app-private no-backup storage. Tokens remain memory-only. Startup restores saved-account availability without network activity; Connect loads saved details. Explicit Save/Update ends the old session, Disconnect retains the saved account, and Forget clears current credentials while preserving unresolved-command guards. Storage errors are generic and make no unverified claim about partial writes/deletion. Browser mode never saves credentials.

The confirmed usability cause was MainActivity.onStop destroying the WebView, including during file-picker export. Ordinary backgrounding now pauses and retains the live page/session, and return resumes it. Activity/process destruction still closes transport and loses live state. There is no automatic login, fallback or command retry. Expiry requires Connect using the remembered account.

The supplied 0.3.2 native log confirms three login/enrollment/read sessions, two HTTP 200 unlock submissions and one HTTP 200 lock submission. Lock polling reached SUCCESS; the sole unlock poll was PENDING. Jon reports both locking and unlocking worked physically. Later cached timestamps advanced, while the earlier explicit refresh still returned an unchanged sample. Climate, lights and horn remain untested. The raw private log is not republished; safe summaries and placeholder recipes are under docs/verification/. The derived recipe document's wrong owners.hyundaiusa.com origin was corrected to the actual api.telematics.hyundaiusa.com runtime; no working endpoint changed.

The app loads only its three installed assets, needs only Internet permission, and has no extension, hosted credential proxy, computer or Termux requirement. Native host/endpoint/header restrictions, normal system TLS, no redirects/cookies/cache, bounded requests/responses and fixed-length POST streaming remain. Earlier userscript/browser modes are preserved for regressions.

A sanitized unresolved-command action/time marker is persisted before transmission. Pending commands block account updates. Forget keeps the warning even if current credentials must be removed. Unknown outcomes require physical acknowledgement and never trigger retry. Sanitized logs persist and export through Android's document picker.

The original Tasker XML imported task names with zero actions on Tasker 6.7.6-beta. The corrected candidate matches native action attribute/child ordering and retains 40 tasks, 113 actions, eight profiles and ten scenes. It still needs actual phone import/runtime verification and currently uses the preserved loopback bridge. The APK has no Tasker integration. The broken export is archived.

## Controlling sources

Implementation: to-shreds/Misc/SantaFe on main. Readiness: to-shreds/ProjectStatus/projects/santa-fe-control-center/STATUS.md. Main evidence: docs/verification/account-log-review.json and confirmed-api-recipes.json. Hyundai USA source remains pinned to 82801884bdf619c5f2a35ff6bbae1693d2e1a3e8. Platform and Tasker sources are in docs/SOURCES.md; private native Tasker export is not published.

## Completed and verified

64 client regressions and 25 actual Chromium fixture scenarios pass with zero unexpected requests; the mobile saved-account layout is readable. 83 real JCE encrypted-store checks, 137 policy checks and 61 transport fixtures pass. Android release and separate instrumentation compile. Alignment, v2/v3 signature, version 6/0.3.3, Internet-only permission and all bundled asset bytes verify. Certificate remains 90ba911d370453290457ea862344a7cbc4aca6f6f49931725b76a91b599c952d. APK is 62,296 bytes; SHA256 fa176ff122be5948fb4ece2c49d0bd08032386f05985ed36fbddf7bfb62f3c4a. Prior 0.3.0/0.3.1/0.3.2 APKs are unchanged. Python/Tasker remains unchanged with 92 tests, also passed in the initial cloud job.

The cloud workflow was repaired after actual failures: invalid job-level runner context, missing SDK tools, and non-executable script assumptions. It now initializes SDK tools, invokes scripts with bash, builds a CI-key-only APK, installs it on an isolated API 35 emulator and runs credential-free instrumentation. Actionlint 1.7.12 and 17 Bash blocks validate. Cloud run 37262643353 on 7d41c9078fef89ac84e64687150d6058fe61ff5e passed both jobs: 56 actual Android 15/API 35 Keystore/lifecycle checks and all regression suites. The emulator used the same release sources with an ephemeral CI signing key, not the public APK's exact signed bytes. No real account or car was used. See docs/TEST_REPORT.md and docs/verification/android-verification.json.

## Do not break

Preserve settings, prior APKs, bridge/simulator, Tasker candidate and unrelated projects. No plaintext credentials in git, WebStorage, Tasker persistent variables or logs. Saved accounts belong only in the native encrypted store; tokens remain volatile. Private release signing identity is preserved outside git; restore SantaFe-API-Lab-private-update-key.zip and set SF_SIGNING_DIR before updates. Never publish its keystore/password/archive or private native exports/Hyundai APK.

No guessed endpoints, challenge bypass, public credential relay, Termux:Tasker, AutoInput or Shizuku dependency. Equipment does not prove API capability. Accepted requests are distinct from completion; vehicle timestamps determine freshness. Real accounts and real car commands are never exercised by automated tests.

## Unresolved / next action

Cloud checks passed. Jon can install 0.3.3, save account settings once and continue manual climate/light/horn tests without retyping. Lock/unlock are confirmed as described above; each remaining control needs its own result and physical check.

The later Tasker implementation should use the confirmed reads and individually confirmed controls, with volatile session secrets and native encrypted remembered credentials if needed. Preserve the existing bridge project, check actual imported actions on Tasker 6.7.6-beta, and keep unconfirmed controls/automatic profiles disabled. Tasker Java runtime, scenes/background behavior and new 0.3.3 phone behavior remain acceptance checks.
