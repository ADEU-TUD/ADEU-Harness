package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.docs.DocMarkup
import io.github.thepytales.llmhub.docs.text.PageLayout
import io.github.thepytales.llmhub.docs.text.PageLayout.Block
import io.github.thepytales.llmhub.docs.text.PageLayout.Break
import io.github.thepytales.llmhub.docs.text.PageLayout.Line
import io.github.thepytales.llmhub.docs.text.PageLayout.Space
import io.github.thepytales.llmhub.docs.text.RichText
import io.github.thepytales.llmhub.ui.PageBreakAt
import io.github.thepytales.llmhub.ui.PageSpacers
import io.github.thepytales.llmhub.ui.pageMargins
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.sp
import io.github.thepytales.llmhub.docs.DocumentWriter
import io.github.thepytales.llmhub.docs.MarkdownDocx
import io.github.thepytales.llmhub.docs.OfficeImport
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Seitenaufteilung des Text-Kerns (AP-43): Witwen/Waisen, Überschriften, Blöcke, Umbrüche, Kopf-/Fußzeilen. */
class PageLayoutTest {
    /** A paragraph [id] of [n] lines, each [h] high. */
    private fun para(id: Int, n: Int, h: Float = 10f, keep: Boolean = false) = (0 until n).map { Line(h, id, it, n, keep) }

    @Test
    fun linesFillPagesAndBreakWhereTheyNoLongerFit() {
        val items = para(0, 25)
        val plan = PageLayout.plan(items, pageHeight = 100f, between = 50f)
        assertEquals(3, plan.pages)
        assertEquals((0 until 25).map { it / 10 }, plan.page.toList())
        // The 11th line starts the second page: 100 px of text area plus 50 px between the pages.
        assertEquals(50f, plan.push[10], 0.01f)
        assertEquals(50f, plan.push[20], 0.01f)
        assertEquals(50f, plan.rest, 0.01f)
    }

    @Test
    fun orphansAndWidowsMoveLines() {
        // 9 lines of filler, then a paragraph of 4: one line would stay alone at the end of page 1 (Waise).
        val orphan = para(0, 9) + para(1, 4)
        val p1 = PageLayout.plan(orphan, 100f)
        assertEquals(1, p1.page[9], "Waise: der Absatz beginnt auf der nächsten Seite")
        // 7 lines of filler, then 4 lines: 3 would fit, 1 would go over alone (Witwe) – two go over instead.
        val widow = para(0, 7) + para(1, 4)
        val p2 = PageLayout.plan(widow, 100f)
        assertEquals(listOf(0, 0, 1, 1), p2.page.toList().takeLast(4), "Witwe: zwei Zeilen gehen mit")
        // A paragraph of three lines that does not fit with two at each end moves as a whole.
        val three = para(0, 8) + para(1, 3)
        assertEquals(listOf(1, 1, 1), PageLayout.plan(three, 100f).page.toList().takeLast(3))
    }

    @Test
    fun headingsStayWithTheirParagraph() {
        // Heading at the end of page 1, the paragraph after it would start on page 2.
        val items = para(0, 8) + listOf(Space(2f)) + para(1, 1, keep = true) + listOf(Space(2f)) + para(2, 5)
        val plan = PageLayout.plan(items, 100f)
        val heading = 9
        assertEquals(1, plan.page[heading], "Überschrift wandert mit")
        assertEquals(0f, plan.y[heading], 0.01f)
        assertTrue(plan.push[heading] > 0f)
        // Spaces never start a page.
        assertEquals(0, plan.page[8])
    }

    @Test
    fun blocksBreaksAndTallPieces() {
        // A table that does not fit goes to the next page as a whole.
        val items = para(0, 6) + listOf(Space(5f), Block(60f), Space(5f)) + para(1, 2)
        val plan = PageLayout.plan(items, 100f)
        assertEquals(1, plan.page[7])
        // A forced page break starts the next piece on a new page, wherever it is.
        val br = para(0, 2) + listOf(Space(5f), Break(4f), Space(5f)) + para(1, 2)
        val p2 = PageLayout.plan(br, 100f)
        assertEquals(1, p2.page[5])
        assertEquals(0f, p2.y[5], 0.01f)
        assertEquals(2, p2.pages)
        // A picture taller than a page starts a page and runs over; what follows goes on after it.
        val tall = para(0, 3) + listOf(Block(250f)) + para(1, 3)
        val p3 = PageLayout.plan(tall, 100f, between = 20f)
        assertEquals(1, p3.page[3])
        assertTrue(p3.page[4] >= 3, "nach dem hohen Bild")
    }

