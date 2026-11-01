package io.github.thepytales.llmhub.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Language of the interface (Einstellungen › Darstellung). German is the source language; [tr] looks up the
 * English text and falls back to German for everything not translated yet, so screens can be translated one by one.
 */
object I18n {
    val LANGUAGES = listOf("de" to "Deutsch", "en" to "English")

    /** Set from the settings by the theme; a state so the interface switches at once. */
    var language by mutableStateOf("de")

    /**
     * German → English, read once from `i18n/en.tsv` in the resources (shared by both platforms): one line per text,
     * German, a tab, English; `\n`, `\t` and `\\` stand for line break, tab and backslash; `#` starts a comment.
     */
    val EN: Map<String, String> by lazy { load() }

    private fun load(): Map<String, String> {
        val stream = I18n::class.java.getResourceAsStream("/i18n/en.tsv") ?: return emptyMap()
        return stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) null else unescape(line.substring(0, tab)) to unescape(line.substring(tab + 1))
            }.toMap()
        }
    }

    private fun unescape(s: String): String {
        if ('\\' !in s) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                out.append(when (s[i + 1]) { 'n' -> '\n'; 't' -> '\t'; else -> s[i + 1] })
                i += 2
            } else {
                out.append(c); i++
            }
        }
        return out.toString()
    }

    fun tr(german: String): String = if (language == "en") EN[german] ?: german else german

    /**
     * Text with placeholders: `tr("„{0}“ gelöscht", name)` translates the text and then puts the values in for `{0}`, `{1}` …
     * (in both languages), so the English text can order them differently.
     */
    fun tr(german: String, vararg args: Any?): String {
        var text = tr(german)
        args.forEachIndexed { i, a -> text = text.replace("{$i}", a.toString()) }
        return text
    }

    /** Added to the system prompt so answers follow the interface language unless the user writes otherwise. */
    fun answerHint(language: String): String? =
        if (language == "en") "Answer in English unless the user writes in another language or asks for one." else null
}

/** Shortcut for [I18n.tr]. */
fun tr(german: String): String = I18n.tr(german)

/** Shortcut for [I18n.tr] with placeholders `{0}`, `{1}` … */
fun tr(german: String, vararg args: Any?): String = I18n.tr(german, *args)

/**
 * Marks a German text in a list or enum that is created once: it stays German there and is translated with [tr] where it
 * is shown. The marker lets UiChecksTest find it and check that it has an English text.
 */
fun trLater(german: String): String = german
