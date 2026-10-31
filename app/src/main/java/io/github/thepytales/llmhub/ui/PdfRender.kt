package io.github.thepytales.llmhub.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * Pictures of PDF pages for the PDF-Werkzeugkasten (AP-11) with Android's PdfRenderer (fast, native). The Windows
 * version (same path under desktop/) draws with PDFBox. Keeps the last document open and the last pictures cached.
 */
object PdfRender {
    private class Open(val key: String, val fd: ParcelFileDescriptor, val renderer: PdfRenderer)

    private var current: Open? = null
    private val images = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 40
    }

    private fun open(file: File): Open {
        val key = file.absolutePath + ":" + file.lastModified() + ":" + file.length()
        current?.takeIf { it.key == key }?.let { return it }
        current?.let { runCatching { it.renderer.close(); it.fd.close() } }
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return Open(key, fd, PdfRenderer(fd)).also { current = it }
    }

    /** Page [index] of [file] about [widthPx] pixels wide; null if it cannot be drawn. */
    @Synchronized
    fun page(file: File, index: Int, widthPx: Int): ImageBitmap? {
        val cacheKey = "${file.absolutePath}:${file.lastModified()}:${file.length()}:$index:$widthPx"
        images[cacheKey]?.let { return it }
        return runCatching {
            val o = open(file)
            o.renderer.openPage(index).use { page ->
                val w = widthPx.coerceIn(32, 2400)
                val h = (w.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp.asImageBitmap()
            }
        }.getOrNull()?.also { images[cacheKey] = it }
    }

    /** Closes [file] (before it is replaced or deleted). */
    @Synchronized
    fun close(file: File) {
        val o = current ?: return
        if (o.key.startsWith(file.absolutePath + ":")) {
            runCatching { o.renderer.close(); o.fd.close() }
            current = null
        }
    }
}
