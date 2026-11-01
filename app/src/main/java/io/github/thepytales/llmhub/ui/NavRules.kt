package io.github.thepytales.llmhub.ui

/** Rules of the navigation, kept apart from the screens so they can be tested (see StorageAndSafetyTest). */

/**
 * Whether the screen with back stack id [from] may still act: only the screen on top does. A second tap on "Zurück"
 * while a screen slides away (the chat's menu button sits where the back arrow was) must not remove the chat too.
 */
fun isOnTop(currentId: String?, from: String) = currentId == from

/** Screens of the side menu: there the menu also opens with a swipe from the left. */
fun isTopLevel(route: String?) =
    route == null || route.startsWith("chat") || route in setOf("transcripts", "templates", "tasks", "images", "literature", "docs", "pdf", "vault", "games", "mail", "files", "creative", "video")

/**
 * Areas of the side menu (AP-02). „Neuer Chat“ always comes first; up to [MAX_TOP_AREAS] areas sit next to it on
 * top, everything else – and every switched-on tool (data/AppTool) – is under „Mehr“.
 */
enum class DrawerArea(val id: String, val label: String, val route: String, val tool: io.github.thepytales.llmhub.data.AppTool? = null) {
    /** „Erstellen“ (AP-24): the TU Vorlagen and, on Windows, designs (poster, flyer …) – next to „Dateien“ the second place for content. */
    TEMPLATES("templates", "Erstellen", "templates"),
    LITERATURE("literature", "Literatur", "literature"),
    TRANSCRIPTS("transcripts", "Transkription", "transcripts"),
    FILES("files", "Dateien", "files"),
    IMAGES("images", "Bilder erzeugen", "images"),
    TASKS("tasks", "Geplante Aufgaben", "tasks"),
    PDF("pdf", "PDF-Werkzeugkasten", "pdf", io.github.thepytales.llmhub.data.AppTool.PDF),
    VAULT("vault", "Tresor", "vault", io.github.thepytales.llmhub.data.AppTool.VAULT),
    GAMES("games", "Lernspiele", "games", io.github.thepytales.llmhub.data.AppTool.GAMES),
    MAIL("mail", "Mail & Kalender", "mail", io.github.thepytales.llmhub.data.AppTool.MAIL),
    VIDEO("video", "Videoeditor", "video", io.github.thepytales.llmhub.data.AppTool.VIDEO),
    ;

    companion object {
        /** „docs“ (Dokumente) became „files“ (Dateien) with AP-16. */
        fun byId(id: String): DrawerArea? = entries.firstOrNull { it.id == (if (id == "docs") "files" else id) }

        /** Areas that stay in the side menu: the two places for content. */
        val ALWAYS: Set<DrawerArea> = setOf(TEMPLATES, FILES)
    }
}

/** Areas next to „Neuer Chat“ on top: with it at most five entries. */
const val MAX_TOP_AREAS = 4

/**
 * The side menu's areas on top and under „Mehr“, from the stored order [top], the switched-on tools and the areas the
 * user [hidden] (Einstellungen › Werkzeuge or right click › Ausblenden; „Erstellen“ and „Dateien“ always stay).
 */
fun drawerLayout(top: String, enabledTools: Set<String>, hidden: Set<String> = emptySet()): Pair<List<DrawerArea>, List<DrawerArea>> {
    val visible = DrawerArea.entries.filter { (it.tool == null || it.tool.id in enabledTools) && (it in DrawerArea.ALWAYS || it.id !in hidden) }
    val upper = top.lines().mapNotNull { DrawerArea.byId(it.trim()) }.filter { it in visible }.distinct().take(MAX_TOP_AREAS)
    return upper to visible.filter { it !in upper }
}

/** Hides or shows [area] in the side menu; returns the new stored list. */
fun hideDrawerArea(hidden: String, area: DrawerArea, hide: Boolean): String {
    if (area in DrawerArea.ALWAYS) return hidden
    val list = hidden.lines().map { it.trim() }.filter { it.isNotEmpty() && it != area.id }
    return (if (hide) list + area.id else list).joinToString("\n")
}

/**
 * Moves [area] on top (at the end) or back under „Mehr“; returns the new stored order, or null when the top is full
 * (the user first moves another area down).
 */
fun moveDrawerArea(top: String, area: DrawerArea, toTop: Boolean): String? {
    val list = top.lines().map { it.trim() }.filter { DrawerArea.byId(it) != null }.distinct()
    return when {
        toTop && area.id in list -> top
        toTop && list.size >= MAX_TOP_AREAS -> null
        toTop -> (list + area.id).joinToString("\n")
        else -> (list - area.id).joinToString("\n")
    }
}
