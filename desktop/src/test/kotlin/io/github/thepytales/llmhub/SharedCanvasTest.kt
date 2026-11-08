package io.github.thepytales.llmhub

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.geometry.Offset
import io.github.thepytales.llmhub.creative.CanvasArt
import io.github.thepytales.llmhub.creative.CanvasOps
import io.github.thepytales.llmhub.creative.CanvasTable
import io.github.thepytales.llmhub.creative.FieldRich
import io.github.thepytales.llmhub.creative.Handle
import io.github.thepytales.llmhub.creative.DesignEditorState
import io.github.thepytales.llmhub.creative.SlideCanvas
import io.github.thepytales.llmhub.creative.filterOf
import io.github.thepytales.llmhub.creative.gradientOf
import io.github.thepytales.llmhub.creative.renderElement
import io.github.thepytales.llmhub.data.Design
import io.github.thepytales.llmhub.data.DesignElement
import io.github.thepytales.llmhub.data.DesignPage
import io.github.thepytales.llmhub.data.ElementKind
import io.github.thepytales.llmhub.docs.CellRich
import io.github.thepytales.llmhub.docs.PptxWriter
import io.github.thepytales.llmhub.docs.SlideDeck
import io.github.thepytales.llmhub.docs.SlideElement
import io.github.thepytales.llmhub.docs.text.CharFormat
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AP-50 Gemeinsame Leinwand: the same operations for Gestalten and slides, groups, gradients and picture filters; the
 * gestures of the slides on the canvas of Gestalten, formatting inside a text box and tables (Teil 2).
 */
class SharedCanvasTest {
    private fun rect(id: String, x: Float, y: Float, w: Float = 20f, h: Float = 10f, group: String = "") =
        DesignElement(id = id, kind = ElementKind.RECT, x = x, y = y, w = w, h = h, fill = "#00008C", group = group)

    @Test
    fun alignDistributeAndLayersWithGroups() {
        val els = listOf(rect("a", 10f, 10f), rect("b", 50f, 40f, group = "g"), rect("c", 80f, 60f, group = "g"), rect("d", 150f, 5f))
        // One element alone goes to the page; a group moves as one block.
        assertEquals(190f, CanvasOps.aligned(els, setOf("a"), "right", 210f, 297f).first { it.id == "a" }.x)
        val left = CanvasOps.aligned(els, setOf("b", "c"), "left", 210f, 297f)
        assertEquals(listOf(0f, 30f), left.filter { it.group == "g" }.map { it.x })
        // Several blocks align to their common bounds.
        val top = CanvasOps.aligned(els, setOf("a", "b", "c", "d"), "top", 210f, 297f)
        assertEquals(listOf(5f, 5f, 25f, 5f), top.map { it.y })
        // Three blocks spread evenly: a, the group b+c, d.
        val spread = CanvasOps.aligned(els, setOf("a", "b", "c", "d"), "distributeH", 210f, 297f)
        val a = spread.first { it.id == "a" }
        val b = spread.first { it.id == "b" }
        val c = spread.first { it.id == "c" }
        val d = spread.first { it.id == "d" }
        assertEquals((b.x - (a.x + a.w)), (d.x - (c.x + c.w)), 0.01f)
        assertEquals(setOf("b", "c"), CanvasOps.withGroups(els, setOf("c")))
        assertEquals(listOf("a", "d", "b", "c"), CanvasOps.reordered(els, setOf("b", "c"), up = true, all = true).map { it.id })
        assertEquals(listOf("b", "a", "c", "d"), CanvasOps.reordered(els, setOf("a"), up = true, all = false).map { it.id })
        assertEquals(els, CanvasOps.grouped(els, setOf("a"), "x"))
        assertEquals(listOf("y", "y"), CanvasOps.grouped(els, setOf("a", "d"), "y").filter { it.id in setOf("a", "d") }.map { it.group })
    }

