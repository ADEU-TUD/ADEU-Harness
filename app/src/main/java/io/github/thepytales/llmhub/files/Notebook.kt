package io.github.thepytales.llmhub.files

import io.github.thepytales.llmhub.ui.tr
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Jupyter notebooks (.ipynb): readable as numbered cells with shortened outputs, and single cells can be replaced,
 * inserted or deleted while everything else in the file (metadata, outputs of other cells, key order) stays.
 */
object Notebook {
    private const val OUTPUT_LINES = 8

    private fun parse(json: String): JsonObject = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull()
        ?.takeIf { it["cells"] is JsonArray } ?: throw IllegalArgumentException(tr("Kein gültiges Jupyter-Notebook (JSON mit „cells“)."))

    private fun source(el: JsonElement?): String = when (el) {
        is JsonArray -> el.joinToString("") { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }
        is JsonPrimitive -> el.contentOrNull.orEmpty()
        else -> ""
    }

    private fun language(nb: JsonObject): String =
        (nb["metadata"] as? JsonObject)?.let { m ->
            ((m["language_info"] as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull
                ?: ((m["kernelspec"] as? JsonObject)?.get("language") as? JsonPrimitive)?.contentOrNull
        } ?: "python"

    /** All cells as "## Zelle 3 · code (python)" with their source and shortened outputs. */
    fun text(json: String): String {
        val nb = parse(json)
        val lang = language(nb)
        val cells = nb["cells"] as JsonArray
        return buildString {
            append("Jupyter-Notebook (").append(lang).append(", ").append(cells.size).append(" Zellen)\n\n")
            cells.forEachIndexed { i, el ->
                val cell = el as? JsonObject ?: return@forEachIndexed
                val type = (cell["cell_type"] as? JsonPrimitive)?.contentOrNull ?: "code"
                append("## Zelle ").append(i + 1).append(" · ").append(type)
                if (type == "code") {
                    append(" (").append(lang).append(")")
                    (cell["execution_count"] as? JsonPrimitive)?.intOrNull?.let { append(" [").append(it).append("]") }
                }
                append('\n')
                val src = source(cell["source"]).trimEnd()
                if (type == "code") append("```").append(lang).append('\n').append(src).append("\n```\n") else append(src).append('\n')
                (cell["outputs"] as? JsonArray)?.takeIf { it.isNotEmpty() }?.let { outs ->
                    append("Ausgabe:\n")
                    outs.forEach { append(output(it)) }
                }
                append('\n')
            }
        }.trim()
    }

    private fun output(el: JsonElement): String {
        val o = el as? JsonObject ?: return ""
        val type = (o["output_type"] as? JsonPrimitive)?.contentOrNull
        val data = o["data"] as? JsonObject
        val text = when {
            type == "error" -> "${(o["ename"] as? JsonPrimitive)?.contentOrNull}: ${(o["evalue"] as? JsonPrimitive)?.contentOrNull}"
            o["text"] != null -> source(o["text"])
            data?.get("text/plain") != null -> source(data["text/plain"])
            data?.get("text/markdown") != null -> source(data["text/markdown"])
            else -> ""
        }
        val images = data?.keys?.filter { it.startsWith("image/") }.orEmpty()
        return buildString {
            val lines = text.trimEnd().lines()
            if (text.isNotBlank()) {
                lines.take(OUTPUT_LINES).forEach { append("    ").append(it.take(300)).append('\n') }
                if (lines.size > OUTPUT_LINES) append("    … (").append(lines.size - OUTPUT_LINES).append(" weitere Zeilen)\n")
            }
            if (images.isNotEmpty()) append("    [Bild ausgelassen: ").append(images.joinToString()).append("]\n")
            if (data?.containsKey("text/html") == true && text.isBlank()) append("    [HTML-Ausgabe ausgelassen]\n")
        }
    }

    /** What to do with a cell. */
    enum class Mode { REPLACE, INSERT_BEFORE, INSERT_AFTER, DELETE }

    /**
     * [json] with cell [number] (1-based) replaced by [source], a new cell inserted before/after it (number = count + 1
     * appends) or the cell deleted. [cellType] changes the type ("code", "markdown", "raw") when given.
     */
    fun edit(json: String, number: Int, source: String, mode: Mode = Mode.REPLACE, cellType: String? = null): String {
        val nb = parse(json)
        val cells = (nb["cells"] as JsonArray).toMutableList()
        val max = if (mode == Mode.INSERT_BEFORE || mode == Mode.INSERT_AFTER) cells.size + 1 else cells.size
        if (number < 1 || number > max) throw IllegalArgumentException(tr("Zelle {0} gibt es nicht – das Notebook hat {1} Zellen.", number, cells.size))
        val sourceJson = JsonArray(splitLines(source).map(::JsonPrimitive))
        val withIds = cells.any { (it as? JsonObject)?.containsKey("id") == true }
        when (mode) {
            Mode.DELETE -> cells.removeAt(number - 1)
            Mode.REPLACE -> {
                val old = cells[number - 1] as? JsonObject ?: JsonObject(emptyMap())
                val type = cellType ?: (old["cell_type"] as? JsonPrimitive)?.contentOrNull ?: "code"
                val keepString = old["source"] is JsonPrimitive
                val next = LinkedHashMap<String, JsonElement>(old)
                next["cell_type"] = JsonPrimitive(type)
                next["source"] = if (keepString) JsonPrimitive(source) else sourceJson
                if (type == "code") {
                    if ("outputs" !in next) next["outputs"] = JsonArray(emptyList())
                    if ("execution_count" !in next) next["execution_count"] = JsonNull
                } else {
                    next.remove("outputs"); next.remove("execution_count")
                }
                cells[number - 1] = JsonObject(next)
            }
            Mode.INSERT_BEFORE, Mode.INSERT_AFTER -> {
                val type = cellType ?: "code"
                val cell = LinkedHashMap<String, JsonElement>()
                cell["cell_type"] = JsonPrimitive(type)
                if (type == "code") cell["execution_count"] = JsonNull
                if (withIds) cell["id"] = JsonPrimitive(java.util.UUID.randomUUID().toString().replace("-", "").take(8))
                cell["metadata"] = JsonObject(emptyMap())
                if (type == "code") cell["outputs"] = JsonArray(emptyList())
                cell["source"] = sourceJson
                val at = if (mode == Mode.INSERT_BEFORE) number - 1 else number
                cells.add(at.coerceIn(0, cells.size), JsonObject(cell))
            }
        }
        val next = JsonObject(LinkedHashMap<String, JsonElement>(nb).apply { put("cells", JsonArray(cells)) })
        return format(next, indentOf(json)) + if (json.endsWith("\n")) "\n" else ""
    }

    /** Jupyter stores source as lines that keep their "\n" (except the last). */
    private fun splitLines(s: String): List<String> {
        if (s.isEmpty()) return emptyList()
        val parts = s.split('\n')
        return parts.mapIndexedNotNull { i, p -> if (i < parts.lastIndex) "$p\n" else p.takeIf { it.isNotEmpty() } }
    }

    private fun indentOf(json: String): String =
        json.lineSequence().drop(1).firstOrNull { it.isNotBlank() }?.takeWhile { it == ' ' || it == '\t' }?.ifEmpty { null } ?: " "

    /** JSON like Python's json.dumps(indent=…, ensure_ascii=False) as used by Jupyter. */
    fun format(el: JsonElement, indent: String = " "): String {
        val sb = StringBuilder()
        fun write(e: JsonElement, level: Int) {
            when (e) {
                is JsonObject -> {
                    if (e.isEmpty()) { sb.append("{}"); return }
                    sb.append("{\n")
                    e.entries.forEachIndexed { i, (k, v) ->
                        sb.append(indent.repeat(level + 1)).append(JsonPrimitive(k).toString()).append(": ")
                        write(v, level + 1)
                        if (i < e.size - 1) sb.append(',')
                        sb.append('\n')
                    }
                    sb.append(indent.repeat(level)).append('}')
                }
                is JsonArray -> {
                    if (e.isEmpty()) { sb.append("[]"); return }
                    sb.append("[\n")
                    e.forEachIndexed { i, v ->
                        sb.append(indent.repeat(level + 1))
                        write(v, level + 1)
                        if (i < e.size - 1) sb.append(',')
                        sb.append('\n')
                    }
                    sb.append(indent.repeat(level)).append(']')
                }
                else -> sb.append(e.toString())
            }
        }
        write(el, 0)
        return sb.toString()
    }

    /** Problem with the structure of a notebook, null if it looks valid. */
    fun problem(json: String): String? = runCatching {
        val nb = parse(json)
        if (nb["nbformat"] == null) return "„nbformat“ fehlt."
        (nb["cells"] as JsonArray).forEachIndexed { i, c ->
            val o = c as? JsonObject ?: return "Zelle ${i + 1} ist kein Objekt."
            if (o["cell_type"] == null || o["source"] == null) return "Zelle ${i + 1}: „cell_type“ oder „source“ fehlt."
        }
        null
    }.getOrElse { it.message }
}
