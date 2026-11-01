package io.github.thepytales.llmhub.docs

import com.sun.jna.Platform
import io.github.thepytales.llmhub.desktop.OfficePdf
import io.github.thepytales.llmhub.desktop.WindowsOcr
import java.io.File

/**
 * What the PDF-Werkzeugkasten Stufe 2 (AP-15) can use on this PC: the text recognition of Windows and an installed
 * Microsoft Office for Word/Excel/PowerPoint → PDF (otherwise the app's own readers convert).
 */
object PdfPlatform {
    val localOcr: LocalOcr? = if (Platform.isWindows()) WindowsOcr else null

    fun officeToPdf(input: File, out: File): Boolean = OfficePdf.convert(input, out)

    val officeName: String = if (Platform.isWindows()) "Microsoft Office" else ""
}
