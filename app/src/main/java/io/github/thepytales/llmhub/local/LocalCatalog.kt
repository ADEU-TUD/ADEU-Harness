package io.github.thepytales.llmhub.local

import io.github.thepytales.llmhub.data.ModelInfo
import io.github.thepytales.llmhub.data.ModelTier
import io.github.thepytales.llmhub.data.ModelType
import io.github.thepytales.llmhub.data.Privacy
import java.util.Locale

/** What the app knows about this device, for choosing suitable models. */
data class Hardware(
    val phone: Boolean,
    /** Installed memory in GB. */
    val ramGb: Double,
    val cores: Int,
    val cpuName: String = "",
    /** Graphics card with own memory (Windows), null without one. */
    val gpuName: String? = null,
    val vramGb: Double = 0.0,
    val freeDiskGb: Double = Double.NaN,
    /** Only the basic details could be read (the full check was blocked or too slow, e.g. by IT rules). */
    val limited: Boolean = false,
) {
    /** Memory a model may use: on a dedicated GPU its VRAM, else a part of the RAM (the rest is for Windows/Android and other apps). */
    val usableForModel: Double get() = if (vramGb >= 4) vramGb * 0.92 else ramGb * if (phone) 0.45 else 0.55

    fun describe(): String = buildString {
        append(if (phone) "Handy" else "PC")
        append(" · ").append(String.format(Locale.GERMANY, "%.0f GB Arbeitsspeicher", ramGb))
        append(" · ").append(cores).append(" Kerne")
        if (gpuName != null) {
            append(" · ").append(gpuName)
            if (vramGb > 0) append(String.format(Locale.GERMANY, " (%.0f GB)", vramGb))
        }
    }
}

/** A language model for this device: one GGUF file from Hugging Face at a fixed commit, checked by SHA-256. */
data class LocalModel(
    val id: String,
    val name: String,
    val family: String,
    val description: String,
    val repo: String,
    val commit: String,
    val file: String,
    val size: Long,
    val sha256: String,
    /** Memory needed while answering (model + context), in GB. */
    val needGb: Double,
    val context: Int,
    val tools: Boolean,
    /** Active parameters in billions (speed), for MoE models fewer than the total. */
    val activeB: Double,
) {
    val modelId get() = Privacy.LOCAL_PREFIX + id
    val url get() = "https://huggingface.co/$repo/resolve/$commit/$file?download=true"
}

/** How well a model fits this device. */
enum class Fit(val label: String) {
    RECOMMENDED("Empfohlen"),
    OK("Passt"),
    SLOW("Langsam"),
    TOO_BIG("Zu groß für dieses Gerät"),
}

/**
 * Curated models for this device: open licenses (Apache 2.0), good German, from small (phones, old ThinkPads) to
 * large (gaming PCs). Change versions only here.
 */
