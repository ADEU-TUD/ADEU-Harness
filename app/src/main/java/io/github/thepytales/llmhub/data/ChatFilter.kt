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

        /**
         * Suche nach Bedeutung (AP-107): chats of [list] that fit [filter] and are about [query] in other words – not
         * among [found] –, best first; empty without the model on this device. Title and the first messages count.
         */
        fun similar(list: List<Conversation>, query: String, filter: ChatFilter, found: Set<String>, k: Int = 8, now: Long = System.currentTimeMillis()): List<Pair<Conversation, String?>> {
            val q = query.trim()
            if (q.length < 3 || !SemanticSearch.active) return emptyList()
            val cands = list.filter { filter.matches(it, now) && it.id !in found }.sortedByDescending { it.updatedAt }.take(400)
            if (cands.isEmpty()) return emptyList()
            val texts = cands.map { conv ->
                (conv.title + "\n" + conv.messages.filter { it.role == "user" || it.role == "assistant" }.take(4).joinToString("\n") { it.content.take(400) }).take(1_500)
            }
            val cos = SemanticSearch.similarity(q, texts, limit = 120) ?: return emptyList()
            // Only clearly fitting chats: well above the average of all.
            val known = cos.filterNotNull()
            if (known.size < 2) return emptyList()
            val mean = known.average()
            val sd = kotlin.math.sqrt(known.sumOf { (it - mean) * (it - mean) } / known.size).coerceAtLeast(0.005)
            return cands.indices.filter { i -> cos[i]?.let { it > mean + 1.5 * sd && it > 0.78 } == true }
                .sortedByDescending { cos[it] }.take(k)
                .map { i -> cands[i] to cands[i].messages.firstOrNull { it.role == "user" }?.content?.replace(Regex("\\s+"), " ")?.take(120) }
        }

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
