package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.MailSortRun
import io.github.thepytales.llmhub.agent.MailSorter
import io.github.thepytales.llmhub.agent.SortItem
import io.github.thepytales.llmhub.data.MailRule
import io.github.thepytales.llmhub.data.Outlook
import io.github.thepytales.llmhub.tasks.TaskScheduler
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/*
 * Lora sortiert Mails (AP-69 b–d): the card of a sort run in Lora's panel – progress with Pause/Abbrechen while the model
 * on this device reads, then one preview for the whole order (folder → count, each folder and each mail to open, mails
 * moved to another folder or left out, uncertain ones apart) and one click „Einordnen“; afterwards Rückgängig for the
 * whole run, the offer to remember corrections, fixed rules and a scheduled repeat. The new mail view can show the same
 * card ([MailSortCard], [OpenMailSorts]).
 */

/** Groups of a preview that show this many mails before „Alle zeigen“. */
private const val SHOWN = 25

/** The card of the sort run [runId]. */
@Composable
fun MailSortCard(c: AppContainer, runId: String) {
    val runs by MailSorter.runs(c).collectAsStateWithLifecycle()
    val run = runs.firstOrNull { it.id == runId } ?: run { HintText(tr("Dieser Sortierlauf ist nicht mehr da.")); return }
    val title = if (run.targets.isEmpty()) tr("Mails einordnen") else tr("Mails einordnen: {0}", run.targets.joinToString(", ") { it.path })
    val subtitle = listOfNotNull(
        run.sourcePath.ifBlank { run.spec.source.ifBlank { tr("Posteingang") } },
        run.spec.rangeLabel.takeIf { it.isNotBlank() },
        tr("läuft auf diesem Gerät") + (run.model.takeIf { it.isNotBlank() }?.let { " (" + MailSorter.modelName(it) + ")" } ?: ""),
    ).joinToString(" · ")
    ResultCard(icon = Icons.Outlined.DriveFileMove, title = title, subtitle = subtitle) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp).fillMaxWidth()) {
            when (run.state) {
                MailSortRun.PROPOSING -> {
                    Working(tr("Lora liest die Betreffzeilen und überlegt passende Ordner …"), null)
                    PauseCancel(c, run)
                }
                MailSortRun.FOLDERS -> FolderEditor(c, run)
                MailSortRun.CLASSIFYING -> {
                    Working(
                        if (!run.loaded) tr("Lora holt die Mails aus dem Postfach …") else tr("{0} von {1} Mails gelesen", run.classifiedCount, run.items.size),
                        if (run.items.isEmpty()) null else run.classifiedCount.toFloat() / run.items.size,
                    )
                    PauseCancel(c, run)
                }
                MailSortRun.REVIEW -> Preview(c, run)
                MailSortRun.MOVING -> {
                    val total = run.items.count { it.moved } + run.toMove.size
                    Working(tr("{0} von {1} Mails eingeordnet", run.movedCount, total), if (total == 0) null else run.movedCount.toFloat() / total)
                    PauseCancel(c, run)
                }
                MailSortRun.UNDOING -> {
                    val total = run.items.count { it.movedTo != null }
                    Working(tr("Wird zurückgeschoben …"), if (total == 0) null else run.items.count { it.back }.toFloat() / total)
                }
                MailSortRun.PAUSED -> {
                    HintText(tr("Pausiert") + " · " + MailSorter.summary(run.copy(state = run.resumeState ?: MailSortRun.CLASSIFYING)))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(onClick = { MailSorter.proceed(c, run.id) }) { Text(tr("Fortsetzen")) }
                        TextButton(onClick = { MailSorter.cancel(c, run.id) }) { Text(tr("Abbrechen")) }
                    }
                }
                MailSortRun.DONE -> Done(c, run)
                MailSortRun.UNDONE -> DoneRow(MailSorter.summary(run))
                MailSortRun.CANCELLED -> {
                    HintText(MailSorter.summary(run))
                    if (run.canUndo) TextButton(onClick = { MailSorter.undo(c, run.id) }) { Text(tr("Rückgängig")) }
                }
                else -> {
                    Text(run.message.ifBlank { tr("Das hat nicht geklappt.") }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (run.resumeState != null) FilledTonalButton(onClick = { MailSorter.proceed(c, run.id) }) { Text(tr("Erneut versuchen")) }
                        if (run.canUndo) TextButton(onClick = { MailSorter.undo(c, run.id) }) { Text(tr("Rückgängig")) }
                        TextButton(onClick = { MailSorter.cancel(c, run.id) }) { Text(tr("Abbrechen")) }
                    }
                }
            }
        }
    }
}

