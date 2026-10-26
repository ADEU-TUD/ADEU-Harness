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
import androidx.compose.ui.text.style.TextDecoration

/**
 * Editing without visible Markdown (like Word or Google Docs): the text keeps its marks ("## ", "**", "- ", "[x](…)"),
 * but the field hides them and shows the formatting instead – headings without "#", bold text without "**", "•"
 * instead of "- ", "☐" instead of "- [ ] ", links without their address. Only the Markdown file type shows the marks.
 */
object MarkdownHiding {
    /** Marks of a text: hidden characters, characters shown as another one, and the formatting of the rest. */
    class Marks(val hidden: BooleanArray, val replaced: Map<Int, Char>, val styles: List<AnnotatedString.Range<SpanStyle>>, val lineMarks: List<IntRange>)

    /** Block marks at the start of a line: heading, quote (with callout), list item, checklist item. */
    private val LINE_MARK = Regex("^(#{1,6}[ \\t]+|>[ \\t]?(\\[![A-Za-z]+][ \\t]*)?|[ \\t]*([-*+][ \\t](\\[[ xX]][ \\t])?|\\d+[.)][ \\t]))")

    fun analyze(text: String, link: Color = Color(0xFF2F57B2), code: Color = Color(0x22888888)): Marks {
        val hidden = BooleanArray(text.length)
        val replaced = HashMap<Int, Char>()
        val styles = mutableListOf<AnnotatedString.Range<SpanStyle>>()
        val lineMarks = mutableListOf<IntRange>()
        fun hide(from: Int, to: Int) { for (i in from until to.coerceAtMost(text.length)) if (i >= 0) hidden[i] = true }
        var pos = 0
        var inCode = false
        for (line in text.split('\n')) {
            val start = pos
            val end = start + line.length
            pos = end + 1
            val t = line.trimStart()
            if (t.startsWith("```")) {
                // Fence lines disappear with their line break; the code between them is shown in monospace.
                if (!inCode) hide(start, (end + 1).coerceAtMost(text.length)) else hide(start - 1, end)
                inCode = !inCode
                continue
            }
            if (inCode) {
                if (end > start) styles += AnnotatedString.Range(SpanStyle(fontFamily = FontFamily.Monospace, background = code), start, end)
                continue
            }
            if (t.startsWith("|")) continue // tables are edited as grid
            var body = start
            LINE_MARK.find(line)?.let { m ->
                val mark = m.value
                val mEnd = start + mark.length
                lineMarks += start until mEnd
                val trimmed = mark.trimStart()
                val indent = mark.length - trimmed.length
                when {
                    trimmed.startsWith("#") -> hide(start, mEnd)
                    trimmed.startsWith(">") -> {
                        // A callout's first line ("> [!NOTE]") vanishes completely.
                        if (m.groups[2] != null && mEnd >= end) hide(start, (end + 1).coerceAtMost(text.length)) else hide(start, mEnd)
                    }
                    m.groups[4] != null -> {
                        // "- [ ] " → "☐ ": bullet, space and brackets hidden, the box character replaces the state.
                        val b = start + indent
                        hide(b, b + 3)
                        replaced[b + 3] = if (text[b + 3] == ' ') '☐' else '☑'
                        hidden[b + 4] = true
                        if (text[b + 3] != ' ') styles += AnnotatedString.Range(SpanStyle(textDecoration = TextDecoration.LineThrough, color = Color.Gray), mEnd, end)
                    }
                    trimmed.first() in "-*+" -> replaced[start + indent] = '•'
                }
                body = mEnd
            }
            inline(text, body, end, hidden, styles, link, code)
        }
        return Marks(hidden, replaced, styles, lineMarks)
    }

    private val INLINE = Regex("(\\*\\*|__)(.+?)\\1|`([^`]+)`|\\[([^\\]]+)]\\(([^)\\s]+)\\)|(?<![*\\w])\\*(?!\\s)(.+?)(?<!\\s)\\*(?!\\*)|~~(.+?)~~")

