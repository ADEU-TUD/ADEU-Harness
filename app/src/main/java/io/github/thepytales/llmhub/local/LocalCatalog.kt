package io.github.thepytales.llmhub.local

import io.github.thepytales.llmhub.data.ModelInfo
import io.github.thepytales.llmhub.data.ModelTier
import io.github.thepytales.llmhub.data.ModelType
import io.github.thepytales.llmhub.data.Privacy
import java.util.Locale
import io.github.thepytales.llmhub.ui.trLater

/** What the app knows about this device, for choosing suitable models. */
data class Hardware(
    val phone: Boolean,
    /** A Mac (shown as "Mac", not "PC"; AP-86). */
    val mac: Boolean = false,
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
    /**
     * Geräteprofil (AP-105): theoretical memory bandwidth in GB/s (speed × bus width, or the Apple chip), NaN if unknown –
     * it decides how fast a model writes on the processor.
     */
    val memBandwidthGbs: Double = Double.NaN,
    /** Memory as read, e.g. "DDR5-5600 · 128 Bit" (empty if unknown). */
    val memDetail: String = "",
    /** Instruction sets that make reading fast: avx2, avx512, amx, neon, dotprod, i8mm, sve. */
    val cpuFeatures: Set<String> = emptySet(),
    /** Apple chip with shared memory, e.g. "Apple M3 Max" (empty otherwise). */
    val chip: String = "",
    /** Cores of the graphics (Apple chip) – 0 if unknown. */
    val gpuCores: Int = 0,
) {
    /** Memory a model may use: on a dedicated GPU its VRAM, else a part of the RAM (the rest is for Windows/Android and other apps). */
    val usableForModel: Double get() = if (vramGb >= 4) vramGb * 0.92 else ramGb * if (phone) 0.45 else 0.55

    fun describe(): String = buildString {
        append(if (phone) "Handy" else if (mac) "Mac" else "PC")
        append(" · ").append(String.format(Locale.GERMANY, "%.0f GB Arbeitsspeicher", ramGb))
        append(" · ").append(cores).append(" Kerne")
        if (gpuName != null) {
            append(" · ").append(gpuName)
            if (vramGb > 0) append(String.format(Locale.GERMANY, " (%.0f GB)", vramGb))
        }
    }
}

/**
 * What a local model is mainly for (AP-106). The [ALLROUND] model is the standard; a specialist is loaded only when it
 * is needed (switching costs 10–30 s, and small devices hold only one large model at a time).
 */
enum class Purpose(val label: String, val hint: String) {
    ALLROUND(trLater("Allrounder"), trLater("für fast alles: Chat, Zusammenfassen, Umformulieren, Übersetzen")),
    TEXT(trLater("Texte"), trLater("Briefe, Gutachten, Feedback – besonders gutes Deutsch")),
    CODE(trLater("Code"), trLater("Programmieren, Skripte, Formeln, Fehlersuche")),
    MAIL(trLater("Postfach"), trLater("Mails zusammenfassen, einordnen, Entwürfe – mit Werkzeugen")),
    ;

    companion object {
        fun parse(text: String): Set<Purpose> = text.split(',').mapNotNull { n -> entries.firstOrNull { it.name == n.trim() } }.toSet()
        fun format(p: Set<Purpose>): String = entries.filter { it in p }.joinToString(",") { it.name }
    }
}

/**
 * Bilder verstehen lokal (AP-110): the image part of a model (mmproj for llama.cpp), a small download of its own from the
 * same repository and commit as the model, checked by SHA-256. Models of the same family and size share it.
 */
data class Projector(val repo: String, val commit: String, val file: String, val size: Long, val sha256: String) {
    val url get() = "https://huggingface.co/$repo/resolve/$commit/$file?download=true"
    /** The name on the device: unique also where repositories call it only „mmproj-F16.gguf“. */
    val localName get() = if (file.startsWith("mmproj-") && file.count { it == '-' } <= 1) repo.substringAfter('/').removeSuffix("-GGUF") + "-" + file else file
}

