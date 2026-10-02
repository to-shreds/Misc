The page and media are synthetic. No provider account, programme, title, result, or private data is included.

`replay.webm` is a 12-second silent test pattern, created with:

```sh
ffmpeg -hide_banner -loglevel error -y -f lavfi -i testsrc2=size=320x180:rate=30 -t 12 -c:v libvpx-vp9 -b:v 90k -an tests/fixtures/replay.webm
```

The browser harness runs the installed extension with an additional test-only isolated-world command listener. Production JavaScript, styles, origins, and match patterns remain unchanged. A local fixture-only CONNECT tunnel serves the DAZN and schedule hostnames from the local HTTPS fixture inside this disposable Firefox profile; it refuses all other destinations and never forwards external traffic. TLS material is generated under the system temporary directory and deleted after the run. No external provider request or credential is required.

Run from `firefox-extension` with Node 20+, OpenSSL and Playwright Firefox installed:

```sh
npm ci
npx playwright install --with-deps firefox
npm run test:firefox
```

The suite installs a real temporary WebExtension using web-ext's `RemoteFirefox.installTemporaryAddon` API, then drives it with Playwright. The test-only probe captures the closed shell shadow root and the real shield instance before bootstrap. It provides fixture commands without exposing a production debugging interface. UI events exercise the production shell callbacks, background messaging, catalogue resolution, tab navigation and storage. Media tests use native WebM decoding and `requestVideoFrameCallback`, plus synthetic DVR range overrides for the missing-beginning case.

On this nested Linux execution environment, Firefox's child-process user namespace setup failed with `uid_map: EPERM`. The successful local fixture run used these explicit per-process browser settings:

```sh
MOZ_DISABLE_CONTENT_SANDBOX=1 MOZ_DISABLE_RDD_SANDBOX=1 npm run test:firefox
```

Those settings are never set by the test code, extension, or normal launch scripts. They are only a workaround for this trusted fixture process; the surrounding execution sandbox remains active. The generated JSON records whether either setting was present. Normal desktop and CI hosts should first run with their default browser sandbox.

Screenshots and `firefox-synthetic.json` are generated under ignored `reports/`. A mobile viewport is a responsive layout test on desktop Firefox. This suite does not prove physical Android behavior, Windows media surfaces, Mozilla signing, DAZN login, DRM, real provider playback, or VPN operation.