    @Test
    fun hundredPagesArePlannedQuickly() {
        // 100 pages of 45 lines in paragraphs of 6 with headings in between (AP-43 d: große Dokumente).
        val items = ArrayList<PageLayout.Item>()
        var id = 0
        repeat(750) { k ->
            if (k % 5 == 0) { items += para(id++, 1, 18f, keep = true); items += Space(6f) }
            items += para(id++, 6, 14f)
            items += Space(6f)
        }
        val t0 = System.nanoTime()
        val plan = PageLayout.plan(items, 1000f, 120f)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue(plan.pages in 60..120, "Seiten: ${plan.pages}")
        assertTrue(ms < 500, "Seitenplan in $ms ms")
        // No paragraph leaves a single line at a page end or start.
        val lines = items.indices.filter { items[it] is Line }
        for (i in lines) {
            val l = items[i] as Line
            if (l.lines < 2) continue
            val first = i - l.line
            val onPage = (first until first + l.lines).count { plan.page[it] == plan.page[i] }
            assertTrue(onPage >= 2, "Absatz ${l.para}: nur $onPage Zeile auf Seite ${plan.page[i]}")
        }
        // Large texts: finding the paragraph of an offset is a binary search.
        val big = RichText.ofMarkdown((1..4000).joinToString("\n\n") { "Absatz $it mit etwas Text." })
        val t1 = System.nanoTime()
        var sum = 0
        for (o in 0 until big.text.length step 7) sum += big.paraAt(o)
        assertTrue((System.nanoTime() - t1) / 1_000_000 < 300, "paraAt")
        assertEquals(3999, big.paraAt(big.text.length))
    }

    @Test
    fun pageSpacersShowRoomWithoutChangingTheText() {
        val text = "Erste Zeile und Silbentrennung\nZweiter Absatz"
        val src = AnnotatedString(
            text,
            spanStyles = listOf(AnnotatedString.Range(SpanStyle(fontWeight = FontWeight.Bold), 0, 5)),
            paragraphStyles = listOf(
                AnnotatedString.Range(ParagraphStyle(textIndent = TextIndent(20.sp, 5.sp)), 0, 31),
                AnnotatedString.Range(ParagraphStyle(), 31, text.length),
            ),
        )
        val breaks = listOf(PageBreakAt.at(text, 16, 40f), PageBreakAt.at(text, 21, 30f), PageBreakAt.at(text, 31, 50f))
        assertEquals("\n${PageBreakAt.LINE}\n", breaks[0].ins)
        assertEquals("-\n${PageBreakAt.HYPH}\n", breaks[1].ins, "mitten im Wort: mit Trennstrich")
        assertEquals("${PageBreakAt.PARA}\n", breaks[2].ins)
        val t = PageSpacers(breaks, 1f, 1f).filter(src)
        val shown = t.text.text
        assertEquals(text, shown.replace("\n${PageBreakAt.LINE}\n", "").replace("-\n${PageBreakAt.HYPH}\n", "").replace("${PageBreakAt.PARA}\n", ""))
        // Offsets map both ways; the cursor at a break stands after the room.
        for (o in 0..text.length) assertEquals(o, t.offsetMapping.transformedToOriginal(t.offsetMapping.originalToTransformed(o)))
        assertEquals(16 + 4, t.offsetMapping.originalToTransformed(16))
        // The continued paragraph has no first-line indent; formats stay at their letters.
        val cont = t.text.paragraphStyles.first { it.start == 16 + 4 }
        assertEquals(TextIndent(5.sp, 5.sp), cont.item.textIndent)
        assertEquals("Erste", shown.substring(t.text.spanStyles.first { it.item.fontWeight == FontWeight.Bold }.let { it.start until it.end }))
        // Paragraph ranges do not overlap (Compose requires that).
        val ps = t.text.paragraphStyles.sortedBy { it.start }
        for (k in 1 until ps.size) assertTrue(ps[k].start >= ps[k - 1].end, "Absätze überlappen: $ps")
    }

