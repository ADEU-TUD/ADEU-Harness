package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key

/** No menu of Compose's own: the field shows only its content (the app opens its own menu, AP-125). */
@OptIn(ExperimentalFoundationApi::class)
private object NoTextMenu : TextContextMenu {
    @Composable
    override fun Area(textManager: TextContextMenu.TextManager, state: ContextMenuState, content: @Composable () -> Unit) = content()
}

/**
 * Text fields with the app's own Rechtsklick-Menü (AP-125, Fließtext-Editor), Windows/macOS/Linux: Compose's own text
 * menu (Cut, Copy, Paste, Select All) is left out, so a right click opens only the app's menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun OwnTextMenu(content: @Composable () -> Unit) = CompositionLocalProvider(LocalTextContextMenu provides NoTextMenu, content = content)

private val LESS = Key(java.awt.event.KeyEvent.VK_LESS)
private val GREATER = Key(java.awt.event.KeyEvent.VK_GREATER)

/**
 * The key with „<“ and „>“ (AP-125 c, Strg+Umschalt+> / Strg+< for the font size like Word): beside Umschalt on a
 * German keyboard (VK_LESS), or wherever a layout has these characters.
 */
internal fun isAngleKey(e: KeyEvent): Boolean =
    e.key == LESS || e.key == GREATER || e.awtEventOrNull?.keyChar.let { it == '<' || it == '>' }

/** Pointer over the handles of a picture and the column borders of a table (AP-125 d), as in Word. */
internal val DiagonalResizeIcon: androidx.compose.ui.input.pointer.PointerIcon = androidx.compose.ui.input.pointer.PointerIcon(java.awt.Cursor(java.awt.Cursor.SE_RESIZE_CURSOR))
internal val ColumnResizeIcon: androidx.compose.ui.input.pointer.PointerIcon = androidx.compose.ui.input.pointer.PointerIcon(java.awt.Cursor(java.awt.Cursor.E_RESIZE_CURSOR))
