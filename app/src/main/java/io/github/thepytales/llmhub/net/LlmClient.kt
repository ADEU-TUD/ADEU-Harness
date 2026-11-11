package io.github.thepytales.llmhub.net

import io.github.thepytales.llmhub.data.Privacy
import io.github.thepytales.llmhub.data.ApiProvider
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.ProviderKind
import io.github.thepytales.llmhub.data.SettingsStore
import io.github.thepytales.llmhub.data.ToolCall
import io.github.thepytales.llmhub.data.UsageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
import io.github.thepytales.llmhub.ui.trLater
import io.github.thepytales.llmhub.ui.tr

/** [retryAfter] is the wait in seconds the server asked for (header retry-after, sent with 429). */
class ApiException(val code: Int, message: String, val retryAfter: Long? = null) : IOException(message)

/** How much a reasoning model thinks before answering; AUTO sends nothing and keeps the model's default. */
enum class ThinkingLevel(val label: String, val description: String, val effort: String?) {
    AUTO(trLater("Modellstandard"), trLater("Das Modell entscheidet selbst (wie bisher)."), null),
    OFF(trLater("Aus"), trLater("Kein Gedankengang – am schnellsten."), null),
    LOW(trLater("Niedrig"), trLater("Kurz nachdenken – schnelle Antworten."), "low"),
    MEDIUM(trLater("Mittel"), trLater("Ausgewogen."), "medium"),
    HIGH(trLater("Hoch"), trLater("Gründlich nachdenken – langsamer, für schwierige Aufgaben."), "high"),
    ;

    companion object {
        fun parse(name: String?) = entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: AUTO
    }
}

/**
 * Live update while a chat completion streams in. [progress]: share of the prompt a model on this device has read so far
 * (0..1, AP-116), null once it writes or when the server does not tell.
 */
data class StreamUpdate(val content: String, val reasoning: String, val toolCalls: List<ToolCall>, val progress: Double? = null)

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

/**
 * Client for the OpenAI-compatible endpoint (https://llm.scads.ai/v1) and the further providers of the settings
 * (e.g. Gemini): every request goes to the provider that serves its model.
 */
class LlmClient(private val settings: SettingsStore, private val usage: UsageStore) {
    /** Model id (lowercase) → id of the further provider that serves it; set by ModelsRepository. */
    @Volatile
    var owners: Map<String, String> = emptyMap()

    /** Replaces personal details before a chat request leaves the device (set by the app container). */
    @Volatile
    var privacy: io.github.thepytales.llmhub.data.Pseudonymizer? = null

    /** Cleared when the server rejects stream_options (then usage is estimated). */
    @Volatile
    private var askForUsage = true

    /** Shared by all chats, tasks and helper requests, so together they stay below the key's limit. */
    val limiter = RateLimiter({ settings.current.requestsPerMinute })

    /** Models whose server rejected the reasoning parameters; they get requests without them. */
    private val noThinkingParams = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(2, TimeUnit.MINUTES)
        .build()

    /**
     * For streamed answers: tokens arrive continuously, so 3 minutes without a single byte means the connection hangs
     * (instead of waiting up to 10 minutes as for transcriptions and images).
     */
    private val streamHttp: OkHttpClient = http.newBuilder().readTimeout(3, TimeUnit.MINUTES).build()

    /**
     * Starts the server for a model on this device and returns its endpoint (set by the app container, see
     * local/LocalLlm); called before every request to a local model.
     */
    var localProvider: (suspend (String) -> ApiProvider)? = null
    /** Endpoint of the running local model (127.0.0.1). */
    @Volatile private var local: ApiProvider? = null

    /** Ends the work of the model server on this device at once (AP-116, set by the app container: LocalLlm.abort). */
    var localAbort: (() -> Unit)? = null
    /** What a model on this device may do here (AP-116: thinking, steps, time; LocalLlm.limits). */
    var localLimits: ((String) -> io.github.thepytales.llmhub.local.LocalLimits)? = null
    /** Thermal state of this device (AP-116, Android; LocalLlm.heat). */
    var localHeat: (() -> io.github.thepytales.llmhub.local.Heat)? = null
    /** Hitzeschutz (AP-116): pause before a request on a warm device, at most [coolMaxMs]; check every [heatCheckMs] during one. */
    internal var coolStepMs = 15_000L
    internal var coolMaxMs = 120_000L
    internal var heatCheckMs = 5_000L

