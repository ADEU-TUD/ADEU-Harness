package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.docs.AiReview
import io.github.thepytales.llmhub.docs.DocCitations
import io.github.thepytales.llmhub.docs.DocMarkup
import io.github.thepytales.llmhub.docs.DocStats
import io.github.thepytales.llmhub.docs.DocumentWriter
import io.github.thepytales.llmhub.docs.HtmlPdf
import io.github.thepytales.llmhub.docs.MarkdownDocx
import io.github.thepytales.llmhub.docs.OfficeImport
import io.github.thepytales.llmhub.docs.PptxWriter
import io.github.thepytales.llmhub.docs.SlideDeck
import io.github.thepytales.llmhub.docs.SlideHandout
import io.github.thepytales.llmhub.docs.SpellCheck
import io.github.thepytales.llmhub.research.CitationStyle
import io.github.thepytales.llmhub.research.Person
import io.github.thepytales.llmhub.research.Reference
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Word-like extras of the documents (footnotes, pictures, page setup) and the new slide layouts. */
class DocsAndSlidesExtrasTest {
    private fun png(w: Int = 200, h: Int = 100): ByteArray {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = java.awt.Color(0, 0, 140)
        g.fillRect(0, 0, w, h)
        g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
    }

    private fun pngFile(): File = Files.createTempFile("bild", ".png").toFile().apply { writeBytes(png()) }

    /** All parts of the package; every XML part must parse. */
    private fun parts(file: File): Map<String, ByteArray> = ZipFile(file).use { zip ->
        zip.entries().toList().associate { e -> e.name to zip.getInputStream(e).readBytes() }
    }.also { all ->
        val f = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        all.filterKeys { it.endsWith(".xml") || it.endsWith(".rels") }.forEach { (name, bytes) ->
            runCatching { f.newDocumentBuilder().parse(bytes.inputStream()) }.onFailure { throw AssertionError("$name ist kein gültiges XML: ${it.message}") }
        }
    }

    private fun text(parts: Map<String, ByteArray>, name: String) = parts[name]?.toString(Charsets.UTF_8) ?: error("$name fehlt")

    @Test
    fun footnotesAreParsedAndNumberedByUse() {
        val md = "# Titel\n\nErst B[^b], dann A[^a] und nochmal B[^b].\n\n[^a]: Note A\n[^b]: Note B\n  weiter B\n"
        val notes = DocMarkup.footnotes(md)
        assertEquals(listOf("b" to 1, "a" to 2), notes.notes.map { it.label to it.number })
        assertEquals("Note B weiter B", notes.notes.first().text)
        assertFalse("[^a]:" in notes.body)
        assertEquals("3", DocMarkup.nextNoteLabel("x[^1] y[^2]\n\n[^1]: a\n[^2]: b"))
    }

    @Test
    fun footnotesInWordAreRealFootnotes() {
        val file = Files.createTempFile("fussnoten", ".docx").toFile()
        MarkdownDocx.write(file, "Fußnoten", "# Fußnoten\n\nEin Satz mit Beleg[^1].\n\n| A | B |\n| --- | --- |\n| x[^2] | y |\n\n[^1]: Müller 2020, S. 3.\n\n[^2]: In der Tabelle.\n")
        val p = parts(file)
        val notes = text(p, "word/footnotes.xml")
        assertTrue("w:id=\"1\"" in notes && "Müller 2020" in notes && "w:id=\"2\"" in notes && "separator" in notes)
        val doc = text(p, "word/document.xml")
        assertTrue("<w:footnoteReference w:id=\"1\"/>" in doc && "<w:footnoteReference w:id=\"2\"/>" in doc)
        assertFalse("[^1]" in doc)
        assertTrue("footnotes.xml" in text(p, "word/_rels/document.xml.rels"))
        assertTrue("/word/footnotes.xml" in text(p, "[Content_Types].xml"))
        assertTrue("word/settings.xml" in p)
    }

