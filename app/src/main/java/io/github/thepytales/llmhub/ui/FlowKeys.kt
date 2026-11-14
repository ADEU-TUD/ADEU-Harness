package io.github.thepytales.llmhub.ui

import androidx.compose.ui.text.TextRange
import io.github.thepytales.llmhub.docs.text.CharFormat
import io.github.thepytales.llmhub.docs.text.ParaFormat
import io.github.thepytales.llmhub.docs.text.RichText
import io.github.thepytales.llmhub.docs.text.WordKeys

/**
 * Tasten und Markieren wie Word (AP-125 c): Strg+Pfeile by word and paragraph, Strg+Pos1/Ende, Strg+Rück/Entf, all
 * across the pieces of a long text and the blocks between them (FlowSpan) and with Umschalt to mark; Alt+Umschalt+↑/↓
 * moves paragraphs, Umschalt+F3 changes Groß-/Kleinschreibung, Strg+Umschalt+>/< the font size, Strg+Umschalt+C and
 * Strg+Alt+V copy and paste a format, Strg+Klick marks a sentence.
 */

/** Puts the cursor of [t] at [offset] in [target] – with [extend] the selection reaches there (also into other fields). */
fun FlowState.jumpTo(t: FlowSeg.Text, target: FlowSeg.Text, offset: Int, extend: Boolean): Boolean {
    if (!extend) {
        focusPiece(target, TextRange(offset), keepSpan = false)
        return true
    }
    val anchor = spanAnchor ?: (t to t.selection.start)
    if (anchor.first === target) {
        spanAnchor = null
        focusPiece(target, TextRange(anchor.second, offset), keepSpan = false)
        return true
    }
    spanAnchor = anchor
    val edge = if (segs.indexOf(anchor.first) < segs.indexOf(target)) 0 else target.rich.text.length
    focusPiece(target, TextRange(edge, offset), keepSpan = true)
    return true
}

/** Strg+←/→: to the start of the word before or after (Word on Windows); at the edge of a field into the next one. */
fun FlowState.moveByWord(t: FlowSeg.Text, forward: Boolean, extend: Boolean): Boolean {
    val at = t.selection.end.coerceIn(0, t.rich.text.length)
    val len = t.rich.text.length
    if (forward && at == len) return textBeside(t, true)?.let { jumpTo(t, it, 0, extend) } ?: true
    if (!forward && at == 0) return textBeside(t, false)?.let { jumpTo(t, it, it.rich.text.length, extend) } ?: true
    return jumpTo(t, t, if (forward) WordKeys.nextWord(t.rich.text, at) else WordKeys.prevWord(t.rich.text, at), extend)
}

/**
 * Strg+↑/↓ (like Word): to the start of this paragraph or of the one before, to the start of the next one; over the
 * edge of a field into the next one, after the last paragraph of the document to its end.
 */
fun FlowState.moveByParagraph(t: FlowSeg.Text, forward: Boolean, extend: Boolean): Boolean {
    val r = t.rich
    val at = t.selection.end.coerceIn(0, r.text.length)
    val p = r.paraAt(at)
    return if (forward) {
        when {
            p + 1 < r.paras.size -> jumpTo(t, t, r.starts[p + 1], extend)
            else -> textBeside(t, true)?.let { jumpTo(t, it, 0, extend) } ?: jumpTo(t, t, r.text.length, extend)
        }
    } else {
        when {
            at > r.starts[p] -> jumpTo(t, t, r.starts[p], extend)
            p > 0 -> jumpTo(t, t, r.starts[p - 1], extend)
            else -> textBeside(t, false)?.let { b -> jumpTo(t, b, b.rich.starts.last(), extend) } ?: true
        }
    }
}

/** Strg+Pos1/Ende: start or end of the whole document (not only of this field). */
fun FlowState.moveToDocumentEdge(t: FlowSeg.Text, end: Boolean, extend: Boolean): Boolean {
    val texts = segs.filterIsInstance<FlowSeg.Text>()
    val target = (if (end) texts.lastOrNull() else texts.firstOrNull()) ?: return false
    return jumpTo(t, target, if (end) target.rich.text.length else 0, extend)
}

