package io.github.thepytales.llmhub.files

import io.github.thepytales.llmhub.docs.OfficeImport
import java.io.File

/** Text of a file in a shared local folder: Office files as Markdown, PDFs and everything else like attachments. */
object LocalText {
    /** Folders on this PC can be shared with the agent. */
    const val SUPPORTED = true

    suspend fun read(file: File): String {
        val ext = file.extension.lowercase()
        if (ext in setOf("docx", "pptx", "xlsx", "xlsm", "csv", "md", "txt")) {
            OfficeImport.read(file.name, file.readBytes())?.let { imported ->
                return imported.tabs.joinToString("\n\n") { (t, md) -> if (imported.tabs.size > 1) "## $t\n\n$md" else md }
            }
        }
        val r = AttachmentReader.read(file)
        return r.file?.text ?: throw IllegalArgumentException(r.error ?: "„${file.name}“ lässt sich nicht als Text lesen.")
    }
}
