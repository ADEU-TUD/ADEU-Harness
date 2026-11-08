package io.github.thepytales.llmhub.video

import io.github.thepytales.llmhub.AppLog
import io.github.thepytales.llmhub.desktop.Os
import io.github.thepytales.llmhub.ui.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

/**
 * Bildschirmaufnahme (AP-56): the whole screen, a window or an area, with webcam, microphone and system sound, through
 * **one** ffmpeg process (so all sources share one clock) that writes every source into a file of its own – screen and
 * webcam as video, microphone and system sound as WAV. Pause ends the running part, Weiter starts the next one; Stopp
 * makes a project for the video editor with separate tracks: the screen on V1, the webcam as picture-in-picture on V2,
 * the clicks as rings on V3, the microphone on A1 and the system sound on A2.
 *
 * Windows: gdigrab (screen, window, area with cursor) and DirectShow (webcam, microphone; system sound through a
 * loopback device such as „Stereomix“). Linux: x11grab, v4l2, PulseAudio. macOS: AVFoundation.
 */
object ScreenRecorder {
    enum class Source { SCREEN, WINDOW, AREA }

    /** A rectangle on the desktop in pixels. */
    data class Area(val x: Int, val y: Int, val w: Int, val h: Int) {
        /** Even width and height (H.264 wants them). */
        fun even() = Area(x, y, (w / 2) * 2, (h / 2) * 2)
    }

    /**
     * What to record. [screen]: the monitor (its bounds) for [Source.SCREEN] – null is the whole desktop; [window]: the
     * window title; [area]: the rectangle. Device names as ffmpeg lists them (null = not recorded).
     */
    data class Setup(
        val source: Source = Source.SCREEN,
        val screen: Area? = null,
        val window: String? = null,
        val area: Area? = null,
        val webcam: String? = null,
        val mic: String? = null,
        val systemAudio: String? = null,
        val fps: Int = 30,
        val drawMouse: Boolean = true,
        val clicks: Boolean = true,
        val countdown: Int = 3,
        val transcribe: Boolean = false,
        /** Tests: lavfi sources instead of the screen and the devices. */
        val test: Boolean = false,
    ) {
        /** The rectangle the clicks are measured in (null: the whole primary screen as far as known). */
        val bounds: Area? get() = when (source) { Source.AREA -> area; Source.SCREEN -> screen; Source.WINDOW -> null }
    }

    enum class Platform { WINDOWS, LINUX, MAC }

    val platform: Platform get() = when { Os.windows -> Platform.WINDOWS; Os.mac -> Platform.MAC; else -> Platform.LINUX }

    /** The files of one part (between start/Weiter and Pause/Stopp). */
    data class Part(val screen: File, val webcam: File?, val mic: File?, val system: File?, var seconds: Double = 0.0)

    /** A click at [sec] seconds of the recording (pauses left out), at [x], [y] as shares of the recorded picture. */
    data class Click(val sec: Double, val x: Float, val y: Float)

    // ── Devices ──

    data class Devices(val video: List<String>, val audio: List<String>) {
        /** Loopback devices that carry the system sound („Stereomix“, „What U Hear“, virtual-audio-capturer …). */
        val loopback: List<String> get() = audio.filter { LOOPBACK.containsMatchIn(it) }
        val microphones: List<String> get() = audio.filterNot { LOOPBACK.containsMatchIn(it) }
    }

    private val LOOPBACK = Regex("stereo ?mix|what u hear|wave out|loopback|virtual-audio-capturer|monitor of", RegexOption.IGNORE_CASE)

    /** The cameras and microphones ffmpeg sees. */
    suspend fun devices(exe: String = Media.ffmpeg() ?: "ffmpeg"): Devices = withContext(Dispatchers.IO) {
        runCatching {
            when (platform) {
                Platform.WINDOWS -> parseDshow(run(listOf(exe, "-hide_banner", "-list_devices", "true", "-f", "dshow", "-i", "dummy")))
                Platform.MAC -> parseAvfoundation(run(listOf(exe, "-hide_banner", "-f", "avfoundation", "-list_devices", "true", "-i", "")))
                Platform.LINUX -> Devices(
                    File("/dev").listFiles().orEmpty().filter { it.name.matches(Regex("video\\d+")) }.map { it.absolutePath }.sorted(),
                    listOf("default"),
                )
            }
        }.getOrDefault(Devices(emptyList(), emptyList()))
    }

