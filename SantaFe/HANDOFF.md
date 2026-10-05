# Santa Fe handoff

## Current state

HTML-based API validation controls the later Tasker implementation. API Lab 0.3.2 bundles `SantaFe/index.html`, CSS and client code in an Android APK under `android/`. Its restricted native HTTPS component connects directly to Hyundai. The static GitHub page links that APK; `santafe/index.html` still redirects there. The existing 0.1.0 loopback bridge/simulator remains intact.

The latest supplied 0.3.1 native Android log confirms login HTTP 200, encoded-email enrollment HTTP 502 C500, then literal-@ enrollment HTTP 200 using the same session. One active 2026 Santa Fe Hybrid Calligraphy HEV was returned, with generation 3 and service evStatus N. Cached status and a manual refresh:true status request also returned HTTP 200. Both status bodies and their vehicle timestamp were identical, about two hours old, so fresh physical data is not confirmed. No remote control request occurred. Derived evidence and confirmed read recipes are in docs/verification/account-log-review.json and confirmed-api-recipes.json; the private raw attachment is not republished.

0.3.2 makes the now-confirmed literal-@ enrollment format the default. Test alternate lookup remains a manual same-session comparison after failure; it changes only the @ representation, preserves other encoding and headers, and adopts a form only after the expected vehicle-list array. No automatic fallback, password retry or command runs. A refresh returning the previous vehicle timestamp displays a warning. Newly discovered identifier fields and opaque echoes are scrubbed from fresh responses and older saved logs on startup. Earlier 0.3.0 and 0.3.1 releases remain intact.

Jon declined the broad trust required by Tampermonkey. The Android app is now the recommended test workflow. It loads only its installed assets, never live website scripts. Only Internet permission is requested. Native request policy, normal system TLS, no redirects/cookies/cache, bounded requests and responses, fixed-length POST streaming and no application retry loop constrain transport. The earlier userscript/manual browser modes remain for preservation and regression checks, not the recommended phone setup.

Credentials and session state are memory-only; on leaving the foreground the WebView is destroyed and requests are stopped. Sanitized logs persist and export through Android's document picker, which also clears the login. A minimal sanitized unresolved-command marker is written before transmission and survives restart. Acknowledgement after checking the vehicle is required before another command following an interrupted session; no transaction ID, VIN or credentials are stored in that marker.

The user's first browser attempt showed "Browser helper not detected" and a failed direct response read. That established a transport/setup failure, not an account failure. Legacy automatic/helper modes still refuse login without the helper. In the app the native component is required; missing or malformed bridge state locks login and never falls back to browser requests.

The original Tasker project imported named tasks but zero actions on Tasker 6.7.6-beta. The generator now matches the native export's `sr`-first action attributes and lexicographic serialized child-slot ordering. It retains 40 tasks, 113 actions, 8 profiles and 10 scenes. This is a corrected import candidate, not a confirmed phone/runtime fix. The broken XML is archived in `tasker/archive/`.

## Controlling sources

Implementation: `to-shreds/Misc/SantaFe` on main. Readiness: `to-shreds/ProjectStatus/projects/santa-fe-control-center/STATUS.md`. Diagnostic request recipes: upstream Hyundai USA source at commit `82801884bdf619c5f2a35ff6bbae1693d2e1a3e8`, recorded in `docs/SOURCES.md` and `docs/verification/diagnostic-api-source.json`. Native Tasker shapes were checked against the private Regex export; only a synthetic sanitized fixture is committed.

## Completed

HTML login/enrollment/cached-status testing; manual fresh-status requests; returned-field capability inspection; individual lock/unlock/climate/light/horn tests; manual transaction polling with per-command service types; automatic sanitized local logs and JSON export; restricted userscript transport retained; Android app and native request policy; Tasker serialization correction and regressions; preservation of old XML and action payloads.

The unauthenticated login preflight returned HTTP 500 without CORS permission for the GitHub Pages origin. Ordinary HTML could not read that response. Native HTTPS avoids browser CORS. Jon's subsequent native logs independently confirm login and vehicle reads; an MFA challenge and vehicle controls remain unverified.

Application ID `com.jon.santafelab`, version `0.3.2`, version code `5`, minSdk26/target35. Signing identity is preserved privately as `SantaFe-API-Lab-private-update-key.zip` in Jon's files. Restore it outside git and set `SF_SIGNING_DIR` before updates; never publish the archive, keystore or password. Public certificate SHA256: `90ba911d370453290457ea862344a7cbc4aca6f6f49931725b76a91b599c952d`.

## Verification

See `docs/TEST_REPORT.md`, `docs/verification/android-verification.json` and the diagnostic verification files. 52 client regressions and 20 actual Chromium headless-shell scenarios pass, with zero unexpected requests. The unchanged native policy/transport retain their 137/61-check benchmark; Python/Tasker retain 92 tests. The signed 0.3.2 APK and all three bundled assets verify. Fixture success does not establish live API support. Jon's 0.3.1 log confirms phone callbacks, export, login, enrollment and status reads. New 0.3.2 phone execution, fresh physical status, remote commands and Tasker phone import remain unverified.

## Do not break

Preserve the bridge, settings and unrelated projects. No credentials in git, Tasker XML/variables, local storage or exported logs. No public credential proxy, challenge bypass, guessed endpoints, cloud relay, Termux:Tasker, AutoInput or Shizuku dependency. Trim equipment does not prove API capability. Cached timestamps control freshness. Accepted requests are not physical completion; unknown outcomes must never cause automatic retry. Never publish the private native export or Hyundai APK.

## Unresolved / next action

Jon can install 0.3.2 and Connect using the confirmed lookup automatically. No further email-format diagnosis is needed unless that lookup fails. For the next live feature, manually test one control when ready, poll its transaction, physically check the vehicle, and export the sanitized log. No remote feature or automatic profile should be described as confirmed before that evidence.

Tasker must use only confirmed recipes. The existing 113-action import candidate still requires a phone import check and uses the preserved loopback bridge; the APK currently has no Tasker integration. A separate direct, read-only Tasker candidate is the next implementation step: login, literal-@ enrollment, cached status, manual status request, disconnect and sanitized export, with session secrets in volatile Java objects rather than persistent Tasker variables. Controls and automatic profiles remain disabled until confirmed. Tasker Java runtime, scenes and phone behavior remain acceptance checks. Do not request credentials in chat or operate a real vehicle from automated tests.
