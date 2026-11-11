package io.github.thepytales.llmhub.audio

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.Privacy
import io.github.thepytales.llmhub.ui.tr
import java.io.File

/**
 * Diktat (AP-65): speaking into documents and text fields. The recording is recognized on the device with the app's
 * speech recognition (sherpa-onnx, Einstellungen › Transkription) – the Uni server only if no model is loaded and the
 * privacy level allows it. Punctuation and paragraphs are spoken („Punkt“, „Komma“, „neuer Absatz“; English „period“,
 * „new paragraph“); there are no voice commands for the app (the chat's voice mode is for talking with the models).
 */
object Dictation {
    /** What a spoken command becomes: [text], whether it sticks to the word before / the word after. */
    private data class Sym(val text: String, val left: Boolean, val right: Boolean, val endsSentence: Boolean = false)

    private val PARAGRAPH = Sym("\n", left = true, right = true, endsSentence = true)

    /** Commands of two words (checked first), then of one; matched without case and the model's own punctuation. */
    private val TWO = mapOf(
        "neuer absatz" to PARAGRAPH, "neuen absatz" to PARAGRAPH, "neue zeile" to PARAGRAPH, "nächste zeile" to PARAGRAPH,
        "anführungszeichen auf" to Sym("„", left = false, right = true), "anführungszeichen unten" to Sym("„", left = false, right = true),
        "anführungszeichen zu" to Sym("“", left = true, right = false), "anführungszeichen oben" to Sym("“", left = true, right = false),
        "klammer auf" to Sym("(", left = false, right = true), "klammer zu" to Sym(")", left = true, right = false),
        "new paragraph" to PARAGRAPH, "new line" to PARAGRAPH, "full stop" to Sym(".", left = true, right = false, endsSentence = true),
        "question mark" to Sym("?", left = true, right = false, endsSentence = true),
        "exclamation mark" to Sym("!", left = true, right = false, endsSentence = true), "exclamation point" to Sym("!", left = true, right = false, endsSentence = true),
        "open quote" to Sym("“", left = false, right = true), "close quote" to Sym("”", left = true, right = false),
        "open bracket" to Sym("(", left = false, right = true), "close bracket" to Sym(")", left = true, right = false),
        "open parenthesis" to Sym("(", left = false, right = true), "close parenthesis" to Sym(")", left = true, right = false),
    )
    private val ONE = mapOf(
        "punkt" to Sym(".", left = true, right = false, endsSentence = true), "komma" to Sym(",", left = true, right = false),
        "fragezeichen" to Sym("?", left = true, right = false, endsSentence = true), "ausrufezeichen" to Sym("!", left = true, right = false, endsSentence = true),
        "doppelpunkt" to Sym(":", left = true, right = false), "semikolon" to Sym(";", left = true, right = false), "strichpunkt" to Sym(";", left = true, right = false),
        "bindestrich" to Sym("-", left = true, right = true), "gedankenstrich" to Sym("–", left = false, right = false),
        "period" to Sym(".", left = true, right = false, endsSentence = true), "comma" to Sym(",", left = true, right = false),
        "colon" to Sym(":", left = true, right = false), "semicolon" to Sym(";", left = true, right = false), "hyphen" to Sym("-", left = true, right = true),
    )

    /**
     * Words before which „Punkt“ or „Komma“ is the noun, not the command: „der wichtigste Punkt“, „auf den Punkt“.
     */
    private val NOUN_BEFORE = setOf(
        "der", "den", "dem", "des", "ein", "einen", "einem", "eines", "einer", "kein", "keinen", "keinem", "dieser", "diesen", "diesem", "dieses",
        "jeder", "jeden", "jedem", "zum", "am", "im", "beim", "vom", "letzten", "ersten", "zweiten", "dritten", "nächsten", "vorigen", "wichtigste", "wichtigsten",
        "wichtiger", "wichtigen", "springende", "springenden", "toten", "wunden", "the", "a", "this", "that", "next", "last", "first",
    )

