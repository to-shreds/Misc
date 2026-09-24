# Misc

## Zork I: The Great Underground Empire, modern interface

**The original game, not a remake of its rules.** This runs the unmodified Infocom Zork I release 119 / serial 880429 in the MIT-licensed ifvms.js Z-machine. The modern interface only handles presentation, input and browser storage.

### Play

Download **[zork.html](zork.html)** using GitHub's Download raw file button, then open it in a browser. Everything required for gameplay is embedded in that one file. No installation, account, API key or game download is required.

The split-file website is **[zork/index.html](zork/index.html)**. When GitHub Pages is enabled for this repository, its address is `https://to-shreds.github.io/Misc/zork/`. GitHub Pages setup: Settings → Pages → Deploy from a branch → main → / (root). Do not treat that address as live until Pages has been enabled and its deployment succeeds.

For a local web server, from the repository root:

```sh
python -m http.server 8000
```

Then visit `http://localhost:8000/zork/`. Do not double-click the split-file `zork/index.html`; use `zork.html` for direct opening.

### What the GUI adds

A responsive reading pane, clickable compass, visible-object and inventory cards, general action buttons, a multi-object command builder, the complete original text-command field, six save slots with Quetzal export/import, refresh recovery, personal notes, transcript export, reading-size controls, reduced-motion support, and dark/paper themes. The decorative scene header is a visual motif, not a literal diagram or puzzle hint.

### Fidelity contract

The story is byte-for-byte unchanged and checked against a pinned SHA-256 before play when the browser provides Web Crypto. The source lock and automated tests independently verify it. The original program owns room connections, descriptions, puzzles, score, combat, randomness, carrying limits, timers, deaths, softlocks and the ending. No LLM or substitute rules engine runs the game.

Selecting items or opening a panel never injects LOOK, INVENTORY, WAIT or another hidden turn. A compass/action click sends an ordinary parser command, shown in the transcript. All compass directions remain available; the GUI does not label valid exits. Closed opaque containers, invisible objects and dark rooms do not expose their contents through the item cards. Typing remains the complete fallback for everything the original parser supports. Graphical controls are conveniences, not an exhaustive list of valid language.

SAVE and RESTORE run the original save/restore opcodes with a browser file dialog adapter. Automatic reload recovery is a separate interpreter-level resume point at a command prompt, not an added in-game undo button. Browser storage can be cleared or denied, so export important manual saves.

### Files and maintenance

`zork/` is canonical editable source; `zork.html` is its generated, directly-openable distribution. Do not hand-edit the distribution. Rebuild it with `python scripts/standalone.py` after source changes. `sources.lock.json` pins the story, interpreter and bundler. Upstream files are committed locally: routine gameplay and tests never fetch a CDN. `python scripts/vendor.py` rebuilds the pinned vendor snapshot, requiring Internet, git and Node/npm.

Run `node --test tests/engine.cjs` for engine/fidelity checks. For browser tests, install `playwright==1.56.0`, run `playwright install chromium`, then `python tests/browser.py`. CI runs both suites and publishes screenshots plus a downloadable site artifact.

Read **[HANDOFF.md](HANDOFF.md)** before continuing this project. Test scope and remaining limitations are in **[TESTING.md](TESTING.md)**.

### Licensing and attribution

Original Zork I: [historicalsource/zork1](https://github.com/historicalsource/zork1), MIT, © 2025 Microsoft; see [game license](zork/data/LICENSE-zork.txt). Interpreter: [curiousdannii/ifvms.js](https://github.com/curiousdannii/ifvms.js), MIT; see [interpreter license](zork/vendor/LICENSE-ifvms.txt). Interface: [MIT](LICENSE). The original in-game Infocom copyright/trademark banner is preserved. This is an independent interface and does not claim endorsement or ownership of Zork trademarks.
