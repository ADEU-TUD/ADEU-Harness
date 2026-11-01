package io.github.thepytales.llmhub.files

import java.io.File

/**
 * Text of a file that only exists as bytes – a download from Nextcloud (AP-22), an entry of a ZIP with submissions
 * (AP-20). Same rules as attachments (PDF with pages, Word/PowerPoint as Markdown, Excel, text).
 */
object FileText {
    suspend fun read(name: String, bytes: ByteArray): ReadResult {
        val dir = kotlin.io.path.createTempDirectory("lora-text").toFile()
        val file = File(dir, safeName(name)).apply { writeBytes(bytes) }
        return try {
            AttachmentReader.read(file)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** A file name without folders and characters Windows does not allow. */
    fun safeName(name: String): String =
        name.replace('\\', '/').substringAfterLast('/').replace(Regex("[:*?\"<>|\\u0000-\\u001f]"), "_").trim().ifEmpty { "Datei" }.take(180)
}
