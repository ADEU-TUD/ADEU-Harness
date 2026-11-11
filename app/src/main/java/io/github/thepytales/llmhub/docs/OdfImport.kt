package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.files.OdfText
import org.w3c.dom.Element
import java.time.LocalDate
import java.util.Locale

/**
 * OpenDocument (LibreOffice) as a source (AP-90): .odt into the document editor (via [OdfText]: headings, lists,
 * tables, bold, italic), .ods into the sheet – one tab per table with values, dates and formulas (OpenFormula
 * „of:=SUM([.A1:.A3])“ becomes „=SUM(A1:A3)“) – and .odp into slides (title, text, notes). Used by [OfficeImport],
 * so opening, converting (Word, Excel, PowerPoint, PDF, Markdown …) and reading for Lora all work.
 */
object OdfImport {
    private const val MAX_ROWS = 20_000
    private const val MAX_COLS = 200

    fun odt(bytes: ByteArray): String = OdfText.markdown(bytes)

    /** The tables of an .ods file: name → sheet Markdown. */
    fun ods(bytes: ByteArray): List<Pair<String, String>> {
        val root = OdfText.zipEntry(bytes, "content.xml")?.let(OdfText::parse) ?: return emptyList()
        val sheet = root.child("body")?.child("spreadsheet") ?: return emptyList()
        return sheet.kids().filter { it.localName == "table" }.map { t ->
            t.attr("name").ifBlank { "Tabelle" } to SheetEngine.markdown(rows(t).ifEmpty { listOf(listOf("")) })
        }
    }

