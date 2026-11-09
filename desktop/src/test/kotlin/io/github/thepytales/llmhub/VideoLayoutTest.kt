package io.github.thepytales.llmhub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.ui.LlmHubTheme
import io.github.thepytales.llmhub.ui.LoraHelper
import io.github.thepytales.llmhub.ui.LoraTourLayer
import io.github.thepytales.llmhub.ui.LoraTours
import io.github.thepytales.llmhub.ui.PREVIEW_MIN_WIDTH
import io.github.thepytales.llmhub.ui.Spot
import io.github.thepytales.llmhub.ui.Spotlight
import io.github.thepytales.llmhub.ui.VideoHub
import io.github.thepytales.llmhub.ui.VideoLayout
import io.github.thepytales.llmhub.ui.VideoScreen
import io.github.thepytales.llmhub.ui.fitPanels
import io.github.thepytales.llmhub.ui.fitTimeline
import io.github.thepytales.llmhub.ui.tourCardPosition
import io.github.thepytales.llmhub.video.AudioClip
import io.github.thepytales.llmhub.video.LayerClip
import io.github.thepytales.llmhub.video.Marker
import io.github.thepytales.llmhub.video.Media
import io.github.thepytales.llmhub.video.SubtitleCue
import io.github.thepytales.llmhub.video.TextOverlay
import io.github.thepytales.llmhub.video.VideoClip
import io.github.thepytales.llmhub.video.VideoEdit
import io.github.thepytales.llmhub.video.VideoProject
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The arrangement of the video editor (AP-70 c–e). Pictures in build/video-layout. */
@OptIn(ExperimentalTestApi::class)
class VideoLayoutTest {
    private val base = Files.createTempDirectory("adeu-video-layout").toFile()
    private val out = File("build/video-layout").apply { mkdirs() }

    private fun ffmpeg(): String? = Media.ffmpeg()?.takeIf { runCatching { ProcessBuilder(it, "-version").start().waitFor(10, TimeUnit.SECONDS) }.getOrNull() == true }

    private fun source(name: String, sec: Int, size: String = "640x360"): File {
        val f = File(base, name)
        ProcessBuilder(
            ffmpeg(), "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
            "-f", "lavfi", "-i", "testsrc2=size=$size:rate=25:duration=$sec",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=$sec:sample_rate=48000",
            "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", f.absolutePath,
        ).redirectErrorStream(true).start().waitFor(60, TimeUnit.SECONDS)
        return f
    }

    /** A lecture with a webcam corner, music, a lower third, subtitles and markers. */
    private fun project(): VideoProject {
        val a = source("vorlesung.mp4", 12)
        val cam = source("webcam.mp4", 6, "320x240")
        return VideoEdit.withPool(
            VideoProject(
                title = "Vorlesung 3 – Einführung in die Schulpädagogik", width = 1920, height = 1080,
                clips = listOf(VideoClip(path = a.absolutePath, inSec = 0.0, outSec = 6.0), VideoClip(path = a.absolutePath, inSec = 7.0, outSec = 12.0, transition = "fade")),
                layers = listOf(LayerClip(path = cam.absolutePath, startSec = 1.0, outSec = 5.0)),
                audio = listOf(AudioClip(path = a.absolutePath, startSec = 0.0, outSec = 10.0, track = 1)),
                overlays = listOf(TextOverlay(style = "bauchbinde", text = "Prof. Dr. Erika Muster", subtext = "Professur für Schulpädagogik", startSec = 0.0, durationSec = 5.0)),
                subtitles = listOf(SubtitleCue(0.0, 3.0, "Herzlich willkommen zur dritten Sitzung.")),
                markers = listOf(Marker(sec = 4.0, note = "Beispiel")),
                burnSubtitles = true,
            ),
        )
    }

    private fun SkikoComposeUiTest.settle(rounds: Int = 10) {
        repeat(rounds) { Thread.sleep(150); mainClock.advanceTimeBy(300) }
        waitForIdle()
    }

    private fun context(dir: File) = android.content.Context(File(dir, "files").apply { mkdirs() }, File(dir, "cache").apply { mkdirs() })

    private fun container(): AppContainer {
        val c = AppContainer(context(base))
        c.appContext.getSharedPreferences("ui_state", 0).edit().putBoolean("videoSetupSeen", true).apply()
        return c
    }

