package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.AppMap
import io.github.thepytales.llmhub.data.MemoryCategory
import io.github.thepytales.llmhub.data.MemoryEntry
import io.github.thepytales.llmhub.data.MemoryMode
import io.github.thepytales.llmhub.ui.tr
import io.github.thepytales.llmhub.ui.trLater
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * „Was Lora über dich weiß“ (AP-39): preferences per tool in the memory (category Vorlieben). Lora asks before she
 * remembers anything („Soll ich mir merken, dass …?“) and uses what she knows as defaults of her actions (folder
 * structure, export preset). Nothing from the Tresor ends up here. The entries are normal memory entries, so they are
 * synced, shown under Einstellungen › Gedächtnis and go into chats like the rest.
 */
object LoraPrefs {
    /** The tools a preference can belong to, in the order of Einstellungen › Lora › Gedächtnis. */
    val TOOLS: List<Pair<String, String>> = listOf(
        "files" to trLater("Dateien"), "pdf" to trLater("PDF-Werkzeugkasten"), "doc" to trLater("Dokumente"), "sheet" to trLater("Tabellen"),
        "slides" to trLater("Folien"), "video" to trLater("Video"), "literature" to trLater("Literatur"), "chat" to trLater("Chat"),
        "" to trLater("Allgemein"),
    )

    fun toolLabel(key: String?): String = tr(TOOLS.firstOrNull { it.first == key.orEmpty() }?.second ?: TOOLS.last().second)

    private val TOOL_WORDS = listOf(
        "video" to Regex("(video|opal|youtube|720p|1080p|export|untertitel|bauchbinde)"),
        "slides" to Regex("(folie|praesentation|prasentation|powerpoint|deck)"),
        "sheet" to Regex("(tabelle|excel|spalte|zeile|noten)"),
        "literature" to Regex("(literatur|zitierstil|apa|din 1505|harvard|bibliograf|quellen)"),
        "pdf" to Regex("(pdf|schwarz|scan)"),
        "doc" to Regex("(handout|dokument|word|brief|text|zweispaltig|schrift|protokoll)"),
        "files" to Regex("(ordner|hochgeladen|datei|aufraum|sortier|semester|ablage)"),
    )

    /** The tool a sentence is about („Videos für OPAL in 720p“ → video), or "" for a general one. */
    fun guessTool(text: String): String {
        val n = AppMap.norm(text)
        return TOOL_WORDS.firstOrNull { it.second.containsMatchIn(n) }?.first.orEmpty()
    }

    fun all(c: AppContainer): List<MemoryEntry> = c.memory.current.filter { MemoryCategory.of(it.category) == MemoryCategory.PREFERENCES }

    private fun active(c: AppContainer) = MemoryMode.of(c.settings.current.memoryMode) != MemoryMode.OFF

    /** The remembered value of [param] of the action [actionId], if any. */
    fun default(c: AppContainer, actionId: String, param: String): String? =
        if (!active(c)) null else all(c).firstOrNull { it.pref == "$actionId:$param" && !it.expired() }?.value?.takeIf { it.isNotBlank() }

    /**
     * [args] with the remembered defaults for the parameters the user left open; the second value says which were
     * used (shown as „wie gemerkt“).
     */
    fun withDefaults(c: AppContainer, action: ToolAction, args: JsonObject): Pair<JsonObject, List<String>> {
        if (action.remember.isEmpty()) return args to emptyList()
        val used = mutableListOf<String>()
        val map = args.toMutableMap()
        for (p in action.remember) {
            val given = runCatching { args[p]?.jsonPrimitive?.contentOrNull }.getOrNull()
            if (!given.isNullOrBlank()) continue
            default(c, action.id, p)?.let { v -> map[p] = JsonPrimitive(v); used += v }
        }
        return JsonObject(map) to used
    }

    /** The preferences of [area] as one line for a prompt (handout, writing), or null. */
    fun instructions(c: AppContainer, area: ActionArea?): String? {
        if (!active(c)) return null
        val key = area?.key ?: return null
        return all(c).filter { it.tool == key && !it.expired() }.joinToString("; ") { it.text }.ifBlank { null }
    }

