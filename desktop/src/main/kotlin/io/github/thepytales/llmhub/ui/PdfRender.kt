package io.github.thepytales.llmhub.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File

/**
 * Pictures of PDF pages for the PDF-Werkzeugkasten (AP-11), Windows version with PDFBox. Keeps the last documents open
 * and the last pictures in a small cache; one page at a time (PDFBox is not thread-safe per document).
 */
object PdfRender {
    private class Open(val key: String, val doc: PDDocument, val renderer: PDFRenderer)

    private val docs = ArrayDeque<Open>()
    private val images = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 48
    }

    private fun open(file: File): Open {
        val key = file.absolutePath + ":" + file.lastModified() + ":" + file.length()
        docs.firstOrNull { it.key == key }?.let { return it }
        val doc = Loader.loadPDF(file)
        val o = Open(key, doc, PDFRenderer(doc))
        docs.addFirst(o)
        while (docs.size > 3) docs.removeLast().doc.close()
        return o
    }

    /** Page [index] of [file] about [widthPx] pixels wide; null if it cannot be drawn. */
    @Synchronized
    fun page(file: File, index: Int, widthPx: Int): ImageBitmap? {
        val cacheKey = "${file.absolutePath}:${file.lastModified()}:${file.length()}:$index:$widthPx"
        images[cacheKey]?.let { return it }
        return runCatching {
            val o = open(file)
            val page = o.doc.getPage(index)
            val rot = ((page.rotation % 360) + 360) % 360
            val w = if (rot % 180 == 90) page.cropBox.height else page.cropBox.width
            val scale = (widthPx / w).coerceIn(0.05f, 6f)
            o.renderer.renderImage(index, scale, ImageType.RGB).toComposeImageBitmap()
        }.getOrNull()?.also { images[cacheKey] = it }
    }

    /** Closes [file] (before it is replaced or deleted). */
    @Synchronized
    fun close(file: File) {
        val prefix = file.absolutePath + ":"
        docs.filter { it.key.startsWith(prefix) }.forEach { it.doc.close(); docs.remove(it) }
    }
}
