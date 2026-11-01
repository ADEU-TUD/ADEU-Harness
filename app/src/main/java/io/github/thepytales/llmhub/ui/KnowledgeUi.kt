package io.github.thepytales.llmhub.ui

import io.github.thepytales.llmhub.files.ProjectTree
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
import io.github.thepytales.llmhub.agent.WorkFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
        if (docs.isEmpty()) SettingsHint(tr("Noch keine Dokumente."))
        docs.forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    SettingsHint(
                        tr("{0} Abschnitte · {1}k Zeichen · ", d.chunks, (d.chars + 999) / 1000) +
                            if (d.embeddedWith != null) tr("semantische Suche") else tr("Stichwortsuche")
                    )
                }
                IconButton(onClick = { c.knowledge.remove(d) }) { Icon(Icons.Outlined.Delete, contentDescription = tr("Entfernen")) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = pick) {
                Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(tr("Dokumente hinzufügen"))
            }
            if (busy > 0) {
                CircularProgressIndicator(Modifier.padding(start = 12.dp).size(18.dp), strokeWidth = 2.dp)
                Text(tr(" wird für die Suche aufbereitet …"), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Einstellungen › Wissen: the general knowledge collection. */
@Composable
fun KnowledgeSection(c: AppContainer) {
    SettingsCard(tr("Wissenssammlung")) {
        SettingsHint(
            tr("Eigene Dokumente, die das Modell in jedem Chat durchsuchen kann – z.B. Modulhandbuch, Prüfungsordnung, " +
                "Leitfäden oder Literatur (PDF, Word, Text …). Es liest nicht alles auf einmal, sondern sucht die passenden Stellen " +
                "und nennt die Quelle. Dokumente nur für ein Thema gehören besser in ein Projekt (Seitenleiste › Projekt › ⋮ › Projekt bearbeiten).")
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
    /** After a folder was chosen: asks once whether the model may read its files. */
    var askAccess by remember { mutableStateOf(false) }
    val pickFolder = rememberProjectFolderPicker { path ->
        scope.launch {
            val problem = withContext(Dispatchers.IO) {
                if (ProjectTree.isTree(path)) ProjectTree.problem(c.appContext, path) else WorkFolder.problem(File(path))
            }
            workProblem = problem
            if (problem == null) {
                workDir = path
                askAccess = true
            }
        }
    }
    if (askAccess) {
        AlertDialog(
            onDismissRequest = { access = false; askAccess = false },
            title = { Text(tr("Ordner als Kontext nutzen?")) },
            text = {
                Text(
                    tr("Soll die KI die Dateien in diesem Ordner als Kontext nutzen? Was sie liest, geht an das gewählte Modell – siehe ") +
                        tr("Datenschutzstufe. Was sie erstellt, landet in jedem Fall in „{0}“. Änderbar unter Projekt bearbeiten.", WorkFolder.OUTPUT),
                )
            },
            confirmButton = { TextButton(onClick = { access = true; askAccess = false }) { Text(tr("Ja, alles lesen")) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { access = false; askAccess = false }) { Text(tr("Später entscheiden")) }
                    TextButton(onClick = { access = false; askAccess = false }) { Text(tr("Nur {0}", WorkFolder.OUTPUT)) }
                }
            },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (folder == null) tr("Neues Projekt") else tr("Projekt bearbeiten")) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    name, { name = it }, label = { Text(tr("Name")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(tr("z.B. Seminar Allgemeine Didaktik")) },
                )
                Text(tr("Projektordner"), style = MaterialTheme.typography.titleSmall)
                if (!PROJECT_FOLDERS) {
                    SettingsHint(tr("Einen Projektordner auf dem Rechner gibt es in der Windows-Version. Auf Android bleiben die Dateien in der App und lassen sich über Teilen oder Speichern unter weitergeben."))
                } else {
                    if (workDir.isBlank()) {
                        SettingsHint(
                            (if (ProjectTree.SUPPORTED) tr("Ein Ordner auf dem Gerät für das Projekt – auch in Nextcloud oder Google Drive, wenn deren App ihn im Auswahldialog anbietet. ")
                            else tr("Ein Ordner auf diesem PC für das Projekt – z. B. in Google Drive, OneDrive, Nextcloud oder auf einem Netzlaufwerk. ")) +
                                tr("Alles, was die KI im Projekt erstellt (Briefe, PDF, Word, PowerPoint, Excel …), landet dort im Unterordner „{0}“.", WorkFolder.OUTPUT),
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(ProjectTree.label(workDir), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = { openProjectFolder(c.appContext, workDir) }) { Icon(Icons.Outlined.FolderOpen, contentDescription = tr("Ordner öffnen")) }
                            IconButton(onClick = { workDir = ""; workProblem = null }) { Icon(Icons.Outlined.Delete, contentDescription = tr("Projektordner entfernen")) }
                        }
                        SettingsHint(tr("Was die KI erstellt, landet im Unterordner „{0}“ – ohne Rückfrage; deine eigenen Dateien überschreibt sie nie.", WorkFolder.OUTPUT))
                        ToggleRow(
                            tr("KI darf die Dateien im Ordner lesen"),
                            if (access) tr("Die KI sieht, welche Dateien im Ordner liegen, und liest die passenden (z. B. Notizen, Vorlagen, frühere Briefe). " +
                                "Was sie liest, geht an das gewählte Modell – siehe Datenschutzstufe.")
                            else tr("Die KI sieht nur „{0}“; die übrigen Dateien im Ordner bleiben für sie unsichtbar.", WorkFolder.OUTPUT),
                            access,
                        ) { access = it }
                    }
                    workProblem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    OutlinedButton(onClick = { pickFolder(tr("Projektordner für „{0}“ wählen", name.ifBlank { tr("Neues Projekt") })) }) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(if (workDir.isBlank()) tr("Ordner wählen …") else tr("Anderen Ordner wählen …"))
                    }
                }
                OutlinedTextField(
                    instructions, { instructions = it },
                    label = { Text(tr("Anweisungen für alle Chats im Projekt")) },
                    supportingText = { Text(tr("z.B. „Es geht um das Seminar Classroom Management im WS 2026/27, Zielgruppe Lehramt Gymnasium. Antworte knapp.“")) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(tr("Datenschutzstufe"), style = MaterialTheme.typography.titleSmall)
                PrivacyLevel.entries.forEach { level ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { privacy = level },
                    ) {
                        RadioButton(selected = privacy == level, onClick = { privacy = level })
                        TitleBlock(level.label, level.description, Modifier.weight(1f))
                    }
                }
                Text(tr("Dokumente zum Durchsuchen"), style = MaterialTheme.typography.titleSmall)
                if (folder == null) {
                    SettingsHint(tr("PDF, Word & Co., die nur die Chats in diesem Projekt durchsuchen, lassen sich nach dem Erstellen hinzufügen (⋮ › Projekt bearbeiten)."))
                } else {
                    SettingsHint(tr("Nur die Chats in diesem Projekt durchsuchen diese Dokumente – sie liegen in der App, nicht im Projektordner."))
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
            }) { Text(if (folder == null) tr("Erstellen") else tr("Speichern")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
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

/** Project folders exist on Windows (paths) and Android (chosen in the system dialog). */
val PROJECT_FOLDERS = FolderAccess.SUPPORTED || ProjectTree.SUPPORTED

/** Opens a project folder in Explorer or in the file app. */
fun openProjectFolder(context: android.content.Context, path: String) {
    if (ProjectTree.isTree(path)) ProjectTree.open(context, path) else FolderAccess.open(path)
}