    /** Widths, heights and folded panels, tracks and sections are in ui_state: a new start reads them; reset clears them. */
    @Test
    fun arrangementSurvivesRestartAndResets() {
        val first = context(File(base, "pc1"))
        val l = VideoLayout(first.getSharedPreferences("ui_state", 0))
        l.pool.drag(80.dp); l.pool.save()
        l.props.drag(-40.dp); l.props.save()
        l.timeline.drag(120.dp); l.timeline.save()
        l.poolOpen = false
        l.menu = true
        l.leftTab = "text"
        l.tracksTaller(true)
        l.toggleTrack("A1"); l.toggleTrack("S")
        l.setSection("fx.time", true); l.setSection("clip.sound", false)
        // A new start: the file of ui_state copied to another data folder, read by a fresh instance.
        val again = File(base, "pc2/files").apply { mkdirs() }
        File(base, "pc1/files/ui_state.prefs.json").copyTo(File(again, "ui_state.prefs.json"))
        val r = VideoLayout(context(File(base, "pc2")).getSharedPreferences("ui_state", 0))
        assertEquals(340.dp, r.pool.value)
        assertEquals(280.dp, r.props.value, "nicht unter das Minimum")
        assertEquals(420.dp, r.timeline.value)
        assertFalse(r.poolOpen)
        assertTrue(r.propsOpen)
        assertTrue(r.menu)
        assertEquals("text", r.leftTab)
        assertEquals(1.25f, r.trackScale, 1e-4f)
        assertEquals(setOf("A1", "S"), r.foldedTracks)
        assertTrue(r.sectionOpen("fx.time", false))
        assertFalse(r.sectionOpen("clip.sound", true))
        assertTrue(r.sectionOpen("clip.time", true), "unbekannte Abschnitte behalten ihre Vorgabe")
        // Anordnung zurücksetzen: everything as on the first start, also after the next start.
        r.reset()
        val fresh = VideoLayout(context(File(base, "pc2")).getSharedPreferences("ui_state", 0))
        for (x in listOf(r, fresh)) {
            assertEquals(260.dp, x.pool.value); assertEquals(320.dp, x.props.value); assertEquals(300.dp, x.timeline.value)
            assertTrue(x.poolOpen && x.propsOpen && !x.menu && !x.bigPreview)
            assertEquals("media", x.leftTab); assertEquals(1f, x.trackScale); assertTrue(x.foldedTracks.isEmpty())
            assertFalse(x.sectionOpen("fx.time", false))
        }
        base.deleteRecursively()
    }

    /** Too narrow: panels first get narrower, then one folds in (the one opened last stays); heights keep the viewer. */
    @Test
    fun panelsFoldWhenTooNarrow() {
        val pool = 260.dp to 200.dp
        val props = 320.dp to 280.dp
        // Full HD at 125 %: everything as wanted.
        assertEquals(260.dp, fitPanels(1536.dp, pool, props).pool)
        assertEquals(320.dp, fitPanels(1536.dp, pool, props).props)
        // 1366 × 768 at 150 % (910 dp): narrower, both still there, the preview keeps its minimum.
        val small = fitPanels(910.dp, pool, props)
        assertNotNull(small.pool); assertNotNull(small.props)
        assertTrue(small.pool!! + small.props!! + PREVIEW_MIN_WIDTH + 20.dp <= 910.dp + 0.5.dp, "$small")
        // With Lora's panel beside it (≈ 520 dp left): one folds in, the properties stay unless the pool was opened last.
        assertNull(fitPanels(700.dp, pool, props).pool)
        assertEquals(320.dp, fitPanels(700.dp, pool, props).props)
        assertNull(fitPanels(700.dp, pool, props, preferPool = true).props)
        assertNotNull(fitPanels(700.dp, pool, props, preferPool = true).pool)
        // A panel the user closed stays closed.
        assertNull(fitPanels(1536.dp, null, props).pool)
        // The timeline leaves the viewer at least 190 dp.
        assertEquals(150.dp, fitTimeline(300.dp, 400.dp, 150.dp))
        assertEquals(300.dp, fitTimeline(900.dp, 300.dp, 150.dp))
    }

