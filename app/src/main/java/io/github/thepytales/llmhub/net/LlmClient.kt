package io.github.thepytales.llmhub.net

import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.SettingsStore
import io.github.thepytales.llmhub.data.ToolCall
import io.github.thepytales.llmhub.data.UsageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class ApiException(val code: Int, message: String) : IOException(message)

/** Live update while a chat completion streams in. */
data class StreamUpdate(val content: String, val reasoning: String, val toolCalls: List<ToolCall>)

/** Token counts reported by the server. */
data class TokenUsage(val prompt: Long, val completion: Long)

data class ChatResult(
    val content: String,
    val reasoning: String,
    val toolCalls: List<ToolCall>,
    val finishReason: String?,
    val usage: TokenUsage? = null,
)

val JSON = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
private val JSON_TYPE = "application/json".toMediaType()

/** Client for the OpenAI-compatible endpoint (https://llm.scads.ai/v1). */
class LlmClient(private val settings: SettingsStore, private val usage: UsageStore) {
    /** Cleared when the server rejects stream_options (then usage is estimated). */
    @Volatile
    private var askForUsage = true

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(2, TimeUnit.MINUTES)
        .build()

    private fun request(path: String): Request.Builder {
        val s = settings.current
        if (!s.hasApiKey) throw ApiException(401, "Kein API-Key hinterlegt. Bitte in den Einstellungen eintragen.")
        return Request.Builder()
            .url(s.baseUrl.trimEnd('/') + path)
            .header("Authorization", "Bearer ${s.apiKey}")
    }

