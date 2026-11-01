package io.github.thepytales.llmhub.ui

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
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
