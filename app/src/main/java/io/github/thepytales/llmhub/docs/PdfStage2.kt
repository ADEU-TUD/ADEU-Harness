package io.github.thepytales.llmhub.docs

import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID
import kotlin.math.abs

/*
 * PDF-Werkzeugkasten Stufe 2 (AP-15): text recognition (OCR) for scans, converting to and from PDF, signing, and the
 * AI functions („Frag das PDF“, tables, explaining forms). The parts here are platform-independent; reading and
 * writing the PDF is in PdfExtras (one per platform), the OCR engine of the PC in PdfPlatform.
 */

/** A recognized word and its place on the displayed page (fractions, see [PdfBox]). */
data class OcrWord(val text: String, val box: PdfBox)

data class OcrLine(val words: List<OcrWord>) {
    val text: String get() = words.joinToString(" ") { it.text }
}

/**
 * The recognized text of page [page] (0-based). [positioned]: the words lie where they are on the page (OCR of the
 * PC); otherwise the lines are spread over the page (text of a vision model) – searchable and copyable, but a
 * highlight does not sit exactly on the word.
 */
data class OcrPage(val page: Int, val lines: List<OcrLine>, val positioned: Boolean = true) {
    val text: String get() = lines.joinToString("\n") { it.text }
    val words: Int get() = lines.sumOf { it.words.size }

    companion object {
        /** Lines of plain text (a vision model's answer) spread evenly over the page, one box per line. */
        fun plain(page: Int, text: String): OcrPage {
            val rows = text.replace("\r", "").lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("```") }
            if (rows.isEmpty()) return OcrPage(page, emptyList(), positioned = false)
            val top = 0.06f
            val step = ((0.88f) / rows.size).coerceAtMost(0.03f)
            return OcrPage(
                page,
                rows.mapIndexed { i, row -> OcrLine(listOf(OcrWord(row, PdfBox(0.08f, top + i * step, 0.84f, step * 0.8f)))) },
                positioned = false,
            )
        }
    }
}

/** Text recognition on this device (Windows: the OCR built into Windows). */
interface LocalOcr {
    /** Shown to the user, e.g. „Texterkennung von Windows (Deutsch)“. */
    val name: String

    /** Recognizes the [images] (PNG files of pages); one result per image, null where it failed. */
    fun recognize(images: List<File>): List<OcrPage?>
}

/** Reads the output of the Windows OCR script (see desktop/WindowsOcr): pages, words with pixel boxes, lines. */
object WindowsOcrOutput {
    data class Parsed(val pages: Map<Int, OcrPage>, val engine: String?, val error: String?, val failed: Map<Int, String>)

    fun parse(output: String): Parsed {
        val pages = mutableMapOf<Int, OcrPage>()
        val failed = mutableMapOf<Int, String>()
        var engine: String? = null
        var error: String? = null
        var page = -1
        var w = 1f
        var h = 1f
        val lines = mutableListOf<OcrLine>()
        val words = mutableListOf<OcrWord>()
        fun endLine() {
            if (words.isNotEmpty()) lines += OcrLine(words.toList())
            words.clear()
        }
        fun endPage() {
            endLine()
            if (page >= 0) pages[page] = OcrPage(page, lines.toList())
            lines.clear()
            page = -1
        }
        output.lineSequence().map { it.trimEnd('\r') }.forEach { line ->
            when {
                line.startsWith("#ENGINE ") -> engine = line.removePrefix("#ENGINE ").trim()
                line.startsWith("#ERROR") -> error = line.removePrefix("#ERROR").trim()
                line.startsWith("#PAGE ") -> {
                    endPage()
                    val parts = line.split(' ')
                    page = parts.getOrNull(1)?.toIntOrNull() ?: -1
                    w = parts.getOrNull(2)?.toFloatOrNull()?.takeIf { it > 0 } ?: 1f
                    h = parts.getOrNull(3)?.toFloatOrNull()?.takeIf { it > 0 } ?: 1f
                }
                line.startsWith("#FAIL ") -> {
                    endPage()
                    val rest = line.removePrefix("#FAIL ")
                    rest.substringBefore(' ').toIntOrNull()?.let { failed[it] = rest.substringAfter(' ', "") }
                }
                line == "#LINE" -> endLine()
                page >= 0 && line.count { it == '\t' } >= 4 -> {
                    val p = line.split('\t', limit = 5)
                    val x = p[0].toFloatOrNull()
                    val y = p[1].toFloatOrNull()
                    val bw = p[2].toFloatOrNull()
                    val bh = p[3].toFloatOrNull()
                    if (x != null && y != null && bw != null && bh != null && p[4].isNotBlank()) {
                        words += OcrWord(p[4], PdfBox(x / w, y / h, bw / w, bh / h))
                    }
                }
            }
        }
        endPage()
        return Parsed(pages, engine, error, failed)
    }
}

