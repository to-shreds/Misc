# Actual provider and device acceptance

All items are pending unless a dated observation is recorded. Only use a game already watched. Never substitute synthetic tests for these results. Do not put provider screenshots, page text, credentials, raw catalogue responses or spoiler metadata into the public repository.

## Desktop Firefox on Windows

- Install the development extension and grant the declared DAZN/NHL host permissions. Confirm protection on the first usable DAZN paint and after reload.
- Manually connect Nord to the UK. Verify the neutral schedule and select the known completed game.
- Use the ordinary DAZN sign-in form. Verify email/password steps and redirect. Any unrecognized authentication route must stay covered. Record whether an iframe/captcha is required; the current build deliberately blocks embedded widgets.
- Resolve the selected game through page-origin catalogue fetch. If testing the explicit background alternative, record it separately.
- Confirm the exact selected event/asset route opens and the video remains covered/muted. Record fixed diagnostic vocabulary only.
- Prepare beginning. Confirm Ready while covered. Tap Play and independently check that the first video frame and audio are the intended beginning.
- Test Pause, Play, backward/forward preparation, Restart, Close, and protected document fullscreen. No provider timeline or total length may appear.
- Close/reopen, prepare the saved position and verify resume. Change tab, background Firefox and return.
- Inspect Windows media controls and Bluetooth surfaces for neutral title/artwork and absence of duration/progress. A browser-generated duration display is a failure even if DAZN's own `setPositionState` was neutralized.
- Test provider source/media replacement and ending the known video. No autoplay-next frame or audio may appear.

## Firefox on a physical Android device

Repeat every applicable desktop item using `npm run start:android -- --android-device=SERIAL`. Additionally check touch layout, rotation, foreground/background, lock screen, notification, Bluetooth and native Picture-in-Picture affordances. Record device, Android version, Firefox version and extension version without private identifiers.

Desktop mobile viewport tests only measure responsive layout. They do not satisfy this section.

## Subsequent gates

Only after completed replay succeeds on both platforms, implement and verify still-running DVR from the actual beginning. A missing beginning must fail covered; never fall back to live. Only after extension playback is proven, build the thin Android launcher, reuse the established Nord automation, and verify explicit Restore VPN.

Then decide Mozilla signing and the simplest persistent Android installation path. Do not present the unsigned development ZIP as an installable production Android add-on.
