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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * Preview of the video editor (AP-25, AP-52): plays the timeline **as the export makes it** – the same ffmpeg filter
 * graph ([VideoRender.graph]) at 360 lines from the proxy files, with all video tracks (picture-in-picture, crop,
 * opacity), transitions, the sound of every track and the music. One ffmpeg process gives the pictures, a second one
 * with the same graph the sound; Java Sound plays the sound and is the clock the pictures follow. Titles and subtitles
 * are laid over the picture by the screen with the same drawing as in the export. Only the even loudness (it needs
 * seconds of look-ahead) is left to the export.
 *
 * J/K/L: [forward] plays and speeds up (1×, 2×, 4×), [backward] runs back, [stop] holds. [seek] shows the single
 * picture at a position (where only V1 is to be seen straight from the file, otherwise through the graph).
 */
class VideoPlayer {
    private val _position = MutableStateFlow(0.0)
    val position: StateFlow<Double> = _position.asStateFlow()
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()
    private val _frame = MutableStateFlow<ImageBitmap?>(null)
    val frame: StateFlow<ImageBitmap?> = _frame.asStateFlow()
    /** Speed of the playback: 1, 2, 4 forward, -1, -2, -4 backward (J/K/L); 0 while it stands. */
    private val _speed = MutableStateFlow(0.0)
    val speed: StateFlow<Double> = _speed.asStateFlow()

    @Volatile private var generation = 0
    @Volatile private var procs: List<Process> = emptyList()
    private val stills = Executors.newSingleThreadExecutor { r -> Thread(r, "Videovorschau").apply { isDaemon = true } }
    @Volatile private var wantedStill = 0L
    @Volatile private var option: String? = null

    /** Tests: the pictures shown while playing (to check that the preview really runs). */
    @Volatile var framesShown = 0
        private set

    private fun scriptOption(exe: String) = option ?: VideoRender.scriptOption(exe).also { option = it }

    /** Shows the picture at timeline time [t] (the newest request wins). */
    fun seek(project: VideoProject, t: Double) {
        stop()
        val pos = t.coerceIn(0.0, project.duration.coerceAtLeast(0.0))
        _position.value = pos
        val ticket = ++wantedStill
        stills.execute {
            if (ticket != wantedStill) return@execute
            val img = picture(project, pos)
            if (ticket == wantedStill) _frame.value = img
        }
    }

    /**
     * Whether the picture at [t] needs the graph: something on V2 …, a placed or cropped clip, a transition, a hidden
     * V1 or the fade at the start and end. Otherwise it comes straight from the file (quicker while scrubbing).
     */
    internal fun needsGraph(p: VideoProject, t: Double): Boolean {
        if (p.layers.any { !p.track("V${it.track}").hidden && t >= it.startSec - 1e-6 && t < it.endSec }) return true
        if (p.track("V1").hidden) return true
        if (p.fadeEnds && p.duration > 2.0 && (t < 0.5 || t > p.duration - 0.6)) return true
        val (i, _) = p.at(t) ?: return false
        if (!p.clips[i].frame.isFull || p.clips[i].fx.picture) return true
        val s = p.starts()
        if (i + 1 < p.clips.size && t >= s[i + 1] - 1e-6) return true
        return i > 0 && t < s[i] + p.transitionOf(i)
    }

