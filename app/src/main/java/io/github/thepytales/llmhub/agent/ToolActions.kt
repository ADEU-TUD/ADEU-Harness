package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.AppLog
import io.github.thepytales.llmhub.data.LoraDrive
import io.github.thepytales.llmhub.data.TrashEntry
import io.github.thepytales.llmhub.docs.DocFormat
import io.github.thepytales.llmhub.docs.PdfConvert
import io.github.thepytales.llmhub.docs.PdfStage3
import io.github.thepytales.llmhub.docs.PdfTools
import io.github.thepytales.llmhub.docs.parsePageRanges
import io.github.thepytales.llmhub.net.s
import io.github.thepytales.llmhub.ui.tr
import io.github.thepytales.llmhub.ui.trLater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Lora handelt in Werkzeugen (AP-38). Every tool reports its actions here – name, description, parameters, risk and
 * platform –, like the map of the app (AppMap) for places. Lora gets as tools only the actions of the area that is open
 * (short prompts, few tools). An action first makes a [ActionPlan]: the step line for the panel („Fügt 2 PDFs
 * zusammen …“) and, for everything that moves, deletes (Papierkorb) or overwrites, a preview „vorher → nachher“ that the
 * user confirms. Its [ActionResult] carries „Rückgängig“.
 *
 * New tools (the editors of AP-44, AP-48, AP-51, the video of AP-52) register their actions through the same
 * interface ([register]).
 */
enum class ActionArea(val label: String) {
    FILES(trLater("Dateien")), PDF(trLater("PDF-Werkzeugkasten")), DOC(trLater("Dokument")), VIDEO(trLater("Video"));

    companion object {
        /** The area of a screen route, or null (Lora then only navigates). */
        fun of(route: String?): ActionArea? {
            val r = route.orEmpty().substringBefore('?')
            return when {
                r == "files" || r == "docs" -> FILES
                r == "pdf" -> PDF
                r.startsWith("doc/") -> DOC
                r == "video" -> VIDEO
                else -> null
            }
        }
    }
}

/** What the user has in front of them; the screens keep it up to date, actions use it when no file is named. */
object LoraFocus {
    /** Files chosen (or last opened) in the Dateien tab. */
    @Volatile
    var files: List<File> = emptyList()

    /** The folder open in the Dateien tab. */
    @Volatile
    var folder: File? = null

    /** The PDF-Werkzeugkasten reports its tabs here: name, working copy, original (set once it has opened a PDF). */
    @Volatile
    var pdfSource: () -> List<Triple<String, File, File?>> = { emptyList() }

    /** All PDFs open in the PDF-Werkzeugkasten (name, working copy), the active one first. */
    val pdfTabs: List<Pair<String, File>> get() = runCatching { pdfSource() }.getOrDefault(emptyList()).map { it.first to it.second }

    /** The original of the active PDF in the werkzeugkasten (null for imported copies or when none is open). */
    val pdf: File? get() = runCatching { pdfSource().firstOrNull()?.third }.getOrNull()

    /** The document open in the editor. */
    @Volatile
    var docId: String? = null
}

/** Hooks of the screens for actions that work on what is open there (set by the screens; null when closed). */
object ActionHosts {
    /** Changes the PDF open in the werkzeugkasten (with its own Rückgängig); false when none is open. */
    @Volatile
    var changeOpenPdf: (suspend (label: String, op: (File, File) -> Unit) -> Boolean)? = null

    /** Takes back the last change of the open PDF. */
    @Volatile
    var undoOpenPdf: (() -> Unit)? = null

    /** Opens a PDF in the werkzeugkasten (navigates there). */
    @Volatile
    var openPdf: ((File) -> Unit)? = null

    /** The document editor (AP-47): writes at the cursor, revises a section as suggestions, inserts a table. */
    @Volatile
    var doc: DocActions? = null
}

/** What the open document offers Lora (AP-38 Stufe c, AP-47). */
interface DocActions {
    /** Writes text from [prompt] at the cursor (visible, then Übernehmen · Neu · Verwerfen). */
    fun write(prompt: String)

    /** Revises [section] (heading text or „markierung“) after [instruction] as suggestions to accept one by one. */
    fun revise(section: String, instruction: String): Boolean

    /** Inserts a table at the cursor; returns the Markdown before, for Rückgängig. */
    fun insertTable(header: List<String>, rows: Int): String?

    /** Makes the paragraph or line with [text] a heading of [level] (Formatvorlage, not bold text). */
    fun heading(text: String, level: Int): String?

    /** Restores the Markdown of the open tab (Rückgängig of the two above). */
    fun restore(markdown: String)
}

data class ActionParam(val name: String, val description: String, val required: Boolean = true)

/** The outcome of an action: a sentence, the files made, how to take it back, and what to open. */
class ActionResult(
    val text: String,
    val files: List<File> = emptyList(),
    val undo: (suspend () -> String)? = null,
    val open: File? = null,
)

/**
 * A planned action. [title] is the step line („Fügt 2 PDFs zusammen …“); [risky] ones wait for „Ausführen“ and show
 * [preview] (vorher → nachher) first.
 */
class ActionPlan(
    val actionId: String,
    val title: String,
    val risky: Boolean,
    val preview: List<Pair<String, String>> = emptyList(),
    val run: suspend () -> ActionResult,
)

class ActionFailed(message: String) : Exception(message)

/** One action of a tool. [plan] checks the parameters and prepares; nothing changes before [ActionPlan.run]. */
class ToolAction(
    val id: String,
    val area: ActionArea,
    val title: String,
    val description: String,
    val params: List<ActionParam>,
    val risky: Boolean,
    val android: Boolean = true,
    val plan: suspend (AppContainer, JsonObject) -> ActionPlan,
) {
    /** The tool for function calling: „act_“ + the id with „_“. */
    val toolName: String get() = "act_" + id.replace('.', '_')

    fun toolDef(): ToolDef = ToolDef(
        toolName, description + if (risky) " (Der Nutzer sieht vorher eine Vorschau und bestätigt.)" else " (Mit Rückgängig.)",
        params.map { Param(it.name, it.description, it.required) },
    )
}