/**
 * A language model for this device: one GGUF file from Hugging Face at a fixed commit, checked by SHA-256. Curated
 * models of others are called „<original name> – Lora Edition“ (AP-106): chosen, quantized or taken over, measured and
 * set up for Lora by us – weights unchanged, base model, authors and licence as in the original ([card]).
 */
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
    /**
     * All parameters in billions (AP-105): for experts (MoE) and Gemma's per-layer embeddings more than [activeB]; only
     * the part [activeB]/[totalB] of the file is read for each word.
     */
    val totalB: Double = activeB,
    /** What it is for (AP-106). */
    val purposes: Set<Purpose> = setOf(Purpose.ALLROUND),
    /** Rough strength for the choice within a purpose (about billions of parameters of a dense model). */
    val quality: Double = activeB,
    /** The original model (Hugging Face) and its authors, the licence (AP-106). */
    val base: String = "",
    val authors: String = "",
    val license: String = "Apache-2.0",
    /** Who quantized the file (the repository on Hugging Face). */
    val quantizedBy: String = repo.substringBefore('/'),
    /** Experts (MoE): only some of the weights compute per token – slower to read than a dense model of [activeB]. */
    val moe: Boolean = activeB < totalB * 0.5,
    /**
     * Lange Texte lokal (AP-108): memory of the KV cache per token of context in KB at 16 bit (cautious estimate from the
     * architecture: attention layers × KV heads × head size; hybrid and sliding-window models need less).
     */
    val kvKb: Double = 64.0,
    /** The longest context the model handles well (tokens), the upper limit of [LocalCatalog.contextFor]. */
    val maxContext: Int = 32_768,
    /** Its image part (AP-110), null for models that only read text (the code specialists). */
    val projector: Projector? = null,
) {
    val modelId get() = Privacy.LOCAL_PREFIX + id
    val url get() = "https://huggingface.co/$repo/resolve/$commit/$file?download=true"
    /** The name without „– Lora Edition“. */
    val original get() = name.substringBefore(" – Lora Edition")
}

/** How well a model fits this device. */
enum class Fit(val label: String) {
    RECOMMENDED(trLater("Empfohlen")),
    OK(trLater("Passt")),
    SLOW(trLater("Langsam")),
    TOO_BIG(trLater("Zu groß für dieses Gerät")),
}

/**
 * Curated models for this device (AP-106): only licences that allow use and passing on (Apache-2.0; Gemma 4 is
 * Apache-2.0 too; no Llama), good German, in fine steps from phones and 8 GB laptops to 24 GB graphics cards and Macs
 * with 64 GB. Each purpose has a ladder from small to strong; [pick] takes the strongest one that fits the memory and
 * reaches the minimum speed of the forecast (AP-105). Change versions only here; files stay at their commit.
 */
object LocalCatalog {
    private const val QWEN = "Qwen-Team (Alibaba Cloud)"
    private const val GOOGLE = "Google DeepMind"
    private val ALL_MAIL = setOf(Purpose.ALLROUND, Purpose.MAIL)

