package io.github.thepytales.llmhub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.docs.DocScanner
import io.github.thepytales.llmhub.docs.MarkdownPdf
import io.github.thepytales.llmhub.docs.ScanImages
import io.github.thepytales.llmhub.docs.ScanMode
import io.github.thepytales.llmhub.docs.ScanPage
import io.github.thepytales.llmhub.docs.ScanPoint
import io.github.thepytales.llmhub.docs.ScanQuad
import io.github.thepytales.llmhub.docs.ScanRaster
import io.github.thepytales.llmhub.ui.DriveScreen
import io.github.thepytales.llmhub.ui.LlmHubTheme
import io.github.thepytales.llmhub.ui.ScanDraft
import io.github.thepytales.llmhub.ui.ScanScreen
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pictures of the last packages of Runde 2 (AP-62, AP-66) for the pull request and by eye: build/scan-screen.png (a
 * photographed page with its corners), build/files-details.png (Dateien with the Details-Spalte).
 */
@OptIn(ExperimentalTestApi::class)
class Round2EndLookTest {
    private val base = java.nio.file.Files.createTempDirectory("adeu-runde2-ende").toFile().also {
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(it, "daten").absolutePath)
    }
    private val c by lazy { AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() })) }

    private val owner = object : androidx.lifecycle.LifecycleOwner {
        val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
    }

    @androidx.compose.runtime.Composable
    private fun Themed(content: @androidx.compose.runtime.Composable () -> Unit) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
            LlmHubTheme(AppSettings()) { content() }
        }
    }

    private fun ui(width: Int, height: Int, block: SkikoComposeUiTest.() -> Unit) =
        runSkikoComposeUiTest(Size(width * 2f, height * 2f), Density(2f)) {
            mainClock.autoAdvance = false
            block()
        }

    private fun SkikoComposeUiTest.settle(ms: Long = 2500) {
        repeat(8) {
            Thread.sleep(250)
            mainClock.advanceTimeBy(ms / 8)
        }
        waitForIdle()
    }

    private fun SkikoComposeUiTest.save(name: String) {
        File("build").mkdirs()
        val img = onNodeWithTag("shot").captureToImage().toAwtImage()
        ImageIO.write(img, "png", File("build/$name"))
        assertTrue(img.width > 100 && img.height > 100)
    }

    /** A page photographed at a slant on a desk. */
    private fun photo(): File {
        val page = java.awt.image.BufferedImage(600, 848, java.awt.image.BufferedImage.TYPE_INT_RGB).apply {
            val g = createGraphics()
            g.color = java.awt.Color(246, 244, 238); g.fillRect(0, 0, width, height)
            g.color = java.awt.Color(30, 30, 35); g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 40)
            g.drawString("Rechnung", 60, 110)
            g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 20)
            for (i in 0 until 16) g.drawString("Position ${i + 1}  ·  Fachliteratur  ·  ${(i + 3) * 7},90 €", 60, 190 + i * 36)
            g.dispose()
        }
        val corners = listOf(330.0 to 60.0, 860.0 to 100.0, 920.0 to 860.0, 270.0 to 830.0)
        val m = DocScanner.homography(corners, listOf(0.0 to 0.0, 599.0 to 0.0, 599.0 to 847.0, 0.0 to 847.0))
        val out = ScanRaster(1200, 900)
        for (y in 0 until 900) for (x in 0 until 1200) {
            val z = m[6] * x + m[7] * y + 1
            val u = (m[0] * x + m[1] * y + m[2]) / z
            val v = (m[3] * x + m[4] * y + m[5]) / z
            out[x, y] = if (u >= 0 && v >= 0 && u < 600 && v < 848) page.getRGB(u.toInt(), v.toInt()) else ScanRaster.argb(96, 74, 56)
        }
        return File(base, "foto.jpg").apply { writeBytes(ScanImages.jpeg(out, 90)) }
    }

    @Test
    fun scanScreen() = ui(760, 820) {
        val f = photo()
        val quad = DocScanner.detect(ScanImages.read(f, 1000)) ?: ScanQuad.FULL
        ScanDraft.pages.clear()
        ScanDraft.pages += ScanPage(f, quad, ScanMode.GRAY, detected = quad != ScanQuad.FULL)
        ScanDraft.pages += ScanPage(f, quad, ScanMode.BW, detected = true)
        setContent {
            Themed {
                Box(Modifier.size(760.dp, 820.dp).background(MaterialTheme.colorScheme.background).testTag("shot")) { ScanScreen(c, onBack = {}, onNavigate = {}) }
            }
        }
        settle(4000)
        onNodeWithText("Ecken anpassen").performClick()
        settle(3000)
        save("scan-screen.png")
        ScanDraft.pages.clear()
        assertTrue(quad != ScanQuad.FULL && quad.area in 0.3f..0.8f, "Seite gefunden: $quad")
        assertTrue(quad.points.all { it.x in 0f..1f } && ScanPoint(0f, 0f) != quad.tl)
    }

    @Test
    fun filesWithDetails() = ui(1280, 760) {
        val created = c.drive.created.apply { mkdirs() }
        MarkdownPdf.write(File(created, "Ausschreibung Lehrinnovation.pdf"), "Ausschreibung", "# Ausschreibung\n\nFörderung bis 25000 Euro.\n\n## Fristen\n\nBis 30.11.2026 einreichen.")
        File(created, "Einladung Kolloquium.md").writeText("# Einladung\n\nLiebe Kolleginnen und Kollegen,\n\nherzliche Einladung zum Kolloquium am 12.11.2026.")
        setContent {
            Themed {
                Box(Modifier.size(1280.dp, 760.dp).background(MaterialTheme.colorScheme.background).testTag("shot")) {
                    DriveScreen(c, onBack = {}, onOpenDoc = {}, onOpenChat = {}, onNavigate = {})
                }
            }
        }
        settle()
        onAllNodesWithText("Erstellt").onFirst().performClick()
        settle()
        onNodeWithContentDescription("Details einblenden").performClick()
        settle()
        onNodeWithText("Ausschreibung Lehrinnovation.pdf").performMouseInput { moveTo(center) }
        settle(3000)
        save("files-details.png")
        // The Details-Spalte describes the file under the mouse.
        onNodeWithText("Seiten").assertExists()
        onNodeWithText("Dateien › Erstellt").assertExists()
        // Leertaste: the Schnellansicht of the file under the mouse.
        onAllNodesWithText("Ausschreibung Lehrinnovation.pdf").onFirst().performKeyInput { pressKey(Key.Spacebar) }
        settle(3000)
        onAllNodesWithText("Leertaste schließt", substring = true).onFirst().assertExists()
    }
}