    @Test
    fun designEditorUsesTheSharedOperations() {
        val design = Design(title = "Plakat", pages = listOf(DesignPage(elements = listOf(rect("a", 10f, 10f), rect("b", 60f, 30f), rect("c", 100f, 80f)))))
        val state = DesignEditorState(design)
        state.selection = setOf("a", "b")
        state.group()
        val g = state.current.elements.first().group
        assertTrue(g.isNotEmpty())
        assertEquals(setOf("a", "b"), state.withGroups(setOf("b")))
        state.selection = setOf("a", "b")
        state.align("right")
        // The group goes to the right edge of the page as one block.
        assertEquals(listOf(140f, 190f), state.current.elements.take(2).map { it.x })
        state.selection = setOf("a")
        state.reorder(up = true, all = true)
        assertEquals(listOf("c", "a", "b"), state.current.elements.map { it.id })
        state.selection = setOf("a", "b")
        state.ungroup()
        assertTrue(state.current.elements.all { it.group.isEmpty() })
        state.undo()
        assertTrue(state.current.elements.filter { it.id != "c" }.all { it.group == g })
    }

    @Test
    fun slideElementsBehaveLikeDesignElements() {
        val els = listOf(
            SlideElement(kind = "text", x = 2.0, y = 3.0, w = 10.0, h = 2.0, text = "Titel", group = "g"),
            SlideElement(kind = "form", shape = "ellipse", x = 5.0, y = 6.0, w = 4.0, h = 4.0, fill = "#00008C", group = "g"),
            SlideElement(kind = "bild", x = 20.0, y = 2.0, w = 8.0, h = 6.0, src = "Bilder/a.png"),
        )
        // The same conversion both ways keeps everything.
        assertEquals(els, SlideCanvas.fromDesign(els, SlideCanvas.toDesign(els)))
        assertEquals(ElementKind.ELLIPSE, SlideCanvas.toDesign(els)[1].kind)
        assertEquals(50f, SlideCanvas.toDesign(els)[1].x)
        // Centred on the slide: the group moves as one block, like in Gestalten.
        val centred = SlideCanvas.align(els, setOf(0), "hcenter")
        val left = minOf(centred[0].x, centred[1].x)
        val right = maxOf(centred[0].x + centred[0].w, centred[1].x + centred[1].w)
        assertEquals(33.867 / 2, (left + right) / 2, 0.01)
        assertEquals(3.0, centred[1].x - centred[0].x, 0.001)
        assertEquals(els[2], centred[2])
        assertEquals(listOf("bild", "text", "form"), SlideCanvas.reorder(els, setOf(2), up = false, all = true).map { it.kind })
        assertEquals(setOf(0, 1), SlideCanvas.groupOf(els, 1))
        // Saved with the slide.
        val md = SlideDeck.markdown(listOf(SlideDeck.Slide("Frei", layout = SlideDeck.Layout.FREE, elements = centred)))
        assertEquals(centred, SlideDeck.parse(md).single().elements)
    }

    @Test
    fun gradientsAndPictureFilters() {
        val plain = rect("p", 0f, 0f, 40f, 40f)
        assertNull(gradientOf(plain))
        assertNull(filterOf(plain))
        val grad = plain.copy(fill = "#FFFFFF", fill2 = "#000000", gradientAngle = 90f)
        assertNotNull(gradientOf(grad))
        assertTrue(grad.hasEffects)
        assertNotNull(filterOf(plain.copy(grayscale = true)))
        assertNotNull(filterOf(plain.copy(brightness = 0.3f, contrast = -0.2f)))
        // Drawn from white at the top to black at the bottom.
        val img = renderElement(Design(title = "x"), grad, 4f).toAwtImage()
        fun lum(y: Int) = img.getRGB(img.width / 2, y).let { ((it shr 16) and 0xFF) + ((it shr 8) and 0xFF) + (it and 0xFF) }
        assertTrue(lum(2) > 600 && lum(img.height - 3) < 150, "oben ${lum(2)}, unten ${lum(img.height - 3)}")
        // Left to right with 0°.
        val side = renderElement(Design(title = "x"), grad.copy(gradientAngle = 0f), 4f).toAwtImage()
        fun lumX(x: Int) = side.getRGB(x, side.height / 2).let { ((it shr 16) and 0xFF) + ((it shr 8) and 0xFF) + (it and 0xFF) }
        assertTrue(lumX(2) > lumX(side.width - 3) + 300)
    }

    // ── Teil 2: slides on the canvas of Gestalten, text formatting inside a box, tables (AP-50) ──

