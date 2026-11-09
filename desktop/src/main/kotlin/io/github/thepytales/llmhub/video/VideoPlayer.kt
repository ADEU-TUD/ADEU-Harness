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
 * picture at a position (where only V1 is to be seen straight from the file, otherwise through the graph) – both at
 * the size and with the bars of the played pictures, so nothing jumps between standing and playing (AP-70).
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

    /** Where a picture of the preview came from (AP-70): a single picture from the file or the graph, playing, J. */
    enum class Source { FILE, GRAPH, PLAY, BACK }

    /** A picture as the screen got it, with the time it shows (tests check size, place and gaps of the sequence). */
    class Shown(val image: ImageBitmap, val at: Double, val source: Source)

    /** Tests: every picture handed to the screen. */
    @Volatile var onShown: ((Shown) -> Unit)? = null

    @Volatile private var generation = 0
    @Volatile private var procs: List<Process> = emptyList()
    private val stills = Executors.newSingleThreadExecutor { r -> Thread(r, "Videovorschau").apply { isDaemon = true } }
    @Volatile private var option: String? = null

    // Single pictures (AP-70): the newest request wins, but a picture that is ready is shown while a newer one is still
    // being made (scrubbing shows pictures instead of standing still), never an older one after a newer, never none.
    private val lock = Any()
    @Volatile private var wantedStill = 0L
    private var shownTicket = 0L
    private var shownKey: Any? = null
    private var lastKey: Any? = null
    /** Playbacks begun: a single picture asked for before one is not shown any more. */
    @Volatile private var starts = 0

    /** Tests: the pictures shown while playing (to check that the preview really runs). */
    @Volatile var framesShown = 0
        private set

    /** Tests: ffmpeg runs for single pictures (an edit that does not change the picture needs none). */
    @Volatile var stillRuns = 0
        private set

    private fun scriptOption(exe: String) = option ?: VideoRender.scriptOption(exe).also { option = it }

    private fun show(img: ImageBitmap, at: Double, source: Source) {
        _frame.value = img
        onShown?.invoke(Shown(img, at, source))
    }

    /**
     * Shows the picture at timeline time [t]. The picture shown stays until the new one is ready; a request for the
     * picture that is already there (an edit of a title, of the sound, a marker …) starts no ffmpeg run.
     */
    fun seek(project: VideoProject, t: Double) {
        if (_playing.value) stop()
        val pos = t.coerceIn(0.0, project.duration.coerceAtLeast(0.0))
        _position.value = pos
        val key = stillKey(project, pos)
        val ticket: Long
        synchronized(lock) {
            if (key == shownKey) { shownTicket = ++wantedStill; lastKey = key; return }
            if (key == lastKey) return
            ticket = ++wantedStill
            lastKey = key
            if (project.clips.isEmpty()) { shownTicket = ticket; shownKey = key; _frame.value = null; return }
        }
        val began = starts
        stills.execute {
            if (ticket != wantedStill) return@execute
            val made = still(project, pos)
            synchronized(lock) {
                if (made == null) { if (lastKey == key) lastKey = null; return@execute }
                if (began != starts || ticket < shownTicket) return@execute
                shownTicket = ticket
                shownKey = key
            }
            show(made!!.first, pos, made.second)
        }
    }

    /** What decides the single picture at [pos]: the project without what the picture does not show, and the frame. */
    internal fun stillKey(p: VideoProject, pos: Double): Any = pictureOf(p) to Math.round(pos * VideoRender.PREVIEW_FPS)

    /**
     * Whether the picture at [t] needs the graph: something on V2 …, a placed or cropped clip, a transition, a hidden
     * V1 or the fade at the start and end. Otherwise it comes straight from the file (quicker while scrubbing), through
     * the same filters as in the graph.
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

    /** One picture of the timeline at [t] (preview size), as the export shows it there (without titles). */
    fun picture(p: VideoProject, t: Double): ImageBitmap? = still(p, t)?.first

    private fun still(p: VideoProject, t: Double): Pair<ImageBitmap, Source>? {
        if (p.clips.isEmpty()) return null
        val exe = Media.ffmpeg() ?: return null
        val (w, h) = VideoRender.previewSize(p)
        if (!needsGraph(p, t)) {
            val (i, src) = p.at(t) ?: return null
            val file = File(p.clips[i].path)
            val picture = Media.isStill(file)
            // At the very end of a file there may be no picture any more: then the one just before.
            val img = rgba(exe, VideoRender.previewStillArgs(p, file, src, picture), w, h)
                ?: if (picture) null else rgba(exe, VideoRender.previewStillArgs(p, file, maxOf(0.0, src - 0.12), false), w, h)
            return img?.let { it to Source.FILE }
        }
        val work = File(Media.cacheDir, "bild-${System.nanoTime()}")
        return try {
            val from = t.coerceIn(0.0, maxOf(0.0, p.duration - 0.08))
            val args = VideoRender.previewArgs(p, VideoRender.Window(from, minOf(p.duration, from + 0.3)), VideoRender.PreviewOut.STILL, work, { Media.probe(it) }, scriptOption(exe))
            rgba(exe, args, w, h)?.let { it to Source.GRAPH }
        } catch (e: Exception) {
            AppLog.e("Vorschaubild", e)
            null
        } finally {
            work.deleteRecursively()
        }
    }

    /** Runs ffmpeg for one RGBA picture of [w]×[h]; null when it gives none. */
    private fun rgba(exe: String, args: List<String>, w: Int, h: Int): ImageBitmap? = runCatching {
        stillRuns++
        val proc = ProcessBuilder(listOf(exe) + args).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val bytes = proc.inputStream.use { it.readNBytes(w * h * 4) }
        if (!proc.waitFor(30, TimeUnit.SECONDS)) proc.destroyForcibly()
        if (bytes.size < w * h * 4) null
        else Image.makeRaster(ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL), bytes, w * 4).toComposeImageBitmap()
    }.onFailure { AppLog.e("Vorschaubild", it) }.getOrNull()

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
        starts++
        _playing.value = true
        _speed.value = sp
        Thread({
            try {
                var last = System.nanoTime()
                while (gen == generation && _position.value > 0.0) {
                    val at = _position.value
                    val img = picture(project, at)
                    if (gen != generation) break
                    if (img != null) {
                        synchronized(lock) { shownKey = null; lastKey = null }
                        show(img, at, Source.BACK)
                    }
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
        starts++
        synchronized(lock) { lastKey = null }
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
        val clock = PlaybackClock()
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
                    clock.sound(line.microsecondPosition / 1e6)
                }
                if (gen == generation) line.drain()
            } catch (e: Exception) {
                // No sound device (servers, tests): the picture runs on the wall clock; the sound is read away.
                clock.soundGone()
                runCatching { audio.inputStream.transferTo(java.io.OutputStream.nullOutputStream()) }
            } finally {
                clock.soundGone()
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
            // Wait for the clock (sound if there is some, otherwise the wall clock from the first picture).
            while (gen == generation) {
                val now = clock.now()
                if (now + 0.01 >= t) break
                Thread.sleep(((t - now) * 1000).toLong().coerceIn(1, 40))
            }
            if (gen != generation) break
            val img = Image.makeRaster(ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL), buf, w * 4).toComposeImageBitmap()
            if (shown == 0) synchronized(lock) { shownKey = null; lastKey = null }
            val at = timelineAt + t * speed
            _position.value = at
            show(img, at, Source.PLAY)
            shown++
            framesShown++
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

        /** [p] without what its single picture does not show (titles, subtitles and sound are laid over or played). */
        internal fun pictureOf(p: VideoProject): VideoProject = p.copy(
            id = "", title = "", overlays = emptyList(), audio = emptyList(), subtitles = emptyList(), burnSubtitles = false,
            sound = AudioSettings(), transcripts = emptyMap(), markers = emptyList(), markIn = null, markOut = null,
            media = emptyList(), bins = emptyList(), magnet = true, pendingTranscripts = emptyMap(), audioTracks = 1,
            trackStates = p.trackStates.filterKeys { it.startsWith("V") }, createdAt = 0, updatedAt = 0,
        )
    }
}

