package io.github.thepytales.llmhub

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.data.ElementKind
import io.github.thepytales.llmhub.ui.I18n
import io.github.thepytales.llmhub.ui.LlmHubTheme
import io.github.thepytales.llmhub.ui.VideoHub
import io.github.thepytales.llmhub.ui.VideoScreen
import io.github.thepytales.llmhub.video.Media
import io.github.thepytales.llmhub.video.MediaInfo
import io.github.thepytales.llmhub.video.SubtitleCue
import io.github.thepytales.llmhub.video.TextOverlay
import io.github.thepytales.llmhub.video.VideoClip
import io.github.thepytales.llmhub.video.VideoPlayer
import io.github.thepytales.llmhub.video.VideoProject
import io.github.thepytales.llmhub.video.VideoRender
import io.github.thepytales.llmhub.video.VideoTitles
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fehler aus dem Video-Review (nach 1.0.53): each test failed with the code before the fix. Pictures in
 * build/video-review.
 */
@OptIn(ExperimentalTestApi::class)
class VideoReviewTest {
    private val base = Files.createTempDirectory("adeu-video-review").toFile()
    private val out = File("build/video-review").apply { mkdirs() }

    private fun ffmpeg(): String? = Media.ffmpeg()?.takeIf { runCatching { ProcessBuilder(it, "-version").start().waitFor(10, TimeUnit.SECONDS) }.getOrNull() == true }

    private fun source(name: String, sec: Int): File {
        val f = File(base, name)
        ProcessBuilder(
            ffmpeg(), "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
            "-f", "lavfi", "-i", "testsrc2=size=320x180:rate=25:duration=$sec",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=$sec:sample_rate=48000",
            "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", f.absolutePath,
        ).redirectErrorStream(true).start().waitFor(60, TimeUnit.SECONDS)
        return f
    }

    // ── Model and export (no screen) ────────────────────────────────────────────────────────────────

    /** A clip under 0.2 s with a transition: coerceIn(0.1, < 0.1) threw – in every drawing of the timeline. */
    @Test
    fun shortClipWithTransitionDoesNotThrow() {
        val p = VideoProject(
            title = "Kurz",
            clips = listOf(
                VideoClip(path = "bild.png", inSec = 0.0, outSec = 0.1),
                VideoClip(path = "a.mp4", inSec = 0.0, outSec = 4.0, transition = "fade"),
                VideoClip(path = "b.mp4", inSec = 0.0, outSec = 0.0, transition = "black"),
            ),
        )
        assertEquals(0.05, p.transitionOf(1), 1e-9)
        assertEquals(0.0, p.transitionOf(2))
        assertEquals(3, p.starts().size)
        assertTrue(p.duration > 4.0)
    }

    /** 9:16 and 1:1: titles and lower thirds were laid out for 1.8 × the height and ran out of the picture. */
    @Test
    fun titlesStayInsidePortraitAndSquareFrames() {
        val mmPerPx = io.github.thepytales.llmhub.data.DesignFormat.MM_PER_PX
        for ((w, h) in listOf(1080 to 1920, 1080 to 1080, 1920 to 1080, 1280 to 720)) {
            for (style in listOf("titel", "bauchbinde", "einblendung", "abspann")) {
                val d = VideoTitles.design(TextOverlay(style = style, text = "Prof. Dr. Erika Muster", subtext = "Professur für Schulpädagogik", startSec = 0.0), w, h)
                d.pages.first().elements.filter { it.kind == ElementKind.TEXT }.forEach { e ->
                    assertTrue(e.x >= 0f && e.x + e.w <= w * mmPerPx + 0.01f, "$style ${w}x$h: Text bei ${e.x}…${e.x + e.w} mm, Bild ${w * mmPerPx} mm breit")
                    assertTrue(e.y >= 0f && e.y + e.h <= h * mmPerPx + 0.01f, "$style ${w}x$h: Text bei y ${e.y}…${e.y + e.h} mm")
                }
                File(out, "${w}x$h-$style.png").writeBytes(VideoTitles.png(VideoTitles.design(TextOverlay(style = style, text = "Prof. Dr. Erika Muster", subtext = "Professur für Schulpädagogik", startSec = 0.0), w, h), w / 3))
            }
            val sub = VideoTitles.subtitleDesign("Herzlich willkommen zur dritten Sitzung der Vorlesung", w, h).pages.first().elements.first { it.kind == ElementKind.TEXT }
            assertTrue(sub.w >= w * mmPerPx * 0.7f, "Untertitel ${w}x$h nur ${sub.w} mm breit")
            File(out, "${w}x$h-untertitel.png").writeBytes(VideoTitles.png(VideoTitles.subtitleDesign("Herzlich willkommen zur dritten Sitzung der Vorlesung", w, h), w / 3))
        }
    }

