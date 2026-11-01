package io.github.thepytales.llmhub.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.App
import io.github.thepytales.llmhub.files.LoraMirror
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Where the Lora-Ordner is, in one line (Dateien tab, settings). */
fun loraFolderLabel(): String {
    val tree = App.container.settings.current.loraTree
    return if (tree.isBlank()) tr("In der App (noch kein Ordner gewählt)") else tr("Kopie in ") + LoraMirror.label(tree)
}

/** Android cannot show a folder of the app in a file manager. */
const val LORA_FOLDER_OPENS = false

fun openLoraFolder(dir: File? = null) {}

/**
 * Lora-Ordner (AP-16), Android: the files live inside the app; a folder chosen once in the system dialog (e.g.
 * Dokumente/Lora) gets a copy of them and gives back what was changed there. Chats and settings stay in the app.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoraFolderSettings() {
    val c = App.container
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsState()
    var busy by remember { mutableStateOf(false) }
    fun sync(tree: String) {
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { LoraMirror.sync(context, c.drive.root, tree) } }
            busy = false
            r.onSuccess { Notices.info(tr("Abgeglichen: {0} hin, {1} zurück", it.up, it.down) + if (it.removed > 0) tr(", {0} entfernt", it.removed) else "") }
                .onFailure { Notices.error(tr("Abgleich ging nicht: {0}", it.message)) }
            c.drive.changed()
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { LoraMirror.keep(context, uri.toString()) }
            c.settings.update { it.copy(loraTree = uri.toString()) }
            sync(uri.toString())
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            tr("Deine Dateien (Hochgeladen, Erstellt, eigene Ordner, Dokumente als Word-, Excel- und PowerPoint-Dateien) liegen in der App. " +
                "Wähle einen Ordner auf dem Handy, z. B. „Dokumente/Lora“ – dann liegt dort eine Kopie, und Änderungen dort kommen zurück. " +
                "Chats und Einstellungen bleiben in der App."),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(loraFolderLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pick.launch(null) }) { Text(if (settings.loraTree.isBlank()) tr("Ordner wählen …") else tr("Anderen Ordner wählen …")) }
            if (settings.loraTree.isNotBlank()) {
                OutlinedButton(enabled = !busy, onClick = { sync(settings.loraTree) }) { Text(if (busy) tr("Gleicht ab …") else tr("Jetzt abgleichen")) }
                TextButton(onClick = { c.settings.update { it.copy(loraTree = "") } }) { Text(tr("Ordner lösen")) }
            }
        }
    }
}
