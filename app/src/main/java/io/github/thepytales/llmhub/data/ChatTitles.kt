package io.github.thepytales.llmhub.data

/**
 * Short titles for new chats (AP-33), made on the device from the first message: courtesies and the request verb go,
 * the subject stays – „Suche bitte im Web nach aktuellen Ausschreibungen für Forschung, Lehre …“ becomes „Aktuelle
 * Ausschreibungen für Forschung“.
 */
object ChatTitles {
    private val FILLER = Regex(
        "^((bitte|hallo|hi|hey|guten (morgen|tag|abend)|lora|liebe lora)[,!:.]?\\s+|(kannst|könntest|koenntest|würdest|wuerdest) du( mir)?( bitte)?\\s+|" +
            "ich (möchte|moechte|will|brauche|hätte gern|haette gern|suche)( gern| gerne| bitte)?\\s+|hilf mir( bitte)?( dabei)?,?\\s+)+",
        RegexOption.IGNORE_CASE,
    )
    private val VERB = Regex(
        "^(such(e|st)?|recherchier(e|en)?|find(e|en)?|erstell(e|en)?|schreib(e|en)?|formulier(e|en)?|erklär(e|en)?|erklaer(e|en)?|fass(e|en)?|" +
            "übersetz(e|en)?|uebersetz(e|en)?|gib|nenn(e|en)?|zeig(e|en)?|mach(e|en)?|analysier(e|en)?|vergleich(e|en)?|prüf(e|en)?|pruef(e|en)?|plan(e|en)?|" +
            "entwirf|entwerfen|überarbeite|ueberarbeite|korrigier(e|en)?)\\b\\s*" +
            "((bitte|mir|mal|uns|kurz|kurze?n?|kurzer|zusammen)\\s+)*((im|in) (web|internet|netz)\\s+)?((nach|zu|über|ueber|für|fuer|mit)\\s+)?",
        RegexOption.IGNORE_CASE,
    )
    private val ARTICLE = Regex("^(eine?[nmrs]?|die|den|der|das|dem|des)\\s+", RegexOption.IGNORE_CASE)

    /** A short title for a chat that starts with [text]; [max] characters at most. */
    fun make(text: String, max: Int = 48): String {
        val first = text.trim().lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
            .replace(Regex("[*_`#>]"), "").trim()
        if (first.isEmpty()) return ""
        var t = first.replace(FILLER, "")
        t = t.replace(VERB, "").trimStart(':', ',', ';', '-', '–', ' ')
        t = t.replace(ARTICLE, "")
        // The first clause is usually the subject.
        val stop = Regex("[.?!;:]|,\\s").find(t)?.range?.first
        if (stop != null && stop >= 18) t = t.substring(0, stop)
        t = t.trim().trimEnd(',', '.', ':', ';', '-', '–').trim()
        if (t.length < 6) t = first
        t = t.replaceFirstChar { it.uppercase() }
        // German adjectives after „nach“ lose their -n („aktuellen Ausschreibungen“ → „Aktuelle Ausschreibungen“).
        t = t.replace(Regex("^([A-ZÄÖÜ][a-zäöüß]+e)n(\\s+[A-ZÄÖÜ][a-zäöüß]+en\\b)")) { m -> m.groupValues[1] + m.groupValues[2] }
        if (t.length <= max) return t
        val cut = t.take(max).substringBeforeLast(' ').ifBlank { t.take(max) }
        return cut.trimEnd(',', '.', ':', ';', '-', '–') + "…"
    }
}
