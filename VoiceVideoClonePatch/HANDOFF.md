# HANDOFF.md

## Objective
Build an Android app that turns a downloaded YouTube video of the user's speech into a short reference for OmniVoice, then saves a reusable voice and synthesizes new speech.

## Authoritative sources
- Patch: this folder, `VoiceVideoClonePatch/` in `to-shreds/Misc`.
- Upstream model/app: `debpalash/VoiceStudio` tag `v0.5.7`.
- Status: `to-shreds/ProjectStatus/projects/voice-video-clone/STATUS.md`.

## Implementation
- `VideoSampleTools.kt`: Android AAC-only track extractor with bounded 12-second sample, using a storage-provider URI and platform media codecs.
- `patch_voicestudio.py`: anchored changes to the upstream native clone screen, opening video in a picker, providing a clip-start slider and preview.
- `../../.github/workflows/voicevideo-android.yml`: remote Android debug APK build. External model packs remain user-selected downloads.

## Do not break
- Preserve upstream clone screen and actual local Whisper/OmniVoice inference.
- Never claim a voice is fine-tuned: these are zero-shot profiles.
- Do not silently select other people's voices: currently user picks the sample.
- Keep video on device; don't create a network service or YouTube downloader.
- Patch must fail closed if upstream anchors change.

## Verification and next action
**Verified benchmark (2026-11-08):** GitHub Actions [run 42514430937](https://github.com/to-shreds/Misc/actions/runs/42514430937) completed successfully, including `assembleDebug` and artifact upload, for implementation commit `5a5e3fadd76063a1c3fb1e6e7f8434cebab81fb6`. APK SHA-256 `31841c341e6c60bbb134cfc76a95db54b943fa3e7697ac33cad177a1982819bc`; 146,364,654 bytes. The APK ZIP passed integrity checks, the compiled `VideoSampleTools` class is present, the native `libvs_omni.so` libraries are present, and the binary manifest includes distinct package ID `sh.toshreds.voicevideoclone`. Android APK signature block marker exists, although signing has not been independently verified with `apksigner`.

**Next action:** Perform real-device acceptance: import a downloaded AAC MP4, choose a clean reference, preview it, clone, synthesize speech, and verify cleanup, cancellation, error recovery and output quality. Automatic selection among speakers is not implemented. Review ProjectStatus before any new work.