    @Test
    fun footnotesInHtmlAndPdfAtTheEnd() {
        val html = DocumentWriter.toHtml("T", "# T\n\nText[^n].\n\n[^n]: Die Notiz.\n", print = true)
        assertTrue("<sup class=\"fnref\"><a href=\"#fn-1\">1</a></sup>" in html)
        assertTrue("class=\"footnotes\"" in html && "<li id=\"fn-1\">Die Notiz.</li>" in html)
        assertFalse("[^n]" in html)
        val text = DocumentWriter.toText("T", "Text[^n].\n\n[^n]: Die Notiz.\n")
        assertTrue("Text[1]." in text && "[1] Die Notiz." in text)
        val pdf = Files.createTempFile("fn", ".pdf").toFile()
        HtmlPdf.renderBuiltIn(HtmlPdf.withPrintColors(html), pdf)
        assertTrue(HtmlPdf.pageCount(pdf) >= 1)
    }

    @Test
    fun picturesAreEmbeddedInWordHtmlAndPdf() {
        val img = pngFile()
        assertEquals(200 to 100, DocMarkup.imageSize(img.readBytes()))
        val md = "# Bilder\n\nText davor.\n![Ein blaues Rechteck](${img.absolutePath}){width=50%}\nText danach.\n"
        val parsed = DocMarkup.image("![Ein blaues Rechteck](${img.absolutePath}){width=50%}")
        assertEquals(50, parsed?.width)
        val file = Files.createTempFile("bild", ".docx").toFile()
        MarkdownDocx.write(file, "Bilder", md)
        val p = parts(file)
        assertTrue("word/media/bild1.png" in p)
        val doc = text(p, "word/document.xml")
        assertTrue("r:embed=\"rIdImg1\"" in doc && "descr=\"Ein blaues Rechteck\"" in doc)
        // Half of the text width (9638 twips = 6 120 130 EMU).
        assertTrue("cx=\"${9638L * 635 / 2}\"" in doc, doc)
        assertTrue("Caption" in doc)
        assertTrue("media/bild1.png" in text(p, "word/_rels/document.xml.rels"))
        val html = DocumentWriter.toHtml("Bilder", md, print = true)
        assertTrue("<img src=\"data:image/png;base64," in html && "width:50%" in html && "<figcaption>Ein blaues Rechteck</figcaption>" in html)
        // Relative paths are resolved against the document's folder; other files than pictures are never embedded.
        val dir = Files.createTempDirectory("doc").toFile()
        val rel = DocMarkup.storeImage(dir, img.readBytes())
        assertTrue(rel.startsWith("Bilder/"))
        assertTrue(File(dir, rel).isFile)
        assertTrue(File(dir, rel).absolutePath in DocMarkup.resolveImages("![x]($rel)", dir))
        val notImage = File(dir, "geheim.txt").apply { writeText("nicht einbetten") }
        assertNull(DocMarkup.imageBytes(notImage.absolutePath))
    }

    @Test
    fun headerFooterWithPageNumbersAndDate() {
        val file = Files.createTempFile("seiten", ".docx").toFile()
        MarkdownDocx.write(file, "S", DocMarkup.withPageSetup("# S\n\nText", DocMarkup.PageSetup(pageNumbers = true, date = true, header = "Professur")))
        val p = parts(file)
        val footer = text(p, "word/footer1.xml")
        assertTrue("Seite " in footer && " PAGE " in footer && " NUMPAGES " in footer && " von " in footer)
        val header = text(p, "word/header1.xml")
        assertTrue("Professur" in header && DocMarkup.today() in header)
        assertTrue("headerReference" in text(p, "word/document.xml"))
        // Switched off (and no footer line): no footer at all.
        val off = Files.createTempFile("ohne", ".docx").toFile()
        MarkdownDocx.write(off, "S", DocMarkup.withPageSetup("# S\n\nText", DocMarkup.PageSetup(pageNumbers = false)))
        val q = parts(off)
        assertFalse("word/footer1.xml" in q)
        assertFalse("footerReference" in text(q, "word/document.xml"))
        val setup = DocMarkup.pageSetup(DocMarkup.withPageSetup("Text", DocMarkup.PageSetup(false, true, "Kopf")))
        assertEquals(DocMarkup.PageSetup(false, true, "Kopf"), setup)
        val html = DocumentWriter.toHtml("S", "# S\n\nText\n\n<!-- seitenzahlen: aus -->\n<!-- kopfzeile: Kopf -->\n", print = true)
        assertFalse("counter(pages)" in html)
        assertTrue("@top-left{content:\"Kopf\"" in html)
        assertTrue("\"Seite \" counter(page) \" von \" counter(pages)" in DocumentWriter.toHtml("S", "Text", print = true))
    }

