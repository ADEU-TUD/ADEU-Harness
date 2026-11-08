package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.desktop.Os
import io.github.thepytales.llmhub.ui.tr
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.IOUtils
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.PDResources
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.common.PDStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x500.style.IETFUtils
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Digitale Signatur (AP-60), Windows: a PDF signed with a certificate from the Windows certificate store (the private
 * key stays there – Windows signs, „Windows-MY“) or from a PKCS#12 file (.p12/.pfx with password), as a CMS signature
 * (PAdES basic, adbe.pkcs7.detached) with an optional visible field; existing signatures are checked – the signed bytes
 * unchanged, the whole file covered, the certificate trusted by this PC.
 */
object PdfSigning {
    const val SUPPORTED = true

    private const val WINDOWS_STORE = "Windows-MY"

    private fun nameOf(c: X509Certificate): String = runCatching {
        val n = X500Name(c.subjectX500Principal.name)
        n.getRDNs(BCStyle.CN).firstOrNull()?.first?.value?.let { IETFUtils.valueToString(it) }
    }.getOrNull() ?: c.subjectX500Principal.name

    private fun info(alias: String, c: X509Certificate) = PdfCert(alias, nameOf(c), runCatching {
        X500Name(c.issuerX500Principal.name).getRDNs(BCStyle.CN).firstOrNull()?.first?.value?.let { IETFUtils.valueToString(it) }
    }.getOrNull() ?: c.issuerX500Principal.name, c.notAfter.time)

    /** Certificates with a private key in the Windows certificate store („Eigene Zertifikate“); empty elsewhere. */
    fun storeCertificates(): List<PdfCert> {
        if (!Os.windows) return emptyList()
        return runCatching {
            val ks = KeyStore.getInstance(WINDOWS_STORE).apply { load(null, null) }
            ks.aliases().toList().filter { ks.isKeyEntry(it) }.mapNotNull { a -> (ks.getCertificate(a) as? X509Certificate)?.let { info(a, it) } }
        }.getOrDefault(emptyList())
    }

    /** A PKCS#12 file read into memory (the private key never goes into a cache file). */
    private fun pkcs12(bytes: ByteArray, password: String): KeyStore =
        KeyStore.getInstance("PKCS12").apply { load(ByteArrayInputStream(bytes), password.toCharArray()) }

    /** The certificates with a key in a PKCS#12 file ([bytes] of the .p12/.pfx). */
    fun fileCertificates(bytes: ByteArray, password: String): List<PdfCert> {
        val ks = pkcs12(bytes, password)
        return ks.aliases().toList().filter { ks.isKeyEntry(it) }.mapNotNull { a -> (ks.getCertificate(a) as? X509Certificate)?.let { info(a, it) } }
    }

    private fun key(alias: String, keyFile: ByteArray?, password: String?): Pair<PrivateKey, List<X509Certificate>> {
        val ks = if (keyFile != null) pkcs12(keyFile, password.orEmpty()) else KeyStore.getInstance(WINDOWS_STORE).apply { load(null, null) }
        val pk = ks.getKey(alias, (if (keyFile != null) password.orEmpty() else "").toCharArray()) as? PrivateKey
            ?: throw IllegalStateException(tr("Zum Zertifikat „{0}“ gibt es keinen privaten Schlüssel.", alias))
        val chain = ks.getCertificateChain(alias)?.map { it as X509Certificate }?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(ks.getCertificate(alias) as? X509Certificate)
        val cert = chain.first()
        runCatching { cert.checkValidity() }.onFailure { throw IllegalStateException(tr("Das Zertifikat „{0}“ ist abgelaufen oder noch nicht gültig.", nameOf(cert))) }
        return pk to chain
    }

