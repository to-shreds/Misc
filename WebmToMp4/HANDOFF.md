# WebM to MP4 Android handoff

## Goal
A deliberately simple Android WebM to MP4 converter for local videos as large as 3 GB. Default lossless H.264 QP0 + ALAC, optional CRF 1–35 H.264/AAC. **It must keep converting when the UI leaves the foreground, the phone locks, or the Activity is removed from Recents.** Never require the user to watch an open app during transcoding.

## Controlling sources
Repository: \`to-shreds/Misc\`. Authoritative Android project: \`WebmToMp4/\`. GitHub Actions:
- \`.github/workflows/webm-to-mp4.yml\` builds/signs the production-installable debug APK and publishes it to the release tag \`webm-to-mp4-v1\`.
- \`.github/workflows/webm-to-mp4-smoke.yml\` separately attempts emulator conversion tests, including a switch to background.
- \`app/src/androidTest/java/com/toshreds/webmtomp4/ConversionSmokeRunner.java\` exercises real WebM-to-MP4 transcoding on emulator.

## Current architecture, v1.2
\`MainActivity\` selects source/output through ACTION_OPEN_DOCUMENT and ACTION_CREATE_DOCUMENT, asks for notification permission on Android 13+, starts \`ConversionService\` via \`startForegroundService\`, and polls its immutable volatile \`Snapshot\` for progress while visible. It **never owns the native FFmpeg session**.

\`ConversionService\` owns FFmpegKit 8.1.9, SAF read/write file handles, cancellation, and notification progress/completion. On Android 15+ it declares and starts the \`mediaProcessing\` foreground service type; on older versions it uses \`dataSync\`. A partial CPU wake lock protects ongoing processing during screen-off and is released at termination. Notification has a Cancel action and activity return link. \`android:stopWithTask=false\` intentionally preserves service when Activity is swiped away. Android's 6h/24h mediaProcessing budget is handled through \`onTimeout\`. Android force stop or process death is not resumable.

\`CommandBuilder.java\` uses fragmented MP4 so streaming directly to non-seekable Android SAF destination avoids an extra multi-GB temp file. No Internet or blanket storage permission. Errors/cancellation attempt to remove partial destination.

## Nonnegotiable constraints
- Never regress offscreen/background conversion or the cancellation notification.
- No mandatory cloud service, Render bandwidth, internet traffic, or copying 3 GB to app cache.
- Preserve lossless default and optional CRF slider, simple one-screen UI.
- Android 15+ mediaProcessing services have a system-enforced six-hour limit; do not promise unlimited run duration or resurrection after force stop.
- CI debug signing keys change each build: installing newer version may require uninstalling the old APK; do not claim seamless signature-compatible upgrades.

## Latest verification
- APK produced by GitHub Actions run \`38663958764\` at source commit \`554f423c9238fd393863a629edde8db8367c7460\`, unit command checks, architecture wiring checks, Android build, ARM64 16 KB alignment, signing, and androidTest compilation all passed.
- APK downloaded to ChatGPT from release build and stored as \`/WebmToMp4.apk\` in Library and \`/mnt/data/WebmToMp4-Background-v1.2.apk\` in the current conversation sandbox. SHA-256: \`00350d4771eae3851e400c536492c1d8bd0df8ccf7d7f5f01c534c744a24247e\`.
- Runtime emulator test was added in subsequent code-only/test-only commits. It is not yet confirmed by device execution. Do not conflate compile/architecture checks with tested screen-off behavior.

## Remaining next step
Check the Android emulator workflow run for concrete runtime results; if it cannot finish due CI emulator provisioning, report that testing limitation explicitly. The definitive real-world validation is a multi-GB WebM conversion while device screen is off and the user switches apps. If errors persist, inspect Android Logcat and avoid assuming it is a battery-setting problem.

## Artifact
The current direct installer is in ChatGPT Library as \`WebmToMp4.apk\`; GitHub also publishes \`https://github.com/to-shreds/Misc/releases/tag/webm-to-mp4-v1\`.
