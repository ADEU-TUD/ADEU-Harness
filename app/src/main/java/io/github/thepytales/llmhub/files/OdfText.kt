package io.github.thepytales.llmhub.files

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * OpenDocument (LibreOffice) as Markdown: .odt with headings (text:h by outline level), lists, tables and bold or
 * italic text; .ods one Markdown table per sheet; .odp one section per slide with its notes.
 */
object OdfText {
    private const val MAX_ROWS = 2000
    private const val MAX_COLS = 60

    fun markdown(bytes: ByteArray): String {
        val content = zipEntry(bytes, "content.xml") ?: return ""
        val root = parse(content) ?: return ""
        val styles = Styles(root)
        val body = root.child("body") ?: return ""
        val out = StringBuilder()
        body.child("text")?.let { blocks(it, styles, out, 0) }
        body.child("spreadsheet")?.let { sheet ->
            sheet.children().filter { it.localName == "table" }.forEach { t ->
                val table = tableMarkdown(t, styles)
                if (table.isNotBlank()) out.append("## ").append(t.attr("name").ifBlank { "Tabelle" }).append("\n\n").append(table).append("\n\n")
            }
        }
        body.child("presentation")?.let { pres ->
            pres.children().filter { it.localName == "page" }.forEachIndexed { i, page -> slide(page, i + 1, styles, out) }
        }
        return out.toString().replace(Regex("\n{3,}"), "\n\n").trim()
    }

    /** Bold and italic of automatic text styles; numbered list styles. */
    private class Styles(root: Element) {
        val bold = HashSet<String>()
        val italic = HashSet<String>()
        val numbered = HashSet<String>()

        init {
            root.child("automatic-styles")?.children()?.forEach { s ->
                val name = s.attr("name")
                when (s.localName) {
                    "style" -> s.child("text-properties")?.let { p ->
                        if (p.attr("font-weight") == "bold") bold += name
                        if (p.attr("font-style") == "italic") italic += name
                    }
                    "list-style" -> if (s.children().firstOrNull()?.localName == "list-level-style-number") numbered += name
                }
            }
        }
    }

    private fun blocks(el: Element, styles: Styles, out: StringBuilder, depth: Int) {
        for (c in el.children()) {
            when (c.localName) {
                "h" -> {
                    val level = c.attr("outline-level").toIntOrNull()?.coerceIn(1, 6) ?: 1
                    val t = inline(c, styles).trim()
                    if (t.isNotEmpty()) out.append("#".repeat(level)).append(' ').append(t.replace("**", "")).append("\n\n")
                }
                "p" -> {
                    val t = inline(c, styles).trim()
                    if (t.isNotEmpty()) out.append(t).append("\n\n")
                    // Frames with text boxes inside a paragraph.
                    c.descendants("text-box").forEach { blocks(it, styles, out, depth) }
                }
                "list" -> { list(c, styles, out, 0, styles.numbered.contains(c.attr("style-name"))); out.append('\n') }
                "table" -> { out.append(tableMarkdown(c, styles)).append("\n\n") }
                "section", "index-body", "table-of-content", "alphabetical-index", "illustration-index", "bibliography", "text-box",
                "deletion", "change", "soft-page-break" -> if (depth < 20) blocks(c, styles, out, depth + 1)
                "frame" -> c.descendants("text-box").forEach { blocks(it, styles, out, depth + 1) }
            }
        }
    }

    private fun list(el: Element, styles: Styles, out: StringBuilder, level: Int, numbered: Boolean) {
        var no = 0
        for (item in el.children().filter { it.localName == "list-item" || it.localName == "list-header" }) {
            no++
            var first = true
            for (c in item.children()) {
                when (c.localName) {
                    "p", "h" -> {
                        val t = inline(c, styles).trim()
                        if (t.isEmpty()) continue
                        if (first) out.append("  ".repeat(level)).append(if (numbered) "$no." else "-").append(' ').append(t).append('\n')
                        else out.append("  ".repeat(level + 1)).append(t).append('\n')
                        first = false
                    }
                    "list" -> list(c, styles, out, level + 1, styles.numbered.contains(c.attr("style-name")) || numbered && c.attr("style-name").isEmpty())
                }
            }
        }
    }

    /** Text of a paragraph with spaces, tabs, line breaks, links and bold/italic spans. */
    private fun inline(el: Element, styles: Styles): String {
        val sb = StringBuilder()
        fun walk(n: Node) {
            for (i in 0 until n.childNodes.length) {
                val c = n.childNodes.item(i)
                if (c.nodeType == Node.TEXT_NODE) { sb.append(c.nodeValue.replace(Regex("\\s+"), " ")); continue }
                val e = c as? Element ?: continue
                when (e.localName) {
                    "s" -> sb.append(" ".repeat(e.attr("c").toIntOrNull()?.coerceIn(1, 100) ?: 1))
                    "tab" -> sb.append('\t')
                    "line-break" -> sb.append("  \n")
                    "span" -> {
                        val style = e.attr("style-name")
                        val mark = (if (style in styles.bold) "**" else "") + (if (style in styles.italic) "*" else "")
                        val keep = sb.length
                        walk(e)
                        val inner = sb.substring(keep)
                        sb.setLength(keep)
                        if (mark.isNotEmpty() && inner.isNotBlank()) sb.append(mark).append(inner.trim()).append(mark.reversed()).append(if (inner.endsWith(" ")) " " else "")
                        else sb.append(inner)
                    }
                    "a" -> {
                        val keep = sb.length
                        walk(e)
                        val text = sb.substring(keep)
                        val href = e.attrNs("href")
                        if (href.isNotBlank() && !href.startsWith("#") && text.isNotBlank() && text.trim() != href) { sb.setLength(keep); sb.append("[").append(text.trim()).append("](").append(href).append(")") }
                    }
                    "note" -> {
                        val body = e.child("note-body")?.let { nb -> nb.children().joinToString(" ") { inline(it, styles).trim() } }.orEmpty()
                        if (body.isNotBlank()) sb.append(" [Fußnote: ").append(body.trim()).append("]")
                    }
                    "frame", "annotation", "tracked-changes", "bookmark", "bookmark-start", "bookmark-end", "reference-mark" -> {}
                    else -> walk(e)
                }
            }
        }
        walk(el)
        return sb.toString()
    }

