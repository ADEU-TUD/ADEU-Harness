package io.github.thepytales.llmhub.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import io.github.thepytales.llmhub.docs.CellRich
import io.github.thepytales.llmhub.docs.text.CharFormat

/** Formatierung innerhalb einer Zelle (AP-48) in the table editor: the cell's text shown with its formats. */
object SheetRichText {
    fun style(f: CharFormat): SpanStyle = SpanStyle(
        fontWeight = if (f.bold) FontWeight.Bold else null,
        fontStyle = if (f.italic) FontStyle.Italic else null,
        textDecoration = when {
            f.underline && f.strike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
            f.underline || f.link != null -> TextDecoration.Underline
            f.strike -> TextDecoration.LineThrough
            else -> null
        },
        color = f.color?.let { hex -> runCatching { Color(0xFF000000 or hex.toLong(16)) }.getOrNull() } ?: Color.Unspecified,
        background = f.highlight?.let { hex -> runCatching { Color(0xFF000000 or hex.toLong(16)) }.getOrNull() } ?: Color.Unspecified,
        fontFamily = if (f.code) FontFamily.Monospace else null,
        baselineShift = if (f.sup) BaselineShift.Superscript else if (f.sub) BaselineShift.Subscript else null,
        fontSize = if (f.sup || f.sub) 10.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
    )

    /** The plain text of a cell with the formats of its parts. */
    fun annotated(raw: String): AnnotatedString {
        val b = AnnotatedString.Builder()
        CellRich.runs(raw).forEach { (text, f) ->
            val start = b.length
            b.append(text)
            if (f != CharFormat.PLAIN) b.addStyle(style(f), start, b.length)
        }
        return b.toAnnotatedString()
    }

    /**
     * The formula bar hides the marks of a cell with formatting (as the document editor does), so typing and selecting
     * work on the visible text; a formula keeps showing exactly what was typed.
     */
    object Transformation : VisualTransformation {
        override fun filter(text: AnnotatedString): TransformedText {
            val raw = text.text
            if (raw.trimStart().startsWith("=") || !CellRich.isRich(raw)) return TransformedText(text, OffsetMapping.Identity)
            val p = CellRich.parse(raw)
            val mapping = object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = p.plainOffset(offset).coerceIn(0, p.plain.length)
                override fun transformedToOriginal(offset: Int): Int = p.rawOffset(offset).coerceIn(0, raw.length)
            }
            return TransformedText(annotated(raw), mapping)
        }
    }

    /**
     * An edit that only removed hidden marks (Backspace next to „**“) removes the visible character next to them
     * instead – the user cannot see the marks, so that is what was meant.
     */
    fun fixEdit(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
        val a = old.text
        val b = new.text
        if (b.length >= a.length || a.trimStart().startsWith("=") || !CellRich.isRich(a)) return new
        var p = 0
        while (p < b.length && a[p] == b[p]) p++
        var q = 0
        while (q < b.length - p && a[a.length - 1 - q] == b[b.length - 1 - q]) q++
        val removedEnd = a.length - q
        if (b.length - q != p) return new
        val parsed = CellRich.parse(a)
        if ((p until removedEnd).any { !parsed.hidden[it] }) return new
        val backspace = old.selection.collapsed && old.selection.start == removedEnd
        if (backspace) {
            var k = p - 1
            while (k >= 0 && parsed.hidden[k]) k--
            if (k < 0) return old
            return TextFieldValue(a.removeRange(k, k + 1), TextRange(k))
        }
        var k = removedEnd
        while (k < a.length && parsed.hidden[k]) k++
        if (k >= a.length) return old
        return TextFieldValue(a.removeRange(k, k + 1), TextRange(p))
    }

    /** The selected part of the formula bar in plain offsets, if it is a part of a text cell (not all of it). */
    fun part(bar: TextFieldValue): IntRange? {
        val t = bar.text
        if (t.trimStart().startsWith("=") || bar.selection.collapsed) return null
        val p = CellRich.parse(t)
        val a = p.plainOffset(bar.selection.min)
        val b = p.plainOffset(bar.selection.max)
        if (a >= b || (a == 0 && b == p.plain.length)) return null
        return a until b
    }

    /** The bar after [change] of its selected part (plain offsets), with the same part selected again. */
    fun changePart(bar: TextFieldValue, change: (String, Int, Int) -> String): TextFieldValue? {
        val part = part(bar) ?: return null
        val next = change(bar.text, part.first, part.last + 1)
        val np = CellRich.parse(next)
        return TextFieldValue(next, TextRange(np.rawOffset(part.first), np.rawOffset(part.last + 1)))
    }
}