/** A signature to put on PDFs: drawn [strokes] (fractions of a box of [aspect] = width/height) or an [image]. */
@Serializable
data class Signature(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String = "Unterschrift",
    val strokes: List<List<PdfPoint>> = emptyList(),
    val aspect: Float = 3f,
    /** PNG or JPEG, Base64 (a scanned signature). */
    val image: String? = null,
    /** Ink color 0xRRGGBB (dark blue like a ballpoint pen). */
    val color: Int = 0x1A2A6C,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        /**
         * Strokes drawn on a pad of [w] × [h] px → a signature cropped to the ink with a small margin (fractions of
         * that box). Null when nothing was drawn.
         */
        fun fromPad(strokes: List<List<Pair<Float, Float>>>, name: String = "Unterschrift"): Signature? {
            val pts = strokes.flatten()
            if (pts.size < 2) return null
            val minX = pts.minOf { it.first }
            val maxX = pts.maxOf { it.first }
            val minY = pts.minOf { it.second }
            val maxY = pts.maxOf { it.second }
            val pad = maxOf(maxX - minX, maxY - minY) * 0.04f + 2f
            val x0 = minX - pad
            val y0 = minY - pad
            val bw = (maxX - minX + 2 * pad).coerceAtLeast(1f)
            val bh = (maxY - minY + 2 * pad).coerceAtLeast(1f)
            return Signature(
                name = name,
                strokes = strokes.filter { it.isNotEmpty() }.map { s -> s.map { (x, y) -> PdfPoint((x - x0) / bw, (y - y0) / bh) } },
                aspect = (bw / bh).coerceIn(0.5f, 12f),
            )
        }
    }
}

/** Size of PNG and JPEG pictures from their header (for the shape of a signature from a picture), without decoding. */
object SignatureImages {
    fun size(bytes: ByteArray): Pair<Int, Int>? {
        fun u16(i: Int) = ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
        fun u32(i: Int) = (u16(i) shl 16) or u16(i + 2)
        if (bytes.size > 24 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte()) {
            return (u32(16) to u32(20)).takeIf { it.first > 0 && it.second > 0 }
        }
        if (bytes.size > 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
            var i = 2
            while (i + 9 < bytes.size) {
                if (bytes[i] != 0xFF.toByte()) { i++; continue }
                val marker = bytes[i + 1].toInt() and 0xFF
                // Start of frame (baseline, progressive …): height, then width.
                if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) return (u16(i + 7) to u16(i + 5)).takeIf { it.first > 0 && it.second > 0 }
                if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) { i += 2; continue }
                i += 2 + u16(i + 2)
            }
        }
        return null
    }
}

/** What becomes of a file in the toolbox: a PDF as it is, or converted (AP-15 „Umwandeln“). */
enum class ToPdfKind { PDF, OFFICE, IMAGE, HTML, TEXT, UNSUPPORTED;

    companion object {
        fun of(name: String): ToPdfKind = when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> PDF
            "doc", "docx", "docm", "dot", "dotx", "odt", "rtf", "xls", "xlsx", "xlsm", "ods", "ppt", "pptx", "pptm", "odp" -> OFFICE
            "png", "jpg", "jpeg", "gif", "bmp", "tif", "tiff", "webp" -> IMAGE
            "html", "htm" -> HTML
            "txt", "md", "markdown", "csv" -> TEXT
            else -> UNSUPPORTED
        }
    }
}

