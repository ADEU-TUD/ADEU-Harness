package io.github.thepytales.llmhub.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

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

/** Plays synthesized speech; only one message plays at a time. */
class AudioPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private val _playingId = MutableStateFlow<String?>(null)
    val playingId: StateFlow<String?> = _playingId.asStateFlow()

    fun play(id: String, bytes: ByteArray) {
        stop()
        val f = File(context.cacheDir, "speech_$id.mp3").apply { writeBytes(bytes) }
        val p = MediaPlayer()
        p.setDataSource(f.absolutePath)
        p.setOnCompletionListener { stop() }
        p.prepare()
        p.start()
        player = p
        _playingId.value = id
    }

    fun stop() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        _playingId.value = null
    }
}
