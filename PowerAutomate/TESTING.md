# Verification for Flowcraft 1.0.0

## Results

On 2026-09-24, **40 Node tests** passed and **29 Chromium interaction checks** passed. There were no failing tests or uncaught browser errors in that run. The browser-generated ZIP passed Python zipfile's independent CRC verification and contained the expected five legacy package files.

Node tests cover all nine recipes, export/import round trips, preservation of unfamiliar actions and native workflow settings, connector remapping, missing and forward references, cycles, initialization and variable types, concurrent loop writes, append self-reference, loop-scoped outputs, reference-picker availability, safe movement/deletion/duplication, conditions, schedules, schema generation, expression delimiter checks, Solution rejection, unsafe object keys, ZIP corruption/encryption/traversal/duplicates, and the standard CRC32 test vector.

Browser tests cover required-field editing, blocked exports, stale-error removal, a sandboxed email preview, real download creation, STORE and independent DEFLATE import, project save/load, reference insertion into JSON-mode fields, multi-step patterns, undo/redo, condition helpers, friendly schedule controls, invalid-edit preservation, unsupported Solution rejection, HTML injection prevention, no runtime data requests, and mobile layout/navigation.

A real native legacy export supplied earlier in the conversation was also imported locally: all four action definitions were preserved exactly. That private fixture is not published. Testing it exposed optional Outlook-folder and loop-concurrency defaults; those are now handled without false missing-field errors and have a synthetic regression test.

## What these results do not prove

The test environment prevents URL navigation in Chromium. Browser checks therefore load the actual built standalone HTML with Playwright's `set_content`, not a fabricated hosted URL. This is a real browser interaction test but **not a hosted-site browser navigation test**.

There has been **no Microsoft tenant import or execution of a flow newly exported by Flowcraft**. The earlier SOS automation successfully ran in the user's tenant, but that is not a test of this builder. Package structure is modeled on actual exports and checked locally. Connector parameter acceptance, prompt execution, permissions, licensing, service throttling, and runtime correctness still require an import and live test.

Static expression validation checks delimiter/quote structure and selected references, not the complete WDL grammar or function semantics. Email previews do not evaluate dynamic actions. Optional browser storage is not encrypted. Complex imported settings are preserved, not automatically understood or certified.

## Repeat

From `PowerAutomate`:

```sh
python build.py
node --test tests/core.test.cjs
python tests/browser.py
```

The browser test requires the Python `playwright` package and a Chromium executable at `/usr/bin/chromium`. Adjust the launch path to your installed Chromium when running elsewhere. It writes screenshots, a report, and synthetic ZIP/project fixtures under `tests/`. No client data is required.

## First tenant test

Use the Hello, automation recipe with your own recipient address. Import the generated ZIP using **Import Package (Legacy)**, map Outlook, inspect the flow, and run its manual trigger. Confirm the email arrives. Then test a sequential loop and an existing saved AI prompt separately. Keep the original SOS flow unchanged while testing this independent tool.
