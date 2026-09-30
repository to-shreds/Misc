# NHL UK 2.8 diagnostic repair

Version: `2.8-diagnostic-test`, versionCode 10, package `app.nhluk`.
Readiness: **BLOCKED for watching hockey**. Actual DAZN lookup, routing and playback are not established.

## Reproduced evidence

The latest 2.7 screenshot displayed `Protection could not be verified. Playback stays covered.` This does not establish that catalogue discovery succeeded, that a player loaded, or that a protection hook failed.

The 2.7 source started `Browser.run()` on resume before the remote player had loaded. That poll returned `shield:false` from an empty WebView. The controls translated every such result into the generic protection message, including during catalogue lookup and after a catalogue failure.

The new regression test reproduces the exact message with the unmodified 2.7 controls: a simulated catalogue HTTP error is immediately replaced when the empty-player callback arrives. The HTTP error is a test input, NOT evidence of the user's actual DAZN response. Prior confident conclusions about CORS or successful catalogue matching were not established by the screenshots.

## Targeted changes

- Native polling requires an actual player navigation and loaded document, and stops after page failure. Catalogue errors/timeouts remain visible and late callbacks cannot reopen the flow.
- Catalogue/player transport errors retain HTTP status or WebView error numbers. Guard initialization, media hooks, metadata hooks, configuration, origin, runtime and malformed-result errors have separate neutral codes.
- User-initiated `Copy diagnostic` copies only a fixed schema: build, stage, allowlisted error code/category, Android API, WebView version, and up to 12 distinct stage events. It excludes scores, page text, URLs, game IDs, titles, video timing, credentials, cookies/tokens, account details and IP addresses. Nothing is copied until the user taps the button.
- Existing resolved candidates load their own selected URL rather than the generic NHL hub; invalid cached configuration fails closed.
- No new reveal path or browser fallback was added. Nord, authentication and storage native classes, catalogue/controller/dashboard assets, and prepare/play/skip frame/audio/seek checks are unchanged and were compared with 2.7.

## Executed checks

17 controller, 30 catalogue, 26 existing offline browser, 26 source-contract, and 20 new lifecycle/diagnostic checks passed. Native source operand/register checks passed across 120 methods. Package/manifest/DEX/resources/annotations, alignment, independent RSA-4096 APK v2 signature verification, tamper rejection, JAR verification, packaged-asset equality and signing-identity comparisons passed. No private key is present in the APK.

New checks include the 2.7 negative reproduction, pending/failed status retention, late-callback rejection, HTTP/WebView codes, report data filtering, injected guard faults, cached-candidate preparation and interpretation of the native pre-poll branch.

## Limits

Browser navigation, including routed fixture URLs, was rejected by host policy with ERR_BLOCKED_BY_ADMINISTRATOR. Tests therefore use in-memory HTML, CSP removal for fixture delivery, a synthetic lexical location and a silent fixture video with a synthetic long duration. Native branch interpretation is not Android/ART. Actual origin/CSP/asset behavior, native clipboard and cover behavior, Nord, DAZN authentication/catalogue/routing, Widevine and real playback remain untested.

Container requests failed external DNS and the web tool could not access the live catalogue endpoint. Those environment failures do not diagnose the user's connection. No actual UK catalogue response or authenticated DAZN player was obtained.

## Deliverables

- `NHL-UK-2.8-diagnostic.apk`, 126,141 bytes. SHA-256: `839e014ef70f4b06e7a60cea96178944577e0a752a5d27d6edc0fe7fd5676c3c`.
- `NHL-UK-2.8-private-update-backup.zip`, 311,596 bytes. SHA-256: `48b1d87954aaae9e04f5d49904c94d2c83274d6426dfa977c4ed27b9a63ca710`. Source, tests, reports, HANDOFF, APK and private update-signing key. Keep private.
- Signing certificate SHA-256: `ab3541f1f7b9bf41ab26f5951d4f2672cd71f7ca89678257250a6a9fd54d013a`.

## Next evidence

Install over 2.7 without clearing data. Select a previously watched game, let the stage finish or time out, then obtain Copy diagnostic. Use that actual status before modifying endpoints, authentication or playback again. This is not a verified-working player or a build verified safe for an unwatched game.

Recorded: 2026-09-30T03:23:22+00:00.
