package io.github.thepytales.llmhub.ui

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
                (r.error ?: r.note ?: if (r.file == null) tr("Aus dieser Datei ließ sich kein Text lesen.") else null)
                    ?.let { Notices.error(it) }
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

/** Picks audio and video files; hands each one's name, size and content to [onPicked] (transcription). */
@Composable
fun rememberMediaImport(anyFile: Boolean = false, onPicked: suspend (name: String, size: Long, input: InputStream) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            scope.launch {
                runCatching {
                    var name = "Audio"
                    var size = -1L
                    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { c.getString(it)?.let { n -> name = n } }
                            c.getColumnIndex(android.provider.OpenableColumns.SIZE).takeIf { it >= 0 }?.let { if (!c.isNull(it)) size = c.getLong(it) }
                        }
                    }
                    val input = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri) } ?: error(tr("Datei nicht lesbar"))
                    onPicked(name, size, input)
                }.onFailure { Notices.error(tr("Die Datei konnte nicht geöffnet werden: {0}", it.message)) }
            }
        }
    }
    return { launcher.launch(if (anyFile) arrayOf("*/*") else arrayOf("audio/*", "video/*")) }
}

/** Asks for the microphone if needed and then runs [onGranted] (Windows: runs it directly). */
@Composable
fun rememberMicPermission(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) onGranted() else Notices.error(tr("Ohne Mikrofon-Berechtigung ist keine Aufnahme möglich."))
    }
    return {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) onGranted()
        else launcher.launch(android.Manifest.permission.RECORD_AUDIO)
    }
}

/** Picks one picture (photo picker) and hands over its bytes, null if it could not be read. */
@Composable
fun rememberImageBytesPicker(onPicked: (ByteArray?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            onPicked(withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() })
        }
    }
    return { launcher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}
