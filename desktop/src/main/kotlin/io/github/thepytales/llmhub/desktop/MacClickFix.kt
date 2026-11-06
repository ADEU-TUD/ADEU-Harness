package io.github.thepytales.llmhub.desktop

import io.github.thepytales.llmhub.AppLog
import io.github.thepytales.llmhub.ErrorLog
import java.awt.AWTEvent
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * macOS: buttons that only reacted to the second, third or fourth click – on some Macs all the time, on others never.
 * Two causes, both in how the mouse reaches Compose:
 *
 * 1. **Scrolling during the click.** Compose cancels a click when, between press and release, a scroll event arrives
 *    and a list under the pointer takes it (the tap detector checks after every event whether it was consumed). On a
 *    Mac such scroll events come while clicking: from the touch surface of a Magic Mouse (the finger moves a little
 *    with every click), from a thumb resting on the trackpad, from the inertia of a swipe and from smooth or
 *    free-spinning wheels (Logitech). Nearly every place of the app lies in a scrollable list (side menu with many
 *    chats, settings, chat). So while a button is held and the pointer stays put – a click, not a drag – scroll events
 *    are held back, at most [MacPointer.CLICK_MS]; dragging and scrolling at the same time keeps working.
 * 2. **Button state.** AWT on macOS does not take the buttons in the modifiers from the event but asks the system at
 *    the moment it handles it (NSEvent.pressedMouseButtons); with tap to click and quick clicks that lags behind or runs
 *    ahead. Compose reads "pressed" from these bits, so a press without them, or a release or move with them, swallowed
 *    the click. The event types are exact (macOS sends "moved" only while no button is down), so the bits are set from
 *    the presses, releases and moves seen.
 *
 * Only macOS; elsewhere nothing is installed. What happened goes into „Problem melden“ ([report]): how often each fix
 * took effect, the input devices, and – when someone clicks again at the same spot right after a click – the events of
 * the click before, so a click that still gets lost can be traced.
 */
object MacClickFix {
    private val pointer = MacPointer { AppLog.i(it) }

    @Volatile
    private var installed = false

    @Volatile
    private var device = ""

    fun install() {
        if (!Os.mac) return
        runCatching {
            Toolkit.getDefaultToolkit().systemEventQueue.push(object : EventQueue() {
                override fun dispatchEvent(event: AWTEvent) {
                    // A mistake in the filter must never cost the event.
                    val out = try {
                        pointer.filter(event)
                    } catch (ex: RuntimeException) {
                        event
                    }
                    if (out != null) super.dispatchEvent(out)
                }
            })
            installed = true
            ErrorLog.diagnostics = ::report
            // A few seconds after the start, when the window is up (asks the screens and runs small system tools).
            Thread {
                Thread.sleep(5000)
                device = runCatching { MacInputDevices.describe() }.getOrDefault("")
                AppLog.i("macOS-Eingabe: $device")
            }.apply { isDaemon = true; name = "mac-input" }.start()
        }.onFailure { AppLog.e("Klick-Schutz für macOS", it) }
    }

    /** For „Problem melden“: whether the fix runs, how often it helped, the input devices and the last repeated clicks. */
    fun report(): String = buildString {
        append("macOS-Klicks: ").append(if (installed) "Schutz aktiv" else "Schutz nicht aktiv")
        append(" · Tastenzustand korrigiert ${pointer.fixedButtons}×")
        append(" · Scrollen während eines Klicks zurückgehalten ${pointer.heldScrolls}×")
        if (device.isNotEmpty()) append("\n").append(device)
        val notes = pointer.notes()
        if (notes.isNotEmpty()) {
            append("\nErneut an derselben Stelle geklickt (P Drücken, R Loslassen, W Scrollen, D Ziehen, M Bewegen; * korrigiert):")
            notes.forEach { append("\n").append(it) }
        }
    }
}

/**
 * The button state and the scroll guard of [MacClickFix] for the events of the AWT queue (one instance in the app, a
 * fresh one per test). Runs on the event thread only.
 */
