package io.github.thepytales.llmhub.docs

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale
import kotlin.math.abs

/**
 * Look of a cell: bold, italic, text and fill color ("#RRGGBB"), number format and alignment. Number formats:
 * "0", "0.00", "#,##0.00", "eur" (Währung), "0%", "0.0%" and "date".
 */
@Serializable
data class CellStyle(
    val b: Boolean = false,
    val i: Boolean = false,
    val color: String? = null,
    val fill: String? = null,
    val nf: String? = null,
    /** "l", "c" or "r". */
    val align: String? = null,
) {
    val isEmpty: Boolean get() = this == CellStyle()
}

/**
 * A rule of the conditional formatting for [range] ("B2:B30"): [op] is "lt", "gt", "between", "eq", "contains"
 * or "scale" (two-color scale from [fill] for the smallest to [fill2] for the largest value).
 */
@Serializable
data class CondRule(
    val range: String,
    val op: String,
    val a: String = "",
    val b: String = "",
    val fill: String = "#FFC7CE",
    val color: String? = "#9C0006",
    val fill2: String? = null,
)

/**
 * A chart of a range of the sheet: "bar" (Säulen), "hbar" (Balken), "stacked", "line", "pie", "scatter" (Punkt: erste
 * Spalte x), "histogram" or "box" (Boxplot: je Spalte die Rohwerte); the first row and column may hold names. [id]
 * stays the same, so documents and slides can show the chart linked (AP-49).
 */
@Serializable
data class SheetChart(
    val type: String = "bar",
    val range: String,
    val title: String = "",
    val xLabel: String = "",
    val yLabel: String = "",
    val id: String = "",
    /** The first column holds the names even if they are numbers (grades 1 … 6 of a distribution); null: guessed. */
    val labels: Boolean? = null,
)

/**
 * Data validation of [range] (like Excel's "Datenüberprüfung"): a choice list from [list] or from the cells of
 * [source] ("E2:E9", "Listen!A2:A20"); [strict] refuses other values, otherwise the cell is only marked.
 */
@Serializable
data class CellCheck(val range: String, val list: List<String> = emptyList(), val source: String? = null, val strict: Boolean = true)

/**
 * What a sheet keeps besides its cells: formats, conditional formatting, charts and whether the head row stays
 * visible. Stored as a hidden comment after the Markdown table (`<!-- sheet: {…} -->`), so the tab stays a plain
 * Markdown table and the formats survive saving, versions and undo.
 */
