package io.github.thepytales.llmhub

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.github.thepytales.llmhub.creative.DesignViewport
import io.github.thepytales.llmhub.ui.CtrlWheel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Zoom of the interface and of the design editor (release 1.0.50): a touchpad gesture made dozens of 10 % steps and
 * queued as many „Zoom … %“ hints, and zooming in the design editor threw the page out of view.
 */
class ZoomTest {
    private val view = Size(1000f, 700f)
    private val a4 = Size(210f, 297f)

    private fun near(a: Offset, b: Offset) = abs(a.x - b.x) < 0.01f && abs(a.y - b.y) < 0.01f

    @Test
    fun touchpadDeltasAddUpToWholeSteps() {
        val steps = CtrlWheel.Steps()
        // A touchpad gesture of 24 small deltas makes three steps, not 24.
        val made = (1..24).sumOf { steps.add(-0.125f) }
        assertEquals(3, made)
        // A mouse notch is one step each, in both directions.
        val wheel = CtrlWheel.Steps()
        assertEquals(1, wheel.add(-1f))
        assertEquals(-1, wheel.add(1f))
        // Changing direction forgets the rest.
        val turn = CtrlWheel.Steps()
        turn.add(-0.9f)
        assertEquals(0, turn.add(0.5f))
    }

    @Test
    fun zoomKeepsThePointUnderTheMouse() {
        val anchor = Offset(620f, 410f)
        var zoom = 1f
        var pan = Offset.Zero
        val before = DesignViewport.origin(view, a4, zoom, pan)
        val fit = DesignViewport.fit(view, a4)
        val mm = (anchor - before) / (fit * zoom)
        repeat(5) { val (z, p) = DesignViewport.zoomAt(view, a4, zoom, pan, 1.12f, anchor); zoom = z; pan = p }
        val after = DesignViewport.origin(view, a4, zoom, pan)
        assertTrue(near(after + mm * (fit * zoom), anchor), "Punkt unter der Maus verschoben")
        // Zooming back out by the same amount returns to the start.
        repeat(5) { val (z, p) = DesignViewport.zoomAt(view, a4, zoom, pan, 1 / 1.12f, anchor); zoom = z; pan = p }
        assertTrue(abs(zoom - 1f) < 0.001f && near(pan, Offset.Zero), "nicht zurück: $zoom $pan")
    }

    @Test
    fun theButtonsZoomAroundTheMiddleAndThePageStaysInView() {
        // Panned to the side, zooming with the buttons keeps the middle of the view on the same page point.
        val (z, p) = DesignViewport.zoomAt(view, a4, 2f, Offset(150f, -80f), 1.25f)
        assertEquals(2.5f, z, 0.001f)
        assertTrue(near(p, Offset(150f * 1.25f, -80f * 1.25f)), "$p")
        // However far it is pushed, at least part of the page stays visible.
        val far = DesignViewport.clampPan(view, a4, 1f, Offset(5000f, -5000f))
        val o = DesignViewport.origin(view, a4, 1f, far)
        val s = DesignViewport.fit(view, a4)
        assertTrue(o.x <= view.width - DesignViewport.KEEP_VISIBLE + 0.01f && o.y + a4.height * s >= DesignViewport.KEEP_VISIBLE - 0.01f, "$o")
        // Zoom limits hold.
        assertEquals(DesignViewport.MAX_ZOOM, DesignViewport.zoomAt(view, a4, 7f, Offset.Zero, 4f).first)
        assertEquals(DesignViewport.MIN_ZOOM, DesignViewport.zoomAt(view, a4, 0.3f, Offset.Zero, 0.1f).first)
    }

    @Test
    fun wheelFactorFollowsTheScrollAmount() {
        assertEquals(1.12f, DesignViewport.wheelFactor(-1f), 0.0001f)
        assertEquals(1f, DesignViewport.wheelFactor(0f), 0.0001f)
        assertTrue(DesignViewport.wheelFactor(0.1f) in 0.98f..1f)
    }
}
