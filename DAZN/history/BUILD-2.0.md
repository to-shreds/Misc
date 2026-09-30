# NHL UK 2.0 local build report

Version: `2.0-spoiler-test`, versionCode 2, package `app.nhluk`.
Readiness: IN PROGRESS. This is not a device-verified or confirmed-working DAZN client.

## Actual completed checks

- 17 existing Nord/connection controller regression checks passed.
- 25 catalogue/model checks passed, covering result-field removal, neutral team/date labels, exact replay matching, ambiguous candidates, unsafe links, elapsed-only bookmarks, incompatible resume timelines and playoff-opponent gating.
- 22 offline Chromium checks passed, covering the dashboard, mocked Nord handoff, saved-position controls, inline-important and late-inserted content, poster removal, muted decoded-frame preparation, explicit Play, unexpected seeks, media metadata suppression, live-timeline rejection, changed replay duration, short clips, end-of-video masking, synthetic discovery, ambiguity and neutral sign-in fields.
- Source-level operand/register/type checks passed across 71 native methods. These are not Android ART verification.
- Binary APK ZIP/alignment, AndroidManifest, resources, DEX headers/hashes/pools and the nine intended Javascript-interface method annotations passed independent checks.
- Independent APK Signature Scheme v2 verification passed with RSA-4096. A modified APK was rejected. JAR signature verification also passed; its self-signed-certificate and absent-timestamp warnings are expected for this private build.
- Every packaged asset matches the final source byte-for-byte. No private key is present in the APK.
- The original v1 signing certificate is retained. Certificate SHA-256: `ab3541f1f7b9bf41ab26f5951d4f2672cd71f7ca89678257250a6a9fd54d013a`.

## Limits of the tests

The browser tests use in-memory synthetic pages, simulated location, an explicitly mocked long duration and a silent black fixture video. Host browser restrictions prevented navigation, so CSP was removed for fixture delivery. They do not verify actual asset loading/CSP, real origin/navigation enforcement, Android/native cover compositing, device media controls, NordVPN, live NHL service access, authenticated DAZN discovery/login or Widevine playback.

No actual phone, Android SDK, emulator or ADB was available. The build extends the original local Python DEX assembler and APK packager. Its structural checks do not establish successful installation or playback.

Moving/infinite live timelines and an unconfirmed beginning are deliberately blocked. Delayed viewing of a still-running game is not a verified feature. The app cannot remove spoilers embedded in a broadcast. First acceptance testing must use a game already watched.

## Delivered artifacts

`NHL-UK-2.0-spoiler-test.apk`: 98,719 bytes.
SHA-256: `8354c791b814c816cae2432143226de2a0989468d4deb94fd4c59c8cf3d048db`.

`NHL-UK-2.0-private-update-backup.zip`: 247,596 bytes.
SHA-256: `62cdacf43e2ad409f1b1881eeaa831d5cd5afeeb9f488c953af30a790da5dc3d`.

The APK and private source/signing backup are delivered in chat, not stored in this repository. Available GitHub actions do not provide a binary asset-upload operation. The signing key must not be published or uploaded to a repository. Full source, HANDOFF, local status, reports and the signed build are in the private backup.
