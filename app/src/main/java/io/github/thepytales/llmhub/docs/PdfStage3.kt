package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.ui.tr
import java.io.File

/**
 * PDF-Werkzeugkasten Stufe 3 (AP-26) is for the PC (Windows has its own version of this file with PDFBox 3). On Android
 * only „Vergleichen“ (shared, see PdfCompare) and – since AP-64 – „Verkleinern“ (scans as mail attachments) are there.
 */
@Suppress("UNUSED_PARAMETER")
object PdfStage3 {
    const val SUPPORTED = false
    /** „Verkleinern“ works here too (AP-64). */
    const val SHRINK = true

    private fun pc(): Nothing = throw UnsupportedOperationException(tr("Diese Funktion gibt es in der Windows-App."))

    fun find(file: File, terms: List<String>, wholeWords: Boolean = true, limit: Int = 2000): List<PdfRedaction> = pc()
    fun redact(file: File, boxes: List<PdfRedaction>, out: File, dpi: Int = 200, cleanMetadata: Boolean = true): Unit = pc()
    fun replaceText(file: File, find: String, replace: String, page: Int?, out: File): Int = pc()
    fun images(file: File): List<PdfImageInfo> = pc()
    fun imagePreview(file: File, page: Int, name: String, maxPx: Int = 240): ByteArray? = pc()
    fun replaceImage(file: File, page: Int, name: String, image: ByteArray?, out: File): Unit = pc()
    /**
     * Makes [file] smaller (AP-64, Android with pdfbox-android): pictures larger than [maxPx] or stored raw are drawn
     * again smaller and saved as JPEG; pictures with transparency stay. Returns the sizes before and after.
     */
    fun shrink(file: File, out: File, maxPx: Int = 1600, quality: Float = 0.75f): Pair<Long, Long> {
        PdfTools.load(file).use { doc ->
            val done = HashMap<Any, com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject>()
            for (page in doc.pages) {
                val res = page.resources ?: continue
                for (name in res.xObjectNames.toList()) {
                    val x = runCatching { res.getXObject(name) }.getOrNull() as? com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject ?: continue
                    if (x.isStencil || runCatching { x.softMask }.getOrNull() != null) continue
                    val already = done[x.cosObject]
                    if (already != null) {
                        res.put(name, already)
                        continue
                    }
                    val big = maxOf(x.width, x.height) > maxPx
                    val raw = x.cosObject.length > 200_000
                    if (!big && !raw) continue
                    val bmp = runCatching { x.image }.getOrNull() ?: continue
                    val f = maxPx.toFloat() / maxOf(bmp.width, bmp.height)
                    val smaller = if (big) android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt().coerceAtLeast(1), (bmp.height * f).toInt().coerceAtLeast(1), true) else bmp
                    val next = com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory.createFromImage(doc, smaller, quality)
                    if (next.cosObject.length < x.cosObject.length) {
                        res.put(name, next)
                        done[x.cosObject] = next
                    }
                }
            }
            doc.save(out)
        }
        return file.length() to out.length()
    }
    fun toPdfA(file: File, title: String, out: File): List<String> = pc()
    fun isPdfA(file: File): Boolean = pc()
    fun protect(file: File, openPassword: String, ownerPassword: String, print: Boolean, copy: Boolean, out: File): Unit = pc()
    fun unprotect(file: File, password: String, out: File): Unit = pc()
    fun encrypted(file: File): Boolean = pc()
    fun meta(file: File): PdfMeta = pc()
    fun setMeta(file: File, m: PdfMeta, clean: Boolean, out: File): Unit = pc()
    fun uaCheck(context: android.content.Context, file: File): List<PdfUaFinding> = pc()
    fun uaFix(context: android.content.Context, file: File, title: String, out: File): Unit = pc()
    fun autoTag(file: File, out: File, title: String, language: String = "de-DE", alt: Map<String, String> = emptyMap()): PdfTagReport = pc()
}
