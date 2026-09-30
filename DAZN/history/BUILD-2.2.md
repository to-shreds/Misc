# NHL UK 2.2 local build report

Version: `2.2-login-test`, versionCode 4, package `app.nhluk`.
Readiness: IN PROGRESS pending real Android/Nord/DAZN verification.

## Changes

- Replaced the 2.1 selective-field login rendering with a dedicated `https://my.dazn.com/signin` preflight before every replay.
- Existing DAZN WebView session state is reused when DAZN accepts it; otherwise the actual first-party login page is shown.
- Login page media remains suppressed. NHL UK does not read, bridge, log or persist the DAZN password.
- When DAZN leaves the sign-in destination, the remote page is covered again and the exact selected replay/hub flow resumes under the guard.
- Manual `Sign in again` uses the same dedicated account path.
- Automatic Nord UK switching and all no-spoiler/resume protections remain.

## Verification

Final local run: 2026-09-29.

- 17 controller tests passed.
- 25 catalogue/no-spoiler tests passed.
- 26 offline Chromium tests passed, including usable full login page, media suppression, preflight auth completion and covered DAZN-home redirect.
- 13 automation/sign-in source checks passed.
- Native operand/register-flow checks passed across 84 methods.
- Binary APK/manifest/accessibility-resource/DEX checks passed.
- Independent APK Signature Scheme v2 verification passed with RSA-4096.
- JAR signature verification passed; self-signed/timestamp warnings are expected for this private build.
- Tampered APK was rejected.
- Certificate SHA-256: `ab3541f1f7b9bf41ab26f5951d4f2672cd71f7ca89678257250a6a9fd54d013a`.
- APK SHA-256: `3b78f1152312102c26b003047229e12e07e50fe4592955d06049fd28dd575a6e`.

## Not verified

Actual Android installation/ART, Samsung WebView behavior, live Nord switching, real DAZN email/password sign-in, DAZN persistent-session behavior, replay discovery, Widevine playback and an unwatched replay. Social-provider sign-in redirects are not verified. First acceptance test must use a game already watched.