    val models = listOf(
        LocalModel(
            "qwen3.5-0.8b", trLater("Qwen 3.5 0.8B – Lora Edition"), "Qwen",
            trLater("Winzig – für Handys und Rechner mit 8 GB, wenn nichts anderes schnell genug ist. Kurze Antworten, Umformulieren."),
            "unsloth/Qwen3.5-0.8B-GGUF", "6ab461498e2023f6e3c1baea90a8f0fe38ab64d0", "Qwen3.5-0.8B-Q8_0.gguf",
            811_843_840L, "0ad885ffd4bb022fc4f0d33a3308fa108ef8613159d3b3a67e23abca056b7a6c", 1.6, 8192, true, 0.8,
            purposes = setOf(Purpose.ALLROUND), quality = 0.8, base = "Qwen/Qwen3.5-0.8B", authors = QWEN, kvKb = 16.0, maxContext = 131_072,
            projector = Projector("unsloth/Qwen3.5-0.8B-GGUF", "6ab461498e2023f6e3c1baea90a8f0fe38ab64d0", "mmproj-F16.gguf", 204_987_232L, "56e4c6cfe73b0c82e3e82bc518d7591997e61d81f723fc41a586f4fa69ea2453"),
        ),
        LocalModel(
            "qwen3.5-2b", trLater("Qwen 3.5 2B – Lora Edition"), "Qwen",
            trLater("Sehr klein und schnell – für ältere Handys und Laptops mit 8 GB. Kurze Texte, Umformulieren, Zusammenfassen."),
            "unsloth/Qwen3.5-2B-GGUF", "f6d5376be1edb4d416d56da11e5397a961aca8ae", "Qwen3.5-2B-Q4_K_M.gguf",
            1_280_835_840L, "aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223", 2.4, 8192, true, 2.0,
            purposes = ALL_MAIL + Purpose.CODE, quality = 2.0, base = "Qwen/Qwen3.5-2B", authors = QWEN, kvKb = 16.0, maxContext = 131_072,
            projector = Projector("unsloth/Qwen3.5-2B-GGUF", "f6d5376be1edb4d416d56da11e5397a961aca8ae", "mmproj-F16.gguf", 668_227_264L, "7035e9cb8d7c6a9681d07eef9a364783e86ea4cd73faab2eabb4f43a101830c7"),
        ),
        LocalModel(
            "gemma-4-e2b", trLater("Gemma 4 E2B – Lora Edition"), "Gemma",
            trLater("Kleines Modell von Google mit gutem Deutsch – Handys und Laptops ohne Grafikkarte."),
            "ggml-org/gemma-4-E2B-it-GGUF", "6938c58e698502037401a5f19f6905f2fb2f7dee", "gemma-4-E2B-it-Q4_0.gguf",
            2_841_481_184L, "8e30dff3ac4c8434c49a7036fa15564bdbb6044e42bf04550bf1a096ad7e6a52", 4.0, 8192, true, 2.0, 5.1,
            purposes = setOf(Purpose.TEXT), quality = 2.5, base = "google/gemma-4-E2B-it", authors = GOOGLE, moe = false, kvKb = 16.0, maxContext = 131_072,
            projector = Projector("ggml-org/gemma-4-E2B-it-GGUF", "6938c58e698502037401a5f19f6905f2fb2f7dee", "mmproj-gemma-4-E2B-it-Q8_0.gguf", 557_368_064L, "9406f99c16d68cda4f1f0552192dcc99021ea1fc6d2fd50b1dc3ccf30d04b292"),
        ),
        LocalModel(
            "qwen3.5-4b", trLater("Qwen 3.5 4B – Lora Edition"), "Qwen",
            trLater("Kompakt und vielseitig: strukturiert gut, nutzt Werkzeuge (Dokumente, Rechnen). Für Laptops mit 16 GB."),
            "unsloth/Qwen3.5-4B-GGUF", "e87f176479d0855a907a41277aca2f8ee7a09523", "Qwen3.5-4B-Q4_K_M.gguf",
            2_740_937_888L, "00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4", 4.2, 8192, true, 4.0,
            purposes = ALL_MAIL + Purpose.CODE, quality = 4.0, base = "Qwen/Qwen3.5-4B", authors = QWEN, kvKb = 48.0, maxContext = 131_072,
            projector = Projector("unsloth/Qwen3.5-4B-GGUF", "e87f176479d0855a907a41277aca2f8ee7a09523", "mmproj-F16.gguf", 672_423_616L, "cd88edcf8d031894960bb0c9c5b9b7e1fea6ebee02b9f7ce925a00d12891f864"),
        ),
        LocalModel(
            "qwen3.5-4b-q8", trLater("Qwen 3.5 4B (genauer) – Lora Edition"), "Qwen",
            trLater("Dasselbe Modell mit feinerer Quantisierung (8 Bit) – etwas genauer, braucht mehr Speicher. Für Grafikkarten ab 6 GB und Macs."),
            "unsloth/Qwen3.5-4B-GGUF", "e87f176479d0855a907a41277aca2f8ee7a09523", "Qwen3.5-4B-Q8_0.gguf",
            4_482_403_488L, "10cc391b403021dd11c614679d2fd92f611c3681d29e29651b717316965d61e1", 5.9, 16384, true, 4.0,
            purposes = ALL_MAIL, quality = 4.3, base = "Qwen/Qwen3.5-4B", authors = QWEN, kvKb = 48.0, maxContext = 131_072,
            projector = Projector("unsloth/Qwen3.5-4B-GGUF", "e87f176479d0855a907a41277aca2f8ee7a09523", "mmproj-F16.gguf", 672_423_616L, "cd88edcf8d031894960bb0c9c5b9b7e1fea6ebee02b9f7ce925a00d12891f864"),
        ),
        LocalModel(
            "gemma-4-e4b", trLater("Gemma 4 E4B – Lora Edition"), "Gemma",
            trLater("Beste Sprachqualität ohne Grafikkarte: Briefe, Gutachten, Feedback. Für PCs mit 16 GB und neue Handys."),
            "ggml-org/gemma-4-E4B-it-GGUF", "a69f018d7678a2b9f5e705f8903d6fb29b9b25dc", "gemma-4-E4B-it-Q4_0.gguf",
            4_590_807_392L, "a555b900214b477d8880e7832e0b8925e139b0159640036b09fe472b6f2097f2", 6.0, 8192, true, 4.0, 8.0,
            purposes = setOf(Purpose.TEXT), quality = 4.5, base = "google/gemma-4-E4B-it", authors = GOOGLE, moe = false, kvKb = 24.0, maxContext = 131_072,
            projector = Projector("ggml-org/gemma-4-E4B-it-GGUF", "a69f018d7678a2b9f5e705f8903d6fb29b9b25dc", "mmproj-gemma-4-E4B-it-Q8_0.gguf", 559_874_816L, "197f49a93027f9843772bd24a6a9e0be2a32a788de5a3def330e9c585d86edd1"),
        ),
        LocalModel(
            "qwen2.5-coder-7b", trLater("Qwen 2.5 Coder 7B – Lora Edition"), "Qwen",
            trLater("Spezialist fürs Programmieren (Python, R, JavaScript, SQL, LaTeX) – für Grafikkarten ab 8 GB und schnelle PCs."),
            "unsloth/Qwen2.5-Coder-7B-Instruct-GGUF", "0ecf11859560b2bf42e703207f9371186d02245f", "Qwen2.5-Coder-7B-Instruct-Q4_K_M.gguf",
            4_683_073_504L, "9a961bb225cb2b9fd84b2297df0d53089895c049d7d9dc5f5f8aebbcd3247872", 6.0, 16384, true, 7.6,
            purposes = setOf(Purpose.CODE), quality = 7.0, base = "Qwen/Qwen2.5-Coder-7B-Instruct", authors = QWEN, kvKb = 57.0, maxContext = 32_768,
        ),
        LocalModel(
            "qwen3.5-9b", trLater("Qwen 3.5 9B – Lora Edition"), "Qwen",
            trLater("Deutlich stärker beim Denken und Auswerten – mit Grafikkarte (8 GB) oder 24 GB Arbeitsspeicher."),
            "unsloth/Qwen3.5-9B-GGUF", "3885219b6810b007914f3a7950a8d1b469d598a5", "Qwen3.5-9B-Q4_K_M.gguf",
            5_680_522_464L, "03b74727a860a56338e042c4420bb3f04b2fec5734175f4cb9fa853daf52b7e8", 7.2, 16384, true, 9.0,
            purposes = ALL_MAIL, quality = 9.0, base = "Qwen/Qwen3.5-9B", authors = QWEN, kvKb = 48.0, maxContext = 131_072,
            projector = Projector("unsloth/Qwen3.5-9B-GGUF", "3885219b6810b007914f3a7950a8d1b469d598a5", "mmproj-F16.gguf", 918_166_080L, "f70dc3509053962b0d0d3ee8a7eacebf5d60aa560cad78254ae8698516ae029f"),
        ),
        LocalModel(
            "qwen3.5-9b-q8", trLater("Qwen 3.5 9B (genauer) – Lora Edition"), "Qwen",
            trLater("Qwen 3.5 9B mit 8 Bit statt 4 – genauer bei Zahlen und Fakten. Für Grafikkarten ab 12 GB und Macs ab 24 GB."),
            "unsloth/Qwen3.5-9B-GGUF", "3885219b6810b007914f3a7950a8d1b469d598a5", "Qwen3.5-9B-Q8_0.gguf",
            9_527_502_048L, "809626574d0cb43d4becfa56169980da2bb448f2299270f7be443cb89d0a6ae4", 11.2, 16384, true, 9.0,
            purposes = ALL_MAIL, quality = 9.6, base = "Qwen/Qwen3.5-9B", authors = QWEN, kvKb = 48.0, maxContext = 131_072,
            projector = Projector("unsloth/Qwen3.5-9B-GGUF", "3885219b6810b007914f3a7950a8d1b469d598a5", "mmproj-F16.gguf", 918_166_080L, "f70dc3509053962b0d0d3ee8a7eacebf5d60aa560cad78254ae8698516ae029f"),
        ),
        LocalModel(
            "gemma-4-12b", trLater("Gemma 4 12B – Lora Edition"), "Gemma",
            trLater("Sehr gutes Deutsch und lange Texte – für Grafikkarten ab 10 GB oder PCs mit 32 GB."),
            "ggml-org/gemma-4-12B-it-GGUF", "a29b2a6b584a5bdcb672ccdcda99ea9faeacc51a", "gemma-4-12B-it-Q4_0.gguf",
            7_219_673_216L, "3712b9bd32cae83a22f67ee7a4466d8d7a4f21646ac8a07d19bf9418e8767a70", 9.5, 16384, true, 12.0,
            purposes = setOf(Purpose.TEXT), quality = 12.0, base = "google/gemma-4-12B-it", authors = GOOGLE, kvKb = 64.0, maxContext = 131_072,
            projector = Projector("ggml-org/gemma-4-12B-it-GGUF", "a29b2a6b584a5bdcb672ccdcda99ea9faeacc51a", "mmproj-gemma-4-12B-it-Q8_0.gguf", 158_987_616L, "59e62255435dda870e2d1de97cc031330b31a898bac12b38a182cecff9cd3738"),
        ),
        LocalModel(
            "qwen2.5-coder-14b", trLater("Qwen 2.5 Coder 14B – Lora Edition"), "Qwen",
            trLater("Stärkerer Programmier-Spezialist – für Grafikkarten ab 12 GB und Macs ab 24 GB."),
            "unsloth/Qwen2.5-Coder-14B-Instruct-GGUF", "388f3f20271ef903bb2dbe7d8236158903e7edb3", "Qwen2.5-Coder-14B-Instruct-Q4_K_M.gguf",
            8_988_110_240L, "13dec282d3ac9a87896248d5f69187102fad7c56315c553d2fab5403121702fe", 10.8, 16384, true, 14.7,
            purposes = setOf(Purpose.CODE), quality = 14.0, base = "Qwen/Qwen2.5-Coder-14B-Instruct", authors = QWEN, kvKb = 196.0, maxContext = 32_768,
        ),
        LocalModel(
            "gemma-4-26b-a4b", trLater("Gemma 4 26B A4B – Lora Edition"), "Gemma",
            trLater("Großes Expertenmodell, rechnet aber nur mit 4 Mrd. Parametern – schnell auf Gaming-PCs (16 GB Grafik) oder mit 32 GB RAM."),
            "ggml-org/gemma-4-26B-A4B-it-GGUF", "9cf8c8db0f83fb4582f15381f8fb1a7c1b0b9ca2", "gemma-4-26B-A4B-it-Q4_0.gguf",
            14_618_145_824L, "d208665ab1cd3a69f7a9a4bc59430e8448c8093d9b06334f566ac59d6d504a03", 17.0, 16384, true, 4.0, 26.0,
            purposes = setOf(Purpose.TEXT), quality = 18.0, base = "google/gemma-4-26B-A4B-it", authors = GOOGLE, kvKb = 48.0, maxContext = 131_072,
            projector = Projector("ggml-org/gemma-4-26B-A4B-it-GGUF", "9cf8c8db0f83fb4582f15381f8fb1a7c1b0b9ca2", "mmproj-gemma-4-26B-A4B-it-Q8_0.gguf", 806_408_320L, "cc4e855736da450bf1e162d8cccfe0ad685727d0c9e04ef7dd8d884f3121039b"),
        ),
        LocalModel(
            "qwen3.6-27b", trLater("Qwen 3.6 27B – Lora Edition"), "Qwen",
            trLater("Großes dichtes Modell, sehr gründlich – für Grafikkarten mit 24 GB und Macs ab 32 GB; ohne Grafikkarte zu langsam."),
            "unsloth/Qwen3.6-27B-GGUF", "82d411acf4a06cfb8d9b073a5211bf410bfc29bf", "Qwen3.6-27B-Q4_K_M.gguf",
            16_817_244_384L, "5ed60d0af4650a854b1755bd392f9aef4872643dc25a254bc68043fa638392a0", 19.5, 16384, true, 27.0,
            purposes = ALL_MAIL, quality = 27.0, base = "Qwen/Qwen3.6-27B", authors = QWEN, kvKb = 96.0, maxContext = 131_072,
            projector = Projector("unsloth/Qwen3.6-27B-GGUF", "82d411acf4a06cfb8d9b073a5211bf410bfc29bf", "mmproj-F16.gguf", 927_607_360L, "eacf610d1ee4bd5ed0197a0777dd8f4fceb8eefa27009067c7d496cb68fbde45"),
        ),
        LocalModel(
            "gemma-4-31b", trLater("Gemma 4 31B – Lora Edition"), "Gemma",
            trLater("Das stärkste Gemma für Texte – für Grafikkarten mit 24 GB und Macs ab 32 GB."),
            "unsloth/gemma-4-31B-it-GGUF", "c1ac76e99d5513b141e8adde7288b85c3f9c32ec", "gemma-4-31B-it-Q4_K_M.gguf",
            18_323_733_440L, "38bd64c852c4b460434cc7162fa9bdcf242faf86502581a754cb72956bb17f84", 21.5, 16384, true, 31.0,
            purposes = setOf(Purpose.TEXT), quality = 30.0, base = "google/gemma-4-31B-it", authors = GOOGLE, kvKb = 96.0, maxContext = 131_072,
            projector = Projector("unsloth/gemma-4-31B-it-GGUF", "c1ac76e99d5513b141e8adde7288b85c3f9c32ec", "mmproj-F16.gguf", 1_198_957_024L, "6edcca228213c28d3567a35d22f849eea52d8360875093851959adf5d2f270eb"),
        ),
        LocalModel(
            "qwen3-coder-30b-a3b", trLater("Qwen 3 Coder 30B A3B – Lora Edition"), "Qwen",
            trLater("Der stärkste Programmier-Spezialist, rechnet nur mit 3 Mrd. Parametern – Grafikkarten mit 24 GB, Macs ab 32 GB, PCs ab 48 GB."),
            "unsloth/Qwen3-Coder-30B-A3B-Instruct-GGUF", "b17cb02dd882d5b6ab62fc777ad2995f19668350", "Qwen3-Coder-30B-A3B-Instruct-Q4_K_M.gguf",
            18_556_689_568L, "fadc3e5f8d42bf7e894a785b05082e47daee4df26680389817e2093056f088ad", 20.5, 16384, true, 3.3, 30.5,
            purposes = setOf(Purpose.CODE), quality = 20.0, base = "Qwen/Qwen3-Coder-30B-A3B-Instruct", authors = QWEN, kvKb = 98.0, maxContext = 131_072,
        ),
        LocalModel(
            "qwen3.6-35b-a3b", trLater("Qwen 3.6 35B A3B – Lora Edition"), "Qwen",
            trLater("Das stärkste Modell für High-End-PCs (24 GB Grafik oder 48 GB RAM) – nah an den Server-Modellen."),
            "ggml-org/Qwen3.6-35B-A3B-GGUF", "7878dc9a905c7c9379e5e8d6a1aa9affdd6839c5", "Qwen3.6-35B-A3B-Q4_K_M.gguf",
            20_419_565_568L, "671e47e0ec53c665d048b98c3ecbfd5236b5ca9c3e02ed19fc8f81f7b85140c7", 21.5, 16384, true, 3.0, 35.0,
            purposes = ALL_MAIL + Purpose.CODE, quality = 22.0, base = "Qwen/Qwen3.6-35B-A3B", authors = QWEN, kvKb = 32.0, maxContext = 131_072,
            projector = Projector("ggml-org/Qwen3.6-35B-A3B-GGUF", "7878dc9a905c7c9379e5e8d6a1aa9affdd6839c5", "mmproj-Qwen3.6-35B-A3B-Q8_0.gguf", 614_194_304L, "904cbf8c8e876220066ab3bf676c7efa40f3da372276fdaf8b01d2fb2a37a51d"),
        ),
    )

