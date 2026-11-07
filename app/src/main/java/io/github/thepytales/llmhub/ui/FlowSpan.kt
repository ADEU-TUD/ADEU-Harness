package io.github.thepytales.llmhub.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.text.TextRange
import io.github.thepytales.llmhub.docs.DocBlocks
import io.github.thepytales.llmhub.docs.text.RichHtml
import io.github.thepytales.llmhub.docs.text.RichText

/**
 * Über Grenzen hinweg (AP-43 d, AP-44): the running text of a document can be several text fields – pieces of a long
 * text and the text between tables, pictures and formulas. Here the cursor goes on from one to the next (arrows),
 * Backspace and Entf join pieces, and a selection reaches over several of them and the blocks between ([FlowState.spanAnchor]
 * is where it began, the cursor of the active field where it ends): Strg+A, Umschalt+Pfeile and Umschalt+Klick make one;
 * copying, cutting, deleting, typing and formatting work on all of it.
 */

/** The pieces of the selection over several fields in document order: text with its range, blocks as a whole (null). */
fun FlowState.spanParts(): List<Pair<FlowSeg, IntRange?>>? {
    val (a, ao) = spanAnchor ?: return null
    val f = active ?: return null
    val ia = segs.indexOf(a)
    val iff = segs.indexOf(f)
    if (ia < 0 || iff < 0 || ia == iff) return null
    val fo = f.selection.end.coerceIn(0, f.rich.text.length)
    val anchorOff = ao.coerceIn(0, a.rich.text.length)
    val (s, so, e, eo) = if (ia < iff) listOf(ia, anchorOff, iff, fo) else listOf(iff, fo, ia, anchorOff)
    return (s..e).mapNotNull { i ->
        val seg = segs[i]
        when {
            seg is FlowSeg.Raw -> if (isHiddenSetting(seg.markdown)) null else seg to null
            seg is FlowSeg.Text -> {
                val len = seg.rich.text.length
                seg to when (i) { s -> so until len; e -> 0 until eo; else -> 0 until len }
            }
            else -> null
        }
    }
}

/** The part of the selection over several fields that lies in [seg] (text range or the whole block), or null. */
fun FlowState.spanIn(seg: FlowSeg): Pair<Boolean, IntRange?>? = spanParts()?.firstOrNull { it.first === seg }?.let { true to it.second }

/** The text fields in document order. */
private fun FlowState.texts() = segs.filterIsInstance<FlowSeg.Text>()

/** The next (or previous) text field after [t], over blocks. */
fun FlowState.textBeside(t: FlowSeg.Text, forward: Boolean): FlowSeg.Text? {
    val i = segs.indexOf(t)
    if (i < 0) return null
    val range = if (forward) (i + 1 until segs.size) else (i - 1 downTo 0)
    for (k in range) (segs[k] as? FlowSeg.Text)?.let { return it }
    return null
}

/** Puts the cursor (or selection) into [t]; [keepSpan]: the selection over several fields stays. */
fun FlowState.focusPiece(t: FlowSeg.Text, sel: TextRange, keepSpan: Boolean) {
    if (!keepSpan) spanAnchor = null
    t.selection = TextRange(sel.start.coerceIn(0, t.rich.text.length), sel.end.coerceIn(0, t.rich.text.length))
    t.typing = null
    focusByEditor = true
    active = t
    runCatching { t.focus.requestFocus() }
}

/** Offset in [t] at [x] (px from the left of the field) on its first or last line, from its last layout. */
private fun offsetAtX(t: FlowSeg.Text, x: Float, lastLine: Boolean): Int {
    val layout = t.layout ?: return if (lastLine) t.rich.text.length else 0
    if (layout.lineCount == 0) return 0
    val line = if (lastLine) layout.lineCount - 1 else 0
    val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
    val shown = layout.getOffsetForPosition(Offset(x, y))
    return Shown(t.rich.text, t.breaks, t.deleted).toOrig(shown)
}

