# Zork I GUI handoff

## User requirement
Make a modern version of Zork; publish to `to-shreds/Misc`; remain perfectly faithful except for adding a GUI. We explicitly chose **Zork I**, not the original mainframe Dungeon or the whole trilogy. Repo was empty before this work. Version 1.0.0.

## Architectural non-negotiables
Run the **unmodified** `zork/data/zork1.z3`, release 119 / 880429. SHA-256 `37084966477dff679282de42974b2077156b1bd68fad92a65d4ea94d8eb64d79`. The original game is MIT-licensed by Microsoft. The bundled MIT `ifvms.js` VM is also upstream-unmodified. Provenance in sources.lock.json.

No rewritten story, AI narration, approximate puzzles, changed routes, automatic solutions, resource rebalancing or softened deaths. No hidden UI probe commands. All gameplay actions go to the original parser. Preserve the full typed-command path.

## Implementation map
- `zork/engine.js`: small Glk presentation adapter, stream/file handling, original Z-machine VM ownership, read-only runtime access, command-prompt snapshots.
- `zork/view.js`: read-only presentation model, object visibility and containment, no injected commands. Inventory tracked via original object tree; player ID 44. See TESTING.md for conservative visibility limitations.
- `zork/data/objects.json`: parser nouns, synonyms, room/global-object relationships derived from the pinned original ZIL definitions and exact release's object table. Not a substitute game database. Do not expose this full data as hints.
- `zork/app.js`: DOM, commands, object selection, dialogs, theme/notes, six native Quetzal slots, restore validation, refresh resume, metadata/story loading. Also accepts the generated embedded asset object for single-file use.
- `zork/style.css` and `zork/index.html`: responsive interface, no remote fonts, frameworks or runtime CDN dependencies.
- `scripts/standalone.py`: generates root `zork.html` with scripts/style/story/metadata/licenses embedded; preserves split files as authoritative source.
- `scripts/vendor.py`: fetches pinned upstream revisions and bundles the unchanged VM with esbuild 0.25.0.
- `tests/engine.cjs`, `tests/browser.py`: repeatable regression checks.

## Persistence
Namespace `misc-zork119-v1:`. `slots` contains native Quetzal bytes plus presentation history/known nouns. `resume` contains a Quetzal memory+stack image at a line-input suspension point, read_data, io, VM RNG seed if seeded, and presentation history. `notes`, `preferences` separate. Native story saves and reload snapshots are deliberately different mechanisms. A reload must not perform LOOK/INVENTORY or advance a turn. Invalid imports are tested in a disposable VM before touching the live interpreter. Manual slots survive new game; reload-state does not. Storage failure falls back to portable save export.

## Testing and continuation
Start with `node --test tests/engine.cjs`, then `python tests/browser.py`. CI artifacts include actual HTTP-browser results and screenshots. The whole 350-point adventure has **not** been exhaustively playtested; do not claim it has. See TESTING.md. Local visual development also used Chromium with in-memory assets because the development container disallows URL navigation; those visual checks alone are not a hosted deployment test.

## Publishing
All source and game assets belong in this repo, not scattered chat ZIPs. Root `zork.html` is a convenience distribution, not the source of truth. GitHub Pages can serve main/root with `.nojekyll`; expected address after enablement is `https://to-shreds.github.io/Misc/zork/`. Do not claim a Pages URL is live without verifying. GitHub's connector can publish code but may lack administration permissions for first-time Pages enablement. A ready-to-open standalone HTML is the no-hosting fallback. No paid services.
