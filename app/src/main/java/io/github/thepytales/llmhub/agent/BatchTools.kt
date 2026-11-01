package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.BatchGrading
import io.github.thepytales.llmhub.data.BatchJob
import io.github.thepytales.llmhub.data.BatchStore
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.net.s
import kotlinx.serialization.json.JsonObject

/**
 * Stapel (AP-20) for the agent: a job for many attached files („fasse alle 40 PDFs zusammen“, „bewerte die Abgaben nach
 * diesem Raster“) goes into the queue instead of one long chat; the agent reads the progress and the results. Offered
 * while the tool „Stapel & Massenkontrolle“ is switched on.
 */
class BatchTools(private val c: AppContainer) : ToolProvider {
    override val defs = listOf(
        ToolDef(
            CREATE,
            "Legt einen Stapel an: dieselbe Aufgabe für viele angehängte Dateien, abgearbeitet nacheinander im Hintergrund (mit " +
                "Pause/Fortsetzen, übersteht einen Neustart). Für mehr als etwa 5 Dateien besser als alles in einem Chat. Mit rubric wird " +
                "daraus eine Massenkontrolle (Bewertungsvorschläge je Kriterium, ein Mensch entscheidet).",
            listOf(
                Param("title", "Kurzer Titel, z. B. „Hausarbeiten Didaktik WiSe 26/27“"),
                Param("instruction", "Der Auftrag für jede Datei (bei Massenkontrolle: Hinweise zur Bewertung)"),
                Param("files", "Namen der angehängten Dateien, durch Zeilenumbruch getrennt; leer = alle Anhänge des Chats", required = false),
                Param("rubric", "Nur Massenkontrolle: Bewertungsraster, je Zeile „Kriterium; Punkte; Beschreibung“", required = false),
                Param("start", "\"true\": gleich starten (sonst wartet der Stapel auf „Starten“ unter Stapel)", required = false),
            ),
            writes = true,
        ),
        ToolDef(
            STATUS,
            "Zeigt die Stapel mit Fortschritt (ohne id) oder den Stand eines Stapels (mit id).",
            listOf(Param("id", "Stapel-ID", required = false)),
        ),
        ToolDef(
            RESULTS,
            "Liest die Ergebnisse eines Stapels: je Datei das Ergebnis bzw. bei Massenkontrolle die Tabelle mit Vorschlägen und Entscheidungen.",
            listOf(Param("id", "Stapel-ID")),
        ),
    )

    override fun parallelSafe(name: String) = name != CREATE

    override fun risk(name: String, args: JsonObject): Risk? = if (name == CREATE) Risk.EDIT else null

    override suspend fun run(name: String, args: JsonObject, ctx: RepoContext): ToolOutput = when (name) {
        CREATE -> create(args, ctx)
        STATUS -> {
            val id = args.s("id")?.trim().orEmpty()
            if (id.isEmpty()) {
                val jobs = c.batches.jobs.value
                ToolOutput(if (jobs.isEmpty()) "Noch keine Stapel." else jobs.joinToString("\n") { "- ${it.title}: ${status(it)} – $ID_LABEL ${it.id}" })
            } else c.batches.get(id)?.let { ToolOutput("${it.title}: ${status(it)}") } ?: ToolOutput("FEHLER: Kein Stapel mit der ID $id.")
        }
        else -> {
            val job = c.batches.get(args.req("id").trim()) ?: return ToolOutput("FEHLER: Kein Stapel mit dieser ID.")
            ToolOutput(if (job.grading) "Massenkontrolle „${job.title}“ (${status(job)}):\n\n" + BatchGrading.resultTable(job) else BatchGrading.taskResults(job))
        }
    }

    private fun create(args: JsonObject, ctx: RepoContext): ToolOutput {
        val wanted = args.s("files")?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val files = ctx.files.distinctBy { it.name }.filter { f -> wanted.isEmpty() || wanted.any { w -> f.name.equals(w, ignoreCase = true) || f.name.contains(w, ignoreCase = true) } }
        if (files.isEmpty()) return ToolOutput("FEHLER: Keine passenden Anhänge im Chat. Der Nutzer kann die Dateien auch unter Stapel › Neuer Stapel auswählen (Ordner, ZIP, Mehrfachauswahl).")
        val rubric = args.s("rubric")?.let { BatchGrading.parseRubric(it) }.orEmpty()
        val model = c.settings.current.defaultModel.takeIf { ctx.model == null } ?: ctx.model!!
        val job = c.batches.create(
            BatchJob(
                title = args.req("title"),
                kind = if (rubric.isNotEmpty()) BatchJob.GRADING else BatchJob.TASK,
                instruction = args.req("instruction"),
                model = model,
                rubric = rubric,
            ),
            files.map { BatchStore.Source(it.name, it.text, it.pages) },
        )
        val start = args.s("start") == "true"
        if (start) c.batchRunner.start(job.id)
        return ToolOutput(
            "Stapel „${job.title}“ angelegt: ${job.items.size} Dateien, Modell ${ModelCatalog.info(model).displayName}" +
                (if (job.grading) ", Raster mit ${rubric.size} Kriterien (${BatchGrading.fmt(job.maxPoints)} Punkte)" else "") + ". " +
                (if (start) "Er läuft jetzt im Hintergrund." else "Er startet unter Stapel mit „Starten“.") + " $ID_LABEL ${job.id}",
        )
    }

    private fun status(j: BatchJob): String {
        val state = when (j.state) {
            BatchJob.RUNNING -> "läuft"
            BatchJob.PAUSED -> "pausiert"
            BatchJob.DONE -> "fertig"
            else -> "bereit"
        }
        return "$state, ${j.done} von ${j.items.size} Dateien" + (if (j.failed > 0) ", ${j.failed} mit Fehler" else "") +
            if (j.grading) ", ${j.items.count { it.decision != null }} entschieden" else ""
    }

    companion object {
        const val CREATE = "create_batch"
        const val STATUS = "batch_status"
        const val RESULTS = "read_batch_results"
        const val ID_LABEL = "Stapel-ID:"
    }
}