    /** A subtitle during a lower third covered its second line; now it goes above it (preview and export). */
    @Test
    fun subtitleGoesAboveALowerThird() {
        val lower = TextOverlay(style = "bauchbinde", text = "Prof. Dr. Erika Muster", subtext = "Professur für Schulpädagogik", startSec = 2.0, durationSec = 5.0)
        val p = VideoProject(title = "T", overlays = listOf(lower))
        assertTrue(p.subtitleRaised(SubtitleCue(1.0, 3.0, "a")))
        assertTrue(!p.subtitleRaised(SubtitleCue(7.5, 9.0, "b")))
        val box = { raised: Boolean -> VideoTitles.subtitleDesign("Herzlich willkommen", 1920, 1080, raised).pages.first().elements.first { it.kind == ElementKind.RECT } }
        val lowerTop = VideoTitles.design(lower, 1920, 1080).pages.first().elements.minOf { it.y }
        assertTrue(box(false).y + box(false).h > lowerTop, "unten überdeckt der Untertitel die Bauchbinde (Ausgangslage)")
        assertTrue(box(true).y + box(true).h <= lowerTop, "angehoben liegt er darüber")
        val pic = VideoTitles.render(VideoTitles.design(lower, 1920, 1080), 960).toAwtImage()
        val subPic = VideoTitles.render(VideoTitles.subtitleDesign("Herzlich willkommen zur dritten Sitzung.", 1920, 1080, true), 960).toAwtImage()
        pic.graphics.drawImage(subPic, 0, 0, null)
        ImageIO.write(pic, "png", File(out, "bauchbinde-mit-untertitel.png"))
    }

    /** The last line of the subtitle list was not quoted like the others: a ' in the user folder broke the export. */
    @Test
    fun subtitleListQuotesApostrophes() {
        val work = File(base, "O'Neil/export").apply { mkdirs() }
        val p = VideoProject(
            title = "T", clips = listOf(VideoClip(path = File(base, "a.mp4").absolutePath, inSec = 0.0, outSec = 6.0)),
            subtitles = listOf(SubtitleCue(1.0, 2.5, "Hallo")), burnSubtitles = true,
        )
        VideoRender.plan(p, VideoRender.preset("opal"), File(base, "out.mp4"), work, { MediaInfo(6.0, 320, 180, 25.0, hasVideo = true, hasAudio = true) }, rnnoise = null)
        val lines = File(work, "untertitel.ffconcat").readLines().filter { it.startsWith("file ") }
        assertTrue(lines.size >= 3, lines.toString())
        lines.forEach { assertTrue(Regex("""file '([^']|'\\'')*'""").matches(it), "nicht richtig gequotet: $it") }
    }

