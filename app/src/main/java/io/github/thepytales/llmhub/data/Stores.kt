package io.github.thepytales.llmhub.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Where a [ListStore] keeps its JSON text. */
interface TextBackend {
    fun read(): String?
    fun write(text: String)
}

class FileBackend(private val file: File) : TextBackend {
    override fun read(): String? = if (file.exists()) file.readText() else null
    override fun write(text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }
}

/** Stores the JSON in (encrypted) shared preferences – used for data that can contain secrets. */
class PrefsBackend(private val prefs: SharedPreferences, private val key: String) : TextBackend {
    override fun read(): String? = prefs.getString(key, null)
    override fun write(text: String) {
        prefs.edit().putString(key, text).apply()
    }
}

private val storeJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** A small persisted list of items with string ids (folders, templates, tasks …). */
open class ListStore<T>(
    private val backend: TextBackend,
    private val serializer: KSerializer<T>,
    private val keyOf: (T) -> String,
    defaults: () -> List<T> = { emptyList() },
) {
    private val _items = MutableStateFlow(load(defaults))
    val items: StateFlow<List<T>> = _items.asStateFlow()
    val current: List<T> get() = _items.value

    private fun load(defaults: () -> List<T>): List<T> {
        val text = runCatching { backend.read() }.getOrNull() ?: return defaults()
        return runCatching { storeJson.decodeFromString(ListSerializer(serializer), text) }.getOrElse { defaults() }
    }

    fun get(key: String): T? = _items.value.firstOrNull { keyOf(it) == key }

    fun upsert(item: T) = change { list ->
        if (list.any { keyOf(it) == keyOf(item) }) list.map { if (keyOf(it) == keyOf(item)) item else it } else list + item
    }

    fun update(key: String, transform: (T) -> T) = change { list -> list.map { if (keyOf(it) == key) transform(it) else it } }

    fun delete(key: String) = change { list -> list.filterNot { keyOf(it) == key } }

    fun replaceAll(items: List<T>) = change { items }

    @Synchronized
    private fun change(transform: (List<T>) -> List<T>) {
        _items.update(transform)
        runCatching { backend.write(storeJson.encodeToString(ListSerializer(serializer), _items.value)) }
    }
}
