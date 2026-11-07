package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.ui.Block
import io.github.thepytales.llmhub.ui.Segment
import io.github.thepytales.llmhub.ui.inlineSegments
import io.github.thepytales.llmhub.ui.parseBlocks
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes Markdown as a minimal Word document (Office Open XML), without external libraries: headings, lists,
 * tables, real footnotes, pictures, page breaks, landscape sections and header/footer with "Seite X von Y"
 * (see [DocMarkup]).
 */
object MarkdownDocx {
    private const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val TEXT_WIDTH = 9638 // A4 minus 2 cm margins, in twentieths of a point
    private const val TEXT_WIDTH_LANDSCAPE = 14570 // A4 landscape minus 2 cm margins
    private const val TEXT_HEIGHT = 14570
    private const val TEXT_HEIGHT_LANDSCAPE = 9638

    private const val R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val CT = "application/vnd.openxmlformats-officedocument.wordprocessingml"

    /** A picture of the document in word/media. */
    private class Media(val name: String, val rel: String, val bytes: ByteArray)

    /** What one export collects while writing the body: footnote numbers, pictures, the current page orientation. */
    private class Ctx(val notes: List<DocMarkup.Note>, val header: Boolean, val footer: Boolean) {
        val numbers = notes.associate { it.label to it.number }
        val media = mutableListOf<Media>()
        var landscape = false
        var drawingId = 10
        /** Headings for an Inhaltsverzeichnis (AP-16). */
        var headings: List<Pair<Int, String>> = emptyList()
        /** Page margins in twips (AP-16). */
        var margin = 1134
        /** Formatvorlagen the document chose (AP-46); null: the look of the export style. */
        var sheet: io.github.thepytales.llmhub.docs.text.StyleSheet? = null
        /** Erste Seite anders / gerade Seiten anders (AP-45): their own header and footer parts. */
        var first = false
        var even = false
        /** Sections written so far (only the first one has the Deckblatt). */
        var sections = 0
        val settings: Boolean get() = notes.isNotEmpty() || even
        val width: Int get() = if (landscape) TEXT_WIDTH_LANDSCAPE else TEXT_WIDTH
        val height: Int get() = if (landscape) TEXT_HEIGHT_LANDSCAPE else TEXT_HEIGHT
    }

    private val current = ThreadLocal<Ctx?>()
    private val ctx: Ctx get() = current.get() ?: Ctx(emptyList(), header = false, footer = true)

    private fun contentTypes(c: Ctx) = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append("<Default Extension=\"png\" ContentType=\"image/png\"/>")
        c.media.map { it.name.substringAfterLast('.') }.filter { it != "png" }.distinct().forEach { ext ->
            append("<Default Extension=\"$ext\" ContentType=\"image/$ext\"/>")
        }
        append("<Override PartName=\"/word/document.xml\" ContentType=\"$CT.document.main+xml\"/>")
        for (n in parts(c)) {
            if (c.footer) append("<Override PartName=\"/word/footer$n.xml\" ContentType=\"$CT.footer+xml\"/>")
            if (c.header) append("<Override PartName=\"/word/header$n.xml\" ContentType=\"$CT.header+xml\"/>")
        }
        if (c.notes.isNotEmpty()) append("<Override PartName=\"/word/footnotes.xml\" ContentType=\"$CT.footnotes+xml\"/>")
        if (c.settings) append("<Override PartName=\"/word/settings.xml\" ContentType=\"$CT.settings+xml\"/>")
        append("<Override PartName=\"/word/styles.xml\" ContentType=\"$CT.styles+xml\"/>")
        append("<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>")
        append("</Types>")
    }

    private fun docRels(withLogo: Boolean, c: Ctx) = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        for (n in parts(c)) {
            val id = if (n == 1) "" else n.toString()
            if (c.footer) append("<Relationship Id=\"rIdFooter$id\" Type=\"$R_NS/footer\" Target=\"footer$n.xml\"/>")
            if (c.header) append("<Relationship Id=\"rIdHeader$id\" Type=\"$R_NS/header\" Target=\"header$n.xml\"/>")
        }
        if (c.notes.isNotEmpty()) append("<Relationship Id=\"rIdFootnotes\" Type=\"$R_NS/footnotes\" Target=\"footnotes.xml\"/>")
        if (c.settings) append("<Relationship Id=\"rIdSettings\" Type=\"$R_NS/settings\" Target=\"settings.xml\"/>")
        append("<Relationship Id=\"rIdStyles\" Type=\"$R_NS/styles\" Target=\"styles.xml\"/>")
        if (withLogo) append("<Relationship Id=\"rIdLogo\" Type=\"$R_NS/image\" Target=\"media/logo.png\"/>")
        c.media.forEach { append("<Relationship Id=\"${it.rel}\" Type=\"$R_NS/image\" Target=\"${it.name}\"/>") }
        append("</Relationships>")
    }

    private const val RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
