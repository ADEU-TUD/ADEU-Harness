package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.docs.DocBlocks
import io.github.thepytales.llmhub.docs.DocMarkup
import io.github.thepytales.llmhub.docs.MarkdownDocx
import io.github.thepytales.llmhub.docs.OfficeImport
import io.github.thepytales.llmhub.docs.text.Align
import io.github.thepytales.llmhub.docs.text.CharFormat
import io.github.thepytales.llmhub.docs.text.DocOutline
import io.github.thepytales.llmhub.docs.text.DocxWriter
import io.github.thepytales.llmhub.docs.text.ParaKind
import io.github.thepytales.llmhub.docs.text.RichHtml
import io.github.thepytales.llmhub.docs.text.RichRtf
import io.github.thepytales.llmhub.docs.text.RichText
import io.github.thepytales.llmhub.docs.text.StyleSet
import io.github.thepytales.llmhub.docs.text.StyleSheet
import io.github.thepytales.llmhub.docs.text.Styles
import io.github.thepytales.llmhub.ui.inlineSegments
import io.github.thepytales.llmhub.ui.sheetFor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Editor-Kern und Dokument (AP-43 bis AP-46): paragraph settings and Formatvorlagen in the Markdown, the Gliederung,
 * the clipboard with formatting and the Word extras (Kopf-/Fußzeile, Ränder, Textfelder, Inhaltssteuerelemente).
 */
class EditorCoreTest {
    private fun rich(md: String) = RichText.fromBlocks(DocBlocks.split(md).filter(RichText::isFlowBlock))
    private fun md(r: RichText) = DocBlocks.join(RichText.toBlocks(r))

    @Test
    fun formatvorlagenAndParagraphSettingsRoundTrip() {
        var r = rich("Titel der Arbeit\n\nUntertitel\n\nText mit Abstand.\n\nAbbildung 1: Ein Bild\n\nMüller, A. (2020): Ein Buch. Dresden.\n\nval x = 1\n")
        r = r.withParas(r.paras.mapIndexed { i, p ->
            when (i) {
                0 -> p.copy(kind = ParaKind.TITLE, align = Align.CENTER)
                1 -> p.copy(kind = ParaKind.SUBTITLE)
                2 -> p.copy(spacing = 1.5f, left = 10, first = 5)
                3 -> p.copy(kind = ParaKind.CAPTION)
                4 -> p.copy(kind = ParaKind.LITERATURE)
                else -> p.copy(kind = ParaKind.CODE)
            }
        })
        val out = md(r)
        assertTrue("<!--a:c;s=title-->Titel der Arbeit" in out, out)
        assertTrue("<!--a:;z=1.5;l=10;f=5-->Text" in out, out)
        val back = rich(out)
        assertEquals(listOf(ParaKind.TITLE, ParaKind.SUBTITLE, ParaKind.NORMAL, ParaKind.CAPTION, ParaKind.LITERATURE, ParaKind.CODE), back.paras.map { it.kind })
        assertEquals(Align.CENTER, back.paras[0].align)
        assertEquals(1.5f, back.paras[2].spacing)
        assertEquals(10, back.paras[2].left)
        assertEquals(5, back.paras[2].first)
        assertEquals(out, md(back))
        // Every export reads the marker as no text.
        assertTrue(inlineSegments("<!--a:;z=1.5;l=10;f=5-->Text").none { "<!--" in it.text })
        // Enter after a title starts normal text.
        val typed = back.edited(back.text.replaceFirst("Titel der Arbeit", "Titel der Arbeit\nweiter"), null)
        assertEquals(ParaKind.NORMAL, typed.paras[1].kind)
    }

