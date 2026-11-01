package io.github.thepytales.llmhub.docs

import java.io.File

/**
 * What the PDF-Werkzeugkasten Stufe 2 (AP-15) can use on this platform. Android: no text recognition on the device yet
 * (a vision model can read scans, see PdfOcr) and no Office – files are converted with the app's own readers.
 */
object PdfPlatform {
    val localOcr: LocalOcr? = null

    /** Converts an Office file with an installed Office; false when there is none. */
    @Suppress("UNUSED_PARAMETER")
    fun officeToPdf(input: File, out: File): Boolean = false

    const val officeName: String = ""
}
