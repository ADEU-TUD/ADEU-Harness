package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn as FoundationLazyColumn
import androidx.compose.foundation.verticalScroll as foundationVerticalScroll

/*
 * Bildlaufleisten am PC (AP-123). Compose draws no scrollbars on the desktop, so long pages and lists did not show that
 * they go on, nor where one is. verticalScroll and LazyColumn of this package (same names and parameters as in
 * androidx.compose.foundation, screens take them without an import) draw a slim bar at the right edge while the content
 * is longer than the window: wider and darker under the mouse, and it can be dragged. Android keeps its own behaviour.
 */

/** Foundation's verticalScroll; on the PC with a scrollbar. */
fun Modifier.verticalScroll(
    state: ScrollState,
    enabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false,
): Modifier = (if (IS_ANDROID) this else then(ScrollbarElement(PlainScroll(state, reverseScrolling))))
    .foundationVerticalScroll(state = state, enabled = enabled, flingBehavior = flingBehavior, reverseScrolling = reverseScrolling)

/** Foundation's LazyColumn; on the PC with a scrollbar. */
@Composable
fun LazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) = FoundationLazyColumn(
    modifier = if (IS_ANDROID) modifier else modifier.then(ScrollbarElement(LazyScroll(state))),
    state = state,
    contentPadding = contentPadding,
    reverseLayout = reverseLayout,
    verticalArrangement = verticalArrangement,
    horizontalAlignment = horizontalAlignment,
    flingBehavior = flingBehavior,
    userScrollEnabled = userScrollEnabled,
    content = content,
)

/** Where a scrolling container stands: the visible share and the position (0 top … 1 bottom), and how to move it. */
internal sealed interface ScrollGeometry {
    /** Visible share of the content and position, or null when everything fits. */
    fun measure(viewport: Float): Pair<Float, Float>?

    /** Moves the content so the bar moves by [barDelta] pixels on a track of [track] pixels. */
    fun drag(barDelta: Float, viewport: Float, track: Float)
}

internal class PlainScroll(val state: ScrollState, val reverse: Boolean) : ScrollGeometry {
    override fun measure(viewport: Float): Pair<Float, Float>? {
        val max = state.maxValue
        if (max <= 0 || max == Int.MAX_VALUE || viewport <= 0f) return null
        val pos = state.value.toFloat() / max
        return viewport / (viewport + max) to if (reverse) 1f - pos else pos
    }

    override fun drag(barDelta: Float, viewport: Float, track: Float) {
        if (track <= 0f) return
        val delta = barDelta * state.maxValue / track
        state.dispatchRawDelta(if (reverse) -delta else delta)
    }

    override fun equals(other: Any?) = other is PlainScroll && other.state === state && other.reverse == reverse
    override fun hashCode() = state.hashCode() * 31 + reverse.hashCode()
}

internal class LazyScroll(val state: LazyListState) : ScrollGeometry {
    /** Estimated length of all items and how far they are scrolled (items of a lazy list are measured when shown). */
    private fun lengths(): Pair<Float, Float>? {
        val info = state.layoutInfo
        val items = info.visibleItemsInfo
        if (items.isEmpty() || info.totalItemsCount == 0) return null
        val average = items.sumOf { it.size }.toFloat() / items.size + info.mainAxisItemSpacing
        val total = average * info.totalItemsCount + info.beforeContentPadding + info.afterContentPadding
        val scrolled = state.firstVisibleItemIndex * average + state.firstVisibleItemScrollOffset
        return total to scrolled
    }

    override fun measure(viewport: Float): Pair<Float, Float>? {
        if (!state.canScrollForward && !state.canScrollBackward) return null
        val (total, scrolled) = lengths() ?: return null
        if (total <= viewport) return null
        val pos = (scrolled / (total - viewport)).coerceIn(0f, 1f)
        val share = (viewport / total).coerceIn(0f, 1f)
        return share to if (state.layoutInfo.reverseLayout) 1f - pos else pos
    }

    override fun drag(barDelta: Float, viewport: Float, track: Float) {
        val (total, _) = lengths() ?: return
        if (track <= 0f) return
        val delta = barDelta * (total - viewport) / track
        state.dispatchRawDelta(if (state.layoutInfo.reverseLayout) -delta else delta)
    }

    override fun equals(other: Any?) = other is LazyScroll && other.state === state
    override fun hashCode() = state.hashCode()
}

private class ScrollbarElement(val geometry: ScrollGeometry) : ModifierNodeElement<ScrollbarNode>() {
    override fun create() = ScrollbarNode(geometry)
    override fun update(node: ScrollbarNode) { node.geometry = geometry }
    override fun equals(other: Any?) = other is ScrollbarElement && other.geometry == geometry
    override fun hashCode() = geometry.hashCode()
    override fun InspectorInfo.inspectableProperties() { name = "scrollbar" }
}

/** Draws the bar over the content and lets the mouse drag it; everything else goes to the content as before. */
private class ScrollbarNode(var geometry: ScrollGeometry) : DelegatingNode(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private var hovered = false
    private var dragging = false

    /** Bar in pixels for a container [height] pixels high: top and length, or null when everything fits. */
    private fun bar(height: Float, px: Float): Pair<Float, Float>? {
        val (share, pos) = geometry.measure(height) ?: return null
        val inset = BAR_INSET_DP * px
        val track = height - 2 * inset
        if (track <= 0f) return null
        val length = (track * share).coerceIn(minOf(MIN_BAR_DP * px, track), track)
        return inset + (track - length) * pos to length
    }

    init {
        delegate(SuspendingPointerInputModifierNode {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: continue
                    val px = density
                    val h = size.height.toFloat()
                    val bar = bar(h, px)
                    val onBar = bar != null && change.position.x >= size.width - GRIP_DP * px &&
                        change.position.y in bar.first..(bar.first + bar.second)
                    when (event.type) {
                        PointerEventType.Press -> if (onBar && bar != null) {
                            change.consume()
                            dragging = true
                            invalidateDraw()
                            var last = change.position.y
                            while (true) {
                                val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull() ?: break
                                c.consume()
                                if (!c.pressed) break
                                val dy = c.position.y - last
                                last = c.position.y
                                if (dy != 0f) geometry.drag(dy, h, h - 2 * BAR_INSET_DP * px - bar.second)
                            }
                            dragging = false
                            invalidateDraw()
                        }
                        else -> {
                            val now = onBar && event.type != PointerEventType.Exit
                            if (now != hovered) {
                                hovered = now
                                invalidateDraw()
                            }
                        }
                    }
                }
            }
        })
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val (top, length) = bar(size.height, density) ?: return
        val strong = hovered || dragging
        val width = (if (strong) 8f else 5f) * density
        val color = currentValueOf(LocalContentColor).copy(alpha = if (strong) 0.5f else 0.28f)
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - width - BAR_INSET_DP * density, top),
            size = Size(width, length),
            cornerRadius = CornerRadius(width / 2, width / 2),
        )
    }

    override fun onDetach() {
        hovered = false
        dragging = false
    }

    private companion object {
        const val BAR_INSET_DP = 2f
        const val MIN_BAR_DP = 32f
        /** Width from the right edge in which the mouse grabs the bar. */
        const val GRIP_DP = 12f
    }
}
