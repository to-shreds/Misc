# HANDOFF: DAZN / NHL UK

## Read first

STATUS.md is the current truth. FAILURE_HISTORY.md explains how the project got here. NO_SPOILERS.md is a hard product and safety contract. Do not weaken it to make a test pass.

The public Misc repository does not contain the private signing key. The latest private source archive is NHL-UK-3.1-private-backup-fresh.zip. Its SHA-256 is be3772613c6eb4606dd83c4dff87444aa56c97189acd804225284502188a74dc.

## Current phone result

Version 3.1 reached "Prepare beginning." Tapping it produced "Unexpected position change." Nothing has ever successfully played in the protected DAZN player.

This is materially different from the prior failures. Do not go back to catalog search, login, or Nord automation unless new evidence points there.

## Preserve these completed decisions

- Package remains app.nhluk.
- Keep the existing signing identity for install-over updates.
- NordVPN remains the single Android VPN. No second VpnService.
- UK switching is automatic. Routine manual Nord country selection is not acceptable.
- DAZN authentication uses a dedicated WebView and shared WebView cookies.
- The app's own Sabres schedule is spoiler-free and independent of DAZN's visible sports UI.
- DAZN catalogue discovery uses the browser-origin fetch transport that first succeeded on-device in 2.9.
- Multiple same-game DAZN records are variants, not automatically fatal ambiguity.
- Remote DAZN pages do not receive a native Android Javascript interface.
- No stream, Widevine license, token, password, or account data extraction.
- The player stays opaque and muted until the selected position is verified.
- A separate Play action is required after preparation.

## Latest source mechanics to inspect

Start with assets/shield.js, particularly monitor(), poll(), prepare(), play(), and the currentTime override.

The present prepare sequence is roughly:

1. mark the page covered;
2. verify a single media element and seekable beginning;
3. set preparing=true and internalSeek=true;
4. set currentTime using the original HTMLMediaElement descriptor;
5. request decoded video frames;
6. call the original play() while still muted/covered;
7. when a decoded frame appears near target, pause, mark frameVerified=true, and set internalSeek=false;
8. wait for explicit Play.

The current guard also:

- treats a seeking event as unexpected whenever internalSeek is false;
- treats a page/player currentTime write as unexpected when the selected player is preparing, frameVerified, or authorized and the write is not marked internal.

The phone result is consistent with a race or normal DAZN player seek that occurs after step 7, but that is a hypothesis, not a conclusion.

## Highest-information next test

Instrument the event order before changing tolerances or disabling protections.

Use a fixed, spoiler-safe diagnostic event vocabulary such as:

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

Do not include position values, duration values, page titles, HTML, URLs, query strings, cookies, tokens, scores, results, or current game state.

A likely repair, if diagnostics prove a delayed event from the app's own seek, is a bounded seek-generation or settling-state model rather than a simple boolean internalSeek. If diagnostics prove DAZN legitimately self-seeks after the app's seek, model and authorize only the specific provider transition needed during preparation. Do not merely add a broad time window that ignores all seeks.

## Test discipline

A local synthetic pass is necessary but never sufficient. Every claim about DAZN, Nord, Widevine, or Samsung WebView must be labeled unverified until observed on-device.

Do not say a build is fixed, working, ready, or nearly done merely because tests pass. Device evidence has repeatedly invalidated synthetic assumptions.

First device test after any player change must use a replay the user already watched.

## Persistence

After meaningful progress:

1. update this HANDOFF.md if source/next-step state changed;
2. update STATUS.md;
3. update to-shreds/ProjectStatus/projects/nhl-uk/STATUS.md as the final persistence step;
4. never commit the private signing key.
