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

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

    private const val RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    fun write(file: File, title: String, markdown: String) {
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", CONTENT_TYPES)
            entry("_rels/.rels", RELS)
            entry("word/document.xml", document(title, markdown))
        }
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
    ) {
        fun xml() = buildString {
            if (mono) append("<w:rFonts w:ascii=\"Courier New\" w:hAnsi=\"Courier New\" w:cs=\"Courier New\"/>")
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

    private fun document(title: String, markdown: String): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        append("<w:document xmlns:w=\"$W_NS\"><w:body>")
        for (b in parseBlocks(DocumentWriter.withTitle(title, markdown))) {
            when (b) {
                is Block.Heading -> {
                    val size = when (b.level) { 1 -> 40; 2 -> 32; 3 -> 27; else -> 24 }
                    val border = if (b.level == 1) "<w:pBdr><w:bottom w:val=\"single\" w:sz=\"6\" w:space=\"4\" w:color=\"BBBBBB\"/></w:pBdr>" else ""
                    append(para(runs(b.text, RunStyle(bold = true, size = size)), "<w:keepNext/>$border<w:spacing w:before=\"240\" w:after=\"120\"/>"))
                }
                is Block.Paragraph -> append(para(runs(b.text), SPACING))
                is Block.Item -> {
                    val left = 360 + 360 * b.indent
                    append(para(run(b.marker) + "<w:r><w:tab/></w:r>" + runs(b.text), "<w:spacing w:after=\"60\"/><w:ind w:left=\"${left + 360}\" w:hanging=\"360\"/>"))
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
                is Block.Table -> table(b.rows)
                Block.Rule -> append(para("", "<w:pBdr><w:bottom w:val=\"single\" w:sz=\"6\" w:space=\"1\" w:color=\"BBBBBB\"/></w:pBdr>"))
            }
        }
        append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>")
        append("<w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/>")
        append("</w:sectPr></w:body></w:document>")
    }

    private fun StringBuilder.table(rows: List<List<String>>) {
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
            append("<w:tr>")
            for (c in 0 until cols) {
                val shade = if (r == 0) "<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F1F3F5\"/>" else ""
                append("<w:tc><w:tcPr><w:tcW w:w=\"$colW\" w:type=\"dxa\"/>$shade</w:tcPr>")
                append(para(runs(row.getOrNull(c).orEmpty(), RunStyle(bold = r == 0))))
                append("</w:tc>")
            }
            append("</w:tr>")
        }
        append("</w:tbl>")
        append(para(""))
    }
}