internal class MacPointer(
    private val now: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
) {
    /** Buttons held according to the presses, releases and moves seen (extended modifier bits). */
    private var held = 0

    /** A click in progress: a button went down and the pointer has not moved further than [SLOP] since. */
    private var clicking = false
    private var clickAt = 0L
    private var clickX = 0
    private var clickY = 0

    var fixedButtons = 0
        private set
    var heldScrolls = 0
        private set

    /** The events since the last press (for the note when the next click comes at the same spot). */
    private var trace = Trace(0L)
    private var lastRelease: Release? = null
    private val notes = ArrayDeque<String>()
    private var logged = 0

    private class Release(val at: Long, val x: Int, val y: Int)

    /** The event to dispatch: [event] itself, a copy with the right button bits, or null (held back). */
    fun filter(event: AWTEvent): AWTEvent? {
        if (event !is MouseEvent) return event
        if (event is MouseWheelEvent) {
            val hold = clicking && now() - clickAt <= CLICK_MS
            trace.add("W", hold)
            if (hold) {
                heldScrolls++
                return null
            }
            return withButtons(event)
        }
        val mask = maskOf(event.button)
        when (event.id) {
            MouseEvent.MOUSE_PRESSED -> {
                if (held == 0 || !clicking) startClick(event)
                held = held or mask
            }
            MouseEvent.MOUSE_RELEASED -> {
                held = held and mask.inv()
                if (held == 0) {
                    clicking = false
                    lastRelease = Release(now(), event.xOnScreen, event.yOnScreen)
                }
            }
            MouseEvent.MOUSE_DRAGGED -> {
                if (clicking && (abs(event.xOnScreen - clickX) > SLOP || abs(event.yOnScreen - clickY) > SLOP)) clicking = false
            }
            MouseEvent.MOUSE_MOVED -> {
                held = 0
                clicking = false
            }
            MouseEvent.MOUSE_ENTERED, MouseEvent.MOUSE_EXITED -> Unit
            else -> return event
        }
        val out = withButtons(event)
        trace.add(event, out !== event, now())
        return out
    }

    /** The last notes about a click repeated at the same spot, oldest first. */
    fun notes(): List<String> = synchronized(notes) { notes.toList() }

    private fun startClick(event: MouseEvent) {
        val t = now()
        lastRelease?.let { r ->
            val gap = t - r.at
            if (gap in REPEAT_MIN_MS..REPEAT_MAX_MS && abs(event.xOnScreen - r.x) <= SPOT && abs(event.yOnScreen - r.y) <= SPOT) {
                val note = "${LocalTime.now().withNano(0)} nach $gap ms: ${trace.text()}"
                synchronized(notes) {
                    notes.addLast(note)
                    while (notes.size > MAX_NOTES) notes.removeFirst()
                }
                if (logged++ < MAX_LOGGED) log("macOS: erneut geklickt – $note")
            }
        }
        clicking = true
        clickAt = t
        clickX = event.xOnScreen
        clickY = event.yOnScreen
        trace = Trace(t)
    }

    /** [e] with the button bits of [held] (the event itself when they are right already). */
    private fun withButtons(e: MouseEvent): MouseEvent {
        val mods = (e.modifiersEx and BUTTONS.inv()) or held
        if (mods == e.modifiersEx) return e
        fixedButtons++
        return if (e is MouseWheelEvent) {
            MouseWheelEvent(
                e.component, e.id, e.`when`, mods, e.x, e.y, e.xOnScreen, e.yOnScreen, e.clickCount, e.isPopupTrigger,
                e.scrollType, e.scrollAmount, e.wheelRotation, e.preciseWheelRotation,
            )
        } else {
            MouseEvent(e.component, e.id, e.`when`, mods, e.x, e.y, e.xOnScreen, e.yOnScreen, e.clickCount, e.isPopupTrigger, e.button)
        }
    }

    /** The events of one click and what came after it, short: "P1 · W×2 gehalten · R1 +84ms · M×5". */
    private class Trace(private val start: Long) {
        private val parts = mutableListOf<StringBuilder>()
        private var kind = ""
        private var count = 0
        private var extra = 0
        private var more = 0

        fun add(e: MouseEvent, fixed: Boolean, at: Long) {
            val b = if (e.button in 1..5) e.button.toString() else ""
            when (e.id) {
                MouseEvent.MOUSE_PRESSED -> single("P$b" + (if (fixed) "*" else "") + if (parts.isEmpty()) "" else " +${at - start}ms")
                MouseEvent.MOUSE_RELEASED -> single("R$b" + (if (fixed) "*" else "") + " +${at - start}ms")
                MouseEvent.MOUSE_DRAGGED -> add("D", fixed)
                MouseEvent.MOUSE_MOVED -> add("M", fixed)
                MouseEvent.MOUSE_ENTERED -> single("Ein")
                MouseEvent.MOUSE_EXITED -> single("Aus")
            }
        }

        /** A run of moves, drags or scrolls; [flag] counts the corrected (scrolls: held back) ones. */
        fun add(k: String, flag: Boolean) {
            if (k != kind) {
                if (!open()) return
                kind = k
                count = 0
                extra = 0
            }
            count++
            if (flag) extra++
            parts[parts.lastIndex].apply {
                setLength(0)
                append(k).append(if (count > 1) "×$count" else "")
                if (extra > 0) append(if (k == "W") " gehalten" + (if (extra < count) " $extra" else "") else "*")
            }
        }

        private fun single(s: String) {
            kind = ""
            if (open()) parts[parts.lastIndex].append(s)
        }

        private fun open(): Boolean {
            if (parts.size >= MAX_PARTS) {
                more++
                return false
            }
            parts += StringBuilder()
            return true
        }

        fun text(): String = parts.joinToString(" · ") + if (more > 0) " · …" else ""
    }

    companion object {
        /** Longest click during which scrolling is held back (a long press is over by then). */
        const val CLICK_MS = 1500L

        /** Pointer movement (screen points) from where a held button counts as a drag. */
        const val SLOP = 12

        /** A press this soon after a release at the same spot ([SPOT] points) counts as clicking again. */
        const val REPEAT_MIN_MS = 300L
        const val REPEAT_MAX_MS = 3000L
        const val SPOT = 12

        private const val MAX_NOTES = 6
        private const val MAX_LOGGED = 30
        private const val MAX_PARTS = 24

        /** Extended bits of the buttons 1–5, the ones Compose reads. */
        private val BUTTONS = (1..5).fold(0) { acc, b -> acc or InputEvent.getMaskForButton(b) }

        private fun maskOf(button: Int) = if (button in 1..5) InputEvent.getMaskForButton(button) else 0
    }
}

