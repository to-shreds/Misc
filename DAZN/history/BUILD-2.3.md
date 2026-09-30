# NHL UK 2.3 local build report

Version: `2.3-auth-test`, versionCode 5, package `app.nhluk`.
Readiness: IN PROGRESS pending real Android/Nord/DAZN verification.

## Fixes in this benchmark

- Removed the failed 2.2 in-player DAZN authentication mechanism.
- Added a dedicated, non-exported `Auth` activity that runs after UK VPN verification and before the protected replay activity.
- Updated the DAZN login target to the current first-party `dazn.com/signin?return_to=...` pattern used by DAZN's own help-page Sign In links.
- The Auth WebView has JavaScript, DOM storage, first/third-party cookies and a Chrome-like user agent, but no Javascript bridge and no player/spoiler shield.
- The first main-frame navigation from a DAZN authentication destination to a non-authentication DAZN destination is intercepted before display; cookies are flushed and the protected replay activity launches.
- Player Options -> DAZN account now exits to the dedicated Auth activity instead of trying to expose login controls inside the protected player.
- Removed FLAG_SECURE from the test build so screenshots can be captured for debugging.
- Automatic Nord UK switching and all existing no-spoiler/resume protections remain.

## Verification

Final local run: 2026-09-29T23:41:42Z.

- 17 controller tests passed.
- 25 catalogue/no-spoiler tests passed.
- 26 offline Chromium player/shield tests passed.
- 18 automation/auth source checks passed.
- Native operand/register-flow checks passed across 95 methods.
- Binary AndroidManifest, resources and DEX checks passed, including isolated non-exported Auth and Browser activities.
- Independent APK Signature Scheme v2 verification passed with RSA-4096.
- JAR signature verification passed. Self-signed-chain/timestamp warnings are expected for this private build.
- Tampered APK was rejected.
- Certificate SHA-256: `ab3541f1f7b9bf41ab26f5951d4f2672cd71f7ca89678257250a6a9fd54d013a`.
- APK SHA-256: `d4133ea31ae24debc8f171ce338404c4ebe0ab4273377f1a1342cd29ba05068a`.

## Not verified

Android installation/ART behavior, the dedicated Auth activity on Jon's Samsung, live Nord UI automation, real DAZN email/password sign-in, DAZN session reuse, authenticated replay discovery, Widevine playback and an unwatched replay. First device acceptance must use a game already watched.
