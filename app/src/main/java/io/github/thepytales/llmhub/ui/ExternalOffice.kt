package io.github.thepytales.llmhub.ui

import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * In Word/Excel/PowerPoint öffnen und zurück (AP-59): the Windows app opens a document's real file in its Office program
 * and watches it – while it is open there the editor says so, and a save there comes back into the app. Android has no
 * [opener] (the documents open in the app).
 */
object ExternalOffice {
    /** Opens a file in its Office program and watches it; null where there is none (Android). */
    @Volatile
    var opener: ((File) -> Boolean)? = null

    /** Files (absolute paths) open in Word, Excel or PowerPoint now. */
    val open = MutableStateFlow<Set<String>>(emptySet())

    /** „Word“, „Excel“, „PowerPoint“ for [f], or null for other files. */
    fun program(f: File): String? = when (f.extension.lowercase()) {
        "docx", "doc", "docm", "dotx", "rtf", "odt" -> "Word"
        "xlsx", "xlsm", "xls", "ods" -> "Excel"
        "pptx", "ppt", "pptm", "odp" -> "PowerPoint"
        else -> null
    }

    fun isOpen(f: File?): Boolean = f != null && f.absolutePath in open.value
}