/** Strg+Rück/Entf: the word before or after the cursor goes (a selection goes as a whole; at the edge fields join). */
fun FlowState.deleteWord(t: FlowSeg.Text, forward: Boolean): Boolean {
    if (spanParts() != null) return deleteSpan()
    val sel = t.selection
    val text = t.rich.text
    val (a, b) = when {
        !sel.collapsed -> sel.min to sel.max
        forward -> if (sel.start >= text.length) return joinWithNext(t) else sel.start to WordKeys.nextWord(text, sel.start)
        else -> if (sel.start <= 0) return joinWithPrevious(t) else WordKeys.prevWord(text, sel.start) to sel.start
    }
    if (a >= b) return true
    checkpoint()
    t.rich = t.rich.cut(a, b)
    t.selection = TextRange(a)
    t.typing = null
    changed()
    return true
}

/** Each paragraph of [r] as a piece of its own (with its formats). */
internal fun paragraphsOf(r: RichText): List<RichText> = r.paras.indices.map { i -> r.paraRange(i).let { r.slice(it.first, it.last + 1) } }

/** Pieces of one paragraph each, joined into one text again. */
internal fun joined(parts: List<RichText>): RichText {
    if (parts.isEmpty()) return RichText.empty()
    val text = parts.joinToString("\n") { it.text }
    val chars = ArrayList<CharFormat>(text.length)
    parts.forEachIndexed { i, p -> if (i > 0) chars += CharFormat.PLAIN; chars += p.chars }
    return RichText(text, chars, parts.flatMap { it.paras })
}

/** The paragraphs the cursor or selection of [t] touches (a selection ending at a paragraph start leaves that one out). */
private fun touched(t: FlowSeg.Text): IntRange {
    val r = t.rich
    val sel = t.selection
    val a = r.paraAt(sel.min.coerceIn(0, r.text.length))
    var b = r.paraAt(sel.max.coerceIn(0, r.text.length))
    if (!sel.collapsed && b > a && sel.max == r.starts[b]) b--
    return a..b
}

/**
 * Alt+Umschalt+↑/↓ (like Word): the paragraphs at the cursor swap with the one above or below; at the edge of a field
 * they go over the table, picture … between into the next field. The selection moves with them.
 */
fun FlowState.moveParagraphs(t: FlowSeg.Text, up: Boolean): Boolean {
    if (spanParts() != null) return true
    val r = t.rich
    val sel = t.selection
    val range = touched(t)
    val parts = paragraphsOf(r)
    val moved = parts.subList(range.first, range.last + 1)
    val startRel = sel.start - r.starts[range.first]
    val endRel = sel.end - r.starts[range.first]
    if (up && range.first > 0 || !up && range.last < parts.size - 1) {
        checkpoint()
        val order = if (up) parts.subList(0, range.first - 1) + moved + parts[range.first - 1] + parts.subList(range.last + 1, parts.size)
        else parts.subList(0, range.first) + parts[range.last + 1] + moved + parts.subList(range.last + 2, parts.size)
        t.rich = joined(order)
        val at = if (up) r.starts[range.first - 1] else r.starts[range.first] + parts[range.last + 1].text.length + 1
        t.selection = TextRange(at + startRel, at + endRel)
        t.typing = null
        changed()
        return true
    }
    val target = textBeside(t, !up) ?: return true
    checkpoint()
    val rest = parts.subList(0, range.first) + parts.subList(range.last + 1, parts.size)
    val piece = joined(moved)
    val tr = target.rich
    val empty = tr.text.isEmpty()
    val at: Int
    target.rich = when {
        empty -> { at = 0; piece }
        up -> { at = tr.text.length + 1; joined(paragraphsOf(tr) + moved) }
        else -> { at = 0; joined(moved + paragraphsOf(tr)) }
    }
    t.rich = joined(rest)
    // A piece of a long text that is now empty goes (between a table and the next text one stays, it holds the cursor).
    val i = segs.indexOf(t)
    if (rest.isEmpty() && (segs.getOrNull(i - 1) is FlowSeg.Text || segs.getOrNull(i + 1) is FlowSeg.Text)) segs.removeAt(i)
    focusPiece(target, TextRange(at + startRel, at + endRel), keepSpan = false)
    changed()
    return true
}

