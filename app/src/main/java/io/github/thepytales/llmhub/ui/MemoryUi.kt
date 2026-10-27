package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.Memory
import io.github.thepytales.llmhub.agent.MemoryLearner
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.MemoryCategory
import io.github.thepytales.llmhub.data.MemoryEntry
import io.github.thepytales.llmhub.data.MemoryMode
import io.github.thepytales.llmhub.data.Privacy
import io.github.thepytales.llmhub.data.PrivacyLevel
import io.github.thepytales.llmhub.data.PrivacyScope
import io.github.thepytales.llmhub.net.ThinkingLevel
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat get() = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)

/** The chip above the input field: how many details of the memory go along; a tap shows them. */
@Composable
fun MemoryChip(c: AppContainer, conv: Conversation, onClick: () -> Unit) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val all by c.memory.items.collectAsStateWithLifecycle()
    if (MemoryMode.of(s.memoryMode) == MemoryMode.OFF) return
    val count = remember(all, conv.folderId) { Memory.entries(c, conv.folderId).size }
    val label = when {
        conv.memoryOff -> tr("Gedächtnis aus")
        count == 0 -> tr("Gedächtnis")
        else -> tr("Gedächtnis") + " · $count"
    }
    ContextChip(Icons.Outlined.Psychology, label, onClick, highlighted = false)
}

