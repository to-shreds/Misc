# Flowcraft / Power Automate builder

## Current state
Version 1.1.0 is published at `to-shreds/Misc/PowerAutomate`. Source commit: `42903956dc4783452f35f5053864120ff2b3c8f2`. GitHub Pages build/deployment run `36026882766` completed successfully on 2026-09-24. The web tool could not independently fetch the hosted URL; do not describe this as a hosted-browser navigation test. The previous 1.0 bundled application passed 40 Node tests and 29 Chromium interaction checks. Version 1.1 changes presentation and template disclosure while preserving the same workflow engine; its source syntax and static UI hooks were rechecked, but the full Chromium suite has not yet been rerun after the visual simplification. The builder's own generated flows still need their first real Microsoft tenant import/run. Do not conflate prior SOS flow success with builder verification.

## Controlling sources
`to-shreds/Misc/PowerAutomate` owns implementation. `index.html`, `style.css`, `core.js`, `zip.js`, `app.js`, and `build.py` are canonical. `to-shreds/ProjectStatus/projects/power-automate-builder/STATUS.md` controls readiness and next steps. README.md links primary Microsoft specifications. TESTING.md defines actual test scope.

## Key decisions
- Native workflow JSON is authoritative; the UI is a view/editor, not a lossy intermediate flow format.
- Nine recipes, 29 step types, four triggers, compact nested groups, reference/condition helpers, project save/load, undo/redo, and legacy ZIP import/export are implemented.
- Version 1.1 simplifies the visible path: slate-blue/white theme, five common templates first, technical JSON hidden under More/Show technical settings, simpler labels, and reduced visual clutter.
- Existing legacy single-flow packages can be opened. Unknown action fields and branches are preserved; graph reorder operations refuse unsupported dependency shapes.
- New packages use the five legacy members and fresh deployment resource IDs. Prompt IDs refer to existing models. No fake model IDs or guessed Solution serialization.
- Solution ZIP import and arbitrary Solution generation are intentionally unsupported in v1. Reject without discarding the user's canvas. Adding packaged prompts needs a separately tested future implementation.
- No backend, OAuth, account access, workflow execution, analytics, runtime CDN, or external fonts.
- Browser persistence is opt-in and plaintext. Import/export remains entirely local to the page.
- Shared variable collection defaults to sequential loops. Prevent initialization inside groups and unsafe concurrent writes.
- Friendly labels never rename internal action IDs, so references are stable.
- Root power-automate.html links here for Misc launcher discovery. Zork, Knotbook, and the existing SOS automation were not changed.

## Implementation map
- core.js: catalog, templates, dependency-preserving mutations, reference discovery, local checks, package maps and importer.
- zip.js: standard STORE writer, STORE/DEFLATE reader, integrity and resource-limit checks.
- app.js: DOM editor, compact tree, settings, dialogs, patterns, reference/condition helpers, save/load/undo, export review.
- style.css: responsive desktop/mobile UI.
- build.py: creates standalone.html from the canonical hosted files.
- tests/core.test.cjs and tests/browser.py: repeatable tests using only synthetic data.

## Do not break
Never silently change or drop unfamiliar imported settings. Never make local PASS mean tenant-validated. Never store client examples, tokens, or tenant exports in this public repo. Never execute imported expressions or allow email-preview scripts/network access. Do not add shared-variable writes to parallel recipes. Save project edits on failures, keep invalid input visibly blocked, and do not replace the canvas on a failed import. Do not regenerate the existing SOS automation or change its live settings.

## Verification and artifacts
Version 1.1 preserves the workflow core and package format. JavaScript syntax, required static DOM hooks, the new version marker, template-disclosure state, and the slate-blue palette were rechecked after the UI update. The full browser suite should be rerun before treating 1.1 as fully regression-tested. Browser-generated ZIP integrity was independently checked with Python zipfile. STORE/DEFLATE imports and definition round trips passed. Browser tests used actual bundled HTML via Playwright set_content because URL navigation is blocked in the environment.

`Flowcraft.html` is the conversation's generated single-file offline build, SHA-256 `f5ada9547ab03ae69a596f24eaa3b9452705deca290b99e0855fcb12c34c7ab3`. Rebuild it from canonical source with `python build.py`. Do not hand-edit generated distributions. The downloadable source bundle excludes all private user exports and private test fixtures.

## Remaining work
Run a Hello recipe ZIP through an actual Microsoft tenant and confirm delivery. Current checks do not implement the complete WDL grammar, tenant schemas, dynamic message-size guarantees, or arbitrary connector authentication. Solution generation remains deliberately outside v1.

## Next action
Open `https://to-shreds.github.io/Misc/PowerAutomate/`, export Hello, automation, and import through My flows > Import > Import Package (Legacy). Capture the exact first Microsoft validation/run error if any. Patch the smallest relevant template and add regression coverage; do not restart or flatten the native graph.
