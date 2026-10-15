# WebM to MP4 Android App

Offline, simple WebM to MP4 converter. Choose a local .webm file, select **Lossless** (default) or **CRF** 1–35 (default 18), tap Convert, select a destination in Android's file picker, and keep the app open during encoding.

- **Lossless:** \`libx264 -qp 0\` for H.264 video and ALAC for audio. These are lossless encodes of decoded video/audio frames. Files may be *many times* larger than the original; some hardware video players do not handle lossless H.264.
- **CRF:** H.264/yuv420p + AAC at 192 kb/s. CRF 18 is high quality but is not lossless.
- **Storage:** Both source and output are streamed using Android Storage Access Framework (SAF). **No full-size intermediate copy is written.** A fragmented MP4 can be written to non-seekable SAF destinations. Some MP4 editors may require remuxing fragmented MP4 before editing. The selected destination must have enough free space; a lossless 3 GB WebM can result in an MP4 many times larger than 3 GB.
- **Privacy:** No Internet or broad storage permissions.
- **Cancellation:** FFmpeg is cancelled, and the app attempts to delete the partial output.

## Crash fix in version 1.1

Version 1.0 bundled \`ffmpeg-kit-min-gpl:7.1.6\` which upstream later documented could not be loaded on some Android devices due to NDK r26c ELF RELRO protection problems. The 8.1.9 min-gpl replacement uses r27c and fixes loading. Library initialization now occurs off the UI thread, and linker failures become visible errors rather than immediate uncaught exceptions. Input metadata extraction is also off the UI thread.

## Build

Java 17, Android SDK platform 35, Gradle 8.10.2. Run \`gradle -p WebmToMp4 :app:assembleDebug\`. CI runs a host-side command-vector test and an instrumented end-to-end conversion test on an API 35 Android emulator. Then it publishes \`WebmToMp4.apk\` in GitHub Actions and the GitHub release.

**Important when updating from v1.0:** GitHub Actions generates a new random debug keystore for each build. Android will therefore report an incompatible signature if you try to install the updated package over the original. Uninstall the original "WebM to MP4" before installing v1.1. There are no app settings to lose.

Application code is GPL-3.0-or-later in this directory (matches x264 GPL license).
