# Android runtime verification

This separate, test-only instrumentation package inspects the unmodified signed release app. It is not bundled into the tester APK. Use an isolated emulator or test device, never a live-account session.

`build-test.sh` compiles the instrumentation with Android platform 35/build-tools 35.0.0 and signs it with the app's existing private signing identity. Install the release APK first, then install `build/SantaFe-runtime-verification.apk` with `adb install -t -r`. Run `adb shell am instrument -w com.jon.santafelab.runtime/.RuntimeVerification`.

The suite checks bundled asset loading, the native handshake and callback, enabled login inputs, hidden extension setup, WebView restrictions, protected screen capture, rejection of a non-Hyundai destination before any connection, and form/session clearing across stop/restart. Its synthetic input values are never submitted. It does not validate Hyundai login, execute car commands, or establish successful file-picker writes.

Compilation passed for version 0.3.0. Execution is currently unverified because the software emulator did not finish booting within the bounded attempt. See `../../../docs/verification/android-verification.json` for the release's actual evidence and limits.
