package io.github.thepytales.llmhub.ui

import io.github.thepytales.llmhub.data.PrivacyLevel
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.App
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.Folder
import io.github.thepytales.llmhub.data.MemoryEntry
import io.github.thepytales.llmhub.data.MemoryMode
import io.github.thepytales.llmhub.agent.WorkFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Einstellungen › Gedächtnis: what the model may note and what it noted. */
@Composable
fun MemorySection(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val entries by c.memory.items.collectAsStateWithLifecycle()
    val mode = MemoryMode.of(s.memoryMode)
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MemoryEntry?>(null) }
    SettingsCard("Gedächtnis") {
        SettingsHint(
            "Angaben, die das Modell in jedem Chat kennt – z.B. Ihre Funktion, Lehrveranstaltungen oder wie Texte klingen sollen. " +
                "Sie bleiben auf diesem Gerät und gehen nur als Teil Ihrer Anfragen an den Uni-Server."
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
    SettingsCard("Vorschläge in Formularen") {
        SwitchRow(
            "Vorschläge aus meinem Wissen",
            "Brief, Präsentation, Seminarplan und andere Vorlagen schlagen unter jedem Feld passende Angaben vor – aus dem Gedächtnis, " +
                "Ihren Absenderangaben, der allgemeinen Wissenssammlung und dem, was Sie früher in gleichnamige Felder geschrieben haben.",
            s.formSuggestions,
        ) { v -> c.settings.update { it.copy(formSuggestions = v) } }
    }
    SettingsCard("Gemerkt (${entries.size})") {
        if (entries.isEmpty()) SettingsHint("Noch nichts. Sagen Sie im Chat z.B. „Merk dir, dass ich im Master Allgemeine Didaktik unterrichte.“")
        entries.sortedByDescending { it.createdAt }.forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { editing = e }) {
                Text(e.text, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
                IconButton(onClick = { c.memory.delete(e.id) }) { Icon(Icons.Outlined.Delete, contentDescription = "Löschen") }
            }
        }
        OutlinedButton(onClick = { adding = true }) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Selbst eintragen")
        }
    }
    if (adding || editing != null) {
        TextInputDialog(
            if (editing != null) "Eintrag ändern" else "Neuer Eintrag",
            editing?.text.orEmpty(),
            "Was soll das Modell wissen?",
            confirm = "Speichern",
            onConfirm = { t ->
                val e = editing
                if (e != null) c.memory.update(e.id) { it.copy(text = t.trim()) } else c.memory.upsert(MemoryEntry(text = t.trim()))
            },
            onDismiss = { adding = false; editing = null },
        )
    }
}

/**
 * Documents of one knowledge collection ("" = general, else a project) with "Dokumente hinzufügen". Text is read
 * like an attachment; embeddings are computed in the background.
 */
