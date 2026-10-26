package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.Template
import io.github.thepytales.llmhub.data.TemplateField
import io.github.thepytales.llmhub.net.ThinkingLevel
import io.github.thepytales.llmhub.ui.splitThinking

/** What the ✨ button of a template field does with its text. */
enum class AssistMode(val label: String, val instruction: String) {
    SMOOTH(
        "Glätten",
        "Korrigiere Rechtschreibung, Grammatik und Zeichensetzung und glätte holprige Formulierungen. Inhalt, Aussagen, " +
            "Reihenfolge und ungefähre Länge bleiben gleich.",
    ),
    EXPAND(
        "Ausformulieren",
        "Formuliere aus den Stichpunkten bzw. dem kurzen Text einen vollständigen, gut lesbaren Text. Nutze nur die " +
            "vorhandenen Angaben und den Zusammenhang der anderen Felder. Erfinde keine Fakten; fehlt eine konkrete Angabe " +
            "(Datum, Betrag, Name, Anzahl), setze einen kurzen Platzhalter in eckigen Klammern, z.B. [Datum].",
    ),
    SHORTEN("Kürzen", "Kürze den Text deutlich auf das Wesentliche; wichtige Inhalte, Zahlen und Termine bleiben erhalten."),
    FORMAL("Förmlicher", "Formuliere förmlicher und sachlicher, wie in einem amtlichen Schreiben der Universität (Sie-Form)."),
    FRIENDLY("Freundlicher", "Formuliere freundlicher und verbindlicher, ohne an Sachlichkeit zu verlieren."),
    TIDY(
        "Ordnen",
        "Bringe die Angaben in die übliche Form: je Zeile genau eine Angabe, in der üblichen Reihenfolge (bei Anschriften: " +
            "Anrede mit Titel und Name, Einrichtung, Straße und Hausnummer, Postleitzahl und Ort; bei Absendern: Name mit Titel, " +
            "Funktion, Telefon, E-Mail, Raum). Korrigiere offensichtliche Tippfehler. Ergänze nichts, was nicht dasteht.",
    ),
    CUSTOM("Eigene Anweisung …", ""),
    ;

    companion object {
        /** Modes offered for [field]: addresses are tidied, texts rewritten. */
        fun of(field: TemplateField): List<AssistMode> =
            if (field.isAddress || field.isSender) listOf(TIDY, CUSTOM)
            else listOf(SMOOTH, EXPAND, SHORTEN, FORMAL, FRIENDLY, CUSTOM)
    }
}

/** Rewrites the content of one template field with the model (the ✨ button in the form). */
object FieldAssist {
    fun prompt(
        template: Template,
        field: TemplateField,
        values: Map<String, String>,
        mode: AssistMode,
        custom: String = "",
        about: List<String> = emptyList(),
    ): List<ChatMessage> {
        val letterBody = template.direct == "letter" && !field.isAddress && !field.isSender && field.multiline
        val system = buildString {
            append("Du hilfst beim Ausfüllen eines Formulars in einer App der Professur für Allgemeine Didaktik und Empirische ")
            append("Unterrichtsforschung an der TU Dresden. Antworte ausschließlich mit dem neuen Inhalt des einen Feldes – ")
            append("ohne Einleitung, ohne Erklärung, ohne Anführungszeichen, ohne Überschrift. Deutsch, geschlechtergerecht, keine Emojis.")
            if (letterBody) {
                append(" Das Feld ist der Text eines förmlichen Briefs: ohne Anrede, ohne Grußformel, ohne Unterschrift – die setzt ")
                append("die App selbst. Sie-Form, Absätze durch eine Leerzeile getrennt, kein Markdown außer bei Bedarf **fett**.")
            }
        }
        val others = values.filter { (k, v) -> k != field.label && v.isNotBlank() }
        val user = buildString {
            append("Vorlage: ").append(template.name)
            if (template.description.isNotBlank()) append(" – ").append(template.description)
            if (others.isNotEmpty()) {
                append("\n\nAndere Angaben im Formular:\n")
                others.forEach { (k, v) -> append("- ").append(k).append(": ").append(v.trim().replace("\n", " / ")).append('\n') }
            }
            if (about.isNotEmpty()) {
                append("\nWas über den Nutzer bekannt ist (nur verwenden, wenn es zum Feld passt):\n")
                about.forEach { append("- ").append(it).append('\n') }
            }
            append("\nFeld „").append(field.label).append("“")
            if (field.hint.isNotBlank()) append(" (Hinweis: ").append(field.hint).append(")")
            val current = values[field.label].orEmpty().trim()
            if (current.isEmpty()) append("\nDas Feld ist noch leer – schreibe den Inhalt anhand der anderen Angaben.")
            else append("\nBisheriger Inhalt:\n<<<\n").append(current).append("\n>>>")
            append("\n\nAufgabe: ").append(if (mode == AssistMode.CUSTOM) custom.trim() else mode.instruction)
        }
        return listOf(ChatMessage(role = "system", content = system), ChatMessage(role = "user", content = user))
    }

    /** Memory entries (unless switched off) – so "Vorschlag schreiben" knows e.g. the own courses. */
    private fun about(c: AppContainer): List<String> =
        if (io.github.thepytales.llmhub.data.MemoryMode.of(c.settings.current.memoryMode) == io.github.thepytales.llmhub.data.MemoryMode.OFF) emptyList()
        else c.memory.current.map { it.text }.take(40)

    /** Cleans the answer: no reasoning, no code fences, no surrounding quotes or "<<<" markers. */
    fun clean(answer: String): String {
        var t = splitThinking(answer).second.trim()
        t = t.removePrefix("```").removeSuffix("```").trim()
        t = t.removePrefix("<<<").removeSuffix(">>>").trim()
        if (t.length > 1 && (t.first() == '"' && t.last() == '"' || t.first() == '„' && t.last() == '“')) t = t.substring(1, t.length - 1).trim()
        return t
    }

    suspend fun rewrite(
        c: AppContainer,
        template: Template,
        field: TemplateField,
        values: Map<String, String>,
        mode: AssistMode,
        custom: String = "",
    ): String {
        val result = c.llm.streamChat(
            model = c.settings.current.defaultModel,
            messages = prompt(template, field, values, mode, custom, about(c)),
            tools = null,
            thinking = ThinkingLevel.OFF,
        ) { }
        return clean(result.content).ifEmpty { throw IllegalStateException("Das Modell hat keinen Text geliefert.") }
    }
}
