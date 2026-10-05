# Santa Fe handoff

## Current state

Browser-first API validation is the controlling next step. API Lab 0.2.1 is a static GitHub Pages page at `SantaFe/index.html`, with CSS, client code, and a scoped browser userscript transport. `santafe/index.html` redirects there. The existing 0.1.0 loopback bridge/simulator remains intact.

The user's first browser attempt showed "Browser helper not detected" and a failed direct response read. The required setup is now above the login form. Automatic/helper modes lock credentials and Connect until a helper handshake succeeds, and an Enter/programmatic submission cannot send a login without it. Automatic mode no longer falls back to a known-blocked direct request. Direct mode remains an explicitly selected diagnostic option. No account failure or real API capability was inferred from the screenshot.

The original Tasker project imported named tasks but zero actions on Tasker 6.7.6-beta. The generator now matches the native export's `sr`-first action attributes and lexicographic serialized child-slot ordering. It retains 40 tasks, 113 actions, 8 profiles and 10 scenes. This is a corrected import candidate, not a confirmed phone/runtime fix. The broken XML is archived in `tasker/archive/`.

## Controlling sources

Implementation: `to-shreds/Misc/SantaFe` on main. Readiness: `to-shreds/ProjectStatus/projects/santa-fe-control-center/STATUS.md`. Diagnostic request recipes: upstream Hyundai USA source at commit `82801884bdf619c5f2a35ff6bbae1693d2e1a3e8`, recorded in `docs/SOURCES.md` and `docs/verification/diagnostic-api-source.json`. Native Tasker shapes were checked against the private Regex export; only a synthetic sanitized fixture is committed.

## Completed

Static login/enrollment/cached-status testing; manual fresh-status requests; returned-field capability inspection; individual lock/unlock/climate/light/horn tests; manual transaction polling with per-command service types; automatic sanitized local logs and JSON export; restricted userscript transport; Tasker serialization correction and regressions; preservation of old XML and action payloads.

The unauthenticated login preflight returned HTTP 500 without CORS permission for the GitHub Pages origin. Ordinary HTML cannot read that response. The page links the helper and one-time Firefox/Tampermonkey setup. Requests go directly to Hyundai without a relay or saved credentials. Android extension behavior remains a real-device check.

## Verification

See `docs/TEST_REPORT.md` and `docs/verification/`. The current Python suite passes 92 tests. Diagnostic behavior is tested with explicit API fixtures; fixture success is never real-account verification. Actual phone import, real login, VIN-specific API support and physical vehicle operations have not been tested in this workspace.

## Do not break

Preserve the bridge, settings and unrelated projects. No credentials in git, Tasker XML/variables, local storage or exported logs. No public credential proxy, challenge bypass, guessed endpoints, cloud relay, Termux:Tasker, AutoInput or Shizuku dependency. Trim equipment does not prove API capability. Cached timestamps control freshness. Accepted requests are not physical completion; unknown outcomes must never cause automatic retry. Never publish the private native export or Hyundai APK.

## Unresolved / next action

Jon opens API Lab in Firefox, installs the linked helper once, enters credentials locally and downloads the sanitized log after read tests and any deliberate vehicle tests. Review that evidence before building live Tasker behavior. Separately import the corrected XML on Tasker 6.7.6-beta and verify visible actions, starting with SF Open. Scene sizing, Java runtime, permissions/background behavior and optional bridge dependency installation remain unverified.
