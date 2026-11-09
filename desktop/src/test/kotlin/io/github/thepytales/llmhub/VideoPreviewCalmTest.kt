package io.github.thepytales.llmhub

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import io.github.thepytales.llmhub.video.Frame
import io.github.thepytales.llmhub.video.LayerClip
import io.github.thepytales.llmhub.video.Marker
import io.github.thepytales.llmhub.video.Media
import io.github.thepytales.llmhub.video.PlaybackClock
import io.github.thepytales.llmhub.video.TextOverlay
import io.github.thepytales.llmhub.video.VideoClip
import io.github.thepytales.llmhub.video.VideoPlayer
import io.github.thepytales.llmhub.video.VideoProject
import io.github.thepytales.llmhub.video.VideoRender
import java.io.File
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * AP-70 a: the preview stands still. Single pictures (from the file or the graph) and played pictures have the same
 * size and lie in the same place, also for 4:3 and portrait sources in a 16:9 project; scrubbing shows pictures and
 * never an empty one; an edit that does not change the picture starts no ffmpeg run; the clock of the playback lets the
 * pictures come evenly although the sound is read in blocks.
 */
class VideoPreviewCalmTest {
    private val base = Files.createTempDirectory("adeu-video-ruhig").toFile()

    private fun ffmpeg(): String? = Media.ffmpeg()?.takeIf { runCatching { ProcessBuilder(it, "-version").start().waitFor(10, TimeUnit.SECONDS) }.getOrNull() == true }

    /** A test picture (moving pattern, tagged like camera footage) with a tone. */
    private fun source(name: String, size: String, rate: Int, sec: Int = 4): File {
        val f = File(base, name)
        val p = ProcessBuilder(
            listOf(ffmpeg()!!, "-nostdin", "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "testsrc2=s=$size:r=$rate:d=$sec",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=$sec:sample_rate=48000", "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p",
                "-colorspace", "bt709", "-color_primaries", "bt709", "-color_trc", "bt709", "-c:a", "aac", "-shortest", f.absolutePath),
        ).redirectErrorStream(true).start()
        val log = p.inputStream.bufferedReader().readText()
        p.waitFor(120, TimeUnit.SECONDS)
        assertTrue(f.isFile && f.length() > 0, "Testvideo $name: $log")
        Media.makeProxy(f)
        return f
    }

    /** Size of a picture, where its content lies (the bars are black) and how bright it is. */
    private data class Look(val w: Int, val h: Int, val left: Int, val top: Int, val right: Int, val bottom: Int, val luma: Double) {
        val blank get() = luma < 8
    }

    private fun look(img: ImageBitmap): Look {
        val a = img.toAwtImage()
        var l = a.width; var r = -1; var t = a.height; var b = -1; var sum = 0L
        for (y in 0 until a.height) for (x in 0 until a.width) {
            val c = a.getRGB(x, y)
            val v = ((c shr 16 and 0xff) + (c shr 8 and 0xff) + (c and 0xff)) / 3
            sum += v
            if (v > 24) { l = minOf(l, x); r = maxOf(r, x); t = minOf(t, y); b = maxOf(b, y) }
        }
        return Look(a.width, a.height, l, t, r, b, sum.toDouble() / (a.width * a.height))
    }

    private fun meanDiff(a: ImageBitmap, b: ImageBitmap): Double {
        val x = a.toAwtImage(); val y = b.toAwtImage()
        var d = 0L
        for (j in 0 until x.height) for (i in 0 until x.width) {
            val c1 = x.getRGB(i, j); val c2 = y.getRGB(i, j)
            d += abs((c1 shr 16 and 0xff) - (c2 shr 16 and 0xff)) + abs((c1 shr 8 and 0xff) - (c2 shr 8 and 0xff)) + abs((c1 and 0xff) - (c2 and 0xff))
        }
        return d / (3.0 * x.width * x.height)
    }

    private fun waitFor(ms: Long, until: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (!until() && System.currentTimeMillis() < end) Thread.sleep(20)
    }

