# Current architecture: Firefox-first

The controlling implementation specification is `CODEX_FIREFOX_HANDOFF.md`.

The prior Android WebView architecture is archived at `history/LEGACY-WEBVIEW-ARCHITECTURE.md`.

## System

### Firefox extension

One Manifest V3 WebExtension codebase targets:

- Firefox desktop on Windows;
- Firefox for Android.

The extension runs directly on DAZN's real website and lets Firefox own:

- DAZN authentication;
- cookies/session;
- Media Source Extensions;
- DRM/Widevine;
- actual video playback.

The extension owns:

- the spoiler-free NHL UK interface;
- NHL schedule sanitization;
- DAZN catalogue resolution;
- exact-game and variant selection;
- player positioning;
- spoiler shielding;
- elapsed-only resume;
- neutral media metadata;
- diagnostics.

### Android companion

The Android app becomes a thin launcher only.

It owns:

- automatic NordVPN UK switch;
- UK route verification where reliable;
- launching Firefox to DAZN;
- explicit Restore VPN action.

It does not host DAZN, catalogue search, authentication, DRM, or playback.

### Windows

The same Firefox extension is the Windows client.

Windows Nord automation is not required for the first extension milestone. The user can put Nord on UK manually until a reliable Windows automation mechanism is separately established.

## Browser-extension structure

The extension uses an in-page app shell rather than browser popup UI.

A content script runs at document start on DAZN origins so DAZN sports content is covered before it can flash.

The extension keeps the normal DAZN page hidden except for:

- the real login experience when explicitly in authentication mode;
- the selected video element after playback authorization.

The extension does not display DAZN home/search rails, scores, thumbnails, recaps, recommendations, native timeline controls, or autoplay content.

## Network model

NHL schedule data is sanitized immediately to neutral allowlisted fields.

DAZN catalogue access uses structured discovery services.

The catalogue transport must preserve the lesson from the legacy 2.9 build: DAZN-origin browser semantics worked on the real phone.

The implementation may use:

- a DAZN-page-origin fetch path;
- an extension background fetch with host permission when DAZN accepts that request shape.

The transport is abstracted and tested rather than assumed.

## Player model

Do not port the legacy seek guard literally.

During preparation the selected DAZN video remains fully hidden and muted. DAZN may perform normal player-internal seeks while hidden.

The extension controls an explicit preparation state machine:

- wait for intended media;
- wait for usable timeline;
- position to beginning/resume;
- allow bounded provider settling;
- reassert target when necessary;
- verify stable decoded frame at target;
- pause;
- remain covered;
- show explicit Play.

Only after a final Play-time verification can the selected video become visible/audible.

No broad blind seek-ignore window is allowed.

## User interface

One responsive interface serves desktop and Android.

Core controls:

- game list;
- Continue Watching;
- Prepare beginning / Prepare resume;
- Play/Pause;
- skip backward/forward;
- restart from beginning;
- fullscreen;
- elapsed time watched;
- close/return to games.

No total runtime, remaining time, percentage, live edge, or conventional progress bar.

## Storage

Use `browser.storage.local`.

No telemetry, analytics, cloud database, or sync for v1.

Bookmarks store elapsed-only state plus the minimal route/feed identity needed for compatibility.

## Distribution and testing

Use Mozilla `web-ext`.

Desktop:

```
web-ext run
```

Android:

```
web-ext run --target=firefox-android --android-device=<device> --firefox-apk=org.mozilla.firefox
```

Persistent/public distribution comes after real playback works.

The same source package must declare Android support through `browser_specific_settings.gecko_android`.

## Acceptance order

1. extension shell and spoiler cover on both platforms;
2. neutral schedule;
3. DAZN login;
4. exact catalogue match;
5. completed-replay preparation;
6. completed-replay Play;
7. resume;
8. Android Firefox parity;
9. still-running from-beginning DVR;
10. thin Android Nord launcher.

Do not build the launcher first. The extension must prove playback first.