    fun get(id: String): LocalModel? = models.firstOrNull { it.id == id || it.modelId == id }

    /** This device, once known (set by [LocalLlm]) – for the context length of [context]. */
    @Volatile var hardware: Hardware? = null

    /** Share of the 16-bit size the KV cache takes in 8 bit (q8_0 with its scales). */
    const val KV_Q8 = 0.53

    /**
     * Lange Texte lokal (AP-108): the context length for [m] on [hw] – as much as the memory left beside the model holds
     * with an 8-bit KV cache, at least the catalog value, at most [LocalModel.maxContext], in steps of 4096 tokens.
     */
    fun contextFor(m: LocalModel, hw: Hardware): Int {
        val gpu = hw.vramGb >= 4 && m.needGb <= hw.vramGb * 0.92
        val budget = if (gpu) hw.vramGb * 0.92 else hw.ramGb * if (hw.phone) 0.45 else 0.6
        val free = budget - m.size / 1e9 - 0.8
        val tokens = (free * 1e6 / (m.kvKb * KV_Q8)).toInt()
        return (tokens / 4096 * 4096).coerceIn(m.context, maxOf(m.context, m.maxContext))
    }

    /** The speed forecast on this device with its measurements (set by [LocalLlm]; AP-105). */
    @Volatile var forecaster: ((LocalModel) -> SpeedForecast.Forecast)? = null