/** The ranges a change of letters applies to: the selection (also over several fields) or the word at the cursor. */
private fun FlowState.letterRanges(t: FlowSeg.Text): List<Pair<FlowSeg.Text, IntRange>> {
    spanParts()?.let { parts -> return parts.mapNotNull { (s, r) -> if (s is FlowSeg.Text && r != null && !r.isEmpty()) s to r else null } }
    val sel = t.selection
    return listOf(t to if (sel.collapsed) WordKeys.wordAt(t.rich.text, sel.start) else sel.min until sel.max).filter { !it.second.isEmpty() }
}

/** Umschalt+F3: Groß-/Kleinschreibung of the selection (or the word at the cursor) in Word's order; it stays marked. */
fun FlowState.cycleCase(t: FlowSeg.Text): Boolean {
    val ranges = letterRanges(t)
    if (ranges.isEmpty()) return true
    // One decision for all of it, as in Word.
    val all = ranges.joinToString("\n") { (s, r) -> s.rich.text.substring(r.first, r.last + 1) }
    val changedAll = WordKeys.cycleCase(all)
    if (changedAll == all) return true
    checkpoint()
    var k = 0
    for ((s, r) in ranges) {
        val piece = changedAll.substring(k, k + r.last + 1 - r.first)
        k += piece.length + 1
        val text = s.rich.text
        s.rich = RichText(text.substring(0, r.first) + piece + text.substring(r.last + 1), s.rich.chars, s.rich.paras)
    }
    changed()
    return true
}

/**
 * Strg+Umschalt+> / < (like Word): the font size of the selection one step bigger or smaller (each character from its
 * own size); without a selection for what is typed next. The size of the Formatvorlage counts as no own size.
 */
fun FlowState.stepFontSize(t: FlowSeg.Text, up: Boolean): Boolean {
    fun styleSize(p: ParaFormat) = sheet.of(p.kind).size
    val parts = spanParts()?.mapNotNull { (s, r) -> if (s is FlowSeg.Text && r != null && !r.isEmpty()) s to r else null }
        ?: if (t.selection.collapsed) null else listOf(t to (t.selection.min until t.selection.max))
    if (parts == null) {
        val f = formatHere()
        val base = styleSize(paraHere())
        val next = WordKeys.stepSize(f.size?.toFloat() ?: base, up)
        t.typing = f.copy(size = next.takeIf { it.toFloat() != base })
        return true
    }
    checkpoint()
    for ((s, range) in parts) {
        val r = s.rich
        val chars = r.chars.toMutableList()
        for (i in range) {
            if (r.text[i] == '\n') continue
            val base = styleSize(r.paras[r.paraAt(i)])
            val next = WordKeys.stepSize(chars[i].size?.toFloat() ?: base, up)
            chars[i] = chars[i].copy(size = next.takeIf { it.toFloat() != base })
        }
        s.rich = RichText(r.text, chars, r.paras)
    }
    changed()
    return true
}

/** Strg+Umschalt+C (Word) or Strg+Alt+C (Docs): the format at the cursor is remembered for Strg+Alt+V. */
fun FlowState.copyFormat(): Boolean {
    formatClip = formatHere().stored to paraHere()
    Notices.info(tr("Format kopiert – mit Strg+Alt+V auf eine Markierung übertragen."))
    return true
}

/** Strg+Alt+V (Docs): the remembered format on the selection (characters and paragraph), or on what is typed next. */
fun FlowState.pasteFormat(t: FlowSeg.Text): Boolean {
    val (chars, para) = formatClip ?: run { Notices.info(tr("Zuerst ein Format kopieren (Strg+Umschalt+C).")); return true }
    format { chars }
    paragraph { q -> q.copy(kind = para.kind, align = para.align, spacing = para.spacing, left = para.left, first = para.first) }
    if (t.selection.collapsed) t.typing = chars
    return true
}

/** Strg+Enter: a page break at the cursor; the cursor stands in the text after it. */
fun FlowState.pageBreakHere(): Boolean {
    if (spanParts() != null) deleteSpan()
    insertBlock("\\newpage")
    wantFocus = active
    return true
}

/** Strg+Klick (like Word): the sentence at [offset] of [t] is marked. */
fun FlowState.selectSentence(t: FlowSeg.Text, offset: Int) {
    val s = WordKeys.sentenceAt(t.rich.text, offset)
    focusPiece(t, TextRange(s.first, s.last + 1), keepSpan = false)
}
