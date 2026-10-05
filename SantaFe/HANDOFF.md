# Santa Fe handoff

## Current state

HTML-based API validation is the controlling next step. API Lab 0.3.1 bundles `SantaFe/index.html`, CSS and client code in an Android APK under `android/`. Its restricted native HTTPS component connects directly to Hyundai. The static GitHub page links that APK; `santafe/index.html` still redirects there. The existing 0.1.0 loopback bridge/simulator remains intact.

Latest phone evidence is now inspected: the supplied 0.3.0 native Android log records three HTTP 200 login responses containing access tokens, each immediately followed by enrollment HTTP 502/API 502, subcode C500, function getEnrollmentDetailsByUser, and NO DATA FOUND TO PERFORM THIS OPERATION. Two deliberately incorrect logins also returned HTTP 502 but had the distinct IDM_401_1 credential error. No cached status or command request ran. Jon confirms the official MyHyundai app shows the Santa Fe. See docs/verification/account-log-review.json for the derived summary; the private raw attachment is not republished. Do not equate generic 502 with an outage, bad password or proven enrollment cause.

0.3.1 fixes the confirmed diagnostic error presentation and adds a manual Test vehicle lookup button after enrollment failure. It sends exactly one alternate enrollment GET with the same session headers, changing only %40 to literal @; all other encoding remains unchanged. The pinned library uses literal @, but that discrepancy is a test hypothesis, not a confirmed fix. An expected vehicle-list array is required before this session adopts the alternate format. Cached status follows only when a vehicle is selected. No automatic comparison, password retry or vehicle command runs. Failed enrollment invalidates stale status; commands remain gated. Logs record a nonprivate enrollment_path_format and still redact both account URL forms.

Jon declined the broad trust required by Tampermonkey. The Android app is now the recommended test workflow. It loads only its installed assets, never live website scripts. Only Internet permission is requested. Native request policy, normal system TLS, no redirects/cookies/cache, bounded requests and responses, fixed-length POST streaming and no application retry loop constrain transport. The earlier userscript/manual browser modes remain for preservation and regression checks, not the recommended phone setup.

Credentials and session state are memory-only; on leaving the foreground the WebView is destroyed and requests are stopped. Sanitized logs persist and export through Android's document picker, which also clears the login. A minimal sanitized unresolved-command marker is written before transmission and survives restart. Acknowledgement after checking the vehicle is required before another command following an interrupted session; no transaction ID, VIN or credentials are stored in that marker.

The user's first browser attempt showed "Browser helper not detected" and a failed direct response read. That established a transport/setup failure, not an account failure. Legacy automatic/helper modes still refuse login without the helper. In the app the native component is required; missing or malformed bridge state locks login and never falls back to browser requests.

The original Tasker project imported named tasks but zero actions on Tasker 6.7.6-beta. The generator now matches the native export's `sr`-first action attributes and lexicographic serialized child-slot ordering. It retains 40 tasks, 113 actions, 8 profiles and 10 scenes. This is a corrected import candidate, not a confirmed phone/runtime fix. The broken XML is archived in `tasker/archive/`.

## Controlling sources

Implementation: `to-shreds/Misc/SantaFe` on main. Readiness: `to-shreds/ProjectStatus/projects/santa-fe-control-center/STATUS.md`. Diagnostic request recipes: upstream Hyundai USA source at commit `82801884bdf619c5f2a35ff6bbae1693d2e1a3e8`, recorded in `docs/SOURCES.md` and `docs/verification/diagnostic-api-source.json`. Native Tasker shapes were checked against the private Regex export; only a synthetic sanitized fixture is committed.

## Completed

HTML login/enrollment/cached-status testing; manual fresh-status requests; returned-field capability inspection; individual lock/unlock/climate/light/horn tests; manual transaction polling with per-command service types; automatic sanitized local logs and JSON export; restricted userscript transport retained; Android app and native request policy; Tasker serialization correction and regressions; preservation of old XML and action payloads.

The unauthenticated login preflight returned HTTP 500 without CORS permission for the GitHub Pages origin. Ordinary HTML could not read that response. Native HTTPS avoids browser CORS. No real login, MFA challenge, VIN-specific support or vehicle command has been verified by that transport fact.

Application ID `com.jon.santafelab`, version `0.3.1`, version code `4`, minSdk26/target35. Signing identity is preserved privately as `SantaFe-API-Lab-private-update-key.zip` in Jon's files. Restore it outside git and set `SF_SIGNING_DIR` before updates; never publish the archive, keystore or password. Public certificate SHA256: `90ba911d370453290457ea862344a7cbc4aca6f6f49931725b76a91b599c952d`.

## Verification

See `docs/TEST_REPORT.md`, `docs/verification/android-verification.json` and the diagnostic verification files. The unchanged Python/Tasker code retains its 92-test benchmark. New app/client checks use explicit fixtures; fixture success is never real-account verification. 137 native policy checks, 61 transport fixtures, 47 client regressions and 19 actual Chromium headless-shell scenarios pass. The signed 0.3.1 APK and its bundled assets verify. 0.3.0 token receipt/native callbacks/log export are supported by the inspected phone log; 0.3.1 phone execution, successful enrollment/VIN-specific reads, physical commands and Tasker phone import remain unverified.

## Do not break

Preserve the bridge, settings and unrelated projects. No credentials in git, Tasker XML/variables, local storage or exported logs. No public credential proxy, challenge bypass, guessed endpoints, cloud relay, Termux:Tasker, AutoInput or Shizuku dependency. Trim equipment does not prove API capability. Cached timestamps control freshness. Accepted requests are not physical completion; unknown outcomes must never cause automatic retry. Never publish the private native export or Hyundai APK.

## Unresolved / next action

Jon installs 0.3.1 over the existing app, taps Connect and, if enrollment fails, taps Test vehicle lookup without leaving the app or signing in again. Export the resulting sanitized same-session comparison. If both forms fail, compare the account lookup recipe to the official/current primary sources before testing any additional headers or identity variation. No repeated incorrect-password tests. Use successful account/VIN evidence to determine later Tasker behavior. Separately import the corrected XML on Tasker 6.7.6-beta and verify visible actions, starting with SF Open. Scene sizing, Tasker Java runtime, permissions/background behavior and optional bridge dependency installation remain unverified. Do not request credentials in chat or operate a real vehicle from automated tests.
