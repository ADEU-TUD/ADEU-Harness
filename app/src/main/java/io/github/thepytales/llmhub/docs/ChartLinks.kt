package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.net.JSON
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Charts of a table shown linked in documents and slides (AP-49, like Sheets → Docs): the ```chart block carries a
 * `"link"` to the sheet document, its tab and the chart; when the document is opened or exported, the data comes
 * fresh from the table, so changed grades change the chart.
 */
object ChartLinks {
    /** The chart [chart] (its id, or its position as number) of tab [tab] (id or title) of the sheet document [doc]. */
    data class Link(val doc: String, val tab: String, val chart: String)

    private val BLOCK = Regex("```chart[ \\t]*\\n([\\s\\S]*?)\\n```")

    /** A ```chart block with the data of [spec] and the link. */
    fun block(spec: ChartSpec, link: Link): String = "```chart\n" + withLink(spec.toJson(), link) + "\n```"

    private fun withLink(json: String, link: Link): String {
        fun str(s: String) = JsonPrimitive(s).toString()
        return json.trimEnd().removeSuffix("}") + ",\"link\":{\"doc\":${str(link.doc)},\"tab\":${str(link.tab)},\"chart\":${str(link.chart)}}}"
    }

    /** The link of the JSON of a chart block (null if it is a plain chart). */
    fun linkOf(json: String): Link? {
        val o = runCatching { JSON.parseToJsonElement(json.trim()) as? JsonObject }.getOrNull() ?: return null
        val l = o["link"] as? JsonObject ?: return null
        fun s(k: String) = (l[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return Link(s("doc"), s("tab"), s("chart")).takeIf { it.doc.isNotBlank() }
    }

    /** All links in [markdown]. */
    fun links(markdown: String): List<Link> = BLOCK.findAll(markdown).mapNotNull { linkOf(it.groupValues[1]) }.toList()

    /** [markdown] with every linked chart drawn from fresh data ([resolve] null keeps the block as it is). */
    fun refresh(markdown: String, resolve: (Link) -> ChartSpec?): String {
        if (!markdown.contains("\"link\"")) return markdown
        return BLOCK.replace(markdown) { m ->
            val link = linkOf(m.groupValues[1]) ?: return@replace m.value
            val spec = resolve(link) ?: return@replace m.value
            Regex.escapeReplacement(block(spec, link))
        }
    }

    /** The chart a link points to, computed from the sheet document [docOf] gives (with the other tabs for references). */
    fun resolve(link: Link, docOf: (String) -> LiveDoc?): ChartSpec? {
        val doc = docOf(link.doc)?.takeIf { it.docKind == DocKind.SHEET } ?: return null
        val index = doc.tabs.indexOfFirst { it.id == link.tab }.takeIf { it >= 0 } ?: doc.tabs.indexOfFirst { it.title.equals(link.tab, true) }.takeIf { it >= 0 } ?: return null
        val meta = SheetMeta.parse(doc.tabs[index].markdown)
        val chart = meta.charts.firstOrNull { it.id.isNotEmpty() && it.id == link.chart } ?: link.chart.toIntOrNull()?.let { meta.charts.getOrNull(it) } ?: return null
        return SheetTools.chartSpec(chart, SheetTools.workbook(doc.tabs).sheet(index))
    }

    /** [doc] with its linked charts updated, or null when nothing changed. */
    fun refreshDoc(doc: LiveDoc, docOf: (String) -> LiveDoc?): LiveDoc? {
        if (doc.tabs.none { it.markdown.contains("\"link\"") }) return null
        val cache = HashMap<Link, ChartSpec?>()
        val tabs = doc.tabs.map { t -> t.copy(markdown = refresh(t.markdown) { l -> cache.getOrPut(l) { resolve(l, docOf) } }) }
        return if (tabs == doc.tabs) null else doc.copy(tabs = tabs)
    }

    /** The link to chart [chart] of tab [tabId] of the sheet document [docId]. */
    fun linkTo(docId: String, tabId: String, chart: SheetChart, index: Int) = Link(docId, tabId, chart.id.ifEmpty { index.toString() })
}

/**
 * Calculations Lora does on the device (AP-49 „Lora rechnet“): distribution of a column, key figures per group and a
 * plain explanation of a formula. Results are formulas, so they follow later changes of the data.
 */
object SheetCalc {
    /** A block of cells to write at (row, col) and the chart that belongs to it. */
    data class Block(
        val cells: List<List<String>>, val row: Int, val col: Int, val chart: SheetChart?, val note: String,
        /** Formats of the block's cells (row, column inside the block). */
        val styles: Map<Pair<Int, Int>, CellStyle> = emptyMap(),
    ) {
        /** [meta] with the block's formats and chart. */
        fun meta(meta: SheetMeta): SheetMeta {
            var m = meta
            styles.forEach { (k, st) -> m = m.restyle(row + k.first, col + k.second, row + k.first, col + k.second) { st } }
            return if (chart != null) m.copy(charts = m.charts + chart) else m
        }
    }

    /** The first free column right of the data (one empty column as gap). */
    fun freeColumn(rows: List<List<String>>): Int {
        val used = rows.maxOfOrNull { r -> r.indexOfLast { it.isNotBlank() } } ?: -1
        return used + 2
    }

    private fun abs(col: Int, from: Int, to: Int) = "\$${SheetEngine.colName(col)}\$${from + 1}:\$${SheetEngine.colName(col)}\$${to + 1}"

    /**
     * Distribution of column [col] (Notenverteilung): every value with its count and share – for up to [maxValues]
     * different values, otherwise [bins] classes of equal width. Counts are ZÄHLENWENN/ZÄHLENWENNS formulas.
     */
    fun distribution(rows: List<List<String>>, col: Int, sheet: SheetEngine.Sheet, maxValues: Int = 15, bins: Int = 8): Block? {
        if (rows.size < 2) return null
        val last = rows.lastIndex
        val values = (1..last).map { sheet.value(it, col) }
        val nums = values.mapNotNull { (it as? SheetEngine.Value.Num)?.takeIf { n -> !n.date }?.v }
        val texts = values.filter { it is SheetEngine.Value.Text && it.v.isNotBlank() }.map { SheetEngine.format(it).trim() }
        val head = rows[0].getOrNull(col).orEmpty().ifBlank { SheetEngine.colName(col) }
        val at = freeColumn(rows)
        val range = abs(col, 1, last)
        val k = SheetEngine.colName(at)
        val out = mutableListOf(listOf(head, "Anzahl", "Anteil"))
        val distinct = (if (nums.size >= texts.size) nums.distinct().sorted().map(SheetEngine::formatNumber) else texts.distinctBy { it.lowercase() }.sortedBy { it.lowercase() })
        if (distinct.isEmpty()) return null
        val n: Int
        if (distinct.size <= maxValues || nums.size < texts.size) {
            distinct.take(60).forEachIndexed { i, v ->
                val r = i + 2
                out += listOf(v, "=ZÄHLENWENN($range;$k$r)", "")
            }
            n = out.size - 1
        } else {
            val lo = nums.min()
            val hi = nums.max()
            val step = ((hi - lo) / bins).let { if (it <= 0) 1.0 else it }
            (0 until bins).forEach { i ->
                val a = lo + step * i
                val b = if (i == bins - 1) hi else lo + step * (i + 1)
                val label = SheetEngine.short(a) + "–" + SheetEngine.short(b)
                val upper = if (i == bins - 1) "\"<=\"&${SheetEngine.formatNumber(b)}" else "\"<\"&${SheetEngine.formatNumber(b)}"
                out += listOf(label, "=ZÄHLENWENNS($range;\">=\"&${SheetEngine.formatNumber(a)};$range;$upper)", "")
            }
            n = out.size - 1
        }
        val countCol = SheetEngine.colName(at + 1)
        val total = "SUMME(\$$countCol\$2:\$$countCol\$${n + 1})"
        val cells = out.mapIndexed { i, row -> if (i == 0) row else listOf(row[0], row[1], "=WENN($total=0;0;$countCol${i + 1}/$total)") }
        val chart = SheetChart("bar", SheetEngine.rangeRef(0, at, n, at + 1), "Verteilung: $head", xLabel = head, yLabel = "Anzahl", id = newId(), labels = true)
        val styles = (1..n).associate { (it to 2) to CellStyle(nf = "0%") }
        return Block(cells, 0, at, chart, "$n Werte von „$head“ gezählt", styles)
    }

    /**
     * Key figures of column [valueCol] per group of column [groupCol] (Pivot-Zusammenfassung): Anzahl, Mittelwert, Min,
     * Max as ZÄHLENWENN/MITTELWERTWENN/MINWENNS/MAXWENNS formulas, one row per group.
     */
    fun byGroup(rows: List<List<String>>, groupCol: Int, valueCol: Int, sheet: SheetEngine.Sheet): Block? {
        if (rows.size < 2) return null
        val last = rows.lastIndex
        val groups = (1..last).map { SheetEngine.format(sheet.value(it, groupCol)).trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
            .sortedWith(compareBy({ SheetEngine.numberOrDate(it) == null }, { SheetEngine.numberOrDate(it) ?: 0.0 }, { it.lowercase() }))
        if (groups.isEmpty() || groups.size > 200) return null
        val g = abs(groupCol, 1, last)
        val v = abs(valueCol, 1, last)
        val at = freeColumn(rows)
        val k = SheetEngine.colName(at)
        val gh = rows[0].getOrNull(groupCol).orEmpty().ifBlank { SheetEngine.colName(groupCol) }
        val vh = rows[0].getOrNull(valueCol).orEmpty().ifBlank { SheetEngine.colName(valueCol) }
        val cells = listOf(listOf(gh, "Mittelwert $vh", "Anzahl", "Min", "Max")) + groups.mapIndexed { i, name ->
            val r = i + 2
            listOf(
                name, "=WENNFEHLER(RUNDEN(MITTELWERTWENN($g;$k$r;$v);2);\"–\")", "=ZÄHLENWENN($g;$k$r)",
                "=MINWENNS($v;$g;$k$r)", "=MAXWENNS($v;$g;$k$r)",
            )
        }
        val chart = SheetChart("bar", SheetEngine.rangeRef(0, at, groups.size, at + 1), "Mittelwert $vh je $gh", xLabel = gh, yLabel = vh, id = newId(), labels = true)
        return Block(cells, 0, at, chart, "${groups.size} Gruppen von „$gh“")
    }

    /** [block] written into [rows] (the sheet grows as needed). */
    fun apply(rows: List<List<String>>, block: Block): List<List<String>> = SheetTools.pasteAt(rows, block.row, block.col, block.cells)

    /** A short id for charts (stable for links). */
    fun newId(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(10)

    /**
     * What a formula does, in words and without a model: the functions with their meaning and the ranges with their
     * column headings („SVERWEIS: Nachschlagen … · A2 = Matrikel (Zeile 2)“).
     */
    fun explain(formula: String, rows: List<List<String>>): String {
        if (!formula.trimStart().startsWith("=")) return "Keine Formel – die Zelle enthält einen festen Wert."
        val hints = SheetEngine.FUNCTIONS.toMap()
        val names = Regex("([A-Za-zÄÖÜäöü][A-Za-zÄÖÜäöü0-9.]*)\\s*\\(").findAll(formula).map { it.groupValues[1].uppercase() }.distinct().toList()
        val parts = mutableListOf<String>()
        names.forEach { n ->
            val german = hints.keys.firstOrNull { it == n } ?: hints.keys.firstOrNull { SheetEngine.excelName(it) == SheetEngine.excelName(n) }
            val hint = german?.let { hints[it] } ?: SheetHelp.SIGNATURES[german ?: n]?.let { "Argumente: $it" }
            parts += if (hint != null) "${german ?: n}: ${hint.substringBefore(", z.B.").substringBefore(": =")}" else n
        }
        val refs = Regex("(?<![A-Za-z!'])\\$?([A-Z]{1,3})\\$?(\\d{1,6})(?::\\$?([A-Z]{1,3})\\$?(\\d{1,6}))?(?![\\w(])").findAll(formula).map { m ->
            val col = SheetEngine.parseRef(m.groupValues[1] + "1")!!.second
            val head = rows.firstOrNull()?.getOrNull(col).orEmpty()
            val what = if (m.groupValues[3].isEmpty()) "Zeile ${m.groupValues[2]}" else "Zeilen ${m.groupValues[2]}–${m.groupValues[4]}"
            "${m.value.replace("$", "")} = ${head.ifBlank { "Spalte ${m.groupValues[1]}" }} ($what)"
        }.distinct().toList()
        val cols = Regex("(?<![A-Za-z!'\\d])\\$?([A-Z]{1,3}):\\$?([A-Z]{1,3})(?![\\w(\\d])").findAll(formula).map { m ->
            val a = SheetEngine.parseRef(m.groupValues[1] + "1")!!.second
            "${m.value.replace("$", "")} = ganze Spalte ${rows.firstOrNull()?.getOrNull(a).orEmpty().ifBlank { m.groupValues[1] }}"
        }.distinct().toList()
        val sb = StringBuilder()
        if (parts.isNotEmpty()) sb.append(parts.joinToString(" · "))
        if (refs.isNotEmpty() || cols.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append(". ")
            sb.append("Bezüge: ").append((cols + refs).joinToString(", "))
        }
        if (formula.contains('$')) sb.append(". Mit $ bleibt ein Bezug beim Ausfüllen fest")
        return sb.toString().ifBlank { "Rechnet: ${formula.trim()}" } + "."
    }
}
