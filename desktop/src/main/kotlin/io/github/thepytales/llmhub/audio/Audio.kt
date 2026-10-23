package io.github.thepytales.llmhub.audio

import android.content.Context
import javazoom.jl.player.advanced.AdvancedPlayer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineEvent
import javax.sound.sampled.TargetDataLine
import kotlin.coroutines.resume

/** Records microphone audio to a WAV file (16 kHz mono) for transcription. */
class Recorder(private val context: Context) {
    private val format = AudioFormat(16_000f, 16, 1, true, false)
    private var line: TargetDataLine? = null
    private var thread: Thread? = null
    private var buffer = ByteArrayOutputStream()

    fun start() {
        val info = DataLine.Info(TargetDataLine::class.java, format)
        if (!AudioSystem.isLineSupported(info)) throw IllegalStateException("Kein Mikrofon gefunden.")
        val l = AudioSystem.getLine(info) as TargetDataLine
        l.open(format)
        l.start()
        buffer = ByteArrayOutputStream()
        val out = buffer
        line = l
        thread = Thread({
            val chunk = ByteArray(4096)
            while (true) {
                val n = l.read(chunk, 0, chunk.size)
                if (n <= 0) break
                synchronized(out) { out.write(chunk, 0, n) }
            }
        }, "recorder").apply { isDaemon = true; start() }
    }

    /** Stops recording and returns the file, or null if nothing was recorded. */
    fun stop(): File? {
        val l = line ?: return null
        line = null
        l.stop()
        l.close()
        thread?.join(1000)
        thread = null
        val bytes = synchronized(buffer) { buffer.toByteArray() }
        if (bytes.size < format.frameSize * 1600) return null // under 0.1 s
        val file = File(context.cacheDir, "recording.wav").apply { delete() }
        AudioInputStream(ByteArrayInputStream(bytes), format, (bytes.size / format.frameSize).toLong()).use {
            AudioSystem.write(it, AudioFileFormat.Type.WAVE, file)
        }
        return file
    }
}

/** Plays audio (synthesized speech: MP3 from the server, WAV from Windows); one at a time. */
class AudioPlayer(@Suppress("UNUSED_PARAMETER") context: Context) {
    @Volatile
    private var mp3: AdvancedPlayer? = null

    @Volatile
    private var clip: javax.sound.sampled.Clip? = null

    /** Plays [bytes] and returns when playback has finished or was stopped. */
    suspend fun play(bytes: ByteArray) {
        stop()
        val wav = bytes.size > 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF"
        try {
            if (wav) playWav(bytes) else playMp3(bytes)
        } finally {
            stop()
        }
    }

    private suspend fun playMp3(bytes: ByteArray) = suspendCancellableCoroutine { cont ->
        val player = AdvancedPlayer(ByteArrayInputStream(bytes))
        mp3 = player
        cont.invokeOnCancellation { runCatching { player.close() } }
        Thread({
            runCatching { player.play() }
            if (cont.isActive) cont.resume(Unit)
        }, "speech").apply { isDaemon = true }.start()
    }

    private suspend fun playWav(bytes: ByteArray) {
        val c = AudioSystem.getClip()
        clip = c
        AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes)).use { c.open(it) }
        suspendCancellableCoroutine { cont ->
            c.addLineListener { e -> if (e.type == LineEvent.Type.STOP && cont.isActive) cont.resume(Unit) }
            cont.invokeOnCancellation { stop() }
            c.start()
        }
    }

    fun stop() {
        mp3?.let { runCatching { it.close() } }
        mp3 = null
        clip?.let {
            runCatching { it.stop() }
            runCatching { it.close() }
        }
        clip = null
    }
}
