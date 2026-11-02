package io.github.thepytales.llmhub.ui

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.abs

/**
 * Strg+mouse wheel zooms the whole interface (Main.kt). Areas with a zoom of their own – the canvas of the design
 * editor – mark themselves with [ownsCtrlWheel]; there the wheel zooms only the content. Before, both zoomed at once:
 * the canvas changed its size with every step and threw the page out of view.
 */
object CtrlWheel {
    private val areas = mutableMapOf<Any, Rect>()

    /** True if [position] (window pixels) lies in an area with its own Strg+wheel zoom. */
    fun ownedAt(position: Offset): Boolean = areas.values.any { it.contains(position) }

    internal fun put(key: Any, bounds: Rect) { areas[key] = bounds }
    internal fun remove(key: Any) { areas.remove(key) }

    /**
     * Turns wheel deltas into whole zoom steps. A mouse wheel notch is ±1 and makes one step; a touchpad sends many
     * small deltas, which are added up – before, each of them made a 10 % step and a short gesture zoomed far.
     */
    class Steps {
        private var rest = 0f

        fun add(deltaY: Float): Int {
            if (deltaY == 0f) return 0
            if (rest != 0f && (rest < 0) != (deltaY < 0)) rest = 0f
            rest += deltaY
            if (abs(rest) < 1f) return 0
            val step = if (rest < 0) 1 else -1
            rest = 0f
            return step
        }
    }
}

/** This area zooms its own content with Strg+wheel; the interface zoom leaves it alone (see [CtrlWheel]). */
fun Modifier.ownsCtrlWheel(): Modifier = composed {
    val key = remember { Any() }
    DisposableEffect(key) { onDispose { CtrlWheel.remove(key) } }
    onGloballyPositioned { CtrlWheel.put(key, it.boundsInWindow()) }
}
