package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.net.JSON
import io.github.thepytales.llmhub.net.s
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

data class Param(val name: String, val description: String, val required: Boolean = true, val type: String = "string")

data class ToolDef(
    val name: String,
    val description: String,
    val params: List<Param>,
    val writes: Boolean = false,
)

/** Result of a tool call: text for the model and optionally a file created for the user. */
data class ToolOutput(val text: String, val file: String? = null)

/** A group of tools the model can call. */
interface ToolProvider {
    val defs: List<ToolDef>

    suspend fun run(name: String, args: JsonObject, ctx: RepoContext): ToolOutput

    fun handles(name: String) = defs.any { it.name == name }
}

const val MAX_TOOL_RESULT = 60_000

/** Executes a tool call and returns the result for the model. Never throws. */
suspend fun ToolProvider.execute(name: String, arguments: String, ctx: RepoContext): ToolOutput = try {
    val args = if (arguments.isBlank()) JsonObject(emptyMap())
    else JSON.parseToJsonElement(arguments).jsonObject
    val out = run(name, args, ctx)
    if (out.text.length > MAX_TOOL_RESULT) out.copy(text = out.text.take(MAX_TOOL_RESULT) + "\n…[gekürzt]") else out
} catch (e: Exception) {
    ToolOutput("FEHLER: ${e.message ?: e.javaClass.simpleName}")
}

/** Tool definitions in OpenAI function-calling format. */
fun List<ToolDef>.toSchema(): JsonArray = buildJsonArray {
    forEach { d ->
        add(buildJsonObject {
            put("type", "function")
            putJsonObject("function") {
                put("name", d.name)
                put("description", d.description)
                putJsonObject("parameters") {
                    put("type", "object")
                    putJsonObject("properties") {
                        d.params.forEach { p ->
                            putJsonObject(p.name) {
                                put("type", p.type)
                                put("description", p.description)
                            }
                        }
                    }
                    putJsonArray("required") { d.params.filter { it.required }.forEach { add(JsonPrimitive(it.name)) } }
                }
            }
        })
    }
}

fun JsonObject.req(key: String): String =
    s(key) ?: throw IllegalArgumentException("Parameter '$key' fehlt")

fun JsonObject.int(key: String): Int? = s(key)?.toDoubleOrNull()?.toInt()
