package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.docs.DocMarkup
import io.github.thepytales.llmhub.docs.DocumentWriter
import io.github.thepytales.llmhub.docs.MarkdownDocx
import io.github.thepytales.llmhub.docs.OfficeImport
import io.github.thepytales.llmhub.docs.text.PageLayout
import io.github.thepytales.llmhub.docs.text.ParaFormat
import io.github.thepytales.llmhub.docs.text.RichText
import io.github.thepytales.llmhub.docs.text.TabKind
import io.github.thepytales.llmhub.docs.text.TabStop
import io.github.thepytales.llmhub.ui.pageMargins
import io.github.thepytales.llmhub.ui.sectionOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import io.github.thepytales.llmhub.docs.text.PageLayout.Line
import io.github.thepytales.llmhub.docs.text.PageLayout.Row
import io.github.thepytales.llmhub.docs.text.PageLayout.Space
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Reste von AP-43 bis AP-45: Tabellen über Seiten, Abschnitte, Tabstopps, große Dokumente, Prüfsammlung. */
class EditorRestTest {
    private fun para(id: Int, n: Int, h: Float = 10f) = (0 until n).map { Line(h, id, it, n) }
    private fun table(id: Int, n: Int, h: Float = 12f, header: Float = h) = (0 until n).map { Row(h, id, it, n, header) }

    @Test
    fun tablesBreakBetweenRowsAndRepeatTheirHeader() {
        // 5 lines of text, then a table of 12 rows of 12 px: it does not fit on page 1 as a whole.
        val items = para(0, 5) + listOf(Space(4f)) + table(0, 12)
        val plan = PageLayout.plan(items, pageHeight = 100f, between = 30f)
        val rows = items.indices.filter { items[it] is Row }
        // The table starts on page 1 (54 px used, 46 px left: header + two rows) and goes on row by row.
        assertEquals(0, plan.page[rows[0]], "Tabelle beginnt auf Seite 1")
        val firstOnNext = rows.first { plan.page[it] == 1 }
        assertTrue(firstOnNext > rows[1], "mindestens eine Zeile bleibt bei der Kopfzeile")
        // On page 2 the header row is repeated: the first row there starts below it.
        assertTrue(plan.repeat[firstOnNext], "Kopfzeile wiederholt")
        assertEquals(12f, plan.y[firstOnNext], 0.01f)
        // Rows 0–2 on page 1, header + 7 rows on page 2, header + the last 2 on page 3: repeated once per further page.
        assertEquals(3, plan.pages)
        assertEquals(plan.pages - 1, rows.count { plan.repeat[it] }, "nur am Seitenanfang")
        // Every row lies inside its page's text area.
        for (r in rows) assertTrue(plan.y[r] + 12f <= 100.5f, "Zeile ${(items[r] as Row).row} über dem Seitenende")
        // The room before the row (push) holds the repeated header at its end.
        assertTrue(plan.push[firstOnNext] >= 30f + 12f, "push ${plan.push[firstOnNext]}")
    }

    @Test
    fun headerRowNeverStaysAloneAtThePageEnd() {
        // 8 lines of text (80 px): only the header row would fit – the whole table moves.
        val items = para(0, 8) + listOf(Space(4f)) + table(0, 4)
        val plan = PageLayout.plan(items, 100f)
        val header = items.indexOfFirst { it is Row }
        assertEquals(1, plan.page[header], "Kopfzeile geht mit der ersten Zeile")
        assertFalse(plan.repeat.any { it }, "auf der neuen Seite steht die echte Kopfzeile")
        // A table without a header row (header = 0) never repeats anything.
        val plain = para(0, 5) + table(0, 12, header = 0f)
        assertFalse(PageLayout.plan(plain, 100f).repeat.any { it })
    }

    @Test
    fun longTablesRunOverManyPages() {
        val items = table(0, 60, h = 20f, header = 20f)
        val plan = PageLayout.plan(items, 100f, 10f)
        // 5 rows on page 1; after it the repeated header takes one place: 4 rows per page for the other 55.
        assertEquals(1 + (55 + 3) / 4, plan.pages)
        for (k in items.indices) if (plan.page[k] > 0 && (k == 0 || plan.page[k - 1] != plan.page[k])) assertTrue(plan.repeat[k])
    }

    // ── Abschnitte mit eigener Kopf-/Fußzeile ─────────────────────────────────────────────────────────────

    private val withSections = DocMarkup.withPageSetup(
        "# Arbeit\n\nEinleitung.\n\n" + DocMarkup.sectionMarker(DocMarkup.Section(header = "Kapitel 2 – Methoden", footer = "Entwurf {seite}")) +
            "\n\n## Methoden\n\nText.\n\n" + DocMarkup.sectionMarker(DocMarkup.Section(footer = "")) + "\n\n## Anhang\n\nTabellen.\n",
        DocMarkup.PageSetup(header = "Hausarbeit", footer = "TU Dresden"),
    )