</Relationships>"""

    /**
     * Accessibility: German as document language (spell check, screen readers) and real heading styles, so the
     * structure shows in the navigation pane and screen readers can jump between headings.
     */
    private fun styles(themeFont: String) = buildString {
        // Formatvorlagen of the document (AP-46) as Word styles, so „Überschrift 1 ändern“ in Word works on them too.
        val sheet = ctx.sheet
        val body = sheet?.of(io.github.thepytales.llmhub.docs.text.ParaKind.NORMAL)
        val font = body?.font ?: themeFont
        val bodySize = body?.let { (it.size * 2).toInt() } ?: 21
        val line = body?.let { (it.spacing * 240).toInt() } ?: 276
        val after = body?.let { it.after * 20 } ?: 120
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<w:styles xmlns:w=\"$W_NS\">")
        append("<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"$font\" w:hAnsi=\"$font\" w:cs=\"$font\"/>")
        append("<w:sz w:val=\"$bodySize\"/><w:lang w:val=\"de-DE\" w:eastAsia=\"de-DE\" w:bidi=\"ar-SA\"/></w:rPr></w:rPrDefault>")
        append("<w:pPrDefault><w:pPr><w:spacing w:after=\"$after\" w:line=\"$line\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>")
        append("<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Standard\"><w:name w:val=\"Normal\"/><w:qFormat/></w:style>")
        fun rPr(kind: io.github.thepytales.llmhub.docs.text.ParaKind, fallback: String): String {
            val d = sheet?.of(kind) ?: return fallback
            return buildString {
                d.font?.let { append("<w:rFonts w:ascii=\"${esc(it)}\" w:hAnsi=\"${esc(it)}\" w:cs=\"${esc(it)}\"/>") }
                if (d.bold) append("<w:b/>")
                if (d.italic) append("<w:i/>")
                d.color?.let { append("<w:color w:val=\"$it\"/>") }
                append("<w:sz w:val=\"${(d.size * 2).toInt()}\"/>")
            }
        }
        fun spacing(kind: io.github.thepytales.llmhub.docs.text.ParaKind): String {
            val d = sheet?.of(kind) ?: return ""
            return "<w:spacing w:before=\"${d.before * 20}\" w:after=\"${d.after * 20}\" w:line=\"${(d.spacing * 240).toInt()}\" w:lineRule=\"auto\"/>"
        }
        for (level in 1..4) {
            val kind = io.github.thepytales.llmhub.docs.text.ParaKind.heading(level)
            append("<w:style w:type=\"paragraph\" w:styleId=\"Heading$level\"><w:name w:val=\"heading $level\"/>")
            append("<w:basedOn w:val=\"Standard\"/><w:next w:val=\"Standard\"/><w:uiPriority w:val=\"9\"/><w:qFormat/>")
            append("<w:pPr><w:keepNext/>${spacing(kind)}<w:outlineLvl w:val=\"${level - 1}\"/></w:pPr><w:rPr>${rPr(kind, "<w:b/>")}</w:rPr></w:style>")
        }
        // Titel, Untertitel and Literatur (AP-46).
        append("<w:style w:type=\"paragraph\" w:styleId=\"Title\"><w:name w:val=\"Title\"/><w:basedOn w:val=\"Standard\"/><w:next w:val=\"Standard\"/><w:qFormat/>")
        append("<w:pPr>${spacing(io.github.thepytales.llmhub.docs.text.ParaKind.TITLE)}</w:pPr><w:rPr>${rPr(io.github.thepytales.llmhub.docs.text.ParaKind.TITLE, "<w:sz w:val=\"52\"/>")}</w:rPr></w:style>")
        append("<w:style w:type=\"paragraph\" w:styleId=\"Subtitle\"><w:name w:val=\"Subtitle\"/><w:basedOn w:val=\"Standard\"/><w:next w:val=\"Standard\"/><w:qFormat/>")
        append("<w:pPr>${spacing(io.github.thepytales.llmhub.docs.text.ParaKind.SUBTITLE)}</w:pPr><w:rPr>${rPr(io.github.thepytales.llmhub.docs.text.ParaKind.SUBTITLE, "<w:color w:val=\"666666\"/><w:sz w:val=\"30\"/>")}</w:rPr></w:style>")
        append("<w:style w:type=\"paragraph\" w:styleId=\"Bibliography\"><w:name w:val=\"Bibliography\"/><w:basedOn w:val=\"Standard\"/><w:qFormat/>")
        append("<w:pPr>${spacing(io.github.thepytales.llmhub.docs.text.ParaKind.LITERATURE)}<w:ind w:left=\"567\" w:hanging=\"567\"/></w:pPr><w:rPr>${rPr(io.github.thepytales.llmhub.docs.text.ParaKind.LITERATURE, "")}</w:rPr></w:style>")
        append("<w:style w:type=\"paragraph\" w:styleId=\"FootnoteText\"><w:name w:val=\"footnote text\"/><w:basedOn w:val=\"Standard\"/>")
        append("<w:pPr><w:spacing w:after=\"40\" w:line=\"240\" w:lineRule=\"auto\"/></w:pPr><w:rPr><w:sz w:val=\"18\"/></w:rPr></w:style>")
        append("<w:style w:type=\"character\" w:styleId=\"FootnoteReference\"><w:name w:val=\"footnote reference\"/><w:rPr><w:vertAlign w:val=\"superscript\"/></w:rPr></w:style>")
        append("<w:style w:type=\"paragraph\" w:styleId=\"Caption\"><w:name w:val=\"caption\"/><w:basedOn w:val=\"Standard\"/><w:qFormat/>")
        append("<w:pPr><w:jc w:val=\"center\"/><w:spacing w:after=\"200\"/></w:pPr><w:rPr><w:i/><w:color w:val=\"555555\"/><w:sz w:val=\"18\"/></w:rPr></w:style>")
        append("</w:styles>")
    }

    private fun core(title: String): String {
        val now = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" " +
            "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><dc:title>${esc(title)}</dc:title>" +
            "<dc:language>de-DE</dc:language><dc:creator>Lora</dc:creator>" +
            "<dcterms:created xsi:type=\"dcterms:W3CDTF\">$now</dcterms:created><dcterms:modified xsi:type=\"dcterms:W3CDTF\">$now</dcterms:modified>" +
            "</cp:coreProperties>"
    }

    fun write(file: File, title: String, markdown: String, theme: DocTheme = DocTheme()) {
        val notes = DocMarkup.footnotes(DocMarkup.isolate(markdown))
        writeWith(file, title, markdown, notes.notes, theme) { withLogo -> document(title, notes.body, theme, withLogo) }
    }

    /**
     * Word with tracked changes: the text of [newMarkdown], with what changed since [oldMarkdown] as revisions
     * (inserted / deleted, by [author]) that can be accepted or rejected in Word.
     */
    fun writeTracked(file: File, title: String, oldMarkdown: String, newMarkdown: String, theme: DocTheme = DocTheme(), author: String = "ADEU LLM") {
        val notes = DocMarkup.footnotes(DocMarkup.isolate(newMarkdown))
        val old = DocMarkup.footnotes(DocMarkup.isolate(oldMarkdown)).body
        writeWith(file, title, newMarkdown, notes.notes, theme) { withLogo -> trackedDocument(old, notes.body, theme, withLogo, author) }
    }

    /**
     * The body XML of [markdown] – paragraphs and tables, without section properties – to put into an existing Word file
     * (Word-Treue, AP-45). Null when the piece needs parts of the package (pictures, footnotes, landscape sections).
     */
    internal fun fragment(markdown: String, theme: DocTheme = DocTheme()): String? {
        if ("![" in markdown || Regex("\\[\\^[^\\]]+]").containsMatchIn(markdown) || markdown.lineSequence().any { DocMarkup.orientation(it) != null }) return null
        val c = Ctx(emptyList(), header = false, footer = false)
        c.headings = DocMarkup.tocEntries(markdown)
        current.set(c)
        try {
            val xml = buildString { for (b in parseBlocks(markdown)) block(b, theme) }
            return if (c.media.isNotEmpty()) null else xml
        } finally {
            current.remove()
        }
    }

    private fun writeWith(file: File, title: String, markdown: String, notes: List<DocMarkup.Note>, theme: DocTheme, body: (Boolean) -> String) {
        val logo = theme.logo?.takeIf { theme.logoSize != null }
        val setup = DocMarkup.pageSetup(markdown)
        val c = Ctx(
            notes,
            header = setup.hasHeader || setup.headerEven.isNotBlank() || setup.titlePage && setup.headerFirst.isNotBlank(),
            footer = setup.pageNumbers || theme.footer.isNotBlank() || setup.footer.isNotBlank() || setup.footerEven.isNotBlank() || setup.titlePage && setup.footerFirst.isNotBlank(),
        )
        c.margin = setup.marginTwips
        c.first = setup.titlePage
        c.even = setup.hasEven
        c.headings = DocMarkup.tocEntries(markdown)
        c.sheet = io.github.thepytales.llmhub.docs.text.Styles.read(markdown).takeIf { it.explicit || it.changed.isNotEmpty() }
        current.set(c)
        try {
            // The body first: it collects the pictures that the package lists.
            val document = body(logo != null)
            val footnotes = if (notes.isEmpty()) null else footnotesXml(notes)
            ZipOutputStream(file.outputStream()).use { zip ->
                fun bytes(name: String, content: ByteArray) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content)
                    zip.closeEntry()
                }
                fun entry(name: String, content: String) = bytes(name, content.toByteArray(Charsets.UTF_8))
                entry("[Content_Types].xml", contentTypes(c))
                entry("_rels/.rels", RELS)
                entry("word/_rels/document.xml.rels", docRels(logo != null, c))
                if (c.footer) entry("word/footer1.xml", footer(setup.footer, theme.footer, setup.pageNumbers))
                if (c.header) entry("word/header1.xml", header(setup.header, setup.date))
                // Deckblatt: own header and footer of the first page (no page number); even pages: theirs.
                if (c.first && c.footer) entry("word/footer2.xml", footer(setup.footerFirst, "", false))
                if (c.first && c.header) entry("word/header2.xml", header(setup.headerFirst, false))
                if (c.even && c.footer) entry("word/footer3.xml", footer(setup.footerEven, theme.footer, setup.pageNumbers))
                if (c.even && c.header) entry("word/header3.xml", header(setup.headerEven, setup.date))
                if (footnotes != null) entry("word/footnotes.xml", footnotes)
                if (c.settings) entry("word/settings.xml", settings(c))
                entry("word/styles.xml", styles(if (theme.style == DocStyle.TUD) "Noto Sans" else if (theme.style == DocStyle.CLASSIC) "Georgia" else "Calibri"))
                entry("docProps/core.xml", core(title.ifBlank { markdown.lineSequence().firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim() ?: "Dokument" }))
                if (logo != null) bytes("word/media/logo.png", logo)
                c.media.forEach { bytes("word/${it.name}", it.bytes) }
                entry("word/document.xml", document)
            }
        } finally {
            current.remove()
        }
    }

    /** Numbers of the header/footer parts: 1 for all pages, 2 for the first, 3 for even pages. */
    private fun parts(c: Ctx) = listOfNotNull(1, 2.takeIf { c.first }, 3.takeIf { c.even })

    /** Word links the separator lines of the footnote area from the settings; even pages get their own headers there. */
    private fun settings(c: Ctx) = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<w:settings xmlns:w=\"$W_NS\">" + (if (c.even) "<w:evenAndOddHeaders/>" else "") +
        (if (c.notes.isNotEmpty()) "<w:footnotePr><w:footnote w:id=\"-1\"/><w:footnote w:id=\"0\"/></w:footnotePr>" else "") + "</w:settings>"

    private fun footnotesXml(notes: List<DocMarkup.Note>) = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<w:footnotes xmlns:w=\"$W_NS\" xmlns:r=\"$R_NS\">")
        append("<w:footnote w:type=\"separator\" w:id=\"-1\"><w:p><w:pPr><w:spacing w:after=\"0\" w:line=\"240\" w:lineRule=\"auto\"/></w:pPr><w:r><w:separator/></w:r></w:p></w:footnote>")
        append("<w:footnote w:type=\"continuationSeparator\" w:id=\"0\"><w:p><w:pPr><w:spacing w:after=\"0\" w:line=\"240\" w:lineRule=\"auto\"/></w:pPr><w:r><w:continuationSeparator/></w:r></w:p></w:footnote>")
        for (n in notes) {
            append("<w:footnote w:id=\"${n.number}\"><w:p><w:pPr><w:pStyle w:val=\"FootnoteText\"/></w:pPr>")
            append("<w:r><w:rPr><w:rStyle w:val=\"FootnoteReference\"/></w:rPr><w:footnoteRef/></w:r>")
            append("<w:r><w:t xml:space=\"preserve\"> </w:t></w:r>")
            append(inlineSegments(n.text).joinToString("") { segRun(it, RunStyle(size = 18)) })
            append("</w:p></w:footnote>")
        }
        append("</w:footnotes>")
    }

    private fun field(instr: String, shown: String, rPr: String) =
        "<w:r>$rPr<w:fldChar w:fldCharType=\"begin\"/></w:r>" +
            "<w:r>$rPr<w:instrText xml:space=\"preserve\"> $instr </w:instrText></w:r>" +
            "<w:r>$rPr<w:fldChar w:fldCharType=\"separate\"/></w:r>" +
            "<w:r>$rPr<w:t>$shown</w:t></w:r>" +
            "<w:r>$rPr<w:fldChar w:fldCharType=\"end\"/></w:r>"

    /** Text of a header or footer with its fields `{seite}`/`{seiten}` as PAGE / NUMPAGES fields. */
    private fun fieldRuns(text: String, rPr: String): String = DocMarkup.fieldPieces(text).joinToString("") { (isField, t) ->
        if (isField) field(if (t == "seite") "PAGE" else "NUMPAGES", "1", rPr) else "<w:r>$rPr<w:t xml:space=\"preserve\">${esc(t)}</w:t></w:r>"
    }

    /** Footer line ([own] and the theme's [org] line) and "Seite X von Y" (PAGE / NUMPAGES fields, updated by Word). */
    private fun footer(own: String, org: String, numbers: Boolean): String {
        val rPr = "<w:rPr><w:color w:val=\"888888\"/><w:sz w:val=\"16\"/></w:rPr>"
        fun t(s: String) = "<w:r>$rPr<w:t xml:space=\"preserve\">${esc(s)}</w:t></w:r>"
        val content = buildString {
            val line = listOf(own, org).filter { it.isNotBlank() }.joinToString("   ·   ")
            if (line.isNotBlank()) append(fieldRuns(line + if (numbers) "   ·   " else "", rPr))
            if (numbers) {
                append(t("Seite "))
                append(field("PAGE", "1", rPr))
                append(t(" von "))
                append(field("NUMPAGES", "1", rPr))
            }
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<w:ftr xmlns:w=\"$W_NS\" xmlns:r=\"$R_NS\"><w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr>$content</w:p></w:ftr>"
    }

    /** Header line on the left, today's date on the right (at a right tab). */
    private fun header(text: String, date: Boolean): String {
        val rPr = "<w:rPr><w:color w:val=\"888888\"/><w:sz w:val=\"16\"/></w:rPr>"
        val left = if (text.isBlank()) "" else fieldRuns(text, rPr)
        val right = if (!date) "" else "<w:r>$rPr<w:tab/></w:r><w:r>$rPr<w:t>${esc(DocMarkup.today())}</w:t></w:r>"
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<w:hdr xmlns:w=\"$W_NS\" xmlns:r=\"$R_NS\"><w:p><w:pPr><w:tabs><w:tab w:val=\"right\" w:pos=\"$TEXT_WIDTH\"/></w:tabs></w:pPr>" +
            "$left$right</w:p></w:hdr>"
    }

    /** The logo as inline picture, about 1.4 cm high (TU Dresden: 17 mm as in the official templates). */
    private fun logoParagraph(theme: DocTheme): String {
        val (w, h) = theme.logoSize ?: return ""
        val tud = theme.style == DocStyle.TUD
        var cy = if (tud) 612_000L else 504_000L
        var cx = cy * w / h
        val maxWidth = if (tud) 1_900_000L else 1_800_000L
        if (cx > maxWidth) {
            cx = maxWidth
            cy = cx * h / w
        }
        val align = when (theme.style) {
            DocStyle.CLASSIC -> "center"
            DocStyle.TUD -> "left"
            else -> "right"
        }
        return "<w:p><w:pPr><w:spacing w:after=\"120\"/><w:jc w:val=\"$align\"/></w:pPr><w:r>" +
            drawing("rIdLogo", 1, "logo.png", if (tud) "Logo der Technischen Universität Dresden" else "Logo", cx, cy) + "</w:r></w:p>"
    }

    private fun drawing(rel: String, id: Int, name: String, descr: String, cx: Long, cy: Long) =
        "<w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\"><wp:extent cx=\"$cx\" cy=\"$cy\"/>" +
            "<wp:docPr id=\"$id\" name=\"${attr(name)}\" descr=\"${attr(descr)}\"/>" +
            "<a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">" +
            "<a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
            "<pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
            "<pic:nvPicPr><pic:cNvPr id=\"$id\" name=\"${attr(name)}\"/><pic:cNvPicPr/></pic:nvPicPr>" +
            "<pic:blipFill><a:blip r:embed=\"$rel\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
            "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm>" +
            "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic>" +
            "</wp:inline></w:drawing>"

    /** A picture of the document as inline drawing (width in percent of the text width), caption below it. */
    private fun imageParagraph(img: DocMarkup.Image): String {
        val c = ctx
        val bytes = DocMarkup.imageBytes(img.src)
            ?: return para(run("[Bild: ${img.alt.ifBlank { img.src.substringAfterLast('/') }}]", RunStyle(italic = true, color = "888888")), SPACING)
        val type = DocMarkup.imageType(bytes) ?: "png"
        val n = c.media.size + 1
        val media = Media("media/bild$n.$type", "rIdImg$n", bytes)
        c.media += media
        val (pw, ph) = DocMarkup.imageSize(bytes) ?: (800 to 600)
        val maxW = c.width * 635L
        val maxH = c.height * 635L * 85 / 100
        var cx = if (img.width != null) maxW * img.width / 100 else minOf(pw * 9525L, maxW)
        var cy = cx * ph / pw
        if (cy > maxH) {
            cy = maxH
            cx = cy * pw / ph
        }
        val id = c.drawingId++
        val caption = img.alt.isNotBlank()
        val picture = para(
            "<w:r>" + drawing(media.rel, id, "Bild $n", img.alt.ifBlank { "Bild $n" }, cx, cy) + "</w:r>",
            (if (caption) "<w:keepNext/>" else "") + "<w:spacing w:before=\"120\" w:after=\"${if (caption) 60 else 160}\"/><w:jc w:val=\"center\"/>",
        )
        return picture + if (caption) para(runs(img.alt), "<w:pStyle w:val=\"Caption\"/>") else ""
    }

    private fun esc(s: String) = s
        .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun attr(s: String) = esc(s).replace("\"", "&quot;").replace("\n", " ")

    /** Run formatting; rendered in the element order the OOXML schema requires. */
    private data class RunStyle(
        val mono: Boolean = false,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val strike: Boolean = false,
        val color: String? = null,
        val size: Int? = null,
        val underline: Boolean = false,
        val shade: Boolean = false,
        val font: String? = null,
        /** Highlight colour (RRGGBB) of the Fließtext-Editor, written as shading so every colour works. */
        val highlight: String? = null,
        val sup: Boolean = false,
        val sub: Boolean = false,
    ) {
        fun xml() = buildString {
            if (mono) append("<w:rFonts w:ascii=\"Courier New\" w:hAnsi=\"Courier New\" w:cs=\"Courier New\"/>")
            else if (font != null) append("<w:rFonts w:ascii=\"${attr(font)}\" w:hAnsi=\"${attr(font)}\" w:cs=\"${attr(font)}\"/>")
            if (bold) append("<w:b/>")
            if (italic) append("<w:i/>")
            if (strike) append("<w:strike/>")
            if (color != null) append("<w:color w:val=\"$color\"/>")
            if (size != null) append("<w:sz w:val=\"$size\"/>")
            if (underline) append("<w:u w:val=\"single\"/>")
            if (highlight != null) append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"$highlight\"/>")
            else if (shade) append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F1F3F5\"/>")
            if (sup) append("<w:vertAlign w:val=\"superscript\"/>") else if (sub) append("<w:vertAlign w:val=\"subscript\"/>")
        }
    }

    private fun run(text: String, style: RunStyle = RunStyle()): String {
        val props = style.xml().let { if (it.isEmpty()) "" else "<w:rPr>$it</w:rPr>" }
        return text.split('\n').joinToString("<w:r><w:br/></w:r>") { line ->
            "<w:r>$props<w:t xml:space=\"preserve\">${esc(line)}</w:t></w:r>"
        }
    }

    private fun segRun(seg: Segment, base: RunStyle) = run(
        seg.text,
        base.copy(
            mono = base.mono || seg.code,
            bold = base.bold || seg.bold,
            italic = base.italic || seg.italic,
            strike = base.strike || seg.strike,
            color = if (seg.link != null) "1D4ED8" else seg.color ?: base.color,
            underline = base.underline || seg.link != null || seg.underline,
            shade = base.shade || seg.code,
            size = seg.size?.let { it * 2 } ?: base.size,
            font = seg.font ?: base.font,
            highlight = seg.highlight ?: base.highlight,
            sup = seg.sup || base.sup,
            sub = seg.sub || base.sub,
        ),
    )

    /** A footnote mark in the text; Word numbers it and puts the note at the bottom of the page. */
    private fun noteRef(n: Int) = "<w:r><w:rPr><w:rStyle w:val=\"FootnoteReference\"/></w:rPr><w:footnoteReference w:id=\"$n\"/></w:r>"

    private fun runs(text: String, base: RunStyle = RunStyle()): String =
        // Formulas (AP-16) as Word formulas, the text around them as runs.
        LatexOmml.split(text).joinToString("") { (t, math) -> if (math) LatexOmml.inline(t) else textRuns(t, base) }

    private fun textRuns(text: String, base: RunStyle): String {
        val numbers = ctx.numbers
        return inlineSegments(text).joinToString("") { seg ->
            if (seg.code || numbers.isEmpty()) segRun(seg, base)
            else DocMarkup.splitNotes(seg.text, numbers).joinToString("") { (t, n) -> if (n != null) noteRef(n) else segRun(seg.copy(text = t), base) }
        }
    }

    /**
     * A paragraph of the Fließtext-Editor with its marker (AP-44/46): Formatvorlage (Titel, Untertitel,
     * Bildunterschrift, Code, Literatur), alignment, line spacing and indents.
     */
    private fun markedParagraph(text: String): String {
        val m = io.github.thepytales.llmhub.ui.ALIGN_MARK.find(text.trimStart()) ?: return para(runs(text), SPACING)
        val p = io.github.thepytales.llmhub.docs.text.ParaFormat.fromMarker(io.github.thepytales.llmhub.docs.text.ParaFormat(), m)
        val sheet = ctx.sheet
        val style = when (p.kind) {
            io.github.thepytales.llmhub.docs.text.ParaKind.TITLE -> "Title"
            io.github.thepytales.llmhub.docs.text.ParaKind.SUBTITLE -> "Subtitle"
            io.github.thepytales.llmhub.docs.text.ParaKind.CAPTION -> "Caption"
            io.github.thepytales.llmhub.docs.text.ParaKind.LITERATURE -> "Bibliography"
            else -> null
        }
        val pPr = buildString {
            style?.let { append("<w:pStyle w:val=\"$it\"/>") }
            when {
                p.spacing != null -> append("<w:spacing w:after=\"120\" w:line=\"${(p.spacing * 240).toInt()}\" w:lineRule=\"auto\"/>")
                style == null && p.kind != io.github.thepytales.llmhub.docs.text.ParaKind.CODE -> append(if (sheet != null) "" else SPACING)
                p.kind == io.github.thepytales.llmhub.docs.text.ParaKind.CODE -> append("<w:spacing w:after=\"0\" w:line=\"240\" w:lineRule=\"auto\"/>")
            }
            if (p.left != 0 || p.first != 0) {
                val left = (p.left * 56.693f).toInt()
                val first = (p.first * 56.693f).toInt()
                append("<w:ind w:left=\"$left\"" + (if (first >= 0) " w:firstLine=\"$first\"" else " w:hanging=\"${-first}\"") + "/>")
            }
            append(jc(text))
        }
        val base = if (p.kind == io.github.thepytales.llmhub.docs.text.ParaKind.CODE) RunStyle(mono = true, size = 20) else null
        return para(if (base != null) runs(text, base) else runs(text), pPr)
    }

    /** Alignment of a paragraph of the Fließtext-Editor (marker `<!--a:c-->` at its start, AP-44). */
    private fun jc(text: String): String = when (io.github.thepytales.llmhub.ui.ALIGN_MARK.find(text.trimStart())?.groupValues?.get(1)) {
        "c" -> "<w:jc w:val=\"center\"/>"
        "r" -> "<w:jc w:val=\"right\"/>"
        "j" -> "<w:jc w:val=\"both\"/>"
        else -> ""
    }

    private fun para(content: String, pPr: String = "") =
        "<w:p>${if (pPr.isEmpty()) "" else "<w:pPr>$pPr</w:pPr>"}$content</w:p>"

    private const val SPACING = "<w:spacing w:after=\"120\" w:line=\"276\" w:lineRule=\"auto\"/>"

    private fun accentOf(theme: DocTheme) = if (theme.style == DocStyle.CLASSIC || theme.style == DocStyle.MINIMAL) null else theme.accentHex
    private fun headFontOf(theme: DocTheme) = if (theme.style == DocStyle.CLASSIC || theme.style == DocStyle.ELEGANT) "Georgia" else null

    private fun document(title: String, markdown: String, theme: DocTheme, withLogo: Boolean): String = buildString {
        head(theme, withLogo)
        for (b in parseBlocks(DocumentWriter.withTitle(title, markdown))) block(b, theme)
        tail()
    }

    private fun StringBuilder.head(theme: DocTheme, withLogo: Boolean) {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        append("<w:document xmlns:w=\"$W_NS\" xmlns:r=\"$R_NS\" ")
        append("xmlns:m=\"${LatexOmml.NS}\" xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\"><w:body>")
        if (withLogo) append(logoParagraph(theme))
        if (theme.style == DocStyle.TUD && theme.unit.isNotBlank()) {
            val lines = theme.unit.lines().map { it.trim() }.filter { it.isNotEmpty() }
            lines.forEachIndexed { i, line ->
                val last = i == lines.lastIndex
                // As in the official templates: the unit in small black type below the logo.
                val style = RunStyle(bold = last, size = 17)
                append(para(run(line, style), "<w:spacing w:after=\"${if (last) 240 else 0}\"/>"))
            }
        }
    }

    /** Section properties: header/footer, A4 portrait or landscape, 2 cm margins. */
    private fun sectPr(landscape: Boolean): String = buildString {
        val c = ctx
        append("<w:sectPr>")
        if (c.header) append("<w:headerReference w:type=\"default\" r:id=\"rIdHeader\"/>")
        if (c.header && c.first) append("<w:headerReference w:type=\"first\" r:id=\"rIdHeader2\"/>")
        if (c.header && c.even) append("<w:headerReference w:type=\"even\" r:id=\"rIdHeader3\"/>")
        if (c.footer) append("<w:footerReference w:type=\"default\" r:id=\"rIdFooter\"/>")
        if (c.footer && c.first) append("<w:footerReference w:type=\"first\" r:id=\"rIdFooter2\"/>")
        if (c.footer && c.even) append("<w:footerReference w:type=\"even\" r:id=\"rIdFooter3\"/>")
        append(if (landscape) "<w:pgSz w:w=\"16838\" w:h=\"11906\" w:orient=\"landscape\"/>" else "<w:pgSz w:w=\"11906\" w:h=\"16838\"/>")
        val m = c.margin
        append("<w:pgMar w:top=\"$m\" w:right=\"$m\" w:bottom=\"$m\" w:left=\"$m\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/>")
        if (c.first && c.sections == 0) append("<w:titlePg/>")
        c.sections++
        append("</w:sectPr>")
    }

    private fun StringBuilder.tail() {
        append(sectPr(ctx.landscape))
        append("</w:body></w:document>")
    }

    /** Paragraph properties of a block (style, indent, borders) for the tracked version. */
    private fun pPrOf(b: Block, theme: DocTheme): String = when (b) {
        is Block.Heading -> "<w:pStyle w:val=\"Heading${b.level.coerceIn(1, 4)}\"/><w:keepNext/><w:spacing w:before=\"240\" w:after=\"120\"/>"
        is Block.Item -> "<w:spacing w:after=\"60\"/><w:ind w:left=\"${720 + 360 * b.indent}\" w:hanging=\"360\"/>"
        is Block.Quote -> "<w:pBdr><w:left w:val=\"single\" w:sz=\"12\" w:space=\"8\" w:color=\"BBBBBB\"/></w:pBdr>$SPACING<w:ind w:left=\"240\"/>"
        else -> SPACING
    }

    private fun textOf(b: Block): String = when (b) {
        is Block.Heading -> b.text
        is Block.Paragraph -> b.text
        is Block.Item -> b.marker + " " + b.text
        is Block.Quote -> b.text
        is Block.Code -> b.code
        is Block.Table -> b.rows.joinToString("\n") { it.joinToString(" | ") }
        Block.Rule -> ""
    }

    /** Plain text of inline Markdown (the tracked version compares words without formatting marks). */
    private fun plainInline(text: String) = inlineSegments(text).joinToString("") { it.text }

    /** Blocks the tracked version keeps as they are (markers, pictures, tables). */
    private fun special(b: Block) = b is Block.Table || b is Block.Rule ||
        b is Block.Paragraph && (DocMarkup.isComment(b.text) || DocMarkup.isPageBreak(b.text) || DocMarkup.image(b.text) != null)

    private fun trackedDocument(oldMd: String, newMd: String, theme: DocTheme, withLogo: Boolean, author: String): String = buildString {
        head(theme, withLogo)
        val date = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()
        var revision = 1
        fun attrs() = "w:id=\"${revision++}\" w:author=\"${esc(author)}\" w:date=\"$date\""
        fun insRun(t: String, style: RunStyle) = if (t.isEmpty()) "" else "<w:ins ${attrs()}>${run(t, style)}</w:ins>"
        fun delRun(t: String, style: RunStyle): String {
            if (t.isEmpty()) return ""
            val props = style.xml().let { if (it.isEmpty()) "" else "<w:rPr>$it</w:rPr>" }
            return "<w:del ${attrs()}>" + t.split('\n').joinToString("<w:r><w:br/></w:r>") { line -> "<w:r>$props<w:delText xml:space=\"preserve\">${esc(line)}</w:delText></w:r>" } + "</w:del>"
        }
        fun styleOf(b: Block) = when (b) {
            is Block.Heading -> RunStyle(bold = true, size = when (b.level) { 1 -> 40; 2 -> 32; 3 -> 27; else -> 24 }, color = if (b.level <= 2) accentOf(theme) else null, font = headFontOf(theme))
            is Block.Quote -> RunStyle(italic = true, color = "555555")
            is Block.Code -> RunStyle(mono = true, size = 18)
            else -> RunStyle()
        }
        val oldBlocks = parseBlocks(oldMd)
        val newBlocks = parseBlocks(newMd)
        // Longest common subsequence of the blocks; changed neighbours are compared word by word.
        val dp = Array(oldBlocks.size + 1) { IntArray(newBlocks.size + 1) }
        for (i in oldBlocks.indices.reversed()) for (j in newBlocks.indices.reversed()) {
            dp[i][j] = if (oldBlocks[i] == newBlocks[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        var i = 0
        var j = 0
        while (i < oldBlocks.size || j < newBlocks.size) {
            when {
                i < oldBlocks.size && j < newBlocks.size && oldBlocks[i] == newBlocks[j] -> { block(newBlocks[j], theme); i++; j++ }
                // A changed paragraph: same kind of block on both sides → word by word.
                i < oldBlocks.size && j < newBlocks.size && oldBlocks[i]::class == newBlocks[j]::class && !special(oldBlocks[i]) && !special(newBlocks[j]) &&
                    dp[i][j] == dp[i + 1][j + 1] -> {
                    val style = styleOf(newBlocks[j])
                    val pieces = TextDiff.words(plainInline(textOf(oldBlocks[i])), plainInline(textOf(newBlocks[j])))
                    val content = pieces.joinToString("") { p ->
                        when (p.op) {
                            TextDiff.Op.SAME -> run(p.text, style)
                            TextDiff.Op.ADDED -> insRun(p.text, style)
                            TextDiff.Op.REMOVED -> delRun(p.text, style)
                        }
                    }
                    append(para(content, pPrOf(newBlocks[j], theme)))
                    i++; j++
                }
                j < newBlocks.size && (i >= oldBlocks.size || dp[i][j + 1] >= dp[i + 1][j]) -> {
                    val b = newBlocks[j]
                    if (special(b)) block(b, theme)
                    else append(para(insRun(plainInline(textOf(b)), styleOf(b)), pPrOf(b, theme) + "<w:rPr><w:ins ${attrs()}/></w:rPr>"))
                    j++
                }
                else -> {
                    val b = oldBlocks[i]
                    val text = if (b is Block.Table) "[Tabelle entfernt] " + textOf(b).take(200) else plainInline(textOf(b))
                    if (!special(b) || b is Block.Table) append(para(delRun(text, styleOf(b)), pPrOf(b, theme) + "<w:rPr><w:del ${attrs()}/></w:rPr>"))
                    i++
                }
            }
        }
        tail()
    }

    /** Paragraph-level extras: editor notes, page breaks, landscape sections and pictures. */
    private fun StringBuilder.paragraph(text: String) {
        val c = ctx
        DocMarkup.orientation(text)?.let { landscape ->
            if (landscape != c.landscape) {
                // The section so far ends here (its properties sit in the last paragraph); the next one turns.
                append(para("", sectPr(c.landscape)))
                c.landscape = landscape
            }
            return
        }
        when {
            DocMarkup.isPageBreak(text) -> append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
            DocMarkup.isToc(text) -> toc()
            LatexOmml.display(text) != null -> append(LatexOmml.paragraph(LatexOmml.display(text)!!))
            DocMarkup.isComment(text) -> {}
            else -> {
                val img = DocMarkup.image(text)
                if (img != null) append(imageParagraph(img)) else append(markedParagraph(text))
            }
        }
    }

    /**
     * Inhaltsverzeichnis (AP-16) as Word field (TOC, levels 1–3, linked): it lists the headings at once; Word fills in
     * the page numbers with „Felder aktualisieren“ (F9).
     */
    private fun StringBuilder.toc() {
        val entries = ctx.headings
        val rPr = ""
        append(para(run("Inhaltsverzeichnis", RunStyle(bold = true, size = 28)), "<w:spacing w:before=\"120\" w:after=\"120\"/>"))
        val begin = "<w:r>$rPr<w:fldChar w:fldCharType=\"begin\" w:dirty=\"true\"/></w:r><w:r>$rPr<w:instrText xml:space=\"preserve\"> TOC \\o \"1-3\" \\h \\z \\u </w:instrText></w:r><w:r>$rPr<w:fldChar w:fldCharType=\"separate\"/></w:r>"
        val end = "<w:r>$rPr<w:fldChar w:fldCharType=\"end\"/></w:r>"
        if (entries.isEmpty()) {
            append(para(begin + run("(Überschriften erscheinen hier)") + end))
            return
        }
        entries.forEachIndexed { i, (level, text) ->
            val pPr = "<w:pStyle w:val=\"TOC$level\"/><w:ind w:left=\"${(level - 1) * 360}\"/><w:spacing w:after=\"40\"/>"
            append(para((if (i == 0) begin else "") + run(text) + (if (i == entries.lastIndex) end else ""), pPr))
        }
    }

    private fun StringBuilder.block(b: Block, theme: DocTheme) {
        val accent = accentOf(theme)
        val headFont = headFontOf(theme)
        run {
            when (b) {
                is Block.Heading -> {
                    val size = when (b.level) { 1 -> 40; 2 -> 32; 3 -> 27; else -> 24 }
                    val lineColor = accent ?: "BBBBBB"
                    val border = if (b.level == 1) "<w:pBdr><w:bottom w:val=\"single\" w:sz=\"8\" w:space=\"4\" w:color=\"$lineColor\"/></w:pBdr>" else ""
                    val def = ctx.sheet?.of(io.github.thepytales.llmhub.docs.text.ParaKind.heading(b.level.coerceIn(1, 4)))
                    // With Formatvorlagen (AP-46) the heading looks as its Word style says.
                    val style = if (def != null) RunStyle() else RunStyle(bold = true, size = size, color = if (b.level <= 2) accent else null, font = headFont)
                    val align = jc(b.text).ifEmpty { if (b.level == 1 && theme.style == DocStyle.CLASSIC) "<w:jc w:val=\"center\"/>" else "" }
                    val level = b.level.coerceIn(1, 4)
                    append(para(runs(b.text, style), "<w:pStyle w:val=\"Heading$level\"/><w:keepNext/>$border<w:spacing w:before=\"240\" w:after=\"120\"/>$align"))
                }
                is Block.Paragraph -> paragraph(b.text)
                is Block.Item -> {
                    val left = 360 + 360 * b.indent
                    val box = Regex("^\\[([ xX])]\\s+").find(b.text)
                    val marker = if (box == null) b.marker else if (box.groupValues[1].isBlank()) "☐" else "☒"
                    val text = if (box == null) b.text else b.text.substring(box.range.last + 1)
                    append(para(run(marker) + "<w:r><w:tab/></w:r>" + runs(text), "<w:spacing w:after=\"60\"/><w:ind w:left=\"${left + 360}\" w:hanging=\"360\"/>"))
                }
                is Block.Quote -> append(
                    para(
                        runs(b.text, RunStyle(italic = true, color = "555555")),
                        "<w:pBdr><w:left w:val=\"single\" w:sz=\"12\" w:space=\"8\" w:color=\"BBBBBB\"/></w:pBdr>$SPACING<w:ind w:left=\"240\"/>",
                    )
                )
                is Block.Code -> append(
                    para(
                        run(b.code, RunStyle(mono = true, size = 18)),
                        "<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F1F3F5\"/><w:spacing w:after=\"160\"/>",
                    )
                )
                is Block.Table -> table(b.rows, accent)
                Block.Rule -> append(para("", "<w:pBdr><w:bottom w:val=\"single\" w:sz=\"6\" w:space=\"1\" w:color=\"BBBBBB\"/></w:pBdr>"))
            }
        }
    }

    private fun StringBuilder.table(rows: List<List<String>>, accent: String?) {
        if (rows.isEmpty()) return
        val width = ctx.width
        val cols = rows.maxOf { it.size }
        val colW = width / cols
        val border = listOf("top", "left", "bottom", "right", "insideH", "insideV")
            .joinToString("") { "<w:$it w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"BBBBBB\"/>" }
        append("<w:tbl><w:tblPr><w:tblW w:w=\"$width\" w:type=\"dxa\"/><w:tblBorders>$border</w:tblBorders></w:tblPr>")
        append("<w:tblGrid>")
        repeat(cols) { append("<w:gridCol w:w=\"$colW\"/>") }
        append("</w:tblGrid>")
        rows.forEachIndexed { r, row ->
            // The head row repeats on every page and is announced as column header by screen readers.
            append(if (r == 0) "<w:tr><w:trPr><w:tblHeader/></w:trPr>" else "<w:tr>")
            for (c in 0 until cols) {
                val fill = if (accent != null) accent else "F1F3F5"
                val shade = if (r == 0) "<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"$fill\"/>" else ""
                append("<w:tc><w:tcPr><w:tcW w:w=\"$colW\" w:type=\"dxa\"/>$shade</w:tcPr>")
                val headColor = if (r == 0 && accent != null) "FFFFFF" else null
                append(para(runs(row.getOrNull(c).orEmpty(), RunStyle(bold = r == 0, color = headColor))))
                append("</w:tc>")
            }
            append("</w:tr>")
        }
        append("</w:tbl>")
        append(para(""))
    }
}
