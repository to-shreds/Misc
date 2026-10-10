# Sports Desk handoff

Updated 2026-10-10. The code in `to-shreds/Misc`, branch `feature/sports-desk`, directory `SportsDesk/`, controls implementation. `to-shreds/ProjectStatus/projects/sports-desk/STATUS.md` controls readiness and next steps.

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

## Blocker and next action

Render `get_selected_workspace` returned `INVALID_ARGUMENT`, no selected workspace. Its instruction expressly requires asking the user which workspace to use, not choosing one automatically. `list_workspaces` returned one workspace: **My Workspace**, ID `tea-dakujgmk1f9s73d2v8ng`. No service was created or changed. Ask the user to confirm this workspace for the **new free metadata-only helper**. Do not work around this connector gate using another deployment path.

After confirmation, inspect current services to avoid duplicates, then create the separate `sports-desk-links` service from this prepared branch with runtime python, plan free, region virginia, build `python -m py_compile SportsDesk/server.py`, start `python SportsDesk/server.py`, auto-deploy no. Use the actual returned service URL. Verify health plus current catalog and player resolution. Set `DEFAULT_HELPER` in the HTML, preserving saved source behavior, then finish frontend visual/behavior QA and publish the new directory to main/GitHub Pages without replacing unrelated files. Update this handoff and ProjectStatus last.

## Verification limits

- `DEFAULT_HELPER` is intentionally empty; the HTML currently opens to setup, not a usable live catalog. A service URL has not been invented.
- No Render service, production helper URL, or live Pages publication exists for this project yet.
- Production pinned-DNS transport could not resolve external hosts in the execution environment (`Temporary failure in name resolution`). Ordinary environment HTTP could retrieve HTML for live parser/resolver probes. Actual Render runtime fetch is unverified until deployment.
- No full browser UI acceptance: the Sites managed environment had no control-browser skill, so no local browser-preview path was improvised. JavaScript tests check state/behavior in a simulated DOM. Physical Android remains untested.
- Domain moves with the current layout are handled; a changed site structure or player policy may still need adapter work. Discovery does not establish stream availability.
