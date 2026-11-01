package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thepytales.llmhub.data.AppSettings

enum class ThemeMode(val label: String) {
    SYSTEM(trLater("System")),
    LIGHT(trLater("Hell")),
    DARK(trLater("Dunkel")),
}

/**
 * Looks of the whole app. [recommended] ones are calm and businesslike and shown first; the others imitate
 * well-known chat apps for those who are used to them.
 */
enum class AppStyle(val label: String, val description: String, val recommended: Boolean = false) {
    ADEU("ADEU", trLater("Klar und aufgeräumt: helle Flächen, weiße Karten, Violett als Akzent. Standard."), true),
    TUD("TU Dresden", trLater("Farben des Corporate Designs der TU Dresden: Dunkelblau, sachliches Grau, ruhige Formen."), true),
    OFFICE("Office", trLater("Nüchtern wie Microsoft 365: Weiß, neutrales Grau, Blau, kleine Ecken, keine Schatten."), true),
    CONTRAST(trLater("Hoher Kontrast"), trLater("Barrierearm: Schwarz auf Weiß, deutliche Rahmen und kräftige Akzente (WCAG AAA)."), true),
    CLAUDE("Claude", trLater("Warme, ruhige Töne, weiße Karten und schwarze Knöpfe – wie die Claude-App.")),
    STANDARD("Material You", MATERIAL_YOU_DESCRIPTION),
    CHATGPT("ChatGPT", trLater("Schlicht in Schwarz, Weiß und Grau; Antworten ohne Sprechblase.")),
    GEMINI("Gemini", trLater("Kühles Blau, helle Flächen und runde Formen.")),
}

enum class Accent(val label: String, val color: Color?) {
    AUTO(trLater("Automatisch"), null),
    BLUE(trLater("Blau"), Color(0xFF2563EB)),
    INDIGO(trLater("Indigo"), Color(0xFF4F46E5)),
    VIOLET(trLater("Violett"), Color(0xFF7C3AED)),
    PINK(trLater("Pink"), Color(0xFFDB2777)),
    RED(trLater("Rot"), Color(0xFFDC2626)),
    ORANGE(trLater("Orange"), Color(0xFFEA580C)),
    AMBER(trLater("Gelb"), Color(0xFFD97706)),
    GREEN(trLater("Grün"), Color(0xFF16A34A)),
    TEAL(trLater("Türkis"), Color(0xFF0D9488)),
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
    /** Raised surfaces on the background: input field, menus, cards. */
    val card: Color,
    /** Background of bottom sheets (the cards inside use [card]). */
    val sheet: Color,
    /** Brand color for small accents: logo, unread dots. */
    val brand: Color,
    /** Font of large titles such as the greeting and the app name in the side menu. */
    val titleFont: FontFamily,
    /** Main action buttons such as "Neuer Chat" and its content color. */
    val action: Color = Color.Black,
    val onAction: Color = Color.White,
    /** Thin frame around cards (ADEU look), null for none. */
    val cardBorder: BorderStroke? = null,
    /** Status colors: finished / ready and hints that need attention. */
    val success: Color = Color(0xFF259A64),
    val warning: Color = Color(0xFFC2710C),
    /** Colors of the speakers in transcripts. */
    val speakers: List<Color> = AdeuColors.speakersLight,
    /** Shape of cards and grouped lists. */
    val cardShape: Shape = RoundedCornerShape(20.dp),
)

val LocalChatLook = staticCompositionLocalOf {
    ChatLook(
        userBubble = Color.LightGray,
        onUserBubble = Color.Black,
        answerFont = FontFamily.Default,
        userShape = RoundedCornerShape(18.dp),
        inputShape = RoundedCornerShape(20.dp),
        card = Color.White,
        sheet = Color.White,
        brand = Color(0xFFD97757),
        titleFont = FontFamily.Default,
    )
}

