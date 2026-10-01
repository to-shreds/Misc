# HANDOFF: DAZN / NHL UK

## Controlling implementation handoff

The project has pivoted from the legacy Android WebView player to a Firefox-first architecture.

**Codex should read and execute `CODEX_FIREFOX_HANDOFF.md`.**

That file is now the controlling implementation specification.

Read these supporting files afterward:

1. `STATUS.md`
2. `NO_SPOILERS.md`
3. `ARCHITECTURE.md`
4. `FAILURE_HISTORY.md`
5. `TESTING.md`
6. `SOURCE_MAP.md`
7. `history/LEGACY-WEBVIEW-ARCHITECTURE.md`

## Current architectural decision

Build one Firefox WebExtension codebase for Windows Firefox and Firefox for Android.

The extension becomes the NHL UK product surface and runs on DAZN's real site so Firefox, not Android WebView, owns DAZN login, DRM, MSE, cookies, and video playback.

Keep a separate tiny Android companion only for automatic NordVPN UK switching, launching Firefox, and explicit VPN restoration.

Do not continue the legacy all-in-one WebView player as the primary architecture.

## Legacy status

The final legacy source is version 3.1-multi-search-test.

Its latest real-phone result was:

- exact game resolution advanced far enough to show `Prepare beginning`;
- pressing `Prepare beginning` failed with `Unexpected position change`;
- no build ever completed protected playback.

The legacy source archive remains useful for:

- Nord automation;
- no-spoiler requirements;
- NHL schedule sanitization;
- team alias/catalogue matching logic;
- same-game variant logic;
- diagnostics patterns;
- Android package/signing continuity.

Do not port the legacy remote-player seek guard wholesale.

## New implementation order

Codex should:

1. create `DAZN/firefox-extension/`;
2. make one MV3 extension lint/run on desktop and Android Firefox;
3. implement the document-start spoiler shield and responsive in-page shell;
4. port only pure schedule/catalogue logic;
5. prove authentication in real Firefox;
6. prove structured DAZN catalogue resolution;
7. implement a new covered player state machine from scratch;
8. prove completed-replay Prepare and Play on desktop;
9. prove Android Firefox parity;
10. add elapsed-only resume;
11. only then refactor the existing Android APK into `DAZN/android-launcher/`.

## Hard constraints

- No spoilers, ever.
- No native DAZN app.
- No new DAZN WebView player.
- No unfiltered DAZN sports page.
- No live-edge fallback.
- No total runtime or percentage.
- No stream/license/token extraction.
- No signing keys in git.
- Do not claim readiness from synthetic tests alone.

## Persistence

After meaningful work:

1. update `CODEX_FIREFOX_HANDOFF.md` only if the architecture changes;
2. update this HANDOFF;
3. update `STATUS.md`;
4. add a report under `history/` or `reports/`;
5. update `to-shreds/ProjectStatus/projects/nhl-uk/STATUS.md` last.