    /**
     * A clip set longer than its file: its picture ended early while its sound was padded – at a cross-dissolve every
     * clip after it ran ahead of its sound. Now picture and sound of the film are equally long.
     */
    @Test
    fun clipLongerThanItsFileKeepsPictureAndSoundTogether(): Unit = runBlocking {
        val exe = ffmpeg() ?: return@runBlocking
        val a = source("kurz.mp4", 3)
        val b = source("zwei.mp4", 3)
        val p = VideoProject(
            title = "Sync", width = 640, height = 360, fadeEnds = false, sound = io.github.thepytales.llmhub.video.AudioSettings(normalize = false),
            clips = listOf(VideoClip(path = a.absolutePath, inSec = 0.0, outSec = 5.0), VideoClip(path = b.absolutePath, inSec = 0.0, outSec = 3.0, transition = "fade", transitionSec = 0.6)),
        )
        val film = VideoRender.render(p, VideoRender.preset("projekt"), File(base, "sync.mp4")) { }
        val probe = ProcessBuilder(exe.replace("ffmpeg", "ffprobe").takeIf { File(it).canExecute() } ?: "ffprobe", "-v", "error", "-show_streams", "-print_format", "json", film.absolutePath)
            .redirectErrorStream(true).start()
        val json = JSONObject(probe.inputStream.bufferedReader().readText())
        val streams = json.getJSONArray("streams")
        val durations = (0 until streams.length()).associate { i -> streams.getJSONObject(i).let { it.getString("codec_type") to it.getString("duration").toDouble() } }
        val video = durations.getValue("video")
        val audio = durations.getValue("audio")
        assertEquals(p.duration, video, 0.15, "Bild $video s, Projekt ${p.duration} s")
        assertTrue(abs(video - audio) < 0.15, "Bild $video s, Ton $audio s")
    }

    /** A picture: the preview at 2 s into it was black (ffmpeg found no frame after 0), and playing jumped over it. */
    @Test
    fun stillPicturesShowAndPlay() {
        val exe = ffmpeg() ?: return
        val png = File(base, "folie.png")
        ProcessBuilder(exe, "-nostdin", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "testsrc2=size=320x180", "-frames:v", "1", png.absolutePath).start().waitFor(30, TimeUnit.SECONDS)
        assertNotNull(Media.frame(png, 2.0), "Bild bei 2 s")
        val p = VideoProject(title = "Folie", clips = listOf(VideoClip(path = png.absolutePath, inSec = 0.0, outSec = 1.5)))
        val player = VideoPlayer()
        try {
            val t0 = System.nanoTime()
            player.play(p)
            while (!player.playing.value && System.nanoTime() - t0 < 2e9) Thread.sleep(10)
            while (player.playing.value && System.nanoTime() - t0 < 10e9) Thread.sleep(20)
            val sec = (System.nanoTime() - t0) / 1e9
            assertTrue(sec >= 1.2, "Die Folie lief nur $sec s statt 1,5 s")
        } finally {
            player.dispose()
        }
    }

    // ── Editor ──────────────────────────────────────────────────────────────────────────────────────

    private val owner = object : androidx.lifecycle.LifecycleOwner {
        val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
    }

    private fun SkikoComposeUiTest.settle(rounds: Int = 8) {
        repeat(rounds) { Thread.sleep(80); mainClock.advanceTimeBy(300) }
        waitForIdle()
    }

