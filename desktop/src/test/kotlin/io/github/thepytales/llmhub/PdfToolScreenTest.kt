package io.github.thepytales.llmhub

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toAwtImage
import io.github.thepytales.llmhub.ui.PdfToolScreen
import io.github.thepytales.llmhub.ui.PdfWork
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/** The PDF-Werkzeugkasten (AP-11) opens a PDF and shows every mode without failing. */
@OptIn(ExperimentalTestApi::class)
class PdfToolScreenTest {
    @Test
    fun opensAPdfInEveryMode() = runComposeUiTest {
        val base = Files.createTempDirectory("adeu-pdfui").toFile()
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(base, "daten").absolutePath)
        val ctx = android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() })
        val c = AppContainer(ctx)
        val pdf = File(base, "Formular.pdf")
        PDDocument().use { doc ->
            repeat(2) { i ->
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText(); cs.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f); cs.newLineAtOffset(72f, 760f)
                    cs.showText(if (i == 0) "Name: ____________________" else "Seite 2"); cs.endText()
                }
            }
            doc.save(pdf)
        }
        runBlocking { PdfWork.open(ctx, pdf) }
        val owner = object : androidx.lifecycle.LifecycleOwner {
            val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
            override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
        }
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) { PdfToolScreen(c, onBack = {}) }
        }
        waitForIdle()
        onAllNodesWithText("Formular.pdf").fetchSemanticsNodes().isNotEmpty().let { assertTrue(it, "Tab") }
        val out = System.getenv("ADEU_SCREENS")?.let { File(it).apply { mkdirs() } }
        fun shot(name: String) {
            waitForIdle()
            Thread.sleep(400)
            waitForIdle()
            out?.let { ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(it, "$name.png")) }
        }
        shot("pdf-ansehen")
        for (mode in listOf("Seiten", "Markieren", "Formular")) {
            onNodeWithText(mode).performClick()
            shot("pdf-" + mode.lowercase())
        }
        onNodeWithText("Felder erkennen").performClick()
        Thread.sleep(800)
        shot("pdf-formular-erkannt")
        PdfWork.tabs.toList().forEach { PdfWork.close(it) }
        base.deleteRecursively()
    }
}
