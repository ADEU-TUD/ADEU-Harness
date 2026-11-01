package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.docs.PdfCompare
import io.github.thepytales.llmhub.docs.PdfMeta
import io.github.thepytales.llmhub.docs.PdfStage3
import io.github.thepytales.llmhub.docs.PdfUaFinding
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.text.PDFTextStripper
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** PDF-Werkzeugkasten Stufe 3 (AP-26). */
class PdfStage3Test {
    private val dir: File = Files.createTempDirectory("lora-pdf3").toFile()
    private val context = Context(File(dir, "files").apply { mkdirs() }, File(dir, "cache").apply { mkdirs() })

    /** Two pages of text (one with a picture), title and author set. */
    private fun sample(image: Boolean = true): File = File(dir, "probe-${System.nanoTime()}.pdf").also { f ->
        PDDocument().use { doc ->
            val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
            listOf(
                listOf("Gutachten zur Arbeit von Max Mustermann", "Matrikelnummer 4711123, Dresden", "Die Arbeit ist gut gegliedert."),
                listOf("Seite zwei: Max Mustermann hat sorgfältig zitiert.", "Note: 1,7"),
            ).forEachIndexed { i, lines ->
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    lines.forEachIndexed { n, line ->
                        cs.beginText(); cs.setFont(font, 14f); cs.newLineAtOffset(72f, 760f - n * 24f); cs.showText(line); cs.endText()
                    }
                    if (image && i == 1) {
                        val img = BufferedImage(2400, 1600, BufferedImage.TYPE_INT_RGB)
                        val r = Random(1)
                        for (y in 0 until 1600 step 4) for (x in 0 until 2400 step 4) { val c = r.nextInt(0xFFFFFF); for (dy in 0..3) for (dx in 0..3) img.setRGB(x + dx, y + dy, c) }
                        cs.drawImage(LosslessFactory.createFromImage(doc, img), 72f, 300f, 300f, 200f)
                    }
                }
            }
            doc.documentInformation.title = "Gutachten Mustermann"
            doc.documentInformation.author = "Prof. Weber"
            doc.save(f)
        }
    }

    private fun text(f: File, page: Int? = null, password: String = "") = Loader.loadPDF(f, password).use { d ->
        PDFTextStripper().apply { if (page != null) { startPage = page; endPage = page } }.getText(d)
    }

    private fun out() = File(dir, "out-${System.nanoTime()}.pdf")

    @Test
    fun redactionRemovesTheTextForReal() {
        val f = sample()
        val hits = PdfStage3.find(f, listOf("Max Mustermann", "4711123"))
        assertEquals(3, hits.size, hits.toString())
        assertTrue(hits.all { it.box.x in 0f..1f && it.box.y in 0f..1f && it.box.w > 0 })
        assertEquals(setOf(0, 1), hits.map { it.page }.toSet())
        // „Muster“ as whole word: no hit inside „Mustermann“.
        assertTrue(PdfStage3.find(f, listOf("Muster")).isEmpty())
        val o = out()
        PdfStage3.redact(f, hits, o)
        val t = text(o)
        assertFalse("Mustermann" in t || "4711123" in t, t)
        Loader.loadPDF(o).use { d ->
            assertEquals(2, d.numberOfPages)
            assertEquals(null, d.documentInformation.title, "Metadaten entfernt")
        }
    }

    @Test
    fun replaceImagesShrinkAndArchive() {
        val f = sample()
        val o = out()
        assertEquals(1, PdfStage3.replaceText(f, "1,7", "2,0", null, o))
        assertTrue("2,0" in text(o, 2))

        val images = PdfStage3.images(f)
        assertEquals(1, images.size)
        assertEquals(2400, images.single().width)
        assertTrue(PdfStage3.imagePreview(f, images.single().page, images.single().name)!!.isNotEmpty())
        val small = out()
        val (before, after) = PdfStage3.shrink(f, small, maxPx = 1000, quality = 0.6f)
        assertTrue(after < before / 2, "$before → $after")
        assertEquals(1000, PdfStage3.images(small).single().width)
        val removed = out()
        PdfStage3.replaceImage(f, 1, images.single().name, null, removed)
        assertEquals(1, PdfStage3.images(removed).single().width)

        val a = out()
        val problems = PdfStage3.toPdfA(f, "Gutachten", a)
        assertTrue(PdfStage3.isPdfA(a))
        assertTrue(problems.any { "Nicht eingebettete Schriften" in it }, problems.toString())
        Loader.loadPDF(a).use { assertEquals("de-DE", it.documentCatalog.language) }
    }

    @Test
    fun passwordMetadataAndPdfUa() {
        val f = sample(image = false)
        val locked = out()
        PdfStage3.protect(f, "geheim", "chef", print = true, copy = false, out = locked)
        assertTrue(runCatching { Loader.loadPDF(locked).close() }.isFailure, "ohne Passwort nicht zu öffnen")
        assertTrue("Mustermann" in text(locked, password = "geheim"))
        val open = out()
        PdfStage3.unprotect(locked, "geheim", open)
        assertFalse(PdfStage3.encrypted(open))
        assertTrue(runCatching { PdfStage3.unprotect(locked, "falsch", out()) }.exceptionOrNull()!!.message!!.contains("Passwort"))

        assertEquals("Prof. Weber", PdfStage3.meta(f).author)
        val m = out()
        PdfStage3.setMeta(f, PdfMeta(title = "Neu", author = "", language = "de-DE"), clean = true, out = m)
        PdfStage3.meta(m).let { assertEquals("Neu", it.title); assertEquals("", it.author); assertEquals("de-DE", it.language) }

        val findings = PdfStage3.uaCheck(context, f)
        assertTrue(findings.any { it.level == PdfUaFinding.Level.ERROR && "Keine Tags" in it.text })
        assertTrue(findings.any { it.fixable && "Sprache" in it.text })
        val fixed = out()
        PdfStage3.uaFix(context, f, "Gutachten", fixed)
        val after = PdfStage3.uaCheck(context, fixed)
        assertTrue(after.none { it.fixable }, after.toString())
        assertTrue(after.any { it.level == PdfUaFinding.Level.OK && "Titel" in it.text })
    }

    @Test
    fun comparingFindsChangedWords() {
        val r = PdfCompare.compare(
            listOf("Die Arbeit ist gut gegliedert.", "Note: 1,7"),
            listOf("Die Arbeit ist sehr gut gegliedert.", "Note: 2,0", "Anhang"),
        )
        assertFalse(r.same)
        assertEquals(listOf(0, 1, 2), r.changedPages)
        assertEquals(PdfCompare.Change(0, "", "sehr"), r.changes.first())
        assertEquals(PdfCompare.Change(1, "1,7", "2,0"), r.changes[1])
        val md = PdfCompare.markdown("alt.pdf", "neu.pdf", r)
        assertTrue("## Seite 2" in md && "~~1,7~~ → **2,0**" in md && "Die Seitenzahl unterscheidet sich." in md)
        assertTrue(PdfCompare.compare(listOf("a b"), listOf("a  b")).same)
    }
}
