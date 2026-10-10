# Sports Desk handoff

Updated 2026-10-10T23:40:27.429Z. The code in `to-shreds/Misc`, directory `SportsDesk/`, controls implementation. `to-shreds/ProjectStatus/projects/sports-desk/STATUS.md` controls readiness and next steps.

## Goal and fixed choices

Independent GitHub-hosted HTML sports interface, editable Buffstreams source address, successful replacement of the saved event catalog, and no Render video bandwidth. Preserve all TorBox projects and other Misc applications. No scores or spoilers in the interface. No paid service or video relay. The user explicitly declined the paid Cloudflare relay on 2026-10-10; keep the free setup.

## Live entry points

- Page: https://to-shreds.github.io/Misc/SportsDesk/
- Helper: https://sports-desk-links.onrender.com
- Health: https://sports-desk-links.onrender.com/healthz
- Source: https://github.com/to-shreds/Misc/tree/main/SportsDesk
- GitHub Pages publishes `main`, repository root. The first live application commit is `d90eb3a4b67b86aa9f14eb97f7a1886cbd51d37c`; later documentation commits do not change that HTML.
- Published HTML SHA-256: `3015631e9a8c69122919defe8815815d303bd60383253152da45cf05e6523892`.

## Implemented behavior

- `index.html` is a single self-contained responsive page with search, sport/live/favorite filters, locally saved settings/list/favorites, bounded loading/errors, and source-time labels without an assumed timezone.
- The actual verified helper URL is preconfigured. Normal use needs no setup. Settings exposes the source website; the helper address sits in collapsed advanced settings.
- Settings > Website address > Check & save validates the new catalog before replacing the old source/list. Failed, empty, malformed, cancelled and superseded requests retain the previous list. A domain-only input is normalized to HTTPS.
- Watch resolves a current source player on demand and navigates to it as a top-level page. Back restores list/filter state. The source player owns playback; the helper never fetches player, playlist or video bytes.
- Lists older than five minutes are marked saved. Live markers are not represented as current after that threshold. Source times are not silently converted to local time.
- The parser supports numbered events, preseason/playoff categories, and named UFC/WWE/boxing events. A domain move with a supported layout works. A changed layout or player policy can still need adapter maintenance.
- `server.py` fetches only bounded public HTTPS HTML and returns JSON. It validates and pins public DNS, retains TLS hostname checking, validates redirects, rejects media MIME types before reading the body, and bounds time, bytes, concurrency, caching and requests. There is no arbitrary proxy endpoint.

## Render deployment and future updates

The user explicitly confirmed **My Workspace**, ID `tea-dakujgmk1f9s73d2v8ng`. The new separate service is `sports-desk-links`, ID `srv-db5cfgt9fdbs73ccm2l0`. Runtime python, plan free, region virginia, branch `feature/sports-desk`, automatic deployment off. Build: `python -m py_compile SportsDesk/server.py`. Start: `python SportsDesk/server.py`.

Initial live deployment: `dep-db5cfhd9fdbs73ccm480`, backend commit `c2946bdbc825d64c80953fc52101720fa753a584`. The running backend source is unchanged by the frontend connection and documentation commits. Do not create a duplicate helper. No existing Render service was modified.

Keep `feature/sports-desk` in sync with the approved SportsDesk changes on `main`. Because automatic deployment is off, a backend change needs an explicit deploy trigger after its code is committed on that branch. Frontend-only changes publish through the existing Pages workflow and need no Render deploy. Check current branch heads and preserve unrelated changes before updating either ref. A sleeping free helper may take about a minute to wake; the page permits 80 seconds before offering a retry.

## Verification

- 16 Python checks passed for URL/private DNS/redirect/media boundaries, score-free parsing, current layouts, player resolution without fetching the player, API flow and absent proxy routes.
- 7 JavaScript behavior checks passed, including a rerun after production configuration. They cover safe replacement, failures, late/cancelled requests, unsafe URLs and direct player navigation.
- Render Blueprint validated against the published JSON Schema.
- Render deployment is live. Health returned `html-metadata-only` and `mediaProxy: false`. Actual production transport and GitHub-origin CORS preflight passed.
- Initial production check discovered 47 events across nine sports and resolved college-football and MMA players. The final check read configuration from the delivered public page, discovered 46 events across nine sports at 2026-10-10T23:22:24Z, and resolved a current source player. Counts naturally change with the schedule.
- GitHub Pages workflow `38094714871` completed successfully. Public HTTP 200 HTML matched local source byte for byte. The publication commit changed only the nine new files within `SportsDesk/`.
- The source player previously played video in the cloud browser: 1280x720, readyState 4, paused false, approximately 19 seconds elapsed, no media error. Playback was stopped. This establishes one playback path, not every stream.