    @Test
    fun styleSetsAreReadWrittenAndAdapted() {
        val doc = "# Bericht\n\nText.\n"
        val sci = Styles.write(doc, StyleSheet(StyleSet.SCIENCE, explicit = true))
        assertTrue("<!-- vorlagen: wissenschaftlich -->" in sci, sci)
        val sheet = Styles.read(sci)
        assertEquals(StyleSet.SCIENCE, sheet.set)
        assertEquals("Times New Roman", sheet.of(ParaKind.NORMAL).font)
        assertEquals(1.5f, sheet.of(ParaKind.NORMAL).spacing)
        // Lists look like normal text.
        assertEquals(sheet.of(ParaKind.NORMAL), sheet.of(ParaKind.BULLET))
        // „Überschrift 1 an Markierung anpassen“
        val h1 = Styles.adapt(sheet.of(ParaKind.H1), CharFormat(font = "Georgia", size = 22, color = "00008C"), io.github.thepytales.llmhub.docs.text.ParaFormat())
        val changed = Styles.write(sci, sheet.copy(changed = mapOf(ParaKind.H1 to h1)))
        assertTrue(Regex("<!-- vorlage h1: schrift=Georgia; größe=22; fett; farbe=00008C").containsMatchIn(changed), changed)
        val again = Styles.read(changed)
        assertEquals(22f, again.of(ParaKind.H1).size)
        assertEquals("00008C", again.of(ParaKind.H1).color)
        // Writing twice keeps one set of lines; the text stays.
        val twice = Styles.write(changed, again)
        assertEquals(1, Regex("vorlagen:").findAll(twice).count())
        assertTrue(twice.startsWith("# Bericht\n\nText."), twice)
        // The setting lines are no running text and no text of the exports.
        assertFalse(RichText.isFlowBlock("<!-- vorlagen: tud -->"))
        assertTrue(DocMarkup.isComment("<!-- vorlage h1: größe=20 -->"))
        // Without own lines: the saved default, else the export style.
        assertEquals(StyleSet.TUD, sheetFor(doc, "TUD", "").set)
        assertEquals(StyleSet.PLAIN, sheetFor(doc, "TUD", "<!-- vorlagen: schlicht -->").set)
        assertEquals(StyleSet.SCIENCE, sheetFor(sci, "TUD", "<!-- vorlagen: schlicht -->").set)
    }

    @Test
    fun outlineMovesWholeSections() {
        val doc = "# Arbeit\n\nVorwort.\n\n## Einleitung\n\nE1.\n\n### Frage\n\nF1.\n\n## Methode\n\nM1.\n\n## Ergebnisse\n\nR1.\n"
        val heads = DocOutline.headings(doc)
        assertEquals(listOf("Arbeit", "Einleitung", "Frage", "Methode", "Ergebnisse"), heads.map { it.text })
        // „Ergebnisse“ before „Einleitung“: the section moves with its text.
        val moved = DocOutline.move(doc, 4, 1)
        assertEquals("# Arbeit\n\nVorwort.\n\n## Ergebnisse\n\nR1.\n\n## Einleitung\n\nE1.\n\n### Frage\n\nF1.\n\n## Methode\n\nM1.\n", moved)
        // „Einleitung“ (with its sub-section) one step down: after „Methode“.
        val down = DocOutline.step(doc, 1, up = false)
        assertEquals("# Arbeit\n\nVorwort.\n\n## Methode\n\nM1.\n\n## Einleitung\n\nE1.\n\n### Frage\n\nF1.\n\n## Ergebnisse\n\nR1.\n", down)
        assertEquals(doc, DocOutline.move(moved, 1, heads.size))
        // A section cannot move into itself; the end is a target.
        assertEquals(doc, DocOutline.move(doc, 1, 2))
        assertTrue(DocOutline.move(doc, 1, heads.size).endsWith("## Einleitung\n\nE1.\n\n### Frage\n\nF1.\n"))
    }

