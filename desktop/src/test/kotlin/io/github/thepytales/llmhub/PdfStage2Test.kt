package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.docs.DocFormat
import io.github.thepytales.llmhub.docs.MarkdownDocx
import io.github.thepytales.llmhub.docs.MarkdownPdf
import io.github.thepytales.llmhub.docs.OcrLine
import io.github.thepytales.llmhub.docs.OcrPage
import io.github.thepytales.llmhub.docs.OcrWord
import io.github.thepytales.llmhub.docs.PdfBox
import io.github.thepytales.llmhub.docs.PdfConvert
import io.github.thepytales.llmhub.docs.PdfExtras
import io.github.thepytales.llmhub.docs.PdfOcr
import io.github.thepytales.llmhub.docs.PdfTables
import io.github.thepytales.llmhub.docs.PdfTools
import io.github.thepytales.llmhub.docs.Signature
import io.github.thepytales.llmhub.docs.SignatureImages
import io.github.thepytales.llmhub.docs.ToPdfKind
import io.github.thepytales.llmhub.docs.WindowsOcrOutput
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PDF-Werkzeugkasten Stufe 2 (AP-15): OCR layer, signing, converting, tables. */
class PdfStage2Test {
    private val dir = Files.createTempDirectory("adeu-pdf2").toFile()
    private val context = Context(File(dir, "files").apply { mkdirs() }, File(dir, "cache").apply { mkdirs() })

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private val markdown = """
        # Ausschreibung Lehrinnovation

        Die Förderung richtet sich an Lehrende der TU Dresden. Anträge sind bis zum 30.11.2026 einzureichen.
        Gefördert werden Projekte mit digitalen Lehrformaten.

        ## Fördersummen

        | Linie | Betrag | Dauer |
        | --- | --- | --- |
        | Klein | 5000 | 6 Monate |
        | Groß | 25000 | 12 Monate |

        - Antrag als PDF
        - Stellungnahme der Fakultät
    """.trimIndent()

    private fun textPdf(): File = File(dir, "ausschreibung.pdf").also { MarkdownPdf.write(it, "Ausschreibung", markdown) }

    /** A scan: a picture of text without a text layer. */
    private fun scanPdf(): File {
        val img = BufferedImage(1240, 1754, BufferedImage.TYPE_INT_RGB)
        img.createGraphics().apply {
            color = Color.WHITE
            fillRect(0, 0, img.width, img.height)
            color = Color.BLACK
            font = Font(Font.SERIF, Font.PLAIN, 48)
            drawString("Bescheid über die Förderung", 120, 200)
            dispose()
        }
        val png = ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
        return File(dir, "scan.pdf").also { PdfExtras.imagesToPdf(context, listOf("scan.png" to png), it) }
    }

    @Test
    fun scansGetAnInvisibleTextLayerThatSearchFinds() {
        val scan = scanPdf()
        val texts = PdfExtras.pageTexts(context, scan)
        assertEquals(listOf(0), PdfOcr.scanned(texts), "eine Seite ohne Text")
        // The word boxes as an OCR engine would return them (fractions of the page).
        val page = OcrPage(0, listOf(OcrLine(listOf(OcrWord("Bescheid", PdfBox(0.097f, 0.087f, 0.17f, 0.03f)), OcrWord("Förderung", PdfBox(0.5f, 0.087f, 0.2f, 0.03f))))))
        val out = File(dir, "scan-ocr.pdf")
        PdfExtras.addTextLayer(context, scan, listOf(page), out)
        assertTrue(PdfExtras.pageTexts(context, out).first().contains("Förderung"))
        assertEquals(1, PdfTools.search(context, out, "Bescheid").size)
        // The text sits where the word is on the page (search highlights and „Frag das PDF“ quotes fit).
        val run = PdfTools.textRuns(context, out).first.first { "Bescheid" in it.text }
        val (w, h) = PdfTools.info(context, out).sizes.first()
        assertTrue(kotlin.math.abs(run.x / w - 0.097f) < 0.02f && kotlin.math.abs(run.y / h - 0.087f) < 0.03f, "x=${run.x / w} y=${run.y / h}")
        // The layer is invisible: the rendered page looks as before.
        val before = PDFRenderer(Loader.loadPDF(scan)).renderImageWithDPI(0, 50f)
        val after = PDFRenderer(Loader.loadPDF(out)).renderImageWithDPI(0, 50f)
        assertEquals((0 until before.width step 7).sumOf { x -> (0 until before.height step 7).count { y -> before.getRGB(x, y) != after.getRGB(x, y) } }, 0)
    }

