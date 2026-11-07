package io.github.thepytales.llmhub.docs.text

import io.github.thepytales.llmhub.docs.DocMarkup
import io.github.thepytales.llmhub.ui.ALIGN_MARK
import io.github.thepytales.llmhub.ui.Segment
import io.github.thepytales.llmhub.ui.inlineSegments

/**
 * Gemeinsamer Text-Kern (AP-43, Stufe a/b für den Fließtext-Editor AP-44): running text as one string with a
 * character format per character and a paragraph format per paragraph (paragraphs are separated by '\n'). The editor
 * works on it as one piece – a continuous cursor, selecting across paragraphs –, and it converts to and from the
 * document Markdown, which stays the storage and exchange format (chat, exports, Word).
 */
data class CharFormat(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val code: Boolean = false,
    val sup: Boolean = false,
    val sub: Boolean = false,
    /** RRGGBB or null. */
    val color: String? = null,
    val highlight: String? = null,
    /** Points or null (size of the paragraph style). */
    val size: Int? = null,
    val font: String? = null,
    val link: String? = null,
    /** Lora is writing this right now (AP-47): shown highlighted until it is taken or discarded. */
    val ai: Boolean = false,
    /** Suggestion [ins] > 0: inserted by suggestion number [ins]; [del] > 0: deleted by suggestion number [del]. */
    val ins: Int = 0,
    val del: Int = 0,
) {
    /** The format as stored (without Lora's marks). */
    val stored: CharFormat get() = if (!ai && ins == 0 && del == 0) this else copy(ai = false, ins = 0, del = 0)

    companion object {
        val PLAIN = CharFormat()

        internal fun of(s: Segment) = CharFormat(
            bold = s.bold, italic = s.italic, underline = s.underline, strike = s.strike, code = s.code, sup = s.sup, sub = s.sub,
            color = s.color, highlight = s.highlight, size = s.size, font = s.font, link = s.link,
        )
    }
}

/**
 * Formatvorlagen of a paragraph (AP-46). Headings, quotes and lists are Markdown; the others ([mark]) are normal
 * paragraphs with the setting `s=…` in their paragraph marker (see ALIGN_MARK), so every export still reads them as text.
 */
enum class ParaKind(val label: String, val level: Int = 0, val mark: String? = null) {
    NORMAL("Normaler Text"), TITLE("Titel", mark = "title"), SUBTITLE("Untertitel", mark = "subtitle"),
    H1("Überschrift 1", 1), H2("Überschrift 2", 2), H3("Überschrift 3", 3), H4("Überschrift 4", 4),
    QUOTE("Zitat"), CAPTION("Bildunterschrift", mark = "caption"), CODE("Code", mark = "code"), LITERATURE("Literatur", mark = "lit"),
    BULLET("Aufzählung"), NUMBERED("Nummerierte Liste"), CHECK("Checkliste");

    val heading: Boolean get() = level > 0
    val list: Boolean get() = this == BULLET || this == NUMBERED || this == CHECK

    /** After Enter at the end of a paragraph of this kind, the new one is normal text (titles, headings, captions). */
    val endsAtEnter: Boolean get() = heading || this == TITLE || this == SUBTITLE || this == CAPTION

    companion object {
        fun heading(level: Int) = when (level) { 1 -> H1; 2 -> H2; 3 -> H3; else -> H4 }
        fun ofMark(mark: String?): ParaKind? = entries.firstOrNull { it.mark != null && it.mark == mark }
    }
}

enum class Align(val mark: String?) { LEFT(null), CENTER("c"), RIGHT("r"), JUSTIFY("j") }

/** How the text after a tab stands at its Tabstopp: starting there, centred on it, or ending there. */
enum class TabKind(val mark: Char) { LEFT('l'), CENTER('c'), RIGHT('r') }

