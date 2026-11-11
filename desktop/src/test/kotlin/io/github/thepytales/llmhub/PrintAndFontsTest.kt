package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.docs.FontRescue
import io.github.thepytales.llmhub.docs.Imposition
import io.github.thepytales.llmhub.docs.PrintPilot
import io.github.thepytales.llmhub.files.SystemFonts
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.text.PDFTextStripper
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Druck-Lotse (AP-97) and Schriften-Notfall (AP-91). */
class PrintAndFontsTest {
    private val dir = createTempDirectory("lora-druck").toFile()
    private val MM = 72f / 25.4f

    private fun pdf(name: String, pages: Int, size: PDRectangle = PDRectangle.A5): File {
        val f = File(dir, name)
        PDDocument().use { d ->
            val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
            for (i in 1..pages) {
                val p = PDPage(size)
                d.addPage(p)
                PDPageContentStream(d, p).use { cs ->
                    cs.beginText(); cs.setFont(font, 20f); cs.newLineAtOffset(40f, size.height / 2); cs.showText("Seite $i"); cs.endText()
                }
            }
            d.save(f)
        }
        return f
    }

    private fun pageTexts(f: File): List<String> = Loader.loadPDF(f).use { d ->
        (1..d.numberOfPages).map { n -> PDFTextStripper().apply { startPage = n; endPage = n; sortByPosition = true }.getText(d).trim().replace(Regex("\\s+"), " ") }
    }

    @Test
    fun bookletOrder() {
        assertEquals(listOf(8 to 1, 2 to 7, 6 to 3, 4 to 5), Imposition.booklet(8))
        assertEquals(8, Imposition.padded(5))
        assertEquals(6, Imposition.booklet(9).size)
        // Every page appears exactly once.
        val all = Imposition.booklet(16).flatMap { listOf(it.first, it.second) }.sorted()
        assertEquals((1..16).toList(), all)
    }

    @Test
    fun a5BookletOnA4() {
        val src = pdf("heft.pdf", 6)
        val out = File(dir, "heft-broschuere.pdf")
        PrintPilot.booklet(src, out)
        Loader.loadPDF(out).use { d ->
            assertEquals(4, d.numberOfPages)
            val box = d.getPage(0).mediaBox
            assertEquals(PDRectangle.A4.height, box.width, 0.5f)
            assertEquals(PDRectangle.A4.width, box.height, 0.5f)
        }
        assertEquals(listOf("Seite 1", "Seite 2", "Seite 6 Seite 3", "Seite 4 Seite 5"), pageTexts(out))
    }

    @Test
    fun severalPagesPerSheetHandoutDuplexAndBleed() {
        val src = pdf("vortrag.pdf", 7, PDRectangle.A4)
        val four = File(dir, "4.pdf").also { PrintPilot.nUp(src, it, 4) }
        assertEquals(listOf("Seite 1 Seite 2 Seite 3 Seite 4", "Seite 5 Seite 6 Seite 7"), pageTexts(four))
        val hand = File(dir, "hand.pdf").also { PrintPilot.handout(src, it) }
        assertEquals(3, Loader.loadPDF(hand).use { it.numberOfPages })
        val duplex = File(dir, "duplex.pdf").also { PrintPilot.duplex(src, it, 10.0) }
        assertEquals(7, Loader.loadPDF(duplex).use { it.numberOfPages })

        val bleed = File(dir, "beschnitt.pdf")
        assertTrue(PrintPilot.bleed(src, bleed, 3.0, marks = true))
        Loader.loadPDF(bleed).use { d ->
            val p = d.getPage(0)
            assertEquals(PDRectangle.A4.width + 2 * 11 * MM, p.mediaBox.width, 0.5f)
            assertEquals(PDRectangle.A4.width, p.trimBox.width, 0.5f)
            assertEquals(PDRectangle.A4.width + 6 * MM, p.bleedBox.width, 0.5f)
        }
        assertTrue(PrintPilot.check(bleed).hasBleed)
    }

