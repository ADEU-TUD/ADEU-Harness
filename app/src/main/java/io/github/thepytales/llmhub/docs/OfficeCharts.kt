package io.github.thepytales.llmhub.docs

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10

/**
 * Echte Office-Diagramme (AP-48, AP-49): charts as DrawingML (`c:chartSpace`), the format Excel, Word and PowerPoint
 * share. In a workbook the series point at the cells of the sheet (Excel redraws when the numbers change); in Word and
 * PowerPoint the chart carries its data in a small embedded workbook, so „Daten bearbeiten“ works there too. Reading
 * gives the type, titles and series back – with the cell ranges for the table editor, with the cached values for
 * documents and slides.
 *
 * Box plots have no classic Office chart: they are written as stacked columns (invisible base up to Q1, then the box
 * up to the median and up to Q3); histograms as columns of their classes. Both carry their kind and range in the
 * alternative text („Histogramm von B1:B40“), so the table editor reads them back as they were.
 */
object OfficeCharts {
    const val NS_C = "http://schemas.openxmlformats.org/drawingml/2006/chart"
    const val CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.drawingml.chart+xml"
    const val REL_CHART = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart"
    const val REL_PACKAGE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/package"
    const val XLSX_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    /** Where a series' name, categories (or x values) and values are in a workbook (`Tabelle1!$B$2:$B$9`). */
    data class Refs(val name: String? = null, val cat: String? = null, val values: String? = null)

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")

    private fun num(d: Double): String = if (d == floor(d) && kotlin.math.abs(d) < 1e15) d.toLong().toString() else d.toString()

    /** Classes of a histogram as Sturges' rule gives them (the same as the drawing): labels and counts. */
    fun histogram(values: List<Double>): Pair<List<String>, List<Double>> {
        if (values.isEmpty()) return emptyList<String>() to emptyList()
        val lo = values.min()
        val hi = values.max().let { if (it == lo) it + 1 else it }
        val bins = ceil(log10(values.size.toDouble()) / log10(2.0) + 1).toInt().coerceIn(4, 20)
        val width = (hi - lo) / bins
        val counts = IntArray(bins)
        values.forEach { v -> counts[((v - lo) / width).toInt().coerceIn(0, bins - 1)]++ }
        fun n(d: Double) = SheetEngine.formatNumber(Math.round(d * 100) / 100.0)
        return (0 until bins).map { i -> "${n(lo + i * width)}–${n(lo + (i + 1) * width)}" } to counts.map { it.toDouble() }
    }

    /**
     * The chart Office draws for [spec]: histograms become columns of their classes, box plots stacked columns
     * (base, lower and upper half of the box). Returns null if there is nothing to draw.
     */
    fun drawable(spec: ChartSpec): ChartSpec? {
        if (spec.series.all { it.values.isEmpty() }) return null
        return when (spec.type) {
            ChartSpec.Type.HISTOGRAM -> {
                val s = spec.series.first()
                val (labels, counts) = histogram(s.values)
                spec.copy(type = ChartSpec.Type.BAR, labels = labels, series = listOf(ChartSpec.Series(s.name.ifBlank { "Anzahl" }, counts)), yLabel = spec.yLabel.ifBlank { "Anzahl" })
            }
            ChartSpec.Type.BOX -> {
                val stats = spec.series.map { Stats.of(it.values) }
                spec.copy(
                    type = ChartSpec.Type.STACKED, labels = spec.series.mapIndexed { i, s -> spec.labels.getOrNull(i) ?: s.name },
                    series = listOf(
                        ChartSpec.Series("Q1", stats.map { it.q1 }),
                        ChartSpec.Series("Median", stats.map { it.median - it.q1 }),
                        ChartSpec.Series("Q3", stats.map { it.q3 - it.median }),
                    ),
                )
            }
            else -> spec
        }
    }

    private fun text(t: String, size: Int, bold: Boolean): String =
        "<c:tx><c:rich><a:bodyPr/><a:lstStyle/><a:p><a:pPr><a:defRPr sz=\"${size * 100}\" b=\"${if (bold) 1 else 0}\"/></a:pPr>" +
            "<a:r><a:rPr lang=\"de-DE\" sz=\"${size * 100}\" b=\"${if (bold) 1 else 0}\"/><a:t>${esc(t)}</a:t></a:r></a:p></c:rich></c:tx>"