/** What the model knows in this chat; switch it off for the chat, add or change entries. */
@Composable
fun MemoryChatDialog(c: AppContainer, conv: Conversation, onMemoryOff: (Boolean) -> Unit, onSettings: () -> Unit, onDismiss: () -> Unit) {
    val all by c.memory.items.collectAsStateWithLifecycle()
    val entries = remember(all, conv.folderId) { Memory.entries(c, conv.folderId) }
    var editing by remember { mutableStateOf<MemoryEntry?>(null) }
    var adding by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Psychology, contentDescription = null) },
        title = { Text(tr("Gedächtnis in diesem Chat")) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Gedächtnis nutzen"))
                        Text(
                            if (conv.memoryOff) tr("In diesem Chat aus – das Modell kennt die Angaben nicht und lernt nichts dazu.")
                            else tr("Geht bei jeder Anfrage automatisch mit; Sie müssen nichts dazuschreiben."),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = !conv.memoryOff, onCheckedChange = { onMemoryOff(!it) })
                }
                HorizontalDivider()
                if (entries.isEmpty()) {
                    Text(
                        tr("Noch nichts gemerkt. Erzählen Sie im Chat etwas über sich oder tragen Sie es hier ein."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MemoryList(c, entries, onEdit = { editing = it })
                Row {
                    TextButton(onClick = { adding = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(tr("Eintrag hinzufügen"))
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { onDismiss(); onSettings() }) { Text(tr("Alle verwalten")) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Fertig")) } },
    )
    if (adding || editing != null) {
        MemoryEditDialog(c, editing, initialText = "", folderId = conv.folderId, onDismiss = { adding = false; editing = null })
    }
}

/** Entries grouped by category; tap to change, bin to delete. Expired ones are shown faded. */
@Composable
fun MemoryList(c: AppContainer, entries: List<MemoryEntry>, onEdit: (MemoryEntry) -> Unit) {
    val now = System.currentTimeMillis()
    MemoryCategory.entries.forEach { cat ->
        val inCat = entries.filter { MemoryCategory.of(it.category) == cat }.sortedByDescending { it.createdAt }
        if (inCat.isEmpty()) return@forEach
        Text(cat.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
        inCat.forEach { e ->
            val expired = e.expired(now)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onEdit(e) }) {
                Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                    Text(e.text, color = if (expired) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified)
                    val notes = listOfNotNull(
                        e.projectId?.let { id -> c.folders.get(id)?.name?.let { tr("Nur Projekt") + " „$it“" } },
                        e.expiresAt?.let { (if (expired) tr("abgelaufen am") else tr("gilt bis")) + " " + dateFormat.format(Date(it)) },
                        tr("dazugelernt").takeIf { e.source == "learned" },
                    )
                    if (notes.isNotEmpty()) {
                        Text(notes.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = { c.memory.delete(e.id) }) { Icon(Icons.Outlined.Delete, contentDescription = tr("Löschen")) }
            }
        }
    }
}

/**
 * New or changed entry: text, area, for all chats or only the project, optionally until a date. [initialText] from
 * „Merken …“ at a message: short texts are taken as they are, longer ones are first condensed by the model.
 */
@Composable
fun MemoryEditDialog(c: AppContainer, entry: MemoryEntry?, initialText: String, folderId: String?, onDismiss: () -> Unit) {
    val long = entry == null && initialText.length > 240
    var text by remember { mutableStateOf(entry?.text ?: if (long) "" else initialText.trim()) }
    var category by remember { mutableStateOf(MemoryCategory.of(entry?.category)) }
    var project by remember { mutableStateOf(entry?.projectId != null) }
    var until by remember { mutableStateOf(entry?.expiresAt?.let { dateFormat.format(Date(it)) }.orEmpty()) }
    var condensing by remember { mutableStateOf(long) }
    val projectName = (entry?.projectId ?: folderId)?.let { c.folders.get(it)?.name }
    if (long) {
        // A whole message is too long for the memory: the model suggests the lasting part (in the project's privacy level).
        LaunchedEffect(initialText) {
            val level = Privacy.level(c, folderId)
            val model = if (level == PrivacyLevel.LOCAL) c.localLlm.preferredModel() else Privacy.suitableModel(c, folderId, c.settings.current.defaultModel)
            val found = model?.let {
                runCatching {
                    withContext(PrivacyScope("memory")) {
                        c.llm.streamChat(it, MemoryLearner.prompt(listOf(initialText), emptyList(), emptyList(), folderId != null), null, thinking = ThinkingLevel.OFF) { }
                    }
                }.getOrNull()
            }?.let { MemoryLearner.parse(splitThinking(it.content).second, folderId).firstOrNull() }
            if (found != null && text.isBlank()) {
                text = found.text
                category = MemoryCategory.of(found.category)
                project = found.projectId != null
                found.expiresAt?.let { until = dateFormat.format(Date(it)) }
            } else if (text.isBlank()) text = initialText.trim().take(300)
            condensing = false
        }
    }
    val untilMillis = Memory.parseDate(until)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry != null) tr("Eintrag ändern") else tr("Merken")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(tr("Was soll das Modell wissen?")) },
                    placeholder = { if (condensing) Text(tr("Vorschlag wird formuliert …"), fontStyle = FontStyle.Italic) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownField(
                    label = tr("Bereich"),
                    value = category.label,
                    options = MemoryCategory.entries.map { it.label },
                    onSelect = { v -> category = MemoryCategory.entries.first { it.label == v } },
                )
                if (projectName != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { project = false }) {
                        RadioButton(selected = !project, onClick = { project = false })
                        Text(tr("Für alle Chats"))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { project = true }) {
                        RadioButton(selected = project, onClick = { project = true })
                        Text(tr("Nur im Projekt") + " „$projectName“")
                    }
                }
                OutlinedTextField(
                    value = until,
                    onValueChange = { until = it },
                    label = { Text(tr("Gilt bis (optional, TT.MM.JJJJ)")) },
                    isError = until.isNotBlank() && untilMillis == null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank() && (until.isBlank() || untilMillis != null),
                onClick = {
                    val projectId = if (project) entry?.projectId ?: folderId else null
                    if (entry != null) {
                        c.memory.update(entry.id) { it.copy(text = text.trim(), category = category.name, projectId = projectId, expiresAt = untilMillis) }
                    } else {
                        c.memory.upsert(MemoryEntry(text = text.trim(), category = category.name, projectId = projectId, expiresAt = untilMillis, source = "user"))
                        Notices.info(tr("Gemerkt."))
                    }
                    onDismiss()
                },
            ) { Text(tr("Speichern")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

/**
 * Above the input field after a quiet moment in the chat: details the memory learner found. „Vorher fragen“: the
 * user picks what to keep; „Automatisch“: what was saved, with „Rückgängig“.
 */
@Composable
fun MemoryLearnBar(c: AppContainer, chatId: String) {
    val suggestions by c.memoryLearner.suggestions.collectAsStateWithLifecycle()
    val saved by c.memoryLearner.saved.collectAsStateWithLifecycle()
    val found = suggestions[chatId].orEmpty()
    val auto = saved[chatId].orEmpty()
    if (found.isEmpty() && auto.isEmpty()) return
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Psychology, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    if (found.isNotEmpty()) (if (found.size == 1) tr("1 neue Angabe für das Gedächtnis") else "${found.size} " + tr("neue Angaben für das Gedächtnis"))
                    else tr("Ins Gedächtnis übernommen"),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { if (found.isNotEmpty()) c.memoryLearner.decline(chatId) else c.memoryLearner.dismissSaved(chatId) }) {
                    Icon(Icons.Outlined.Close, contentDescription = tr("Schließen"), modifier = Modifier.size(18.dp))
                }
            }
            if (found.isNotEmpty()) {
                val chosen = remember(found) { mutableStateListOf<String>().apply { addAll(found.map { it.id }) } }
                found.forEach { e ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { if (e.id in chosen) chosen.remove(e.id) else chosen.add(e.id) }) {
                        Checkbox(checked = e.id in chosen, onCheckedChange = { on -> if (on) chosen.add(e.id) else chosen.remove(e.id) })
                        Text(e.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }
                Row {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { c.memoryLearner.decline(chatId) }) { Text(tr("Nein, danke")) }
                    TextButton(enabled = chosen.isNotEmpty(), onClick = { c.memoryLearner.accept(chatId, found.filter { it.id in chosen }) }) { Text(tr("Übernehmen")) }
                }
            } else {
                auto.forEach { e -> Text("– " + e.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 26.dp, bottom = 2.dp)) }
                Row {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { c.memoryLearner.undo(chatId) }) { Text(tr("Rückgängig")) }
                    TextButton(onClick = { c.memoryLearner.dismissSaved(chatId) }) { Text(tr("OK")) }
                }
            }
        }
    }
}

