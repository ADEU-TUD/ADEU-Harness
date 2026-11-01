package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.net.JSON
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.util.UUID
import kotlin.math.sqrt
import io.github.thepytales.llmhub.ui.trLater

/** One criterion of a Bewertungsraster: name, highest number of points and what it means. */
@Serializable
data class BatchCriterion(val name: String, val max: Double, val description: String = "")

/** The AI's points for one criterion with its reason and the passages it relies on. */
@Serializable
data class BatchScore(val criterion: String, val points: Double, val reason: String = "", val evidence: List<String> = emptyList())

/** The AI's suggestion for one work (Massenkontrolle). */
@Serializable
data class BatchGrade(val scores: List<BatchScore> = emptyList(), val feedback: String = "", val summary: String = "") {
    val total: Double get() = scores.sumOf { it.points }
}

/**
 * What a person decided for one work: points per criterion (order of the raster), a note. [accepted]: the suggestion
 * was taken unchanged. [calibration]: graded by hand before the run, an example for the AI.
 */
@Serializable
data class BatchDecision(
    val points: List<Double>,
    val note: String = "",
    val at: Long = System.currentTimeMillis(),
    val accepted: Boolean = false,
    val calibration: Boolean = false,
) {
    val total: Double get() = points.sum()
}

/** One file of a batch. Its text lives next to the job ([BatchStore.text]). */
@Serializable
data class BatchItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val chars: Int = 0,
    val pages: Int? = null,
    val status: String = BatchItem.PENDING,
    val result: String = "",
    val error: String? = null,
    val attempts: Int = 0,
    val startedAt: Long = 0,
    val finishedAt: Long = 0,
    val grade: BatchGrade? = null,
    val decision: BatchDecision? = null,
) {
    val open: Boolean get() = status == PENDING || status == RUNNING

    companion object {
        const val PENDING = "PENDING"
        const val RUNNING = "RUNNING"
        const val DONE = "DONE"
        const val FAILED = "FAILED"
        /** Graded by hand beforehand (calibration) or taken out of the run. */
        const val SKIPPED = "SKIPPED"
    }
}

/** A line of the protocol: who decided what and when (KI-Verordnung: a person decides, the app writes it down). */
@Serializable
data class BatchLogEntry(val at: Long, val item: String, val text: String)

/**
 * Stapel (AP-20): many files with the same Auftrag – or, as Massenkontrolle, the same Bewertungsraster. Runs one file
 * after the other in the background ([io.github.thepytales.llmhub.agent.BatchRunner]), can be paused, survives a
 * restart and loses nothing when interrupted (every result is stored as soon as it is there).
 */
@Serializable
data class BatchJob(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val kind: String = TASK,
    val instruction: String,
    val model: String,
    val items: List<BatchItem> = emptyList(),
    val state: String = READY,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Names and personal details are replaced before sending (on by default). */
    val pseudonymize: Boolean = true,
    /** Only the Uni server or models on this device („Nur TU Dresden“, on by default). */
    val uniOnly: Boolean = true,
    /** Only between 22 and 6 o'clock (the PC or phone stays on). */
    val night: Boolean = false,
    val rubric: List<BatchCriterion> = emptyList(),
    val log: List<BatchLogEntry> = emptyList(),
) {
    val grading: Boolean get() = kind == GRADING
    val done: Int get() = items.count { !it.open }
    val failed: Int get() = items.count { it.status == BatchItem.FAILED }
    val maxPoints: Double get() = rubric.sumOf { it.max }

    companion object {
        const val TASK = "TASK"
        const val GRADING = "GRADING"

        const val READY = "READY"
        const val RUNNING = "RUNNING"
        const val PAUSED = "PAUSED"
        const val DONE = "DONE"
    }
}

/** The batches of this device: one folder per job with job.json and the texts of its files. */
class BatchStore(files: File) {
    private val dir = File(files, "batches").apply { mkdirs() }
    private val _jobs = MutableStateFlow(load())
    val jobs: StateFlow<List<BatchJob>> = _jobs.asStateFlow()

    private fun jobDir(id: String) = File(dir, id)
    private fun jobFile(id: String) = File(jobDir(id), "job.json")
    private fun textFile(id: String, item: String) = File(jobDir(id), "$item.txt")

    private fun load(): List<BatchJob> = dir.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull { d ->
        runCatching { AtomicFiles.read(File(d, "job.json"))?.let { JSON.decodeFromString(BatchJob.serializer(), it) } }.getOrNull()
    }.sortedByDescending { it.createdAt }