    @Test
    fun clipboardHtmlFromWordAndDocs() {
        val word = """
            Version:0.9
            <html><head><style>p.MsoNormal{margin:0}</style></head><body>
            <!--StartFragment-->
            <p class=MsoTitle>Projektbericht</p>
            <p class=MsoNormal>Ein <b>fetter</b> und <span style='font-style:italic'>kursiver</span> Satz mit <span style='color:#C00000'>Farbe</span>.</p>
            <p class=MsoListParagraph><![if !supportLists]><span>·<span>&nbsp;&nbsp;&nbsp; </span></span><![endif]>Erster Punkt</p>
            <p class=MsoListParagraph><![if !supportLists]><span>·<span>&nbsp;&nbsp;&nbsp; </span></span><![endif]>Zweiter Punkt</p>
            <p class=MsoNormal align=center style='text-align:center'>Mitte &amp; Ende</p>
            <!--EndFragment-->
            </body></html>
        """.trimIndent()
        val r = RichHtml.toRich(word)
        assertEquals(listOf("Projektbericht", "Ein fetter und kursiver Satz mit Farbe.", "Erster Punkt", "Zweiter Punkt", "Mitte & Ende"), r.paras.indices.map { r.paraText(it) })
        assertEquals(ParaKind.TITLE, r.paras[0].kind)
        assertEquals(ParaKind.BULLET, r.paras[2].kind)
        assertEquals(Align.CENTER, r.paras[4].align)
        val t = r.text
        assertTrue(r.chars[t.indexOf("fetter")].bold && !r.chars[t.indexOf("Ein")].bold)
        assertTrue(r.chars[t.indexOf("kursiver")].italic)
        assertEquals("C00000", r.chars[t.indexOf("Farbe")].color)
        // Google Docs: bold as font-weight, a heading and a numbered list.
        val docs = "<meta charset='utf-8'><b style=\"font-weight:normal;\" id=\"docs-internal-guid\"><h2><span style=\"font-weight:700\">Kapitel</span></h2>" +
            "<ol><li><p><span>eins</span></p></li><li><p><span style=\"font-weight:700\">zwei</span></p></li></ol></b>"
        val g = RichHtml.toRich(docs)
        assertEquals(listOf("Kapitel", "eins", "zwei"), g.paras.indices.map { g.paraText(it) })
        assertEquals(listOf(ParaKind.H2, ParaKind.NUMBERED, ParaKind.NUMBERED), g.paras.map { it.kind })
        assertTrue(g.chars[g.text.indexOf("zwei")].bold && !g.chars[g.text.indexOf("eins")].bold)
        // Out and back in.
        val mine = rich("## Teil\n\nEin **fetter** und <u>unterstrichener</u> Satz.\n\n- a\n- b\n")
        val back = RichHtml.toRich(RichHtml.fromRich(mine))
        assertEquals(mine.text, back.text)
        assertEquals(mine.paras.map { it.kind }, back.paras.map { it.kind })
        assertTrue(back.chars[back.text.indexOf("fetter")].bold && back.chars[back.text.indexOf("unterstrichener")].underline)
        // A piece of the text keeps its formats.
        val piece = mine.slice(mine.text.indexOf("fetter"), mine.text.indexOf(" Satz"))
        assertTrue(piece.chars.first().bold && piece.paras.size == 1)
    }

    @Test
    fun marginsInMillimetres() {
        val md = DocMarkup.withPageSetup("Text\n", DocMarkup.PageSetup(margins = DocMarkup.marginsFor(18)))
        assertTrue("<!-- raender: 18 mm -->" in md, md)
        val s = DocMarkup.pageSetup(md)
        assertEquals(18, s.marginMm)
        assertEquals(1020, s.marginTwips)
        assertEquals("breit", DocMarkup.marginsFor(25))
        assertEquals(20, DocMarkup.pageSetup("<!-- raender: 99 mm -->").marginMm)
    }

