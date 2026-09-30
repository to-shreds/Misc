# NHL UK 2.6 local build report

Version: `2.6-backend-discovery-test`, versionCode 8, package `app.nhluk`.
Readiness: IN PROGRESS pending real Android/DAZN discovery and playback verification.

## Device finding that triggered this build

The 2.5 phone test reached the protected player and ended at `Could not confirm this full replay`. VPN switching and DAZN authentication were already past the failure point. The remaining architecture still depended on searching and scraping DAZN's hidden visual site.

## Architecture change

- New starts no longer use the visible DAZN search UI or stale pre-2.6 cached webpage links.
- The covered DAZN WebView queries `https://search.discovery.indazn.com/v1/search` with a neutral team-matchup query, `country=gb`, and `brand=dazn`.
- Discovery results are reduced to neutral teams/date/start plus opaque DAZN IDs and safe content type. Raw provider descriptions, images, scores, account data, cookies and authorization material are not returned to the local controls.
- Exact matches are resolved by the selected NHL schedule record, not by page text.
- Covered fixture routes are derived from DAZN's `ArticleNavigateTo`, `ArticleNavParams`, `EventId` and `AssetId` fields. A bounded set of opaque route variants may be tried while the native cover remains visible.
- DAZN's own authenticated page/player still handles DRM playback. This build does not extract streams, licenses or playback tokens.
- Manual webpage-discovery controls were removed from the protected player.

## Still-running game support

A still-running event may be started from the beginning only when the DAZN player exposes a seekable DVR range beginning at zero with at least one hour available. Picture and sound remain covered/muted while the app forces the requested position and verifies a decoded frame. Infinite/live duration by itself is never accepted, and there is no fallback to the live edge.

## Verification

Final local verification on 2026-09-30:

- 17 controller tests passed.
- 30 catalogue/no-spoiler tests passed.
- 27 offline Chromium player/discovery tests passed.
- 25 automation/auth source checks passed.
- Native operand/register-flow checks passed across 99 methods.
- Binary AndroidManifest, resources, DEX and accessibility checks passed.
- Independent APK Signature Scheme v2 verification passed with RSA-4096.
- JAR signature verification passed. Self-signed-chain/timestamp warnings are expected for this private build.
- Tampered APK was rejected.
- Signing certificate SHA-256: `ab3541f1f7b9bf41ab26f5951d4f2672cd71f7ca89678257250a6a9fd54d013a`.
- APK SHA-256: `e3f116a6875436e212271c6fc44c16d6acb383fa6dd9d5b02a662a45b79fc785`.
- Private backup SHA-256: `b585a10651a737cbd29ae075443547d6b187cdb1454bf1817a2b17bee89d2be5`.

## External design evidence

Current 2026 open-source DAZN clients use DAZN discovery infrastructure directly. The active `herrnst/plugin.video.dazn` client obtains service endpoints from DAZN startup services and uses discovery Event/Rail services. IPTV-org's DAZN integration uses `rail-router.discovery.indazn.com/eu/v10/Rail`. Another current DAZN navigator uses `search.discovery.indazn.com/v1/search` for catalogue search and `event.discovery.indazn.com/eu/v7/Event` for event details.

These sources support the backend-discovery architecture, but they do not verify the exact UK response or route behavior on Jon's phone.

## Not verified

Actual Android installation/ART behavior, DAZN production CORS/CSP behavior for the discovery request, UK search results for the selected game, opaque fixture routing, Widevine playback, and the first revealed frame/audio on the phone. First acceptance testing must continue to use a game already watched.