    /** One picture of the timeline at [t] (360 lines), as the export shows it there (without titles). */
    fun picture(p: VideoProject, t: Double): ImageBitmap? {
        if (p.clips.isEmpty()) return null
        if (!needsGraph(p, t)) {
            val (i, src) = p.at(t) ?: return null
            return Media.frame(File(p.clips[i].path), src, VideoRender.PREVIEW_HEIGHT)
        }
        val exe = Media.ffmpeg() ?: return null
        val work = File(Media.cacheDir, "bild-${System.nanoTime()}")
        return try {
            val from = t.coerceIn(0.0, maxOf(0.0, p.duration - 0.08))
            val args = VideoRender.previewArgs(p, VideoRender.Window(from, minOf(p.duration, from + 0.3)), VideoRender.PreviewOut.STILL, work, { Media.probe(it) }, scriptOption(exe))
            val proc = ProcessBuilder(listOf(exe) + args).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            val bytes = proc.inputStream.readBytes()
            proc.waitFor(30, TimeUnit.SECONDS)
            if (bytes.isEmpty()) null else Image.makeFromEncoded(bytes).toComposeImageBitmap()
        } catch (e: Exception) {
            AppLog.e("Vorschaubild", e)
            null
        } finally {
            work.deleteRecursively()
        }
    }

    /** Space: play or hold. With [loop] (In–Out) the piece repeats. */
    fun toggle(project: VideoProject, loop: Pair<Double, Double>? = null) = if (_playing.value) stop() else play(project, loop = loop)

    /** L: play forward; while it plays forward, each L doubles the speed up to 4×. */
    fun forward(project: VideoProject, loop: Pair<Double, Double>? = null) {
        val now = _speed.value
        play(project, speed = if (_playing.value && now > 0) minOf(4.0, now * 2) else 1.0, loop = loop)
    }

    /** J: run backward (pictures straight from the files, without sound); each J doubles the speed up to 4×. */
    fun backward(project: VideoProject) {
        val now = _speed.value
        val sp = if (_playing.value && now < 0) maxOf(-4.0, now * 2) else -1.0
        stop()
        if (project.clips.isEmpty()) return
        val gen = generation
        _playing.value = true
        _speed.value = sp
        Thread({
            try {
                var last = System.nanoTime()
                while (gen == generation && _position.value > 0.0) {
                    val img = picture(project, _position.value)
                    if (gen != generation) break
                    _frame.value = img
                    val nowNs = System.nanoTime()
                    val step = maxOf(0.04, (nowNs - last) / 1e9) * -sp
                    last = nowNs
                    _position.value = (_position.value - step).coerceAtLeast(0.0)
                }
            } finally {
                if (gen == generation) { _playing.value = false; _speed.value = 0.0 }
            }
        }, "Video rückwärts").apply { isDaemon = true }.start()
    }

    fun stop() {
        generation++
        procs.forEach { runCatching { it.destroyForcibly() } }
        procs = emptyList()
        _playing.value = false
        _speed.value = 0.0
    }

    /** Plays from the playhead (or from the start when it is at the end); [loop] repeats In–Out. */
    fun play(project: VideoProject, speed: Double = 1.0, loop: Pair<Double, Double>? = null) {
        val exe = Media.ffmpeg() ?: return
        val pos = _position.value
        stop()
        if (project.clips.isEmpty()) return
        val gen = generation
        _playing.value = true
        _speed.value = speed
        val total = project.duration
        val range = loop?.takeIf { it.second - it.first > 0.2 }?.let { it.first.coerceIn(0.0, total) to it.second.coerceIn(0.0, total) }
        var startAt = if (range != null) (if (pos in range.first..(range.second - 0.05)) pos else range.first)
        else if (pos >= total - 0.05) 0.0 else pos
        Thread({
            try {
                while (gen == generation) {
                    val end = range?.second ?: minOf(total, startAt + WINDOW)
                    if (end - startAt > 0.04) playWindow(exe, project, startAt, end, speed, gen)
                    if (gen != generation) break
                    if (range != null) { startAt = range.first; continue }
                    if (end >= total - 1e-3) { _position.value = total; break }
                    startAt = end
                }
            } catch (e: Exception) {
                AppLog.e("Videovorschau", e)
            } finally {
                if (gen == generation) { _playing.value = false; _speed.value = 0.0 }
            }
        }, "Videowiedergabe").apply { isDaemon = true }.start()
    }