/**
 * PDF → Markdown (AP-15 „Umwandeln“, also the base for Word): lines from the text runs of the pages, headings by font
 * size, lists, paragraphs joined, hyphenation at the line end undone, page numbers and running heads dropped, and
 * tables found by [PdfTables] as Markdown tables.
 */
object PdfMarkdown {
    private data class Line(val page: Int, val y: Float, val h: Float, val parts: List<PdfTextRun>) {
        val text: String get() = parts.joinToString(" ") { it.text.trim() }.replace(Regex("\\s+"), " ").trim()
        val x: Float get() = parts.first().x
    }

    private fun lines(runs: List<PdfTextRun>): List<Line> = runs.groupBy { it.page }.toSortedMap().flatMap { (page, list) ->
        val sorted = list.filter { it.text.isNotBlank() }.sortedWith(compareBy({ it.y }, { it.x }))
        val out = mutableListOf<MutableList<PdfTextRun>>()
        sorted.forEach { r ->
            val last = out.lastOrNull()
            if (last != null && abs((last.first().y + last.first().h / 2) - (r.y + r.h / 2)) < maxOf(last.first().h, r.h) * 0.5f) last += r else out += mutableListOf(r)
        }
        out.map { parts -> Line(page, parts.minOf { it.y }, parts.maxOf { it.h }, parts.sortedBy { it.x }) }
    }

    private val BULLET = Regex("^[•●▪◦‣∙·–-]\\s*")
    private val NUMBERED = Regex("^(\\d{1,2}|[a-z])[.)]\\s+")
    private val PAGE_NUMBER = Regex("^(Seite\\s+)?\\d{1,4}(\\s*(/|von)\\s*\\d{1,4})?$", RegexOption.IGNORE_CASE)