/** Tabstopp (AP-44): [mm] from the left margin (as in Word). */
data class TabStop(val mm: Int, val kind: TabKind = TabKind.LEFT) {
    companion object {
        /** Without own stops a tab goes to the next multiple of 12,5 mm (Word's default). */
        const val DEFAULT_MM = 12.5f

        private val STOP = Regex("(\\d{1,3})([lcr])")

        /** `40l120r` → stops at 40 mm (left) and 120 mm (right), sorted. */
        fun parse(v: String): List<TabStop> = STOP.findAll(v).map { m ->
            TabStop(m.groupValues[1].toInt().coerceIn(0, 400), TabKind.entries.first { it.mark == m.groupValues[2][0] })
        }.distinctBy { it.mm }.sortedBy { it.mm }.toList()

        fun write(stops: List<TabStop>): String = stops.sortedBy { it.mm }.joinToString("") { "${it.mm}${it.kind.mark}" }
    }
}

/**
 * Format of one paragraph. [indent] is the list level (0…5); [soft]: this paragraph is the next line of the one before
 * (a line break inside a Markdown paragraph, e.g. an address). [spacing]: line spacing (1.0, 1.15, 1.5, 2.0; null = that
 * of the Formatvorlage); [left] and [first]: left indent and first-line indent in mm (negative [first]: hanging).
 */
data class ParaFormat(
    val kind: ParaKind = ParaKind.NORMAL,
    val align: Align = Align.LEFT,
    val indent: Int = 0,
    val checked: Boolean = false,
    val soft: Boolean = false,
    val spacing: Float? = null,
    val left: Int = 0,
    val first: Int = 0,
    /** Tabstopps of the paragraph (AP-44), sorted; empty: every 12,5 mm. */
    val tabs: List<TabStop> = emptyList(),
) {
    /** The paragraph marker (`<!--a:c;s=title-->`) or "" when it has nothing to say. */
    fun marker(): String {
        val parts = buildList {
            kind.mark?.let { add("s=$it") }
            spacing?.let { add("z=" + (if (it == it.toInt().toFloat()) it.toInt().toString() else it.toString())) }
            if (left != 0) add("l=$left")
            if (first != 0) add("f=$first")
            if (tabs.isNotEmpty()) add("t=" + TabStop.write(tabs))
        }
        if (align == Align.LEFT && parts.isEmpty()) return ""
        return "<!--a:" + (align.mark ?: "") + parts.joinToString("") { ";$it" } + "-->"
    }

    companion object {
        /** [base] with what the paragraph marker [m] (a match of ALIGN_MARK) says. */
        fun fromMarker(base: ParaFormat, m: MatchResult): ParaFormat {
            var p = base.copy(align = Align.entries.firstOrNull { it.mark != null && it.mark == m.groupValues[1] } ?: Align.LEFT)
            for (kv in m.groupValues[2].split(';')) {
                val k = kv.substringBefore('=', "")
                val v = kv.substringAfter('=', "")
                when (k) {
                    "s" -> ParaKind.ofMark(v)?.let { if (!p.kind.heading && !p.kind.list && p.kind != ParaKind.QUOTE) p = p.copy(kind = it) }
                    "z" -> v.toFloatOrNull()?.takeIf { it in 0.5f..4f }?.let { p = p.copy(spacing = it) }
                    "l" -> v.toIntOrNull()?.let { p = p.copy(left = it.coerceIn(0, 150)) }
                    "f" -> v.toIntOrNull()?.let { p = p.copy(first = it.coerceIn(-80, 80)) }
                    "t" -> p = p.copy(tabs = TabStop.parse(v))
                }
            }
            return p
        }
    }
}

/** Running text: [text] with one [CharFormat] per character and one [ParaFormat] per paragraph. */
class RichText(val text: String, val chars: List<CharFormat>, val paras: List<ParaFormat>) {
    init {
        require(chars.size == text.length) { "chars ${chars.size} != text ${text.length}" }
        require(paras.size == text.count { it == '\n' } + 1) { "paras ${paras.size} != lines" }
    }