    @Test
    fun pageBreakAndLandscapeSections() {
        val md = "# Q\n\nHochformat.\n\\newpage\nNeue Seite.\n\n<!-- quer -->\n\n| A | B | C |\n| --- | --- | --- |\n| 1 | 2 | 3 |\n\n<!-- hoch -->\n\nWieder hoch.\n"
        val file = Files.createTempFile("quer", ".docx").toFile()
        MarkdownDocx.write(file, "Q", md)
        val doc = text(parts(file), "word/document.xml")
        assertTrue("<w:br w:type=\"page\"/>" in doc)
        assertTrue("w:orient=\"landscape\"" in doc)
        assertEquals(3, Regex("<w:sectPr>").findAll(doc).count())
        // The table in the landscape section uses the wider text area.
        assertTrue("w:tblW w:w=\"14570\"" in doc)
        assertFalse("&lt;!--" in doc)
        val html = DocumentWriter.toHtml("Q", md, print = true)
        assertTrue("<div class=\"pagebreak\"></div>" in html && "<section class=\"quer\">" in html && "@page quer{size:A4 landscape}" in html)
        val pdf = Files.createTempFile("quer", ".pdf").toFile()
        HtmlPdf.renderBuiltIn(HtmlPdf.withPrintColors(html), pdf)
        assertTrue(HtmlPdf.pageCount(pdf) >= 3, "Seitenumbruch und Querformat beginnen neue Seiten")
    }

    @Test
    fun wordImportKeepsFootnotesAndPictures() {
        val img = pngFile()
        val file = Files.createTempFile("import", ".docx").toFile()
        MarkdownDocx.write(file, "Import", "# Import\n\nSatz mit Note[^1].\n\n![Logo der Uni](${img.absolutePath})\n\n[^1]: Die Fußnote.\n")
        val imported = OfficeImport.read("import.docx", file.readBytes())!!
        val md = imported.tabs.first().second
        assertTrue("Satz mit Note[^1]." in md, md)
        assertTrue("[^1]: Die Fußnote." in md, md)
        val line = md.lines().first { it.startsWith("![") }
        val pic = DocMarkup.image(line)!!
        assertEquals("Logo der Uni", pic.alt)
        assertTrue(pic.src.startsWith("Bilder/"))
        assertTrue(imported.media.containsKey(pic.src))
    }

    private val deck = listOf(
        SlideDeck.Slide("Vortrag", "Untertitel\nOktober 2026", layout = SlideDeck.Layout.TITLE),
        SlideDeck.Slide("Agenda", "", layout = SlideDeck.Layout.AGENDA),
        SlideDeck.Slide("Teil eins", "", layout = SlideDeck.Layout.SECTION),
        SlideDeck.Slide("Bild und Text", "- Punkt", layout = SlideDeck.Layout.IMAGE_TEXT, image = "Bilder/a.png", imageAlt = "Ein Foto", imageLeft = true),
        SlideDeck.Slide("Großes Bild", "", layout = SlideDeck.Layout.IMAGE, image = "Bilder/b.png", imageAlt = "Panorama"),
        SlideDeck.Slide("Vergleich", "### Vorher\n\n- alt", "### Nachher\n\n- neu", layout = SlideDeck.Layout.COMPARISON, notes = "Sagen"),
        SlideDeck.Slide("Teil zwei", "", layout = SlideDeck.Layout.SECTION),
        SlideDeck.Slide("Zahlen", "| Jahr | Wert |\n| --- | --- |\n| 2025 | 3 |", layout = SlideDeck.Layout.TABLE),
        SlideDeck.Slide("Diagramm", "```chart\n{\"type\": \"bar\", \"title\": \"Werte\", \"labels\": [\"A\", \"B\"], \"series\": [{\"name\": \"x\", \"values\": [3, 5]}]}\n```", layout = SlideDeck.Layout.CHART),
        SlideDeck.Slide("Vielen Dank!", "Anna Muster\nanna.muster@tu-dresden.de", layout = SlideDeck.Layout.END),
    )

