package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.docs.text.CharFormat
import io.github.thepytales.llmhub.docs.text.RichText
import io.github.thepytales.llmhub.ui.INLINE
import io.github.thepytales.llmhub.ui.INLINE_TAGS
import io.github.thepytales.llmhub.ui.Segment
import io.github.thepytales.llmhub.ui.withTag

/**
 * Formatierung innerhalb einer Zelle (AP-48, mit dem Text-Kern AP-43): a cell keeps its text as inline Markdown of the
 * document editor – `**fett**`, `*kursiv*`, `~~durch~~`, `<u>…</u>`, `<sup>`/`<sub>` and `<span style="color:#C00000">` –
 * so a part of a cell can be bold or red while the sheet stays a plain Markdown table. Formulas see the plain text,
 * Excel gets real rich-text runs (`<r><rPr>…`), and Word and PDF show the formatting as in the document.
 *
 * The scanner keeps for every character of the cell text whether it is a mark (hidden in the cell and the formula bar)
 * and the format of every visible character, so the formula bar can format a selection like Google Sheets does.
 */
object CellRich {
    /** [plain] with one format per character; [rawOf] maps each plain character to its index in the raw text. */
    class Parsed(val raw: String, val plain: String, val chars: List<CharFormat>, val hidden: BooleanArray, val rawOf: IntArray) {
        val rich: Boolean get() = chars.any { it != CharFormat.PLAIN }

        /** Plain offset (0…plain.length) of the raw offset [r]: the visible characters before it. */
        fun plainOffset(r: Int): Int {
            var n = 0
            for (i in 0 until r.coerceIn(0, raw.length)) if (!hidden[i]) n++
            return n
        }

        /** Raw offset of the plain offset [p]: before its character, or after the last visible one at the end. */
        fun rawOffset(p: Int): Int = when {
            plain.isEmpty() -> raw.length
            p <= 0 -> rawOf[0]
            p >= plain.length -> rawOf[plain.length - 1] + 1
            else -> rawOf[p]
        }
    }

    /** Characters that can start a mark (cells without them are plain, which keeps large sheets fast). */
    private fun mayHaveMarks(raw: String): Boolean {
        for (ch in raw) if (ch == '*' || ch == '<' || ch == '~' || ch == '_' || ch == '`' || ch == '[') return true
        return false
    }

    /** Whether the (non-formula) cell text [raw] has formatting inside. */
    fun isRich(raw: String): Boolean = !raw.trimStart().startsWith("=") && mayHaveMarks(raw) && parse(raw).rich

    /** The text of the cell without its marks (what formulas, filters and sorting see). */
    fun plain(raw: String): String = if (!mayHaveMarks(raw) || raw.trimStart().startsWith("=")) raw else parse(raw).plain

    fun parse(raw: String): Parsed {
        val hidden = BooleanArray(raw.length)
        val formats = arrayOfNulls<CharFormat>(raw.length)
        scan(raw, 0, raw.length, Segment(""), hidden, formats)
        val plain = StringBuilder()
        val chars = ArrayList<CharFormat>()
        val rawOf = IntArray(raw.length)
        for (i in raw.indices) if (!hidden[i]) {
            rawOf[plain.length] = i
            plain.append(raw[i])
            chars += formats[i] ?: CharFormat.PLAIN
        }
        return Parsed(raw, plain.toString(), chars, hidden, rawOf.copyOf(plain.length))
    }

    /** Tags first (they may hold Markdown), then the Markdown marks between them – like `inlineSegments`. */
    private fun scan(raw: String, from: Int, to: Int, base: Segment, hidden: BooleanArray, formats: Array<CharFormat?>) {
        if (from >= to) return
        val part = raw.substring(from, to)
        var last = 0
        if ('<' in part) for (m in INLINE_TAGS.findAll(part)) {
            marks(raw, from + last, from + m.range.first, base, hidden, formats)
            val content = m.groups[3]!!.range
            for (i in from + m.range.first until from + content.first) hidden[i] = true
            for (i in from + content.last + 1..from + m.range.last) hidden[i] = true
            scan(raw, from + content.first, from + content.last + 1, base.withTag(m.groupValues[1], m.groupValues[2]), hidden, formats)
            last = m.range.last + 1
        }
        marks(raw, from + last, to, base, hidden, formats)
    }

    private fun marks(raw: String, from: Int, to: Int, base: Segment, hidden: BooleanArray, formats: Array<CharFormat?>) {
        if (from >= to) return
        val part = raw.substring(from, to)
        var last = 0
        for (m in INLINE.findAll(part)) {
            for (i in from + last until from + m.range.first) formats[i] = CharFormat.of(base)
            val s = from + m.range.first
            val e = from + m.range.last + 1
            val g = m.groups
            fun inner(open: Int, close: Int, seg: Segment, recurse: Boolean) {
                for (i in s until s + open) hidden[i] = true
                for (i in e - close until e) hidden[i] = true
                if (recurse) scan(raw, s + open, e - close, seg, hidden, formats)
                else for (i in s + open until e - close) formats[i] = CharFormat.of(seg)
            }
            when {
                g[2] != null -> inner(2, 2, base.copy(bold = true), true)
                g[3] != null -> inner(1, 1, base.copy(code = true), false)
                g[4] != null -> inner(1, g[5]!!.value.length + 3, base.copy(link = g[5]!!.value), true)
                g[6] != null -> inner(1, 1, base.copy(italic = true), true)
                g[7] != null -> inner(2, 2, base.copy(strike = true), true)
            }
            last = m.range.last + 1
        }
        for (i in from + last until to) formats[i] = CharFormat.of(base)
    }

