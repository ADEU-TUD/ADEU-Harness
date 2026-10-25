package io.github.thepytales.llmhub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Android: context menus open with a long press only. The Windows version of this file adds the right click. */
@Suppress("UNUSED_PARAMETER")
fun Modifier.secondaryClick(action: () -> Unit): Modifier = this

/** How a context menu is opened – for hints in the interface. */
const val CONTEXT_GESTURE = "Lange drücken"

/** System back (gesture or button) handled by the screen itself, e.g. to close a search first. */
@Composable
fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit) = BackHandler(enabled, onBack)
