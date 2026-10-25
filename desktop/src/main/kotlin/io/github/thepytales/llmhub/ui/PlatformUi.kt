package io.github.thepytales.llmhub.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Windows: the right click opens the same menu as a long press on Android. */
fun Modifier.secondaryClick(action: () -> Unit): Modifier = onRightClick(action)

/** How a context menu is opened – for hints in the interface. */
const val CONTEXT_GESTURE = "Rechtsklick"

/** Windows has no system back; Escape is handled per screen. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit) {}