    private fun unzip(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z -> var e = z.nextEntry; while (e != null) { out[e.name] = z.readBytes(); e = z.nextEntry } }
        return out
    }

    private fun zip(files: Map<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z -> files.forEach { (n, d) -> z.putNextEntry(ZipEntry(n)); z.write(d); z.closeEntry() } }
        return bos.toByteArray()
    }

    /**
     * A Word file as templates make them (Prüfsammlung, AP-45): a foreign author, header and footer, wide margins,
     * a text box (with the fallback copy for old Word), fields in content controls, a subtitle and 1.5 line spacing.
     */
    private fun template(): ByteArray {
        val f = File.createTempFile("vorlage", ".docx")
        MarkdownDocx.write(f, "Antrag", "# Antrag\n\nErster Absatz.\n\n## Teil A\n\nZweiter Absatz.\n")
        val files = unzip(f.readBytes())
        f.delete()
        files["docProps/core.xml"] = files["docProps/core.xml"]!!.toString(Charsets.UTF_8).replace("<dc:creator>Lora</dc:creator>", "<dc:creator>Sekretariat</dc:creator>").toByteArray()
        var doc = files["word/document.xml"]!!.toString(Charsets.UTF_8)
        val box = "<w:p><w:r><w:t>Anker</w:t></w:r><w:r><mc:AlternateContent xmlns:mc=\"http://schemas.openxmlformats.org/markup-compatibility/2006\"><mc:Choice Requires=\"wps\"><w:drawing><wps:txbx xmlns:wps=\"http://schemas.microsoft.com/office/word/2010/wordprocessingShape\"><w:txbxContent><w:p><w:r><w:t>Im Textfeld</w:t></w:r></w:p></w:txbxContent></wps:txbx></w:drawing></mc:Choice>" +
            "<mc:Fallback><w:pict><v:textbox xmlns:v=\"urn:schemas-microsoft-com:vml\"><w:txbxContent><w:p><w:r><w:t>Im Textfeld</w:t></w:r></w:p></w:txbxContent></v:textbox></w:pict></mc:Fallback></mc:AlternateContent></w:r></w:p>"
        val fields = "<w:sdt><w:sdtPr><w:alias w:val=\"Name\"/></w:sdtPr><w:sdtContent><w:p><w:r><w:t>Antragsteller: Erika Muster</w:t></w:r></w:p></w:sdtContent></w:sdt>" +
            "<w:p><w:pPr><w:pStyle w:val=\"Subtitle\"/></w:pPr><w:r><w:t>Ein Untertitel</w:t></w:r></w:p>" +
            "<w:p><w:pPr><w:spacing w:line=\"360\" w:lineRule=\"auto\"/><w:ind w:left=\"567\" w:hanging=\"567\"/></w:pPr><w:r><w:t>Anderthalbzeilig mit hängendem Einzug.</w:t></w:r></w:p>"
        val at = doc.indexOf("<w:p><w:pPr><w:pStyle w:val=\"Heading2\"/>")
        doc = doc.substring(0, at) + box + fields + doc.substring(at)
        // Header and footer of a template, margins 2.5 cm.
        doc = doc.replace(Regex("<w:sectPr>.*</w:sectPr>"), "<w:sectPr><w:headerReference w:type=\"default\" r:id=\"rIdKopf\"/><w:footerReference w:type=\"default\" r:id=\"rIdFuss\"/><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1417\" w:right=\"1417\" w:bottom=\"1134\" w:left=\"1417\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/></w:sectPr>")
        files["word/document.xml"] = doc.toByteArray()
        val ns = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\""
        files["word/kopf.xml"] = "<w:hdr $ns><w:p><w:r><w:t>Professur für Didaktik</w:t></w:r></w:p></w:hdr>".toByteArray()
        files["word/fuss.xml"] = "<w:ftr $ns><w:p><w:r><w:t xml:space=\"preserve\">Seite </w:t></w:r><w:fldSimple w:instr=\"PAGE\"><w:r><w:t>1</w:t></w:r></w:fldSimple><w:r><w:t xml:space=\"preserve\"> · Vertraulich</w:t></w:r></w:p></w:ftr>".toByteArray()
        val rels = files["word/_rels/document.xml.rels"]!!.toString(Charsets.UTF_8).replace("</Relationships>",
            "<Relationship Id=\"rIdKopf\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/header\" Target=\"kopf.xml\"/>" +
                "<Relationship Id=\"rIdFuss\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer\" Target=\"fuss.xml\"/></Relationships>")
        files["word/_rels/document.xml.rels"] = rels.toByteArray()
        return zip(files)
    }

    @Test
    fun wordTemplateExtrasAreReadAndSurviveSaving() {
        val bytes = template()
        val md = OfficeImport.docx(bytes)
        assertTrue("Antragsteller: Erika Muster" in md, "Inhaltssteuerelement: $md")
        assertEquals(1, Regex("Im Textfeld").findAll(md).count(), "Textfeld einmal (ohne die Kopie für altes Word): $md")
        assertTrue(DocBlocks.split(md).any { DocMarkup.isTextBox(it) && "Im Textfeld" in it }, md)
        assertTrue("<!--a:;s=subtitle-->Ein Untertitel" in md, md)
        assertTrue("<!--a:;z=1.5;f=-10-->Anderthalbzeilig" in md || "<!--a:;z=1.5;l=10;f=-10-->Anderthalbzeilig" in md, md)
        val setup = DocMarkup.pageSetup(md)
        assertEquals("Professur für Didaktik", setup.header)
        assertEquals("Vertraulich", setup.footer)
        assertEquals("breit", setup.margins)
        // Edited in the app and written back: the text box, the field and header/footer stay as they were, once.
        val now = md.replace("Zweiter Absatz.", "Zweiter Absatz, **geändert**.")
        val merged = assertNotNull(DocxWriter.merge(bytes, md, now), "Zurückschreiben ins Original")
        val files = unzip(merged)
        val doc = files["word/document.xml"]!!.toString(Charsets.UTF_8)
        assertEquals(2, Regex("Im Textfeld").findAll(doc).count(), "Textfeld (Choice + Fallback) unverändert")
        assertTrue("<w:alias w:val=\"Name\"/>" in doc && "geändert" in doc)
        assertTrue("rIdKopf" in doc && files["word/kopf.xml"] != null)
        assertFalse("vorlagen" in doc || "kopfzeile" in doc, "Einstellungszeilen sind kein Text")
    }

    @Test
    fun lorasOwnWordFileKeepsNoStaleHeader() {
        val f = File.createTempFile("eigen", ".docx")
        MarkdownDocx.write(f, "Brief", DocMarkup.withPageSetup("# Brief\n\nText.\n", DocMarkup.PageSetup(date = true, footer = "Fußzeile")))
        val md = OfficeImport.docx(f.readBytes())
        f.delete()
        val s = DocMarkup.pageSetup(md)
        assertEquals("", s.header)
        assertEquals("", s.footer)
    }

    @Test
    fun wordExportWritesFormatvorlagen() {
        val src = Styles.write("# Bericht\n\n<!--a:c;s=title-->Großer Titel\n\n<!--a:;s=subtitle-->Untertitel\n\n<!--a:;z=2-->Doppelt.\n\n<!--a:;s=lit-->Müller (2020).\n", StyleSheet(StyleSet.SCIENCE, explicit = true))
        val f = File.createTempFile("vorlagen", ".docx")
        MarkdownDocx.write(f, "Bericht", src)
        val files = unzip(f.readBytes())
        f.delete()
        val styles = files["word/styles.xml"]!!.toString(Charsets.UTF_8)
        val doc = files["word/document.xml"]!!.toString(Charsets.UTF_8)
        assertTrue("w:ascii=\"Times New Roman\"" in styles && "w:line=\"360\"" in styles, styles)
        assertTrue("w:styleId=\"Title\"" in styles && "w:styleId=\"Bibliography\"" in styles)
        assertTrue("<w:pStyle w:val=\"Title\"/>" in doc && "<w:pStyle w:val=\"Subtitle\"/>" in doc && "<w:pStyle w:val=\"Bibliography\"/>" in doc, doc)
        assertTrue("w:line=\"480\"" in doc, "Zeilenabstand doppelt")
        assertFalse("vorlagen" in doc || "s=title" in doc, doc)
    }

    /** Zwischenablage als RTF (AP-43 c): own RTF comes back with its formats; Word's RTF is read. */
    @Test
    fun rtfClipboardRoundTripAndWord() {
        val r = rich("# Überschrift\n\nEin **fetter**, *kursiver* und <u>unterstrichener</u> Text mit <span style=\"color:#C00000\">Farbe</span> und ä/ß/€.\n\n<!--a:c-->Zentriert.\n")
        val rtf = RichRtf.fromRich(r)
        assertTrue(rtf.startsWith("{\\rtf1") && "\\u8364?" in rtf, rtf)
        val back = RichRtf.toRich(rtf)
        assertTrue("Überschrift" in back.text && "ä/ß/€" in back.text, back.text)
        val bold = back.text.indexOf("fetter")
        assertTrue(back.chars[bold].bold && back.chars[back.text.indexOf("kursiver")].italic && back.chars[back.text.indexOf("unterstrichener")].underline)
        assertEquals("C00000", back.chars[back.text.indexOf("Farbe")].color)
        assertEquals(Align.CENTER, back.paras[back.paraAt(back.text.indexOf("Zentriert"))].align)
        assertEquals(3, back.paras.size, back.text)
        // RTF as Word writes it: font and colour tables, \'xx bytes, \u with a replacement, list text and a field.
        val word = "{\\rtf1\\ansi\\ansicpg1252\\deff0{\\fonttbl{\\f0\\froman Times New Roman;}{\\f1\\fswiss Arial;}}" +
            "{\\colortbl;\\red255\\green0\\blue0;\\red255\\green255\\blue0;}{\\*\\generator Word;}" +
            "\\pard\\qj\\f0\\fs24 Gr\\'fc\\'dfe {\\b fett} und {\\cf1 rot} {\\highlight2 gelb}\\par" +
            "\\pard{\\listtext\\'b7\\tab}{\\f1\\fs28 Arial gro\\u223?} {\\field{\\*\\fldinst HYPERLINK x}{\\fldrslt Link}}\\par}"
        val w = RichRtf.toRich(word)
        assertEquals("Grüße fett und rot gelb\nArial groß Link", w.text)
        assertTrue(w.chars[w.text.indexOf("fett")].bold)
        assertEquals("FF0000", w.chars[w.text.indexOf("rot")].color)
        assertEquals("FFFF00", w.chars[w.text.indexOf("gelb")].highlight)
        assertEquals(Align.JUSTIFY, w.paras[0].align)
        val big = w.chars[w.text.indexOf("Arial")]
        assertEquals("Arial", big.font)
        assertEquals(14, big.size)
    }
}
