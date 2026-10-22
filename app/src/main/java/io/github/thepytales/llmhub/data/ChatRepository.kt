package io.github.thepytales.llmhub.data

import android.content.Context
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

/** Persists conversations as one JSON file each in the app's private storage. */
class ChatRepository(context: Context) {
    private val dir = File(context.filesDir, "conversations").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        scope.launch {
            val all = dir.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { f ->
                runCatching { json.decodeFromString(Conversation.serializer(), f.readText()).normalized() }.getOrNull()
            }
            // Chats that were opened but never used are not worth keeping.
            val (empty, used) = all.partition { it.messages.isEmpty() }
            empty.forEach { File(dir, "${it.id}.json").delete() }
            _conversations.update { current ->
                // Keep chats saved while loading (e.g. by a background task).
                (current + used.filter { u -> current.none { it.id == u.id } }).sortedByDescending { it.updatedAt }
            }
            _loaded.value = true
        }
    }

    suspend fun awaitLoaded() {
        loaded.first { it }
    }

    fun get(id: String): Conversation? = _conversations.value.firstOrNull { it.id == id }

    fun save(conversation: Conversation) {
        _conversations.update { list ->
            (list.filterNot { it.id == conversation.id } + conversation).sortedByDescending { it.updatedAt }
        }
        scope.launch {
            writeLock.withLock {
                File(dir, "${conversation.id}.json")
                    .writeText(json.encodeToString(Conversation.serializer(), conversation))
            }
        }
    }

    /** Changes a stored chat without touching its position in the list. */
    fun modify(id: String, transform: (Conversation) -> Conversation) {
        get(id)?.let { save(transform(it)) }
    }

    fun delete(id: String) {
        // Documents created by tools in this chat are deleted with it.
        val files = get(id)?.messages?.mapNotNull { it.file }.orEmpty()
        _conversations.update { list -> list.filterNot { it.id == id } }
        scope.launch {
            writeLock.withLock { File(dir, "$id.json").delete() }
            files.forEach { File(it).delete() }
        }
    }

    /** Chats of a deleted folder move back to the main list. */
    fun clearFolder(folderId: String) {
        _conversations.value.filter { it.folderId == folderId }.forEach { save(it.copy(folderId = null)) }
    }
}
