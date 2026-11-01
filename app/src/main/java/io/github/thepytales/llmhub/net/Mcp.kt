package io.github.thepytales.llmhub.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import io.github.thepytales.llmhub.ui.tr
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/** A tool a MCP server offers (tools/list). */
data class McpToolSpec(val name: String, val description: String, val inputSchema: JsonObject, val readOnly: Boolean = false)

/** The server asks for a login (HTTP 401); [resourceMetadata] is where its OAuth description lives, if it said so. */
class McpAuthRequired(val resourceMetadata: String?, message: String) : IOException(message)

/** Carries JSON-RPC messages to a MCP server and back. */
interface McpTransport {
    /** Sends [message] (a request with id or a notification) and returns the response of a request (null for notifications). */
    suspend fun send(message: JsonObject, expectResponse: Boolean): JsonObject?
    fun close() {}
}

/**
 * Streamable HTTP (MCP 2025-06-18): every message is a POST to the endpoint; the answer is JSON or a short SSE stream that
 * carries it. The server may hand out a session id (Mcp-Session-Id) that goes along with every further request.
 * [headers] gives the authorization of the moment (token or OAuth access token).
 */
class McpHttpTransport(
    private val http: OkHttpClient,
    private val url: String,
    private val headers: suspend () -> Map<String, String>,
) : McpTransport {
    @Volatile private var session: String? = null
    @Volatile var protocolVersion: String? = null

    override suspend fun send(message: JsonObject, expectResponse: Boolean): JsonObject? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .header("Accept", "application/json, text/event-stream")
            .apply {
                headers().forEach { (k, v) -> header(k, v) }
                session?.let { header("Mcp-Session-Id", it) }
                protocolVersion?.let { header("MCP-Protocol-Version", it) }
            }
            .post(message.toString().toRequestBody(JSON_TYPE))
            .build()
        http.newCall(req).execute().use { resp ->
            resp.header("Mcp-Session-Id")?.let { session = it }
            if (resp.code == 401) {
                val meta = resp.header("WWW-Authenticate")?.let { Regex("resource_metadata=\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
                throw McpAuthRequired(meta, "Der Server verlangt eine Anmeldung (HTTP 401).")
            }
            if (resp.code == 404 && session != null) {
                session = null
                throw IOException(tr("Die Sitzung beim Server ist abgelaufen – bitte noch einmal versuchen."))
            }
            if (!resp.isSuccessful) throw IOException(tr("Der MCP-Server antwortet mit HTTP {0}", resp.code) + (resp.body?.string()?.take(200)?.let { ": $it" } ?: "."))
            if (!expectResponse) return@withContext null
            val id = message["id"]
            val type = resp.header("Content-Type").orEmpty()
            val body = resp.body ?: throw IOException("Leere Antwort des MCP-Servers.")
            if (type.contains("text/event-stream")) {
                // Read events until the one that answers our id.
                val source = body.source()
                val data = StringBuilder()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    when {
                        line.startsWith("data:") -> data.append(line.removePrefix("data:").trimStart()).append('\n')
                        line.isEmpty() && data.isNotEmpty() -> {
                            val o = runCatching { JSON.parseToJsonElement(data.toString()).jsonObject }.getOrNull()
                            data.clear()
                            if (o != null && o["id"] == id && (o.containsKey("result") || o.containsKey("error"))) return@withContext o
                        }
                    }
                }
                if (data.isNotEmpty()) runCatching { JSON.parseToJsonElement(data.toString()).jsonObject }.getOrNull()?.let { if (it["id"] == id) return@withContext it }
                throw IOException(tr("Der MCP-Server hat die Verbindung ohne Antwort beendet."))
            }
            val text = body.string()
            val parsed = JSON.parseToJsonElement(text)
            // A batch answer is an array; take the element with our id.
            when (parsed) {
                is JsonArray -> parsed.map { it.jsonObject }.firstOrNull { it["id"] == id }
                else -> parsed.jsonObject
            } ?: throw IOException("Unerwartete Antwort des MCP-Servers.")
        }
    }

    companion object {
        private val JSON_TYPE = "application/json".toMediaType()
    }
}

/**
 * A connection to one MCP server (AP-23): handshake (initialize), the tool list (tools/list, with paging), tool calls
 * (tools/call). Results come back as text for the model; pictures and other content are named.
 */
class McpSession(private val transport: McpTransport, private val clientVersion: String = "1.0") {
    private val ids = AtomicLong(1)
    private val lock = Mutex()
    @Volatile private var ready = false
    var serverName: String? = null
        private set
    var instructions: String? = null
        private set

