#!/usr/bin/env python3
"""Apply a small, fail-closed video-import patch to VoiceStudio v0.5.7."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve() if len(sys.argv)>1 else Path("upstream").resolve()
base = Path("VoiceVideoClonePatch")
screen = root / "mobile/android/features/src/main/kotlin/com/voicestudio/mobile/features/voices/VoicesScreen.kt"
destination = screen.with_name("VideoSampleTools.kt")
source = screen.read_text()

changes = [
    ('import android.Manifest',
     'import android.Manifest' + chr(10) + 'import android.net.Uri'),
    (
        '    var sample by rememberSaveable {mutableStateOf<String?>(null)}',
        '''    var sample by rememberSaveable {mutableStateOf<String?>(null)}
    var videoUri by remember {mutableStateOf<Uri?>(null)}
    var videoDuration by remember {mutableFloatStateOf(0f)}
    var videoStart by remember {mutableFloatStateOf(0f)}
    var sampleBusy by remember {mutableStateOf(false)}'''
    ),
    (
        '''    val pick=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri ->
        if(uri!=null) scope.launch {try {sample?.let {File(it).delete()};sample=copyMedia(context,uri,"audio").absolutePath} catch(e:Exception) {vm.fail(e)}}
    }''',
        '''    val pick=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri ->
        if(uri!=null) scope.launch {
            try {
                sample?.let {File(it).delete()}; sample=null; videoUri=null
                val length=VideoSampleTools.duration(context,uri)
                val mime=context.contentResolver.getType(uri).orEmpty()
                if(mime.startsWith("video/") || length>29.0) {
                    videoUri=uri; videoDuration=length.toFloat(); videoStart=0f
                } else {
                    sample=copyMedia(context,uri,"audio").absolutePath
                }
            } catch(e:Exception) {vm.fail(e)}
        }
    }'''
    ),
    (
        '''        Secondary("mobile.voices.pick_file",!recording) {pick.launch(arrayOf("audio/*"))}''',
        '''        Text("Choose a recording or a downloaded YouTube video. Clean, solo speech works best.")
        Secondary("mobile.voices.pick_file",!recording&&!sampleBusy) {pick.launch(arrayOf("audio/*","video/*"))}
        if(videoUri!=null) {
            Text("Choose a point with only your voice and no music.")
            val clipLength=minOf(12f,videoDuration)
            Text("Start: ${formatDuration((videoStart*1000).toLong())}  ·  End: ${formatDuration(((videoStart+clipLength)*1000).toLong())}")
            if(videoDuration>clipLength) Slider(value=videoStart,onValueChange={videoStart=it},valueRange=0f..(videoDuration-clipLength))
            Button(onClick={
                val chosen=videoUri ?: return@Button
                scope.launch {
                    sampleBusy=true
                    try {
                        sample?.let {File(it).delete()}
                        sample=VideoSampleTools.extract(context,chosen,videoStart.toDouble(),clipLength.toDouble()).absolutePath
                    } catch(e:Exception) {vm.fail(e)}
                    finally {sampleBusy=false}
                }
            },enabled=!sampleBusy&&!recording) {Text(if(sampleBusy) "Preparing clip…" else "Use this voice sample")}
        }'''
    ),
    (
        '''        if(sample!=null&&!recording) Text(tr("mobile.android.sample_ready"))''',
        '''        if(sample!=null&&!recording) {
            Text(tr("mobile.android.sample_ready"))
            LocalAudioPlayer(File(sample!!),vm::fail)
        }'''
    ),
    (
        '&&phone.progress==null&&(location==RunLocation.COMPUTER||cloneReady)) {',
        '&&phone.progress==null&&!sampleBusy&&(location==RunLocation.COMPUTER||cloneReady)) {'
    )
]
for before, after in changes:
    n=source.count(before)
    if n!=1: raise SystemExit(f"Patch aborted: upstream anchor count {n}, expected one: {before[:100]!r}")
    source=source.replace(before, after, 1)
screen.write_text(source)
destination.write_text(base.joinpath("VideoSampleTools.kt").read_text())

# Use a distinct install identity and display name; don't overwrite the official VoiceStudio.
gradle = root / "mobile/android/app/build.gradle.kts"
gradle_source = gradle.read_text()
for before, after in [
    ('applicationId = "sh.voicestudio.mobile"', 'applicationId = "sh.toshreds.voicevideoclone"'),
    ('val label = (en["app"] as Map<*, *>)["name"].toString()', 'val label = "VoiceStudio Video Clone"')
]:
    if gradle_source.count(before) != 1:
        raise SystemExit(f"Patch aborted: Gradle anchor not unique: {before}")
    gradle_source = gradle_source.replace(before, after, 1)
gradle.write_text(gradle_source)
print("Patched", screen, destination, "and", gradle)
