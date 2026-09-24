# Flowcraft / Power Automate builder

## Current state
Version 1.2.1 is published at `to-shreds/Misc/PowerAutomate`. The builder engine and package format are unchanged from 1.0/1.1, but the normal interface has been rewritten for nontechnical users. The visible flow now reads as ordinary instructions: choose what you want to happen, read the boxes from top to bottom, answer plain-English questions, and download the ZIP. Power Automate concepts such as variables, JSON, expressions, scopes, concurrency, and connector details are hidden unless the user deliberately opens more step types or technical options.

The previous 1.0 application passed 40 Node tests and 29 Chromium interaction checks. Version 1.2.1 has passed JavaScript syntax checks, required-DOM-hook checks, plain-language feature checks, and the browser-test source has been updated for the new labels. The full Chromium suite has not yet been rerun after the 1.2 beginner rewrite. The builder's own generated flows still need their first real Microsoft tenant import/run.

## Controlling sources
`to-shreds/Misc/PowerAutomate` owns implementation. `index.html`, `style.css`, `core.js`, `zip.js`, `app.js`, and `build.py` are canonical. `to-shreds/ProjectStatus/projects/power-automate-builder/STATUS.md` controls readiness and next steps. README.md links the primary Microsoft specifications. TESTING.md defines actual test scope.

## Key decisions
- Native workflow JSON is authoritative; the UI is a safer, simpler view/editor over it.
- Nine recipes, 29 step types, four triggers, nested loops/conditions/scopes, reference/condition helpers, project save/load, undo/redo, and legacy ZIP import/export remain implemented.
- The ordinary interface is beginner-first. It uses `1. What do you want to happen?`, `2. Put it together`, and `3. Download ZIP`.
- Common templates and common step types are shown first. Less common actions and the multi-step patterns are behind `Show more step types`.
- Normal labels use phrases such as `Send an email`, `Do this for every item`, `Make a yes or no decision`, `Use an earlier answer`, and `Who should receive the email?`.
- Conditions show a sentence-like summary plus a rule builder. Raw condition JSON is hidden unless technical mode is enabled.
- Common validation errors are translated into ordinary language.
- Existing legacy single-flow packages can still be opened. Unknown action fields and branches are preserved; graph reorder operations refuse unsupported dependency shapes.
- New packages use the five legacy package members and fresh deployment resource IDs. Prompt IDs refer to existing models. No fake model IDs or guessed Solution serialization.
- Solution ZIP import and arbitrary Solution generation remain deliberately unsupported in v1.x.
- No backend, OAuth, account access, workflow execution, analytics, runtime CDN, or external fonts.
- Browser persistence is opt-in and plaintext. Import/export remains local to the page.
- Shared-variable collection still defaults to sequential loops and preserves the safety checks from 1.0.
- Friendly display labels never rename internal action IDs, so references remain stable.

## Implementation map
- core.js: catalog, templates, dependency-preserving mutations, reference discovery, local checks, package maps and importer.
- zip.js: standard STORE writer, STORE/DEFLATE reader, integrity and resource-limit checks.
- app.js: beginner-facing copy layer, DOM editor, compact tree, settings, dialogs, reference/condition helpers, save/load/undo, export review.
- style.css: slate-blue/white responsive desktop/mobile UI.
- build.py: creates standalone.html from canonical hosted files.
- tests/core.test.cjs and tests/browser.py: repeatable tests using synthetic data only.

## Do not break
Never silently change or drop unfamiliar imported settings. Never make local PASS mean tenant-validated. Never store client examples, tokens, or tenant exports in this public repo. Never execute imported expressions or allow email-preview scripts/network access. Do not add shared-variable writes to parallel recipes. Save project edits on failures, keep invalid input visibly blocked, and do not replace the canvas on a failed import. Do not regenerate the existing SOS automation or change its live settings. Keep technical capability available without forcing technical vocabulary into the normal interface.

## Verification and artifacts
Version 1.2.1 passed JavaScript syntax and static DOM-hook checks after the beginner rewrite. Required copy/state markers, hidden-technical-step behavior, plain-language field mappings, condition-summary code, translated validation messages, and the updated browser-test labels were confirmed in source. The full Chromium/browser suite still needs a fresh run for 1.2.1. Browser-generated ZIP integrity, STORE/DEFLATE imports, and definition round trips were previously verified in 1.0 and the underlying package engine has not changed.

Earlier conversation `Flowcraft.html` and source-bundle attachments are stale relative to 1.2.1. Rebuild a fresh standalone from canonical source with `python build.py` if an offline artifact is needed.

## Remaining work
Run the full browser suite against 1.2.1, then run a Hello/test-email ZIP through an actual Microsoft tenant. Current checks do not implement the complete WDL grammar, tenant schemas, dynamic message-size guarantees, or arbitrary connector authentication. Solution generation remains deliberately outside v1.x.

## Next action
Open `https://to-shreds.github.io/Misc/PowerAutomate/`, try the beginner interface, then export `Send myself a test email` and import it through `My flows > Import > Import Package (Legacy)`. Capture the exact first Microsoft validation/run error if any. Patch the smallest relevant template and add regression coverage.
