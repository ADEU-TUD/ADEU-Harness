package io.github.thepytales.llmhub.desktop

import io.github.thepytales.llmhub.AppLog
import java.awt.AWTEvent
import java.awt.EventQueue
import java.awt.Toolkit
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.util.concurrent.atomic.AtomicInteger

/**
 * macOS: buttons sometimes reacted only to the second click (Einstellungen, Neuer Chat …). Compose reads which mouse
 * button is down from the extended modifiers of the AWT event. With „Tippen zum Klicken“ on the trackpad (and with
 * quick clicks) macOS' AWT now and then delivers a press without the button in the modifiers – or a release that still
 * carries it. Compose then sees no press, or a press that never ends, so the click only completes with the next one.
 *
 * This queue puts the button state right before Compose sees the event: a press always has its button down, a release
 * never. Only macOS; every other event passes unchanged. How often it helped is written to the log (Problem melden).
 */
object MacClickFix {
    private val fixed = AtomicInteger()

    fun install() {
        if (!Os.mac) return
        runCatching {
            Toolkit.getDefaultToolkit().systemEventQueue.push(object : EventQueue() {
                override fun dispatchEvent(event: AWTEvent) {
                    super.dispatchEvent(normalize(event))
                }
            })
        }.onFailure { AppLog.e("Klick-Korrektur für macOS", it) }
    }

    /** The event with the button state of a press or release made consistent (else the event itself). */
    internal fun normalize(event: AWTEvent): AWTEvent {
        if (event !is MouseEvent || event is MouseWheelEvent || event.button == MouseEvent.NOBUTTON) return event
        if (event.id != MouseEvent.MOUSE_PRESSED && event.id != MouseEvent.MOUSE_RELEASED) return event
        val mask = runCatching { InputEvent.getMaskForButton(event.button) }.getOrNull() ?: return event
        val down = event.modifiersEx and mask != 0
        val modifiers = when {
            event.id == MouseEvent.MOUSE_PRESSED && !down -> event.modifiersEx or mask
            event.id == MouseEvent.MOUSE_RELEASED && down -> event.modifiersEx and mask.inv()
            else -> return event
        }
        if (fixed.incrementAndGet().let { it == 1 || it % 50 == 0 }) AppLog.i("macOS-Klick korrigiert (${fixed.get()}×)")
        return MouseEvent(
            event.component, event.id, event.`when`, modifiers, event.x, event.y, event.xOnScreen, event.yOnScreen,
            event.clickCount, event.isPopupTrigger, event.button,
        )
    }
}
