package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.Rectangle

/**
 * Zweiter Bildschirm beim Vorführen (AP-51), Windows: the slides go full screen on a screen other than the one the
 * app is on (a projector, a second monitor), the Referentenansicht stays with the app. The window is borderless and
 * covers that screen exactly; it does not take the focus, so the keys keep working in the Referentenansicht.
 */
object PresentScreens {
    private fun devices() = runCatching {
        if (GraphicsEnvironment.isHeadless()) emptyList() else GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.toList()
    }.getOrDefault(emptyList())

    @Suppress("UNUSED_PARAMETER")
    fun hasSecond(context: android.content.Context? = null): Boolean = devices().size > 1

    /** Bounds (in the screen coordinates Compose uses) of the screen the slides go to: not the one of the app's window. */
    fun second(): Rectangle? {
        val all = devices()
        if (all.size < 2) return null
        val main = runCatching { KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow?.graphicsConfiguration?.device }.getOrNull()
            ?: runCatching { GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice }.getOrNull()
        return (all.firstOrNull { it != main } ?: all[1]).defaultConfiguration.bounds
    }
}

@Composable
fun AudienceScreen(visible: Boolean, onClose: () -> Unit, content: @Composable () -> Unit) {
    if (!visible) return
    val bounds = remember { PresentScreens.second() } ?: return
    val state = rememberWindowState(
        placement = WindowPlacement.Floating,
        position = WindowPosition(bounds.x.dp, bounds.y.dp),
        size = DpSize(bounds.width.dp, bounds.height.dp),
    )
    Window(
        onCloseRequest = onClose, state = state, title = tr("Präsentation"), undecorated = true, resizable = false,
        alwaysOnTop = true, focusable = false,
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { content() }
    }
}

/** The display stays on while presenting: Windows does not go to sleep while the app is in front (nothing to do). */
@Composable
fun KeepScreenOn() {}
