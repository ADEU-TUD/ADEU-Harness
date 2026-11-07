package io.github.thepytales.llmhub.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.sp
import io.github.thepytales.llmhub.docs.DocMarkup
import io.github.thepytales.llmhub.docs.text.PageLayout
import io.github.thepytales.llmhub.docs.text.ParaKind
import io.github.thepytales.llmhub.docs.text.RichText

/**
 * Echte Seiten im Fließtext-Editor (AP-43/AP-44): the Seitenansicht breaks the running text into A4 pages with the rules
 * of [PageLayout] (Witwen/Waisen, headings with the next paragraph). A page break inside a text field is room shown in
 * the text – an invisible line of the needed height, inserted by [PageSpacers] and never part of the document –, a
 * break before a field or a block is room above it.
 */

/** A page break before [offset] of a text field, [height] px of room; [ins] is what [PageSpacers] shows there. */
data class PageBreakAt(val offset: Int, val height: Float, val ins: String) {
    /** Characters of [ins] that stay with the line before (the hyphen of a split word). */
    val keep: Int get() = if (ins.startsWith("-")) 1 else 0

    /** Inside a paragraph (the rest of it goes on after the room). */
    val inside: Boolean get() = !ins.startsWith(PARA)

    /**
     * Lines Compose makes of the room: a paragraph range ending in a line break gets an empty line after it (that is the
     * room between paragraphs), and one starting with a line break an empty line before – so 2 before a paragraph,
     * 3 inside one, all as high as the room's line height.
     */
    val lines: Int get() = if (inside) 3 else 2

    companion object {
        /** Marks of the invisible lines (word joiner + a zero-width character); never in a document. */
        const val PARA = "\u2060\u200B"
        const val LINE = "\u2060\u200C"
        const val HYPH = "\u2060\u200D"

        /** The break before [offset] of [text]: before a paragraph, inside one, or inside a hyphenated word. */
        fun at(text: String, offset: Int, height: Float): PageBreakAt {
            val before = text.getOrNull(offset - 1)
            val here = text.getOrNull(offset)
            val ins = when {
                before == null || before == '\n' -> "$PARA\n"
                before.isLetterOrDigit() && here != null && here.isLetterOrDigit() -> "-\n$HYPH\n"
                else -> "\n$LINE\n"
            }
            return PageBreakAt(offset, height, ins)
        }
    }
}

/**
 * Text shown in a text field that is not part of the document: the room of page breaks ([breaks]) and the removed text
 * of tracked changes ([deleted], offset → text, Änderungen nachverfolgen). Removed text stands before the character at
 * its offset (inside that paragraph), the room of a page break before both.
 */
internal class Shown(private val src: String, breaks: List<PageBreakAt>, deleted: List<Pair<Int, String>>) {
    class Ins(val offset: Int, val text: String, val brk: PageBreakAt?)

    val ins: List<Ins> = (
        breaks.filter { it.offset in 1 until src.length }.distinctBy { it.offset }.map { Ins(it.offset, it.ins, it) } +
            deleted.filter { it.first in 0..src.length && it.second.isNotEmpty() }.map { Ins(it.first, removedText(it.second), null) }
        ).sortedWith(compareBy({ it.offset }, { if (it.brk != null) 0 else 1 }))

    /** Where each inserted piece starts in the shown text. */
    val at: IntArray
    val text: String

    init {
        val sb = StringBuilder(src.length + ins.sumOf { it.text.length })
        val a = IntArray(ins.size)
        var last = 0
        ins.forEachIndexed { i, x ->
            sb.append(src, last, x.offset)
            a[i] = sb.length
            sb.append(x.text)
            last = x.offset
        }
        sb.append(src, last, src.length)
        at = a
        text = sb.toString()
    }

    /** Start of [o] in the shown text: after the room of a break there, before removed text there. */
    fun start(o: Int): Int {
        var r = o
        for (x in ins) if (x.offset < o || x.offset == o && x.brk != null) r += x.text.length
        return r
    }

