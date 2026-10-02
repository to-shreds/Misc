# DAZN / NHL UK current status

State: **IN PROGRESS**

## Objective and source

One Firefox extension for spoiler-safe Sabres replay playback on Windows and Android, followed by a thin Android NordVPN launcher. `CODEX_FIREFOX_HANDOFF.md` controls architecture. Code is in `to-shreds/Misc/DAZN/firefox-extension/`, development version `0.1.0`.

## Latest benchmark

The first shared Firefox implementation now exists: document-start protection, responsive schedule, controlled sign-in, structured catalogue matching and routes, covered decoded-frame preparation, explicit Play, source/seek handling, elapsed-only resume and neutral diagnostics/media metadata.

Current provider search and route conventions were verified using DAZN responses and deployed frontend code. The public-data smoke check sanitized 88 schedule rows and matched 3 games into 5 variants, without displaying or storing raw provider content. This establishes data processing, not protected playback.

Unit tests, Mozilla lint, package generation and actual installed-extension synthetic Firefox checks have been run. `reports/FIREFOX-IMPLEMENTATION-2026-10-02.md` records the final counts, package hash and limitations. Commands and installation instructions are in `firefox-extension/README.md`.

## Remaining work

Real DAZN login, account entitlement, DRM playback and the first revealed broadcast frame/audio must pass on Windows and physical Android. System media controls, notification duration/progress, fullscreen and background/rotation behavior also need real-device evidence. Embedded frames/plugins are blocked, so iframe-dependent login widgets may need a carefully verified integration change.

Live/DVR playback and playoff progression remain unavailable. The Android Nord launcher has not been started, as required by the handoff's playback gate. The unsigned development ZIP is a test package, not a persistent Android release. No legacy files or private signing keys were imported.

## Current artifact and next action

Development package: `artifacts/nhl-uk-firefox-0.1.0-unsigned.zip`.

Next: follow `firefox-extension/tests/REAL-DEVICE-ACCEPTANCE.md` with a previously watched completed replay on Windows Firefox, then on a connected Android phone. Do not use an unwatched game to validate the player.

## Plain-English Status

The first Firefox version is built, and its protective controls and replay preparation work in synthetic Firefox tests. It has not yet played a real DAZN replay on your Windows computer or Android phone, so it is not ready for unwatched hockey. The next step is to test a game you have already watched on both devices. The automatic Nord launcher comes after those playback tests pass.

PROJECT_STATUS_FINAL: IN PROGRESS | 2026-10-02T04:40:38Z | Firefox development version 0.1.0 implemented and synthetically tested; real DAZN playback, physical-device acceptance and the gated Android launcher remain incomplete.
