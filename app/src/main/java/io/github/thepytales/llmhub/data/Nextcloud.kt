package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.files.FileText
import io.github.thepytales.llmhub.files.ReadResult
import io.github.thepytales.llmhub.net.DavEntry
import io.github.thepytales.llmhub.net.WebDavClient
import io.github.thepytales.llmhub.ui.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URLEncoder

/**
 * Nextcloud fest eingebaut (AP-22): the app's way into the Nextcloud of Einstellungen › Verbindungen – browse, open and
 * attach files, save results there. Shared by Android and Windows (WebDAV over OkHttp); Android's sync of the
 * Lora-Ordner (AP-28) can build on it.
 */
class NextcloudService(private val c: AppContainer) {
    val configured: Boolean get() = c.settings.current.hasNextcloud

    fun client(): WebDavClient {
        val s = c.settings.current
        if (!s.hasNextcloud) throw IOException(tr("Nextcloud ist noch nicht eingerichtet (Einstellungen › Verbindungen › Nextcloud)."))
        return WebDavClient(c.llm.http, s.nextcloudServer, s.nextcloudUser, s.nextcloudPassword)
    }

    /** Folder for results (Einstellungen › Verbindungen › Nextcloud › Ordner für Ergebnisse). */
    val resultFolder: String get() = WebDavClient.normalize(c.settings.current.nextcloudFolder.ifBlank { "/Lora" })

    suspend fun list(path: String): List<DavEntry> = client().list(path)

    /** Downloads [path] into the cache (folder „nextcloud“) and returns the file. */
    suspend fun fetch(path: String): File = withContext(Dispatchers.IO) {
        val bytes = client().download(path)
        val dir = File(c.appContext.cacheDir, "nextcloud/" + Integer.toHexString(path.hashCode())).apply { mkdirs() }
        File(dir, FileText.safeName(path.substringAfterLast('/'))).apply { writeBytes(bytes) }
    }

    /** A file of Nextcloud as attachment: its text (PDF, Word, Excel …), read on the device. */
    suspend fun attach(path: String): ReadResult {
        val bytes = client().download(path, maxBytes = 25L * 1024 * 1024)
        return FileText.read(path.substringAfterLast('/'), bytes)
    }

    /** Copies [path] into the Lora-Ordner („Hochgeladen“) to open it in the app; returns the copy. */
    suspend fun import(path: String, chatId: String? = null): File? = withContext(Dispatchers.IO) {
        val bytes = client().download(path)
        c.drive.keepUpload(path.substringAfterLast('/'), bytes, chatId)
    }

    /**
     * Saves [file] in Nextcloud in [folder] (the result folder by default). An existing file of the same name is kept:
     * the new one gets „ (2)“ – unless [overwrite]. Returns the path in Nextcloud.
     */
    suspend fun save(file: File, folder: String = resultFolder, overwrite: Boolean = false): String =
        saveBytes(file.name, withContext(Dispatchers.IO) { file.readBytes() }, folder, overwrite)

    /** Like [save], but streams the file (a video of several hundred MB, AP-57); returns the path in Nextcloud. */
    suspend fun saveLarge(file: File, folder: String = resultFolder): String {
        val dav = client()
        val clean = FileText.safeName(file.name)
        var target = WebDavClient.join(folder, clean)
        var n = 2
        val base = clean.substringBeforeLast('.', clean)
        val ext = clean.substringAfterLast('.', "").let { if (it.isEmpty() || it == clean) "" else ".$it" }
        while (dav.exists(target) && n < 100) target = WebDavClient.join(folder, "$base ($n)$ext").also { n++ }
        dav.uploadFile(target, file, mimeOf(clean))
        return target
    }

    suspend fun saveBytes(name: String, bytes: ByteArray, folder: String = resultFolder, overwrite: Boolean = false): String {
        val dav = client()
        val clean = FileText.safeName(name)
        var target = WebDavClient.join(folder, clean)
        if (!overwrite) {
            var n = 2
            val base = clean.substringBeforeLast('.', clean)
            val ext = clean.substringAfterLast('.', "").let { if (it.isEmpty() || it == clean) "" else ".$it" }
            while (dav.exists(target) && n < 100) target = WebDavClient.join(folder, "$base ($n)$ext").also { n++ }
        }
        dav.upload(target, bytes, mimeOf(clean))
        return target
    }

    /** The folder of [path] in the Nextcloud web interface. */
    fun webLink(path: String): String {
        val s = c.settings.current
        val dir = if (path.endsWith("/")) path else WebDavClient.parent(path)
        return WebDavClient.serverBase(s.nextcloudServer) + "/index.php/apps/files/?dir=" + URLEncoder.encode(dir, "UTF-8").replace("+", "%20")
    }

    companion object {
        /** TU Dresden's Nextcloud (cloudstore of the ZIH); suggested in the settings. */
        const val TUD_SERVER = "https://cloudstore.zih.tu-dresden.de"

        fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "md" -> "text/markdown; charset=utf-8"
            "txt" -> "text/plain; charset=utf-8"
            "csv" -> "text/csv; charset=utf-8"
            "html", "htm" -> "text/html; charset=utf-8"
            "json" -> "application/json"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "zip" -> "application/zip"
            "ics" -> "text/calendar; charset=utf-8"
            else -> "application/octet-stream"
        }

        /** „12 KB“, „3,4 MB“. */
        fun size(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${(bytes + 1023) / 1024} KB"
            else -> String.format(java.util.Locale.GERMANY, "%.1f MB", bytes / 1048576.0)
        }
    }
}