    /** Counts [abortLocal]: a request that broke while another one was stopped may try once more. */
    private val localAborts = java.util.concurrent.atomic.AtomicInteger()

    /** Stops the model server's work (Stopp, time limit, heat). */
    fun abortLocal() {
        localAborts.incrementAndGet()
        localAbort?.invoke()
    }

    /** The limits for [model] on this device; [LocalLimits.PC_CPU] when the app container did not set them (tests). */
    fun limitsFor(model: String): io.github.thepytales.llmhub.local.LocalLimits =
        localLimits?.invoke(model) ?: io.github.thepytales.llmhub.local.LocalLimits.PC_CPU

    /** Makes sure the local model is running before a request (no-op for server models). */
    private suspend fun prepare(model: String) {
        if (Privacy.isLocal(model)) local = (localProvider ?: error(tr("Lokale Modelle sind nicht verfügbar."))).invoke(model)
    }

    /** Further provider that serves [model], or null for the main one. */
    fun providerFor(model: String?): ApiProvider? {
        if (model == null) return null
        if (Privacy.isLocal(model)) return local
        val usable = settings.current.providers.filter { it.usable }
        if (usable.isEmpty()) return null
        owners[model.lowercase()]?.let { id -> usable.firstOrNull { it.id == id }?.let { return it } }
        val via = ModelCatalog.info(model).via ?: return null
        return usable.firstOrNull { it.providerKind == via }
    }

    /** Request to the provider of [model] (null: the main provider). */
    private fun request(path: String, model: String?): Request.Builder = request(path, providerFor(model), model)

