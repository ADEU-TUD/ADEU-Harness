package io.github.thepytales.llmhub.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.files.AttachmentReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/** Picks documents and reads their text (Android: system file picker). Returns the function that opens the picker. */
@Composable
fun rememberDocumentPicker(onRead: (FileAttachment) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            scope.launch {
                val r = AttachmentReader.read(context, uri)
                r.file?.let(onRead)
                (r.error ?: r.note ?: if (r.file == null) "Aus dieser Datei ließ sich kein Text lesen." else null)
                    ?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
            }
        }
    }
    return { launcher.launch(arrayOf("*/*")) }
}

/** "Speichern unter" for a file the app writes into [write]; returns the function that asks for the place. */
@Composable
fun rememberFileExport(mime: String, onDone: (Boolean) -> Unit, write: suspend (OutputStream) -> Unit): (suggestedName: String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)!!.use { write(it) } }.isSuccess
            }
            onDone(ok)
        }
    }
    return { name -> launcher.launch(name) }
}

/** Opens a file the user picks and hands its content to [read]. */
@Composable
fun rememberFileImport(mimes: Array<String>, read: suspend (InputStream) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { read(it) } }
        }
    }
    return { launcher.launch(mimes) }
}