    private fun title(t: String, size: Int = 14, bold: Boolean = true) = "<c:title>${text(t, size, bold)}<c:overlay val=\"0\"/></c:title>"

    private fun fill(hex: String) = "<a:solidFill><a:srgbClr val=\"${hex.removePrefix("#").uppercase()}\"/></a:solidFill>"

    private fun strData(ref: String?, values: List<String>): String {
        val pts = values.mapIndexed { i, v -> "<c:pt idx=\"$i\"><c:v>${esc(v)}</c:v></c:pt>" }.joinToString("")
        return if (ref != null) "<c:strRef><c:f>${esc(ref)}</c:f><c:strCache><c:ptCount val=\"${values.size}\"/>$pts</c:strCache></c:strRef>"
        else "<c:strLit><c:ptCount val=\"${values.size}\"/>$pts</c:strLit>"
    }

    private fun numData(ref: String?, values: List<Double>): String {
        val pts = values.mapIndexed { i, v -> "<c:pt idx=\"$i\"><c:v>${num(v)}</c:v></c:pt>" }.joinToString("")
        return if (ref != null) "<c:numRef><c:f>${esc(ref)}</c:f><c:numCache><c:formatCode>General</c:formatCode><c:ptCount val=\"${values.size}\"/>$pts</c:numCache></c:numRef>"
        else "<c:numLit><c:formatCode>General</c:formatCode><c:ptCount val=\"${values.size}\"/>$pts</c:numLit>"
    }

    private fun seriesName(name: String, ref: String?): String = when {
        ref != null -> "<c:tx>${strData(ref, listOf(name))}</c:tx>"
        name.isNotBlank() -> "<c:tx><c:v>${esc(name)}</c:v></c:tx>"
        else -> ""
    }

