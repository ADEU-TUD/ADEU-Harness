package io.github.thepytales.llmhub.docs.text

/**
 * Autokorrektur beim Tippen (AP-125, like Word): after one typed character the text around the cursor may change –
 * typographic quotes and dashes, symbols, a capital at the start of a sentence, two capitals at the start of a word,
 * common typos. Each rule can be switched off ([Rule.id], Extras › Autokorrektur). Works on plain text; the editor
 * applies the result as one change of its own, so Rückgängig right after it takes back only the correction.
 */
object AutoCorrect {
    enum class Rule(val id: String, val label: String) {
        QUOTES("quotes", "„Anführungszeichen“ und Apostroph ’"),
        DASHES("dashes", "-- wird –, ... wird …"),
        SYMBOLS("symbols", "(c) wird ©, (r) wird ®, -> wird →"),
        SENTENCE("sentence", "Satzanfang groß"),
        TWO_CAPS("twocaps", "ZWei GRoßbuchstaben am Wortanfang"),
        TYPOS("typos", "Häufige Tippfehler (udn → und)"),
    }

    /** One correction: replace [range] (in the text after typing) with [text]; the cursor ends at [cursor]. */
    data class Fix(val range: IntRange, val text: String, val cursor: Int, val rule: Rule)

    fun rulesOff(setting: String): Set<Rule> = setting.split(',').mapNotNull { id -> Rule.entries.firstOrNull { it.id == id.trim() } }.toSet()
    fun setting(off: Set<Rule>): String = off.joinToString(",") { it.id }

    /** Abbreviations after which no sentence begins (z. B., bzw. …), lower case, without the dot. */
    private val ABBREVIATIONS = setOf(
        "z", "b", "zb", "bzw", "ca", "vgl", "usw", "etc", "d", "h", "dh", "u", "a", "ua", "nr", "s", "str", "dr", "prof",
        "bspw", "evtl", "ggf", "inkl", "max", "min", "mind", "o", "sog", "tel", "v", "vs", "zzgl", "abs", "abb", "tab",
        "kap", "bd", "hrsg", "ff", "f", "e", "i", "eg", "ie", "al", "mr", "mrs", "ms", "jr", "st", "dipl", "ing", "m", "sc",
    )

    /** Common typos (lower case → correct); the case of the first letter is kept. */
    private val TYPOS = mapOf(
        "udn" to "und", "nciht" to "nicht", "nihct" to "nicht", "eien" to "eine", "ncoh" to "noch", "acuh" to "auch",
        "dsa" to "das", "dei" to "die", "sien" to "sein", "wrid" to "wird", "teh" to "the", "adn" to "and",
        "taht" to "that", "wiht" to "with",
    )

    private const val WORD_END = " \n\t.,;:!?)"

