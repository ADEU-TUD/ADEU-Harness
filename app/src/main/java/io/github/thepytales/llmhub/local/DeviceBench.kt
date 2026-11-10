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
 * Kurzmessung (AP-105), offered at the first start and under *Modelle auf diesem Gerät*: about 20 seconds.
 * 1. Memory bandwidth in Java (one second; for devices whose firmware does not say it – Linux, Android, blocked PCs).
 * 2. With an installed model (the smallest): it reads a fixed text of about 400 words and writes 64 tokens; llama-server
 *    reports the exact tokens per second for both (`timings`). Loading the model is not counted.
 * The results calibrate [SpeedForecast] for all models; AP-76 (*Messen*) adds its measurements as further samples.
 * Stored per device (like AP-76): a measurement from another PC in a synced data folder does not count.
 */
object DeviceBench {
    @Serializable
    data class Stored(
        val hardware: String = "",
        val at: Long = 0,
        /** Bandwidth measured in Java in GB/s (0 = not measured). */
        val javaGbs: Double = 0.0,
        val samples: List<StoredSample> = emptyList(),
    )

    @Serializable
    data class StoredSample(
        val modelId: String,
        val gpu: Boolean,
        val writeTokS: Double,
        val readTokS: Double? = null,
        val wordsPerToken: Double? = null,
        /** "kurz" (this measurement) or "messen" (AP-76). */
        val kind: String = "kurz",
        val at: Long = System.currentTimeMillis(),
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun file(c: AppContainer) = File(c.appContext.filesDir, "geraeteprofil-messung.json")

    private val _stored = MutableStateFlow<Stored?>(null)

    fun stored(c: AppContainer): StateFlow<Stored?> {
        if (_stored.value == null) _stored.value = runCatching { json.decodeFromString<Stored>(file(c).readText()) }.getOrDefault(Stored())
        return _stored
    }

    /** Only what was measured on this device ([Hardware.describe] must match). */
    fun here(c: AppContainer, hw: Hardware?): Stored {
        val s = stored(c).value ?: Stored()
        return if (hw == null || s.hardware.isEmpty() || s.hardware == hw.describe()) s else Stored()
    }

    @Synchronized
    private fun save(c: AppContainer, s: Stored) {
        runCatching { file(c).apply { parentFile?.mkdirs() }.writeText(json.encodeToString(s)) }
        _stored.value = s
    }

    fun forget(c: AppContainer) {
        runCatching { file(c).delete() }
        _stored.value = Stored()
    }

    /**
     * All samples of this device for [SpeedForecast]: the Kurzmessung and the AP-76 measurements (words per second turned
     * into tokens with the measured words per token).
     */
    fun samples(c: AppContainer, hw: Hardware?): List<SpeedForecast.Sample> {
        val s = here(c, hw)
        val own = s.samples.map { SpeedForecast.Sample(it.modelId, it.gpu, it.writeTokS, it.readTokS, it.wordsPerToken) }
        val wpt = own.mapNotNull { it.wordsPerToken }.takeIf { it.isNotEmpty() }?.average() ?: SpeedForecast.WORDS_PER_TOKEN
        val speed = LocalSpeed.results(c).value.orEmpty().values
            .filter { r -> hw == null || r.hardware.isEmpty() || r.hardware == hw.describe() }
            .flatMap { r ->
                val id = LocalCatalog.get(r.modelId)?.id ?: return@flatMap emptyList()
                listOfNotNull(
                    SpeedForecast.Sample(id, r.gpu, r.wordsPerSec / wpt),
                    r.cpuWordsPerSec?.let { SpeedForecast.Sample(id, false, it / wpt) },
                )
            }
        // A model measured both ways counts once per kind: the Kurzmessung (exact tokens) wins.
        return own + speed.filter { sp -> own.none { it.modelId == sp.modelId && it.gpu == sp.gpu } }
    }

    fun javaGbs(c: AppContainer, hw: Hardware?): Double? = here(c, hw).javaGbs.takeIf { it > 0 }

    /** The forecast of [m] with everything measured on this device. */
    fun forecast(c: AppContainer, m: LocalModel, hw: Hardware): SpeedForecast.Forecast =
        SpeedForecast.forecast(m, hw, samples(c, hw), javaGbs(c, hw))

    // ── Measuring ──────────────────────────────────────────────────────────────────────────────────────────

    @Volatile private var sink = 0L

    /**
     * Reads a large array with several threads, best of three passes: GB/s the processor gets from the memory. Larger
     * than any processor cache (256 MB on a PC, 64 MB on a phone), about one second.
     */
    fun javaBandwidth(phone: Boolean, threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)): Double {
        val longs = (if (phone) 64 else 256) * 1024 * 1024 / 8
        val data = runCatching { LongArray(longs) { it.toLong() } }.getOrNull() ?: return 0.0
        val chunk = longs / threads
        var best = Double.MAX_VALUE
        repeat(4) { pass ->
            val start = System.nanoTime()
            val sums = LongArray(threads)
            val workers = (0 until threads).map { t ->
                Thread {
                    var s = 0L
                    var i = t * chunk
                    val end = if (t == threads - 1) longs else i + chunk
                    while (i < end) { s += data[i]; i += 1 }
                    sums[t] = s
                }.apply { start() }
            }
            workers.forEach { it.join() }
            val secs = (System.nanoTime() - start) / 1e9
            sink = sums.sum() // keeps the compiler from dropping the loop
            if (pass > 0) best = minOf(best, secs) // the first pass warms up the compiler
        }
        return longs * 8 / best / 1e9
    }

