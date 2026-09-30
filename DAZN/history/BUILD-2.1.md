# NHL UK 2.1 local build report

Version: `2.1-auto-test`, versionCode 3, package `app.nhluk`.
Readiness: IN PROGRESS pending real Android/Nord/DAZN verification.

## Fixes in this benchmark

1. Replaced the 2.0 manual `Open Nord` fallback with an app-initiated Nord country switch plus a narrowly scoped AccessibilityService that can approve the current confirmation UI and return automatically.
2. Added Android accessibility service metadata with `canRetrieveWindowContent=true`, required for active-window content retrieval.
3. Replaced `signin-not-found` as a terminal condition with a covered first-party DAZN account fallback and broadened trusted first-party DAZN subdomains.

## Verification

Final run: 2026-09-29T14:50:10Z.

- 17 controller tests passed.
- 25 catalogue/no-spoiler tests passed.
- 24 offline Chromium tests passed.
- 11 automation/sign-in source checks passed.
- Native operand/register-flow checks passed across 84 methods.
- Binary APK/manifest/accessibility-resource/DEX checks passed.
- Independent APK Signature Scheme v2 verification passed with RSA-4096.
- JAR signature verification passed. Self-signed chain/timestamp warnings are expected for this private build.
- Tampered APK was rejected.
- Certificate SHA-256: `ab3541f1f7b9bf41ab26f5951d4f2672cd71f7ca89678257250a6a9fd54d013a`.
- APK SHA-256: `be2cc0c68628d3dc73d69212963cdaa5065a7f5c3cc426e2ebde8d9f79ce4b76`.

## Not verified

Android installation/ART verification, Samsung restricted-settings behavior, live Nord accessibility UI, live public-exit switching, authenticated DAZN login, replay discovery, Widevine playback and an unwatched replay. First device acceptance must use a replay already watched.