    /**
     * The correction after [typed] was typed at [cursor] − 1 in [text] (the text after typing; [cursor] behind the
     * new character), or null. [code]: the character stands in code (no correction there).
     */
    fun after(text: String, cursor: Int, typed: Char, off: Set<Rule> = emptySet(), code: Boolean = false): Fix? {
        if (code || cursor < 1 || cursor > text.length) return null
        val before = text.substring(0, cursor - 1)
        // Quotes as they are typed: „…“ in German, ’ as apostrophe inside a word.
        if (Rule.QUOTES !in off && typed == '"') {
            val prev = before.lastOrNull()
            val opening = prev == null || prev.isWhitespace() || prev in "([{–—/"
            return Fix(cursor - 1 until cursor, if (opening) "„" else "“", cursor, Rule.QUOTES)
        }
        if (Rule.QUOTES !in off && typed == '\'') {
            val prev = before.lastOrNull()
            if (prev != null && prev.isLetterOrDigit()) return Fix(cursor - 1 until cursor, "’", cursor, Rule.QUOTES)
            return null
        }
        if (Rule.DASHES !in off) {
            // ... → …
            if (typed == '.' && before.endsWith("..") && !before.endsWith("...")) return Fix(cursor - 3 until cursor, "…", cursor - 2, Rule.DASHES)
            // „Wort -- Wort“ → „Wort – Wort“ once the space after the dashes is typed.
            if (typed == ' ' && before.endsWith(" --")) return Fix(cursor - 3 until cursor, "– ", cursor - 1, Rule.DASHES)
        }
        if (Rule.SYMBOLS !in off) {
            val symbols = listOf("(c)" to "©", "(C)" to "©", "(r)" to "®", "(R)" to "®", "(tm)" to "™", "(TM)" to "™", "->" to "→", "<-" to "←", "=>" to "⇒")
            for ((from, to) in symbols) {
                if (from.last() == typed && (before + typed).endsWith(from)) {
                    val start = cursor - from.length
                    // Not inside longer arrows or a path such as „a->b“ in code-like text with no space before.
                    if (from == "<-" && before.endsWith("<<")) continue
                    return Fix(start until cursor, to, start + to.length, Rule.SYMBOLS)
                }
            }
        }
        // Rules at the end of a word: the word before the typed character.
        if (typed !in WORD_END) return null
        var a = cursor - 1
        while (a > 0 && text[a - 1].isLetter()) a--
        val word = text.substring(a, cursor - 1)
        if (word.isEmpty()) return null
        // A word fixed by a rule also gets its capital at the start of a sentence (one step, like Word).
        fun capital(w: String) = if (Rule.SENTENCE !in off && w[0].isLowerCase() && sentenceStartsAt(text, a)) w.replaceFirstChar { it.uppercase() } else w
        if (Rule.TYPOS !in off) {
            TYPOS[word.lowercase()]?.let { right ->
                val fixed = capital(if (word[0].isUpperCase()) right.replaceFirstChar { it.uppercase() } else right)
                if (fixed != word) return Fix(a until cursor - 1, fixed, cursor - word.length + fixed.length, Rule.TYPOS)
            }
        }
        if (Rule.TWO_CAPS !in off && word.length >= 3 && word[0].isUpperCase() && word[1].isUpperCase() && word.drop(2).all { it.isLowerCase() }) {
            val fixed = word[0] + word.substring(1).lowercase()
            return Fix(a until cursor - 1, fixed, cursor, Rule.TWO_CAPS)
        }
        if (Rule.SENTENCE !in off && word[0].isLowerCase() && sentenceStartsAt(text, a)) {
            return Fix(a until a + 1, word[0].uppercase(), cursor, Rule.SENTENCE)
        }
        return null
    }

    /** Does a sentence begin at [at]: at the start of a paragraph, or after „. “, „! “, „? “ – not after an abbreviation or a number. */
    internal fun sentenceStartsAt(text: String, at: Int): Boolean {
        var i = at
        // Spaces before the word.
        var spaces = 0
        while (i > 0 && text[i - 1] == ' ') { i--; spaces++ }
        if (i == 0 || text[i - 1] == '\n') return true
        if (spaces == 0) return false
        val mark = text[i - 1]
        if (mark == '!' || mark == '?') return true
        if (mark != '.') return false
        if (i >= 2 && text[i - 2] == '.') return false // „…“ typed as dots, or „..“
        // The token before the dot: an abbreviation (z.B., bzw.) or a number (1., 3.) starts no sentence.
        var s = i - 1
        while (s > 0 && !text[s - 1].isWhitespace()) s--
        val token = text.substring(s, i - 1).trim('(', '[', '"', '„', '“')
        if (token.isEmpty()) return false
        if (token.any { it.isDigit() }) return false
        val parts = token.lowercase().split('.').filter { it.isNotEmpty() }
        if (parts.size > 1 && parts.all { it.length <= 2 }) return false // z.B, d.h, u.a
        if (token.length == 1) return false // Initialen: „A. Weber“
        return token.lowercase() !in ABBREVIATIONS
    }
}
