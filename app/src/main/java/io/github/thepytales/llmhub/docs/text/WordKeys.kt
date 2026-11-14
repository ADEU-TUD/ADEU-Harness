package io.github.thepytales.llmhub.docs.text

/**
 * Tasten und Markieren wie Word (AP-125 c): where Strg+Pfeile, Strg+Rück/Entf and Strg+Klick go in running text, the
 * Groß-/Kleinschreibung of Umschalt+F3 and the steps of Strg+Umschalt+>/<. Pure functions on the text, so the editor
 * and the tests share them.
 */
object WordKeys {
    private enum class Kind { BREAK, SPACE, WORD, MARK }

    /** Letters and digits form words; an apostrophe between letters belongs to the word („geht’s“, „don't“). */
    private fun kind(text: String, i: Int): Kind {
        val c = text[i]
        return when {
            c == '\n' -> Kind.BREAK
            c.isWhitespace() -> Kind.SPACE
            c.isLetterOrDigit() || c == '_' -> Kind.WORD
            (c == '\'' || c == '’') && i > 0 && i + 1 < text.length && text[i - 1].isLetter() && text[i + 1].isLetter() -> Kind.WORD
            else -> Kind.MARK
        }
    }

    /**
     * Strg+→ (and the end of Strg+Entf): the start of the next word, as in Word on Windows – over the rest of this word
     * (or this run of punctuation) and the spaces after it; at the end of a paragraph the start of the next one.
     */
    fun nextWord(text: String, from: Int): Int {
        val len = text.length
        var i = from.coerceIn(0, len)
        if (i >= len) return len
        if (text[i] == '\n') return i + 1
        val k = kind(text, i)
        if (k == Kind.WORD || k == Kind.MARK) while (i < len && kind(text, i) == k) i++
        while (i < len && kind(text, i) == Kind.SPACE) i++
        return i
    }

    /** Strg+← (and the start of Strg+Rück): the start of this word or of the one before; a paragraph start stops it. */
    fun prevWord(text: String, from: Int): Int {
        var i = from.coerceIn(0, text.length)
        if (i <= 0) return 0
        if (text[i - 1] == '\n') return i - 1
        while (i > 0 && kind(text, i - 1) == Kind.SPACE) i--
        if (i == 0 || text[i - 1] == '\n') return i
        val k = kind(text, i - 1)
        while (i > 0 && kind(text, i - 1) == k) i--
        return i
    }

    /** The word at [at] (letters and digits around it) or an empty range at [at]. */
    fun wordAt(text: String, at: Int): IntRange {
        val p = at.coerceIn(0, text.length)
        var a = p
        var b = p
        while (a > 0 && kind(text, a - 1) == Kind.WORD) a--
        while (b < text.length && kind(text, b) == Kind.WORD) b++
        return a until b
    }

    private const val ENDS = ".!?…"

    /**
     * Strg+Klick (like Word): the sentence at [at] within its paragraph – from after the end of the sentence before (a
     * „.“, „!“, „?“ or „…“ followed by a space) to its own end mark and the spaces after it.
     */
    fun sentenceAt(text: String, at: Int): IntRange {
        val p = at.coerceIn(0, text.length)
        val paraStart = text.lastIndexOf('\n', p - 1).let { if (it < 0) 0 else it + 1 }
        val paraEnd = text.indexOf('\n', p).let { if (it < 0) text.length else it }
        var a = paraStart
        var i = p - 1
        while (i > paraStart) {
            if (text[i].isWhitespace() && text[i - 1] in ENDS) { a = i + 1; break }
            i--
        }
        while (a < paraEnd && text[a] == ' ') a++
        var b = paraEnd
        var j = maxOf(a, p)
        while (j < paraEnd) {
            if (text[j] in ENDS && (j + 1 >= paraEnd || text[j + 1].isWhitespace())) {
                b = j + 1
                while (b < paraEnd && text[b] == ' ') b++
                break
            }
            j++
        }
        return a until b
    }

    /**
     * Umschalt+F3 (like Word): kleinbuchstaben → Erster Buchstabe Groß → GROSSBUCHSTABEN → kleinbuchstaben; mixed
     * text becomes GROSSBUCHSTABEN. Letter by letter, so the text keeps its length (and its formatting).
     */
    fun cycleCase(s: String): String {
        val letters = s.filter { it.isLetter() }
        if (letters.isEmpty()) return s
        return when {
            letters.all { !it.isUpperCase() } -> titleCase(s)
            // „ß“ has no capital of its own here (STRAßE counts as capitals).
            letters.all { !it.isLowerCase() || it == 'ß' } -> s.map { it.lowercaseChar() }.joinToString("")
            else -> s.map { it.uppercaseChar() }.joinToString("")
        }
    }

    private fun titleCase(s: String): String {
        val out = StringBuilder(s.length)
        s.forEachIndexed { i, c ->
            val start = i == 0 || !(s[i - 1].isLetterOrDigit() || (s[i - 1] == '\'' || s[i - 1] == '’') && i > 1 && s[i - 2].isLetter())
            out.append(if (start) c.uppercaseChar() else c)
        }
        return out.toString()
    }

    /** The sizes of Word's list; Strg+Umschalt+>/< step through them. */
    val SIZES = listOf(8, 9, 10, 11, 12, 14, 16, 18, 20, 22, 24, 26, 28, 36, 48, 72)

    /** The next size from [pt] (Strg+Umschalt+> [up], Strg+Umschalt+< down): Word's steps, beyond 72 in tens, at least 1. */
    fun stepSize(pt: Float, up: Boolean): Int = if (up) {
        SIZES.firstOrNull { it > pt + 0.01f } ?: (((pt.toInt() / 10) + 1) * 10).coerceAtMost(400)
    } else {
        SIZES.lastOrNull { it < pt - 0.01f } ?: (kotlin.math.ceil(pt).toInt() - 1).coerceAtLeast(1)
    }
}