    /** Start offsets of the paragraphs. */
    val starts: IntArray by lazy {
        val out = IntArray(paras.size)
        var p = 1
        text.forEachIndexed { i, ch -> if (ch == '\n') out[p++] = i + 1 }
        out
    }

    /** The paragraph [offset] is in (binary search: large documents stay quick while typing, AP-43 d). */
    fun paraAt(offset: Int): Int {
        val s = starts
        var lo = 0
        var hi = s.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (s[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun paraRange(i: Int): IntRange = starts[i] until (if (i + 1 < starts.size) starts[i + 1] - 1 else text.length)

    fun paraText(i: Int): String = text.substring(paraRange(i).first, paraRange(i).last + 1)

    /** The piece from [from] until [to] with its formats (for the clipboard). */
    fun slice(from: Int, to: Int): RichText {
        val a = from.coerceIn(0, text.length)
        val b = to.coerceIn(a, text.length)
        return RichText(text.substring(a, b), chars.subList(a, b), paras.subList(paraAt(a), paraAt(b) + 1))
    }

    /** The same text with other paragraph formats. */
    fun withParas(p: List<ParaFormat>) = RichText(text, chars, p)

    /**
     * [new] text replacing [old] text of this one (as the text field reports a change): the character formats move
     * with the text, typed characters take [typing] (or the format before them), paragraph formats follow the lines.
     */
    fun edited(newText: String, typing: CharFormat?): RichText {
        if (newText == text) return this
        var pre = 0
        val max = minOf(text.length, newText.length)
        while (pre < max && text[pre] == newText[pre]) pre++
        var suf = 0
        while (suf < max - pre && text[text.length - 1 - suf] == newText[newText.length - 1 - suf]) suf++
        var removedEnd = text.length - suf
        var inserted = newText.substring(pre, newText.length - suf)
        // Enter at the end of a paragraph and at the start of the next one give the same texts; the line break
        // belongs to the end of the paragraph before (so a heading followed by a heading keeps both formats).
        while (removedEnd == pre && pre > 0 && inserted.isNotEmpty() && inserted.last() == '\n' && text[pre - 1] == '\n') {
            inserted = "\n" + inserted.dropLast(1)
            pre--
            removedEnd = pre
        }
        val fmt = typing ?: chars.getOrNull(pre - 1)?.takeIf { pre > 0 && text[pre - 1] != '\n' } ?: chars.getOrNull(pre)?.takeIf { pre < text.length && text[pre] != '\n' } ?: CharFormat.PLAIN
        // Links do not grow at their end; Lora's marks never pass on to what the user types.
        val typed = (if (typing == null && fmt.link != null && (pre >= text.length || chars.getOrNull(pre)?.link != fmt.link)) fmt.copy(link = null) else fmt)
            .let { if (typing == null) it.stored else it }
        val newChars = ArrayList<CharFormat>(newText.length)
        newChars.addAll(chars.subList(0, pre))
        inserted.forEach { newChars += typed }
        newChars.addAll(chars.subList(removedEnd, chars.size))
        // Paragraphs: those before the change and after it keep theirs; the changed range takes the first one's
        // format, new paragraphs after a heading are normal text, list items continue the list.
        val first = paraAt(pre)
        val last = paraAt(removedEnd)
        val newLines = inserted.count { it == '\n' }
        val head = paras[first]
        val next = if (head.kind.endsAtEnter) ParaFormat(align = head.align) else head.copy(checked = false, soft = false)
        val newParas = ArrayList<ParaFormat>(paras.size - (last - first) + newLines)
        newParas.addAll(paras.subList(0, first))
        newParas += head
        repeat(newLines) { newParas += next }
        newParas.addAll(paras.subList(last + 1, paras.size))
        return RichText(newText, newChars, newParas)
    }

    /** Applies [change] to the characters of [range]. */
    fun format(range: IntRange, change: (CharFormat) -> CharFormat): RichText {
        if (range.isEmpty()) return this
        val list = chars.toMutableList()
        for (i in range.first.coerceAtLeast(0)..range.last.coerceAtMost(text.length - 1)) if (text[i] != '\n') list[i] = change(list[i])
        return RichText(text, list, paras)
    }

    /** Applies [change] to the paragraphs touched by [from]..[to]. */
    fun formatParas(from: Int, to: Int, change: (ParaFormat) -> ParaFormat): RichText {
        val a = paraAt(from)
        val b = paraAt(to.coerceAtLeast(from))
        return RichText(text, chars, paras.mapIndexed { i, p -> if (i in a..b) change(p) else p })
    }

    /** Replaces [range] with [insert] (all in [fmt]). */
    fun replace(range: IntRange, insert: String, fmt: CharFormat = CharFormat.PLAIN): RichText {
        val start = range.first.coerceIn(0, text.length)
        val end = (range.last + 1).coerceIn(start, text.length)
        val newText = text.substring(0, start) + insert + text.substring(end)
        val r = edited(newText, fmt)
        return r
    }

    /**
     * [other] inserted at [offset]: its first paragraph continues the one at [offset] (taking its format when that one
     * is empty), the following paragraphs keep their own formats.
     */
    fun insert(offset: Int, other: RichText): RichText {
        val at = offset.coerceIn(0, text.length)
        val p = paraAt(at)
        val newText = text.substring(0, at) + other.text + text.substring(at)
        val newChars = chars.subList(0, at) + other.chars + chars.subList(at, chars.size)
        val here = paras[p]
        val emptyHere = paraText(p).isBlank()
        val firstFmt = if (emptyHere) other.paras.first() else here
        val tailFmt = if (other.paras.size > 1) other.paras.last() else firstFmt
        val newParas = buildList {
            addAll(paras.subList(0, p))
            add(firstFmt)
            if (other.paras.size > 1) {
                addAll(other.paras.subList(1, other.paras.size - 1))
                add(tailFmt)
            }
            addAll(paras.subList(p + 1, paras.size))
        }
        return RichText(newText, newChars, newParas)
    }

    /** Removes the characters for which [drop] is true (paragraph formats follow the removed line breaks). */
    fun removeWhere(drop: (Int) -> Boolean): RichText {
        var r = this
        var i = r.text.length - 1
        while (i >= 0) {
            if (drop(i)) {
                var j = i
                while (j - 1 >= 0 && drop(j - 1)) j--
                r = r.cut(j, i + 1)
                i = j - 1
            } else i--
        }
        return r
    }

    /** The text without [from] until [to] (paragraphs merge like when deleting in the editor). */
    fun cut(from: Int, to: Int): RichText {
        val a = from.coerceIn(0, text.length)
        val b = to.coerceIn(a, text.length)
        val first = paraAt(a)
        val last = paraAt(b)
        return RichText(
            text.substring(0, a) + text.substring(b),
            chars.subList(0, a) + chars.subList(b, chars.size),
            paras.subList(0, first + 1) + paras.subList(last + 1, paras.size),
        )
    }

    /** Numbers of the open suggestions (AP-47), in text order. */
    val suggestions: List<Int> by lazy { chars.flatMap { listOf(it.ins, it.del) }.filter { it > 0 }.distinct() }

    /** Accepts ([accept] true) or rejects suggestion [n]. */
    fun settle(n: Int, accept: Boolean): RichText {
        val r = removeWhere { i -> if (accept) chars[i].del == n else chars[i].ins == n }
        return RichText(r.text, r.chars.map { if (it.ins == n || it.del == n) it.copy(ins = 0, del = 0) else it }, r.paras)
    }

    /** Takes what Lora wrote (her highlight goes away). */
    fun keepAi(): RichText = RichText(text, chars.map { if (it.ai) it.copy(ai = false) else it }, paras)

    companion object {
        fun empty() = RichText("", emptyList(), listOf(ParaFormat()))

        /** The running text of a piece of Markdown (Lora's answer, pasted text); [mark] applies to every character. */
        fun ofMarkdown(markdown: String, mark: (CharFormat) -> CharFormat = { it }): RichText {
            val blocks = io.github.thepytales.llmhub.docs.DocBlocks.split(markdown).filter(::isFlowBlock)
            val r = if (blocks.isEmpty()) RichText(markdown.trim().replace('\n', ' '), List(markdown.trim().length) { CharFormat.PLAIN }, listOf(ParaFormat())) else fromBlocks(blocks)
            return RichText(r.text, r.chars.map(mark), r.paras)
        }

        /**
         * Suggestions (AP-47): [old] (formatted) replaced by [new] (plain text) word by word – kept words stay, removed
         * ones are marked deleted, added ones inserted; each changed stretch gets its own number from [firstId].
         */
        fun suggest(old: RichText, new: String, firstId: Int): RichText {
            val a = tokens(old.text)
            val b = tokens(new)
            // Longest common subsequence of the words.
            val n = a.size
            val m = b.size
            val dp = Array(n + 1) { IntArray(m + 1) }
            for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
            val text = StringBuilder()
            val chars = ArrayList<CharFormat>()
            var id = firstId - 1
            var inChange = false
            var i = 0
            var j = 0
            var pos = 0
            val oldStarts = IntArray(n + 1).also { arr -> var p = 0; a.forEachIndexed { k, w -> arr[k] = p; p += w.length }; arr[n] = p }
            fun keep(k: Int) {
                val w = a[k]
                text.append(w)
                for (c in 0 until w.length) chars += old.chars[oldStarts[k] + c]
                inChange = false
            }
            fun open() { if (!inChange) { id++; inChange = true } }
            while (i < n || j < m) {
                when {
                    i < n && j < m && a[i] == b[j] -> { keep(i); i++; j++ }
                    j < m && (i == n || dp[i][j + 1] >= dp[i + 1][j]) -> {
                        open()
                        val w = b[j]
                        val base = if (pos > 0) chars.lastOrNull()?.stored ?: CharFormat.PLAIN else old.chars.firstOrNull()?.stored ?: CharFormat.PLAIN
                        text.append(w)
                        repeat(w.length) { chars += base.copy(ins = id) }
                        j++
                    }
                    else -> {
                        open()
                        val w = a[i]
                        text.append(w)
                        for (c in 0 until w.length) chars += old.chars[oldStarts[i] + c].copy(del = id)
                        i++
                    }
                }
                pos = text.length
            }
            val t = text.toString()
            // Line breaks: the old paragraphs keep their formats; new ones are normal text.
            val paras = ArrayList<ParaFormat>()
            var oldPara = 0
            paras += old.paras.first()
            for (k in t.indices) if (t[k] == '\n') {
                if (chars[k].ins == 0 && oldPara + 1 < old.paras.size) { oldPara++; paras += old.paras[oldPara] } else paras += ParaFormat()
            }
            return RichText(t, chars, paras)
        }

        /** Words with the spaces after them, and line breaks on their own. */
        private fun tokens(s: String): List<String> = Regex("\\n|[^\\s]+[ \\t]*|[ \\t]+").findAll(s).map { it.value }.toList()


        // ── Markdown → running text ───────────────────────────────────────────────────────────────────

        private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
        private val LIST = Regex("^(\\s*)([-*+]|\\d+[.)])\\s+(\\[([ xX])]\\s+)?(.*)$")

        /** Whether a block of the document belongs to the running text (paragraphs, headings, lists, quotes). */
        fun isFlowBlock(block: String): Boolean {
            val t = block.trim()
            if (t.isEmpty()) return false
            if (t.startsWith("```") || t.startsWith("|") || t.startsWith("$$") || t.startsWith("<!--") && !ALIGN_MARK.containsMatchIn(t)) return false
            if (DocMarkup.isPageBreak(t) || DocMarkup.isToc(t) || DocMarkup.isComment(t) || DocMarkup.image(t) != null) return false
            if (t.matches(Regex("^([-*_])\\1{2,}$"))) return false
            if (t.lines().any { it.trimStart().startsWith("[^") && it.contains("]:") }) return false
            // Raw HTML blocks other than the editor's own tags stay as they are.
            if (t.startsWith("<") && !t.startsWith("<u>") && !t.startsWith("<mark") && !t.startsWith("<span") && !t.startsWith("<sup>") && !t.startsWith("<sub>") && !ALIGN_MARK.containsMatchIn(t)) return false
            return true
        }

        /** The running text of consecutive flow blocks. */
        fun fromBlocks(blocks: List<String>): RichText {
            val text = StringBuilder()
            val chars = ArrayList<CharFormat>()
            val paras = ArrayList<ParaFormat>()
            fun para(fmt: ParaFormat, inline: String) {
                if (paras.isNotEmpty()) { text.append('\n'); chars += CharFormat.PLAIN }
                var body = inline
                var f = fmt
                ALIGN_MARK.find(body)?.let { m ->
                    f = ParaFormat.fromMarker(fmt, m)
                    body = body.substring(m.range.last + 1)
                }
                paras += f
                inlineSegments(body).forEach { seg ->
                    val f = CharFormat.of(seg)
                    // Line breaks inside one run cannot happen here (paragraph lines are split before).
                    text.append(seg.text.replace('\n', ' '))
                    repeat(seg.text.length) { chars += f }
                }
            }
            for (block in blocks) {
                val lines = block.lines()
                val t = block.trim()
                when {
                    HEADING.matches(t) -> {
                        val m = HEADING.find(t)!!
                        para(ParaFormat(kind = ParaKind.heading(m.groupValues[1].length)), m.groupValues[2])
                    }
                    t.startsWith(">") -> lines.forEachIndexed { i, l ->
                        para(ParaFormat(kind = ParaKind.QUOTE, soft = i > 0), l.trimStart().removePrefix(">").removePrefix(" "))
                    }
                    LIST.containsMatchIn(lines.first()) -> {
                        for (l in lines) {
                            if (l.isBlank()) continue
                            val m = LIST.find(l)
                            if (m == null) {
                                // A continuation line of the item before.
                                para(paras.lastOrNull()?.copy(soft = true) ?: ParaFormat(), l.trim())
                                continue
                            }
                            val level = (m.groupValues[1].replace("\t", "  ").length / 2).coerceIn(0, 5)
                            val box = m.groupValues[4]
                            val kind = when {
                                m.groupValues[3].isNotEmpty() -> ParaKind.CHECK
                                m.groupValues[2][0].isDigit() -> ParaKind.NUMBERED
                                else -> ParaKind.BULLET
                            }
                            para(ParaFormat(kind = kind, indent = level, checked = box.equals("x", true)), m.groupValues[5])
                        }
                    }
                    else -> lines.forEachIndexed { i, l -> para(ParaFormat(soft = i > 0), l.trimEnd().removeSuffix("  ").removeSuffix("\\")) }
                }
            }
            if (paras.isEmpty()) paras += ParaFormat()
            return RichText(text.toString(), chars, paras)
        }

        // ── Running text → Markdown ───────────────────────────────────────────────────────────────────

        /** The inline Markdown of [text] with [chars] (runs of equal format, tags outside, Markdown marks inside). */
        fun inline(text: String, chars: List<CharFormat>): String {
            // Suggested insertions are not stored until accepted; suggested deletions stay until accepted.
            val keep = text.indices.filter { chars[it].ins == 0 }
            val t = keep.map { text[it] }.joinToString("")
            val f = keep.map { chars[it].stored }
            val out = StringBuilder()
            var i = 0
            while (i < t.length) {
                val fi = f[i]
                var j = i + 1
                while (j < t.length && f[j] == fi) j++
                out.append(run(t.substring(i, j), fi))
                i = j
            }
            return out.toString()
        }

        private fun run(raw: String, f: CharFormat): String {
            if (raw.isEmpty()) return ""
            if (f == CharFormat.PLAIN) return raw
            // Spaces at the edges stay outside the marks („** x**“ is no bold text in Markdown).
            val lead = raw.takeWhile { it == ' ' }
            val trail = raw.takeLastWhile { it == ' ' }.takeIf { lead.length < raw.length }.orEmpty()
            var t = raw.substring(lead.length, raw.length - trail.length)
            if (t.isEmpty()) return raw
            if (f.code) t = "`$t`"
            if (f.link != null) t = "[$t](${f.link})"
            if (f.strike) t = "~~$t~~"
            if (f.italic) t = "*$t*"
            if (f.bold) t = "**$t**"
            if (f.sup) t = "<sup>$t</sup>" else if (f.sub) t = "<sub>$t</sub>"
            if (f.underline) t = "<u>$t</u>"
            val css = buildList {
                f.color?.let { add("color:#$it") }
                f.highlight?.let { add("background:#$it") }
                f.size?.let { add("font-size:${it}pt") }
                f.font?.let { add("font-family:$it") }
            }
            if (css.isNotEmpty()) t = "<span style=\"${css.joinToString("; ")}\">$t</span>"
            return lead + t + trail
        }

        /** The Markdown blocks of the running text (headings, paragraphs, lists, quotes). */
        fun toBlocks(r: RichText): List<String> {
            val blocks = mutableListOf<String>()
            var cur: StringBuilder? = null
            var curKind: ParaKind? = null
            val numbers = IntArray(6)
            fun flush() {
                cur?.let { if (it.isNotBlank()) blocks += it.toString() }
                cur = null
                curKind = null
            }
            for (i in r.paras.indices) {
                val p = r.paras[i]
                val range = r.paraRange(i)
                val body = inline(r.text.substring(range.first, range.last + 1), r.chars.subList(range.first, range.last + 1))
                // A paragraph starting with a tab or space gets an (empty) marker, so Markdown does not take it for code.
                val align = p.marker().ifEmpty { if (body.startsWith('\t') || body.startsWith("    ")) "<!--a:-->" else "" }
                val listLike = p.kind.list
                when {
                    p.kind.heading -> {
                        flush()
                        blocks += "#".repeat(p.kind.level) + " " + align + body
                    }
                    p.kind == ParaKind.QUOTE -> {
                        if (curKind != ParaKind.QUOTE) flush()
                        val b = cur ?: StringBuilder().also { cur = it; curKind = ParaKind.QUOTE }
                        if (b.isNotEmpty()) b.append('\n')
                        b.append("> ").append(align).append(body)
                    }
                    listLike -> {
                        // A list of another kind (bullets → numbers → checklist) starts its own block.
                        if (curKind == null || !curKind!!.list || (!p.soft && p.indent == 0 && curKind != p.kind)) { flush(); numbers.fill(0) }
                        val b = cur ?: StringBuilder().also { cur = it }
                        curKind = p.kind
                        if (p.soft) {
                            b.append('\n').append("  ".repeat(p.indent + 1)).append(body)
                            continue
                        }
                        val lvl = p.indent.coerceIn(0, 5)
                        for (k in lvl + 1 until numbers.size) numbers[k] = 0
                        if (b.isNotEmpty()) b.append('\n')
                        b.append("  ".repeat(lvl))
                        when (p.kind) {
                            ParaKind.NUMBERED -> { numbers[lvl]++; b.append("${numbers[lvl]}. ") }
                            ParaKind.CHECK -> b.append(if (p.checked) "- [x] " else "- [ ] ")
                            else -> b.append("- ")
                        }
                        b.append(align).append(body)
                    }
                    p.soft && curKind == ParaKind.NORMAL && cur != null -> cur!!.append("  \n").append(body)
                    else -> {
                        flush()
                        if (body.isBlank()) continue
                        cur = StringBuilder(align + body)
                        curKind = ParaKind.NORMAL
                    }
                }
            }
            flush()
            return blocks
        }
    }
}