    @Test
    fun sectionMarkersCarryHeaderAndFooter() {
        assertEquals("<!-- abschnitt -->", DocMarkup.sectionMarker(DocMarkup.Section()))
        val s = DocMarkup.section("<!-- abschnitt: kopfzeile=Kapitel 2; fusszeile= -->")!!
        assertEquals("Kapitel 2", s.header)
        assertEquals("", s.footer, "leer: keine Fußzeile")
        assertEquals(null, DocMarkup.section("<!-- abschnitt -->")!!.header, "nicht gesetzt: wie davor")
        assertEquals(null, DocMarkup.section("<!-- kopfzeile: x -->"))
        val all = DocMarkup.sections(withSections)
        assertEquals(2, all.size)
        // Taking over: the third section keeps the header of the second, has no footer.
        val m = DocMarkup.sectionMargins(DocMarkup.pageSetup(withSections), all)
        assertEquals("Kapitel 2 – Methoden" to "Entwurf {seite}", m[0])
        assertEquals("Kapitel 2 – Methoden" to "", m[1])
        // The setting lines of the document do not swallow the section lines (and the other way round).
        assertEquals("Hausarbeit", DocMarkup.pageSetup(withSections).header)
        assertTrue(DocMarkup.isComment(DocMarkup.sectionMarker(all[0])), "nicht im Text der Exporte")
        assertTrue(DocMarkup.section(DocMarkup.stripComments(withSections).lines().first { "abschnitt" in it }) != null, "bleibt beim Entfernen der Notizen")
    }

    @Test
    fun pagesShowTheHeaderOfTheirSection() {
        val setup = DocMarkup.pageSetup(withSections)
        val starts = DocMarkup.sections(withSections).let { listOf(3 to it[0], 5 to it[1]) }
        assertEquals(null, sectionOf(starts, setup, 2))
        assertEquals("Hausarbeit", pageMargins(setup, 2, 6, "1.1.2027", sectionOf(starts, setup, 2)).headerLeft)
        val p4 = pageMargins(setup, 4, 6, "1.1.2027", sectionOf(starts, setup, 4))
        assertEquals("Kapitel 2 – Methoden", p4.headerLeft)
        assertEquals("Entwurf 4", p4.footerLeft)
        assertEquals("", pageMargins(setup, 6, 6, "1.1.2027", sectionOf(starts, setup, 6)).footerLeft)
    }

    @Test
    fun pdfGivesSectionsNamedPages() {
        val html = DocumentWriter.toHtml("Arbeit", withSections, print = true)
        assertTrue("@page abs1{" in html && "@page abs2{" in html, html.take(3000))
        assertTrue("Kapitel 2 – Methoden" in html.substringAfter("@page abs1{").substringBefore("}"), "Kopfzeile des Abschnitts")
        assertTrue("<div class=\"abschnitt\" style=\"page:abs1\">" in html && "<div class=\"abschnitt\" style=\"page:abs2\">" in html)
        assertEquals(html.split("<div class=\"abschnitt\"").size - 1, html.split("</div>").size - 1 - Regex("<div(?! class=\"abschnitt\")").findAll(html).count())
        // Tables: the header row in <thead>, printed again on every page.
        val t = DocumentWriter.toHtml("T", "| A | B |\n| --- | --- |\n| 1 | 2 |\n| 3 | 4 |\n", print = true)
        assertTrue("<thead><tr><th" in t && "</thead>" in t && "<tbody>" in t && "thead{display:table-header-group}" in t, t.takeLast(800))
    }

