package io.github.thepytales.llmhub.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import io.github.thepytales.llmhub.data.AppSettings

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Hell"),
    DARK("Dunkel"),
}

enum class AppStyle(val label: String, val description: String) {
    STANDARD("Standard", "Material You – Farben passend zum Hintergrundbild (Android 12+)."),
    CLAUDE("Claude", "Warme Creme-Töne, Terrakotta-Akzent und Serifenschrift für Antworten."),
    CHATGPT("ChatGPT", "Schlicht in Schwarz, Weiß und Grau; Antworten ohne Sprechblase."),
    GEMINI("Gemini", "Kühles Blau, helle Flächen und runde Formen."),
}

enum class Accent(val label: String, val color: Color?) {
    AUTO("Automatisch", null),
    BLUE("Blau", Color(0xFF2563EB)),
    INDIGO("Indigo", Color(0xFF4F46E5)),
    VIOLET("Violett", Color(0xFF7C3AED)),
    PINK("Pink", Color(0xFFDB2777)),
    RED("Rot", Color(0xFFDC2626)),
    ORANGE("Orange", Color(0xFFEA580C)),
    AMBER("Gelb", Color(0xFFD97706)),
    GREEN("Grün", Color(0xFF16A34A)),
    TEAL("Türkis", Color(0xFF0D9488)),
    GRAY("Grau", Color(0xFF64748B)),
}

inline fun <reified T : Enum<T>> enumOr(name: String, default: T): T =
    runCatching { enumValueOf<T>(name) }.getOrDefault(default)

/** Style-specific details of the chat screen that the color scheme alone does not cover. */
data class ChatLook(
    val userBubble: Color,
    val onUserBubble: Color,
    val answerFont: FontFamily,
    val userShape: Shape,
    val inputShape: Shape,
)

val LocalChatLook = staticCompositionLocalOf {
    ChatLook(
        userBubble = Color.LightGray,
        onUserBubble = Color.Black,
        answerFont = FontFamily.Default,
        userShape = RoundedCornerShape(18.dp),
        inputShape = RoundedCornerShape(20.dp),
    )
}

private fun Color.hsl(): FloatArray = FloatArray(3).also { ColorUtils.colorToHSL(toArgb(), it) }

private fun hsl(h: Float, s: Float, l: Float) = Color(ColorUtils.HSLToColor(floatArrayOf(h, s.coerceIn(0f, 1f), l)))

/** Builds a full Material color scheme from a single seed color (simple HSL-based tonal palette). */
private fun seedScheme(seed: Color, dark: Boolean): ColorScheme {
    val (h, s) = seed.hsl()
    val th = (h + 60f) % 360f
    val ns = s * 0.12f
    return if (!dark) lightColorScheme(
        primary = hsl(h, s, 0.40f),
        onPrimary = Color.White,
        primaryContainer = hsl(h, s, 0.90f),
        onPrimaryContainer = hsl(h, s, 0.14f),
        inversePrimary = hsl(h, s, 0.80f),
        secondary = hsl(h, s * 0.35f, 0.40f),
        onSecondary = Color.White,
        secondaryContainer = hsl(h, s * 0.45f, 0.90f),
        onSecondaryContainer = hsl(h, s * 0.45f, 0.14f),
        tertiary = hsl(th, s * 0.5f, 0.40f),
        onTertiary = Color.White,
        tertiaryContainer = hsl(th, s * 0.5f, 0.90f),
        onTertiaryContainer = hsl(th, s * 0.5f, 0.14f),
        background = hsl(h, ns, 0.98f),
        onBackground = hsl(h, ns, 0.10f),
        surface = hsl(h, ns, 0.98f),
        onSurface = hsl(h, ns, 0.10f),
        surfaceVariant = hsl(h, ns * 1.5f, 0.90f),
        onSurfaceVariant = hsl(h, ns * 1.5f, 0.30f),
        surfaceTint = hsl(h, s, 0.40f),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = hsl(h, ns, 0.96f),
        surfaceContainer = hsl(h, ns, 0.94f),
        surfaceContainerHigh = hsl(h, ns, 0.92f),
        surfaceContainerHighest = hsl(h, ns, 0.90f),
        surfaceDim = hsl(h, ns, 0.87f),
        surfaceBright = hsl(h, ns, 0.98f),
        outline = hsl(h, ns, 0.50f),
        outlineVariant = hsl(h, ns, 0.80f),
    ) else darkColorScheme(
        primary = hsl(h, s, 0.80f),
        onPrimary = hsl(h, s, 0.20f),
        primaryContainer = hsl(h, s * 0.8f, 0.30f),
        onPrimaryContainer = hsl(h, s, 0.90f),
        inversePrimary = hsl(h, s, 0.40f),
        secondary = hsl(h, s * 0.35f, 0.80f),
        onSecondary = hsl(h, s * 0.35f, 0.20f),
        secondaryContainer = hsl(h, s * 0.3f, 0.28f),
        onSecondaryContainer = hsl(h, s * 0.45f, 0.90f),
        tertiary = hsl(th, s * 0.5f, 0.80f),
        onTertiary = hsl(th, s * 0.5f, 0.20f),
        tertiaryContainer = hsl(th, s * 0.4f, 0.30f),
        onTertiaryContainer = hsl(th, s * 0.5f, 0.90f),
        background = hsl(h, ns, 0.07f),
        onBackground = hsl(h, ns, 0.90f),
        surface = hsl(h, ns, 0.07f),
        onSurface = hsl(h, ns, 0.90f),
        surfaceVariant = hsl(h, ns * 1.5f, 0.22f),
        onSurfaceVariant = hsl(h, ns * 1.5f, 0.78f),
        surfaceTint = hsl(h, s, 0.80f),
        surfaceContainerLowest = hsl(h, ns, 0.04f),
        surfaceContainerLow = hsl(h, ns, 0.09f),
        surfaceContainer = hsl(h, ns, 0.11f),
        surfaceContainerHigh = hsl(h, ns, 0.14f),
        surfaceContainerHighest = hsl(h, ns, 0.18f),
        surfaceDim = hsl(h, ns, 0.07f),
        surfaceBright = hsl(h, ns, 0.24f),
        outline = hsl(h, ns, 0.56f),
        outlineVariant = hsl(h, ns, 0.30f),
    )
}