/** Terracotta of the Claude look. */
val ClaudeOrange = Color(0xFFD97757)

/** Colors of the common ADEU design (the look of the transcription, formerly the Whisper app). */
object AdeuColors {
    val accent = Color(0xFF5B4FE9)
    val accent2 = Color(0xFF8B5CF6)
    val accentDark = Color(0xFF7B70FF)
    val accent2Dark = Color(0xFFA77BFF)
    val record = Color(0xFFE5484D)
    val speakersLight = listOf(
        Color(0xFF5B5BF0), Color(0xFF0B9B95), Color(0xFFD27D00), Color(0xFFDF3F72),
        Color(0xFF0A86CC), Color(0xFF9350E0), Color(0xFF4F9214), Color(0xFFD9571A),
    )
    val speakersDark = listOf(
        Color(0xFF8E8FFB), Color(0xFF33C9C1), Color(0xFFF4A62A), Color(0xFFF3729B),
        Color(0xFF45B5F2), Color(0xFFB98AF6), Color(0xFF8BC948), Color(0xFFF38B4F),
    )
}

private fun Color.hsl(): FloatArray {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val l = (max + min) / 2f
    val d = max - min
    if (d == 0f) return floatArrayOf(0f, 0f, l)
    val s = d / (1f - kotlin.math.abs(2f * l - 1f))
    val h = when (max) {
        red -> ((green - blue) / d).mod(6f)
        green -> (blue - red) / d + 2f
        else -> (red - green) / d + 4f
    } * 60f
    return floatArrayOf((h + 360f) % 360f, s.coerceIn(0f, 1f), l)
}

