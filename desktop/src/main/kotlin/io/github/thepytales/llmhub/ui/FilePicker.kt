package io.github.thepytales.llmhub.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.desktop.DesktopActions
import io.github.thepytales.llmhub.desktop.Toasts
import io.github.thepytales.llmhub.files.AttachmentReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/** Picks documents and reads their text (Windows: file dialog). Returns the function that opens the dialog. */
@Composable
fun rememberDocumentPicker(onRead: (FileAttachment) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val files = withContext(Dispatchers.IO) { DesktopActions.pickFiles("Dokumente auswählen") }
            files.forEach { f ->
                val r = AttachmentReader.read(f)
                r.file?.let(onRead)
                (r.error ?: r.note ?: if (r.file == null) "Aus „${f.name}“ ließ sich kein Text lesen." else null)?.let { Toasts.show(it) }
            }
        }
    }
}

/** "Speichern unter" for a file the app writes into [write]. */
@Composable
fun rememberFileExport(mime: String, onDone: (Boolean) -> Unit, write: suspend (OutputStream) -> Unit): (suggestedName: String) -> Unit {
    val scope = rememberCoroutineScope()
    return { name ->
        scope.launch {
            val ext = name.substringAfterLast('.', "").ifEmpty { null }
            val target = withContext(Dispatchers.IO) { DesktopActions.pickSaveFile("Speichern unter", name, ext) } ?: return@launch
            val ok = withContext(Dispatchers.IO) { runCatching { target.outputStream().use { write(it) } }.isSuccess }
            onDone(ok)
        }
    }
}

/** Opens a file the user picks and hands its content to [read]. */
@Composable
fun rememberFileImport(@Suppress("UNUSED_PARAMETER") mimes: Array<String>, read: suspend (InputStream) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val file = withContext(Dispatchers.IO) { DesktopActions.pickFiles("Datei öffnen", multiple = false).firstOrNull() } ?: return@launch
            withContext(Dispatchers.IO) { file.inputStream().use { read(it) } }
        }
    }
}
