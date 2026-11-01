package io.github.thepytales.llmhub.files

import android.net.Uri
import java.io.File

/**
 * Text of a file that only exists as bytes – a download from Nextcloud (AP-22), an entry of a ZIP with submissions
 * (AP-20). Same rules as attachments (PDF with pages, Word/PowerPoint as Markdown, Excel, text). Windows has its own
 * version of this file.
 */
object FileText {
    suspend fun read(name: String, bytes: ByteArray): ReadResult {
        val context = io.github.thepytales.llmhub.App.container.appContext
        val dir = File(context.cacheDir, "filetext/" + java.util.UUID.randomUUID()).apply { mkdirs() }
        val file = File(dir, safeName(name)).apply { writeBytes(bytes) }
        return try {
            AttachmentReader.read(context, Uri.fromFile(file))
        } finally {
            dir.deleteRecursively()
        }
    }

    /** A file name without folders and characters Windows and Android do not allow. */
    fun safeName(name: String): String =
        name.replace('\\', '/').substringAfterLast('/').replace(Regex("[:*?\"<>|\\u0000-\\u001f]"), "_").trim().ifEmpty { "Datei" }.take(180)
}