/** Selection over all fields (Strg+A in a document of several fields). */
fun FlowState.selectAllPieces(): Boolean {
    val all = texts()
    if (all.size < 2) return false
    val first = all.first()
    val last = all.last()
    spanAnchor = first to 0
    focusPiece(last, TextRange(0, last.rich.text.length), keepSpan = true)
    return true
}

/** Removes the selection over several fields with the blocks in it; the cursor stands where it began. */
fun FlowState.deleteSpan(): Boolean {
    val parts = spanParts() ?: return false
    val first = parts.first()
    val last = parts.last()
    val a = first.first as? FlowSeg.Text ?: return false
    val b = last.first as? FlowSeg.Text ?: return false
    checkpoint()
    val keep = a.rich.cut(first.second!!.first, a.rich.text.length)
    val tail = b.rich.cut(0, (last.second!!.last + 1).coerceAtLeast(0))
    val at = keep.text.length
    a.rich = keep.insert(at, tail)
    val ia = segs.indexOf(a)
    val ib = segs.indexOf(b)
    for (k in ib downTo ia + 1) segs.removeAt(k)
    spanAnchor = null
    focusPiece(a, TextRange(at), keepSpan = false)
    changed()
    return true
}

/** The selection over several fields to the clipboard (HTML with tables, plain text). */
fun FlowState.copySpan(c: io.github.thepytales.llmhub.AppContainer): Boolean {
    val parts = spanParts() ?: return false
    val html = StringBuilder("<html><body>")
    val plain = StringBuilder()
    for ((seg, range) in parts) {
        if (seg is FlowSeg.Text && range != null) {
            if (range.isEmpty()) continue
            val piece = seg.rich.slice(range.first, range.last + 1)
            html.append(RichHtml.fromRich(piece).removePrefix("<html><body>").removeSuffix("</body></html>"))
            if (plain.isNotEmpty()) plain.append('\n')
            plain.append(piece.text.filterIndexed { i, _ -> piece.chars[i].ins == 0 })
        } else if (seg is FlowSeg.Raw) {
            if (plain.isNotEmpty()) plain.append('\n')
            if (seg.isTable) {
                val rows = DocBlocks.tableRows(seg.markdown)
                html.append("<table>")
                rows.forEachIndexed { r, row -> html.append("<tr>").append(row.joinToString("") { val tag = if (r == 0) "th" else "td"; "<$tag>${esc(it)}</$tag>" }).append("</tr>") }
                html.append("</table>")
                plain.append(rows.joinToString("\n") { it.joinToString("\t") })
            } else {
                html.append("<p>").append(esc(DocBlocks.plainOf(seg.markdown))).append("</p>")
                plain.append(DocBlocks.plainOf(seg.markdown))
            }
        }
    }
    html.append("</body></html>")
    return RichClipboard.copy(c, html.toString(), plain.toString(), null)
}

private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

/** Backspace at the start of a piece: its first paragraph joins the last one of the piece before (text right before it). */
fun FlowState.joinWithPrevious(t: FlowSeg.Text): Boolean {
    val i = segs.indexOf(t)
    val prev = segs.getOrNull(i - 1) as? FlowSeg.Text ?: return false
    checkpoint()
    val at = prev.rich.text.length
    prev.rich = prev.rich.insert(at, t.rich)
    segs.removeAt(i)
    focusPiece(prev, TextRange(at), keepSpan = false)
    changed()
    return true
}

/** Entf at the end of a piece: the next piece (text right after it) joins it. */
fun FlowState.joinWithNext(t: FlowSeg.Text): Boolean {
    val i = segs.indexOf(t)
    val next = segs.getOrNull(i + 1) as? FlowSeg.Text ?: return false
    checkpoint()
    val at = t.rich.text.length
    t.rich = t.rich.insert(at, next.rich)
    segs.removeAt(i + 1)
    focusPiece(t, TextRange(at), keepSpan = false)
    changed()
    return true
}

/**
 * Keys of [t] that go beyond its field (before the field's own handling); true when handled. [c] for the clipboard.
 */
