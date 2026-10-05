# Source and interface notes

## Browser-first API Lab benchmark, October 4, 2026

Diagnostic request recipes are pinned to the primary upstream implementation:

https://github.com/Hyundai-Kia-Connect/hyundai_kia_connect_api/blob/82801884bdf619c5f2a35ff6bbae1693d2e1a3e8/hyundai_kia_connect_api/HyundaiBlueLinkApiUSA.py

USA blob: `9a7fa8b0f333877451ce4b03073f85ed267084b9`. These are source-backed recipes, not an official public Hyundai API contract or proof of this account/VIN's support. Non-EV climate's body `vin` uses the registration ID upstream; lock/light bodies use the VIN. Transaction IDs come from response headers. Lights/horn polling use `LIGHTS_ONLY` and `HORN_AND_LIGHTS`; ordinary commands use `REMOTE_POLL`. Empty HTTP 200 command bodies do not prove completion.

Actual credential-free preflight evidence is at `verification/diagnostic-api-source.json`: HTTP 500 with no `Access-Control-Allow-Origin` or allowed-header response. No account login or vehicle operation was involved.

Primary browser-helper documentation:

https://www.tampermonkey.net/documentation.php?locale=en#api:GM_xmlhttpRequest

https://violentmonkey.github.io/api/gm/#gm_xmlhttprequest

https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS

Special header support varies by browser, including Android. Helper detection and fixture tests do not establish real Firefox/Hyundai compatibility.

The user's native Tasker 6.7.6-beta export was inspected privately. All 466 native actions put `sr` before `ve`; serialized children are lexicographically ordered. The generator now follows both conventions. A synthetic fixture is at `tests/fixtures/tasker/native-action-shapes.xml`. These differences are a likely explanation for zero actions, but only a new actual import can establish the fix.

Reviewed during this build on October 4, 2026. These references support interface choices, not a claim of live validation of this project.

## Tasker

User-supplied Regex_Scene.prj.xml: TaskerData tv=6.7.6-beta and device metrics 1440.0,3120.0; native task, profile, Scene, and action argument examples. Original omitted from release. New IDs and names are generated independently.

Official Java Code documentation: https://tasker.joaoapps.com/userguide/en/help/ah_java_code.html

Official Web element documentation: https://tasker.joaoapps.com/userguide/en/element_web.html

Taskomater's recorded XML action/event/state codes: https://github.com/Taskomater/Tasker-XML-Info/blob/master/Tasker_XML_Codes.md

Primary author examples supplement the user's export for XML constructs absent from that sample: https://gist.github.com/Raj9039852537/15bdb3f5be5e6901e901e5e16608bfe8 and https://gist.github.com/sbougerel/0cb4630962159ded1e7a703352dc9718

The recorded code list is older than the target Tasker version. Structural consistency and syntax checks do not prove import/runtime behavior. That phone check remains necessary.

## Hyundai library

Public package release selected for optional installation: https://pypi.org/project/hyundai-kia-connect-api/4.35.0/

Its documentation requires Python 3.12 or newer and maps region=3 to USA, brand=2 to Hyundai. Those numeric mappings are used, not the similarly named string constants.

VehicleManager source: https://github.com/Hyundai-Kia-Connect/hyundai_kia_connect_api/blob/master/hyundai_kia_connect_api/VehicleManager.py

Read blob SHA: 1788099627e978ea820dc1d29a4526928e98b444.

Vehicle model: https://github.com/Hyundai-Kia-Connect/hyundai_kia_connect_api/blob/master/hyundai_kia_connect_api/Vehicle.py

Read blob SHA: b8e98e551b587a2ea70fe5f9841711fda8b57eb8.

Constants: https://github.com/Hyundai-Kia-Connect/hyundai_kia_connect_api/blob/master/hyundai_kia_connect_api/const.py

Read blob SHA: 3f9fe5344d29fed31aa015ca7c534b587c1d3b86.

U.S. implementation: https://github.com/Hyundai-Kia-Connect/hyundai_kia_connect_api/blob/master/hyundai_kia_connect_api/HyundaiBlueLinkApiUSA.py

The reviewed default-branch source and selected PyPI release are distinct reference points. The dependency was not installed here because container package downloads failed. Fake-library contract tests cover intended calls, not the actual distribution or Hyundai service. Do not equate method existence with VIN-specific support.

## Termux

Official Termux:Boot instructions: https://github.com/termux/termux-boot/blob/master/README.md

Boot is optional and distinct from Termux:Tasker. This release includes manual foreground startup, not a boot installation or permanent wake lock.

## Enrollment comparison after the phone log

The inspected 0.3.0 export records HTTP 200 token responses followed by HTTP 502 C500 from getEnrollmentDetailsByUser with NO DATA FOUND TO PERFORM THIS OPERATION. Jon confirms the official MyHyundai app shows the car. Derived evidence is in verification/account-log-review.json; the private attachment is not published.

The pinned HyundaiBlueLinkApiUSA.py line 388 concatenates the entered username into /ac/v2/enrollment/details/, preserving @. Our existing request uses encodeURIComponent, producing %40. The separate primary BlueLinky implementation also preserves @ in getVehicles(): https://github.com/Hacksore/bluelinky/blob/master/src/controllers/american.controller.ts

