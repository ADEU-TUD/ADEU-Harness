package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.ui.tr
import java.io.File

/**
 * PDF-Werkzeugkasten Stufe 3 (AP-26) is for the PC (Windows has its own version of this file with PDFBox 3). On Android
 * only „Vergleichen“ (shared, see PdfCompare) is there.
 */
@Suppress("UNUSED_PARAMETER")
object PdfStage3 {
    const val SUPPORTED = false

    private fun pc(): Nothing = throw UnsupportedOperationException(tr("Diese Funktion gibt es in der Windows-App."))

    fun find(file: File, terms: List<String>, wholeWords: Boolean = true, limit: Int = 2000): List<PdfRedaction> = pc()
    fun redact(file: File, boxes: List<PdfRedaction>, out: File, dpi: Int = 200, cleanMetadata: Boolean = true): Unit = pc()
    fun replaceText(file: File, find: String, replace: String, page: Int?, out: File): Int = pc()
    fun images(file: File): List<PdfImageInfo> = pc()
    fun imagePreview(file: File, page: Int, name: String, maxPx: Int = 240): ByteArray? = pc()
    fun replaceImage(file: File, page: Int, name: String, image: ByteArray?, out: File): Unit = pc()
    fun shrink(file: File, out: File, maxPx: Int = 1600, quality: Float = 0.75f): Pair<Long, Long> = pc()
    fun toPdfA(file: File, title: String, out: File): List<String> = pc()
    fun isPdfA(file: File): Boolean = pc()
    fun protect(file: File, openPassword: String, ownerPassword: String, print: Boolean, copy: Boolean, out: File): Unit = pc()
    fun unprotect(file: File, password: String, out: File): Unit = pc()
    fun encrypted(file: File): Boolean = pc()
    fun meta(file: File): PdfMeta = pc()
    fun setMeta(file: File, m: PdfMeta, clean: Boolean, out: File): Unit = pc()
    fun uaCheck(context: android.content.Context, file: File): List<PdfUaFinding> = pc()
    fun uaFix(context: android.content.Context, file: File, title: String, out: File): Unit = pc()
}