    private suspend fun request(method: String, params: JsonObject = JsonObject(emptyMap())): JsonObject {
        val msg = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", ids.getAndIncrement())
            put("method", method)
            put("params", params)
        }
        val resp = transport.send(msg, expectResponse = true) ?: throw IOException("Keine Antwort auf $method.")
        resp["error"]?.let { e ->
            val o = e as? JsonObject
            throw IOException(tr("Der MCP-Server meldet einen Fehler bei {0}: ", method) + (o?.get("message")?.jsonPrimitive?.contentOrNull ?: e.toString()))
        }
        return resp["result"] as? JsonObject ?: JsonObject(emptyMap())
    }

    private suspend fun notify(method: String) {
        transport.send(buildJsonObject { put("jsonrpc", "2.0"); put("method", method) }, expectResponse = false)
    }

    suspend fun initialize() = lock.withLock {
        if (ready) return@withLock
        val result = request("initialize", buildJsonObject {
            put("protocolVersion", PROTOCOL)
            putJsonObject("capabilities") {}
            putJsonObject("clientInfo") { put("name", "Lora"); put("version", clientVersion) }
        })
        (transport as? McpHttpTransport)?.protocolVersion = result["protocolVersion"]?.jsonPrimitive?.contentOrNull ?: PROTOCOL
        serverName = (result["serverInfo"] as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
        instructions = result["instructions"]?.jsonPrimitive?.contentOrNull
        notify("notifications/initialized")
        ready = true
    }

    suspend fun tools(): List<McpToolSpec> {
        initialize()
        val out = mutableListOf<McpToolSpec>()
        var cursor: String? = null
        do {
            val r = request("tools/list", buildJsonObject { cursor?.let { put("cursor", it) } })
            r["tools"]?.jsonArray?.forEach { t ->
                val o = t.jsonObject
                val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val annotations = o["annotations"] as? JsonObject
                out += McpToolSpec(
                    name = name,
                    description = (o["description"]?.jsonPrimitive?.contentOrNull ?: o["title"]?.jsonPrimitive?.contentOrNull).orEmpty(),
                    inputSchema = o["inputSchema"] as? JsonObject ?: JsonObject(mapOf("type" to JsonPrimitive("object"))),
                    readOnly = annotations?.get("readOnlyHint")?.jsonPrimitive?.contentOrNull == "true",
                )
            }
            cursor = r["nextCursor"]?.jsonPrimitive?.contentOrNull
        } while (cursor != null && out.size < 500)
        return out
    }

    /** Calls [tool]; the result as text (an error of the tool starts with „FEHLER:“, so the model sees it). */
    suspend fun call(tool: String, arguments: JsonObject): String {
        initialize()
        val r = request("tools/call", buildJsonObject { put("name", tool); put("arguments", arguments) })
        return resultText(r)
    }

    fun close() = transport.close()

    companion object {
        const val PROTOCOL = "2025-06-18"

        /** Text of a tools/call result: text parts, named pictures and resources, structured content as JSON. */
        fun resultText(r: JsonObject): String {
            val parts = r["content"]?.let { (it as? JsonArray) }.orEmpty().mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                when (o["type"]?.jsonPrimitive?.contentOrNull) {
                    "text" -> o["text"]?.jsonPrimitive?.contentOrNull
                    "image" -> "[Bild (${o["mimeType"]?.jsonPrimitive?.contentOrNull ?: "Bild"}) – im Chat nicht darstellbar]"
                    "audio" -> "[Audio – im Chat nicht abspielbar]"
                    "resource_link" -> "[Link: ${o["name"]?.jsonPrimitive?.contentOrNull.orEmpty()} ${o["uri"]?.jsonPrimitive?.contentOrNull.orEmpty()}]"
                    "resource" -> (o["resource"] as? JsonObject)?.let { res ->
                        res["text"]?.jsonPrimitive?.contentOrNull ?: "[Ressource ${res["uri"]?.jsonPrimitive?.contentOrNull.orEmpty()}]"
                    }
                    else -> null
                }
            }
            val structured = (r["structuredContent"] as? JsonElement)?.takeIf { parts.isEmpty() }?.toString()
            val text = (parts + listOfNotNull(structured)).joinToString("\n\n").ifBlank { "(Das Werkzeug hat nichts zurückgegeben.)" }
            val error = r["isError"]?.jsonPrimitive?.contentOrNull == "true"
            return if (error) "FEHLER: $text" else text
        }
    }
}
