package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.ui.trLater

/**
 * Rollenauswahl beim ersten Start (AP-80): „Wofür nutzt du Lora vor allem?“ – Lehre, Forschung, Verwaltung, Studium,
 * mehrere möglich. Daraus folgen die vorgeschlagenen Werkzeuge, die Reihenfolge der TU Vorlagen, Loras Vorschläge auf
 * der Startseite und die empfohlenen Inhalte zum Nachladen (AP-78). Später änderbar unter Einstellungen › Lora; eine
 * Rolle schaltet Werkzeuge nur ein, nie aus.
 */
enum class Role(val label: String, val description: String) {
    TEACHING(trLater("Lehre"), trLater("Seminare, Prüfungen, Lehrvideos, Lernspiele")),
    RESEARCH(trLater("Forschung"), trLater("Literatur, Daten, Anträge, Publikationen")),
    ADMIN(trLater("Verwaltung"), trLater("Briefe, Mails, Protokolle, Fristen")),
    STUDY(trLater("Studium"), trLater("Lernen, Exzerpte, Hausarbeiten")),
    ;

    companion object {
        fun parse(text: String): Set<Role> = text.split(',').mapNotNull { n -> entries.firstOrNull { it.name == n.trim() } }.toSet()
        fun format(roles: Set<Role>): String = entries.filter { it in roles }.joinToString(",") { it.name }
    }
}

object Roles {
    /** Tools a role suggests (switched on with the role, never off). */
    fun tools(roles: Set<Role>, android: Boolean): Set<AppTool> = buildSet {
        if (Role.TEACHING in roles) addAll(listOf(AppTool.PDF, AppTool.GAMES, AppTool.BATCH, AppTool.VIDEO))
        if (Role.RESEARCH in roles) addAll(listOf(AppTool.PDF, AppTool.VAULT, AppTool.PROPOSALS))
        if (Role.ADMIN in roles) addAll(listOf(AppTool.PDF, AppTool.MAIL, AppTool.BATCH))
        if (Role.STUDY in roles) add(AppTool.PDF)
    }.filter { !it.planned && (!android || it.android) }.toSet()

    /** The sections of the TU Vorlagen in the order that fits the roles (the others keep their order behind). */
    fun categories(roles: Set<Role>): List<TemplateCategory> {
        val first = buildList {
            if (Role.TEACHING in roles) addAll(listOf(TemplateCategory.TEACHING, TemplateCategory.THESES))
            if (Role.RESEARCH in roles) addAll(listOf(TemplateCategory.RESEARCH, TemplateCategory.THESES))
            if (Role.ADMIN in roles) addAll(listOf(TemplateCategory.TUD, TemplateCategory.COMMUNICATION))
            if (Role.STUDY in roles) add(TemplateCategory.GENERAL)
        }.distinct()
        return first + TemplateCategory.entries.filter { it !in first }
    }

    /** Wissenspakete (AP-78) worth loading for the roles. */
    fun packs(roles: Set<Role>): List<String> = if (Role.TEACHING in roles || Role.STUDY in roles) listOf("lehramt-tud") else emptyList()

    /** [s] with the roles and the tools they suggest switched on. */
    fun apply(s: AppSettings, roles: Set<Role>, android: Boolean): AppSettings {
        var next = s.copy(roles = Role.format(roles))
        tools(roles, android).forEach { t -> next = next.withTool(t, true) }
        return next
    }
}