    private fun run(cmd: List<String>): String {
        val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor(20, TimeUnit.SECONDS)
        return out
    }

    /** `ffmpeg -list_devices true -f dshow -i dummy` (both the old layout with headings and the new one with „(video)“). */
    internal fun parseDshow(out: String): Devices {
        val video = mutableListOf<String>()
        val audio = mutableListOf<String>()
        var section: MutableList<String>? = null
        for (line in out.lines()) {
            if ("Alternative name" in line) continue
            when {
                "DirectShow video devices" in line -> { section = video; continue }
                "DirectShow audio devices" in line -> { section = audio; continue }
            }
            val name = Regex("\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: continue
            when {
                line.trimEnd().endsWith("(video)") -> video += name
                line.trimEnd().endsWith("(audio)") -> audio += name
                line.trimEnd().endsWith("(none)") -> {}
                else -> section?.add(name)
            }
        }
        return Devices(video.distinct(), audio.distinct())
    }

    /** `ffmpeg -f avfoundation -list_devices true -i ""`: „[0] FaceTime HD Camera“ under the video or audio heading; screens left out. */
    internal fun parseAvfoundation(out: String): Devices {
        val video = mutableListOf<String>()
        val audio = mutableListOf<String>()
        var section: MutableList<String>? = null
        for (line in out.lines()) {
            when {
                "AVFoundation video devices" in line -> { section = video; continue }
                "AVFoundation audio devices" in line -> { section = audio; continue }
            }
            val m = Regex("\\[(\\d+)] (.+)$").find(line) ?: continue
            val name = m.groupValues[2].trim()
            if (!name.startsWith("Capture screen")) section?.add(name)
        }
        return Devices(video, audio)
    }

    // ── The ffmpeg command ──

    /**
     * Arguments of one part: one input per source, one output per source. The screen is encoded on the fly
     * (x264 ultrafast, nearly lossless enough for text), the webcam likewise, the sound uncompressed. MKV and WAV survive
     * an aborted process better than MP4; „q“ on stdin ends them cleanly.
     */
    fun args(s: Setup, part: Part, platform: Platform = this.platform): List<String> {
        val a = mutableListOf("-hide_banner", "-loglevel", "error", "-y", "-thread_queue_size", "1024")
        val fps = s.fps.coerceIn(5, 60).toString()
        // Input 0: the screen.
        if (s.test) a += listOf("-re", "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=$fps")
        else when (platform) {
            Platform.WINDOWS -> {
                a += listOf("-f", "gdigrab", "-framerate", fps, "-draw_mouse", if (s.drawMouse) "1" else "0")
                val r = when (s.source) { Source.AREA -> s.area?.even(); Source.SCREEN -> s.screen?.even(); Source.WINDOW -> null }
                if (r != null) a += listOf("-offset_x", r.x.toString(), "-offset_y", r.y.toString(), "-video_size", "${r.w}x${r.h}")
                a += listOf("-i", if (s.source == Source.WINDOW && !s.window.isNullOrBlank()) "title=${s.window}" else "desktop")
            }
            Platform.LINUX -> {
                val r = (s.bounds ?: Area(0, 0, 1920, 1080)).even()
                val display = System.getenv("DISPLAY")?.takeIf { it.isNotBlank() } ?: ":0.0"
                a += listOf("-f", "x11grab", "-framerate", fps, "-draw_mouse", if (s.drawMouse) "1" else "0", "-video_size", "${r.w}x${r.h}", "-i", "$display+${r.x},${r.y}")
            }
            Platform.MAC -> a += listOf("-f", "avfoundation", "-capture_cursor", if (s.drawMouse) "1" else "0", "-capture_mouse_clicks", if (s.clicks) "1" else "0", "-framerate", fps, "-i", "Capture screen 0:none")
        }
        var next = 1
        val webcam = part.webcam?.let { s.webcam }?.let { cam ->
            if (s.test) a += listOf("-re", "-f", "lavfi", "-i", "testsrc=size=320x240:rate=$fps")
            else when (platform) {
                Platform.WINDOWS -> a += listOf("-f", "dshow", "-rtbufsize", "256M", "-i", "video=$cam")
                Platform.LINUX -> a += listOf("-f", "v4l2", "-i", cam)
                Platform.MAC -> a += listOf("-f", "avfoundation", "-framerate", "30", "-i", "$cam:none")
            }
            next++
        }
        fun audioInput(name: String, tone: Int) {
            if (s.test) a += listOf("-re", "-f", "lavfi", "-i", "sine=f=$tone:sample_rate=48000")
            else when (platform) {
                Platform.WINDOWS -> a += listOf("-f", "dshow", "-audio_buffer_size", "50", "-i", "audio=$name")
                Platform.LINUX -> a += listOf("-f", "pulse", "-i", name)
                Platform.MAC -> a += listOf("-f", "avfoundation", "-i", ":$name")
            }
        }
        val mic = part.mic?.let { s.mic }?.let { audioInput(it, 440); next++ }
        val system = part.system?.let { s.systemAudio }?.let { audioInput(it, 660); next++ }
        // Outputs, one per source.
        val area = if (platform == Platform.MAC && s.source == Source.AREA) s.area?.even() else null
        a += listOf("-map", "0:v")
        area?.let { a += listOf("-vf", "crop=${it.w}:${it.h}:${it.x}:${it.y}") }
        a += listOf("-c:v", "libx264", "-preset", "ultrafast", "-crf", "20", "-pix_fmt", "yuv420p", "-g", (s.fps * 2).toString(), part.screen.absolutePath)
        if (webcam != null) a += listOf("-map", "$webcam:v", "-c:v", "libx264", "-preset", "ultrafast", "-crf", "23", "-pix_fmt", "yuv420p", part.webcam!!.absolutePath)
        if (mic != null) a += listOf("-map", "$mic:a", "-c:a", "pcm_s16le", "-ar", "48000", part.mic!!.absolutePath)
        if (system != null) a += listOf("-map", "$system:a", "-c:a", "pcm_s16le", "-ar", "48000", part.system!!.absolutePath)
        return a
    }

    // ── The project ──

    /** A ring on transparent ground for the clicks (TUD blue with a light halo), [px] square. */
    fun ring(file: File, px: Int = 128): File {
        if (file.isFile) return file
        val img = BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0x00, 0x30, 0x5E, 70)
        g.fillOval(px / 8, px / 8, px * 3 / 4, px * 3 / 4)
        g.stroke = BasicStroke(px / 12f)
        g.color = Color(0xF5, 0xA2, 0x00, 235)
        g.drawOval(px / 6, px / 6, px * 2 / 3, px * 2 / 3)
        g.dispose()
        file.parentFile?.mkdirs()
        ImageIO.write(img, "png", file)
        return file
    }

