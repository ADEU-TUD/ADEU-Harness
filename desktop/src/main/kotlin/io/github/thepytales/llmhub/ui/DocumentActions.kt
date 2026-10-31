package io.github.thepytales.llmhub.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.thepytales.llmhub.desktop.DesktopActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.ui.graphics.vector.ImageVector

/** Windows actions for created documents (Android has its own version of this file). */

/** Opens [file] with the program Windows uses for it (PDF viewer, Word …). */
fun openDocument(context: Context, file: File) {
    if (!DesktopActions.open(file)) Notices.error("Kein Programm zum Öffnen von ${file.extension.uppercase()}-Dateien gefunden.")
}

fun openPrimary(context: Context, file: File) = openDocument(context, file)

@Suppress("UNUSED_PARAMETER")
fun primaryLabel(file: File) = "Öffnen"

/** Shows the file in Explorer – from there it can be dragged into an e-mail, Teams … */
@Suppress("UNUSED_PARAMETER")
fun shareDocument(context: Context, file: File) {
    if (!DesktopActions.showInFolder(file)) Notices.info(file.absolutePath)
}

object ShareDoc {
    val icon: ImageVector get() = Icons.Outlined.FolderOpen
    const val label = "Im Ordner zeigen"
    const val inExport = false
}

const val SAVE_AS_LABEL = "Speichern unter …"

/** "Speichern unter": copies the file to a place the user picks (Documents\Lora by default). */
@Suppress("UNUSED_PARAMETER")
@Composable
fun rememberSaveAs(mime: String): (File) -> Unit {
    val scope = rememberCoroutineScope()
    return { file ->
        scope.launch {
            val target = withContext(Dispatchers.IO) { DesktopActions.pickSaveFile("Speichern unter", file.name, file.extension) } ?: return@launch
            val ok = withContext(Dispatchers.IO) { runCatching { file.copyTo(target, overwrite = true) }.isSuccess }
            if (ok) Notices.info("Gespeichert: ${target.absolutePath}") else Notices.error("Speichern fehlgeschlagen.")
        }
    }
}
