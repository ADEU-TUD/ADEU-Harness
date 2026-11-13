package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.App
import io.github.thepytales.llmhub.desktop.CloudFolders
import io.github.thepytales.llmhub.desktop.DesktopActions
import io.github.thepytales.llmhub.desktop.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Where the Lora-Ordner is, in one line (Dateien tab, settings). */
fun loraFolderLabel(): String = Paths.root.absolutePath + (CloudFolders.serviceOf(Paths.root)?.let { " ($it)" } ?: "")

/** The Lora-Ordner can be shown in the Explorer (Windows). */
const val LORA_FOLDER_OPENS = true

fun openLoraFolder(dir: File? = null) = DesktopActions.open(dir ?: Paths.root)

/**
 * Lora-Ordner (AP-16), Windows: one folder for everything like an Obsidian vault – the user's files, and hidden in it
 * the app's data (chats, settings). Put it where it is needed: move it here (copied, Lora restarts there), or move it in
 * the Explorer and then show Lora the new place.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoraFolderSettings() {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<Pair<File, Boolean>?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            tr("Alles liegt in einem Ordner, wie bei Obsidian: deine Dateien (Hochgeladen, Erstellt, eigene Ordner, Dokumente als Word-, " +
                "Excel- und PowerPoint-Dateien) und versteckt im Unterordner „.lora“ Chats und Einstellungen. API-Schlüssel bleiben auf diesem PC."),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(loraFolderLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { openLoraFolder() }) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(tr("Im Explorer öffnen"))
            }
            OutlinedButton(onClick = {
                scope.launch {
                    val picked = FolderAccess.pick(tr("Wohin soll der Lora-Ordner?"))?.let(::File) ?: return@launch
                    // An empty folder or one named „Lora“ is used as it is; otherwise „Lora“ inside it.
                    val dir = if (picked.name.equals("Lora", true) || picked.listFiles().isNullOrEmpty()) picked else File(picked, "Lora")
                    target = dir to false
                }
            }) {
                Icon(Icons.Outlined.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(tr("Verschieben …"))
            }
            OutlinedButton(onClick = {
                scope.launch { FolderAccess.pick(tr("Vorhandenen Lora-Ordner wählen"))?.let { target = File(it) to true } }
            }) { Text(tr("Anderen Lora-Ordner wählen …")) }
            TextButton(onClick = { Navigation.openRoute.value = "settings/sync" }) { Text(tr("Synchronisation …")) }
        }
        HintText(
            tr("Selbst verschieben geht auch: Lora beenden, den Ordner im Explorer verschieben, Lora starten – sie fragt dann, wo er jetzt liegt."),
        )
    }
    target?.let { (dir, existing) ->
        val hasData = existing && Paths.hasData(dir)
        AlertDialog(
            onDismissRequest = { target = null },
            title = { Text(if (existing) tr("Lora-Ordner wechseln") else tr("Lora-Ordner verschieben")) },
            text = {
                Text(
                    when {
                        existing && hasData -> tr("In „{0}“ liegt schon ein Lora-Ordner. Lora startet neu und arbeitet dort weiter.", dir.absolutePath)
                        existing -> tr("„{0}“ enthält noch keinen Lora-Ordner. Lora startet dort neu und leer – zum Mitnehmen deiner Daten „Verschieben …“ nehmen.", dir.absolutePath)
                        else -> tr("Alles wird nach „{0}“ kopiert, dann startet Lora neu und arbeitet dort. Der bisherige Ordner bleibt als Sicherung – ", dir.absolutePath) +
                            tr("löschen, sobald alles da ist.")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    target = null
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                runCatching { App.container.docs.files?.flush() }
                                runCatching { App.container.chats.awaitWrites() }
                                Paths.relocate(dir, copy = !existing)
                            }
                        }.onSuccess { Paths.restart() }.onFailure { Notices.error(tr("Das ging nicht: {0}", it.message)) }
                    }
                }) { Text(if (existing) tr("Wechseln und neu starten") else tr("Kopieren und neu starten")) }
            },
            dismissButton = { TextButton(onClick = { target = null }) { Text(tr("Abbrechen")) } },
        )
    }
}