    @Test
    fun slideGesturesRunOnTheCanvasOfGestalten() {
        val els = listOf(
            SlideElement(kind = "text", x = 2.0, y = 2.0, w = 8.0, h = 2.0, text = "Titel"),
            SlideElement(kind = "tudshape", x = 20.0, y = 5.0, w = 6.0, h = 6.0, fill = "#97C6FF"),
            SlideElement(kind = "symbol", icon = "School", x = 5.0, y = 10.0, w = 3.0, h = 3.0),
            SlideElement(kind = "qr", text = "https://tu-dresden.de", x = 28.0, y = 12.0, w = 4.0, h = 4.0),
        )
        val d = SlideCanvas.toDesign(els)
        assertEquals(listOf(ElementKind.TEXT, ElementKind.TUD_SHAPE, ElementKind.ICON, ElementKind.QR), d.map { it.kind })
        assertEquals("School", d[2].icon)
        val state = DesignEditorState(SlideCanvas.design(els))
        assertEquals(SlideCanvas.W, state.design.width)
        // Einrasten: the box moved close to the middle of the slide snaps onto it and shows the guide.
        val box = state.current.elements[0]
        val wanted = SlideCanvas.W / 2 - box.w / 2 - box.x
        state.beginGesture()
        state.dragMove(listOf(box), Offset(wanted + 1.5f, 0f), snap = 3f, free = false)
        assertEquals(SlideCanvas.W / 2, state.current.elements[0].cx, 0.01f)
        assertTrue(state.guides.any { it.vertical && kotlin.math.abs(it.at - SlideCanvas.W / 2) < 0.01f })
        // Strg moves freely.
        state.dragMove(listOf(box), Offset(wanted + 1.5f, 0f), snap = 3f, free = true)
        assertEquals(SlideCanvas.W / 2 + 1.5f, state.current.elements[0].cx, 0.01f)
        state.endGesture()
        // Drehen: the TUD-Shape turns in 45° steps only, other elements freely (45° within 3°).
        val shape = state.current.elements[1]
        state.dragRotate(shape, Offset(shape.cx + 30f, shape.cy - 25f), steps = false)
        assertEquals(45f, state.current.elements[1].rotation)
        val text = state.current.elements[0]
        state.dragRotate(text, Offset(text.cx + 10f, text.cy - 30f), steps = true)
        assertEquals(15f, state.current.elements[0].rotation)
        // Resizing at a corner keeps the proportions of a QR code.
        val qr = state.current.elements[3]
        state.dragResize(qr, 4, Offset(qr.x + 60f, qr.y + 30f), keepRatio = Handle.keepsRatio(qr))
        assertEquals(state.current.elements[3].w, state.current.elements[3].h, 0.01f)
        // Back on the slide: rotation and sizes in cm, the rest unchanged.
        val back = SlideCanvas.fromDesign(els, state.current.elements)
        assertEquals(45.0, back[1].rotation)
        assertEquals(els[1].copy(rotation = 45.0), back[1])
        assertEquals(back[3].w, back[3].h, 0.001)
        // Mehrfachauswahl: grouping several, a click on one takes the group, aligning moves them as one block.
        val grouped = SlideCanvas.group(els, setOf(0, 2), "g1")
        assertEquals(setOf(0, 2), SlideCanvas.groupOf(grouped, 2))
        assertEquals(SlideCanvas.ids(setOf(0, 2)), CanvasOps.withGroups(SlideCanvas.toDesign(grouped), setOf("s0")))
        assertEquals(listOf("", "", "", ""), SlideCanvas.group(grouped, setOf(0), "", apart = true).map { it.group })
        val spread = SlideCanvas.align(els, setOf(0, 1, 3), "top")
        assertEquals(listOf(2.0, 2.0, 2.0), listOf(spread[0].y, spread[1].y, spread[3].y))
        assertEquals(els[2], spread[2])
        // Saved with the slide.
        val md = SlideDeck.markdown(listOf(SlideDeck.Slide("Frei", layout = SlideDeck.Layout.FREE, elements = back)))
        assertEquals(back, SlideDeck.parse(md).single().elements)
    }

