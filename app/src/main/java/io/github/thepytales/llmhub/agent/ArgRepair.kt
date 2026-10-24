package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.net.JSON
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Tool arguments as models really send them: wrapped in code fences, with raw line breaks inside strings,
 * trailing commas or cut off at the end. They are repaired where that is unambiguous; otherwise the model
 * gets an error that says exactly what to send instead of a generic failure.
 */
object ArgRepair {
    sealed interface Result {
        data class Ok(val args: JsonObject, val repaired: Boolean) : Result
        data class Error(val message: String) : Result
    }

    fun parse(arguments: String): Result {
        val raw = arguments.trim()
        if (raw.isEmpty()) return Result.Ok(JsonObject(emptyMap()), false)
        tryParse(raw)?.let { return Result.Ok(it, false) }
        var t = raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        tryParse(t)?.let { return Result.Ok(it, true) }
        t = escapeControlCharsInStrings(t)
        tryParse(t)?.let { return Result.Ok(it, true) }
        t = t.replace(Regex(",\\s*([}\\]])"), "$1")
        tryParse(t)?.let { return Result.Ok(it, true) }
        closeOpen(t)?.let { closed -> tryParse(closed)?.let { return Result.Ok(it, true) } }
        if ('"' !in t && '\'' in t) tryParse(t.replace('\'', '"'))?.let { return Result.Ok(it, true) }
        // Last resort: the lenient parser also takes unquoted values.
        runCatching { JSON.parseToJsonElement(t).jsonObject }.getOrNull()?.let { return Result.Ok(it, true) }
        val error = runCatching { JSON.parseToJsonElement(raw) }.exceptionOrNull()?.message?.lineSequence()?.firstOrNull().orEmpty()
        return Result.Error("Die Argumente sind kein gültiges JSON-Objekt ($error). Anfang: ${raw.take(120)}")
    }

    private val strict = Json

    private fun tryParse(t: String): JsonObject? = runCatching { strict.parseToJsonElement(t).jsonObject }.getOrNull()

    /** Raw line breaks and tabs inside string literals become \n and \t. */
    internal fun escapeControlCharsInStrings(t: String): String = buildString {
        var inString = false
        var escaped = false
        for (ch in t) {
            when {
                escaped -> { append(ch); escaped = false }
                ch == '\\' -> { append(ch); escaped = inString }
                ch == '"' -> { append(ch); inString = !inString }
                inString && ch == '\n' -> append("\\n")
                inString && ch == '\r' -> {}
                inString && ch == '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }

    /** Closes a string, arrays and objects that a cut-off answer left open. */
    internal fun closeOpen(t: String): String? {
        val stack = ArrayDeque<Char>()
        var inString = false
        var escaped = false
        for (ch in t) {
            if (escaped) { escaped = false; continue }
            when {
                ch == '\\' && inString -> escaped = true
                ch == '"' -> inString = !inString
                inString -> {}
                ch == '{' -> stack.addLast('}')
                ch == '[' -> stack.addLast(']')
                ch == '}' || ch == ']' -> if (stack.removeLastOrNull() != ch) return null
            }
        }
        if (stack.isEmpty() && !inString) return null
        return buildString {
            append(t)
            if (inString) append('"')
            while (stack.isNotEmpty()) append(stack.removeLast())
        }
    }

    /** Missing required parameters of [def], as a message for the model; null if everything is there. */
    fun missing(def: ToolDef, args: JsonObject): String? {
        val absent = def.params.filter { p -> p.required && (args[p.name] == null || args[p.name] is JsonNull) }
        if (absent.isEmpty()) return null
        return "Pflichtangabe fehlt: " + absent.joinToString("; ") { "${it.name} (${it.description})" } +
            ". Rufe ${def.name} erneut mit allen Pflichtangaben auf."
    }

    /** The known tool name closest to [name] (typos like "create_documents"). */
    fun closest(name: String, known: List<String>): String? =
        known.minByOrNull { distance(it.lowercase(), name.lowercase()) }?.takeIf { distance(it.lowercase(), name.lowercase()) <= 3 }

    private fun distance(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]
            d[0] = i
            for (j in 1..b.length) {
                val tmp = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return d[b.length]
    }
}
