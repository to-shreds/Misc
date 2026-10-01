# CODEX HANDOFF: Firefox-first DAZN / NHL UK rebuild

## Bootstrap

This file is intended to be read from a clone of:

`https://github.com/to-shreds/Misc.git`

If you are in a fresh Codex workspace and the repository is not present, clone that repository first.

The Firefox rebuild does **not** require the private legacy 3.1 archive to begin. Everything required for the initial Firefox extension implementation is documented in this repository. Treat the private archive as optional reference material only for later legacy-code reuse or Android signing continuity. Do not stop the Firefox work merely because that archive is unavailable.

This file is the controlling implementation handoff for the next architecture.

If you are Codex, read this file first, then read:

1. `STATUS.md`
2. `NO_SPOILERS.md`
3. `FAILURE_HISTORY.md`
4. `TESTING.md`
5. `SOURCE_MAP.md`
6. `history/LEGACY-WEBVIEW-ARCHITECTURE.md`

Do not continue the Android WebView player unless this file explicitly tells you to reuse a component from it.

## Architectural decision

Pivot the project away from DAZN playback inside an Android WebView.

Build:

1. one Firefox WebExtension codebase that runs on:
   - Firefox desktop on Windows;
   - Firefox for Android;
2. one very small Android companion launcher whose only substantial jobs are:
   - switch the existing NordVPN app to the UK automatically;
   - verify the UK route using the existing proven launcher logic where practical;
   - open Firefox to DAZN;
   - provide an explicit Restore VPN action.

The Firefox extension becomes the actual NHL UK product surface.

The old all-in-one Android app is legacy reference material only.

## Why

The legacy app repeatedly proved that Android WebView is the unstable layer:

- DAZN login required multiple special cases.
- DAZN catalogue requests behaved differently depending on browser origin.
- DAZN DRM/player behavior inside WebView caused position-management races.
- Version 3.1 reached `Prepare beginning` but failed with `Unexpected position change`.
- No WebView build ever completed protected playback on-device.

A Firefox extension lets DAZN authentication, DRM, Media Source Extensions, cookies, and video playback run in the browser environment DAZN actually targets.

The extension still controls the surrounding experience and the HTML video element, but it no longer recreates the browser.

## Platform facts to preserve

Current Mozilla documentation supports one WebExtension codebase for desktop Firefox and Firefox for Android.

Use Manifest V3.

The manifest must include a stable Firefox extension ID under `browser_specific_settings.gecko.id`.

For Android support, include `browser_specific_settings.gecko_android`. An empty object is sufficient when no separate Android version range is needed.

For AMO submission, include the required Firefox data-collection declaration. This project should declare no transmitted user data unless implementation requirements later prove otherwise.

Use an extension background script/event page, not a Firefox service worker. Firefox MV3 currently supports `background.scripts`; Firefox does not currently support extension `background.service_worker`.

Use `web-ext lint` and test the same source with:

```
web-ext run
web-ext run --target=firefox-android --android-device=<device> --firefox-apk=org.mozilla.firefox
```

Official reference set:

- https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/manifest.json/browser_specific_settings
- https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/Content_scripts
- https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/manifest.json/host_permissions
- https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/manifest.json/background
- https://extensionworkshop.com/documentation/develop/developing-extensions-for-firefox-for-android/
- https://extensionworkshop.com/documentation/develop/getting-started-with-web-ext/

## Repository layout to create

Create the new implementation under this existing public repo:

```
DAZN/
  CODEX_FIREFOX_HANDOFF.md
  FIREFOX_ARCHITECTURE.md
  firefox-extension/
    manifest.json
    package.json
    src/
      background.js
      content/
        bootstrap.js
        spoiler-shield.js
        app-shell.js
        auth.js
        schedule.js
        catalogue.js
        route-resolver.js
        player-controller.js
        resume.js
        media-session.js
        diagnostics.js
      styles/
        shield.css
        app.css
    tests/
      ...
  android-launcher/
    ...
  history/
    ...
```

