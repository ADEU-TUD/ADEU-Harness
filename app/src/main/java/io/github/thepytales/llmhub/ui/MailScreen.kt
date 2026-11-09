package io.github.thepytales.llmhub.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.ChatModes
import io.github.thepytales.llmhub.agent.OutlookTools
import io.github.thepytales.llmhub.data.CalendarEntry
import io.github.thepytales.llmhub.data.MailFolder
import io.github.thepytales.llmhub.data.MailRule
import io.github.thepytales.llmhub.data.Outlook
import io.github.thepytales.llmhub.data.OutlookAction
import io.github.thepytales.llmhub.data.OutlookBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** Connection to Outlook as the screen shows it. */
private sealed interface OutlookState {
    data object Connecting : OutlookState
    data class Ready(val accounts: String) : OutlookState
    data class Problem(val message: String) : OutlookState
}

/** Starts a chat in the Postfach mode; [send] sends [text] at once, otherwise it waits in the input. */
internal fun newMailChat(text: String, send: Boolean) {
    ChatHandoff.pending.value = ChatHandoff.Content(text, emptyList(), mode = ChatModes.MAIL.id, send = send)
    ChatHandoff.open.value = ""
}

/**
 * Mail & Kalender (AP-13, Windows): the connection to the classic Outlook, starts for Lora (what is important, sort,
 * deadlines, free times, answer), today's and tomorrow's appointments, the fixed sorting rules with „Anwenden“ (shows
 * first what would move) and the protocol of every change the app made in Outlook. Nothing is deleted or sent.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MailScreen(c: AppContainer, onBack: () -> Unit) {
    val bridge = Outlook.bridge
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<OutlookState>(OutlookState.Connecting) }
    var agenda by remember { mutableStateOf<List<CalendarEntry>?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val rules by c.mailRules.items.collectAsStateWithLifecycle()
    val log by c.outlookLog.items.collectAsStateWithLifecycle()
    var editRule by remember { mutableStateOf<MailRule?>(null) }
    var applyPlan by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var allLog by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        if (bridge == null) {
            state = OutlookState.Problem(tr("Noch kein Postfach verbunden. Unter Windows arbeitet Lora mit dem klassischen Outlook; sonst – und auf dem Handy – unter Einstellungen › Verbindungen › „Mail über den Server“ den Mailserver eintragen."))
            return@LaunchedEffect
        }
        state = OutlookState.Connecting
        state = withContext(Dispatchers.IO) {
            runCatching { OutlookState.Ready(bridge.connect()) }.getOrElse { OutlookState.Problem(Outlook.explain(it)) }
        }
        if (state is OutlookState.Ready) {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
            agenda = withContext(Dispatchers.IO) { runCatching { bridge.events(today, today + 2 * 86_400_000L) }.getOrNull() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = { Text(tr("Mail & Kalender")) },
                actions = { IconButton(onClick = { attempt++ }) { Icon(Icons.Outlined.Refresh, contentDescription = tr("Neu laden")) } },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 860.dp).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = LORA_BUTTON_ROOM),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { StatusCard(state, onRetry = { attempt++ }) }
                // AP-68: only a model on this device reads the mails (data note, model, download offer).
                item { MailLocalModelCard(c) }
                item {
                    AppCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(tr("Mit Lora"), style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                MAIL_STARTS.forEach { s ->
                                    AssistChip(
                                        onClick = { newMailChat(tr(s.text), s.send) },
                                        label = { Text(tr(s.label)) },
                                        leadingIcon = { Icon(s.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        enabled = bridge != null,
                                    )
                                }
                            }
                            HintText(tr("Lora löscht nie etwas und sendet nie: Antworten landen als Entwurf in Outlook, Einladungen öffnen sich dort zum Prüfen und Senden. Vor dem Verschieben fragt die App nach."))
                        }
                    }
                }
                agenda?.let { list ->
                    item {
                        AppCard {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(tr("Heute und morgen"), style = MaterialTheme.typography.titleSmall)
                                }
                                if (list.isEmpty()) HintText(tr("Keine Termine."))
                                list.take(10).forEach { e ->
                                    Text(
                                        Outlook.span(e.start, e.end, e.allDay) + " · " + e.subject.ifBlank { tr("(ohne Titel)") } + if (e.location.isNotBlank()) " · ${e.location}" else "",
                                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    AppCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Rule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(tr("Sortierregeln"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                TextButton(onClick = { editRule = MailRule(name = "", folder = "") }, enabled = bridge != null) {
                                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(tr("Regel"))
                                }
                            }
                            if (rules.isEmpty()) {
                                HintText(tr("Noch keine Regeln. Lege eine an oder bitte Lora: „Schlag mir Regeln zum Sortieren vor“. Regeln wirken nur, wenn du sie hier anwendest."))
                            }
                            rules.forEach { r ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(r.name, style = MaterialTheme.typography.bodyMedium)
                                        HintText(OutlookTools.ruleText(r) + if (r.moved > 0) " · " + tr("bisher") + " ${r.moved}" else "")
                                    }
                                    Switch(modifier = Modifier.a11yLabel(r.name), checked = r.enabled, onCheckedChange = { on -> c.mailRules.update(r.id) { it.copy(enabled = on) } })
                                    IconButton(onClick = {
                                        c.mailRules.delete(r.id)
                                        Notices.undo(tr("Regel gelöscht"), restore = { c.mailRules.upsert(r) })
                                    }) { Icon(Icons.Outlined.Delete, contentDescription = tr("Regel löschen")) }
                                }
                            }
                            if (rules.any { it.enabled } && bridge != null) {
                                OutlinedButton(enabled = !busy, onClick = {
                                    busy = true
                                    scope.launch {
                                        applyPlan = withContext(Dispatchers.IO) {
                                            runCatching { OutlookTools.applyRules(c, bridge, 200, dry = true, by = "Sie") }.getOrElse { Outlook.explain(it) }
                                        }
                                        busy = false
                                    }
                                }) {
                                    if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Outlined.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(tr("Regeln anwenden …"))
                                }
                            }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Protokoll"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (log.size > 8) TextButton(onClick = { allLog = !allLog }) { Text(tr(if (allLog) "Weniger" else "Alle anzeigen")) }
                    }
                    HintText(tr("Alles, was die App in Outlook geändert hat – gelöscht oder gesendet wird nie etwas."))
                }
                val shownLog = log.sortedByDescending { it.at }.let { if (allLog) it else it.take(8) }
                if (shownLog.isEmpty()) item { HintText(tr("Noch nichts.")) }
                items(shownLog, key = { it.id }) { a -> LogRow(a) }
            }
        }
    }

    editRule?.let { r -> RuleDialog(bridge, r, onDismiss = { editRule = null }, onSave = { rule -> c.mailRules.upsert(rule); editRule = null }) }

    applyPlan?.let { plan ->
        val canMove = plan.startsWith("Würde verschieben")
        AlertDialog(
            onDismissRequest = { applyPlan = null },
            title = { Text(tr("Regeln anwenden")) },
            text = { Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) { Text(plan, style = MaterialTheme.typography.bodySmall) } },
            confirmButton = {
                if (canMove && bridge != null) TextButton(onClick = {
                    applyPlan = null
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { OutlookTools.applyRules(c, bridge, 200, dry = false, by = "Sie") }.getOrElse { Outlook.explain(it) } }
                        busy = false
                        Notices.info(result.lines().first())
                    }
                }) { Text(tr("Verschieben")) } else TextButton(onClick = { applyPlan = null }) { Text(tr("OK")) }
            },
            dismissButton = { if (canMove) TextButton(onClick = { applyPlan = null }) { Text(tr("Abbrechen")) } },
        )
    }
}

private data class MailStart(val label: String, val text: String, val send: Boolean, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val MAIL_STARTS = listOf(
    MailStart(trLater("Was ist seit gestern wichtig?"), trLater("Was ist seit gestern im Posteingang wichtig?"), true, Icons.Outlined.Inbox),
    MailStart(trLater("Posteingang sortieren"), trLater("Schau dir die letzten 100 Mails im Posteingang an und schlag mir Ordner und feste Regeln zum Sortieren vor. Noch nichts verschieben."), true, Icons.Outlined.DriveFileMove),
    MailStart(trLater("Fristen und Termine aus Mails"), trLater("Finde in den Mails der letzten 7 Tage Fristen und Termine und schlag vor, was ich als Termin oder Aufgabe übernehmen soll."), true, Icons.Outlined.TaskAlt),
    MailStart(trLater("Antwort entwerfen …"), trLater("Entwirf eine Antwort auf die Mail von "), false, Icons.Outlined.EditNote),
    MailStart(trLater("Freie Zeit finden …"), trLater("Finde einen gemeinsamen Termin (60 Minuten) in den nächsten zwei Wochen mit "), false, Icons.Outlined.EventAvailable),
)

@Composable
private fun StatusCard(state: OutlookState, onRetry: () -> Unit) {
    AppCard {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            LoraFigure(
                Modifier.size(104.dp, 104.dp / LORA_ASPECT), label = tr("Lora sortiert Briefe"),
                director = rememberLoraDirector(listOf(LoraAction.MAIL, LoraAction.FOLDER, LoraAction.CALENDAR), LoraAction.MAIL),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr("Klassisches Outlook"), style = MaterialTheme.typography.titleMedium)
                when (state) {
                    OutlookState.Connecting -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        HintText(tr("Verbinde mit Outlook … (startet es bei Bedarf im Hintergrund)"))
                    }
                    is OutlookState.Ready -> {
                        StatusPill(tr("Verbunden"), PillTone.SUCCESS)
                        HintText(state.accounts)
                    }
                    is OutlookState.Problem -> {
                        Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        if (Outlook.supported) TextButton(onClick = onRetry) { Text(tr("Erneut versuchen")) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(a: OutlookAction) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Text(Outlook.show(a.at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(150.dp))
        Column(Modifier.weight(1f)) {
            Text(a.what + " · " + a.detail, style = MaterialTheme.typography.bodySmall)
            HintText(a.by)
        }
    }
}

/** A new rule: name, sender and/or subject part, target folder (from Outlook's folders). */
@Composable
private fun RuleDialog(bridge: OutlookBridge?, start: MailRule, onDismiss: () -> Unit, onSave: (MailRule) -> Unit) {
    var name by remember { mutableStateOf(start.name) }
    var from by remember { mutableStateOf(start.from) }
    var subject by remember { mutableStateOf(start.subject) }
    var folder by remember { mutableStateOf<MailFolder?>(null) }
    var folders by remember { mutableStateOf<List<MailFolder>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (bridge == null) return@LaunchedEffect
        withContext(Dispatchers.IO) { runCatching { bridge.folders() } }.onSuccess { folders = it }.onFailure { error = Outlook.explain(it) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Sortierregel")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(tr("Name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(from, { from = it }, label = { Text(tr("Absender enthält")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(subject, { subject = it }, label = { Text(tr("Betreff enthält")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedButton(onClick = { menu = true }, enabled = folders != null, modifier = Modifier.fillMaxWidth()) {
                        Text(folder?.path?.trim('\\') ?: if (folders == null && error == null) tr("Ordner werden geladen …") else tr("Zielordner wählen"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        folders.orEmpty().take(300).forEach { f ->
                            DropdownMenuItem(text = { Text(f.path.trim('\\'), maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { folder = f; menu = false })
                        }
                    }
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                HintText(tr("Die Regel verschiebt nur, wenn du unter „Regeln anwenden“ zustimmst."))
            }
        },
        confirmButton = {
            val f = folder
            Button(
                enabled = f != null && (from.isNotBlank() || subject.isNotBlank()),
                onClick = { f?.let { onSave(start.copy(name = name.ifBlank { from.ifBlank { subject } }.trim(), from = from.trim(), subject = subject.trim(), folder = it.id, folderName = it.path.trim('\\'))) } },
            ) { Text(tr("Speichern")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}