    /** Seconds of reading that a request with attachments may take without asking (AP-108). */
    const val QUICK_READ_S = 90.0

    /**
     * Characters [m] reads on this device in about [QUICK_READ_S] – what goes along by itself (the matching passages);
     * null while the device is unknown.
     */
    fun quickChars(m: LocalModel): Int? {
        val hw = hardware ?: return null
        val f = forecaster?.invoke(m) ?: SpeedForecast.forecast(m, hw)
        return (f.readTokS * QUICK_READ_S * LongText.CHARS_PER_TOKEN).toInt()
    }

    /** The context length of [m] on this device ([hardware]), the catalog value while it is unknown. */
    fun context(m: LocalModel): Int = hardware?.let { contextFor(m, it) } ?: m.context

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

    /** Minimum speed of a recommendation (AP-106): words per second while writing and the first word of a short request. */
    const val MIN_WORDS_PER_SEC = 8.0
    const val MAX_FIRST_WORD_MS = 2_000L

    /** A recommendation for one purpose: the model, its forecast, and whether it reaches the minimum speed. */
    data class Pick(val purpose: Purpose, val model: LocalModel, val forecast: SpeedForecast.Forecast, val fastEnough: Boolean)

    /**
     * The model for [purpose] on [hw] (AP-106): of the models for this purpose, the strongest that fits the memory and
     * reaches [MIN_WORDS_PER_SEC] and [MAX_FIRST_WORD_MS] in the [forecast] (AP-105, calibrated by measurements on the
     * device); if none is fast enough, the fastest that fits. Null if nothing fits at all.
     */
    fun pick(purpose: Purpose, hw: Hardware, forecast: (LocalModel) -> SpeedForecast.Forecast = { SpeedForecast.forecast(it, hw) }): Pick? {
        val fitting = models.filter { purpose in it.purposes && fit(it, hw) != Fit.TOO_BIG }.map { it to forecast(it) }
        if (fitting.isEmpty()) return null
        fun ok(f: SpeedForecast.Forecast) = f.wordsPerSec >= MIN_WORDS_PER_SEC && f.firstWordMs() <= MAX_FIRST_WORD_MS
        // A specialist counts more for its own purpose (a code model for code) than a general model of the same size.
        fun score(m: LocalModel) = m.quality * if (m.purposes == setOf(purpose) && purpose != Purpose.ALLROUND) 1.5 else 1.0
        fitting.filter { ok(it.second) }.maxByOrNull { score(it.first) }?.let { return Pick(purpose, it.first, it.second, true) }
        val fastest = fitting.maxByOrNull { it.second.wordsPerSec }!!
        return Pick(purpose, fastest.first, fastest.second, false)
    }