    private fun request(path: String, provider: ApiProvider?, model: String? = null): Request.Builder {
        val s = settings.current
        val info = model?.let { ModelCatalog.info(it) }
        val via = info?.via
        if (provider == null && info != null && via != null) {
            throw ApiException(
                401,
                tr("Für {0} fehlt der API-Key von {1} (Einstellungen › API-Zugang › Weitere Anbieter).", info.displayName, via.label),
            )
        }
        val (url, key) = if (provider != null) provider.baseUrl to provider.apiKey else s.baseUrl to s.apiKey
        if (key.isBlank()) throw ApiException(401, tr("Kein API-Key hinterlegt. Bitte in den Einstellungen eintragen."))
        return Request.Builder()
            .url(url.trimEnd('/') + path)
            .header("Authorization", "Bearer $key")
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
            400 -> if (body.orEmpty().contains("context", ignoreCase = true) || body.orEmpty().contains("token", ignoreCase = true))
                tr(" (Der Chat ist zu lang für das Modell – einen neuen Chat beginnen oder ein Modell mit mehr Kontext wählen)") else ""
            401 -> tr(" (API-Key ungültig oder abgelaufen? Einstellungen › API-Zugang – im TU-Self-Service lässt sich ein neuer erzeugen)")
            403 -> tr(" (Kein Zugriff auf dieses Modell mit diesem API-Key)")
            404 -> tr(" (Modell oder Adresse nicht gefunden – das Modell wurde vielleicht abgeschaltet, bitte ein anderes wählen)")
            413 -> tr(" (Anfrage zu groß – Anhang verkleinern oder weniger Bilder schicken)")
            429 -> tr(" (Rate-Limit des API-Keys erreicht – zu viele Anfragen pro Minute, bitte kurz warten)")
            502, 503, 504 -> tr(" (Der Uni-Server erreicht das Modell gerade nicht – es ist überlastet, startet neu oder wird gewartet. " +
                "Später erneut versuchen oder ein anderes Modell wählen)")
            else -> ""
        }
        return "HTTP $code$hint: ${parsed ?: body?.take(500) ?: "unbekannter Fehler"}"
    }

    /** Models of the main provider, or of [provider]. */
    suspend fun listModels(provider: ApiProvider? = null): List<String> = withContext(Dispatchers.IO) {
        http.newCall(request("/models", provider).get().build()).execute().use { resp ->
            val body = resp.body?.string()
            if (!resp.isSuccessful) throw ApiException(resp.code, errorMessage(resp.code, body))
            JSON.parseToJsonElement(body ?: "{}").jsonObject["data"]?.jsonArray
                // Gemini lists its models as "models/gemini-…", but expects the name without the prefix.
                ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/") }
                .orEmpty()
        }
    }

    /**
     * Streams a chat completion and calls [onUpdate] with the accumulated state. Temporary server errors
     * (rate limit, 502/503/504, dropped connections) are retried; [onRetry] gets the reason and the wait in seconds.
     */
    suspend fun streamChat(
        model: String,
        messages: List<ChatMessage>,
        tools: JsonArray?,
        onRetry: (reason: String, waitSeconds: Long) -> Unit = { _, _ -> },
        /** Overrides the thinking level of the settings, e.g. OFF for quick helper requests. */
        thinking: ThinkingLevel? = null,
        onUpdate: (StreamUpdate) -> Unit,
    ): ChatResult {
        // Keys of the self-service portal have a low requests-per-minute limit, which agent runs with many
        // tool steps reach quickly. The server answers 429 with retry-after; waiting and retrying is enough.
        // 502/503/504 come from the gateway in front of the models ("upstream connect error … connection
        // termination") while a model restarts or is overloaded – usually gone after a few seconds.
        // With pseudonymization the server only sees placeholders; the answer gets the real details back here,
        // so every caller (chat, tasks, ✨, comparison) works with the real text.
        // Postfach nur mit lokalem Modell (AP-68): mail tool results and mail tools never go to a server.
        io.github.thepytales.llmhub.agent.MailAccess.guard(model, messages, tools)
        // A model on this device: nothing leaves it, so no placeholders, no rate limit and no automatic repetition (AP-116).
        val onDevice = Privacy.isLocal(model)
        if (onDevice) return streamLocal(model, messages, tools, thinking, onRetry, onUpdate)
        val masker = privacy?.takeIf { it.enabled && !onDevice }
        // The chat of this request: with „Platzhalter je Chat“ it has its own numbering.
        val scope = coroutineContext[io.github.thepytales.llmhub.data.PrivacyScope]?.id
        masker?.prepareThorough(messages, scope)
        val sent = masker?.maskMessages(messages, scope) ?: messages
        val update: (StreamUpdate) -> Unit = if (masker == null) onUpdate else { u -> onUpdate(masker.unmask(u, scope)) }
        var attempt = 0
        while (true) {
            try {
                if (!onDevice) limiter.acquire { wait -> onRetry(tr("Kurze Pause, damit das Rate-Limit des API-Keys nicht erreicht wird"), wait) }
                val result = streamChatWithFallbacks(model, sent, tools, thinking, update)
                return masker?.unmask(result, scope) ?: result
            } catch (e: IOException) {
                coroutineContext.ensureActive()
                // Offline or a broken certificate does not fix itself within seconds.
                if (e is java.net.UnknownHostException || e is javax.net.ssl.SSLException) throw e
                val code = (e as? ApiException)?.code ?: 0
                if (code == 429) limiter.onRateLimited()
                val reason = when (code) {
                    0 -> tr("Verbindung zum Server unterbrochen")
                    429 -> tr("Rate-Limit des API-Keys erreicht")
                    in RETRY_CODES -> tr("Server des Modells vorübergehend nicht erreichbar (HTTP {0})", code)
                    else -> throw e
                }
                if (attempt >= RETRIES) throw e
                val fallback = if (code == 429) 15L * (attempt + 1) else SERVER_RETRY_WAITS[attempt]
                val wait = ((e as? ApiException)?.retryAfter ?: fallback).coerceIn(1, MAX_RETRY_WAIT)
                onRetry(reason, wait)
                delay(wait * 1000)
                attempt++
            }
        }
    }

    /**
     * A request to the model on this device (AP-116). Nothing is repeated on its own: a timeout here means „too slow“,
     * not „network gone“. A stopped or timed-out request also ends the server's work ([abortLocal]) – llama-server would
     * otherwise finish reading the prompt. A warm phone first cools down; one that gets hot stops the request.
     */
    private suspend fun streamLocal(
        model: String,
        messages: List<ChatMessage>,
        tools: JsonArray?,
        thinking: ThinkingLevel?,
        onRetry: (reason: String, waitSeconds: Long) -> Unit,
        onUpdate: (StreamUpdate) -> Unit,
    ): ChatResult {
        coolDown(onRetry)
        var again = false
        while (true) {
            val aborts = localAborts.get()
            val hot = java.util.concurrent.atomic.AtomicBoolean(false)
            try {
                return kotlinx.coroutines.coroutineScope {
                    val watch = launch { watchHeat(hot) }
                    try {
                        prepare(model)
                        streamChatWithFallbacks(model, messages, tools, thinking, onUpdate)
                    } finally {
                        watch.cancel()
                    }
                }
            } catch (e: Throwable) {
                // Stopp or the time limit of the answer: the server must not keep computing.
                if (e is kotlinx.coroutines.CancellationException || !coroutineContext.isActive) {
                    abortLocal()
                    // As cancellation, not as the „Canceled“ I/O error of the hung-up call (that would look like „Netz weg“).
                    coroutineContext.ensureActive()
                    throw e
                }
                if (hot.get()) throw io.github.thepytales.llmhub.local.OnDeviceStop(io.github.thepytales.llmhub.local.Heat.stopNote())
                // Another request was stopped and took the server with it: this one once more with a fresh server.
                if (e is IOException && e !is ApiException && !again && localAborts.get() != aborts) {
                    again = true
                    continue
                }
                if (e is java.net.SocketTimeoutException) {
                    abortLocal()
                    val mailbox = messages.any { io.github.thepytales.llmhub.agent.MailAccess.isMailTool(it.toolName) }
                    throw io.github.thepytales.llmhub.local.OnDeviceStop(io.github.thepytales.llmhub.local.LocalLimits.tooSlow(mailbox))
                }
                throw e
            }
        }
    }

    /** Hitzeschutz (AP-116): before a request a warm device pauses (with a note) until it is cool, at most [coolMaxMs]. */
    private suspend fun coolDown(onRetry: (reason: String, waitSeconds: Long) -> Unit) {
        val heat = localHeat ?: return
        var waited = 0L
        while (heat() != io.github.thepytales.llmhub.local.Heat.OK) {
            if (waited >= coolMaxMs) throw io.github.thepytales.llmhub.local.OnDeviceStop(io.github.thepytales.llmhub.local.Heat.stopNote())
            onRetry(io.github.thepytales.llmhub.local.Heat.waitNote(), (coolStepMs / 1000).coerceAtLeast(1))
            delay(coolStepMs)
            waited += coolStepMs
        }
    }

    /** During a request: when the device gets hot, the server stops at once ([hot] tells the request why). */
    private suspend fun watchHeat(hot: java.util.concurrent.atomic.AtomicBoolean) {
        val heat = localHeat ?: return
        while (true) {
            delay(heatCheckMs)
            if (heat() == io.github.thepytales.llmhub.local.Heat.HOT) {
                hot.set(true)
                abortLocal()
                // Ends the request at once (its scope is cancelled), whatever the killed server still sends.
                throw io.github.thepytales.llmhub.local.OnDeviceStop(io.github.thepytales.llmhub.local.Heat.stopNote())
            }
        }
    }

    private suspend fun streamChatWithFallbacks(
        model: String,
        messages: List<ChatMessage>,
        tools: JsonArray?,
        override: ThinkingLevel?,
        onUpdate: (StreamUpdate) -> Unit,
    ): ChatResult {
        val wanted = override ?: ThinkingLevel.parse(settings.current.thinkingLevel)
        val thinking = if (Privacy.isLocal(model)) {
            // AP-116: on a phone never, on a PC without a graphics card not by default – thinking costs minutes there.
            limitsFor(model).thinking(wanted).takeIf { model !in noThinkingParams } ?: ThinkingLevel.AUTO
        } else {
            wanted.takeIf { ModelCatalog.info(model).thinking != false && model !in noThinkingParams } ?: ThinkingLevel.AUTO
        }
        return try {
            streamChatOnce(model, messages, tools, askForUsage, thinking, onUpdate)
        } catch (e: ApiException) {
            val msg = e.message.orEmpty()
            when {
                e.code !in 400..422 -> throw e
                askForUsage && msg.contains("stream_options", ignoreCase = true) -> {
                    askForUsage = false
                    streamChatWithFallbacks(model, messages, tools, override, onUpdate)
                }
                thinking != ThinkingLevel.AUTO && THINKING_PARAM_ERROR.containsMatchIn(msg) -> {
                    // The model's server does not know these parameters: answer with its default thinking.
                    noThinkingParams += model
                    streamChatOnce(model, messages, tools, askForUsage, ThinkingLevel.AUTO, onUpdate)
                }
                else -> throw e
            }
        }
    }

    private suspend fun streamChatOnce(
        model: String,
        messages: List<ChatMessage>,
        tools: JsonArray?,
        withUsage: Boolean,
        thinking: ThinkingLevel,
        onUpdate: (StreamUpdate) -> Unit,
    ): ChatResult = withContext(Dispatchers.IO) {
        val provider = providerFor(model)
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            if (withUsage) putJsonObject("stream_options") { put("include_usage", true) }
            // AP-116: llama-server reports how far it has read the prompt (the long part on a processor).
            if (provider?.id == io.github.thepytales.llmhub.local.LocalLlm.LOCAL_PROVIDER_ID) put("return_progress", true)
            if (thinking != ThinkingLevel.AUTO && provider?.providerKind == ProviderKind.GEMINI) {
                // Gemini only knows reasoning_effort; its newer models cannot switch thinking off, "low" is the least.
                put("reasoning_effort", thinking.effort ?: "low")
            } else if (thinking != ThinkingLevel.AUTO) {
                // vLLM passes chat_template_kwargs to the model's chat template: Qwen and GLM read enable_thinking,
                // DeepSeek reads thinking. reasoning_effort is the OpenAI parameter (gpt-oss and others).
                putJsonObject("chat_template_kwargs") {
                    put("enable_thinking", thinking != ThinkingLevel.OFF)
                    put("thinking", thinking != ThinkingLevel.OFF)
                }
                val effort = thinking.effort ?: "low".takeIf { "gpt-oss" in model.lowercase() }
                effort?.let { put("reasoning_effort", it) }
            }
            put("messages", JsonArray(messages.map { it.toApiJson() }))
            if (tools != null && tools.isNotEmpty()) {
                put("tools", tools)
                put("tool_choice", "auto")
            }
        }
        val call = streamHttp.newCall(
            request("/chat/completions", provider, model)
                .header("Accept", "text/event-stream")
                .post(body.toString().toRequestBody(JSON_TYPE))
                .build()
        )
        // AP-116: hang up as soon as the request is cancelled (Stopp, time limit) – not only once the blocked read returns,
        // which with a model on this device can take minutes.
        @OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
        val handle = coroutineContext[Job]?.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { if (it != null) call.cancel() }
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val wait = resp.header("retry-after")?.trim()?.toLongOrNull()
                    throw ApiException(resp.code, errorMessage(resp.code, resp.body?.string()), wait)
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
                        (chunk["prompt_progress"] as? JsonObject)?.let { pp ->
                            promptProgress(pp)?.takeIf { content.isEmpty() && reasoning.isEmpty() && calls.isEmpty() }?.let { share ->
                                onUpdate(StreamUpdate("", "", emptyList(), share))
                            }
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
                            // Gemini: thought signature that has to go back with the call in the next request.
                            (tc["extra_content"] as? JsonObject)?.let { partial.extra = it }
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
            limiter.acquire()
            fun attempt(withFormat: Boolean): Pair<Int, String?> {
                val body = buildJsonObject {
                    put("model", model)
                    put("prompt", prompt)
                    put("n", 1)
                    put("size", size)
                    if (withFormat) put("response_format", "b64_json")
                }
                http.newCall(request("/images/generations", model).post(body.toString().toRequestBody(JSON_TYPE)).build())
                    .execute().use { return it.code to it.body?.string() }
            }
            var (code, body) = attempt(true)
            if (code in 400..422) {
                val retry = attempt(false)
                code = retry.first; body = retry.second
            }
            if (code !in 200..299) throw ApiException(code, errorMessage(code, body))
            usage.record(model, "image", 0, 0, estimated = false)
            imagesFrom(body)
        }

    /**
     * Reworks a picture (OpenAI-compatible /images/edits): [mask] is a PNG of the same size whose transparent pixels
     * mark what may change. Servers whose model edits from a reference without a mask (FLUX.2) get a second request
     * without it; the prompt then names the places. A server without the endpoint gets a clear message.
     */
    suspend fun editImage(model: String, image: ByteArray, mask: ByteArray?, prompt: String, promptWithoutMask: String, size: String): List<ByteArray> =
        withContext(Dispatchers.IO) {
            limiter.acquire()
            fun attempt(withMask: Boolean, withFormat: Boolean): Pair<Int, String?> {
                val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("model", model)
                    .addFormDataPart("prompt", if (withMask) prompt else promptWithoutMask)
                    .addFormDataPart("n", "1")
                    .addFormDataPart("size", size)
                    .addFormDataPart("image", "image.png", image.toRequestBody("image/png".toMediaType()))
                    .apply {
                        if (withMask && mask != null) addFormDataPart("mask", "mask.png", mask.toRequestBody("image/png".toMediaType()))
                        if (withFormat) addFormDataPart("response_format", "b64_json")
                    }
                    .build()
                http.newCall(request("/images/edits", model).post(body).build()).execute().use { return it.code to it.body?.string() }
            }
            var withMask = mask != null
            var (code, body) = attempt(withMask, true)
            if (code in 400..422 && code != 401 && code != 403 && code != 404) {
                // Some servers reject response_format, others the mask: one step back at a time.
                attempt(withMask, false).let { code = it.first; body = it.second }
                if (code in 400..422 && withMask) {
                    withMask = false
                    attempt(false, false).let { code = it.first; body = it.second }
                }
            }
            if (code == 404 || code == 405 || code == 501) {
                throw ApiException(code, tr("Der Server bietet das Überarbeiten von Bildern (noch) nicht an. Neue Bilder erzeugen geht weiterhin."))
            }
            if (code !in 200..299) throw ApiException(code, errorMessage(code, body))
            usage.record(model, "image", 0, 0, estimated = false)
            imagesFrom(body)
        }

    /** The pictures of an images response (base64 or URL). */
    private fun imagesFrom(body: String?): List<ByteArray> {
        val data = JSON.parseToJsonElement(body ?: "{}").jsonObject["data"]?.jsonArray.orEmpty()
        return data.mapNotNull { el ->
            val obj = el.jsonObject
            obj.str("b64_json")?.let { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }
                ?: obj.str("url")?.let { url ->
                    if (url.startsWith("data:")) {
                        android.util.Base64.decode(url.substringAfter(","), android.util.Base64.DEFAULT)
                    } else {
                        http.newCall(Request.Builder().url(url).build()).execute().use { it.body?.bytes() }
                    }
                }
        }.ifEmpty { throw ApiException(500, tr("Keine Bilddaten in der Antwort")) }
    }

    /** State of the Uni server's models from its public status page. */
    val status = ServerStatus(http) { settings.current.baseUrl }

    /**
     * Orders [documents] by relevance to [query] with a reranker model (vLLM /rerank, Cohere/Jina format).
     * Returns the indices of the documents, most relevant first.
     */
    /** Texts for embeddings and reranking: with placeholders when „Wissensspeicher pseudonymisiert“ is on (search and stored texts alike). */
    private fun forSearch(model: String, text: String): String =
        if (Privacy.isLocal(model)) text else privacy?.maskForSearch(text) ?: text

    suspend fun rerank(model: String, query: String, documents: List<String>): List<Int> = withContext(Dispatchers.IO) {
        limiter.acquire()
        val body = buildJsonObject {
            put("model", model)
            put("query", forSearch(model, query))
            putJsonArray("documents") { documents.forEach { add(JsonPrimitive(forSearch(model, it))) } }
            put("top_n", documents.size)
        }
        http.newCall(request("/rerank", model).post(body.toString().toRequestBody(JSON_TYPE)).build()).execute().use { resp ->
            val text = resp.body?.string()
            if (!resp.isSuccessful) throw ApiException(resp.code, errorMessage(resp.code, text))
            val results = JSON.parseToJsonElement(text ?: "{}").jsonObject["results"]?.jsonArray.orEmpty()
            results.mapNotNull { r ->
                val o = r.jsonObject
                val i = o["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@mapNotNull null
                val score = (o["relevance_score"] ?: o["score"])?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0
                i to score
            }.sortedByDescending { it.second }.map { it.first }.filter { it in documents.indices }
        }
    }

    /** Embedding vectors for [inputs] (semantic search in the knowledge collection). */
    suspend fun embed(model: String, inputs: List<String>): List<FloatArray> = withContext(Dispatchers.IO) {
        limiter.acquire()
        val body = buildJsonObject {
            put("model", model)
            putJsonArray("input") { inputs.forEach { add(JsonPrimitive(forSearch(model, it))) } }
        }
        http.newCall(request("/embeddings", model).post(body.toString().toRequestBody(JSON_TYPE)).build()).execute().use { resp ->
            val text = resp.body?.string()
            if (!resp.isSuccessful) {
                if (resp.code == 429) limiter.onRateLimited()
                throw ApiException(resp.code, errorMessage(resp.code, text))
            }
            val data = JSON.parseToJsonElement(text ?: "{}").jsonObject["data"]?.jsonArray.orEmpty()
            usage.record(model, "embedding", inputs.sumOf { it.length } / 4L, 0, estimated = true)
            data.sortedBy { it.jsonObject.str("index")?.toIntOrNull() ?: 0 }.map { el ->
                el.jsonObject["embedding"]!!.jsonArray.let { arr -> FloatArray(arr.size) { i -> arr[i].jsonPrimitive.content.toFloat() } }
            }
        }
    }

    /** Text to speech; returns audio bytes (usually mp3). */
    suspend fun speech(model: String, voice: String, text: String): ByteArray = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", model)
            put("input", text)
            put("voice", voice)
        }
        http.newCall(request("/audio/speech", model).post(body.toString().toRequestBody(JSON_TYPE)).build())
            .execute().use { resp ->
                if (!resp.isSuccessful) throw ApiException(resp.code, errorMessage(resp.code, resp.body?.string()))
                usage.record(model, "tts", text.length / 4L, 0, estimated = true)
                resp.body!!.bytes()
            }
    }

    /** Speech to text for a recorded audio file; [language] (ISO code) helps Whisper with short pieces. */
    suspend fun transcribe(model: String, file: File, language: String? = null, mime: String = "audio/mp4"): String = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("file", file.name, file.asRequestBody(mime.toMediaType()))
            .apply { if (!language.isNullOrBlank()) addFormDataPart("language", language) }
            .build()
        http.newCall(request("/audio/transcriptions", model).post(body).build()).execute().use { resp ->
            val text = resp.body?.string()
            if (!resp.isSuccessful) {
                if (resp.code == 429) limiter.onRateLimited()
                throw ApiException(resp.code, errorMessage(resp.code, text), resp.header("retry-after")?.toLongOrNull())
            }
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
        var extra: JsonObject? = null
        val name = StringBuilder()
        val arguments = StringBuilder()
    }

    private fun Map<Int, PartialToolCall>.toToolCalls(): List<ToolCall> = entries.map { (i, p) ->
        ToolCall(id = p.id ?: "call_$i", name = p.name.toString(), arguments = p.arguments.toString(), extra = p.extra?.toString())
    }

    companion object {
        /**
         * Share of the prompt read (AP-116) from llama-server's prompt_progress: total, cache (reused from the last
         * request) and processed (including the cache) – (processed − cache) / (total − cache).
         */
        fun promptProgress(pp: JsonObject): Double? {
            val total = pp.str("total")?.toDoubleOrNull() ?: return null
            val processed = pp.str("processed")?.toDoubleOrNull() ?: return null
            val cache = pp.str("cache")?.toDoubleOrNull() ?: 0.0
            if (total <= cache) return null
            return ((processed - cache) / (total - cache)).coerceIn(0.0, 1.0)
        }

        private const val RETRIES = 3
        private const val MAX_RETRY_WAIT = 90L
        private val RETRY_CODES = setOf(502, 503, 504, 520, 521, 522, 523, 524, 529)
        private val SERVER_RETRY_WAITS = listOf(5L, 15L, 30L)
        private val THINKING_PARAM_ERROR = Regex("reasoning|chat_template_kwargs|thinking|extra_forbidden|extra (inputs|fields)", RegexOption.IGNORE_CASE)
    }
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/** User text with the instructions of its template and the content of attached files in front of it. */
fun ChatMessage.textWithFiles(): String = if (files.isEmpty() && instructions.isNullOrBlank()) content else buildString {
    instructions?.takeIf { it.isNotBlank() }?.let {
        append("<vorlage name=\"").append(template.orEmpty()).append("\">\n").append(it.trim()).append("\n</vorlage>\n\n")
    }
    files.forEach { f ->
        append("<datei name=\"").append(f.name).append("\"")
        f.pages?.let { append(" seiten=\"").append(it).append("\"") }
        if (f.path != null) {
            append(" ausfuellbar=\"").append(if (f.formFields > 0) "${f.formFields} Formularfelder" else "per Textposition").append("\"")
        }
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
                            tc.extra?.let { e -> runCatching { JSON.parseToJsonElement(e) }.getOrNull()?.let { put("extra_content", it) } }
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