    fun build(runs: List<PdfTextRun>, sizes: List<Pair<Float, Float>>): String {
        val all = lines(runs)
        if (all.isEmpty()) return ""
        val pages = all.map { it.page }.distinct().size
        // Running heads and feet: the same text at the top or bottom of most pages.
        val repeated = if (pages < 3) emptySet() else all.groupBy { it.text.replace(Regex("\\d+"), "#") }
            .filter { (_, ls) -> ls.map { it.page }.distinct().size >= pages * 0.6 && ls.all { l -> sizes.getOrNull(l.page)?.let { (_, ph) -> l.y < ph * 0.1f || l.y > ph * 0.88f } == true } }
            .keys
        val body = all.filter { l ->
            val ph = sizes.getOrNull(l.page)?.second ?: Float.MAX_VALUE
            val edge = l.y < ph * 0.08f || l.y > ph * 0.9f
            !(edge && PAGE_NUMBER.matches(l.text)) && l.text.replace(Regex("\\d+"), "#") !in repeated
        }
        val bodySize = body.flatMap { l -> List(l.text.length.coerceAtMost(200)) { l.h } }.sorted().let { if (it.isEmpty()) 10f else it[it.size / 2] }
        val found = PdfTables.detect(runs)
        val tables = found.associateBy { it.page to it.top }
        val tableLines = found.flatMap { t -> body.filter { l -> l.page == t.page && l.y >= t.top - 0.5f && l.y <= t.bottom + 0.5f } }.toSet()
        val text = body.filter { it !in tableLines }
        // The usual distance from line to line within a paragraph (most frequent one); a larger one starts a paragraph.
        val pitch = text.zipWithNext().filter { (a, b) -> a.page == b.page && abs(a.h - b.h) < a.h * 0.15f && b.y - a.y in 0.5f..a.h * 4f }
            .map { (a, b) -> (Math.round((b.y - a.y) * 2) / 2f) }
            .groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<Float, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()?.takeIf { it.value >= 2 }?.key
        // Left margin and right edge of the text per page: indented runs of lines are lists (bullets drawn as graphics).
        val margin = text.groupBy { it.page }.mapValues { (_, ls) -> ls.groupingBy { Math.round(it.x) }.eachCount().maxByOrNull { it.value }?.key?.toFloat() ?: 0f }
        val right = text.groupBy { it.page }.mapValues { (_, ls) -> ls.maxOf { it.x + it.parts.last().let { r -> r.x + r.w - it.x } } }
        fun indented(l: Line) = l.x - (margin[l.page] ?: l.x) >= 8f
        fun sameIndent(a: Line?, b: Line?) = a != null && b != null && a.page == b.page && abs(a.x - b.x) < 2.5f
        val out = StringBuilder()
        var para = StringBuilder()
        var prev: Line? = null
        var inList = false
        fun flush() {
            if (para.isNotEmpty()) out.append(para.toString().trim()).append(if (inList) "\n" else "\n\n")
            para = StringBuilder()
        }
        fun endList() {
            if (inList) {
                flush()
                inList = false
                if (!out.endsWith("\n\n")) out.append("\n")
            }
        }
        for ((index, l) in body.withIndex()) {
            if (l in tableLines) {
                tables[l.page to l.y]?.let { t ->
                    endList()
                    flush()
                    out.append(t.markdown()).append("\n\n")
                }
                prev = null
                continue
            }
            val t = l.text
            val heading = l.h >= bodySize * 1.25f && t.length < 140 && !t.endsWith(",")
            val next = body.getOrNull(index + 1)
            val p = prev
            val samePage = p != null && p.page == l.page
            val step = if (p != null && samePage) l.y - p.y else Float.MAX_VALUE
            val newParagraph = p == null || step > (pitch?.times(1.25f) ?: (l.h * 2.6f)) || (!samePage && p.text.endsWith("."))
            // Part of an indented block of at least two lines at the same place: a list.
            val listLine = indented(l) && (sameIndent(l, next) || sameIndent(l, p))
            when {
                heading -> {
                    endList()
                    flush()
                    out.append(if (l.h >= bodySize * 1.7f) "# " else "## ").append(t.trimEnd('.', ':')).append("\n\n")
                }
                BULLET.containsMatchIn(t) && t.length > 2 -> {
                    flush()
                    inList = true
                    para.append("- ").append(t.replace(BULLET, ""))
                }
                listLine && !(inList && p != null && sameIndent(p, l) && !newParagraph && (p.x + p.parts.last().let { r -> r.x + r.w - p.x }) >= (right[l.page] ?: 0f) - 30f) -> {
                    // A new item, unless the previous one filled its line and this one continues it.
                    flush()
                    inList = true
                    para.append("- ").append(t)
                }
                NUMBERED.containsMatchIn(t) && p != null && (p.text.let { NUMBERED.containsMatchIn(it) || it.endsWith(":") }) -> {
                    flush()
                    inList = true
                    para.append(t)
                }
                else -> {
                    if (inList && !listLine && (newParagraph || !indented(l))) endList()
                    // A new paragraph after a clear gap; across a page break only when the last line ended a sentence.
                    if (newParagraph) flush()
                    when {
                        para.isEmpty() -> para.append(t)
                        para.endsWith("-") && t.first().isLowerCase() -> {
                            para.setLength(para.length - 1)
                            para.append(t)
                        }
                        else -> para.append(' ').append(t)
                    }
                }
            }
            prev = l
        }
        endList()
        flush()
        return out.toString().replace(Regex("\n{3,}"), "\n\n").trim()
    }
}

/**
 * A table found in a PDF: rows of cells, its vertical extent in points (from the top) on its first page, the last page
 * it reaches (AP-95: tables over several pages are joined, the repeated head row only once) and cells spanning several
 * columns as merges (row, first column, last column).
 */
data class PdfTable(
    val page: Int,
    val top: Float,
    val bottom: Float,
    val rows: List<List<String>>,
    val lastPage: Int = page,
    val merges: List<IntArray> = emptyList(),
) {
    val columns: Int get() = rows.maxOfOrNull { it.size } ?: 0

    fun markdown(): String {
        val n = columns
        fun row(cells: List<String>) = "| " + (cells + List(n - cells.size) { "" }).joinToString(" | ") { it.replace("|", "\\|") } + " |"
        return (listOf(row(rows.first()), "|" + " --- |".repeat(n)) + rows.drop(1).map(::row)).joinToString("\n")
    }

    /** Pages as people say them: „Seite 3“ or „Seiten 3–5“. */
    val pages: String get() = if (lastPage == page) "Seite ${page + 1}" else "Seiten ${page + 1}–${lastPage + 1}"
}