    /** The cell text of [plain] with [chars] (runs of equal format, as the document editor writes them). */
    fun write(plain: String, chars: List<CharFormat>): String = RichText.inline(plain, chars)

    /** What a format button changes in a part of a cell. */
    enum class Mark { BOLD, ITALIC, UNDERLINE, STRIKE }

    /**
     * [raw] with [mark] switched for the plain characters [from] until [to]: on if one of them does not have it yet
     * (like Word), otherwise off.
     */
    fun toggle(raw: String, from: Int, to: Int, mark: Mark): String {
        val p = parse(raw)
        val a = from.coerceIn(0, p.plain.length)
        val b = to.coerceIn(a, p.plain.length)
        if (a == b) return raw
        fun has(f: CharFormat) = when (mark) { Mark.BOLD -> f.bold; Mark.ITALIC -> f.italic; Mark.UNDERLINE -> f.underline; Mark.STRIKE -> f.strike }
        val on = (a until b).any { !has(p.chars[it]) }
        val chars = p.chars.mapIndexed { i, f ->
            if (i !in a until b) f else when (mark) {
                Mark.BOLD -> f.copy(bold = on)
                Mark.ITALIC -> f.copy(italic = on)
                Mark.UNDERLINE -> f.copy(underline = on)
                Mark.STRIKE -> f.copy(strike = on)
            }
        }
        return write(p.plain, chars)
    }

    /** [raw] with the text color [color] ("#RRGGBB" or null for automatic) for the plain characters [from] until [to]. */
    fun color(raw: String, from: Int, to: Int, color: String?): String {
        val p = parse(raw)
        val a = from.coerceIn(0, p.plain.length)
        val b = to.coerceIn(a, p.plain.length)
        if (a == b) return raw
        val c = color?.removePrefix("#")?.uppercase()
        return write(p.plain, p.chars.mapIndexed { i, f -> if (i in a until b) f.copy(color = c) else f })
    }

    /** Whether all plain characters [from] until [to] have [mark] (for the state of the format buttons). */
    fun has(raw: String, from: Int, to: Int, mark: Mark): Boolean {
        val p = parse(raw)
        val a = from.coerceIn(0, p.plain.length)
        val b = to.coerceIn(a, p.plain.length)
        if (a == b) return false
        return (a until b).all { i ->
            val f = p.chars[i]
            when (mark) { Mark.BOLD -> f.bold; Mark.ITALIC -> f.italic; Mark.UNDERLINE -> f.underline; Mark.STRIKE -> f.strike }
        }
    }

    /** Runs of equal format (text, format) of a cell. */
    fun runs(raw: String): List<Pair<String, CharFormat>> {
        val p = parse(raw)
        val out = ArrayList<Pair<String, CharFormat>>()
        var i = 0
        while (i < p.plain.length) {
            var j = i + 1
            while (j < p.plain.length && p.chars[j] == p.chars[i]) j++
            out += p.plain.substring(i, j) to p.chars[i]
            i = j
        }
        return out
    }

    // ── Excel ──

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")

    /** The runs of a rich cell as the content of Excel's `<is>`/`<si>` (`<r><rPr>…</rPr><t>…</t></r>`). */
    fun xlsxRuns(raw: String, bold: Boolean = false, italic: Boolean = false): String = runs(raw).joinToString("") { (text, f) ->
        val pr = buildString {
            if (f.bold || bold) append("<b/>")
            if (f.italic || italic) append("<i/>")
            if (f.strike) append("<strike/>")
            if (f.underline) append("<u/>")
            if (f.sup) append("<vertAlign val=\"superscript\"/>") else if (f.sub) append("<vertAlign val=\"subscript\"/>")
            append("<sz val=\"${f.size ?: 11}\"/>")
            f.color?.let { append("<color rgb=\"FF${it.uppercase()}\"/>") }
            append("<rFont val=\"${esc(f.font ?: "Calibri")}\"/>")
        }
        "<r><rPr>$pr</rPr><t xml:space=\"preserve\">${esc(text)}</t></r>"
    }

    /**
     * Rich-text runs read from Excel (text, format) as cell text; sizes and fonts only where they differ from the
     * first run (Excel repeats the cell's font in every run), black counts as automatic.
     */
    fun fromRuns(runs: List<Pair<String, CharFormat>>): String {
        if (runs.isEmpty()) return ""
        val baseSize = runs.first().second.size
        val baseFont = runs.first().second.font
        // A mark every run has belongs to the whole cell (its cell format, e.g. the bold head row).
        val allBold = runs.all { it.second.bold }
        val allItalic = runs.all { it.second.italic }
        val text = StringBuilder()
        val chars = ArrayList<CharFormat>()
        runs.forEach { (t, f) ->
            val g = f.copy(
                bold = f.bold && !allBold,
                italic = f.italic && !allItalic,
                size = f.size?.takeIf { it != baseSize },
                font = f.font?.takeIf { it != baseFont },
                color = f.color?.uppercase()?.takeIf { it != "000000" },
            )
            text.append(t)
            repeat(t.length) { chars += g }
        }
        if (chars.all { it == CharFormat.PLAIN }) return text.toString()
        return write(text.toString(), chars)
    }
}