    /**
     * The recommendations for [hw]: the allrounder (standard) and one specialist for each further purpose in [purposes]
     * (loaded only when needed); a purpose whose pick equals another is shown once.
     */
    fun picks(hw: Hardware, purposes: Set<Purpose> = setOf(Purpose.ALLROUND), forecast: (LocalModel) -> SpeedForecast.Forecast = { SpeedForecast.forecast(it, hw) }): List<Pick> =
        (listOf(Purpose.ALLROUND) + Purpose.entries.filter { it != Purpose.ALLROUND && it in purposes })
            .mapNotNull { pick(it, hw, forecast) }.distinctBy { it.model.id }

    /** The recommended models for [hw] (allrounder first). */
    fun recommended(hw: Hardware, purposes: Set<Purpose> = setOf(Purpose.ALLROUND), forecast: (LocalModel) -> SpeedForecast.Forecast = { SpeedForecast.forecast(it, hw) }): List<LocalModel> =
        picks(hw, purposes, forecast).map { it.model }

    fun fitOf(m: LocalModel, hw: Hardware): Fit = if (m in recommended(hw)) Fit.RECOMMENDED else fit(m, hw)

    /**
     * Purposes that follow from the roles of the first start (AP-80) when none were chosen: Verwaltung → Postfach,
     * Lehre and Forschung → Texte. Code only when chosen.
     */
    fun purposes(chosen: String, roles: String): Set<Purpose> {
        Purpose.parse(chosen).takeIf { it.isNotEmpty() }?.let { return it + Purpose.ALLROUND }
        val r = roles.split(',').map { it.trim() }.toSet()
        return buildSet {
            add(Purpose.ALLROUND)
            if ("ADMIN" in r) add(Purpose.MAIL)
            if ("TEACHING" in r || "RESEARCH" in r) add(Purpose.TEXT)
        }
    }

