package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.docs.DocBlocks
import io.github.thepytales.llmhub.docs.DocMarkup
import io.github.thepytales.llmhub.docs.MarkdownDocx
import io.github.thepytales.llmhub.docs.OfficeImport
import io.github.thepytales.llmhub.docs.text.DocxWriter
import io.github.thepytales.llmhub.docs.text.ParaFormat
import io.github.thepytales.llmhub.docs.text.TabKind
import io.github.thepytales.llmhub.docs.text.TabStop
import io.github.thepytales.llmhub.ui.ALIGN_MARK
import io.github.thepytales.llmhub.ui.FlowState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Prüfsammlung (AP-45, Offene Frage 5: nachgebaute statt echter Dateien): four Word files as the TU templates are built
 * – Brief, Abschlussarbeit, Antrag, Protokoll – written the way Word writes them (foreign author, Formatvorlagen by
 * name, sections with their own header and footer, Tabstopps, footnotes, fields, content controls, numbered lists,
 * tables with a repeated header row). Each one is opened, shown in the Fließtext-Editor, edited in one place and
 * written back into the original; nothing else may change, and what the app knows must come back the same.
 */
class PruefsammlungTest {
    // ── A small Word writer for the fixtures ─────────────────────────────────────────────────────────────

    private val ns = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\""

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** Runs of [text]: `\t` as Word tabs, `**…**` bold. */
    private fun runs(text: String): String = Regex("\\*\\*(.+?)\\*\\*|([^*]+)").findAll(text).joinToString("") { m ->
        val bold = m.groupValues[1].isNotEmpty()
        val t = if (bold) m.groupValues[1] else m.groupValues[2]
        val rPr = if (bold) "<w:rPr><w:b/></w:rPr>" else ""
        t.split('\t').joinToString("<w:r>$rPr<w:tab/></w:r>") { if (it.isEmpty()) "" else "<w:r>$rPr<w:t xml:space=\"preserve\">${esc(it)}</w:t></w:r>" }
    }

    private fun p(text: String, style: String? = null, tabs: List<TabStop> = emptyList(), extra: String = "", sect: String? = null): String {
        val pPr = buildString {
            style?.let { append("<w:pStyle w:val=\"$it\"/>") }
            if (tabs.isNotEmpty()) {
                append("<w:tabs>")
                tabs.forEach { append("<w:tab w:val=\"${when (it.kind) { TabKind.LEFT -> "left"; TabKind.CENTER -> "center"; TabKind.RIGHT -> "right" }}\" w:pos=\"${(it.mm * 56.693f).toInt()}\"/>") }
                append("</w:tabs>")
            }
            append(extra)
            sect?.let { append(it) }
        }
        return "<w:p>${if (pPr.isEmpty()) "" else "<w:pPr>$pPr</w:pPr>"}${runs(text)}</w:p>"
    }

    private fun table(rows: List<List<String>>, repeatHeader: Boolean = true) = buildString {
        append("<w:tbl><w:tblPr><w:tblStyle w:val=\"TableGrid\"/><w:tblW w:w=\"0\" w:type=\"auto\"/></w:tblPr>")
        rows.forEachIndexed { r, row ->
            append("<w:tr>")
            if (r == 0 && repeatHeader) append("<w:trPr><w:tblHeader/></w:trPr>")
            row.forEach { append("<w:tc><w:tcPr><w:tcW w:w=\"2000\" w:type=\"dxa\"/></w:tcPr>${p(it)}</w:tc>") }
            append("</w:tr>")
        }
        append("</w:tbl>")
    }

    private fun sectPr(header: String?, footer: String?, titlePg: Boolean = false, firstHeader: String? = null, type: String? = null) = buildString {
        append("<w:sectPr>")
        header?.let { append("<w:headerReference w:type=\"default\" r:id=\"$it\"/>") }
        firstHeader?.let { append("<w:headerReference w:type=\"first\" r:id=\"$it\"/>") }
        footer?.let { append("<w:footerReference w:type=\"default\" r:id=\"$it\"/>") }
        type?.let { append("<w:type w:val=\"$it\"/>") }
        append("<w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1417\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1417\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/>")
        if (titlePg) append("<w:titlePg/>")
        append("</w:sectPr>")
    }