    /** End of a range ending at [o]: before everything inserted there (removed text at the very end stays inside). */
    fun end(o: Int): Int {
        var r = o
        for (x in ins) if (x.offset < o || o == src.length && x.offset == o && x.brk == null) r += x.text.length
        return r
    }

    fun toOrig(t: Int): Int {
        var removed = 0
        ins.forEachIndexed { i, x ->
            if (t < at[i]) return (t - removed).coerceIn(0, src.length)
            if (t < at[i] + x.text.length) return x.offset
            removed += x.text.length
        }
        return (t - removed).coerceIn(0, src.length)
    }

    /** Ranges of the shown text that are room of page breaks. */
    val spacers: List<IntRange> get() = ins.indices.filter { ins[it].brk != null }.map { at[it] until at[it] + ins[it].text.length }

    companion object {
        /** Removed text in one line (a removed paragraph break shows as ¶). */
        fun removedText(s: String) = s.replace("\n", " ¶ ")
    }
}

/** Where [offset] of the document text is in the shown text (see [Shown.start]), without building that text. */
internal fun shownOffset(breaks: List<PageBreakAt>, deleted: List<Pair<Int, String>>, offset: Int): Int {
    var o = offset
    for (b in breaks) if (b.offset <= offset) o += b.ins.length
    for ((d, t) in deleted) if (d < offset && t.isNotEmpty()) o += Shown.removedText(t).length
    return o
}

/**
 * The shown text of a text field: room for page breaks ([breaks]; [density] × [fontScale] turn px into sp) and removed
 * text of tracked changes ([deleted], in [deletedStyle]).
 */
class PageSpacers(
    private val breaks: List<PageBreakAt>, private val density: Float, private val fontScale: Float,
    private val deleted: List<Pair<Int, String>> = emptyList(), private val deletedStyle: SpanStyle = SpanStyle(),
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val src = text.text
        val sh = Shown(src, breaks, deleted)
        if (sh.ins.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val list = sh.ins
        val at = sh.at
        val out = AnnotatedString.Builder(sh.text)
        // Character formats: split around the room of page breaks; a hyphen takes the format of the letter before it.
        for (r in text.spanStyles) {
            var a = r.start
            list.forEachIndexed { i, x ->
                val b = x.brk ?: return@forEachIndexed
                if (b.offset in (a + 1) until r.end) {
                    val end = at[i] + b.keep
                    if (end > sh.start(a)) out.addStyle(r.item, sh.start(a), end)
                    a = b.offset
                }
            }
            if (sh.end(r.end) > sh.start(a)) out.addStyle(r.item, sh.start(a), sh.end(r.end))
        }
        // Paragraph formats: a paragraph split by a page break goes on after the room without its first-line indent.
        // Compose wants them in text order and without overlaps.
        val paras = ArrayList<AnnotatedString.Range<ParagraphStyle>>()
        for (r in text.paragraphStyles) {
            var a = r.start
            var style = r.item
            list.forEachIndexed { i, x ->
                val b = x.brk ?: return@forEachIndexed
                if (b.offset in (a + 1) until r.end && b.inside) {
                    paras += AnnotatedString.Range(style, sh.start(a), at[i] + b.keep)
                    a = b.offset
                    style = style.textIndent?.let { ti -> style.copy(textIndent = TextIndent(ti.restLine, ti.restLine)) } ?: style
                }
            }
            if (sh.end(r.end) > sh.start(a)) paras += AnnotatedString.Range(style, sh.start(a), sh.end(r.end))
        }
        list.forEachIndexed { i, x ->
            val b = x.brk
            if (b == null) {
                // Removed text of a tracked change: red and struck through.
                out.addStyle(deletedStyle, at[i], at[i] + x.text.length)
                return@forEachIndexed
            }
            // The invisible lines of a page break: as high as the room, transparent.
            val s = at[i] + b.keep
            val e = at[i] + b.ins.length
            paras += AnnotatedString.Range(
                ParagraphStyle(
                    lineHeight = (b.height / b.lines / density / fontScale).coerceAtLeast(0.5f).sp,
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                ),
                s, e,
            )
            out.addStyle(SpanStyle(color = Color.Transparent, fontSize = 1.sp), s, e)
        }
        paras.sortBy { it.start }
        var end = 0
        for (r in paras) {
            val a = maxOf(r.start, end)
            if (r.end > a) { out.addStyle(r.item, a, r.end); end = r.end }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = sh.start(offset.coerceIn(0, src.length))
            override fun transformedToOriginal(offset: Int): Int = sh.toOrig(offset)
        }
        return TransformedText(out.toAnnotatedString(), mapping)
    }

    override fun equals(other: Any?) = other is PageSpacers && other.breaks == breaks && other.density == density && other.fontScale == fontScale &&
        other.deleted == deleted && other.deletedStyle == deletedStyle
    override fun hashCode() = breaks.hashCode() * 31 + deleted.hashCode()
}

