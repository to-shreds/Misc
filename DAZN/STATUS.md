# DAZN / NHL UK current status

State: **IN PROGRESS**

## Current direction

The legacy Android WebView player is no longer the primary implementation path.

The project has adopted a Firefox-first architecture:

- one Firefox WebExtension codebase for Windows Firefox and Firefox for Android;
- a thin Android companion app for automatic NordVPN UK switching, launching Firefox, and explicit VPN restoration.

The controlling implementation specification is `CODEX_FIREFOX_HANDOFF.md`.

## Why the architecture changed

The legacy WebView project progressively solved VPN switching, DAZN web authentication, catalogue transport, team matching, and same-game variants, but no version ever completed protected playback.

The final 3.1 phone test reached `Prepare beginning` and then failed with `Unexpected position change`.

Rather than continue fighting DAZN's DRM/player lifecycle inside Android WebView, the new architecture lets Firefox run DAZN in its native browser environment and moves NHL UK controls into a WebExtension on the real DAZN page.

## What is already durable

The repo preserves:

- the full 2.0 through 3.1 build history;
- no-spoiler contract;
- legacy architecture;
- source map;
- test limitations;
- DAZN discovery research;
- the final legacy failure chronology;
- the new Firefox implementation handoff.

## New implementation scope

Not yet built:

- `DAZN/firefox-extension/`;
- desktop Firefox extension shell;
- Android Firefox extension shell;
- new extension-native player state machine;
- elapsed-only Firefox resume;
- thin Android Nord launcher refactor.

## Current definition of done

The same Firefox extension must safely play a known Sabres replay on Windows Firefox and Firefox for Android without showing DAZN sports UI or spoiler metadata.

The Android companion must automatically switch Nord to UK and launch Firefox.

Only after completed replays work should still-running DVR-from-start behavior be accepted.

## Next action

Give Codex this instruction:

`Clone https://github.com/to-shreds/Misc.git, read CODEX.md, and execute it.`

Codex should create the extension implementation under `DAZN/firefox-extension/` and work milestone by milestone. It should not start by patching the legacy 3.1 WebView seek guard.

## Plain-English Status

The old Android player still does not work and is now historical reference rather than the main path forward. The project has a fully mapped Firefox-extension architecture that should remove the WebView layer that caused most of the playback trouble. The next step is implementation in Codex, starting with one shared Firefox extension for Windows and Android. A fresh Codex workspace does not need the private 3.1 archive to begin.

PROJECT_STATUS_FINAL: IN PROGRESS | 2026-10-01T00:04:59-04:00 | Firefox-first architecture adopted and fully handed off; extension and thin Android launcher implementation have not started yet.