    /** Clicks closer than 0.3 s to the one before are one ring (a double click). */
    internal fun mergeClicks(clicks: List<Click>): List<Click> {
        val out = mutableListOf<Click>()
        for (c in clicks.sortedBy { it.sec }) if (out.isEmpty() || c.sec - out.last().sec > 0.3) out += c
        return out
    }

    /**
     * The video project of a finished recording: the parts one after another on V1 (screen), the webcam of each part on
     * V2 in the lower right corner, the clicks as rings on V3, the microphone on A1 and the system sound on A2 – each part
     * starting where its screen part starts. Size and frame rate from the first screen file.
     */
    fun project(title: String, parts: List<Part>, clicks: List<Click>, ring: File?, info: (File) -> MediaInfo? = { Media.probe(it) }, fps: Int = 30): VideoProject {
        require(parts.isNotEmpty()) { tr("Die Aufnahme ist leer.") }
        val clips = mutableListOf<VideoClip>()
        val layers = mutableListOf<LayerClip>()
        val audio = mutableListOf<AudioClip>()
        var t = 0.0
        for (part in parts) {
            val len = info(part.screen)?.duration?.takeIf { it > 0.05 } ?: part.seconds
            if (len <= 0.05) continue
            clips += VideoClip(path = part.screen.absolutePath, inSec = 0.0, outSec = len, volume = 0f, muted = true)
            part.webcam?.takeIf { it.isFile }?.let { f ->
                val l = info(f)?.duration?.takeIf { it > 0.05 } ?: len
                layers += LayerClip(path = f.absolutePath, track = 2, startSec = t, outSec = minOf(l, len), muted = true, frame = Frame.corner("unten-rechts", 0.28f), fadeSec = 0.0)
            }
            part.mic?.takeIf { it.isFile }?.let { f ->
                val l = info(f)?.duration?.takeIf { it > 0.05 } ?: len
                audio += AudioClip(path = f.absolutePath, startSec = t, outSec = minOf(l, len), volume = 1f, fadeSec = 0.05, track = 1)
            }
            part.system?.takeIf { it.isFile }?.let { f ->
                val l = info(f)?.duration?.takeIf { it > 0.05 } ?: len
                audio += AudioClip(path = f.absolutePath, startSec = t, outSec = minOf(l, len), volume = 0.6f, fadeSec = 0.05, track = 2)
            }
            t += len
        }
        require(clips.isNotEmpty()) { tr("Die Aufnahme ist leer.") }
        if (ring != null) mergeClicks(clicks).filter { it.sec < t }.forEach { c ->
            layers += LayerClip(path = ring.absolutePath, track = 3, startSec = (c.sec - 0.05).coerceAtLeast(0.0), inSec = 0.0, outSec = 0.6, muted = true, frame = Frame(x = c.x, y = c.y, scale = 0.07f), fadeSec = 0.12)
        }
        val first = info(File(clips.first().path))
        val (w, h) = size(first?.width ?: 0, first?.height ?: 0)
        val files = (clips.map { it.path } + layers.filter { it.track == 2 }.map { it.path } + audio.map { it.path }).distinct()
        val p = VideoProject(
            title = title, width = w, height = h, fps = first?.fps?.takeIf { it in 10.0..61.0 }?.let { if (it > 40) 50 else if (it > 27) 30 else 25 } ?: fps,
            clips = clips, layers = layers, audio = audio, fadeEnds = false,
            videoTracks = if (layers.any { it.track == 3 }) 3 else if (layers.isNotEmpty()) 2 else 1,
            audioTracks = if (audio.any { it.track == 2 }) 2 else 1,
            sound = AudioSettings(normalize = true),
        )
        return VideoEdit.addToPool(p, files)
    }