/**
 * Tabellen herausziehen (AP-15, AP-95) on the device: lines with two or more cells (text runs apart by a wide gap) whose
 * columns line up over at least two rows. Columns are bands of the cells' widths (right-aligned amounts and centred
 * values land in their column, not in the one whose start is nearest); a line above the head whose cells each span
 * several columns („Ansatz in Euro“ over 2026 and 2027) is kept as group row with merged cells; a table that goes on at
 * the top of the next page with the same columns is joined, its repeated head row (and group row) left out. Works for
 * PDFs from Word, Excel or LaTeX; scans need the Texterkennung first (it gives the page a text layer).
 */
object PdfTables {
    private data class Row(val y: Float, val h: Float, val cells: List<PdfTextRun>)

    fun detect(runs: List<PdfTextRun>): List<PdfTable> = join(runs.groupBy { it.page }.toSortedMap().flatMap { (page, list) -> onPage(page, list) })

    private fun onPage(page: Int, list: List<PdfTextRun>): List<PdfTable> {
        val sorted = list.filter { it.text.isNotBlank() }.sortedWith(compareBy({ it.y }, { it.x }))
        val rows = mutableListOf<MutableList<PdfTextRun>>()
        sorted.forEach { r ->
            val last = rows.lastOrNull()
            if (last != null && abs((last.first().y + last.first().h / 2) - (r.y + r.h / 2)) < maxOf(last.first().h, r.h) * 0.5f) last += r else rows += mutableListOf(r)
        }
        val lines = rows.map { cells -> Row(cells.minOf { it.y }, cells.maxOf { it.h }, cells.sortedBy { it.x }) }
        val tables = mutableListOf<PdfTable>()
        var i = 0
        while (i < lines.size) {
            val first = lines[i]
            if (first.cells.size < 2) { i++; continue }
            val block = mutableListOf(first)
            var j = i + 1
            while (j < lines.size) {
                val prev = block.last()
                val next = lines[j]
                // Rows of a table are further apart than lines of text (padding); a gap of more than a few lines ends it.
                val close = next.y - prev.y < maxOf(prev.h, next.h) * 4.8f
                if (!close || next.cells.size < 2 || !aligned(block.first(), next)) break
                block += next
                j++
            }
            if (block.size >= 2) {
                val bands = bands(block)
                // A group row right above: every cell reaches over two or more columns („Ansatz in Euro“ over 2026 and 2027).
                val above = lines.getOrNull(i - 1)?.takeIf { g ->
                    first.y - g.y < maxOf(g.h, first.h) * 2.6f && g.cells.isNotEmpty() && g.cells.size < bands.size &&
                        g.cells.all { c -> span(c, bands).let { it.last > it.first } } && tables.none { it.bottom >= g.y }
                }
                val merges = mutableListOf<IntArray>()
                val rowsText = (listOfNotNull(above) + block).mapIndexed { r, row ->
                    val cells = MutableList(bands.size) { "" }
                    row.cells.forEach { cell ->
                        val k = if (row === above) span(cell, bands).also { sp -> merges += intArrayOf(r, sp.first, sp.last) }.first
                        else column(cell.x + cell.w / 2, bands)
                        cells[k] = (cells[k] + " " + cell.text.trim()).trim()
                    }
                    cells
                }
                tables += PdfTable(page, (above ?: first).y, block.last().y, rowsText, merges = merges)
                i = j
            } else i++
        }
        return tables
    }

