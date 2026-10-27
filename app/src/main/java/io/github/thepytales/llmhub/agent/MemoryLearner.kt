package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.MemoryCategory
import io.github.thepytales.llmhub.data.MemoryEntry
import io.github.thepytales.llmhub.data.MemoryMode
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.Privacy
import io.github.thepytales.llmhub.data.PrivacyLevel
import io.github.thepytales.llmhub.data.PrivacyScope
import io.github.thepytales.llmhub.data.thread
import io.github.thepytales.llmhub.logFailure
import io.github.thepytales.llmhub.net.ThinkingLevel
import io.github.thepytales.llmhub.ui.splitThinking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Learns from chats without interrupting them (Einstellungen › Gedächtnis › Nach dem Chat dazulernen): when a chat has
 * been quiet for a moment after an answer, a small model reads the new messages of the user and picks out lasting
 * details (function, courses, style, contacts, projects). „Vorher fragen“ shows them in the chat as suggestions,
 * „Automatisch“ saves them and offers to undo.
 */
class MemoryLearner(private val c: AppContainer) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()

    /** Chat id → details found there, waiting for the user's OK. */
    private val _suggestions = MutableStateFlow<Map<String, List<MemoryEntry>>>(emptyMap())
    val suggestions: StateFlow<Map<String, List<MemoryEntry>>> = _suggestions.asStateFlow()

    /** Chat id → details saved automatically (shown once with „Rückgängig“). */
    private val _saved = MutableStateFlow<Map<String, List<MemoryEntry>>>(emptyMap())
    val saved: StateFlow<Map<String, List<MemoryEntry>>> = _saved.asStateFlow()

    /** Texts the user declined in this session; they are not suggested again. */
    private val declined = ConcurrentHashMap.newKeySet<String>()

    private fun active(conv: Conversation): Boolean {
        val s = c.settings.current
        return s.memoryLearn && MemoryMode.of(s.memoryMode) != MemoryMode.OFF && !conv.memoryOff && !conv.temporary && conv.taskId == null
    }

    /** Called when an answer in [conv] is finished; learning starts once the chat has been quiet for [QUIET_MS]. */
    fun afterAnswer(conv: Conversation) {
        if (!active(conv)) return
        jobs.remove(conv.id)?.cancel()
        jobs[conv.id] = scope.launch {
            delay(QUIET_MS)
            // A new answer started meanwhile: it calls again when it is done.
            if (c.runs.get(conv.id) != null) return@launch
            runCatching { learn(conv.id) }.logFailure("Gedächtnis lernen")
            jobs.remove(conv.id)
        }
    }

    /** Model for learning: small and fast, within the data protection level of the project; null = none allowed. */
    private fun model(conv: Conversation): String? {
        val level = Privacy.level(c, conv.folderId)
        if (level == PrivacyLevel.LOCAL) return c.localLlm.preferredModel()
        val fast = ModelCatalog.FAST_CHAT.takeIf { Privacy.allows(c, level, it) }
        return fast ?: Privacy.suitableModel(c, conv.folderId, c.settings.current.defaultModel)
    }

    internal suspend fun learn(chatId: String) {
        val conv = c.chats.get(chatId) ?: return
        if (!active(conv)) return
        val thread = conv.thread()
        val from = conv.learnedUpTo?.let { id -> thread.indexOfFirst { it.id == id } + 1 } ?: 0
        val fresh = thread.drop(from.coerceAtLeast(0))
        val said = fresh.filter { it.role == "user" && it.template == null }.map { it.content.trim() }.filter { it.length >= 15 }
        val lastId = thread.lastOrNull()?.id ?: return
        if (said.isEmpty()) {
            mark(chatId, lastId)
            return
        }
        val model = model(conv) ?: return
        val existing = Memory.entries(c, conv.folderId).map { it.text }
        val answer = withContext(PrivacyScope(chatId)) {
            c.llm.streamChat(
                model = model,
                messages = prompt(said, fresh, existing, conv.folderId != null),
                tools = null,
                thinking = ThinkingLevel.OFF,
            ) { }
        }
        val found = parse(splitThinking(answer.content).second, conv.folderId)
            .filter { e -> !Memory.known(c, e.text, e.projectId) && e.text.lowercase() !in declined }
            .take(MAX_SUGGESTIONS)
        mark(chatId, lastId)
        if (found.isEmpty()) return
        if (MemoryMode.of(c.settings.current.memoryMode) == MemoryMode.AUTO) {
            found.forEach { c.memory.upsert(it) }
            _saved.update { it + (chatId to found) }
        } else {
            _suggestions.update { it + (chatId to (it[chatId].orEmpty() + found).distinctBy { e -> e.text.lowercase() }) }
        }
    }

    /** Remembers how far the chat was read, so nothing is suggested twice (also after a restart). */
    private fun mark(chatId: String, lastId: String) {
        if (c.runs.edit(chatId) { it.copy(learnedUpTo = lastId) }) return
        c.chats.get(chatId)?.let { conv -> c.chats.save(conv.copy(learnedUpTo = lastId)) }
    }

    fun accept(chatId: String, entries: List<MemoryEntry>) {
        entries.filter { !Memory.known(c, it.text, it.projectId) }.forEach { c.memory.upsert(it) }
        _suggestions.update { it - chatId }
    }

    fun decline(chatId: String) {
        _suggestions.value[chatId].orEmpty().forEach { declined += it.text.lowercase() }
        _suggestions.update { it - chatId }
    }

    /** Takes back what was saved automatically in [chatId]. */
    fun undo(chatId: String) {
        _saved.value[chatId].orEmpty().forEach { e -> c.memory.delete(e.id); declined += e.text.lowercase() }
        _saved.update { it - chatId }
    }

    fun dismissSaved(chatId: String) = _saved.update { it - chatId }

    companion object {
        /** Quiet time after an answer before the chat is read. */
        const val QUIET_MS = 25_000L
        const val MAX_SUGGESTIONS = 3
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun prompt(said: List<String>, fresh: List<ChatMessage>, existing: List<String>, inProject: Boolean): List<ChatMessage> {
            val system = buildString {
                append(
                    "Du pflegst das Gedächtnis eines KI-Assistenten über seinen Nutzer (Lehrende und Forschende an einer Universität). " +
                        "Lies, was der Nutzer geschrieben hat, und nenne nur DAUERHAFTE Angaben über ihn selbst, die in künftigen Chats " +
                        "helfen: Funktion und Einrichtung, Lehrveranstaltungen und Module, Vorlieben für Stil, Ton, Anrede und Format, " +
                        "wiederkehrende Kontakte mit ihrer Rolle, laufende Vorhaben (Anträge, Projekte, Betreuungen) mit Frist.\n" +
                        "Nicht: Inhalte der aktuellen Aufgabe, Einmaliges, Vermutungen, Angaben über andere ohne Bezug zum Nutzer, " +
                        "Sensibles (Gesundheit, Religion, Politik, Passwörter, Finanzen). Lieber nichts als etwas Unsicheres.\n" +
                        "Antworte NUR mit einer JSON-Liste (höchstens $MAX_SUGGESTIONS Einträge, sonst []):\n" +
                        "[{\"text\": \"Kurzer Satz in der dritten Person\", \"category\": \"" +
                        MemoryCategory.entries.joinToString("|") { it.name } + "\""
                )
                if (inProject) append(", \"scope\": \"allgemein|projekt\"")
                append(", \"valid_until\": \"JJJJ-MM-TT oder null\"}]")
                if (existing.isNotEmpty()) {
                    append("\n\nSchon bekannt (nicht wiederholen):\n")
                    existing.take(60).forEach { append("- ").append(it).append('\n') }
                }
            }
            val context = fresh.lastOrNull { it.role == "assistant" && !it.error }?.let { splitThinking(it.content).second.take(600) }
            val user = buildString {
                append("Was der Nutzer geschrieben hat:\n")
                said.takeLast(12).forEach { append("<<<\n").append(it.take(2000)).append("\n>>>\n") }
                if (!context.isNullOrBlank()) append("\n(Zum Verständnis, letzte Antwort des Assistenten gekürzt: ").append(context).append(")")
            }
            return listOf(ChatMessage(role = "system", content = system), ChatMessage(role = "user", content = user))
        }

        /** The JSON list of the model as memory entries (tolerant: text around it, single objects, missing fields). */
        fun parse(answer: String, folderId: String?): List<MemoryEntry> {
            val start = answer.indexOf('[')
            val end = answer.lastIndexOf(']')
            if (start < 0 || end <= start) return emptyList()
            val array = runCatching { json.parseToJsonElement(answer.substring(start, end + 1)) as? JsonArray }.getOrNull() ?: return emptyList()
            return array.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                fun str(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull?.trim()
                val text = str("text")?.takeIf { it.length in 8..300 } ?: return@mapNotNull null
                MemoryEntry(
                    text = text,
                    category = MemoryCategory.of(str("category")).name,
                    projectId = folderId.takeIf { str("scope")?.lowercase()?.startsWith("proj") == true },
                    expiresAt = Memory.parseDate(str("valid_until")),
                    source = "learned",
                )
            }
        }
    }
}
