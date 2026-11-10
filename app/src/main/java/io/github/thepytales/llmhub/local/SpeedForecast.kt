package io.github.thepytales.llmhub.local

import java.util.Locale
import kotlin.math.abs

/**
 * Geräteprofil und Vorhersage (AP-105): how fast every model of the catalog will write and read on this device, before
 * it is downloaded.
 *
 * - **Writing** (one word after the other) is limited by the memory: for each token the active part of the model is read
 *   once. Tokens per second ≈ usable bandwidth ÷ bytes per token. Bandwidth: the graphics card, the Apple chip (shared
 *   memory) or the main memory (speed × bus width from the firmware tables; else the measured bandwidth of the Kurzmessung).
 * - **Reading** (the request, a long document) is limited by computing: ≈ threads × instruction sets ÷ active parameters
 *   on the processor; on graphics by their size.
 * - **Calibration**: every real measurement on this device (Kurzmessung with the smallest installed model, AP-76 *Messen*)
 *   gives the ratio measured ÷ formula; the median of these ratios corrects all predictions of the same kind
 *   (processor or graphics). So the formula only has to be right in its proportions between models.
 *
 * Pure functions – the numbers of a device are passed in ([Hardware], [Sample]s), tests need no hardware.
 */
object SpeedForecast {
    /** German words per token (Qwen/Gemma tokenizers, measured on German mails: about 0.55–0.65) until measured here. */
    const val WORDS_PER_TOKEN = 0.6

    /** Tokens of a short request with the chat template (the mail of AP-76) – for the time to the first word. */
    const val SHORT_PROMPT_TOKENS = 90

    /** Fixed costs before the first word (sampling, template, HTTP) in ms. */
    private const val OVERHEAD_MS = 150.0

    enum class Source { ESTIMATE, CALIBRATED, MEASURED }

    data class Forecast(
        val writeTokS: Double,
        val readTokS: Double,
        val wordsPerToken: Double,
        val gpu: Boolean,
        val source: Source,
    ) {
        val wordsPerSec get() = writeTokS * wordsPerToken
        /** Reading in words per second (pages: about 400 words). */
        val readWordsPerSec get() = readTokS * wordsPerToken
        fun firstWordMs(promptTokens: Int = SHORT_PROMPT_TOKENS): Long = (promptTokens / readTokS * 1000 + OVERHEAD_MS).toLong()
        /** Seconds to read [words] words (a long document before the first word). */
        fun readSeconds(words: Int): Double = words / readWordsPerSec
    }

    /**
     * One real measurement on this device: tokens per second while writing and – if known – reading, and whether the
     * graphics computed. [wordsPerToken] when the answer text was counted.
     */
    data class Sample(
        val modelId: String,
        val gpu: Boolean,
        val writeTokS: Double,
        val readTokS: Double? = null,
        val wordsPerToken: Double? = null,
    )

    // ── Bytes per token ─────────────────────────────────────────────────────────────────────────────────────

    /** Bits per weight of the quantization in the file name (Q4_K_M ≈ 4.85 …). */
    fun bitsPerWeight(file: String): Double {
        val f = file.uppercase(Locale.ROOT)
        return when {
            "IQ4_XS" in f -> 4.25
            "Q4_K_M" in f -> 4.85
            "Q4_K_S" in f -> 4.6
            "Q4_0" in f -> 4.55
            "Q5_K_M" in f -> 5.7
            "Q6_K" in f -> 6.6
            "Q8_0" in f -> 8.5
            "BF16" in f || "F16" in f -> 16.0
            "Q3_K" in f -> 3.9
            else -> 4.85
        }
    }

    /**
     * Bytes read per written token: the active share of the file (experts and per-layer embeddings are only partly
     * read); never less than the active parameters at the bits of the file.
     */
    fun bytesPerToken(m: LocalModel): Double {
        val share = (m.activeB / m.totalB).coerceIn(0.0, 1.0)
        val byFile = m.size * share
        val byParams = m.activeB * 1e9 * bitsPerWeight(m.file) / 8
        return maxOf(byFile, byParams * 0.8)
    }

    // ── Bandwidth and computing of the device ───────────────────────────────────────────────────────────────

    /** Apple chips: memory bandwidth (GB/s) and graphics cores of the common configuration. */
    private val APPLE = listOf(
        "M1 Ultra" to (800.0 to 64), "M1 Max" to (400.0 to 32), "M1 Pro" to (200.0 to 16), "M1" to (68.25 to 8),
        "M2 Ultra" to (800.0 to 76), "M2 Max" to (400.0 to 38), "M2 Pro" to (200.0 to 19), "M2" to (100.0 to 10),
        "M3 Ultra" to (819.0 to 80), "M3 Max" to (400.0 to 40), "M3 Pro" to (150.0 to 18), "M3" to (100.0 to 10),
        "M4 Max" to (546.0 to 40), "M4 Pro" to (273.0 to 20), "M4" to (120.0 to 10),
        "M5 Max" to (614.0 to 40), "M5 Pro" to (307.0 to 20), "M5" to (153.0 to 10),
    )