    /** A fixed text to read (invented minutes of a meeting, about 400 words); the same everywhere. */
    private val TEXT = """
        Protokoll der Sitzung des Instituts für Erziehungswissenschaft vom 12. März. Anwesend waren elf Mitglieder, die
        Sitzung begann um 14 Uhr im Raum 214. Erster Punkt war die Planung des Sommersemesters. Für die Einführung in die
        Didaktik werden zwei zusätzliche Übungsgruppen gebraucht, weil sich mehr Studierende eingeschrieben haben als im
        Vorjahr. Die Gruppen sollen dienstags und donnerstags am Nachmittag stattfinden; die Räume werden bei der
        Verwaltung angefragt. Zweiter Punkt war die Prüfungsordnung. Die Kommission hat vorgeschlagen, die Hausarbeit im
        Modul Bildungsforschung durch ein Portfolio zu ersetzen, das aus drei kurzen Aufgaben und einer Reflexion besteht.
        Die Mitglieder diskutierten, ob der Aufwand für die Lehrenden dadurch steigt. Es wurde vereinbart, im Wintersemester
        einen Probelauf mit zwei Seminaren zu machen und danach zu entscheiden. Dritter Punkt war die Ausstattung der
        Bibliothek. Es fehlen aktuelle Lehrbücher zur inklusiven Bildung und zur Schulentwicklung; die Liste der Wünsche
        wird bis Ende des Monats gesammelt und dann an die Fachreferentin geschickt. Vierter Punkt war die Tagung im
        Herbst. Das Programm steht zur Hälfte, es fehlen noch zwei Vorträge zum Thema digitale Medien im Unterricht. Wer
        jemanden vorschlagen möchte, meldet sich bis zum 30. April im Sekretariat. Die Anmeldung soll ab Juni über die
        Webseite möglich sein. Fünfter Punkt waren die Abschlussarbeiten. Im laufenden Jahr werden voraussichtlich
        achtzehn Masterarbeiten betreut, davon sechs mit empirischem Schwerpunkt. Für die Auswertung von Interviews soll
        ein gemeinsamer Leitfaden entstehen, damit die Studierenden ähnliche Standards einhalten. Eine kleine Arbeitsgruppe
        übernimmt den Entwurf und stellt ihn in der nächsten Sitzung vor. Unter Verschiedenes wurde angemerkt, dass die
        Kaffeemaschine im Teeraum repariert werden muss und dass die Sprechstunden in der vorlesungsfreien Zeit auf der
        Webseite aktualisiert werden sollten. Die nächste Sitzung findet am 9. April statt. Die Sitzung endete um 15:40 Uhr.
    """.trimIndent().replace(Regex("""\s+"""), " ")

