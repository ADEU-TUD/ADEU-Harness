package io.github.thepytales.llmhub.data

import java.text.Normalizer

/**
 * Big tools that can be switched on under Einstellungen › Werkzeuge (AP-02). New tools start switched off, so the app
 * does not get crowded; switched-on ones appear in the side menu under „Mehr“ and on the start page, and Lora
 * suggests a tool when a request fits it ([suggestFor]). [planned] tools are listed but cannot be switched on yet.
 */
enum class AppTool(
    val id: String,
    val label: String,
    val description: String,
    /** Screen of the tool (see ui/AppRoot). */
    val route: String,
    /** Words of a request that fit the tool (lower case, without accents). */
    val keywords: List<String>,
    /** Offered on Android too (Windows gets everything). */
    val android: Boolean = true,
    val planned: Boolean = false,
) {
    PDF(
        "pdf", "PDF-Werkzeugkasten",
        "PDFs in Tabs ansehen, zusammenfügen, teilen, Seiten drehen und sortieren, markieren und kommentieren, Formulare ausfüllbar machen.",
        "pdf", listOf("pdf", "zusammenfugen", "zusammenfuehren", "seiten drehen", "seiten loschen", "seitenzahlen", "markieren", "formular", "ausfullbar", "heften"),
    ),
    VAULT(
        "vault", "Tresor",
        "Geschützter Bereich mit PIN für sensible Forschung, z. B. Interviews oder Daten von Studierenden: verschlüsselt, nur Uni-Server oder lokale Modelle.",
        "vault", listOf("tresor", "sensibel", "vertraulich", "geschutzt", "pin", "interviewdaten", "daten von studierenden", "geheim"),
    ),
    GAMES(
        "games", "Lernspiele",
        "Quiz, Zuordnen, Memory und Escape-Rooms als eigenständige Browserspiele bauen lassen, in der App testen und als HTML/ZIP für OPAL oder Moodle exportieren.",
        "games", listOf("lernspiel", "quiz", "memory", "escape", "zuordnungsspiel", "spiel fur", "gamification"),
    ),
    CREATIVE(
        "creative", "Kreativbereich", "Poster, Flyer und Aushänge im Corporate Design gestalten.", "creative",
        listOf("poster", "flyer", "aushang", "canva"), android = false, planned = true,
    ),
    VIDEO(
        "video", "Videoeditor", "Lehrvideos schneiden, Untertitel einbrennen, für OPAL und YouTube exportieren.", "video",
        listOf("video schneiden", "videoschnitt", "untertitel einbrennen"), android = false, planned = true,
    );

    companion object {
        fun byId(id: String): AppTool? = entries.firstOrNull { it.id == id }

        /** Tools of this platform that can be switched on now. */
        fun available(android: Boolean): List<AppTool> = entries.filter { !it.planned && (!android || it.android) }

        /** Planned tools of this platform (shown greyed out). */
        fun planned(android: Boolean): List<AppTool> = entries.filter { it.planned && (!android || it.android) }

        private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
            .replace("ß", "ss")

        /**
         * A tool that fits [text] (a request or the name of an attached file) and is not switched on yet – for Lora's
         * hint „Für PDFs gibt es den PDF-Werkzeugkasten – einschalten?“. Null when nothing fits.
         */
        fun suggestFor(text: String, enabled: Set<String>, android: Boolean, tools: List<AppTool> = available(android)): AppTool? {
            val t = norm(text)
            if (t.isBlank()) return null
            return tools.firstOrNull { tool ->
                tool.id !in enabled && tool.keywords.any { k -> Regex("(^|[^a-z])" + Regex.escape(k)).containsMatchIn(t) }
            }
        }
    }
}

/** Adds or removes [tool] in the list of switched-on tools. */
fun AppSettings.withTool(tool: AppTool, on: Boolean): AppSettings {
    val ids = enabledToolIds.let { if (on) it + tool.id else it - tool.id }
    return copy(enabledTools = AppTool.entries.map { it.id }.filter { it in ids }.joinToString("\n"))
}
