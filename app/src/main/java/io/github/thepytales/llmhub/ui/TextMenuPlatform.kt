package io.github.thepytales.llmhub.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.utf16CodePoint

/**
 * Text fields with the app's own Rechtsklick-Menü (AP-125, Fließtext-Editor). Android: the system's menu of a long
 * press stays as it is. The Windows version has a file of the same name that leaves out Compose's own text menu, so
 * only the app's menu opens.
 */
@Composable
internal fun OwnTextMenu(content: @Composable () -> Unit) = content()

/**
 * The key with „<“ and „>“ (AP-125 c, Strg+Umschalt+> / Strg+< for the font size like Word). Android: by the
 * character it types; the Windows version also knows the key beside Umschalt of a German keyboard.
 */
internal fun isAngleKey(e: KeyEvent): Boolean = e.utf16CodePoint == '<'.code || e.utf16CodePoint == '>'.code

/** Pointer over the handles of a picture and the column borders of a table (AP-125 d). Android: no own pointer. */
internal val DiagonalResizeIcon: androidx.compose.ui.input.pointer.PointerIcon = androidx.compose.ui.input.pointer.PointerIcon.Crosshair
internal val ColumnResizeIcon: androidx.compose.ui.input.pointer.PointerIcon = androidx.compose.ui.input.pointer.PointerIcon.Crosshair
