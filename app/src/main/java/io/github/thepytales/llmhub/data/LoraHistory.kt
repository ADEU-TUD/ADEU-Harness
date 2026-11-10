package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Lora-Verlauf mit „Nochmal“ (AP-75): the last wishes sent to Lora (from her panel or Strg+K), newest first – to repeat,
 * change before sending or pin. A pinned wish gets a name and appears in Strg+K under it (the first step to own Lora
 * commands). Only on this device, can be switched off (Einstellungen › Lora), nothing from the Tresor.
 */
object LoraHistory {
    @Serializable
    data class Entry(
        val text: String,
        val at: Long = System.currentTimeMillis(),
        /** Set when pinned: the name it has in Strg+K. */
        val name: String? = null,
    ) {
        val pinned: Boolean get() = name != null
    }

    /** How many unpinned wishes are kept (pinned ones stay until unpinned). */
    const val MAX = 20

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun file(c: AppContainer) = File(c.appContext.filesDir, "lora-verlauf.json")

    private val _version = MutableStateFlow(0)
    /** Changes with every change of the list (for the screens). */
    val version: StateFlow<Int> = _version

    @Synchronized
    fun all(c: AppContainer): List<Entry> =
        runCatching { json.decodeFromString<List<Entry>>(file(c).readText()) }.getOrDefault(emptyList())

    /** The last wishes, newest first (pinned ones included, at their place in time). */
    fun recent(c: AppContainer): List<Entry> = all(c).sortedByDescending { it.at }

    fun pinned(c: AppContainer): List<Entry> = all(c).filter { it.pinned }.sortedBy { it.name!!.lowercase() }

    @Synchronized
    private fun save(c: AppContainer, entries: List<Entry>) {
        runCatching {
            val f = file(c)
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json.encodeToString(entries))
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
        _version.value++
    }

    /** The list after [text] was sent at [at]: to the front, the same wish only once, at most [MAX] unpinned. */
    internal fun add(entries: List<Entry>, text: String, at: Long): List<Entry> {
        val t = text.trim()
        if (t.isEmpty()) return entries
        val old = entries.firstOrNull { same(it.text, t) }
        val rest = entries.filterNot { same(it.text, t) }
        val next = listOf(Entry(t, at, old?.name)) + rest
        val unpinned = next.filter { !it.pinned }.sortedByDescending { it.at }.take(MAX).toSet()
        return next.filter { it.pinned || it in unpinned }
    }

    private fun same(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)

    /** Keeps [text] as the newest wish; nothing when the history is switched off, from the Tresor or too long. */
    @Synchronized
    fun record(c: AppContainer, text: String, vault: Boolean = false): Boolean {
        if (vault || !c.settings.current.loraHistory) return false
        val t = text.trim()
        if (t.isEmpty() || t.length > 500) return false
        save(c, add(all(c), t, System.currentTimeMillis()))
        return true
    }

    /** Pins [text] under [name] (also renames a pinned one). */
    @Synchronized
    fun pin(c: AppContainer, text: String, name: String) {
        val n = name.trim().ifEmpty { text.trim().take(40) }
        val list = all(c)
        save(c, if (list.any { same(it.text, text) }) list.map { if (same(it.text, text)) it.copy(name = n) else it } else list + Entry(text.trim(), name = n))
    }

    @Synchronized
    fun unpin(c: AppContainer, text: String) = save(c, all(c).map { if (same(it.text, text)) it.copy(name = null) else it }.let { l ->
        // An unpinned wish counts like the others again: beyond the last MAX it goes.
        val keep = l.filter { !it.pinned }.sortedByDescending { it.at }.take(MAX).toSet()
        l.filter { it.pinned || it in keep }
    })

    @Synchronized
    fun remove(c: AppContainer, text: String) = save(c, all(c).filterNot { same(it.text, text) })

    /** Deletes the history; pinned commands stay unless [pinnedToo]. */
    @Synchronized
    fun clear(c: AppContainer, pinnedToo: Boolean = false) = save(c, if (pinnedToo) emptyList() else all(c).filter { it.pinned })

    /** Pinned commands whose name or text contains all words of [query] (Strg+K). */
    fun search(c: AppContainer, query: String): List<Entry> =
        match((pinned(c) + KnowledgePacks.installed(c).flatMap { it.commands }).distinctBy { it.name?.lowercase() }, query)

    internal fun match(pinned: List<Entry>, query: String): List<Entry> {
        val words = AppMap.norm(query).split(' ').filter { it.isNotBlank() }
        return pinned.filter { e -> val hay = AppMap.norm(e.name.orEmpty() + " " + e.text); words.all { it in hay } }
    }

    /**
     * Pfeil hoch/runter in an input field: the entry [step] places from [index] (−1 = the own text, 0 = newest); null when
     * there is nothing further.
     */
    fun browse(size: Int, index: Int, up: Boolean): Int? {
        val next = if (up) index + 1 else index - 1
        return next.takeIf { it in -1 until size && it != index }
    }
}
