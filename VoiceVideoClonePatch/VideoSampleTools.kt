package com.voicestudio.mobile.features.voices

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

/** Extracts a bounded AAC voice reference from video, without copying the video. */
object VideoSampleTools {
    private fun requireAudio(extractor: MediaExtractor): Pair<Int, MediaFormat> {
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: error("No audio track found. Choose a video with speech.")
        return track to extractor.getTrackFormat(track)
    }

    suspend fun duration(context: Context, source: Uri): Double = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, source, null)
            val (_, format) = requireAudio(extractor)
            val micros = if (format.containsKey(MediaFormat.KEY_DURATION))
                format.getLong(MediaFormat.KEY_DURATION) else 0L
            val seconds = micros / 1_000_000.0
            require(seconds >= 3.0 && seconds.isFinite()) {
                "This file needs at least three seconds of speech and a readable duration."
            }
            seconds
        } finally { extractor.release() }
    }

    suspend fun extract(
        context: Context, source: Uri, startSeconds: Double, durationSeconds: Double = 12.0
    ): File = withContext(Dispatchers.IO) {
        require(startSeconds.isFinite() && startSeconds >= 0.0)
        require(durationSeconds.isFinite() && durationSeconds in 3.0..15.0)
        val folder = File(context.filesDir, "uploads").apply { mkdirs() }
        val output = File(folder, "video-voice-${UUID.randomUUID()}.m4a")
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var muxerStopped = false
        try {
            extractor.setDataSource(context, source, null)
            val (audioTrack, format) = requireAudio(extractor)
            val mime = format.getString(MediaFormat.KEY_MIME)
            require(mime == MediaFormat.MIMETYPE_AUDIO_AAC) {
                "This video uses unsupported audio ($mime). Please download an MP4 with AAC audio."
            }
            extractor.selectTrack(audioTrack)
            val startUs = (startSeconds * 1_000_000).toLong()
            val endUs = ((startSeconds + durationSeconds) * 1_000_000).toLong()
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val writer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = writer
            val destinationTrack = writer.addTrack(format)
            writer.start()
            muxerStarted = true
            val buffer = ByteBuffer.allocate(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            var firstPts = -1L
            var lastPts = -1L
            var totalBytes = 0L
            while (extractor.sampleTrackIndex >= 0) {
                currentCoroutineContext().ensureActive()
                val pts = extractor.sampleTime
                if (pts < 0 || pts >= endUs) break
                if (pts < startUs) { extractor.advance(); continue }
                val size = extractor.sampleSize
                require(size in 1..buffer.capacity().toLong()) {
                    "Invalid or oversized AAC audio sample."
                }
                buffer.clear()
                val count = extractor.readSampleData(buffer, 0)
                if (count <= 0) break
                if (firstPts < 0) firstPts = pts
                val relativePts = (pts - firstPts).coerceAtLeast(0)
                val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                    MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                info.set(0, count, relativePts, flags)
                buffer.position(0)
                buffer.limit(count)
                writer.writeSampleData(destinationTrack, buffer, info)
                lastPts = relativePts
                totalBytes += count
                if (!extractor.advance()) break
            }
            require(lastPts >= 2_800_000L && totalBytes > 1024) {
                "Not enough audio in this selection. Choose another point in the video."
            }
            writer.stop()
            muxerStopped = true
            writer.release()
            muxer = null
            require(output.isFile && output.length() > 1024) {
                "Could not save the audio clip."
            }
            output
        } catch (failure: Throwable) {
            output.delete()
            throw failure
        } finally {
            muxer?.let { active ->
                if (muxerStarted && !muxerStopped) runCatching { active.stop() }
                runCatching { active.release() }
            }
            extractor.release()
        }
    }
}
