package io.github.thepytales.llmhub.ui

import android.content.ClipboardManager
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.thepytales.llmhub.BuildConfig
import io.github.thepytales.llmhub.MainActivity
import io.github.thepytales.llmhub.R
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.files.AttachmentReader
import io.github.thepytales.llmhub.tasks.Notifier
import kotlinx.coroutines.launch
import java.io.File

/** What the chat needs from the platform (Android here; Windows has its own version of this file). */
class ChatPlatform(
    val pickPhotos: () -> Unit,
    /** First tile of the attach sheet (see [CameraTile]): take a photo. */
    val primaryAttach: () -> Unit,
    val pickFiles: () -> Unit,
    /** Picture (screenshot) from the clipboard: Strg+V or „Aus der Zwischenablage“; false if there is none. */
    val paste: () -> Boolean,
    /** Runs [start] once the microphone may be used (asks for the permission first). */
    val withMicrophone: (start: () -> Unit) -> Unit,
    /** Puts a shortcut to a chat on the home screen; null where that does not exist. */
    val pinShortcut: ((Conversation) -> Unit)?,
    /** Keeps the display on (voice mode), or lets it go off again. */
    val keepScreenOn: (Boolean) -> Unit = {},
)

/**
 * Pictures and files for the chat input: photo picker, camera, documents, and what another app shared with
 * ADEU LLM (only into a new chat).
 */
@Composable
fun rememberChatPlatform(
    isNewChat: () -> Boolean,
    images: MutableList<String>,
    files: MutableList<FileAttachment>,
    onText: (String) -> Unit,
    onLoading: (Int) -> Unit,
): ChatPlatform {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

    suspend fun read(uri: Uri) {
        onLoading(1)
        val r = AttachmentReader.read(context, uri)
        onLoading(-1)
        r.file?.let { files.add(it) }
        images.addAll(r.images)
        r.error?.let { Notices.error(it) } ?: r.note?.let { Notices.info(it) }
    }

    /** Screenshot or copied picture from the clipboard, read like a picked photo; false if there is none. */
    fun paste(): Boolean {
        val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip ?: return false
        val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        if (uris.isEmpty()) return false
        scope.launch { uris.take(MAX_IMAGES).forEach { read(it) } }
        return true
    }

    LaunchedEffect(Unit) {
        // Shared from another app: a new chat gets the text in the input field and the files as attachments.
        if (isNewChat()) {
            MainActivity.shared.value?.let { shared ->
                MainActivity.shared.value = null
                if (shared.text.isNotBlank()) onText(shared.text)
                shared.uris.forEach { uri ->
                    val mime = context.contentResolver.getType(uri).orEmpty()
                    if (mime.startsWith("image/")) encodeImage(context, uri)?.let { images.add(it) } else read(uri)
                }
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES)) { uris ->
        scope.launch { uris.forEach { uri -> encodeImage(context, uri)?.let { images.add(it) } } }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) scope.launch { encodeImage(context, uri)?.let { images.add(it) } }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri -> scope.launch { read(uri) } }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) afterPermission?.invoke() else Notices.error(tr("Mikrofon-Berechtigung benötigt."))
        afterPermission = null
    }

    return remember {
        ChatPlatform(
            pickPhotos = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            primaryAttach = {
                try {
                    val f = File(context.cacheDir, "camera/photo_${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
                    val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", f)
                    cameraUri = uri
                    camera.launch(uri)
                } catch (e: ActivityNotFoundException) {
                    Notices.error(tr("Keine Kamera-App gefunden."))
                }
            },
            pickFiles = { filePicker.launch(arrayOf("*/*")) },
            paste = ::paste,
            withMicrophone = { start ->
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
                else {
                    afterPermission = start
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            pinShortcut = { conv ->
                if (!pinChatShortcut(context, conv)) Notices.error(tr("Dein Startbildschirm unterstützt keine Verknüpfungen."))
            },
            keepScreenOn = { on ->
                var ctx: Context? = context
                while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) ctx = ctx.baseContext
                (ctx as? android.app.Activity)?.window?.let { w ->
                    val flag = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    if (on) w.addFlags(flag) else w.clearFlags(flag)
                }
            },
        )
    }
}

/** Label of the share entry in the chat menu. */
val SHARE_LABEL: String get() = tr("Teilen")

/** Puts a shortcut to [conv] on the home screen; false if the launcher cannot do that. */
private fun pinChatShortcut(context: Context, conv: Conversation): Boolean {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
    val intent = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .putExtra(Notifier.EXTRA_CHAT, conv.id)
    val label = conv.title.ifBlank { "Chat" }
    val info = ShortcutInfoCompat.Builder(context, "chat_${conv.id}")
        .setShortLabel(label.take(24))
        .setLongLabel(label.take(60))
        .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
        .setIntent(intent)
        .build()
    return runCatching { ShortcutManagerCompat.requestPinShortcut(context, info, null) }.getOrDefault(false)
}