    /**
     * Fertig, wenn: abspielen und scrubben eines 1080p-Projekts – auch mit Quellen, die nicht 16:9 sind – zuckt nicht:
     * gleiche Größe und Lage im Stand und beim Abspielen, keine leeren Bilder.
     */
    @Test
    fun stillAndPlaybackLieTheSame() {
        if (ffmpeg() == null) return
        val wide = source("breit.mp4", "1920x1080", 25)
        val old = source("vier-drittel.mp4", "1440x1080", 25)
        val tall = source("hochformat.mp4", "1080x1920", 30)
        val p = VideoProject(
            title = "Ruhig", width = 1920, height = 1080, fadeEnds = false,
            clips = listOf(
                VideoClip(path = wide.absolutePath, inSec = 0.0, outSec = 2.5),
                VideoClip(path = old.absolutePath, inSec = 0.0, outSec = 2.5, transition = "fade", transitionSec = 0.5),
                VideoClip(path = tall.absolutePath, inSec = 0.0, outSec = 4.0),
            ),
        )
        val (w, h) = VideoRender.previewSize(p)
        val player = VideoPlayer()
        try {
            // From the file and through the graph (an invisible picture on V2 forces it): the same picture.
            val forced = p.copy(layers = listOf(0.0, 4.0).map { LayerClip(path = wide.absolutePath, startSec = it, outSec = 4.0, frame = Frame(opacity = 0f)) })
            val boxes = mutableMapOf<Int, Look>()
            for ((i, t) in listOf(0 to 1.0, 1 to 3.2, 2 to 5.5)) {
                assertTrue(!player.needsGraph(p, t) && player.needsGraph(forced, t))
                val file = assertNotNull(player.picture(p, t))
                val graph = assertNotNull(player.picture(forced, t))
                val a = look(file); val b = look(graph)
                assertEquals(w to h, a.w to a.h, "Standbild aus der Datei in Vorschaugröße (Clip $i)")
                assertEquals(a.copy(luma = 0.0), b.copy(luma = 0.0), "Datei und Graph deckungsgleich (Clip $i)")
                assertTrue(meanDiff(file, graph) < 3.0, "Datei und Graph gleich scharf und farbig (Clip $i): ${meanDiff(file, graph)}")
                boxes[i] = a
            }
            assertTrue(boxes[1]!!.left > 60 && boxes[2]!!.left > 200, "Balken bei 4:3 und Hochformat: $boxes")

            // The sequence the screen gets: still → playing (through all three clips) → still → scrubbing.
            val shown = Collections.synchronizedList(mutableListOf<VideoPlayer.Shown>())
            val times = Collections.synchronizedMap(java.util.IdentityHashMap<VideoPlayer.Shown, Long>())
            player.onShown = { times[it] = System.nanoTime(); shown += it }
            player.seek(p, 1.0)
            waitFor(10_000) { shown.isNotEmpty() }
            player.play(p)
            waitFor(20_000) { player.position.value > 6.2 || !player.playing.value }
            player.stop()
            val stopAt = player.position.value
            player.seek(p, stopAt + 0.001)
            Thread.sleep(400)
            player.seek(p, 5.0)
            waitFor(10_000) { shown.lastOrNull()?.source == VideoPlayer.Source.FILE && abs(shown.last().at - 5.0) < 1e-6 }
            val frames = synchronized(shown) { shown.toList() }
            val played = frames.filter { it.source == VideoPlayer.Source.PLAY }
            assertTrue(played.size > 100, "Wiedergabe lief: ${played.size} Bilder")
            frames.forEach { s ->
                val l = look(s.image)
                assertEquals(w to h, l.w to l.h, "jedes Bild in Vorschaugröße (${s.source} bei ${s.at})")
                assertTrue(!l.blank, "kein leeres Bild (${s.source} bei ${s.at})")
                // Outside the cross-dissolve each picture lies exactly where the still of its clip lies.
                val clip = when { s.at < 1.95 -> 0; s.at in 2.55..4.45 -> 1; s.at >= 4.55 -> 2; else -> -1 }
                if (clip >= 0) assertEquals(boxes[clip]!!.copy(luma = 0.0), l.copy(luma = 0.0), "Lage im Clip $clip (${s.source} bei ${s.at})")
            }
            // Pictures while playing came evenly (wall clock here): no burst at the start (before: 10 pictures in 0.3 s).
            val start = (times[played[10]]!! - times[played[0]]!!) / 1_000_000
            assertTrue(start >= 360, "die ersten zehn Bilder in ${start} ms")
        } finally {
            player.dispose()
        }
    }

