package io.github.thepytales.llmhub.files

import io.github.thepytales.llmhub.docs.OfficeImport
import java.io.File

/** Text of a file in a shared local folder (Windows has its own version of this file that also reads PDFs). */
object LocalText {
    /** Whether folders on this device can be shared with the agent (Android: no plain file access). */
    const val SUPPORTED = false

    suspend fun read(file: File): String {
        val bytes = file.readBytes()
        OfficeImport.read(file.name, bytes)?.let { imported -> return imported.tabs.joinToString("\n\n") { it.second } }
        return bytes.toString(Charsets.UTF_8)
    }
}
