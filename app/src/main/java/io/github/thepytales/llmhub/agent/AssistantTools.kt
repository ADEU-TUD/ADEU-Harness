package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.data.Privacy

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.KnowledgeDoc
import io.github.thepytales.llmhub.data.MemoryEntry
import io.github.thepytales.llmhub.data.MemoryMode
import io.github.thepytales.llmhub.data.ModelType
import io.github.thepytales.llmhub.net.s
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * Tools of the assistant itself (offered whenever the model supports tools): instructions that are only loaded
 * when needed (read_guide), the memory about the user (remember/forget) and the knowledge collection of own
 * documents (search_knowledge, add_to_knowledge).
 */
class AssistantTools(private val c: AppContainer) : ToolProvider {
    private val memoryMode get() = MemoryMode.of(c.settings.current.memoryMode)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val defs: List<ToolDef>
        get() = buildList {
            add(
                ToolDef(
                    "read_guide",
                    "Lädt eine ausführliche Anleitung der App, bevor du die entsprechende Aufgabe erledigst. Verfügbar: " +
                        Guides.all.joinToString(", ") { "${it.name} (${it.title})" } + ".",
                    listOf(Param("name", "Name der Anleitung")),
                )
            )
            if (memoryMode != MemoryMode.OFF) {
                add(
                    ToolDef(
                        "remember",
                        "Merkt sich eine dauerhaft nützliche Angabe über den Nutzer für alle künftigen Chats (Funktion, " +
                            "Lehrveranstaltungen, Vorlieben für Stil und Format, wiederkehrende Kontakte). Nicht für Einmaliges, " +
                            "nichts Sensibles (Gesundheit, Passwörter). Ein kurzer Satz in der dritten Person.",
                        listOf(Param("text", "Die Angabe, z.B. „Unterrichtet im Master Lehramt das Modul Allgemeine Didaktik.“")),
                        writes = true,
                    )
                )
                add(
                    ToolDef(
                        "forget",
                        "Löscht eine gemerkte Angabe, wenn der Nutzer das möchte oder sie nicht mehr stimmt.",
                        listOf(Param("text", "Die Angabe oder ein eindeutiger Teil davon")),
                        writes = true,
                    )
                )
            }
            if (c.knowledge.docs.value.isNotEmpty()) {
                add(
                    ToolDef(
                        "search_knowledge",
                        "Durchsucht die Wissenssammlung des Nutzers (eigene Dokumente wie Modulhandbücher, Ordnungen, Literatur; " +
                            "im Projekt auch die Projektdateien) und liefert die passendsten Textstellen mit Quelle. Nutze es, " +
                            "bevor du Fragen zu diesen Dokumenten beantwortest, und nenne die Quelle.",
                        listOf(
                            Param("query", "Suchanfrage in eigenen Worten, z.B. „Prüfungsleistung Modul EW-SEGY-BW 2“"),
                            Param("max_results", "Anzahl Textstellen (Standard 6)", required = false, type = "integer"),
                        ),
                    )
                )
            }
            add(
                ToolDef(
                    "run_javascript",
                    "Führt JavaScript in einer abgeschotteten Umgebung aus (ohne Internet und Dateizugriff, Zeitlimit 10 s) – für " +
                        "exakte Berechnungen, Statistik, Datumsrechnungen und Auswertungen von Tabellen. Rechne damit, statt Zahlen zu " +
                        "schätzen. Ausgabe mit print(...); der Wert des letzten Ausdrucks wird ebenfalls zurückgegeben. Angehängte " +
                        "Dateien stehen als Text in files[\"Name\"] (Liste: fileList). Hilfsfunktionen: parseCSV(text) → Liste von " +
                        "Objekten (Zahlen erkannt, auch Excel-Text mit Tabulatoren), sum, mean, median, sd (Stichprobe), round(x, n), " +
                        "column(rows, name), numbers(list) (nur Zahlen), describe(list) → {n, M, SD, Md, Min, Max}, freq(list) → " +
                        "Häufigkeiten, percent(teil, gesamt), table(rows) → Markdown-Tabelle. Interrater-Reliabilität (exakt): " +
                        "cohensKappa(codiererA, codiererB, gewichtung?) mit 'linear'/'quadratic' für ordinale Kategorien, " +
                        "fleissKappa(einheiten) für mehrere Codierer (je Einheit eine Liste der Codes), krippendorffAlpha(einheiten, " +
                        "'nominal'|'ordinal'|'interval'|'ratio') auch mit fehlenden Werten (null), agreement(a, b) prozentuale " +
                        "Übereinstimmung, crosstab(a, b), units(rows, ['Spalte A', 'Spalte B']) macht aus einer Tabelle die Einheiten, " +
                        "interpret(kappa) nach Landis & Koch.",
                    listOf(Param("code", "JavaScript-Code (ES6)")),
                )
            )
            add(
                ToolDef(
                    "add_to_knowledge",
                    "Nimmt eine im Chat angehängte Datei in die Wissenssammlung auf, damit sie in künftigen Chats durchsucht " +
                        "werden kann – im Projekt dieses Chats oder allgemein. Nur auf Wunsch des Nutzers.",
                    listOf(
                        Param("file", "Name der angehängten Datei (Standard: die zuletzt angehängte)", required = false),
                        Param("scope", "projekt (Standard, wenn der Chat in einem Projekt ist) oder allgemein", required = false),
                    ),
                )
            )
        }