    @Test
    fun formattingInsideATextBox() {
        val raw = "Ein **fetter** und *kursiver* Text\nmit <u>Strich</u> und <span style=\"color:#C00000\">Rot</span>"
        val p = FieldRich.parse(raw)
        assertEquals("Ein fetter und kursiver Text\nmit Strich und Rot", p.plain)
        assertTrue(p.chars[5].bold && !p.chars[3].bold && p.chars[16].italic)
        assertTrue(p.chars[p.plain.indexOf("Strich")].underline)
        assertEquals("C00000", p.chars[p.plain.indexOf("Rot")].color)
        assertEquals(raw, FieldRich.write(p.plain, p.chars))
        // Plain boxes keep every character: a star stays a star.
        assertEquals("2*3*4", FieldRich.parse("2*3*4", rich = false).plain)
        // Formatting a selection like Word: on if one character lacks it, otherwise off.
        val bold = FieldRich.toggle(p.chars, 0, 4, CellRich.Mark.BOLD)
        assertEquals("**Ein fetter** und", FieldRich.write(p.plain, bold).substringBefore(" *kursiver"))
        assertEquals(p.chars, FieldRich.toggle(bold, 0, 4, CellRich.Mark.BOLD))
        val red = FieldRich.color(p.chars, 0, 3, "#00008C")
        assertTrue(FieldRich.write(p.plain, red).startsWith("<span style=\"color:#00008C\">Ein</span>"))
        // Typing keeps the formats around and gives new characters the format before them (or the one switched on).
        val typed = FieldRich.reflow(p.plain, p.chars, p.plain.replace("fetter", "fetterer"))
        assertEquals(p.plain.length + 2, typed.size)
        assertTrue(typed[10].bold && typed[11].bold && !typed[12].bold)
        val italic = FieldRich.reflow("ab", listOf(CharFormat.PLAIN, CharFormat.PLAIN), "aXb", CharFormat(italic = true))
        assertEquals(listOf(false, true, false), italic.map { it.italic })
        // Runs per paragraph for PowerPoint.
        val lines = FieldRich.lines(raw, rich = true)
        assertEquals(2, lines.size)
        assertEquals("fetter" to CharFormat(bold = true), lines[0][1])
        assertEquals("Ein fetter und kursiver Text", FieldRich.annotated(raw, rich = true).text.lines().first())
        // A slide's rich text box survives the Markdown of the deck and becomes runs with b, i, u and colour in PowerPoint.
        val box = SlideElement(kind = "text", x = 2.0, y = 4.0, w = 20.0, h = 4.0, text = raw, rich = true, rotation = 30.0)
        val table = SlideElement(kind = "tabelle", x = 2.0, y = 10.0, w = 20.0, h = 6.0, size = 16, color = "#000000", rows = listOf(listOf("Fach", "Note"), listOf("**Mathe**", "1,3")))
        val shape = SlideElement(kind = "tudshape", x = 25.0, y = 2.0, w = 6.0, h = 6.0, fill = "#97C6FF", flipX = true)
        val form = SlideElement(kind = "form", shape = "rechteck", x = 25.0, y = 10.0, w = 6.0, h = 3.0, fill = "#00008C", fill2 = "#97C6FF")
        val md = SlideDeck.markdown(listOf(SlideDeck.Slide("Titel", layout = SlideDeck.Layout.TITLE), SlideDeck.Slide("", layout = SlideDeck.Layout.FREE, elements = listOf(box, table, shape, form))))
        assertEquals(listOf(box, table, shape, form), SlideDeck.parse(md)[1].elements)
        val file = Files.createTempFile("leinwand", ".pptx").toFile()
        PptxWriter.write(file, "Leinwand", md, "Professur")
        val slide = ZipFile(file).use { z -> z.getInputStream(z.getEntry("ppt/slides/slide2.xml")).readBytes().decodeToString() }
        assertTrue("<a:t>fetter</a:t>" in slide && Regex("b=\"1\"[^>]*>(?:(?!</a:rPr>).)*</a:rPr><a:t>fetter").containsMatchIn(slide), slide.take(400))
        assertTrue(Regex("i=\"1\"[^>]*>(?:(?!</a:rPr>).)*</a:rPr><a:t>kursiver").containsMatchIn(slide))
        assertTrue(Regex("u=\"sng\"[^>]*>(?:(?!</a:rPr>).)*</a:rPr><a:t>Strich").containsMatchIn(slide))
        assertTrue("C00000" in slide && "**" !in slide && "&lt;u&gt;" !in slide)
        assertTrue("rot=\"1800000\"" in slide, "Drehung")
        // The table is a real PowerPoint table with the head row in TUD blue and the bold cell as a bold run.
        assertTrue("<a:tbl>" in slide && "<a:gridCol" in slide && "00008C" in slide && "<a:t>Mathe</a:t>" in slide)
        // TUD-Shape as own geometry (mirrored), gradient fill.
        assertTrue("TUD-Shape" in slide && "<a:custGeom>" in slide && "flipH=\"1\"" in slide)
        assertTrue("<a:gradFill" in slide && "97C6FF" in slide)
        // Symbols and QR codes go in as pictures drawn by the renderer of Gestalten.
        assertNotNull(CanvasArt.png(SlideCanvas.toDesign(SlideElement(kind = "qr", text = "x", w = 3.0, h = 3.0), "q"), 8f))
        // The handout shows the same: formatted runs, the turn, the table and the Shape.
        val html = io.github.thepytales.llmhub.docs.SlideHandout.html(md, "Leinwand", 1, notes = false)
        assertTrue("<b>fetter</b>" in html && "<u>Strich</u>" in html && "color:#C00000" in html && "**" !in html)
        assertTrue("rotate(30.0deg)" in html && "<table" in html && "<b>Mathe</b>" in html && "<polygon" in html && "linear-gradient" in html)
    }