private fun hsl(h: Float, s: Float, l: Float) = Color.hsl(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

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
        AppStyle.ADEU -> {
            val base = if (chosen == null) {
                val c = if (dark) AdeuColors.accentDark else AdeuColors.accent
                seedScheme(c, dark).copy(
                    primary = c,
                    // Dark text on the light violet of the dark mode: 5:1 instead of 3.75:1 with white (WCAG AA).
                    onPrimary = if (dark) Color(0xFF0E0B30) else Color.White,
                    primaryContainer = if (dark) Color(0xFF2E2A5C) else Color(0xFFE9E7FC),
                    onPrimaryContainer = if (dark) Color(0xFFD9D6FF) else Color(0xFF2B1FA8),
                    secondaryContainer = if (dark) Color(0xFF2A2840) else Color(0xFFEDEBFD),
                    onSecondaryContainer = if (dark) Color(0xFFAAA3FF) else Color(0xFF4B3FD9),
                    tertiary = if (dark) AdeuColors.accent2Dark else AdeuColors.accent2,
                )
            } else seedScheme(chosen, dark)
            val withError = base.copy(error = if (dark) Color(0xFFF2555A) else Color(0xFFE5484D))
            if (dark) withError.withNeutrals(
                background = Color(0xFF111115), onSurface = Color(0xFFECECF2), onSurfaceVariant = Color(0xFFA6A6B6),
                outline = Color(0xFF717183),
                n = listOf(Color(0xFF1A1A20), Color(0xFF16161B), Color(0xFF222229), Color(0xFF2A2A33), Color(0xFF2C2C35)),
            ) else withError.withNeutrals(
                background = Color(0xFFF6F6F9), onSurface = Color(0xFF16161D), onSurfaceVariant = Color(0xFF53536A),
                outline = Color(0xFF8A8A9C),
                n = listOf(Color.White, Color(0xFFF1F1F6), Color(0xFFEDEDF3), Color(0xFFE9E9F0), Color(0xFFE3E3EA)),
            )
        }
        AppStyle.TUD -> {
            val blue = if (dark) Color(0xFF97C6FF) else Color(0xFF00008C)
            val base = seedScheme(chosen ?: Color(0xFF00008C), dark).let {
                if (chosen != null) it else it.copy(
                    primary = blue,
                    onPrimary = if (dark) Color(0xFF001450) else Color.White,
                    primaryContainer = if (dark) Color(0xFF1A2A6B) else Color(0xFFE3EEFF),
                    onPrimaryContainer = if (dark) Color(0xFFDCE8FF) else Color(0xFF001450),
                    secondary = if (dark) Color(0xFFB4BEC9) else Color(0xFF566371),
                    secondaryContainer = if (dark) Color(0xFF26313D) else Color(0xFFE8ECF0),
                    onSecondaryContainer = if (dark) Color(0xFFDDE3EA) else Color(0xFF001450),
                    tertiary = if (dark) Color(0xFF97C6FF) else Color(0xFF2F57B2),
                )
            }
            if (dark) base.withNeutrals(
                background = Color(0xFF0B1220), onSurface = Color(0xFFE8EDF3), onSurfaceVariant = Color(0xFFA9B4C0),
                outline = Color(0xFF6B7785),
                n = listOf(Color(0xFF131C2B), Color(0xFF101827), Color(0xFF1A2434), Color(0xFF212C3D), Color(0xFF2A3647)),
            ) else base.withNeutrals(
                background = Color(0xFFF4F6F9), onSurface = Color(0xFF0F1A2A), onSurfaceVariant = Color(0xFF566371),
                outline = Color(0xFF8A96A3),
                n = listOf(Color.White, Color(0xFFEEF1F5), Color(0xFFE9EDF2), Color(0xFFE2E7ED), Color(0xFFD9DFE6)),
            )
        }
        AppStyle.OFFICE -> {
            val blue = if (dark) Color(0xFF479EF5) else Color(0xFF0F6CBD)
            val base = seedScheme(chosen ?: Color(0xFF0F6CBD), dark).let {
                if (chosen != null) it else it.copy(
                    primary = blue,
                    onPrimary = if (dark) Color(0xFF0A2540) else Color.White,
                    primaryContainer = if (dark) Color(0xFF0E3A62) else Color(0xFFEBF3FC),
                    onPrimaryContainer = if (dark) Color(0xFFCFE4FA) else Color(0xFF0C3B5E),
                    secondaryContainer = if (dark) Color(0xFF333333) else Color(0xFFF0F0F0),
                    onSecondaryContainer = if (dark) Color(0xFFFFFFFF) else Color(0xFF242424),
                )
            }
            if (dark) base.withNeutrals(
                background = Color(0xFF1F1F1F), onSurface = Color(0xFFFFFFFF), onSurfaceVariant = Color(0xFFD6D6D6),
                outline = Color(0xFF666666),
                n = listOf(Color(0xFF292929), Color(0xFF242424), Color(0xFF2E2E2E), Color(0xFF383838), Color(0xFF424242)),
            ) else base.withNeutrals(
                background = Color(0xFFFAFAFA), onSurface = Color(0xFF242424), onSurfaceVariant = Color(0xFF616161),
                outline = Color(0xFF8A8A8A),
                n = listOf(Color.White, Color(0xFFF5F5F5), Color(0xFFF0F0F0), Color(0xFFEBEBEB), Color(0xFFE0E0E0)),
            )
        }
        AppStyle.CONTRAST -> {
            val base = (if (dark) darkColorScheme() else lightColorScheme()).copy(
                primary = if (dark) Color(0xFFFFE600) else Color(0xFF0000B3),
                onPrimary = if (dark) Color.Black else Color.White,
                primaryContainer = if (dark) Color(0xFF3A3500) else Color(0xFFDDE0FF),
                onPrimaryContainer = if (dark) Color(0xFFFFFFFF) else Color.Black,
                secondary = if (dark) Color.White else Color.Black,
                secondaryContainer = if (dark) Color(0xFF2B2B2B) else Color(0xFFE6E6E6),
                onSecondaryContainer = if (dark) Color.White else Color.Black,
                tertiary = if (dark) Color(0xFF7FDBFF) else Color(0xFF6B0099),
                error = if (dark) Color(0xFFFF8A80) else Color(0xFFB00000),
            )
            if (dark) base.withNeutrals(
                background = Color.Black, onSurface = Color.White, onSurfaceVariant = Color(0xFFE0E0E0),
                outline = Color.White,
                n = listOf(Color.Black, Color(0xFF0D0D0D), Color(0xFF141414), Color(0xFF1C1C1C), Color(0xFF262626)),
            ) else base.withNeutrals(
                background = Color.White, onSurface = Color.Black, onSurfaceVariant = Color(0xFF1F1F1F),
                outline = Color.Black,
                n = listOf(Color.White, Color(0xFFF7F7F7), Color(0xFFF0F0F0), Color(0xFFE8E8E8), Color(0xFFDADADA)),
            )
        }
        AppStyle.STANDARD -> if (chosen == null) dynamic ?: seedScheme(Color(0xFF1E3A8A), dark) else seedScheme(chosen, dark)
        AppStyle.CLAUDE -> {
            // Like the Claude app: black (or white) buttons, the accent only where it was chosen.
            val base = seedScheme(chosen ?: ClaudeOrange, dark).let { if (chosen == null) it.monochrome(dark) else it }
                .copy(tertiary = ClaudeOrange, onTertiary = Color.White)
            if (dark) base.withNeutrals(
                background = Color(0xFF262624), onSurface = Color(0xFFF5F4EE), onSurfaceVariant = Color(0xFFA6A39A),
                outline = Color(0xFF6E6C66),
                n = listOf(Color(0xFF1F1E1D), Color(0xFF2A2927), Color(0xFF30302E), Color(0xFF393937), Color(0xFF42413E)),
            ) else base.withNeutrals(
                background = Color(0xFFFAF9F5), onSurface = Color(0xFF141413), onSurfaceVariant = Color(0xFF73726C),
                outline = Color(0xFFA3A199),
                n = listOf(Color.White, Color(0xFFF5F4EF), Color(0xFFF0EEE6), Color(0xFFE8E6DC), Color(0xFFE0DED5)),
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

private fun chatLook(style: AppStyle, scheme: ColorScheme, dark: Boolean, amoled: Boolean): ChatLook {
    val look = when (style) {
        AppStyle.ADEU -> ChatLook(
            userBubble = if (dark) Color(0xFF26243F) else Color(0xFFECEAFD),
            onUserBubble = scheme.onSurface,
            answerFont = FontFamily.Default,
            userShape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
            inputShape = RoundedCornerShape(26.dp),
            card = if (dark) Color(0xFF1A1A20) else Color.White,
            sheet = scheme.background,
            brand = scheme.primary,
            titleFont = FontFamily.Default,
            action = scheme.primary,
            onAction = scheme.onPrimary,
            cardBorder = BorderStroke(1.dp, if (dark) Color(0xFF2C2C35) else Color(0xFFE3E3EA)),
            success = if (dark) Color(0xFF3FC585) else Color(0xFF259A64),
            warning = if (dark) Color(0xFFF0A33A) else Color(0xFFC2710C),
            speakers = if (dark) AdeuColors.speakersDark else AdeuColors.speakersLight,
            cardShape = RoundedCornerShape(18.dp),
        )
        AppStyle.TUD -> ChatLook(
            userBubble = scheme.primaryContainer,
            onUserBubble = scheme.onPrimaryContainer,
            answerFont = FontFamily.Default,
            userShape = RoundedCornerShape(12.dp, 12.dp, 2.dp, 12.dp),
            inputShape = RoundedCornerShape(12.dp),
            card = scheme.surfaceContainerLowest,
            sheet = scheme.background,
            brand = scheme.primary,
            titleFont = FontFamily.Default,
            action = scheme.primary,
            onAction = scheme.onPrimary,
            cardBorder = BorderStroke(1.dp, scheme.surfaceContainerHighest),
            cardShape = RoundedCornerShape(10.dp),
        )
        AppStyle.OFFICE -> ChatLook(
            userBubble = scheme.primaryContainer,
            onUserBubble = scheme.onPrimaryContainer,
            answerFont = FontFamily.Default,
            userShape = RoundedCornerShape(8.dp),
            inputShape = RoundedCornerShape(8.dp),
            card = scheme.surfaceContainerLowest,
            sheet = scheme.background,
            brand = scheme.primary,
            titleFont = FontFamily.Default,
            action = scheme.primary,
            onAction = scheme.onPrimary,
            cardBorder = BorderStroke(1.dp, scheme.surfaceContainerHighest),
            cardShape = RoundedCornerShape(6.dp),
        )
        AppStyle.CONTRAST -> ChatLook(
            userBubble = scheme.primaryContainer,
            onUserBubble = scheme.onPrimaryContainer,
            answerFont = FontFamily.Default,
            userShape = RoundedCornerShape(8.dp),
            inputShape = RoundedCornerShape(8.dp),
            card = scheme.background,
            sheet = scheme.background,
            brand = scheme.primary,
            titleFont = FontFamily.Default,
            action = scheme.primary,
            onAction = scheme.onPrimary,
            cardBorder = BorderStroke(2.dp, scheme.onSurface),
            success = if (dark) Color(0xFF7CFF8A) else Color(0xFF006B1F),
            warning = if (dark) Color(0xFFFFC94D) else Color(0xFF8A4B00),
            cardShape = RoundedCornerShape(8.dp),
        )
        AppStyle.STANDARD -> ChatLook(
            userBubble = scheme.primaryContainer,
            onUserBubble = scheme.onPrimaryContainer,
            answerFont = FontFamily.Default,
            userShape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
            inputShape = RoundedCornerShape(26.dp),
            card = if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest,
            sheet = scheme.surfaceContainerLow,
            brand = scheme.primary,
            titleFont = FontFamily.Default,
            action = scheme.primary,
            onAction = scheme.onPrimary,
        )
        AppStyle.CLAUDE -> ChatLook(
            userBubble = if (dark) Color(0xFF141413) else Color(0xFFF0EEE6),
            onUserBubble = scheme.onSurface,
            answerFont = FontFamily.Default,
            userShape = RoundedCornerShape(20.dp),
            inputShape = RoundedCornerShape(28.dp),
            card = if (dark) Color(0xFF30302E) else Color.White,
            sheet = if (dark) Color(0xFF262624) else Color(0xFFF5F4EF),
            brand = ClaudeOrange,
            titleFont = FontFamily.Serif,
            action = scheme.onSurface,
            onAction = scheme.surface,
        )
        AppStyle.CHATGPT -> ChatLook(
            userBubble = scheme.surfaceContainerHigh,
            onUserBubble = scheme.onSurface,
            answerFont = FontFamily.Default,
            userShape = RoundedCornerShape(22.dp),
            inputShape = RoundedCornerShape(28.dp),
            card = if (dark) Color(0xFF303030) else Color.White,
            sheet = if (dark) Color(0xFF212121) else Color(0xFFF9F9F9),
            brand = Color(0xFF10A37F),
            titleFont = FontFamily.Default,
            action = scheme.onSurface,
            onAction = scheme.surface,
        )
        AppStyle.GEMINI -> ChatLook(
            userBubble = scheme.surfaceContainerHigh,
            onUserBubble = scheme.onSurface,
            answerFont = FontFamily.Default,
            userShape = RoundedCornerShape(24.dp, 4.dp, 24.dp, 24.dp),
            inputShape = RoundedCornerShape(32.dp),
            card = if (dark) Color(0xFF1E1F20) else Color(0xFFF0F4F9),
            sheet = if (dark) Color(0xFF131314) else Color.White,
            brand = Color(0xFF4285F4),
            titleFont = FontFamily.Default,
            action = scheme.primary,
            onAction = scheme.onPrimary,
        )
    }
    val speakers = if (dark) look.copy(speakers = AdeuColors.speakersDark) else look
    return if (dark && amoled && style != AppStyle.CONTRAST) speakers.copy(card = Color(0xFF161616), sheet = Color(0xFF0A0A0A), cardBorder = null) else speakers
}

/** ADEU look: titles a little bolder and tighter, like the transcription screens. */
private fun adeuTypography(): Typography {
    val t = Typography()
    fun semi(s: androidx.compose.ui.text.TextStyle, spacing: Float = -0.01f) = s.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (s.fontSize.value * spacing).sp)
    return t.copy(
        displaySmall = semi(t.displaySmall, -0.02f),
        headlineLarge = semi(t.headlineLarge, -0.02f),
        headlineMedium = semi(t.headlineMedium, -0.02f),
        headlineSmall = semi(t.headlineSmall),
        titleLarge = semi(t.titleLarge),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Corner radii per look: round in ADEU, restrained in the businesslike looks. */
private fun shapesOf(style: AppStyle): Shapes? = when (style) {
    AppStyle.ADEU -> Shapes(
        extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp),
    )
    AppStyle.TUD -> Shapes(
        extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(6.dp), medium = RoundedCornerShape(10.dp),
        large = RoundedCornerShape(12.dp), extraLarge = RoundedCornerShape(16.dp),
    )
    AppStyle.OFFICE, AppStyle.CONTRAST -> Shapes(
        extraSmall = RoundedCornerShape(2.dp), small = RoundedCornerShape(4.dp), medium = RoundedCornerShape(6.dp),
        large = RoundedCornerShape(8.dp), extraLarge = RoundedCornerShape(10.dp),
    )
    else -> null
}

/** Typography per look; null keeps Material's. */
private fun typographyOf(style: AppStyle): Typography? = when (style) {
    AppStyle.ADEU, AppStyle.TUD -> adeuTypography()
    AppStyle.OFFICE -> Typography().let { t ->
        t.copy(
            headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
    AppStyle.CONTRAST -> Typography().let { t ->
        t.copy(
            bodyLarge = t.bodyLarge.copy(fontWeight = FontWeight.Medium),
            bodyMedium = t.bodyMedium.copy(fontWeight = FontWeight.Medium),
            bodySmall = t.bodySmall.copy(fontWeight = FontWeight.Medium),
            labelLarge = t.labelLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold),
            titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
            headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
            headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        )
    }
    else -> null
}

@Composable
fun LlmHubTheme(settings: AppSettings, content: @Composable () -> Unit) {
    if (I18n.language != settings.uiLanguage) I18n.language = settings.uiLanguage
    val dark = when (enumOr(settings.themeMode, ThemeMode.SYSTEM)) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val style = enumOr(settings.appStyle, AppStyle.ADEU)
    val accent = enumOr(settings.accent, Accent.AUTO)
    val colors = styledScheme(style, accent, dark, dynamicScheme(dark)).let { if (dark && settings.amoled) it.amoled() else it }
    SystemBars(dark, colors.background)

    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalChatLook provides chatLook(style, colors, dark, settings.amoled),
        LocalDensity provides Density(density.density * settings.uiZoom, density.fontScale * settings.textScale),
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = typographyOf(style) ?: MaterialTheme.typography,
            shapes = shapesOf(style) ?: MaterialTheme.shapes,
            content = content,
        )
    }
}

/** Colors and look of a style without applying it (previews under Einstellungen › Darstellung). */
fun stylePreview(style: AppStyle, dark: Boolean): Pair<ColorScheme, ChatLook> {
    val scheme = styledScheme(style, Accent.AUTO, dark, null)
    return scheme to chatLook(style, scheme, dark, amoled = false)
}
