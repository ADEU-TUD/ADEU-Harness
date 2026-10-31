package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.docs.PdfBox
import io.github.thepytales.llmhub.docs.PdfFieldSpec
import io.github.thepytales.llmhub.docs.PdfFieldType
import io.github.thepytales.llmhub.docs.PdfFormDetect
import io.github.thepytales.llmhub.docs.PdfForms
import io.github.thepytales.llmhub.docs.PdfMark
import io.github.thepytales.llmhub.docs.PdfMarkKind
import io.github.thepytales.llmhub.docs.PdfNumbering
import io.github.thepytales.llmhub.docs.PdfPoint
import io.github.thepytales.llmhub.docs.PdfTextRun
import io.github.thepytales.llmhub.docs.PdfTools
import io.github.thepytales.llmhub.docs.parsePageRanges
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** PDF-Werkzeugkasten Stufe 1 (AP-11). */
class PdfToolboxTest {
    private val dir = Files.createTempDirectory("adeu-pdf").toFile()

    /** A PDF with [pages] pages, each with „Seite n“ and, on the first, a paper form. */
    private fun pdf(name: String, pages: Int, form: Boolean = false, outline: Boolean = false): File {
        val f = File(dir, name)
        PDDocument().use { doc ->
            val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
            repeat(pages) { i ->
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    fun text(x: Float, y: Float, s: String) {
                        cs.beginText(); cs.setFont(font, 11f); cs.newLineAtOffset(x, y); cs.showText(s); cs.endText()
                    }
                    text(72f, 780f, "$name Seite ${i + 1}")
                    if (form && i == 0) {
                        text(72f, 700f, "Name: ______________________________")
                        text(72f, 670f, "Matrikelnummer:")
                        text(72f, 640f, "[ ] Ich stimme zu")
                        text(72f, 600f, "Datum: ____________")
                    }
                }
            }
            if (outline) {
                val o = PDDocumentOutline()
                doc.documentCatalog.documentOutline = o
                listOf("Einleitung" to 0, "Methode" to 1).forEach { (t, p) ->
                    val item = PDOutlineItem()
                    item.title = t
                    item.destination = PDPageFitDestination().apply { page = doc.getPage(p) }
                    o.addLast(item)
                }
            }
            doc.save(f)
        }
        return f
    }

    private fun pageTexts(f: File): List<String> = Loader.loadPDF(f).use { doc ->
        (1..doc.numberOfPages).map { p -> PDFTextStripper().apply { startPage = p; endPage = p }.getText(doc).trim() }
    }

    private fun out(name: String) = File(dir, name)

    @Test
    fun mergeSortDeleteRotateExtractSplit() {
        val a = pdf("A.pdf", 2)
        val b = pdf("B.pdf", 3)
        PdfTools.merge(null, listOf(a, b), out("AB.pdf"))
        assertEquals(5, PdfTools.info(null, out("AB.pdf")).pages)
        // Sort: last page first, delete page 2, rotate the new first page.
        PdfTools.rearrange(null, out("AB.pdf"), listOf(4, 0, 2, 3), mapOf(4 to 90), out("sorted.pdf"))
        val info = PdfTools.info(null, out("sorted.pdf"))
        assertEquals(4, info.pages)
        assertEquals(90, info.rotations[0])
        val texts = pageTexts(out("sorted.pdf")).map { it.replace(Regex("\\s+"), " ") }
        assertTrue(texts[0].replace(" ", "").contains("B.pdfSeite3") && texts[1].contains("A.pdf Seite 1") && texts[2].contains("B.pdf Seite 1"), texts.toString())
        // Rotated page: width and height swap.
        assertTrue(info.sizes[0].first > info.sizes[0].second)
        PdfTools.extract(null, out("AB.pdf"), listOf(1, 3), out("x.pdf"))
        assertEquals(listOf("A.pdf Seite 2", "B.pdf Seite 2"), pageTexts(out("x.pdf")))
        val parts = PdfTools.split(null, out("AB.pdf"), listOf(listOf(0, 1), listOf(2, 3, 4)), File(dir, "teile"), "AB")
        assertEquals(listOf(2, 3), parts.map { PdfTools.info(null, it).pages })
        assertEquals(listOf(0, 1, 2, 4, 5), parsePageRanges("1-3, 5, 6-", 6))
    }

    @Test
    fun pageNumbersSearchAndOutline() {
        val f = pdf("Buch.pdf", 3, outline = true)
        PdfTools.addPageNumbers(null, f, PdfNumbering("Seite {n} von {N}"), out("nummern.pdf"))
        val texts = pageTexts(out("nummern.pdf"))
        assertTrue(texts[2].contains("Seite 3 von 3"), texts[2])
        val hits = PdfTools.search(null, f, "seite 2")
        assertEquals(listOf(2), hits.map { it.page })
        val outline = PdfTools.outline(null, f)
        assertEquals(listOf("Einleitung" to 1, "Methode" to 2), outline.map { it.title to it.page })
    }

    @Test
    fun marksBecomeAnnotationsWithAnExportableList() {
        val f = pdf("Text.pdf", 2)
        val marks = listOf(
            PdfMark(0, PdfMarkKind.HIGHLIGHT, listOf(PdfBox(0.1f, 0.05f, 0.4f, 0.02f)), text = "wichtig"),
            PdfMark(0, PdfMarkKind.UNDERLINE, listOf(PdfBox(0.1f, 0.1f, 0.3f, 0.02f))),
            PdfMark(0, PdfMarkKind.STRIKE, listOf(PdfBox(0.1f, 0.15f, 0.3f, 0.02f))),
            PdfMark(1, PdfMarkKind.INK, strokes = listOf(listOf(PdfPoint(0.2f, 0.2f), PdfPoint(0.3f, 0.25f), PdfPoint(0.4f, 0.2f)))),
            PdfMark(1, PdfMarkKind.NOTE, listOf(PdfBox(0.5f, 0.5f, 0.02f, 0.02f)), text = "Quelle prüfen"),
            PdfMark(1, PdfMarkKind.STAMP, listOf(PdfBox(0.6f, 0.1f, 0.1f, 0.03f)), text = "GEPRÜFT"),
        )
        PdfTools.annotate(null, f, marks, out("markiert.pdf"))
        val comments = PdfTools.comments(null, out("markiert.pdf"))
        assertEquals(6, comments.size)
        assertTrue(comments.any { it.kind == "Notiz" && it.text == "Quelle prüfen" && it.page == 2 })
        assertTrue(comments.any { it.kind == "Hervorhebung" && it.text == "wichtig" })
        val md = PdfTools.commentsMarkdown("Text.pdf", comments)
        assertTrue(md.contains("## Seite 2") && md.contains("Stempel"))
        // Every annotation has its own appearance, so every viewer shows it.
        Loader.loadPDF(out("markiert.pdf")).use { doc -> assertTrue(doc.pages.flatMap { it.annotations }.all { it.appearance?.normalAppearance != null }) }
    }

    @Test
    fun aPaperFormBecomesFillable() {
        val f = pdf("Antrag.pdf", 1, form = true)
        val found = PdfTools.detectFields(null, f)
        val names = found.map { it.name }
        assertTrue("Name" in names, names.toString())
        assertTrue("Matrikelnummer" in names, names.toString())
        assertTrue(found.any { it.type == PdfFieldType.CHECKBOX && it.label.contains("stimme") }, found.toString())
        assertTrue(found.any { it.name == "Datum" && it.type == PdfFieldType.DATE }, found.toString())
        // The user moves one field a little and adds another; then the form is written.
        val edited = found.map { if (it.name == "Name") it.copy(box = it.box.copy(x = it.box.x + 0.01f)) else it } +
            PdfFieldSpec("Unterschrift", 0, PdfBox(0.1f, 0.8f, 0.4f, 0.04f), PdfFieldType.SIGNATURE)
        PdfTools.setFormFields(null, f, edited, out("ausfuellbar.pdf"))
        val again = PdfTools.formFields(null, out("ausfuellbar.pdf"))
        assertEquals(edited.size, again.size)
        val name = again.first { it.name == "Name" }
        assertEquals(edited.first { it.name == "Name" }.box.x, name.box.x, 0.002f)
        // And it can be filled in afterwards (like on the PC).
        val ctx = android.content.Context(dir, dir)
        val report = PdfForms.fill(ctx, out("ausfuellbar.pdf"), out("ausgefuellt.pdf"), mapOf("Name" to "Lea Muster", "Ich stimme zu" to "ja"), emptyList(), false)
        assertTrue("Name" in report.filled.joinToString(), report.toString())
        val info = PdfForms.inspect(ctx, out("ausgefuellt.pdf"))
        assertEquals("Lea Muster", info.fields.first { it.name == "Name" }.value)
    }

    @Test
    fun detectionOnTextRuns() {
        val runs = listOf(
            PdfTextRun(0, 72f, 100f, 300f, 11f, "Vorname: ______________"),
            PdfTextRun(0, 72f, 130f, 80f, 11f, "Begründung:"),
            PdfTextRun(0, 72f, 160f, 120f, 11f, "☐ ja  ☐ nein"),
        )
        val fields = PdfFormDetect.detect(runs, 595f, 842f)
        assertTrue(fields.any { it.name == "Vorname" && it.type == PdfFieldType.TEXT })
        assertTrue(fields.any { it.name == "Begründung" && it.type == PdfFieldType.MULTILINE })
        assertEquals(2, fields.count { it.type == PdfFieldType.CHECKBOX })
        assertTrue(fields.all { it.box.x in 0f..1f && it.box.right <= 1.001f })
    }
}
