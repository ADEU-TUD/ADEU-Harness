package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.FieldHistory
import io.github.thepytales.llmhub.data.MemoryMode
import io.github.thepytales.llmhub.data.Template
import io.github.thepytales.llmhub.data.TemplateField
import io.github.thepytales.llmhub.net.JSON
import io.github.thepytales.llmhub.net.ThinkingLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One suggested value for a template field and where it comes from (shown on the chip). */
data class FieldSuggestion(val text: String, val source: String)

/**
 * Suggestions below the fields of a template form (Brief, Präsentation, Seminarplan …). Without a model: earlier
 * entries of fields with the same name, the own sender details and recent recipients. With the model (once per
 * form): what fits from the memory ("Gedächtnis"), the sender details and the knowledge collection – the model only
 * picks from these, it does not invent anything.
 */
object FieldSuggestions {
    private const val HISTORY = 8
    private const val MAX_LENGTH = 600

    /** Fields the model fills in: not the text of a letter (written by the user), not the sender (in the settings). */
    fun wanted(template: Template, field: TemplateField): Boolean =
        !field.isSender && !(template.direct != null && field.multiline && !field.isAddress)

    fun local(c: AppContainer, template: Template, values: Map<String, String>): Map<String, List<FieldSuggestion>> {
        val sender = c.settings.current.senderInfo.trim()
        val contacts = c.contacts.current.sortedByDescending { it.lastUsed }.take(2)
        return template.fields.associate { f ->
            val current = values[f.label].orEmpty().trim()
            val list = buildList {
                if (f.isSender && sender.isNotEmpty()) add(FieldSuggestion(sender, "Absenderangaben"))
                if (f.isAddress) contacts.forEach { add(FieldSuggestion(it.address, "Adressbuch")) }
                c.fieldHistory.get(key(f))?.values.orEmpty().forEach { add(FieldSuggestion(it, "zuletzt")) }
            }
            f.label to list.filter { it.text.trim() != current }.distinctBy { it.text.trim() }.take(4)
        }
    }

    /** Keeps what was sent in the fields, so it is offered again next time. */
    fun remember(c: AppContainer, template: Template, values: Map<String, String>) {
        template.fields.filter { wanted(template, it) && !it.isAddress }.forEach { f ->
            val v = values[f.label].orEmpty().trim()
            if (v.isEmpty() || v.length > MAX_LENGTH) return@forEach
            val old = c.fieldHistory.get(key(f))?.values.orEmpty()
            c.fieldHistory.upsert(FieldHistory(key(f), (listOf(v) + old.filter { it != v }).take(HISTORY)))
        }
    }

    private fun key(f: TemplateField) = f.label.trim().lowercase()

    /** Whether there is anything about the user the model could use. */
    fun hasKnowledge(c: AppContainer): Boolean =
        (MemoryMode.of(c.settings.current.memoryMode) != MemoryMode.OFF && c.memory.current.isNotEmpty()) ||
            c.settings.current.senderInfo.isNotBlank() || c.knowledge.docs.value.any { it.collection.isEmpty() }

    fun prompt(c: AppContainer, template: Template, values: Map<String, String>): List<ChatMessage>? {
        val fields = template.fields.filter { wanted(template, it) && values[it.label].isNullOrBlank() }
        if (fields.isEmpty()) return null
        val s = c.settings.current
        val memory = if (MemoryMode.of(s.memoryMode) != MemoryMode.OFF) c.memory.current.map { it.text } else emptyList()
        val query = listOf(template.name, template.description).plus(fields.map { it.label }).plus(values.values).joinToString(" ")
        val hits = runCatching { c.knowledge.search(query, listOf(""), 4) }.getOrDefault(emptyList())
        if (memory.isEmpty() && hits.isEmpty() && s.senderInfo.isBlank() && s.userName.isBlank()) return null
        val system = "Du füllst Formularfelder einer App der TU Dresden vor – ausschließlich mit Angaben, die unten über den Nutzer " +
            "bekannt sind (Gedächtnis, Absenderangaben, Auszüge aus seiner Wissenssammlung). Erfinde nichts und rate nicht. " +
            "Antworte nur mit einem JSON-Objekt ohne Erklärung: {\"Feldname\": [\"Vorschlag\", …]} mit höchstens 3 Vorschlägen je Feld, " +
            "jeder Vorschlag ist der vollständige Inhalt des Feldes (Zeilen mit \\n getrennt), kurz und passend zum Hinweis des Feldes. " +
            "Lass Felder weg, zu denen die Angaben nichts hergeben. Deutsch, keine Emojis."
        val user = buildString {
            append("Vorlage: ").append(template.name)
            if (template.description.isNotBlank()) append(" – ").append(template.description)
            append("\n\nLeere Felder:\n")
            fields.forEach { f -> append("- ").append(f.label).append(if (f.hint.isNotBlank()) " (${f.hint})" else "").append('\n') }
            val filled = values.filter { (k, v) -> v.isNotBlank() && template.fields.any { it.label == k } }
            if (filled.isNotEmpty()) {
                append("\nSchon ausgefüllt:\n")
                filled.forEach { (k, v) -> append("- ").append(k).append(": ").append(v.trim().replace("\n", " / ").take(300)).append('\n') }
            }
            append("\nWas über den Nutzer bekannt ist:\n")
            if (s.userName.isNotBlank()) append("- Vorname: ").append(s.userName.trim()).append('\n')
            if (s.senderInfo.isNotBlank()) append("- Absenderangaben: ").append(s.senderInfo.trim().replace("\n", " / ")).append('\n')
            memory.forEach { append("- ").append(it).append('\n') }
            if (hits.isNotEmpty()) {
                append("\nAuszüge aus der Wissenssammlung:\n")
                hits.forEach { h -> append("[").append(h.doc.name).append("]\n").append(h.text.take(1500)).append("\n\n") }
            }
        }
        return listOf(ChatMessage(role = "system", content = system), ChatMessage(role = "user", content = user))
    }

    /** Asks the model once for suggestions for all empty fields; empty if nothing about the user fits. */
    suspend fun fromKnowledge(c: AppContainer, template: Template, values: Map<String, String>): Map<String, List<FieldSuggestion>> {
        val messages = withContext(Dispatchers.IO) { prompt(c, template, values) } ?: return emptyMap()
        val result = c.llm.streamChat(model = c.settings.current.defaultModel, messages = messages, tools = null, thinking = ThinkingLevel.OFF) { }
        return parse(FieldAssist.clean(result.content), template)
    }

    fun parse(answer: String, template: Template): Map<String, List<FieldSuggestion>> {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start < 0 || end <= start) return emptyMap()
        val obj = runCatching { JSON.parseToJsonElement(answer.substring(start, end + 1)) as? JsonObject }.getOrNull() ?: return emptyMap()
        return template.fields.mapNotNull { f ->
            val v = obj[f.label] ?: obj.entries.firstOrNull { it.key.equals(f.label, ignoreCase = true) }?.value ?: return@mapNotNull null
            val texts = when (v) {
                is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                is JsonPrimitive -> listOfNotNull(v.contentOrNull)
                else -> emptyList()
            }.map { it.replace("\\n", "\n").trim() }.filter { it.isNotEmpty() && it.length <= MAX_LENGTH }.distinct().take(3)
            if (texts.isEmpty()) null else f.label to texts.map { FieldSuggestion(it, "aus Ihrem Wissen") }
        }.toMap()
    }
}
