package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

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