    @Test
    fun newSlideLayoutsRoundTrip() {
        val md = SlideDeck.markdown(deck)
        val back = SlideDeck.parse(md)
        assertEquals(deck.map { it.layout }, back.map { it.layout })
        assertEquals(deck[3], back[3])
        assertEquals(deck[4], back[4])
        assertEquals(deck[5], back[5])
        assertEquals(md, SlideDeck.markdown(back))
        assertEquals(listOf("Teil eins", "Teil zwei"), SlideDeck.agenda(back, 1))
        assertEquals("Vorher" to "- alt", SlideDeck.columnHead(back[5].body))
        assertNotNull(SlideDeck.chartOf(back[8].body))
        // Old decks without markers keep their layouts.
        val old = SlideDeck.parse("# T\n\n---\n\n## Inhalt\n\n- a\n- b\n\n---\n\n## Zwei\n\n- l\n\n|||\n\n- r")
        assertEquals(listOf(SlideDeck.Layout.TITLE, SlideDeck.Layout.CONTENT, SlideDeck.Layout.TWO_COLUMNS), old.map { it.layout })
    }

    @Test
    fun pptxWithNewLayoutsIsValid() {
        val a = pngFile()
        val md = SlideDeck.markdown(deck.map { if (it.image.isNotBlank()) it.copy(image = a.absolutePath) else it })
        val slides = PptxWriter.parse(md)
        assertEquals(
            listOf(PptxWriter.Kind.TITLE, PptxWriter.Kind.AGENDA, PptxWriter.Kind.SECTION, PptxWriter.Kind.IMAGE_TEXT, PptxWriter.Kind.IMAGE, PptxWriter.Kind.COMPARISON,
                PptxWriter.Kind.SECTION, PptxWriter.Kind.TABLE, PptxWriter.Kind.CHART, PptxWriter.Kind.END),
            slides.map { it.kind },
        )
        assertEquals(listOf("Teil eins", "Teil zwei"), slides[1].agenda)
        assertEquals("Vorher", slides[5].leftHead)
        val file = Files.createTempFile("folien", ".pptx").toFile()
        PptxWriter.write(file, "Vortrag", md, "Professur")
        val p = parts(file)
        assertTrue("ppt/media/bild4.png" in p && "ppt/media/bild5.png" in p)
        assertTrue("rIdImg" in text(p, "ppt/slides/_rels/slide4.xml.rels"))
        assertTrue("r:embed=\"rIdImg\"" in text(p, "ppt/slides/slide4.xml") && "descr=\"Ein Foto\"" in text(p, "ppt/slides/slide4.xml"))
        assertTrue("Teil zwei" in text(p, "ppt/slides/slide2.xml"))
        // The chart is drawn as shapes (bars and labels), not as a table.
        val chart = text(p, "ppt/slides/slide9.xml")
        assertTrue(Regex("name=\"Balken").findAll(chart).count() >= 2 && "Werte" in chart && "<a:tbl>" !in chart)
        assertTrue("<a:tbl>" in text(p, "ppt/slides/slide8.xml"))
        assertTrue("anna.muster@tu-dresden.de" in text(p, "ppt/slides/slide10.xml"))
    }