@Serializable
data class SheetMeta(
    val cells: Map<String, CellStyle> = emptyMap(),
    val rules: List<CondRule> = emptyList(),
    val charts: List<SheetChart> = emptyList(),
    /** Head row stays at the top while scrolling (and is frozen in Excel). */
    val freeze: Boolean = true,
    /** Merged cells ("A1:C1"); the first cell shows the text. */
    val merges: List<String> = emptyList(),
    /** Rows frozen at the top (null: the head row if [freeze]). */
    val frozenRows: Int? = null,
    /** Columns frozen at the left. */
    val frozenCols: Int = 0,
    /** Column widths in dp by column letter ("B" to 180); others have the standard width. */
    val widths: Map<String, Int> = emptyMap(),
    /** Choice lists and other checks of cell values. */
    val checks: List<CellCheck> = emptyList(),
) {
    val isEmpty: Boolean get() = this == SheetMeta()

    fun style(r: Int, c: Int): CellStyle? = cells[SheetEngine.ref(r, c)]

    /** Rows that stay at the top while scrolling. */
    val topRows: Int get() = frozenRows ?: if (freeze) 1 else 0

    /** Width of column [c] in dp ([default] if it was not set). */
    fun width(c: Int, default: Int = 118): Int = widths[SheetEngine.colName(c)] ?: default

    /** The merged range (top, left, bottom, right) that contains (r, c), or null. */
    fun mergeAt(r: Int, c: Int): IntArray? = mergeRanges.firstOrNull { g -> r in g[0]..g[2] && c in g[1]..g[3] }

    val mergeRanges: List<IntArray> get() = merges.mapNotNull { SheetEngine.parseRange(it) }.filter { it[0] != it[2] || it[1] != it[3] }

    /** Merges the rectangle (merged ranges inside or overlapping it are replaced). */
    fun merge(top: Int, left: Int, bottom: Int, right: Int): SheetMeta {
        if (top == bottom && left == right) return this
        val kept = mergeRanges.filter { g -> g[2] < top || g[0] > bottom || g[3] < left || g[1] > right }
        return copy(merges = kept.map { SheetEngine.rangeRef(it[0], it[1], it[2], it[3]) } + SheetEngine.rangeRef(top, left, bottom, right))
    }

    /** Removes merged ranges that overlap the rectangle. */
    fun unmerge(top: Int, left: Int, bottom: Int, right: Int): SheetMeta =
        copy(merges = mergeRanges.filter { g -> g[2] < top || g[0] > bottom || g[3] < left || g[1] > right }.map { SheetEngine.rangeRef(it[0], it[1], it[2], it[3]) })

    /** The check of cell (r, c) (the last rule that covers it wins, as in Excel). */
    fun checkAt(r: Int, c: Int): CellCheck? = checks.lastOrNull { k -> SheetEngine.parseRange(k.range)?.let { g -> r in g[0]..g[2] && c in g[1]..g[3] } == true }

    /** [transform] applied to the style of every cell of the rectangle. */
    fun restyle(top: Int, left: Int, bottom: Int, right: Int, transform: (CellStyle) -> CellStyle): SheetMeta {
        val next = cells.toMutableMap()
        for (r in top..bottom) for (c in left..right) {
            val key = SheetEngine.ref(r, c)
            val s = transform(next[key] ?: CellStyle())
            if (s.isEmpty) next.remove(key) else next[key] = s
        }
        return copy(cells = next)
    }

    /** Rows rearranged: new row i was old row [order][i] (null = inserted). Formats move with their rows. */
    fun remapRows(order: List<Int?>): SheetMeta = remap(order, rows = true)

    /** Columns rearranged like [remapRows]. */
    fun remapCols(order: List<Int?>): SheetMeta = remap(order, rows = false)

    private fun remap(order: List<Int?>, rows: Boolean): SheetMeta {
        val newOf = HashMap<Int, Int>()
        order.forEachIndexed { i, old -> if (old != null && old !in newOf) newOf[old] = i }
        val cellsNext = cells.mapNotNull { (k, v) ->
            val (r, c) = SheetEngine.parseRef(k) ?: return@mapNotNull null
            val n = newOf[if (rows) r else c] ?: return@mapNotNull null
            (if (rows) SheetEngine.ref(n, c) else SheetEngine.ref(r, n)) to v
        }.toMap()
        fun range(s: String): String? {
            val g = SheetEngine.parseRange(s) ?: return null
            val (lo, hi) = if (rows) g[0] to g[2] else g[1] to g[3]
            val mapped = (lo..hi).mapNotNull { newOf[it] }
            if (mapped.isEmpty()) return null
            return if (rows) SheetEngine.rangeRef(mapped.min(), g[1], mapped.max(), g[3]) else SheetEngine.rangeRef(g[0], mapped.min(), g[2], mapped.max())
        }
        return copy(
            cells = cellsNext,
            rules = rules.mapNotNull { rule -> range(rule.range)?.let { rule.copy(range = it) } },
            charts = charts.mapNotNull { ch -> range(ch.range)?.let { ch.copy(range = it) } },
            merges = merges.mapNotNull { range(it) }.filter { m -> SheetEngine.parseRange(m)?.let { it[0] != it[2] || it[1] != it[3] } == true },
            checks = checks.mapNotNull { k -> range(k.range)?.let { k.copy(range = it) } },
            widths = if (rows) widths else widths.mapNotNull { (k, v) -> SheetEngine.parseRef(k + "1")?.second?.let { newOf[it] }?.let { SheetEngine.colName(it) to v } }.toMap(),
        )
    }

    /** Rows and columns swapped. */
    fun transposed(): SheetMeta {
        fun t(s: String) = SheetEngine.parseRange(s)?.let { g -> SheetEngine.rangeRef(g[1], g[0], g[3], g[2]) } ?: s
        return copy(
            cells = cells.mapNotNull { (k, v) -> SheetEngine.parseRef(k)?.let { (r, c) -> SheetEngine.ref(c, r) to v } }.toMap(),
            rules = rules.map { it.copy(range = t(it.range)) },
            charts = charts.map { it.copy(range = t(it.range)) },
            merges = merges.map(::t),
            checks = checks.map { it.copy(range = t(it.range)) },
            widths = emptyMap(),
            frozenRows = if (frozenCols > 0) frozenCols else frozenRows,
            frozenCols = frozenRows ?: 0,
        )
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }
        private val COMMENT = Regex("(?m)^<!--\\s*sheet:\\s*(\\{.*\\})\\s*-->\\s*$")

        /** The formats stored in the Markdown of a sheet tab. */
        fun parse(markdown: String): SheetMeta {
            // The comment stands after the table: search from the end (fast for sheets with 100 000 rows).
            val at = markdown.lastIndexOf("<!-- sheet:").takeIf { it >= 0 } ?: markdown.lastIndexOf("<!--").takeIf { it >= 0 } ?: return SheetMeta()
            val start = markdown.lastIndexOf('\n', at).let { if (it < 0) 0 else it + 1 }
            return COMMENT.find(markdown.substring(start))?.let { m -> runCatching { json.decodeFromString(serializer(), m.groupValues[1]) }.getOrNull() } ?: SheetMeta()
        }

        /** Markdown of a sheet tab: the table and, if there are any, its formats. */
        fun compose(rows: List<List<String>>, meta: SheetMeta): String {
            val table = SheetEngine.markdown(rows)
            if (meta.isEmpty) return table
            // "--" would end the comment; inside JSON strings it is written as "-\u002d".
            val data = json.encodeToString(serializer(), meta).replace("--", "-\\u002d")
            return table + "\n<!-- sheet: $data -->\n"
        }

        /** The Markdown without the stored formats. */
        fun strip(markdown: String): String {
            val at = markdown.lastIndexOf("<!--").takeIf { it >= 0 } ?: return markdown.trimEnd() + "\n"
            val start = markdown.lastIndexOf('\n', at).let { if (it < 0) 0 else it + 1 }
            return (markdown.substring(0, start) + markdown.substring(start).replace(COMMENT, "")).trimEnd() + "\n"
        }

        /** A number in the format [nf] (null if [nf] is not set or [v] is no number). */
        fun formatValue(v: SheetEngine.Value, nf: String?): String? {
            if (nf.isNullOrBlank() || v !is SheetEngine.Value.Num) return null
            return when (nf) {
                "date" -> SheetEngine.dateText(v.v)
                "eur" -> SheetEngine.formatPattern(v.v, "#.##0,00") + " €"
                "0", "0.00", "#,##0.00", "0%", "0.0%", "0.00%" -> SheetEngine.formatPattern(v.v, nf)
                else -> SheetEngine.formatPattern(v.v, nf)
            }
        }

        /** What the cell (r, c) shows with its number format. */
        fun show(sheet: SheetEngine.Sheet, r: Int, c: Int, style: CellStyle?): String {
            style?.nf?.let { nf -> formatValue(sheet.value(r, c), nf)?.let { return it } }
            return sheet.display(r, c)
        }

        /** Labels of the number formats for menus (key to label). */
        val NUMBER_FORMATS = listOf(
            null to "Standard",
            "0" to "Ganzzahl (1235)",
            "0.00" to "Zahl (1234,57)",
            "#,##0.00" to "Tausender (1.234,57)",
            "eur" to "Währung (1.234,57 €)",
            "0%" to "Prozent (12 %)",
            "0.0%" to "Prozent (12,3 %)",
            "date" to "Datum (03.04.2026)",
        )
    }
}