/** A line of a text field as laid out without the page breaks: its start in the document text and its height. */
internal data class NaturalLine(val offset: Int, val height: Float)

/**
 * The lines of [layout] without the invisible lines of page breaks, with their offsets in [rich] – or null when the
 * layout is not that of [rich] with [breaks] and [deleted] (yet).
 */
internal fun naturalLines(layout: TextLayoutResult, rich: RichText, breaks: List<PageBreakAt>, deleted: List<Pair<Int, String>>): List<NaturalLine>? {
    val sh = Shown(rich.text, breaks, deleted)
    if (layout.layoutInput.text.text != sh.text) return null
    val spacers = sh.spacers
    val out = ArrayList<NaturalLine>(layout.lineCount)
    for (l in 0 until layout.lineCount) {
        val start = layout.getLineStart(l)
        // The invisible lines of a break are no lines of the document.
        if (spacers.any { start in it }) continue
        // From the top of this line to the top of the next one (lines overlap by a fraction of a pixel; their
        // heights would add up to more than the text is high).
        val next = if (l + 1 < layout.lineCount) layout.getLineTop(l + 1) else layout.getLineBottom(l)
        out += NaturalLine(sh.toOrig(start).coerceIn(0, rich.text.length), next - layout.getLineTop(l))
    }
    return out
}

/**
 * The pages of the document in the Seitenansicht: the items of [PageLayout] from the measured text fields and blocks.
 * [gap]: spacing between two pieces; [minField]: least height of a text field. Null while something is not measured.
 */
internal fun pageItems(state: FlowState, gap: Float, minField: Float): Pair<List<PageLayout.Item>, List<Pair<FlowSeg, Int>>>? {
    val items = ArrayList<PageLayout.Item>()
    val owners = ArrayList<Pair<FlowSeg, Int>>()
    var first = true
    var paraBase = 0
    for (seg in state.segs) {
        if (seg is FlowSeg.Raw && isHiddenSetting(seg.markdown)) continue
        if (!first) { items += PageLayout.Space(gap); owners += seg to -2 }
        first = false
        when (seg) {
            is FlowSeg.Text -> {
                val layout = seg.layout ?: return null
                val rich = seg.rich
                val lines = naturalLines(layout, rich, seg.breaks, seg.deleted) ?: return null
                val paraOf = lines.map { rich.paraAt(it.offset) }
                // The empty line Compose adds after a paragraph (its line break) is the room between paragraphs: a
                // space, not a line of the paragraph (Witwen/Waisen count only real lines).
                val gap = BooleanArray(lines.size) { k -> k > 0 && paraOf[k - 1] == paraOf[k] && rich.text.getOrNull(lines[k].offset) == '\n' }
                val counts = HashMap<Int, Int>()
                paraOf.forEachIndexed { k, p -> if (!gap[k]) counts[p] = (counts[p] ?: 0) + 1 }
                val seen = HashMap<Int, Int>()
                var total = 0f
                lines.forEachIndexed { k, line ->
                    val p = paraOf[k]
                    if (gap[k]) {
                        items += PageLayout.Space(line.height)
                        owners += seg to -2
                        total += line.height
                        return@forEachIndexed
                    }
                    val idx = seen[p] ?: 0
                    seen[p] = idx + 1
                    val kind = rich.paras[p].kind
                    items += PageLayout.Line(line.height, paraBase + p, idx, counts[p] ?: 1, keepWithNext = kind.heading || kind == ParaKind.TITLE || kind == ParaKind.SUBTITLE)
                    owners += seg to line.offset
                    total += line.height
                }
                if (total < minField) { items += PageLayout.Space(minField - total); owners += seg to -2 }
                paraBase += rich.paras.size
            }
            is FlowSeg.Raw -> {
                val h = seg.height.takeIf { it >= 0f } ?: return null
                items += if (DocMarkup.isPageBreak(seg.markdown)) PageLayout.Break(h) else PageLayout.Block(h)
                owners += seg to -1
            }
        }
    }
    return items to owners
}

