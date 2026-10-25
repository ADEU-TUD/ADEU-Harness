package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.net.JSON
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** A chart described as data: bar, stacked bar, line, scatter, pie, box plot or histogram. */
data class ChartSpec(
    val type: Type,
    val title: String = "",
    val xLabel: String = "",
    val yLabel: String = "",
    val labels: List<String> = emptyList(),
    val series: List<Series> = emptyList(),
) {
    enum class Type { BAR, STACKED, HBAR, LINE, SCATTER, PIE, BOX, HISTOGRAM }

    /** [x] only for scatter plots; for box plots and histograms [values] are the raw data. */
    data class Series(val name: String, val values: List<Double>, val x: List<Double> = emptyList())

    /** The data as Markdown table (for Word, PowerPoint, screen readers). */
    fun table(): String {
        val rows = mutableListOf<List<String>>()
        fun n(v: Double) = if (v == floor(v) && abs(v) < 1e12) v.toLong().toString() else "%.3g".format(v).replace('.', ',')
        when (type) {
            Type.SCATTER -> {
                rows += listOf("Reihe", xLabel.ifBlank { "x" }, yLabel.ifBlank { "y" })
                series.forEach { s -> s.values.indices.forEach { i -> rows += listOf(s.name, n(s.x.getOrElse(i) { i + 1.0 }), n(s.values[i])) } }
            }
            Type.BOX, Type.HISTOGRAM -> {
                rows += listOf("Gruppe", "n", "Min", "Q1", "Median", "Q3", "Max", "M")
                series.forEachIndexed { i, s ->
                    val st = Stats.of(s.values)
                    rows += listOf(labels.getOrNull(i) ?: s.name, st.n.toString(), n(st.min), n(st.q1), n(st.median), n(st.q3), n(st.max), n(st.mean))
                }
            }
            else -> {
                rows += listOf(xLabel.ifBlank { "Kategorie" }) + series.map { it.name.ifBlank { yLabel.ifBlank { "Wert" } } }
                labels.forEachIndexed { i, l -> rows += listOf(l) + series.map { s -> s.values.getOrNull(i)?.let(::n).orEmpty() } }
            }
        }
        return DocBlocks.table(rows)
    }

    companion object {
        /** Reads the JSON the model writes (lenient: numbers as text, "data" instead of "values", one series as list). */
        fun parse(text: String): ChartSpec? {
            val o = runCatching { JSON.parseToJsonElement(text.trim()) as? JsonObject }.getOrNull() ?: return null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
            fun nums(e: Any?): List<Double> = (e as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.let { p -> p.doubleOrNull ?: p.contentOrNull?.replace(',', '.')?.toDoubleOrNull() } }
            val type = when (s("type").lowercase()) {
                "bar", "balken", "column", "säulen" -> Type.BAR
                "stacked", "gestapelt", "stacked_bar" -> Type.STACKED
                "hbar", "horizontal", "barh" -> Type.HBAR
                "line", "linie", "linien" -> Type.LINE
                "scatter", "streu", "streudiagramm" -> Type.SCATTER
                "pie", "kreis", "torte", "donut" -> Type.PIE
                "box", "boxplot" -> Type.BOX
                "histogram", "histogramm" -> Type.HISTOGRAM
                else -> Type.BAR
            }
            val labels = (o["labels"] as? JsonArray ?: o["categories"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            val seriesJson = o["series"] as? JsonArray
            val series = seriesJson?.mapNotNull { e ->
                val so = e as? JsonObject ?: return@mapNotNull null
                ChartSpec.Series(
                    (so["name"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    nums(so["values"] ?: so["data"] ?: so["y"]),
                    nums(so["x"]),
                )
            } ?: listOf(ChartSpec.Series(s("name"), nums(o["values"] ?: o["data"] ?: o["y"]), nums(o["x"])))
            if (series.all { it.values.isEmpty() }) return null
            return ChartSpec(type, s("title"), s("x_label").ifBlank { s("xLabel") }, s("y_label").ifBlank { s("yLabel") }, labels, series)
        }
    }
}

/** Descriptive statistics for box plots and histograms. */
data class Stats(val n: Int, val min: Double, val q1: Double, val median: Double, val q3: Double, val max: Double, val mean: Double) {
    companion object {
        fun quantile(sorted: List<Double>, p: Double): Double {
            if (sorted.isEmpty()) return 0.0
            val h = (sorted.size - 1) * p
            val lo = floor(h).toInt()
            val hi = ceil(h).toInt()
            return sorted[lo] + (h - lo) * (sorted[hi] - sorted[lo])
        }

        fun of(values: List<Double>): Stats {
            val s = values.sorted()
            if (s.isEmpty()) return Stats(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
            return Stats(s.size, s.first(), quantile(s, 0.25), quantile(s, 0.5), quantile(s, 0.75), s.last(), s.average())
        }
    }
}

/**
 * The chart as drawing commands in a fixed 640 × 380 area, so the same layout becomes SVG (documents) and a
 * Compose canvas (chat) – colors with enough contrast, labels at the axes, legend for several series.
 */
object ChartLayout {
    const val W = 640f
    const val H = 380f
    val PALETTE = listOf("#1E40AF", "#C2410C", "#047857", "#7C3AED", "#B91C1C", "#0E7490", "#A16207", "#BE185D")

    sealed interface Shape
    data class Rect(val x: Float, val y: Float, val w: Float, val h: Float, val color: String, val opacity: Float = 1f) : Shape
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: String, val width: Float = 1f) : Shape
    data class Poly(val points: List<Pair<Float, Float>>, val color: String, val width: Float = 2f) : Shape
    data class Dot(val x: Float, val y: Float, val r: Float, val color: String) : Shape
    data class Wedge(val cx: Float, val cy: Float, val r: Float, val start: Float, val sweep: Float, val color: String) : Shape
    data class Label(val x: Float, val y: Float, val text: String, val size: Float = 11f, val color: String = "#444444", val anchor: String = "middle", val bold: Boolean = false, val vertical: Boolean = false) : Shape

    private fun color(i: Int) = PALETTE[i % PALETTE.size]

    /** "Nice" axis ticks (1, 2, 5 × 10^n). */
    fun ticks(lo: Double, hi: Double, count: Int = 5): List<Double> {
        if (hi <= lo) return listOf(lo)
        val raw = (hi - lo) / count
        val mag = 10.0.pow(floor(log10(raw)))
        val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * mag }.first { it >= raw }
        val start = floor(lo / step) * step
        val out = mutableListOf<Double>()
        var v = start
        while (v <= hi + step * 0.001) { out += v; v += step }
        return out
    }

    private fun num(v: Double): String = if (abs(v - Math.round(v)) < 1e-9 && abs(v) < 1e12) Math.round(v).toString() else "%.2f".format(v).trimEnd('0').trimEnd(',', '.').replace('.', ',')

    fun layout(spec: ChartSpec): List<Shape> {
        val out = mutableListOf<Shape>()
        val top = if (spec.title.isNotBlank()) 34f else 14f
        if (spec.title.isNotBlank()) out += Label(W / 2, 20f, spec.title, 14f, "#1A1A1A", bold = true)
        val legend = spec.series.size > 1 && spec.type !in setOf(ChartSpec.Type.BOX, ChartSpec.Type.PIE)
        if (spec.type == ChartSpec.Type.PIE) return out + pie(spec, top)
        val left = 56f
        val right = if (legend) 130f else 18f
        val bottom = 52f
        val plotW = W - left - right
        val plotH = H - top - bottom
        val x0 = left
        val y0 = top + plotH
        // Value range
        val values: List<Double> = when (spec.type) {
            ChartSpec.Type.STACKED -> spec.labels.indices.map { i -> spec.series.sumOf { it.values.getOrElse(i) { 0.0 } } } + 0.0
            ChartSpec.Type.HISTOGRAM -> listOf(0.0)
            else -> spec.series.flatMap { it.values } + if (spec.type in setOf(ChartSpec.Type.BAR, ChartSpec.Type.HBAR)) listOf(0.0) else emptyList()
        }
        if (spec.type == ChartSpec.Type.HISTOGRAM) return out + histogram(spec, x0, y0, plotW, plotH)
        if (spec.type == ChartSpec.Type.HBAR) return out + hbar(spec, top, plotH, values)
        val lo = values.minOrNull() ?: 0.0
        val hi = values.maxOrNull() ?: 1.0
        val t = ticks(min(lo, if (spec.type == ChartSpec.Type.SCATTER || spec.type == ChartSpec.Type.LINE || spec.type == ChartSpec.Type.BOX) lo else 0.0), if (hi == lo) hi + 1 else hi)
        val yMin = t.first()
        val yMax = t.last().coerceAtLeast(yMin + 1e-9)
        fun y(v: Double) = (y0 - (v - yMin) / (yMax - yMin) * plotH).toFloat()
        // Grid and y axis
        t.forEach { v ->
            out += Line(x0, y(v), x0 + plotW, y(v), "#E5E7EB")
            out += Label(x0 - 6, y(v) + 4, num(v), 10f, anchor = "end")
        }
        out += Line(x0, top, x0, y0, "#9CA3AF")
        out += Line(x0, y0, x0 + plotW, y0, "#9CA3AF")
        if (spec.yLabel.isNotBlank()) out += Label(14f, top + plotH / 2, spec.yLabel, 11f, vertical = true)
        if (spec.xLabel.isNotBlank()) out += Label(x0 + plotW / 2, H - 8f, spec.xLabel, 11f)
        when (spec.type) {
            ChartSpec.Type.BAR, ChartSpec.Type.STACKED -> {
                val n = spec.labels.size.coerceAtLeast(spec.series.maxOf { it.values.size })
                val slot = plotW / n.coerceAtLeast(1)
                val groups = if (spec.type == ChartSpec.Type.STACKED) 1 else spec.series.size
                val barW = (slot * 0.75f / groups).coerceAtMost(48f)
                for (i in 0 until n) {
                    val cx = x0 + slot * (i + 0.5f)
                    var stack = 0.0
                    spec.series.forEachIndexed { si, s ->
                        val v = s.values.getOrNull(i) ?: return@forEachIndexed
                        if (spec.type == ChartSpec.Type.STACKED) {
                            out += Rect(cx - barW / 2, y(stack + v), barW, y(stack) - y(stack + v), color(si))
                            stack += v
                        } else {
                            val bx = cx - barW * groups / 2 + barW * si
                            val yy = y(max(v, 0.0)); val yb = y(min(v, 0.0).coerceAtLeast(yMin))
                            out += Rect(bx + 1, yy, barW - 2, yb - yy, color(si))
                            if (groups == 1 && n <= 12) out += Label(bx + barW / 2, yy - 4, num(v), 10f, "#1A1A1A")
                        }
                    }
                    spec.labels.getOrNull(i)?.let { out += Label(cx, y0 + 16, it.take(if (n > 8) 8 else 16), 10f) }
                }
            }
            ChartSpec.Type.LINE -> {
                val n = spec.labels.size.coerceAtLeast(spec.series.maxOf { it.values.size })
                val step = plotW / (n - 1).coerceAtLeast(1)
                spec.series.forEachIndexed { si, s ->
                    val pts = s.values.mapIndexed { i, v -> (x0 + step * i) to y(v) }
                    out += Poly(pts, color(si), 2.5f)
                    pts.forEach { (px, py) -> out += Dot(px, py, 3.5f, color(si)) }
                }
                spec.labels.forEachIndexed { i, l -> if (n <= 14 || i % (n / 10 + 1) == 0) out += Label(x0 + step * i, y0 + 16, l.take(10), 10f) }
            }
            ChartSpec.Type.SCATTER -> {
                val xs = spec.series.flatMap { s -> s.x.ifEmpty { s.values.indices.map { it + 1.0 } } }
                val xt = ticks(xs.minOrNull() ?: 0.0, (xs.maxOrNull() ?: 1.0).let { if (it == xs.minOrNull()) it + 1 else it })
                fun x(v: Double) = (x0 + (v - xt.first()) / (xt.last() - xt.first()).coerceAtLeast(1e-9) * plotW).toFloat()
                xt.forEach { v -> out += Label(x(v), y0 + 16, num(v), 10f) }
                spec.series.forEachIndexed { si, s ->
                    val sx = s.x.ifEmpty { s.values.indices.map { it + 1.0 } }
                    s.values.forEachIndexed { i, v -> out += Dot(x(sx.getOrElse(i) { i + 1.0 }), y(v), 4f, color(si)) }
                }
            }
            ChartSpec.Type.BOX -> {
                val n = spec.series.size
                val slot = plotW / n.coerceAtLeast(1)
                spec.series.forEachIndexed { i, s ->
                    val st = Stats.of(s.values)
                    val cx = x0 + slot * (i + 0.5f)
                    val bw = (slot * 0.45f).coerceAtMost(70f)
                    val iqr = st.q3 - st.q1
                    val sorted = s.values.sorted()
                    val lowW = sorted.firstOrNull { it >= st.q1 - 1.5 * iqr } ?: st.min
                    val highW = sorted.lastOrNull { it <= st.q3 + 1.5 * iqr } ?: st.max
                    out += Line(cx, y(lowW), cx, y(st.q1), "#374151")
                    out += Line(cx, y(st.q3), cx, y(highW), "#374151")
                    out += Line(cx - bw / 4, y(lowW), cx + bw / 4, y(lowW), "#374151")
                    out += Line(cx - bw / 4, y(highW), cx + bw / 4, y(highW), "#374151")
                    out += Rect(cx - bw / 2, y(st.q3), bw, y(st.q1) - y(st.q3), color(i), 0.35f)
                    out += Line(cx - bw / 2, y(st.median), cx + bw / 2, y(st.median), color(i), 2.5f)
                    sorted.filter { it < lowW || it > highW }.forEach { v -> out += Dot(cx, y(v), 3f, "#374151") }
                    out += Label(cx, y0 + 16, (spec.labels.getOrNull(i) ?: s.name).take(16), 10f)
                    out += Label(cx, y0 + 30, "n = ${st.n}", 9f, "#6B7280")
                }
            }
            else -> {}
        }
        if (legend) {
            spec.series.forEachIndexed { si, s ->
                val ly = top + 10 + si * 18f
                out += Rect(W - right + 14, ly - 9, 12f, 12f, color(si))
                out += Label(W - right + 32, ly + 1, s.name.take(18), 11f, anchor = "start")
            }
        }
        return out
    }

    private fun hbar(spec: ChartSpec, top: Float, plotH: Float, values: List<Double>): List<Shape> {
        val out = mutableListOf<Shape>()
        val left = 150f
        val plotW = W - left - 40f
        val t = ticks(min(0.0, values.minOrNull() ?: 0.0), (values.maxOrNull() ?: 1.0).coerceAtLeast(1e-9))
        fun x(v: Double) = (left + (v - t.first()) / (t.last() - t.first()) * plotW).toFloat()
        t.forEach { v -> out += Line(x(v), top, x(v), top + plotH, "#E5E7EB"); out += Label(x(v), top + plotH + 16, num(v), 10f) }
        val n = spec.labels.size.coerceAtLeast(1)
        val slot = plotH / n
        val s = spec.series.first()
        spec.labels.forEachIndexed { i, l ->
            val v = s.values.getOrElse(i) { 0.0 }
            val cy = top + slot * (i + 0.5f)
            val bh = (slot * 0.7f).coerceAtMost(28f)
            out += Rect(x(0.0), cy - bh / 2, x(v) - x(0.0), bh, color(0))
            out += Label(left - 6, cy + 4, l.take(24), 10f, anchor = "end")
            out += Label(x(v) + 4, cy + 4, num(v), 10f, "#1A1A1A", anchor = "start")
        }
        if (spec.xLabel.isNotBlank()) out += Label(left + plotW / 2, H - 8f, spec.xLabel, 11f)
        return out
    }

    private fun histogram(spec: ChartSpec, x0: Float, y0: Float, plotW: Float, plotH: Float): List<Shape> {
        val out = mutableListOf<Shape>()
        val data = spec.series.first().values
        if (data.isEmpty()) return out
        val lo = data.min()
        val hi = data.max().let { if (it == lo) it + 1 else it }
        val bins = ceil(log10(data.size.toDouble()) / log10(2.0) + 1).toInt().coerceIn(4, 20) // Sturges
        val width = (hi - lo) / bins
        val counts = IntArray(bins)
        data.forEach { v -> counts[((v - lo) / width).toInt().coerceIn(0, bins - 1)]++ }
        val yt = ticks(0.0, counts.max().toDouble().coerceAtLeast(1.0))
        fun y(v: Double) = (y0 - v / yt.last() * plotH).toFloat()
        yt.forEach { v -> out += Line(x0, y(v), x0 + plotW, y(v), "#E5E7EB"); out += Label(x0 - 6, y(v) + 4, num(v), 10f, anchor = "end") }
        val bw = plotW / bins
        counts.forEachIndexed { i, c ->
            out += Rect(x0 + bw * i + 1, y(c.toDouble()), bw - 2, y0 - y(c.toDouble()), PALETTE[0])
        }
        (0..bins step (if (bins > 10) 2 else 1)).forEach { i -> out += Label(x0 + bw * i, y0 + 16, num(lo + width * i), 10f) }
        out += Line(x0, y0, x0 + plotW, y0, "#9CA3AF")
        out += Label(x0 + plotW / 2, H - 8f, spec.xLabel.ifBlank { "Wert" }, 11f)
        out += Label(14f, y0 - plotH / 2, spec.yLabel.ifBlank { "Häufigkeit" }, 11f, vertical = true)
        return out
    }

    private fun pie(spec: ChartSpec, top: Float): List<Shape> {
        val out = mutableListOf<Shape>()
        val vals = spec.series.first().values.map { max(it, 0.0) }
        val total = vals.sum().takeIf { it > 0 } ?: return out
        val r = (H - top - 20f) / 2
        val cx = 40f + r
        val cy = top + 10f + r
        var a = -90f
        vals.forEachIndexed { i, v ->
            val sweep = (v / total * 360).toFloat()
            out += Wedge(cx, cy, r, a, sweep, color(i))
            val mid = Math.toRadians((a + sweep / 2).toDouble())
            if (sweep > 18) out += Label((cx + cos(mid) * r * 0.65).toFloat(), (cy + sin(mid) * r * 0.65).toFloat() + 4, "${Math.round(v / total * 100)} %", 11f, "#FFFFFF", bold = true)
            a += sweep
        }
        vals.indices.forEach { i ->
            val ly = top + 20 + i * 20f
            out += Rect(cx + r + 40, ly - 10, 12f, 12f, color(i))
            out += Label(cx + r + 58, ly, "${spec.labels.getOrNull(i) ?: "Teil ${i + 1}"} (${num(vals[i])})", 11f, anchor = "start")
        }
        return out
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** SVG for PDF and HTML documents (with title and a text alternative for screen readers). */
    fun svg(spec: ChartSpec): String = buildString {
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ${W.toInt()} ${H.toInt()}\" width=\"100%\" role=\"img\" aria-label=\"${esc(spec.title.ifBlank { "Diagramm" })}\" ")
        append("style=\"max-width:640px;font-family:'Noto Sans',Arial,sans-serif\">")
        append("<title>${esc(spec.title.ifBlank { "Diagramm" })}</title>")
        layout(spec).forEach { s ->
            when (s) {
                is Rect -> append("<rect x=\"${s.x}\" y=\"${s.y}\" width=\"${max(s.w, 0f)}\" height=\"${max(s.h, 0f)}\" fill=\"${s.color}\" fill-opacity=\"${s.opacity}\"/>")
                is Line -> append("<line x1=\"${s.x1}\" y1=\"${s.y1}\" x2=\"${s.x2}\" y2=\"${s.y2}\" stroke=\"${s.color}\" stroke-width=\"${s.width}\"/>")
                is Poly -> append("<polyline fill=\"none\" stroke=\"${s.color}\" stroke-width=\"${s.width}\" points=\"${s.points.joinToString(" ") { "${it.first},${it.second}" }}\"/>")
                is Dot -> append("<circle cx=\"${s.x}\" cy=\"${s.y}\" r=\"${s.r}\" fill=\"${s.color}\"/>")
                is Wedge -> {
                    val a1 = Math.toRadians(s.start.toDouble()); val a2 = Math.toRadians((s.start + s.sweep).toDouble())
                    val large = if (s.sweep > 180) 1 else 0
                    if (s.sweep >= 359.9f) append("<circle cx=\"${s.cx}\" cy=\"${s.cy}\" r=\"${s.r}\" fill=\"${s.color}\"/>")
                    else append("<path d=\"M${s.cx},${s.cy} L${s.cx + s.r * cos(a1)},${s.cy + s.r * sin(a1)} A${s.r},${s.r} 0 $large 1 ${s.cx + s.r * cos(a2)},${s.cy + s.r * sin(a2)} Z\" fill=\"${s.color}\" stroke=\"#FFFFFF\" stroke-width=\"1.5\"/>")
                }
                is Label -> {
                    val transform = if (s.vertical) " transform=\"rotate(-90 ${s.x} ${s.y})\"" else ""
                    append("<text x=\"${s.x}\" y=\"${s.y}\" font-size=\"${s.size}\" fill=\"${s.color}\" text-anchor=\"${s.anchor}\"${if (s.bold) " font-weight=\"bold\"" else ""}$transform>${esc(s.text)}</text>")
                }
            }
        }
        append("</svg>")
    }
}
