package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.BatchCriterion
import io.github.thepytales.llmhub.data.BatchDecision
import io.github.thepytales.llmhub.data.BatchGrading
import io.github.thepytales.llmhub.data.BatchItem
import io.github.thepytales.llmhub.data.BatchJob
import io.github.thepytales.llmhub.data.BatchStore
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.ModelType
import io.github.thepytales.llmhub.docs.MarkdownDocx
import io.github.thepytales.llmhub.docs.XlsxWriter
import io.github.thepytales.llmhub.files.FileText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Ready-made Aufträge for a plain batch (the text can be changed). */
private val TASK_PRESETS = listOf(
    trLater("Zusammenfassen") to trLater("Fasse die Datei in 5–8 Sätzen zusammen: Thema, Ziel, Vorgehen, wichtigste Ergebnisse. Danach drei Stichworte."),
    trLater("Exzerpt") to trLater("Exzerpiere den Text für eine Literaturarbeit: Fragestellung, theoretischer Rahmen, Methode (Stichprobe, Design), zentrale Ergebnisse, Grenzen, Bedeutung für die Praxis – jeweils mit Seitenangabe (S. x) und 2–4 wörtlichen Kernzitaten."),
    trLater("Steckbrief") to trLater("Erstelle einen kurzen Steckbrief der Datei als Liste: Titel, Art des Dokuments, Autor:innen bzw. Absender, Datum, Inhalt in einem Satz, offene Punkte oder Fristen."),
    trLater("Übersetzen") to trLater("Übersetze den Text vollständig ins Englische. Behalte Gliederung, Überschriften und Fachbegriffe bei."),
)