    private fun zipOf(f: File): LinkedHashMap<String, ByteArray> {
        val files = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(f.readBytes())).use { z -> generateSequence { z.nextEntry }.forEach { files[it.name] = z.readBytes() } }
        return files
    }

    @Test
    fun wordWritesAndReadsSections() {
        val f = File.createTempFile("abschnitte", ".docx")
        MarkdownDocx.write(f, "Arbeit", withSections)
        val files = zipOf(f)
        f.delete()
        val doc = files["word/document.xml"]!!.toString(Charsets.UTF_8)
        assertEquals(3, Regex("<w:sectPr>").findAll(doc).count(), "drei Abschnitte")
        assertTrue("r:id=\"rIdHeader4\"" in doc && "r:id=\"rIdFooter5\"" in doc, doc.takeLast(1500))
        assertTrue("Kapitel 2 – Methoden" in files["word/header4.xml"]!!.toString(Charsets.UTF_8))
        assertTrue("Entwurf" in files["word/footer4.xml"]!!.toString(Charsets.UTF_8))
        val rels = files["word/_rels/document.xml.rels"]!!.toString(Charsets.UTF_8)
        assertTrue("header5.xml" in rels && "footer5.xml" in rels)
        assertTrue("/word/header5.xml" in files["[Content_Types].xml"]!!.toString(Charsets.UTF_8))
        // As a file from Word it comes back with its sections (own header and footer, taken over where not set).
        files["docProps/core.xml"] = files["docProps/core.xml"]!!.toString(Charsets.UTF_8).replace("<dc:creator>Lora</dc:creator>", "<dc:creator>Word</dc:creator>").toByteArray()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> files.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        val md = OfficeImport.docx(out.toByteArray())
        val back = DocMarkup.sections(md)
        assertEquals(2, back.size, md)
        assertEquals("Kapitel 2 – Methoden", back[0].header)
        assertTrue(back[0].footer!!.startsWith("Entwurf"), back[0].footer)
        assertEquals("Hausarbeit", DocMarkup.pageSetup(md).header, "die Kopfzeile des ersten Abschnitts ist die des Dokuments")
        assertTrue(md.indexOf("abschnitt") in md.indexOf("Einleitung") until md.indexOf("Methoden"), md)
    }

    // ── Tabstopps ───────────────────────────────────────────────────────────────────────────────────────────

    private val stops = listOf(TabStop(40), TabStop(90, TabKind.CENTER), TabStop(160, TabKind.RIGHT))

    @Test
    fun tabStopsLiveInTheParagraphMarker() {
        val p = ParaFormat(tabs = stops)
        assertEquals("<!--a:;t=40l90c160r-->", p.marker())
        val m = io.github.thepytales.llmhub.ui.ALIGN_MARK.find(p.marker())!!
        assertEquals(stops, ParaFormat.fromMarker(ParaFormat(), m).tabs)
        assertEquals(listOf(TabStop(5), TabStop(30, TabKind.RIGHT)), TabStop.parse("30r5l5c"), "sortiert, je Stelle einer")
        // Through Markdown and back: tabs and stops stay, a tab at the start is no code block.
        val r = RichText.fromBlocks(listOf("${p.marker()}Name\tDatum\tOrt", "<!--a:-->\tEingerückt"))
        val back = RichText.fromBlocks(RichText.toBlocks(r))
        assertEquals("Name\tDatum\tOrt\n\tEingerückt", back.text)
        assertEquals(stops, back.paras[0].tabs)
        val lead = RichText.toBlocks(RichText.fromBlocks(listOf("x")).let { RichText("\tEinzug", List(7) { io.github.thepytales.llmhub.docs.text.CharFormat.PLAIN }, listOf(ParaFormat())) })
        assertTrue(lead.single().startsWith("<!--a:-->\t"), lead.single())
    }

    @Test
    fun tabsGoToTheNextStop() {
        assertEquals(40f to TabKind.LEFT, io.github.thepytales.llmhub.ui.FlowTabs.nextStop(stops, 10f))
        assertEquals(90f to TabKind.CENTER, io.github.thepytales.llmhub.ui.FlowTabs.nextStop(stops, 40f))
        assertEquals(162.5f to TabKind.LEFT, io.github.thepytales.llmhub.ui.FlowTabs.nextStop(stops, 161f), "danach die üblichen 12,5 mm")
        assertEquals(25f to TabKind.LEFT, io.github.thepytales.llmhub.ui.FlowTabs.nextStop(emptyList(), 13f))
    }

    @Test
    fun wordAndPdfKeepTabs() {
        val md = "# Brief\n\n${ParaFormat(tabs = stops).marker()}Dresden\tden 7.11.2026\tSeite 1\n\nOhne\tStopps\n"
        val f = File.createTempFile("tabs", ".docx")
        MarkdownDocx.write(f, "Brief", md)
        val files = zipOf(f)
        f.delete()
        val doc = files["word/document.xml"]!!.toString(Charsets.UTF_8)
        assertTrue("<w:tabs><w:tab w:val=\"left\" w:pos=\"2267\"/><w:tab w:val=\"center\" w:pos=\"5102\"/><w:tab w:val=\"right\" w:pos=\"9070\"/></w:tabs>" in doc, doc)
        assertTrue(Regex("<w:tab/>").findAll(doc).count() >= 3, "Tabs als w:tab")
        files["docProps/core.xml"] = files["docProps/core.xml"]!!.toString(Charsets.UTF_8).replace("<dc:creator>Lora</dc:creator>", "<dc:creator>Word</dc:creator>").toByteArray()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> files.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        val back = OfficeImport.docx(out.toByteArray())
        assertTrue("Dresden\tden 7.11.2026\tSeite 1" in back, back)
        val line = back.lines().first { "Dresden" in it }
        assertEquals(stops, ParaFormat.fromMarker(ParaFormat(), io.github.thepytales.llmhub.ui.ALIGN_MARK.find(line)!!).tabs)
        val html = DocumentWriter.toHtml("Brief", md, print = true)
        assertTrue("<span class=\"tabstop\" style=\"left:90.0mm;transform:translateX(-50%)\">Seite 1</span>" in html, html.takeLast(1500))
        assertTrue("Ohne<span class=\"tab\">\t</span>Stopps" in html)
    }
}
