package io.github.thepytales.llmhub.docs

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads Excel workbooks (.xlsx) for the table editor (AP-48): cells, formulas (also Excel's shared formulas), number
 * formats, merged cells, conditional formatting, choice lists, frozen rows/columns and column widths. The cells are
 * read with a small scanner instead of a DOM tree, so exports with 100 000 rows stay fast and small in memory; the
 * rest of a sheet (a few elements) is read as XML.
 */
object XlsxReader {
    /** Limits of a sheet in the editor (larger sheets are cut, the rest is reported in [Result.cut]). */
    const val MAX_ROWS = 200_000
    const val MAX_COLS = 1_000
    private const val MAX_CELLS = 4_000_000

    /** Tabs (title → Markdown with formats); [cut] names sheets that had more rows or columns than the editor takes. */
    data class Result(val tabs: List<Pair<String, String>>, val cut: List<String> = emptyList())

    fun read(bytes: ByteArray): List<Pair<String, String>> = readAll(bytes).tabs

    fun readAll(bytes: ByteArray): Result {
        val files = entries(bytes)
        fun text(name: String) = files[name]?.toString(Charsets.UTF_8)
        val shared = text("xl/sharedStrings.xml")?.let { sharedStrings(it, rich = true) }.orEmpty()
        val rels = xml(files["xl/_rels/workbook.xml.rels"])?.children()?.associate { it.attr("Id") to it.attr("Target") }.orEmpty()
        val sheets = xml(files["xl/workbook.xml"])?.all("sheet").orEmpty()
        val stylesRoot = xml(files["xl/styles.xml"])
        val styles = OfficeImport.xlsxCellStyles(stylesRoot)
        val dxfs = dxfs(stylesRoot)
        val cut = mutableListOf<String>()
        val charts = mutableListOf<Pair<String, SheetChart>>()
        val tabs = sheets.mapNotNull { s ->
            val target = rels[s.attr("id")] ?: return@mapNotNull null
            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/" + target.removePrefix("./")
            val x = text(path) ?: return@mapNotNull null
            val name = s.attr("name")
            val sheet = sheet(x, shared, styles, dxfs)
            if (sheet.cut) cut += name
            runCatching { charts += charts(files, path, name) }
            name to sheet.markdown
        }
        if (charts.isEmpty()) return Result(tabs, cut)
        // Each chart goes to the sheet its data is on (the table editor draws a chart from its own sheet's cells).
        val withCharts = tabs.map { (name, md) ->
            val mine = charts.filter { it.first.equals(name, ignoreCase = true) }.map { it.second }
            if (mine.isEmpty()) name to md
            else {
                val meta = SheetMeta.parse(md)
                name to SheetMeta.compose(SheetEngine.grid(md), meta.copy(charts = meta.charts + mine))
            }
        }
        return Result(withCharts, cut)
    }

    /**
     * The charts in the drawing of the sheet at [sheetPath] (AP-48), each with the sheet its data is on: Excel's
     * column, bar, line, pie and scatter charts; histograms and box plots written by Lora come back from their
     * alternative text. Charts whose series do not point at the cells of one sheet are left out.
     */
    private fun charts(files: Map<String, ByteArray>, sheetPath: String, sheetName: String): List<Pair<String, SheetChart>> {
        fun relsOf(part: String): Map<String, String> {
            val dir = part.substringBeforeLast('/')
            val x = xml(files["$dir/_rels/${part.substringAfterLast('/')}.rels"]) ?: return emptyMap()
            return x.children().associate { r -> r.attr("Id") to resolve(dir, r.attr("Target")) }
        }
        val drawing = relsOf(sheetPath).values.firstOrNull { it.contains("/drawings/") } ?: return emptyList()
        val root = xml(files[drawing]) ?: return emptyList()
        val drawingRels = relsOf(drawing)
        return root.all("graphicFrame").mapNotNull { frame ->
            val pr = frame.all("cNvPr").firstOrNull()
            val rid = frame.all("chart").firstOrNull()?.let { c ->
                (0 until c.attributes.length).map { c.attributes.item(it) }.firstOrNull { it.localName == "id" }?.nodeValue
            } ?: return@mapNotNull null
            val chartPath = drawingRels[rid] ?: return@mapNotNull null
            val read = OfficeCharts.read(files[chartPath]) ?: return@mapNotNull null
            val id = Regex("^Lora-Diagramm (\\S+)$").find(pr?.attr("name").orEmpty())?.groupValues?.get(1)
                ?: java.util.UUID.randomUUID().toString().take(8)
            val alt = pr?.attr("descr").orEmpty()
            Regex("^(Histogramm|Boxplot) von ([A-Z]+[0-9]+:[A-Z]+[0-9]+)$").find(alt)?.let { m ->
                val type = if (m.groupValues[1] == "Histogramm") "histogram" else "box"
                return@mapNotNull sheetName to SheetChart(type, m.groupValues[2], read.title, read.xLabel, if (type == "histogram" && read.yLabel == "Anzahl") "" else read.yLabel, id = id)
            }
            read.sheetChart(id)
        }
    }

    /** [target] of a relationship of a part in [dir] as path in the package ("../charts/chart1.xml" → "xl/charts/chart1.xml"). */
    private fun resolve(dir: String, target: String): String {
        if (target.startsWith("/")) return target.removePrefix("/")
        val parts = dir.split('/').filter { it.isNotEmpty() }.toMutableList()
        target.split('/').forEach { p ->
            when (p) {
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                ".", "" -> {}
                else -> parts += p
            }
        }
        return parts.joinToString("/")
    }

    private class Sheet(val markdown: String, val cut: Boolean)

    private fun sheet(x: String, shared: List<String>, styles: List<CellStyle?>, dxfs: List<Pair<String?, String?>>): Sheet {
        val sdStart = x.indexOf("<sheetData")
        val sdOpenEnd = if (sdStart >= 0) x.indexOf('>', sdStart) else -1
        val selfClosed = sdOpenEnd > 0 && x[sdOpenEnd - 1] == '/'
        val sdEnd = if (sdStart >= 0 && !selfClosed) x.indexOf("</sheetData>", sdOpenEnd) else -1
        val rest = when {
            sdStart < 0 -> x
            selfClosed -> x.substring(0, sdStart) + x.substring(sdOpenEnd + 1)
            sdEnd > 0 -> x.substring(0, sdStart) + x.substring(sdEnd + "</sheetData>".length)
            else -> x.substring(0, sdStart)
        }
        val root = xml(rest.toByteArray(Charsets.UTF_8))

        val grid = ArrayList<ArrayList<String>?>()
        val formats = HashMap<String, CellStyle>()
        var maxRow = -1
        var maxCol = -1
        var cut = false
        // Masters of shared formulas: index → (row, column, formula as typed in the editor).
        val masters = HashMap<String, Triple<Int, Int, String>>()
        // Cells Excel filled from a spilling formula (dynamic arrays) are computed again by the editor.
        val spilled = HashSet<Long>()
        fun key(r: Int, c: Int) = r.toLong() * 100_000 + c

        fun put(r: Int, c: Int, v: String) {
            while (grid.size <= r) grid += null
            val row = grid[r] ?: ArrayList<String>().also { grid[r] = it }
            while (row.size <= c) row += ""
            row[c] = v
        }

        if (sdStart >= 0 && !selfClosed) {
            val end = if (sdEnd > 0) sdEnd else x.length
            var i = sdOpenEnd + 1
            var curRow = -1
            var curCol = -1
            while (true) {
                val lt = x.indexOf('<', i)
                if (lt < 0 || lt >= end) break
                val te = x.indexOf('>', lt)
                if (te < 0) break
                val nameEnd = nameEnd(x, lt + 1, te)
                val tag = x.substring(lt + 1, nameEnd)
                when (tag) {
                    "row" -> {
                        curRow = attr(x, lt, te, "r")?.toIntOrNull()?.minus(1) ?: (curRow + 1)
                        curCol = -1
                        i = te + 1
                    }
                    "c" -> {
                        val ref = attr(x, lt, te, "r")?.let(SheetEngine::parseRef)
                        val r = ref?.first ?: curRow.coerceAtLeast(0)
                        val c = ref?.second ?: (curCol + 1)
                        curRow = r
                        curCol = c
                        val selfClose = x[te - 1] == '/'
                        val close = if (selfClose) te else x.indexOf("</c>", te).let { if (it < 0) end else it }
                        i = if (selfClose) te + 1 else close + 4
                        if (r >= MAX_ROWS || c >= MAX_COLS) { cut = true; continue }
                        val t = attr(x, lt, te, "t").orEmpty()
                        val style = styles.getOrNull(attr(x, lt, te, "s")?.toIntOrNull() ?: -1)
                        val date = style?.nf == "date"
                        var formula: String? = null
                        var value = ""
                        var inline: String? = null
                        if (!selfClose) {
                            val fs = child(x, te + 1, close, "f")
                            if (fs != null) {
                                val ft = attr(x, fs.first, fs.second, "t").orEmpty()
                                val body = fs.third?.let(::unescape)
                                when {
                                    ft == "shared" -> {
                                        val si = attr(x, fs.first, fs.second, "si").orEmpty()
                                        if (!body.isNullOrBlank()) {
                                            formula = "=" + OfficeImport.fromExcel(body)
                                            masters[si] = Triple(r, c, formula)
                                        } else masters[si]?.let { (mr, mc, f) -> formula = SheetEngine.shift(f, r - mr, c - mc) }
                                    }
                                    !body.isNullOrBlank() -> {
                                        formula = "=" + OfficeImport.fromExcel(body)
                                        if (ft == "array" && DYNAMIC.containsMatchIn(body)) {
                                            attr(x, fs.first, fs.second, "ref")?.let(SheetEngine::parseRange)?.let { g ->
                                                if ((g[2] - g[0] + 1).toLong() * (g[3] - g[1] + 1) <= 1_000_000) {
                                                    for (rr in g[0]..g[2]) for (cc in g[1]..g[3]) if (rr != r || cc != c) spilled += key(rr, cc)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            child(x, te + 1, close, "v")?.third?.let { value = unescape(it) }
                            if (t == "inlineStr") inline = richTexts(x, te + 1, close)
                        }
                        if (key(r, c) in spilled && formula == null) continue
                        val cell = when {
                            formula != null -> formula!!
                            t == "s" -> shared.getOrNull(value.trim().toIntOrNull() ?: -1).orEmpty()
                            t == "inlineStr" -> inline.orEmpty()
                            t == "b" -> if (value.trim() == "1") "WAHR" else "FALSCH"
                            t == "e" -> germanError(value.trim())
                            t == "d" -> SheetEngine.parseDate(value.trim().take(16).replace('T', ' '))?.let(SheetEngine::dateText) ?: value
                            t == "str" -> value
                            date && value.trim().toDoubleOrNull() != null -> SheetEngine.dateText(value.trim().toDouble())
                            else -> value.trim().toDoubleOrNull()?.let(SheetEngine::formatNumber) ?: value
                        }.replace("\r\n", "\n").replace('\r', '\n')
                        if (cell.isNotEmpty()) {
                            if (grid.size.toLong() * (maxCol + 1).coerceAtLeast(c + 1) > MAX_CELLS) { cut = true; continue }
                            put(r, c, cell)
                            if (r > maxRow) maxRow = r
                            if (c > maxCol) maxCol = c
                        }
                        // Dates typed as dates need no format; the head row's bold is the editor's own.
                        var st = style ?: continue
                        if (date && formula == null) st = st.copy(nf = null)
                        if (r == 0 && st.b) st = st.copy(b = false, fill = st.fill?.takeIf { !it.equals("#DCE6F5", true) })
                        if (!st.isEmpty) formats[SheetEngine.ref(r, c)] = st
                    }
                    else -> i = te + 1
                }
            }
        }
        if (maxRow < 0) return Sheet(SheetEngine.blank(), cut)
        val rows = maxRow + 1
        val cols = maxCol + 1
        val table = (0 until rows).map { r -> val row = grid.getOrNull(r); (0 until cols).map { c -> row?.getOrNull(c).orEmpty() } }
        val kept = formats.filterKeys { k -> SheetEngine.parseRef(k)?.let { it.first < rows && it.second < cols } == true }
        val meta = meta(root, dxfs, rows, cols).copy(cells = kept)
        return Sheet(SheetMeta.compose(table, meta), cut)
    }

    private val DYNAMIC = Regex("(?i)(UNIQUE|FILTER|SORT|SEQUENCE|TEXTSPLIT|TRANSPOSE|VSTACK|HSTACK|CHOOSECOLS|CHOOSEROWS)\\s*\\(")

    /** Frozen panes, column widths, merged cells, conditional formatting and choice lists of a sheet. */
    private fun meta(root: Element?, dxfs: List<Pair<String?, String?>>, rows: Int, cols: Int): SheetMeta {
        root ?: return SheetMeta()
        var meta = SheetMeta()
        root.all("pane").firstOrNull { it.attr("state").startsWith("frozen") }?.let { p ->
            val y = p.attr("ySplit").toDoubleOrNull()?.toInt() ?: 0
            val x = p.attr("xSplit").toDoubleOrNull()?.toInt() ?: 0
            meta = meta.copy(freeze = y >= 1, frozenRows = y.takeIf { it != 1 && (it > 1 || x > 0) }?.coerceAtMost(50), frozenCols = x.coerceIn(0, 20))
        }
        val widths = LinkedHashMap<String, Int>()
        root.all("col").forEach { col ->
            val w = col.attr("width").toDoubleOrNull() ?: return@forEach
            if (col.attr("customWidth") != "1" && col.attr("customWidth") != "true") return@forEach
            val from = (col.attr("min").toIntOrNull() ?: return@forEach) - 1
            val to = ((col.attr("max").toIntOrNull() ?: return@forEach) - 1).coerceAtMost(cols - 1)
            // Excel's width counts characters of the standard font (about 7 px each, plus 5 px padding).
            val dp = (w * 7 + 5).toInt().coerceIn(28, 600)
            for (c in from..to) widths[SheetEngine.colName(c)] = dp
        }
        val merges = root.all("mergeCell").mapNotNull { m ->
            SheetEngine.parseRange(m.attr("ref"))?.takeIf { g -> g[0] < rows && g[1] < cols && (g[0] != g[2] || g[1] != g[3]) }
                ?.let { g -> SheetEngine.rangeRef(g[0], g[1], g[2].coerceAtMost(rows - 1), g[3].coerceAtMost(cols - 1)) }
        }
        val rules = root.all("conditionalFormatting").flatMap { cf ->
            val sqref = cf.attr("sqref").split(' ').firstOrNull { SheetEngine.parseRange(it) != null } ?: return@flatMap emptyList()
            cf.children().filter { it.localName == "cfRule" }.mapNotNull { rule -> rule(sqref, rule, dxfs)?.let { (it to (rule.attr("priority").toIntOrNull() ?: 99)) } }
        }.sortedBy { it.second }.map { it.first }
        val checks = root.all("dataValidation").mapNotNull { v ->
            if (v.attr("type") != "list") return@mapNotNull null
            val range = v.attr("sqref").split(' ').firstOrNull { SheetEngine.parseRange(it) != null } ?: return@mapNotNull null
            val f = v.children().firstOrNull { it.localName == "formula1" }?.textContent?.trim() ?: return@mapNotNull null
            val strict = v.attr("showErrorMessage") == "1" || v.attr("showErrorMessage") == "true"
            if (f.startsWith("\"")) CellCheck(range, f.trim('"').split(',').map { it.trim() }.filter { it.isNotEmpty() }, strict = strict)
            else CellCheck(range, source = f.removePrefix("="), strict = strict)
        }
        return meta.copy(widths = widths, merges = merges, rules = rules, checks = checks)
    }

    /** One rule of the conditional formatting that the editor knows (cell value, text contains, color scale). */
    private fun rule(sqref: String, e: Element, dxfs: List<Pair<String?, String?>>): CondRule? {
        val (fill, color) = dxfs.getOrNull(e.attr("dxfId").toIntOrNull() ?: -1) ?: (null to null)
        fun value(s: String?): String? {
            val t = s?.trim() ?: return null
            return when {
                t.startsWith("\"") -> t.trim('"').replace("\"\"", "\"")
                t.toDoubleOrNull() != null -> SheetEngine.formatNumber(t.toDouble())
                else -> null
            }
        }
        val formulas = e.children().filter { it.localName == "formula" }.map { it.textContent }
        return when (e.attr("type")) {
            "cellIs" -> {
                val a = value(formulas.getOrNull(0)) ?: return null
                when (e.attr("operator")) {
                    "lessThan" -> CondRule(sqref, "lt", a, fill = fill ?: "#FFC7CE", color = color)
                    "greaterThan" -> CondRule(sqref, "gt", a, fill = fill ?: "#FFC7CE", color = color)
                    "equal" -> CondRule(sqref, "eq", a, fill = fill ?: "#FFC7CE", color = color)
                    "between" -> CondRule(sqref, "between", a, value(formulas.getOrNull(1)) ?: return null, fill = fill ?: "#FFC7CE", color = color)
                    else -> null
                }
            }
            "containsText" -> e.attr("text").takeIf { it.isNotEmpty() }?.let { CondRule(sqref, "contains", it, fill = fill ?: "#FFC7CE", color = color) }
            "colorScale" -> {
                val colors = e.all("color").mapNotNull { rgb(it) }
                if (colors.size < 2) null else CondRule(sqref, "scale", fill = colors.first(), color = null, fill2 = colors.last())
            }
            else -> null
        }
    }

    /** Fill and text color of the differential formats (dxfs) the conditional formatting uses. */
    private fun dxfs(root: Element?): List<Pair<String?, String?>> =
        root?.children()?.firstOrNull { it.localName == "dxfs" }?.children()?.filter { it.localName == "dxf" }?.map { d ->
            val fill = d.all("patternFill").firstOrNull()?.let { p -> p.children().firstOrNull { it.localName == "bgColor" }?.let(::rgb) ?: p.children().firstOrNull { it.localName == "fgColor" }?.let(::rgb) }
            val color = d.all("font").firstOrNull()?.children()?.firstOrNull { it.localName == "color" }?.let(::rgb)
            fill to color
        }.orEmpty()

    private fun rgb(e: Element): String? = e.attr("rgb").takeIf { it.length >= 6 }?.let { "#" + it.takeLast(6).uppercase() }

    /** Excel's error codes → the editor's German ones. */
    private fun germanError(code: String): String = when (code) {
        "#N/A" -> "#NV"
        "#VALUE!" -> "#WERT!"
        "#REF!" -> "#BEZUG!"
        "#NUM!" -> "#ZAHL!"
        "#SPILL!" -> "#ÜBERLAUF!"
        "#CALC!" -> "#KALK!"
        else -> code
    }

    // ── Scanner ──

    /**
     * Shared strings (sharedStrings.xml): the text of each `<si>`, runs joined, phonetic hints left out; with [rich]
     * formatted runs become formatting inside the cell (AP-48).
     */
    fun sharedStrings(x: String, rich: Boolean = false): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (true) {
            val s = x.indexOf("<si", i)
            if (s < 0) break
            val te = x.indexOf('>', s)
            if (te < 0) break
            if (x[te - 1] == '/') { out += ""; i = te + 1; continue }
            val e = x.indexOf("</si>", te).let { if (it < 0) x.length else it }
            out += (if (rich) richTexts(x, te + 1, e) else texts(x, te + 1, e)).replace("\r\n", "\n").replace('\r', '\n')
            i = e + 5
        }
        return out
    }

    /** The joined text of all `<t>` elements between [from] and [to], without `<rPh>` (phonetic) parts. */
    private fun texts(x: String, from: Int, to: Int): String {
        val sb = StringBuilder()
        var i = from
        while (i < to) {
            val lt = x.indexOf('<', i)
            if (lt < 0 || lt >= to) break
            val te = x.indexOf('>', lt)
            if (te < 0 || te > to) break
            val name = x.substring(lt + 1, nameEnd(x, lt + 1, te))
            when {
                name == "rPh" -> { i = x.indexOf("</rPh>", te).let { if (it < 0 || it > to) to else it + 6 }; continue }
                name == "t" && x[te - 1] != '/' -> {
                    val e = x.indexOf("</t>", te).let { if (it < 0 || it > to) to else it }
                    sb.append(unescape(x.substring(te + 1, e)))
                    i = e + 4
                    continue
                }
            }
            i = te + 1
        }
        return sb.toString()
    }

    /** Like [texts], but runs (`<r>`) with their own format become cell text with formatting (see [CellRich]). */
    private fun richTexts(x: String, from: Int, to: Int): String {
        val first = x.indexOf("<r>", from)
        if (first < 0 || first >= to || x.indexOf("<rPr", from).let { it < 0 || it >= to }) return texts(x, from, to)
        val runs = ArrayList<Pair<String, io.github.thepytales.llmhub.docs.text.CharFormat>>()
        var i = first
        while (true) {
            val s = x.indexOf("<r>", i)
            if (s < 0 || s >= to) break
            val e = x.indexOf("</r>", s).let { if (it < 0 || it > to) to else it }
            val pr = child(x, s + 3, e, "rPr")?.third.orEmpty()
            fun has(tag: String) = Regex("<$tag(\\s+val=\"(1|true|single|double)\")?\\s*/>").containsMatchIn(pr) || pr.contains("<$tag>")
            fun v(tag: String) = Regex("<$tag\\s[^>]*val=\"([^\"]*)\"").find(pr)?.groupValues?.get(1)
            val color = Regex("<color\\s[^>]*rgb=\"([0-9A-Fa-f]{6,8})\"").find(pr)?.groupValues?.get(1)?.takeLast(6)?.uppercase()
            val f = io.github.thepytales.llmhub.docs.text.CharFormat(
                bold = has("b"), italic = has("i"), underline = has("u"), strike = has("strike"),
                sup = v("vertAlign") == "superscript", sub = v("vertAlign") == "subscript",
                color = color, size = v("sz")?.toDoubleOrNull()?.toInt(), font = v("rFont"),
            )
            runs += texts(x, s + 3, e) to f
            i = e + 4
        }
        return CellRich.fromRuns(runs)
    }

    /** End of a tag name starting at [from] (before the first space, "/" or ">"). */
    private fun nameEnd(x: String, from: Int, te: Int): Int {
        var j = from
        while (j < te && !x[j].isWhitespace() && x[j] != '/' && x[j] != '>') j++
        return j
    }

    /**
     * The first child [name] between [from] and [to]: (start of its tag, end of its start tag, text or null if it is
     * empty like `<f t="shared" si="0"/>`).
     */
    private fun child(x: String, from: Int, to: Int, name: String): Triple<Int, Int, String?>? {
        var i = from
        while (true) {
            val lt = x.indexOf("<$name", i)
            if (lt < 0 || lt >= to) return null
            val after = x.getOrNull(lt + 1 + name.length)
            if (after != ' ' && after != '>' && after != '/') { i = lt + 1; continue }
            val te = x.indexOf('>', lt)
            if (te < 0 || te > to) return null
            if (x[te - 1] == '/') return Triple(lt, te, null)
            val e = x.indexOf("</$name>", te).let { if (it < 0 || it > to) to else it }
            return Triple(lt, te, x.substring(te + 1, e))
        }
    }

    /** Value of the attribute [name] of the tag between [lt] and [te] (null if it has none). */
    private fun attr(x: String, lt: Int, te: Int, name: String): String? {
        var i = lt
        while (true) {
            val at = x.indexOf("$name=", i)
            if (at < 0 || at >= te) return null
            if (!x[at - 1].isWhitespace()) { i = at + 1; continue }
            val q = x[at + name.length + 1]
            if (q != '"' && q != '\'') return null
            val e = x.indexOf(q, at + name.length + 2)
            if (e < 0 || e > te) return null
            return unescape(x.substring(at + name.length + 2, e))
        }
    }

    /** XML entities and Excel's `_x000D_` escapes → characters. */
    fun unescape(s: String): String {
        if (s.indexOf('&') < 0 && s.indexOf("_x") < 0) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '&') {
                val semi = s.indexOf(';', i)
                if (semi in (i + 2)..(i + 10)) {
                    val ent = s.substring(i + 1, semi)
                    val rep = when {
                        ent == "amp" -> "&"
                        ent == "lt" -> "<"
                        ent == "gt" -> ">"
                        ent == "quot" -> "\""
                        ent == "apos" -> "'"
                        ent.startsWith("#x") || ent.startsWith("#X") -> ent.substring(2).toIntOrNull(16)?.let { runCatching { String(Character.toChars(it)) }.getOrNull() }
                        ent.startsWith("#") -> ent.substring(1).toIntOrNull()?.let { runCatching { String(Character.toChars(it)) }.getOrNull() }
                        else -> null
                    }
                    if (rep != null) { sb.append(rep); i = semi + 1; continue }
                }
            } else if (ch == '_' && i + 6 < s.length && s[i + 1] == 'x' && s[i + 6] == '_') {
                val code = s.substring(i + 2, i + 6).toIntOrNull(16)
                if (code != null) { sb.append(code.toChar()); i += 7; continue }
            }
            sb.append(ch)
            i++
        }
        return sb.toString()
    }

    // ── Zip and XML ──

    private fun entries(bytes: ByteArray): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (!e.isDirectory && (e.name.endsWith(".xml") || e.name.endsWith(".rels"))) out[e.name] = zip.readBytes()
                e = zip.nextEntry
            }
        }
        return out
    }

    private fun xml(bytes: ByteArray?): Element? = bytes?.let {
        runCatching {
            val f = DocumentBuilderFactory.newInstance()
            f.isNamespaceAware = true
            runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { f.isExpandEntityReferences = false }
            f.newDocumentBuilder().parse(ByteArrayInputStream(it)).documentElement
        }.getOrNull()
    }

    private fun Node.children(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

    private fun Element.all(local: String): List<Element> {
        val list = getElementsByTagNameNS("*", local)
        return (0 until list.length).map { list.item(it) as Element }
    }

    private fun Element.attr(local: String): String = (0 until attributes.length).map { attributes.item(it) }
        .firstOrNull { it.localName == local || it.nodeName == local }?.nodeValue.orEmpty()
}
