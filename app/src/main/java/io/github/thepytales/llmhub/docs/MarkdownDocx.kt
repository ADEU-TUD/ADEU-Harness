package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.ui.Block
import io.github.thepytales.llmhub.ui.Segment
import io.github.thepytales.llmhub.ui.inlineSegments
import io.github.thepytales.llmhub.ui.parseBlocks
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes Markdown as a minimal Word document (Office Open XML), without external libraries. */
object MarkdownDocx {
    private const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val TEXT_WIDTH = 9638 // A4 minus 2 cm margins, in twentieths of a point

    private const val R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Default Extension="png" ContentType="image/png"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
<Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>
<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
</Types>"""

    private fun docRels(withLogo: Boolean) = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rIdFooter" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>
<Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""" +
        (if (withLogo) "\n<Relationship Id=\"rIdLogo\" Type=\"$R_NS/image\" Target=\"media/logo.png\"/>" else "") +
        "\n</Relationships>"

    private const val RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
</Relationships>"""

    /**
     * Accessibility: German as document language (spell check, screen readers) and real heading styles, so the
     * structure shows in the navigation pane and screen readers can jump between headings.
     */
    private fun styles(font: String) = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<w:styles xmlns:w=\"$W_NS\">")
        append("<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"$font\" w:hAnsi=\"$font\" w:cs=\"$font\"/>")
        append("<w:sz w:val=\"21\"/><w:lang w:val=\"de-DE\" w:eastAsia=\"de-DE\" w:bidi=\"ar-SA\"/></w:rPr></w:rPrDefault>")
        append("<w:pPrDefault><w:pPr><w:spacing w:after=\"120\" w:line=\"276\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>")
        append("<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Standard\"><w:name w:val=\"Normal\"/><w:qFormat/></w:style>")
        for (level in 1..4) {
            append("<w:style w:type=\"paragraph\" w:styleId=\"Heading$level\"><w:name w:val=\"heading $level\"/>")
            append("<w:basedOn w:val=\"Standard\"/><w:next w:val=\"Standard\"/><w:uiPriority w:val=\"9\"/><w:qFormat/>")
            append("<w:pPr><w:keepNext/><w:outlineLvl w:val=\"${level - 1}\"/></w:pPr><w:rPr><w:b/></w:rPr></w:style>")
        }
        append("</w:styles>")
    }

    private fun core(title: String): String {
        val now = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" " +
            "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><dc:title>${esc(title)}</dc:title>" +
            "<dc:language>de-DE</dc:language><dc:creator>ADEU LLM</dc:creator>" +
            "<dcterms:created xsi:type=\"dcterms:W3CDTF\">$now</dcterms:created><dcterms:modified xsi:type=\"dcterms:W3CDTF\">$now</dcterms:modified>" +
            "</cp:coreProperties>"
    }

    fun write(file: File, title: String, markdown: String, theme: DocTheme = DocTheme()) =
        writeWith(file, title, markdown, theme) { withLogo -> document(title, markdown, theme, withLogo) }

    /**
     * Word with tracked changes: the text of [newMarkdown], with what changed since [oldMarkdown] as revisions
     * (inserted / deleted, by [author]) that can be accepted or rejected in Word.
     */
    fun writeTracked(file: File, title: String, oldMarkdown: String, newMarkdown: String, theme: DocTheme = DocTheme(), author: String = "ADEU LLM") =
        writeWith(file, title, newMarkdown, theme) { withLogo -> trackedDocument(oldMarkdown, newMarkdown, theme, withLogo, author) }

    private fun writeWith(file: File, title: String, markdown: String, theme: DocTheme, body: (Boolean) -> String) {
        val logo = theme.logo?.takeIf { theme.logoSize != null }
        ZipOutputStream(file.outputStream()).use { zip ->
            fun bytes(name: String, content: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
            fun entry(name: String, content: String) = bytes(name, content.toByteArray(Charsets.UTF_8))
            entry("[Content_Types].xml", CONTENT_TYPES)
            entry("_rels/.rels", RELS)
            entry("word/_rels/document.xml.rels", docRels(logo != null))
            entry("word/footer1.xml", footer(theme))
            entry("word/styles.xml", styles(if (theme.style == DocStyle.TUD) "Noto Sans" else if (theme.style == DocStyle.CLASSIC) "Georgia" else "Calibri"))
            entry("docProps/core.xml", core(title.ifBlank { markdown.lineSequence().firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim() ?: "Dokument" }))
            if (logo != null) bytes("word/media/logo.png", logo)
            entry("word/document.xml", body(logo != null))
        }
    }

    private fun footer(theme: DocTheme): String {
        val rPr = "<w:rPr><w:color w:val=\"888888\"/><w:sz w:val=\"16\"/></w:rPr>"
        val text = if (theme.footer.isBlank()) "Seite " else "${esc(theme.footer)}   ·   Seite "
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<w:ftr xmlns:w=\"$W_NS\" xmlns:r=\"$R_NS\"><w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr>" +
            "<w:r>$rPr<w:t xml:space=\"preserve\">$text</w:t></w:r>" +
            "<w:r>$rPr<w:fldChar w:fldCharType=\"begin\"/></w:r>" +
            "<w:r>$rPr<w:instrText xml:space=\"preserve\"> PAGE </w:instrText></w:r>" +
            "<w:r>$rPr<w:fldChar w:fldCharType=\"separate\"/></w:r>" +
            "<w:r>$rPr<w:t>1</w:t></w:r>" +
            "<w:r>$rPr<w:fldChar w:fldCharType=\"end\"/></w:r>" +
            "</w:p></w:ftr>"
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
        return "<w:p><w:pPr><w:spacing w:after=\"120\"/><w:jc w:val=\"$align\"/></w:pPr><w:r><w:drawing>" +
            "<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\"><wp:extent cx=\"$cx\" cy=\"$cy\"/>" +
            "<wp:docPr id=\"1\" name=\"Logo\" descr=\"${if (tud) "Logo der Technischen Universität Dresden" else "Logo"}\"/>" +
            "<a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">" +
            "<a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
            "<pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
            "<pic:nvPicPr><pic:cNvPr id=\"1\" name=\"logo.png\"/><pic:cNvPicPr/></pic:nvPicPr>" +
            "<pic:blipFill><a:blip r:embed=\"rIdLogo\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
            "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm>" +
            "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic>" +
            "</wp:inline></w:drawing></w:r></w:p>"
    }

    private fun esc(s: String) = s
        .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

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
    ) {
        fun xml() = buildString {
            if (mono) append("<w:rFonts w:ascii=\"Courier New\" w:hAnsi=\"Courier New\" w:cs=\"Courier New\"/>")
            else if (font != null) append("<w:rFonts w:ascii=\"$font\" w:hAnsi=\"$font\" w:cs=\"$font\"/>")
            if (bold) append("<w:b/>")
            if (italic) append("<w:i/>")
            if (strike) append("<w:strike/>")
            if (color != null) append("<w:color w:val=\"$color\"/>")
            if (size != null) append("<w:sz w:val=\"$size\"/>")
            if (underline) append("<w:u w:val=\"single\"/>")
            if (shade) append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F1F3F5\"/>")
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
            color = if (seg.link != null) "1D4ED8" else base.color,
            underline = base.underline || seg.link != null,
            shade = base.shade || seg.code,
        ),
    )

    private fun runs(text: String, base: RunStyle = RunStyle()) = inlineSegments(text).joinToString("") { segRun(it, base) }

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
        append("xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\"><w:body>")
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

    private fun StringBuilder.tail() {
        append("<w:sectPr><w:footerReference w:type=\"default\" r:id=\"rIdFooter\"/><w:pgSz w:w=\"11906\" w:h=\"16838\"/>")
        append("<w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/>")
        append("</w:sectPr></w:body></w:document>")
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
                i < oldBlocks.size && j < newBlocks.size && oldBlocks[i]::class == newBlocks[j]::class && oldBlocks[i] !is Block.Table &&
                    dp[i][j] == dp[i + 1][j + 1] -> {
                    val style = styleOf(newBlocks[j])
                    val pieces = io.github.thepytales.llmhub.docs.TextDiff.words(plainInline(textOf(oldBlocks[i])), plainInline(textOf(newBlocks[j])))
                    val content = pieces.joinToString("") { p ->
                        when (p.op) {
                            io.github.thepytales.llmhub.docs.TextDiff.Op.SAME -> run(p.text, style)
                            io.github.thepytales.llmhub.docs.TextDiff.Op.ADDED -> insRun(p.text, style)
                            io.github.thepytales.llmhub.docs.TextDiff.Op.REMOVED -> delRun(p.text, style)
                        }
                    }
                    append(para(content, pPrOf(newBlocks[j], theme)))
                    i++; j++
                }
                j < newBlocks.size && (i >= oldBlocks.size || dp[i][j + 1] >= dp[i + 1][j]) -> {
                    val b = newBlocks[j]
                    if (b is Block.Table || b is Block.Rule) block(b, theme)
                    else append(para(insRun(plainInline(textOf(b)), styleOf(b)), pPrOf(b, theme) + "<w:rPr><w:ins ${attrs()}/></w:rPr>"))
                    j++
                }
                else -> {
                    val b = oldBlocks[i]
                    val text = if (b is Block.Table) "[Tabelle entfernt] " + textOf(b).take(200) else plainInline(textOf(b))
                    if (b !is Block.Rule) append(para(delRun(text, styleOf(b)), pPrOf(b, theme) + "<w:rPr><w:del ${attrs()}/></w:rPr>"))
                    i++
                }
            }
        }
        tail()
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
                    val style = RunStyle(bold = true, size = size, color = if (b.level <= 2) accent else null, font = headFont)
                    val align = if (b.level == 1 && theme.style == DocStyle.CLASSIC) "<w:jc w:val=\"center\"/>" else ""
                    val level = b.level.coerceIn(1, 4)
                    append(para(runs(b.text, style), "<w:pStyle w:val=\"Heading$level\"/><w:keepNext/>$border<w:spacing w:before=\"240\" w:after=\"120\"/>$align"))
                }
                is Block.Paragraph -> append(para(runs(b.text), SPACING))
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
        val cols = rows.maxOf { it.size }
        val colW = TEXT_WIDTH / cols
        val border = listOf("top", "left", "bottom", "right", "insideH", "insideV")
            .joinToString("") { "<w:$it w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"BBBBBB\"/>" }
        append("<w:tbl><w:tblPr><w:tblW w:w=\"$TEXT_WIDTH\" w:type=\"dxa\"/><w:tblBorders>$border</w:tblBorders></w:tblPr>")
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