    private fun axis(kind: String, id: Int, cross: Int, pos: String, label: String, grid: Boolean, deleted: Boolean = false): String = buildString {
        append("<c:$kind><c:axId val=\"$id\"/><c:scaling><c:orientation val=\"minMax\"/></c:scaling><c:delete val=\"${if (deleted) 1 else 0}\"/><c:axPos val=\"$pos\"/>")
        if (grid) append("<c:majorGridlines><c:spPr><a:ln w=\"6350\">${fill("#E5E7EB")}</a:ln></c:spPr></c:majorGridlines>")
        if (label.isNotBlank()) append(title(label, 10, false))
        append("<c:numFmt formatCode=\"General\" sourceLinked=\"1\"/><c:majorTickMark val=\"out\"/><c:minorTickMark val=\"none\"/><c:tickLblPos val=\"nextTo\"/>")
        append("<c:crossAx val=\"$cross\"/><c:crosses val=\"autoZero\"/>")
        if (kind == "catAx") append("<c:auto val=\"1\"/><c:lblAlgn val=\"ctr\"/><c:lblOffset val=\"100\"/><c:noMultiLvlLbl val=\"0\"/>")
        else append("<c:crossBetween val=\"between\"/>")
        append("</c:$kind>")
    }

    /**
     * The `chartSpace` part of [spec] (already [drawable]). [refs] give each series' cells (empty: the values are
     * written as literals); [externalData] is the relationship id of an embedded workbook, if there is one.
     */
    fun xml(spec: ChartSpec, refs: List<Refs> = emptyList(), externalData: String? = null, boxBase: Boolean = false): String = buildString {
        val colors = spec.colors.ifEmpty { ChartLayout.TUD }
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        append("<c:chartSpace xmlns:c=\"$NS_C\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
        append("<c:lang val=\"de-DE\"/><c:roundedCorners val=\"0\"/><c:chart>")
        if (spec.title.isNotBlank()) append(title(spec.title)).append("<c:autoTitleDeleted val=\"0\"/>") else append("<c:autoTitleDeleted val=\"1\"/>")
        append("<c:plotArea><c:layout/>")
        val labels = spec.labels
        fun color(i: Int) = colors[i % colors.size]
        when (spec.type) {
            ChartSpec.Type.PIE -> {
                val s = spec.series.first()
                val r = refs.getOrNull(0)
                append("<c:pieChart><c:varyColors val=\"1\"/><c:ser><c:idx val=\"0\"/><c:order val=\"0\"/>").append(seriesName(s.name, r?.name))
                s.values.indices.forEach { i -> append("<c:dPt><c:idx val=\"$i\"/><c:bubble3D val=\"0\"/><c:spPr>${fill(color(i))}<a:ln w=\"12700\">${fill("#FFFFFF")}</a:ln></c:spPr></c:dPt>") }
                append("<c:dLbls><c:showLegendKey val=\"0\"/><c:showVal val=\"0\"/><c:showCatName val=\"0\"/><c:showSerName val=\"0\"/><c:showPercent val=\"1\"/><c:showBubbleSize val=\"0\"/><c:showLeaderLines val=\"1\"/></c:dLbls>")
                if (labels.isNotEmpty() || r?.cat != null) append("<c:cat>").append(strData(r?.cat, labels.take(s.values.size))).append("</c:cat>")
                append("<c:val>").append(numData(r?.values, s.values)).append("</c:val></c:ser><c:firstSliceAng val=\"0\"/></c:pieChart>")
            }
            ChartSpec.Type.SCATTER -> {
                append("<c:scatterChart><c:scatterStyle val=\"lineMarker\"/><c:varyColors val=\"0\"/>")
                spec.series.forEachIndexed { i, s ->
                    val r = refs.getOrNull(i)
                    append("<c:ser><c:idx val=\"$i\"/><c:order val=\"$i\"/>").append(seriesName(s.name, r?.name))
                    append("<c:spPr><a:ln w=\"19050\"><a:noFill/></a:ln></c:spPr>")
                    append("<c:marker><c:symbol val=\"circle\"/><c:size val=\"6\"/><c:spPr>${fill(color(i))}<a:ln><a:noFill/></a:ln></c:spPr></c:marker>")
                    append("<c:xVal>").append(numData(r?.cat, s.x.ifEmpty { s.values.indices.map { it + 1.0 } })).append("</c:xVal>")
                    append("<c:yVal>").append(numData(r?.values, s.values)).append("</c:yVal><c:smooth val=\"0\"/></c:ser>")
                }
                append("<c:axId val=\"5001\"/><c:axId val=\"5002\"/></c:scatterChart>")
                append(axis("valAx", 5001, 5002, "b", spec.xLabel, grid = false))
                append(axis("valAx", 5002, 5001, "l", spec.yLabel, grid = true))
            }
            ChartSpec.Type.LINE -> {
                append("<c:lineChart><c:grouping val=\"standard\"/><c:varyColors val=\"0\"/>")
                spec.series.forEachIndexed { i, s ->
                    val r = refs.getOrNull(i)
                    append("<c:ser><c:idx val=\"$i\"/><c:order val=\"$i\"/>").append(seriesName(s.name, r?.name))
                    append("<c:spPr><a:ln w=\"28575\" cap=\"rnd\">${fill(color(i))}<a:round/></a:ln></c:spPr>")
                    append("<c:marker><c:symbol val=\"circle\"/><c:size val=\"5\"/><c:spPr>${fill(color(i))}</c:spPr></c:marker>")
                    if (labels.isNotEmpty() || r?.cat != null) append("<c:cat>").append(strData(r?.cat, labels.take(s.values.size))).append("</c:cat>")
                    append("<c:val>").append(numData(r?.values, s.values)).append("</c:val><c:smooth val=\"0\"/></c:ser>")
                }
                append("<c:marker val=\"1\"/><c:axId val=\"5001\"/><c:axId val=\"5002\"/></c:lineChart>")
                append(axis("catAx", 5001, 5002, "b", spec.xLabel, grid = false))
                append(axis("valAx", 5002, 5001, "l", spec.yLabel, grid = true))
            }
            else -> {
                val horizontal = spec.type == ChartSpec.Type.HBAR
                val stacked = spec.type == ChartSpec.Type.STACKED
                append("<c:barChart><c:barDir val=\"${if (horizontal) "bar" else "col"}\"/><c:grouping val=\"${if (stacked) "stacked" else "clustered"}\"/><c:varyColors val=\"0\"/>")
                spec.series.forEachIndexed { i, s ->
                    val r = refs.getOrNull(i)
                    append("<c:ser><c:idx val=\"$i\"/><c:order val=\"$i\"/>").append(seriesName(s.name, r?.name))
                    // A box plot's base is invisible; its two halves of the box are outlined.
                    val box = boxBase && i == 0
                    append(if (box) "<c:spPr><a:noFill/><a:ln><a:noFill/></a:ln></c:spPr>" else "<c:spPr>${fill(color(if (boxBase) 0 else i))}" + (if (boxBase) "<a:ln w=\"9525\">${fill("#1F2937")}</a:ln>" else "") + "</c:spPr>")
                    append("<c:invertIfNegative val=\"0\"/>")
                    if (labels.isNotEmpty() || r?.cat != null) append("<c:cat>").append(strData(r?.cat, labels.take(s.values.size))).append("</c:cat>")
                    append("<c:val>").append(numData(r?.values, s.values)).append("</c:val></c:ser>")
                }
                append("<c:gapWidth val=\"${if (boxBase) 80 else 150}\"/>")
                if (stacked) append("<c:overlap val=\"100\"/>")
                append("<c:axId val=\"5001\"/><c:axId val=\"5002\"/></c:barChart>")
                append(axis("catAx", 5001, 5002, if (horizontal) "l" else "b", spec.xLabel, grid = false))
                append(axis("valAx", 5002, 5001, if (horizontal) "b" else "l", spec.yLabel, grid = true))
            }
        }
        append("</c:plotArea>")
        val legend = spec.type == ChartSpec.Type.PIE || (spec.series.size > 1 && !boxBase)
        if (legend) append("<c:legend><c:legendPos val=\"b\"/><c:overlay val=\"0\"/></c:legend>")
        append("<c:plotVisOnly val=\"1\"/><c:dispBlanksAs val=\"gap\"/></c:chart>")
        append("<c:txPr><a:bodyPr/><a:lstStyle/><a:p><a:pPr><a:defRPr sz=\"1000\"/></a:pPr><a:endParaRPr lang=\"de-DE\"/></a:p></c:txPr>")
        if (externalData != null) append("<c:externalData r:id=\"$externalData\"><c:autoUpdate val=\"0\"/></c:externalData>")
        append("</c:chartSpace>")
    }

    // ── Charts with their own data (Word, PowerPoint) ──

    /** A chart part with the workbook it carries: chart XML, its relationships and the workbook bytes. */
    class Embedded(val chartXml: String, val relsXml: String, val workbook: ByteArray)

    private const val SHEET = "Tabelle1"

    /**
     * [spec] as chart with its data in an embedded workbook ([workbookTarget] is the path of the workbook relative to
     * the chart part, e.g. "../embeddings/Microsoft_Excel_Worksheet1.xlsx"); null for a chart without values.
     */
    fun embedded(spec: ChartSpec, workbookTarget: String, cache: File? = null): Embedded? {
        val box = spec.type == ChartSpec.Type.BOX
        val d = drawable(spec) ?: return null
        val rows = mutableListOf<List<String>>()
        val refs = mutableListOf<Refs>()
        fun col(c: Int) = SheetEngine.colName(c)
        fun nf(v: Double) = SheetEngine.formatNumber(v)
        if (d.type == ChartSpec.Type.SCATTER) {
            // Each series in two columns: x and y.
            val height = d.series.maxOf { it.values.size }
            rows += d.series.flatMap { s -> listOf(d.xLabel.ifBlank { "x" }, s.name.ifBlank { "y" }) }
            for (i in 0 until height) rows += d.series.flatMap { s -> listOf(s.x.getOrNull(i)?.let(::nf).orEmpty(), s.values.getOrNull(i)?.let(::nf).orEmpty()) }
            d.series.forEachIndexed { k, s ->
                val n = s.values.size
                refs += Refs("$SHEET!\$${col(2 * k + 1)}\$1", "$SHEET!\$${col(2 * k)}\$2:\$${col(2 * k)}\$${n + 1}", "$SHEET!\$${col(2 * k + 1)}\$2:\$${col(2 * k + 1)}\$${n + 1}")
            }
        } else {
            val n = d.series.maxOf { it.values.size }
            val labels = (0 until n).map { i -> d.labels.getOrNull(i) ?: "${i + 1}" }
            rows += listOf(d.xLabel.ifBlank { "" }) + d.series.map { it.name.ifBlank { d.yLabel.ifBlank { "Wert" } } }
            labels.forEachIndexed { i, l -> rows += listOf(l) + d.series.map { s -> s.values.getOrNull(i)?.let(::nf).orEmpty() } }
            d.series.forEachIndexed { k, _ ->
                refs += Refs("$SHEET!\$${col(k + 1)}\$1", "$SHEET!\$A\$2:\$A\$${n + 1}", "$SHEET!\$${col(k + 1)}\$2:\$${col(k + 1)}\$${n + 1}")
            }
        }
        val dir = cache ?: File(System.getProperty("java.io.tmpdir"))
        dir.mkdirs()
        val tmp = File.createTempFile("diagramm", ".xlsx", dir)
        val workbook = try {
            // Text cells stay text (a category "1" is a name, not a number): the head row is text anyway.
            XlsxWriter.writeSheets(tmp, listOf(XlsxWriter.Sheet(SHEET, rows, SheetMeta(freeze = false))))
            tmp.readBytes()
        } finally {
            tmp.delete()
        }
        val rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rIdData\" Type=\"$REL_PACKAGE\" Target=\"${esc(workbookTarget)}\"/></Relationships>"
        return Embedded(xml(d, refs, "rIdData", boxBase = box), rels, workbook)
    }

    /** The `<a:graphic>` of a chart frame that points at the chart part [rid]. */
    fun graphic(rid: String): String =
        "<a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><a:graphicData uri=\"$NS_C\">" +
            "<c:chart xmlns:c=\"$NS_C\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" r:id=\"$rid\"/></a:graphicData></a:graphic>"

    /** Alternative text of a chart: its title, or kind and range of a histogram/box plot so they come back as such. */
    fun altText(spec: ChartSpec, range: String? = null): String = when {
        spec.type == ChartSpec.Type.HISTOGRAM && range != null -> "Histogramm von $range"
        spec.type == ChartSpec.Type.BOX && range != null -> "Boxplot von $range"
        else -> spec.title.ifBlank { "Diagramm" }
    }

    // ── Reading ──

    /** A series as read: name, where its parts are (if they point at cells) and the values Office cached. */
    data class ReadSeries(
        val name: String, val nameRef: String?, val catRef: String?, val valRef: String?,
        val cats: List<String>, val values: List<Double>, val xs: List<Double>,
    )

    /** A chart as read: [type] as the table editor names it ("bar", "hbar", "stacked", "line", "pie", "scatter"). */
    data class Read(val type: String, val title: String, val xLabel: String, val yLabel: String, val series: List<ReadSeries>) {
        /** As chart with its cached values (documents and slides). */
        fun spec(): ChartSpec? {
            val t = when (type) {
                "hbar" -> ChartSpec.Type.HBAR; "stacked" -> ChartSpec.Type.STACKED; "line" -> ChartSpec.Type.LINE
                "pie" -> ChartSpec.Type.PIE; "scatter" -> ChartSpec.Type.SCATTER; else -> ChartSpec.Type.BAR
            }
            val ss = series.filter { it.values.isNotEmpty() }
            if (ss.isEmpty()) return null
            return ChartSpec(
                t, title, xLabel, yLabel, labels = if (t == ChartSpec.Type.SCATTER) emptyList() else ss.first().cats,
                series = ss.map { ChartSpec.Series(it.name, it.values, if (t == ChartSpec.Type.SCATTER) it.xs else emptyList()) },
                colors = ChartLayout.TUD,
            )
        }

        /**
         * As chart of the table editor: the sheet the data is on and the range that holds the names, categories and
         * values (null if the series do not point at cells of one sheet).
         */
        fun sheetChart(id: String): Pair<String, SheetChart>? {
            val parts = series.flatMap { listOfNotNull(it.nameRef, it.catRef, it.valRef) }.map { ref(it) ?: return null }
            if (parts.isEmpty() || parts.map { it.first.lowercase() }.distinct().size != 1) return null
            val top = parts.minOf { it.second[0] }
            val left = parts.minOf { it.second[1] }
            val bottom = parts.maxOf { it.second[2] }
            val right = parts.maxOf { it.second[3] }
            return parts.first().first to SheetChart(type, SheetEngine.rangeRef(top, left, bottom, right), title, xLabel, yLabel, id = id)
        }
    }

    /** "'Noten 2026'!$B$2:$B$9" → (sheet, top/left/bottom/right). */
    fun ref(f: String): Pair<String, IntArray>? {
        val bang = f.lastIndexOf('!')
        if (bang <= 0) return null
        val sheet = f.substring(0, bang).trim().let { if (it.startsWith("'")) it.trim('\'').replace("''", "'") else it }
        val range = SheetEngine.parseRange(f.substring(bang + 1).replace("$", "")) ?: return null
        return sheet to range
    }

    fun read(bytes: ByteArray?): Read? {
        val root = bytes?.let(::dom) ?: return null
        val chart = root.first("chart") ?: return null
        val plot = chart.first("plotArea") ?: return null
        val kinds = plot.children().filter { it.localName.endsWith("Chart") }
        val main = kinds.firstOrNull() ?: return null
        val type = when (main.localName) {
            "barChart", "bar3DChart" -> {
                val dir = main.first("barDir")?.attr("val")
                val grouping = main.first("grouping")?.attr("val").orEmpty()
                if (grouping.contains("tacked")) "stacked" else if (dir == "bar") "hbar" else "bar"
            }
            "lineChart", "line3DChart", "areaChart", "area3DChart", "radarChart", "stockChart" -> "line"
            "pieChart", "pie3DChart", "doughnutChart", "ofPieChart" -> "pie"
            "scatterChart", "bubbleChart" -> "scatter"
            else -> return null
        }
        fun texts(e: Element?): String = e?.all("t")?.joinToString("") { it.textContent }?.trim().orEmpty()
        val title = chart.children().firstOrNull { it.localName == "title" }?.let(::texts).orEmpty()
        val axes = plot.children().filter { it.localName == "catAx" || it.localName == "valAx" || it.localName == "dateAx" }
        fun axisTitle(pos: Set<String>, kind: String? = null) = axes.firstOrNull { a ->
            (kind == null || a.localName == kind) && a.first("axPos")?.attr("val") in pos
        }?.children()?.firstOrNull { it.localName == "title" }?.let(::texts).orEmpty()
        val xLabel = when (type) {
            "scatter" -> axisTitle(setOf("b", "t"))
            else -> axes.firstOrNull { it.localName == "catAx" || it.localName == "dateAx" }?.children()?.firstOrNull { it.localName == "title" }?.let(::texts).orEmpty()
        }
        val yLabel = when (type) {
            "scatter" -> axisTitle(setOf("l", "r"))
            else -> axes.firstOrNull { it.localName == "valAx" }?.children()?.firstOrNull { it.localName == "title" }?.let(::texts).orEmpty()
        }
        val series = kinds.flatMap { k -> k.children().filter { it.localName == "ser" } }.map { s ->
            fun data(e: Element?): Triple<String?, List<String>, List<Double>> {
                e ?: return Triple(null, emptyList(), emptyList())
                val f = e.all("f").firstOrNull()?.textContent?.trim()
                val pts = e.all("pt").map { (it.attr("idx").toIntOrNull() ?: 0) to (it.first("v")?.textContent ?: "") }.sortedBy { it.first }
                val count = e.all("ptCount").firstOrNull()?.attr("val")?.toIntOrNull() ?: (pts.maxOfOrNull { it.first + 1 } ?: 0)
                val byIdx = pts.toMap()
                val strs = (0 until count).map { byIdx[it].orEmpty() }
                return Triple(f, strs, strs.map { it.trim().toDoubleOrNull() ?: 0.0 })
            }
            val tx = s.children().firstOrNull { it.localName == "tx" }
            val name = tx?.let { t -> t.all("v").firstOrNull()?.textContent ?: texts(t) }.orEmpty()
            val nameRef = tx?.all("f")?.firstOrNull()?.textContent?.trim()
            val cat = data(s.children().firstOrNull { it.localName == "cat" || it.localName == "xVal" })
            val v = data(s.children().firstOrNull { it.localName == "val" || it.localName == "yVal" })
            ReadSeries(name, nameRef, cat.first, v.first, cat.second, v.third, if (type == "scatter") cat.third else emptyList())
        }
        return Read(type, title, xLabel, yLabel, series)
    }

    // ── XML ──

    private fun dom(bytes: ByteArray): Element? = runCatching {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = true
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { f.isExpandEntityReferences = false }
        f.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement
    }.getOrNull()

    private fun Node.children(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

    private fun Element.first(local: String): Element? {
        val list = getElementsByTagNameNS("*", local)
        return if (list.length > 0) list.item(0) as Element else null
    }

    private fun Element.all(local: String): List<Element> {
        val list = getElementsByTagNameNS("*", local)
        return (0 until list.length).map { list.item(it) as Element }
    }

    private fun Element.attr(local: String): String = (0 until attributes.length).map { attributes.item(it) }
        .firstOrNull { it.localName == local || it.nodeName == local }?.nodeValue.orEmpty()
}
