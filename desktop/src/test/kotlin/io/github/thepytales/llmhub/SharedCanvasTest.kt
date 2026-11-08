package io.github.thepytales.llmhub

import androidx.compose.ui.graphics.toAwtImage
import io.github.thepytales.llmhub.creative.CanvasOps
import io.github.thepytales.llmhub.creative.DesignEditorState
import io.github.thepytales.llmhub.creative.SlideCanvas
import io.github.thepytales.llmhub.creative.filterOf
import io.github.thepytales.llmhub.creative.gradientOf
import io.github.thepytales.llmhub.creative.renderElement
import io.github.thepytales.llmhub.data.Design
import io.github.thepytales.llmhub.data.DesignElement
import io.github.thepytales.llmhub.data.DesignPage
import io.github.thepytales.llmhub.data.ElementKind
import io.github.thepytales.llmhub.docs.SlideDeck
import io.github.thepytales.llmhub.docs.SlideElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** AP-50 Gemeinsame Leinwand: the same operations for Gestalten and slides, groups, gradients and picture filters. */
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
}