## Controlling playback constraints

The source homepage redirects to `/home6` and does not grant GitHub CORS reading access. Examined source players use `gooz.aapmains.net/new-stream-embed/...`; their iframe ancestor policy excludes GitHub. Playlists reject the GitHub context and source media does not grant the GitHub origin CORS access. A text-only manifest helper cannot make normal in-page HLS playback work under those policies. Use the verified top-level source-player approach unless new evidence changes this. Do not show source-page or video screenshots that reveal scores.

## Readiness and verification limits

The page is published and usable; no deployment blocker remains. Render and Pages HTTP/API checks passed. Full rendered UI acceptance and physical Android testing were not performed: the managed Sites environment lacked its required control-browser skill, so no alternate preview/browser-control path was improvised. JavaScript checks use a simulated DOM. Stream links can be offline, expired or changed by the provider; discovery is not an all-stream playback guarantee. Future source structure changes may require parser updates.

The current release still opens the source player directly and remains usable. The user's requested in-page playback remains unresolved under the current tested source restrictions. The user declined the proposed paid relay, so the free release stays unchanged. Update this handoff before finalizing ProjectStatus after future work.

## Embedded playback follow-up (2026-10-10T23:36:45.537Z)

The user asked to keep the stream inside Sports Desk. Normal iframe embedding remains blocked by the source player's frame-ancestors allowlist, which excludes GitHub. A native video element with only a text-playlist relay was investigated before implementation. It is not a reliable solution for current Android/Chrome.

Current source tests found nested HLS playlist HTTP 403 from the GitHub context and HTTP 200 from the original player context. One bounded 1KB Range GET to a signed R2 segment returned HTTP 206 and Content-Type text/plain, but no Access-Control-Allow-Origin for GitHub. Current Chromium HLS security code requires media/key/map resources either to allow the top frame origin through CORS or to share the actual media-playlist origin. Relaying the playlist through Render while media stays on R2 satisfies neither condition. Redirects are evaluated by their actual final origins. This is protocol/header and primary-source evidence, not a new browser playback test. Do not claim every Safari version is ruled out.

- Primary browser source: https://chromium.googlesource.com/chromium/src/+/HEAD/media/filters/hls_network_access_impl.cc (MediaSegmentSecurityChecks).
- A draft transport refactor was restored. Backend tests passed again (16). Server and frontend production source remain unchanged. No native-playback endpoint, new player UI, service or deployment was shipped.
- A separate Cloudflare Worker is a plausible media-relay architecture that would keep video off Render while retaining the GitHub frontend. It would relay actual video, not only playlist text. It is not implemented or verified.
- Workers Paid currently starts at $5 USD/month, includes 10 million requests and 30 million CPU milliseconds/month, and has no egress/bandwidth surcharge. Usage above included request/CPU allowances costs extra: https://developers.cloudflare.com/workers/platform/pricing/
- Cloudflare's CDN-specific terms require appropriate Paid Services for video delivery; a free video-relay entitlement has not been established. Developer Platform is explicitly listed as a Paid Service in the CDN clause, while Developer Platform terms govern Workers separately. Do not promise that Free Workers or Sites offers a verified free video relay.
- Terms: https://www.cloudflare.com/service-specific-terms-application-services/ and https://www.cloudflare.com/service-specific-terms-developer-platform/

Decision on 2026-10-10: the user declined the paid Cloudflare relay. Keep the existing free release with direct source-player navigation. Do not create a paid relay, alter billing, or repeat the cost question. Free embedded playback remains unresolved under the current tested restrictions; no further implementation or deployment is scheduled. Revisit only if the user requests another approach or a compatible source policy becomes available. Preserve the working page and the no-Render-video constraint.