    fun get(id: String): BatchJob? = _jobs.value.firstOrNull { it.id == id }

    fun text(id: String, item: String): String? = runCatching { textFile(id, item).takeIf { it.isFile }?.readText() }.getOrNull()

    /** A new job with the files [sources] (name, text, pages). */
    @Synchronized
    fun create(job: BatchJob, sources: List<Source>): BatchJob {
        val items = sources.map { s -> BatchItem(name = s.name, chars = s.text.length, pages = s.pages) }
        jobDir(job.id).mkdirs()
        items.zip(sources).forEach { (i, s) -> textFile(job.id, i.id).writeText(s.text) }
        val j = job.copy(items = items)
        save(j)
        _jobs.value = listOf(j) + _jobs.value.filter { it.id != j.id }
        return j
    }

    @Synchronized
    fun addItems(id: String, sources: List<Source>): BatchJob? {
        val j = get(id) ?: return null
        val items = sources.map { s -> BatchItem(name = s.name, chars = s.text.length, pages = s.pages) }
        items.zip(sources).forEach { (i, s) -> textFile(id, i.id).writeText(s.text) }
        return update(id) { it.copy(items = it.items + items, state = if (it.state == BatchJob.DONE) BatchJob.PAUSED else it.state) }
    }

    @Synchronized
    fun update(id: String, transform: (BatchJob) -> BatchJob): BatchJob? {
        val old = get(id) ?: return null
        val next = transform(old).copy(updatedAt = System.currentTimeMillis())
        save(next)
        _jobs.value = _jobs.value.map { if (it.id == id) next else it }
        return next
    }

    fun updateItem(id: String, item: String, transform: (BatchItem) -> BatchItem): BatchJob? =
        update(id) { j -> j.copy(items = j.items.map { if (it.id == item) transform(it) else it }) }

    /** Notes a decision in the protocol and on the item. */
    fun decide(id: String, item: String, decision: BatchDecision, text: String): BatchJob? = update(id) { j ->
        j.copy(
            items = j.items.map { if (it.id == item) it.copy(decision = decision, status = if (decision.calibration && it.open) BatchItem.SKIPPED else it.status) else it },
            log = j.log + BatchLogEntry(
                decision.at, j.items.firstOrNull { it.id == item }?.name ?: item,
                text + if (decision.note.isNotBlank() && decision.note !in text) " – ${decision.note}" else "",
            ),
        )
    }

    @Synchronized
    fun delete(id: String) {
        jobDir(id).deleteRecursively()
        _jobs.value = _jobs.value.filter { it.id != id }
    }

    private fun save(j: BatchJob) {
        jobDir(j.id).mkdirs()
        AtomicFiles.write(jobFile(j.id), JSON.encodeToString(BatchJob.serializer(), j))
    }

    /** A file for a new batch. */
    data class Source(val name: String, val text: String, val pages: Int? = null)
}

/** Prompts, reading the answers, outliers, checking passages and exports of the Massenkontrolle (pure functions, tested). */
object BatchGrading {
    /** System prompt of a plain batch: one file, one result, no questions back. */
    fun taskSystem(job: BatchJob): String =
        "Du bearbeitest die Dateien eines Stapels nacheinander, jede für sich, mit demselben Auftrag. Antworte nur mit dem " +
            "Ergebnis für die eine Datei dieser Nachricht – vollständig, auf Deutsch (außer der Auftrag verlangt etwas anderes), " +
            "ohne Rückfragen und ohne Einleitung. Erfinde nichts, was nicht in der Datei steht; fehlt etwas, schreibe das.\n\n" +
            "AUFTRAG:\n" + job.instruction.trim()

