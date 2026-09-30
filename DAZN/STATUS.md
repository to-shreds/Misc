# DAZN / NHL UK current status

State: BLOCKED

## Definition of done

A successful build must do all of the following on the real Samsung phone:

- preserve NordVPN as the Android always-on VPN;
- automatically switch Nord to a UK exit without routine manual country selection;
- authenticate to DAZN and reuse the web session;
- list Sabres games with no scores, results, status, thumbnails, recap text, total runtime, remaining time, or other spoilers;
- resolve the exact selected NHL game;
- prepare the beginning, or a saved elapsed position, while picture and sound remain covered;
- reveal only the verified selected replay position after a separate Play action;
- save and restore elapsed-only resume position;
- restore the user's normal VPN state when explicitly requested;
- never fall through to an unfiltered DAZN sports page.

No build has met that definition.

## Latest device evidence

Current build tested: 3.1-multi-search-test, versionCode 13.

Observed sequence on the phone:

1. The game selection and catalogue flow advanced far enough to present "Prepare beginning."
2. The user tapped Prepare beginning.
3. The app reported "Unexpected position change."
4. No successful playback occurred.

This is the first confirmed failure at the protected position-preparation stage. It strongly suggests that catalogue resolution and at least one candidate player route progressed farther than the previous catalogue failures. It does not prove that the selected media was the correct full replay, that Widevine playback would succeed, or that resume works.

## What is actually proven on-device

- DAZN web sign-in has succeeded in the dedicated authentication flow.
- The old direct catalogue navigation was rejected by DAZN with HTTP 403.
- The DAZN-origin browser-fetch transport introduced in 2.9 returned usable catalogue data.
- 2.9 encountered multiple same-game candidates rather than a transport failure.
- 3.1 reached a state in which the protected player exposed Prepare beginning.
- The guard has so far prevented an unverified video from being shown.

## What is not proven

- Correct full-replay route selection.
- Safe seek to the beginning on DAZN's actual player.
- Widevine playback after the guard is released.
- Resume on the real player.
- Still-running DVR playback from the beginning.
- End-to-end VPN restore behavior.
- Safe use with an unwatched game.

## Current source-level failure target

The current guard can emit unexpected-seek from several paths in assets/shield.js.

Relevant mechanisms include:

- a seeking event on the selected media element when internalSeek is false;
- a write to currentTime by page/player code while the guard considers the video preparing, verified, or authorized;
- the final Play check if the media position no longer matches the verified target.

Because the user saw the error immediately after Prepare beginning, before a separate Play action, the final Play check is not the leading path. The exact cause is not yet established. Plausible explanations include a delayed seeking event from the app's own seek, or a DAZN player self-adjustment after the guard clears internalSeek. Do not choose a fix until device diagnostics distinguish those cases.

## Next action

Instrument the prepare sequence, not the catalogue or VPN flow.

Add spoiler-safe diagnostic events sufficient to distinguish:

- prepare requested;
- target setter invoked internally;
- seeking event while internalSeek is true;
- seeking event after internalSeek becomes false;
- provider currentTime setter attempt while preparing;
- seeked event;
- decoded frame verified;
- provider position adjustment after frame verification;
- source/timeline change.

Do not record or expose the actual video position, total duration, live edge, result metadata, page text, URLs, tokens, or account details.

After instrumentation, build an installable update with the same package/signing identity and test only a game already watched.

PROJECT_STATUS_FINAL: BLOCKED | 2026-09-30 | Version 3.1 reached protected position preparation on-device, but Prepare beginning failed with unexpected position change and no build has successfully played a protected replay.
