package io.github.thepytales.llmhub.files

import io.github.thepytales.llmhub.docs.OfficeImport
import java.io.File

/** Text of a file in a shared local folder (Windows has its own version of this file that also reads PDFs). */
object LocalText {
    /** Whether folders on this device can be shared with the agent (Android: no plain file access). */
    const val SUPPORTED = false

    suspend fun read(file: File): String = read(file.name, file.readBytes())

    suspend fun read(name: String, bytes: ByteArray): String {
        if (name.substringAfterLast('.', "").lowercase() in setOf("docx", "pptx", "xlsx", "xlsm")) {
            OfficeImport.read(name, bytes)?.let { imported -> return imported.tabs.joinToString("\n\n") { it.second } }
        }
        if (FormatReaders.handles(name)) FormatReaders.text(name, bytes)?.let { return it }
        return TextCodec.text(bytes)
    }

    suspend fun searchText(file: File): String? = runCatching { read(file) }.getOrNull()
}
