# WebM to MP4 (Android v1.2)

Choose a WebM, select Lossless (default) or CRF 1–35, tap Convert, and pick an MP4 output location. No internet or uploads.

## Background conversion

Conversion is now owned by an Android foreground service and **continues when you press Home, switch apps, lock your phone, or remove the UI from Recents**, instead of relying on the Activity. A persistent progress notification provides a Cancel action and a shortcut back to the app. Opening the app shows the running progress. Android 13+ asks for notification permission. Even if denied, the conversion continues, although its notification may be hidden from the standard notification drawer.

A partial CPU wake lock keeps processing while the screen is off. It is released when the service completes, fails, is cancelled or is destroyed. Android 15+ imposes an approximately six-hour total media-processing foreground-service budget per 24 hours. The app handles this time limit, but Android user force-stop, extreme memory pressure, or restrictive OEM battery controls can still terminate processing. It does not resume an interrupted encoding after process death.

Input and output use Android Storage Access Framework streams, so a 3 GB WebM does not need to be copied into the app's cache. Fragmented MP4 supports non-seekable destinations. Source file is never modified. A failed or cancelled conversion attempts to delete the partially written destination.

## Codec modes

- **Lossless:** H.264 at QP 0 with ALAC audio. Output may be many times the size of the input and not playable on some hardware players.
- **CRF:** H.264/yuv420p video and AAC audio, CRF 1–35 with 18 as default; lower CRF means higher quality and larger MP4.

## Build / testing

Java 17, Gradle 8.10.2, Android SDK 35. Build with gradle -p WebmToMp4 :app:assembleDebug. CI runs Java argument-vector tests, checks native 16 KB alignment, compiles Android instrumentation tests, signs the APK, and publishes to the repo release.

Manual Android emulator conversion testing is defined in the GitHub Actions workflow but did not complete previously because Android SDK system image downloading timed out. Passing compilation does not establish that 3 GB background conversion has been empirically tested on a device.

**Install updates:** CI uses ephemeral debug-signing keys. Uninstall the prior WebM to MP4 build if Android rejects the new APK due to an incompatible signature. No user settings are stored.

Android app source in this directory is GPL-3.0-or-later.
