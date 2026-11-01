package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.focusable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed

/*
 * The key of shortcuts (AP-31): Strg on Windows, Linux and Android keyboards, ⌘ on a Mac. Both count everywhere, so
 * "Strg+K" works on every computer; the hints show ⌘ on a Mac (see I18n.platformText).
 */

/** Strg or ⌘ is held. */
val KeyEvent.isCmd: Boolean get() = isCtrlPressed || isMetaPressed

/** Strg or ⌘ is held (mouse wheel, clicks). */
val PointerKeyboardModifiers.isCmd: Boolean get() = isCtrlPressed || isMetaPressed

/** Enter or Space (without Strg/⌘): the key that activates the focused element. */
val KeyEvent.isActivate: Boolean get() = !isCmd && (key == Key.Enter || key == Key.NumPadEnter || key == Key.Spacebar)

/** F2 or Shift+F10 (Windows: edit, context menu) on the focused element. */
val KeyEvent.isSecondary: Boolean get() = key == Key.F2 || isShiftPressed && key == Key.F10

/**
 * Keyboard access for an element whose clicks come from pointer gestures (tap on a word, long press) – AP-30: Tab
 * reaches it, the focus is marked like on every button, Enter or Space runs [onActivate] and F2 or Shift+F10 runs
 * [onSecondary] (e.g. edit). Pointer gestures stay as they are; screen readers get their actions from semantics.
 */
fun Modifier.keyboardClick(onSecondary: (() -> Unit)? = null, onActivate: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    onKeyEvent { e ->
        val activate = e.isActivate
        if (!activate && (onSecondary == null || !e.isSecondary)) return@onKeyEvent false
        // Runs on release, like a button; the press is taken too, so it does not reach the screen around it.
        if (e.type == KeyEventType.KeyUp) if (activate) onActivate() else onSecondary?.invoke()
        true
    }
        .indication(source, LocalIndication.current)
        .focusable(interactionSource = source)
}
