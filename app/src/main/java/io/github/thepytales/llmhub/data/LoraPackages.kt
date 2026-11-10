package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.ui.tr
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Lora-Pakete teilen (AP-83): own commands (pinned, AP-75), own words (AP-78), own TU Vorlagen and house rules (AP-79)
 * as **one file** in the format of the Wissenspakete, to import on another PC – with a preview of what comes in and what
 * would be replaced. Nothing personal goes along: no chats, no memory, no keys, no history beyond the pinned commands.
 * An imported pack appears under „Inhalte zum Nachladen“ and can be removed again (its templates go with it).
 */
object LoraPackages {
    enum class Part { WORDS, COMMANDS, TEMPLATES, HOUSE }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    /** „Lehrstuhl Grundschule 2026“ → „geteilt-lehrstuhl-grundschule-2026“. */
    fun idOf(name: String): String {
        val slug = AppMap.norm(name).replace(' ', '-').replace(Regex("[^a-z0-9-]"), "").trim('-').take(48)
        return "geteilt-" + slug.ifEmpty { "paket" }
    }

    /** The pack of this device with the chosen [parts]. */
    fun build(c: AppContainer, name: String, description: String, parts: Set<Part>): KnowledgePacks.Pack = build(
        name, description, parts,
        words = OwnWords.all(c), commands = LoraHistory.pinned(c), templates = c.templates.current, rules = HouseStyle.rules(c),
    )

    internal fun build(
        name: String, description: String, parts: Set<Part>,
        words: List<Term>, commands: List<LoraHistory.Entry>, templates: List<Template>, rules: List<HouseRule>,
    ): KnowledgePacks.Pack = KnowledgePacks.Pack(
        id = idOf(name), title = name.trim().ifEmpty { tr("Lora-Paket") }, description = description.trim(),
        date = java.time.LocalDate.now().toString(), origin = "geteilt",
        entries = if (Part.WORDS in parts) words.map { it.copy(source = "", date = "") } else emptyList(),
        // Only the pinned commands with their name; the time they were sent stays here.
        commands = if (Part.COMMANDS in parts) commands.filter { it.pinned }.map { LoraHistory.Entry(it.text, at = 0, name = it.name) } else emptyList(),
        templates = if (Part.TEMPLATES in parts) templates else emptyList(),
        houseRules = if (Part.HOUSE in parts) rules else emptyList(),
    )

    fun export(pack: KnowledgePacks.Pack): String = json.encodeToString(pack)

    /** What an import would do: the pack, the lines that come in and the lines that would replace something. */
    data class Preview(val pack: KnowledgePacks.Pack, val added: List<String>, val replaced: List<String>)

    fun preview(c: AppContainer, text: String): Preview = preview(
        read(text), words = Vocabulary.all(c).map { it.first }, commands = LoraHistory.pinned(c) + KnowledgePacks.installed(c).flatMap { it.commands },
        templates = c.templates.current, rules = HouseStyle.all(c),
    )

    /** Reads a shared pack; it always counts as shared (its words need no source). */
    fun read(text: String): KnowledgePacks.Pack {
        val raw = runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<KnowledgePacks.Pack>(text) }
            .getOrElse { throw IllegalArgumentException(tr("Das ist keine Lora-Paket-Datei.")) }
        return KnowledgePacks.parse(json.encodeToString(raw.copy(origin = "geteilt", id = if (raw.id.startsWith("geteilt-")) raw.id else idOf(raw.title))))
    }

    internal fun preview(
        p: KnowledgePacks.Pack, words: List<Term>, commands: List<LoraHistory.Entry>, templates: List<Template>, rules: List<HouseRule>,
    ): Preview {
        val added = mutableListOf<String>()
        val replaced = mutableListOf<String>()
        p.entries.forEach { t ->
            val old = words.firstOrNull { it.term.equals(t.term, true) }
            when {
                old == null -> added += tr("Wort „{0}“", t.term)
                old.explanation != t.explanation || old.synonyms != t.synonyms -> replaced += tr("Wort „{0}“", t.term)
            }
        }
        p.commands.forEach { e ->
            val old = commands.firstOrNull { it.name.equals(e.name, true) }
            when {
                old == null -> added += tr("Befehl „{0}“", e.name.orEmpty())
                !old.text.equals(e.text, true) -> replaced += tr("Befehl „{0}“", e.name.orEmpty())
            }
        }
        p.templates.forEach { t ->
            val old = templates.firstOrNull { it.id == t.id || it.name.equals(t.name, true) }
            when {
                old == null -> added += tr("Vorlage „{0}“", t.name)
                old != t -> replaced += tr("Vorlage „{0}“", t.name)
            }
        }
        p.houseRules.forEach { r ->
            val old = rules.firstOrNull { it.avoid.equals(r.avoid, true) }
            when {
                old == null -> added += tr("Hausstil „{0}“ → „{1}“", r.avoid, r.preferred)
                old != r -> replaced += tr("Hausstil „{0}“ → „{1}“", r.avoid, r.preferred)
            }
        }
        return Preview(p, added, replaced)
    }

    /**
     * Imports [p]: words, commands and house rules take effect as part of the pack; templates go into the own templates
     * (a template with the same name is replaced).
     */
    fun import(c: AppContainer, p: KnowledgePacks.Pack) {
        p.templates.forEach { t ->
            c.templates.current.firstOrNull { it.name.equals(t.name, true) && it.id != t.id }?.let { c.templates.delete(it.id) }
            c.templates.upsert(t)
        }
        KnowledgePacks.install(c, export(p.copy(origin = "geteilt")))
    }

    /** Removes an imported pack and its templates (unless they were changed here since). */
    fun remove(c: AppContainer, id: String) {
        val p = KnowledgePacks.installed(c).firstOrNull { it.id == id } ?: return
        p.templates.forEach { t -> if (c.templates.get(t.id) == t) c.templates.delete(t.id) }
        KnowledgePacks.delete(c, id)
    }
}