/** Fill and text color a cell gets from the conditional formatting. */
data class CellPaint(val fill: String?, val color: String?)

/** Conditional formatting, filling series, clipboard and charts of the table editor. */
object SheetTools {
    private const val MAX_CELLS = 40_000

    /** Whether [v] meets [rule] (not for color scales). Empty cells never do. */
    fun matches(rule: CondRule, v: SheetEngine.Value): Boolean {
        val text = SheetEngine.format(v)
        if (text.isEmpty() || v is SheetEngine.Value.Err) return false
        val n = (v as? SheetEngine.Value.Num)?.v
        fun num(s: String) = SheetEngine.numberOrDate(s)
        return when (rule.op) {
            "lt" -> n != null && num(rule.a)?.let { n < it } == true
            "gt" -> n != null && num(rule.a)?.let { n > it } == true
            "between" -> {
                val a = num(rule.a)
                val b = num(rule.b)
                n != null && a != null && b != null && n >= minOf(a, b) && n <= maxOf(a, b)
            }
            "eq" -> num(rule.a)?.let { n != null && abs(n - it) < 1e-9 } ?: text.trim().equals(rule.a.trim(), ignoreCase = true)
            "contains" -> rule.a.isNotEmpty() && text.contains(rule.a, ignoreCase = true)
            else -> false
        }
    }

    /** Colors of all cells that a rule applies to; the first matching rule wins (like the order in Excel). */
    fun conditional(rules: List<CondRule>, sheet: SheetEngine.Sheet): Map<Pair<Int, Int>, CellPaint> {
        val out = HashMap<Pair<Int, Int>, CellPaint>()
        rules.forEach { rule ->
            val g = SheetEngine.parseRange(rule.range) ?: return@forEach
            if ((g[2] - g[0] + 1).toLong() * (g[3] - g[1] + 1) > MAX_CELLS) return@forEach
            val cells = (g[0]..g[2]).flatMap { r -> (g[1]..g[3]).map { c -> r to c } }
            if (rule.op == "scale") {
                val nums = cells.mapNotNull { k -> (sheet.value(k.first, k.second) as? SheetEngine.Value.Num)?.let { k to it.v } }
                if (nums.isEmpty()) return@forEach
                val lo = nums.minOf { it.second }
                val hi = nums.maxOf { it.second }
                nums.forEach { (k, v) ->
                    if (k !in out) out[k] = CellPaint(mix(rule.fill, rule.fill2 ?: "#63BE7B", if (hi > lo) (v - lo) / (hi - lo) else 0.5), null)
                }
            } else {
                cells.forEach { k -> if (k !in out && matches(rule, sheet.value(k.first, k.second))) out[k] = CellPaint(rule.fill, rule.color) }
            }
        }
        return out
    }

