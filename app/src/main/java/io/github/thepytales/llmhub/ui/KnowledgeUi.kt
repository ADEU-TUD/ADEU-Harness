package io.github.thepytales.llmhub.ui

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
                "und nennt die Quelle. Dokumente nur für ein Thema gehören besser in ein Projekt (Ordner › Projekt bearbeiten)."
        )
        KnowledgeList(c, "")
    }
}

/** A folder as project: name, instructions for every chat in it and its own documents. */
@Composable
fun ProjectDialog(c: AppContainer, folder: Folder, onDismiss: () -> Unit) {
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    var instructions by remember(folder.id) { mutableStateOf(folder.instructions) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Projekt") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    instructions, { instructions = it },
                    label = { Text("Anweisungen für alle Chats im Projekt") },
                    supportingText = { Text("z.B. „Es geht um das Seminar Classroom Management im WS 2026/27, Zielgruppe Lehramt Gymnasium. Antworte knapp.“") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Projektdateien", style = MaterialTheme.typography.titleSmall)
                SettingsHint("Nur die Chats in diesem Projekt durchsuchen diese Dokumente.")
                KnowledgeList(c, folder.id)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                c.folders.update(folder.id) { it.copy(name = name.trim(), instructions = instructions.trim()) }
                onDismiss()
            }) { Text("Speichern") }
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
