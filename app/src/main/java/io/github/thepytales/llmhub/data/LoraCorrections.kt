package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.AppContainer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * Lora lernt aus Korrekturen (AP-72): when Lora got a wish wrong and the user corrected her – a button under „Meinst du …
 * oder …?“ (AP-73), „nein, ich meinte …“, a tap on the right card, „Rückgängig“ right after an action –, the pair
 * *wish → meant action* is kept **on this device** and wins over the command model the next time. A small list like the
 * own list of the pseudonymization: visible and deletable under Einstellungen › Lora, exportable as a file (reviewed by
 * the user) for the next training of the command model (tools/befehle). Nothing is kept in the Tresor, nothing leaves the
 * device by itself.
 *
 * A pair with [Entry.wrong] says the opposite: this action was undone right after Lora carried it out for this wish – next
 * time she does not carry it out without asking.
 */
object LoraCorrections {
    @Serializable
    data class Entry(
        /** The wish as typed (lower case, single spaces), shown in the list and exported. */
        val wish: String,
        /** What was open ([io.github.thepytales.llmhub.agent.ActionArea.key], "app" = none). */
        val area: String,
        /** The meant action (a label of [io.github.thepytales.llmhub.agent.LoraCommands]). */
        val label: String,
        val at: Long = System.currentTimeMillis(),
        /** How often the pair has been used since. */
        val used: Int = 0,
        /** The action was undone right after Lora carried it out: not this one without asking. */
        val wrong: Boolean = false,
    )

    /** At most this many pairs; the oldest unused go first. */
    const val MAX = 300

    // encodeDefaults: [Entry.at] defaults to the clock – left out, it would read back as another time on every load.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun file(c: AppContainer) = File(c.appContext.filesDir, "lora-korrekturen.json")

    /** Wishes are compared without case, accents, punctuation and extra spaces. */
    fun key(wish: String): String = AppMap.norm(wish).replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** What may be learned at all: a real wish (not a single letter, not a whole text) and a command, never a free request. */
    fun learnable(wish: String, label: String): Boolean {
        val k = key(wish)
        return k.length >= 2 && k.length <= 200 && !label.startsWith("frei.") && label.isNotBlank()
    }

    @Synchronized
    fun all(c: AppContainer): List<Entry> =
        runCatching { json.decodeFromString<List<Entry>>(file(c).readText()) }.getOrDefault(emptyList())

    @Synchronized
    private fun save(c: AppContainer, entries: List<Entry>) {
        runCatching {
            val f = file(c)
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json.encodeToString(entries))
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }

    /** Keeps [entries] at [MAX]: the oldest unused pairs go first. */
    internal fun trim(entries: List<Entry>): List<Entry> =
        if (entries.size <= MAX) entries else entries.sortedWith(compareByDescending<Entry> { it.used > 0 }.thenByDescending { it.at }).take(MAX)

    /**
     * Remembers that [wish] (typed while [area] was open) meant [label]; replaces an older pair for the same wish there.
     * [vault]: a Tresor chat is open – then nothing is kept. Returns whether something was kept.
     */
    @Synchronized
    fun learn(c: AppContainer, wish: String, area: String, label: String, vault: Boolean = false): Boolean {
        if (vault || !c.settings.current.loraLearn || !learnable(wish, label)) return false
        val k = key(wish)
        val rest = all(c).filterNot { key(it.wish) == k && it.area == area && (!it.wrong || it.label == label) }
        save(c, trim(rest + Entry(CommandRecognizer.normalize(wish), area, label)))
        return true
    }

    /** „Rückgängig“ right after Lora carried out [label] for [wish]: next time not without asking. */
    @Synchronized
    fun undone(c: AppContainer, wish: String, area: String, label: String, vault: Boolean = false): Boolean {
        if (vault || !c.settings.current.loraLearn || !learnable(wish, label)) return false
        val k = key(wish)
        val rest = all(c).filterNot { key(it.wish) == k && it.area == area && it.label == label }
        save(c, trim(rest + Entry(CommandRecognizer.normalize(wish), area, label, wrong = true)))
        return true
    }

    /**
     * The action [wish] meant the last time it was corrected, or null. The same wish in the same area first; a place,
     * setting or Lora's own action also counts from another area (an action of a tool only where that tool is open).
     */
    @Synchronized
    fun meant(c: AppContainer, wish: String, area: String): String? {
        if (!c.settings.current.loraLearn) return null
        val k = key(wish)
        if (k.isEmpty()) return null
        val entries = all(c)
        val hit = entries.lastOrNull { !it.wrong && key(it.wish) == k && it.area == area }
            ?: entries.lastOrNull { !it.wrong && key(it.wish) == k && !it.label.startsWith("tu:") }
            ?: return null
        save(c, entries.map { if (it === hit) it.copy(used = it.used + 1) else it })
        return hit.label
    }

    /** Whether [label] was undone right after Lora carried it out for [wish] (then she asks instead). */
    @Synchronized
    fun refused(c: AppContainer, wish: String, area: String, label: String): Boolean {
        if (!c.settings.current.loraLearn) return false
        val k = key(wish)
        return all(c).any { it.wrong && it.label == label && it.area == area && key(it.wish) == k }
    }

    @Synchronized
    fun remove(c: AppContainer, entry: Entry) = save(c, all(c).filterNot { it == entry })

    @Synchronized
    fun clear(c: AppContainer) { file(c).delete() }

    /** The names of the areas in the Messkorpus and the training data (tools/befehle/corpus.py, AREAS). */
    private val CORPUS_AREAS = mapOf("files" to "dateien", "doc" to "dokument", "sheet" to "tabelle", "slides" to "folien")

    /**
     * The pairs as training lines for tools/befehle – one JSON object per line like gen.py writes them
     * (`{"text": …, "bereich": …, "label": …}`); undone ones are left out, they say what was *not* meant.
     */
    fun export(entries: List<Entry>): String = entries.filterNot { it.wrong }.joinToString("") { e ->
        buildJsonObject {
            put("text", e.wish)
            put("bereich", CORPUS_AREAS[e.area] ?: e.area)
            put("label", e.label)
        }.toString() + "\n"
    }
}
