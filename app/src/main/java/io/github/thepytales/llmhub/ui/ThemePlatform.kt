package io.github.thepytales.llmhub.ui

import android.app.Activity
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** Android parts of the theme (Windows has its own version of this file). */
const val MATERIAL_YOU_DESCRIPTION = "Farben passend zum Hintergrundbild (Android 12+)."

/** Colors of the wallpaper (Android 12+) for the Material You look. */
@Composable
fun dynamicScheme(dark: Boolean): ColorScheme? {
    val context = LocalContext.current
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else null
}

/**
 * Status/navigation bar icons must follow the app theme, not the system theme. The window behind the app gets the
 * app's background too, so transitions never show a differently colored (e.g. dark grey) window.
 */
@Composable
fun SystemBars(dark: Boolean, background: Color) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        window.setBackgroundDrawable(ColorDrawable(background.toArgb()))
    }
}
