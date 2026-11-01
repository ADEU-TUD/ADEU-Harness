package io.github.thepytales.llmhub.video

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.github.thepytales.llmhub.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.Executors
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * Preview of the video editor (AP-25): plays the timeline clip after clip from the proxy files (360 lines) – ffmpeg
 * decodes picture and sound, Java Sound plays the sound and is the clock the pictures follow. Titles and subtitles
 * are laid over the picture by the screen; transitions are shown as cuts, the music track only in the export.
 * Without playing, [seek] shows the single picture at a position.
 */
class VideoPlayer {
    private val _position = MutableStateFlow(0.0)
    val position: StateFlow<Double> = _position.asStateFlow()
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()
    private val _frame = MutableStateFlow<ImageBitmap?>(null)
    val frame: StateFlow<ImageBitmap?> = _frame.asStateFlow()

    @Volatile private var generation = 0
    @Volatile private var procs: List<Process> = emptyList()
    private val stills = Executors.newSingleThreadExecutor { r -> Thread(r, "Videovorschau").apply { isDaemon = true } }
    @Volatile private var wantedStill = 0L

    /** Shows the picture at timeline time [t] (the newest request wins). */
    fun seek(project: VideoProject, t: Double) {
        stop()
        val pos = t.coerceIn(0.0, project.duration.coerceAtLeast(0.0))
        _position.value = pos
        val ticket = ++wantedStill
        stills.execute {
            if (ticket != wantedStill) return@execute
            val (i, src) = project.at(pos) ?: run { _frame.value = null; return@execute }
            val img = Media.frame(File(project.clips[i].path), src, 360)
            if (ticket == wantedStill) _frame.value = img
        }
    }

    fun toggle(project: VideoProject) = if (_playing.value) stop() else play(project)

    fun stop() {
        generation++
        procs.forEach { runCatching { it.destroyForcibly() } }
        procs = emptyList()
        _playing.value = false
    }

    fun play(project: VideoProject) {
        val exe = Media.ffmpeg() ?: return
        stop()
        if (project.clips.isEmpty()) return
        val gen = generation
        _playing.value = true
        val startAt = if (_position.value >= project.duration - 0.05) 0.0 else _position.value
        Thread({
            try {
                val starts = project.starts()
                var (index, srcStart) = project.at(startAt) ?: return@Thread
                var timelineStart = startAt
                while (gen == generation && index < project.clips.size) {
                    val clip = project.clips[index]
                    // Up to where the next clip begins (or the end of this one).
                    val clipEnd = if (index + 1 < project.clips.size) starts[index + 1] else starts[index] + clip.length
                    val length = (clipEnd - timelineStart).coerceAtLeast(0.0)
                    if (length > 0.04) playPiece(exe, File(clip.path), srcStart, length, timelineStart, clip, gen)
                    index++
                    if (index < project.clips.size) {
                        timelineStart = starts[index]
                        srcStart = project.clips[index].inSec
                    }
                }
                if (gen == generation) _position.value = project.duration
            } catch (e: Exception) {
                AppLog.e("Videovorschau", e)
            } finally {
                if (gen == generation) _playing.value = false
            }
        }, "Videowiedergabe").apply { isDaemon = true }.start()
    }

    private fun fmt(v: Double) = String.format(Locale.ROOT, "%.3f", v)

    /** Plays [length] seconds of [file] from [from] (seconds of the file), placed at [timelineAt]. */
    private fun playPiece(exe: String, file: File, from: Double, length: Double, timelineAt: Double, clip: VideoClip, gen: Int) {
        val src = Media.proxy(file) ?: file
        val info = Media.probe(src)
        val h = 360
        val w = info?.takeIf { it.height > 0 }?.let { ((it.width.toDouble() * h / it.height).toInt() / 2) * 2 } ?: 640
        val fps = 25.0
        val video = if (info?.hasVideo != false) ProcessBuilder(
            exe, "-nostdin", "-hide_banner", "-loglevel", "error", "-ss", fmt(from), "-t", fmt(length), "-i", src.absolutePath,
            "-vf", "scale=$w:$h,fps=25", "-f", "rawvideo", "-pix_fmt", "rgba", "pipe:1",
        ).redirectError(ProcessBuilder.Redirect.DISCARD).start() else null
        val rate = 44100f
        val audio = if (info?.hasAudio == true && !clip.muted) ProcessBuilder(
            exe, "-nostdin", "-hide_banner", "-loglevel", "error", "-ss", fmt(from), "-t", fmt(length), "-i", src.absolutePath,
            "-vn", "-ac", "2", "-ar", rate.toInt().toString(), "-af", "volume=${fmt(clip.volume.toDouble())}", "-f", "s16le", "pipe:1",
        ).redirectError(ProcessBuilder.Redirect.DISCARD).start() else null
        procs = listOfNotNull(video, audio)
        val began = System.nanoTime()
        // Microseconds of sound played (-1: no sound clock).
        val audioClock = java.util.concurrent.atomic.AtomicLong(-1)
        val sound = audio?.let { p ->
            Thread({
                var line: SourceDataLine? = null
                try {
                    val format = AudioFormat(rate, 16, 2, true, false)
                    line = AudioSystem.getSourceDataLine(format).apply { open(format, (rate * 4 / 5).toInt()); start() }
                    val buf = ByteArray(8820)
                    val input = p.inputStream
                    while (gen == generation) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        line.write(buf, 0, n - n % 4)
                        audioClock.set(line.microsecondPosition)
                    }
                    if (gen == generation) line.drain()
                } catch (e: Exception) {
                    // No sound device (servers, tests): the picture runs on the wall clock.
                    audioClock.set(-1)
                } finally {
                    runCatching { line?.close() }
                }
            }, "Videoton").apply { isDaemon = true; start() }
        }
        val frameBytes = w * h * 4
        val buf = ByteArray(frameBytes)
        var shown = 0
        val input = video?.inputStream
        while (gen == generation) {
            if (input != null) {
                if (!readFully(input, buf)) break
                val t = shown / fps
                // Wait for the clock (sound if there is some, otherwise the wall clock).
                while (gen == generation) {
                    val clock = audioClock.get()
                    val now = if (clock >= 0) clock / 1e6 else (System.nanoTime() - began) / 1e9
                    if (now + 0.01 >= t) break
                    Thread.sleep(((t - now) * 1000).toLong().coerceIn(1, 40))
                }
                val img = Image.makeRaster(ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL), buf, w * 4)
                _frame.value = img.toComposeImageBitmap()
                shown++
                _position.value = timelineAt + t
            } else {
                // Only sound: move the position with the clock.
                val clock = audioClock.get()
                val now = if (clock >= 0) clock / 1e6 else (System.nanoTime() - began) / 1e9
                if (now >= length) break
                _position.value = timelineAt + now
                Thread.sleep(40)
            }
        }
        sound?.join(2000)
        video?.destroy()
        audio?.destroy()
    }

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n <= 0) return false
            off += n
        }
        return true
    }

    fun dispose() {
        stop()
        stills.shutdownNow()
    }
}