    /** Bandwidth (GB/s) and graphics cores of an Apple chip by its name ("Apple M3 Max"), null if unknown. */
    fun appleChip(name: String): Pair<Double, Int>? {
        val n = name.replace("Apple", "").trim()
        APPLE.firstOrNull { (k, _) -> n == k || n.startsWith("$k ") }?.let { return it.second }
        // A newer chip: base like the latest known, Pro twice, Max four times, Ultra eight times.
        val gen = Regex("""M(\d+)""").find(n) ?: return null
        val base = 153.0 * (1 + 0.1 * ((gen.groupValues[1].toIntOrNull() ?: 5) - 5).coerceAtLeast(0))
        return when {
            "Ultra" in n -> base * 8 to 80
            "Max" in n -> base * 4 to 40
            "Pro" in n -> base * 2 to 20
            else -> base to 10
        }
    }

    /** Graphics cards (GB/s), longer names first; unknown cards by their memory. */
    private val GPUS = listOf(
        "RTX 5090" to 1792.0, "RTX 5080" to 960.0, "RTX 5070 Ti" to 896.0, "RTX 5070" to 672.0, "RTX 5060 Ti" to 448.0, "RTX 5060" to 448.0,
        "RTX 4090" to 1008.0, "RTX 4080" to 717.0, "RTX 4070 Ti SUPER" to 672.0, "RTX 4070 Ti" to 504.0, "RTX 4070 SUPER" to 504.0,
        "RTX 4070" to 504.0, "RTX 4060 Ti" to 288.0, "RTX 4060" to 272.0,
        "RTX 3090" to 936.0, "RTX 3080 Ti" to 912.0, "RTX 3080" to 760.0, "RTX 3070" to 448.0, "RTX 3060 Ti" to 448.0, "RTX 3060" to 360.0,
        "RX 9070 XT" to 645.0, "RX 9070" to 645.0, "RX 7900 XTX" to 960.0, "RX 7900 XT" to 800.0, "RX 7800 XT" to 624.0,
        "RX 7700 XT" to 432.0, "RX 7600" to 288.0, "Arc B580" to 456.0, "Arc A770" to 560.0, "RTX A4000" to 448.0, "RTX A2000" to 288.0,
    )

    fun gpuBandwidth(name: String?, vramGb: Double): Double {
        val n = name.orEmpty()
        GPUS.firstOrNull { (k, _) -> n.contains(k, ignoreCase = true) }?.let { return it.second }
        return when {
            vramGb >= 20 -> 900.0
            vramGb >= 14 -> 600.0
            vramGb >= 10 -> 450.0
            vramGb >= 7 -> 350.0
            else -> 200.0
        }
    }

    private fun isApple(hw: Hardware) = hw.chip.isNotBlank() || (hw.gpuName ?: "").startsWith("Apple")

    /**
     * Usable bandwidth in GB/s for writing. Graphics: the card (or the Apple chip; large chips lose a little more to
     * computing). Processor: the memory, of which llama.cpp uses about 60 %; [measuredGbs] (Kurzmessung in Java) when the
     * firmware said nothing; else a cautious office PC (dual-channel DDR4).
     */
    fun bandwidth(hw: Hardware, gpu: Boolean, measuredGbs: Double? = null): Double {
        if (gpu && isApple(hw)) {
            val bw = hw.memBandwidthGbs.takeIf { !it.isNaN() } ?: appleChip(hw.chip)?.first ?: 100.0
            return bw * 0.85 / (1 + bw / 1500)
        }
        if (gpu) return gpuBandwidth(hw.gpuName, hw.vramGb) * 0.65
        val threadsShare = (LocalLlm.threads(hw) / 4.0).coerceAtMost(1.0)
        val raw = when {
            !hw.memBandwidthGbs.isNaN() -> hw.memBandwidthGbs * 0.6
            measuredGbs != null && measuredGbs > 1 -> measuredGbs * 0.85
            hw.phone -> 20.0
            else -> 24.0
        }
        return raw * (0.6 + 0.4 * threadsShare)
    }

    /** Relative speed of the instruction sets for reading on the processor (AVX2 = 1). */
    fun simd(hw: Hardware): Double {
        val f = hw.cpuFeatures
        return when {
            "amx" in f -> 2.0
            "avx512" in f -> 1.35
            "avx2" in f -> 1.0
            "i8mm" in f -> 1.0
            "dotprod" in f -> 0.8
            "neon" in f -> 0.45
            f.isEmpty() -> if (hw.phone) 0.7 else 0.9
            else -> 0.4
        }
    }

