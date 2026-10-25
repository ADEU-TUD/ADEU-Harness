package io.github.thepytales.llmhub

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import io.github.thepytales.llmhub.ui.EditRegion
import io.github.thepytales.llmhub.ui.EditStroke
import io.github.thepytales.llmhub.ui.cleanSide
import io.github.thepytales.llmhub.ui.editPrompts
import io.github.thepytales.llmhub.ui.editSummary
import io.github.thepytales.llmhub.ui.renderMask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** "Überarbeiten" of pictures: mask, prompts and places in words. */
class ImageEditTest {
    private val sky = EditRegion(0, listOf(EditStroke(listOf(Offset(0.1f, 0.1f), Offset(0.25f, 0.15f)), 0.1f)), "blauer Himmel")
    private val dog = EditRegion(1, listOf(EditStroke(listOf(Offset(0.8f, 0.8f)), 0.1f)), "ein Hund")

    @Test
    fun maskIsTransparentOnlyWhereMarked() {
        val mask = renderMask(100, 100, listOf(sky, dog)).toPixelMap()
        assertEquals(0f, mask[15, 12].alpha, "markiert = durchsichtig")
        assertEquals(0f, mask[80, 80].alpha)
        assertEquals(1f, mask[50, 50].alpha, "Rest bleibt deckend")
        assertEquals(1f, mask[95, 5].alpha)
    }

    @Test
    fun promptsNameAreasAndPlaces() {
        val (withMask, withoutMask) = editPrompts(listOf(sky, dog, EditRegion(2, emptyList(), "ungenutzt")), "wärmeres Licht")
        assertTrue("Bereich 1: blauer Himmel" in withMask && "Bereich 2: ein Hund" in withMask)
        assertTrue("ungenutzt" !in withMask, "leere Bereiche fallen weg")
        assertTrue("oben links: blauer Himmel" in withoutMask && "unten rechts: ein Hund" in withoutMask)
        assertTrue("wärmeres Licht" in withMask && "wärmeres Licht" in withoutMask)
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