    private fun errorMessage(code: Int, body: String?): String {
        val parsed = runCatching {
            val obj = JSON.parseToJsonElement(body ?: "").jsonObject
            val err = obj["error"]
            when (err) {
                is JsonObject -> err["message"]?.jsonPrimitive?.contentOrNull
                is JsonPrimitive -> err.contentOrNull
                else -> obj["detail"]?.toString()
            }
        }.getOrNull()
        val hint = when (code) {
            401 -> " (API-Key ungültig?)"
            429 -> " (Rate-Limit erreicht, bitte kurz warten)"
            else -> ""
        }
        return "HTTP $code$hint: ${parsed ?: body?.take(500) ?: "unbekannter Fehler"}"
    }

    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        http.newCall(request("/models").get().build()).execute().use { resp ->
            val body = resp.body?.string()
            if (!resp.isSuccessful) throw ApiException(resp.code, errorMessage(resp.code, body))
            JSON.parseToJsonElement(body ?: "{}").jsonObject["data"]?.jsonArray
                ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
                .orEmpty()
        }
    }

    /** Streams a chat completion and calls [onUpdate] with the accumulated state. */
    suspend fun streamChat(
        model: String,
        messages: List<ChatMessage>,
        tools: JsonArray?,
        onUpdate: (StreamUpdate) -> Unit,
    ): ChatResult = try {
        streamChatOnce(model, messages, tools, askForUsage, onUpdate)
    } catch (e: ApiException) {
        if (askForUsage && e.code in 400..422 && e.message.orEmpty().contains("stream_options", ignoreCase = true)) {
            askForUsage = false
            streamChatOnce(model, messages, tools, false, onUpdate)
        } else throw e
    }

    private suspend fun streamChatOnce(
        model: String,
        messages: List<ChatMessage>,
        tools: JsonArray?,
        withUsage: Boolean,
        onUpdate: (StreamUpdate) -> Unit,
    ): ChatResult = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            if (withUsage) putJsonObject("stream_options") { put("include_usage", true) }
            put("messages", JsonArray(messages.map { it.toApiJson() }))
            if (tools != null && tools.isNotEmpty()) {
                put("tools", tools)
                put("tool_choice", "auto")
            }
        }
        val call = http.newCall(
            request("/chat/completions")
                .header("Accept", "text/event-stream")
                .post(body.toString().toRequestBody(JSON_TYPE))
                .build()
        )
        val handle = coroutineContext[Job]?.invokeOnCompletion { if (it != null) call.cancel() }
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw ApiException(resp.code, errorMessage(resp.code, resp.body?.string()))
                }
                val content = StringBuilder()
                val reasoning = StringBuilder()
                val calls = sortedMapOf<Int, PartialToolCall>()
                var finish: String? = null
                var tokens: TokenUsage? = null
                val source = resp.body!!.source()
                var lastEmit = 0L
                try {
                    while (true) {
                        coroutineContext.ensureActive()
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        if (data.isEmpty()) continue
                        val chunk = runCatching { JSON.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
                        chunk["error"]?.let { err ->
                            val msg = (err as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull ?: err.toString()
                            throw ApiException(500, msg)
                        }
                        (chunk["usage"] as? JsonObject)?.let { u ->
                            val p = u.str("prompt_tokens")?.toLongOrNull()
                            val c = u.str("completion_tokens")?.toLongOrNull()
                            if (p != null || c != null) tokens = TokenUsage(p ?: 0, c ?: 0)
                        }
                        val choice = (chunk["choices"] as? JsonArray)?.firstOrNull()?.jsonObject ?: continue
                        choice["finish_reason"]?.let { if (it !is JsonNull) finish = it.jsonPrimitive.contentOrNull }
                        val delta = (choice["delta"] ?: choice["message"]) as? JsonObject ?: continue
                        delta.str("content")?.let { content.append(it) }
                        (delta.str("reasoning_content") ?: delta.str("reasoning"))?.let { reasoning.append(it) }
                        (delta["tool_calls"] as? JsonArray)?.forEachIndexed { i, el ->
                            val tc = el.jsonObject
                            val index = tc["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: i
                            val partial = calls.getOrPut(index) { PartialToolCall() }
                            tc.str("id")?.let { if (it.isNotEmpty()) partial.id = it }
                            (tc["function"] as? JsonObject)?.let { fn ->
                                fn.str("name")?.let { partial.name.append(it) }
                                fn.str("arguments")?.let { partial.arguments.append(it) }
                            }
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 50) {
                            lastEmit = now
                            onUpdate(StreamUpdate(content.toString(), reasoning.toString(), calls.toToolCalls()))
                        }
                    }
                } finally {
                    recordChat(model, messages, content.length + reasoning.length + calls.values.sumOf { it.arguments.length }, tokens)
                }
                val result = ChatResult(content.toString(), reasoning.toString(), calls.toToolCalls(), finish, tokens)
                onUpdate(StreamUpdate(result.content, result.reasoning, result.toolCalls))
                result
            }
        } finally {
            handle?.dispose()
        }
    }

    /** Generates images and returns the raw image bytes. */
    suspend fun generateImage(model: String, prompt: String, size: String): List<ByteArray> =
        withContext(Dispatchers.IO) {
            fun attempt(withFormat: Boolean): Pair<Int, String?> {
                val body = buildJsonObject {
                    put("model", model)
                    put("prompt", prompt)
                    put("n", 1)
                    put("size", size)
                    if (withFormat) put("response_format", "b64_json")
                }
                http.newCall(request("/images/generations").post(body.toString().toRequestBody(JSON_TYPE)).build())
                    .execute().use { return it.code to it.body?.string() }
            }
            var (code, body) = attempt(true)
            if (code in 400..422) {
                val retry = attempt(false)
                code = retry.first; body = retry.second
            }
            if (code !in 200..299) throw ApiException(code, errorMessage(code, body))
            usage.record(model, "image", 0, 0, estimated = false)
            val data = JSON.parseToJsonElement(body ?: "{}").jsonObject["data"]?.jsonArray.orEmpty()
            data.mapNotNull { el ->
                val obj = el.jsonObject
                obj.str("b64_json")?.let { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }
                    ?: obj.str("url")?.let { url ->
                        if (url.startsWith("data:")) {
                            android.util.Base64.decode(url.substringAfter(","), android.util.Base64.DEFAULT)
                        } else {
                            http.newCall(Request.Builder().url(url).build()).execute().use { it.body?.bytes() }
                        }
                    }
            }.ifEmpty { throw ApiException(500, "Keine Bilddaten in der Antwort") }
        }

    /** Text to speech; returns audio bytes (usually mp3). */
    suspend fun speech(model: String, voice: String, text: String): ByteArray = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", model)
            put("input", text)
            put("voice", voice)
        }
        http.newCall(request("/audio/speech").post(body.toString().toRequestBody(JSON_TYPE)).build())
            .execute().use { resp ->
                if (!resp.isSuccessful) throw ApiException(resp.code, errorMessage(resp.code, resp.body?.string()))
                usage.record(model, "tts", text.length / 4L, 0, estimated = true)
                resp.body!!.bytes()
            }
    }

    /** Speech to text for a recorded audio file. */
    suspend fun transcribe(model: String, file: File): String = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("file", file.name, file.asRequestBody("audio/mp4".toMediaType()))
            .build()
        http.newCall(request("/audio/transcriptions").post(body).build()).execute().use { resp ->
            val text = resp.body?.string()
            if (!resp.isSuccessful) throw ApiException(resp.code, errorMessage(resp.code, text))
            val result = JSON.parseToJsonElement(text ?: "{}").jsonObject.str("text")?.trim().orEmpty()
            usage.record(model, "stt", 0, result.length / 4L, estimated = true)
            result
        }
    }

    /** Counts a chat request; without usage data from the server the tokens are estimated (≈ 4 characters per token). */
    private fun recordChat(model: String, messages: List<ChatMessage>, outputChars: Int, tokens: TokenUsage?) {
        if (tokens != null) {
            usage.record(model, "chat", tokens.prompt, tokens.completion, estimated = false)
        } else {
            val inputChars = messages.sumOf { m ->
                m.content.length + m.files.sumOf { it.text.length } + m.toolCalls.sumOf { it.arguments.length }
            }
            val imageTokens = messages.sumOf { it.images.size } * 800L
            usage.record(model, "chat", inputChars / 4L + imageTokens, outputChars / 4L, estimated = true)
        }
    }

    private class PartialToolCall {
        var id: String? = null
        val name = StringBuilder()
        val arguments = StringBuilder()
    }

    private fun Map<Int, PartialToolCall>.toToolCalls(): List<ToolCall> = entries.map { (i, p) ->
        ToolCall(id = p.id ?: "call_$i", name = p.name.toString(), arguments = p.arguments.toString())
    }
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/** User text with the content of attached files in front of it. */
fun ChatMessage.textWithFiles(): String = if (files.isEmpty()) content else buildString {
    files.forEach { f ->
        append("<datei name=\"").append(f.name).append("\"")
        f.pages?.let { append(" seiten=\"").append(it).append("\"") }
        append(">\n").append(f.text.trim())
        if (f.truncated) append("\n[… gekürzt]")
        append("\n</datei>\n\n")
    }
    append(content)
}

/** Converts a stored message into the OpenAI chat message format. */
fun ChatMessage.toApiJson(): JsonElement = buildJsonObject {
    put("role", role)
    when (role) {
        "user" -> if (images.isEmpty()) {
            put("content", textWithFiles())
        } else {
            putJsonArray("content") {
                val text = textWithFiles()
                if (text.isNotBlank()) add(buildJsonObject { put("type", "text"); put("text", text) })
                images.forEach { url ->
                    add(buildJsonObject {
                        put("type", "image_url")
                        putJsonObject("image_url") { put("url", url) }
                    })
                }
            }
        }
        "assistant" -> {
            if (content.isEmpty() && toolCalls.isNotEmpty()) put("content", JsonNull) else put("content", content)
            if (toolCalls.isNotEmpty()) {
                put("tool_calls", buildJsonArray {
                    toolCalls.forEach { tc ->
                        add(buildJsonObject {
                            put("id", tc.id)
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", tc.name)
                                put("arguments", tc.arguments.ifBlank { "{}" })
                            }
                        })
                    }
                })
            }
        }
        "tool" -> {
            put("tool_call_id", toolCallId ?: "")
            toolName?.let { put("name", it) }
            put("content", content)
        }
        else -> put("content", content)
    }
}