    /** System prompt of the Massenkontrolle: raster, calibration examples, strict JSON. Stays the same for the whole run. */
    fun gradingSystem(job: BatchJob, examples: List<Pair<String, BatchDecision>>): String = buildString {
        append("Du bewertest studentische Arbeiten nach einem festen Bewertungsraster und machst einen VORSCHLAG. Ein Mensch prüft jeden Vorschlag und entscheidet (KI-Verordnung: Prüfungsbewertung ist Hochrisiko – du assistierst nur).\n")
        append("Bewerte jede Arbeit für sich, streng nach dem Raster, gleich streng für alle. Name, Herkunft oder Schreibstil der Person spielen keine Rolle.\n\n")
        append("BEWERTUNGSRASTER:\n")
        job.rubric.forEachIndexed { i, k ->
            append("${i + 1}. ${k.name} (0–${fmt(k.max)} Punkte)")
            if (k.description.isNotBlank()) append(": ").append(k.description.trim())
            append('\n')
        }
        append("Gesamt höchstens ${fmt(job.maxPoints)} Punkte.\n")
        if (job.instruction.isNotBlank()) append("\nHINWEISE DER PRÜFENDEN PERSON:\n").append(job.instruction.trim()).append('\n')
        if (examples.isNotEmpty()) {
            append("\nKALIBRIERUNG – so hat die prüfende Person von Hand bewertet (halte dich an diesen Maßstab):\n")
            examples.forEachIndexed { n, (excerpt, d) ->
                append("\nBeispiel ${n + 1} – Auszug:\n\"\"\"\n").append(excerpt.take(2500)).append("\n\"\"\"\nPunkte: ")
                append(job.rubric.mapIndexed { i, k -> "${k.name} = ${fmt(d.points.getOrElse(i) { 0.0 })}" }.joinToString("; "))
                append(" (gesamt ${fmt(d.total)})")
                if (d.note.isNotBlank()) append("\nBegründung: ").append(d.note.trim())
                append('\n')
            }
        }
        append("\nANTWORTE NUR mit JSON in genau dieser Form (keine Erklärung davor oder danach):\n")
        append("{\"bewertung\":[{\"kriterium\":\"<Name aus dem Raster>\",\"punkte\":<Zahl>,\"begruendung\":\"<1–3 Sätze>\",\"belege\":[\"<wörtliches Zitat aus der Arbeit> (S. <Seite>)\"]}],")
        append("\"rueckmeldung\":\"<Feedback an die Person: 3–6 Sätze, wertschätzend, konkret, mit dem wichtigsten nächsten Schritt>\",\"zusammenfassung\":\"<ein Satz>\"}\n")
        append("Je Kriterium genau ein Eintrag in der Reihenfolge des Rasters. Belege sind wörtliche, kurze Zitate (höchstens 25 Wörter) aus der Arbeit mit Seitenangabe, wenn Seiten markiert sind. Halbe Punkte sind erlaubt.")
    }

    /** The user message for one file. */
    fun fileMessage(name: String, pages: Int?, text: String, maxChars: Int): String {
        val body = if (text.length > maxChars) text.take(maxChars) + "\n\n[… gekürzt: die Datei ist länger als ${maxChars / 1000}k Zeichen]" else text
        return "DATEI: $name" + (pages?.let { " ($it Seiten)" } ?: "") + "\n\n" + body
    }