/**
 * One line about the input of this Mac for [MacClickFix.report]: model, refresh rate, tap to click, pointing devices
 * and programs that change mouse events – the things that differ between a Mac that loses clicks and one that does
 * not. Device and program names are only reported as fixed labels (a Bluetooth name can contain the owner's name).
 */
internal object MacInputDevices {
    fun describe(): String {
        val parts = mutableListOf<String>()
        run("sysctl", "-n", "hw.model")?.trim()?.takeIf { it.isNotEmpty() && it.length <= 40 && ' ' !in it }?.let { parts += it }
        val hz = runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.displayMode.refreshRate }.filter { it > 0 }
        }.getOrDefault(emptyList())
        parts += if (hz.isEmpty()) "Bildwiederholrate unbekannt" else "Bildschirm " + hz.joinToString("/") + " Hz"
        parts += when (run("defaults", "read", "com.apple.AppleMultitouchTrackpad", "Clicking")?.trim()) {
            "1" -> "Tippen zum Klicken an"
            "0" -> "Tippen zum Klicken aus"
            else -> "Tippen zum Klicken unbekannt"
        }
        val products = run("ioreg", "-r", "-c", "IOHIDDevice", "-l", "-w", "0")?.let { out ->
            PRODUCT.findAll(out).map { it.groupValues[1] }.toList()
        }.orEmpty()
        parts += "Zeigegeräte: " + pointingDevices(products).ifEmpty { listOf("unbekannt") }.joinToString(", ")
        parts += "Eingabe-Programme: " + inputTools(run("ps", "-Axo", "comm").orEmpty()).ifEmpty { listOf("keine bekannten") }.joinToString(", ")
        return parts.joinToString(" · ")
    }

    private val PRODUCT = Regex(""""Product" = "([^"\n]{1,80})"""")

    /** Fixed labels for the HID product names of pointing devices. */
    fun pointingDevices(products: List<String>): List<String> = products.mapNotNull { p ->
        val l = p.lowercase()
        when {
            "magic mouse" in l -> "Magic Mouse"
            "magic trackpad" in l -> "Magic Trackpad"
            "trackpad" in l -> "Trackpad (eingebaut)"
            LOGITECH.any { it in l } -> "Logitech-Maus"
            "mouse" in l || "maus" in l -> "andere Maus"
            else -> null
        }
    }.distinct()

    private val LOGITECH = listOf("logi", "mx master", "mx anywhere", "mx vertical", "mx ergo", "m720", "m590", "lift vertical")

    /** Programs that rewrite or synthesize mouse and scroll events, from the process list (ps -Axo comm). */
    fun inputTools(processes: String): List<String> {
        val l = processes.lowercase()
        return TOOLS.filter { (needle, _) -> needle in l }.map { it.second }.distinct()
    }

    private val TOOLS = listOf(
        "logioptionsplus" to "Logi Options+", "logi options" to "Logi Options+", "logitech options" to "Logitech Options",
        "bettertouchtool" to "BetterTouchTool", "karabiner" to "Karabiner-Elements", "mac mouse fix" to "Mac Mouse Fix",
        "linearmouse" to "LinearMouse", "steermouse" to "SteerMouse", "/mos.app/" to "Mos", "scroll reverser" to "Scroll Reverser",
        "magicprefs" to "MagicPrefs", "hammerspoon" to "Hammerspoon", "smoothscroll" to "SmoothScroll",
        "unnaturalscrollwheels" to "UnnaturalScrollWheels", "usb overdrive" to "USB Overdrive", "bettermouse" to "BetterMouse",
        "swish.app" to "Swish",
    )

    private fun run(vararg command: String): String? = runCatching {
        val p = ProcessBuilder(*command).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroyForcibly()
        out.takeIf { p.exitValue() == 0 }
    }.getOrNull()
}
