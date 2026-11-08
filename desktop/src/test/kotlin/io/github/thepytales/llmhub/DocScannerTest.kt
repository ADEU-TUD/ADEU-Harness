package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.docs.DocScanner
import io.github.thepytales.llmhub.docs.OcrLine
import io.github.thepytales.llmhub.docs.OcrPage
import io.github.thepytales.llmhub.docs.OcrWord
import io.github.thepytales.llmhub.docs.PdfBox
import io.github.thepytales.llmhub.docs.ScanImages
import io.github.thepytales.llmhub.docs.ScanMode
import io.github.thepytales.llmhub.docs.ScanPage
import io.github.thepytales.llmhub.docs.ScanPoint
import io.github.thepytales.llmhub.docs.ScanQuad
import io.github.thepytales.llmhub.docs.ScanRaster
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.Random
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Dokumenten-Scanner (AP-62): pages photographed at a slant on a desk are found, straightened and made clean, and three
 * of them become one searchable PDF in the Lora-Ordner under „Scans“.
 */
class DocScannerTest {
    private val base = Files.createTempDirectory("scanner").toFile()

    @AfterTest
    fun cleanUp() { base.deleteRecursively() }

    /** A flat A4 page (840 × 1188) with a heading, lines of „text“ and a black box at a known place. */
    private fun flatPage(n: Int): BufferedImage = BufferedImage(840, 1188, BufferedImage.TYPE_INT_RGB).apply {
        val g = createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color(246, 244, 238)
        g.fillRect(0, 0, width, height)
        g.color = Color(25, 25, 30)
        g.font = Font(Font.SANS_SERIF, Font.BOLD, 54)
        g.drawString("Seite $n", 80, 140)
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 26)
        for (i in 0 until 18) g.drawString("Rechnung Nummer 1234$i vom 08.11.2026 für die Professur", 80, 240 + i * 44)
        // Marker: a box at 70–80 % of the width, 85–90 % of the height.
        g.fillRect((0.70 * width).toInt(), (0.85 * height).toInt(), (0.10 * width).toInt(), (0.05 * height).toInt())
        g.dispose()
    }

    private val corners = listOf(450.0 to 60.0, 1110.0 to 110.0, 1190.0 to 1180.0, 380.0 to 1150.0)

    /**
     * The page as photographed: a darker, grainy desk (1600 × 1250), the sheet at a slant (perspective), lit unevenly
     * (a shadow towards the bottom left).
     */
    private fun photo(page: BufferedImage, seed: Long = 1): ScanRaster {
        val w = 1600
        val h = 1250
        val out = ScanRaster(w, h)
        val rnd = Random(seed)
        // From the photo to the page: the inverse of putting the page at [corners].
        val pageCorners = listOf(0.0 to 0.0, (page.width - 1).toDouble() to 0.0, (page.width - 1).toDouble() to (page.height - 1).toDouble(), 0.0 to (page.height - 1).toDouble())
        val m = DocScanner.homography(corners, pageCorners)
        for (y in 0 until h) for (x in 0 until w) {
            val z = m[6] * x + m[7] * y + 1
            val u = (m[0] * x + m[1] * y + m[2]) / z
            val v = (m[3] * x + m[4] * y + m[5]) / z
            val shade = 1.0 - 0.22 * ((w - x).toDouble() / w) * (y.toDouble() / h)
            out[x, y] = if (u >= 0 && v >= 0 && u < page.width && v < page.height) {
                val p = page.getRGB(u.toInt(), v.toInt())
                ScanRaster.argb((((p shr 16) and 0xFF) * shade).toInt(), (((p shr 8) and 0xFF) * shade).toInt(), ((p and 0xFF) * shade).toInt())
            } else {
                val n = rnd.nextInt(24)
                ScanRaster.argb(92 + n, 70 + n, 52 + n)
            }
        }
        return out
    }

    private fun meanLuma(r: ScanRaster, x0: Double, y0: Double, x1: Double, y1: Double): Double {
        var s = 0L; var n = 0
        for (y in (y0 * r.height).toInt() until (y1 * r.height).toInt()) for (x in (x0 * r.width).toInt() until (x1 * r.width).toInt()) {
            s += ScanRaster.luma(r[x, y]); n++
        }
        return s.toDouble() / n
    }

    @Test
    fun findsTheTiltedPageAndStraightensIt() {
        val photo = photo(flatPage(1))
        val quad = assertNotNull(DocScanner.detect(photo), "Seite nicht gefunden")
        val expected = ScanQuad.ordered(corners.map { (x, y) -> ScanPoint((x / photo.width).toFloat(), (y / photo.height).toFloat()) })
        assertTrue(DocScanner.distance(quad, expected) < 0.015f, "Ecken zu ungenau: $quad statt $expected")

        val straight = DocScanner.warp(photo, quad)
        // Nearly A4: straightened exactly to A4.
        assertEquals(1.414, straight.height.toDouble() / straight.width, 0.01)
        // The black box lands where it is on the paper.
        assertTrue(meanLuma(straight, 0.715, 0.86, 0.785, 0.89) < 80, "Kasten nicht an seinem Platz")
        assertTrue(meanLuma(straight, 0.86, 0.86, 0.95, 0.89) > 150, "Rand neben dem Kasten nicht hell")

        // Grey and black-and-white: white paper also in the shadow, dark text.
        for (mode in listOf(ScanMode.GRAY, ScanMode.BW, ScanMode.COLOR)) {
            val clean = DocScanner.enhance(straight, mode)
            assertTrue(meanLuma(clean, 0.02, 0.92, 0.3, 0.98) > 225, "$mode: Papier im Schatten nicht weiß")
            assertTrue(meanLuma(clean, 0.715, 0.86, 0.785, 0.89) < 60, "$mode: Kasten nicht schwarz")
        }
    }

    @Test
    fun nothingToFindMeansTheWholePicture() {
        // Only paper (the photo shows nothing but the page) and only desk: no edges.
        val paper = ScanRaster(800, 600).apply { pixels.fill(ScanRaster.argb(240, 240, 235)) }
        assertNull(DocScanner.detect(paper))
        val noise = Random(3)
        val desk = ScanRaster(800, 600).apply { for (i in pixels.indices) pixels[i] = noise.nextInt(255).let { ScanRaster.argb(it, it, it) } }
        assertNull(DocScanner.detect(desk))
        assertEquals(ScanQuad.FULL, ScanQuad.ordered(listOf(ScanPoint(1f, 1f), ScanPoint(0f, 0f), ScanPoint(0f, 1f), ScanPoint(1f, 0f))))
    }

    @Test
    fun turnsAndMapsCornersExactly() {
        val r = ScanRaster(3, 2, IntArray(6) { it })
        val q = r.rotated(1)
        assertEquals(2, q.width)
        assertEquals(3, q.height)
        // The bottom left pixel (index 3) comes to the top left after a quarter turn clockwise.
        assertEquals(3, q[0, 0])
        assertEquals(r.pixels.toList(), r.rotated(4).pixels.toList())
        val m = DocScanner.homography(listOf(0.0 to 0.0, 10.0 to 0.0, 10.0 to 20.0, 0.0 to 20.0), corners)
        for ((i, p) in listOf(0.0 to 0.0, 10.0 to 0.0, 10.0 to 20.0, 0.0 to 20.0).withIndex()) {
            val z = m[6] * p.first + m[7] * p.second + 1
            assertTrue(abs((m[0] * p.first + m[1] * p.second + m[2]) / z - corners[i].first) < 1e-6)
            assertTrue(abs((m[3] * p.first + m[4] * p.second + m[5]) / z - corners[i].second) < 1e-6)
        }
    }

    /** Fertig, wenn: drei fotografierte Seiten ergeben ein durchsuchbares PDF im Lora-Ordner. */
    @Test
    fun threePhotographedPagesBecomeOneSearchablePdfInTheLoraFolder() = runBlocking {
        val lora = File(base, "Lora").apply { mkdirs() }
        val photos = (1..3).map { n ->
            File(base, "foto$n.jpg").also { it.writeBytes(ScanImages.jpeg(photo(flatPage(n), n.toLong()), 90)) }
        }
        val pages = photos.mapIndexed { i, f ->
            val quad = assertNotNull(DocScanner.detect(ScanImages.read(f, 1000)), "Seite ${i + 1} nicht gefunden")
            ScanPage(f, quad, if (i == 2) ScanMode.BW else ScanMode.GRAY, detected = true)
        }
        // Text recognition as the PC's would answer: words with their places on the page.
        val seen = mutableListOf<Int>()
        val ocr = DocScanner.Ocr { pdf, list, progress ->
            assertTrue(pdf.isFile)
            seen += list
            progress(list.size, list.size)
            list.map { p ->
                OcrPage(p, listOf(
                    OcrLine(listOf(OcrWord("Seite", PdfBox(0.1f, 0.08f, 0.15f, 0.04f)), OcrWord("${p + 1}", PdfBox(0.27f, 0.08f, 0.04f, 0.04f)))),
                    OcrLine(listOf(OcrWord("Rechnung", PdfBox(0.1f, 0.2f, 0.2f, 0.02f)), OcrWord("Nummer", PdfBox(0.32f, 0.2f, 0.15f, 0.02f)), OcrWord("12340", PdfBox(0.49f, 0.2f, 0.12f, 0.02f)))),
                ))
            } to "Testerkennung"
        }
        val target = DocScanner.target(File(lora, "Scans"), DocScanner.defaultName(0))
        val saved = DocScanner.save(null, pages, target, ocr)
        assertEquals(listOf(0, 1, 2), seen)
        assertEquals(3, saved.pages)
        assertEquals(3, saved.textPages)
        assertTrue(saved.file.isFile && saved.file.parentFile.name == "Scans" && saved.file.parentFile.parentFile == lora)
        assertTrue(saved.file.name.startsWith("Scan ") && saved.file.name.endsWith(".pdf"), saved.file.name)
        assertTrue(File(lora, "Scans").listFiles().orEmpty().none { it.name.endsWith(".tmp") }, "Zwischendatei liegen geblieben")
        Loader.loadPDF(saved.file).use { doc ->
            assertEquals(3, doc.numberOfPages)
            for (p in 1..3) {
                val text = PDFTextStripper().apply { startPage = p; endPage = p }.getText(doc)
                assertTrue("Seite $p" in text && "Rechnung" in text, "Seite $p nicht durchsuchbar: $text")
            }
        }
        // A second scan with the same name does not overwrite the first.
        assertTrue(DocScanner.target(File(lora, "Scans"), saved.file.nameWithoutExtension).name.endsWith("(2).pdf"))
    }

    @Test
    fun withoutTextRecognitionThePdfIsStillSaved() = runBlocking {
        val f = File(base, "foto.jpg").also { it.writeBytes(ScanImages.jpeg(photo(flatPage(1)), 85)) }
        val failing = DocScanner.Ocr { _, _, _ -> error("kein Netz") }
        val saved = DocScanner.save(null, listOf(ScanPage(f)), File(base, "ohne.pdf"), failing)
        assertTrue(saved.file.isFile)
        assertEquals(0, saved.textPages)
        assertEquals("kein Netz", saved.ocrError)
        val plain = DocScanner.save(null, listOf(ScanPage(f, mode = ScanMode.ORIGINAL, rotation = 1)), File(base, "ohne2.pdf"), null)
        Loader.loadPDF(plain.file).use { doc ->
            // Turned a quarter: the landscape photo becomes a portrait page.
            assertTrue(doc.getPage(0).mediaBox.height > doc.getPage(0).mediaBox.width)
        }
    }
}