    @Test
    fun printCheckFindsWeakPicturesAndLooseFonts() {
        val f = File(dir, "poster.pdf")
        PDDocument().use { d ->
            val p = PDPage(PDRectangle.A4)
            d.addPage(p)
            val img = LosslessFactory.createFromImage(d, BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB))
            PDPageContentStream(d, p).use { cs ->
                cs.drawImage(img, 50f, 300f, 400f, 400f)
                cs.beginText(); cs.setFont(PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN), 30f); cs.newLineAtOffset(50f, 100f); cs.showText("Poster"); cs.endText()
            }
            d.save(f)
        }
        val c = PrintPilot.check(f)
        assertEquals(1, c.pages)
        assertEquals(210.0, c.widthMm, 1.0)
        assertEquals(7, c.images.single().dpi)
        assertTrue(c.issues.any { it.title == "Bilder mit zu wenig Auflösung" && it.severe }, c.issues.toString())
        assertTrue(c.issues.any { it.title == "Schriften nicht eingebettet" && "Times-Roman" in it.detail }, c.issues.toString())
        assertTrue(c.issues.any { it.title == "Ohne Beschnitt" })
        assertEquals(listOf("Times-Roman" to false), PrintPilot.fonts(f).map { it.name to it.embedded })
    }

    // ── Schriften-Notfall ──

    private fun docx(fonts: List<String>, pages: Int = 12): File {
        val f = File(dir, "arbeit.docx")
        val runs = fonts.joinToString("") { "<w:p><w:r><w:rPr><w:rFonts w:ascii=\"$it\" w:hAnsi=\"$it\"/></w:rPr><w:t>Text in $it</w:t></w:r></w:p>" }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun put(n: String, s: String) { z.putNextEntry(ZipEntry(n)); z.write(s.toByteArray()); z.closeEntry() }
            put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""")
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            put("word/_rels/document.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"></Relationships>""")
            put("word/document.xml", """<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$runs</w:body></w:document>""")
            put("word/settings.xml", """<?xml version="1.0" encoding="UTF-8"?><w:settings xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:zoom w:percent="100"/></w:settings>""")
            put("docProps/app.xml", """<?xml version="1.0" encoding="UTF-8"?><Properties><Pages>$pages</Pages></Properties>""")
        }
        f.writeBytes(out.toByteArray())
        return f
    }

    @Test
    fun missingFontsGetAReplacementWithItsEffect() {
        val f = docx(listOf("Carlito", "Calibri", "Univers", "Gibt Es Nicht Sans", "Symbol"))
        val r = FontRescue.check(f)
        assertEquals(12, r.pages)
        val names = r.fonts.map { it.name }
        assertEquals(listOf("Carlito", "Calibri", "Univers", "Gibt Es Nicht Sans"), names)
        val univers = r.fonts.first { it.name == "Univers" }
        assertTrue(univers.tu && univers.missing)
        if (!SystemFonts.installed("Calibri") && SystemFonts.installed("Carlito")) {
            val calibri = r.fonts.first { it.name == "Calibri" }
            assertEquals("Carlito", calibri.replacement)
            assertTrue(calibri.metric)
            assertEquals("Gleiche Zeichenbreiten – Umbrüche und Seitenzahl bleiben.", calibri.effect)
        }
        assertNotNull(r.fonts.first { it.name == "Gibt Es Nicht Sans" }.replacement)
        assertEquals("Text etwa 12 % breiter – aus 12 Seiten werden etwa 13.", FontRescue.effect("Calibri", "Arial", false, 12))

        // Replacing writes the new names into the file.
        val out = File(dir, "ersetzt.docx")
        FontRescue.replace(f, mapOf("Univers" to "Liberation Sans"), out)
        val again = FontRescue.officeFonts(out.name, out.readBytes())
        assertTrue("Liberation Sans" in again && "Univers" !in again, again.toString())
    }

    @Test
    fun installedFontsAreEmbeddedIntoWord() {
        val family = listOf("DejaVu Sans", "Liberation Sans", "Carlito", "Noto Sans").firstOrNull { SystemFonts.fileOf(it)?.extension == "ttf" } ?: return
        val f = docx(listOf(family))
        val out = File(dir, "eingebettet.docx")
        val e = FontRescue.embed(f, out)
        assertEquals(listOf(family), e.fonts, e.skipped.toString())
        ZipFile(out).use { z ->
            val table = z.getInputStream(z.getEntry("word/fontTable.xml")).readBytes().decodeToString()
            val key = Regex("w:fontKey=\"(\\{[^\"]+})\"").find(table)!!.groupValues[1]
            val part = z.getInputStream(z.getEntry("word/fonts/font1.odttf")).readBytes()
            assertContentEquals(SystemFonts.fileOf(family)!!.readBytes(), FontRescue.deobfuscate(part, key))
            assertTrue("odttf" in z.getInputStream(z.getEntry("[Content_Types].xml")).readBytes().decodeToString())
            assertTrue("embedTrueTypeFonts" in z.getInputStream(z.getEntry("word/settings.xml")).readBytes().decodeToString())
            assertTrue("relationships/fontTable" in z.getInputStream(z.getEntry("word/_rels/document.xml.rels")).readBytes().decodeToString())
        }
        assertTrue(FontRescue.officeFonts(out.name, out.readBytes())[family] == true)
    }
}
