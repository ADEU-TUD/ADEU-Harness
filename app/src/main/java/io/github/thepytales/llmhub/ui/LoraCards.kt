package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.ActionLog
import io.github.thepytales.llmhub.agent.FlowStep
import io.github.thepytales.llmhub.agent.LoraFlow
import io.github.thepytales.llmhub.agent.LoraFlows
import io.github.thepytales.llmhub.agent.LoraPrefs
import io.github.thepytales.llmhub.agent.LoraStep
import io.github.thepytales.llmhub.data.MemoryEntry
import kotlinx.coroutines.launch
import java.io.File

/** „Soll ich mir merken, dass …?“ (AP-39): Merken · Nein, then Rückgängig. */
@Composable
internal fun RememberCard(c: AppContainer, turn: LoraTurn, i: Int, step: LoraStep.Remember) {
    val state = turn.state[i]
    ResultCard(icon = Icons.Outlined.Psychology, title = step.text, subtitle = tr("Vorliebe · {0}", LoraPrefs.toolLabel(step.tool))) {
        when {
            state?.startsWith("done:") == true -> DoneRow(tr("Gemerkt – zu sehen unter Einstellungen › Lora › Gedächtnis")) {
                TextButton(onClick = { c.memory.delete(state.removePrefix("done:")); turn.state[i] = "undone" }) { Text(tr("Rückgängig")) }
            }
            state == "undone" -> HintText(tr("Wieder vergessen."))
            state == "no" -> HintText(tr("Nicht gemerkt."))
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    val e = LoraPrefs.save(c, step)
                    io.github.thepytales.llmhub.agent.ToolActions.log("Vorliebe gemerkt: ${step.text}")
                    turn.state[i] = "done:" + e.id
                }) { Text(tr("Merken")) }
                TextButton(onClick = { turn.state[i] = "no" }) { Text(tr("Nein")) }
            }
        }
    }
}

/** A tour Lora offers (AP-41). */
@Composable
internal fun TourCard(turn: LoraTurn, i: Int, step: LoraStep.Tour, go: (String) -> Unit) {
    val tour = LoraTours.byId(step.tourId) ?: return
    ResultCard(icon = Icons.Outlined.Explore, title = tr("Tour: {0}", tr(tour.title)), subtitle = tr("{0} Schritte", tour.steps.size)) {
        if (turn.state[i] == "done") DoneRow(tr("Läuft – unten im Fenster geht es weiter"))
        else FilledTonalButton(onClick = { LoraTours.start(tour, go); turn.state[i] = "done"; if (!LoraLayout.docked) LoraHelper.close() }) { Text(tr("Tour starten")) }
    }
}

private fun stepIcon(status: String): ImageVector = when (status) {
    FlowStep.DONE -> Icons.Outlined.Check
    FlowStep.FAILED -> Icons.Outlined.ErrorOutline
    FlowStep.SKIPPED -> Icons.Outlined.SkipNext
    FlowStep.CONFIRM -> Icons.Outlined.HourglassEmpty
    else -> Icons.Outlined.RadioButtonUnchecked
}

private fun minutes(sec: Int): String = if (sec < 60) tr("unter einer Minute") else tr("ca. {0} Min.", (sec + 59) / 60)

/**
 * Ein Ablauf (AP-40): the plan with steps, tools, estimated time and data protection; Los · Ändern · Abbrechen,
 * then progress with Pause/Fortsetzen, a waiting risky step with its preview, Wiederholen for a failed step and the
 * files of every step.
 */
