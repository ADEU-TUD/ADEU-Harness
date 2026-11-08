package io.github.thepytales.llmhub.ui

import androidx.compose.runtime.Composable

/**
 * Zweiter Bildschirm beim Vorführen (AP-51): the slides full screen on the second screen while the Referentenansicht
 * (next slide, notes, timer) stays on the main screen. On Android the presentation stays on the device's own screen;
 * the Windows app has its own version of this file.
 */
object PresentScreens {
    /** Whether a second screen is connected that the slides can go to. */
    fun hasSecond(): Boolean = false
}

/** Shows [content] (the slide for the audience) full screen on the second screen while [visible]. Nothing on Android. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun AudienceScreen(visible: Boolean, onClose: () -> Unit, content: @Composable () -> Unit) {}

/** „Aufnehmen“ in the Referentenansicht (AP-56): only in the Windows app (screen recording); nothing on Android. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun PresentationRecordButton(second: Boolean) {}
