# Flowcraft / Power Automate builder

## Current state
Version 1.0.0 is implemented as a static browser application. It has nine recipes, 29 steps, four triggers, compact nested groups, reference and condition helpers, local project editing, and legacy ZIP import/export. It passed 40 Node tests and 29 Chromium interaction checks. The builder's own generated flows still need their first real Microsoft tenant import/run. Do not conflate prior SOS flow success with builder verification.

## Controlling sources
`to-shreds/Misc/PowerAutomate` owns implementation. `index.html`, `style.css`, `core.js`, `zip.js`, `app.js`, and `build.py` are canonical. `to-shreds/ProjectStatus/projects/power-automate-builder/STATUS.md` controls readiness and next steps. README.md links primary Microsoft specifications. TESTING.md defines actual test scope.

## Key decisions
- Native workflow JSON is authoritative; the UI is a view/editor, not a lossy intermediate flow format.
- Existing legacy single-flow packages can be opened. Unknown action fields and branches are preserved; graph reorder operations refuse unsupported dependency shapes.
- New packages use the five legacy members and fresh deployment resource IDs. Prompt IDs refer to existing models. No fake model IDs or guessed Solution serialization.
- Solution ZIP import and arbitrary Solution generation are intentionally unsupported in v1. Reject without discarding the user's canvas. Adding packaged prompts needs a separately tested future implementation.
- No backend, OAuth, account access, workflow execution, analytics, runtime CDN, or external fonts.
- Browser persistence is opt-in and plaintext. Import/export remains entirely local to the page.
- Shared variable collection defaults to sequential loops. Prevent initialization inside groups and unsafe concurrent writes.
- Friendly labels never rename internal action IDs, so references are stable.
- Root power-automate.html links here for Misc launcher discovery. Do not change Zork or Knotbook.

## Implementation map
- core.js: catalog, templates, dependency-preserving mutations, reference discovery, local checks, package maps and importer.
- zip.js: standard STORE writer, STORE/DEFLATE reader, integrity and resource-limit checks.
- app.js: DOM editor, compact tree, settings, dialogs, patterns, reference/condition helpers, save/load/undo, export review.
- style.css: responsive desktop/mobile UI.
- build.py: creates standalone.html from the canonical hosted files.
- tests/core.test.cjs and tests/browser.py: repeatable tests using only synthetic data.

## Do not break
Never silently change or drop unfamiliar imported settings. Never make local PASS mean tenant-validated. Never store client examples, tokens, or tenant exports in this public repo. Never execute imported expressions or allow email-preview scripts/network access. Do not add shared-variable writes to parallel recipes. Save project edits on failures, keep invalid input visibly blocked, and do not replace the canvas on a failed import. Do not regenerate the existing SOS automation or change its live settings.

## Remaining work
Verify the published page and then run a Hello recipe ZIP through an actual Microsoft tenant. Future extensions may include controlled Solution generation, richer schema-aware reference fields, and more connector templates. Current checks do not implement the complete WDL grammar, tenant schemas, dynamic message-size guarantees, or arbitrary connector authentication.

## Next action
Use the published builder, export Hello, automation, and import through My flows > Import > Import Package (Legacy). Capture the exact first Microsoft validation/run error if any. Patch the smallest relevant template and add regression coverage; do not restart or flatten the native graph.