    @Test
    fun theTextLayerFitsRotatedPagesToo() {
        val scan = scanPdf()
        val rotated = File(dir, "gedreht.pdf")
        PdfTools.rearrange(context, scan, listOf(0), mapOf(0 to 90), rotated)
        val box = PdfBox(0.3f, 0.4f, 0.2f, 0.03f)
        val out = File(dir, "gedreht-ocr.pdf")
        PdfExtras.addTextLayer(context, rotated, listOf(OcrPage(0, listOf(OcrLine(listOf(OcrWord("Quer", box)))))), out)
        val run = PdfTools.textRuns(context, out).first.first { "Quer" in it.text }
        val (w, h) = PdfTools.info(context, out).sizes.first()
        assertTrue(w > h, "quer angezeigt")
        assertTrue(kotlin.math.abs(run.x / w - box.x) < 0.03f && kotlin.math.abs(run.y / h - box.y) < 0.04f, "x=${run.x / w} y=${run.y / h}")
    }

    @Test
    fun windowsOcrOutputIsRead() {
        val parsed = WindowsOcrOutput.parse(
            "#ENGINE de-DE\r\n#PAGE 0 1000 2000\r\n100\t200\t300\t40\tFörderbescheid\r\n420\t200\t80\t40\tvom\r\n#LINE\r\n100\t260\t200\t40\t1.11.2026\r\n#LINE\r\n#FAIL 1 Datei fehlt\r\n#PAGE 2 500 500\r\n#LINE\r\n",
        )
        assertEquals("de-DE", parsed.engine)
        val p = parsed.pages.getValue(0)
        assertEquals(listOf("Förderbescheid vom", "1.11.2026"), p.lines.map { it.text })
        assertEquals(PdfBox(0.1f, 0.1f, 0.3f, 0.02f), p.lines.first().words.first().box)
        assertEquals("Datei fehlt", parsed.failed[1])
        assertTrue(parsed.pages.getValue(2).lines.isEmpty())
        assertNull(parsed.error)
        assertEquals("NOENGINE", WindowsOcrOutput.parse("#ERROR NOENGINE").error)
        // Text of a vision model: lines spread over the page, not positioned.
        val plain = OcrPage.plain(3, "Zeile eins\n\n```\nZeile zwei")
        assertEquals(listOf("Zeile eins", "Zeile zwei"), plain.lines.map { it.text })
        assertTrue(!plain.positioned)
    }

    @Test
    fun pdfBecomesMarkdownWithHeadingsListsAndTables() {
        val pdf = textPdf()
        val md = PdfConvert.markdown(context, pdf)
        if (System.getenv("PDF_DEBUG") != null) PdfTools.textRuns(context, pdf).first.forEach { println("RUN p=${it.page} x=${it.x} y=${it.y} w=${it.w} h=${it.h} '${it.text}'") }
        assertTrue(Regex("(?m)^#{1,2} Ausschreibung Lehrinnovation").containsMatchIn(md), md)
        assertTrue(Regex("(?m)^## Fördersummen").containsMatchIn(md), md)
        assertTrue("einzureichen. Gefördert werden" in md, "Absatz zusammengefügt: $md")
        assertTrue(Regex("(?m)^- Antrag als PDF").containsMatchIn(md), md)
        val tables = PdfTables.detect(PdfTools.textRuns(context, pdf).first)
        assertEquals(1, tables.size, tables.toString())
        assertEquals(listOf("Linie", "Betrag", "Dauer"), tables.first().rows.first())
        assertEquals(listOf("Groß", "25000", "12 Monate"), tables.first().rows.last())
        assertTrue("| Klein | 5000 | 6 Monate |" in md, md)
        // As Word and as Excel (its tables).
        runBlocking {
            val docx = PdfConvert.fromPdf(context, pdf, "Ausschreibung", DocFormat.DOCX, File(dir, "out"))
            ZipFile(docx).use { z -> assertTrue(z.getInputStream(z.getEntry("word/document.xml")).readBytes().decodeToString().contains("Fördersummen")) }
            val xlsx = PdfConvert.fromPdf(context, pdf, "Ausschreibung (Tabellen)", DocFormat.XLSX, File(dir, "out"))
            ZipFile(xlsx).use { z -> assertNotNull(z.getEntry("xl/workbook.xml")) }
        }
    }