    /** "#RRGGBB" → (r, g, b). */
    fun rgb(hex: String): Triple<Int, Int, Int>? {
        val h = hex.trim().removePrefix("#").let { if (it.length == 8) it.substring(2) else it }
        if (h.length != 6) return null
        val v = h.toIntOrNull(16) ?: return null
        return Triple(v shr 16 and 0xFF, v shr 8 and 0xFF, v and 0xFF)
    }

    fun mix(a: String, b: String, t: Double): String {
        val x = rgb(a) ?: return a
        val y = rgb(b) ?: return b
        fun m(p: Int, q: Int) = (p + (q - p) * t.coerceIn(0.0, 1.0)).toInt().coerceIn(0, 255)
        return String.format(Locale.ROOT, "#%02X%02X%02X", m(x.first, y.first), m(x.second, y.second), m(x.third, y.third))
    }

    // ── Filling series ("Ausfüllen") ──

    private val DAY_LISTS = listOf(
        listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag"),
        listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"),
        listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember"),
        listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez"),
        listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"),
    )

    /**
     * The [count] values that follow [seed] like Excel's fill handle: numbers go on (1, 2, 3 or with the step of the
     * last two), dates by a day (or the step of the last two, whole months stay months), weekday and month names
     * cycle, "Woche 1" becomes "Woche 2"; other text repeats.
     */
    fun fillSeries(seed: List<String>, count: Int): List<String> {
        val s = seed.map { it.trim() }.filter { it.isNotEmpty() }
        if (s.isEmpty() || count <= 0) return List(count.coerceAtLeast(0)) { "" }
        // Dates
        val dates = s.map { SheetEngine.parseDate(it) }
        if (dates.all { it != null }) {
            val ds = dates.map { SheetEngine.dateOf(it!!) }
            val iso = s.last().contains('-')
            fun text(d: java.time.LocalDate) = if (iso) d.toString() else SheetEngine.dateText(SheetEngine.serial(d))
            if (ds.size >= 2) {
                val a = ds[ds.size - 2]
                val b = ds.last()
                if (a.dayOfMonth == b.dayOfMonth && (a.monthValue != b.monthValue || a.year != b.year)) {
                    val months = (b.year - a.year) * 12L + (b.monthValue - a.monthValue)
                    return (1..count).map { k -> text(b.plusMonths(months * k)) }
                }
            }
            val step = if (ds.size >= 2) java.time.temporal.ChronoUnit.DAYS.between(ds[ds.size - 2], ds.last()) else 1L
            return (1..count).map { k -> text(ds.last().plusDays(step * k)) }
        }
        // Numbers
        val nums = s.map { XlsxWriter.number(it) }
        if (nums.all { it != null } && s.none { it.endsWith("%") }) {
            val step = if (nums.size >= 2) nums.last()!! - nums[nums.size - 2]!! else 1.0
            return (1..count).map { k -> SheetEngine.formatNumber(round10(nums.last()!! + step * k)) }
        }
        // Weekdays and months
        DAY_LISTS.forEach { list ->
            val idx = s.map { v -> list.indexOfFirst { it.equals(v.removeSuffix("."), ignoreCase = true) } }
            if (idx.all { it >= 0 }) {
                val step = if (idx.size >= 2) (idx.last() - idx[idx.size - 2]).let { if (it == 0) 1 else it } else 1
                val upper = s.last().all { !it.isLetter() || it.isUpperCase() }
                return (1..count).map { k ->
                    val name = list[Math.floorMod(idx.last() + step * k, list.size)]
                    if (upper) name.uppercase() else name
                }
            }
        }
        // Text with a number at the end ("Woche 1", "Gruppe 3a" keeps "a")
        val pattern = Regex("^(.*?)(\\d+)(\\D{0,3})$")
        val parts = s.map { pattern.find(it) }
        if (parts.all { it != null } && parts.map { it!!.groupValues[1] to it.groupValues[3] }.distinct().size == 1) {
            val n = parts.map { it!!.groupValues[2].toLong() }
            val step = if (n.size >= 2) n.last() - n[n.size - 2] else 1L
            val (pre, post) = parts.last()!!.groupValues[1] to parts.last()!!.groupValues[3]
            val width = parts.last()!!.groupValues[2].length.takeIf { parts.last()!!.groupValues[2].startsWith("0") } ?: 0
            return (1..count).map { k -> pre + (n.last() + step * k).toString().padStart(width, '0') + post }
        }
        return (0 until count).map { s[it % s.size] }
    }

