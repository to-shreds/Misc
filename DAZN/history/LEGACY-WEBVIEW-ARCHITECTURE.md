# Legacy Android WebView architecture

This file preserves the architecture that was current through version 3.1. It is historical only. The project pivoted to a Firefox extension architecture on 2026-10-01.

# Architecture

## User flow

The intended end-to-end flow is:

Local Sabres dashboard -> automatic Nord UK switch -> UK verification -> DAZN auth/session reuse -> spoiler-safe catalogue resolution -> covered DAZN event/player -> Prepare beginning or resume -> decoded-frame verification -> explicit Play -> elapsed-only resume.

## Components

### Main dashboard

A local WebView hosts the app's own interface and NHL schedule data. It must never show scores or results. It uses NHL schedule data only to identify teams and scheduled times.

### Nord automation

NordVPN remains Android's one active VPN application.

The app requests a UK country switch and uses a narrowly scoped AccessibilityService to handle Nord's current confirmation UI. The service acts only while an app-private switch request is armed and only on the Nord package.

The app verifies an active VPN plus a UK public exit before proceeding.

### DAZN authentication

A separate non-exported Auth activity hosts DAZN's ordinary web login. It shares WebView cookies with playback.

The app does not store or extract the DAZN password. Authentication and player views are separate.

### Catalogue discovery

The current transport was established after several failed approaches.

The working direction as of 2.9 is a synthetic HTTPS page under a DAZN origin that uses Chromium fetch to call DAZN's search discovery service. This avoided the HTTP 403 seen when the JSON API was treated as a top-level page.

Raw catalogue data stays inside the hidden renderer. The app accepts only sanitized game identity fields and opaque DAZN route identifiers.

3.0 added same-game variant resolution. 3.1 added city/location aliases and multiple neutral search terms.

### Protected DAZN player

A non-exported Browser activity hosts the remote DAZN player behind a native opaque cover.

assets/shield.js injects a fail-closed page guard. It hides/mutes media, neutralizes system media metadata, blocks uncontrolled picture-in-picture/fullscreen and tries to ensure that only the chosen media element can be exposed.

The remote DAZN page receives no Android Javascript bridge.

### Preparation model

Before Play, the guard requires:

- a single selected media element;
- an acceptable seekable timeline;
- the requested beginning or resume position to be available;
- a controlled seek while picture and sound are hidden;
- requestVideoFrameCallback support;
- a decoded frame near the requested position;
- no unapproved source or position change.

Only after this does the UI offer Play.

### Resume

Bookmarks are elapsed-only and keyed to the selected game/route/feed. The UI must never display total duration, percentage, or remaining time.

### Diagnostics

Diagnostics are user-initiated and fixed-schema. They may contain build, stage, allowlisted error code, Android API, WebView version, and allowlisted event markers.

They must not contain scores, page text, URLs, game IDs, titles, video timing values, credentials, cookies/tokens, account details, or IP addresses.

## Current architectural concern

The prepare guard uses a boolean internalSeek to distinguish app-controlled and external position changes. The latest device result shows that this model may be too coarse for DAZN's real player lifecycle.

Do not replace it blindly. Instrument event provenance and ordering first.
