package io.github.thepytales.llmhub.local

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.ui.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Sprachmodell-Empfehlung nach Messung (AP-76): how fast the installed models really are on this device – words per
 * second and time to the first word, with the graphics card and (if there is one) without –, measured once on request
 * (about half a minute per model). From the numbers: a recommendation for the Postfach, short tasks in the mixed mode
 * and confidential projects, and the mixed mode only takes models that reach [SHORT_MIN].
 */
object LocalSpeed {
    @Serializable
    data class Result(
        val modelId: String,
        /** Words per second while writing (after the first word). */
        val wordsPerSec: Double,
        /** Time from sending to the first word, in ms (reading the request, warming up). */
        val firstWordMs: Long,
        /** Measured with the graphics card. */
        val gpu: Boolean,
        /** The same without the graphics card (only measured when there is one). */
        val cpuWordsPerSec: Double? = null,
        val cpuFirstWordMs: Long? = null,
        val at: Long = System.currentTimeMillis(),
        /** The device as [Hardware.describe] said it (a measurement from another PC – synced data folder – does not count). */
        val hardware: String = "",
    )

    /** Short tasks in the mixed mode (a few sentences): below this the server answers sooner. */
    const val SHORT_MIN = 6.0
    /** Long mails and summaries in the Postfach (300 words in under half a minute). */
    const val MAIL_MIN = 12.0
    /** Confidential projects: slower is fine, the data stay on the device. */
    const val CONFIDENTIAL_MIN = 3.0
    /** The first word may take this long for short tasks. */
    const val FIRST_WORD_MAX_MS = 6_000L

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun file(c: AppContainer) = File(c.appContext.filesDir, "lokale-modelle-messung.json")

    private val _results = MutableStateFlow<Map<String, Result>?>(null)

    /** The measurements of this device by model id (loaded on first use). */
    fun results(c: AppContainer): StateFlow<Map<String, Result>?> {
        if (_results.value == null) _results.value = load(c)
        return _results
    }

    private fun load(c: AppContainer): Map<String, Result> =
        runCatching { json.decodeFromString<List<Result>>(file(c).readText()) }.getOrDefault(emptyList()).associateBy { it.modelId }

    /** The measurement of [modelId] on this device, if it was measured here. */
    fun of(c: AppContainer, modelId: String, hw: Hardware? = c.localLlm.hardware.value): Result? =
        results(c).value?.get(modelId)?.takeIf { hw == null || it.hardware.isEmpty() || it.hardware == hw.describe() }

    @Synchronized
    private fun save(c: AppContainer, r: Result) {
        val next = (results(c).value.orEmpty() + (r.modelId to r))
        runCatching {
            val f = file(c)
            f.parentFile?.mkdirs()
            f.writeText(json.encodeToString(next.values.toList()))
        }
        _results.value = next
    }

    fun forget(c: AppContainer) {
        runCatching { file(c).delete() }
        _results.value = emptyMap()
    }

    // ── Measurement ─────────────────────────────────────────────────────────────────────────────────────

