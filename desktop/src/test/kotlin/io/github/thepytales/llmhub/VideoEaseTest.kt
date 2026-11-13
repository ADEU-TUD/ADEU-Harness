package io.github.thepytales.llmhub

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.ui.GUIDE_STEPS
import io.github.thepytales.llmhub.ui.LlmHubTheme
import io.github.thepytales.llmhub.ui.Sel
import io.github.thepytales.llmhub.ui.VIDEO_SHORTCUTS
import io.github.thepytales.llmhub.ui.VideoHub
import io.github.thepytales.llmhub.ui.VideoScreen
import io.github.thepytales.llmhub.ui.editHint
import io.github.thepytales.llmhub.ui.CutTool
import io.github.thepytales.llmhub.ui.parseSeconds
import io.github.thepytales.llmhub.video.AudioClip
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Der Videoeditor für Einsteiger (AP-121): shortening up to the playhead, cutting out the marked piece, a copy of a
 * project, times as minutes and seconds; on screen the start cards, „So entsteht dein Video“, labelled buttons and the
 * right-click menu. Pictures in build/video-ease.
 */
@OptIn(ExperimentalTestApi::class)
class VideoEaseTest {
    private val base = Files.createTempDirectory("adeu-video-ease").toFile()
    private val out = File("build/video-ease").apply { mkdirs() }

    private fun clip(inSec: Double, outSec: Double, path: String = "/x/vortrag.mp4") = VideoClip(path = path, inSec = inSec, outSec = outSec)

    /** „Anfang bis hier“ and „Ende ab hier“ shorten only the clip under the playhead; the rest moves along with the magnet. */
    @Test
    fun trimToPlayhead() {
        val title = TextOverlay(style = "einblendung", text = "Danach", startSec = 15.0, durationSec = 2.0)
        val p = VideoProject(title = "T", clips = listOf(clip(0.0, 10.0), clip(20.0, 30.0)), overlays = listOf(title))
        // Start of clip 1 up to 4 s: the clip shows 4–10, everything after moves 4 s forward.
        val a = assertNotNull(VideoEdit.trimToPlayhead(p, 4.0, start = true))
        assertEquals(4.0, a.clips[0].inSec, 1e-9)
        assertEquals(10.0, a.clips[0].outSec, 1e-9)
        assertEquals(16.0, a.duration, 1e-9)
        assertEquals(11.0, a.overlays.single().startSec, 1e-9, "Titel rückt mit")
        // End of clip 2 from 13 s on (3 s into it).
        val b = assertNotNull(VideoEdit.trimToPlayhead(p, 13.0, start = false))
        assertEquals(23.0, b.clips[1].outSec, 1e-9)
        assertEquals(13.0, b.duration, 1e-9)
        // At the edge or almost nothing left: nothing happens.
        assertNull(VideoEdit.trimToPlayhead(p, 0.0, start = true))
        assertNull(VideoEdit.trimToPlayhead(p, 9.95, start = true))
        assertNull(VideoEdit.trimToPlayhead(VideoProject(title = "leer"), 1.0, start = true))
        // A locked V1 stays.
        assertNull(VideoEdit.trimToPlayhead(VideoEdit.setTrack(p, "V1") { it.copy(locked = true) }, 4.0, start = true))
        // Faster clips: timeline seconds turn into source seconds.
        val fast = VideoProject(title = "F", clips = listOf(clip(0.0, 20.0).copy(fx = io.github.thepytales.llmhub.video.ClipFx(speed = 2.0))))
        val f = assertNotNull(VideoEdit.trimToPlayhead(fast, 5.0, start = false))
        assertEquals(10.0, f.clips.single().outSec, 1e-9)
    }