    private const val WRITE_TOKENS = 64
    private const val TIMEOUT_S = 90L

    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_S + 5, TimeUnit.SECONDS).proxy(java.net.Proxy.NO_PROXY).build()
    }

    /** The model of the Kurzmessung: the smallest installed one (fast to load), null without any. */
    fun benchModel(installed: List<LocalModel>): LocalModel? = installed.minByOrNull { it.size }

    /**
     * Runs the Kurzmessung. [progress] gets a line for the screen. Without an installed model only the bandwidth is
     * measured (the forecast stays an estimate).
     */
    suspend fun run(c: AppContainer, progress: (String) -> Unit = {}): Stored {
        val hw = c.localLlm.hardware.value ?: error(tr("Das Gerät wird noch geprüft – bitte gleich noch einmal."))
        progress(tr("Speicher wird gemessen …"))
        val gbs = withContext(Dispatchers.Default) { javaBandwidth(hw.phone) }
        val before = here(c, hw)
        var next = before.copy(hardware = hw.describe(), at = System.currentTimeMillis(), javaGbs = gbs)
        val m = benchModel(c.localLlm.installedModels())
        if (m != null) {
            progress(tr("{0} wird geladen …", tr(m.name)))
            val gpu = c.localLlm.usesGpu(m, hw)
            val p = c.localLlm.provider(m.modelId)
            progress(tr("{0} liest und schreibt …", tr(m.name)))
            val t = withContext(Dispatchers.IO) { timed(p.baseUrl, p.apiKey, m.id) }
            c.localLlm.touch()
            val sample = StoredSample(m.id, gpu, t.writeTokS, t.readTokS, t.wordsPerToken)
            next = next.copy(samples = next.samples.filterNot { it.modelId == m.id && it.gpu == gpu && it.kind == "kurz" } + sample)
        }
        save(c, next)
        return next
    }

    data class Timed(val readTokS: Double, val writeTokS: Double, val wordsPerToken: Double?)

    /** One request without the prompt cache; the server's own timings (prompt_per_second, predicted_per_second). */
    private fun timed(baseUrl: String, key: String, model: String): Timed {
        val body = buildJsonObject {
            put("model", model)
            put("stream", false)
            put("max_tokens", WRITE_TOKENS)
            put("temperature", 0.0)
            put("cache_prompt", false)
            putJsonObject("chat_template_kwargs") { put("enable_thinking", false) }
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", "Fasse das folgende Protokoll in fünf Sätzen zusammen.\n\n$TEXT")
                })
            })
        }.toString()
        val req = Request.Builder().url(baseUrl.trimEnd('/') + "/chat/completions").header("Authorization", "Bearer $key")
            .post(body.toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error(tr("Das Modell antwortet nicht (HTTP {0}).", resp.code))
            return parseTimings(resp.body?.string().orEmpty())
        }
    }

    /** The speeds from a llama-server answer; words per token from the written text. */
    internal fun parseTimings(text: String): Timed {
        val o = Json.parseToJsonElement(text).jsonObject
        val t = o["timings"]?.jsonObject ?: error(tr("Das Modell hat keine Messwerte geliefert."))
        fun d(k: String) = t[k]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
        val read = d("prompt_per_second") ?: error(tr("Das Modell hat keine Messwerte geliefert."))
        val write = d("predicted_per_second") ?: error(tr("Das Modell hat keine Messwerte geliefert."))
        val n = d("predicted_n")
        val answer = o["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
        val words = answer.split(Regex("""\s+""")).count { it.any(Char::isLetterOrDigit) }
        val wpt = if (n != null && n >= 16 && words >= 8) (words / n).coerceIn(0.3, 1.0) else null
        return Timed(read, write, wpt)
    }

    /**
     * Messprotokoll to copy (for *Problem melden* or the work plan): device profile, forecast and measurement per model and
     * the deviation – the numbers AP-105 needs from five device classes.
     */
    fun protocol(c: AppContainer, hw: Hardware): String = buildString {
        val s = here(c, hw)
        val samples = samples(c, hw)
        appendLine("Lora – Geräteprofil und Messung (AP-105)")
        appendLine("Gerät: ${hw.describe()}")
        appendLine("Prozessor: ${hw.cpuName.ifBlank { "?" }} · Befehlssätze: ${hw.cpuFeatures.sorted().joinToString(", ").ifBlank { "?" }}")
        appendLine("Speicher: ${hw.memDetail.ifBlank { "?" }} · Bandbreite: ${fmt(hw.memBandwidthGbs)} GB/s · in Java gemessen: ${fmt(s.javaGbs)} GB/s")
        if (hw.chip.isNotBlank()) appendLine("Apple-Chip: ${hw.chip} · Grafikkerne: ${hw.gpuCores.takeIf { it > 0 } ?: "?"}")
        if (hw.gpuName != null) appendLine("Grafik: ${hw.gpuName} · ${fmt(hw.vramGb)} GB")
        appendLine("Messungen: ${samples.size}")
        for (x in samples) appendLine("  ${x.modelId} · ${if (x.gpu) "Grafik" else "Prozessor"} · schreibt ${fmt(x.writeTokS)} Token/s" +
            (x.readTokS?.let { " · liest ${fmt(it)} Token/s" } ?: "") + (x.wordsPerToken?.let { " · ${fmt(it)} Wörter/Token" } ?: ""))
        val checks = SpeedForecast.check(samples, hw, s.javaGbs.takeIf { it > 0 })
        if (checks.isNotEmpty()) {
            appendLine("Vorhersage aus den übrigen Messungen gegen Messung (Ziel ±30 %):")
            for (k in checks) appendLine("  ${k.modelId} · ${if (k.gpu) "Grafik" else "Prozessor"} · vorhergesagt ${fmt(k.forecastTokS)} · gemessen ${fmt(k.measuredTokS)} Token/s · " +
                String.format(Locale.GERMANY, "%+.0f %%", k.deviation * 100))
        }
        appendLine("Vorhersage aller Modelle (Wörter/s schreiben · lesen):")
        for (m in LocalCatalog.models) {
            val f = SpeedForecast.forecast(m, hw, samples, s.javaGbs.takeIf { it > 0 })
            appendLine("  ${m.id} · ${SpeedForecast.words(f.wordsPerSec)} · ${SpeedForecast.words(f.readWordsPerSec)} · ${f.source.name.lowercase()}")
        }
    }

    private fun fmt(x: Double) = if (x.isNaN() || x <= 0) "?" else String.format(Locale.GERMANY, "%.1f", x)
}