/** Replaces all neutral surface colors; [n] goes from lowest to highest container. */
private fun ColorScheme.withNeutrals(
    background: Color,
    onSurface: Color,
    onSurfaceVariant: Color,
    outline: Color,
    n: List<Color>,
) = copy(
    background = background,
    onBackground = onSurface,
    surface = background,
    onSurface = onSurface,
    surfaceVariant = n[4],
    onSurfaceVariant = onSurfaceVariant,
    surfaceContainerLowest = n[0],
    surfaceContainerLow = n[1],
    surfaceContainer = n[2],
    surfaceContainerHigh = n[3],
    surfaceContainerHighest = n[4],
    surfaceDim = n[1],
    surfaceBright = n[4],
    outline = outline,
    outlineVariant = n[4],
)

/** Monochrome primary colors (ChatGPT look without a chosen accent). */
private fun ColorScheme.monochrome(dark: Boolean) = if (dark) copy(
    primary = Color(0xFFECECEC), onPrimary = Color(0xFF0D0D0D),
    primaryContainer = Color(0xFF3A3A3A), onPrimaryContainer = Color(0xFFECECEC),
    secondary = Color(0xFFB4B4B4), secondaryContainer = Color(0xFF353535), onSecondaryContainer = Color(0xFFECECEC),
) else copy(
    primary = Color(0xFF0D0D0D), onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E8E8), onPrimaryContainer = Color(0xFF0D0D0D),
    secondary = Color(0xFF5D5D5D), secondaryContainer = Color(0xFFEDEDED), onSecondaryContainer = Color(0xFF0D0D0D),
)

