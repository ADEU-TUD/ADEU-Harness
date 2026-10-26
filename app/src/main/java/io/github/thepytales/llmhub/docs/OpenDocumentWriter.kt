package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.ui.Block
import io.github.thepytales.llmhub.ui.inlineSegments
import io.github.thepytales.llmhub.ui.parseBlocks
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * OpenDocument files (LibreOffice) from Markdown: .odt text with headings, lists, tables, quotes and code; .ods one
 * sheet per Markdown table (numbers stay numbers); .odp one slide per "---" section or heading.
 */
object OpenDocumentWriter {
    private const val NS = "xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" " +
        "xmlns:style=\"urn:oasis:names:tc:opendocument:xmlns:style:1.0\" " +
        "xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\" " +
        "xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\" " +
        "xmlns:draw=\"urn:oasis:names:tc:opendocument:xmlns:drawing:1.0\" " +
        "xmlns:fo=\"urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0\" " +
        "xmlns:xlink=\"http://www.w3.org/1999/xlink\" " +
        "xmlns:svg=\"urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0\" " +
        "xmlns:presentation=\"urn:oasis:names:tc:opendocument:xmlns:presentation:1.0\" " +
        "xmlns:meta=\"urn:oasis:names:tc:opendocument:xmlns:meta:1.0\" " +
        "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" office:version=\"1.3\""

    private const val ODT = "application/vnd.oasis.opendocument.text"
    private const val ODS = "application/vnd.oasis.opendocument.spreadsheet"
    private const val ODP = "application/vnd.oasis.opendocument.presentation"

    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")

    /** Text with runs of spaces and tabs as ODF elements. */
    private fun textEsc(s: String): String = esc(s).replace("\t", "<text:tab/>").replace(Regex(" {2,}")) { m -> " <text:s text:c=\"${m.value.length - 1}\"/>" }

    fun odt(title: String, markdown: String): ByteArray = pack(ODT, content(title, markdown), styles(), title)
    fun ods(title: String, markdown: String): ByteArray = pack(ODS, spreadsheet(title, markdown), styles(), title)
    fun odp(title: String, markdown: String): ByteArray = pack(ODP, presentation(title, markdown), styles(presentation = true), title)