    override fun risk(name: String, args: JsonObject): Risk? =
        if (name in setOf("remember", "forget") && memoryMode == MemoryMode.ASK) Risk.PUBLIC else null

    override fun parallelSafe(name: String) = name in setOf("read_guide", "search_knowledge", "run_javascript")

    /** Memory and the existence of knowledge, for the system prompt (changes rarely, so the server cache holds). */
    fun prompt(conv: Conversation): String? {
        val parts = mutableListOf<String>()
        val memory = c.memory.current
        if (memoryMode != MemoryMode.OFF && memory.isNotEmpty()) {
            parts += "Was du über den Nutzer weißt (er sieht und verwaltet das unter Einstellungen › Gedächtnis):\n" +
                memory.joinToString("\n") { "- ${it.text}" }
        }
        val collections = collections(conv.folderId)
        val docs = c.knowledge.docs.value.filter { it.collection in collections }
        if (docs.isNotEmpty()) {
            parts += "Wissenssammlung (mit search_knowledge durchsuchen): " + docs.take(30).joinToString(", ") { d ->
                d.name + if (d.collection.isNotEmpty()) " (Projekt)" else ""
            } + if (docs.size > 30) " und ${docs.size - 30} weitere." else "."
        }
        return parts.joinToString("\n\n").ifBlank { null }
    }

    private fun collections(folderId: String?) = listOfNotNull("", folderId)

    override suspend fun run(name: String, args: JsonObject, ctx: RepoContext): ToolOutput = when (name) {
        "read_guide" -> {
            val wanted = args.s("name")?.trim()?.lowercase().orEmpty()
            val guide = Guides.all.firstOrNull { it.name == wanted || wanted in it.name || it.title.lowercase().contains(wanted) }
            if (guide == null) ToolOutput("FEHLER: Anleitung „$wanted“ gibt es nicht. Verfügbar: ${Guides.all.joinToString(", ") { it.name }}.")
            else ToolOutput(guide.text(c))
        }
        "remember" -> {
            val text = args.req("text").trim().take(300)
            val existing = c.memory.current.firstOrNull { it.text.equals(text, ignoreCase = true) }
            if (existing == null) c.memory.upsert(MemoryEntry(text = text))
            ToolOutput("Gemerkt: „$text“. Erwähne es kurz in deiner Antwort.")
        }
        "forget" -> {
            val text = args.req("text").trim()
            val hits = c.memory.current.filter { it.text.contains(text, ignoreCase = true) || text.contains(it.text, ignoreCase = true) }
            hits.forEach { c.memory.delete(it.id) }
            ToolOutput(if (hits.isEmpty()) "Nichts Passendes gemerkt." else "Gelöscht: ${hits.joinToString("; ") { "„${it.text}“" }}")
        }
        "search_knowledge" -> search(args.req("query"), (args.int("max_results") ?: 6).coerceIn(1, 15), ctx)
        "add_to_knowledge" -> addFromChat(args, ctx)
        "run_javascript" -> {
            val files = ctx.files.associate { it.name to it.text }
            val r = withContext(Dispatchers.Default) { ScriptRunner.run(args.req("code"), files) }
            ToolOutput(if (r.error == null) r.output else "FEHLER im Skript: ${r.error}" + if (r.output.isNotBlank()) "\nAusgabe bis dahin:\n${r.output}" else "")
        }
        else -> ToolOutput("FEHLER: Unbekanntes Werkzeug '$name'.")
    }