    /** The marked piece goes out of the middle: the clip is split, linked sound goes along, titles after it move – also without the magnet. */
    @Test
    fun cutRangeOutOfTheMiddle() {
        val c = clip(0.0, 30.0)
        val sound = AudioClip(path = "/x/mikro.wav", startSec = 0.0, outSec = 30.0, track = 1, linkedTo = c.id)
        val after = TextOverlay(style = "einblendung", text = "Später", startSec = 20.0, durationSec = 3.0)
        val before = TextOverlay(style = "bauchbinde", text = "Name", startSec = 1.0, durationSec = 3.0)
        val p = VideoProject(
            title = "R", clips = listOf(c), audio = listOf(sound), overlays = listOf(before, after),
            markers = listOf(Marker(sec = 25.0, note = "Fragen")), subtitles = listOf(SubtitleCue(26.0, 28.0, "Fragen?")),
            magnet = false, markIn = 10.0, markOut = 15.0,
        )
        val q = VideoEdit.cutRange(p, 10.0, 15.0)
        assertEquals(25.0, q.duration, 1e-6)
        assertEquals(2, q.clips.size)
        assertEquals(10.0, q.clips[0].outSec, 1e-6)
        assertEquals(15.0, q.clips[1].inSec, 1e-6)
        assertEquals(2, q.audio.size, "verknüpfter Ton auf beiden Teilen")
        assertTrue(q.audio.all { it.linkedTo != null })
        assertEquals(15.0, q.overlays.first { it.text == "Später" }.startSec, 1e-6, "Titel danach rückt nach")
        assertEquals(1.0, q.overlays.first { it.text == "Name" }.startSec, 1e-6, "Titel davor bleibt")
        assertEquals(20.0, q.markers.single().sec, 1e-6)
        assertEquals(21.0, q.subtitles.single().startSec, 1e-6)
        assertTrue(!q.magnet, "Magnet bleibt aus")
        // Over two clips: both shortened, together 4 s shorter.
        val two = VideoProject(title = "Z", clips = listOf(clip(0.0, 10.0), clip(0.0, 10.0, "/x/b.mp4")))
        val r = VideoEdit.cutRange(two, 8.0, 12.0)
        assertEquals(16.0, r.duration, 1e-6)
        assertEquals(8.0, r.clips[0].outSec, 1e-6)
        assertEquals(2.0, r.clips[1].inSec, 1e-6)
        // Nothing marked or a locked V1: unchanged.
        assertEquals(two, VideoEdit.cutRange(two, 3.0, 3.01))
        val locked = VideoEdit.setTrack(two, "V1") { it.copy(locked = true) }
        assertEquals(locked, VideoEdit.cutRange(locked, 2.0, 5.0))
    }

    @Test
    fun duplicateAndSeconds() {
        val p = VideoProject(title = "Vorlesung", clips = listOf(clip(0.0, 5.0)))
        val d = VideoEdit.duplicate(p)
        assertNotEquals(p.id, d.id)
        assertEquals("Vorlesung (Kopie)", d.title)
        assertEquals(p.clips, d.clips)
        assertEquals(83.5, parseSeconds("1:23,5")!!, 1e-9)
        assertEquals(83.5, parseSeconds("83.5")!!, 1e-9)
        assertEquals(3723.0, parseSeconds("1:02:03")!!, 1e-9)
        assertNull(parseSeconds("1:x"))
        assertNull(parseSeconds(""))
        // The hint says what can be done with what is selected.
        assertTrue("Rechtsklick" in editHint(p, Sel.Clip(p.clips[0].id), CutTool.SELECT))
        assertTrue("Entf" in editHint(p.copy(markIn = 1.0, markOut = 2.0), Sel.None, CutTool.SELECT))
        // The steps: cut counts once there is a second clip or a shortened start, text once there is a title.
        assertTrue(GUIDE_STEPS.first { it.key == "cut" }.done(p.copy(clips = p.clips + clip(0.0, 3.0))))
        assertTrue(!GUIDE_STEPS.first { it.key == "cut" }.done(p))
        assertTrue(GUIDE_STEPS.first { it.key == "text" }.done(p.copy(overlays = listOf(TextOverlay(text = "x", startSec = 0.0)))))
        // The new keys are in the overview.
        assertTrue(VIDEO_SHORTCUTS.any { it.first == "Umschalt+Z" } && VIDEO_SHORTCUTS.any { it.first == "Rechtsklick" })
    }

    private fun ffmpeg(): String? = Media.ffmpeg()?.takeIf { runCatching { ProcessBuilder(it, "-version").start().waitFor(10, TimeUnit.SECONDS) }.getOrNull() == true }

