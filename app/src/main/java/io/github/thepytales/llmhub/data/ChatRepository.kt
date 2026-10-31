package io.github.thepytales.llmhub.data

import android.content.Context
import io.github.thepytales.llmhub.logFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists conversations as one JSON file each in the app's private storage.
 *
 * The list in [conversations] is kept light: attached images (the bulk of a chat, as data URLs) are replaced by
 * [IMAGE_STUB] there, and long texts that only the open chat needs – attached documents (up to 120 000 characters),
 * tool results and thinking – are cut to a short start with [TEXT_STUB] (AP-04), so hundreds of chats do not fill the
 * memory. The list is enough for the side menu, the search over questions and answers and the memory. [get] returns
 * the full chat (from a small cache or the file), and [save] puts stubbed parts back from the stored version, so a
 * light copy can never overwrite them.
 */
class ChatRepository(context: Context) {
    private val dir = File(context.filesDir, "conversations").apply { mkdirs() }
    /** Deleted chats, restorable for [TRASH_DAYS] days ("Rückgängig", Einstellungen › Daten › Papierkorb). */
    private val trash = File(dir, "papierkorb")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    /**
     * Newest version of each chat that still has to be written. The write jobs of [save] may run in any order; each
     * one writes what is newest then, so an older version can never overwrite a newer one on disk.
     */
    private val pending = HashMap<String, Conversation>()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    /** All chats, newest first, with images replaced by [IMAGE_STUB] (see [get] for a full chat). */
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** Full versions of the chats used last (open chat, running answers). */
    private val full = object : LinkedHashMap<String, Conversation>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Conversation>?) = size > FULL_CACHE
    }

    init {
        scope.launch {
            val all = dir.listFiles { f -> f.extension == "json" || f.name.endsWith(".json.tmp") }.orEmpty()
                .map { if (it.name.endsWith(".tmp")) File(dir, it.name.removeSuffix(".tmp")) else it }
                .distinct()
                // Each chat is made light right after reading, so the full texts of all chats are never in memory at once.
                .mapNotNull { f -> readFile(f)?.light() }
            // Chats that were opened but never used are not worth keeping (after a day, so a crash right after
            // creating a chat in a project does not lose its settings).
            val dayAgo = System.currentTimeMillis() - 24 * 3600_000L
            val (empty, used) = all.partition { it.messages.isEmpty() && it.updatedAt < dayAgo }
            empty.forEach { File(dir, "${it.id}.json").delete() }
            _conversations.update { current ->
                // Keep chats saved while loading (e.g. by a background task).
                (current + used.filter { u -> current.none { it.id == u.id } }).sortedByDescending { it.updatedAt }
            }
            _loaded.value = true
            purgeTrash()
        }
    }

    /** Reads a chat file; a broken one is set aside (folder "defekt") instead of being ignored for good. */
    private fun readFile(f: File): Conversation? {
        val text = runCatching { AtomicFiles.read(f) }.logFailure("Chat lesen").getOrNull() ?: return null
        return runCatching { json.decodeFromString(Conversation.serializer(), text).normalized() }
            .getOrElse { e -> AtomicFiles.quarantine(f, e); null }
    }

    suspend fun awaitLoaded() {
        loaded.first { it }
    }

    /**
     * The vault (AP-10): its chats are kept and written there, encrypted – never in [conversations] or the files of
     * this folder, so they stay out of the chat list, the search, the memory and suggestions.
     */
    @Volatile
    var vault: VaultStore? = null

    /** The full chat with all images, or null if there is none with this id. */
    fun get(id: String): Conversation? {
        vault?.get(id)?.let { return it }
        synchronized(full) { full[id] }?.let { return it }
        if (_conversations.value.none { it.id == id }) return null
        synchronized(pending) { pending[id] }?.let { return it }
        val loaded = readFile(File(dir, "$id.json")) ?: return _conversations.value.firstOrNull { it.id == id }?.takeIf { !it.hasStubs() }
        synchronized(full) { full[id] = loaded }
        return loaded
    }

    fun save(conversation: Conversation) {
        if (conversation.vault) {
            vault?.save(conversation)
            return
        }
        val complete = if (conversation.hasStubs()) restoreStubs(conversation) else conversation
        synchronized(full) { full[complete.id] = complete }
        _conversations.update { list ->
            (list.filterNot { it.id == complete.id } + complete.light()).sortedByDescending { it.updatedAt }
        }
        synchronized(pending) { pending[complete.id] = complete }
        scope.launch { writeLock.withLock { write(complete.id) } }
    }

    /** Writes the newest version of [id] if it was not written yet (call with [writeLock]). */
    private fun write(id: String) {
        val c = synchronized(pending) { pending.remove(id) } ?: return
        runCatching { AtomicFiles.write(File(dir, "$id.json"), json.encodeToString(Conversation.serializer(), c)) }
            .logFailure("Chat speichern")
    }

    /** Returns when everything saved so far is on disk. */
    suspend fun awaitWrites() = writeLock.withLock {
        synchronized(pending) { pending.keys.toList() }.forEach(::write)
    }

    /** Puts images and long texts of the stored version back where [c] only has stubs. */
    private fun restoreStubs(c: Conversation): Conversation {
        val stored = (synchronized(full) { full[c.id] } ?: synchronized(pending) { pending[c.id] } ?: readFile(File(dir, "${c.id}.json")))
            ?.messages?.associateBy { it.id }.orEmpty()
        return c.copy(messages = c.messages.map { m ->
            val old = stored[m.id]
            var out = m
            if (m.images.any { it == IMAGE_STUB }) {
                out = out.copy(images = old?.images?.takeIf { it.size == m.images.size } ?: m.images.filter { it != IMAGE_STUB })
            }
            if (m.content.endsWith(TEXT_STUB)) out = out.copy(content = old?.content ?: m.content.removeSuffix(TEXT_STUB))
            if (m.reasoning?.endsWith(TEXT_STUB) == true) out = out.copy(reasoning = old?.reasoning ?: m.reasoning.removeSuffix(TEXT_STUB))
            if (m.files.any { it.text.endsWith(TEXT_STUB) }) {
                out = out.copy(files = m.files.mapIndexed { i, f ->
                    if (!f.text.endsWith(TEXT_STUB)) f else old?.files?.getOrNull(i)?.takeIf { it.name == f.name } ?: f.copy(text = f.text.removeSuffix(TEXT_STUB))
                })
            }
            out
        })
    }

    /** Changes a stored chat without touching its position in the list. */
    fun modify(id: String, transform: (Conversation) -> Conversation) {
        get(id)?.let { save(transform(it)) }
    }

    /**
     * Moves the chat into the trash; [restore] brings it back. Documents created in the chat stay until the trash
     * is emptied.
     */
    fun delete(id: String) {
        vault?.takeIf { it.contains(id) }?.let { v ->
            v.delete(id)
            return
        }
        _conversations.update { list -> list.filterNot { it.id == id } }
        synchronized(full) { full.remove(id) }
        scope.launch {
            writeLock.withLock {
                // A save still waiting goes to the trash with the chat (and cannot bring the file back afterwards).
                write(id)
                val f = File(dir, "$id.json")
                trash.mkdirs()
                if (f.isFile && !f.renameTo(File(trash, "$id.json"))) f.delete()
                File(trash, "$id.json").takeIf { it.isFile }?.setLastModified(System.currentTimeMillis())
            }
        }
    }

    /** Brings a deleted chat back from the trash. */
    fun restore(id: String) {
        scope.launch {
            val restored = writeLock.withLock {
                val t = File(trash, "$id.json")
                val f = File(dir, "$id.json")
                if (t.isFile && t.renameTo(f)) readFile(f) else null
            } ?: return@launch
            _conversations.update { list -> (list.filterNot { it.id == id } + restored.light()).sortedByDescending { it.updatedAt } }
        }
    }

    /** Chats in the trash, newest deletion first (Einstellungen › Daten › Papierkorb). */
    fun trashed(): List<Pair<Conversation, Long>> =
        trash.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { f -> readFile(f)?.let { it.light() to f.lastModified() } }
            .sortedByDescending { it.second }

    /** Deletes a chat from the trash for good, with the documents created in it. */
    fun deleteForever(id: String) {
        val f = File(trash, "$id.json")
        readFile(f)?.messages?.mapNotNull { it.file }?.forEach { File(it).delete() }
        f.delete()
    }

    private fun purgeTrash() {
        val limit = System.currentTimeMillis() - TRASH_DAYS * 24 * 3600_000L
        trash.listFiles { f -> f.extension == "json" && f.lastModified() < limit }.orEmpty()
            .forEach { deleteForever(it.nameWithoutExtension) }
    }

    /** Chats of a deleted folder move back to the main list. */
    fun clearFolder(folderId: String) {
        _conversations.value.filter { it.folderId == folderId }.forEach { save(it.copy(folderId = null)) }
    }

    companion object {
        /** Placeholder for an image in the light list (see class comment). */
        const val IMAGE_STUB = "stub:"
        /** End of a text that was cut in the light list (see class comment). */
        const val TEXT_STUB = "\u2063⟪gekürzt⟫"
        /** Texts up to this length stay whole in the list. */
        internal const val KEEP_CHARS = 3000
        const val TRASH_DAYS = 30
        private const val FULL_CACHE = 8
    }
}