    @Test
    fun otherFilesBecomePdfs() = runBlocking {
        assertEquals(ToPdfKind.OFFICE, ToPdfKind.of("Antrag.DOCX"))
        assertEquals(ToPdfKind.IMAGE, ToPdfKind.of("foto.jpeg"))
        assertEquals(ToPdfKind.UNSUPPORTED, ToPdfKind.of("musik.mp3"))
        val out = File(dir, "umgewandelt")
        // Word without Office (this machine): the app's own reader.
        val docx = File(dir, "Antrag.docx").also { MarkdownDocx.write(it, "Antrag", markdown) }
        val fromWord = PdfConvert.toPdf(context, docx, "Antrag.docx", out)
        assertTrue("Fördersummen" in PdfExtras.pageTexts(context, fromWord.file).joinToString(" "))
        val txt = File(dir, "notiz.txt").also { it.writeText("Erste Zeile\nZweite Zeile") }
        val fromText = PdfConvert.toPdf(context, txt, "notiz.txt", out)
        assertTrue(PdfExtras.pageTexts(context, fromText.file).first().contains("Zweite Zeile"))
        val scan = scanPdf()
        assertEquals(1, PdfTools.info(context, scan).pages)
        assertTrue(PdfTools.info(context, scan).sizes.first().let { (w, h) -> h > w }, "Hochformat")
        // Pages as pictures.
        val zip = File(dir, "seiten.zip").also { PdfExtras.pagesZip(context, scan, 40, it) }
        ZipFile(zip).use { z -> assertNotNull(z.getEntry("Seite-001.png")) }
        Unit
    }

    @Test
    fun signaturesAreDrawnWhereTapped() {
        val pdf = textPdf()
        val sig = Signature.fromPad(listOf(listOf(10f to 50f, 60f to 20f, 110f to 60f, 160f to 30f), listOf(40f to 70f, 140f to 70f)))!!
        assertTrue(sig.aspect > 1.5f && sig.strokes.flatten().all { it.x in 0f..1f && it.y in 0f..1f })
        assertNull(Signature.fromPad(listOf(listOf(1f to 1f))))
        val out = File(dir, "unterschrieben.pdf")
        val box = PdfBox(0.55f, 0.8f, 0.3f, 0.3f * 0.707f / sig.aspect)
        PdfExtras.sign(context, pdf, 0, box, sig, "Dresden, 01.11.2026", out)
        val img = PDFRenderer(Loader.loadPDF(out)).renderImageWithDPI(0, 60f)
        val inside = (0 until 40).count { i ->
            val x = ((box.x + box.w * (i % 8) / 8f) * img.width).toInt()
            val y = ((box.y + box.h * (i / 8) / 5f) * img.height).toInt()
            img.getRGB(x, y) and 0xFFFFFF != 0xFFFFFF
        }
        val inkNear = (0 until img.width).any { x -> (0 until img.height).any { y -> x > box.x * img.width && x < box.right * img.width && y > box.y * img.height && y < box.bottom * img.height && (img.getRGB(x, y) and 0xFF) < 160 && ((img.getRGB(x, y) shr 16) and 0xFF) < 100 } }
        assertTrue(inkNear, "Tinte in der Box ($inside Stichproben)")
        assertTrue(PdfExtras.pageTexts(context, out).first().contains("Dresden, 01.11.2026"))
        // The shape of a signature from a picture.
        val png = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(300, 100, BufferedImage.TYPE_INT_ARGB), "png", it) }.toByteArray()
        val jpg = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(320, 80, BufferedImage.TYPE_INT_RGB), "jpg", it) }.toByteArray()
        assertEquals(300 to 100, SignatureImages.size(png))
        assertEquals(320 to 80, SignatureImages.size(jpg))
        assertNull(SignatureImages.size("kein Bild".toByteArray()))
        val withImage = File(dir, "bild-unterschrift.pdf")
        PdfExtras.sign(context, pdf, 0, box, Signature(image = java.util.Base64.getEncoder().encodeToString(png), aspect = 3f), null, withImage)
        assertTrue(withImage.length() > pdf.length())
    }
}