    /**
     * Modellkarte (AP-106): what „Lora Edition“ means, original, authors, licence and source – shown in the app and
     * written into docs/MODELLKATALOG.md.
     */
    fun card(m: LocalModel): String = buildString {
        appendLine(m.name)
        appendLine()
        appendLine(io.github.thepytales.llmhub.ui.tr("„Lora Edition“ heißt: von uns ausgewählt, als fertige Datei übernommen, auf Geräten gemessen und für Lora eingestellt (Kontextlänge, Vorlage). Die Gewichte sind unverändert. Basismodell, Urheber und Lizenz sind die des Originals; die Urheber haben Lora weder geprüft noch gebilligt."))
        appendLine()
        appendLine(io.github.thepytales.llmhub.ui.tr("Original: {0} ({1})", m.original, m.base.ifBlank { "–" }))
        appendLine(io.github.thepytales.llmhub.ui.tr("Urheber: {0}", m.authors.ifBlank { "–" }))
        appendLine(io.github.thepytales.llmhub.ui.tr("Lizenz: {0} (unverändert; Lizenztext beim Original auf Hugging Face)", m.license))
        appendLine(io.github.thepytales.llmhub.ui.tr("Quantisiert von: {0}", m.quantizedBy))
        appendLine(io.github.thepytales.llmhub.ui.tr("Datei: {0} ({1} GB)", m.file, String.format(Locale.GERMANY, "%.2f", m.size / 1e9)))
        appendLine(io.github.thepytales.llmhub.ui.tr("Herkunft: huggingface.co/{0}, Stand {1}", m.repo, m.commit))
        appendLine("SHA-256: ${m.sha256}")
        appendLine(io.github.thepytales.llmhub.ui.tr("Zweck: {0}", m.purposes.joinToString(", ") { io.github.thepytales.llmhub.ui.tr(it.label) }))
    }

