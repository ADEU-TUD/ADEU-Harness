package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.docs.PdfAccessibility
import io.github.thepytales.llmhub.docs.PdfBox
import io.github.thepytales.llmhub.docs.PdfMark
import io.github.thepytales.llmhub.docs.PdfMarkKind
import io.github.thepytales.llmhub.docs.PdfPoint
import io.github.thepytales.llmhub.docs.PdfRedaction
import io.github.thepytales.llmhub.docs.PdfSigning
import io.github.thepytales.llmhub.docs.PdfSnap
import io.github.thepytales.llmhub.docs.PdfStage3
import io.github.thepytales.llmhub.docs.PdfTools
import io.github.thepytales.llmhub.docs.PdfWord
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.awt.image.BufferedImage
import java.io.File
import java.math.BigInteger
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** PDF-Reste (AP-60): marking along the text, blacking out with the mouse, digital signatures, tags for PDF/UA. */
class PdfRestTest {
    private val base = Files.createTempDirectory("pdf-reste").toFile()

    @AfterTest
    fun cleanUp() { base.deleteRecursively() }

    /** An untagged report: heading, paragraphs, a list, a picture, a frame line and a page number; content not compressed. */
    private fun report(name: String = "bericht.pdf"): File {
        val f = File(base, name)
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            val regular = PDType1Font(Standard14Fonts.FontName.HELVETICA)
            val bold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
            val img = LosslessFactory.createFromImage(doc, BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB).apply { createGraphics().apply { color = java.awt.Color(0, 48, 93); fillRect(0, 0, 40, 30); dispose() } })
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.OVERWRITE, false).use { cs ->
                fun line(text: String, x: Float, y: Float, font: PDType1Font = regular, size: Float = 11f) {
                    cs.beginText(); cs.setFont(font, size); cs.newLineAtOffset(x, y); cs.showText(text); cs.endText()
                }
                line("Jahresbericht der Professur", 72f, 760f, bold, 20f)
                line("Die erste Zeile spricht vom Antrag der Frau Meier.", 72f, 720f)
                line("Die zweite Zeile nennt das Budget und die dritte Stelle.", 72f, 704f)
                line("• Erstens die Lehre", 72f, 680f)
                line("• Zweitens die Forschung", 72f, 664f)
                cs.drawImage(img, 72f, 600f, 80f, 60f)
                cs.addRect(60f, 590f, 470f, 190f); cs.stroke()
                line("1", 297f, 40f, regular, 9f)
            }
            doc.save(f)
        }
        return f
    }

    @Test
    fun markingSnapsToTheLinesOfText() {
        val f = report()
        val words = PdfTools.words(null, f).filter { it.page == 0 }
        assertTrue(words.any { it.text == "Antrag" } && words.any { it.text == "Budget" }, words.joinToString { it.text })
        val lines = PdfSnap.lines(words)
        assertEquals(listOf("Jahresbericht", "Die", "Die", "•", "•", "1"), lines.map { it.first().text })
        // A drag from „Antrag“ to „Budget“: the rest of the first line and the second up to „Budget“, one box per line.
        val from = words.first { it.text == "Antrag" }.box
        val to = words.first { it.text == "Budget" }.box
        val boxes = PdfSnap.boxes(words, PdfPoint(from.x + 0.005f, from.y + from.h / 2), PdfPoint(to.right - 0.005f, to.y + to.h / 2))
        assertEquals(2, boxes.size)
        assertEquals(from.x, boxes[0].x, 1e-4f)
        assertTrue(boxes[0].right > from.right + 0.1f, "bis zum Ende der ersten Zeile")
        assertEquals(to.right, boxes[1].right, 1e-4f)
        assertTrue(boxes[1].x < to.x - 0.1f, "vom Anfang der zweiten Zeile")
        // Dragged backwards gives the same; on a page without text there is nothing to snap to.
        assertEquals(boxes, PdfSnap.boxes(words, PdfPoint(to.right - 0.005f, to.y + to.h / 2), PdfPoint(from.x + 0.005f, from.y + from.h / 2)))
        assertTrue(PdfSnap.boxes(emptyList(), PdfPoint(0.1f, 0.1f), PdfPoint(0.5f, 0.5f)).isEmpty())
        // The highlight goes into the PDF along the text.
        val out = File(base, "markiert.pdf")
        PdfTools.annotate(null, f, listOf(PdfMark(0, PdfMarkKind.HIGHLIGHT, boxes)), out)
        assertEquals(1, Loader.loadPDF(out).use { it.getPage(0).annotations.size })
    }

    @Test
    fun blackingOutWithTheMouseRemovesTheWord() {
        val f = report()
        val words = PdfTools.words(null, f)
        val meier = words.first { it.text.startsWith("Meier") }
        // A click on the word, and a dragged rectangle over the page number.
        val hit = PdfSnap.wordAt(words, PdfPoint(meier.box.x + meier.box.w / 2, meier.box.y + meier.box.h / 2))
        assertEquals(meier, hit)
        val boxes = listOf(PdfRedaction(0, meier.box, meier.text), PdfRedaction(0, PdfBox(0.45f, 0.93f, 0.1f, 0.04f)))
        val out = File(base, "geschwaerzt.pdf")
        PdfStage3.redact(f, boxes, out)
        val text = Loader.loadPDF(out).use { PDFTextStripper().getText(it) }
        assertFalse("Meier" in text, text)
    }

    /** A self-signed certificate in a PKCS#12 file, as one could export it from Windows. */
    private fun p12(password: String): File {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Name("CN=Erika Muster, O=TU Dresden, C=DE")
        val now = System.currentTimeMillis()
        val holder = JcaX509v3CertificateBuilder(name, BigInteger.valueOf(now), Date(now - 3_600_000), Date(now + 365L * 86_400_000), name, kp.public)
            .build(JcaContentSignerBuilder("SHA256withRSA").build(kp.private))
        val cert = JcaX509CertificateConverter().getCertificate(holder)
        val ks = KeyStore.getInstance("PKCS12").apply { load(null, null); setKeyEntry("erika", kp.private, password.toCharArray(), arrayOf(cert)) }
        return File(base, "erika.p12").apply { outputStream().use { ks.store(it, password.toCharArray()) } }
    }

    @Test
    fun digitalSignatureWithAVisibleFieldIsCheckedAndTamperingSeen() {
        val f = report()
        val key = p12("geheim")
        val certs = PdfSigning.fileCertificates(key.readBytes(), "geheim")
        assertEquals("Erika Muster", certs.single().subject)
        val signed = File(base, "signiert.pdf")
        PdfSigning.sign(f, signed, certs.single().alias, key.readBytes(), "geheim", "Freigabe", "Dresden", 0 to PdfBox(0.55f, 0.85f, 0.35f, 0.08f))
        val checks = PdfSigning.verify(signed)
        val c = checks.single()
        assertEquals("Erika Muster", c.signer)
        assertEquals("Freigabe", c.reason)
        assertTrue(c.valid && c.coversAll, c.toString())
        assertFalse(c.trusted, "selbst ausgestellt")
        assertTrue(c.problem!!.contains("vertraut"))
        // The visible field is on the page and the PDF still renders.
        Loader.loadPDF(signed).use { doc ->
            assertTrue(doc.getPage(0).annotations.any { it.subtype == "Widget" })
            PDFRenderer(doc).renderImage(0, 0.5f)
        }
        // A changed letter in the signed part: the signature no longer fits.
        val bytes = signed.readBytes()
        val at = String(bytes, Charsets.ISO_8859_1).indexOf("Budget")
        assertTrue(at > 0)
        bytes[at] = 'X'.code.toByte()
        val tampered = File(base, "veraendert.pdf").apply { writeBytes(bytes) }
        val t = PdfSigning.verify(tampered).single()
        assertFalse(t.valid)
        assertTrue(t.problem!!.contains("verändert"))
        // A wrong password says so.
        assertFailsWith<Exception> { PdfSigning.fileCertificates(key.readBytes(), "falsch") }
    }

    /** Fertig, wenn: Ein PDF lässt sich am Text markieren, per Maus schwärzen, digital signieren und mit Tags versehen. */
    @Test
    fun tagsForPdfUa() {
        val f = report()
        val before = Loader.loadPDF(f).use { PDFTextStripper().getText(it) }
        val out = File(base, "getaggt.pdf")
        val images = PdfStage3.images(f)
        val report = PdfStage3.autoTag(f, out, "Jahresbericht", alt = mapOf("0:${images.single().name}" to "Logo der Professur"))
        assertEquals(1, report.headings)
        assertEquals(2, report.paragraphs)
        assertEquals(2, report.listItems)
        assertEquals(1, report.figures)
        assertEquals(0, report.figuresWithoutAlt)
        assertTrue(report.artifacts >= 1, "Seitenzahl als Artefakt")
        Loader.loadPDF(out).use { doc ->
            val root = doc.documentCatalog.structureTreeRoot!!
            val types = mutableListOf<String>()
            var alt: String? = null
            fun walk(n: PDStructureNode) { n.kids.forEach { k -> if (k is PDStructureElement) { types += k.structureType; if (k.structureType == "Figure") alt = k.alternateDescription; walk(k) } } }
            walk(root)
            assertEquals(listOf("Document", "H1", "P", "P", "L", "LI", "LBody", "LI", "LBody", "Figure"), types)
            assertEquals("Logo der Professur", alt)
            assertTrue(doc.documentCatalog.markInfo.isMarked)
            assertEquals("de-DE", doc.documentCatalog.language)
            assertEquals(0, doc.getPage(0).structParents)
            assertTrue(root.parentTree.getValue(0) != null)
            val content = String(doc.getPage(0).contents.readAllBytes(), Charsets.ISO_8859_1)
            assertTrue("/H1 <<\n/MCID 0\n>> BDC" in content || Regex("/H1\\s*<<\\s*/MCID 0\\s*>>\\s*BDC").containsMatchIn(content), content.take(400))
            assertTrue("/Artifact BMC" in content)
            // The text and the look stay.
            assertEquals(before, PDFTextStripper().getText(doc))
            PDFRenderer(doc).renderImage(0, 0.5f)
            assertTrue(String(doc.documentCatalog.metadata.toByteArray(), Charsets.UTF_8).contains("pdfuaid:part>1"))
        }
        // The PDF/UA check now sees tags; a second run refuses.
        val ctx = android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() })
        assertTrue(PdfAccessibility.inspect(ctx, out).tagged)
        assertTrue(PdfStage3.uaCheck(ctx, out).none { it.text.startsWith("Keine Tags") })
        assertFailsWith<IllegalStateException> { PdfStage3.autoTag(out, File(base, "zweimal.pdf"), "x") }
    }

    @Test
    fun linesOfWordsInReadingOrder() {
        fun w(x: Float, y: Float, t: String) = PdfWord(0, PdfBox(x, y, 0.08f, 0.02f), t)
        val words = listOf(w(0.5f, 0.2f, "rechts"), w(0.1f, 0.2005f, "links"), w(0.1f, 0.3f, "unten"))
        assertEquals(listOf(listOf("links", "rechts"), listOf("unten")), PdfSnap.lines(words).map { l -> l.map { it.text } })
        assertEquals(1, PdfSnap.boxes(words, PdfPoint(0.12f, 0.21f), PdfPoint(0.55f, 0.21f)).size)
    }
}
