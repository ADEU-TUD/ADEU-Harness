package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.creative.pngBytes
import io.github.thepytales.llmhub.transcribe.Segment
import io.github.thepytales.llmhub.transcribe.Transcript
import io.github.thepytales.llmhub.transcribe.Word
import io.github.thepytales.llmhub.video.AudioClip
import io.github.thepytales.llmhub.video.AudioSettings
import io.github.thepytales.llmhub.video.Media
import io.github.thepytales.llmhub.video.RoughCut
import io.github.thepytales.llmhub.video.SubtitleCue
import io.github.thepytales.llmhub.video.TextOverlay
import io.github.thepytales.llmhub.video.VideoClip
import io.github.thepytales.llmhub.video.VideoProject
import io.github.thepytales.llmhub.video.VideoRender
import io.github.thepytales.llmhub.video.VideoStore
import io.github.thepytales.llmhub.video.VideoTitles
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Videoeditor (AP-25): timeline, rough cut, subtitles and the export with ffmpeg (needs ffmpeg on the test machine). */
class VideoTest {
    private val base = Files.createTempDirectory("adeu-video").toFile()
    private val out = File("build/video-look").apply { mkdirs() }

    private fun ffmpegAvailable() = Media.ffmpeg()?.let { runCatching { ProcessBuilder(it, "-version").start().waitFor(10, TimeUnit.SECONDS) }.getOrNull() } == true

    /** A test video: colour bars with a tone, [sec] long. */
    private fun source(name: String, sec: Int, size: String = "640x360", tone: Int = 440): File {
        val f = File(base, name)
        val p = ProcessBuilder(
            Media.ffmpeg(), "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
            "-f", "lavfi", "-i", "testsrc2=size=$size:rate=25:duration=$sec",
            "-f", "lavfi", "-i", "sine=frequency=$tone:duration=$sec:sample_rate=48000",
            "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", f.absolutePath,
        ).redirectErrorStream(true).start()
        p.waitFor(60, TimeUnit.SECONDS)
        return f
    }

    private fun transcript(vararg segs: Segment) = Transcript(
        id = "t1", title = "Vortrag", created = "2026-11-01T10:00:00", duration = 60.0, language = "de", modelName = "test", device = "pc",
        speakers = emptyMap(), segments = segs.toList(), audio = null, folder = null, marks = emptyList(), notice = null, raw = JsonObject(emptyMap()),
    )

    @Test
    fun timelinePositionsWithTransitions() {
        val p = VideoProject(
            title = "T",
            clips = listOf(
                VideoClip(path = "a.mp4", inSec = 0.0, outSec = 10.0),
                VideoClip(path = "b.mp4", inSec = 5.0, outSec = 9.0, transition = "fade", transitionSec = 1.0),
                VideoClip(path = "c.mp4", inSec = 0.0, outSec = 3.0),
            ),
        )
        assertEquals(listOf(0.0, 9.0, 13.0), p.starts())
        assertEquals(16.0, p.duration, 1e-9)
        assertEquals(1 to 6.0, p.at(10.0))
        assertEquals(2 to 1.0, p.at(14.0))
        // A transition is never longer than half of the shorter clip.
        assertEquals(1.5, p.copy(clips = p.clips.mapIndexed { i, c -> if (i == 2) c.copy(transition = "black", transitionSec = 5.0) else c }).transitionOf(2), 1e-9)
        val store = VideoStore(base)
        val saved = store.save(p)
        assertEquals(p.clips, VideoStore(base).get(saved.id)!!.clips, "gespeichert und wieder gelesen")
    }

    @Test
    fun roughCutDropsPausesAndFillers() {
        val t = transcript(
            Segment(0, 1.0, 4.0, "Willkommen zur Vorlesung äh heute", words = listOf(Word(1.0, 1.6, "Willkommen"), Word(1.6, 2.0, "zur"), Word(2.0, 2.8, "Vorlesung"), Word(2.9, 3.3, "äh"), Word(3.4, 4.0, "heute"))),
            Segment(1, 9.0, 12.0, "Ähm."),
            Segment(2, 15.0, 18.0, "Wir beginnen mit der Wiederholung."),
        )
        val parts = RoughCut.keep(t, 0.0, 20.0, maxPause = 0.8, pad = 0.15)
        assertEquals(3, parts.size, parts.toString())
        assertEquals(0.85, parts[0].first, 1e-6)
        assertTrue(parts[0].second <= 2.9 && parts[1].first >= 3.3, "„äh“ fällt heraus: $parts")
        assertTrue(parts.none { it.first < 12.0 && it.second > 9.0 }, "nur „Ähm.“ fällt heraus")
        assertTrue(RoughCut.saved(0.0, 20.0, parts) > 12)
        val p = VideoProject(title = "T", clips = listOf(VideoClip(path = "a.mp4", inSec = 0.0, outSec = 20.0)))
        val cut = RoughCut.apply(p, 0, parts)
        assertEquals(3, cut.clips.size)
        assertEquals(setOf(1, 3, 4, 5, 8), RoughCut.numbers("1, 3-5 und 8, 99", 10))
        // Subtitles follow the cut: the second part starts on the timeline right after the first.
        val subs = RoughCut.subtitles(cut, mapOf("a.mp4" to t))
        assertTrue(subs.isNotEmpty())
        val last = subs.last { "Wiederholung" in it.text }
        assertTrue(last.startSec < 6.0, "an die neue Stelle verschoben: $last")
    }