object LocalCatalog {
    val models = listOf(
        LocalModel(
            "qwen3.5-2b", "Qwen 3.5 2B", "Qwen",
            "Sehr klein und schnell – für ältere Handys und Laptops mit 8 GB. Kurze Texte, Umformulieren, Zusammenfassen.",
            "unsloth/Qwen3.5-2B-GGUF", "f6d5376be1edb4d416d56da11e5397a961aca8ae", "Qwen3.5-2B-Q4_K_M.gguf",
            1_280_835_840L, "aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223", 2.4, 8192, true, 2.0,
        ),
        LocalModel(
            "gemma-4-e2b", "Gemma 4 E2B", "Gemma",
            "Kleines Modell von Google mit gutem Deutsch – Handys und Laptops ohne Grafikkarte.",
            "ggml-org/gemma-4-E2B-it-GGUF", "6938c58e698502037401a5f19f6905f2fb2f7dee", "gemma-4-E2B-it-Q4_0.gguf",
            2_841_481_184L, "8e30dff3ac4c8434c49a7036fa15564bdbb6044e42bf04550bf1a096ad7e6a52", 4.0, 8192, true, 2.0,
        ),
        LocalModel(
            "qwen3.5-4b", "Qwen 3.5 4B", "Qwen",
            "Kompakt und vielseitig: strukturiert gut, nutzt Werkzeuge (Dokumente, Rechnen). Für Laptops mit 16 GB.",
            "unsloth/Qwen3.5-4B-GGUF", "e87f176479d0855a907a41277aca2f8ee7a09523", "Qwen3.5-4B-Q4_K_M.gguf",
            2_740_937_888L, "00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4", 4.2, 8192, true, 4.0,
        ),
        LocalModel(
            "gemma-4-e4b", "Gemma 4 E4B", "Gemma",
            "Beste Sprachqualität ohne Grafikkarte: Briefe, Gutachten, Feedback. Für PCs mit 16 GB und neue Handys.",
            "ggml-org/gemma-4-E4B-it-GGUF", "a69f018d7678a2b9f5e705f8903d6fb29b9b25dc", "gemma-4-E4B-it-Q4_0.gguf",
            4_590_807_392L, "a555b900214b477d8880e7832e0b8925e139b0159640036b09fe472b6f2097f2", 6.0, 8192, true, 4.0,
        ),
        LocalModel(
            "qwen3.5-9b", "Qwen 3.5 9B", "Qwen",
            "Deutlich stärker beim Denken und Auswerten – mit Grafikkarte (8 GB) oder 24 GB Arbeitsspeicher.",
            "unsloth/Qwen3.5-9B-GGUF", "3885219b6810b007914f3a7950a8d1b469d598a5", "Qwen3.5-9B-Q4_K_M.gguf",
            5_680_522_464L, "03b74727a860a56338e042c4420bb3f04b2fec5734175f4cb9fa853daf52b7e8", 7.2, 16384, true, 9.0,
        ),
        LocalModel(
            "gemma-4-12b", "Gemma 4 12B", "Gemma",
            "Sehr gutes Deutsch und lange Texte – für Grafikkarten ab 10 GB oder PCs mit 32 GB.",
            "ggml-org/gemma-4-12B-it-GGUF", "a29b2a6b584a5bdcb672ccdcda99ea9faeacc51a", "gemma-4-12B-it-Q4_0.gguf",
            7_219_673_216L, "3712b9bd32cae83a22f67ee7a4466d8d7a4f21646ac8a07d19bf9418e8767a70", 9.5, 16384, true, 12.0,
        ),
        LocalModel(
            "gemma-4-26b-a4b", "Gemma 4 26B A4B", "Gemma",
            "Großes Expertenmodell, rechnet aber nur mit 4 Mrd. Parametern – schnell auf Gaming-PCs (16 GB Grafik) oder mit 32 GB RAM.",
            "ggml-org/gemma-4-26B-A4B-it-GGUF", "9cf8c8db0f83fb4582f15381f8fb1a7c1b0b9ca2", "gemma-4-26B-A4B-it-Q4_0.gguf",
            14_618_145_824L, "d208665ab1cd3a69f7a9a4bc59430e8448c8093d9b06334f566ac59d6d504a03", 17.0, 16384, true, 4.0,
        ),
        LocalModel(
            "qwen3.6-35b-a3b", "Qwen 3.6 35B A3B", "Qwen",
            "Das stärkste Modell für High-End-PCs (24 GB Grafik oder 48 GB RAM) – nah an den Server-Modellen.",
            "ggml-org/Qwen3.6-35B-A3B-GGUF", "7878dc9a905c7c9379e5e8d6a1aa9affdd6839c5", "Qwen3.6-35B-A3B-Q4_K_M.gguf",
            20_419_565_568L, "671e47e0ec53c665d048b98c3ecbfd5236b5ca9c3e02ed19fc8f81f7b85140c7", 21.5, 16384, true, 3.0,
        ),
    )

    fun get(id: String): LocalModel? = models.firstOrNull { it.id == id || it.modelId == id }

    /** How [m] fits [hw]: too big if it does not fit into the usable memory; slow on weak CPUs. */
    fun fit(m: LocalModel, hw: Hardware): Fit {
        val gpu = hw.vramGb >= 4
        // Windows/Android and the other open programs need their share of the memory too.
        val ramOk = m.needGb <= hw.ramGb * 0.6
        val fitsGpu = gpu && m.needGb <= hw.vramGb * 0.92
        if (!fitsGpu && !ramOk) return Fit.TOO_BIG
        if (!hw.freeDiskGb.isNaN() && m.size / 1e9 > hw.freeDiskGb - 1) return Fit.TOO_BIG
        // Without a GPU, speed depends on the active parameters: above ~4–5 B it gets slow on laptops and phones;
        // desktop processors with 8 or more cores manage up to ~9 B.
        val cpuLimit = when {
            hw.phone -> 4.5
            hw.cores >= 8 -> 9.5
            hw.cores <= 2 -> 2.5
            else -> 5.0
        }
        if (!fitsGpu && m.activeB > cpuLimit) return Fit.SLOW
        return Fit.OK
    }

    /** The best models for [hw]: up to two recommendations (a fast one and the strongest that runs well). */
    fun recommended(hw: Hardware): List<LocalModel> {
        val ok = models.filter { fit(it, hw) == Fit.OK }
        if (ok.isEmpty()) return listOfNotNull(models.firstOrNull { fit(it, hw) != Fit.TOO_BIG })
        val strongest = ok.maxByOrNull { it.needGb }!!
        val quick = ok.filter { it.activeB <= 4.0 && it != strongest }.maxByOrNull { it.needGb }
        return listOfNotNull(strongest, quick).distinct()
    }

    fun fitOf(m: LocalModel, hw: Hardware): Fit = if (m in recommended(hw)) Fit.RECOMMENDED else fit(m, hw)

    /** For the model picker and the overview. */
    fun info(m: LocalModel) = ModelInfo(
        id = m.modelId,
        type = ModelType.CHAT,
        summary = m.description + " Läuft auf diesem Gerät – nichts verlässt das Gerät.",
        strengths = "Vertrauliche Daten, offline",
        contextTokens = m.context,
        vision = false,
        tools = m.tools,
        label = m.name,
        useCase = "Auf diesem Gerät – vertraulich und offline",
        tier = ModelTier.FEATURED,
        badge = "Lokal",
        power = when { m.needGb >= 15 -> 4; m.needGb >= 7 -> 3; m.needGb >= 4 -> 2; else -> 1 },
        speed = when { m.activeB <= 2.5 -> 4; m.activeB <= 4.5 -> 3; else -> 2 },
    )
}
