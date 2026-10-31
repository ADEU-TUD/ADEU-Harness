package io.github.thepytales.llmhub.agent

import java.text.Normalizer

/** Tool groups a mode needs in addition to the chat's (see AgentRunner.providers). */
enum class ModeTool { RESEARCH, WEB, DOCUMENTS }

/**
 * A special way of working in a chat (AP-05). Every mode looks the same: a chip above the input with ✕, below it one
 * line „what happens here · Daten: …“, and in an empty chat an [example] to tap. A new mode only needs a definition
 * here – it is offered by the /command, the command palette (Strg+K), the start page and Lora.
 */
data class ChatMode(
    val id: String,
    val name: String,
    /** Key of the icon (see ui/ModeUi). */
    val icon: String,
    /** One line: what happens in this mode. */
    val explain: String,
    /** Where data goes in this mode, e.g. „Suchbegriffe gehen an Literaturdatenbanken“. */
    val data: String,
    /** A request to tap in an empty chat. */
    val example: String,
    /** The word after "/" that starts the mode. */
    val command: String,
    val tools: Set<ModeTool> = emptySet(),
    /** Added to the system prompt while the mode is on. */
    val prompt: String = "",
    /** Model the mode starts with in a new chat (null: the chat's). */
    val defaultModel: String? = null,
    /** Opens something instead of staying on (the Sprachmodus is a full-screen conversation). */
    val action: Boolean = false,
    /** Further words that find the mode in the palette and with "/". */
    val keywords: String = "",
)

object ChatModes {
    val LITERATURE = ChatMode(
        id = "literature",
        name = "Literatursuche",
        icon = "search",
        explain = "Der Agent sucht systematisch in Literaturdatenbanken, prüft die Treffer und legt eine Literaturliste an.",
        data = "Suchbegriffe gehen an OpenAlex, Crossref, ERIC, DNB und SLUB, der Chat an das gewählte Modell.",
        example = "Finde aktuelle empirische Studien zu Feedback im Mathematikunterricht der Grundschule (2015–2025, Deutsch und Englisch).",
        command = "Literatursuche",
        tools = setOf(ModeTool.RESEARCH),
        prompt = ResearchTools.MODE_PROMPT,
        keywords = "literatur quellen recherche studien paper",
    )

    val PLANNING = ChatMode(
        id = "planning",
        name = "Planungsmodus",
        icon = "plan",
        explain = "Lora klärt dein Vorhaben in kurzen Fragerunden mit Antwortknöpfen und schreibt daraus einen Plan als Dokument.",
        data = "Wie im normalen Chat: an das gewählte Modell, Namen pseudonymisiert.",
        example = "Ich möchte ein Seminar „Digitale Medien im Sachunterricht“ für das Sommersemester planen.",
        command = "Planen",
        tools = setOf(ModeTool.DOCUMENTS),
        prompt = PLANNING_PROMPT,
        keywords = "plan planung vorhaben seminar antrag projekt paper arbeitsplan rückfragen",
    )

    val SOURCES = ChatMode(
        id = "sources",
        name = "Quellenmodus",
        icon = "sources",
        explain = "Belegpflicht: Jede Aussage bekommt eine Fundstelle (Datei und Seite, DOI, URL, Transkriptzeit); die App prüft danach jedes Zitat gegen die Quelle.",
        data = "Wie im normalen Chat; Suchbegriffe gehen an Websuche und Literaturdatenbanken, die Prüfung der Zitate läuft auf dem Gerät.",
        example = "Was sagen die angehängten Texte über Feedback im Unterricht? Belege jede Aussage mit Seite und Zitat.",
        command = "Quellen",
        tools = setOf(ModeTool.RESEARCH, ModeTool.WEB),
        prompt = Evidence.PROMPT,
        keywords = "quellen belege belegpflicht fundstelle zitat seitenangabe nachweis quellenmodus",
    )

    /** Exzerpt-Agent (AP-09): an excerpt of a text with checked page numbers, attached to the literature entry. */
    val EXCERPT = ChatMode(
        id = "excerpt",
        name = "Exzerpt",
        icon = "excerpt",
        explain = "Der Agent exzerpiert einen Text nach einem Schema (empirisch, theoretisch, Review, Buchkapitel) mit Zitaten und geprüften Seitenangaben und hängt das Exzerpt an den Literatureintrag.",
        data = "Der Text geht an das gewählte Modell, Namen pseudonymisiert; die Prüfung der Seitenangaben läuft auf dem Gerät.",
        example = "Exzerpiere den angehängten Artikel für meine Literaturarbeit.",
        command = "Exzerpt",
        tools = setOf(ModeTool.RESEARCH, ModeTool.DOCUMENTS),
        prompt = io.github.thepytales.llmhub.research.Excerpts.MODE_PROMPT,
        keywords = "exzerpt exzerpieren zusammenfassung artikel paper lesen literaturarbeit",
    )

    val VOICE = ChatMode(
        id = "voice",
        name = "Sprachmodus",
        icon = "voice",
        explain = "Freihändig sprechen: Die App hört bis zur Sprechpause zu, antwortet und liest die Antwort vor.",
        data = "Sprache wird auf dem Uni-Server oder dem Gerät erkannt, die Antwort vom schnellen Sprachmodell geschrieben.",
        example = "Erklär mir in zwei Minuten, was formatives Assessment ist.",
        command = "Sprachmodus",
        action = true,
        keywords = "sprechen stimme vorlesen freihändig gespräch",
    )

    /** All modes, in the order of the menus. */
    val all: List<ChatMode> = listOf(LITERATURE, SOURCES, EXCERPT, PLANNING, VOICE)

    fun byId(id: String?): ChatMode? = all.firstOrNull { it.id == id }

    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").replace(Regex("[^a-z0-9 ]"), "")

    /** Modes that fit what follows the "/" (or a search in the palette); all for an empty [query]. */
    fun matching(query: String): List<ChatMode> {
        val q = norm(query).trim()
        if (q.isEmpty()) return all
        return all.filter { m -> norm(m.name + " " + m.command + " " + m.keywords).split(' ').any { it.startsWith(q) } || norm(m.name).contains(q) }
    }
}

/** Planungsmodus (AP-06): rounds of questions with answer buttons, then a plan document. */
const val PLANNING_PROMPT = "PLANUNGSMODUS: Du hilfst, ein Vorhaben zu planen (Seminar, Antrag, Projekt, Paper, Arbeitsplan …).\n" +
    "1. Kläre das Vorhaben in Fragerunden mit dem Werkzeug ask_user: je Runde 1–4 kurze Fragen mit 2–5 Antwortmöglichkeiten " +
    "(Einfach- oder Mehrfachauswahl; eine freie Antwort ist immer möglich). Frage zuerst nach Ziel, Zielgruppe und Rahmen " +
    "(Zeitraum, Umfang), dann nach Inhalten, Methoden, Arbeitspaketen und Risiken – nur, was du wirklich brauchst.\n" +
    "2. Nach jeder Antwort fasst du in einem Satz zusammen, was feststeht, und stellst die nächste Runde. Meist reichen 2–4 Runden.\n" +
    "3. Steht genug fest, erstellst du mit create_document einen Plan als Word-Dokument (oder Markdown, wenn gewünscht): Ziel, " +
    "Rahmen, Ablauf bzw. Arbeitspakete mit Zeitplan, offene Punkte. Danach fragst du, was angepasst werden soll.\n" +
    "Rate nicht: Wo eine Angabe fehlt, frage nach."