    /**
     * Reference devices of the steps (AP-106): RAM 8 to 64 GB without graphics, graphics 4 to 24 GB, Apple chips, Android.
     * Used for the table in docs/MODELLKATALOG.md and the test that every step has a recommendation for every purpose.
     */
    fun steps(): List<Pair<String, Hardware>> {
        val ddr4 = 51.2
        val ddr5 = 89.6
        fun pc(ram: Int, bw: Double, cores: Int) = Hardware(phone = false, ramGb = ram.toDouble(), cores = cores, memBandwidthGbs = bw, cpuFeatures = setOf("avx2"))
        fun gpu(vram: Int, name: String) = Hardware(phone = false, ramGb = 32.0, cores = 8, gpuName = name, vramGb = vram.toDouble(), memBandwidthGbs = ddr5, cpuFeatures = setOf("avx2"))
        fun mac(ram: Int, chip: String) = Hardware(phone = false, mac = true, ramGb = ram.toDouble(), cores = 8, gpuName = "$chip (Metal)", vramGb = ram * 0.66,
            chip = chip, memBandwidthGbs = SpeedForecast.appleChip(chip)!!.first, gpuCores = SpeedForecast.appleChip(chip)!!.second, cpuFeatures = setOf("neon", "dotprod"))
        fun phone(ram: Int) = Hardware(phone = true, ramGb = ram.toDouble(), cores = 8, cpuFeatures = setOf("neon", "dotprod", "i8mm"))
        return listOf(
            "RAM 8 GB (DDR4, 4 Kerne)" to pc(8, ddr4, 4), "RAM 12 GB (DDR4)" to pc(12, ddr4, 6), "RAM 16 GB (DDR4)" to pc(16, ddr4, 6),
            "RAM 16 GB (DDR5)" to pc(16, ddr5, 8), "RAM 24 GB (DDR5)" to pc(24, ddr5, 8), "RAM 32 GB (DDR5)" to pc(32, ddr5, 8),
            "RAM 48 GB (DDR5)" to pc(48, ddr5, 12), "RAM 64 GB (DDR5)" to pc(64, ddr5, 12),
            "Grafik 4 GB" to gpu(4, "NVIDIA GeForce GTX 1650"), "Grafik 6 GB" to gpu(6, "NVIDIA GeForce RTX 3050"),
            "Grafik 8 GB" to gpu(8, "NVIDIA GeForce RTX 4060"), "Grafik 12 GB" to gpu(12, "NVIDIA GeForce RTX 3060"),
            "Grafik 16 GB" to gpu(16, "NVIDIA GeForce RTX 5080"), "Grafik 24 GB" to gpu(24, "NVIDIA GeForce RTX 4090"),
            "Mac M2 16 GB" to mac(16, "Apple M2"), "Mac M4 24 GB" to mac(24, "Apple M4"), "Mac M1 Pro 32 GB" to mac(32, "Apple M1 Pro"),
            "Mac M4 Pro 48 GB" to mac(48, "Apple M4 Pro"), "Mac M3 Max 64 GB" to mac(64, "Apple M3 Max"),
            "Android 8 GB" to phone(8), "Android 12 GB" to phone(12), "Android 16 GB" to phone(16),
        )
    }

    /** For the model picker and the overview; [vision]: its image part is installed (AP-110). */
    fun info(m: LocalModel, vision: Boolean = false) = ModelInfo(
        id = m.modelId,
        type = ModelType.CHAT,
        summary = m.description + trLater(" Läuft auf diesem Gerät – nichts verlässt das Gerät."),
        strengths = trLater("Vertrauliche Daten, offline"),
        contextTokens = context(m),
        vision = vision,
        tools = m.tools,
        label = m.name,
        useCase = trLater("Auf diesem Gerät – vertraulich und offline"),
        tier = ModelTier.FEATURED,
        badge = trLater("Lokal"),
        power = when { m.needGb >= 15 -> 4; m.needGb >= 7 -> 3; m.needGb >= 4 -> 2; else -> 1 },
        speed = when { m.activeB <= 2.5 -> 4; m.activeB <= 4.5 -> 3; else -> 2 },
    )
}