/** Sort runs that wait or work without a card in Lora's conversation (after a restart, from a scheduled task). */
@Composable
fun OpenMailSorts(c: AppContainer, shown: Set<String> = emptySet()) {
    val runs by MailSorter.runs(c).collectAsStateWithLifecycle()
    val day = System.currentTimeMillis() - 24L * 3600 * 1000
    val open = runs.filter { it.id !in shown && (it.open || (it.taskId != null && it.state == MailSortRun.DONE && it.updatedAt > day)) }
    if (open.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("Mails einordnen"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        open.sortedByDescending { it.updatedAt }.forEach { MailSortCard(c, it.id) }
    }
}

@Composable
private fun Working(text: String, fraction: Float?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        HintText(text)
    }
    if (fraction != null) LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun PauseCancel(c: AppContainer, run: MailSortRun) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = { MailSorter.pause(c, run.id) }) { Text(tr("Pause")) }
        TextButton(onClick = { MailSorter.cancel(c, run.id) }) { Text(tr("Abbrechen")) }
    }
}

/** Lora's suggested folders: take some out, add others, then reading starts. */
@Composable
private fun FolderEditor(c: AppContainer, run: MailSortRun) {
    val paths = remember(run.id) { mutableStateListOf<String>().apply { addAll(run.targets.map { it.path }) } }
    var extra by remember(run.id) { mutableStateOf("") }
    HintText(tr("Diese Ordner schlage ich vor – fehlende lege ich in Outlook an. Passt das?"))
    paths.toList().forEach { p ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = { paths.remove(p) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Close, contentDescription = tr("Entfernen"), modifier = Modifier.size(16.dp)) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(extra, { extra = it }, placeholder = { Text(tr("Weiterer Ordner (Unterordner mit /)")) }, singleLine = true, modifier = Modifier.weight(1f))
        IconButton(enabled = extra.isNotBlank(), onClick = { paths += extra.trim(); extra = "" }) { Icon(Icons.Outlined.Add, contentDescription = tr("Hinzufügen")) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilledTonalButton(enabled = paths.isNotEmpty(), onClick = { MailSorter.acceptFolders(c, run.id, paths.toList()) }) { Text(tr("Mit diesen Ordnern einordnen")) }
        TextButton(onClick = { MailSorter.cancel(c, run.id) }) { Text(tr("Abbrechen")) }
    }
}

/** The preview for the whole order: one click „Einordnen“ instead of a question per mail. */
@Composable
private fun Preview(c: AppContainer, run: MailSortRun) {
    val examples by MailSorter.examples(c).collectAsStateWithLifecycle()
    val source = run.sourcePath.ifBlank { tr("Posteingang") }
    if (run.items.isEmpty()) {
        HintText(tr("Im Zeitraum gibt es keine Mails."))
        TextButton(onClick = { MailSorter.cancel(c, run.id) }) { Text(tr("Schließen")) }
        return
    }
    HintText(tr("Vorschau – noch ist nichts verschoben. Ordner und Mails lassen sich aufklappen, einzelne Mails umhängen oder auslassen."))
    run.targets.forEachIndexed { i, t ->
        val list = run.items.filter { it.classified && !it.skip && it.target == i }
        Group(
            key = "${run.id}/$i", label = t.path, note = if (t.folderId == null) tr("neu") else null, items = list, run = run, c = c,
        )
    }
    val unsure = run.items.filter { it.skip && it.uncertain && !it.changed }
    if (unsure.isNotEmpty()) Group(key = "${run.id}/unsure", label = tr("Unsicher – bleibt in „{0}“", source), note = null, items = unsure, run = run, c = c, muted = true)
    val left = run.items.filter { it.skip && !(it.uncertain && !it.changed) }
    if (left.isNotEmpty()) Group(key = "${run.id}/left", label = tr("Ausgelassen – bleibt in „{0}“", source), note = null, items = left, run = run, c = c, muted = true)
    if (run.more) HintText(tr("Es waren mehr als {0} Mails – die neuesten {0} sind dabei; den Rest danach mit einem neuen Auftrag.", MailSorter.MAX_MAILS))
    if (examples.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
        HintText(tr("Lora nutzt {0} gemerkte Beispiele (nur auf diesem Gerät).", examples.size), Modifier.weight(1f))
        TextButton(onClick = { MailSorter.forgetExamples(c) }) { Text(tr("Vergessen")) }
    }
    val n = run.toMove.size
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilledTonalButton(enabled = n > 0, onClick = { MailSorter.confirm(c, run.id) }) { Text(tr("Einordnen ({0} Mails)", n)) }
        TextButton(onClick = { MailSorter.cancel(c, run.id) }) { Text(tr("Abbrechen")) }
    }
}

