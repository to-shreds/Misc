# Sports Desk

A separate, self-contained HTML event browser for GitHub Pages. Source: `SportsDesk/index.html`. It has no TorBox dependency, credentials, external JavaScript, or video relay.

The metadata helper is live at `https://sports-desk-links.onrender.com` and preconfigured in the HTML. GitHub Pages publication is in progress at `https://to-shreds.github.io/Misc/SportsDesk/`. No setup is needed to load the default source.

## How it works

1. Open the page to load the current Buffstreams event list. When its domain changes, choose **Settings**, paste the new website address, and choose **Check & save**. A domain-only input is normalized to HTTPS. The helper connection is already configured under advanced settings.
2. The helper reads HTML and returns a small event catalog. The browser only replaces its saved source and events after the complete new catalog passes validation. A failed, cancelled, empty, or superseded refresh preserves the previous settings/list.
3. Search, sport filters and favorites are saved locally. Scores, outcome descriptions, artwork, and source scripts are excluded. A saved list older than five minutes is labelled as saved; it is not presented as a current live list. Source times without timezone metadata are labelled as source times, never silently converted to local time.
4. Watch asks the helper to resolve the selected event's current player page, then navigates directly to that page. Use Back to restore the list. The helper does not fetch the player page, manifests, segments, or video, and it offers no arbitrary proxy endpoint.

Domain migration is supported when the site keeps its recognized event/player structure. The adapter covers numbered team-sport events, preseason/playoff category variants, and named UFC/WWE/boxing events. Other structures produce an explicit error rather than emptying the list. Finding a link is not a guarantee that a live stream is on air or playable.

## Why playback opens the source player

Actual October 10, 2026 tests found:

- The source's embedded player limits iframe ancestors and excludes `to-shreds.github.io`.
- Its HLS playlist returned 403 for a GitHub-origin request and 200 for its original player context.
- A media segment returned 200 but **no CORS allowance** for the GitHub origin, while the source-player origin received an explicit allowance.
- The original player opened as a top-level page and played at 1280x720, with video time advancing to approximately 19 seconds, `readyState=4`, and no media error.

Accordingly, this build intentionally uses top-level source-player navigation. It does not claim that an iframe or custom in-page HLS player works. The source controls its player, ads, availability, and later policy changes. No user media credentials are involved.

## Helper

Python 3.10+ standard library only. From the repository root:

```sh
python SportsDesk/server.py
```

- Binds to `0.0.0.0:$PORT`, default 10000.
- `GET /healthz` reports metadata-only mode.
- `POST /api/catalog` accepts `{"source":"https://mybuffstreams.plus/"}`.
- `POST /api/player` accepts the source and an `eventUrl` returned by the catalog.
- Public HTTPS only; credentials/custom ports/private DNS and unsafe redirects are rejected. Each request pins a validated public address while retaining normal TLS hostname verification. Ambient proxy settings are ignored.
- Only HTML content types are read. One-megabyte page cap, bounded redirects/timeouts, four upstream jobs, short bounded caches and request limits. API responses contain JSON only; source HTML is not forwarded to clients.

`SportsDesk/render.yaml` describes a **new free service**, with automatic deployment off because the parent repository contains unrelated applications. Build: `python -m py_compile SportsDesk/server.py`. Start: `python SportsDesk/server.py`. The Blueprint passed Render's published JSON Schema. The service was created with the native Render tool in the user-confirmed **My Workspace**, runtime `python`, plan `free`, region `virginia`, branch `feature/sports-desk`. Service ID: `srv-db5cfgt9fdbs73ccm2l0`.

Render deployment `dep-db5cfhd9fdbs73ccm480` is live at source commit `c2946bdbc825d64c80953fc52101720fa753a584`. Production health returned `html-metadata-only` and `mediaProxy: false`. A GitHub-origin CORS preflight, a 47-event catalog across nine sports, and college-football/MMA player resolution all succeeded. The HTML now uses the verified service URL. No paid plan or media relay is used. A sleeping free service can take about a minute to wake up; the page allows 80 seconds before offering a retry.

## Verification

```sh
python -m unittest discover -s SportsDesk/tests -v
node --test SportsDesk/tests/frontend.mjs
```

Current result: **16 Python tests and 7 JavaScript state/behavior tests passed**. Tests cover unsafe URL/DNS/redirect handling, rejecting media responses before reading the body, score removal, domain changes, player resolution without fetching the player, API flow, absent proxy route, invalid/failed/cancelled source replacement, late responses, and direct navigation.

Production Render discovery on October 10, 2026 returned **47 distinct events across nine sports** and resolved current college-football and MMA player links. This verifies the deployed pinned-DNS transport, HTML parser, API responses, and CORS. No full browser UI acceptance or physical-phone testing is claimed. UI behavior tests use a small simulated DOM, not a rendering engine.

See [HANDOFF.md](HANDOFF.md) and `to-shreds/ProjectStatus/projects/sports-desk/STATUS.md` for continuation.
