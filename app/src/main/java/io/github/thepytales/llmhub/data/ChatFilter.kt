package io.github.thepytales.llmhub.data

/**
 * Filters of the chat search (AP-16): period, project, model, chats with attachments or with files the AI made, the
 * archive. With a [query] the title and the messages must contain it; without one the filters alone pick the chats.
 */
data class ChatFilter(
    /** Days back (0 = all time). */
    val days: Int = 0,
    /** Project id; "" = chats without project; null = all. */
    val project: String? = null,
    val model: String? = null,
    val withAttachments: Boolean = false,
    val withFiles: Boolean = false,
    val archive: Boolean = false,
) {
    val active: Boolean get() = days > 0 || project != null || model != null || withAttachments || withFiles || archive

    fun matches(c: Conversation, now: Long = System.currentTimeMillis()): Boolean {
        if (c.vault) return false
        if (c.archived && !archive) return false
        if (days > 0 && now - c.updatedAt > days * DAY) return false
        if (project != null && (c.folderId ?: "") != project) return false
        if (model != null && c.model != model && c.messages.none { it.model == model }) return false
        if (withAttachments && c.messages.none { it.role == "user" && (it.files.isNotEmpty() || it.images.isNotEmpty()) }) return false
        if (withFiles && c.messages.none { it.file != null }) return false
        return true
    }

    companion object {
        private const val DAY = 24L * 60 * 60 * 1000

        /** The chats of [list] that fit [filter] and contain [query], newest first, with a passage around the match. */
        fun search(list: List<Conversation>, query: String, filter: ChatFilter, now: Long = System.currentTimeMillis()): List<Pair<Conversation, String?>> {
            val q = query.trim()
            if (q.isEmpty() && !filter.active) return emptyList()
            return list.filter { filter.matches(it, now) }.mapNotNull { conv ->
                if (q.isEmpty()) return@mapNotNull conv to null
                val inTitle = conv.title.contains(q, ignoreCase = true)
                val msg = conv.messages.firstOrNull { (it.role == "user" || it.role == "assistant") && it.content.contains(q, ignoreCase = true) }
                    ?: conv.messages.firstOrNull { m -> m.files.any { it.name.contains(q, true) } }
                if (!inTitle && msg == null) return@mapNotNull null
                val snippet = msg?.let { m ->
                    val text = m.content.takeIf { it.contains(q, true) } ?: m.files.joinToString(", ") { it.name }
                    val i = text.indexOf(q, ignoreCase = true).coerceAtLeast(0)
                    val start = (i - 40).coerceAtLeast(0)
                    (if (start > 0) "…" else "") + text.substring(start, minOf(text.length, i + q.length + 80)).replace(Regex("\\s+"), " ")
                }
                conv to snippet
            }.sortedByDescending { it.first.updatedAt }
        }
    }
}
