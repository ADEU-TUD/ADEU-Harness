package io.github.thepytales.llmhub.docs

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.print.PdfPrintBridge
import android.print.PrintAttributes
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Renders HTML/CSS with Android's WebView into an A4 PDF – colors, shapes, fonts and images included. */
object HtmlPdf {
    /** Makes browsers print background colors and images. */
    private const val PRINT_CSS = "<style>html,body{-webkit-print-color-adjust:exact;print-color-adjust:exact}</style>"

    fun withPrintColors(html: String): String {
        val head = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
        return if (head != null) html.substring(0, head.range.last + 1) + PRINT_CSS + html.substring(head.range.last + 1)
        else PRINT_CSS + html
    }

    /** Writes the PDF and returns the number of pages. */
    suspend fun write(context: Context, file: File, html: String): Int {
        withContext(Dispatchers.Main) {
            val web = createWebView(context)
            try {
                withTimeout(30_000) { load(web, withPrintColors(html)) }
                val adapter = web.createPrintDocumentAdapter(file.nameWithoutExtension)
                val attributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setResolution(PrintAttributes.Resolution("pdf", "PDF", 600, 600))
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
                withTimeout(60_000) {
                    suspendCancellableCoroutine<Unit> { cont ->
                        PdfPrintBridge.write(adapter, attributes, file) { error ->
                            if (cont.isActive) {
                                if (error == null) cont.resume(Unit) else cont.resumeWithException(error)
                            }
                        }
                    }
                }
            } finally {
                web.destroy()
            }
        }
        val pages = withContext(Dispatchers.IO) { pageCount(file) }
        if (pages <= 0) throw IllegalStateException("Die PDF-Datei ist leer.")
        return pages
    }

    private fun createWebView(context: Context) = WebView(context.applicationContext).apply {
        // Documents are static; scripts stay off.
        settings.javaScriptEnabled = false
        settings.allowFileAccess = false
        settings.loadsImagesAutomatically = true
        // An off-screen WebView needs a size before it renders anything (A4 at 96 dpi).
        layout(0, 0, 794, 1123)
    }

    private suspend fun load(web: WebView, html: String) = suspendCancellableCoroutine<Unit> { cont ->
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Give images and fonts a moment to render.
                web.postDelayed({ if (cont.isActive) cont.resume(Unit) }, 500)
            }
        }
        web.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
    }

    fun pageCount(file: File): Int = runCatching {
        val renderer = PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
        try {
            renderer.pageCount
        } finally {
            renderer.close()
        }
    }.getOrDefault(0)
}
