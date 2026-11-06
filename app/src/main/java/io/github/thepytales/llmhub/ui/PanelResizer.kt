package io.github.thepytales.llmhub.ui

import android.content.SharedPreferences
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer

/**
 * A width the user drags (side menu, Lora's panel, browser beside the chat), remembered between starts (ui_state).
 * [limit] is the share of the window the layout allows, so the screen in the middle keeps its room on small windows.
 */
class PanelWidth internal constructor(
    private val prefs: SharedPreferences,
    private val key: String,
    val default: Dp,
    val min: Dp,
    val max: Dp,
) {
    private var stored by mutableStateOf(prefs.getFloat(key, default.value).dp.coerceIn(min, max))
    var limit by mutableStateOf(Dp.Infinity)

    /** The width to show. */
    val value: Dp get() = stored.coerceAtMost(limit).coerceAtLeast(min)

    fun drag(delta: Dp) {
        stored = (value + delta).coerceIn(min, max.coerceAtMost(limit).coerceAtLeast(min))
    }

    fun save() {
        prefs.edit().putFloat(key, stored.value).apply()
    }

    fun reset() {
        stored = default
        save()
    }
}

/** The draggable widths of the app; one set for the whole app. */
class PanelWidths private constructor(prefs: SharedPreferences) {
    val menu = PanelWidth(prefs, "menuWidth", 318.dp, 240.dp, 520.dp)
    val lora = PanelWidth(prefs, "loraPanelWidth", 384.dp, 320.dp, 640.dp)
    val browser = PanelWidth(prefs, "browserPanelWidth", 560.dp, 380.dp, 1100.dp)

    /** Limits for a window [width] wide: side menu at most 40 %, panels at the right at most 45 % / 55 %. */
    fun fitTo(width: Dp) {
        menu.limit = width * 0.4f
        lora.limit = width * 0.45f
        browser.limit = width * 0.55f
    }

    companion object {
        @Volatile
        private var instance: PanelWidths? = null

        fun of(c: AppContainer): PanelWidths = instance ?: synchronized(this) {
            instance ?: PanelWidths(c.appContext.getSharedPreferences("ui_state", 0)).also { instance = it }
        }
    }
}

/** Width of the strip that can be grabbed; the line lies at its [PanelResizer] side towards the panel. */
private val HANDLE_WIDTH = 10.dp

/**
 * The line between the side menu, the screen and the panels at the right (desktop and tablets): a fine line with a
 * small grip in the middle; while the pointer is on it or it is dragged, both turn into the brand colour. Dragging
 * changes [width] – [panelLeft]: the panel lies left of the line (side menu), else right of it (Lora, browser) – and a
 * double click puts the width back. The strip has the background of the app, so there is no light gap between the
 * panels; its line sits at the edge of the panel.
 */
@Composable
fun PanelResizer(width: PanelWidth, panelLeft: Boolean, label: String) {
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val dragged by interaction.collectIsDraggedAsState()
    val active = hovered || dragged
    val brand = LocalChatLook.current.brand
    val outline = MaterialTheme.colorScheme.outlineVariant
    val lineColor by animateColorAsState(if (active) brand.copy(alpha = if (dragged) 0.85f else 0.55f) else outline.copy(alpha = 0.8f), tween(160))
    val gripColor by animateColorAsState(if (active) brand else outline, tween(160))
    val line by animateDpAsState(if (active) 3.dp else 1.dp, tween(160))
    val draggable = rememberDraggableState { px ->
        val d = with(density) { px.toDp() }
        width.drag(if (panelLeft) d else -d)
    }
    val gripHeight by animateDpAsState(if (active) 44.dp else 30.dp, tween(160))
    // The grip is centred on the line and reaches a little over the panel's edge (drawn above it: zIndex).
    val shift = (GRIP_WIDTH - line) / 2
    Box(
        Modifier
            .zIndex(1f)
            .fillMaxHeight()
            .width(HANDLE_WIDTH)
            .background(MaterialTheme.colorScheme.background)
            .resizeCursor()
            .hoverable(interaction)
            .pointerInput(width) {
                // Double-click puts the width back; only watches, so the drag below keeps every event.
                var lastUp: Long? = null
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val up = waitForUpOrCancellation()
                    val last = lastUp
                    lastUp = when {
                        up == null -> null
                        last != null && up.uptimeMillis - last <= viewConfiguration.doubleTapTimeoutMillis -> { width.reset(); null }
                        else -> up.uptimeMillis
                    }
                }
            }
            .draggable(draggable, Orientation.Horizontal, interactionSource = interaction, onDragStopped = { width.save() })
            .semantics { contentDescription = label },
        contentAlignment = if (panelLeft) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Box(Modifier.fillMaxHeight().width(line).background(lineColor))
        Box(
            Modifier
                .offset(x = if (panelLeft) -shift else shift)
                .size(width = GRIP_WIDTH, height = gripHeight)
                .clip(RoundedCornerShape(50))
                .background(gripColor),
        )
    }
}

private val GRIP_WIDTH = 5.dp
