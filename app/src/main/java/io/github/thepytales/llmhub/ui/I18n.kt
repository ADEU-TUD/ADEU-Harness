package io.github.thepytales.llmhub.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Language of the interface (Einstellungen › Darstellung). German is the source language; [tr] looks up the
 * English text and falls back to German for everything not translated yet, so screens can be translated one by one.
 */
object I18n {
    val LANGUAGES = listOf("de" to "Deutsch", "en" to "English")

    /** Set from the settings by the theme; a state so the interface switches at once. */
    var language by mutableStateOf("de")

    internal val EN: Map<String, String> = mapOf(
        // Side menu and areas
        "Neuer Chat" to "New chat",
        "Vorlagen" to "Templates",
        "Literatur" to "Literature",
        "Dokumente" to "Documents",
        "Geplante Aufgaben" to "Scheduled tasks",
        "Bilder erzeugen" to "Create images",
        "Transkription" to "Transcription",
        "Projekte" to "Projects",
        "Angeheftet" to "Pinned",
        "Chats" to "Chats",
        "Archiv" to "Archive",
        "Einstellungen" to "Settings",
        "Modelle vergleichen" to "Compare models",
        "Modelle" to "Models",
        "Verbrauch" to "Usage",
        // Chat
        "Wie kann ich helfen?" to "How can I help?",
        "Antworten …" to "Reply …",
        "Guten Morgen" to "Good morning",
        "Guten Tag" to "Good afternoon",
        "Guten Abend" to "Good evening",
        "Hallo" to "Hello",
        "Alle Vorlagen ›" to "All templates ›",
        "Lehre, Forschung, Betreuung, TUD-Dokumente" to "Teaching, research, supervision, TUD documents",
        "Literatur zu einem Thema finden" to "Find literature on a topic",
        "Literaturverzeichnis ordnen" to "Tidy up a bibliography",
        "Dokument entwerfen" to "Draft a document",
        "Diagramm aus Daten" to "Chart from data",
        "Interview transkribieren" to "Transcribe an interview",
        "Werkzeuge genutzt" to "tools used",
        "ADEU LLM nutzt KI und kann Fehler machen. Wichtiges bitte prüfen." to "ADEU LLM uses AI and can make mistakes. Please check important information.",
        // Settings
        "Zugang" to "Access",
        "Assistent" to "Assistant",
        "Dokumente & Darstellung" to "Documents & appearance",
        "Daten" to "Data",
        "Treffer" to "Results",
        "Einstellungen durchsuchen" to "Search settings",
        "Keine passende Einstellung." to "No matching setting.",
        "API-Zugang" to "API access",
        "GitHub" to "GitHub",
        "Umgebungen" to "Environments",
        "Chat & Werkzeuge" to "Chat & tools",
        "Datenschutz" to "Privacy",
        "Gedächtnis" to "Memory",
        "Wissenssammlung" to "Knowledge base",
        "Literatur & Zotero" to "Literature & Zotero",
        "Modellübersicht" to "Model overview",
        "Modelle auf diesem Gerät" to "Models on this device",
        "Dokumente & Logos" to "Documents & logos",
        "Vorlesen & Spracheingabe" to "Read aloud & voice input",
        "Darstellung" to "Appearance",
        "Sicherung & Umzug" to "Backup & transfer",
        "Papierkorb" to "Trash",
        "Lokale Ordner" to "Local folders",
        "Speicherorte" to "Storage locations",
        "Sprache der Oberfläche" to "Interface language",
        "Stil" to "Style",
        "Helligkeit" to "Brightness",
        "Farbton" to "Accent colour",
        "Schriftgröße" to "Text size",
        // Command palette and shortcuts
        "Bereich, Einstellung oder Chat suchen …" to "Search areas, settings or chats …",
        "Aktionen" to "Actions",
        "Bereiche" to "Areas",
        "Zuletzt" to "Recent",
        "Nichts gefunden." to "Nothing found.",
        "Tastenkürzel" to "Keyboard shortcuts",
        "Schließen" to "Close",
        "Befehlspalette: Bereiche, Einstellungen, Chats" to "Command palette: areas, settings, chats",
        "Seitenleiste ein/aus" to "Show/hide side menu",
        "Diese Übersicht" to "This overview",
        "Senden (Umschalt+Enter: neue Zeile)" to "Send (Shift+Enter: new line)",
        "Befehle im Eingabefeld" to "Commands in the input field",
        "Dateien, Literatur und Dokumente erwähnen" to "Mention files, literature and documents",
        "Menü schließen bzw. zurück" to "Close menu or go back",
    )

    fun tr(german: String): String = if (language == "en") EN[german] ?: german else german

    /** Added to the system prompt so answers follow the interface language unless the user writes otherwise. */
    fun answerHint(language: String): String? =
        if (language == "en") "Answer in English unless the user writes in another language or asks for one." else null
}

/** Shortcut for [I18n.tr]. */
fun tr(german: String): String = I18n.tr(german)
