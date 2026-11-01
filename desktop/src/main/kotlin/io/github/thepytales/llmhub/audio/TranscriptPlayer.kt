package io.github.thepytales.llmhub.audio

import de.lokal.whisper.desktop.DesktopSpeechPlatform
import io.github.thepytales.llmhub.ui.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.content.Context
import java.io.File
import java.util.Locale
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * Plays the audio of a transcript on Windows: ffmpeg (part of the app) decodes from the current position – also Opus,
 * M4A and video – and Java Sound plays it. Speed via ffmpeg's tempo filter, seeking restarts the decoder.
 */
@Suppress("UNUSED_PARAMETER")
class TranscriptPlayer(private val file: File, context: Context? = null) {
    private val rate = 22050f
    private val _position = MutableStateFlow(0.0)
    val position: StateFlow<Double> = _position.asStateFlow()
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()
    private val _error = MutableStateFlow<String?>(if (DesktopSpeechPlatform.ffmpeg() == null) tr("ffmpeg fehlt – die Aufnahme lässt sich nicht abspielen.") else null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var speed = 1f
    @Volatile private var worker: Thread? = null
    @Volatile private var process: Process? = null
    @Volatile private var generation = 0

    @Synchronized
    fun play() {
        val exe = DesktopSpeechPlatform.ffmpeg() ?: return
        stopWorker()
        val gen = ++generation
        val start = _position.value
        val tempo = speed
        _playing.value = true
        worker = Thread({
            var line: SourceDataLine? = null
            try {
                val cmd = mutableListOf(exe, "-nostdin", "-hide_banner", "-loglevel", "error", "-ss", String.format(Locale.ROOT, "%.2f", start),
                    "-i", file.absolutePath, "-vn", "-ac", "1", "-ar", rate.toInt().toString())
                if (tempo != 1f) cmd += listOf("-filter:a", String.format(Locale.ROOT, "atempo=%.2f", tempo))
                cmd += listOf("-f", "s16le", "pipe:1")
                val p = ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start()
                process = p
                val fmt = AudioFormat(rate, 16, 1, true, false)
                line = AudioSystem.getSourceDataLine(fmt).apply { open(fmt, (rate * 2 / 5).toInt()); start() }
                val buf = ByteArray(4410)
                var written = 0L
                val input = p.inputStream
                while (gen == generation) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    line.write(buf, 0, n - n % 2)
                    written += n
                    _position.value = start + written / 2.0 / rate * tempo
                }
                if (gen == generation) { line.drain(); _playing.value = false }
            } catch (e: Exception) {
                if (gen == generation) { _error.value = "Wiedergabe fehlgeschlagen: ${e.message}"; _playing.value = false }
            } finally {
                runCatching { line?.stop(); line?.close() }
            }
        }, "transcript-player").apply { isDaemon = true; start() }
    }

    private fun stopWorker() {
        generation++
        runCatching { process?.destroyForcibly() }
        process = null
        worker = null
    }

    @Synchronized
    fun pause() {
        stopWorker()
        _playing.value = false
    }

    fun toggle() = if (_playing.value) pause() else play()

    @Synchronized
    fun seek(seconds: Double) {
        val was = _playing.value
        stopWorker()
        _position.value = seconds.coerceAtLeast(0.0)
        if (was) play()
    }

    @Synchronized
    fun setSpeed(value: Float) {
        speed = value
        if (_playing.value) seek(_position.value)
    }

    fun release() = pause()
}
