package io.github.thepytales.llmhub.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.desktop.DesktopActions
import io.github.thepytales.llmhub.desktop.DroppedFiles
import io.github.thepytales.llmhub.desktop.Images
import io.github.thepytales.llmhub.files.AttachmentReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What the chat needs from the platform (Windows here; Android has its own version of this file). */
class ChatPlatform(
    val pickPhotos: () -> Unit,
    /** First tile of the attach sheet (see [CameraTile]): paste from the clipboard. */
    val primaryAttach: () -> Unit,
    val pickFiles: () -> Unit,
    /** Strg+V: true when it attached a screenshot or copied files. */
    val paste: () -> Boolean,
    /** Runs [start] once the microphone may be used. */
    val withMicrophone: (start: () -> Unit) -> Unit,
    /** Puts a shortcut to a chat on the home screen; null where that does not exist. */
    val pinShortcut: ((Conversation) -> Unit)?,
)

private val imageExtensions = listOf("png", "jpg", "jpeg", "gif", "bmp")

/** Pictures and files for the chat input: file dialogs, clipboard and files dropped onto the window. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun rememberChatPlatform(
    isNewChat: () -> Boolean,
    images: MutableList<String>,
    files: MutableList<FileAttachment>,
    onText: (String) -> Unit,
    onLoading: (Int) -> Unit,
): ChatPlatform {
    val scope = rememberCoroutineScope()

    /** Pictures go to the vision model, everything else is read as document. */
    fun attach(picked: List<File>) {
        picked.forEach { file ->
            onLoading(1)
            scope.launch {
                if (file.extension.lowercase() in imageExtensions) {
                    val img = encodeImage(file)
                    onLoading(-1)
                    if (img != null) images.add(img) else Notices.error("„${file.name}“ konnte nicht gelesen werden.")
                } else {
                    val r = AttachmentReader.read(file)
                    onLoading(-1)
                    r.file?.let { files.add(it) }
                    images.addAll(r.images)
                    r.error?.let { Notices.error(it) } ?: r.note?.let { Notices.info(it) }
                }
            }
        }
    }

    /** Screenshot or copied files from the clipboard; false if there is neither. */
    fun paste(): Boolean {
        val img = DesktopActions.clipboardImage()
        if (img != null) {
            images.add(Images.jpegDataUrl(img))
            return true
        }
        val copied = DesktopActions.clipboardFiles()
        if (copied.isNotEmpty()) {
            attach(copied)
            return true
        }
        return false
    }

    LaunchedEffect(Unit) {
        DroppedFiles.files.collect { attach(it) }
    }

    return remember {
        ChatPlatform(
            pickPhotos = {
                scope.launch { attach(withContext(Dispatchers.IO) { DesktopActions.pickFiles("Bilder auswählen", extensions = imageExtensions) }.take(6)) }
            },
            primaryAttach = { if (!paste()) Notices.info("Die Zwischenablage enthält kein Bild und keine Dateien.") },
            pickFiles = { scope.launch { attach(withContext(Dispatchers.IO) { DesktopActions.pickFiles("Dateien auswählen") }) } },
            paste = ::paste,
            withMicrophone = { start -> start() },
            pinShortcut = null,
        )
    }
}

/** Label of the share entry in the chat menu (Windows copies the text). */
const val SHARE_LABEL = "Kopieren (zum Teilen)"