    @Test
    fun scrubbingShowsPicturesAndNeverNone() {
        if (ffmpeg() == null) return
        val wide = source("scrub.mp4", "1920x1080", 25, sec = 6)
        // The clip runs to the very end of its file: the last instant used to give no picture (black).
        val p = VideoProject(title = "Scrubben", width = 1920, height = 1080, fadeEnds = false, clips = listOf(VideoClip(path = wide.absolutePath, inSec = 0.0, outSec = 6.0)))
        val player = VideoPlayer()
        try {
            val shown = Collections.synchronizedList(mutableListOf<VideoPlayer.Shown>())
            player.onShown = { shown += it }
            player.seek(p, 0.2)
            waitFor(10_000) { shown.isNotEmpty() }
            shown.clear()
            // Dragging the playhead: a request every 25 ms for a second.
            for (i in 0 until 40) { player.seek(p, 0.5 + i * 0.12); Thread.sleep(25) }
            val during = shown.size
            waitFor(10_000) { shown.lastOrNull()?.let { abs(it.at - (0.5 + 39 * 0.12)) < 1e-6 } == true }
            assertTrue(during >= 3, "Bilder schon während des Ziehens: $during")
            assertEquals(0.5 + 39 * 0.12, shown.last().at, 1e-6, "zuletzt das Bild am Abspielkopf")
            val ats = shown.map { it.at }
            assertEquals(ats.sorted(), ats, "nie ein älteres Bild nach einem neueren")
            assertTrue(player.frame.value != null)
            player.seek(p, p.duration)
            waitFor(10_000) { abs((shown.lastOrNull()?.at ?: 0.0) - p.duration) < 1e-6 }
            val end = assertNotNull(shown.lastOrNull())
            assertEquals(p.duration, end.at, 1e-6, "am Ende ein Bild")
            assertTrue(!look(end.image).blank, "am Ende kein schwarzes Bild")
            assertTrue(shown.none { look(it.image).blank })
        } finally {
            player.dispose()
        }
    }

    @Test
    fun anEditThatKeepsThePictureNeedsNoRun() {
        if (ffmpeg() == null) return
        val wide = source("edit.mp4", "1280x720", 25)
        val p = VideoProject(title = "Bearbeiten", width = 1280, height = 720, fadeEnds = false, clips = listOf(VideoClip(path = wide.absolutePath, inSec = 0.0, outSec = 4.0)))
        val player = VideoPlayer()
        try {
            player.seek(p, 1.0)
            waitFor(10_000) { player.frame.value != null }
            val runs = player.stillRuns
            val shownBefore = player.frame.value
            // Title, markers, a title overlay, In/Out: the picture stays the same.
            player.seek(p.copy(title = "Neu", markers = listOf(Marker(sec = 2.0, note = "Kapitel")), overlays = listOf(TextOverlay(text = "Titel", startSec = 0.5)), markIn = 0.5), 1.0)
            Thread.sleep(500)
            assertEquals(runs, player.stillRuns, "kein ffmpeg-Lauf ohne Änderung am Bild")
            assertTrue(player.frame.value === shownBefore)
            // A real change of the picture: one run, and the old picture stays until the new one is there.
            player.seek(p.copy(clips = listOf(p.clips[0].copy(inSec = 1.0))), 1.0)
            waitFor(10_000) { player.frame.value !== shownBefore }
            assertTrue(player.stillRuns > runs)
        } finally {
            player.dispose()
        }
    }

    /** The sound is read in 50 ms blocks; the pictures (every 40 ms) still come evenly. */
    @Test
    fun clockLetsPicturesComeEvenly() {
        var now = 0L
        val clock = PlaybackClock { now }
        val latency = 0.02
        fun soundAt(ms: Long) = maxOf(0.0, ms / 1000.0 - latency)
        // The old way: the last reading of the sound as it was.
        var reading = -1.0
        val newTimes = mutableListOf<Long>()
        val oldTimes = mutableListOf<Long>()
        var nextNew = 0; var nextOld = 0
        for (ms in 0L..3000L) {
            now = ms * 1_000_000
            if (ms % 50 == 0L) { reading = soundAt(ms); clock.sound(reading) }
            while (nextNew < 70 && clock.now() + 0.01 >= nextNew * 0.04) { newTimes += ms; nextNew++ }
            while (nextOld < 70 && reading >= 0 && reading + 0.01 >= nextOld * 0.04) { oldTimes += ms; nextOld++ }
        }
        val gapsNew = newTimes.zipWithNext { a, b -> b - a }.drop(3)
        val gapsOld = oldTimes.zipWithNext { a, b -> b - a }.drop(3)
        assertTrue(gapsOld.any { it == 0L }, "vorher kamen Bilder paarweise: $gapsOld")
        assertTrue(gapsNew.all { it in 30L..50L }, "jetzt gleichmäßig: $gapsNew")
        // Without sound the wall clock counts from the first picture, and the clock never goes back.
        var t = 5_000_000_000L
        val free = PlaybackClock { t }
        assertEquals(0.0, free.now(), 1e-9)
        t += 400_000_000
        assertEquals(0.4, free.now(), 1e-9)
        free.sound(0.1)
        assertEquals(0.4, free.now(), 1e-9, "geht nicht zurück")
    }
}