/**
 * The clock the played pictures follow (AP-70). The position of the sound is known only after each block written to
 * the sound card, every 50 ms; taken as it was, two pictures (40 ms apart) came at once every fifth time and then
 * none – the playback jerked. This clock runs on with the wall clock between two readings (at most [AHEAD] beyond the
 * last one, so a stalled sound holds the picture) and never goes back. Without sound (or once it has ended) the wall
 * clock alone counts, from the first picture on.
 */
internal class PlaybackClock(private val nanos: () -> Long = System::nanoTime) {
    private var soundSec = -1.0
    private var soundAt = 0L
    private var soundEnded = false
    private var wallStart = -1L
    private var last = 0.0

    /** A reading of the sound: [sec] seconds of it have been played. */
    @Synchronized
    fun sound(sec: Double) {
        if (sec != soundSec) { soundSec = sec; soundAt = nanos() }
    }

    /** No (more) sound: the clock runs on by itself. */
    @Synchronized
    fun soundGone() {
        if (soundEnded) return
        soundEnded = true
        if (soundSec >= 0) { soundSec = minOf(soundSec + (nanos() - soundAt) / 1e9, soundSec + AHEAD); soundAt = nanos() }
    }

    /** Seconds played. */
    @Synchronized
    fun now(): Double {
        val t = nanos()
        if (wallStart < 0) wallStart = t
        val v = when {
            soundSec < 0 -> (t - wallStart) / 1e9
            soundEnded -> soundSec + (t - soundAt) / 1e9
            else -> soundSec + minOf(AHEAD, (t - soundAt) / 1e9)
        }
        last = maxOf(last, v)
        return last
    }

    companion object {
        const val AHEAD = 0.12
    }
}