Keep implementation dependency-light. Prefer plain JavaScript, CSS, and WebExtension APIs unless a dependency materially improves reliability.

Do not commit any Android signing key, Firefox signing credential, DAZN credential, cookie, token, or private user data.

## Product model

The Firefox extension owns:

- spoiler-free Sabres schedule;
- custom NHL UK interface;
- DAZN authentication state awareness;
- DAZN catalogue lookup;
- exact-game matching;
- same-game variant resolution;
- playback position control;
- spoiler shielding;
- elapsed-only resume;
- in-browser controls;
- diagnostics.

The Android launcher owns:

- Nord UK switch;
- route verification if still useful;
- launching Firefox;
- Restore VPN.

Windows Firefox uses the same extension without the Android launcher.

For the first Windows version, do not block implementation on automatic Nord desktop control. Assume Nord is already connected to the UK or provide a neutral "UK VPN required" state. Automatic Windows Nord control can be a later optional native-helper project if a reliable supported mechanism is found.

## One codebase, responsive UI

Do not build a desktop popup and an unrelated Android UI.

Build one in-page NHL UK app shell injected into DAZN pages.

Use responsive CSS so the same overlay works on desktop and mobile.

Avoid relying on browser-action popup UI for core playback because Android extension UI is more constrained.

A toolbar action may exist as a convenience to open DAZN or reopen the app shell, but the actual experience lives in the DAZN tab.

## Manifest permissions

Request only the minimum required hosts.

Expected host scope:

- DAZN website origins actually used by login/player;
- DAZN discovery endpoints actually required;
- the official NHL schedule endpoint.

Expected API permissions:

- `storage`;
- possibly `tabs` if authentication or controlled navigation requires a separate tab;
- possibly `scripting` only if dynamic injection is genuinely needed.

Do not request `<all_urls>`.

Do not request cookies access merely because it is available. Prefer DAZN's normal browser session.

If privileged cross-origin fetch is needed, use the background extension context with host permissions. In Firefox MV3, content-script cross-origin fetch does not gain host-permission bypass.

However, preserve the lesson from 2.9: DAZN catalogue access has been proven on the real phone when the request behaves like a DAZN-origin browser fetch. Build the catalogue transport as an abstraction:

1. preferred path: actual DAZN-page-origin fetch when that is accepted and stable;
2. background host-permission fetch as a controlled alternative when it produces equivalent accepted behavior.

Do not assume either transport works until tested against the real provider.

## Immediate spoiler shield

The extension must protect the page before DAZN sports content can flash.

Inject at `document_start`.

The initial page state must be opaque.

Recommended CSS model:

- inject a fixed NHL UK root directly under `document.documentElement`;
- hide ordinary DAZN page descendants by default;
- show only the extension shell;
- when authorized playback begins, make only the selected `video` element visible, not the rest of DAZN's page.

Do not move the DAZN video element into the extension DOM unless testing proves this is harmless. Prefer CSS visibility and fixed positioning.

Do not use `display:none` on ancestors required by DAZN's player.

The page may never visibly expose DAZN home rails, scores, thumbnails, recaps, progress bars, or autoplay recommendations.

## Authentication

Do not recreate DAZN's login form.

Use the real Firefox DAZN session.

When the user is not authenticated:

- navigate to DAZN's real sign-in flow;
- keep a protective cover until the URL is positively recognized as an authentication page;
- allow the actual sign-in page to operate normally;
- when authentication finishes, immediately reapply the spoiler shield before any DAZN sports/home page can become visible;
- resume the pending NHL UK selection.

Prefer a separate authentication tab if Firefox Android supports the exact required tab lifecycle cleanly in testing. Otherwise use the same controlled DAZN tab.

Never inspect or store the user's password.

## NHL schedule