    /** Reading speed in tokens per second before calibration. */
    fun rawRead(m: LocalModel, hw: Hardware, gpu: Boolean): Double {
        val moe = if (m.activeB < m.totalB * 0.5) 0.7 else 1.0
        val perB = when {
            gpu && isApple(hw) -> {
                val cores = hw.gpuCores.takeIf { it > 0 } ?: appleChip(hw.chip)?.second ?: 10
                val gen = Regex("""M(\d+)""").find(hw.chip)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                cores * 110.0 * (1 + 0.1 * (gen - 1))
            }
            gpu -> gpuBandwidth(hw.gpuName, hw.vramGb) * 60
            else -> LocalLlm.threads(hw) * 60.0 * simd(hw) * if (hw.phone) 0.5 else 1.0
        }
        return perB / m.activeB * moe
    }

    /** Writing speed in tokens per second before calibration. */
    fun rawWrite(m: LocalModel, hw: Hardware, gpu: Boolean, measuredGbs: Double? = null): Double {
        val moe = if (m.activeB < m.totalB * 0.5) 0.85 else 1.0
        return bandwidth(hw, gpu, measuredGbs) * 1e9 / bytesPerToken(m) * moe
    }

    // ── Calibration ─────────────────────────────────────────────────────────────────────────────────────────

    data class Calibration(val write: Double? = null, val read: Double? = null, val wordsPerToken: Double? = null, val samples: Int = 0)

    private fun median(xs: List<Double>): Double? = xs.sorted().takeIf { it.isNotEmpty() }?.let { s ->
        if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    /** Ratios measured ÷ formula of [samples] of the kind [gpu] (graphics or processor). */
    fun calibrate(samples: List<Sample>, hw: Hardware, gpu: Boolean, measuredGbs: Double? = null): Calibration {
        val mine = samples.filter { it.gpu == gpu }.mapNotNull { s -> LocalCatalog.get(s.modelId)?.let { it to s } }
        if (mine.isEmpty()) return Calibration(wordsPerToken = median(samples.mapNotNull { it.wordsPerToken }))
        val w = median(mine.map { (m, s) -> s.writeTokS / rawWrite(m, hw, gpu, measuredGbs) })
        val r = median(mine.mapNotNull { (m, s) -> s.readTokS?.let { it / rawRead(m, hw, gpu) } })
        return Calibration(w, r, median(samples.mapNotNull { it.wordsPerToken }), mine.size)
    }

    /**
     * The forecast for [m] on [hw]: on the graphics if it fits there ([LocalLlm.usesGpu] rule), else the processor.
     * A measurement of [m] itself wins ([Source.MEASURED]); otherwise the formula, corrected by the other measurements.
     */
    fun forecast(m: LocalModel, hw: Hardware, samples: List<Sample> = emptyList(), measuredGbs: Double? = null, cpuOnly: Boolean = false): Forecast {
        val gpu = !cpuOnly && hw.vramGb >= 4 && m.needGb <= hw.vramGb * 0.92
        val cal = calibrate(samples, hw, gpu, measuredGbs)
        val wpt = cal.wordsPerToken ?: WORDS_PER_TOKEN
        samples.lastOrNull { it.modelId == m.id && it.gpu == gpu }?.let { own ->
            val read = own.readTokS ?: rawRead(m, hw, gpu) * (cal.read ?: 1.0)
            return Forecast(own.writeTokS, read, wpt, gpu, Source.MEASURED)
        }
        val write = rawWrite(m, hw, gpu, measuredGbs) * (cal.write ?: 1.0)
        val read = rawRead(m, hw, gpu) * (cal.read ?: 1.0)
        return Forecast(write, read, wpt, gpu, if (cal.samples > 0) Source.CALIBRATED else Source.ESTIMATE)
    }

    /**
     * How far the forecast is from each measurement (AP-105 target: at most 30 %): every measured model is forecast
     * from the *other* measurements only (leave one out), so the check is honest. Deviation = forecast ÷ measured − 1.
     */
    data class Check(val modelId: String, val gpu: Boolean, val forecastTokS: Double, val measuredTokS: Double) {
        val deviation get() = forecastTokS / measuredTokS - 1
        val withinTarget get() = abs(deviation) <= TARGET
    }

    const val TARGET = 0.30

    fun check(samples: List<Sample>, hw: Hardware, measuredGbs: Double? = null): List<Check> =
        samples.groupBy { it.modelId to it.gpu }.mapNotNull { (key, own) ->
            val m = LocalCatalog.get(key.first) ?: return@mapNotNull null
            val others = samples.filter { it.modelId != key.first }
            val gpu = key.second
            val cal = calibrate(others, hw, gpu, measuredGbs)
            val f = rawWrite(m, hw, gpu, measuredGbs) * (cal.write ?: 1.0)
            Check(m.id, gpu, f, own.last().writeTokS)
        }

    /** "≈ 12 Wörter/s" with one decimal below 10. */
    fun words(x: Double): String = if (x >= 10) String.format(Locale.GERMANY, "%.0f", x) else String.format(Locale.GERMANY, "%.1f", x)
}
