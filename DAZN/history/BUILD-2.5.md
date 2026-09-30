# NHL UK 2.5 local build report

Version: `2.5-replay-discovery-test`, versionCode 7, package `app.nhluk`.
Readiness: IN PROGRESS pending real Android/DAZN playback verification.

## Device finding that triggered this build

On 2.4, the selected game reached the protected player but remained at `Finding this full replay`. The DAZN account action briefly opened the authentication activity and returned because the existing DAZN session was already valid. Authentication was not the active blocker.

## Fixes

- Automatic DAZN site search now actually submits the neutral matchup query instead of only populating the search field.
- Exact DAZN `/event/` or `/fixture/` pages for the correct teams/date can be replay candidates even when DAZN catalogue metadata omits an explicit `Full replay` label.
- Highlight, condensed, recap, reaction and similar content remains rejected.
- Playback safety remains independent of catalogue naming: the selected media is never revealed unless exactly one stable, finite, seekable >=1-hour timeline is available from the beginning and the requested frame is decoded and verified while covered/muted.
- Authentication no longer guesses success merely because login controls have not rendered. Disappearance-based SPA completion requires that sign-in controls were positively observed first.
- The misleading manual DAZN account button was removed from the protected player because authentication already runs automatically before each game.

## Verification

Final local verification on 2026-09-30:

- 17 controller tests passed.
- 25 catalogue/no-spoiler tests passed.
- 26 offline Chromium player/shield tests passed.
- 22 automation/auth source checks passed.
- Native operand/register-flow checks passed across 99 methods.
- Binary manifest/resources/DEX/accessibility checks passed.
- Independent APK Signature Scheme v2 verification passed with RSA-4096.
- JAR signature verification passed. Self-signed-chain/timestamp warnings are expected for this private build.
- Tampered APK was rejected.
- Signing certificate SHA-256 remains `ab3541f1f7b9bf41ab26f5951d4f2672cd71f7ca89678257250a6a9fd54d013a`.
- APK SHA-256: `ee418031ab5aa1946f43afcf3b43692eb0b19b34a4e034bb98b07455e0a771d7`.

## Not verified

Actual Android installation/ART behavior, DAZN production search submission, event matching, replay-player discovery, Widevine playback, and an unwatched replay. First acceptance testing must continue to use a game already watched.