    private fun hdr(text: String, page: Boolean = false) = "<w:hdr $ns><w:p>${runs(text)}" +
        (if (page) "<w:r><w:t xml:space=\"preserve\"> – Seite </w:t></w:r><w:fldSimple w:instr=\"PAGE\"><w:r><w:t>2</w:t></w:r></w:fldSimple>" else "") + "</w:p></w:hdr>"

    private fun ftr(text: String) = "<w:ftr $ns><w:p>${runs(text)}</w:p></w:ftr>"

    private val styles = "<w:styles $ns>" + listOf(
        "Normal" to "Normal", "Title" to "Title", "Subtitle" to "Subtitle", "Heading1" to "heading 1", "Heading2" to "heading 2",
        "Caption" to "caption", "Literatur" to "Literaturverzeichnis", "ListParagraph" to "List Paragraph",
    ).joinToString("") { (id, name) -> "<w:style w:type=\"paragraph\" w:styleId=\"$id\"><w:name w:val=\"$name\"/></w:style>" } + "</w:styles>"

    private val numbering = "<w:numbering $ns><w:abstractNum w:abstractNumId=\"0\"><w:lvl w:ilvl=\"0\"><w:start w:val=\"1\"/><w:numFmt w:val=\"decimal\"/><w:lvlText w:val=\"%1.\"/></w:lvl></w:abstractNum>" +
        "<w:abstractNum w:abstractNumId=\"1\"><w:lvl w:ilvl=\"0\"><w:numFmt w:val=\"bullet\"/><w:lvlText w:val=\"•\"/></w:lvl></w:abstractNum>" +
        "<w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num><w:num w:numId=\"2\"><w:abstractNumId w:val=\"1\"/></w:num></w:numbering>"

    private fun numbered(text: String, bullet: Boolean = false) = p(text, "ListParagraph", extra = "<w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"${if (bullet) 2 else 1}\"/></w:numPr>")

    /** A footnote reference run (the note text goes into footnotes.xml). */
    private fun noteRef(id: Int) = "<w:r><w:rPr><w:rStyle w:val=\"FootnoteReference\"/></w:rPr><w:footnoteReference w:id=\"$id\"/></w:r>"

    /** The package of a Word file: [body] with [parts] (header/footer by relationship id → XML) and [notes]. */
    private fun docx(body: String, parts: Map<String, String>, notes: Map<Int, String> = emptyMap(), evenOdd: Boolean = false): ByteArray {
        val files = LinkedHashMap<String, ByteArray>()
        fun put(n: String, s: String) { files[n] = s.toByteArray(Charsets.UTF_8) }
        val types = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        types.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        types.append("<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>")
        val rels = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        rels.append("<Relationship Id=\"rIdStyles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>")
        rels.append("<Relationship Id=\"rIdNum\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering\" Target=\"numbering.xml\"/>")
        rels.append("<Relationship Id=\"rIdSettings\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings\" Target=\"settings.xml\"/>")
        for ((id, xml) in parts) {
            val kind = if (xml.startsWith("<w:hdr")) "header" else "footer"
            put("word/$id.xml", xml)
            rels.append("<Relationship Id=\"$id\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/$kind\" Target=\"$id.xml\"/>")
            types.append("<Override PartName=\"/word/$id.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.$kind+xml\"/>")
        }
        if (notes.isNotEmpty()) {
            put("word/footnotes.xml", "<w:footnotes $ns><w:footnote w:type=\"separator\" w:id=\"-1\"><w:p><w:r><w:separator/></w:r></w:p></w:footnote>" +
                notes.entries.joinToString("") { (id, t) -> "<w:footnote w:id=\"$id\"><w:p><w:r><w:footnoteRef/></w:r><w:r><w:t xml:space=\"preserve\"> ${esc(t)}</w:t></w:r></w:p></w:footnote>" } + "</w:footnotes>")
            rels.append("<Relationship Id=\"rIdNotes\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/footnotes\" Target=\"footnotes.xml\"/>")
            types.append("<Override PartName=\"/word/footnotes.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml\"/>")
        }
        rels.append("</Relationships>")
        types.append("</Types>")
        put("[Content_Types].xml", types.toString())
        put("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/></Relationships>")
        put("docProps/core.xml", "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:creator>Sekretariat</dc:creator></cp:coreProperties>")
        put("word/_rels/document.xml.rels", rels.toString())
        put("word/styles.xml", styles)
        put("word/numbering.xml", numbering)
        put("word/settings.xml", "<w:settings $ns>${if (evenOdd) "<w:evenAndOddHeaders/>" else ""}</w:settings>")
        put("word/document.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document $ns><w:body>$body</w:body></w:document>")
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> files.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return out.toByteArray()
    }

