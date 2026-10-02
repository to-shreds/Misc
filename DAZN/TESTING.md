# Testing and evidence rules

## Current Firefox tests

The implementation under `firefox-extension/` has reproducible Node tests, `web-ext lint`, a package build and a synthetic Firefox installed-extension harness. Run the commands in `firefox-extension/README.md`. The current `reports/FIREFOX-IMPLEMENTATION-2026-10-02.md` records the exact results and browser/environment limitations.

Unit and synthetic Firefox success are implementation evidence. They do not certify protected DAZN playback, the first actual broadcast frame, Windows media controls, physical Android, or VPN restoration. Use `firefox-extension/tests/REAL-DEVICE-ACCEPTANCE.md` for the remaining acceptance sequence.

The tests and phone evidence below describe the retired WebView implementation.

## Local test coverage

By 3.1 the local suite included:

- 17 Nord/controller tests;
- 39 catalogue and no-spoiler tests;
- 27 offline Chromium player/shield tests;
- 31 authentication/discovery source-contract checks;
- 22 lifecycle/diagnostic checks;
- native operand/register checks across 120 methods;
- APK/manifest/resources/DEX/accessibility checks;
- APK Signature Scheme v2 verification;
- JAR verification;
- negative tamper rejection.

These tests are useful regression protection. They have not predicted real DAZN behavior reliably enough to establish readiness.

## Important local-test limitations

The browser suite uses synthetic pages and synthetic media fixtures. Host policies have prevented reproducing all real DAZN navigation/origin behavior. There is no local Android emulator/device proving Samsung WebView, Nord accessibility, DAZN DRM, or real playback.

The custom Python DEX assembler and APK packager verify structure and signing, not ART runtime correctness.

## Device evidence hierarchy

Treat direct phone evidence as controlling.

Current major device findings, in order:

- DAZN login became usable after the dedicated Auth activity was introduced.
- A top-level DAZN catalogue API navigation was rejected with HTTP 403.
- The browser-origin fetch transport reached catalogue data.
- Multiple same-game catalogue variants occurred.
- A later search/matching path reached the protected player and displayed Prepare beginning.
- Tapping Prepare beginning produced Unexpected position change.
- No build has revealed and played a protected replay successfully.

## Required acceptance sequence

All player changes must first be tested with a game the user already watched.

Minimum acceptance before any unwatched game:

1. select known game;
2. confirm automatic UK switch;
3. authenticate or reuse session;
4. resolve exact game;
5. remain visually/audio covered;
6. tap Prepare beginning;
7. receive Ready without an unexpected position/source/timeline failure;
8. tap Play;
9. verify first revealed frame is the intended beginning;
10. pause, close, reopen;
11. Prepare resume;
12. verify resume occurs near the saved elapsed position without exposing later content;
13. test rotation/background/foreground;
14. verify no spoiler metadata in system media surfaces;
15. explicitly restore VPN.

## Current next test

Install the Firefox development extension and select a previously watched completed replay. Verify DAZN login, exact route, covered preparation, explicit first-frame/audio release and resume on desktop, then repeat on a physical Android phone. Do not revive the old WebView guard. Do not build the Nord companion until Firefox replay playback passes.
