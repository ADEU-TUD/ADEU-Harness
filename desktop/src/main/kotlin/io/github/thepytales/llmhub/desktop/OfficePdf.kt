package io.github.thepytales.llmhub.desktop

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Variant.VARIANT
import java.io.File

/**
 * Word, Excel and PowerPoint → PDF with the Microsoft Office installed on the PC (AP-15 „Umwandeln“): exactly as Office
 * prints it (layout, fonts, pictures). Over COM like Outlook (AP-13). An Office the user has open is left as it is;
 * only an instance started here is closed again. Without Office the caller converts with the app's own reader.
 */
object OfficePdf {
    private val thread = ComThread("office-com")

    /** Whether [input] is something Office can open (by extension). */
    fun handles(input: File): Boolean = app(input.extension.lowercase()) != null

    private fun app(ext: String): String? = when (ext) {
        "doc", "docx", "docm", "dot", "dotx", "odt", "rtf" -> "Word.Application"
        "xls", "xlsx", "xlsm", "ods" -> "Excel.Application"
        "ppt", "pptx", "pptm", "odp" -> "PowerPoint.Application"
        else -> null
    }

    /** Converts [input] to [out]; false when Office (or that program) is missing or the export failed. */
    fun convert(input: File, out: File): Boolean {
        if (!Platform.isWindows()) return false
        val progId = app(input.extension.lowercase()) ?: return false
        out.delete()
        val ok = runCatching {
            thread.run(240, onTimeout = { IllegalStateException("Office antwortet nicht.") }) {
                val app = ComObj(progId)
                // Started here (invisible, no documents of the user)? Then it is closed at the end.
                val own = !app.bool("Visible")
                try {
                    when (progId) {
                        "Word.Application" -> {
                            if (own) runCatching { app.put("DisplayAlerts", 0) }
                            val doc = app.obj("Documents").obj("Open", VARIANT(input.absolutePath), VARIANT(false), VARIANT(true), VARIANT(false))
                            try {
                                doc.run("ExportAsFixedFormat", VARIANT(out.absolutePath), VARIANT(17))
                            } finally {
                                runCatching { doc.run("Close", VARIANT(0)) }
                            }
                        }
                        "Excel.Application" -> {
                            if (own) runCatching { app.put("DisplayAlerts", false) }
                            val book = app.obj("Workbooks").obj("Open", VARIANT(input.absolutePath), VARIANT(0), VARIANT(true))
                            try {
                                book.run("ExportAsFixedFormat", VARIANT(0), VARIANT(out.absolutePath))
                            } finally {
                                runCatching { book.run("Close", VARIANT(false)) }
                            }
                        }
                        else -> {
                            // ReadOnly, not Untitled, without a window.
                            val pres = app.obj("Presentations").obj("Open", VARIANT(input.absolutePath), VARIANT(-1), VARIANT(0), VARIANT(0))
                            try {
                                pres.run("SaveAs", VARIANT(out.absolutePath), VARIANT(32))
                            } finally {
                                runCatching { pres.run("Close") }
                            }
                        }
                    }
                } finally {
                    val others = runCatching {
                        when (progId) {
                            "Word.Application" -> app.obj("Documents").num("Count")
                            "Excel.Application" -> app.obj("Workbooks").num("Count")
                            else -> app.obj("Presentations").num("Count")
                        }
                    }.getOrDefault(1L)
                    if (own && others == 0L) runCatching { if (progId == "Word.Application") app.run("Quit", VARIANT(0)) else app.run("Quit") }
                }
            }
        }.isSuccess
        return ok && out.isFile && out.length() > 0
    }
}
