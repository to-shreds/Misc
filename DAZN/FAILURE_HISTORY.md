# Failure and build history

This history records what each iteration tried and what the phone actually established. Historical build reports are copied verbatim under history/.

## 1.x prototype

Initial concept: a small Android launcher that keeps NordVPN as the user's VPN application, switches Nord to a UK exit, and opens DAZN/NHL.TV in a browser-style container.

This proved the project could be packaged and updated, but it did not provide the Sabres-first catalogue/resume architecture.

## 2.0

Added Sabres schedule, no-spoiler dashboard, local resume model, guarded embedded player and strict reveal rules.

Device playback was unverified.

## 2.1

Restored automatic Nord switching using the Nord country command plus a narrowly scoped accessibility helper.

DAZN sign-in remained problematic.

## 2.2

Tried authentication inside the protected flow with a DAZN sign-in preflight.

Phone result: black/protected screen behavior; sign-in interaction was not usable.

## 2.3

Split DAZN authentication into a dedicated activity.

Phone result: login succeeded, but DAZN redirected to its main page and the selected game did not resume.

## 2.4

Added post-login SPA handoff detection.

Phone result: app reached protected replay UI but stayed at Finding this full replay.

## 2.5

Tried to improve hidden DAZN webpage search and replay detection.

Phone result: Could not confirm this full replay.

Conclusion: hidden-site scraping was the wrong architecture.

## 2.6

Moved toward DAZN structured discovery APIs.

Phone result: DAZN catalogue lookup failed before event routing.

## 2.7

Moved discovery into a separate hidden WebView.

Phone result: generic Protection could not be verified. Later analysis showed that message could overwrite the real earlier failure.

## 2.8

Added stage-specific diagnostics and fixed the misleading status overwrite.

Phone result: catalogue-http-403.

This was the first reliable evidence that DAZN was receiving and rejecting the direct top-level catalogue API navigation.

## 2.9

Changed catalogue transport to a synthetic DAZN-origin browser page using Chromium fetch.

Phone result: catalogue succeeded, but matcher returned ambiguous because multiple DAZN entries matched the same selected game.

This proved the browser-origin catalogue transport could return DAZN data on the phone.

## 3.0

Added same-game variant resolution.

Phone result on Columbus at Buffalo: catalogue-unavailable.

Source review later showed that a matcher none result was being mislabeled and that city-only labels such as Columbus @ Buffalo could be missed.

## 3.1

Added city/location aliases and multiple neutral catalogue queries while preserving the 2.9 transport and 3.0 variant resolver.

Latest phone result: the selected game progressed far enough to display Prepare beginning. Tapping Prepare beginning produced Unexpected position change.

This is the current frontier.

## Current conclusion

The project is not a working DAZN player. It has progressively established several pieces of the flow, but protected playback has never completed on-device.

The next problem is not catalogue discovery. It is the interaction between the spoiler guard's seek model and DAZN's real media player during Prepare beginning.
