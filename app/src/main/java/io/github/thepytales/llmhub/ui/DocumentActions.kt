package io.github.thepytales.llmhub.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import io.github.thepytales.llmhub.BuildConfig
import io.github.thepytales.llmhub.docs.CalendarFiles
import io.github.thepytales.llmhub.docs.DocFormat
import io.github.thepytales.llmhub.docs.MailFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import androidx.compose.ui.graphics.vector.ImageVector

/** Android actions for created documents (Windows has its own version of this file). */

/**
 * content:// URI of [file] for other apps. The shared folders are in res/xml/file_paths.xml; a file outside of them is
 * copied to the cache first (exports/), so opening or sharing never crashes. Null (with a notice) if even that fails.
 */
fun shareableUri(context: Context, file: File): Uri? {
    val authority = "${BuildConfig.APPLICATION_ID}.files"
    runCatching { return FileProvider.getUriForFile(context, authority, file) }
    runCatching {
        val copy = File(File(context.cacheDir, "exports/" + UUID.randomUUID().toString().take(8)), file.name)
        copy.parentFile?.mkdirs()
        file.copyTo(copy, overwrite = true)
        return FileProvider.getUriForFile(context, authority, copy)
    }
    Notices.error(tr("{0} lässt sich nicht an andere Apps weitergeben.", file.name))
    return null
}

fun openDocument(context: Context, file: File) {
    val uri = shareableUri(context, file) ?: return
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, DocFormat.ofFile(file).mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Notices.error(tr("Keine App zum Öffnen von {0}-Dateien gefunden.", file.extension.uppercase()))
    }
}

/** Opens the mail app with the draft filled in (the user sends it there). */
fun openMailDraft(context: Context, file: File) {
    val d = MailFiles.read(file) ?: return Notices.error(tr("Entwurf nicht lesbar."))
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        .putExtra(Intent.EXTRA_EMAIL, d.to.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray())
        .putExtra(Intent.EXTRA_CC, d.cc.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray())
        .putExtra(Intent.EXTRA_SUBJECT, d.subject)
        .putExtra(Intent.EXTRA_TEXT, d.body)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Notices.error(tr("Keine Mail-App gefunden."))
    }
}

/** Opens the calendar app with the event filled in. */
fun addToCalendar(context: Context, file: File) {
    val e = CalendarFiles.read(file) ?: return Notices.error(tr("Termin nicht lesbar."))
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, CalendarFiles.millis(e.start))
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, CalendarFiles.millis(if (e.allDay) e.end.plusDays(if (e.end == e.start) 1 else 0) else e.end))
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, e.allDay)
        .putExtra(CalendarContract.Events.TITLE, e.title)
        .putExtra(CalendarContract.Events.EVENT_LOCATION, e.location)
        .putExtra(CalendarContract.Events.DESCRIPTION, e.description)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (ex: ActivityNotFoundException) {
        openDocument(context, file)
    }
}

fun shareDocument(context: Context, file: File) {
    val uri = shareableUri(context, file) ?: return
    val intent = Intent(Intent.ACTION_SEND)
        .setType(DocFormat.ofFile(file).mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, tr("Teilen")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private suspend fun copyTo(context: Context, file: File, target: Uri) {
    val ok = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(target)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        }.isSuccess
    }
    if (ok) Notices.info(tr("Gespeichert: {0}", file.name)) else Notices.error(tr("Speichern fehlgeschlagen."))
}

/** Remembers a "save as" launcher that copies a file to a location the user picks. */
@Composable
private fun rememberSaveLauncher(mime: String, onPicked: (Uri) -> Unit) =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri -> if (uri != null) onPicked(uri) }


/** Main action of a document card: e-mail drafts go to the mail app, events to the calendar, the rest opens. */
fun openPrimary(context: Context, file: File) = when (file.extension.lowercase()) {
    "eml" -> openMailDraft(context, file)
    "ics" -> addToCalendar(context, file)
    else -> openDocument(context, file)
}

fun primaryLabel(file: File) = when (file.extension.lowercase()) { "eml" -> tr("In Mail-App"); "ics" -> tr("Zum Kalender"); else -> tr("Öffnen") }

/** "Teilen": the Android share sheet. */
object ShareDoc {
    val icon: ImageVector get() = Icons.Outlined.Share
    val label: String get() = tr("Teilen")
    const val inExport = true
}

val SAVE_AS_LABEL: String get() = tr("Speichern")

/** "Speichern": the system dialog to pick a place; the file is copied there. */
@Composable
fun rememberSaveAs(mime: String): (File) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<File?>(null) }
    val launcher = rememberSaveLauncher(mime) { uri -> pending?.let { f -> scope.launch { copyTo(context, f, uri) } } }
    return { f -> pending = f; launcher.launch(f.name) }
}
