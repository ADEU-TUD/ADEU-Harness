package io.github.thepytales.llmhub.ui

import androidx.compose.ui.Modifier

/** Android: context menus open with a long press only. The Windows version of this file adds the right click. */
@Suppress("UNUSED_PARAMETER")
fun Modifier.secondaryClick(action: () -> Unit): Modifier = this

/** How a context menu is opened – for hints in the interface. */
const val CONTEXT_GESTURE = "Lange drücken"