    @Test
    fun headersAndFootersPerPage() {
        val md = DocMarkup.withPageSetup(
            "# Arbeit",
            DocMarkup.PageSetup(
                header = "Hausarbeit", footer = "TU Dresden", titlePage = true, footerFirst = "Deckblatt",
                headerEven = "Gerade {seite}", footerEven = "{seite}/{seiten}",
            ),
        )
        val s = DocMarkup.pageSetup(md)
        assertTrue(s.titlePage && s.hasEven, md)
        assertEquals("Deckblatt", s.footerFirst)
        val first = pageMargins(s, 1, 5, "1.1.2027")
        assertEquals("", first.headerLeft)
        assertEquals("", first.footerRight, "keine Seitenzahl auf dem Deckblatt")
        assertEquals("Deckblatt", first.footerLeft)
        val second = pageMargins(s, 2, 5, "1.1.2027")
        assertEquals("Gerade 2", second.headerLeft)
        assertEquals("2/5", second.footerLeft)
        val third = pageMargins(s, 3, 5, "1.1.2027")
        assertEquals("Hausarbeit", third.headerLeft)
        assertTrue("3" in third.footerRight && "5" in third.footerRight)
        assertEquals(listOf(false to "S. ", true to "seite", false to " von ", true to "seiten"), DocMarkup.fieldPieces("S. {seite} von {seiten}"))
    }

    private val deckblatt = DocMarkup.PageSetup(
        header = "Hausarbeit", footer = "TU Dresden {seite}", titlePage = true, footerFirst = "Deckblatt",
        headerEven = "Gerade Seite", footerEven = "{seite}/{seiten}",
    )

    @Test
    fun wordKeepsFirstAndEvenPages() {
        val f = File.createTempFile("deckblatt", ".docx")
        MarkdownDocx.write(f, "Arbeit", DocMarkup.withPageSetup("# Arbeit\n\nText.\n", deckblatt))
        val files = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(f.readBytes())).use { z -> generateSequence { z.nextEntry }.forEach { files[it.name] = z.readBytes() } }
        f.delete()
        val doc = files["word/document.xml"]!!.toString(Charsets.UTF_8)
        assertTrue("<w:titlePg/>" in doc && "w:type=\"first\"" in doc && "w:type=\"even\"" in doc, doc)
        assertTrue("<w:evenAndOddHeaders/>" in files["word/settings.xml"]!!.toString(Charsets.UTF_8))
        assertTrue("Deckblatt" in files["word/footer2.xml"]!!.toString(Charsets.UTF_8))
        val even = files["word/footer3.xml"]!!.toString(Charsets.UTF_8)
        assertTrue(" PAGE " in even && " NUMPAGES " in even, even)
        assertTrue("Gerade Seite" in files["word/header3.xml"]!!.toString(Charsets.UTF_8))
        // As a document from Word (not from Lora) it comes back with the same page setup.
        files["docProps/core.xml"] = files["docProps/core.xml"]!!.toString(Charsets.UTF_8).replace("<dc:creator>Lora</dc:creator>", "<dc:creator>Word</dc:creator>").toByteArray()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> files.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        val s = DocMarkup.pageSetup(OfficeImport.docx(out.toByteArray()))
        assertTrue(s.titlePage, "Erste Seite anders")
        assertEquals("Deckblatt", s.footerFirst)
        assertEquals("Gerade Seite", s.headerEven)
        assertEquals("Hausarbeit", s.header)
    }

    @Test
    fun pdfHasFirstAndEvenPagesAndWidowControl() {
        val html = DocumentWriter.toHtml("Arbeit", DocMarkup.withPageSetup("# Arbeit\n\nText.\n", deckblatt), print = true)
        assertTrue("@page:first{" in html && "@page:left{" in html, html.take(3000))
        assertTrue("\"TU Dresden \" counter(page)" in html, "Feld in der Fußzeile")
        assertTrue("orphans:2;widows:2" in html)
    }
}
