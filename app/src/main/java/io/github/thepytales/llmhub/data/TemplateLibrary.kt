package io.github.thepytales.llmhub.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Templates that come with the app, made for the staff of the Professur für Allgemeine Didaktik und Empirische
 * Unterrichtsforschung: kept in code so every update brings the current versions to everybody. Own templates
 * are stored separately (templates.json) and can be shared as text.
 *
 * Prompt templates show only their fields in the chat; [Template.instructions] tell the model what to do.
 */
object TemplateLibrary {
    /** Id prefix of built-in templates. */
    const val BUILT_IN = "b_"

    /** The templates that come with the app (see BuiltInTemplates.kt). */
    val builtIn: List<Template> get() = BuiltInTemplates.all

    /** Start screen of a new chat while the user has no favourites. */
    private val SUGGESTED = listOf("tud_letter", "mail", "summary", "teach_session", "teach_feedback", "res_abstract").map { BUILT_IN + it }

    fun all(own: List<Template>): List<Template> = builtIn + own

    fun find(id: String, own: List<Template>): Template? = own.firstOrNull { it.id == id } ?: builtIn.firstOrNull { it.id == id }

    /** Favourites in the order they were marked, otherwise a few common templates. */
    fun suggestions(favorites: List<String>, own: List<Template>): List<Template> =
        favorites.mapNotNull { find(it, own) }.ifEmpty { SUGGESTED.mapNotNull { find(it, own) } }.take(8)

    /** Copy of a built-in template that the user can change. */
    fun copyOf(t: Template): Template = t.copy(id = UUID.randomUUID().toString(), name = t.name + " (angepasst)")

    /**
     * Until version 2 the default templates were copied into templates.json. Copies the user did not change
     * (recognized by the hash of their text) are removed; the new built-in versions replace them.
     */
    fun removeLegacyDefaults(store: ListStore<Template>) {
        store.current.filter { it.id.startsWith("tpl_") && it.content.hashCode() in LEGACY }.forEach { store.delete(it.id) }
    }

    private val LEGACY = setOf(
        1241340825, -421716484, 1253114753, 453414931, 430831514, -1689628843, -498660521, 591226954,
    )

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /** Text with [list] for colleagues (mail, chat); [import] reads it back. */
    fun export(list: List<Template>): String =
        "Lora-Vorlagen – in der App unter TU Vorlagen › Importieren einfügen:\n" +
            json.encodeToString(ListSerializer(Template.serializer()), list.map { it.copy(id = "") })

    /** Templates from text made by [export] (or a single template as JSON); they get new ids. */
    fun import(text: String): List<Template> {
        val start = text.indexOfFirst { it == '[' || it == '{' }
        if (start < 0) return emptyList()
        val body = text.substring(start).trim()
        val list = runCatching {
            if (body.startsWith("[")) json.decodeFromString(ListSerializer(Template.serializer()), body)
            else listOf(json.decodeFromString(Template.serializer(), body))
        }.getOrDefault(emptyList())
        return list.filter { it.name.isNotBlank() && it.content.isNotBlank() }.map { it.copy(id = UUID.randomUUID().toString()) }
    }
}