RFC 3986 permits @ in a path segment and does not generally guarantee equivalence when reserved characters are percent-encoded: https://www.rfc-editor.org/rfc/rfc3986.html#section-2.2 and https://www.rfc-editor.org/rfc/rfc3986.html#section-3.3

0.3.1 therefore offered a manually triggered comparison that changed only %40 to @ using the same token and unchanged headers. Other delimiters stayed encoded. At that benchmark the comparison was a hypothesis, pending account evidence.

## Confirmed phone comparison, October 5, 2026

Jon's latest 0.3.1 export confirms the encoded form failed with C500, while the same-session literal-@ form returned one active vehicle. Cached status and refresh:true both returned HTTP 200. The status bodies and timestamp were identical, so a new vehicle observation is not established. No command ran. This is direct evidence for this account and session, not a general Hyundai API contract.

0.3.2 uses the successful form by default and retains a manual comparison after failure. Safe derived summaries and header-name-only recipes are in verification/account-log-review.json and verification/confirmed-api-recipes.json. Private account, vehicle and device identifiers from the supplied response are not republished.

## Lock/unlock and remembered accounts, October 5, 2026

Jon's 0.3.2 export records successful lock/unlock submissions using the existing telematics API host, empty HTTP 200 bodies and tmstid headers. Lock polling reached SUCCESS; the sole recorded unlock poll was PENDING. Jon separately reports both controls worked physically. The derived recipes preserve that distinction. Later cached responses contain newer vehicle timestamps; the earlier explicit refresh still returned an unchanged sample. Other controls remain unconfirmed.

The derived recipe file previously named owners.hyundaiusa.com in its base/Origin/Referer fields. That documentation error is corrected to the actual runtime and observed request origin, https://api.telematics.hyundaiusa.com. No working runtime endpoint was changed.

Jon explicitly requested remembered account settings. Android platform guidance controls the implementation: https://developer.android.com/privacy-and-security/keystore , https://developer.android.com/privacy-and-security/cryptography , https://developer.android.com/reference/android/util/AtomicFile and https://developer.android.com/reference/android/content/Context#getNoBackupFilesDir() . Email/password/PIN are encrypted natively; tokens remain memory-only. Remembering is available only in the installed app, with Forget and backup exclusion.

GitHub workflow context availability: https://docs.github.com/en/actions/reference/workflows-and-actions/contexts . The prior job-level runner.temp expression was invalid; runner is supported in step env. SDK initialization now follows the primary android-actions/setup-android v3 source: https://github.com/android-actions/setup-android/tree/v3 . Shell scripts are explicitly invoked with bash because the existing transport runner is stored without executable permission.

Cloud Android environment configuration follows https://developer.android.com/tools/variables and https://developer.android.com/tools/avdmanager . Explicit Android user/emulator/AVD directories and AVD path now agree; their actual discovery and API 35 boot succeeded in run 37262643353.

## Standalone Tasker, October 5, 2026

Jon reports that remote start and stop worked physically in the tester. His subsequent instructions explicitly authorize credentials saved in Tasker and require direct API calls without linking to the APK. This supersedes the earlier project-wide prohibition on ordinary Tasker credential variables for the standalone project only. The API Lab retains its encrypted native account store. No additional export was required for the user's start/stop confirmation.

The direct project ports the unchanged working diagnostic.js recipes, including literal-@ enrollment, existing public client headers, non-EV climate body vin=registration ID, lock/unlock APPCLOUD-VIN and REMOTE_POLL completion reads. The pinned primary upstream check_action_status uses a fixed polling delay. Its source does not establish a unit for the response's nextPollingInterval field, so the direct client uses a bounded fixed ten-second interval instead of guessing that unit.

Official Tasker Java Code help: https://tasker.joaoapps.com/userguide/en/help/ah_java_code.html . Its documented BeanShell action, variable/Java-object scope, bundled OkHttp3 and RxJava2 libraries, and doWithActivity helper control this implementation. The native dialogs finish their temporary Activity on every accepted/cancelled path. Request/response handling uses normal TLS, no redirects, no cookies/cache, no connection retry, a whole-call deadline and a bounded response. Primary OkHttp documentation: https://square.github.io/okhttp/features/calls/ and https://square.github.io/okhttp/3.x/okhttp/okhttp3/OkHttpClient.Builder.html .

Official Tasker trial download: https://tasker.joaoapps.com/download.html , linking https://tasker.joaoapps.com/releases/playstore/Tasker.6.6.20.apk . The downloaded trial has package net.dinglisch.android.taskerm and version 6.6.20; SHA256 a2e7623f0adec61e6726dfc7c5c0389e7a0193066d95c627155a03d7b35042ea. It is used only on an isolated Android 15 emulator and is not redistributed. The user's installed 6.7.6-beta is a distinct runtime and remains a phone acceptance check.

Observed standalone verification: official Tasker 6.6.20 on Android 15/API 35 passed 20 native checks in https://github.com/to-shreds/Misc/actions/runs/37286734266 , source 80e1073e7f11f9d6f8cf7fb8c53a788e3e5f0c48. This establishes actual imported action retention, unchanged delivered core source, native execution, masked inputs and saved synthetic account restoration after process restart. It does not establish live car operation on Jon's 6.7.6-beta.