    /** A request every model can answer in about 120 words; the same for all, so the numbers compare. */
    private const val PROMPT = "Schreibe eine kurze, freundliche Mail an das Kollegium: Die Sitzung am Donnerstag beginnt eine halbe " +
        "Stunde später, um 14:30 Uhr, im gleichen Raum. Bitte um kurze Rückmeldung, wer nicht kommen kann. Etwa 120 Wörter."
    private const val MAX_TOKENS = 180
    private const val RUN_TIMEOUT_S = 45L

    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(RUN_TIMEOUT_S, TimeUnit.SECONDS)
            .callTimeout(RUN_TIMEOUT_S + 5, TimeUnit.SECONDS).proxy(java.net.Proxy.NO_PROXY).build()
    }

    /** One run: words per second and time to the first word. */
    data class Run(val wordsPerSec: Double, val firstWordMs: Long)

    /**
     * Measures [m] (installed): loads it (not counted), then one short answer; with a graphics card also once without.
     * [progress] gets a line for the screen.
     */
    suspend fun measure(c: AppContainer, m: LocalModel, progress: (String) -> Unit = {}): Result {
        val hw = c.localLlm.hardware.value ?: error(tr("Das Gerät wird noch geprüft – bitte gleich noch einmal."))
        val gpu = c.localLlm.usesGpu(m, hw)
        progress(tr("{0} wird geladen …", tr(m.name)))
        val first = run(c, m, cpuOnly = false, progress)
        val cpu = if (gpu) {
            progress(tr("{0} ohne Grafikkarte …", tr(m.name)))
            runCatching { run(c, m, cpuOnly = true, progress) }.getOrNull()
        } else null
        // Afterwards the model runs as usual again (with the graphics card) for the next request.
        if (cpu != null) runCatching { c.localLlm.stop() }
        return Result(m.modelId, first.wordsPerSec, first.firstWordMs, gpu, cpu?.wordsPerSec, cpu?.firstWordMs, hardware = hw.describe())
            .also { save(c, it) }
    }

    private suspend fun run(c: AppContainer, m: LocalModel, cpuOnly: Boolean, progress: (String) -> Unit): Run {
        val p = c.localLlm.provider(m.modelId, cpuOnly)
        progress(tr("{0} schreibt …", tr(m.name)))
        return withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("model", m.id)
                put("stream", true)
                put("max_tokens", MAX_TOKENS)
                put("temperature", 0.3)
                putJsonObject("chat_template_kwargs") { put("enable_thinking", false) }
                put("messages", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", PROMPT) }) })
            }.toString()
            val req = Request.Builder().url(p.baseUrl.trimEnd('/') + "/chat/completions")
                .header("Authorization", "Bearer ${p.apiKey}")
                .post(body.toRequestBody("application/json".toMediaType())).build()
            val start = System.nanoTime()
            var firstAt = 0L
            val text = StringBuilder()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error(tr("Das Modell antwortet nicht (HTTP {0}).", resp.code))
                val src = resp.body?.source() ?: error(tr("Das Modell antwortet nicht."))
                while (!src.exhausted()) {
                    val line = src.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val delta = runCatching {
                        (Json.parseToJsonElement(data).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("delta") as? JsonObject)
                            ?.let { d -> listOf("content", "reasoning_content").mapNotNull { d[it]?.jsonPrimitive?.contentOrNull }.joinToString("") }
                    }.getOrNull().orEmpty()
                    if (delta.isEmpty()) continue
                    if (firstAt == 0L && delta.isNotBlank()) firstAt = System.nanoTime()
                    text.append(delta)
                    c.localLlm.touch()
                }
            }
            val end = System.nanoTime()
            if (firstAt == 0L) error(tr("Das Modell hat keinen Text geliefert."))
            speed(text.toString(), firstAt - start, end - firstAt)
        }
    }

    /** Words per second from the answer [text], the wait for the first word and the writing time (nanoseconds). */
    internal fun speed(text: String, firstNs: Long, writeNs: Long): Run {
        val words = text.split(Regex("""\s+""")).count { it.any(Char::isLetterOrDigit) }
        val seconds = (writeNs / 1e9).coerceAtLeast(0.05)
        // The first word itself was written before the clock for writing started.
        return Run(((words - 1).coerceAtLeast(0) / seconds).let { (it * 10).toLong() / 10.0 }, firstNs / 1_000_000)
    }

    // ── What the numbers mean ───────────────────────────────────────────────────────────────────────────────

    data class Advice(val text: String, val short: Boolean, val mail: Boolean, val confidential: Boolean)

    /** „schafft etwa 12 Wörter je Sekunde – gut für kurze Aufgaben, zu langsam für lange Mails“ */
    fun advice(r: Result): Advice {
        val short = r.wordsPerSec >= SHORT_MIN && r.firstWordMs <= FIRST_WORD_MAX_MS
        val mail = r.wordsPerSec >= MAIL_MIN
        val confidential = r.wordsPerSec >= CONFIDENTIAL_MIN
        val words = r.wordsPerSec.let { if (it >= 10) String.format(Locale.GERMANY, "%.0f", it) else String.format(Locale.GERMANY, "%.1f", it) }
        val first = String.format(Locale.GERMANY, "%.1f", r.firstWordMs / 1000.0)
        val head = tr("schafft etwa {0} Wörter je Sekunde, erstes Wort nach {1} s", words, first)
        val verdict = when {
            mail -> tr("gut für kurze Aufgaben und lange Mails")
            short -> tr("gut für kurze Aufgaben, zu langsam für lange Mails")
            confidential -> tr("für kurze Aufgaben zu langsam – nur für Vertrauliches, wenn Warten nicht stört")
            else -> tr("zu langsam für den Alltag – ein kleineres Modell wählen")
        }
        val cpu = r.cpuWordsPerSec?.let { tr(" (ohne Grafikkarte: {0} Wörter je Sekunde)", String.format(Locale.GERMANY, "%.1f", it)) }.orEmpty()
        return Advice("$head – $verdict$cpu", short, mail, confidential)
    }

    /** Recommendations per use from the measured [installed] models: the strongest that is fast enough for it. */
    data class Picks(val mail: LocalModel?, val short: LocalModel?, val confidential: LocalModel?)

    fun picks(installed: List<LocalModel>, results: Map<String, Result>): Picks {
        val measured = installed.mapNotNull { m -> results[m.modelId]?.let { m to advice(it) } }
        fun best(ok: (Advice) -> Boolean) = measured.filter { ok(it.second) }.maxByOrNull { it.first.needGb }?.first
        return Picks(best { it.mail }, best { it.short }, best { it.confidential })
    }

    /**
     * Whether [m] runs well enough for short tasks in the mixed mode: measured → at least [SHORT_MIN]; not measured →
     * the estimate from the memory ([LocalCatalog.fit]), as before.
     */
    fun goodForShort(c: AppContainer, m: LocalModel, hw: Hardware): Boolean =
        of(c, m.modelId, hw)?.let { advice(it).short } ?: (LocalCatalog.fit(m, hw) == Fit.OK)
}