    /** The CMS signature (detached) over [content]. */
    private fun cms(content: InputStream, pk: PrivateKey, chain: List<X509Certificate>): ByteArray {
        val alg = if (pk.algorithm.equals("EC", true) || pk.algorithm.equals("ECDSA", true)) "SHA256withECDSA" else "SHA256withRSA"
        val gen = CMSSignedDataGenerator()
        val signer = JcaContentSignerBuilder(alg).build(pk)
        gen.addSignerInfoGenerator(JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().build()).build(signer, chain.first()))
        gen.addCertificates(JcaCertStore(chain))
        return gen.generate(CMSProcessableByteArray(IOUtils.toByteArray(content)), false).encoded
    }

    /**
     * Signs [file] into [out] with certificate [cert] (an alias of the Windows store, or of the PKCS#12 bytes [keyFile]
     * with [password]).
     * [visible]: page and box (fractions of the displayed page) of a visible field with name, date and reason.
     */
    fun sign(file: File, out: File, cert: String, keyFile: ByteArray?, password: String?, reason: String, location: String, visible: Pair<Int, PdfBox>?) {
        val (pk, chain) = key(cert, keyFile, password)
        val who = nameOf(chain.first())
        Loader.loadPDF(file).use { doc ->
            val sig = PDSignature().apply {
                setFilter(PDSignature.FILTER_ADOBE_PPKLITE)
                setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED)
                name = who
                this.reason = reason.ifBlank { null }
                this.location = location.ifBlank { null }
                signDate = Calendar.getInstance()
            }
            val options = SignatureOptions().apply { preferredSignatureSize = SignatureOptions.DEFAULT_SIGNATURE_SIZE * 3 }
            try {
                if (visible != null) {
                    val (pageIndex, box) = visible
                    val page = doc.getPage(pageIndex.coerceIn(0, doc.numberOfPages - 1))
                    val lines = listOfNotNull(
                        tr("Digital signiert von"), who,
                        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(sig.signDate.time),
                        reason.takeIf { it.isNotBlank() }?.let { tr("Grund: {0}", it) },
                    )
                    options.setVisualSignature(template(page, rect(page, box), lines))
                    options.page = pageIndex.coerceIn(0, doc.numberOfPages - 1)
                }
                doc.addSignature(sig, SignatureInterface { content -> cms(content, pk, chain) }, options)
                out.outputStream().use { doc.saveIncremental(it) }
            } finally {
                options.close()
            }
        }
    }

    /** [box] (fractions of the displayed page, top left) as a rectangle of the page in PDF points (unrotated pages exactly). */
    private fun rect(page: PDPage, box: PdfBox): PDRectangle {
        val (x0, y0) = PdfTools.toUser(page, box.x, box.y)
        val (x1, y1) = PdfTools.toUser(page, box.right, box.bottom)
        return PDRectangle(minOf(x0, x1), minOf(y0, y1), kotlin.math.abs(x1 - x0), kotlin.math.abs(y1 - y0))
    }

    /** A one-page document with the signature field and its look, which PDFBox copies into the signed file. */
    private fun template(src: PDPage, r: PDRectangle, lines: List<String>): InputStream = PDDocument().use { doc ->
        val page = PDPage(src.mediaBox)
        doc.addPage(page)
        val form = PDAcroForm(doc)
        doc.documentCatalog.acroForm = form
        val field = PDSignatureField(form)
        val widget = field.widgets.first()
        form.isSignaturesExist = true
        form.isAppendOnly = true
        form.cosObject.isDirect = true
        form.fields.add(field)
        widget.rectangle = r
        widget.page = page
        page.annotations.add(widget)
        val xo = PDFormXObject(PDStream(doc)).apply {
            resources = PDResources()
            formType = 1
            bBox = PDRectangle(r.width, r.height)
        }
        val ap = PDAppearanceStream(xo.cosObject)
        widget.appearance = PDAppearanceDictionary().apply { setNormalAppearance(ap) }
        PDPageContentStream(doc, ap).use { cs ->
            cs.setNonStrokingColor(0.94f, 0.96f, 1f)
            cs.addRect(0f, 0f, r.width, r.height); cs.fill()
            cs.setStrokingColor(0f, 0.19f, 0.37f)
            cs.setLineWidth(1f)
            cs.addRect(0.5f, 0.5f, r.width - 1f, r.height - 1f); cs.stroke()
            val size = (r.height / (lines.size + 1.2f)).coerceIn(5f, 11f)
            val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
            val bold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
            cs.setNonStrokingColor(0f, 0.19f, 0.37f)
            lines.forEachIndexed { i, line ->
                cs.beginText()
                cs.setFont(if (i == 1) bold else font, size)
                cs.newLineAtOffset(4f, r.height - (i + 1) * size * 1.15f - 2f)
                cs.showText(line.map { ch -> if (ch.code in 32..255 || ch in "€„“–") ch else '?' }.joinToString(""))
                cs.endText()
            }
        }
        val bytes = ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        ByteArrayInputStream(bytes)
    }

    // --- Checking --------------------------------------------------------------------------------------------------

    /** The signatures of [file] with what their check says. */
    fun verify(file: File): List<PdfSignatureInfo> {
        val bytes = file.readBytes()
        return Loader.loadPDF(bytes).use { doc ->
            doc.signatureDictionaries.map { sig ->
                runCatching {
                    val contents = sig.getContents(bytes)
                    val signed = sig.getSignedContent(bytes)
                    val data = CMSSignedData(CMSProcessableByteArray(signed), contents)
                    val signer = data.signerInfos.signers.first()
                    @Suppress("UNCHECKED_CAST")
                    val holders = data.certificates.getMatches(null) as Collection<X509CertificateHolder>
                    val conv = JcaX509CertificateConverter()
                    val certs = holders.map { conv.getCertificate(it) }
                    @Suppress("UNCHECKED_CAST")
                    val own = holders.firstOrNull { signer.sid.match(it) }?.let { conv.getCertificate(it) } ?: certs.first()
                    val valid = runCatching { signer.verify(JcaSimpleSignerInfoVerifierBuilder().build(own)) }.getOrDefault(false)
                    val range = sig.byteRange
                    val covers = range.size == 4 && range[2].toLong() + range[3] == bytes.size.toLong()
                    val trusted = trusted(own, certs)
                    PdfSignatureInfo(
                        signer = sig.name ?: nameOf(own), time = sig.signDate?.timeInMillis, reason = sig.reason, valid = valid, coversAll = covers, trusted = trusted,
                        problem = when {
                            !valid -> tr("Die Signatur passt nicht – das Dokument wurde nach dem Signieren verändert.")
                            !covers -> tr("Nach dem Signieren wurde etwas hinzugefügt (z. B. Kommentare oder eine weitere Signatur).")
                            !trusted -> tr("Das Zertifikat stammt nicht von einer Stelle, der dieser PC vertraut (z. B. selbst ausgestellt).")
                            else -> null
                        },
                    )
                }.getOrElse { e ->
                    PdfSignatureInfo(sig.name ?: "?", sig.signDate?.timeInMillis, sig.reason, valid = false, coversAll = false, trusted = false, problem = tr("Die Signatur ließ sich nicht lesen: {0}", e.message ?: e.javaClass.simpleName))
                }
            }
        }
    }

    /** Whether [cert] leads (through [chain]) to a root of the Windows store or of Java's list, without revocation check. */
    private fun trusted(cert: X509Certificate, chain: List<X509Certificate>): Boolean = runCatching {
        val roots = if (Os.windows) KeyStore.getInstance("Windows-ROOT").apply { load(null, null) }
        else KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            File(System.getProperty("java.home"), "lib/security/cacerts").inputStream().use { load(it, null) }
        }
        val path = CertificateFactory.getInstance("X.509").generateCertPath(listOf(cert) + chain.filter { it != cert && it.subjectX500Principal != it.issuerX500Principal })
        val params = PKIXParameters(roots).apply { isRevocationEnabled = false }
        CertPathValidator.getInstance("PKIX").validate(path, params)
        true
    }.getOrDefault(false)
}