    /** All preferences for Lora's own prompt („Vorlieben: …“), or null. */
    fun promptLine(c: AppContainer): String? {
        if (!active(c)) return null
        val list = all(c).filter { !it.expired() }
        if (list.isEmpty()) return null
        return "Vorlieben des Nutzers (nutze sie als Standard, ohne nachzufragen): " + list.joinToString("; ") { "${it.text} (${toolLabel(it.tool)})" }
    }

    /** No offers to remember in the Tresor, with the memory off, or when it is known already. */
    private fun mayOffer(c: AppContainer, text: String): Boolean =
        active(c) && !c.drive.isVaultChat(io.github.thepytales.llmhub.ui.LoraHelper.chatId) && !Memory.known(c, text)

    /**
     * After an action with a choice the user made himself (aufräumen nach Semester, Export für OPAL): the question
     * whether Lora should remember it, or null.
     */
    fun offerFor(c: AppContainer, action: ToolAction, args: JsonObject): LoraStep.Remember? {
        for (p in action.remember) {
            val v = runCatching { args[p]?.jsonPrimitive?.contentOrNull }.getOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: continue
            if (default(c, action.id, p).equals(v, ignoreCase = true)) continue
            val text = sentence(action, p, v)
            if (!mayOffer(c, text)) continue
            return LoraStep.Remember(text, action.area.key, "${action.id}:$p", v)
        }
        return null
    }

    /** A readable sentence for a remembered choice. */
    internal fun sentence(action: ToolAction, param: String, value: String): String = when (action.id) {
        "files.tidy" -> tr("Dateien beim Aufräumen nach {0} ordnen", when (value.lowercase()) { "semester" -> tr("Semester"); "monat" -> tr("Monat"); else -> tr("Dateiart") })
        "video.export" -> tr("Videos mit der Vorgabe „{0}“ exportieren", value)
        else -> tr("{0}: {1} = {2}", tr(action.title), param, value)
    }

    private val REMEMBER = Regex("^(merk dir|merke dir|bitte merk dir|merk (es )?dir|ab jetzt|kunftig|kuenftig|in zukunft|ich mochte immer|ich will immer|immer)\\b[ ,:]*(dass |das )?")

    /** „Merk dir, dass ich Handouts zweispaltig will“ → the question to remember it; null for other wishes. */
    fun fromWish(c: AppContainer, wish: String): LoraReply? {
        val n = AppMap.norm(wish)
        if (!REMEMBER.containsMatchIn(n) || n.length < 12) return null
        val raw = wish.trim().replace(Regex("^(?i)(bitte )?(merk(e)? (es )?dir|ab jetzt|künftig|kuenftig|in zukunft)[ ,:]*(dass |das )?"), "").trim().trimEnd('.', '!')
        if (raw.length < 4) return null
        val text = raw.replaceFirstChar { it.uppercase() }
        if (c.drive.isVaultChat(io.github.thepytales.llmhub.ui.LoraHelper.chatId)) return LoraReply(tr("Im Tresor merke ich mir nichts."))
        if (!active(c)) return LoraReply(tr("Das Gedächtnis ist ausgeschaltet (Einstellungen › Gedächtnis)."))
        if (Memory.known(c, text)) return LoraReply(tr("Das weiß ich schon."))
        return LoraReply(tr("Soll ich mir das merken?"), listOf(LoraStep.Remember(text, guessTool(text))))
    }

    /** Saves a confirmed [step]; a preference for the same default replaces the old one. Returns the entry. */
    fun save(c: AppContainer, step: LoraStep.Remember): MemoryEntry {
        step.pref?.let { key -> all(c).filter { it.pref == key }.forEach { c.memory.delete(it.id) } }
        val e = MemoryEntry(text = step.text, category = MemoryCategory.PREFERENCES.name, source = "lora", tool = step.tool.ifBlank { null }, pref = step.pref, value = step.value)
        c.memory.upsert(e)
        return e
    }

    /** „Alles vergessen“ of Einstellungen › Lora › Gedächtnis: removes every preference, returns them for Rückgängig. */
    fun forgetAll(c: AppContainer): List<MemoryEntry> = all(c).also { list -> list.forEach { c.memory.delete(it.id) } }
}
