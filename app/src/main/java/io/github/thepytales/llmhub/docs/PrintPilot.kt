package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.ui.tr
import java.io.File

/** Druck-Lotse (AP-97) is a PC feature; the Windows version of this file does the work. */
object PrintPilot {
    const val AVAILABLE = false

    private fun pc(): Nothing = throw UnsupportedOperationException(tr("Diese Funktion gibt es in der Windows-App."))

    fun fonts(file: File): List<PdfFontInfo> = emptyList()
    fun check(file: File, minDpi: Int = 250): PrintCheck = pc()
    fun booklet(file: File, out: File): Unit = pc()
    fun nUp(file: File, out: File, perSheet: Int): Unit = pc()
    fun handout(file: File, out: File): Unit = pc()
    fun duplex(file: File, out: File, gutterMm: Double = 10.0): Unit = pc()
    fun bleed(file: File, out: File, bleedMm: Double = 3.0, marks: Boolean = true): Boolean = pc()
}
