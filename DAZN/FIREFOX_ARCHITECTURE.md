# Firefox implementation architecture

The controlling scope remains `CODEX_FIREFOX_HANDOFF.md`. The implementation is under `firefox-extension/`; the legacy Android project remains untouched.

## Boundaries

`manifest.json` targets Firefox desktop and Android with MV3 background scripts, a stable Gecko ID, local storage, four explicit HTTPS host permissions and no cookie permission. Firefox 142 is the minimum across both platforms. There is no server and no stream proxy.

The MAIN-world `page-guard.js` patches only safety boundaries that isolated DOM observers cannot enforce synchronously: hidden HTML-media muting, exposed source/position changes, SPA navigation, native media controls, Media Session writes, fullscreen/PiP, and unguarded embedded content. It receives no credentials or provider data. Page code can see this code; it is not a hostile-page security boundary.

The USER-origin document-start stylesheet hides provider descendants before extension JS runs. The isolated `spoiler-shield.js` maintains the cover, selected video identity and neutral title. It never moves the provider video or removes its ancestors from layout. Intentional `cover()` calls are silent; unexpected invalidations notify the controller. This distinction prevents protection from cancelling the player's own preparation.

`app-shell.js` owns a closed shadow tree directly under the document element. All dynamic text comes from neutral, allowlisted records. Only constant markup uses `innerHTML`. The actual interface is inside the tab on both platforms; the toolbar action only opens DAZN.

## Data boundaries

The background fetches the official NHL season schedule. `schedule.js` immediately copies only game ID, game date, scheduled UTC start and team abbreviations. It drops postseason rows to avoid future-opponent disclosure and rejects conflicting identities.

`transport.js` calls DAZN structured search with neutral bundled team names. Its default fetch runs in the DAZN content-script/page-origin context; the user may explicitly choose the background alternative. The same `catalogue.js` matcher sanitizes both. Neither path forwards raw responses into the UI, diagnostics or storage.

Matching requires exactly the selected opponent pair and a scheduled start within the narrow time window on the correct game date. It excludes highlight/recap/clip labels and live/linear variants. The event/asset route comes from DAZN's own deployed router declaration. `reports/CATALOGUE-EVIDENCE-2026-10-02.md` records the current source evidence. Search shape, routes and protected playback are separate facts.

Pending selections are isolated by tab in `browser.storage.session`, expire after one hour, and permit only two redirect attempts. Resume records use `browser.storage.local`. Exact route, event, variant and feed identity are required for automatic resume; equal event IDs alone do not prove equal media timelines.

## Player transaction

`player-controller.js` is independent of DOM selection and receives a shield adapter. Each preparation attempt has a generation, a deadline and bounded source/seek retries. It tolerates provider seeks during covered preparation, waits for a seekable target and independently observes consecutive decoded frames. Timers enforce deadlines and acquire media; timers alone never establish readiness.

Ready stays covered and paused. Explicit Play revalidates source, target and timeline, starts while muted, observes decoded frames again, then permits reveal and audio. Replacement media, source changes, exposed seeks, unexpected autoplay and ended content close the cover. Old callbacks and promises cannot authorize a new generation. There is no duration-based target calculation or live-edge fallback.

## Acceptance boundary

Synthetic tests validate the implemented rules and actual Firefox integration where recorded. They do not establish a safe first DAZN DRM frame, correct real broadcast identity, Windows media surfaces, Firefox Android playback, or Nord restoration. Those must be tested with a previously watched game. The launcher milestone remains gated on completed-replay acceptance.