    private fun inline(text: String, from: Int, to: Int, hidden: BooleanArray, styles: MutableList<AnnotatedString.Range<SpanStyle>>, link: Color, code: Color) {
        if (from >= to) return
        val part = text.substring(from, to)
        for (m in INLINE.findAll(part)) {
            val s = from + m.range.first
            val e = from + m.range.last + 1
            val g = m.groups
            fun mark(open: Int, close: Int, style: SpanStyle, recurse: Boolean) {
                for (i in s until s + open) hidden[i] = true
                for (i in e - close until e) hidden[i] = true
                styles += AnnotatedString.Range(style, s + open, e - close)
                if (recurse) inline(text, s + open, e - close, hidden, styles, link, code)
            }
            when {
                g[2] != null -> mark(2, 2, SpanStyle(fontWeight = FontWeight.Bold), true)
                g[3] != null -> mark(1, 1, SpanStyle(fontFamily = FontFamily.Monospace, background = code), false)
                g[4] != null -> mark(1, g[5]!!.value.length + 3, SpanStyle(color = link, textDecoration = TextDecoration.Underline), true)
                g[6] != null -> mark(1, 1, SpanStyle(fontStyle = FontStyle.Italic), true)
                g[7] != null -> mark(2, 2, SpanStyle(textDecoration = TextDecoration.LineThrough), true)
            }
        }
    }

    /** The field's view of [text]: marks hidden, offsets mapped both ways. */
    fun transform(text: String, link: Color, code: Color): TransformedText {
        val marks = analyze(text, link, code)
        val kept = IntArray(text.length + 1)
        val sb = StringBuilder()
        val origOf = ArrayList<Int>(text.length)
        for (i in text.indices) {
            kept[i] = sb.length
            if (!marks.hidden[i]) {
                sb.append(marks.replaced[i] ?: text[i])
                origOf += i
            }
        }
        kept[text.length] = sb.length
        val shown = AnnotatedString.Builder(sb.toString())
        marks.styles.forEach { r ->
            val a = kept[r.start.coerceIn(0, text.length)]
            val b = kept[r.end.coerceIn(0, text.length)]
            if (b > a) shown.addStyle(r.item, a, b)
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = kept[offset.coerceIn(0, text.length)]
            override fun transformedToOriginal(offset: Int): Int = if (offset >= origOf.size) text.length else origOf[offset.coerceAtLeast(0)]
        }
        return TransformedText(shown.toAnnotatedString(), mapping)
    }

    /**
     * Repairs an edit that only removed hidden marks (the user cannot see them, so a Backspace there meant the visible
     * character next to them): at the start of a heading, quote or list item the formatting is removed (like Word);
     * in lists the item joins the one above; next to "**" the visible character before/after is deleted instead.
     */
    fun fixEdit(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
        val a = old.text
        val b = new.text
        if (b.length >= a.length) return new
        var p = 0
        while (p < b.length && a[p] == b[p]) p++
        var q = 0
        while (q < b.length - p && a[a.length - 1 - q] == b[b.length - 1 - q]) q++
        val removedEnd = a.length - q
        if (b.length - q != p) return new // something was inserted as well (replacing a selection)
        val marks = analyze(a)
        // Deleting inside a line mark (also the visible "•" or "1."): end the formatting of that line.
        val lineMark = marks.lineMarks.firstOrNull { p in it || (removedEnd - 1) in it }
        if (lineMark != null && old.selection.collapsed) {
            val lineStart = lineMark.first
            val markEnd = lineMark.last + 1
            val text: String
            val caret: Int
            val isList = Regex("^[ \\t]*([-*+]|\\d+[.)])[ \\t]").containsMatchIn(a.substring(lineStart, markEnd))
            if (isList && lineStart > 0) {
                // Join with the previous line: the line break and the mark go.
                text = a.substring(0, lineStart - 1) + " " + a.substring(markEnd)
                caret = lineStart
            } else {
                text = a.substring(0, lineStart) + a.substring(markEnd)
                caret = lineStart
            }
            return TextFieldValue(text, TextRange(caret))
        }
        if ((p until removedEnd).any { !marks.hidden[it] }) return new
        // Only hidden inline marks were hit: delete the visible neighbour instead.
        val backspace = old.selection.collapsed && old.selection.start == removedEnd
        if (backspace) {
            var k = p - 1
            while (k >= 0 && marks.hidden[k]) k--
            if (k < 0) return old
            return TextFieldValue(a.removeRange(k, k + 1), TextRange(k))
        }
        var k = removedEnd
        while (k < a.length && marks.hidden[k]) k++
        if (k >= a.length) return old
        return TextFieldValue(a.removeRange(k, k + 1), TextRange(p))
    }
}

