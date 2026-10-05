# Santa Fe API Lab for Android

Install [SantaFe-API-Lab-0.3.1.apk](https://to-shreds.github.io/Misc/SantaFe/android/dist/SantaFe-API-Lab-0.3.1.apk), open **Santa Fe API Lab**, and enter your MyHyundai email, password and four-digit Bluelink service PIN. Connect performs login, enrollment and cached-status reads. Use **Run read tests** for another read pass, then **Export log** to choose a JSON file location. If login succeeds but vehicle enrollment fails, tap **Test vehicle lookup** to make one read-only comparison using the same session. The comparison changes only the email path’s encoded `@` to literal `@`; it sends no password again and no vehicle command. A valid returned vehicle list enables that format for this session, then reads cached status when a vehicle is selected. Errors show the failed step and Hyundai’s error subcode/message. Export and share the sanitized log after the comparison. The original 0.3.0 release is preserved in `dist/`.

The same HTML tester is bundled inside the APK. It does not load the GitHub page, download scripts, require a browser extension, run a server, or use a cloud relay. Android 8 or later and a working system WebView are required. The app requests only Internet permission. Leaving the app, including opening the file picker, clears the login session; sanitized logs remain in the app.

Remote controls remain separate, manually confirmed tests. They operate the actual vehicle. Climate start requires outdoor acknowledgement. Cached timestamps control freshness; accepted HTTP requests do not establish physical completion. Unknown outcomes never cause an automatic retry. If the app closes during a command, check the car before acknowledging the warning and sending another command.

## Credential handling and restrictions

The bundled HTML and native transport necessarily handle account credentials. They send them only to the allowlisted Hyundai HTTPS host. Account details and session tokens are not deliberately persisted or placed in APK/source/logs. Password fields are cleared after login attempts. The WebView is destroyed and active requests are stopped when the activity leaves the foreground. A submitted car command cannot be recalled by closing the app.

The WebView loads only the three installed assets at a synthetic HTTPS origin. Other resources, navigation, frames, workers, browser network requests, file/content access, popups and permission requests are blocked. The native bridge validates destinations, methods, headers, lengths and request bodies before opening a connection. HTTPS uses Android's normal system CA trust and hostname verification. Redirect following, HTTP caching and cookie sessions are disabled. POST bodies use fixed-length streaming; there is no application retry loop. No custom trust manager or certificate-validation bypass is present.

Only sanitized logs and a minimal unresolved-command marker are stored locally. Backup and device transfer are excluded. Autofill and WebView form restoration are disabled. Screenshots and recent-app previews are blocked because the page displays account and vehicle information. Log exports use Android's document picker with no broad storage permission. A destination chosen there may be a cloud provider; choose local Downloads if you want the exported log to stay on the phone.

These controls and fixture tests reduce the trust surface. They are not an independent security audit or proof of a successful Hyundai account login. Device software, keyboard, installed services, VPN and Hyundai remain part of the environment. Real-account responses and vehicle-specific support still need to be tested by the account holder.

## Build and maintain

`./build.sh` compiles directly with JDK 17, Android platform 35 and build-tools 35.0.0. Set `ANDROID_SDK_ROOT` to an existing SDK. `tools/provision-sdk.sh` obtains build tools from the official Google repository and verifies pinned SHA-256 values. No Gradle/Maven or runtime third-party dependencies are needed. HTML/JavaScript/CSS are copied from the parent SantaFe folder into the APK during the build, so there is one canonical tester implementation.

Application ID: `com.jon.santafelab`. Version: `0.3.1`, version code `4`, min SDK `26`, target SDK `35`.

Restore the existing private signing identity before rebuilding updates. It is preserved privately as **SantaFe-API-Lab-private-update-key.zip** in Jon's files. Set `SF_SIGNING_DIR` to its extracted private directory. Never put that archive, its keystore or its password in git. The public signing-certificate SHA-256 is `90ba911d370453290457ea862344a7cbc4aca6f6f49931725b76a91b599c952d`.

Verification results are in `../docs/verification/android-verification.json` and `../docs/TEST_REPORT.md`. Account and physical vehicle tests must not be performed by synthetic instrumentation. Preserve the older Tasker project, simulator, bridge and userscript; those are separate from this Android tester.

Platform references: [WebView bridge restrictions](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges), [HTTP streaming and redirect behavior](https://developer.android.com/reference/java/net/HttpURLConnection), [document picker](https://developer.android.com/training/data-storage/shared/documents-files).
