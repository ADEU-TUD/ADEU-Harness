package io.github.thepytales.llmhub.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.ModelType
import io.github.thepytales.llmhub.data.Repeat
import io.github.thepytales.llmhub.data.ScheduledTask
import io.github.thepytales.llmhub.data.TaskPresets
import io.github.thepytales.llmhub.tasks.Notifier
import io.github.thepytales.llmhub.tasks.TaskScheduler
import java.text.DateFormat
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

private fun formatTime(millis: Long?): String =
    millis?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) } ?: "–"

/** List of scheduled tasks, e.g. a news summary every morning. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(c: AppContainer, onBack: () -> Unit, onEdit: (String) -> Unit, onOpenChat: (String) -> Unit) {
    val tasks by c.tasks.items.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        topBar = { TopAppBar(navigationIcon = { BackButton(onBack) }, title = { Text("Geplante Aufgaben") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit("new") },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Neue Aufgabe") },
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
                    "Der Agent führt Aufgaben zur geplanten Zeit im Hintergrund aus – z.B. jeden Morgen die Nachrichten zu einem " +
                        "Thema zusammenfassen. Das Ergebnis landet als Chat im Ordner der Aufgabe, auf Wunsch mit Benachrichtigung. " +
                        "Android kann die Ausführung im Energiesparmodus etwas verzögern.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tasks.isEmpty()) {
                item {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Beispiel: Morgen-News", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Jeden Tag um 7:00 Uhr die wichtigsten Nachrichten zu „Künstliche Intelligenz“ mit Links zusammenfassen.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(onClick = {
                                val t = TaskPresets.news(c.settings.current.defaultModel)
                                c.tasks.upsert(t.copy(nextRunAt = TaskScheduler.schedule(context, t)))
                                onEdit(t.id)
                            }) { Text("Als Vorlage übernehmen") }
                        }
                    }
                }
            }
            items(tasks, key = { it.id }) { t ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.clickable { onEdit(t.id) },
                ) {
                    Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${t.scheduleLabel()} · ${ModelCatalog.info(t.model).displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = t.enabled, onCheckedChange = { on ->
                                val updated = t.copy(enabled = on)
                                val next = TaskScheduler.schedule(context, updated)
                                c.tasks.upsert(updated.copy(nextRunAt = next))
                            })
                        }
                        Text(t.prompt, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        Text(
                            buildString {
                                if (t.enabled) append("Nächste Ausführung: ${formatTime(t.nextRunAt)}")
                                else append("Pausiert")
                                if (t.lastRunAt != null) append("\nZuletzt: ${formatTime(t.lastRunAt)} – ${t.lastStatus ?: ""}")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Row {
                            TextButton(onClick = {
                                TaskScheduler.runNow(context, t.id)
                                Toast.makeText(context, "„${t.name}“ wird jetzt ausgeführt – das Ergebnis erscheint im Ordner.", Toast.LENGTH_LONG).show()
                            }) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Jetzt ausführen")
                            }
                            t.lastChatId?.takeIf { id -> c.chats.get(id) != null }?.let { id ->
                                TextButton(onClick = { onOpenChat(id) }) {
                                    Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text("Letztes Ergebnis")
                                }
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

    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, "Ohne Berechtigung gibt es keine Benachrichtigungen – die Ergebnisse landen trotzdem im Ordner.", Toast.LENGTH_LONG).show()
    }

    fun save() {
        val next = TaskScheduler.schedule(context, task)
        c.tasks.upsert(task.copy(nextRunAt = next))
        if (task.notify && Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(context)) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Toast.makeText(
            context,
            if (next != null) "Gespeichert – nächste Ausführung: ${formatTime(next)}" else "Gespeichert (pausiert).",
            Toast.LENGTH_LONG,
        ).show()
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = { Text(if (existing == null) "Neue Aufgabe" else "Aufgabe bearbeiten") },
                actions = {
                    if (existing != null) IconButton(onClick = { showDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Löschen") }
                    TextButton(enabled = task.name.isNotBlank() && task.prompt.isNotBlank(), onClick = { save() }) { Text("Speichern") }
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
                label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = task.prompt, onValueChange = { task = task.copy(prompt = it) },
                label = { Text("Aufgabe für den Agenten") },
                placeholder = { Text("z.B. Fasse die wichtigsten Nachrichten zu „Raumfahrt“ der letzten 24 Stunden mit Links zusammen.") },
                minLines = 4, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { showModel = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Modell: ${ModelCatalog.info(task.model).displayName}")
            }

            Text("Zeitplan", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = task.repeat == Repeat.DAYS,
                    onClick = { task = task.copy(repeat = Repeat.DAYS) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Uhrzeit") }
                SegmentedButton(
                    selected = task.repeat == Repeat.INTERVAL,
                    onClick = { task = task.copy(repeat = Repeat.INTERVAL) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Intervall") }
            }
            if (task.repeat == Repeat.DAYS) {
                OutlinedButton(onClick = { showTime = true }) { Text("Um %02d:%02d Uhr".format(task.hour, task.minute)) }
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
                    TextButton(onClick = { task = task.copy(days = (1..7).toList()) }) { Text("Täglich") }
                    TextButton(onClick = { task = task.copy(days = (1..5).toList()) }) { Text("Mo–Fr") }
                    TextButton(onClick = { task = task.copy(days = listOf(6, 7)) }) { Text("Wochenende") }
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 2, 3, 4, 6, 8, 12, 24).forEach { h ->
                        FilterChip(selected = task.intervalHours == h, onClick = { task = task.copy(intervalHours = h) }, label = { Text("$h Std.") })
                    }
                }
            }
            Text(task.scheduleLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("Werkzeuge", style = MaterialTheme.typography.titleSmall)
            ToggleRow("Websuche & Nachrichten", "Suchen, News finden und Webseiten lesen.", task.webTools) { task = task.copy(webTools = it) }
            ToggleRow("Dokumente erstellen", "Z.B. das Ergebnis als PDF ablegen.", task.documentTools) { task = task.copy(documentTools = it) }
            ToggleRow(
                "GitHub",
                if (settings.hasGitHub) (task.repo ?: "Kein festes Repository") else "Erst ein GitHub-Token in den Einstellungen eintragen.",
                task.github,
            ) { on -> task = task.copy(github = on && settings.hasGitHub) }
            if (task.github) {
                OutlinedButton(onClick = { showRepo = true }) { Text(task.repo ?: "Repository wählen") }
                ToggleRow(
                    "Schreibaktionen erlauben",
                    "Im Hintergrund kann niemand bestätigen. Aus: Commits, PRs und Kommentare werden abgelehnt.",
                    task.allowWrites,
                ) { task = task.copy(allowWrites = it) }
            }

            Text("Umgebung", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = task.envId == null, onClick = { task = task.copy(envId = null) }, label = { Text("Keine") })
                environments.forEach { e ->
                    FilterChip(selected = task.envId == e.id, onClick = { task = task.copy(envId = e.id) }, label = { Text(e.name) })
                }
            }

            OutlinedTextField(
                value = task.systemPrompt, onValueChange = { task = task.copy(systemPrompt = it) },
                label = { Text("System-Prompt (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth(),
            )
            ToggleRow("Benachrichtigung", "Kurze Zusammenfassung, wenn die Aufgabe fertig ist.", task.notify) { task = task.copy(notify = it) }
            ToggleRow("Aktiv", "Pausierte Aufgaben laufen nur über „Jetzt ausführen“.", task.enabled) { task = task.copy(enabled = it) }
            Spacer(Modifier.size(40.dp))
        }
    }

    if (showModel) {
        ModelPickerSheet(models.ofType(ModelType.CHAT), task.model, onSelect = { task = task.copy(model = it) }, onDismiss = { showModel = false })
    }
    if (showRepo) {
        RepoPickerDialog(
            c = c, enabled = true, selectedRepo = task.repo, selectedBranch = null, hasToken = settings.hasGitHub,
            onApply = { on, repo, _ -> task = task.copy(github = on, repo = repo) },
            onDismiss = { showRepo = false },
        )
    }
    if (showTime) {
        val state = rememberTimePickerState(initialHour = task.hour, initialMinute = task.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("Uhrzeit") },
            text = { TimePicker(state = state) },
            confirmButton = { TextButton(onClick = { task = task.copy(hour = state.hour, minute = state.minute); showTime = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Abbrechen") } },
        )
    }
    if (showDelete) {
        ConfirmDialog("Aufgabe löschen?", "Bisherige Ergebnis-Chats bleiben erhalten.", "Löschen", onConfirm = {
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
