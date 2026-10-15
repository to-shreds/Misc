# WebM to MP4 (Android)

Simple on-device WebM to MP4 converter. Select a .webm video, use default **Lossless** or switch to **CRF** (1–35, default 18), select where to save, and convert. No account, ads, storage permissions, or Internet permission.

The lossless preset uses H.264 -qp 0 and ALAC audio. Output can be *much* larger than the source, and lossless H.264 cannot be decoded by every Android player. CRF preset uses H.264/AAC with yuv420p for greater playback compatibility. Source colorspace/bit-depth conversions may occur for uncommon files; for common 8-bit WebM sources this preserves decoded video frames.

SAF input is streamed; output is created in app cache and then saved to the selected document location. You need free disk space for the MP4 during conversion. Keep the app open.

Build: Java 17, Android SDK 35, Gradle 8.10.2; run gradle :app:assembleDebug here. GitHub Actions builds a debug-signed APK and uploads it to the repository's webm-to-mp4-v1 release. Each CI build uses a fresh debug signing key, so when updating, you might need to uninstall the old APK before installing a replacement.

GPL-3.0-or-later source under this folder, to comply with FFmpegKit's GPL/x264 components.
