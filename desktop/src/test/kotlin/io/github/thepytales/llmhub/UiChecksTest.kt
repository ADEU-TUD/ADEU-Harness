package io.github.thepytales.llmhub

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.thepytales.llmhub.ui.APP_COMMANDS
import io.github.thepytales.llmhub.ui.AppStyle
import io.github.thepytales.llmhub.ui.SETTINGS_INDEX
import io.github.thepytales.llmhub.ui.filterCommands
import io.github.thepytales.llmhub.ui.settingsTitle
import io.github.thepytales.llmhub.ui.stylePreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiChecksTest {
    private fun contrast(a: Color, b: Color): Double {
        val l1 = a.luminance() + 0.05
        val l2 = b.luminance() + 0.05
        return maxOf(l1, l2) / minOf(l1, l2)
    }

    /** Body text must stay readable (WCAG AA 4.5:1) in every style, light and dark; high contrast reaches AAA (7:1). */
    @Test
    fun textContrastInAllStyles() {
        val failures = mutableListOf<String>()
        for (style in AppStyle.entries) for (dark in listOf(false, true)) {
            val (s, look) = stylePreview(style, dark)
            val min = if (style == AppStyle.CONTRAST) 7.0 else 4.5
            val pairs = listOf(
                "onBackground/background" to contrast(s.onBackground, s.background),
                "onSurface/surface" to contrast(s.onSurface, s.surface),
                "onSurface/card" to contrast(s.onSurface, look.card),
                "onUserBubble/userBubble" to contrast(look.onUserBubble, look.userBubble),
                "onPrimary/primary" to contrast(s.onPrimary, s.primary),
            )
            pairs.filter { it.second < min }.forEach { failures += "${style.name}${if (dark) " dunkel" else ""}: ${it.first} = ${"%.2f".format(it.second)}" }
            // Secondary text (hints, timestamps) at least 3:1.
            val variant = contrast(s.onSurfaceVariant, s.surface)
            if (variant < 3.0) failures += "${style.name}${if (dark) " dunkel" else ""}: onSurfaceVariant/surface = ${"%.2f".format(variant)}"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun commandPaletteFindsAreasAndSettings() {
        assertEquals("settings/literature", filterCommands(APP_COMMANDS, "zitierstil").first().route)
        assertEquals("literature", filterCommands(APP_COMMANDS, "Literatur").first().route)
        assertEquals("settings/appearance", filterCommands(SETTINGS_INDEX, "dunkel").single().route)
        assertTrue(filterCommands(APP_COMMANDS, "gibtsnicht").isEmpty())
        // Every settings page in the search has a title (the page exists).
        SETTINGS_INDEX.forEach { assertEquals(it.title, settingsTitle(it.route.removePrefix("settings/"))) }
    }

    @Test
    fun englishInterfaceFallsBackToGerman() {
        val i18n = io.github.thepytales.llmhub.ui.I18n
        try {
            i18n.language = "en"
            assertEquals("Settings", io.github.thepytales.llmhub.ui.tr("Einstellungen"))
            assertEquals("Literature & Zotero", settingsTitle("literature"))
            assertEquals("Noch nicht übersetzt", io.github.thepytales.llmhub.ui.tr("Noch nicht übersetzt"))
            assertEquals("Answer in English unless the user writes in another language or asks for one.", i18n.answerHint("en"))
            // Every settings page and area of the command palette has an English title.
            APP_COMMANDS.forEach { assertTrue(it.title in i18n.EN, it.title) }
        } finally {
            i18n.language = "de"
        }
        assertEquals("Einstellungen", io.github.thepytales.llmhub.ui.tr("Einstellungen"))
    }
}