    /** The card of a tour step never covers its place: above, below or beside it, whichever has room. */
    @Test
    fun tourCardBesideItsPlace() {
        val area = IntSize(1366, 650)
        val card = IntSize(520, 140)
        for (t in listOf(Rect(100f, 520f, 600f, 560f), Rect(1200f, 10f, 1350f, 50f), Rect(500f, 300f, 700f, 340f), Rect(0f, 100f, 400f, 600f), Rect(20f, 400f, 1340f, 640f))) {
            val at = tourCardPosition(t, card, area, 16f, 12f, 24f)
            val r = Rect(at.x.toFloat(), at.y.toFloat(), at.x + card.width.toFloat(), at.y + card.height.toFloat())
            assertFalse(r.overlaps(t), "Karte $r über dem Ziel $t")
            assertTrue(r.left >= 0f && r.top >= 0f && r.right <= area.width && r.bottom <= area.height, "Karte $r außerhalb")
        }
        // Without a place: at the bottom in the middle, as before.
        val home = tourCardPosition(null, card, area, 16f, 12f, 24f)
        assertEquals((1366 - 520) / 2, home.x)
        assertEquals(650 - 140 - 24, home.y)

        // On screen: a place low in the window, as the toolbar above the timeline – the card goes above it.
        runSkikoComposeUiTest(Size(1366f, 650f), Density(1f)) {
            mainClock.autoAdvance = false
            val c = container()
            val owner = lifecycle()
            setContent {
                androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                    LlmHubTheme(AppSettings()) {
                        Box(Modifier.fillMaxSize()) {
                            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                                Row(Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.Center) {
                                    Spot("video.titles") { Box(Modifier.size(200.dp, 40.dp)) }
                                }
                            }
                            LoraTourLayer(c)
                        }
                    }
                }
            }
            LoraHelper.route.value = "video"
            val tour = LoraTours.byId("video")!!
            LoraTours.start(tour)
            while (LoraTours.active.value?.let { (t, i) -> t.steps[i].spot } != "video.titles") LoraTours.next()
            settle(8)
            val cardBounds = onNodeWithTag("lora.tour.card").fetchSemanticsNode().boundsInRoot
            val target = assertNotNull(Spotlight.bounds["video.titles"])
            assertFalse(cardBounds.overlaps(target), "Karte $cardBounds über dem Ziel $target")
            assertTrue(cardBounds.bottom <= target.top, "Karte über dem Ziel: $cardBounds / $target")
            LoraTours.stop()
            settle(2)
        }
        base.deleteRecursively()
    }

    private fun lifecycle() = object : androidx.lifecycle.LifecycleOwner {
        val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
    }

    private fun SemanticsNode.layout(): TextLayoutResult? {
        val results = mutableListOf<TextLayoutResult>()
        return config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)?.takeIf { it }?.let { results.firstOrNull() }
    }

    private fun SemanticsNode.ancestors(): Sequence<SemanticsNode> = generateSequence(parent) { it.parent }

    private fun SemanticsNode.descendants(): List<SemanticsNode> = children.flatMap { listOf(it) + it.descendants() }

    private fun SemanticsNode.isButton(): Boolean = config.getOrNull(SemanticsProperties.Role).let { it == Role.Button || it == Role.Checkbox || it == Role.Tab }

    /**
     * The editor at the screen sizes of the team – 1366×768 at 150 % and Full HD at 125 % (maximised window without
     * taskbar): every text of the bars and every button label on one line without overflow, the bars' buttons inside
     * the bars, the preview at least its minimum. Pictures in build/video-layout (before: AP-70 report).
     */
    @Test
    fun editorFitsSmallScreensWithoutWrapping() {
        ffmpeg() ?: return
        val c = container()
        val p = VideoHub.store.save(project())
        for ((name, px, density) in listOf(Triple("1366x768-150", Size(1366f, 651f), 1.5f), Triple("1920x1080-125", Size(1920f, 970f), 1.25f))) {
            runSkikoComposeUiTest(px, Density(density)) {
                mainClock.autoAdvance = false
                val owner = lifecycle()
                setContent {
                    androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                        LlmHubTheme(AppSettings()) {
                            androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) { VideoScreen(c, onBack = {}) }
                        }
                    }
                }
                settle()
                onNodeWithText(p.title).performClick()
                settle(20)
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "editor-$name.png"))
                val problems = mutableListOf<String>()
                val bars = onAllNodes(SemanticsMatcher("Leiste") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("video.bar.") == true }, useUnmergedTree = true).fetchSemanticsNodes()
                assertTrue(bars.size >= 6, "${bars.size} Leisten")
                val root = onRoot(useUnmergedTree = true).fetchSemanticsNode()
                for (node in listOf(root) + root.descendants()) {
                    val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: continue
                    val inBar = node.ancestors().any { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("video.bar.") == true }
                    val inButton = node.isButton() || node.ancestors().any { it.isButton() }
                    if (!inBar && !inButton) continue
                    val l = node.layout() ?: continue
                    if (l.lineCount != 1) problems += "„$text“ in ${l.lineCount} Zeilen"
                    // Ellipsis is meant for names (title, file); a button label must fit.
                    if (inButton && (l.getLineRight(0) > l.size.width + 1f || l.isLineEllipsized(l.lineCount - 1))) problems += "„$text“ abgeschnitten (${l.getLineRight(0)} > ${l.size.width})"
                }
                for (bar in bars) {
                    bar.descendants().filter { it.config.contains(SemanticsActions.OnClick) }.forEach { b ->
                        if (b.boundsInRoot.right > bar.boundsInRoot.right + 1f || b.boundsInRoot.left < bar.boundsInRoot.left - 1f) {
                            problems += "${bar.config.getOrNull(SemanticsProperties.TestTag)}: Knopf ${b.config.getOrNull(SemanticsProperties.ContentDescription) ?: ""} ragt heraus"
                        }
                    }
                }
                val viewer = onNodeWithTag("video.bar.viewer", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue(viewer.width / density >= PREVIEW_MIN_WIDTH.value - 1f, "Vorschau nur ${viewer.width / density} dp breit")
                assertTrue(problems.isEmpty(), "$name:\n" + problems.distinct().joinToString("\n"))
            }
        }
        base.deleteRecursively()
    }
}
