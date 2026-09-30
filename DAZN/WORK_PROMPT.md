# Work handoff prompt

Use this prompt in ChatGPT Work:

---

Open the GitHub repositories to-shreds/Misc and to-shreds/ProjectStatus.

This is an ongoing Android project. Do not start from chat recollection. First read, in this order:

1. to-shreds/Misc/DAZN/HANDOFF.md
2. to-shreds/Misc/DAZN/STATUS.md
3. to-shreds/Misc/DAZN/NO_SPOILERS.md
4. to-shreds/Misc/DAZN/ARCHITECTURE.md
5. to-shreds/Misc/DAZN/FAILURE_HISTORY.md
6. to-shreds/Misc/DAZN/TESTING.md
7. to-shreds/Misc/DAZN/SOURCE_MAP.md
8. to-shreds/ProjectStatus/README.md
9. to-shreds/ProjectStatus/projects/nhl-uk/STATUS.md

The current private source archive is named NHL-UK-3.1-private-backup-fresh.zip and has SHA-256 be3772613c6eb4606dd83c4dff87444aa56c97189acd804225284502188a74dc. Locate it from the current conversation/files if available. If it is not available to Work, ask me to attach that exact archive before modifying code. Do not reconstruct or replace the current source from memory.

The archive contains a private signing key. Never commit, publish, upload to a public repository, print, or expose signing/nhl-uk-private.pem. Preserve the existing package app.nhluk and signing identity so the APK installs over the current app.

## Objective

Continue the NHL UK / Sabres Replay project to a genuinely working, one-tap, spoiler-safe DAZN replay client.

The user has NordVPN configured as Android's always-on VPN. The app must automatically switch Nord to the UK, reuse/authenticate the DAZN web session, list Sabres games without spoilers, resolve the exact selected game, prepare the beginning or saved elapsed position behind a fully opaque and muted guard, and reveal the video only after the selected position has been independently verified.

Never use the native DAZN app. The native app is unusable on this phone because DAZN reports vulnerable apps.

## Hard no-spoiler contract

No spoilers. Ever.

Do not display, log, copy, expose, infer into UI, or use as user-visible diagnostics:

- scores or results;
- winner/outcome language;
- period, clock, live state, or series state;
- standings;
- recap text/headlines;
- sports-page thumbnails, posters, hero images, preview frames, or canvases;
- total video duration;
- remaining time;
- progress percentage;
- later playoff opponents before earlier series are explicitly marked watched;
- live/current video frames while preparing from the beginning.

Only neutral teams, scheduled time, home/away identity, and elapsed time the user has personally watched are acceptable.

The app must fail closed. There is no fallback to the live edge, unfiltered DAZN sports UI, native DAZN app, or autoplay-next content.

## Latest real-phone evidence

Version 3.1-multi-search-test is the current source.

The phone progressed far enough for the protected player UI to show "Prepare beginning." When the user tapped Prepare beginning, the app immediately or shortly thereafter reported "Unexpected position change." Nothing has ever successfully played in the protected player.

This is the current frontier.

Do not spend time rewriting Nord automation, DAZN login, or catalogue transport unless new evidence points back to those stages. The 2.9 browser-origin catalogue transport returned real DAZN data on-device, and 3.1 reached the player preparation stage.

## First task: diagnose the seek failure, do not guess

Inspect assets/shield.js, especially:

- monitor()
- the HTMLMediaElement currentTime override
- the seeking/timeupdate listeners
- poll()
- prepare()
- play()

The current design uses a boolean internalSeek. During Prepare beginning it:

1. keeps the player covered/muted;
2. sets preparing=true and internalSeek=true;
3. sets currentTime through the original descriptor;
4. calls requestVideoFrameCallback;
5. calls the original play() while still covered/muted;
6. when a decoded frame appears near target, pauses and marks frameVerified=true;
7. sets internalSeek=false;
8. waits for the user's separate Play action.

The guard treats later seeking or provider currentTime writes as unexpected in several states.

Do not assume that "any position change during preparation is unsafe." Challenge that premise.

While the player is fully opaque and muted, DAZN may legitimately perform internal seeks as part of Media Source Extensions, DRM initialization, manifest changes, DVR normalization, or its own player state machine. The actual safety invariant is that NOTHING becomes visible or audible until the final state is at the requested beginning/resume position and independently verified.

Before changing behavior, add spoiler-safe instrumentation that can distinguish at least:

- prepare:start
- prepare:set-position
- media:seeking-internal
- media:seeking-external
- media:seeked-internal
- media:seeked-external
- media:provider-currenttime-write
- frame:verified
- media:source-change
- media:timeline-change
- prepare:ready
- prepare:failed-<allowlisted-code>

Do not record actual currentTime, duration, seekable endpoints, URLs, page text, titles, IDs, cookies, tokens, account data, or any game-state information in diagnostics.

Use the highest-information local/synthetic test you can to reproduce delayed seeking events and provider self-seeks after an app-controlled seek.

## Preferred direction if evidence supports it

If the current boolean internalSeek is the problem, replace it with an explicit seek/preparation transaction or state machine.

A good design may allow DAZN's own position changes while the player remains fully covered and muted, then reassert the requested target and require a stable decoded frame at that target before Ready.

Do not implement this as "ignore all seeks for N seconds." Do not simply increase tolerances until tests pass. Preserve fail-closed behavior with explicit state and final-state verification.

After Ready, and especially once video can be exposed, an unapproved source/position/timeline change must still immediately cover and mute playback.

## Preserve working architecture

Unless evidence requires otherwise, preserve:

- NordVPN as the only Android VPN;
- automatic UK switching;
- dedicated DAZN Auth activity and shared WebView cookies;
- 2.9 DAZN-origin browser-fetch catalogue transport;
- 3.0 same-game variant handling;
- 3.1 team aliases and neutral multi-search;
- no native Android Javascript bridge on remote DAZN pages;
- no stream/manifest/license/token extraction;
- separate explicit Play after preparation;
- elapsed-only bookmarks;
- fixed-schema user-initiated diagnostics;
- screenshots enabled for debugging.

## Verification requirements

Run all existing regression suites. Add tests specifically for the real-phone failure.

Adversarially test at least:

- app seek followed by delayed seeking event;
- app seek followed by delayed seeked event;
- provider currentTime write during covered preparation;
- provider currentTime write after frame verification but before Play;
- moving seekable ranges for a still-running game;
- source replacement during preparation;
- multiple exact-game DAZN variants;
- pause/prepare-resume;
- no visual/audio exposure in every failure path.

Local tests are not proof of DAZN behavior. State that clearly.

Build an installable update with the same signing identity and a new versionCode. Do not claim it is fixed, working, ready, or nearly done until it passes a real-phone test.

The first handset test must use a game the user has already watched.

## Repository and persistence

Use to-shreds/Misc/DAZN as the durable project directory.

If you need the current source in GitHub, create a sanitized source subtree there from the private archive, excluding all signing keys and private material.

After meaningful work:

1. update Misc/DAZN/HANDOFF.md;
2. update Misc/DAZN/STATUS.md;
3. add a build report under Misc/DAZN/history/ or an appropriate build-artifacts folder;
4. update to-shreds/ProjectStatus/projects/nhl-uk/STATUS.md as the final persistence step;
5. provide the installable APK as a downloadable artifact;
6. never commit the signing key.

Do the work through completion rather than merely describing what should be changed. If a material fact cannot be verified without the phone, build the highest-information diagnostic/repair you can, label the remaining uncertainty precisely, and hand back one clear device test.

---