# DAZN / NHL UK

This folder is the durable project home for the spoiler-safe Sabres replay project.

## Current direction

The project has pivoted to a **Firefox-first architecture**.

The old Android WebView player is preserved as legacy history, but it is no longer the primary implementation path.

The first Firefox development implementation now exists in `firefox-extension/`. Read its `README.md` for commands and `STATUS.md` for the actual verification boundary. It is not yet accepted for unwatched hockey.

The new target is:

- one Firefox WebExtension codebase for Windows Firefox and Firefox for Android;
- one thin Android companion app that handles NordVPN UK switching and launches Firefox.

## Codex

For implementation, tell Codex:

`Clone https://github.com/to-shreds/Misc.git, read CODEX.md, and execute it.`

That one file contains the controlling architecture, repository layout, implementation order, player state-machine design, testing requirements, Android-launcher boundary, distribution plan, and no-spoiler constraints.

## Read order

1. `CODEX_FIREFOX_HANDOFF.md`
2. `STATUS.md`
3. `NO_SPOILERS.md`
4. `ARCHITECTURE.md`
5. `FAILURE_HISTORY.md`
6. `TESTING.md`
7. `SOURCE_MAP.md`

## Legacy history

The prior Android WebView work is preserved under `history/`.

`history/BUILD-2.0.md` through `history/BUILD-3.1.md` record each build.

`history/LEGACY-WEBVIEW-ARCHITECTURE.md` preserves the old architecture.

The final legacy source archive remains `NHL-UK-3.1-private-backup-fresh.zip`, SHA-256 `be3772613c6eb4606dd83c4dff87444aa56c97189acd804225284502188a74dc`.

That private archive contains signing material and must not be committed to this public repository.

## Hard requirement

No spoilers. Ever.

See `NO_SPOILERS.md`.