    /**
     * Column bands: the x ranges of the cells of the rows with the most cells, overlapping ones joined. A cell then
     * belongs to the band it overlaps most.
     */
    private fun bands(block: List<Row>): List<Pair<Float, Float>> {
        val most = block.maxOf { it.cells.size }
        val spans = block.filter { it.cells.size >= most - 1 }.flatMap { r -> r.cells.map { it.x to it.x + it.w } }.sortedBy { it.first }
        val out = mutableListOf<Pair<Float, Float>>()
        for ((a, b) in spans) {
            val last = out.lastOrNull()
            if (last != null && a <= last.second + 1.5f) out[out.lastIndex] = last.first to maxOf(last.second, b) else out += a to b
        }
        // Too many bands (a ragged row): fall back to the starts of the row with the most cells.
        if (out.size > most) return block.first { it.cells.size == most }.cells.map { it.x to it.x + it.w }
        return out
    }

    /**
     * The column of the point [x]: each column reaches from the middle of the gap to its left neighbour to the middle of
     * the gap to its right one – so right-aligned amounts and centred values land in their own column.
     */
    private fun column(x: Float, bands: List<Pair<Float, Float>>): Int {
        for (k in 0 until bands.lastIndex) if (x < (bands[k].second + bands[k + 1].first) / 2) return k
        return bands.lastIndex
    }

    /** The columns a cell reaches over, from the one of its left edge to the one of its right edge. */
    private fun span(cell: PdfTextRun, bands: List<Pair<Float, Float>>): IntRange =
        column(cell.x + 1, bands)..column(cell.x + cell.w - 1, bands)

    /** Whether [row]'s cells start near the columns of [head] (at least two of them, within 14 pt). */
    private fun aligned(head: Row, row: Row): Boolean {
        val starts = head.cells.map { it.x }
        val hits = row.cells.count { c -> starts.any { abs(it - c.x) < 14f } || head.cells.any { h -> c.x >= h.x - 4 && c.x <= h.x + h.w + 4 } ||
            head.cells.any { h -> c.x + c.w >= h.x - 4 && c.x + c.w <= h.x + h.w + 30 } }
        return hits >= minOf(2, row.cells.size) && abs(row.cells.size - head.cells.size) <= 1
    }

    /**
     * Joins a table that goes on at the top of the next page: same number of columns and either the same head row
     * (repeated by Word, Excel, LaTeX – left out) or the first table there. Group rows repeated with the head go too.
     */
    private fun join(tables: List<PdfTable>): List<PdfTable> {
        val out = mutableListOf<PdfTable>()
        for (t in tables) {
            val prev = out.lastOrNull()
            val firstOnPage = tables.firstOrNull { it.page == t.page } === t
            if (prev != null && t.page == prev.lastPage + 1 && firstOnPage && t.columns == prev.columns && t.columns >= 2) {
                val headSize = prev.merges.map { it[0] }.distinct().size + 1
                val head = prev.rows.take(headSize).map { r -> r.map { it.trim() } }
                val repeated = t.rows.take(headSize).map { r -> r.map { it.trim() } } == head
                val sameHead = t.rows.firstOrNull()?.map { it.trim() } == prev.rows[headSize - 1].map { it.trim() }
                val rest = when {
                    repeated -> t.rows.drop(headSize)
                    sameHead -> t.rows.drop(1)
                    else -> t.rows
                }
                out[out.lastIndex] = prev.copy(rows = prev.rows + rest, lastPage = t.page)
            } else out += t
        }
        return out
    }

    /** All tables as one Markdown text with a heading per table (for Excel: one sheet per table). */
    fun markdown(tables: List<PdfTable>): String = tables.mapIndexed { i, t -> "## Tabelle ${i + 1} (${t.pages})\n\n" + t.markdown() }.joinToString("\n\n")

    /** The tables as Excel sheets: one per table, numbers as numbers, group cells merged. */
    fun sheets(tables: List<PdfTable>): List<XlsxWriter.Sheet> = tables.mapIndexed { i, t ->
        XlsxWriter.Sheet(
            "Tabelle ${i + 1} (${t.pages})".take(31),
            t.rows,
            SheetMeta(merges = t.merges.map { m -> SheetEngine.rangeRef(m[0], m[1], m[0], m[2]) }),
        )
    }
}
