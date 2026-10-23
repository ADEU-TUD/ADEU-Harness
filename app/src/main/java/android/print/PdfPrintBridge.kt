package android.print

import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Runs a [PrintDocumentAdapter] (e.g. of a WebView) without the system print dialog and writes the
 * result as PDF. Lives in android.print because the result callbacks have package-private constructors.
 */
object PdfPrintBridge {
    fun write(adapter: PrintDocumentAdapter, attributes: PrintAttributes, file: File, onDone: (Throwable?) -> Unit) {
        val cancel = CancellationSignal()
        adapter.onStart()
        adapter.onLayout(null, attributes, cancel, object : PrintDocumentAdapter.LayoutResultCallback() {
            override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                val fd = try {
                    ParcelFileDescriptor.open(
                        file,
                        ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE,
                    )
                } catch (e: Exception) {
                    adapter.onFinish()
                    onDone(e)
                    return
                }
                adapter.onWrite(arrayOf(PageRange.ALL_PAGES), fd, cancel, object : PrintDocumentAdapter.WriteResultCallback() {
                    override fun onWriteFinished(pages: Array<out PageRange>?) = finish(null)

                    override fun onWriteFailed(error: CharSequence?) =
                        finish(IllegalStateException(error?.toString() ?: "PDF konnte nicht geschrieben werden."))

                    override fun onWriteCancelled() = finish(IllegalStateException("PDF-Erstellung abgebrochen."))

                    private fun finish(error: Throwable?) {
                        runCatching { fd.close() }
                        adapter.onFinish()
                        onDone(error)
                    }
                })
            }

            override fun onLayoutFailed(error: CharSequence?) {
                adapter.onFinish()
                onDone(IllegalStateException(error?.toString() ?: "Seitenlayout fehlgeschlagen."))
            }

            override fun onLayoutCancelled() {
                adapter.onFinish()
                onDone(IllegalStateException("PDF-Erstellung abgebrochen."))
            }
        }, null)
    }
}