    private fun tableMarkdown(table: Element, styles: Styles): String {
        val rows = mutableListOf<List<String>>()
        fun collect(el: Element) {
            for (c in el.children()) {
                if (rows.size >= MAX_ROWS) return
                when (c.localName) {
                    "table-row" -> {
                        val cells = mutableListOf<String>()
                        for (cell in c.children()) {
                            if (cell.localName != "table-cell" && cell.localName != "covered-table-cell") continue
                            val text = cell.children().filter { it.localName == "p" || it.localName == "h" || it.localName == "list" }
                                .joinToString(" ") { p -> if (p.localName == "list") p.textContent.trim() else inline(p, styles).trim() }.trim()
                                .replace("|", "\\|").replace("\n", " ")
                            val repeat = cell.attr("number-columns-repeated").toIntOrNull() ?: 1
                            repeat(minOf(repeat, MAX_COLS - cells.size).coerceAtLeast(0)) { cells += text }
                            if (cells.size >= MAX_COLS) break
                        }
                        val repeatRows = c.attr("number-rows-repeated").toIntOrNull() ?: 1
                        val trimmed = cells.dropLastWhile { it.isEmpty() }
                        repeat(if (trimmed.isEmpty()) minOf(repeatRows, 1) else minOf(repeatRows, MAX_ROWS - rows.size)) { rows += trimmed }
                    }
                    "table-header-rows", "table-rows", "table-row-group" -> collect(c)
                }
            }
        }
        collect(table)
        val used = rows.dropLastWhile { it.isEmpty() }.dropWhile { it.isEmpty() }
        if (used.isEmpty()) return ""
        val width = used.maxOf { it.size }.coerceAtLeast(1)
        fun line(r: List<String>) = "| " + (0 until width).joinToString(" | ") { r.getOrElse(it) { "" } } + " |"
        return buildString {
            append(line(used.first())).append('\n')
            append("|").append(" --- |".repeat(width)).append('\n')
            used.drop(1).forEach { append(line(it)).append('\n') }
        }.trimEnd()
    }

    private fun slide(page: Element, no: Int, styles: Styles, out: StringBuilder) {
        val frames = page.children().filter { it.localName == "frame" || it.localName == "custom-shape" || it.localName == "g" }
        val titleFrame = frames.firstOrNull { it.attrNs("class") == "title" }
        val title = titleFrame?.let { f -> f.descendants("p").joinToString(" ") { inline(it, styles).trim() }.trim() }.orEmpty()
        out.append("## Folie ").append(no).append(if (title.isNotEmpty()) ": $title" else "").append("\n\n")
        for (f in frames) {
            if (f === titleFrame) continue
            f.descendants("table").forEach { out.append(tableMarkdown(it, styles)).append("\n\n") }
            (if (f.localName == "custom-shape") listOf(f) else f.descendants("text-box")).forEach { blocks(it, styles, out, 1) }
        }
        page.child("notes")?.let { notes ->
            val text = notes.descendants("text-box").joinToString("\n") { tb -> tb.descendants("p").joinToString("\n") { inline(it, styles).trim() } }.trim()
            if (text.isNotEmpty()) out.append("Notizen: ").append(text).append("\n\n")
        }
    }

    // ------------------------------------------------------------------ XML helpers

    internal fun zipEntry(bytes: ByteArray, name: String): ByteArray? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (e.name == name) return zip.readBytes()
                e = zip.nextEntry
            }
        }
        return null
    }

    internal fun parse(bytes: ByteArray): Element? = runCatching {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = true
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { f.isExpandEntityReferences = false }
        f.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement
    }.getOrNull()

    private fun Node.children(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
    private fun Element.child(name: String): Element? = children().firstOrNull { it.localName == name }
    private fun Element.attr(name: String): String = attributes?.let { a ->
        (0 until a.length).map { a.item(it) }.firstOrNull { (it.localName ?: it.nodeName.substringAfter(':')) == name }?.nodeValue
    }.orEmpty()
    private fun Element.attrNs(name: String): String = attr(name)

    /** All descendants with [name], not descending into matches (text boxes in text boxes are rare). */
    private fun Element.descendants(name: String): List<Element> {
        val out = mutableListOf<Element>()
        fun walk(e: Element) { for (c in e.children()) if (c.localName == name) out += c else walk(c) }
        walk(this)
        return out
    }
}