    @Test
    fun pptxImportKeepsTablesPicturesAndLayouts() {
        val a = pngFile()
        val file = Files.createTempFile("import", ".pptx").toFile()
        PptxWriter.write(file, "Vortrag", SlideDeck.markdown(deck.map { if (it.image.isNotBlank()) it.copy(image = a.absolutePath) else it }), "Professur")
        val imported = OfficeImport.read("vortrag.pptx", file.readBytes())!!
        val slides = SlideDeck.parse(imported.tabs.first().second)
        val table = slides.first { it.title == "Zahlen" }
        assertEquals(SlideDeck.Layout.TABLE, table.layout)
        assertTrue("| Jahr | Wert |" in table.body && "| 2025 | 3 |" in table.body)
        val pic = slides.first { it.title == "Bild und Text" }
        assertEquals(SlideDeck.Layout.IMAGE_TEXT, pic.layout)
        assertTrue(pic.imageLeft)
        assertTrue(imported.media.containsKey(pic.image))
        assertEquals(SlideDeck.Layout.IMAGE, slides.first { it.title == "Großes Bild" }.layout)
        assertEquals(SlideDeck.Layout.SECTION, slides.first { it.title == "Teil eins" }.layout)
        assertEquals("Sagen", slides.first { it.title == "Vergleich" }.notes)
    }

    @Test
    fun handoutHasThreeOrSixSlidesPerPage() {
        val md = SlideDeck.markdown((1..7).map { n -> if (n == 1) SlideDeck.Slide("Titel", "Untertitel", layout = SlideDeck.Layout.TITLE) else SlideDeck.Slide("Folie $n", "- Punkt $n", notes = "Notiz $n") })
        fun pages(perPage: Int, notes: Boolean): Int {
            val pdf = Files.createTempFile("handout", ".pdf").toFile()
            HtmlPdf.renderBuiltIn(HtmlPdf.withPrintColors(SlideHandout.html(md, "Vortrag", perPage, notes, "Professur")), pdf)
            return HtmlPdf.pageCount(pdf)
        }
        assertEquals(3, pages(3, notes = true))
        assertEquals(3, pages(3, notes = false))
        assertEquals(2, pages(6, notes = true))
        val html = SlideHandout.html(md, "Vortrag", 3, notes = true)
        assertTrue("Notiz 2" in html)
        assertFalse("Notiz 2" in SlideHandout.html(md, "Vortrag", 3, notes = false))
    }

    @Test
    fun fullSlidesAreSplit() {
        val items = (1..9).joinToString("\n") { "- Punkt $it" } + "\n  - Unterpunkt zu 9"
        val slides = listOf(SlideDeck.Slide("T", layout = SlideDeck.Layout.TITLE), SlideDeck.Slide("Viel", "Einleitung\n\n$items", notes = "N"), SlideDeck.Slide("Wenig", "- a\n- b"))
        val (out, n) = SlideDeck.splitFull(slides)
        assertEquals(1, n)
        assertEquals(listOf("T", "Viel (1/2)", "Viel (2/2)", "Wenig"), out.map { it.title })
        assertTrue(out[1].body.startsWith("Einleitung"))
        assertTrue(out.drop(1).take(2).all { s -> s.body.lines().count { it.startsWith("- ") } <= 6 })
        assertTrue("Unterpunkt zu 9" in out[2].body)
        assertEquals("N", out[1].notes)
        assertEquals("", out[2].notes)
        val deckFromDoc = SlideDeck.fromDocument("# Bericht\n\n## Ziele\n\nWir wollen viel erreichen. Und mehr.\n\n- Ziel A\n- Ziel B\n\n## Daten\n\n| a | b |\n| --- | --- |\n| 1 | 2 |\n", "Bericht")
        assertEquals(SlideDeck.Layout.TITLE, deckFromDoc.first().layout)
        assertEquals("Bericht", deckFromDoc.first().title)
        assertTrue(deckFromDoc.any { it.title == "Ziele" && "Ziel A" in it.body && it.notes.isNotBlank() })
        assertTrue(deckFromDoc.any { it.layout == SlideDeck.Layout.TABLE && it.title == "Daten" })
        assertEquals(SlideDeck.Layout.END, deckFromDoc.last().layout)
    }