    /** Opens [project] in the editor (a fresh data folder; the clips need not exist). */
    private fun SkikoComposeUiTest.editor(project: VideoProject, settings: AppSettings = AppSettings()): AppContainer {
        mainClock.autoAdvance = false
        val c = AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
        c.appContext.getSharedPreferences("ui_state", 0).edit().putBoolean("videoSetupSeen", true).apply()
        VideoHub.store.save(project)
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                LlmHubTheme(settings) {
                    androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) { VideoScreen(c, onBack = {}) }
                }
            }
        }
        settle()
        onNodeWithText(project.title).performClick()
        settle()
        return c
    }

    private fun stored(id: String) = VideoHub.store.get(id)!!

    /** The node that shows [text] now – found again by its id after its text changed. */
    private fun SkikoComposeUiTest.fixed(text: String): androidx.compose.ui.test.SemanticsNodeInteraction {
        val id = onNodeWithText(text).fetchSemanticsNode().id
        return onNode(SemanticsMatcher("Knoten $id") { it.id == id })
    }

    /** A 90-minute lecture: the ruler found no step of at least 70 px (first {} threw while drawing). */
    @Test
    fun timelineOfALongLectureDraws() = runSkikoComposeUiTest(Size(1300f, 900f), Density(1f)) {
        val p = VideoProject(title = "Vorlesung lang", clips = listOf(VideoClip(path = File(base, "fehlt.mp4").absolutePath, inSec = 0.0, outSec = 5400.0)))
        editor(p)
        assertTrue(onAllNodesWithText("Exportieren").fetchSemanticsNodes().isNotEmpty(), "Editor offen")
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "zeitleiste-90-minuten.png"))
    }

    /**
     * Typing in a text field of the editor: S split the clip, Entf deleted the selected lower third while its name was
     * being edited.
     */
    @Test
    fun shortcutsDoNotFireWhileTyping() = runSkikoComposeUiTest(Size(1500f, 950f), Density(1f)) {
        val p = VideoProject(title = "Tippen", clips = listOf(VideoClip(path = File(base, "fehlt.mp4").absolutePath, inSec = 0.0, outSec = 30.0)))
        editor(p)
        // Playhead into the clip, then a lower third there (selected, its fields in the panel).
        repeat(12) { onRoot().performKeyInput { pressKey(Key.DirectionRight) } }
        settle(2)
        onNodeWithText("Titel & Bauchbinde").performClick()
        settle(2)
        onAllNodesWithText("Bauchbinde")[0].performClick()
        settle(4)
        assertEquals(1, stored(p.id).overlays.size)
        val name = fixed("Vorname Nachname")
        name.performClick()
        settle(2)
        name.performKeyInput { pressKey(Key.Delete) }
        name.performKeyInput { pressKey(Key.S) }
        name.performKeyInput { pressKey(Key.Spacebar) }
        settle(6)
        val now = stored(p.id)
        assertEquals(1, now.clips.size, "S hat den Clip geteilt")
        assertEquals(1, now.overlays.size, "Entf hat die Bauchbinde gelöscht")
    }

    /** „Ende (s)“: typing 45 into a clip that starts at 12.5 s – the 4 was corrected to 12.7 and the text replaced. */
    @Test
    fun secondsCanBeTypedDigitByDigit() = runSkikoComposeUiTest(Size(1500f, 950f), Density(1f)) {
        val p = VideoProject(title = "Sekunden", clips = listOf(VideoClip(path = File(base, "fehlt.mp4").absolutePath, inSec = 12.5, outSec = 30.0)))
        editor(p)
        // The clip in the timeline: right below the row with the zoom slider (1–12), in the video lane (ruler 22 px,
        // lane 60 px).
        val zoom = SemanticsMatcher("Zoom-Regler") { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == 1f..12f }
        val rowBottom = onAllNodes(zoom)[0].fetchSemanticsNode().boundsInRoot.bottom
        onRoot().performTouchInput { click(Offset(400f, rowBottom + 22f + 30f)) }
        settle(4)
        val end = fixed("30,00")
        end.performClick()
        end.performTextClearance()
        end.performTextInput("4")
        settle(4)
        end.performTextInput("5")
        settle(6)
        assertEquals(45.0, stored(p.id).clips.single().outSec, 1e-9)
    }

    /** English: tr("aus") was stored as "off", so no chip of the noise reduction was selected. */
    @Test
    fun noiseReductionOffIsSelectedInEnglish() = runSkikoComposeUiTest(Size(1500f, 950f), Density(1f)) {
        try {
            editor(VideoProject(title = "Rauschtest 7", clips = listOf(VideoClip(path = File(base, "fehlt.mp4").absolutePath, inSec = 0.0, outSec = 10.0))), AppSettings(uiLanguage = "en"))
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "editor-englisch.png"))
            onNodeWithText("Off").assertIsSelected()
        } finally {
            I18n.language = "de"
        }
    }
}
