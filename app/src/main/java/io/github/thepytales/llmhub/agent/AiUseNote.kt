package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.thread
import io.github.thepytales.llmhub.net.s
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * KI-Nutzungsnachweis (AP-19): what the AI did for a piece of work, taken from the chat (or all chats of a project) on
 * the device – models, purpose, period, tools, documents –, written as a declaration for an examination (students),
 * for a publisher, or as a short footnote. Offered only when a chat is about a work (Hausarbeit, Masterarbeit, Paper,
 * Antrag …) and the AI wrote text for it ([shouldOffer]).
 */
object AiUseNote {
    enum class Format(val label: String) {
        EXAM("Prüfungsleistung"),
        PUBLISHER("Verlag/Zeitschrift"),
        FOOTNOTE("Kurze Fußnote"),
    }

    /** What the AI did: [work] like „Masterarbeit“ (null: not named). */
    data class Usage(
        val models: List<String>,
        val from: Long,
        val to: Long,
        val purposes: List<String>,
        val tools: List<String>,
        val documents: List<String>,
        val words: Int,
        val work: String?,
    )

    /** Kinds of work a declaration is asked for (stem → how it is named). */
    private val WORKS = listOf(
        "hausarbeit" to "Hausarbeit", "seminararbeit" to "Seminararbeit", "bachelorarbeit" to "Bachelorarbeit",
        "masterarbeit" to "Masterarbeit", "abschlussarbeit" to "Abschlussarbeit", "diplomarbeit" to "Diplomarbeit",
        "staatsexamensarbeit" to "Staatsexamensarbeit", "wissenschaftliche arbeit" to "wissenschaftliche Arbeit",
        "doktorarbeit" to "Dissertation", "dissertation" to "Dissertation", "habilitation" to "Habilitationsschrift",
        "thesis" to "Thesis", "exposé" to "Exposé", "expose" to "Exposé", "projektarbeit" to "Projektarbeit", "belegarbeit" to "Belegarbeit",
        "prüfungsleistung" to "Prüfungsleistung", "essay" to "Essay", "fachartikel" to "Fachartikel",
        "zeitschriftenartikel" to "Zeitschriftenartikel", "zeitschriftenbeitrag" to "Zeitschriftenbeitrag", "paper" to "Paper",
        "manuskript" to "Manuskript", "konferenzbeitrag" to "Konferenzbeitrag", "tagungsbeitrag" to "Tagungsbeitrag",
        "sammelbandbeitrag" to "Sammelbandbeitrag", "buchkapitel" to "Buchkapitel", "förderantrag" to "Förderantrag",
        "forschungsantrag" to "Forschungsantrag", "drittmittelantrag" to "Drittmittelantrag", "dfg-antrag" to "DFG-Antrag",
        "projektantrag" to "Projektantrag", "abstract" to "Abstract", "publikation" to "Publikation",
    )

    /** Asking the AI to write or rework text. */
    private val WRITING = Regex(
        "(?i)(schreib|formulier|verfass|überarbeit|ueberarbeit|umformulier|korrigier|lektorier|kürz|paraphrasier|übersetz|" +
            "gliedere|gliederung|entwirf|entwurf|einleitung|fazit|diskussion|zusammenfass|abschnitt|kapitel|absatz|umschreib|glätte|verbesser)",
    )

    /** The work [text] names, e.g. „Masterarbeit“, or null. */
    fun workIn(text: String): String? {
        val t = text.lowercase()
        return WORKS.firstOrNull { (stem, _) -> Regex("(^|[^a-zäöüß])" + Regex.escape(stem)).containsMatchIn(t) }?.second
    }

    private fun prose(content: String) = content.replace(Regex("(?s)<think>.*?</think>"), "").trim()

    /**
     * Whether to offer a declaration for [conv]: the chat is about a work (in the user's words or the title) and the AI
     * wrote for it – a document, or a longer text after a request to write or rework something.
     */
    fun shouldOffer(conv: Conversation): Boolean {
        val thread = conv.thread()
        val asked = thread.filter { it.role == "user" }.joinToString("\n") { it.content }
        if (workIn(asked + "\n" + conv.title) == null) return false
        val document = thread.any { m -> m.role == "assistant" && m.toolCalls.any { it.name == "create_document" || it.name == "edit_document" } }
        val text = WRITING.containsMatchIn(asked) && thread.any { it.role == "assistant" && prose(it.content).length >= 400 }
        return document || text
    }

