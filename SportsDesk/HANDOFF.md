# Sports Desk handoff

Updated 2026-10-10. The code in `to-shreds/Misc`, directory `SportsDesk/`, controls implementation. GitHub Pages publishes from `main`; the separate Render service follows `feature/sports-desk` with auto-deploy off. `to-shreds/ProjectStatus/projects/sports-desk/STATUS.md` controls readiness and next steps.

## Goal and fixed choices

Independent GitHub-hosted HTML sports interface, editable Buffstreams source address, automatic catalog replacement after successful validation, and no Render video bandwidth. Preserve all TorBox projects and all other Misc applications. No spoilers or scores in this interface. No paid service or video relay.

## Completed

- `index.html`: one self-contained file with responsive layout, live search, sport/live/favorite filters, persistent settings/list/favorites, time labels without an assumed source timezone, explicit loading/error states and bounded requests.
- `server.py`: public-HTML-only metadata discovery with pinned public DNS, HTTPS validation, redirect/size/time/concurrency/cache limits, safe JSON output and two bounded API endpoints. No media/player fetching and no generic proxy.
- Atomic source change: rejects failed/empty/bad replacement catalogs, preserves the previous list, cancels obsolete requests and prevents late overwrite.
- Watch resolves a current source player on demand and opens it as a top-level page. Back restores list/filter state. The source's player, rather than a custom embedded player, owns playback.
- `render.yaml`, requirements note, README, 16 passing Python tests and 7 passing JavaScript behavior tests. Render Blueprint validated against `https://render.com/schema/render.yaml.json`.
- Live parser check at 2026-10-10T23:10:59Z found 49 events across nine sports. Current college-football and MMA player links were resolved.

## Controlling playback evidence

Public homepage redirects to `/home6` and does not grant GitHub CORS reading access. The examined player uses `gooz.aapmains.net/new-stream-embed/...` and excludes GitHub in its iframe ancestor policy. Playlist requests from the GitHub context failed; source-player context succeeded. The examined R2 video segment allows the source-player origin but not GitHub. Thus a text-only manifest helper does not enable normal hls.js playback from GitHub.

The source player successfully played as a top-level page in the cloud browser: 1280x720, readyState 4, paused false, time approximately 19 seconds, no media error. The test was stopped. Do not show screenshots of source pages/video containing scores. This evidence proves one source-player path, not every event or a full app flow.

## Deployment in progress

The user explicitly confirmed **My Workspace**, ID `tea-dakujgmk1f9s73d2v8ng`. The separate new free helper is created and live. No existing Render service was changed.

- Service: `sports-desk-links`, ID `srv-db5cfgt9fdbs73ccm2l0`, URL `https://sports-desk-links.onrender.com`.
- Initial live deploy: `dep-db5cfhd9fdbs73ccm480`, commit `c2946bdbc825d64c80953fc52101720fa753a584`.
- Runtime python, plan free, region virginia, branch `feature/sports-desk`, automatic deployment off.
- Build `python -m py_compile SportsDesk/server.py`; start `python SportsDesk/server.py`.
- Production health, GitHub-origin CORS preflight, 47-event catalog across nine sports, and college-football/MMA player resolution all passed. The production pinned-DNS transport works.
- `DEFAULT_HELPER` now uses the actual verified URL. Advanced connection settings are collapsed; normal domain changes need only the website address.

Next: publish this new directory to `main` without replacing unrelated files, verify the actual GitHub Pages bytes and API flow, then update this handoff and ProjectStatus. Intended page: `https://to-shreds.github.io/Misc/SportsDesk/`.

## Verification limits

- GitHub Pages publication and delivered frontend verification are the remaining deployment checks.
- No full browser UI acceptance: the Sites managed environment had no control-browser skill, so no local browser-preview path was improvised. JavaScript tests check state/behavior in a simulated DOM. Physical Android remains untested.
- Domain moves with the current layout are handled; a changed site structure or player policy may still need adapter work. Discovery does not establish stream availability.
