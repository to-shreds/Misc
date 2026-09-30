# NHL UK 2.4 local build report

Version: `2.4-auth-handoff-test`, versionCode 6, package `app.nhluk`.
Readiness: IN PROGRESS pending real Android/Nord/DAZN verification.

## Device finding that triggered this build

On 2.3, DAZN accepted the user's login but then displayed its main page instead of returning to the originally selected Sabres replay. Authentication itself was therefore working. The remaining failure was the post-login handoff.

## Fixes

- The dedicated Auth activity no longer relies only on WebView navigation callbacks to decide that authentication is complete.
- Auth now polls the trusted first-party DAZN URL and a minimal boolean DOM probe that asks only whether ordinary sign-in controls are still present. It does not read page text, scores, images, account information or credentials.
- A normal redirect to a non-auth DAZN URL completes immediately.
- If DAZN completes login as a single-page-app state change, repeated disappearance of login controls completes the handoff even when no conventional redirect callback fires.
- A bounded no-control fallback handles an already-valid DAZN session.
- Completion flushes DAZN's own WebView cookies and launches the protected Browser with the exact original selected-game configuration.
- If the protected Browser is ever redirected to a DAZN authentication URL, it returns to the dedicated Auth activity with the same game config rather than remaining on a covered dead page.
- Automatic Nord UK switching, spoiler protection, exact replay matching and elapsed-only resume are retained.

## Verification

Final local verification on 2026-09-30:

- 17 controller tests passed.
- 25 catalogue/no-spoiler tests passed.
- 26 offline Chromium player/shield tests passed.
- 20 automation/auth source checks passed.
- Native operand/register-flow checks passed across 99 methods.
- Binary manifest/resources/DEX/accessibility checks passed.
- Independent APK Signature Scheme v2 verification passed with RSA-4096.
- JAR signature verification passed. Self-signed-chain/timestamp warnings are expected for this private build.
- Tampered APK was rejected.
- Signing certificate SHA-256: `ab3541f1f7b9bf41ab26f5951d4f2672cd71f7ca89678257250a6a9fd54d013a`.
- APK SHA-256: `36f486201b2d8fb00cbf2f0a32d7e9d3b6988198dbda30535db802b51810b6aa`.

## Not verified

Actual Android installation/ART behavior, DAZN's production post-login SPA transition, live Nord automation, authenticated replay discovery, Widevine playback and an unwatched replay. First acceptance testing must continue to use a game already watched.
