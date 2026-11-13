package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.data.LoraDrive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lora-Dateidialog auf Android (AP-64), where the system dialog does not reach: a folder of the app's own Lora-Ordner
 * (synced with the PC) as the place to save. The folders as a tree, a new folder on the spot, and – when [onElsewhere] is
 * given – „Anderswo …“ for the Android dialog (Downloads, Drive, a USB stick).
 */
@Composable
fun LoraFolderPicker(
    drive: LoraDrive,
    title: String,
    confirm: String,
    onDismiss: () -> Unit,
    onElsewhere: (() -> Unit)? = null,
    onPick: (File) -> Unit,
) {
    var reload by remember { mutableStateOf(0) }
    val folders by produceState(emptyList<String>(), reload) { value = withContext(Dispatchers.IO) { runCatching { drive.allFolders() }.getOrDefault(listOf("")) } }
    var chosen by remember { mutableStateOf(LoraDrive.CREATED) }
    var newName by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.heightIn(max = 460.dp)) {
                HintText(tr("Im Lora-Ordner – über den Sync auch am PC."))
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(folders) { f ->
                        val depth = if (f.isEmpty()) 0 else f.count { it == '/' } + 1
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(if (chosen == f) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .clickable { chosen = f }.padding(start = (8 + depth * 16).dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(if (f.isEmpty()) Icons.Outlined.FolderOpen else Icons.Outlined.Folder, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (f.isEmpty()) tr("Meine Dateien") else f.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                val name = newName
                if (name == null) TextButton(onClick = { newName = "" }) {
                    Icon(Icons.Outlined.CreateNewFolder, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Neuer Ordner hier"))
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(name, { newName = it }, singleLine = true, label = { Text(tr("Name des Ordners")) }, modifier = Modifier.weight(1f))
                    TextButton(enabled = name.isNotBlank(), onClick = {
                        val parent = if (chosen.isEmpty()) drive.root else File(drive.root, chosen)
                        val dir = LoraDrive.unique(parent, LoraDrive.safe(name.trim()), isDir = true).apply { mkdirs() }
                        chosen = drive.rel(dir) ?: chosen
                        newName = null
                        reload++
                        drive.changed()
                    }) { Text(tr("Anlegen")) }
                }
            }
        },
        confirmButton = { Button(onClick = { onPick(if (chosen.isEmpty()) drive.root else File(drive.root, chosen)) }) { Text(confirm) } },
        dismissButton = {
            Row {
                onElsewhere?.let { TextButton(onClick = it) { Text(tr("Anderswo …")) } }
                TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) }
            }
        },
    )
}

/** Copies [file] into [dir] under a free name; the copy (for the notice). */
fun saveIntoLoraFolder(drive: LoraDrive, file: File, dir: File): File {
    dir.mkdirs()
    val target = LoraDrive.unique(dir, file.name)
    file.copyTo(target)
    drive.changed()
    return target
}