    private fun round10(d: Double) = Math.round(d * 1e10) / 1e10

    /**
     * Fills the rectangle down ([down]) or to the right from the first cells of each column (row): formulas are
     * copied with their relative references moved ($-references stay), values go on as series ([fillSeries]).
     */
    fun fill(rows: List<List<String>>, top: Int, left: Int, bottom: Int, right: Int, down: Boolean): List<List<String>> {
        val height = maxOf(rows.size, bottom + 1)
        val width = maxOf(rows.maxOfOrNull { it.size } ?: 1, right + 1)
        val grid = (0 until height).map { r -> (0 until width).map { c -> rows.getOrNull(r)?.getOrNull(c).orEmpty() }.toMutableList() }
        val lines = if (down) (left..right) else (top..bottom)
        for (line in lines) {
            val span = if (down) (top..bottom).toList() else (left..right).toList()
            fun get(i: Int) = if (down) grid[i][line] else grid[line][i]
            fun set(i: Int, v: String) { if (down) grid[i][line] = v else grid[line][i] = v }
            // Seed: the filled cells at the start of the range (at least the first one).
            var k = 0
            while (k < span.size && get(span[k]).isNotBlank()) k++
            // Nothing to fill when the range starts empty or is filled already.
            if (k == 0 || k == span.size) continue
            val seed = span.take(k)
            val rest = span.drop(k)
            if (seed.any { get(it).trimStart().startsWith("=") }) {
                rest.forEachIndexed { j, target ->
                    val from = seed[j % seed.size]
                    val d = target - from
                    set(target, if (down) SheetEngine.shift(get(from), d, 0) else SheetEngine.shift(get(from), 0, d))
                }
            } else {
                fillSeries(seed.map(::get), rest.size).forEachIndexed { j, v -> set(rest[j], v) }
            }
        }
        return grid
    }

    // ── Clipboard ──

    /** Cells as tab-separated text, as Excel puts them on the clipboard (cells with tabs or line breaks quoted). */
    fun toTsv(cells: List<List<String>>): String = cells.joinToString("\r\n") { row ->
        row.joinToString("\t") { v -> if (v.any { it == '\t' || it == '\n' || it == '"' }) "\"" + v.replace("\"", "\"\"") + "\"" else v }
    }

    /**
     * Cells from the clipboard: an HTML table (web pages, Word), a Markdown table, tab-separated text (Excel,
     * Google Sheets, Word tables) or CSV with ";" or ",".
     */
    fun parseClipboard(text: String): List<List<String>> {
        val t = text.replace("\r\n", "\n").replace('\r', '\n').trimEnd('\n')
        if (t.isBlank()) return emptyList()
        if (Regex("(?i)<table[\\s>]").containsMatchIn(t)) return html(t)
        val lines = t.lines()
        if (lines.size >= 2 && lines.all { it.trim().startsWith("|") }) return DocBlocks.tableRows(t)
        if ('\t' in t) return tsv(t)
        if (lines.size >= 2) {
            for (sep in listOf(';', ',')) {
                val counts = lines.map { l -> SeriesData.split(l, sep).size }
                if (counts.first() >= 2 && counts.distinct().size == 1) return lines.map { SeriesData.split(it, sep) }
            }
        }
        return lines.map { listOf(it) }
    }

    private fun tsv(t: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        var atStart = true
        while (i < t.length) {
            val ch = t[i]
            when {
                quoted && ch == '"' && t.getOrNull(i + 1) == '"' -> { cur.append('"'); i++ }
                quoted && ch == '"' -> quoted = false
                !quoted && ch == '"' && atStart -> quoted = true
                !quoted && ch == '\t' -> { row += cur.toString(); cur.clear(); atStart = true; i++; continue }
                !quoted && ch == '\n' -> { row += cur.toString(); cur.clear(); rows += row; row = mutableListOf(); atStart = true; i++; continue }
                else -> cur.append(ch)
            }
            atStart = false
            i++
        }
        row += cur.toString()
        rows += row
        return rows
    }