object ToolActions {
    private val extra = mutableListOf<ToolAction>()

    /** Registers the actions of a tool (e.g. the video editor of the Windows app). */
    fun register(actions: List<ToolAction>) {
        synchronized(extra) { actions.forEach { a -> extra.removeAll { it.id == a.id }; extra += a } }
    }

    val all: List<ToolAction> get() = BUILT_IN + synchronized(extra) { extra.toList() }

    /** The actions Lora gets in [area] (PDF actions also in the Dateien tab, for chosen PDFs). */
    fun forArea(area: ActionArea?, android: Boolean): List<ToolAction> {
        area ?: return emptyList()
        val areas = when (area) {
            ActionArea.FILES -> setOf(ActionArea.FILES, ActionArea.PDF)
            else -> setOf(area)
        }
        return all.filter { it.area in areas && (!android || it.android) && (area != ActionArea.FILES || it.area == ActionArea.FILES || it.id in PDF_IN_FILES) }
    }

    fun byToolName(name: String): ToolAction? = all.firstOrNull { it.toolName == name }

    fun byId(id: String): ToolAction? = all.firstOrNull { it.id == id }

    /** Writes into the log what Lora did (with „Problem melden“ it reaches the developer). */
    fun log(text: String) = AppLog.i("Lora-Aktion: $text")

    private val PDF_IN_FILES = setOf("pdf.open", "pdf.merge", "pdf.compress", "pdf.convert", "pdf.split")

    // ── Finding the files an action is about ─────────────────────────────────────────────────────────────

    /**
     * The files named in [names] (comma separated; names or parts of names), else the ones in focus. Looks in the
     * focus, the open folder and the Lora-Ordner. [ext] limits to a type ("pdf").
     */
    internal fun files(c: AppContainer, names: String?, ext: String? = null): List<File> {
        fun ok(f: File) = f.isFile && (ext == null || f.extension.equals(ext, true))
        val wanted = names.orEmpty().split(',', ';', '\n').map { it.trim().trim('"', '„', '“') }.filter { it.isNotEmpty() && it.lowercase() !in FOCUS_WORDS }
        val focus = (LoraFocus.files + listOfNotNull(LoraFocus.pdf)).filter(::ok)
        if (wanted.isEmpty()) return focus.ifEmpty { if (ext == "pdf") LoraFocus.pdfTabs.map { it.second }.filter(::ok) else emptyList() }
        val pool by lazy {
            (focus + LoraFocus.folder?.listFiles().orEmpty().toList() + runCatching { c.drive.allFiles(4000) }.getOrDefault(emptyList()))
                .filter(::ok).distinctBy { it.absolutePath }
        }
        return wanted.mapNotNull { w ->
            File(w).takeIf { it.isAbsolute && ok(it) }
                ?: LoraFocus.pdfTabs.firstOrNull { (n, f) -> n.equals(w, true) && ok(f) }?.second
                ?: pool.firstOrNull { it.name.equals(w, true) }
                ?: pool.firstOrNull { it.nameWithoutExtension.equals(w.substringBeforeLast('.'), true) }
                ?: pool.firstOrNull { it.name.contains(w, true) }
        }.distinctBy { it.absolutePath }
    }

    private val FOCUS_WORDS = setOf("diese", "dieses", "diese datei", "die datei", "markierte", "ausgewählte", "auswahl", "aktuelle", "das pdf", "dieses pdf")

    /** The folder named [name] (relative to the Lora-Ordner or the open folder), created by [create]. */
    internal fun folder(c: AppContainer, name: String?, create: Boolean): File? {
        val n = name?.trim()?.trim('/', '\\')?.takeIf { it.isNotEmpty() } ?: return LoraFocus.folder
        File(n).takeIf { it.isAbsolute }?.let { return it.takeIf { d -> d.isDirectory || create && d.mkdirs() } }
        val base = LoraFocus.folder ?: c.drive.root
        val known = listOf(File(base, n), File(c.drive.root, n)).firstOrNull { it.isDirectory }
            ?: runCatching { c.drive.allFolders() }.getOrDefault(emptyList()).firstOrNull { it.substringAfterLast('/').equals(n, true) }?.let { c.drive.fileOf(it) }
        return known ?: if (create) File(base, n).also { it.mkdirs() } else null
    }

    /** A new file next to [source] (or in the Erstellt folder) that does not overwrite anything. */
    internal fun beside(c: AppContainer, source: File?, name: String): File {
        val dir = source?.parentFile?.takeIf { it.canWrite() && !it.absolutePath.contains("pdf-work") } ?: c.drive.created
        return LoraDrive.unique(dir, name)
    }

    /** Rückgängig for files an action made: into the Papierkorb (Lora-Ordner) or deleted. */
    private fun removeMade(c: AppContainer, files: List<File>): String {
        val inside = files.filter { c.drive.inside(it) }
        if (inside.isNotEmpty()) c.drive.delete(inside)
        files.filter { !c.drive.inside(it) }.forEach { it.delete() }
        c.drive.changed()
        return tr("Wieder entfernt.")
    }

    private fun pages(c: AppContainer, f: File): Int = runCatching { PdfTools.info(c.appContext, f).pages }.getOrDefault(0)

    private fun list(files: List<File>) = files.joinToString(", ") { "„${it.name}“" }

    // ── The actions ─────────────────────────────────────────────────────────────────────────────────────

