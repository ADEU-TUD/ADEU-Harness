package io.github.thepytales.llmhub.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/** Records microphone audio to an m4a file for transcription. */
class Recorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    fun start() {
        val out = File(context.cacheDir, "recording.m4a").apply { delete() }
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(16000)
        r.setAudioEncodingBitRate(64000)
        r.setOutputFile(out.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        file = out
    }

    /** Loudness since the last call, 0..1 (for the voice mode: has the user stopped speaking?). */
    fun level(): Float = runCatching { (recorder?.maxAmplitude ?: 0) / 32767f }.getOrDefault(0f)

    /** Stops recording and returns the file, or null if nothing was recorded. */
    fun stop(): File? {
        val r = recorder ?: return null
        recorder = null
        return try {
            r.stop()
            file
        } catch (e: RuntimeException) {
            null
        } finally {
            r.release()
        }
    }
}

/** Plays audio files (synthesized speech); one at a time. */
class AudioPlayer(private val context: Context) {
    private var player: MediaPlayer? = null

    /** Plays [bytes] and returns when playback has finished or was stopped. */
    suspend fun play(bytes: ByteArray) {
        stop()
        val f = File(context.cacheDir, "speech_${System.nanoTime()}.mp3").apply { writeBytes(bytes) }
        try {
            suspendCancellableCoroutine<Unit> { cont ->
                val p = MediaPlayer()
                player = p
                p.setDataSource(f.absolutePath)
                p.setOnCompletionListener { if (cont.isActive) cont.resume(Unit) }
                p.setOnErrorListener { _, _, _ ->
                    if (cont.isActive) cont.resume(Unit)
                    true
                }
                p.prepare()
                p.start()
                cont.invokeOnCancellation { stop() }
            }
        } finally {
            stop()
            f.delete()
        }
    }

    fun stop() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
    }
}