private fun Conversation.hasStubs() = messages.any { m ->
    m.images.any { it == ChatRepository.IMAGE_STUB } || m.content.endsWith(ChatRepository.TEXT_STUB) ||
        m.reasoning?.endsWith(ChatRepository.TEXT_STUB) == true || m.files.any { it.text.endsWith(ChatRepository.TEXT_STUB) }
}

private fun String.cut(keep: Int): String = if (length <= ChatRepository.KEEP_CHARS) this else take(keep) + ChatRepository.TEXT_STUB

/** The chat as kept in the list: images as stubs, long attachments, tool results and thinking cut (see class comment). */
internal fun Conversation.light(): Conversation {
    val heavy = messages.any { m ->
        m.images.isNotEmpty() || m.files.any { it.text.length > ChatRepository.KEEP_CHARS } ||
            m.role == "tool" && m.content.length > ChatRepository.KEEP_CHARS || (m.reasoning?.length ?: 0) > ChatRepository.KEEP_CHARS
    }
    if (!heavy) return this
    return copy(messages = messages.map { m ->
        m.copy(
            images = if (m.images.isEmpty()) m.images else m.images.map { ChatRepository.IMAGE_STUB },
            files = m.files.map { f -> if (f.text.length > ChatRepository.KEEP_CHARS) f.copy(text = f.text.cut(1500)) else f },
            content = if (m.role == "tool") m.content.cut(1500) else m.content,
            reasoning = m.reasoning?.cut(800),
        )
    })
}