    /** Plays [from]..[to] of the timeline through the graph of the export. */
    private fun playWindow(exe: String, project: VideoProject, from: Double, to: Double, speed: Double, gen: Int) {
        val work = File(Media.cacheDir, "vorschau-${System.nanoTime()}")
        try {
            val window = VideoRender.Window(from, to)
            val info: (File) -> MediaInfo? = { Media.probe(it) }
            val opt = scriptOption(exe)
            val videoArgs = VideoRender.previewArgs(project, window, VideoRender.PreviewOut.VIDEO, File(work, "bild"), info, opt, speed)
            val audioArgs = VideoRender.previewArgs(project, window, VideoRender.PreviewOut.AUDIO, File(work, "ton"), info, opt, speed)
            val video = ProcessBuilder(listOf(exe) + videoArgs).redirectError(File(work, "bild.log")).start()
            val audio = ProcessBuilder(listOf(exe) + audioArgs).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            procs = listOf(video, audio)
            if (gen != generation) { video.destroyForcibly(); audio.destroyForcibly(); return }
            stream(project, video, audio, from, (to - from) / speed, speed, gen)
            if (gen == generation && video.waitFor(2, TimeUnit.SECONDS) && video.exitValue() != 0) {
                File(work, "bild.log").takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }?.let { AppLog.i("Videovorschau: ${it.take(600)}") }
            }
        } finally {
            work.deleteRecursively()
        }
    }

    /**
     * Shows the pictures of [video] (RGBA at preview size, 25 per second) in time with the sound of [audio];
     * [length] real seconds, the timeline moves [speed] times as fast from [timelineAt].
     */
    private fun stream(project: VideoProject, video: Process, audio: Process, timelineAt: Double, length: Double, speed: Double, gen: Int) {
        val (w, h) = VideoRender.previewSize(project)
        val fps = VideoRender.PREVIEW_FPS.toDouble()
        val rate = 44100f
        val began = System.nanoTime()
        // Microseconds of sound played (-1: no sound clock).
        val audioClock = AtomicLong(-1)
        val sound = Thread({
            var line: SourceDataLine? = null
            try {
                val format = AudioFormat(rate, 16, 2, true, false)
                line = AudioSystem.getSourceDataLine(format).apply { open(format, (rate * 4 / 5).toInt()); start() }
                val buf = ByteArray(8820)
                val input = audio.inputStream
                while (gen == generation) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    line.write(buf, 0, n - n % 4)
                    audioClock.set(line.microsecondPosition)
                }
                if (gen == generation) line.drain()
            } catch (e: Exception) {
                // No sound device (servers, tests): the picture runs on the wall clock; the sound is read away.
                audioClock.set(-1)
                runCatching { audio.inputStream.transferTo(java.io.OutputStream.nullOutputStream()) }
            } finally {
                runCatching { line?.close() }
            }
        }, "Videoton").apply { isDaemon = true; start() }
        val buf = ByteArray(w * h * 4)
        var shown = 0
        val input = video.inputStream
        while (gen == generation) {
            if (!readFully(input, buf)) break
            val t = shown / fps
            if (t > length + 0.5) break
            // Wait for the clock (sound if there is some, otherwise the wall clock).
            while (gen == generation) {
                val clock = audioClock.get()
                val now = if (clock >= 0) clock / 1e6 else (System.nanoTime() - began) / 1e9
                if (now + 0.01 >= t) break
                Thread.sleep(((t - now) * 1000).toLong().coerceIn(1, 40))
            }
            if (gen != generation) break
            val img = Image.makeRaster(ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL), buf, w * 4)
            _frame.value = img.toComposeImageBitmap()
            shown++
            framesShown++
            _position.value = timelineAt + t * speed
        }
        sound.join(2000)
        video.destroy()
        audio.destroy()
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

    companion object {
        /** Seconds of the timeline per preview run; the next one follows on. */
        const val WINDOW = 600.0
    }
}
