# NHL UK 2.9 browser-fetch transport repair

Version: `2.9-browser-fetch-test`, versionCode 11, package `app.nhluk`.
Readiness: **BLOCKED for watching hockey** until real DAZN catalogue and playback work on-device.

## Confirmed device evidence

The 2.8 phone test returned `catalogue-http-403`. This establishes that DAZN's search-discovery endpoint was reached and rejected the top-level WebView navigation used by 2.8. It does not establish a login, matching, DRM or spoiler-guard failure.

## Change

2.9 no longer navigates the hidden WebView directly to the JSON API. A locally supplied HTML shell is intercepted at `https://www.dazn.com/nhl-uk-discovery-shell`. From that DAZN HTTPS origin, Chromium performs a normal CORS `fetch()` to the allowlisted `search.discovery.indazn.com/v1/search` request. The shell does not inherit DAZN-page CSP, has no Android Javascript interface, does not spoof the discovery WebView User-Agent, sends no credentials, and retains Chromium's own browser/TLS/network behavior. Raw catalogue data stays inside the hidden renderer; only sanitized exact-game matching output can advance to the protected player.

All existing Nord switching, authentication, exact-match, cover/mute, timeline, beginning/resume and decoded-frame checks remain mandatory.

## Verification

Passed locally:

- 17 controller tests.
- 30 catalogue/no-spoiler tests.
- 26 offline player/browser tests.
- 29 authentication/discovery source-contract checks.
- 21 lifecycle/diagnostic checks.
- Native operand/register checks across 120 methods.
- Binary manifest/resources/DEX/accessibility checks.
- Independent RSA-4096 APK Signature Scheme v2 verification and tamper rejection.
- JAR signature verification; self-signed-chain and absent-timestamp warnings are expected for this private build.

APK SHA-256: `23311079d242439c31bec1e24a8e316a22ed0ad859abc2fa5e73c37d6369918e`.
Signing certificate SHA-256: `ab3541f1f7b9bf41ab26f5951d4f2672cd71f7ca89678257250a6a9fd54d013a`.
Private backup SHA-256: `4a2670fe17f8c65fe0e290548660de038bbf512916e218781899fbf4bc387cf0`.

## Limits

No Android emulator/device was available. Host browser policy blocked DAZN-origin navigation even when request interception was configured, so the real browser-origin exchange could not be reproduced. The live DAZN endpoint was also inaccessible through the web tool. UK catalogue CORS/response behavior, route selection, Widevine playback and first revealed frame/audio remain unverified.

## Next evidence

Install over 2.8 without clearing data and retry a previously watched game. If catalogue access fails, use `Copy diagnostic`. Do not use an unwatched game until the actual first frame/audio are proven spoiler-safe.
