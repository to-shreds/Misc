# Zork I GUI handoff

## User requirement
Make a modern version of Zork; publish to `to-shreds/Misc`; remain perfectly faithful except for adding a GUI. We explicitly chose **Zork I**, not the original mainframe Dungeon or the whole trilogy. Repo was empty before this work. Version 1.0.1.

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

## Latest benchmark: root launcher and canonical game URL

The public Zork entry point is now root `zork.html`, not `zork/`. Root `index.html` no longer redirects into the split-source directory. It is a responsive Misc launcher that automatically discovers root-level standalone HTML pages and lists other root files/folders from the public GitHub repository. The embedded fallback always links Zork to `zork.html`.

## Latest benchmark: 1.0.1 persistent movement controls

Mobile now has a compact five-button movement strip in the always-visible command area: West, North, Look, South, East. The full Move panel remains available for diagonals, Up/Down, In/Out and the original compass. On wider layouts, the full movement section is sticky at the top of the sidebar so it cannot scroll out of view. These controls only submit the same original parser commands as before.

The split source and root standalone `zork.html` are synchronized. Browser regression coverage now asserts that the mobile quick-move control and all four cardinal arrows are visible in the viewport without sending a hidden game command. Static source verification passed after the change. The full Playwright/browser suite has not been rerun in this environment, so do not claim a new end-to-end browser pass for 1.0.1.

## Testing and continuation
Start with `node --test tests/engine.cjs`, then `python tests/browser.py`. CI artifacts include actual HTTP-browser results and screenshots. The whole 350-point adventure has **not** been exhaustively playtested; do not claim it has. See TESTING.md. Local visual development also used Chromium with in-memory assets because the development container disallows URL navigation; those visual checks alone are not a hosted deployment test.

## Publishing
All source and game assets belong in this repo, not scattered chat ZIPs. Root `zork.html` is the canonical public playable URL while the split files under `zork/` remain the editable source of truth. The intended hosted game address is `https://to-shreds.github.io/Misc/zork.html`. Root `index.html` is a launcher rather than a redirect: it features root-level standalone HTML pages, reads the public repository root to populate the current listing automatically, and falls back to a working Zork card if the GitHub API is unavailable. GitHub Pages serves main/root with `.nojekyll`. Do not claim a Pages URL was browser-verified unless it actually was. GitHub's connector can publish code but may lack administration permissions for first-time Pages enablement. A ready-to-open standalone HTML is the no-hosting fallback. No paid services.
