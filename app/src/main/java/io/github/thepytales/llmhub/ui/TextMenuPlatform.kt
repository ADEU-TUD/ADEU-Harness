package io.github.thepytales.llmhub.ui

import androidx.compose.runtime.Composable

/**
 * Text fields with the app's own Rechtsklick-Menü (AP-125, Fließtext-Editor). Android: the system's menu of a long
 * press stays as it is. The Windows version has a file of the same name that leaves out Compose's own text menu, so
 * only the app's menu opens.
 */
@Composable
internal fun OwnTextMenu(content: @Composable () -> Unit) = content()