/** A folder of the preview: name, „neu“, count; open it to see (and change) its mails. */
@Composable
private fun Group(key: String, label: String, note: String?, items: List<SortItem>, run: MailSortRun, c: AppContainer, muted: Boolean = false) {
    var open by remember(key) { mutableStateOf(false) }
    var all by remember(key) { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(enabled = items.isNotEmpty(), onClickLabel = tr(if (open) "Zuklappen" else "Aufklappen")) { open = !open }.padding(vertical = 2.dp),
    ) {
        Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(
            label, style = MaterialTheme.typography.bodySmall, fontWeight = if (muted) null else FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f, fill = false),
        )
        note?.let { Text(" · $it", style = MaterialTheme.typography.labelSmall, color = LocalChatLook.current.brand) }
        Spacer(Modifier.weight(1f))
        Text(items.size.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
    if (open) Column(Modifier.padding(start = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        (if (all) items else items.take(SHOWN)).forEach { MailRow(c, run, it) }
        if (!all && items.size > SHOWN) TextButton(onClick = { all = true }) { Text(tr("Alle {0} zeigen", items.size)) }
    }
}

/** One mail of the preview: subject and sender; open for why and how sure, „Ändern“ for another folder or leaving it out. */
@Composable
private fun MailRow(c: AppContainer, run: MailSortRun, m: SortItem) {
    var details by remember(run.id + m.id) { mutableStateOf(false) }
    var menu by remember(run.id + m.id) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).clickable(onClickLabel = tr("Details")) { details = !details }.semantics(mergeDescendants = true) {}) {
            Text(m.subject.ifBlank { tr("(ohne Betreff)") }, style = MaterialTheme.typography.bodySmall, maxLines = if (details) 3 else 1, overflow = TextOverflow.Ellipsis)
            Text(
                m.from.ifBlank { m.address } + if (m.changed) " · " + tr("von dir zugeordnet") else "",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (details) {
                val line = listOfNotNull(
                    m.address.takeIf { it.isNotBlank() && !m.from.contains(it) },
                    m.received.takeIf { it > 0 }?.let { Outlook.show(it) },
                    m.reason.takeIf { it.isNotBlank() },
                    if (m.suggested >= 0) tr("Sicherheit {0} %", m.confidence) else tr("kein Ordner passte"),
                ).joinToString(" · ")
                Text(line, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box {
            TextButton(onClick = { menu = true }) { Text(tr("Ändern")) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                run.targets.forEachIndexed { i, t ->
                    if (i != m.target || m.skip) DropdownMenuItem(text = { Text(tr("Nach „{0}“", t.path)) }, onClick = { MailSorter.setTarget(c, run.id, m.id, i); menu = false })
                }
                if (!m.skip) DropdownMenuItem(
                    text = { Text(tr("Auslassen (bleibt in „{0}“)", run.sourcePath.ifBlank { tr("Posteingang") })) },
                    onClick = { MailSorter.setSkip(c, run.id, m.id, true); menu = false },
                )
            }
        }
    }
}

/** After „Einordnen“: the result with Rückgängig, then the offers of Stufe d (examples, rules, repeat). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Done(c: AppContainer, run: MailSortRun) {
    var rules by remember(run.id) { mutableStateOf(false) }
    var schedule by remember(run.id) { mutableStateOf(false) }
    DoneRow(MailSorter.summary(run)) {
        if (run.canUndo) TextButton(onClick = { MailSorter.undo(c, run.id) }) { Text(tr("Rückgängig")) }
    }
    if (run.items.isEmpty()) return
    val created = run.targets.filter { it.created }
    if (created.isNotEmpty()) HintText(tr("Neu in Outlook: {0}", created.joinToString(", ") { it.folderPath.ifBlank { it.path } }))
    val corrections = MailSorter.corrections(run)
    if (!run.examplesAsked && corrections.isNotEmpty()) {
        HintText(tr("Soll ich mir deine {0} Korrekturen als Beispiele für das nächste Einordnen merken? Sie bleiben auf diesem Gerät.", corrections.size))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalButton(onClick = { MailSorter.rememberCorrections(c, run.id, true) }) { Text(tr("Merken")) }
            TextButton(onClick = { MailSorter.rememberCorrections(c, run.id, false) }) { Text(tr("Nein")) }
        }
    }
    val suggestions = remember(run.id, run.state) { MailSorter.ruleSuggestions(c, run) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (suggestions.isNotEmpty()) TextButton(onClick = { rules = true }) { Text(tr("Feste Regeln daraus …")) }
        if (run.taskId == null) TextButton(onClick = { schedule = true }) { Text(tr(if (run.spec.repeatDays.isNotEmpty()) "Als geplante Aufgabe speichern …" else "Regelmäßig wiederholen …")) }
    }
    if (rules) RulesDialog(c, suggestions) { rules = false }
    if (schedule) ScheduleDialog(c, run) { schedule = false }
}

/** Fixed rules from the run (Absender → Ordner), each chosen by the person. */
@Composable
private fun RulesDialog(c: AppContainer, suggestions: List<MailRule>, onDismiss: () -> Unit) {
    val chosen = remember { mutableStateListOf<String>().apply { addAll(suggestions.map { it.id }) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Feste Regeln speichern")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HintText(tr("Diese Absender kamen nur in einen Ordner. Als Regel sortieren sie künftig ohne Modell – angewendet nur, wenn du es willst (Mail & Kalender › Regeln)."))
                suggestions.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { if (r.id in chosen) chosen.remove(r.id) else chosen += r.id }) {
                        Checkbox(r.id in chosen, { on -> if (on) chosen += r.id else chosen.remove(r.id) })
                        Text(tr("Absender enthält „{0}“ → {1}", r.from, r.folderName), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = chosen.isNotEmpty(), onClick = {
                MailSorter.saveRules(c, suggestions.filter { it.id in chosen })
                Notices.info(tr("{0} Regeln gespeichert.", chosen.size))
                onDismiss()
            }) { Text(tr("Speichern")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

/** The order as a scheduled task („jeden Montag die letzte Woche einordnen“). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleDialog(c: AppContainer, run: MailSortRun, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val days = remember { mutableStateListOf<Int>().apply { addAll(run.spec.repeatDays.ifEmpty { listOf(1) }) } }
    var hour by remember { mutableStateOf(8) }
    var noAsk by remember { mutableStateOf(run.spec.noAsk) }
    val locale = if (I18n.language == "en") Locale.ENGLISH else Locale.GERMAN
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Regelmäßig einordnen")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HintText(tr("Lora ordnet dann die Mails seit dem letzten Mal in dieselben Ordner ein – mit dem Modell auf diesem Gerät, während die App läuft (Geplante Aufgaben)."))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..7).forEach { d ->
                        FilterChip(d in days, { if (d in days) days.remove(d) else days += d }, label = { Text(DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, locale)) })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(7, 8, 9, 12, 17).forEach { h -> FilterChip(hour == h, { hour = h }, label = { Text("%02d:00".format(h)) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Ohne Rückfrage"), style = MaterialTheme.typography.bodyMedium)
                        HintText(tr(if (noAsk) "Ordnet gleich ein; Unsichere bleiben liegen. Rückgängig im Lora-Panel." else "Die Vorschau wartet im Lora-Panel auf „Einordnen“."))
                    }
                    Switch(noAsk, { noAsk = it })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = days.isNotEmpty(), onClick = {
                val t = MailSorter.scheduledTask(run, days.toList(), hour, noAsk)
                c.tasks.upsert(t.copy(nextRunAt = TaskScheduler.schedule(context, t)))
                Notices.info(tr("Gespeichert unter Geplante Aufgaben: {0}", t.scheduleLabel()))
                onDismiss()
            }) { Text(tr("Speichern")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}