private fun styledScheme(style: AppStyle, accent: Accent, dark: Boolean, dynamic: ColorScheme?): ColorScheme {
    val chosen = accent.color
    return when (style) {
        AppStyle.STANDARD -> if (chosen == null) dynamic ?: seedScheme(Color(0xFF1E3A8A), dark) else seedScheme(chosen, dark)
        AppStyle.CLAUDE -> {
            val base = seedScheme(chosen ?: Color(0xFFD97757), dark)
            if (dark) base.withNeutrals(
                background = Color(0xFF262624), onSurface = Color(0xFFF5F4EE), onSurfaceVariant = Color(0xFFC2C0B6),
                outline = Color(0xFF75736C),
                n = listOf(Color(0xFF1F1E1D), Color(0xFF2B2A28), Color(0xFF30302E), Color(0xFF393937), Color(0xFF42413E)),
            ) else base.withNeutrals(
                background = Color(0xFFF5F4ED), onSurface = Color(0xFF141413), onSurfaceVariant = Color(0xFF5E5D59),
                outline = Color(0xFF9C9A92),
                n = listOf(Color(0xFFFAF9F5), Color(0xFFF0EEE6), Color(0xFFEDEBE3), Color(0xFFE8E6DC), Color(0xFFE2DFD4)),
            )
        }
        AppStyle.CHATGPT -> {
            val base = seedScheme(chosen ?: Color(0xFF10A37F), dark).let { if (chosen == null) it.monochrome(dark) else it }
            if (dark) base.withNeutrals(
                background = Color(0xFF212121), onSurface = Color(0xFFECECEC), onSurfaceVariant = Color(0xFFB4B4B4),
                outline = Color(0xFF6B6B6B),
                n = listOf(Color(0xFF171717), Color(0xFF1B1B1B), Color(0xFF262626), Color(0xFF2F2F2F), Color(0xFF383838)),
            ) else base.withNeutrals(
                background = Color.White, onSurface = Color(0xFF0D0D0D), onSurfaceVariant = Color(0xFF5D5D5D),
                outline = Color(0xFFADADAD),
                n = listOf(Color.White, Color(0xFFF9F9F9), Color(0xFFF4F4F4), Color(0xFFEDEDED), Color(0xFFE6E6E6)),
            )
        }
        AppStyle.GEMINI -> {
            val base = seedScheme(chosen ?: Color(0xFF0B57D0), dark)
            if (dark) base.withNeutrals(
                background = Color(0xFF131314), onSurface = Color(0xFFE3E3E3), onSurfaceVariant = Color(0xFFC4C7C5),
                outline = Color(0xFF8E918F),
                n = listOf(Color(0xFF0E0E0F), Color(0xFF1B1B1C), Color(0xFF1E1F20), Color(0xFF282A2C), Color(0xFF333537)),
            ) else base.withNeutrals(
                background = Color.White, onSurface = Color(0xFF1F1F1F), onSurfaceVariant = Color(0xFF444746),
                outline = Color(0xFF747775),
                n = listOf(Color.White, Color(0xFFF8FAFD), Color(0xFFF0F4F9), Color(0xFFE9EEF6), Color(0xFFDDE3EA)),
            )
        }
    }
}

private fun ColorScheme.amoled() = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
    surfaceVariant = Color(0xFF222222),
)

private fun chatLook(style: AppStyle, scheme: ColorScheme) = when (style) {
    AppStyle.STANDARD -> ChatLook(
        userBubble = scheme.primaryContainer,
        onUserBubble = scheme.onPrimaryContainer,
        answerFont = FontFamily.Default,
        userShape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
        inputShape = RoundedCornerShape(20.dp),
    )
    AppStyle.CLAUDE -> ChatLook(
        userBubble = scheme.surfaceContainerHighest,
        onUserBubble = scheme.onSurface,
        answerFont = FontFamily.Serif,
        userShape = RoundedCornerShape(12.dp),
        inputShape = RoundedCornerShape(16.dp),
    )
    AppStyle.CHATGPT -> ChatLook(
        userBubble = scheme.surfaceContainerHigh,
        onUserBubble = scheme.onSurface,
        answerFont = FontFamily.Default,
        userShape = RoundedCornerShape(22.dp),
        inputShape = RoundedCornerShape(28.dp),
    )
    AppStyle.GEMINI -> ChatLook(
        userBubble = scheme.surfaceContainerHigh,
        onUserBubble = scheme.onSurface,
        answerFont = FontFamily.Default,
        userShape = RoundedCornerShape(24.dp, 4.dp, 24.dp, 24.dp),
        inputShape = RoundedCornerShape(32.dp),
    )
}

@Composable
fun LlmHubTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (enumOr(settings.themeMode, ThemeMode.SYSTEM)) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val style = enumOr(settings.appStyle, AppStyle.STANDARD)
    val accent = enumOr(settings.accent, Accent.AUTO)
    val context = LocalContext.current
    val dynamic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else null
    val colors = styledScheme(style, accent, dark, dynamic).let { if (dark && settings.amoled) it.amoled() else it }

    // Status/navigation bar icons must follow the app theme, not the system theme.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalChatLook provides chatLook(style, colors),
        LocalDensity provides Density(density.density, density.fontScale * settings.textScale),
    ) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