/**
 * Stapel & Massenkontrolle (AP-20): the jobs with Lora's progress card („23 von 80“), a new job from files, a folder, a
 * ZIP or the Nextcloud, the results and – for the Massenkontrolle – calibration, decisions with protocol, outliers,
 * result table and feedback per person.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchScreen(c: AppContainer, onBack: () -> Unit) {
    val jobs by c.batches.jobs.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf<String?>(null) }
    val open = openId?.let { id -> jobs.firstOrNull { it.id == id } }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { if (open != null) IconButton(onClick = { openId = null }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, tr("Zurück")) } else BackButton(onBack) },
                title = { Text(open?.title ?: tr("Stapel & Massenkontrolle"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    if (open == null) TextButton(onClick = { creating = BatchJob.TASK }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.size(4.dp)); Text(tr("Neuer Stapel")) }
                },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.TopCenter) {
            when {
                open != null -> BatchDetail(c, open)
                jobs.isEmpty() -> Column(Modifier.widthIn(max = 560.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LoraFace(Modifier.size(72.dp), label = "Lora", director = rememberLoraDirector(listOf(LoraAction.FOLDER, LoraAction.STAMP, LoraAction.STAPLER), LoraAction.FOLDER))
                    Text(tr("Noch keine Stapel"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        tr("Ein Stapel bearbeitet viele Dateien mit demselben Auftrag – nacheinander im Hintergrund, mit Pause und Fortsetzen, auch nach einem Neustart. Die Massenkontrolle bewertet Abgaben nach deinem Raster vor: Die KI schlägt vor, du entscheidest."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { creating = BatchJob.TASK }) { Text(tr("Neuer Stapel")) }
                        OutlinedButton(onClick = { creating = BatchJob.GRADING }) { Text(tr("Massenkontrolle")) }
                    }
                }
                else -> LazyColumn(
                    Modifier.widthIn(max = 860.dp).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(jobs, key = { it.id }) { j -> BatchRow(c, j) { openId = j.id } }
                }
            }
        }
    }
    creating?.let { kind -> NewBatchDialog(c, kind, onDismiss = { creating = null }) { id -> creating = null; openId = id } }
}

private fun stateLabel(j: BatchJob) = when (j.state) {
    BatchJob.RUNNING -> tr("läuft")
    BatchJob.PAUSED -> "pausiert"
    BatchJob.DONE -> "fertig"
    else -> "bereit"
}

@Composable
private fun BatchRow(c: AppContainer, j: BatchJob, onOpen: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = LocalChatLook.current.card, border = boxBorder(), modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Outlined.Inventory2, size = 34.dp)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(j.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(tr(if (j.grading) "Massenkontrolle" else "Stapel"), tr(stateLabel(j)), "${j.done} " + tr("von") + " ${j.items.size}").joinToString(" · ") +
                            (if (j.failed > 0) " · ${j.failed} " + tr("mit Fehler") else "") +
                            if (j.grading) " · ${j.items.count { it.decision != null && !it.decision.calibration }} " + tr("entschieden") else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RunButton(c, j)
            }
            if (j.items.isNotEmpty()) LinearProgressIndicator(progress = { j.done.toFloat() / j.items.size }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RunButton(c: AppContainer, j: BatchJob) {
    when (j.state) {
        BatchJob.RUNNING -> OutlinedButton(onClick = { c.batchRunner.pause(j.id) }) { Icon(Icons.Outlined.Pause, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Pause")) }
        BatchJob.DONE -> if (j.failed > 0) OutlinedButton(onClick = { c.batchRunner.retryFailed(j.id) }) { Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Fehler erneut")) }
        else -> Button(enabled = j.items.any { it.status == BatchItem.PENDING }, onClick = { c.batchRunner.start(j.id) }) {
            Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp))
            Text(tr(if (j.state == BatchJob.PAUSED) "Fortsetzen" else "Starten"))
        }
    }
}

/** Lora's card while a batch works: she files folders and stamps, with „23 von 80“. */
@Composable
private fun BatchProgressCard(c: AppContainer, j: BatchJob) {
    val current by c.batchRunner.current.collectAsStateWithLifecycle()
    val waiting by c.batchRunner.waiting.collectAsStateWithLifecycle()
    val working = j.state == BatchJob.RUNNING && current?.jobId == j.id
    Surface(shape = MaterialTheme.shapes.medium, color = LocalChatLook.current.card, border = boxBorder(), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (working) LoraFace(Modifier.size(64.dp), label = "Lora", director = rememberLoraDirector(listOf(LoraAction.FOLDER, LoraAction.STAMP, LoraAction.STAPLER, LoraAction.READ), LoraAction.FOLDER))
            else LoraFace(Modifier.size(64.dp), label = "Lora", director = rememberLoraDirector(listOf(if (j.state == BatchJob.DONE) LoraAction.CHEER else LoraAction.IDLE, LoraAction.LOOK), if (j.state == BatchJob.DONE) LoraAction.CHEER else LoraAction.IDLE))
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${j.done} " + tr("von") + " ${j.items.size}" + " · " + tr(stateLabel(j)), style = MaterialTheme.typography.titleMedium)
                if (j.items.isNotEmpty()) LinearProgressIndicator(progress = { j.done.toFloat() / j.items.size }, modifier = Modifier.fillMaxWidth())
                val line = when {
                    working && waiting != null -> tr(waiting!!)
                    working -> tr("Bearbeitet gerade:") + " " + current!!.name
                    j.state == BatchJob.RUNNING -> tr("Wartet, bis der Stapel davor fertig ist.")
                    j.state == BatchJob.DONE && j.grading -> tr("Fertig – die Vorschläge warten auf deine Entscheidung.")
                    j.state == BatchJob.DONE -> tr("Fertig.")
                    j.state == BatchJob.PAUSED -> tr("Pausiert – nichts geht verloren, „Fortsetzen“ macht an derselben Stelle weiter.")
                    else -> tr("Bereit. Läuft im Hintergrund weiter, auch wenn du die App schließt (Windows: im Infobereich).")
                }
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    ModelCatalog.info(j.model).displayName + (if (j.pseudonymize) " · " + tr("pseudonymisiert") else "") + (if (j.uniOnly) " · " + tr("nur TU Dresden") else "") +
                        if (j.night) " · " + tr("nachts") else "",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BatchDetail(c: AppContainer, j: BatchJob) {
    val scope = rememberCoroutineScope()
    var item by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var onlyOpen by rememberSaveable { mutableStateOf(false) }
    val outliers = remember(j) { if (j.grading) BatchGrading.outliers(j) else emptySet() }
    val saveDocx = rememberSaveAs("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    val saveXlsx = rememberSaveAs("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    val saveZip = rememberSaveAs("application/zip")
    val toNextcloud = rememberNextcloudSave(c)
    fun out(name: String) = File(File(c.appContext.cacheDir, "stapel"), FileText.safeName(name)).apply { parentFile?.mkdirs() }
    fun export(block: () -> File, save: (File) -> Unit) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { block() } }.onSuccess(save).onFailure { Notices.error(tr("Export fehlgeschlagen:") + " " + it.message) }
        }
    }
    LazyColumn(
        Modifier.widthIn(max = 900.dp).fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { BatchProgressCard(c, j) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RunButton(c, j)
                if (j.failed > 0 && j.state != BatchJob.DONE) OutlinedButton(onClick = { c.batchRunner.retryFailed(j.id) }) { Text(tr("Fehler erneut versuchen")) }
                OutlinedButton(onClick = { adding = true }) { Icon(Icons.Outlined.UploadFile, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Dateien hinzufügen")) }
                var menu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { menu = true }) { Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Exportieren")) }
                    DropdownMenu(menu, { menu = false }) {
                        if (j.grading) {
                            DropdownMenuItem(text = { Text(tr("Ergebnistabelle (Excel)")) }, onClick = {
                                menu = false
                                export({ out("${j.title} – Ergebnisse.xlsx").also { XlsxWriter.write(it, "Ergebnisse", BatchGrading.resultTable(j)) } }, saveXlsx)
                            })
                            DropdownMenuItem(text = { Text(tr("Rückmeldungen je Person (Word, ZIP)")) }, onClick = {
                                menu = false
                                export({ feedbackZip(c, j, out("${j.title} – Rückmeldungen.zip")) }, saveZip)
                            })
                            DropdownMenuItem(text = { Text(tr("Protokoll der Entscheidungen (Word)")) }, onClick = {
                                menu = false
                                export({ out("${j.title} – Protokoll.docx").also { MarkdownDocx.write(it, "Protokoll", BatchGrading.protocol(j)) } }, saveDocx)
                            })
                        } else {
                            DropdownMenuItem(text = { Text(tr("Alle Ergebnisse (Word)")) }, onClick = {
                                menu = false
                                export({ out("${j.title}.docx").also { MarkdownDocx.write(it, j.title, BatchGrading.taskResults(j)) } }, saveDocx)
                            })
                            if (toNextcloud != null) DropdownMenuItem(text = { Text(tr("Alle Ergebnisse in Nextcloud")) }, onClick = {
                                menu = false
                                export({ out("${j.title}.docx").also { MarkdownDocx.write(it, j.title, BatchGrading.taskResults(j)) } }, toNextcloud)
                            })
                        }
                    }
                }
                TextButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Löschen")) }
            }
        }
        if (j.grading) item {
            val calibrated = j.items.count { it.decision?.calibration == true }
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("Die KI schlägt vor, du entscheidest"), style = MaterialTheme.typography.titleSmall)
                    Text(
                        tr("Prüfungsbewertung ist nach der KI-Verordnung Hochrisiko: Jeder Vorschlag braucht deine Entscheidung, die App schreibt sie ins Protokoll.") + " " +
                            if (calibrated < 2 && j.done == 0) tr("Tipp: Bewerte vor dem Start 2–3 Arbeiten von Hand („Von Hand bewerten“) – sie geben der KI den Maßstab vor.")
                            else "$calibrated " + tr("Arbeiten von Hand bewertet (Kalibrierung).") + " " + tr("Raster:") + " " + j.rubric.joinToString(" · ") { "${it.name} (${BatchGrading.fmt(it.max)})" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (outliers.isNotEmpty()) Text("${outliers.size} " + tr("Ausreißer – bitte besonders genau ansehen."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GroupLabel(tr("Dateien") + " (${j.items.size})", Modifier.weight(1f))
                FilterChip(selected = onlyOpen, onClick = { onlyOpen = !onlyOpen }, label = { Text(tr(if (j.grading) "Nur nicht entschiedene" else "Nur offene")) })
            }
        }
        val shown = if (!onlyOpen) j.items else j.items.filter { if (j.grading) it.decision == null else it.status != BatchItem.DONE }
        items(shown, key = { it.id }) { i -> ItemRow(j, i, i.id in outliers) { item = i.id } }
    }
    item?.let { id -> j.items.firstOrNull { it.id == id }?.let { i -> ItemDialog(c, j, i, onDismiss = { item = null }) } }
    if (adding) SourcesDialog(c, tr("Dateien hinzufügen"), onDismiss = { adding = false }) { sources ->
        adding = false
        c.batches.addItems(j.id, sources)
        Notices.info("${sources.size} " + tr("Dateien hinzugefügt."))
    }
    if (confirmDelete) ConfirmDialog(
        tr("Stapel löschen?"), "„${j.title}“ " + tr("mit allen Ergebnissen und Entscheidungen wird gelöscht."), tr("Löschen"),
        onConfirm = { c.batchRunner.pause(j.id); c.batches.delete(j.id) }, onDismiss = { confirmDelete = false },
    )
}

/** Writes one Word file per person into a ZIP. */
private fun feedbackZip(c: AppContainer, j: BatchJob, target: File): File {
    val tmp = File(c.appContext.cacheDir, "stapel/rueckmeldungen-${j.id}").apply { deleteRecursively(); mkdirs() }
    ZipOutputStream(target.outputStream()).use { zip ->
        val used = mutableSetOf<String>()
        for (i in j.items.filter { it.grade != null || it.decision != null }) {
            var name = FileText.safeName(i.name.substringBeforeLast('.')) + " – Rückmeldung.docx"
            var n = 2
            while (!used.add(name)) name = FileText.safeName(i.name.substringBeforeLast('.')) + " – Rückmeldung ($n).docx".also { n++ }
            val f = File(tmp, name)
            MarkdownDocx.write(f, "Rückmeldung", BatchGrading.feedback(j, i))
            zip.putNextEntry(ZipEntry(name))
            f.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
    tmp.deleteRecursively()
    return target
}

@Composable
private fun ItemRow(j: BatchJob, i: BatchItem, outlier: Boolean, onOpen: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.small, color = LocalChatLook.current.card, border = boxBorder(), modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(i.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = when {
                    i.status == BatchItem.FAILED -> tr("Fehler:") + " " + i.error.orEmpty()
                    i.status == BatchItem.RUNNING -> tr("wird bearbeitet …")
                    i.decision?.calibration == true -> tr("von Hand bewertet (Kalibrierung)")
                    i.status == BatchItem.PENDING -> tr("wartet") + (i.error?.let { " · " + tr("letzter Versuch:") + " $it" } ?: "")
                    j.grading && i.decision != null -> tr(if (i.decision.accepted) "Vorschlag übernommen" else "angepasst")
                    j.grading -> tr("Vorschlag – noch nicht entschieden")
                    else -> i.result.replace(Regex("\\s+"), " ").take(140)
                }
                Text(detail, style = MaterialTheme.typography.bodySmall, color = if (i.status == BatchItem.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (j.grading) {
                val total = BatchGrading.total(i)
                if (outlier) Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                    Text(tr("Ausreißer"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Spacer(Modifier.size(8.dp))
                Text(total?.let { BatchGrading.fmt(it) + " / " + BatchGrading.fmt(j.maxPoints) } ?: "–", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

/** One file: the result, or for the Massenkontrolle the suggestion with checked passages and the decision. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemDialog(c: AppContainer, j: BatchJob, i: BatchItem, onDismiss: () -> Unit) {
    val text = remember(i.id) { c.batches.text(j.id, i.id).orEmpty() }
    val g = i.grade
    val start = i.decision?.points ?: g?.scores?.map { it.points } ?: j.rubric.map { 0.0 }
    val points = remember(i.id, i.decision, g) { mutableStateListOf(*start.map { BatchGrading.fmt(it) }.toTypedArray()) }
    var note by remember(i.id, i.decision) { mutableStateOf(i.decision?.note.orEmpty()) }
    var showText by remember { mutableStateOf(false) }
    fun parsed(): List<Double>? = points.mapIndexed { k, p -> p.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 && it <= j.rubric[k].max } ?: return null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(i.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (i.status == BatchItem.FAILED) Text(tr("Fehler:") + " " + i.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                if (!j.grading) {
                    if (i.result.isNotBlank()) SelectionContainer { MarkdownText(i.result) } else HintText(tr("Noch kein Ergebnis."))
                } else {
                    if (g == null && i.decision?.calibration != true) HintText(
                        if (i.open) tr("Noch kein Vorschlag. Du kannst die Arbeit jetzt von Hand bewerten – dann dient sie der KI als Beispiel (Kalibrierung) und wird nicht von ihr bewertet.")
                        else tr("Kein Vorschlag erhalten.")
                    )
                    g?.summary?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    j.rubric.forEachIndexed { k, crit ->
                        val s = g?.scores?.getOrNull(k)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(crit.name, style = MaterialTheme.typography.titleSmall)
                                    if (s != null) Text(tr("Vorschlag:") + " ${BatchGrading.fmt(s.points)} / ${BatchGrading.fmt(crit.max)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                OutlinedTextField(
                                    value = points.getOrElse(k) { "" }, onValueChange = { v -> if (k < points.size) points[k] = v },
                                    singleLine = true, modifier = Modifier.width(96.dp), label = { Text("/ " + BatchGrading.fmt(crit.max)) },
                                    isError = points.getOrNull(k)?.replace(',', '.')?.toDoubleOrNull()?.let { it < 0 || it > crit.max } ?: true,
                                )
                            }
                            if (s != null && s.reason.isNotBlank()) Text(s.reason, style = MaterialTheme.typography.bodySmall)
                            s?.evidence?.forEach { q ->
                                val found = BatchGrading.quoteFound(q, text)
                                Text(
                                    (if (found) "✓ " else "✗ ") + q,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (found) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                    g?.feedback?.takeIf { it.isNotBlank() }?.let {
                        Text(tr("Rückmeldung (Vorschlag)"), style = MaterialTheme.typography.titleSmall)
                        SelectionContainer { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(tr("Begründung / Notiz (für Protokoll und Rückmeldung)")) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    if (g != null) Text("✓ = " + tr("Zitat in der Arbeit gefunden") + " · ✗ = " + tr("nicht gefunden, bitte prüfen"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { showText = !showText }) { Text(tr(if (showText) "Text ausblenden" else "Text der Datei zeigen")) }
                if (showText) SelectionContainer { Text(text.take(30_000), style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            if (j.grading) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (g != null) TextButton(onClick = {
                    c.batches.decide(j.id, i.id, BatchDecision(g.scores.map { it.points }, note, accepted = true), "Vorschlag übernommen (${BatchGrading.fmt(g.total)} Punkte)" + if (note.isNotBlank()) " – $note" else "")
                    onDismiss()
                }) { Text(tr("Vorschlag übernehmen")) }
                Button(enabled = parsed() != null, onClick = {
                    val p = parsed() ?: return@Button
                    val calibration = g == null && i.open
                    val text2 = if (calibration) "von Hand bewertet als Kalibrierung (${BatchGrading.fmt(p.sum())} Punkte)" else "entschieden: ${BatchGrading.fmt(p.sum())} Punkte" + (g?.let { " (Vorschlag ${BatchGrading.fmt(it.total)})" } ?: "")
                    c.batches.decide(j.id, i.id, BatchDecision(p, note, accepted = g != null && p == g.scores.map { it.points }, calibration = calibration), text2 + if (note.isNotBlank()) " – $note" else "")
                    onDismiss()
                }) { Text(tr(if (g == null && i.open) "Von Hand bewerten" else "Entscheidung speichern")) }
            } else TextButton(onClick = onDismiss) { Text(tr("Schließen")) }
        },
        dismissButton = {
            Row {
                if (!i.open) TextButton(onClick = { c.batchRunner.redo(j.id, i.id); onDismiss() }) { Text(tr("Neu bearbeiten lassen")) }
                if (j.grading) TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) }
            }
        },
    )
}

/**
 * Picks the files of a batch: several files, a ZIP (e.g. the submissions from OPAL), a folder on this PC or files from
 * the Nextcloud. The texts are read on the device right away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourcesPicker(c: AppContainer, sources: MutableList<BatchStore.Source>) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(0) }
    var nextcloud by remember { mutableStateOf(false) }
    val pickFiles = rememberDocumentPicker { f -> sources += BatchStore.Source(f.name, f.text, f.pages) }
    val pickZip = rememberFileImport(arrayOf("application/zip", "application/x-zip-compressed")) { input ->
        busy++
        try {
            val read = unzip(input.readBytes())
            withContext(Dispatchers.Main) { sources += read.first }
            read.second.takeIf { it > 0 }?.let { Notices.info("$it " + tr("Dateien im ZIP ließen sich nicht lesen und wurden übersprungen.")) }
        } finally {
            busy--
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = pickFiles) { Icon(Icons.Outlined.UploadFile, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Dateien")) }
        OutlinedButton(onClick = pickZip) { Text(tr("ZIP (z. B. OPAL-Abgaben)")) }
        if (FolderAccess.SUPPORTED) OutlinedButton(onClick = {
            scope.launch {
                val dir = FolderAccess.pick(tr("Ordner mit den Dateien wählen")) ?: return@launch
                busy++
                try {
                    val read = withContext(Dispatchers.IO) { readFolder(File(dir)) }
                    sources += read.first
                    if (read.second > 0) Notices.info("${read.second} " + tr("Dateien ließen sich nicht lesen und wurden übersprungen."))
                } finally {
                    busy--
                }
            }
        }) { Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Ordner")) }
        if (c.settings.current.hasNextcloud) OutlinedButton(onClick = { nextcloud = true }) { Icon(Icons.Outlined.Cloud, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Nextcloud")) }
    }
    if (busy > 0) Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.size(8.dp))
        Text(tr("Dateien werden gelesen …"), style = MaterialTheme.typography.bodySmall)
    }
    if (nextcloud) NextcloudBrowser(c, tr("Dateien aus Nextcloud"), confirm = tr("Übernehmen"), onDismiss = { nextcloud = false }, onFiles = { picked ->
        nextcloud = false
        scope.launch {
            busy++
            try {
                for (e in picked) {
                    runCatching { c.nextcloud.attach(e.path) }.getOrNull()?.file?.let { f -> sources += BatchStore.Source(e.name, f.text, f.pages) }
                        ?: Notices.error("„${e.name}“ " + tr("ließ sich nicht lesen."))
                }
            } finally {
                busy--
            }
        }
    })
}

/** The readable files of a ZIP (folders inside included, hidden files and __MACOSX left out); second: unreadable ones. */
internal suspend fun unzip(bytes: ByteArray): Pair<List<BatchStore.Source>, Int> {
    val out = mutableListOf<BatchStore.Source>()
    var failed = 0
    val entries = mutableListOf<Pair<String, ByteArray>>()
    withContext(Dispatchers.IO) {
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name.replace('\\', '/')
                if (e.isDirectory || name.startsWith("__MACOSX/") || name.substringAfterLast('/').startsWith(".") || name.contains("../")) continue
                val data = zip.readBytes()
                if (data.size > 40 * 1024 * 1024) { failed++; continue }
                entries += name to data
            }
        }
    }
    for ((name, data) in entries.sortedBy { it.first.lowercase() }) {
        // Nested ZIPs (one per person, as OPAL sometimes delivers) are opened too.
        if (name.lowercase().endsWith(".zip")) {
            val inner = unzip(data)
            out += inner.first.map { it.copy(name = name.substringAfterLast('/').substringBeforeLast('.') + "/" + it.name) }
            failed += inner.second
            continue
        }
        val r = FileText.read(name.substringAfterLast('/'), data)
        val f = r.file
        if (f != null && f.text.isNotBlank()) out += BatchStore.Source(entryName(name), f.text, f.pages) else failed++
    }
    return out to failed
}

/** „Abgaben/Müller_Anna/Hausarbeit.pdf“ → „Müller_Anna/Hausarbeit.pdf“ (the folder of the person stays visible). */
private fun entryName(name: String): String = name.split('/').filter { it.isNotEmpty() }.takeLast(2).joinToString("/")

/** The readable files of a folder (with subfolders, at most 500). */
internal suspend fun readFolder(dir: File): Pair<List<BatchStore.Source>, Int> {
    val files = dir.walkTopDown().maxDepth(4).filter { it.isFile && !it.name.startsWith(".") && !it.name.startsWith("~$") }.take(500).toList().sortedBy { it.path.lowercase() }
    val out = mutableListOf<BatchStore.Source>()
    var failed = 0
    for (f in files) {
        if (f.extension.lowercase() == "zip") {
            val inner = unzip(f.readBytes())
            out += inner.first
            failed += inner.second
            continue
        }
        val r = FileText.read(f.name, f.readBytes())
        val a = r.file
        val name = f.relativeTo(dir).path.replace('\\', '/')
        if (a != null && a.text.isNotBlank()) out += BatchStore.Source(name, a.text, a.pages) else failed++
    }
    return out to failed
}

/** Adds files to an existing batch. */
@Composable
private fun SourcesDialog(c: AppContainer, title: String, onDismiss: () -> Unit, onDone: (List<BatchStore.Source>) -> Unit) {
    val sources = remember { mutableStateListOf<BatchStore.Source>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SourcesPicker(c, sources)
                SourcesList(sources)
            }
        },
        confirmButton = { Button(enabled = sources.isNotEmpty(), onClick = { onDone(sources.toList()) }) { Text(tr("Hinzufügen")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

@Composable
private fun SourcesList(sources: MutableList<BatchStore.Source>) {
    if (sources.isEmpty()) {
        HintText(tr("Noch keine Dateien ausgewählt."))
        return
    }
    Text("${sources.size} " + tr("Dateien") + " · " + "%,d".format(sources.sumOf { it.text.length }).replace(',', '.') + " " + tr("Zeichen"), style = MaterialTheme.typography.labelLarge)
    Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
        sources.toList().forEachIndexed { n, s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { sources.removeAt(n) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Delete, tr("Entfernen"), Modifier.size(16.dp)) }
            }
        }
    }
}

/** A new batch or Massenkontrolle: files, Auftrag or raster, model and privacy. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewBatchDialog(c: AppContainer, startKind: String, onDismiss: () -> Unit, onCreated: (String) -> Unit) {
    val s = c.settings.current
    val models by c.models.state.collectAsStateWithLifecycle()
    var kind by remember { mutableStateOf(startKind) }
    var title by remember { mutableStateOf("") }
    var instruction by remember { mutableStateOf(tr(TASK_PRESETS.first().second)) }
    var hints by remember { mutableStateOf("") }
    var rubricText by remember { mutableStateOf(BatchGrading.DEFAULT_RUBRIC.joinToString("\n") { "${it.name}; ${BatchGrading.fmt(it.max)}; ${it.description}" }) }
    var model by remember { mutableStateOf(s.defaultModel) }
    var pickModel by remember { mutableStateOf(false) }
    var pseudonymize by remember { mutableStateOf(true) }
    var uniOnly by remember { mutableStateOf(true) }
    var night by remember { mutableStateOf(false) }
    var startNow by remember { mutableStateOf(true) }
    val sources = remember { mutableStateListOf<BatchStore.Source>() }
    val rubric: List<BatchCriterion> = remember(rubricText) { BatchGrading.parseRubric(rubricText) }
    val grading = kind == BatchJob.GRADING
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(if (grading) "Neue Massenkontrolle" else "Neuer Stapel")) },
        text = {
            Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !grading, onClick = { kind = BatchJob.TASK }, label = { Text(tr("Stapel (gleicher Auftrag)")) })
                    FilterChip(selected = grading, onClick = { kind = BatchJob.GRADING }, label = { Text(tr("Massenkontrolle (Raster)")) })
                }
                OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, label = { Text(tr("Titel")) },
                    placeholder = { Text(tr(if (grading) "z. B. Hausarbeiten Didaktik WiSe 26/27" else "z. B. Interviews zusammenfassen")) }, modifier = Modifier.fillMaxWidth())
                GroupLabel(tr("Dateien"))
                SourcesPicker(c, sources)
                SourcesList(sources)
                if (!grading) {
                    GroupLabel(tr("Auftrag für jede Datei"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TASK_PRESETS.forEach { (label, text) -> FilterChip(selected = instruction == tr(text), onClick = { instruction = tr(text) }, label = { Text(tr(label)) }) }
                    }
                    OutlinedTextField(value = instruction, onValueChange = { instruction = it }, minLines = 3, modifier = Modifier.fillMaxWidth())
                } else {
                    GroupLabel(tr("Bewertungsraster"))
                    OutlinedTextField(
                        value = rubricText, onValueChange = { rubricText = it }, minLines = 4, modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text(tr("Je Zeile: Kriterium; Punkte; Beschreibung – auch aus Excel oder der TU Vorlage „Bewertungsraster“ einfügen.") + " " + "${rubric.size} " + tr("Kriterien") + ", ${BatchGrading.fmt(rubric.sumOf { it.max })} " + tr("Punkte")) },
                        isError = rubric.isEmpty(),
                    )
                    OutlinedTextField(value = hints, onValueChange = { hints = it }, minLines = 2, modifier = Modifier.fillMaxWidth(), label = { Text(tr("Hinweise zur Bewertung (optional)")) },
                        placeholder = { Text(tr("z. B. Erwartungshorizont, Umfang 12–15 Seiten, Zitierweise APA 7")) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Modell"))
                        Text(ModelCatalog.info(model).displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { pickModel = true }) { Text(tr("Ändern")) }
                }
                SwitchRow(tr("Pseudonymisiert"), tr("Namen und persönliche Angaben werden vor dem Senden ersetzt."), pseudonymize) { pseudonymize = it }
                SwitchRow(tr("Nur TU Dresden"), tr("Nur der Uni-Server oder Modelle auf diesem Gerät – nie ein externer Anbieter."), uniOnly) { uniOnly = it }
                SwitchRow(tr("Nachts laufen lassen"), tr("Nur zwischen 22 und 6 Uhr; PC oder Handy bleiben dafür an (Windows: im Infobereich)."), night) { night = it }
                SwitchRow(tr("Gleich starten"), tr(if (grading) "Aus: erst 2–3 Arbeiten von Hand bewerten (Kalibrierung), dann starten." else "Aus: später unter Stapel starten."), startNow) { startNow = it }
            }
        },
        confirmButton = {
            Button(enabled = sources.isNotEmpty() && (if (grading) rubric.isNotEmpty() else instruction.isNotBlank()), onClick = {
                val job = c.batches.create(
                    BatchJob(
                        title = title.ifBlank { (if (grading) tr("Massenkontrolle ") else tr("Stapel ")) + java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY).format(java.util.Date()) },
                        kind = kind, instruction = if (grading) hints else instruction, model = model,
                        pseudonymize = pseudonymize, uniOnly = uniOnly, night = night, rubric = if (grading) rubric else emptyList(),
                    ),
                    sources.toList(),
                )
                if (startNow) c.batchRunner.start(job.id)
                onCreated(job.id)
            }) { Text(tr(if (startNow) "Anlegen und starten" else "Anlegen")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
    if (pickModel) ModelPickerSheet(
        models = models.ofType(ModelType.CHAT).let { all -> if (uniOnly) all.filter { !io.github.thepytales.llmhub.data.Privacy.isExternal(c, it.id) } else all },
        selected = model, onSelect = { model = it }, onDismiss = { pickModel = false },
        title = tr("Modell für den Stapel"),
    )
}