/** Puts the room of [plan] into the text fields and blocks of [owners]. */
internal fun applyPlan(plan: PageLayout.Plan, owners: List<Pair<FlowSeg, Int>>, state: FlowState) {
    val breaks = HashMap<Long, MutableList<PageBreakAt>>()
    val tops = HashMap<Long, Float>()
    val firstLine = HashMap<Long, Int>()
    owners.forEachIndexed { k, (seg, off) ->
        if (seg is FlowSeg.Text && off >= 0 && seg.id !in firstLine) firstLine[seg.id] = k
        val push = plan.push[k]
        if (push <= 0f) return@forEachIndexed
        when (seg) {
            is FlowSeg.Raw -> tops[seg.id] = push
            is FlowSeg.Text -> if (off >= 0) {
                if (firstLine[seg.id] == k) tops[seg.id] = push
                else breaks.getOrPut(seg.id) { mutableListOf() } += PageBreakAt.at(seg.rich.text, off, push)
            }
        }
    }
    for (seg in state.segs) {
        val top = tops[seg.id] ?: 0f
        when (seg) {
            is FlowSeg.Text -> {
                val b = breaks[seg.id].orEmpty()
                if (seg.breaks != b) seg.breaks = b
                if (seg.push != top) seg.push = top
            }
            is FlowSeg.Raw -> if (seg.push != top) seg.push = top
        }
    }
    if (state.pageCount != plan.pages) state.pageCount = plan.pages
}

/** No pages (Ohne Seiten, narrow window): all room goes. */
internal fun clearPlan(state: FlowState) {
    for (seg in state.segs) when (seg) {
        is FlowSeg.Text -> { if (seg.breaks.isNotEmpty()) seg.breaks = emptyList(); if (seg.push != 0f) seg.push = 0f }
        is FlowSeg.Raw -> if (seg.push != 0f) seg.push = 0f
    }
    state.pageCount = 1
}

/** What the header and footer of page [page] (1-based) of [pages] show: left and right text. */
internal data class PageMargins(val headerLeft: String, val headerRight: String, val footerLeft: String, val footerRight: String)

internal fun pageMargins(setup: DocMarkup.PageSetup, page: Int, pages: Int, today: String = DocMarkup.today()): PageMargins {
    if (page == 1 && setup.titlePage) {
        return PageMargins(setup.headerFirst, "", setup.footerFirst, "")
    }
    val even = page % 2 == 0 && setup.hasEven
    val header = if (even) setup.headerEven else setup.header
    val footer = if (even) setup.footerEven else setup.footer
    return PageMargins(
        DocMarkup.fields(header, page, pages),
        if (setup.date) today else "",
        DocMarkup.fields(footer, page, pages),
        if (setup.pageNumbers) tr("Seite {0} von {1}", page, pages) else "",
    )
}