    private fun rows(table: Element): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        fun collect(el: Element) {
            for (c in el.kids()) {
                if (rows.size >= MAX_ROWS) return
                when (c.localName) {
                    "table-row" -> {
                        val cells = mutableListOf<String>()
                        for (cell in c.kids()) {
                            if (cell.localName != "table-cell" && cell.localName != "covered-table-cell") continue
                            val v = cellText(cell)
                            val repeat = cell.attr("number-columns-repeated").toIntOrNull() ?: 1
                            // Repeated empty cells at the end of a row fill the sheet to its edge – not wanted.
                            repeat(minOf(repeat, MAX_COLS - cells.size).coerceAtLeast(0)) { cells += v }
                            if (cells.size >= MAX_COLS) break
                        }
                        val trimmed = cells.dropLastWhile { it.isEmpty() }
                        val repeatRows = c.attr("number-rows-repeated").toIntOrNull() ?: 1
                        repeat(if (trimmed.isEmpty()) minOf(repeatRows, 1) else minOf(repeatRows, MAX_ROWS - rows.size)) { rows += trimmed }
                    }
                    "table-header-rows", "table-rows", "table-row-group" -> collect(c)
                }
            }
        }
        collect(table)
        val used = rows.dropLastWhile { it.isEmpty() }
        val width = used.maxOfOrNull { it.size } ?: 0
        return used.map { r -> r + List(width - r.size) { "" } }
    }

    private fun cellText(cell: Element): String {
        cell.attr("formula").takeIf { it.isNotBlank() }?.let { return formula(it) }
        val value = cell.attr("value")
        return when (cell.attr("value-type")) {
            "float", "currency" -> number(value)
            "percentage" -> number((value.toDoubleOrNull() ?: 0.0).times(100).toString()) + " %"
            "date" -> cell.attr("date-value").let { d ->
                runCatching {
                    val day = LocalDate.parse(d.take(10))
                    String.format(Locale.ROOT, "%02d.%02d.%04d", day.dayOfMonth, day.monthValue, day.year) +
                        (d.substringAfter('T', "").take(5).takeIf { it.length == 5 && it != "00:00" }?.let { " $it" } ?: "")
                }.getOrDefault(d)
            }
            "boolean" -> if (cell.attr("boolean-value") == "true") "WAHR" else "FALSCH"
            else -> cell.kids().filter { it.localName == "p" }.joinToString("\n") { it.textContent }
        }
    }

    /** „3.5“ → „3,5“, „1E-3“ → „0,001“. */
    private fun number(v: String): String {
        val d = v.toDoubleOrNull() ?: return v
        val plain = if (d == Math.floor(d) && Math.abs(d) < 1e15) d.toLong().toString() else java.math.BigDecimal(v).stripTrailingZeros().toPlainString()
        return plain.replace('.', ',')
    }

    /** OpenFormula → the editor's notation (English function names, „;“ between arguments, decimal comma). */
    fun formula(f: String): String {
        var t = f.substringAfter(':', f).let { if (f.startsWith("of:") || f.startsWith("oooc:") || f.startsWith("msoxl:")) it else f }
        // [.A1:.B3] [$Tabelle2.A1] ['Mein Blatt'.B2:.C3] → A1:B3, Tabelle2!A1, 'Mein Blatt'!B2:C3
        t = Regex("\\[([^\\]]*)]").replace(t) { m ->
            m.groupValues[1].split(':').joinToString(":") { part ->
                val p = part.replace("$", "")
                val sheet = p.substringBeforeLast('.', "")
                val cell = p.substringAfterLast('.')
                if (sheet.isEmpty()) cell else SheetEngine.quoteSheet(sheet.trim('\'').replace("''", "'")) + "!" + cell
            }.replace(Regex("^(.*?!)([A-Z]+\\d+):\\1"), "$1$2:")
        }
        if (!t.startsWith("=")) t = "=$t"
        return OfficeImport.fromExcel(t)
    }

    /** The slides of an .odp file as the slide editor's Markdown. */
    fun odp(bytes: ByteArray): String {
        val root = OdfText.zipEntry(bytes, "content.xml")?.let(OdfText::parse) ?: return ""
        val pres = root.child("body")?.child("presentation") ?: return ""
        val slides = pres.kids().filter { it.localName == "page" }.map { page ->
            val frames = page.kids().filter { it.localName == "frame" || it.localName == "custom-shape" }
            val titleFrame = frames.firstOrNull { it.attr("class") == "title" }
            val title = titleFrame?.let { paragraphs(it).joinToString(" ") }.orEmpty()
            val sub = frames.firstOrNull { it.attr("class") == "subtitle" }
            val body = buildString {
                for (f in frames) {
                    if (f === titleFrame) continue
                    f.descendants("table").forEach { append(tableText(it)).append("\n\n") }
                    f.descendants("text-box").ifEmpty { if (f.localName == "custom-shape") listOf(f) else emptyList() }.forEach { box ->
                        for (c in box.kids()) when (c.localName) {
                            "list" -> list(c, 0, this)
                            "p", "h" -> c.textContent.trim().takeIf { it.isNotEmpty() }?.let { append(it).append("\n\n") }
                        }
                    }
                }
            }.trim()
            val notes = page.child("notes")?.descendants("text-box")?.joinToString("\n") { paragraphs(it).joinToString("\n") }.orEmpty().trim()
            SlideDeck.Slide(
                title = title, body = body, notes = notes,
                layout = if (sub != null && body.lines().size <= 2) SlideDeck.Layout.TITLE else SlideDeck.Layout.CONTENT,
            )
        }
        return if (slides.isEmpty()) "" else SlideDeck.markdown(slides)
    }

    private fun list(el: Element, level: Int, out: StringBuilder) {
        for (item in el.kids().filter { it.localName == "list-item" }) {
            for (c in item.kids()) when (c.localName) {
                "p", "h" -> c.textContent.trim().takeIf { it.isNotEmpty() }?.let { out.append("  ".repeat(level)).append("- ").append(it).append('\n') }
                "list" -> list(c, level + 1, out)
            }
        }
        if (level == 0) out.append('\n')
    }

    private fun paragraphs(el: Element) = el.descendants("p").map { it.textContent.trim() }.filter { it.isNotEmpty() }

    private fun tableText(t: Element): String = rows(t).takeIf { it.isNotEmpty() }?.let { DocBlocks.table(it) }.orEmpty()

    // ── XML helpers ──

    private fun Element.kids(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
    private fun Element.child(name: String): Element? = kids().firstOrNull { it.localName == name }
    private fun Element.attr(name: String): String {
        val a = attributes ?: return ""
        for (i in 0 until a.length) {
            val n = a.item(i)
            if (n.localName == name || n.nodeName.substringAfter(':') == name) return n.nodeValue
        }
        return ""
    }
    private fun Element.descendants(name: String): List<Element> {
        val out = mutableListOf<Element>()
        fun walk(e: Element) { for (c in e.kids()) if (c.localName == name) out += c else walk(c) }
        walk(this)
        return out
    }
}