    /** Project size for a recording of [w] × [h]: Full HD for wide screens, else the picture's own size (even, at most 1920 wide). */
    internal fun size(w: Int, h: Int): Pair<Int, Int> {
        if (w <= 0 || h <= 0) return 1920 to 1080
        if (kotlin.math.abs(w.toDouble() / h - 16.0 / 9) < 0.02) return 1920 to 1080
        val scale = minOf(1.0, 1920.0 / w, 1920.0 / h)
        return ((w * scale).toInt() / 2 * 2) to ((h * scale).toInt() / 2 * 2)
    }

    /** Title of a new recording: „Aufnahme 08.11.2026 14:05“. */
    fun title(now: Long = System.currentTimeMillis()): String = tr("Aufnahme") + " " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(now))

    // ── A running recording ──

    enum class State { RECORDING, PAUSED, STOPPED }

    /** The recording that runs now (one at a time): the screen, the tray and the hotkeys look at it. */
    private val _active = MutableStateFlow<Recording?>(null)
    val active: StateFlow<Recording?> = _active.asStateFlow()

    /** Starts a recording into [dir] (a folder of its own); throws if one is running or ffmpeg is missing. */
    fun start(setup: Setup, dir: File, exe: String? = Media.ffmpeg()): Recording {
        check(_active.value == null) { tr("Es läuft schon eine Aufnahme.") }
        val ffmpeg = exe ?: throw IllegalStateException(tr("ffmpeg fehlt in der App."))
        return Recording(setup, dir, ffmpeg).also { _active.value = it; it.begin() }
    }

    internal fun finished(r: Recording) { if (_active.value === r) _active.value = null }

    /**
     * One recording: parts of ffmpeg processes, the clicks, the time recorded so far. [stop] waits for ffmpeg to write
     * the files and returns the parts.
     */
    class Recording internal constructor(val setup: Setup, val dir: File, private val exe: String) {
        private val _state = MutableStateFlow(State.RECORDING)
        val state: StateFlow<State> = _state.asStateFlow()
        val parts = mutableListOf<Part>()
        val clicks = mutableListOf<Click>()
        private var proc: Process? = null
        private var partStart = 0L
        private var before = 0.0
        private var clickThread: Thread? = null

        /** Seconds recorded so far (pauses left out). */
        val seconds: Double get() = before + if (_state.value == State.RECORDING && partStart > 0) (System.nanoTime() - partStart) / 1e9 else 0.0

        internal fun begin() {
            dir.mkdirs()
            startPart()
            if (setup.clicks && Os.windows && !setup.test) clickThread = Thread({ watchClicks() }, "Klicks der Aufnahme").apply { isDaemon = true; start() }
        }

        private fun startPart() {
            val n = parts.size + 1
            val part = Part(
                screen = File(dir, "bildschirm-$n.mkv"),
                webcam = setup.webcam?.let { File(dir, "webcam-$n.mkv") },
                mic = setup.mic?.let { File(dir, "mikrofon-$n.wav") },
                system = setup.systemAudio?.let { File(dir, "systemton-$n.wav") },
            )
            val args = args(setup, part)
            val p = ProcessBuilder(listOf(exe) + args).redirectError(File(dir, "ffmpeg-$n.log")).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            proc = p
            parts += part
            partStart = System.nanoTime()
            _state.value = State.RECORDING
        }

        private fun endPart() {
            val p = proc ?: return
            proc = null
            runCatching { p.outputStream.apply { write("q\n".toByteArray()); flush() } }
            if (!p.waitFor(15, TimeUnit.SECONDS)) { p.destroy(); if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly() }
            val len = (System.nanoTime() - partStart) / 1e9
            parts.lastOrNull()?.seconds = len
            before += len
            partStart = 0L
            if (!parts.last().screen.isFile) {
                val log = File(dir, "ffmpeg-${parts.size}.log").takeIf { it.isFile }?.readLines().orEmpty().filter { it.isNotBlank() }.takeLast(5).joinToString("\n")
                AppLog.i("Bildschirmaufnahme: kein Bild geschrieben\n$log")
            }
        }

        /** Pause: the running part ends (its files are complete). */
        @Synchronized
        fun pause() {
            if (_state.value != State.RECORDING) return
            endPart()
            _state.value = State.PAUSED
        }

        /** Weiter: a new part starts. */
        @Synchronized
        fun resume() {
            if (_state.value != State.PAUSED) return
            startPart()
        }

        @Synchronized
        fun toggle() { if (_state.value == State.RECORDING) pause() else resume() }

        /** Stopp: ends the running part; returns the parts that have a screen file. */
        suspend fun stop(): List<Part> = withContext(Dispatchers.IO) {
            synchronized(this@Recording) {
                if (_state.value == State.RECORDING) endPart()
                _state.value = State.STOPPED
            }
            clickThread?.interrupt()
            finished(this@Recording)
            parts.filter { it.screen.isFile && it.screen.length() > 0 }
        }

        /** Abbrechen: stops and deletes the files. */
        suspend fun cancel() {
            stop()
            dir.deleteRecursively()
        }

        /** Records the clicks (left button) inside the recorded picture, polling the button 50 times a second (Windows). */
        private fun watchClicks() {
            val user32 = runCatching { com.sun.jna.platform.win32.User32.INSTANCE }.getOrNull() ?: return
            var wasDown = false
            while (_state.value != State.STOPPED && !Thread.currentThread().isInterrupted) {
                val down = runCatching { user32.GetAsyncKeyState(0x01).toInt() and 0x8000 != 0 }.getOrDefault(false)
                if (down && !wasDown && _state.value == State.RECORDING) {
                    val pt = runCatching { java.awt.MouseInfo.getPointerInfo()?.location }.getOrNull()
                    val b = setup.bounds ?: runCatching { java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds.let { Area(it.x, it.y, it.width, it.height) } }.getOrNull()
                    if (pt != null && b != null && pt.x in b.x until b.x + b.w && pt.y in b.y until b.y + b.h) {
                        synchronized(clicks) { clicks += Click(seconds, (pt.x - b.x).toFloat() / b.w, (pt.y - b.y).toFloat() / b.h) }
                    }
                }
                wasDown = down
                try { Thread.sleep(20) } catch (_: InterruptedException) { return }
            }
        }

        /** Adds a click by hand (tests, and the Referentenansicht, which knows its own clicks). */
        fun click(x: Float, y: Float) { synchronized(clicks) { clicks += Click(seconds, x, y) } }
    }

    /** Folder of a new recording: „Aufnahmen/<Datum Uhrzeit>“ in the Lora-Ordner. */
    fun folder(root: File, now: Long = System.currentTimeMillis()): File =
        File(File(root, "Aufnahmen"), SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.GERMANY).format(Date(now)))
}
