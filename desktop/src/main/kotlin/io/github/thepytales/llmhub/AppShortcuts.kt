package io.github.thepytales.llmhub

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import io.github.thepytales.llmhub.ui.isCmd

/**
 * The app-wide keys of the window (Strg+/ shows them, see ShortcutsDialog) as routes for Navigation.openRoute.
 *
 * Keine Überschneidungen (Hauptnutzer 14.11.2026, AP-125): keys no editor uses are taken **before** the focused element
 * ([early]); Strg+B and Strg+D mean Fett and Duplizieren in the editors (Dokument, Tabelle, Folien, Gestalten), so they
 * reach the focused element first and are the Seitenleiste and Dateien only when nothing used them ([late]) – im Text
 * gewinnt der Text, everywhere else the app keeps its keys.
 */
internal object AppShortcuts {
    /** Before the focused element: Strg+N, Strg+, Strg+K, Strg+/, Strg+L, Strg+Umschalt+L, Strg+T. */
    fun early(e: KeyEvent): String? = if (e.type != KeyEventType.KeyDown || !e.isCmd) null else when (e.key) {
        Key.N -> "chat"
        // Strg+Umschalt+, is Schrift kleiner in the document (AP-125 c, like Google Docs).
        Key.Comma -> if (e.isShiftPressed) null else "settings"
        Key.K -> "palette"
        Key.Slash, Key.NumPadDivide -> "shortcuts"
        Key.L -> if (e.isShiftPressed) "literature" else "lora"
        Key.T -> "transcripts"
        else -> null
    }

    /** After the focused element had its chance: Strg+B Seitenleiste, Strg+D Dateien. */
    fun late(e: KeyEvent): String? = if (e.type != KeyEventType.KeyDown || !e.isCmd || e.isShiftPressed || e.isAltPressed) null else when (e.key) {
        Key.B -> "drawer"
        Key.D -> "files"
        else -> null
    }
}