    /** Reads the AI's JSON answer (also inside a code block or with text around it); null if it is not usable. */
    fun parse(answer: String, rubric: List<BatchCriterion>): BatchGrade? {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val o = runCatching { JSON.parseToJsonElement(answer.substring(start, end + 1)) as? JsonObject }.getOrNull() ?: return null
        fun str(obj: JsonObject, vararg keys: String) = keys.firstNotNullOfOrNull { k -> (obj[k] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
        val list = (o["bewertung"] ?: o["scores"] ?: o["kriterien"]) as? JsonArray ?: return null
        val raw = list.mapNotNull { it as? JsonObject }.map { s ->
            val evidence = ((s["belege"] ?: s["evidence"]) as? JsonArray)?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }.orEmpty()
            BatchScore(
                criterion = str(s, "kriterium", "criterion", "name").orEmpty(),
                points = str(s, "punkte", "points")?.replace(',', '.')?.toDoubleOrNull() ?: 0.0,
                reason = str(s, "begruendung", "begründung", "reason").orEmpty(),
                evidence = evidence,
            )
        }
        if (raw.isEmpty()) return null
        // One score per criterion of the raster, in its order; matched by name, else by position; points within 0…max.
        val scores = rubric.mapIndexed { i, k ->
            val s = raw.firstOrNull { norm(it.criterion) == norm(k.name) } ?: raw.firstOrNull { norm(it.criterion).isNotEmpty() && (norm(k.name).startsWith(norm(it.criterion)) || norm(it.criterion).startsWith(norm(k.name))) } ?: raw.getOrNull(i)
            (s ?: BatchScore(k.name, 0.0, "Keine Bewertung erhalten.")).copy(criterion = k.name, points = (s?.points ?: 0.0).coerceIn(0.0, k.max))
        }
        return BatchGrade(scores, str(o, "rueckmeldung", "rückmeldung", "feedback").orEmpty(), str(o, "zusammenfassung", "summary").orEmpty())
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9äöüß]"), "")

    /** Whether a passage the AI quoted is really in the text (spaces and line breaks ignored, page reference cut off). */
    fun quoteFound(quote: String, text: String): Boolean {
        val q = squash(quote.replace(Regex("\\(\\s*(S\\.|Seite|p\\.)\\s*[0-9]+[^)]*\\)\\s*$"), "").trim().trim('"', '„', '“', '”', '»', '«'))
        if (q.length < 8) return false
        return squash(text).contains(q)
    }

    private fun squash(s: String) = s.lowercase().replace(Regex("[\\s\\u00ad]+"), " ").replace(Regex("[\"„“”»«‚‘’']"), "").trim()

    /** Total of a work: the decision if there is one, else the suggestion. */
    fun total(item: BatchItem): Double? = item.decision?.total ?: item.grade?.total

    /**
     * Outliers worth a second look: works far from the others (|z| ≥ [z]), works the AI could not grade, and works whose
     * suggestion differs much from the decision.
     */
    fun outliers(job: BatchJob, z: Double = 1.8): Set<String> {
        val graded = job.items.filter { it.status == BatchItem.DONE || it.decision != null }
        val totals = graded.mapNotNull { i -> total(i)?.let { i.id to it } }
        val out = mutableSetOf<String>()
        if (totals.size >= 5) {
            val mean = totals.map { it.second }.average()
            val sd = sqrt(totals.sumOf { (it.second - mean) * (it.second - mean) } / (totals.size - 1))
            if (sd > 0) totals.filter { kotlin.math.abs(it.second - mean) / sd >= z }.forEach { out += it.first }
        }
        job.items.filter { it.status == BatchItem.DONE && it.grade == null }.forEach { out += it.id }
        job.items.filter { it.grade != null && it.decision != null && !it.decision.calibration }
            .filter { kotlin.math.abs(it.grade!!.total - it.decision!!.total) >= maxOf(2.0, job.maxPoints * 0.15) }.forEach { out += it.id }
        return out
    }

    fun fmt(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else String.format(java.util.Locale.GERMANY, "%.1f", d)

    /** Reads a raster from a table: one criterion per line „Name; Punkte; Beschreibung“ (also Tab, | or „ – “ between). */
    fun parseRubric(text: String): List<BatchCriterion> = text.lines().map { it.trim().trim('|').trim() }.filter { it.isNotEmpty() }
        .filterNot { it.startsWith("---") || Regex("^[-:| ]+$").matches(it) }
        .mapNotNull { line ->
            val parts = line.split(Regex("\\s*[;\\t|]\\s*")).map { it.trim() }.filter { it.isNotEmpty() }
            val points = parts.drop(1).firstNotNullOfOrNull { p -> Regex("^([0-9]+(?:[.,][0-9]+)?)\\s*(?:P\\.?|Pkt\\.?|Punkte)?$").find(p)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() }
                ?: Regex("\\(([0-9]+(?:[.,][0-9]+)?)\\s*(?:P\\.?|Pkt\\.?|Punkte)\\)").find(line)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
                ?: return@mapNotNull null
            val name = parts.first().replace(Regex("\\(([0-9]+(?:[.,][0-9]+)?)\\s*(?:P\\.?|Pkt\\.?|Punkte)\\)"), "").trim()
            if (name.lowercase() in setOf("kriterium", "kriterien", "criterion")) return@mapNotNull null
            val desc = parts.drop(1).filter { p -> Regex("^([0-9]+(?:[.,][0-9]+)?)\\s*(?:P\\.?|Pkt\\.?|Punkte)?$").find(p) == null }.joinToString("; ")
            BatchCriterion(name, points, desc)
        }

    /** The result table as Markdown (for Excel and Word): suggestion, decision and status per work. */
    fun resultTable(job: BatchJob): String = buildString {
        val crit = job.rubric
        val head = listOf("Datei") + crit.map { "${it.name} (KI)" } + listOf("Summe KI") + crit.map { "${it.name} (Entscheidung)" } + listOf("Summe Entscheidung", "Status", "Notiz")
        append("| ").append(head.joinToString(" | ")).append(" |\n")
        append("|").append(head.joinToString("|") { " --- " }).append("|\n")
        val outliers = outliers(job)
        for (i in job.items) {
            val g = i.grade
            val d = i.decision
            val status = when {
                d?.calibration == true -> "von Hand (Kalibrierung)"
                d != null && d.accepted -> "übernommen"
                d != null -> "angepasst"
                i.status == BatchItem.FAILED -> "Fehler"
                i.open -> "offen"
                else -> "Vorschlag – nicht entschieden"
            } + if (i.id in outliers) " · Ausreißer" else ""
            val row = listOf(cell(i.name)) + crit.indices.map { k -> g?.scores?.getOrNull(k)?.points?.let(::fmt).orEmpty() } + listOf(g?.total?.let(::fmt).orEmpty()) +
                crit.indices.map { k -> d?.points?.getOrNull(k)?.let(::fmt).orEmpty() } + listOf(d?.total?.let(::fmt).orEmpty(), status, cell(d?.note.orEmpty()))
            append("| ").append(row.joinToString(" | ")).append(" |\n")
        }
    }

    /** The results of a plain batch as Markdown: one heading per file. */
    fun taskResults(job: BatchJob): String = buildString {
        append("# ").append(job.title).append("\n\nAuftrag: ").append(job.instruction.trim().replace("\n", " ")).append("\n\n")
        for (i in job.items) {
            append("## ").append(i.name).append("\n\n")
            append(
                when (i.status) {
                    BatchItem.DONE -> i.result.trim()
                    BatchItem.FAILED -> "_Fehler: ${i.error.orEmpty()}_"
                    else -> "_noch nicht bearbeitet_"
                }
            ).append("\n\n")
        }
    }

    /** Feedback for one person (Markdown, for Word): points per criterion as decided, the feedback text. */
    fun feedback(job: BatchJob, item: BatchItem): String = buildString {
        val d = item.decision
        val g = item.grade
        append("# Rückmeldung: ").append(job.title).append("\n\n")
        append("**Arbeit:** ").append(item.name.substringBeforeLast('.')).append("\n\n")
        append("| Kriterium | Punkte |\n| --- | --- |\n")
        job.rubric.forEachIndexed { k, c ->
            val p = d?.points?.getOrNull(k) ?: g?.scores?.getOrNull(k)?.points
            append("| ").append(cell(c.name)).append(" | ").append(p?.let(::fmt) ?: "–").append(" / ").append(fmt(c.max)).append(" |\n")
        }
        append("| **Gesamt** | **").append((d?.total ?: g?.total)?.let(::fmt) ?: "–").append(" / ").append(fmt(job.maxPoints)).append("** |\n\n")
        g?.feedback?.takeIf { it.isNotBlank() }?.let { append(it.trim()).append("\n\n") }
        d?.note?.takeIf { it.isNotBlank() && !d.accepted }?.let { append("**Anmerkung:** ").append(it.trim()).append("\n\n") }
    }

    /** The protocol of all decisions (KI-Verordnung) as Markdown. */
    fun protocol(job: BatchJob): String = buildString {
        val df = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.GERMANY)
        append("# Protokoll: ").append(job.title).append("\n\n")
        append("Die KI hat Vorschläge gemacht, entschieden hat ein Mensch. Modell: ").append(ModelCatalog.info(job.model).displayName)
        append(if (job.pseudonymize) " · pseudonymisiert" else "").append(if (job.uniOnly) " · nur TU Dresden" else "").append("\n\n")
        for (e in job.log) append("- ").append(df.format(java.util.Date(e.at))).append(" · ").append(e.item).append(": ").append(e.text).append('\n')
    }

    private fun cell(s: String) = s.replace("|", "/").replace("\n", " ")

    /** The raster of the TU Vorlage „Bewertungsraster“ as a starting point (adjustable). */
    val DEFAULT_RUBRIC = listOf(
        BatchCriterion(trLater("Fragestellung und Aufbau"), 10.0, trLater("Klare Fragestellung, roter Faden, sinnvolle Gliederung")),
        BatchCriterion(trLater("Theorie und Forschungsstand"), 10.0, trLater("Einschlägige Literatur, korrekt wiedergegeben und verknüpft")),
        BatchCriterion(trLater("Methode und Argumentation"), 10.0, trLater("Begründetes Vorgehen, schlüssige Argumentation")),
        BatchCriterion(trLater("Ergebnisse und Reflexion"), 10.0, trLater("Ergebnisse beantworten die Frage, kritische Reflexion")),
        BatchCriterion(trLater("Form und Sprache"), 10.0, trLater("Zitierweise, Sprache, Formalia")),
    )
}
