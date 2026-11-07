package io.github.thepytales.llmhub.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import io.github.thepytales.llmhub.docs.text.RichText
import io.github.thepytales.llmhub.docs.text.TabKind
import io.github.thepytales.llmhub.docs.text.TabStop

/**
 * Tabstopps im Fließtext-Editor (AP-44). Compose knows no tab stops: a tab is one character of its own width. The
 * editor measures where each tab stands, works out how much wider it must be to reach its stop (the paragraph's own
 * Tabstopps, else every 12,5 mm from the left margin, like Word) and adds that room as letter spacing after the tab
 * character. Right and centred stops take the width of the text after the tab into account.
 */
internal object FlowTabs {
    /**
     * Room to add after each tab of [rich] (offset → px), from [layout] of the shown text ([shown]: offset in the shown
     * text) laid out with [current] room. [pxPerMm]: the zoom of the page; x = 0 is the left margin.
     */
    fun extras(layout: TextLayoutResult, rich: RichText, shown: (Int) -> Int, current: Map<Int, Float>, pxPerMm: Float): Map<Int, Float> {
        val text = rich.text
        if ('\t' !in text) return emptyMap()
        val n = layout.layoutInput.text.length
        val out = HashMap<Int, Float>()
        var o = text.indexOf('\t')
        while (o >= 0) {
            val so = shown(o)
            if (so + 1 > n) break
            val line = layout.getLineForOffset(so)
            val x0 = layout.getHorizontalPosition(so, true)
            val x1 = if (layout.getLineForOffset(so + 1) == line) layout.getHorizontalPosition(so + 1, true) else layout.getLineRight(line)
            val natural = (x1 - x0 - (current[o] ?: 0f)).coerceAtLeast(0f)
            val p = rich.paras[rich.paraAt(o)]
            val (stopMm, kind) = nextStop(p.tabs, x0 / pxPerMm)
            val stopPx = stopMm * pxPerMm
            // The text after the tab up to the next tab or the end of its line (right and centred stops).
            val after = if (kind == TabKind.LEFT) 0f else {
                val paraEnd = text.indexOf('\n', o + 1).let { if (it < 0) text.length else it }
                val nextTab = text.indexOf('\t', o + 1).let { if (it < 0 || it > paraEnd) paraEnd else it }
                val end = minOf(shown(nextTab), layout.getLineEnd(line, visibleEnd = true)).coerceIn(so + 1, n)
                (layout.getHorizontalPosition(end, true) - x1).coerceAtLeast(0f)
            }
            val target = when (kind) {
                TabKind.LEFT -> stopPx
                TabKind.RIGHT -> stopPx - after
                TabKind.CENTER -> stopPx - after / 2f
            }
            val extra = (target - x0 - natural).coerceAtLeast(0f)
            if (extra > 0.5f) out[o] = extra
            o = text.indexOf('\t', o + 1)
        }
        return out
    }

    /** The stop after [mm] (from the left margin): the next own one, else the next multiple of 12,5 mm. */
    fun nextStop(stops: List<TabStop>, mm: Float): Pair<Float, TabKind> {
        stops.firstOrNull { it.mm > mm + 0.5f }?.let { return it.mm.toFloat() to it.kind }
        val after = maxOf(mm, stops.lastOrNull()?.mm?.toFloat() ?: 0f)
        val k = kotlin.math.floor(after / TabStop.DEFAULT_MM + 1e-3f).toInt() + 1
        return k * TabStop.DEFAULT_MM to TabKind.LEFT
    }

    /** [text] with the room of [extras] after its tab characters ([density] × [fontScale] turn px into sp). */
    fun annotate(text: AnnotatedString, extras: Map<Int, Float>, density: Float, fontScale: Float): AnnotatedString {
        if ('\t' !in text) return text
        val b = AnnotatedString.Builder(text)
        // The tab character itself is not drawn (some fonts show a box for it), only its room.
        var o = text.indexOf('\t')
        while (o >= 0) {
            b.addStyle(SpanStyle(color = androidx.compose.ui.graphics.Color.Transparent), o, o + 1)
            o = text.indexOf('\t', o + 1)
        }
        for ((k, px) in extras) {
            if (k !in 0 until text.length || text[k] != '\t') continue
            b.addStyle(SpanStyle(letterSpacing = (px / density / fontScale).sp), k, k + 1)
        }
        return b.toAnnotatedString()
    }

    /** Whether two sets of room differ by more than half a pixel somewhere (a new layout round is worth it). */
    fun differ(a: Map<Int, Float>, b: Map<Int, Float>): Boolean =
        a.keys != b.keys || a.any { (k, v) -> kotlin.math.abs(v - (b[k] ?: 0f)) > 0.5f }
}
