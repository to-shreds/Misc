# NHL UK Firefox development build

One Firefox MV3 extension for desktop and Android. This is an implementation and test build, not an accepted player for unwatched hockey. DAZN subscription playback, Windows system controls and physical Android behavior still require acceptance evidence.

## Development

Requires Node 20 or newer and Firefox 142 or newer. No runtime JavaScript dependencies, telemetry, remote database or cloud resume storage.

```sh
npm ci
npm test
npm run lint
npm run build
npm start
```

`npm start` uses a temporary Firefox profile. Log into DAZN in that profile through the extension's **DAZN sign in** button. Connect Nord to the UK yourself for this development test. The package generated in `dist/` is unsigned and is not a persistent Android installation. Mozilla signing is deliberately deferred until real playback passes.

For a connected physical Android device with Firefox installed and USB debugging authorized:

```sh
npm run start:android -- --android-device=DEVICE_SERIAL
```

Use `adb devices` to identify the serial. No phone is assumed to be attached. Desktop results do not certify Android.

## Browser regression tests

```sh
npx playwright install --with-deps firefox
npm run test:firefox
```

The harness also requires `openssl`. Its committed synthetic WebM can be regenerated with the `ffmpeg` command in `tests/fixtures/README.md`. It creates a temporary synthetic HTTPS provider, isolated test profile and temporary extension copy. A test-only probe exercises the controller inside the extension's isolated world. The production package excludes that probe and fixtures. This is genuine media decoding and synthetic provider behavior, not DAZN DRM or entitlement verification. See the current implementation report for the exact driver and results actually obtained. The fixture README also documents the explicitly scoped workaround used for this container's Firefox process restrictions.

An optional current public-data check is `npm run check:public-data` (Python 3 required). It fetches one neutral NHL season and one neutral DAZN search, printing aggregate counts only. It never authenticates or attempts playback.

## Implemented flow

The document-start USER stylesheet conceals provider content. A responsive in-page shell lists only neutral scheduled matchup and date information. Select a previously watched game, find its exact replay, open the selected provider event/asset route, prepare it behind the cover, then press a separate Play button. Skip and restart also prepare behind cover and require Play again.

The primary catalogue transport is a DAZN-page fetch without credentials. **Extension connection** is an explicit alternative using the same matching logic in the background context; it is not a claim that provider responses or entitlements will be equivalent. Unknown payloads and mismatches remain covered.

Bookmarks contain only the game and variant identity, elapsed watched seconds and a local timestamp. A bookmark is reused only for the exact compatible variant. Closing, pausing and page hiding save authorized watched positions. Diagnostics use fixed vocabulary and contain no provider prose, scores, URLs, IDs, video timing, IP addresses or credentials.

## Intentional limits

- Only preseason and regular-season schedule rows are shown. Playoff rows stay hidden until series-watched controls exist.
- Live candidates are not opened. Still-running DVR acceptance follows completed replay acceptance.
- Only top-level HTML video is supported. Embedded frames and plugin objects are blocked to prevent unguarded audio; DAZN authentication widgets requiring an iframe may consequently fail covered.
- The real DAZN form is used on an exact sign-in route. Only its credential controls and labels are exposed. Unknown auth routes, CAPTCHA flows and other account screens require inspection before support is added.
- The extension disables provider controls, Picture-in-Picture and provider fullscreen. Its own fullscreen uses the protected document.
- Media Session metadata and position writes are neutralized. Browser-generated OS duration indicators and arbitrary Web Audio output still require real platform verification.
- Provider routes establish selected event/asset identity, not proof that an ad or replacement video belongs to that event. Real watched-game tests must establish the actual player lifecycle before use on unwatched games.
- The Android Nord launcher is not started, as required by the controlling handoff until Firefox playback is proven.

Read `../CODEX_FIREFOX_HANDOFF.md`, `../NO_SPOILERS.md`, `../HANDOFF.md` and `../STATUS.md` before continuing work.
