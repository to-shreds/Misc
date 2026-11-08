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
A local source-only check is complete; real Android compilation is **not yet verified**. Inspect GitHub Actions build status and logs. After a successful APK compile, test with an AAC MP4 on actual Android hardware: seek into the video, hear the reference, clone, and synthesize. Investigate unsupported codecs, empty clips, and interrupted preparation. Update ProjectStatus only after accurately evaluating these checks.
