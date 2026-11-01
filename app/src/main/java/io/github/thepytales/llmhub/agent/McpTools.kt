package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.McpPolicy
import io.github.thepytales.llmhub.data.McpServer
import io.github.thepytales.llmhub.data.McpToolInfo
import io.github.thepytales.llmhub.data.Privacy
import io.github.thepytales.llmhub.net.JSON
import io.github.thepytales.llmhub.net.McpAuthRequired
import io.github.thepytales.llmhub.net.McpHttpTransport
import io.github.thepytales.llmhub.net.McpOAuth
import io.github.thepytales.llmhub.net.McpSession
import io.github.thepytales.llmhub.net.McpStdio
import io.github.thepytales.llmhub.net.McpTransport
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * MCP-Client (AP-23): connections to the servers of Einstellungen › Verbindungen and their tools for the model. A tool
 * appears as `mcp_<server>_<tool>`; „Sperren“ hides it, „Fragen“ sends every call through the Freigaben (like other
 * actions that reach outside the app), „Erlauben“ runs it directly. Tools of a server only appear in chats whose privacy
 * level the server keeps (see [McpServer.allowedAt]), never in the vault.
 */
class McpHub(private val c: AppContainer) {
    private val sessions = ConcurrentHashMap<String, McpSession>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    val servers get() = c.mcpServers

    private fun lock(id: String) = locks.getOrPut(id) { Mutex() }

    private fun transport(s: McpServer): McpTransport = if (s.local) {
        val parts = McpStdio.split(s.command)
        if (parts.isEmpty()) throw IOException("Für den lokalen Server fehlt der Befehl.")
        McpStdio.start(parts.first(), parts.drop(1), s.envMap(), null)
    } else {
        if (!s.url.startsWith("https://") && !s.url.startsWith("http://127.0.0.1") && !s.url.startsWith("http://localhost")) {
            throw IOException("Fern-Server nur über https:// (oder auf diesem Gerät über http://127.0.0.1).")
        }
        McpHttpTransport(c.llm.http, s.url.trim()) { authHeaders(s.id) }
    }

    /** The authorization of the moment; refreshes an expired OAuth token first. */
    private suspend fun authHeaders(id: String): Map<String, String> {
        val s = servers.get(id) ?: return emptyMap()
        s.oauth?.let { o ->
            val fresh = if (o.expired()) McpOAuth.refresh(c.llm.http, o)?.also { n -> servers.update(id) { it.copy(oauth = n) } } else o
            return mapOf("Authorization" to "Bearer " + (fresh ?: o).accessToken)
        }
        if (s.token.isBlank()) return emptyMap()
        return if (s.tokenHeader.isBlank()) mapOf("Authorization" to "Bearer ${s.token.trim()}") else mapOf(s.tokenHeader.trim() to s.token.trim())
    }

    private suspend fun session(id: String): McpSession = lock(id).withLock {
        sessions[id]?.let { return@withLock it }
        val s = servers.get(id) ?: throw IOException("Unbekannter MCP-Server.")
        McpSession(transport(s), io.github.thepytales.llmhub.BuildConfig.VERSION_NAME).also {
            try {
                it.initialize()
            } catch (e: Exception) {
                it.close()
                throw e
            }
            sessions[id] = it
        }
    }

    /** Ends the connection (after changes to the server or when it is switched off). */
    fun disconnect(id: String) {
        sessions.remove(id)?.close()
    }

    fun disconnectAll() = sessions.keys.toList().forEach(::disconnect)

    /**
     * Connects to the server and stores its tools. A server that wants a login: [McpAuthRequired] (the settings offer
     * „Anmelden“). Returns the number of tools.
     */
    suspend fun refresh(id: String): Int {
        disconnect(id)
        return try {
            val session = session(id)
            val tools = session.tools()
            servers.update(id) { s ->
                s.copy(
                    tools = tools.map { McpToolInfo(it.name, it.description.take(1500), it.inputSchema.toString(), it.readOnly) },
                    lastError = null, checkedAt = System.currentTimeMillis(), instructions = session.instructions.orEmpty().take(1500),
                )
            }
            tools.size
        } catch (e: Exception) {
            servers.update(id) { it.copy(lastError = e.message ?: e.javaClass.simpleName, checkedAt = System.currentTimeMillis()) }
            disconnect(id)
            throw e
        }
    }

    /** OAuth login in the browser ([openUrl]); afterwards the tools are listed. */
    suspend fun login(id: String, resourceMetadata: String?, openUrl: (String) -> Unit): Int {
        val s = servers.get(id) ?: throw IOException("Unbekannter MCP-Server.")
        val state = McpOAuth.login(c.llm.http, s.url.trim(), resourceMetadata, openUrl, s.clientId.ifBlank { null })
        servers.update(id) { it.copy(oauth = state, lastError = null) }
        return refresh(id)
    }