/** [MarkdownHiding] as visual transformation of a text field. */
class HideMarkdown(private val link: Color, private val code: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText = MarkdownHiding.transform(text.text, link, code)

    override fun equals(other: Any?) = other is HideMarkdown && other.link == link && other.code == code

    override fun hashCode() = link.hashCode() * 31 + code.hashCode()
}

/**
 * Enter in a field with bullet points continues the list ("- " / "1. " / "- [ ] "); on an empty item it ends the
 * list. Returns null when Enter should do what it always does.
 */
fun continueList(v: TextFieldValue): TextFieldValue? {
    if (!v.selection.collapsed) return null
    val at = v.selection.start
    val lineStart = v.text.lastIndexOf('\n', at - 1) + 1
    val line = v.text.substring(lineStart, at)
    val m = Regex("^([ \\t]*)([-*+][ \\t]\\[[ xX]][ \\t]|[-*+][ \\t]|(\\d+)([.)])[ \\t])").find(line) ?: return null
    if (line.substring(m.range.last + 1).isBlank() && v.text.substring(at).substringBefore('\n').isBlank()) {
        // Empty item: remove its mark (ends the list).
        val text = v.text.substring(0, lineStart) + v.text.substring(at)
        return TextFieldValue(text, TextRange(lineStart))
    }
    val next = when {
        m.groupValues[3].isNotEmpty() -> m.groupValues[1] + (m.groupValues[3].toInt() + 1) + m.groupValues[4] + " "
        "[" in m.groupValues[2] -> m.groupValues[1] + "- [ ] "
        else -> m.groupValues[1] + m.groupValues[2]
    }
    val insert = "\n" + next
    return TextFieldValue(v.text.substring(0, at) + insert + v.text.substring(at), TextRange(at + insert.length))
}

/** Tab/Umschalt+Tab: indents the list item at the cursor (or removes the indent). */
fun indentLine(v: TextFieldValue, outdent: Boolean): TextFieldValue {
    val at = v.selection.start
    val lineStart = v.text.lastIndexOf('\n', at - 1) + 1
    return if (outdent) {
        val n = v.text.substring(lineStart).takeWhile { it == ' ' }.length.coerceAtMost(2)
        if (n == 0) v else TextFieldValue(v.text.removeRange(lineStart, lineStart + n), TextRange((at - n).coerceAtLeast(lineStart)))
    } else TextFieldValue(v.text.substring(0, lineStart) + "  " + v.text.substring(lineStart), TextRange(at + 2))
}

/** Puts bold/italic marks around the selection (or the word at the cursor), or removes them again. */
fun toggleMarks(v: TextFieldValue, mark: String, end: String = mark): TextFieldValue {
    val sel = v.selection
    val t = v.text
    val (a, b) = if (sel.collapsed) {
        var s = sel.min
        var e = sel.max
        while (s > 0 && t[s - 1].isLetterOrDigit()) s--
        while (e < t.length && t[e].isLetterOrDigit()) e++
        s to e
    } else sel.min to sel.max
    val word = t.substring(a, b)
    if (a >= mark.length && t.startsWith(mark, a - mark.length) && t.startsWith(end, b)) {
        return TextFieldValue(t.substring(0, a - mark.length) + word + t.substring(b + end.length), TextRange(a - mark.length, b - mark.length))
    }
    return TextFieldValue(t.substring(0, a) + mark + word + end + t.substring(b), TextRange(a + mark.length, b + mark.length))
}
