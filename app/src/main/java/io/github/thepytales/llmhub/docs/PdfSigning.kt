package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.ui.tr
import java.io.File

/**
 * Digitale Signatur (AP-60): signing with a certificate and checking signatures happen in the Windows app (it has the
 * certificate store of Windows); Android says so.
 */
object PdfSigning {
    const val SUPPORTED = false

    private fun pc(): Nothing = throw UnsupportedOperationException(tr("Diese Funktion gibt es in der Windows-App."))

    fun storeCertificates(): List<PdfCert> = emptyList()
    fun fileCertificates(bytes: ByteArray, password: String): List<PdfCert> = pc()
    fun sign(file: File, out: File, cert: String, keyFile: ByteArray?, password: String?, reason: String, location: String, visible: Pair<Int, PdfBox>?): Unit = pc()
    fun verify(file: File): List<PdfSignatureInfo> = pc()
}
