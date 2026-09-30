# NHL UK 3.1 multi-search repair

Version: `3.1-multi-search-test`, versionCode 13, package `app.nhluk`.
Readiness: **BLOCKED for unwatched hockey** until real event routing and protected playback are verified.

## Confirmed device evidence

The 3.0 phone diagnostic returned `stage=catalogue`, `code=catalogue-unavailable` for Columbus at Buffalo.

Source inspection showed that `Replay.match()` can return `status:'none'`, but 3.0 did not recognize that status in the UI and replaced it with the generic `catalogue-unavailable` code. Therefore the screenshot did not prove transport failure. It was consistent with a successful DAZN response that contained no candidate recognized by the matcher.

The same review found a concrete title-matching gap: provider titles using only locations such as `Columbus @ Buffalo` were not recognized, even though full names, abbreviations and mascot words were.

## Change

- Added bundled neutral NHL aliases for team locations and common mascot forms.
- `Columbus @ Buffalo`, `CBJ @ BUF`, full-name and mascot-name forms normalize to the same CBJ/BUF pair.
- Added at most six neutral DAZN search variants per game: full names in both orders, locations, mascots, Sabres-only and opponent-only.
- If a valid catalogue response produces no exact game, the app now reports `not-matched` accurately and automatically tries the next neutral query.
- Different opponents, different dates and materially different start times still fail closed.
- The working 2.9 browser-origin catalogue transport, 3.0 same-game variant resolver, Nord flow, authentication, cover/mute, timeline, resume and fixed diagnostic schema remain unchanged.

## Verification

Passed locally:

- 17 controller tests.
- 39 catalogue/no-spoiler tests, including city-only `Columbus @ Buffalo`, fallback-query generation and Rangers/Islanders disambiguation.
- 27 offline Chromium player/shield tests.
- 31 authentication/discovery source-contract checks.
- 22 lifecycle/diagnostic checks, including automatic retry after a matcher `none` result.
- Native operand/register checks across 120 methods.
- Binary APK/manifest/resources/DEX/accessibility checks.
- Independent RSA-4096 APK Signature Scheme v2 verification and tamper rejection.
- JAR signature verification. Self-signed-chain and absent-timestamp warnings are expected for this private build.

APK SHA-256: `05c56f4e9815ff500c5619dc4129201b8db8c2d67b79bb5f3bd2abb072ca6361`.
Private backup SHA-256: `be3772613c6eb4606dd83c4dff87444aa56c97189acd804225284502188a74dc`.

## Limits

No Android emulator/device was available. The live Columbus/Buffalo DAZN response, event routing, Widevine playback and the first revealed frame/audio remain unverified. This build fixes confirmed status-label and query/title robustness defects; it does not establish that DAZN currently exposes the selected game to the UK catalogue.

## Next evidence

Install over 3.0 without clearing data and retry the same previously watched Columbus/Buffalo game. If all neutral searches fail, the final diagnostic should be `not-matched`, not `catalogue-unavailable`. If a match is found, the next meaningful evidence is the event/player-stage diagnostic.
