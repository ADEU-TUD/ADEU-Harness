package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.docs.DocBlocks
import io.github.thepytales.llmhub.docs.DocMarkup
import io.github.thepytales.llmhub.docs.MarkdownDocx
import io.github.thepytales.llmhub.ui.FlowSeg
import io.github.thepytales.llmhub.ui.FlowState
import io.github.thepytales.llmhub.ui.moveBlock
import io.github.thepytales.llmhub.ui.storedWidths
import io.github.thepytales.llmhub.ui.withColumn
import io.github.thepytales.llmhub.ui.withoutColumn
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Bilder und Tabellen anfassen (AP-125 d): what the editor stores, and that Word gets it. */
class FlowBlocksTest {
    @Test
    fun imageKeepsWidthPlaceAndAltText() {
        val img = DocMarkup.Image("Abb. 1: Hörsaal", "Bilder/a.png", 50, align = "right", description = "Ein voller Hörsaal")
        val md = img.markdown()
        assertEquals("![Abb. 1: Hörsaal](Bilder/a.png){width=50% align=right alt=\"Ein voller Hörsaal\"}", md)
        assertEquals(img, DocMarkup.image(md))
        assertEquals("Ein voller Hörsaal", img.altText)
        // Older lines read as before; without its own Alternativtext the caption is the alternative text.
        val old = DocMarkup.image("![Foto](x.png){width=60%}")!!
        assertEquals(60, old.width)
        assertNull(old.align)
        assertEquals("Foto", old.altText)
        assertEquals("![Foto](x.png){width=60%}", old.markdown())
        // A width inside the Alternativtext is not the width of the picture.
        assertNull(DocMarkup.image("![A](x.png){alt=\"width=30%\"}")!!.width)
    }

    @Test
    fun textWrapAroundThePictureLikeWord() {
        val img = DocMarkup.Image("Abb. 2", "a.png", 40, wrap = "right")
        assertEquals("![Abb. 2](a.png){width=40% wrap=right}", img.markdown())
        assertEquals(img, DocMarkup.image(img.markdown()))
        // On, the picture goes to its edge and gets room for the text; off, it keeps that edge in a line of its own.
        val on = io.github.thepytales.llmhub.ui.wrapped(DocMarkup.Image("A", "a.png", null, align = "left"), true)
        assertEquals("left", on.wrap)
        assertEquals(40, on.width)
        assertEquals("left", io.github.thepytales.llmhub.ui.wrapped(on, false).align)
        assertNull(io.github.thepytales.llmhub.ui.placed(on, null).wrap)
        // The PDF lets it float.
        val dir = Files.createTempDirectory("adeu-ap125e").toFile()
        val png = File(dir, "a.png")
        javax.imageio.ImageIO.write(java.awt.image.BufferedImage(40, 20, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", png)
        val md = DocMarkup.Image("Abb. 2", png.absolutePath, 40, wrap = "right").markdown() + "\n\nText daneben.\n"
        val out = File(dir, "a.docx")
        MarkdownDocx.write(out, "Test", md)
        val xml = ZipFile(out).use { z -> z.getInputStream(z.getEntry("word/document.xml")).readBytes().decodeToString() }
        // Word: picture and caption in a text box at the right margin with „Quadrat“, as Word itself does it.
        assertTrue("<wp:wrapSquare wrapText=\"bothSides\"/>" in xml && "<wp:align>right</wp:align>" in xml, "verankert rechts mit Umbruch")
        val box = xml.substringAfter("<w:txbxContent>").substringBefore("</w:txbxContent>")
        assertTrue("<pic:pic" in box && "Abb. 2" in box, "Bild und Unterschrift im Textfeld")
        javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(xml.byteInputStream())
        val html = io.github.thepytales.llmhub.docs.DocumentWriter.toHtml("Test", md)
        assertTrue("float:right" in html, html.take(400))
        dir.deleteRecursively()
    }

    @Test
    fun columnWidthsInTheLineUnderTheHeader() {
        val rows = listOf(listOf("Phase", "Zeitraum", "Ergebnis"), listOf("1", "2026", "Modul"))
        val md = DocBlocks.table(rows, listOf(20, 30, 50))
        assertTrue("| -------------------- | ------------------------------ |" in md, md)
        assertEquals(listOf(20, 30, 50), DocBlocks.tableWidths(md))
        assertEquals(rows, DocBlocks.tableRows(md))
        // Equal widths stay the plain table of before.
        assertEquals(DocBlocks.table(rows), DocBlocks.table(rows, listOf(33, 33, 33)))
        assertNull(DocBlocks.tableWidths(DocBlocks.table(rows)))
        // New and removed columns keep the sum at 100 %.
        val (r2, w2) = withColumn(rows, listOf(20f, 30f, 50f), 1)
        assertEquals(4, r2[0].size)
        assertEquals(100f, w2.sum(), 0.01f)
        val (r3, w3) = withoutColumn(r2, w2, 1)
        assertEquals(rows, r3)
        assertEquals(listOf(20, 30, 50), storedWidths(w3))
    }

    @Test
    fun wordGetsColumnWidthsAndAlignment() {
        val dir = Files.createTempDirectory("adeu-ap125d").toFile()
        val png = File(dir, "a.png")
        val bi = java.awt.image.BufferedImage(40, 20, java.awt.image.BufferedImage.TYPE_INT_RGB)
        javax.imageio.ImageIO.write(bi, "png", png)
        val md = "Text\n\n" + DocBlocks.table(listOf(listOf("A", "B"), listOf("1", "2")), listOf(25, 75)) + "\n\n" +
            DocMarkup.Image("Abb. 1", png.absolutePath, 40, align = "right", description = "Schwarzes Rechteck").markdown() + "\n"
        val out = File(dir, "a.docx")
        MarkdownDocx.write(out, "Test", md)
        val xml = ZipFile(out).use { z -> z.getInputStream(z.getEntry("word/document.xml")).readBytes().decodeToString() }
        val grid = Regex("<w:gridCol w:w=\"(\\d+)\"/>").findAll(xml).map { it.groupValues[1].toInt() }.toList()
        assertEquals(2, grid.size, xml)
        assertEquals(3f, grid[1].toFloat() / grid[0], 0.05f)
        assertTrue("<w:jc w:val=\"right\"/>" in xml, "Bild rechts")
        assertTrue("descr=\"Schwarzes Rechteck\"" in xml, "eigener Alternativtext")
        assertTrue("Abb. 1" in xml, "Bildunterschrift")
        dir.deleteRecursively()
    }

    @Test
    fun blocksMoveLikeParagraphs() {
        val s = FlowState("Eins\n\nZwei\n\n| A | B |\n| --- | --- |\n| 1 | 2 |\n\nDrei\n")
        val table = s.segs.filterIsInstance<FlowSeg.Raw>().first()
        s.moveBlock(table, up = true)
        assertTrue(s.markdown().startsWith("Eins\n\n| A"), s.markdown())
        assertTrue("| 1 | 2 |\n\nZwei\n\nDrei" in s.markdown(), s.markdown())
        s.moveBlock(table, up = false)
        s.moveBlock(table, up = false)
        assertTrue(s.markdown().trimEnd().endsWith("Drei\n\n| A | B |\n| --- | --- |\n| 1 | 2 |"), s.markdown())
        s.undo()
        assertTrue("| 1 | 2 |\n\nDrei" in s.markdown(), s.markdown())
    }
}