@Composable
fun KnowledgeList(c: AppContainer, collection: String) {
    val all by c.knowledge.docs.collectAsStateWithLifecycle()
    val busy by c.knowledge.busy.collectAsStateWithLifecycle()
    val docs = all.filter { it.collection == collection }.sortedByDescending { it.addedAt }
    val pick = rememberDocumentPicker { file -> c.assistantTools.add(file.name, file.text, collection) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (docs.isEmpty()) SettingsHint("Noch keine Dokumente.")
        docs.forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    SettingsHint(
                        "${d.chunks} Abschnitte · ${(d.chars + 999) / 1000}k Zeichen · " +
                            if (d.embeddedWith != null) "semantische Suche" else "Stichwortsuche"
                    )
                }
                IconButton(onClick = { c.knowledge.remove(d) }) { Icon(Icons.Outlined.Delete, contentDescription = "Entfernen") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = pick) {
                Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Dokumente hinzufügen")
            }
            if (busy > 0) {
                CircularProgressIndicator(Modifier.padding(start = 12.dp).size(18.dp), strokeWidth = 2.dp)
                Text(" wird für die Suche aufbereitet …", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Einstellungen › Wissen: the general knowledge collection. */
@Composable
fun KnowledgeSection(c: AppContainer) {
    SettingsCard("Wissenssammlung") {
        SettingsHint(
            "Eigene Dokumente, die das Modell in jedem Chat durchsuchen kann – z.B. Modulhandbuch, Prüfungsordnung, " +
                "Leitfäden oder Literatur (PDF, Word, Text …). Es liest nicht alles auf einmal, sondern sucht die passenden Stellen " +
                "und nennt die Quelle. Dokumente nur für ein Thema gehören besser in ein Projekt (Seitenleiste › Projekt › ⋮ › Projekt bearbeiten)."
        )
        KnowledgeList(c, "")
    }
}

/**
 * A folder as project: name, project folder on this PC, instructions for every chat in it, data protection and its own
 * documents. With [folder] null it creates a new project and hands it to [onCreated].
 */
@Composable
fun ProjectDialog(c: AppContainer, folder: Folder?, onDismiss: () -> Unit, onCreated: (Folder) -> Unit = {}) {
    val key = folder?.id ?: "new"
    var name by remember(key) { mutableStateOf(folder?.name.orEmpty()) }
    var instructions by remember(key) { mutableStateOf(folder?.instructions.orEmpty()) }
    var privacy by remember(key) { mutableStateOf(folder?.privacy ?: PrivacyLevel.OPEN) }
    var workDir by remember(key) { mutableStateOf(folder?.workDir.orEmpty()) }
    var access by remember(key) { mutableStateOf(folder?.workDirAccess ?: true) }
    var workProblem by remember(key) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (folder == null) "Neues Projekt" else "Projekt bearbeiten") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("z.B. Seminar Allgemeine Didaktik") },
                )
                Text("Projektordner", style = MaterialTheme.typography.titleSmall)
                if (!FolderAccess.SUPPORTED) {
                    SettingsHint("Einen Projektordner auf dem Rechner gibt es in der Windows-Version. Auf Android bleiben die Dateien in der App und lassen sich über Teilen oder Speichern unter weitergeben.")
                } else {
                    if (workDir.isBlank()) {
                        SettingsHint(
                            "Ein Ordner auf diesem PC für das Projekt – z. B. in Google Drive, OneDrive, Nextcloud oder auf einem Netzlaufwerk. " +
                                "Alles, was die KI im Projekt erstellt (Briefe, PDF, Word, PowerPoint, Excel …), landet dort im Unterordner „${WorkFolder.OUTPUT}“.",
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(workDir, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = { FolderAccess.open(workDir) }) { Icon(Icons.Outlined.FolderOpen, contentDescription = "Im Explorer öffnen") }
                            IconButton(onClick = { workDir = ""; workProblem = null }) { Icon(Icons.Outlined.Delete, contentDescription = "Projektordner entfernen") }
                        }
                        SettingsHint("Was die KI erstellt, landet im Unterordner „${WorkFolder.OUTPUT}“ – ohne Rückfrage; deine eigenen Dateien überschreibt sie nie.")
                        ToggleRow(
                            "KI darf die Dateien im Ordner lesen",
                            if (access) "Die KI sieht, welche Dateien im Ordner liegen, und liest die passenden (z. B. Notizen, Vorlagen, frühere Briefe). " +
                                "Was sie liest, geht an das gewählte Modell – siehe Datenschutzstufe."
                            else "Die KI sieht nur „${WorkFolder.OUTPUT}“; die übrigen Dateien im Ordner bleiben für sie unsichtbar.",
                            access,
                        ) { access = it }
                    }
                    workProblem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    OutlinedButton(onClick = {
                        scope.launch {
                            val path = FolderAccess.pick("Projektordner für „${name.ifBlank { "Neues Projekt" }}“ wählen") ?: return@launch
                            val problem = withContext(Dispatchers.IO) { WorkFolder.problem(File(path)) }
                            workProblem = problem
                            if (problem == null) workDir = path
                        }
                    }) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(if (workDir.isBlank()) "Ordner wählen …" else "Anderen Ordner wählen …")
                    }
                }
                OutlinedTextField(
                    instructions, { instructions = it },
                    label = { Text("Anweisungen für alle Chats im Projekt") },
                    supportingText = { Text("z.B. „Es geht um das Seminar Classroom Management im WS 2026/27, Zielgruppe Lehramt Gymnasium. Antworte knapp.“") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Datenschutzstufe", style = MaterialTheme.typography.titleSmall)
                PrivacyLevel.entries.forEach { level ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { privacy = level },
                    ) {
                        RadioButton(selected = privacy == level, onClick = { privacy = level })
                        TitleBlock(level.label, level.description, Modifier.weight(1f))
                    }
                }
                Text("Dokumente zum Durchsuchen", style = MaterialTheme.typography.titleSmall)
                if (folder == null) {
                    SettingsHint("PDF, Word & Co., die nur die Chats in diesem Projekt durchsuchen, lassen sich nach dem Erstellen hinzufügen (⋮ › Projekt bearbeiten).")
                } else {
                    SettingsHint("Nur die Chats in diesem Projekt durchsuchen diese Dokumente – sie liegen in der App, nicht im Projektordner.")
                    KnowledgeList(c, folder.id)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                if (folder == null) {
                    val created = Folder(name = name.trim(), instructions = instructions.trim(), privacy = privacy, workDir = workDir.trim(), workDirAccess = access)
                    c.folders.upsert(created)
                    onCreated(created)
                } else {
                    c.folders.update(folder.id) {
                        it.copy(name = name.trim(), instructions = instructions.trim(), privacy = privacy, workDir = workDir.trim(), workDirAccess = access)
                    }
                }
                onDismiss()
            }) { Text(if (folder == null) "Erstellen" else "Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** The next new chat starts in this folder (set by "Neuer Chat im Projekt"). */
object NewChatFolder {
    @Volatile
    var id: String? = null

    fun take(): String? = id.also { id = null }
}

/** Removes the documents of a deleted project. */
fun deleteProjectKnowledge(folderId: String) = App.container.knowledge.removeCollection(folderId)
