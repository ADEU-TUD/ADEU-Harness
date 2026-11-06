package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.desktop.MacInputDevices
import io.github.thepytales.llmhub.desktop.MacPointer
import java.awt.Canvas
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The click fix for macOS (MacClickFix): button state from the event types, no scrolling during a click. */
class MacClickFixTest {
    private val src = Canvas()
    private var clock = 1_000L
    private val logged = mutableListOf<String>()
    private val pointer = MacPointer(now = { clock }, log = { logged += it })

    // With the screen position given: the canvas is not on screen, so AWT would put 0 there.
    private fun mouse(id: Int, mods: Int = 0, x: Int = 50, y: Int = 50, button: Int = MouseEvent.BUTTON1) =
        MouseEvent(src, id, clock, mods, x, y, x, y, 1, false, if (id == MouseEvent.MOUSE_MOVED) MouseEvent.NOBUTTON else button)

    private fun press(mods: Int = InputEvent.BUTTON1_DOWN_MASK, x: Int = 50, y: Int = 50, button: Int = MouseEvent.BUTTON1) =
        mouse(MouseEvent.MOUSE_PRESSED, mods, x, y, button)

    private fun release(mods: Int = 0, x: Int = 50, y: Int = 50, button: Int = MouseEvent.BUTTON1) =
        mouse(MouseEvent.MOUSE_RELEASED, mods, x, y, button)

    private fun wheel(mods: Int = 0) =
        MouseWheelEvent(src, MouseEvent.MOUSE_WHEEL, clock, mods, 50, 50, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, 1)

    private fun buttons(e: Any?) = (e as MouseEvent).modifiersEx and
        (InputEvent.BUTTON1_DOWN_MASK or InputEvent.BUTTON2_DOWN_MASK or InputEvent.BUTTON3_DOWN_MASK)

    @Test
    fun buttonBitsFollowTheEvents() {
        // A press without the button in the modifiers (tap to click) gets it …
        assertEquals(InputEvent.BUTTON1_DOWN_MASK, buttons(pointer.filter(press(mods = 0))))
        // … a release that still carries it loses it …
        assertEquals(0, buttons(pointer.filter(release(mods = InputEvent.BUTTON1_DOWN_MASK))))
        // … a move after it as well (macOS sends "moved" only while no button is down) …
        assertEquals(0, buttons(pointer.filter(mouse(MouseEvent.MOUSE_MOVED, InputEvent.BUTTON1_DOWN_MASK))))
        // … also a stale right button, which made Compose think the pointer was still down and lose the next click.
        assertEquals(0, buttons(pointer.filter(mouse(MouseEvent.MOUSE_MOVED, InputEvent.BUTTON3_DOWN_MASK))))
        // A drag right after the press keeps its button even when macOS already reports none.
        pointer.filter(press())
        assertEquals(InputEvent.BUTTON1_DOWN_MASK, buttons(pointer.filter(mouse(MouseEvent.MOUSE_DRAGGED, 0))))
        pointer.filter(release())
        // Correct events pass unchanged (no copy), and so do the keyboard modifiers.
        val ok = press(mods = InputEvent.BUTTON1_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)
        assertSame(ok, pointer.filter(ok))
        val fixed = pointer.filter(release(mods = InputEvent.BUTTON1_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)) as MouseEvent
        assertEquals(InputEvent.SHIFT_DOWN_MASK, fixed.modifiersEx)
        assertEquals(MouseEvent.MOUSE_RELEASED, fixed.id)
        assertEquals(MouseEvent.BUTTON1, fixed.button)
        val move = mouse(MouseEvent.MOUSE_MOVED)
        assertSame(move, pointer.filter(move))
        assertTrue(pointer.fixedButtons >= 5, "${pointer.fixedButtons}")
    }

    @Test
    fun noScrollingDuringAClick() {
        // Magic Mouse: the finger on the touch surface scrolls a little while clicking – held back until the release.
        pointer.filter(press())
        clock += 40
        assertNull(pointer.filter(wheel(InputEvent.BUTTON1_DOWN_MASK)))
        pointer.filter(mouse(MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, x = 52, y = 51))
        assertNull(pointer.filter(wheel()))
        clock += 40
        pointer.filter(release())
        assertEquals(2, pointer.heldScrolls)
        // After the click scrolling works again (without the stale button bit).
        val after = pointer.filter(wheel(InputEvent.BUTTON1_DOWN_MASK))
        assertNotNull(after)
        assertEquals(0, buttons(after))
        assertTrue(after is MouseWheelEvent && after.wheelRotation == 1 && after.scrollType == MouseWheelEvent.WHEEL_UNIT_SCROLL)
        // A correct scroll event passes as it is.
        val plain = wheel()
        assertSame(plain, pointer.filter(plain))
    }

    @Test
    fun scrollingWhileDraggingOrHoldingLongStays() {
        // Dragging (e.g. a section in the outline) and scrolling at the same time keeps working.
        pointer.filter(press())
        pointer.filter(mouse(MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, x = 50 + MacPointer.SLOP + 1))
        assertNotNull(pointer.filter(wheel(InputEvent.BUTTON1_DOWN_MASK)))
        pointer.filter(release())
        // Held without moving for longer than a click: scrolling passes again.
        pointer.filter(press())
        clock += MacPointer.CLICK_MS + 1
        assertNotNull(pointer.filter(wheel(InputEvent.BUTTON1_DOWN_MASK)))
        pointer.filter(release())
        assertEquals(0, pointer.heldScrolls)
    }

    @Test
    fun aRepeatedClickNotesTheClickBefore() {
        pointer.filter(mouse(MouseEvent.MOUSE_MOVED))
        pointer.filter(press(mods = 0))
        clock += 30
        pointer.filter(wheel())
        clock += 50
        pointer.filter(release())
        repeat(3) { pointer.filter(mouse(MouseEvent.MOUSE_MOVED, x = 51)) }
        clock += 700
        // Clicked again at the same spot: a note with what happened during the first click.
        pointer.filter(press(x = 51, y = 52))
        val notes = pointer.notes()
        assertEquals(1, notes.size, notes.toString())
        assertTrue("nach 700 ms: P1* · W gehalten · R1 +80ms · M×3" in notes[0], notes[0])
        assertEquals(1, logged.size)
        pointer.filter(release(x = 51, y = 52))
        // A click elsewhere or much later is no repetition.
        clock += 500
        pointer.filter(press(x = 300, y = 52))
        pointer.filter(release(x = 300, y = 52))
        clock += MacPointer.REPEAT_MAX_MS + 1
        pointer.filter(press(x = 300, y = 52))
        assertEquals(1, pointer.notes().size)
    }

    @Test
    fun inputDevicesAsFixedLabels() {
        assertEquals(
            listOf("Trackpad (eingebaut)", "Magic Mouse", "Logitech-Maus"),
            MacInputDevices.pointingDevices(listOf("Apple Internal Keyboard / Trackpad", "Magic Mouse von Peter", "MX Master 3S", "Keyboard Backlight")),
        )
        assertEquals(
            listOf("Logi Options+", "BetterTouchTool"),
            MacInputDevices.inputTools("/Applications/logioptionsplus.app/Contents/MacOS/logioptionsplus\n/Applications/BetterTouchTool.app/Contents/MacOS/BetterTouchTool\n/usr/sbin/cfprefsd"),
        )
        assertEquals(emptyList(), MacInputDevices.inputTools("/usr/libexec/trustd\n/System/Library/CoreServices/Finder.app/Contents/MacOS/Finder"))
    }
}