Use the official NHL schedule service already used by the prior project.

Raw NHL responses may contain scores/status information.

Treat raw schedule data as tainted.

Sanitize immediately to an allowlist such as:

- NHL game ID;
- scheduled UTC start;
- game date;
- home team abbreviation;
- away team abbreviation.

Do not pass scores, current status, result fields, period fields, recap links, or similar data to the UI layer, logs, diagnostics, or storage.

The interface must remain neutral even for completed games.

## DAZN catalogue

Reuse the mature matching concepts from legacy `assets/catalogue.js` but port them cleanly.

Preserve:

- full team names;
- city/location aliases;
- mascot aliases;
- abbreviations;
- exact opponent pair;
- date/start-time filtering;
- same-game variant grouping;
- home/away feed preference when explicitly known;
- replay/VOD/catch-up preference over live when available;
- live candidate only when needed for a still-running game with a safe DVR beginning.

Search terms must remain neutral.

Never use provider scores/results to match or rank a candidate.

Never display provider descriptions or thumbnails.

If the extension can obtain an exact DAZN event route directly from structured provider data, use it.

Do not fall back to visually scraping DAZN sports search results unless no structured path exists and a future explicit design review approves it.

## Player-control philosophy

This is the most important design change.

Do not port the legacy `shield.js` seek policing literally.

The legacy guard tried to classify almost every `currentTime` write or `seeking` event as internal or external. DAZN's real player fought that model.

In Firefox, the player should be allowed to behave normally while it remains completely hidden and muted.

The safety invariant is:

> Nothing can become visible or audible until the final selected media source and requested beginning/resume position have stabilized and been independently verified.

The extension does not need to prevent every provider seek during hidden preparation.

## Player state machine

Implement an explicit transaction/state machine, not one boolean such as `internalSeek`.

Suggested states:

- `IDLE`
- `WAITING_FOR_MEDIA`
- `WAITING_FOR_TIMELINE`
- `POSITIONING`
- `SETTLING`
- `READY_COVERED`
- `PLAYING_EXPOSED`
- `PAUSED_EXPOSED`
- `FAILED_COVERED`

Each preparation attempt gets a generation/token.

Any async callback from an old generation must be ignored.

### Prepare beginning

For a full replay:

1. shield and mute;
2. identify exactly one intended media element;
3. wait for usable media metadata and a seekable timeline;
4. confirm the requested target is available;
5. set the target;
6. allow provider-internal seeks while hidden;
7. if DAZN moves away from target during initialization, reassert target as part of the same bounded preparation transaction;
8. wait for a stable target;
9. verify decoded video frames at or near the target;
10. pause at the target;
11. remain covered;
12. expose a separate `Play` control.

Do not use a fixed blind sleep.

Define stabilization in terms of observable state.

For example, require a bounded number of consecutive checks/frames within target tolerance with no source/timeline replacement.

### Still-running game

A still-running game can be started from the beginning only if the browser player exposes a seekable DVR range that actually contains the beginning.

Do not infer the beginning from total duration.

Do not fall back to live.

If the earliest seekable point is materially later than the beginning, fail covered.

### Explicit Play

When the user taps Play:

1. reverify selected source identity;
2. reverify the target position has not drifted materially;
3. reverify target is still seekable;
4. keep muted and covered while calling play;
5. verify playback begins from the expected target;
6. then expose the video and restore intended audio.

If verification fails, stay covered.

## Video presentation

Hide native DAZN controls unless they can be proven spoiler-safe.

A native seek bar can reveal total duration and current progress.

Provide extension controls instead:

- Play/Pause;
- elapsed-only watched time;
- skip backward;
- skip forward;
- restart from beginning;
- fullscreen;
- close/return to games.

No total length, remaining time, percentage, end marker, live edge, or progress bar.

Fullscreen must not reveal DAZN page chrome.

Test both desktop and Android fullscreen behavior.

