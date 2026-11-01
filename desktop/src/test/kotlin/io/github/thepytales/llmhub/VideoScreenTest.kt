package io.github.thepytales.llmhub

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.ui.LlmHubTheme
import io.github.thepytales.llmhub.ui.VideoHub
import io.github.thepytales.llmhub.ui.VideoScreen
import io.github.thepytales.llmhub.video.Media
import io.github.thepytales.llmhub.video.SubtitleCue
import io.github.thepytales.llmhub.video.TextOverlay
import io.github.thepytales.llmhub.video.VideoClip
import io.github.thepytales.llmhub.video.VideoProject
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/** The video editor (AP-25) opens a project with clips, a lower third and subtitles (pictures in build/video-look). */
@OptIn(ExperimentalTestApi::class)
class VideoScreenTest {
    @Test
    fun editorShowsTimelinePreviewAndProperties() {
        val exe = Media.ffmpeg() ?: return
        val base = Files.createTempDirectory("adeu-video-ui").toFile()
        val src = File(base, "vorlesung.mp4")
        ProcessBuilder(exe, "-nostdin", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=25:duration=12",
            "-f", "lavfi", "-i", "sine=frequency=330:duration=12", "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", src.absolutePath)
            .redirectErrorStream(true).start().waitFor(60, TimeUnit.SECONDS)
        runSkikoComposeUiTest(Size(1500f, 950f), Density(1f)) {
            mainClock.autoAdvance = false
            val c = AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
            c.appContext.getSharedPreferences("ui_state", 0).edit().putBoolean("videoSetupSeen", true).apply()
            VideoHub.store.save(
                VideoProject(
                    title = "Vorlesung 3", width = 1280, height = 720,
                    clips = listOf(VideoClip(path = src.absolutePath, inSec = 0.0, outSec = 6.0), VideoClip(path = src.absolutePath, inSec = 7.0, outSec = 12.0, transition = "fade")),
                    overlays = listOf(TextOverlay(style = "bauchbinde", text = "Prof. Dr. Erika Muster", subtext = "Professur für Schulpädagogik", startSec = 0.0, durationSec = 5.0)),
                    subtitles = listOf(SubtitleCue(0.0, 3.0, "Herzlich willkommen zur dritten Sitzung.")),
                    burnSubtitles = true,
                ),
            )
            val owner = object : androidx.lifecycle.LifecycleOwner {
                val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
                override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
            }
            setContent {
                androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                    LlmHubTheme(AppSettings()) {
                        androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) { VideoScreen(c, onBack = {}) }
                    }
                }
            }
            val out = File("build/video-look").apply { mkdirs() }
            fun SkikoComposeUiTest.settle(rounds: Int = 10) {
                repeat(rounds) { Thread.sleep(200); mainClock.advanceTimeBy(300) }
                waitForIdle()
            }
            settle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "bildschirm-projekte.png"))
            onNodeWithText("Vorlesung 3").performClick()
            settle(20)
            assertTrue(onAllNodesWithText("Exportieren").fetchSemanticsNodes().isNotEmpty(), "Editor offen")
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "bildschirm-editor.png"))
        }
        base.deleteRecursively()
    }
}
