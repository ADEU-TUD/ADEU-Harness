package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.data.AgentEnvironment
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.data.LinkedRepo
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

data class Param(
    val name: String,
    val description: String,
    val required: Boolean = true,
    val type: String = "string",
    /** Full JSON schema of the parameter (arrays of objects, see AskUserTool); replaces [type]. */
    val schema: JsonObject? = null,
)

data class ToolDef(
    val name: String,
    val description: String,
    val params: List<Param>,
    val writes: Boolean = false,
)

/** Result of a tool call: text for the model and optionally a file created for the user. */
data class ToolOutput(val text: String, val file: String? = null)

/** Mutable state the tools of one agent run operate on. */
data class RepoContext(
    /** Linked repositories (owner/name) with their working branch; changed by the GitHub tools. */
    val repos: MutableList<LinkedRepo>,
    /** Environment with variables and network rules. */
    val env: AgentEnvironment? = null,
    /** Images of the latest user message (data URLs), e.g. to save one as logo. */
    val images: List<String> = emptyList(),
    /** Attached PDFs of the chat that were kept for filling in, newest first. */
    val pdfs: List<FileAttachment> = emptyList(),
    /** All files attached in the chat, newest first (e.g. to add one to the knowledge collection). */
    val files: List<FileAttachment> = emptyList(),
    /** Folder (project) of the chat: its knowledge is searched together with the global one. */
    val folderId: String? = null,
    /** Chat the tools run for (documents and comments belong to it). */
    val chatId: String? = null,
    /** Model that calls the tools, for "KI · Modell" in the version history of documents. */
    val model: String? = null,
) {
    /** Thread-safe copy for storing on the chat (reading tools run in parallel). */
    fun linked(): List<LinkedRepo> = synchronized(repos) { repos.toList() }

    /** Adds [repo] or replaces its entry (new branch). */
    fun put(repo: LinkedRepo) {
        synchronized(repos) {
            val i = repos.indexOfFirst { it.name.equals(repo.name, ignoreCase = true) }
            if (i >= 0) repos.set(i, repo) else repos.add(repo)
        }
    }

    fun find(name: String): LinkedRepo? = synchronized(repos) { repos.firstOrNull { it.name.equals(name, ignoreCase = true) } }
}

/** What a write action reaches; decides together with [ApprovalMode] whether the user is asked first. */
enum class Risk {
    /** Commits and branches in the selected repository – traceable and reversible in the Git history. */
    EDIT,
    /** Visible to other people or outside the app: pull requests, issues, comments, writing web requests. */
    PUBLIC,
}

/** When the agent asks before write actions in chats (Einstellungen › Chat & Werkzeuge › Freigaben). */
enum class ApprovalMode(val label: String, val description: String) {
    ASK(
        "Immer fragen",
        "Vor jedem Commit, Branch, Pull Request, Kommentar oder schreibenden Web-Aufruf nachfragen.",
    ),
    EDITS(
        "Code-Änderungen automatisch",
        "Dateien anlegen, ändern, löschen und Branches erstellen ohne Rückfrage – alles bleibt in der Git-Historie " +
            "nachvollziehbar. Gefragt wird nur noch bei Pull Requests, Issues, Kommentaren und schreibenden Web-Aufrufen.",
    ),
    ALL(
        "Alles automatisch",
        "Nie nachfragen – für lange Coding-Sessions. Der Agent führt auch Pull Requests, Kommentare und schreibende " +
            "Web-Aufrufe ohne Rückfrage aus.",
    ),
    ;

    fun asks(risk: Risk) = when (this) {
        ASK -> true
        EDITS -> risk == Risk.PUBLIC
        ALL -> false
    }

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: ASK
    }
}

/** A group of tools the model can call. */
interface ToolProvider {
    val defs: List<ToolDef>

    suspend fun run(name: String, args: JsonObject, ctx: RepoContext): ToolOutput

    fun handles(name: String) = defs.any { it.name == name }

    /** What this call changes, or null if it only reads (then it never needs the user's OK). */
    fun risk(name: String, args: JsonObject): Risk? = null

    /** Like [risk], for providers whose answer depends on the chat (e.g. its project folder). */
    fun risk(name: String, args: JsonObject, ctx: RepoContext): Risk? = risk(name, args)

    /** Whether the call only reads and may run at the same time as other such calls of the same answer. */
    fun parallelSafe(name: String): Boolean = false
}

fun parseArgs(arguments: String): JsonObject =
    (ArgRepair.parse(arguments) as? ArgRepair.Result.Ok)?.args ?: JsonObject(emptyMap())

const val MAX_TOOL_RESULT = 60_000

/**
 * Executes a tool call and returns the result for the model. Never throws. Broken JSON is repaired where
 * possible, missing required parameters are reported precisely so the model can correct the call.
 */
suspend fun ToolProvider.execute(name: String, arguments: String, ctx: RepoContext): ToolOutput = try {
    when (val parsed = ArgRepair.parse(arguments)) {
        is ArgRepair.Result.Error -> ToolOutput("FEHLER: ${parsed.message} Sende die Argumente erneut als gültiges JSON-Objekt.")
        is ArgRepair.Result.Ok -> {
            val missing = defs.firstOrNull { it.name == name }?.let { ArgRepair.missing(it, parsed.args) }
            if (missing != null) ToolOutput("FEHLER: $missing")
            else {
                val out = run(name, parsed.args, ctx)
                if (out.text.length > MAX_TOOL_RESULT) out.copy(text = out.text.take(MAX_TOOL_RESULT) + "\n…[gekürzt]") else out
            }
        }
    }
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
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
                            if (p.schema != null) put(p.name, JsonObject(p.schema + ("description" to JsonPrimitive(p.description))))
                            else putJsonObject(p.name) {
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