fun FlowState.pieceKey(c: io.github.thepytales.llmhub.AppContainer, t: FlowSeg.Text, e: KeyEvent): Boolean {
    val cmd = e.isCtrlPressed || e.isMetaPressed
    val sel = t.selection
    val len = t.rich.text.length
    // A selection over several fields: what the keys do with all of it.
    if (spanParts() != null) {
        when {
            cmd && e.key == Key.C -> return copySpan(c)
            cmd && e.key == Key.X -> { copySpan(c); return deleteSpan() }
            e.key == Key.Backspace || e.key == Key.Delete -> return deleteSpan()
            e.key == Key.Escape -> { spanAnchor = null; return true }
            !cmd && !e.isAltPressed && e.utf16CodePoint >= 32 && e.key != Key.Tab -> {
                val ch = String(Character.toChars(e.utf16CodePoint))
                deleteSpan()
                insertText(ch)
                return true
            }
            !e.isShiftPressed && e.key in ARROWS -> { spanAnchor = null; return false }
        }
    }
    if (cmd && !e.isShiftPressed && e.key == Key.A) return selectAllPieces()
    if (cmd || e.isAltPressed) return false
    val layout = t.layout
    val shownSel = t.shown(sel.end)
    val line = layout?.takeIf { shownSel <= it.layoutInput.text.length }?.getLineForOffset(shownSel)
    val x = layout?.takeIf { shownSel <= it.layoutInput.text.length }?.getHorizontalPosition(shownSel, true) ?: 0f
    val forward: Boolean
    val target: FlowSeg.Text
    val offset: Int
    when {
        e.key == Key.DirectionDown && line != null && line == layout.lineCount - 1 -> {
            target = textBeside(t, true) ?: return false; forward = true; offset = offsetAtX(target, x, lastLine = false)
        }
        e.key == Key.DirectionUp && line == 0 -> {
            target = textBeside(t, false) ?: return false; forward = false; offset = offsetAtX(target, x, lastLine = true)
        }
        e.key == Key.DirectionRight && sel.end == len && (sel.collapsed || e.isShiftPressed) -> {
            target = textBeside(t, true) ?: return false; forward = true; offset = 0
        }
        e.key == Key.DirectionLeft && sel.end == 0 && (sel.collapsed || e.isShiftPressed) -> {
            target = textBeside(t, false) ?: return false; forward = false; offset = target.rich.text.length
        }
        // Backspace/Entf at the edge of a piece joins it with the piece right beside it.
        // (A heading or list item at the start first becomes normal text, as in the field itself.)
        e.key == Key.Backspace && sel.collapsed && sel.start == 0 && t.rich.paras.first().kind == io.github.thepytales.llmhub.docs.text.ParaKind.NORMAL -> return joinWithPrevious(t)
        e.key == Key.Delete && sel.collapsed && sel.start == len -> return joinWithNext(t)
        else -> return false
    }
    if (!e.isShiftPressed) {
        focusPiece(target, TextRange(offset), keepSpan = false)
        return true
    }
    // Umschalt: the selection goes on into the next field (it began where the cursor stood).
    val anchor = spanAnchor ?: (t to sel.start)
    if (anchor.first === target) {
        spanAnchor = null
        focusPiece(target, TextRange(anchor.second, offset), keepSpan = false)
        return true
    }
    spanAnchor = anchor
    val edge = if (segs.indexOf(anchor.first) < segs.indexOf(target)) 0 else target.rich.text.length
    focusPiece(target, TextRange(edge, offset), keepSpan = true)
    @Suppress("UNUSED_VARIABLE") val dir = forward
    return true
}

private val ARROWS = setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.MoveHome, Key.MoveEnd, Key.PageUp, Key.PageDown)

/** A rich piece of [t] in [range] (for tests and Lora). */
internal fun pieceOf(t: FlowSeg.Text, range: IntRange): RichText = t.rich.slice(range.first, range.last + 1)
