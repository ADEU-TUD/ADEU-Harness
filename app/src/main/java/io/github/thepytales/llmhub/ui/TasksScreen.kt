package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.LinkedRepo
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.ModelType
import io.github.thepytales.llmhub.data.Repeat
import io.github.thepytales.llmhub.data.ScheduledTask
import io.github.thepytales.llmhub.data.TaskPresets
import io.github.thepytales.llmhub.data.thread
import io.github.thepytales.llmhub.tasks.TaskScheduler
import java.text.DateFormat
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

private fun formatTime(millis: Long?): String =
    millis?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) } ?: "–"

@Composable
internal fun StatusLine(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = if (ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Text of the last answer in [chat] without Markdown symbols, for a short preview. */
private fun resultPreview(chat: Conversation?): String? =
    chat?.thread()?.lastOrNull { it.role == "assistant" && !it.error }
        ?.let { splitThinking(it.content).second }
        ?.replace(Regex("[#*_`>|]"), "")?.replace(Regex("\\s+"), " ")?.trim()?.ifBlank { null }

/** List of scheduled tasks, e.g. a news summary every morning. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(c: AppContainer, onBack: () -> Unit, onEdit: (String) -> Unit, onOpenChat: (String) -> Unit) {
    val tasks by c.tasks.items.collectAsStateWithLifecycle()
    val chats by c.chats.conversations.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        topBar = { TopAppBar(navigationIcon = { BackButton(onBack) }, title = { Text(tr("Geplante Aufgaben")) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit("new") },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(tr("Neue Aufgabe")) },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    tr("Der Agent erledigt Aufgaben zur geplanten Zeit im Hintergrund – z.B. jeden Morgen die Nachrichten zu einem " +
                        "Thema zusammenfassen. Ist eine Aufgabe fertig, bekommst du eine Benachrichtigung; das Ergebnis liegt als Chat " +
                        "im Ordner der Aufgabe und ist im Menü mit einem Punkt markiert, bis du es geöffnet hast."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { DeliveryStatus(c) }
            if (tasks.isEmpty()) {
                item {
                    Surface(color = LocalChatLook.current.card, shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(tr("Beispiel: Morgen-News"), style = MaterialTheme.typography.titleSmall)
                            Text(
                                tr("Jeden Tag um 7:00 Uhr die wichtigsten Nachrichten zu „Künstliche Intelligenz“ mit Links zusammenfassen."),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(onClick = {
                                val t = TaskPresets.news(c.settings.current.defaultModel)
                                c.tasks.upsert(t.copy(nextRunAt = TaskScheduler.schedule(context, t)))
                                onEdit(t.id)
                            }) { Text(tr("Als Vorlage übernehmen")) }
                        }
                    }
                }
            }
            items(tasks, key = { it.id }) { t ->
                val lastChat = t.lastChatId?.let { id -> chats.firstOrNull { it.id == id } }
                val failed = t.lastStatus != null && t.lastStatus != tr("Erfolgreich")
                // The switch follows the click right away instead of waiting for the list to come back from the store
                // (on Windows it sometimes only did after leaving the screen); a new stored value takes over again.
                var enabled by remember(t.id, t.enabled) { mutableStateOf(t.enabled) }
                Surface(
                    color = LocalChatLook.current.card,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.clickable { onEdit(t.id) },
                ) {
                    Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${t.scheduleLabel()} · ${ModelCatalog.info(t.model).displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = enabled, onCheckedChange = { on ->
                                enabled = on
                                // From the stored task, not from [t]: a run that just finished must not be undone.
                                c.tasks.update(t.id) { cur ->
                                    val updated = cur.copy(enabled = on)
                                    updated.copy(nextRunAt = TaskScheduler.schedule(context, updated))
                                }
                            })
                        }
                        Text(
                            if (enabled) tr("Nächste Ausführung: {0}", formatTime(t.nextRunAt ?: t.nextRun())) else tr("Pausiert – läuft nur über „Jetzt ausführen“"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        if (t.lastRunAt != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp, end = 8.dp)) {
                                Text(
                                    (if (failed) tr("Letzter Lauf fehlgeschlagen · ") else tr("Letztes Ergebnis · ")) + formatTime(t.lastRunAt),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                if (lastChat?.unread == true) {
                                    Surface(shape = RoundedCornerShape(50), color = LocalChatLook.current.brand, contentColor = Color.White) {
                                        Text(tr("Neu"), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                    }
                                }
                            }
                            val preview = if (failed) t.lastStatus else resultPreview(lastChat)
                            preview?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp, end = 8.dp),
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            if (lastChat != null) {
                                FilledTonalButton(onClick = { onOpenChat(lastChat.id) }) {
                                    Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text(tr("Ergebnis ansehen"))
                                }
                                Spacer(Modifier.size(4.dp))
                            }
                            TextButton(onClick = {
                                TaskScheduler.runNow(context, t.id)
                                Notices.info(tr("„{0}“ läuft – du bekommst eine Benachrichtigung, sobald das Ergebnis da ist.", t.name))
                            }) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(4.dp))
                                Text(tr("Jetzt ausführen"))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Creates or edits a scheduled task. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditScreen(c: AppContainer, id: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val models by c.models.state.collectAsStateWithLifecycle()
    val environments by c.environments.items.collectAsStateWithLifecycle()
    val existing = remember(id) { c.tasks.get(id) }
    var task by remember(id) {
        mutableStateOf(existing ?: ScheduledTask(name = "", prompt = "", model = settings.defaultModel))
    }
    var showModel by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showRepo by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    val askNotifications = rememberAskNotifications()

    fun save() {
        val next = TaskScheduler.schedule(context, task)
        // A run that finished while the form was open keeps its result.
        val stored = c.tasks.get(task.id)
        val merged = if (stored == null) task else task.copy(
            lastRunAt = stored.lastRunAt, lastStatus = stored.lastStatus, lastChatId = stored.lastChatId,
            folderId = stored.folderId ?: task.folderId,
        )
        c.tasks.upsert(merged.copy(nextRunAt = next))
        if (task.notify) askNotifications()
        Notices.info(if (next != null) tr("Gespeichert – nächste Ausführung: {0}", formatTime(next)) else tr("Gespeichert (pausiert)."))
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = { Text(if (existing == null) tr("Neue Aufgabe") else tr("Aufgabe bearbeiten")) },
                actions = {
                    if (existing != null) IconButton(onClick = { showDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = tr("Löschen")) }
                    TextButton(enabled = task.name.isNotBlank() && task.prompt.isNotBlank(), onClick = { save() }) { Text(tr("Speichern")) }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = task.name, onValueChange = { task = task.copy(name = it) },
                label = { Text(tr("Name")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = task.prompt, onValueChange = { task = task.copy(prompt = it) },
                label = { Text(tr("Aufgabe für den Agenten")) },
                placeholder = { Text(tr("z.B. Fasse die wichtigsten Nachrichten zu „Raumfahrt“ der letzten 24 Stunden mit Links zusammen.")) },
                minLines = 4, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { showModel = true }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Modell: {0}", ModelCatalog.info(task.model).displayName))
            }

            Text(tr("Zeitplan"), style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = task.repeat == Repeat.DAYS,
                    onClick = { task = task.copy(repeat = Repeat.DAYS) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(tr("Uhrzeit")) }
                SegmentedButton(
                    selected = task.repeat == Repeat.INTERVAL,
                    onClick = { task = task.copy(repeat = Repeat.INTERVAL) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(tr("Intervall")) }
            }
            if (task.repeat == Repeat.DAYS) {
                OutlinedButton(onClick = { showTime = true }) { Text(tr("Um %02d:%02d Uhr").format(task.hour, task.minute)) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { d ->
                        FilterChip(
                            selected = d in task.days,
                            onClick = { task = task.copy(days = if (d in task.days) task.days - d else (task.days + d).sorted()) },
                            label = { Text(DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, Locale.GERMAN)) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { task = task.copy(days = (1..7).toList()) }) { Text(tr("Täglich")) }
                    TextButton(onClick = { task = task.copy(days = (1..5).toList()) }) { Text(tr("Mo–Fr")) }
                    TextButton(onClick = { task = task.copy(days = listOf(6, 7)) }) { Text(tr("Wochenende")) }
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 2, 3, 4, 6, 8, 12, 24).forEach { h ->
                        FilterChip(selected = task.intervalHours == h, onClick = { task = task.copy(intervalHours = h) }, label = { Text(tr("{0} Std.", h)) })
                    }
                }
            }
            Text(task.scheduleLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text(tr("Werkzeuge"), style = MaterialTheme.typography.titleSmall)
            ToggleRow(tr("Websuche & Nachrichten"), tr("Suchen, News finden und Webseiten lesen."), task.webTools) { task = task.copy(webTools = it) }
            ToggleRow(tr("Dokumente erstellen"), tr("Z.B. das Ergebnis als PDF ablegen."), task.documentTools) { task = task.copy(documentTools = it) }
            if (settings.hasGitHub) {
                ToggleRow(tr("GitHub"), task.linkedRepos.joinToString(", ").ifEmpty { tr("Kein festes Repository") }, task.github) { task = task.copy(github = it) }
                if (task.github) {
                    OutlinedButton(onClick = { showRepo = true }) {
                        Text(task.linkedRepos.let { if (it.size > 1) tr("{0} Repositorys", it.size) else it.firstOrNull() ?: tr("Repositorys wählen") })
                    }
                    ToggleRow(
                        tr("Schreibaktionen erlauben"),
                        tr("Im Hintergrund kann niemand bestätigen. Aus: Commits, PRs und Kommentare werden abgelehnt."),
                        task.allowWrites,
                    ) { task = task.copy(allowWrites = it) }
                }
            }

            Text(tr("Umgebung"), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = task.envId == null, onClick = { task = task.copy(envId = null) }, label = { Text(tr("Keine")) })
                environments.forEach { e ->
                    FilterChip(selected = task.envId == e.id, onClick = { task = task.copy(envId = e.id) }, label = { Text(e.name) })
                }
            }

            OutlinedTextField(
                value = task.systemPrompt, onValueChange = { task = task.copy(systemPrompt = it) },
                label = { Text(tr("System-Prompt (optional)")) }, minLines = 2, modifier = Modifier.fillMaxWidth(),
            )
            ToggleRow(tr("Benachrichtigung"), tr("Kurze Zusammenfassung, wenn die Aufgabe fertig ist."), task.notify) { task = task.copy(notify = it) }
            ToggleRow(tr("Aktiv"), tr("Pausierte Aufgaben laufen nur über „Jetzt ausführen“."), task.enabled) { task = task.copy(enabled = it) }
            Spacer(Modifier.size(40.dp))
        }
    }

    if (showModel) {
        ModelPickerSheet(
            models.ofType(ModelType.CHAT), task.model, onSelect = { task = task.copy(model = it) }, onDismiss = { showModel = false },
            defaultModel = settings.defaultModel,
        )
    }
    if (showRepo) {
        RepoPickerSheet(
            c = c, enabled = task.github, selected = task.linkedRepos.map { LinkedRepo(it) }, hasToken = settings.hasGitHub,
            onApply = { on, repos -> task = task.copy(github = on).withRepos(repos.map { it.name }) },
            onDismiss = { showRepo = false },
        )
    }
    if (showTime) {
        val state = rememberTimePickerState(initialHour = task.hour, initialMinute = task.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text(tr("Uhrzeit")) },
            text = { TimePicker(state = state) },
            confirmButton = { TextButton(onClick = { task = task.copy(hour = state.hour, minute = state.minute); showTime = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(tr("Abbrechen")) } },
        )
    }
    if (showDelete) {
        ConfirmDialog(tr("Aufgabe löschen?"), tr("Bisherige Ergebnis-Chats bleiben erhalten."), tr("Löschen"), onConfirm = {
            TaskScheduler.cancel(context, id)
            c.tasks.delete(id)
            onBack()
        }, onDismiss = { showDelete = false })
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