    @Test
    fun formattedTextAndTablesInGestalten() {
        val rich = DesignElement(id = "t", kind = ElementKind.TEXT, x = 10f, y = 10f, w = 120f, h = 30f, text = "Ein **fettes** Wort", rich = true, fontSize = 24f, color = "#000000")
        // The renderer lays out the visible text (no stars), the same width as plain text of the same words in bold.
        val laid = io.github.thepytales.llmhub.creative.DesignText.layout(rich)
        assertEquals("Ein fettes Wort", laid.layout.layoutInput.text.text)
        assertTrue(laid.layout.layoutInput.text.spanStyles.isNotEmpty())
        val plain = rich.copy(rich = false)
        assertEquals("Ein **fettes** Wort", io.github.thepytales.llmhub.creative.DesignText.layout(plain).layout.layoutInput.text.text)
        val table = DesignElement(id = "tab", kind = ElementKind.TABLE, x = 10f, y = 60f, w = 150f, h = 40f, rows = CanvasTable.blank(3, 2), fontSize = 14f, color = "#000000")
        assertEquals(2, CanvasTable.cols(table.rows))
        assertEquals(CanvasTable.HEAD_FILL, CanvasTable.fill(table, 0))
        assertEquals(CanvasTable.BAND_FILL, CanvasTable.fill(table, 2))
        assertEquals(listOf(listOf("A", ""), listOf("", ""), listOf("", "x")), CanvasTable.withCell(listOf(listOf("A")), 2, 1, "x"))
        assertEquals(3, CanvasTable.cols(CanvasTable.addColumn(table.rows)))
        assertEquals(4, CanvasTable.addRow(table.rows).size)
        // The head row is drawn in TUD blue.
        val d = Design(title = "Tabelle", pages = listOf(DesignPage(elements = listOf(rich, table))))
        val img = renderElement(d, table, 4f).toAwtImage()
        val head = img.getRGB(img.width / 4, 3)
        assertTrue((head and 0xFF) > 0x70 && ((head shr 16) and 0xFF) < 0x30, "Kopfzeile ${Integer.toHexString(head)}")
        // PDF and PowerPoint of the design: formatted runs and a real table.
        val pptx = Files.createTempFile("gestalten", ".pptx").toFile()
        io.github.thepytales.llmhub.creative.DesignExport.writePptx(d, pptx)
        val slide = ZipFile(pptx).use { z -> z.getInputStream(z.getEntry("ppt/slides/slide1.xml")).readBytes().decodeToString() }
        assertTrue(Regex("b=\"1\"[^>]*>(?:(?!</a:rPr>).)*</a:rPr><a:t>fettes").containsMatchIn(slide) && "**" !in slide)
        assertTrue("<a:tbl>" in slide && "Spalte 1" in slide)
        val pdf = Files.createTempFile("gestalten", ".pdf").toFile()
        io.github.thepytales.llmhub.creative.DesignExport.writePdf(d, pdf)
        org.apache.pdfbox.Loader.loadPDF(pdf).use { doc ->
            val text = org.apache.pdfbox.text.PDFTextStripper().getText(doc).replace(Regex("\\s+"), " ")
            assertTrue("Ein fettes Wort" in text && "**" !in text, text)
        }
    }
}