@Composable
internal fun FlowCard(c: AppContainer, flowId: String, go: (String) -> Unit) {
    val flows by LoraFlows.flows(c).collectAsStateWithLifecycle()
    val flow = flows.firstOrNull { it.id == flowId } ?: run { HintText(tr("Dieser Ablauf ist nicht mehr da.")); return }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var editing by remember(flowId) { mutableStateOf(false) }
    var change by remember(flowId) { mutableStateOf("") }
    val subtitle = listOfNotNull(
        when (flow.state) {
            LoraFlow.DRAFT -> tr("Plan · {0} Schritte · {1}", flow.steps.count { it.enabled }, minutes(flow.estimateSec))
            LoraFlow.RUNNING -> tr("Läuft … {0} von {1}", flow.steps.count { it.status == FlowStep.DONE }, flow.steps.count { it.enabled })
            LoraFlow.PAUSED -> if (flow.steps.any { it.status == FlowStep.CONFIRM }) tr("Wartet auf dich") else tr("Pausiert")
            LoraFlow.FAILED -> tr("Ein Schritt ist fehlgeschlagen")
            LoraFlow.DONE -> tr("Fertig")
            else -> tr("Abgebrochen")
        },
        flow.note.takeIf { it.isNotBlank() && flow.state == LoraFlow.DRAFT },
    ).joinToString(" · ")
    ResultCard(icon = Icons.Outlined.AccountTree, title = tr("Ablauf: {0}", flow.wish.take(70)), subtitle = subtitle) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
            flow.steps.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                    if (editing && flow.state == LoraFlow.DRAFT) Checkbox(s.enabled, { LoraFlows.toggle(c, flow.id, i) }, modifier = Modifier.size(32.dp))
                    else if (s.status == FlowStep.RUNNING) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    else Icon(stepIcon(s.status), contentDescription = null, modifier = Modifier.size(16.dp), tint = if (s.status == FlowStep.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${i + 1}. " + s.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            color = if (!s.enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (s.status == FlowStep.RUNNING || s.status == FlowStep.CONFIRM) FontWeight.SemiBold else null,
                        )
                        if (s.result.isNotBlank() && s.status != FlowStep.PENDING) {
                            Text(s.result, style = MaterialTheme.typography.labelSmall, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                color = if (s.status == FlowStep.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (s.status == FlowStep.DONE && LoraFlows.canUndo(flow.id, i)) {
                        TextButton(onClick = { scope.launch { LoraFlows.undo(c, flow.id, i) } }) { Text(tr("Rückgängig")) }
                    }
                }
                if (s.status == FlowStep.CONFIRM) {
                    Column(Modifier.padding(start = 24.dp).semantics { contentDescription = tr("Vorschau") }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        s.preview.take(10).forEach { p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(p.getOrElse(0) { "" }, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = tr("wird zu"), modifier = Modifier.padding(horizontal = 4.dp).size(12.dp))
                                Text(p.getOrElse(1) { "" }, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            }
                        }
                        if (s.preview.size > 10) HintText(tr("… und {0} weitere", s.preview.size - 10))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(onClick = { LoraFlows.approve(c, flow.id, i) }) { Text(tr("Ausführen")) }
                            TextButton(onClick = { LoraFlows.skip(c, flow.id, i) }) { Text(tr("Überspringen")) }
                        }
                    }
                }
                if (s.status == FlowStep.FAILED) {
                    Row(Modifier.padding(start = 24.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(onClick = { LoraFlows.retry(c, flow.id, i) }) { Text(tr("Wiederholen")) }
                        TextButton(onClick = { LoraFlows.skip(c, flow.id, i) }) { Text(tr("Überspringen")) }
                    }
                }
                if (s.status == FlowStep.DONE) s.files.map(::File).filter { it.isFile }.take(4).forEach { f ->
                    Text(
                        "↳ " + f.name, style = MaterialTheme.typography.labelSmall, color = LocalChatLook.current.brand, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 24.dp).clickable(onClickLabel = tr("Öffnen")) {
                            if (f.extension.equals("pdf", true)) openActionFile(f, go) else runCatching { openPrimary(context, f) }
                        },
                    )
                }
            }
            if (editing && flow.state == LoraFlow.DRAFT) {
                OutlinedTextField(
                    change, { change = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(tr("Was soll anders sein? (optional)")) }, maxLines = 3,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                when (flow.state) {
                    LoraFlow.DRAFT -> {
                        FilledTonalButton(enabled = flow.steps.any { it.enabled }, onClick = {
                            if (editing && change.isNotBlank()) {
                                LoraFlows.cancel(c, flow.id)
                                LoraHost.send(tr("Plane den Ablauf neu: {0} – Änderung: {1}", flow.wish, change.trim()))
                            } else LoraFlows.start(c, flow.id)
                            editing = false
                        }) { Text(tr(if (editing && change.isNotBlank()) "Neu planen" else "Los")) }
                        if (!editing) TextButton(onClick = { editing = true }) { Text(tr("Ändern")) }
                        TextButton(onClick = { LoraFlows.cancel(c, flow.id) }) { Text(tr("Abbrechen")) }
                    }
                    LoraFlow.RUNNING -> TextButton(onClick = { LoraFlows.pause(c, flow.id) }) { Text(tr("Pause")) }
                    LoraFlow.PAUSED, LoraFlow.FAILED -> {
                        if (flow.steps.none { it.status == FlowStep.CONFIRM || it.status == FlowStep.FAILED }) FilledTonalButton(onClick = { LoraFlows.start(c, flow.id) }) { Text(tr("Fortsetzen")) }
                        TextButton(onClick = { LoraFlows.cancel(c, flow.id) }) { Text(tr("Abbrechen")) }
                    }
                    LoraFlow.DONE -> HintText(tr("Die Ergebnisse liegen in Erstellt bzw. neben den Dateien."))
                    else -> Unit
                }
            }
        }
    }
}

