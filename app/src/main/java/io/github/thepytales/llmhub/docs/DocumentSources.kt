package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.net.JSON
import io.github.thepytales.llmhub.net.s
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * Keeps what a document was made of (the arguments of create_document) next to it, so it can be changed
 * later – by the model with edit_document or by the user filling in open placeholders – without writing
 * everything again.
 */
object DocumentSources {
    private fun sourceOf(file: File) = File(File(file.parentFile, ".quellen"), file.name + ".json")

    fun save(file: File, args: JsonObject) {
        runCatching {
            val f = sourceOf(file)
            f.parentFile?.mkdirs()
            f.writeText(args.toString())
        }
    }

    fun load(file: File): JsonObject? =
        runCatching { JSON.parseToJsonElement(sourceOf(file).readText()).jsonObject }.getOrNull()

    fun delete(file: File) {
        sourceOf(file).delete()
    }

    /** All text of a source that can contain placeholders. */
    fun texts(args: JsonObject): List<String> =
        listOf("title", "content", "letter_sender", "letter_recipient").mapNotNull { args.s(it) }

    /** Applies [change] to every text field of [args]. */
    fun mapTexts(args: JsonObject, change: (String) -> String): JsonObject = JsonObject(
        args.mapValues { (k, v) ->
            if (k in setOf("title", "content", "letter_sender", "letter_recipient") && v is JsonPrimitive && v.isString) JsonPrimitive(change(v.content))
            else v
        }
    )
}

/** Gaps like "[Datum]" or "[Betrag]" that the model left for the user to fill in. */
object Placeholders {
    // Not: links [text](url), checkboxes [ ] / [x], callouts [!NOTE], footnotes [1].
    private val GAP = Regex("\\[([^\\[\\]\\n]{2,80})](?!\\()")

    fun find(texts: List<String>): List<String> = texts.flatMap { t ->
        GAP.findAll(t).map { it.groupValues[1].trim() }.filter { inner ->
            !inner.startsWith("!") && !inner.all { it.isDigit() } && inner.lowercase() != "x" && inner.isNotBlank()
        }.toList()
    }.distinct()

    fun find(text: String) = find(listOf(text))

    /** Replaces "[Name]" by the value given for Name; empty values keep the gap. */
    fun fill(text: String, values: Map<String, String>): String = GAP.replace(text) { m ->
        values[m.groupValues[1].trim()]?.trim()?.takeIf { it.isNotEmpty() } ?: m.value
    }
}