    /** mimetype first and uncompressed, then manifest, content, styles and meta. */
    private fun pack(mime: String, content: String, styles: String, title: String): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            val m = mime.toByteArray(Charsets.US_ASCII)
            z.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = m.size.toLong()
                compressedSize = m.size.toLong()
                crc = CRC32().apply { update(m) }.value
            })
            z.write(m)
            z.closeEntry()
            fun entry(name: String, text: String) { z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            entry(
                "META-INF/manifest.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<manifest:manifest xmlns:manifest=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\" manifest:version=\"1.3\">" +
                    "<manifest:file-entry manifest:full-path=\"/\" manifest:version=\"1.3\" manifest:media-type=\"$mime\"/>" +
                    "<manifest:file-entry manifest:full-path=\"content.xml\" manifest:media-type=\"text/xml\"/>" +
                    "<manifest:file-entry manifest:full-path=\"styles.xml\" manifest:media-type=\"text/xml\"/>" +
                    "<manifest:file-entry manifest:full-path=\"meta.xml\" manifest:media-type=\"text/xml\"/>" +
                    "</manifest:manifest>",
            )
            entry("content.xml", content)
            entry("styles.xml", styles)
            entry(
                "meta.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<office:document-meta $NS><office:meta><meta:generator>ADEU</meta:generator>" +
                    "<dc:title>${esc(title)}</dc:title></office:meta></office:document-meta>",
            )
        }
        return bos.toByteArray()
    }

    private fun styles(presentation: Boolean = false): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<office:document-styles $NS><office:styles>")
        append("<style:default-style style:family=\"paragraph\"><style:text-properties style:font-name=\"Liberation Sans\" fo:font-family=\"'Liberation Sans', Arial\" fo:font-size=\"11pt\"/></style:default-style>")
        append("<style:style style:name=\"Standard\" style:family=\"paragraph\"><style:paragraph-properties fo:margin-bottom=\"0.2cm\"/></style:style>")
        for (level in 1..6) {
            val size = listOf(20, 16, 14, 12, 11, 11)[level - 1]
            append("<style:style style:name=\"Heading_20_$level\" style:display-name=\"Heading $level\" style:family=\"paragraph\" style:parent-style-name=\"Standard\" style:default-outline-level=\"$level\">")
            append("<style:paragraph-properties fo:margin-top=\"0.4cm\" fo:margin-bottom=\"0.2cm\" fo:keep-with-next=\"always\"/>")
            append("<style:text-properties fo:font-size=\"${size}pt\" fo:font-weight=\"bold\"/></style:style>")
        }
        append("<style:style style:name=\"Quotations\" style:family=\"paragraph\" style:parent-style-name=\"Standard\"><style:paragraph-properties fo:margin-left=\"1cm\"/><style:text-properties fo:font-style=\"italic\"/></style:style>")
        append("<style:style style:name=\"Preformatted_20_Text\" style:display-name=\"Preformatted Text\" style:family=\"paragraph\" style:parent-style-name=\"Standard\"><style:text-properties style:font-name=\"Liberation Mono\" fo:font-family=\"'Liberation Mono', Consolas\" fo:font-size=\"10pt\"/></style:style>")
        if (presentation) append("<style:style style:name=\"Title\" style:family=\"paragraph\"><style:text-properties fo:font-size=\"32pt\" fo:font-weight=\"bold\"/></style:style>")
        append("</office:styles>")
        if (presentation) {
            append("<office:automatic-styles><style:page-layout style:name=\"PM1\"><style:page-layout-properties fo:page-width=\"28cm\" fo:page-height=\"15.75cm\" style:print-orientation=\"landscape\"/></style:page-layout></office:automatic-styles>")
            append("<office:master-styles><style:master-page style:name=\"Default\" style:page-layout-name=\"PM1\"/></office:master-styles>")
        }
        append("</office:document-styles>")
    }

    private const val AUTO_STYLES = "<office:automatic-styles>" +
        "<style:style style:name=\"T_b\" style:family=\"text\"><style:text-properties fo:font-weight=\"bold\"/></style:style>" +
        "<style:style style:name=\"T_i\" style:family=\"text\"><style:text-properties fo:font-style=\"italic\"/></style:style>" +
        "<style:style style:name=\"T_bi\" style:family=\"text\"><style:text-properties fo:font-weight=\"bold\" fo:font-style=\"italic\"/></style:style>" +
        "<style:style style:name=\"T_s\" style:family=\"text\"><style:text-properties style:text-line-through-style=\"solid\"/></style:style>" +
        "<style:style style:name=\"T_c\" style:family=\"text\"><style:text-properties style:font-name=\"Liberation Mono\" fo:font-family=\"'Liberation Mono', Consolas\"/></style:style>" +
        "<style:style style:name=\"P_th\" style:family=\"paragraph\"><style:text-properties fo:font-weight=\"bold\"/></style:style>" +
        "<style:style style:name=\"Tbl\" style:family=\"table\"><style:table-properties style:width=\"16cm\" table:align=\"margins\"/></style:style>" +
        "<style:style style:name=\"Cell\" style:family=\"table-cell\"><style:table-cell-properties fo:padding=\"0.1cm\" fo:border=\"0.5pt solid #808080\"/></style:style>" +
        "<text:list-style style:name=\"L_b\">" + "<text:list-level-style-bullet text:level=\"1\" text:bullet-char=\"•\"><style:list-level-properties text:list-level-position-and-space-mode=\"label-alignment\"><style:list-level-label-alignment text:label-followed-by=\"listtab\" fo:text-indent=\"-0.5cm\" fo:margin-left=\"1cm\"/></style:list-level-properties></text:list-level-style-bullet>" +
        "<text:list-level-style-bullet text:level=\"2\" text:bullet-char=\"◦\"><style:list-level-properties text:list-level-position-and-space-mode=\"label-alignment\"><style:list-level-label-alignment text:label-followed-by=\"listtab\" fo:text-indent=\"-0.5cm\" fo:margin-left=\"1.6cm\"/></style:list-level-properties></text:list-level-style-bullet>" +
        "<text:list-level-style-bullet text:level=\"3\" text:bullet-char=\"▪\"><style:list-level-properties text:list-level-position-and-space-mode=\"label-alignment\"><style:list-level-label-alignment text:label-followed-by=\"listtab\" fo:text-indent=\"-0.5cm\" fo:margin-left=\"2.2cm\"/></style:list-level-properties></text:list-level-style-bullet>" +
        "</text:list-style>" +
        "<text:list-style style:name=\"L_n\">" + (1..3).joinToString("") { l ->
            "<text:list-level-style-number text:level=\"$l\" style:num-format=\"1\" style:num-suffix=\".\"><style:list-level-properties text:list-level-position-and-space-mode=\"label-alignment\"><style:list-level-label-alignment text:label-followed-by=\"listtab\" fo:text-indent=\"-0.6cm\" fo:margin-left=\"${0.4 + l * 0.6}cm\"/></style:list-level-properties></text:list-level-style-number>"
        } + "</text:list-style>" +
        "</office:automatic-styles>"

    /** Inline Markdown (bold, italic, code, links, strike) as ODF spans. */
    fun inline(text: String): String = inlineSegments(text).joinToString("") { s ->
        val style = when {
            s.code -> "T_c"
            s.bold && s.italic -> "T_bi"
            s.bold -> "T_b"
            s.italic -> "T_i"
            s.strike -> "T_s"
            else -> null
        }
        var out = textEsc(s.text).replace("\n", "<text:line-break/>")
        if (style != null) out = "<text:span text:style-name=\"$style\">$out</text:span>"
        if (s.link != null) out = "<text:a xlink:type=\"simple\" xlink:href=\"${esc(s.link)}\">$out</text:a>"
        out
    }

    /** Body blocks of a text document. */
    internal fun body(markdown: String): String = buildString {
        val blocks = parseBlocks(ChartBlocks.asTables(markdown))
        var i = 0
        while (i < blocks.size) {
            when (val b = blocks[i]) {
                is Block.Heading -> append("<text:h text:style-name=\"Heading_20_${b.level}\" text:outline-level=\"${b.level}\">").append(inline(b.text)).append("</text:h>")
                is Block.Paragraph -> append("<text:p text:style-name=\"Standard\">").append(inline(b.text.replace(Regex(" {2}\n"), "\n"))).append("</text:p>")
                is Block.Quote -> b.text.lines().forEach { append("<text:p text:style-name=\"Quotations\">").append(inline(it)).append("</text:p>") }
                is Block.Code -> b.code.lines().forEach { append("<text:p text:style-name=\"Preformatted_20_Text\">").append(textEsc(it)).append("</text:p>") }
                is Block.Rule -> append("<text:p text:style-name=\"Standard\"/>")
                is Block.Table -> append(table(b.rows))
                is Block.Item -> {
                    val items = mutableListOf<Block.Item>()
                    while (i < blocks.size && blocks[i] is Block.Item) { items += blocks[i] as Block.Item; i++ }
                    i--
                    append(list(items, 0, 0, items.size))
                }
            }
            i++
        }
    }

    /** Nested list from consecutive items [from, to) with indentation ≥ [level]. */
    private fun list(items: List<Block.Item>, level: Int, from: Int, to: Int): String = buildString {
        val numbered = items.getOrNull(from)?.marker?.firstOrNull()?.isDigit() == true
        append("<text:list").append(if (level == 0) " text:style-name=\"${if (numbered) "L_n" else "L_b"}\"" else "").append('>')
        var i = from
        while (i < to) {
            val item = items[i]
            append("<text:list-item><text:p text:style-name=\"Standard\">").append(inline(item.text)).append("</text:p>")
            var j = i + 1
            while (j < to && items[j].indent > item.indent) j++
            if (j > i + 1) append(list(items, level + 1, i + 1, j))
            append("</text:list-item>")
            i = j
        }
        append("</text:list>")
    }

    private fun table(rows: List<List<String>>): String = buildString {
        if (rows.isEmpty()) return@buildString
        val width = rows.maxOf { it.size }
        append("<table:table table:name=\"Tabelle\" table:style-name=\"Tbl\"><table:table-column table:number-columns-repeated=\"$width\"/>")
        rows.forEachIndexed { r, row ->
            if (r == 0) append("<table:table-header-rows>")
            append("<table:table-row>")
            for (c in 0 until width) {
                append("<table:table-cell table:style-name=\"Cell\" office:value-type=\"string\"><text:p text:style-name=\"${if (r == 0) "P_th" else "Standard"}\">")
                append(inline(row.getOrElse(c) { "" })).append("</text:p></table:table-cell>")
            }
            append("</table:table-row>")
            if (r == 0) append("</table:table-header-rows>")
        }
        append("</table:table>")
    }

    private fun content(title: String, markdown: String): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<office:document-content $NS>$AUTO_STYLES<office:body><office:text>" +
            body(DocumentWriter.withTitle(title, markdown)) + "</office:text></office:body></office:document-content>"

    private fun spreadsheet(title: String, markdown: String): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<office:document-content $NS>$AUTO_STYLES<office:body><office:spreadsheet>")
        for (sheet in XlsxWriter.sheets(title, markdown)) {
            append("<table:table table:name=\"").append(esc(sheet.name)).append("\">")
            val width = sheet.rows.maxOfOrNull { it.size } ?: 1
            append("<table:table-column table:number-columns-repeated=\"$width\"/>")
            sheet.rows.forEachIndexed { r, row ->
                append("<table:table-row>")
                for (c in 0 until width) {
                    val v = row.getOrElse(c) { "" }
                    val n = if (r == 0) null else XlsxWriter.number(v)
                    when {
                        v.isEmpty() -> append("<table:table-cell/>")
                        v.startsWith("=") -> append("<table:table-cell table:formula=\"").append(esc(openFormula(v))).append("\"><text:p>").append(esc(v)).append("</text:p></table:table-cell>")
                        n != null -> append("<table:table-cell office:value-type=\"float\" office:value=\"$n\"><text:p>").append(esc(v)).append("</text:p></table:table-cell>")
                        else -> append("<table:table-cell office:value-type=\"string\"><text:p>").append(esc(v)).append("</text:p></table:table-cell>")
                    }
                }
                append("</table:table-row>")
            }
            append("</table:table>")
        }
        append("</office:spreadsheet></office:body></office:document-content>")
    }

    /** "=SUM(A1:B3, C1)" → "of:=SUM([.A1:.B3];[.C1])" (OpenFormula references and separators). */
    internal fun openFormula(excel: String): String {
        val sb = StringBuilder()
        var quoted = false
        val ref = Regex("^\\$?[A-Z]{1,3}\\$?\\d+(:\\$?[A-Z]{1,3}\\$?\\d+)?")
        var i = 0
        val f = excel.removePrefix("=")
        while (i < f.length) {
            val c = f[i]
            if (c == '"') { quoted = !quoted; sb.append(c); i++; continue }
            if (!quoted) {
                val prev = f.getOrNull(i - 1)
                val m = if (prev == null || !(prev.isLetterOrDigit() || prev == '_' || prev == '.')) ref.find(f.substring(i)) else null
                val after = m?.let { f.getOrNull(i + it.value.length) }
                if (m != null && (after == null || !(after.isLetterOrDigit() || after == '(' || after == '_'))) {
                    sb.append('[').append(m.value.split(':').joinToString(":") { ".$it" }).append(']')
                    i += m.value.length
                    continue
                }
                if (c == ',') { sb.append(';'); i++; continue }
            }
            sb.append(c)
            i++
        }
        return "of:=$sb"
    }

    /** Slides: SlideDeck's "---" sections; without separators each heading starts a slide. */
    private fun slides(title: String, markdown: String): List<SlideDeck.Slide> {
        val deck = SlideDeck.parse(markdown)
        if (deck.size > 1) return deck
        val chunks = mutableListOf(StringBuilder())
        var inCode = false
        for (line in markdown.lines()) {
            if (line.trimStart().startsWith("```")) inCode = !inCode
            if (!inCode && Regex("^#{1,2}\\s").containsMatchIn(line) && chunks.last().isNotBlank()) chunks += StringBuilder()
            chunks.last().append(line).append('\n')
        }
        val parsed = SlideDeck.parse(chunks.joinToString("\n---\n"))
        return parsed.ifEmpty { listOf(SlideDeck.Slide(title = title, layout = SlideDeck.Layout.TITLE)) }
    }

    private fun presentation(title: String, markdown: String): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<office:document-content $NS>$AUTO_STYLES<office:body><office:presentation>")
        slides(title, markdown).forEachIndexed { i, s ->
            append("<draw:page draw:name=\"Folie ${i + 1}\" draw:master-page-name=\"Default\">")
            val first = s.layout == SlideDeck.Layout.TITLE
            append("<draw:frame presentation:class=\"title\" svg:x=\"1.5cm\" svg:y=\"${if (first) "5cm" else "0.8cm"}\" svg:width=\"25cm\" svg:height=\"2.5cm\"><draw:text-box>")
            append("<text:p text:style-name=\"Title\">").append(inline(s.title.ifBlank { if (first) title else "" })).append("</text:p></draw:text-box></draw:frame>")
            val content = listOf(s.body, s.right).filter { it.isNotBlank() }.joinToString("\n\n")
            if (content.isNotBlank()) {
                append("<draw:frame presentation:class=\"").append(if (first) "subtitle" else "outline").append("\" svg:x=\"1.5cm\" svg:y=\"${if (first) "8cm" else "3.6cm"}\" svg:width=\"25cm\" svg:height=\"11cm\"><draw:text-box>")
                append(body(content))
                append("</draw:text-box></draw:frame>")
            }
            if (s.notes.isNotBlank()) {
                append("<presentation:notes><draw:frame presentation:class=\"notes\" svg:x=\"2cm\" svg:y=\"14cm\" svg:width=\"17cm\" svg:height=\"10cm\"><draw:text-box>")
                s.notes.lines().forEach { append("<text:p>").append(inline(it)).append("</text:p>") }
                append("</draw:text-box></draw:frame></presentation:notes>")
            }
            append("</draw:page>")
        }
        append("</office:presentation></office:body></office:document-content>")
    }
}