/** Three short questions that fill an empty memory (first steps, also under Einstellungen › Gedächtnis). */
@Composable
fun MemoryIntroDialog(c: AppContainer, onDismiss: () -> Unit) {
    var about by remember { mutableStateOf("") }
    var teaching by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Psychology, contentDescription = null) },
        title = { Text(tr("Gedächtnis einrichten")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    tr("Drei kurze Angaben genügen – das Modell kennt sie dann in jedem Chat. Alles lässt sich später ändern."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(about, { about = it }, label = { Text(tr("Ihre Funktion und Einrichtung")) },
                    placeholder = { Text("z. B. wiss. Mitarbeiterin, Professur für Allgemeine Didaktik") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(teaching, { teaching = it }, label = { Text(tr("Lehre oder Themen, an denen Sie arbeiten")) },
                    placeholder = { Text("z. B. Seminar Unterrichtsplanung im Master Lehramt") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(style, { style = it }, label = { Text(tr("Wie sollen Texte klingen?")) },
                    placeholder = { Text("z. B. sachlich, Sie-Form, kurze Absätze") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = listOf(about, teaching, style).any { it.isNotBlank() },
                onClick = {
                    listOf(
                        about to MemoryCategory.ABOUT,
                        teaching to MemoryCategory.TEACHING,
                        style to MemoryCategory.STYLE,
                    ).filter { it.first.isNotBlank() }.forEach { (text, cat) ->
                        val sentence = when (cat) {
                            MemoryCategory.ABOUT -> "Funktion und Einrichtung: " + text.trim()
                            MemoryCategory.TEACHING -> "Lehre und Themen: " + text.trim()
                            else -> "Texte sollen so klingen: " + text.trim()
                        }
                        if (!Memory.known(c, sentence)) c.memory.upsert(MemoryEntry(text = sentence, category = cat.name, source = "intro"))
                    }
                    c.settings.update { it.copy(memoryIntroDone = true) }
                    onDismiss()
                },
            ) { Text(tr("Speichern")) }
        },
        dismissButton = {
            TextButton(onClick = { c.settings.update { it.copy(memoryIntroDone = true) }; onDismiss() }) { Text(tr("Später")) }
        },
    )
}

/** On the start page of a new chat while the memory is empty: invites to the three questions. */
@Composable
fun MemoryIntroCard(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val all by c.memory.items.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    if (s.memoryIntroDone || all.isNotEmpty() || MemoryMode.of(s.memoryMode) == MemoryMode.OFF || !s.hasAnyApiKey) return
    Surface(
        color = LocalChatLook.current.card,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable { open = true },
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Psychology, contentDescription = null, tint = LocalChatLook.current.brand)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("Damit die KI Sie kennt"), style = MaterialTheme.typography.titleSmall)
                Text(tr("Drei kurze Fragen – dann müssen Sie nichts mehr dazuschreiben."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { c.settings.update { it.copy(memoryIntroDone = true) } }) { Icon(Icons.Outlined.Close, contentDescription = tr("Ausblenden")) }
        }
    }
    if (open) MemoryIntroDialog(c, onDismiss = { open = false })
}

/** Einstellungen › Gedächtnis: what the model may note, how it learns, and everything it noted. */
@Composable
fun MemorySection(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val entries by c.memory.items.collectAsStateWithLifecycle()
    val folders by c.folders.items.collectAsStateWithLifecycle()
    val mode = MemoryMode.of(s.memoryMode)
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MemoryEntry?>(null) }
    var intro by remember { mutableStateOf(false) }
    SettingsCard("Gedächtnis") {
        SettingsHint(
            "Angaben, die das Modell in jedem Chat kennt – z.B. Ihre Funktion, Lehrveranstaltungen oder wie Texte klingen sollen. " +
                "Sie gehen bei jeder Anfrage automatisch mit (auch im Editor und beim Modellvergleich); über dem Eingabefeld zeigt " +
                "„Gedächtnis“, wie viele es sind, und schaltet es für einen Chat aus. Die Angaben bleiben auf diesem Gerät und " +
                "gehen nur als Teil Ihrer Anfragen an den Server – mit Pseudonymisierung ohne echte Namen."
        )
        MemoryMode.entries.forEach { m ->
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth().clickable { c.settings.update { it.copy(memoryMode = m.name) } },
            ) {
                RadioButton(selected = mode == m, onClick = { c.settings.update { it.copy(memoryMode = m.name) } })
                Column(Modifier.padding(top = 12.dp)) {
                    Text(m.label)
                    SettingsHint(m.description)
                }
            }
        }
    }
    if (mode != MemoryMode.OFF) {
        SettingsCard("Dazulernen") {
            SwitchRow(
                "Nach dem Chat dazulernen",
                "Ruht ein Chat kurz, sucht ein kleines Modell in Ihren Nachrichten nach dauerhaften Angaben (Funktion, Lehre, Stil, " +
                    "Kontakte, Vorhaben). " + if (mode == MemoryMode.AUTO) "Sie werden gespeichert; im Chat steht, was dazukam, mit „Rückgängig“."
                else "Sie erscheinen im Chat als Vorschlag – Sie wählen, was übernommen wird.",
                s.memoryLearn,
            ) { v -> c.settings.update { it.copy(memoryLearn = v) } }
            SwitchRow(
                "Frühere Chats einbeziehen",
                "Das Modell kennt die Titel Ihrer letzten Chats und durchsucht sie, wenn Sie sich auf Früheres beziehen („wie letztes " +
                    "Mal“). Chats aus Projekten mit strengerer Datenschutzstufe bleiben außen vor.",
                s.memoryChats,
            ) { v -> c.settings.update { it.copy(memoryChats = v) } }
        }
    }
    SettingsCard("Vorschläge in Formularen") {
        SwitchRow(
            "Vorschläge aus meinem Wissen",
            "Brief, Präsentation, Seminarplan und andere Vorlagen schlagen unter jedem Feld passende Angaben vor – aus dem Gedächtnis, " +
                "Ihren Absenderangaben, der allgemeinen Wissenssammlung und dem, was Sie früher in gleichnamige Felder geschrieben haben.",
            s.formSuggestions,
        ) { v -> c.settings.update { it.copy(formSuggestions = v) } }
    }
    val general = entries.filter { it.projectId == null }
    SettingsCard("Gemerkt (${entries.size})") {
        if (entries.isEmpty()) SettingsHint("Noch nichts. Sagen Sie im Chat z.B. „Ich unterrichte im Master Allgemeine Didaktik.“ – oder richten Sie es mit drei Fragen ein.")
        MemoryList(c, general, onEdit = { editing = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { adding = true }) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Selbst eintragen")
            }
            OutlinedButton(onClick = { intro = true }) { Text("Mit drei Fragen einrichten") }
        }
    }
    // Entries that only apply in one project.
    folders.forEach { folder ->
        val inProject = entries.filter { it.projectId == folder.id }
        if (inProject.isNotEmpty()) {
            SettingsCard("Projekt „${folder.name}“ (${inProject.size})") {
                MemoryList(c, inProject, onEdit = { editing = it })
            }
        }
    }
    val orphans = entries.filter { e -> e.projectId != null && folders.none { it.id == e.projectId } }
    if (orphans.isNotEmpty()) {
        SettingsCard("Aus gelöschten Projekten (${orphans.size})") {
            SettingsHint("Diese Angaben gehören zu Projekten, die es nicht mehr gibt, und gehen nirgends mehr mit.")
            MemoryList(c, orphans, onEdit = { editing = it })
        }
    }
    if (adding || editing != null) MemoryEditDialog(c, editing, initialText = "", folderId = editing?.projectId, onDismiss = { adding = false; editing = null })
    if (intro) MemoryIntroDialog(c, onDismiss = { intro = false })
}