    private fun html(t: String): List<List<String>> {
        val table = Regex("(?is)<table[\\s>].*?</table>").find(t)?.value ?: t
        fun clean(s: String) = s.replace(Regex("(?i)<br\\s*/?>"), "\n").replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
            .lines().joinToString("\n") { it.trim() }.trim()
        return Regex("(?is)<tr[^>]*>(.*?)</tr>").findAll(table).map { tr ->
            Regex("(?is)<t([dh])([^>]*)>(.*?)</t[dh]>").findAll(tr.groupValues[1]).flatMap { td ->
                val span = Regex("(?i)colspan\\s*=\\s*\"?(\\d+)").find(td.groupValues[2])?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 50) ?: 1
                listOf(clean(td.groupValues[3])) + List(span - 1) { "" }
            }.toList()
        }.filter { it.isNotEmpty() }.toList()
    }

    /** [block] written into [rows] from (r, c) on; the sheet grows as needed. */
    fun pasteAt(rows: List<List<String>>, r: Int, c: Int, block: List<List<String>>): List<List<String>> {
        if (block.isEmpty()) return rows
        val height = maxOf(rows.size, r + block.size)
        val width = maxOf(rows.maxOfOrNull { it.size } ?: 1, c + (block.maxOfOrNull { it.size } ?: 1))
        return (0 until height).map { i ->
            (0 until width).map { j -> block.getOrNull(i - r)?.getOrNull(j - c)?.takeIf { i >= r && j >= c } ?: rows.getOrNull(i)?.getOrNull(j).orEmpty() }
        }
    }

    // ── Charts ──

    /**
     * The chart of [chart]'s range: the first row gives the series names and the first column the labels, if they
     * hold text; a pie chart shows the first series.
     */
    fun chartSpec(chart: SheetChart, sheet: SheetEngine.Sheet): ChartSpec? {
        val g = SheetEngine.parseRange(chart.range) ?: return null
        val raw = chart.type == "histogram" || chart.type == "box" || chart.type == "scatter"
        if ((g[2] - g[0] + 1).toLong() * (g[3] - g[1] + 1) > if (raw) 200_000 else 5_000) return null
        val v = (g[0]..g[2]).map { r -> (g[1]..g[3]).map { c -> sheet.value(r, c) } }
        fun isNum(x: SheetEngine.Value) = x is SheetEngine.Value.Num && !x.date
        if (raw) return rawChart(chart, v, g)
        val (labelCol, headRow) = frame(chart, v)
        val dataCols = if (labelCol) 1 until v[0].size else v[0].indices
        val body = if (headRow) v.drop(1) else v
        if (body.isEmpty() || dataCols.isEmpty()) return null
        val labels = body.mapIndexed { i, row -> if (labelCol) SheetEngine.format(row[0]) else "${i + 1}" }
        val series = dataCols.map { j ->
            ChartSpec.Series(
                if (headRow) SheetEngine.format(v[0][j]) else SheetEngine.colName(g[1] + j),
                body.map { row -> (row[j] as? SheetEngine.Value.Num)?.v ?: 0.0 },
            )
        }
        val type = when (chart.type) { "line" -> ChartSpec.Type.LINE; "pie" -> ChartSpec.Type.PIE; "hbar" -> ChartSpec.Type.HBAR; "stacked" -> ChartSpec.Type.STACKED; else -> ChartSpec.Type.BAR }
        val head = if (labelCol && headRow) SheetEngine.format(v[0][0]) else ""
        return ChartSpec(
            type, chart.title, xLabel = chart.xLabel.ifBlank { head }, yLabel = chart.yLabel, labels = labels,
            series = if (type == ChartSpec.Type.PIE || type == ChartSpec.Type.HBAR) series.take(1) else series, colors = ChartLayout.TUD,
        )
    }

    /** Whether the first column of a chart's cells holds the names (labels) and the first row the series names. */
    private fun frame(chart: SheetChart, v: List<List<SheetEngine.Value>>): Pair<Boolean, Boolean> {
        fun isNum(x: SheetEngine.Value) = x is SheetEngine.Value.Num && !x.date
        val labelCol = v.size > 0 && v[0].size > 1 && (chart.labels ?: v.drop(1).any { row -> !isNum(row[0]) && SheetEngine.format(row[0]).isNotBlank() })
        val dataCols = if (labelCol) 1 until v[0].size else v[0].indices
        val headRow = v.size > 1 && dataCols.any { j -> !isNum(v[0][j]) && SheetEngine.format(v[0][j]).isNotBlank() }
        return labelCol to headRow
    }

    /**
     * Where the series of [chart] are in the workbook (sheet [sheetName]), for Excel's own chart that redraws when the
     * cells change (AP-48): name, categories (x values) and values of each series, as [chartSpec] reads them. Empty
     * for histograms and box plots (Excel gets their classes or quartiles as values); null if the range is invalid.
     */
    fun chartRefs(chart: SheetChart, sheet: SheetEngine.Sheet, sheetName: String): List<OfficeCharts.Refs>? {
        val g = SheetEngine.parseRange(chart.range) ?: return null
        if (chart.type == "histogram" || chart.type == "box") return emptyList()
        val q = SheetEngine.quoteSheet(sheetName)
        fun abs(r0: Int, c0: Int, r1: Int, c1: Int): String {
            val a = "\$" + SheetEngine.colName(c0) + "\$" + (r0 + 1)
            return "$q!" + if (r0 == r1 && c0 == c1) a else a + ":\$" + SheetEngine.colName(c1) + "\$" + (r1 + 1)
        }
        if ((g[2] - g[0] + 1).toLong() * (g[3] - g[1] + 1) > 200_000) return null
        val v = (g[0]..g[2]).map { r -> (g[1]..g[3]).map { c -> sheet.value(r, c) } }
        if (v.isEmpty() || v[0].isEmpty()) return null
        if (chart.type == "scatter") {
            val head = v[0].any { it is SheetEngine.Value.Text && it.v.isNotBlank() }
            val b0 = g[0] + if (head) 1 else 0
            if (b0 > g[2]) return null
            return (g[1] + 1..g[3]).map { c -> OfficeCharts.Refs(if (head) abs(g[0], c, g[0], c) else null, abs(b0, g[1], g[2], g[1]), abs(b0, c, g[2], c)) }
        }
        val (labelCol, headRow) = frame(chart, v)
        val b0 = g[0] + if (headRow) 1 else 0
        if (b0 > g[2]) return null
        val cols = (if (labelCol) g[1] + 1 else g[1])..g[3]
        val refs = cols.map { c -> OfficeCharts.Refs(if (headRow) abs(g[0], c, g[0], c) else null, if (labelCol) abs(b0, g[1], g[2], g[1]) else null, abs(b0, c, g[2], c)) }
        return if (chart.type == "pie" || chart.type == "hbar") refs.take(1) else refs
    }

    /** Scatter (first column x, the others y), histogram and box plot (raw values of each column). */
    private fun rawChart(chart: SheetChart, v: List<List<SheetEngine.Value>>, g: IntArray): ChartSpec? {
        if (v.isEmpty() || v[0].isEmpty()) return null
        val headRow = v[0].any { it is SheetEngine.Value.Text && it.v.isNotBlank() }
        val body = if (headRow) v.drop(1) else v
        fun name(j: Int) = if (headRow) SheetEngine.format(v[0][j]).ifBlank { SheetEngine.colName(g[1] + j) } else SheetEngine.colName(g[1] + j)
        fun nums(j: Int) = body.mapNotNull { (it.getOrNull(j) as? SheetEngine.Value.Num)?.v }
        return when (chart.type) {
            "scatter" -> {
                if (v[0].size < 2) return null
                val series = (1 until v[0].size).map { j ->
                    val pairs = body.mapNotNull { row -> val x = (row[0] as? SheetEngine.Value.Num)?.v; val y = (row.getOrNull(j) as? SheetEngine.Value.Num)?.v; if (x != null && y != null) x to y else null }
                    ChartSpec.Series(name(j), pairs.map { it.second }, pairs.map { it.first })
                }.filter { it.values.isNotEmpty() }
                if (series.isEmpty()) null
                else ChartSpec(ChartSpec.Type.SCATTER, chart.title, xLabel = chart.xLabel.ifBlank { name(0) }, yLabel = chart.yLabel.ifBlank { if (series.size == 1) series[0].name else "" }, series = series, colors = ChartLayout.TUD)
            }
            else -> {
                val series = v[0].indices.map { j -> ChartSpec.Series(name(j), nums(j)) }.filter { it.values.isNotEmpty() }
                if (series.isEmpty()) null
                else ChartSpec(
                    if (chart.type == "box") ChartSpec.Type.BOX else ChartSpec.Type.HISTOGRAM, chart.title,
                    xLabel = chart.xLabel.ifBlank { if (series.size == 1) series[0].name else "" }, yLabel = chart.yLabel,
                    labels = series.map { it.name }, series = if (chart.type == "box") series else series.take(1), colors = ChartLayout.TUD,
                )
            }
        }
    }

    /** Types of charts from a range, for menus (key to label). */
    val CHART_TYPES = listOf(
        "bar" to "Säulendiagramm", "hbar" to "Balkendiagramm", "stacked" to "Gestapelte Säulen", "line" to "Liniendiagramm",
        "pie" to "Kreisdiagramm", "scatter" to "Punktdiagramm", "histogram" to "Histogramm", "box" to "Boxplot",
    )

    /** The charts of a sheet as ```chart blocks (for Word, PDF and the documents). */
    fun chartBlocks(meta: SheetMeta, sheet: SheetEngine.Sheet): String = meta.charts.mapNotNull { chartSpec(it, sheet) }
        .joinToString("") { "\n\n```chart\n" + it.toJson() + "\n```\n" }

    /**
     * A sheet tab for PDF, Word and HTML: the table with computed values in their number formats, followed by its
     * charts. [workbook] resolves references to other sheets.
     */
    fun exportMarkdown(markdown: String, workbook: SheetEngine.Workbook? = null, title: String? = null): String {
        val rows = SheetEngine.grid(markdown)
        val meta = SheetMeta.parse(markdown)
        val sheet = title?.let { workbook?.sheet(it) }?.takeIf { it.rows == rows } ?: SheetEngine.Sheet(rows) { n -> workbook?.sheet(n) }
        val shown = rows.mapIndexed { r, row -> row.indices.map { c -> SheetMeta.show(sheet, r, c, meta.style(r, c)) } }
        return SheetEngine.markdown(shown) + chartBlocks(meta, sheet)
    }

    /** All tabs of a sheet document as workbook (title → rows). */
    fun workbook(tabs: List<DocTab>): SheetEngine.Workbook = SheetEngine.Workbook(tabs.map { it.title to SheetEngine.grid(it.markdown) })

    /** Plain number of a cell for comparisons and sorting (dates as serial numbers). */
    fun sortKey(raw: String): Double? = SheetEngine.numberOrDate(raw)

    // ── Find and replace ──

    /** Options of Suchen/Ersetzen: case, whole cell, in formulas (otherwise in what the cells show). */
    data class Find(val query: String, val matchCase: Boolean = false, val wholeCell: Boolean = false, val inFormulas: Boolean = true)

    private fun hit(text: String, f: Find): Boolean = when {
        f.query.isEmpty() -> false
        f.wholeCell -> text.trim().equals(f.query.trim(), ignoreCase = !f.matchCase)
        else -> text.contains(f.query, ignoreCase = !f.matchCase)
    }

    /** Cells that contain [f]'s text, row by row ([shown] gives what a cell shows, for values of formulas). */
    fun find(rows: List<List<String>>, f: Find, shown: ((Int, Int) -> String)? = null): List<Pair<Int, Int>> {
        if (f.query.isEmpty()) return emptyList()
        val out = ArrayList<Pair<Int, Int>>()
        rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, raw ->
                val formula = raw.trimStart().startsWith("=")
                val text = if (formula && !f.inFormulas && shown != null) shown(r, c) else raw
                if (hit(text, f)) out += r to c
            }
        }
        return out
    }

    /** [raw] with [f]'s text replaced by [with] (a whole cell is replaced completely). */
    fun replaceText(raw: String, f: Find, with: String): String = when {
        f.query.isEmpty() -> raw
        f.wholeCell -> if (hit(raw, f)) with else raw
        else -> Regex(Regex.escape(f.query), if (f.matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)).replace(raw, Regex.escapeReplacement(with))
    }

    /** All rows with [f] replaced in [cells] (null = everywhere); returns the rows and how many cells changed. */
    fun replaceAll(rows: List<List<String>>, f: Find, with: String, cells: Collection<Pair<Int, Int>>? = null): Pair<List<List<String>>, Int> {
        val targets = (cells ?: find(rows, f.copy(inFormulas = true))).toHashSet()
        var n = 0
        val next = rows.mapIndexed { r, row ->
            if (targets.none { it.first == r }) row
            else row.mapIndexed { c, raw ->
                if (r to c !in targets) raw else replaceText(raw, f, with).also { if (it != raw) n++ }
            }
        }
        return next to n
    }

    // ── Choice lists (data validation) ──

    /** The values a [check] offers: its list or the non-empty cells of its source range (also of another sheet). */
    fun options(check: CellCheck, sheet: SheetEngine.Sheet): List<String> {
        if (check.list.isNotEmpty()) return check.list
        val src = check.source?.trim()?.removePrefix("=") ?: return emptyList()
        val bang = src.lastIndexOf('!')
        val target = if (bang > 0) sheet.other(src.substring(0, bang).trim('\'').replace("''", "'")) ?: return emptyList() else sheet
        val g = SheetEngine.parseRange(src.substring(bang + 1).replace("$", "")) ?: return emptyList()
        if ((g[2] - g[0] + 1).toLong() * (g[3] - g[1] + 1) > 5_000) return emptyList()
        return (g[0]..g[2]).flatMap { r -> (g[1]..g[3]).map { c -> target.display(r, c) } }.filter { it.isNotBlank() }.distinct()
    }

    /** Whether [value] is allowed in a cell with [check] (empty is always allowed; case does not matter). */
    fun allowed(check: CellCheck, value: String, sheet: SheetEngine.Sheet): Boolean {
        if (value.isBlank() || value.trimStart().startsWith("=")) return true
        val opts = options(check, sheet)
        return opts.isEmpty() || opts.any { it.trim().equals(value.trim(), ignoreCase = true) }
    }

    /** "ja, nein; vielleicht" → ["ja", "nein", "vielleicht"] (a list as typed in the dialog). */
    fun parseList(text: String): List<String> = text.split(';', ',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}
