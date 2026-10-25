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

/** Audio and video formats ffmpeg reads (for the file dialog). */
private val MEDIA = listOf("mp3", "m4a", "wav", "ogg", "opus", "oga", "flac", "aac", "wma", "amr", "webm", "mp4", "mkv", "mov", "avi", "wmv", "m4v", "3gp", "mpg", "mpeg")

/** Picks audio and video files; hands each one's name, size and content to [onPicked] (transcription). */
@Composable
fun rememberMediaImport(anyFile: Boolean = false, onPicked: suspend (name: String, size: Long, input: InputStream) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val files = withContext(Dispatchers.IO) { DesktopActions.pickFiles(if (anyFile) "Dateien auswählen" else "Audio- oder Videodatei öffnen", multiple = true, extensions = if (anyFile) emptyList() else MEDIA) }
            files.forEach { f ->
                runCatching { onPicked(f.name, f.length(), withContext(Dispatchers.IO) { f.inputStream() }) }
                    .onFailure { Toasts.show("„${f.name}“ konnte nicht geöffnet werden: ${it.message}") }
            }
        }
    }
}

/** Windows asks for the microphone itself (privacy settings); the recording reports when access is denied. */
@Composable
fun rememberMicPermission(onGranted: () -> Unit): () -> Unit = onGranted

/** Picks one picture (file dialog) and hands over its bytes, null if it could not be read. */
@Composable
fun rememberImageBytesPicker(onPicked: (ByteArray?) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            onPicked(withContext(Dispatchers.IO) {
                DesktopActions.pickFiles("Bild auswählen", multiple = false, extensions = listOf("png", "jpg", "jpeg", "gif", "bmp"))
                    .firstOrNull()?.let { runCatching { it.readBytes() }.getOrNull() }
            })
        }
    }
}
