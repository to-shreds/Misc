# NHL UK 2.7 local build report

Version: `2.7-direct-discovery-test`, versionCode 9, package `app.nhluk`.
Readiness: IN PROGRESS pending real Android/DAZN discovery and playback verification.

## Device finding that triggered this build

The 2.6 phone test displayed `DAZN catalogue lookup failed. Nothing was opened.` before any event route opened. Source inspection showed that 2.6 still called DAZN's search-discovery endpoint with `fetch()` from inside a `dazn.com` WebView page, leaving the request subject to browser cross-origin policy.

## Architecture change

- The protected DAZN player page no longer performs catalogue discovery.
- Trusted local controls construct a neutral matchup query.
- A separate 1x1 WebView, kept behind the native spoiler cover and carrying no Android Javascript interface, navigates directly to `https://search.discovery.indazn.com/v1/search?...&country=gb&brand=dazn`.
- The main-frame request includes normal `Accept`, `Referer: https://www.dazn.com/`, and `Origin: https://www.dazn.com` headers.
- Because the discovery endpoint is the page being loaded rather than a cross-origin script fetch, the 2.6 CORS failure path is removed.
- After load, local catalogue code parses the JSON document and returns only a sanitized candidate: neutral teams/date/start and opaque DAZN IDs/routes. Provider descriptions, images, scores, account data, cookies, authorization data, streams, manifests and licenses are not sent to the local controls.
- The protected DAZN WebView then receives the matched candidate and remains responsible only for authenticated player/DRM behavior.
- New neutral diagnostics distinguish network failure, HTTP rejection, invalid response, no exact match and ambiguity.

## Verification

Final local verification on 2026-09-30:

- 17 controller tests passed.
- 30 catalogue/no-spoiler tests passed.
- 26 offline Chromium player/shield tests passed.
- 26 automation/auth/discovery source checks passed.
- Native operand/register-flow checks passed across 116 methods.
- Binary APK/manifest/resources/DEX/accessibility checks passed.
- Independent APK Signature Scheme v2 verification passed with RSA-4096.
- JAR signature verification passed. Self-signed-chain/timestamp warnings are expected for this private build.
- Tampered APK was rejected.
- Signing certificate SHA-256: `ab3541f1f7b9bf41ab26f5951d4f2672cd71f7ca89678257250a6a9fd54d013a`.
- APK SHA-256: `7ba928c1c5126cacabdf49ebda12ef589e641ba47a5391b0f95767df1410d2c6`.
- Private backup SHA-256: `e02ae59b515731559384d7ccd6b921031ca4415fc08d45cd104b3656473a1d0a`.

## External design evidence

Current DAZN integrations continue to use DAZN discovery infrastructure directly. The current open-source DAZN Kodi client obtains service endpoints from DAZN startup services and uses discovery Event/Rail services. IPTV-org's DAZN integration uses `rail-router.discovery.indazn.com`, and another current DAZN catalogue navigator uses `search.discovery.indazn.com/v1/search` and `event.discovery.indazn.com/eu/v7/Event`.

These sources support the endpoint architecture, but they do not verify the actual UK response or Android WebView behavior on Jon's phone.

## Not verified

Actual Android installation/ART behavior, the live DAZN search-discovery response, UK catalogue matching, derived event routing, Widevine playback and the first revealed frame/audio. First acceptance testing must continue to use a game already watched.