    /** What the AI did in [chats] (one chat, or all of a project). */
    fun usage(chats: List<Conversation>): Usage {
        val messages = chats.flatMap { it.thread() }
        val asked = messages.filter { it.role == "user" }.joinToString("\n") { it.content }.lowercase()
        val calls = messages.flatMap { it.toolCalls }
        val names = calls.map { it.name }.toSet()
        val purposes = buildList {
            if (Regex("schreib|formulier|verfass|entwirf|entwurf").containsMatchIn(asked) || "create_document" in names) add("Formulierungshilfe und Entwürfe")
            if (Regex("überarbeit|ueberarbeit|korrigier|lektorier|kürz|umformulier|umschreib|paraphrasier|glätte|verbesser").containsMatchIn(asked) || "edit_document" in names) add("Überarbeitung und sprachliche Korrektur")
            if (Regex("gliederung|gliedere|struktur").containsMatchIn(asked)) add("Gliederung und Struktur")
            if (Regex("zusammenfass").containsMatchIn(asked)) add("Zusammenfassen von Texten")
            if (Regex("übersetz").containsMatchIn(asked)) add("Übersetzung")
            if (Regex("idee|brainstorm").containsMatchIn(asked)) add("Ideenfindung")
            if (Regex("literatur|quelle|studie|recherch").containsMatchIn(asked) || names.any { it.startsWith("search_") || it == "save_literature" || it == "web_search" }) add("Literatur- und Quellenrecherche")
            if (Regex("auswert|statist|daten|diagramm|code|programm").containsMatchIn(asked) || "run_javascript" in names || "create_chart" in names) add("Datenauswertung und Darstellung")
            if (isEmpty()) add("Unterstützung beim Schreiben")
        }
        val tools = names.filter { it != AskUserTool.NAME && !it.startsWith("read_learning") }
            .map { io.github.thepytales.llmhub.ui.toolLabel(it) }.distinct().sorted()
        val documents = calls.filter { it.name == "create_document" }.mapNotNull { parseArgs(it.arguments).s("title")?.trim()?.ifBlank { null } }.distinct()
        val models = messages.filter { it.role == "assistant" }.mapNotNull { it.model ?: chats.firstOrNull()?.model }
            .ifEmpty { chats.map { it.model } }.map { ModelCatalog.info(it).displayName }.distinct()
        val words = messages.filter { it.role == "assistant" }.sumOf { m -> prose(m.content).split(Regex("\\s+")).count { it.isNotBlank() } }
        val times = messages.map { it.createdAt }.ifEmpty { listOf(System.currentTimeMillis()) }
        return Usage(models, times.min(), times.max(), purposes, tools, documents, words, workIn(asked + "\n" + chats.joinToString(" ") { it.title }))
    }

    private val DAY = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
    private val MONTH = SimpleDateFormat("MMMM yyyy", Locale.GERMANY)
    private val MONTH_EN = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)

    private fun period(u: Usage) = DAY.format(Date(u.from)).let { a -> DAY.format(Date(u.to)).let { b -> if (a == b) a else "$a–$b" } }

    /** The declaration in [format] (Markdown). Parts the AI cannot know are marked „[bitte ergänzen …]“. */
    fun text(u: Usage, format: Format): String {
        val models = u.models.joinToString(", ").ifBlank { "einem Sprachmodell" }
        val parts = (if (u.documents.isEmpty()) "" else "Entwürfe: " + u.documents.joinToString(", ") { "„$it“" } + "; ") +
            "[bitte ergänzen, z. B. Einleitung, Kap. 2.3]"
        return when (format) {
            Format.EXAM -> buildString {
                append("**Erklärung zur Nutzung von KI-Werkzeugen**\n\n")
                append("Bei der Erstellung der vorliegenden Arbeit${u.work?.let { " ($it)" }.orEmpty()} habe ich KI-gestützte Werkzeuge wie folgt genutzt:\n\n")
                append("| Werkzeug / Modell | Zweck | Zeitraum | Betroffene Teile |\n|---|---|---|---|\n")
                append("| Lora (KI-Assistenz-App) mit $models | ${u.purposes.joinToString("; ")} | ${period(u)} | $parts |\n\n")
                if (u.tools.isNotEmpty()) append("Genutzte Funktionen: ${u.tools.joinToString(", ")}.\n\n")
                append("Alle Ausgaben der KI habe ich geprüft, überarbeitet und, wo nötig, mit Quellen belegt. Die Arbeit ist in ihrer gedanklichen ")
                append("Leistung meine eigene; für den gesamten Inhalt trage ich die Verantwortung.")
            }
            Format.PUBLISHER -> buildString {
                val en = u.purposes.map { PURPOSE_EN[it] ?: it }.joinToString(", ")
                append("**Declaration of generative AI and AI-assisted technologies in the writing process**\n\n")
                append("During the preparation of this work the author(s) used Lora with $models (${MONTH_EN.format(Date(u.to))}) in order to $en. ")
                append("After using this tool, the author(s) reviewed and edited the content as needed and take(s) full responsibility for the content of the publication.\n\n")
                append("*Deutsch:* Bei der Vorbereitung dieser Arbeit wurde Lora mit $models (${MONTH.format(Date(u.to))}) genutzt für: ")
                append(u.purposes.joinToString(", ") { it.replaceFirstChar(Char::lowercase) })
                append(". Die Inhalte wurden danach geprüft und überarbeitet; die Verantwortung für den Inhalt liegt bei den Autor:innen.")
            }
            Format.FOOTNOTE ->
                "Bei der Erstellung wurden KI-Werkzeuge genutzt (Lora mit $models, ${MONTH.format(Date(u.to))}) für: " +
                    u.purposes.joinToString(", ") { it.replaceFirstChar(Char::lowercase) } + ". Die Ergebnisse wurden geprüft und überarbeitet."
        }
    }

    private val PURPOSE_EN = mapOf(
        "Formulierungshilfe und Entwürfe" to "draft and phrase text",
        "Überarbeitung und sprachliche Korrektur" to "revise and proofread the language",
        "Gliederung und Struktur" to "structure the text",
        "Zusammenfassen von Texten" to "summarize texts",
        "Übersetzung" to "translate",
        "Ideenfindung" to "brainstorm ideas",
        "Literatur- und Quellenrecherche" to "search literature and sources",
        "Datenauswertung und Darstellung" to "analyse and visualize data",
        "Unterstützung beim Schreiben" to "assist with writing",
    )
}