    private fun unzip(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val files = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z -> generateSequence { z.nextEntry }.forEach { files[it.name] = z.readBytes() } }
        return files
    }

    // ── The four documents ───────────────────────────────────────────────────────────────────────────────

    private val bezug = listOf(TabStop(45), TabStop(90), TabStop(165, TabKind.RIGHT))

    /** TUD-Briefvorlage: Deckblatt-Kopf (Logo-Zeile), Folgeseiten mit Seitenzahl, Bezugszeichen an Tabstopps. */
    private fun brief() = docx(
        p("Technische Universität Dresden · 01062 Dresden", extra = "<w:spacing w:after=\"0\"/>") +
            p("Frau") + p("Erika Musterfrau") + p("Musterstraße 1") + p("01069 Dresden") +
            p("Ihr Zeichen\tUnser Zeichen\tBearbeiterin\tDresden,", tabs = bezug) +
            p("AZ 12/3\tPD-4711\tA. Beispiel\t7. November 2026", tabs = bezug) +
            p("**Ihr Antrag auf Verlängerung**") +
            p("Sehr geehrte Frau Musterfrau,") +
            p("vielen Dank für Ihren Antrag vom 1. November. Wir haben ihn geprüft und freuen uns, Ihnen mitteilen zu können, dass die Verlängerung genehmigt ist.") +
            p("Mit freundlichen Grüßen") + p("Dr. Anna Beispiel") +
            sectPr("kopfFolge", "fussBrief", titlePg = true, firstHeader = "kopfErste"),
        mapOf("kopfErste" to hdr("TU Dresden – Fakultät Erziehungswissenschaften"), "kopfFolge" to hdr("Brief an E. Musterfrau", page = true), "fussBrief" to ftr("Besucheradresse: Weberplatz 5, 01217 Dresden")),
    )

    /** Abschlussarbeit: Titelblatt, Abschnitte mit eigener Kopfzeile, Überschriften, Fußnoten, lange Tabelle, Literatur. */
    private fun arbeit() = docx(
        p("Lernen mit digitalen Medien", "Title") + p("Masterarbeit an der Professur für Didaktik", "Subtitle") +
            p("vorgelegt von Max Muster, Dresden 2026", sect = sectPr(null, "fussTitel")) +
            p("Einleitung", "Heading1") +
            "<w:p><w:r><w:t xml:space=\"preserve\">Digitale Medien verändern das Lernen.</w:t></w:r>${noteRef(1)}<w:r><w:t xml:space=\"preserve\"> Diese Arbeit untersucht das.</w:t></w:r></w:p>" +
            p("Forschungsfrage", "Heading2") + p("Wie wirkt sich der Einsatz aus?") +
            p("Methode", "Heading1") + p("Es wurden 40 Lernende befragt.") +
            table(listOf(listOf("Nr.", "Gruppe", "Wert")) + (1..40).map { listOf("$it", if (it % 2 == 0) "A" else "B", "${it * 2}") }) +
            p("Tabelle 1: Ergebnisse der Befragung", "Caption", sect = sectPr("kopfHaupt", "fussHaupt")) +
            p("Literatur", "Heading1") + p("Muster, M. (2024). Lernen. Dresden: TUD Press.", "Literatur") +
            sectPr("kopfAnhang", "fussHaupt"),
        mapOf("fussTitel" to ftr("Titelblatt"), "kopfHaupt" to hdr("Lernen mit digitalen Medien"), "fussHaupt" to ftr("Masterarbeit Max Muster"), "kopfAnhang" to hdr("Literatur und Anhang")),
        notes = mapOf(1 to "Vgl. Muster 2024, S. 3."),
    )

    /** Antrag: Felder in Inhaltssteuerelementen, Ankreuzfelder, Tabelle ohne Wiederholung, Aufzählung. */
    private fun antrag() = docx(
        p("Antrag auf Mittel aus dem Lehrfonds", "Title") +
            "<w:sdt><w:sdtPr><w:alias w:val=\"Antragsteller\"/></w:sdtPr><w:sdtContent>${p("Antragsteller: Prof. Dr. Erika Muster")}</w:sdtContent></w:sdt>" +
            "<w:sdt><w:sdtPr><w:alias w:val=\"Einrichtung\"/></w:sdtPr><w:sdtContent>${p("Einrichtung: Professur für Didaktik")}</w:sdtContent></w:sdt>" +
            p("Art der Maßnahme", "Heading1") + p("☒ Neue Lehrveranstaltung\t☐ Überarbeitung", tabs = listOf(TabStop(80))) +
            p("Kosten", "Heading1") + table(listOf(listOf("Posten", "Betrag"), listOf("Hilfskraft", "3.000 €"), listOf("Material", "500 €")), repeatHeader = false) +
            p("Begründung", "Heading1") + numbered("Mehr Studierende erreichen", bullet = true) + numbered("Digitale Formate erproben", bullet = true) +
            p("Ort, Datum\tUnterschrift", tabs = listOf(TabStop(100))) +
            sectPr("kopfAntrag", "fussAntrag"),
        mapOf("kopfAntrag" to hdr("Lehrfonds 2027"), "fussAntrag" to ftr("Bitte bis 31.01. einreichen")),
    )

    /** Protokoll: Kopfdaten an Tabstopps, Teilnehmertabelle, nummerierte Tagesordnung mit Zeiten am rechten Rand. */
    private fun protokoll() = docx(
        p("Protokoll der Institutsratssitzung", "Title") +
            p("Datum:\t5. November 2026", tabs = listOf(TabStop(35))) + p("Ort:\tWeberplatz 5, Raum 21", tabs = listOf(TabStop(35))) +
            p("Protokoll:\tA. Beispiel", tabs = listOf(TabStop(35))) +
            table(listOf(listOf("Name", "Funktion", "anwesend"), listOf("Prof. Muster", "Leitung", "ja"), listOf("Dr. Beispiel", "Protokoll", "ja"), listOf("M. Muster", "Studierende", "nein"))) +
            p("Tagesordnung", "Heading1") +
            numbered("Begrüßung\t10:00") + numbered("Bericht der Leitung\t10:15") + numbered("Verschiedenes\t11:30") +
            p("Beschlüsse", "Heading1") + p("**Beschluss 1:** Der Lehrfonds wird um 10 % erhöht. (einstimmig)") +
            sectPr("kopfProt", "fussProt"),
        mapOf("kopfProt" to hdr("Institut für Berufspädagogik", page = true), "fussProt" to ftr("intern")),
        evenOdd = false,
    )

    // ── Checks ───────────────────────────────────────────────────────────────────────────────────────────

    /** Opens [bytes], edits [old] → [new] in the editor's Markdown, writes back into the original; returns all three. */
    private fun roundTrip(bytes: ByteArray, old: String, new: String): Triple<String, ByteArray, String> {
        val md = OfficeImport.docx(bytes)
        // The Fließtext-Editor takes it as it is (no change just by opening it).
        assertEquals(DocBlocks.join(DocBlocks.split(md)).trim(), FlowState(md).markdown().trim(), "Editor ändert beim Öffnen nichts")
        assertTrue(old in md, "„$old“ fehlt:\n$md")
        val edited = md.replace(old, new)
        val merged = assertNotNull(DocxWriter.merge(bytes, md, edited), "ins Original zurückschreiben")
        return Triple(md, merged, OfficeImport.docx(merged))
    }

    private fun stops(md: String, text: String): List<TabStop> =
        ParaFormat.fromMarker(ParaFormat(), ALIGN_MARK.find(md.lines().first { text in it })!!).tabs

    /** Everything but [changed]: the same parts byte for byte, document.xml the same but for the edited paragraph. */
    private fun untouched(before: ByteArray, after: ByteArray, edited: String) {
        val a = unzip(before)
        val b = unzip(after)
        assertEquals(a.keys, b.keys, "dieselben Teile")
        for ((name, bytes) in a) if (name != "word/document.xml") assertTrue(bytes.contentEquals(b[name]!!), "$name unverändert")
        val da = a["word/document.xml"]!!.toString(Charsets.UTF_8)
        val db = b["word/document.xml"]!!.toString(Charsets.UTF_8)
        assertEquals(Regex("<w:sectPr").findAll(da).count(), Regex("<w:sectPr").findAll(db).count(), "Abschnitte bleiben")
        assertEquals(Regex("<w:tab ").findAll(da).count(), Regex("<w:tab ").findAll(db).count(), "Tabstopps bleiben")
        assertEquals(Regex("<w:tbl>").findAll(da).count(), Regex("<w:tbl>").findAll(db).count(), "Tabellen bleiben")
        assertEquals(Regex("<w:sdt>").findAll(da).count(), Regex("<w:sdt>").findAll(db).count(), "Inhaltssteuerelemente bleiben")
        assertTrue(edited in db, "Änderung angekommen")
    }

    @Test
    fun briefOpensAndComesBackUnchangedButForTheEdit() {
        val bytes = brief()
        val (md, merged, back) = roundTrip(bytes, "freuen uns", "freuen uns sehr")
        val setup = DocMarkup.pageSetup(md)
        assertTrue(setup.titlePage, "erste Seite anders")
        assertEquals("TU Dresden – Fakultät Erziehungswissenschaften", setup.headerFirst)
        // „– Seite 2“ is the page number field, not text of the header.
        assertTrue(setup.header.startsWith("Brief an E. Musterfrau") && "Seite" !in setup.header, setup.header)
        assertEquals("Besucheradresse: Weberplatz 5, 01217 Dresden", setup.footer)
        assertTrue("AZ 12/3\tPD-4711\tA. Beispiel\t7. November 2026" in md, md)
        assertEquals(bezug, stops(md, "AZ 12/3"))
        untouched(bytes, merged, "freuen uns sehr")
        assertTrue("freuen uns sehr" in back && "Mit freundlichen Grüßen" in back)
    }

    @Test
    fun abschlussarbeitKeepsSectionsNotesAndItsLongTable() {
        val bytes = arbeit()
        val (md, merged, back) = roundTrip(bytes, "untersucht das", "untersucht das genauer")
        // Sections: the title page is the document's own, the main part and the appendix have their own headers.
        val secs = DocMarkup.sections(md)
        assertEquals(2, secs.size, md)
        assertEquals("Lernen mit digitalen Medien", secs[0].header)
        assertEquals("Masterarbeit Max Muster", secs[0].footer)
        assertEquals("Literatur und Anhang", secs[1].header)
        assertEquals("Titelblatt", DocMarkup.pageSetup(md).footer)
        assertTrue("[^1]" in md && "Vgl. Muster 2024, S. 3." in md, "Fußnote")
        assertTrue("<!--a:;s=subtitle-->Masterarbeit an der Professur" in md, md)
        val rows = DocBlocks.tableRows(DocBlocks.split(md).first { it.trim().startsWith("|") })
        assertEquals(41, rows.size)
        assertTrue("<!--a:;s=lit-->Muster, M. (2024)" in md || "s=lit" in md.lines().first { "Muster, M." in it }, md)
        untouched(bytes, merged, "untersucht das genauer")
        assertEquals(md.replace("untersucht das", "untersucht das genauer"), back, "nach dem Speichern liest sich die Datei wie bearbeitet")
        // Written anew instead (a new section break): the sections come out again with their headers.
        val f = File.createTempFile("arbeit", ".docx")
        MarkdownDocx.write(f, "Arbeit", md)
        val docXml = unzip(f.readBytes())["word/document.xml"]!!.toString(Charsets.UTF_8)
        f.delete()
        assertEquals(3, Regex("<w:sectPr>").findAll(docXml).count())
        assertTrue("<w:tblHeader/>" in docXml, "Kopfzeile der Tabelle wiederholt")
    }

    @Test
    fun antragKeepsFieldsAndCheckboxes() {
        val bytes = antrag()
        val (md, merged, back) = roundTrip(bytes, "Digitale Formate erproben", "Digitale Formate erproben und auswerten")
        assertTrue("Antragsteller: Prof. Dr. Erika Muster" in md && "Einrichtung: Professur für Didaktik" in md, md)
        assertTrue("☒ Neue Lehrveranstaltung\t☐ Überarbeitung" in md)
        assertEquals(listOf(TabStop(80)), stops(md, "Neue Lehrveranstaltung"))
        assertTrue("- Mehr Studierende erreichen" in md, md)
        assertEquals("Lehrfonds 2027", DocMarkup.pageSetup(md).header)
        untouched(bytes, merged, "und auswerten")
        assertTrue("Digitale Formate erproben und auswerten" in back)
        assertTrue("<w:alias w:val=\"Antragsteller\"/>" in unzip(merged)["word/document.xml"]!!.toString(Charsets.UTF_8))
    }

    @Test
    fun protokollKeepsTabsTablesAndNumbers() {
        val bytes = protokoll()
        val (md, merged, back) = roundTrip(bytes, "um 10 % erhöht", "um 12 % erhöht")
        assertTrue("Datum:\t5. November 2026" in md, md)
        assertEquals(listOf(TabStop(35)), stops(md, "Datum:"))
        assertTrue(Regex("1\\. Begrüßung\\t10:00").containsMatchIn(md), md)
        assertTrue("**Beschluss 1:**" in md)
        DocMarkup.pageSetup(md).header.let { assertTrue(it.startsWith("Institut für Berufspädagogik") && "Seite" !in it, it) }
        untouched(bytes, merged, "um 12 % erhöht")
        assertTrue("um 12 % erhöht" in back)
    }

    @Test
    fun allFourShowInThePageView() {
        // Every document of the collection opens in the editor as running text and blocks, nothing lost on the way.
        for ((name, bytes) in listOf("Brief" to brief(), "Arbeit" to arbeit(), "Antrag" to antrag(), "Protokoll" to protokoll())) {
            val md = OfficeImport.docx(bytes)
            val st = FlowState(md)
            val text = st.segs.filterIsInstance<io.github.thepytales.llmhub.ui.FlowSeg.Text>().joinToString("\n") { it.rich.text }
            val visible = md.replace(Regex("<!--.*?-->"), "").lines().filter { !it.trim().startsWith("|") && !it.startsWith("[^") }.joinToString("\n")
            val words = Regex("[A-Za-zÄÖÜäöüß]{5,}").findAll(DocBlocks.plainOf(visible)).map { it.value }.toSet()
            val missing = words.filter { it !in text && it !in md.lines().filter { l -> l.trim().startsWith("|") || l.startsWith("[^") }.joinToString(" ") }
            assertTrue(missing.isEmpty(), "$name: fehlt im Editor: ${missing.take(10)}")
        }
    }
}