    /** Calls [tool] of server [id]; reconnects once when the connection broke. */
    suspend fun call(id: String, tool: String, args: JsonObject): String {
        return try {
            session(id).call(tool, args)
        } catch (e: McpAuthRequired) {
            // An expired login: refresh once, else the user has to sign in again.
            val s = servers.get(id)
            val renewed = s?.oauth?.let { McpOAuth.refresh(c.llm.http, it) }
            if (renewed == null) throw IOException("Der MCP-Server „${s?.name}“ verlangt eine neue Anmeldung (Einstellungen › Verbindungen).")
            servers.update(id) { it.copy(oauth = renewed) }
            disconnect(id)
            session(id).call(tool, args)
        } catch (e: IOException) {
            disconnect(id)
            session(id).call(tool, args)
        }
    }

    /** Servers whose tools may appear in [conv] (enabled, privacy level kept, not in the vault). */
    fun usable(conv: Conversation): List<McpServer> {
        if (conv.vault) return emptyList()
        val level = Privacy.level(c, conv)
        return servers.current.filter { s -> s.enabled && s.tools.isNotEmpty() && s.allowedAt(level) && (!s.local || McpStdio.SUPPORTED) }
    }

    fun tools(conv: Conversation): McpTools? = usable(conv).takeIf { it.isNotEmpty() }?.let { McpTools(this, it) }
}

/** The MCP tools of one chat run (the servers usable there, see [McpHub.usable]). */
class McpTools(private val hub: McpHub, servers: List<McpServer>) : ToolProvider {
    private data class Target(val server: McpServer, val tool: McpToolInfo)

    private val targets: Map<String, Target> = buildMap {
        for (s in servers) for (t in s.tools) {
            if (s.policy(t.name) == McpPolicy.BLOCK) continue
            var n = toolName(s.name, t.name)
            var i = 2
            while (containsKey(n)) n = toolName(s.name, t.name, i++)
            put(n, Target(s, t))
        }
    }

    override val defs: List<ToolDef> = targets.map { (name, t) ->
        val schema = runCatching { JSON.parseToJsonElement(t.tool.schema).jsonObject }.getOrNull() ?: JsonObject(emptyMap())
        val props = schema["properties"] as? JsonObject ?: JsonObject(emptyMap())
        val required = (schema["required"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet().orEmpty()
        ToolDef(
            name,
            "[MCP-Server „${t.server.name}“] " + t.tool.description.ifBlank { t.tool.name },
            props.map { (p, s) ->
                val o = s as? JsonObject ?: JsonObject(emptyMap())
                Param(p, o["description"]?.jsonPrimitive?.contentOrNull.orEmpty(), required = p in required, schema = JsonObject(o - "description"))
            },
            writes = !t.tool.readOnly,
        )
    }

    override fun risk(name: String, args: JsonObject): Risk? {
        val t = targets[name] ?: return null
        return if (t.server.policy(t.tool.name) == McpPolicy.ALLOW) null else Risk.PUBLIC
    }

    override fun parallelSafe(name: String) = targets[name]?.let { it.tool.readOnly && it.server.policy(it.tool.name) == McpPolicy.ALLOW } == true

    override suspend fun run(name: String, args: JsonObject, ctx: RepoContext): ToolOutput {
        val t = targets[name] ?: return ToolOutput("FEHLER: Unbekanntes MCP-Werkzeug $name.")
        return ToolOutput(hub.call(t.server.id, t.tool.name, args))
    }

    /** For the confirmation dialog: which server and tool a call goes to. */
    fun describe(name: String): String? = targets[name]?.let { "${it.tool.name} (MCP-Server „${it.server.name}“)" }

    companion object {
        /** `mcp_<server>_<tool>` with the characters function names allow, at most 64 long. */
        fun toolName(server: String, tool: String, n: Int = 1): String {
            fun clean(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
                .replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').lowercase()
            val suffix = if (n > 1) "_$n" else ""
            val srv = clean(server).take(20).ifEmpty { "server" }
            return ("mcp_${srv}_" + clean(tool).ifEmpty { "tool" }).take(64 - suffix.length) + suffix
        }

        const val PROMPT = "Werkzeuge mit „mcp_“ stammen von MCP-Servern, die der Nutzer verbunden hat (fremde Dienste). Nutze sie " +
            "nur, wenn sie zur Anfrage passen, und sag dem Nutzer, welchen Dienst du verwendet hast."
    }
}