    private suspend fun search(query: String, k: Int, ctx: RepoContext): ToolOutput {
        val collections = collections(ctx.folderId)
        // Semantic search asks the Uni server; projects that keep data on the device use the keyword search only.
        val model = embeddingModel().takeIf { Privacy.level(c, ctx.folderId).allowsUniServer }
        val vector = if (model != null && c.knowledge.docs.value.any { it.collection in collections && it.embeddedWith == model }) {
            runCatching { c.llm.embed(model, listOf(query)).firstOrNull() }.getOrNull()
        } else null
        val hits = c.knowledge.search(query, collections, k, vector, model)
        if (hits.isEmpty()) return ToolOutput("Keine passende Stelle in der Wissenssammlung gefunden.")
        return ToolOutput(
            hits.joinToString("\n\n") { h ->
                "[${h.doc.name}, Abschnitt ${h.index + 1} von ${h.doc.chunks}${if (h.doc.collection.isNotEmpty()) ", Projekt" else ""}]\n${h.text}"
            } + "\n\nNenne die Quelle (Dokumentname) bei Aussagen aus diesen Stellen."
        )
    }

    private fun addFromChat(args: JsonObject, ctx: RepoContext): ToolOutput {
        val wanted = args.s("file")?.trim()?.takeIf { it.isNotEmpty() }
        val file = if (wanted == null) ctx.files.firstOrNull()
        else ctx.files.firstOrNull { it.name.equals(wanted, ignoreCase = true) } ?: ctx.files.firstOrNull { it.name.contains(wanted, ignoreCase = true) }
        file ?: return ToolOutput("FEHLER: In diesem Chat ist keine passende Datei angehängt. Bitte den Nutzer, sie anzuhängen.")
        val global = args.s("scope")?.lowercase()?.startsWith("allg") == true || ctx.folderId == null
        val doc = add(file.name, file.text, if (global) "" else ctx.folderId!!)
        return ToolOutput(
            "„${doc.name}“ ist in der Wissenssammlung (${if (global) "allgemein" else "Projekt"}, ${doc.chunks} Abschnitte)." +
                if (file.truncated) " Hinweis: Die Datei war sehr lang, nur der eingelesene Teil wurde aufgenommen." else ""
        )
    }

    /** Adds a document and computes its embeddings in the background. */
    fun add(name: String, text: String, collection: String): KnowledgeDoc {
        val doc = c.knowledge.add(name, text, collection)
        val serverAllowed = collection.isEmpty() || Privacy.level(c, collection).allowsUniServer
        embeddingModel()?.takeIf { serverAllowed }?.let { model -> scope.launch { c.knowledge.embed(doc, model) { c.llm.embed(model, it) } } }
        return doc
    }

    /** The embedding model of the server: Qwen3 Embedding if offered, else the first one the server lists. */
    fun embeddingModel(): String? {
        val live = c.models.state.value.liveIds
        if (live == null) return PREFERRED_EMBEDDING
        return live.firstOrNull { it.equals(PREFERRED_EMBEDDING, ignoreCase = true) }
            ?: live.firstOrNull { io.github.thepytales.llmhub.data.ModelCatalog.info(it).type == ModelType.EMBEDDING }
    }

    companion object {
        const val PREFERRED_EMBEDDING = "Qwen/Qwen3-Embedding-4B"
    }
}

/** Long instructions for single kinds of tasks; included directly when the request obviously needs them. */
object Guides {
    class Guide(val name: String, val title: String, val needed: Regex, val text: (AppContainer) -> String)

    private val DOCUMENT_WORDS = Regex(
        "dokument|pdf|word|docx|pptx|powerpoint|präsentation|praesentation|folie|brief|schreiben an|protokoll|formular|urkunde|" +
            "bescheinigung|zertifikat|merkblatt|aushang|bericht|einladung|flyer|lebenslauf|rechnung|logo|ausfüllen|exportier|als datei|herunterladen|" +
            "opal|onyx|qti|quiz|multiple.choice|selbsttest|reisekosten|beleg|quittung|barrierefrei",
        RegexOption.IGNORE_CASE,
    )

    val documents = Guide("dokumente", "Dokumente, Briefe, Präsentationen, PDF-Formulare und Logos", DOCUMENT_WORDS) { c -> c.documentTools.prompt() }

    val all = listOf(documents)
}