## Resume

Store only:

- NHL game ID;
- selected DAZN route/variant identity as needed;
- explicit feed preference if known;
- elapsed seconds watched;
- last-watched timestamp if useful.

Do not persist total duration.

Save during authorized playback on a reasonable interval plus pause/pagehide.

Resume is another covered preparation transaction to the saved elapsed position.

If the saved route no longer exists, re-resolve the game and prepare the saved elapsed position on a compatible variant.

## Media session and system surfaces

DAZN must not leak spoiler metadata to lock screen, media notification, Bluetooth head unit, or system controls.

Neutralize Media Session metadata while NHL UK controls playback.

Use neutral labels only.

Do not expose duration/progress through extension UI.

Test Android media notification and Windows media controls.

Keep Picture-in-Picture disabled for v1 unless it can be demonstrated spoiler-safe.

## Navigation and SPA behavior

DAZN is a SPA.

Do not depend only on traditional page loads.

The extension must survive:

- `history.pushState`;
- `replaceState`;
- `popstate`;
- DOM replacement;
- player replacement;
- source replacement.

Prefer simple location observation plus DOM/media observers over fragile DAZN-specific internal JS hooks.

Use MAIN-world injection only when isolated-world DOM access cannot accomplish a required function.

If MAIN-world code is introduced:

- keep it tiny;
- never pass secrets into it;
- use a narrow message schema;
- assume DAZN page code can see and interfere with it.

## Extension storage

Use `browser.storage.local`.

Do not use cloud sync for v1.

Desktop and Android resume states may remain device-local initially.

No remote telemetry.

No analytics.

No third-party database.

The AMO data-collection declaration should remain `none` unless requirements materially change.

## Android launcher

Refactor the old Android project into a thin launcher.

Reuse only the parts already useful:

- Nord country switch;
- scoped AccessibilityService if still required;
- UK verification where reliable;
- explicit VPN restore state.

Delete or retire:

- DAZN Auth WebView;
- DAZN Browser/WebView player;
- catalogue WebView;
- WebView spoiler shield;
- WebView resume implementation.

Launcher flow:

1. tap NHL UK;
2. remember prior Nord state/country if available;
3. switch Nord to UK automatically;
4. verify;
5. launch `https://www.dazn.com/` specifically in Firefox using Android intent/package targeting;
6. leave a persistent notification or launcher action for `Restore VPN`.

Do not automatically restore VPN merely because Firefox backgrounds.

Do not make the launcher depend on the extension for basic recovery.

If Firefox is missing, show one concise setup screen.

## Windows

The extension itself is the Windows client.

Do not block v1 on Windows Nord automation.

For v1:

- extension verifies/indicates whether DAZN is usable in the current region;
- user may connect Nord to UK manually.

After playback works, separately research whether Nord offers a reliable supported Windows automation interface.

If not, an optional native-messaging helper can be evaluated later.

Do not couple the extension core to a Windows native helper.

## Distribution

Development:

- use `web-ext run` on Firefox desktop;
- use `web-ext run --target=firefox-android` against a real Android device;
- use `web-ext lint` with Android compatibility checking.

Do not spend significant time on AMO publication until playback works.

Once functional:

- package one Firefox extension;
- ensure `browser_specific_settings.gecko_android` is present;
- sign through Mozilla for persistent installation;
- decide listed vs unlisted distribution based on the simplest reliable Android installation path at that time.

The GitHub Pages site, if used, is only a landing page, documentation page, or distribution pointer. It is not the runtime player.

## Testing strategy

The new extension architecture must be tested in actual Firefox, not only jsdom or Chromium.

### Unit tests

Test pure logic separately:

- schedule sanitization;
- team aliases;
- catalogue matching;
- variant ranking;
- bookmark compatibility;
- diagnostics redaction;
- state-machine transitions.

### Synthetic browser tests

Create synthetic pages that model:

