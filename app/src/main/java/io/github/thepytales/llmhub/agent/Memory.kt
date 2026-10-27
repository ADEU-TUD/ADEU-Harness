package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.MemoryCategory
import io.github.thepytales.llmhub.data.MemoryEntry
import io.github.thepytales.llmhub.data.MemoryMode
import io.github.thepytales.llmhub.data.Privacy
import io.github.thepytales.llmhub.data.thread
import io.github.thepytales.llmhub.ui.splitThinking
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The memory about the user (Einstellungen › Gedächtnis): which entries go into a chat, how they are written into
 * the system prompt, and the earlier chats the model may look into (search_chats). The memory goes along with every
 * request – also for models without tools, in the editor assistant and in the model comparison – so nobody has to
 * point the model to it.
 */
object Memory {
    fun mode(c: AppContainer) = MemoryMode.of(c.settings.current.memoryMode)

    /** Entries for a chat in the project [folderId]: general ones and those of the project, without expired ones. */
    fun entries(c: AppContainer, folderId: String?, now: Long = System.currentTimeMillis()): List<MemoryEntry> =
        if (mode(c) == MemoryMode.OFF) emptyList()
        else c.memory.current.filter { !it.expired(now) && (it.projectId == null || it.projectId == folderId) }

    /** Entries that go into [conv] (none when the memory is off for it). */
    fun entriesFor(c: AppContainer, conv: Conversation): List<MemoryEntry> = if (conv.memoryOff) emptyList() else entries(c, conv.folderId)

    /** The block for the system prompt; null without entries. Grouped by category, project entries last. */
    fun block(c: AppContainer, entries: List<MemoryEntry>): String? {
        if (entries.isEmpty()) return null
        val date = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
        fun line(e: MemoryEntry) = "- " + e.text + (e.expiresAt?.let { " (gilt bis ${date.format(Date(it))})" } ?: "")
        return buildString {
            append(
                "Gedächtnis – was du über den Nutzer weißt (er sieht und verwaltet es unter Einstellungen › Gedächtnis). " +
                    "Berücksichtige diese Angaben in jeder Antwort, wo sie passen (Funktion, Lehre, Schreibstil, Kontakte, Vorhaben), " +
                    "ohne sie aufzuzählen oder zu wiederholen. Frag nicht nach, was hier schon steht. Bei Widersprüchen gilt die " +
                    "neuere Angabe im Chat."
            )
            val general = entries.filter { it.projectId == null }.sortedBy { it.createdAt }
            MemoryCategory.entries.forEach { cat ->
                val inCat = general.filter { MemoryCategory.of(it.category) == cat }
                if (inCat.isNotEmpty()) {
                    append("\n").append(cat.label).append(":\n")
                    inCat.forEach { append(line(it)).append('\n') }
                }
            }
            entries.filter { it.projectId != null }.groupBy { it.projectId }.forEach { (id, list) ->
                val name = c.folders.get(id!!)?.name ?: "dieses Projekt"
                append("\nNur für das Projekt „").append(name).append("“:\n")
                list.sortedBy { it.createdAt }.forEach { append(line(it)).append('\n') }
            }
        }.trimEnd()
    }

    /** System prompt part for a chat. */
    fun prompt(c: AppContainer, conv: Conversation): String? = block(c, entriesFor(c, conv))

    /** For helper requests outside chats (editor assistant, comparison, transcripts). */
    fun promptFor(c: AppContainer, folderId: String?): String? = block(c, entries(c, folderId))

    /** Earlier chats may be searched in [conv] (setting „Frühere Chats einbeziehen“). */
    fun chatsAllowed(c: AppContainer, conv: Conversation) =
        mode(c) != MemoryMode.OFF && c.settings.current.memoryChats && !conv.memoryOff && !conv.temporary

    /** When a chat was started (its first message), for a stable order of the hint in the system prompt. */
    private fun started(conv: Conversation) = conv.messages.minOfOrNull { it.createdAt } ?: conv.updatedAt

    /**
     * Earlier chats whose content may go to [model]: chats of a project with a stricter data protection level
     * („Nur TU Dresden“, „Nur auf diesem Gerät“) stay out when the model is not allowed there.
     */
    fun earlierChats(c: AppContainer, conv: Conversation, model: String): List<Conversation> =
        c.chats.conversations.value
            .filter { it.id != conv.id && !it.temporary && it.messages.any { m -> m.role == "user" } && it.taskId == null }
            .filter { Privacy.allows(c, Privacy.level(c, it.folderId), model) }
            .sortedByDescending { started(it) }

    /** Titles of the latest chats for the system prompt, so the model knows when search_chats can help. */
    fun chatsHint(c: AppContainer, conv: Conversation, model: String): String? {
        if (!chatsAllowed(c, conv)) return null
        val recent = earlierChats(c, conv, model).take(8)
        if (recent.isEmpty()) return null
        val date = SimpleDateFormat("dd.MM.", Locale.GERMANY)
        return "Frühere Chats des Nutzers (neueste zuerst): " + recent.joinToString("; ") { "„${it.title}“ (${date.format(Date(started(it)))})" } +
            ". Bezieht sich der Nutzer auf Früheres („wie letztes Mal“, „der Antrag von neulich“), durchsuche sie mit search_chats, " +
            "statt nachzufragen."
    }

    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

