package io.github.thepytales.llmhub.ui

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Zweiter Bildschirm beim Vorführen (AP-51) auf Android (AP-64): a projector or screen on HDMI/USB-C or a wireless
 * display (Miracast, „Bildschirm übertragen“) gets the slides full screen through Android's Presentation, while the
 * Referentenansicht (next slide, notes, timer) stays on the phone or tablet. The Windows app has its own version.
 */
object PresentScreens {
    /** The screens Android offers for presentations (not the device's own). */
    fun displays(context: Context?): List<Display> = runCatching {
        (context?.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)?.toList()
    }.getOrNull().orEmpty()

    /** Whether a second screen is connected that the slides can go to. */
    fun hasSecond(context: Context? = null): Boolean = displays(context).isNotEmpty()
}

private fun Context.activity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Shows [content] (the slide for the audience) full screen on the second screen while [visible]. */
@Composable
fun AudienceScreen(visible: Boolean, onClose: () -> Unit, content: @Composable () -> Unit) {
    if (!visible) return
    val context = LocalContext.current
    val activity = remember(context) { context.activity() } ?: return
    val display = remember { PresentScreens.displays(context).firstOrNull() } ?: return
    val parent = rememberCompositionContext()
    val latest by rememberUpdatedState(content)
    val close by rememberUpdatedState(onClose)
    DisposableEffect(display) {
        val presentation = Presentation(activity, display)
        val view = ComposeView(presentation.context).apply {
            setParentCompositionContext(parent)
            setContent { Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { latest() } }
        }
        // A dialog's window has no lifecycle of its own: the activity's keeps the slide's composition alive.
        val owner = activity as? androidx.activity.ComponentActivity
        if (owner != null) {
            view.setViewTreeLifecycleOwner(owner)
            view.setViewTreeSavedStateRegistryOwner(owner)
            presentation.window?.decorView?.let { d ->
                d.setViewTreeLifecycleOwner(owner)
                d.setViewTreeSavedStateRegistryOwner(owner)
            }
        }
        presentation.setContentView(view)
        presentation.setOnDismissListener { close() }
        runCatching { presentation.show() }.onFailure { close() }
        onDispose {
            presentation.setOnDismissListener(null)
            runCatching { presentation.dismiss() }
        }
    }
}

/** The display stays on while presenting. */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** „Aufnehmen“ in the Referentenansicht (AP-56): only in the Windows app (screen recording); nothing on Android. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun PresentationRecordButton(second: Boolean) {}