- delayed video insertion;
- video replacement;
- delayed seeked;
- multiple provider seeks during hidden preparation;
- source changes;
- moving DVR seekable window;
- short clip first variant then full replay second variant;
- auth SPA transition;
- media-session metadata replacement.

### Desktop Firefox integration

Use `web-ext run`.

Test real DAZN with a game the user already watched.

### Android Firefox integration

Use a physical Android device over ADB with `web-ext run --target=firefox-android`.

Do not accept desktop success as proof of Android success.

## Acceptance milestones

### Milestone A: extension shell

- extension installs on desktop and Android;
- DAZN page is spoiler-covered from first usable paint;
- same responsive NHL UK shell appears on both;
- neutral NHL schedule works;
- no DAZN sports UI leaks.

### Milestone B: authentication

- ordinary DAZN login works in Firefox;
- post-login sports content never flashes;
- pending NHL UK selection resumes.

### Milestone C: catalogue

- selected known game resolves to exact DAZN variants;
- no scores/results used or shown;
- catalogue behavior works on both platforms.

### Milestone D: covered player preparation

- known completed replay reaches video;
- Prepare beginning stabilizes at beginning while covered/muted;
- no unexpected-position failure from normal provider initialization;
- Ready is reached without revealing video.

### Milestone E: playback

- Play reveals the correct beginning;
- pause/play works;
- extension controls work;
- DAZN chrome remains hidden;
- system media metadata remains neutral.

### Milestone F: resume

- close/reopen;
- saved elapsed position prepares behind cover;
- resume begins near saved elapsed position;
- no total-duration/progress spoilers.

### Milestone G: still-running game

Only after completed replay works:

- confirm the DVR seekable range contains the beginning;
- prepare beginning while game is still in progress;
- never expose current/live position;
- fail covered if beginning is unavailable.

### Milestone H: Android launcher

Only after extension playback is proven:

- thin launcher switches Nord UK;
- launches Firefox;
- extension takes over;
- explicit Restore VPN works.

## What not to do

Do not:

- rebuild another DAZN WebView player;
- port the old `shield.js` wholesale;
- use a broad "ignore seeks for N seconds" hack;
- scrape visible DAZN search results if structured discovery is available;
- show DAZN home/search pages;
- use native DAZN app;
- reveal native DAZN timeline controls;
- use scores/status to identify a game;
- call a build "fixed" because unit tests pass;
- weaken the no-spoiler contract for convenience;
- commit signing keys or credentials.

## First implementation sequence

Do this in order:

1. Create `DAZN/firefox-extension/`.
2. Create a valid Firefox MV3 manifest for desktop and Android.
3. Add `web-ext` lint/run scripts.
4. Build the document-start spoiler shield and responsive extension shell.
5. Port only pure schedule/catalogue matching logic from legacy source.
6. Prove the shell and schedule on desktop Firefox and Android Firefox.
7. Implement real DAZN auth handling in Firefox.
8. Implement catalogue lookup.
9. Implement the new covered player state machine from scratch.
10. Prove completed replay Prepare beginning and Play.
11. Implement elapsed-only resume.
12. Test Android.
13. Only then refactor the Android APK into the thin Nord launcher.

Do not begin by modifying the old 3.1 player guard.

## Durable-state rule

After each meaningful benchmark:

1. update this file if the controlling architecture changes;
2. update `STATUS.md`;
3. update `HANDOFF.md`;
4. add an implementation/build report under `history/` or a new `reports/` folder;
5. update `to-shreds/ProjectStatus/projects/nhl-uk/STATUS.md` last.

## Definition of success

The project is successful when the same Firefox extension can safely play a Sabres replay on Firefox desktop and Firefox Android without DAZN sports UI spoilers, and the Android companion can automatically handle the Nord UK step before launching Firefox.

Until real Firefox playback passes on-device, readiness remains implementation/testing state, not READY.
