# VoiceVideoClonePatch

This is a proposed modification to the open-source **VoiceStudio v0.5.7 Android application**, which uses OmniVoice locally. It adds selecting downloaded YouTube videos, trimming an audio reference, previewing the sample, saving the voice and then generating new speech in VoiceStudio.

## Use

1. In the YouTube Studio app, find one of your uploaded videos under Content, open its three-dot menu, and tap **Save to device**.
2. Install the patched VoiceStudio APK once the GitHub Actions build succeeds.
3. Open **Voices** and **Clone**, enter a voice name, confirm consent, choose **Pick File** and select your video.
4. Move the start-time slider to a clean portion where only you speak. Tap **Use this voice sample**, listen to the preview, then **Clone**.
5. Open **Speak**, select the new voice profile, enter text, and synthesize audio.

## Scope and limitations

- The selection is manual, not speaker detection. One good 5-to-15-second sample usually works better than feeding hours of talking into a zero-shot voice model.
- The source video must have an AAC audio track. Its audio reference is extracted offline using Android MediaExtractor/MediaMuxer. Video is not uploaded to a server.
- The app does not download video from YouTube links. Use the platform's own Save to device feature for your own uploads.
- This builds a developer/test APK from the pinned upstream v0.5.7 source. It requires on-device model downloads separately and is not signed by upstream VoiceStudio.
- Testing voice quality and device performance requires real Android hardware. A successful CI compilation alone does not prove the complete cloning flow works.

## Build

GitHub Actions workflow: `.github/workflows/voicevideo-android.yml`. It clones `debpalash/VoiceStudio` tag `v0.5.7`, applies `patch_voicestudio.py` and builds `assembleDebug`. Use the resulting workflow artifact APK.

This add-on follows the AGPL-3.0 terms of the upstream VoiceStudio code. The OmniVoice **pretrained model weights** have separate CC BY-NC terms, so check those terms before commercial use.