    @Test
    fun statisticsSpellCheckAndReview() {
        val s = DocStats.of("# Titel\n\nZwei **Wörter** hier[^1].\n\n<!-- seitenzahlen: aus -->\n\n[^1]: Note\n")
        assertEquals(5, s.words)
        assertEquals(1, s.minutes)
        assertTrue(s.charsNoSpaces < s.chars)
        val answer = "Hier:\n```json\n[{\"original\": \"Fehlr\", \"korrektur\": \"Fehler\", \"grund\": \"Tippfehler\"}, {\"original\": \"gibts\", \"korrektur\": \"gibt’s\", \"grund\": \"Apostroph\"}, {\"original\": \"x\", \"korrektur\": \"x\"}]\n```"
        val list = SpellCheck.parse(answer)
        assertEquals(2, list.size)
        val text = "Ein Fehlr im Text."
        assertEquals(1, SpellCheck.applicable(text, list).size)
        assertEquals("Ein Fehler im Text.", SpellCheck.applyAll(text, SpellCheck.applicable(text, list)))
        assertEquals(emptyList(), SpellCheck.parse("Keine Fehler."))

        val old = "# T\n\nAbsatz eins.\n\nAbsatz zwei.\n\nAbsatz drei.\n"
        val new = "# T\n\nAbsatz eins geändert.\n\nAbsatz zwei.\n\nGanz neu.\n"
        val r = AiReview.compare(old, new)
        assertEquals(listOf(AiReview.Change(1, "Absatz eins."), AiReview.Change(3, "Absatz drei.")), r.changes)
        val blocks = io.github.thepytales.llmhub.docs.DocBlocks.split(new)
        assertEquals("Absatz eins.", AiReview.reject(blocks, r.changes[0])[1])
        val removed = AiReview.compare(old, "# T\n\nAbsatz eins.\n\nAbsatz drei.\n")
        assertEquals(listOf(AiReview.Removed(2, "Absatz zwei.")), removed.removed)
        assertEquals(io.github.thepytales.llmhub.docs.DocBlocks.split(old), AiReview.restore(io.github.thepytales.llmhub.docs.DocBlocks.split("# T\n\nAbsatz eins.\n\nAbsatz drei.\n"), removed.removed[0]))
    }

    @Test
    fun citationsAndAutomaticBibliography() {
        val a = Reference(id = "a", authors = listOf(Person("Müller", "Hans"), Person("Schmidt", "Eva")), year = "2020", title = "Unterricht", publisher = "Beltz", place = "Weinheim")
        val b = Reference(id = "b", authors = listOf(Person("Abel", "Ida"), Person("Berg", "Jo"), Person("Cohn", "Kai")), year = "2019", title = "Lernen", container = "Zeitschrift", volume = "3", pages = "1-10")
        assertEquals("(Müller & Schmidt, 2020)", DocCitations.inText(a, CitationStyle.APA7_DE))
        assertEquals("(Abel et al., 2019)", DocCitations.inText(b, CitationStyle.APA7_DE))
        assertEquals("(Müller/Schmidt 2020)", DocCitations.inText(a, CitationStyle.ZFPAED))
        assertEquals("(Abel u. a. 2019)", DocCitations.inText(b, CitationStyle.ZFPAED))
        assertEquals("[2]", DocCitations.inText(a, CitationStyle.IEEE, DocCitations.number(listOf(a, b), a, CitationStyle.IEEE)))
        var md = "# Arbeit\n\nText (Müller & Schmidt, 2020).\n\n[^1]: Eine Note.\n"
        md = DocCitations.withBibliography(md, listOf(a), CitationStyle.APA7_DE)
        md = DocCitations.withBibliography(md, listOf(a, b), CitationStyle.APA7_DE)
        assertEquals(1, Regex("## Literatur").findAll(md).count())
        assertEquals(listOf("b", "a"), DocCitations.cited(md).ids)
        assertEquals(CitationStyle.APA7_DE, DocCitations.cited(md).style)
        assertTrue(md.indexOf("Abel") < md.indexOf("Müller, H."), md)
        assertTrue("[^1]: Eine Note." in md)
        // The marker is not exported.
        assertFalse("literatur:" in DocumentWriter.toHtml("Arbeit", md, print = true))
    }
}
