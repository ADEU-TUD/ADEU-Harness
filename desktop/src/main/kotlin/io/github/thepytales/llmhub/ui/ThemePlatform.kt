package io.github.thepytales.llmhub.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** Windows parts of the theme. */
const val MATERIAL_YOU_DESCRIPTION = "Material-Design von Google in der gewählten Farbe."

/** Windows has no wallpaper colors for apps; Material You uses its default blue. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun dynamicScheme(dark: Boolean): ColorScheme? = null

@Suppress("UNUSED_PARAMETER")
@Composable
fun SystemBars(dark: Boolean) {}