    private fun core(token: String) = token.lowercase().trim { !it.isLetterOrDigit() }

    /**
     * [text] as the recognition wrote it, with the spoken punctuation turned into characters, the model's own
     * punctuation next to a command removed, spaces and capitals after the end of a sentence put right.
     */
    fun punctuate(text: String): String {
        val tokens = text.replace('\n', ' ').split(Regex("\\s+")).filter { it.isNotBlank() }
        val pieces = mutableListOf<Any>() // String (a word as written) or Sym
        var i = 0
        while (i < tokens.size) {
            val two = if (i + 1 < tokens.size) TWO[core(tokens[i]) + " " + core(tokens[i + 1])] else null
            val prev = (pieces.lastOrNull() as? String)?.let(::core)
            val one = ONE[core(tokens[i])]?.takeIf { prev == null || prev !in NOUN_BEFORE }
            val sym = two ?: one
            if (sym == null) {
                pieces += tokens[i]
                i++
                continue
            }
            // The model's own mark next to the spoken one goes („gut. Punkt.“ → „gut.“).
            if (sym.left || sym == PARAGRAPH) (pieces.lastOrNull() as? String)?.let { w ->
                val stripped = w.replace(Regex("[.,;:!?…]+$"), "")
                if (stripped.isNotEmpty()) pieces[pieces.lastIndex] = stripped
            }
            pieces += sym
            i += if (two != null) 2 else 1
        }
        val out = StringBuilder()
        var glue = true // no space before the next word (start, after a newline or an opening mark)
        var capital = false
        for (p in pieces) {
            when (p) {
                is Sym -> {
                    if (p.left) while (out.endsWith(" ")) out.setLength(out.length - 1)
                    else if (!glue && out.isNotEmpty()) out.append(' ')
                    out.append(p.text)
                    glue = p.right
                    if (p.endsSentence) capital = true
                }
                is String -> {
                    var w = p
                    if (!glue && out.isNotEmpty()) out.append(' ')
                    if (capital) w = w.replaceFirstChar { it.uppercaseChar() }
                    out.append(w)
                    glue = false
                    // The model's own end of a sentence counts too.
                    capital = w.last() in ".!?"
                }
            }
        }
        return out.toString().replace(Regex(" *\n *"), "\n").trim(' ')
    }

    /**
     * What to insert after [before] (the text before the cursor): a space if a word ends there, a capital letter at the
     * start of a sentence or paragraph.
     */
    fun join(before: String, dictated: String): String {
        if (dictated.isEmpty()) return dictated
        val tail = before.trimEnd(' ', ' ')
        val startOfSentence = tail.isEmpty() || tail.endsWith("\n") || tail.trimEnd('"', '“', '”', '»', ')').lastOrNull()?.let { it in ".!?" } == true
        var text = if (startOfSentence && dictated.first().isLetter()) dictated.replaceFirstChar { it.uppercaseChar() } else dictated
        val needsSpace = before.isNotEmpty() && !before.last().isWhitespace() && before.last() !in "(„“\"'«»-/" &&
            (text.first().isLetterOrDigit() || text.first() in "„(–\"")
        if (needsSpace) text = " $text"
        return text
    }

    /** Recognizes [file]: on the device first; the Uni server only if no model is loaded and [allowServer]. */
    suspend fun recognize(c: AppContainer, file: File, allowServer: Boolean = Privacy.level(c, null as String?).allowsUniServer): Pair<String, Boolean> {
        // Wo Lora arbeitet (AP-112): Spracheingabe und Diktat decide whether the Uni-Server may help.
        if (!allowServer && c.transcription.localModel() == null) throw IllegalStateException(tr("Für das Diktat auf dem Gerät unter Einstellungen › Transkription ein Erkennungsmodell laden."))
        return io.github.thepytales.llmhub.data.Places.recognize(c, file, deviceOnly = !allowServer)
    }
}
