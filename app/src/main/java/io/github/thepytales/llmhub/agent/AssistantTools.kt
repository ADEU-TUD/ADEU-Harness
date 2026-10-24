package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.net.s
import kotlinx.serialization.json.JsonObject

/**
 * Tools of the assistant itself (always offered when the model supports tools): instructions that are only
 * loaded when needed (read_guide), so the system prompt stays short and stable.
 */
class AssistantTools(private val c: AppContainer) : ToolProvider {
    override val defs = listOf(
        ToolDef(
            "read_guide",
            "Lädt eine ausführliche Anleitung der App, bevor du die entsprechende Aufgabe erledigst. Verfügbar: " +
                Guides.all.joinToString(", ") { "${it.name} (${it.title})" } + ".",
            listOf(Param("name", "Name der Anleitung")),
        ),
    )

    override fun parallelSafe(name: String) = name == "read_guide"

    override suspend fun run(name: String, args: JsonObject, ctx: RepoContext): ToolOutput = when (name) {
        "read_guide" -> {
            val wanted = args.s("name")?.trim()?.lowercase().orEmpty()
            val guide = Guides.all.firstOrNull { it.name == wanted || wanted in it.name || it.title.lowercase().contains(wanted) }
            if (guide == null) ToolOutput("FEHLER: Anleitung „$wanted“ gibt es nicht. Verfügbar: ${Guides.all.joinToString(", ") { it.name }}.")
            else ToolOutput(guide.text(c))
        }
        else -> ToolOutput("FEHLER: Unbekanntes Werkzeug '$name'.")
    }
}

/** Long instructions for single kinds of tasks; included directly when the request obviously needs them. */
object Guides {
    class Guide(val name: String, val title: String, val needed: Regex, val text: (AppContainer) -> String)

    private val DOCUMENT_WORDS = Regex(
        "dokument|pdf|word|docx|pptx|powerpoint|präsentation|praesentation|folie|brief|schreiben an|protokoll|formular|urkunde|" +
            "bescheinigung|zertifikat|merkblatt|aushang|bericht|einladung|flyer|lebenslauf|rechnung|logo|ausfüllen|exportier|als datei|herunterladen",
        RegexOption.IGNORE_CASE,
    )

    val documents = Guide("dokumente", "Dokumente, Briefe, Präsentationen, PDF-Formulare und Logos", DOCUMENT_WORDS) { c -> c.documentTools.prompt() }

    val all = listOf(documents)
}