    private fun source(name: String, sec: Int): File {
        val f = File(base, name)
        ProcessBuilder(
            ffmpeg(), "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
            "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=25:duration=$sec",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=$sec:sample_rate=48000",
            "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", f.absolutePath,
        ).redirectErrorStream(true).start().waitFor(60, TimeUnit.SECONDS)
        return f
    }

    private fun SkikoComposeUiTest.settle(rounds: Int = 10) {
        repeat(rounds) { Thread.sleep(150); mainClock.advanceTimeBy(300) }
        waitForIdle()
    }

    private fun lifecycle() = object : androidx.lifecycle.LifecycleOwner {
        val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
    }

    /**
     * The start page with its two cards and the editor with „So entsteht dein Video“ and labelled buttons, at Full HD
     * 125 % and 1366 × 768 150 %; then a right-click on the clip and „Hier teilen“.
     */
    @Test
    fun startPageGuideAndRightClick() {
        ffmpeg() ?: return
        val a = source("vortrag.mp4", 12)
        val c = AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
        c.appContext.getSharedPreferences("ui_state", 0).edit().putBoolean("videoSetupSeen", true).apply()
        val p = VideoHub.store.save(VideoEdit.withPool(VideoProject(title = "Antrittsvorlesung Physik", clips = listOf(VideoClip(path = a.absolutePath, inSec = 0.0, outSec = 12.0)))))
        for ((name, px, density) in listOf(Triple("1920x1080-125", Size(1920f, 970f), 1.25f), Triple("1366x768-150", Size(1366f, 651f), 1.5f))) {
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
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "start-$name.png"))
                assertTrue(onAllNodesWithText("Video schneiden").fetchSemanticsNodes().isNotEmpty(), "Startkarte")
                assertTrue(onAllNodesWithText("Bildschirm aufnehmen").fetchSemanticsNodes().isNotEmpty(), "Startkarte Aufnahme")
                onNodeWithText(p.title).performClick()
                settle(20)
                onNodeWithTag("video.guide", useUnmergedTree = true).fetchSemanticsNode()
                assertTrue(onAllNodesWithText("So entsteht dein Video").fetchSemanticsNodes().isNotEmpty())
                assertTrue(onAllNodesWithText("Gespeichert").fetchSemanticsNodes().isNotEmpty() || density > 1.4f, "Gespeichert im Kopf")
                if (density < 1.4f) assertTrue(onAllNodesWithText("Teilen").fetchSemanticsNodes().isNotEmpty(), "Knöpfe mit Wort")
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "editor-$name.png"))
            }
        }
        // Right-click on the clip at about 6 s, then „Hier teilen“: two clips, cut there.
        runSkikoComposeUiTest(Size(1500f, 950f), Density(1f)) {
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
            settle(16)
            val zoom = SemanticsMatcher("Zoom-Regler") { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == 1f..24f }
            val rowBottom = onAllNodes(zoom)[0].fetchSemanticsNode().boundsInRoot.bottom
            val width = onRoot().fetchSemanticsNode().boundsInRoot.width
            val x6 = 118f + (width - 118f - 20f) / 2f
            val v1 = rowBottom + 22f + 29f + 28f
            onAllNodes(isRoot())[0].performMouseInput { rightClick(Offset(x6, v1)) }
            settle(4)
            assertTrue(onAllNodesWithText("Hier teilen").fetchSemanticsNodes().isNotEmpty(), "Menü offen")
            ImageIO.write(onAllNodes(isRoot())[0].captureToImage().toAwtImage(), "png", File(out, "rechtsklick.png"))
            onNodeWithText("Hier teilen").performClick()
            settle(6)
            val now = VideoHub.store.get(p.id)!!
            assertEquals(2, now.clips.size, "geteilt")
            assertEquals(6.0, now.clips[0].outSec, 0.3)
            // Help menu: the keys.
            onNodeWithContentDescription("Hilfe").performClick()
            settle(2)
            assertTrue(onAllNodesWithText("Tour durch den Videoeditor").fetchSemanticsNodes().isNotEmpty())
        }
        base.deleteRecursively()
    }
}
