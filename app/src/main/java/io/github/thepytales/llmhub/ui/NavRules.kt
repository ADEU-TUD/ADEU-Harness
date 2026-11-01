package io.github.thepytales.llmhub.ui

/** Rules of the navigation, kept apart from the screens so they can be tested (see StorageAndSafetyTest). */

/**
 * Whether the screen with back stack id [from] may still act: only the screen on top does. A second tap on "Zurück"
 * while a screen slides away (the chat's menu button sits where the back arrow was) must not remove the chat too.
 */
fun isOnTop(currentId: String?, from: String) = currentId == from

/** Screens of the side menu: there the menu also opens with a swipe from the left. */
fun isTopLevel(route: String?) =
    route == null || route.startsWith("chat") || route in setOf("transcripts", "templates", "tasks", "images", "literature", "docs", "pdf", "vault", "games", "mail")

/**
 * Areas of the side menu (AP-02). „Neuer Chat“ always comes first; up to [MAX_TOP_AREAS] areas sit next to it on
 * top, everything else – and every switched-on tool (data/AppTool) – is under „Mehr“.
 */
enum class DrawerArea(val id: String, val label: String, val route: String, val tool: io.github.thepytales.llmhub.data.AppTool? = null) {
    TEMPLATES("templates", "TU Vorlagen", "templates"),
    LITERATURE("literature", "Literatur", "literature"),
    TRANSCRIPTS("transcripts", "Transkription", "transcripts"),
    DOCS("docs", "Dokumente", "docs"),
    IMAGES("images", "Bilder erzeugen", "images"),
    TASKS("tasks", "Geplante Aufgaben", "tasks"),
    PDF("pdf", "PDF-Werkzeugkasten", "pdf", io.github.thepytales.llmhub.data.AppTool.PDF),
    VAULT("vault", "Tresor", "vault", io.github.thepytales.llmhub.data.AppTool.VAULT),
    GAMES("games", "Lernspiele", "games", io.github.thepytales.llmhub.data.AppTool.GAMES),
    MAIL("mail", "Mail & Kalender", "mail", io.github.thepytales.llmhub.data.AppTool.MAIL),
    ;

    companion object {
        fun byId(id: String): DrawerArea? = entries.firstOrNull { it.id == id }
    }
}

/** Areas next to „Neuer Chat“ on top: with it at most five entries. */
const val MAX_TOP_AREAS = 4

/** The side menu's areas on top and under „Mehr“, from the stored order [top] and the switched-on tools. */
fun drawerLayout(top: String, enabledTools: Set<String>): Pair<List<DrawerArea>, List<DrawerArea>> {
    val visible = DrawerArea.entries.filter { it.tool == null || it.tool.id in enabledTools }
    val upper = top.lines().mapNotNull { DrawerArea.byId(it.trim()) }.filter { it in visible }.distinct().take(MAX_TOP_AREAS)
    return upper to visible.filter { it !in upper }
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
