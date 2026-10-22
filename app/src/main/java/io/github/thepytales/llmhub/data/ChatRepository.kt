package io.github.thepytales.llmhub.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        scope.launch {
            val loaded = dir.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { f ->
                runCatching { json.decodeFromString(Conversation.serializer(), f.readText()) }.getOrNull()
            }
            _conversations.value = loaded.sortedByDescending { it.updatedAt }
        }
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

    fun delete(id: String) {
        _conversations.update { list -> list.filterNot { it.id == id } }
        scope.launch { writeLock.withLock { File(dir, "$id.json").delete() } }
    }
}
