package io.github.thepytales.llmhub

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import io.github.thepytales.llmhub.ui.EditRegion
import io.github.thepytales.llmhub.ui.EditStroke
import io.github.thepytales.llmhub.ui.cleanSide
import io.github.thepytales.llmhub.ui.editPrompts
import io.github.thepytales.llmhub.ui.editSummary
import io.github.thepytales.llmhub.ui.maskPng
import io.github.thepytales.llmhub.ui.renderMask
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** "Überarbeiten" of pictures: mask, prompts and places in words. */
class ImageEditTest {
    private val sky = EditRegion(0, listOf(EditStroke(listOf(Offset(0.1f, 0.1f), Offset(0.25f, 0.15f)), 0.1f)), "blauer Himmel")
    private val dog = EditRegion(1, listOf(EditStroke(listOf(Offset(0.8f, 0.8f)), 0.1f)), "ein Hund")

    @Test
    fun maskIsTransparentAndWhiteOnlyWhereMarked() {
        val painted = renderMask(100, 100, listOf(sky, dog)).toPixelMap()
        assertEquals(1f, painted[15, 12].red, "markiert = weiß")
        assertEquals(0f, painted[50, 50].red)
        // The PNG for the server: marked pixels white and transparent (OpenAI reads alpha, diffusers brightness).
        val png = ImageIO.read(ByteArrayInputStream(maskPng(100, 100, listOf(sky, dog))))
        fun argb(x: Int, y: Int) = png.getRGB(x, y).toLong() and 0xFFFFFFFFL
        assertEquals(0x00FFFFFFL, argb(15, 12), "markiert = weiß und durchsichtig")
        assertEquals(0x00FFFFFFL, argb(80, 80))
        assertEquals(0xFF000000L, argb(50, 50), "Rest bleibt schwarz und deckend")
        assertEquals(0xFF000000L, argb(95, 5))
    }

    @Test
    fun promptsNameAreasAndPlaces() {
        val (withMask, withoutMask) = editPrompts(listOf(sky, dog, EditRegion(2, emptyList(), "ungenutzt")), "wärmeres Licht")
        assertTrue("ungenutzt" !in withMask, "leere Bereiche fallen weg")
        // Both prompts name the places: servers that ignore the mask must still know where to change what.
        for (p in listOf(withMask, withoutMask)) {
            assertTrue("in the top left corner" in p && ": blauer Himmel" in p, p)
            assertTrue("in the bottom right corner" in p && ": ein Hund" in p, p)
            assertTrue("% from the left edge" in p)
            assertTrue("wärmeres Licht" in p)
        }
        assertEquals(listOf("1 · oben links: blauer Himmel", "2 · unten rechts: ein Hund"), editSummary(listOf(sky, dog)))
        // Without marks the description applies to the whole picture.
        assertEquals("Mach es winterlich" to "Mach es winterlich", editPrompts(emptyList(), "Mach es winterlich"))
    }

    @Test
    fun ownSizesStayWithinLimitsAndMultiplesOf16() {
        assertEquals(1024, cleanSide("1024"))
        assertEquals(1008, cleanSide("1000"))
        assertEquals(256, cleanSide("10"))
        assertEquals(2048, cleanSide("5000"))
        assertEquals(null, cleanSide("abc"))
    }
}