    private val BUILT_IN: List<ToolAction> = listOf(
        // Stufe a – Dateien
        ToolAction(
            "files.rename", ActionArea.FILES, trLater("Umbenennen"), "Benennt eine Datei um.",
            listOf(ActionParam("datei", "Name der Datei (leer: die ausgewählte)", required = false), ActionParam("neuer_name", "Neuer Name, mit Endung")),
            risky = true,
        ) { c, a ->
            val f = files(c, a.s("datei")).singleOrNull() ?: throw ActionFailed(tr("Welche Datei? Bitte eine auswählen oder den Namen nennen."))
            val name = io.github.thepytales.llmhub.files.FileText.safeName(a.s("neuer_name").orEmpty()).let { n ->
                if (n.contains('.') || f.extension.isEmpty()) n else "$n.${f.extension}"
            }
            if (name.isBlank()) throw ActionFailed(tr("Wie soll die Datei heißen?"))
            ActionPlan("files.rename", tr("Benennt „{0}“ um …", f.name), true, listOf(f.name to name)) {
                val renamed = withContext(Dispatchers.IO) { c.drive.rename(f, name) }
                ActionResult(tr("Umbenannt in „{0}“.", renamed.name), listOf(renamed), undo = {
                    withContext(Dispatchers.IO) { c.drive.rename(renamed, f.name) }
                    tr("Wieder „{0}“.", f.name)
                })
            }
        },
        ToolAction(
            "files.rename_by_content", ActionArea.FILES, trLater("Nach Inhalt umbenennen"),
            "Schlägt für Dateien Namen nach ihrem Inhalt vor (z. B. „2026-10-12 Protokoll Institutsrat.pdf“) und benennt sie um.",
            listOf(ActionParam("dateien", "Namen, kommagetrennt (leer: die ausgewählten oder der offene Ordner)", required = false)),
            risky = true,
        ) { c, a ->
            val fs = files(c, a.s("dateien")).ifEmpty { LoraFocus.folder?.listFiles()?.filter { it.isFile && c.drive.visible(it) }.orEmpty() }.take(20)
            if (fs.isEmpty()) throw ActionFailed(tr("Welche Dateien? Bitte auswählen oder einen Ordner öffnen."))
            val names = suggestNames(c, fs)
            val changes = fs.zip(names).filter { (f, n) -> n.isNotBlank() && n != f.name }
            if (changes.isEmpty()) throw ActionFailed(tr("Die Namen passen schon."))
            ActionPlan("files.rename_by_content", tr("Benennt {0} Dateien nach ihrem Inhalt um …", changes.size), true, changes.map { (f, n) -> f.name to n }) {
                val done = withContext(Dispatchers.IO) { changes.map { (f, n) -> f to c.drive.rename(f, n) } }
                ActionResult(tr("{0} Dateien umbenannt.", done.size), done.map { it.second }, undo = {
                    withContext(Dispatchers.IO) { done.forEach { (old, now) -> runCatching { c.drive.rename(now, old.name) } } }
                    tr("Alte Namen wiederhergestellt.")
                })
            }
        },
        ToolAction(
            "files.move", ActionArea.FILES, trLater("Verschieben"), "Verschiebt Dateien in einen Ordner (wird bei Bedarf angelegt).",
            listOf(ActionParam("dateien", "Namen, kommagetrennt (leer: die ausgewählten)", required = false), ActionParam("ordner", "Zielordner im Lora-Ordner, z. B. Lehre/WiSe 2026")),
            risky = true,
        ) { c, a ->
            val fs = files(c, a.s("dateien"))
            if (fs.isEmpty()) throw ActionFailed(tr("Welche Dateien? Bitte auswählen oder die Namen nennen."))
            val name = a.s("ordner") ?: throw ActionFailed(tr("Wohin?"))
            val target = folder(c, name, create = false)
            val shown = target?.let { c.drive.rel(it) ?: it.absolutePath } ?: name
            ActionPlan("files.move", tr("Verschiebt {0} Dateien nach „{1}“ …", fs.size, shown), true, fs.map { it.name to "$shown/${it.name}" }) {
                val dir = folder(c, name, create = true) ?: throw ActionFailed(tr("Den Ordner gibt es nicht."))
                val moved = withContext(Dispatchers.IO) { fs.zip(c.drive.move(fs, dir)) }
                ActionResult(tr("{0} Dateien nach „{1}“ verschoben.", moved.size, shown), moved.map { it.second }, undo = {
                    withContext(Dispatchers.IO) { moved.forEach { (old, now) -> old.parentFile?.let { runCatching { c.drive.move(listOf(now), it) } } } }
                    tr("Zurück an den alten Ort.")
                })
            }
        },
        ToolAction(
            "files.tag", ActionArea.FILES, trLater("Schlagwörter vergeben"), "Vergibt Schlagwörter an Dateien (ergänzt die vorhandenen).",
            listOf(ActionParam("dateien", "Namen, kommagetrennt (leer: die ausgewählten)", required = false), ActionParam("schlagwoerter", "Schlagwörter, kommagetrennt")),
            risky = false,
        ) { c, a ->
            val fs = files(c, a.s("dateien"))
            if (fs.isEmpty()) throw ActionFailed(tr("Welche Dateien? Bitte auswählen oder die Namen nennen."))
            val tags = a.s("schlagwoerter").orEmpty().split(',', ';').map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }
            if (tags.isEmpty()) throw ActionFailed(tr("Welche Schlagwörter?"))
            ActionPlan("files.tag", tr("Vergibt Schlagwörter an {0} Dateien …", fs.size), false) {
                val before = fs.associateWith { f -> c.fileLibrary.meta.get(c.drive.metaKey(f))?.tags.orEmpty() }
                before.forEach { (f, old) -> c.fileLibrary.setTags(c.drive.metaKey(f), old + tags) }
                ActionResult(tr("Schlagwörter {0} vergeben.", tags.joinToString(" ") { "#$it" }), fs, undo = {
                    before.forEach { (f, old) -> c.fileLibrary.setTags(c.drive.metaKey(f), old) }
                    tr("Schlagwörter zurückgesetzt.")
                })
            }
        },
        ToolAction(
            "files.duplicates", ActionArea.FILES, trLater("Duplikate finden"),
            "Findet doppelte Dateien (gleicher Inhalt) im offenen Ordner oder im ganzen Lora-Ordner und legt die Kopien in den Papierkorb.",
            listOf(ActionParam("ordner", "Ordner (leer: der offene, sonst der ganze Lora-Ordner)", required = false)),
            risky = true,
        ) { c, a ->
            val base = a.s("ordner")?.let { folder(c, it, false) } ?: LoraFocus.folder
            val all = withContext(Dispatchers.IO) {
                if (base != null) base.walkTopDown().maxDepth(6).filter { it.isFile && c.drive.visible(it) && !it.absolutePath.contains("${File.separator}.") }.take(5000).toList()
                else c.drive.allFiles(5000)
            }
            val groups = withContext(Dispatchers.IO) { duplicates(all) }
            if (groups.isEmpty()) throw ActionFailed(tr("Keine Duplikate gefunden – jede Datei gibt es nur einmal."))
            // The oldest (or shortest named) stays, the others go into the Papierkorb.
            val extras = groups.flatMap { g -> g.sortedWith(compareBy<File>({ it.lastModified() }, { it.name.length })).drop(1) }
            ActionPlan(
                "files.duplicates", tr("Legt {0} doppelte Dateien in den Papierkorb …", extras.size), true,
                extras.map { f -> (c.drive.rel(f) ?: f.name) to tr("Papierkorb") },
            ) {
                val trashed = withContext(Dispatchers.IO) { c.drive.delete(extras) }
                ActionResult(tr("{0} Duplikate im Papierkorb (30 Tage wiederherstellbar).", trashed.size), undo = {
                    withContext(Dispatchers.IO) { trashed.forEach { runCatching { c.drive.restore(it) } } }
                    tr("Alle wiederhergestellt.")
                })
            }
        },
        ToolAction(
            "files.tidy", ActionArea.FILES, trLater("Aufräumen"),
            "Räumt einen Ordner auf: schlägt Unterordner nach Dateiart, Semester oder Monat vor und verschiebt die Dateien hinein.",
            listOf(
                ActionParam("ordner", "Ordner (leer: der offene, sonst Hochgeladen)", required = false),
                ActionParam("nach", "typ, semester oder monat (Standard: typ)", required = false),
            ),
            risky = true,
        ) { c, a ->
            val dir = a.s("ordner")?.let { folder(c, it, false) } ?: LoraFocus.folder ?: c.drive.uploads
            val by = a.s("nach")?.lowercase().orEmpty().let { if ("sem" in it) "semester" else if ("mon" in it) "monat" else "typ" }
            val loose = dir.listFiles().orEmpty().filter { it.isFile && c.drive.visible(it) }
            if (loose.isEmpty()) throw ActionFailed(tr("In „{0}“ liegen keine losen Dateien.", dir.name))
            val plan = loose.map { f -> f to tidyFolder(f, by) }
            ActionPlan(
                "files.tidy", tr("Ordnet {0} Dateien in „{1}“ in Unterordner …", loose.size, dir.name), true,
                plan.map { (f, sub) -> f.name to "$sub/${f.name}" },
            ) {
                val moved = withContext(Dispatchers.IO) {
                    plan.groupBy({ it.second }, { it.first }).flatMap { (sub, fs) -> fs.zip(c.drive.move(fs, File(dir, sub).apply { mkdirs() })) }
                }
                ActionResult(tr("{0} Dateien in {1} Ordner sortiert.", moved.size, plan.map { it.second }.distinct().size), undo = {
                    withContext(Dispatchers.IO) {
                        moved.forEach { (_, now) -> runCatching { c.drive.move(listOf(now), dir) } }
                        plan.map { File(dir, it.second) }.distinct().filter { it.listFiles().isNullOrEmpty() }.forEach { it.delete() }
                    }
                    tr("Alles wieder wie vorher.")
                })
            }
        },
        ToolAction(
            "files.trash", ActionArea.FILES, trLater("In den Papierkorb"), "Legt Dateien in den Papierkorb (30 Tage wiederherstellbar).",
            listOf(ActionParam("dateien", "Namen, kommagetrennt (leer: die ausgewählten)", required = false)),
            risky = true,
        ) { c, a ->
            val fs = files(c, a.s("dateien"))
            if (fs.isEmpty()) throw ActionFailed(tr("Welche Dateien? Bitte auswählen oder die Namen nennen."))
            ActionPlan("files.trash", tr("Legt {0} Dateien in den Papierkorb …", fs.size), true, fs.map { it.name to tr("Papierkorb") }) {
                val trashed: List<TrashEntry> = withContext(Dispatchers.IO) { c.drive.delete(fs) }
                ActionResult(tr("{0} Dateien im Papierkorb.", trashed.size), undo = {
                    withContext(Dispatchers.IO) { trashed.forEach { runCatching { c.drive.restore(it) } } }
                    tr("Wiederhergestellt.")
                })
            }
        },
        // Stufe b – PDF-Werkzeugkasten
        ToolAction(
            "pdf.open", ActionArea.PDF, trLater("Im PDF-Werkzeugkasten öffnen"), "Öffnet ein PDF (oder die ausgewählte Datei) im PDF-Werkzeugkasten zum Bearbeiten.",
            listOf(ActionParam("datei", "Name des PDFs (leer: das ausgewählte)", required = false)),
            risky = false,
        ) { c, a ->
            val f = files(c, a.s("datei"), "pdf").firstOrNull() ?: throw ActionFailed(tr("Welches PDF? Bitte eines auswählen oder den Namen nennen."))
            ActionPlan("pdf.open", tr("Öffnet „{0}“ im PDF-Werkzeugkasten …", f.name), false) {
                ActionResult(tr("„{0}“ ist im PDF-Werkzeugkasten offen.", f.name), open = f)
            }
        },
        ToolAction(
            "pdf.merge", ActionArea.PDF, trLater("Zusammenfügen"), "Fügt mehrere PDFs in der genannten Reihenfolge zu einem neuen PDF zusammen.",
            listOf(ActionParam("dateien", "PDFs, kommagetrennt, in Reihenfolge (leer: die ausgewählten bzw. offenen)", required = false), ActionParam("name", "Name des neuen PDFs", required = false)),
            risky = false,
        ) { c, a ->
            val fs = files(c, a.s("dateien"), "pdf").ifEmpty { LoraFocus.pdfTabs.map { it.second } }
            if (fs.size < 2) throw ActionFailed(tr("Zum Zusammenfügen braucht es mindestens zwei PDFs – bitte auswählen oder nennen."))
            val name = (a.s("name")?.takeIf { it.isNotBlank() } ?: (fs.first().nameWithoutExtension + " " + tr("(zusammengefügt)"))).removeSuffix(".pdf") + ".pdf"
            ActionPlan("pdf.merge", tr("Fügt {0} PDFs zusammen …", fs.size), false) {
                val out = beside(c, LoraFocus.pdf ?: fs.first(), io.github.thepytales.llmhub.files.FileText.safeName(name))
                withContext(Dispatchers.IO) { PdfTools.merge(c.appContext, fs, out) }
                c.drive.changed()
                ActionResult(tr("„{0}“ erstellt ({1} Seiten).", out.name, pages(c, out)), listOf(out), undo = { removeMade(c, listOf(out)) }, open = out)
            }
        },
        ToolAction(
            "pdf.split", ActionArea.PDF, trLater("Teilen"), "Teilt ein PDF: in einzelne Seiten oder nach Bereichen (z. B. 1-3;4-10).",
            listOf(ActionParam("datei", "PDF (leer: das offene)", required = false), ActionParam("bereiche", "„einzeln“ oder Bereiche mit Semikolon, z. B. 1-3;4-10")),
            risky = false,
        ) { c, a ->
            val f = openOrNamedPdf(c, a.s("datei"))
            val total = pages(c, f)
            val spec = a.s("bereiche").orEmpty()
            val groups = if (spec.isBlank() || spec.lowercase().startsWith("einzel")) (0 until total).map { listOf(it) }
            else spec.split(';').map { parsePageRanges(it, total) }.filter { it.isNotEmpty() }
            if (groups.isEmpty()) throw ActionFailed(tr("Die Seitenbereiche passen nicht zum PDF ({0} Seiten).", total))
            ActionPlan("pdf.split", tr("Teilt „{0}“ in {1} PDFs …", displayName(f), groups.size), false) {
                val dir = beside(c, LoraFocus.pdf ?: f, displayName(f).removeSuffix(".pdf") + " " + tr("(geteilt)")).apply { mkdirs() }
                val made = withContext(Dispatchers.IO) { PdfTools.split(c.appContext, f, groups, dir, displayName(f).removeSuffix(".pdf")) }
                c.drive.changed()
                ActionResult(tr("{0} PDFs im Ordner „{1}“.", made.size, dir.name), made, undo = { removeMade(c, made + dir) })
            }
        },
        ToolAction(
            "pdf.delete_pages", ActionArea.PDF, trLater("Seiten löschen"), "Löscht Seiten aus dem offenen PDF.",
            listOf(ActionParam("seiten", "Seiten, z. B. 2, 5-7")), risky = true,
        ) { c, a -> pageEdit(c, "pdf.delete_pages", a.s("seiten").orEmpty(), tr("Löscht Seite(n) {0} …")) { f, sel, total, out -> PdfTools.rearrange(c.appContext, f, (0 until total).filter { it !in sel }, emptyMap(), out) } },
        ToolAction(
            "pdf.rotate", ActionArea.PDF, trLater("Seiten drehen"), "Dreht Seiten des offenen PDFs.",
            listOf(ActionParam("seiten", "Seiten, z. B. 1-3 oder „alle“"), ActionParam("grad", "90, 180 oder 270 (im Uhrzeigersinn)", required = false)), risky = false,
        ) { c, a ->
            val deg = a.s("grad")?.filter { it.isDigit() }?.toIntOrNull()?.let { ((it / 90) * 90).mod(360) }?.takeIf { it != 0 } ?: 90
            pageEdit(c, "pdf.rotate", a.s("seiten").orEmpty(), tr("Dreht Seite(n) {0} …"), risky = false) { f, sel, total, out ->
                PdfTools.rearrange(c.appContext, f, (0 until total).toList(), sel.associateWith { deg }, out)
            }
        },
        ToolAction(
            "pdf.reorder", ActionArea.PDF, trLater("Seiten sortieren"), "Ordnet die Seiten des offenen PDFs neu (vollständige neue Reihenfolge).",
            listOf(ActionParam("reihenfolge", "Neue Reihenfolge, z. B. 3,1,2,4-10")), risky = false,
        ) { c, a ->
            pageEdit(c, "pdf.reorder", a.s("reihenfolge").orEmpty(), tr("Sortiert die Seiten neu ({0}) …"), risky = false, keepOrder = true) { f, sel, total, out ->
                PdfTools.rearrange(c.appContext, f, sel + (0 until total).filter { it !in sel }, emptyMap(), out)
            }
        },
        ToolAction(
            "pdf.compress", ActionArea.PDF, trLater("Verkleinern"), "Verkleinert ein PDF (Bilder neu komprimiert) als neue Datei.",
            listOf(ActionParam("datei", "PDF (leer: das offene bzw. ausgewählte)", required = false)), risky = false, android = false,
        ) { c, a ->
            val f = openOrNamedPdf(c, a.s("datei"))
            ActionPlan("pdf.compress", tr("Verkleinert „{0}“ …", displayName(f)), false) {
                val out = beside(c, LoraFocus.pdf ?: f, displayName(f).removeSuffix(".pdf") + " " + tr("(klein)") + ".pdf")
                val (before, after) = withContext(Dispatchers.IO) { PdfStage3.shrink(f, out) }
                c.drive.changed()
                ActionResult(
                    tr("„{0}“: {1} statt {2}.", out.name, io.github.thepytales.llmhub.data.NextcloudService.size(after), io.github.thepytales.llmhub.data.NextcloudService.size(before)),
                    listOf(out), undo = { removeMade(c, listOf(out)) }, open = out,
                )
            }
        },
        ToolAction(
            "pdf.ocr", ActionArea.PDF, trLater("Texterkennung"), "Erkennt Text auf gescannten Seiten des offenen PDFs (danach durchsuchbar und kopierbar).",
            emptyList(), risky = false,
        ) { c, _ ->
            val f = openOrNamedPdf(c, null)
            ActionPlan("pdf.ocr", tr("Erkennt Text in „{0}“ …", displayName(f)), false) {
                val ctx = c.appContext
                val texts = withContext(Dispatchers.IO) { io.github.thepytales.llmhub.docs.PdfExtras.pageTexts(ctx, f) }
                val scanned = io.github.thepytales.llmhub.docs.PdfOcr.scanned(texts)
                if (scanned.isEmpty()) return@ActionPlan ActionResult(tr("Alle Seiten haben schon Text."))
                val model = if (io.github.thepytales.llmhub.docs.PdfPlatform.localOcr != null) null else io.github.thepytales.llmhub.docs.PdfOcr.visionModel(c)
                val work = File(ctx.cacheDir, "pdf-work/ocr-lora-${System.nanoTime()}").apply { mkdirs() }
                val r = io.github.thepytales.llmhub.docs.PdfOcr.recognize(c, ctx, f, scanned, model, work) { _, _ -> }
                val found = r.pages.filter { it.lines.isNotEmpty() }
                if (found.isEmpty()) throw ActionFailed(tr("Auf den Seiten wurde kein Text erkannt."))
                val change = ActionHosts.changeOpenPdf
                if (change != null && change(tr("Texterkennung")) { src, out -> io.github.thepytales.llmhub.docs.PdfExtras.addTextLayer(ctx, src, found, out) }) {
                    ActionResult(tr("Text auf {0} Seiten erkannt ({1}).", found.size, r.engine), undo = { ActionHosts.undoOpenPdf?.invoke(); tr("Texterkennung zurückgenommen.") })
                } else throw ActionFailed(tr("Bitte das PDF im PDF-Werkzeugkasten öffnen."))
            }
        },
        ToolAction(
            "pdf.redact", ActionArea.PDF, trLater("Schwärzen"),
            "Schwärzt Begriffe (Namen, Matrikelnummern …) im offenen PDF dauerhaft: der Text darunter wird entfernt. Immer mit Vorschau.",
            listOf(ActionParam("begriffe", "Begriffe, kommagetrennt")), risky = true, android = false,
        ) { c, a ->
            val f = openOrNamedPdf(c, null)
            val terms = a.s("begriffe").orEmpty().split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
            if (terms.isEmpty()) throw ActionFailed(tr("Was soll geschwärzt werden?"))
            val hits = withContext(Dispatchers.IO) { PdfStage3.find(f, terms) }
            if (hits.isEmpty()) throw ActionFailed(tr("Die Begriffe kommen im PDF nicht vor."))
            val preview = hits.groupBy { it.text.ifBlank { "?" } }.map { (t, hs) -> "„$t“" to tr("{0}× geschwärzt (Seite {1})", hs.size, hs.map { it.page + 1 }.distinct().joinToString(", ")) }
            ActionPlan("pdf.redact", tr("Schwärzt {0} Stellen in „{1}“ …", hits.size, displayName(f)), true, preview) {
                val change = ActionHosts.changeOpenPdf ?: throw ActionFailed(tr("Bitte das PDF im PDF-Werkzeugkasten öffnen."))
                if (!change(tr("Schwärzen")) { src, out -> PdfStage3.redact(src, PdfStage3.find(src, terms), out) }) throw ActionFailed(tr("Das hat nicht geklappt"))
                ActionResult(tr("{0} Stellen geschwärzt – erst „Speichern“ im Werkzeugkasten ändert das Original.", hits.size), undo = { ActionHosts.undoOpenPdf?.invoke(); tr("Schwärzung zurückgenommen.") })
            }
        },
        ToolAction(
            "pdf.convert", ActionArea.PDF, trLater("Umwandeln"), "Wandelt ein PDF in Word (docx), Markdown (md) oder Text (txt) um.",
            listOf(ActionParam("datei", "PDF (leer: das offene bzw. ausgewählte)", required = false), ActionParam("format", "docx, md oder txt")), risky = false,
        ) { c, a ->
            val f = openOrNamedPdf(c, a.s("datei"))
            val format = when (a.s("format")?.lowercase()?.trim('.').orEmpty()) {
                "md", "markdown" -> DocFormat.MD
                "txt", "text" -> DocFormat.TXT
                else -> DocFormat.DOCX
            }
            ActionPlan("pdf.convert", tr("Wandelt „{0}“ in {1} um …", displayName(f), format.label), false) {
                val dir = (LoraFocus.pdf ?: f).parentFile?.takeIf { it.canWrite() && !it.absolutePath.contains("pdf-work") } ?: c.drive.created
                val made = PdfConvert.fromPdf(c.appContext, f, displayName(f).removeSuffix(".pdf"), format, dir)
                c.drive.changed()
                ActionResult(tr("„{0}“ erstellt.", made.name), listOf(made), undo = { removeMade(c, listOf(made)) })
            }
        },
        // Stufe c – Dokument (über den Editor, AP-47)
        ToolAction(
            "doc.write", ActionArea.DOC, trLater("Hilf mir schreiben"), "Schreibt an der Cursorstelle des offenen Dokuments (z. B. eine Einleitung zu …); der Nutzer übernimmt oder verwirft.",
            listOf(ActionParam("auftrag", "Was geschrieben werden soll")), risky = false,
        ) { _, a ->
            val host = ActionHosts.doc ?: throw ActionFailed(tr("Bitte ein Dokument öffnen."))
            val prompt = a.s("auftrag")?.takeIf { it.isNotBlank() } ?: throw ActionFailed(tr("Was soll ich schreiben?"))
            ActionPlan("doc.write", tr("Schreibt im Dokument …"), false) {
                host.write(prompt)
                ActionResult(tr("Ich schreibe an der Cursorstelle – danach Übernehmen, Neu oder Verwerfen."))
            }
        },
        ToolAction(
            "doc.revise", ActionArea.DOC, trLater("Abschnitt überarbeiten"),
            "Überarbeitet einen Abschnitt (Überschrift) oder die Markierung als Vorschläge, die einzeln angenommen werden (Änderungen nachverfolgen).",
            listOf(ActionParam("abschnitt", "Überschrift des Abschnitts oder „markierung“"), ActionParam("anweisung", "Wie überarbeiten, z. B. kürzer, sachlicher")), risky = false,
        ) { _, a ->
            val host = ActionHosts.doc ?: throw ActionFailed(tr("Bitte ein Dokument öffnen."))
            val section = a.s("abschnitt").orEmpty().ifBlank { "markierung" }
            val instruction = a.s("anweisung").orEmpty().ifBlank { "Sprachlich verbessern" }
            ActionPlan("doc.revise", tr("Überarbeitet „{0}“ …", section), false) {
                if (!host.revise(section, instruction)) throw ActionFailed(tr("Den Abschnitt „{0}“ finde ich nicht.", section))
                ActionResult(tr("Die Vorschläge stehen im Text – jede Änderung einzeln annehmen oder ablehnen."))
            }
        },
        ToolAction(
            "doc.insert_table", ActionArea.DOC, trLater("Tabelle einfügen"), "Fügt an der Cursorstelle eine Tabelle mit Kopfzeile ein.",
            listOf(ActionParam("spalten", "Spaltenüberschriften, kommagetrennt"), ActionParam("zeilen", "Anzahl der Zeilen (Standard 3)", required = false)), risky = false,
        ) { _, a ->
            val host = ActionHosts.doc ?: throw ActionFailed(tr("Bitte ein Dokument öffnen."))
            val header = a.s("spalten").orEmpty().split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("Spalte 1", "Spalte 2") }
            val rows = a.s("zeilen")?.toIntOrNull()?.coerceIn(1, 50) ?: 3
            ActionPlan("doc.insert_table", tr("Fügt eine Tabelle ein …"), false) {
                val before = host.insertTable(header, rows) ?: throw ActionFailed(tr("Bitte ein Dokument öffnen."))
                ActionResult(tr("Tabelle mit {0} Spalten eingefügt.", header.size), undo = { host.restore(before); tr("Tabelle entfernt.") })
            }
        },
        ToolAction(
            "doc.heading", ActionArea.DOC, trLater("Als Überschrift"), "Macht einen Absatz zur Überschrift (Formatvorlage Überschrift 1–4).",
            listOf(ActionParam("text", "Anfang des Absatzes"), ActionParam("ebene", "1 bis 4", required = false)), risky = false,
        ) { _, a ->
            val host = ActionHosts.doc ?: throw ActionFailed(tr("Bitte ein Dokument öffnen."))
            val text = a.s("text").orEmpty().ifBlank { throw ActionFailed(tr("Welcher Absatz?")) }
            val level = a.s("ebene")?.toIntOrNull()?.coerceIn(1, 4) ?: 2
            ActionPlan("doc.heading", tr("Setzt „{0}“ als Überschrift {1} …", text.take(40), level), false) {
                val before = host.heading(text, level) ?: throw ActionFailed(tr("Den Absatz finde ich nicht."))
                ActionResult(tr("Formatvorlage „Überschrift {0}“ gesetzt.", level), undo = { host.restore(before); tr("Zurückgesetzt.") })
            }
        },
    )

    /** The PDF the action is about: named, else the open one (working copy), else the chosen one. */
    private fun openOrNamedPdf(c: AppContainer, name: String?): File =
        (if (name.isNullOrBlank()) LoraFocus.pdfTabs.firstOrNull()?.takeIf { LoraFocus.pdf != null || LoraFocus.files.none { it.extension.equals("pdf", true) } }?.second else null)
            ?: files(c, name, "pdf").firstOrNull()
            ?: throw ActionFailed(tr("Welches PDF? Bitte eines öffnen, auswählen oder den Namen nennen."))

    private fun displayName(f: File): String = LoraFocus.pdfTabs.firstOrNull { it.second == f }?.first ?: f.name

    /** Changes pages of the open PDF (with the werkzeugkasten's own Rückgängig). */
    private suspend fun pageEdit(
        c: AppContainer, id: String, spec: String, title: String, risky: Boolean = true, keepOrder: Boolean = false,
        op: (File, List<Int>, Int, File) -> Unit,
    ): ActionPlan {
        val f = LoraFocus.pdfTabs.firstOrNull()?.second ?: throw ActionFailed(tr("Bitte das PDF im PDF-Werkzeugkasten öffnen."))
        val total = pages(c, f)
        val sel = if (spec.trim().lowercase() in setOf("alle", "all", "")) (0 until total).toList()
        else parsePageRanges(spec, total).let { if (keepOrder) it else it.sorted() }
        if (sel.isEmpty()) throw ActionFailed(tr("Die Seiten passen nicht zum PDF ({0} Seiten).", total))
        if (id == "pdf.delete_pages" && sel.size >= total) throw ActionFailed(tr("Alle Seiten zu löschen geht nicht."))
        val shown = spec.ifBlank { tr("alle") }
        val preview = if (risky) listOf(tr("{0} Seiten", total) to tr("{0} Seiten (ohne {1})", total - sel.size, shown)) else emptyList()
        return ActionPlan(id, title.replace("{0}", shown), risky, preview) {
            val change = ActionHosts.changeOpenPdf ?: throw ActionFailed(tr("Bitte das PDF im PDF-Werkzeugkasten öffnen."))
            if (!change(tr(title.substringBefore(" …").replace("{0}", shown))) { src, out -> op(src, sel, total, out) }) throw ActionFailed(tr("Das hat nicht geklappt"))
            ActionResult(tr("Erledigt – „Speichern“ im Werkzeugkasten schreibt es ins Original."), undo = { ActionHosts.undoOpenPdf?.invoke(); tr("Zurückgenommen.") })
        }
    }

    /** Groups of files with the same content (same size first, then the same SHA-256). */
    internal fun duplicates(files: List<File>): List<List<File>> =
        files.filter { it.length() > 0 }.groupBy { it.length() }.values.filter { it.size > 1 }.flatMap { same ->
            same.groupBy { f -> runCatching { f.inputStream().use { i -> LoraDrive.sha256(i.readBytes()) } }.getOrDefault(f.absolutePath) }.values.filter { it.size > 1 }
        }

    /** The subfolder a file goes into when tidying [by] typ, semester or monat. */
    internal fun tidyFolder(f: File, by: String): String {
        val day = java.time.Instant.ofEpochMilli(f.lastModified()).atZone(ZoneId.systemDefault()).toLocalDate()
        return when (by) {
            "semester" -> semester(day)
            "monat" -> "%04d-%02d".format(day.year, day.monthValue)
            else -> when (f.extension.lowercase()) {
                "pdf" -> "PDF"
                "doc", "docx", "odt", "rtf", "txt", "md" -> tr("Dokumente")
                "xls", "xlsx", "ods", "csv" -> tr("Tabellen")
                "ppt", "pptx", "odp" -> tr("Präsentationen")
                "png", "jpg", "jpeg", "gif", "bmp", "webp", "heic", "svg" -> tr("Bilder")
                "mp3", "m4a", "wav", "ogg", "opus", "flac", "aac" -> "Audio"
                "mp4", "mkv", "mov", "avi", "webm", "wmv", "m4v" -> "Video"
                "zip", "7z", "rar" -> tr("Archive")
                else -> tr("Sonstiges")
            }
        }
    }

    /** „WiSe 2025-26“ (October to March) or „SoSe 2026“ (April to September), as at TU Dresden. */
    internal fun semester(d: LocalDate): String = when (d.monthValue) {
        in 4..9 -> "SoSe ${d.year}"
        in 10..12 -> "WiSe ${d.year}-${(d.year + 1) % 100}"
        else -> "WiSe ${d.year - 1}-${d.year % 100}"
    }

    /** Names after the content: date and topic, asked from a quick model (or from the first line without a key). */
    private suspend fun suggestNames(c: AppContainer, files: List<File>): List<String> {
        val texts = withContext(Dispatchers.IO) {
            files.map { f -> runCatching { if (f.length() > 30_000_000) "" else io.github.thepytales.llmhub.files.FileText.read(f.name, f.readBytes()).file?.text.orEmpty() }.getOrDefault("").take(1200) }
        }
        val model = if (c.settings.current.hasAnyApiKey) io.github.thepytales.llmhub.data.Privacy.suitableModel(c, null, io.github.thepytales.llmhub.data.ModelCatalog.FAST_CHAT) else null
        val fromModel = model?.let { m ->
            runCatching {
                val prompt = buildString {
                    append("Schlage für jede Datei einen kurzen, sprechenden deutschen Dateinamen vor: Datum (JJJJ-MM-TT) falls im Text, dann Art und Thema, ")
                    append("ohne Endung, ohne Sonderzeichen außer Bindestrich und Leerzeichen, höchstens 60 Zeichen. Antworte nur mit einer Zeile je Datei, ")
                    append("in derselben Reihenfolge, Format: Nummer. Name\n\n")
                    files.forEachIndexed { i, f -> append("${i + 1}. ${f.name}\n").append(texts[i].replace('\n', ' ').take(800)).append("\n\n") }
                }
                val r = c.llm.streamChat(m, listOf(io.github.thepytales.llmhub.data.ChatMessage(role = "user", content = prompt)), null, thinking = io.github.thepytales.llmhub.net.ThinkingLevel.OFF) {}
                val lines = io.github.thepytales.llmhub.ui.splitThinking(r.content).second.lines().mapNotNull { l -> Regex("^\\s*(\\d+)[.)]\\s*(.+)$").find(l)?.let { it.groupValues[1].toInt() to it.groupValues[2].trim() } }.toMap()
                files.indices.map { lines[it + 1].orEmpty() }
            }.getOrNull()
        }
        return files.mapIndexed { i, f ->
            val base = fromModel?.getOrNull(i)?.takeIf { it.isNotBlank() }
                ?: texts[i].lines().map { it.trim().trim('#', ' ', '*') }.firstOrNull { it.length in 4..80 }
                ?: return@mapIndexed f.name
            io.github.thepytales.llmhub.files.FileText.safeName(base.removeSuffix("." + f.extension).take(70)) + if (f.extension.isNotEmpty()) ".${f.extension}" else ""
        }
    }
}