    /** Searchable text of a chat: questions and answers without thinking and tool results. */
    private fun turns(conv: Conversation): List<ChatMessage> =
        conv.thread().filter { (it.role == "user" || it.role == "assistant") && !it.error }
            .map { if (it.role == "assistant") it.copy(content = splitThinking(it.content).second) else it }
            .filter { it.content.isNotBlank() }

    /**
     * search_chats: the earlier chats that fit [query] best with short excerpts, or with [chatId] the course of one
     * chat (shortened). Only chats the current model may see (see [earlierChats]).
     */
    fun searchChats(c: AppContainer, conv: Conversation, model: String, query: String, chatId: String?, max: Int = 5): String {
        val allowed = earlierChats(c, conv, model)
        val date = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
        if (!chatId.isNullOrBlank()) {
            val target = allowed.firstOrNull { it.id == chatId.trim() || it.title.equals(chatId.trim(), ignoreCase = true) }
                ?: return "FEHLER: Chat „$chatId“ nicht gefunden (oder in diesem Projekt nicht freigegeben)."
            val full = c.chats.get(target.id) ?: target
            val text = turns(full).joinToString("\n\n") { m -> (if (m.role == "user") "Nutzer: " else "Assistent: ") + m.content.trim() }
            return "Chat „${target.title}“ vom ${date.format(Date(started(target)))}:\n\n" +
                if (text.length > 12_000) text.take(6_000) + "\n\n[…]\n\n" + text.takeLast(5_000) else text
        }
        val terms = norm(query).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.distinct()
        if (terms.isEmpty()) return "FEHLER: Bitte Suchbegriffe angeben."
        data class Hit(val conv: Conversation, val score: Int, val excerpts: List<String>)
        val hits = allowed.mapNotNull { other ->
            val title = norm(other.title)
            var score = terms.count { it in title } * 4
            val excerpts = mutableListOf<String>()
            turns(other).forEach { m ->
                val text = norm(m.content)
                val found = terms.filter { it in text }
                if (found.isNotEmpty()) {
                    score += found.size
                    if (excerpts.size < 3) {
                        val at = text.indexOf(found.first()).coerceAtLeast(0)
                        val from = (at - 160).coerceAtLeast(0)
                        val to = (at + 240).coerceAtMost(m.content.length)
                        val piece = m.content.substring(from.coerceAtMost(m.content.length), to).replace(Regex("\\s+"), " ").trim()
                        excerpts += (if (m.role == "user") "Nutzer: " else "Assistent: ") + (if (from > 0) "…" else "") + piece + (if (to < m.content.length) "…" else "")
                    }
                }
            }
            if (score == 0) null else Hit(other, score, excerpts)
        }.sortedByDescending { it.score }.take(max.coerceIn(1, 10))
        if (hits.isEmpty()) return "Keine früheren Chats zu „$query“ gefunden."
        return hits.joinToString("\n\n") { h ->
            val project = h.conv.folderId?.let { c.folders.get(it)?.name }?.let { " · Projekt $it" }.orEmpty()
            "„${h.conv.title}“ (${date.format(Date(started(h.conv)))}$project, chat_id ${h.conv.id})\n" + h.excerpts.joinToString("\n") { "  $it" }
        } + "\n\nDen ganzen Verlauf eines Chats liefert search_chats mit chat_id."
    }

    /** "2027-03-31", "31.03.2027" or "31.3.27" → end of that day; null if not a date. */
    fun parseDate(text: String?): Long? {
        val t = text?.trim().orEmpty()
        if (t.isEmpty() || t.equals("null", ignoreCase = true)) return null
        val iso = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""").find(t)
        val de = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{2,4})""").find(t)
        val (y, m, d) = when {
            iso != null -> Triple(iso.groupValues[1].toInt(), iso.groupValues[2].toInt(), iso.groupValues[3].toInt())
            de != null -> Triple(de.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }, de.groupValues[2].toInt(), de.groupValues[1].toInt())
            else -> return null
        }
        if (m !in 1..12 || d !in 1..31) return null
        return Calendar.getInstance().apply {
            clear()
            set(y, m - 1, d, 23, 59, 59)
        }.timeInMillis
    }

    /** Same entry already known (ignoring case, punctuation and one containing the other). */
    fun known(c: AppContainer, text: String, projectId: String? = null): Boolean {
        val n = norm(text).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        if (n.isEmpty()) return true
        return c.memory.current.any { e ->
            (e.projectId == null || e.projectId == projectId) && norm(e.text).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().let { o -> o == n || (n.length > 20 && o.contains(n)) || (o.length > 20 && n.contains(o)) }
        }
    }
}