/** Abläufe that run or wait without a turn of this conversation (e.g. after a restart), shown at the top of the panel. */
@Composable
internal fun OpenFlows(c: AppContainer, shown: Set<String>, go: (String) -> Unit) {
    val flows by LoraFlows.flows(c).collectAsStateWithLifecycle()
    val open = flows.filter { it.active && it.id !in shown }
    if (open.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("Laufende Abläufe"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        open.forEach { FlowCard(c, it.id, go) }
    }
}

/**
 * Einstellungen › Lora › Gedächtnis (AP-39): „Was Lora über dich weiß“ – preferences grouped by tool, each changeable
 * and deletable, „Alles vergessen“; the protocol of Lora's actions (AP-38); tours (AP-41).
 */
@Composable
internal fun LoraMemorySection(c: AppContainer) {
    val all by c.memory.items.collectAsStateWithLifecycle()
    val prefs = all.filter { io.github.thepytales.llmhub.data.MemoryCategory.of(it.category) == io.github.thepytales.llmhub.data.MemoryCategory.PREFERENCES }
    var editing by remember { mutableStateOf<MemoryEntry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var forget by remember { mutableStateOf(false) }
    Section(tr("Gedächtnis – was Lora über dich weiß")) {
        Spot("lora.memory") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HintText(tr("Vorlieben je Werkzeug, z. B. „Hochgeladenes nach Semester ordnen“ oder „Videos für OPAL in 720p“. Lora fragt immer, bevor sie sich etwas merkt, und nutzt es danach als Standard. Aus dem Tresor merkt sie sich nichts."))
                if (prefs.isEmpty()) HintText(tr("Noch keine Vorlieben. Sag Lora zum Beispiel: „Merk dir, dass ich Handouts zweispaltig möchte.“"))
                LoraPrefs.TOOLS.forEach { (key, label) ->
                    val inTool = prefs.filter { it.tool.orEmpty() == key }.sortedBy { it.createdAt }
                    if (inTool.isEmpty()) return@forEach
                    Text(tr(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                    inTool.forEach { e ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClickLabel = tr("Ändern")) { editing = e }) {
                            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                                Text(e.text)
                                e.value?.let { Text(tr("Standard für Lora: {0}", it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            IconButton(onClick = { c.memory.delete(e.id); Notices.undo(tr("Vergessen: {0}", e.text.take(40)), restore = { c.memory.upsert(e) }) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = tr("Löschen"))
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { adding = true }) { Text(tr("Vorliebe hinzufügen")) }
                    if (prefs.isNotEmpty()) TextButton(onClick = { forget = true }) { Text(tr("Alles vergessen")) }
                }
            }
        }
    }
    if (forget) AlertDialog(
        onDismissRequest = { forget = false },
        title = { Text(tr("Alles vergessen?")) },
        text = { Text(tr("Lora vergisst alle {0} Vorlieben. Die übrigen Einträge unter Einstellungen › Gedächtnis bleiben.", prefs.size)) },
        confirmButton = {
            TextButton(onClick = {
                val gone = LoraPrefs.forgetAll(c)
                forget = false
                Notices.undo(tr("{0} Vorlieben vergessen", gone.size), restore = { gone.forEach { c.memory.upsert(it) } })
            }) { Text(tr("Vergessen")) }
        },
        dismissButton = { TextButton(onClick = { forget = false }) { Text(tr("Abbrechen")) } },
    )
    editing?.let { e -> PrefDialog(c, e) { editing = null } }
    if (adding) PrefDialog(c, null) { adding = false }

    Section(tr("Touren")) {
        val s by c.settings.settings.collectAsStateWithLifecycle()
        SwitchRow(tr("Touren anbieten"), tr("Beim ersten Öffnen eines Werkzeugs bietet Lora eine kurze Tour an (Leuchtrahmen und eine Zeile Text)."), s.loraTours, spot = "lora.tours") { v ->
            c.settings.update { it.copy(loraTours = v) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LoraTours.available(IS_ANDROID).forEach { t -> TextButton(onClick = { LoraTours.start(t, LoraHost.go) }) { Text(tr(t.title)) } }
        }
        if (s.loraToursSeen.isNotBlank()) TextButton(onClick = { c.settings.update { it.copy(loraToursSeen = "") } }) { Text(tr("Touren wieder anbieten")) }
    }

    // Alles rückgängig seit heute Morgen (AP-77): also reachable by asking Lora („Mach alles von heute rückgängig“).
    Section(tr("Heute erledigt")) { DayUndoCard() }

    Section(tr("Protokoll der Lora-Aktionen")) {
        var tick by remember { mutableStateOf(0) }
        val lines = remember(tick) { ActionLog.recent(30) }
        Spot("lora.log") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                HintText(tr("Was Lora in den Werkzeugen geplant, ausgeführt und zurückgenommen hat – auf diesem Gerät gespeichert."))
                if (lines.isEmpty()) HintText(tr("Noch keine Aktionen."))
                lines.forEach { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                if (lines.isNotEmpty()) Row {
                    TextButton(onClick = { tick++ }) { Text(tr("Aktualisieren")) }
                    TextButton(onClick = { ActionLog.clear(); tick++ }) { Text(tr("Protokoll leeren")) }
                }
            }
        }
    }
}

/** Change or add a preference: text and tool. */
@Composable
private fun PrefDialog(c: AppContainer, entry: MemoryEntry?, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(entry?.text.orEmpty()) }
    var tool by remember { mutableStateOf(entry?.tool.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(if (entry == null) "Vorliebe hinzufügen" else "Vorliebe ändern")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text(tr("Was soll Lora wissen?")) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                DropdownField(
                    label = tr("Werkzeug"),
                    value = LoraPrefs.toolLabel(tool),
                    options = LoraPrefs.TOOLS.map { tr(it.second) },
                    onSelect = { v -> tool = LoraPrefs.TOOLS.firstOrNull { tr(it.second) == v }?.first.orEmpty() },
                )
                if (entry?.pref != null) HintText(tr("Ändert sich der Text, bleibt der Standardwert für Lora „{0}“ – zum Ändern löschen und neu wählen.", entry.value.orEmpty()))
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = {
                if (entry != null) c.memory.update(entry.id) { it.copy(text = text.trim(), tool = tool.ifBlank { null }) }
                else c.memory.upsert(MemoryEntry(text = text.trim(), category = io.github.thepytales.llmhub.data.MemoryCategory.PREFERENCES.name, source = "user", tool = tool.ifBlank { null }))
                onDismiss()
            }) { Text(tr("Speichern")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}
