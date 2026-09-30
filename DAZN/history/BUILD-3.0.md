# NHL UK 3.0 exact-game variant resolver

Version: `3.0-variant-resolver-test`, versionCode 12, package `app.nhluk`.
Readiness: **BLOCKED for unwatched hockey** until the real DAZN event route and first protected frame/audio are verified on-device.

## Confirmed device evidence

The 2.9 phone diagnostic was `stage=catalogue`, `code=ambiguous`. This establishes that the DAZN-origin catalogue transport succeeded and that more than one DAZN catalogue candidate passed the app's exact Sabres/opponent/date-time filter. It does not establish which provider variant would play, whether the candidates are alternate feeds/representations, or whether Widevine playback works.

## Change

3.0 treats multiple catalogue representations of the **same selected NHL game** as variants, not as different games. It still rejects different opponents, different dates, and candidates materially separated from the NHL scheduled start.

Resolution order is intentionally spoiler-safe:

1. Keep only exact team-pair candidates near the selected NHL scheduled time.
2. Prefer records carrying a provider start time nearest the NHL schedule over date-only records.
3. Prefer replay/VOD/catch-up representations when present. A live representation remains eligible only when no replay representation exists, preserving the guarded still-running DVR-from-start path.
4. Preserve explicit home/away groups when DAZN provides them and prefer the Sabres feed.
5. Otherwise merge the exact-game candidate routes into one covered route bundle. The protected player tries those exact-game variants one at a time behind the opaque/muted guard.
6. If a first exact-game variant is a short clip or lacks a from-start timeline, the app tries the next exact-game route while still covered instead of exposing the bad candidate or stopping immediately.
7. A saved resume route is preferred when it is still one of the resolved exact-game variants.

No score, result, provider description, thumbnail, total runtime, live edge, stream URL, token, license, account field, or credential is used for this choice or exposed to the user.

## Verification

Passed locally:

- 17 controller tests.
- 36 catalogue/no-spoiler tests, including a synthetic reproduction of the 2.9 ambiguous case with multiple exact DAZN search tiles.
- 27 offline Chromium player/shield tests, including covered fallback from an unsafe first exact-game variant.
- 31 authentication/discovery source-contract checks.
- 21 lifecycle/diagnostic checks.
- Native operand/register-flow checks across 120 methods.
- Binary APK/manifest/resources/DEX/accessibility checks.
- Independent RSA-4096 APK Signature Scheme v2 verification and tamper rejection.
- JAR signature verification. Self-signed-chain and absent-timestamp warnings are expected for this private build.

APK SHA-256: `44a5f6323261993b71dda2c6136b2949977d88b9c42f126d43bd74b90aca6c9b`.
Signing certificate SHA-256: `ab3541f1f7b9bf41ab26f5951d4f2672cd71f7ca89678257250a6a9fd54d013a`.
Private backup SHA-256: `6ea80e4249cc57f2bbacee9f372ea08507a4b3ece9bed9e451f692071a63a74d`.

## Limits

No Android emulator/device was available. DAZN's live UK catalogue response, the actual provider variant ordering, derived event routing, Widevine playback, and the first revealed frame/audio remain unverified. The local browser suite uses synthetic pages/video and does not establish production DAZN behavior.

## Next evidence

Install over 2.9 without clearing data. Retry the same previously watched game. The expected progression is catalogue match -> exact-game variant route -> protected player preparation. If another variant fails, the app should try the next exact-game route while covered. Use `Copy diagnostic` if the flow still stops. Do not use an unwatched game until the first frame/audio are proven spoiler-safe.
