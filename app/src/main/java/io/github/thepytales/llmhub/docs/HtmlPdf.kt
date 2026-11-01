package io.github.thepytales.llmhub.docs

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.print.PdfPrintBridge
import android.print.PrintAttributes
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.thepytales.llmhub.ui.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Renders HTML/CSS with Android's WebView into an A4 PDF – colors, shapes, fonts and images included. */
object HtmlPdf {
    /** Makes browsers print background colors and images. */
    private const val PRINT_CSS = "<style>html,body{-webkit-print-color-adjust:exact;print-color-adjust:exact}</style>"

    /** How long the page may load; slow external images or fonts must not block the PDF. */
    private const val LOAD_TIMEOUT = 15_000L
    private const val PRINT_TIMEOUT = 45_000L

    /** WebView prints one document at a time; parallel jobs (e.g. a retry after a timeout) would wait on each other. */
    private val lock = Mutex()

    fun withPrintColors(html: String): String {
        val head = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
        return if (head != null) html.substring(0, head.range.last + 1) + PRINT_CSS + html.substring(head.range.last + 1)
        else PRINT_CSS + html
    }

    /**
     * Writes the PDF and returns the number of pages. Fails with a normal exception (not a cancellation)
     * when the WebView does not deliver, so callers can fall back to the simple renderer.
     */
    suspend fun write(context: Context, file: File, html: String): Int = lock.withLock {
        withContext(Dispatchers.Main) {
            val web = createWebView(context)
            try {
                // Unfinished subresources only cost them their place in the PDF, not the whole document.
                withTimeoutOrNull(LOAD_TIMEOUT) { load(web, withPrintColors(html)) }
                val adapter = web.createPrintDocumentAdapter(file.nameWithoutExtension)
                val attributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setResolution(PrintAttributes.Resolution("pdf", "PDF", 600, 600))
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
                withTimeoutOrNull(PRINT_TIMEOUT) {
                    suspendCancellableCoroutine<Unit> { cont ->
                        PdfPrintBridge.write(adapter, attributes, file) { error ->
                            if (cont.isActive) {
                                if (error == null) cont.resume(Unit) else cont.resumeWithException(error)
                            }
                        }
                    }
                } ?: throw IllegalStateException(tr("Die Seite wurde nicht rechtzeitig als PDF gedruckt."))
            } finally {
                web.stopLoading()
                web.destroy()
            }
        }
        val pages = withContext(Dispatchers.IO) { pageCount(file) }
        if (pages <= 0) throw IllegalStateException(tr("Die PDF-Datei ist leer."))
        pages
    }

    private fun createWebView(context: Context) = WebView(context.applicationContext).apply {
        // Documents are static; scripts stay off.
        settings.javaScriptEnabled = false
        settings.allowFileAccess = false
        settings.loadsImagesAutomatically = true
        // An off-screen WebView needs a size before it renders anything (A4 at 96 dpi).
        measure(
            View.MeasureSpec.makeMeasureSpec(A4_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(A4_HEIGHT, View.MeasureSpec.EXACTLY),
        )
        layout(0, 0, A4_WIDTH, A4_HEIGHT)
    }

    /** Waits until the page has loaded – whichever signal of the WebView comes first. */
    private suspend fun load(web: WebView, html: String) {
        suspendCancellableCoroutine<Unit> { cont ->
            fun done() {
                if (cont.isActive) cont.resume(Unit)
            }
            web.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) = done()
            }
            web.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress >= 100) done()
                }
            }
            web.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
        }
        // Give images and fonts a moment to render.
        delay(500)
    }

    fun pageCount(file: File): Int = runCatching {
        val renderer = PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
        try {
            renderer.pageCount
        } finally {
            renderer.close()
        }
    }.getOrDefault(0)

    private const val A4_WIDTH = 794
    private const val A4_HEIGHT = 1123
}
