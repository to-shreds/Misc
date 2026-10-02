# Firefox 0.1.0 implementation and verification

Date: 2026-10-02. State: **IN PROGRESS**. The controlling architecture remains `../CODEX_FIREFOX_HANDOFF.md`.

## Built

The first shared Firefox extension implements the responsive protected shell, neutral NHL schedule, real-form sign-in handling, DAZN structured discovery, exact event/asset routing, covered player transactions, decoded-frame verification, explicit Play, conservative resume and fixed diagnostics. It includes desktop/Android MV3 configuration, native-control suppression, neutral Media Session writes, protection against exposed source/seek changes and disabled embedded media.

There are no runtime dependencies, cookies permission, analytics, stream relays, extracted media/license URLs, credentials or signing material. Development dependencies are pinned in `package-lock.json`. All original legacy files remain available.

## Verification actually completed

| Check | Observed result |
| --- | --- |
| `npm test` | 81 tests passed, 0 failed |
| `npm run lint` | 0 errors, 0 warnings, 0 notices |
| Installed-extension synthetic Firefox suite | 14 checks passed in actual Firefox 153.0 on Linux |
| Public NHL and DAZN data smoke check | 88 sanitized games, 3 exact matching games, 5 compatible variants |
| `npm run build` | Unsigned development ZIP built and contents inspected |
| `git diff --check` | No whitespace errors |

The browser suite installs the extension using `web-ext`'s `RemoteFirefox.installTemporaryAddon`, then drives Firefox using Playwright. It runs the production source, manifest host matches, MAIN/isolated worlds and USER stylesheet, with one added test-only isolated probe. No production JS is injected through the page driver. The probe captures the closed shell for UI actions and separately exercises the player API with actual decoded synthetic WebM frames.

Passed browser checks cover first-provider-script/paint shielding, responsive layout, recognized auth form, synchronous SPA auth return, neutral metadata, the full schedule/catalogue/route/Prepare/Play/Pause/reload/resume/Close flow, delayed media, repeated provider seeks, source replacement, element replacement, covered resume, exposed seek failure, ambiguous media and unavailable DVR beginning.

The desktop and mobile viewport screenshots were inspected. Provider content remained hidden and the neutral synthetic schedule fit both sizes. A mobile viewport is not a phone.

Machine-readable results and synthetic screenshots are retained in `firefox-0.1.0/`. The fixtures contain no provider account or actual game result.

## Environment-specific testing limitation

This nested container denied Firefox content/media subprocess user-namespace setup. Testing used these explicit environment flags only for the disposable, trusted, local-fixture browser process:

```sh
MOZ_DISABLE_CONTENT_SANDBOX=1 MOZ_DISABLE_RDD_SANDBOX=1 npm run test:firefox
```

The fixture tunnel accepts only the four test provider hostnames, terminates them at a local HTTPS fixture and rejects every other destination. No external provider traffic is forwarded in that profile. The extension, manifest and normal `web-ext run` scripts do not set those flags. Normal desktop/CI runs should retain the browser's default sandbox. The JSON report records both overrides.

An initial decoder-process crash looked like preparation failure. After correcting the test environment, fully hidden video produced decoded callbacks; cover CSS was not weakened. Actual testing then exposed and fixed a source-identity race where Firefox retained old `currentSrc` while a new `src` loaded. Preparation now invalidates both source-attribute and metadata changes before accepting decoded evidence.

## Package

`../artifacts/nhl-uk-firefox-0.1.0-unsigned.zip`

SHA-256:

```text
9e36017eb1e62ec6d15d2661db635134d53d0f228ab8bdc4b15dc1f7d05e38c8
```

The ZIP contains the manifest and production source/styles only, plus empty directory entries. It excludes tests, fixtures, reports, development dependencies and package files. It is unsigned and does not provide persistent installation in standard Android Firefox.

## Not established

No authenticated DAZN session was available in the test profile, and no Windows device or physical Android phone was attached. Ordinary DAZN login, entitlement/DRM, a correct real broadcast's first frame/audio, OS duration/progress, fullscreen and background/rotation behavior remain unverified. The current embedded-frame block may prevent CAPTCHA or other iframe-dependent login flows. The fixture tests do not resolve that provider question.

Still-running live/DVR and playoff progression are not enabled. The thin Android Nord launcher and Mozilla signing/distribution are intentionally deferred under the controlling handoff until real completed-replay playback is proven. The next action is `../firefox-extension/tests/REAL-DEVICE-ACCEPTANCE.md` on a previously watched game, first Windows Firefox, then physical Firefox Android.