    @Test
    fun subtitleFilesRoundTrip() {
        val p = VideoProject(title = "T", subtitles = listOf(SubtitleCue(1.0, 2.5, "Hallo"), SubtitleCue(3.0, 4.25, "Zwei\nZeilen")))
        val srt = VideoRender.srt(p)
        assertTrue("00:00:01,000 --> 00:00:02,500" in srt)
        assertEquals(p.subtitles, VideoRender.parseSubtitles(srt))
        assertEquals(p.subtitles, VideoRender.parseSubtitles(VideoRender.vtt(p)))
        assertTrue(VideoRender.filterPath(File("/x/C:/sh.rnnn")).endsWith("C\\:/sh.rnnn"), "Doppelpunkt im Filtergraph geschützt")
    }

    @Test
    fun titlesAreDrawnInTheTudDesign() {
        listOf("titel", "bauchbinde", "einblendung", "abspann").forEach { style ->
            val png = VideoTitles.png(VideoTitles.design(TextOverlay(style = style, text = "Prof. Dr. Erika Muster", subtext = "Professur für Schulpädagogik", startSec = 0.0), 1280, 720), 1280)
            File(out, "titel-$style.png").writeBytes(png)
            assertTrue(png.size > 2000, style)
        }
        File(out, "untertitel.png").writeBytes(VideoTitles.png(VideoTitles.subtitleDesign("Herzlich willkommen zur dritten Sitzung\nder Vorlesung", 1280, 720), 1280))
    }

    @Test
    fun exportsAFilmWithEverything(): Unit = runBlocking {
        if (!ffmpegAvailable()) return@runBlocking
        val a = source("a.mp4", 6)
        val b = source("b.mp4", 5, size = "480x360", tone = 660)
        val music = source("musik.mp4", 8, tone = 220)
        val info = Media.probe(a)
        assertNotNull(info)
        assertTrue(info.hasVideo && info.hasAudio && info.width == 640, info.toString())
        Media.forceNoProbe = true
        try {
            val fallback = Media.probeFfmpeg(b)!!
            assertEquals(480, fallback.width)
            assertEquals(5.0, fallback.duration, 0.2)
        } finally {
            Media.forceNoProbe = false
        }
        val p = VideoProject(
            title = "Lehrvideo Test", width = 1280, height = 720,
            clips = listOf(
                VideoClip(path = a.absolutePath, inSec = 1.0, outSec = 5.0),
                VideoClip(path = b.absolutePath, inSec = 0.0, outSec = 4.0, transition = "fade", transitionSec = 0.8),
                VideoClip(path = a.absolutePath, inSec = 0.0, outSec = 2.0, transition = "black", transitionSec = 0.5),
            ),
            overlays = listOf(TextOverlay(style = "bauchbinde", text = "Erika Muster", subtext = "TU Dresden", startSec = 0.5, durationSec = 3.0)),
            audio = listOf(AudioClip(path = music.absolutePath, startSec = 1.0, outSec = 6.0, volume = 0.2f)),
            subtitles = listOf(SubtitleCue(0.2, 2.0, "Willkommen"), SubtitleCue(4.0, 6.0, "Zweiter Teil")),
            burnSubtitles = true,
            sound = AudioSettings(denoise = "leicht", normalize = true, gainDb = 1f),
        )
        assertEquals(10.0 - 0.8 - 0.5, p.duration, 1e-6)
        val target = File(out, "export.mp4")
        var last = 0f
        VideoRender.render(p, VideoRender.preset("opal"), target) { last = it }
        assertTrue(target.isFile && target.length() > 20_000, "MP4 geschrieben")
        assertEquals(1f, last)
        val result = Media.probe(target)!!
        assertEquals(1280, result.width)
        assertEquals(720, result.height)
        assertTrue(result.hasAudio)
        assertEquals(p.duration, result.duration, 0.35)
        // A picture from the middle for a look at it.
        Media.frame(target, 1.5, 360)?.let { img ->
            File(out, "export-bild.png").writeBytes(img.pngBytes())
        }
    }
}
