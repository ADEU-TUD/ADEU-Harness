package io.github.thepytales.llmhub.data
import io.github.thepytales.llmhub.ui.trLater

enum class ModelType(val label: String) {
    CHAT("Chat"),
    IMAGE(trLater("Bildgenerierung")),
    TTS(trLater("Sprachausgabe")),
    STT(trLater("Spracherkennung")),
    EMBEDDING(trLater("Embedding")),
    RERANK(trLater("Rerank")),
    EVALUATION(trLater("Evaluation")),
}

/** Where a chat model appears in the model picker. */
enum class ModelTier {
    /** Shown at the top of the picker – the best models, each with a clear purpose. */
    FEATURED,
    /** Shown under „Weitere Modelle“. */
    MORE,
    /** Not offered for chats (outdated or only an alias of another model). */
    HIDDEN,
}

data class ModelInfo(
    val id: String,
    val type: ModelType,
    val summary: String,
    val strengths: String = "",
    val contextTokens: Int? = null,
    val thinking: Boolean? = null,
    val vision: Boolean? = null,
    val tools: Boolean? = null,
    /** For aliases: the model the alias currently points to. */
    val aliasOf: String? = null,
    /** Rough performance rating 1..5 used for the overview. */
    val power: Int? = null,
    /** Rough speed rating 1..5 used for the overview. */
    val speed: Int? = null,
    /** Readable name, e.g. "DeepSeek V4.1 Flash"; defaults to the part after the provider. */
    val label: String? = null,
    /** One line in the picker that says what the model is good for. */
    val useCase: String = "",
    val tier: ModelTier = if (aliasOf != null) ModelTier.HIDDEN else ModelTier.MORE,
    /** Small label next to the name in the picker, e.g. "Neu". */
    val badge: String? = null,
    /** Further provider whose key the model needs (null = main provider ScaDS.AI). */
    val via: ProviderKind? = null,
) {
    val isAlias get() = aliasOf != null
    val displayName get() = label ?: id.substringAfter('/')
    val provider get() = if ('/' in id) id.substringBefore('/') else null
}

/**
 * Built-in descriptions of the models offered by the university endpoint (state of the status page
 * at llm.scads.ai/status). Models returned by /v1/models that are unknown here get a generic entry.
 */
object ModelCatalog {
    /** Default chat model: fast, sees images, uses tools and has a huge context. */
    const val DEFAULT_CHAT = "deepseek-ai/DeepSeek-V4.1-Flash"
    /** Fastest model for small helper requests (learning the memory) and very long documents. */
    const val FAST_CHAT = "zai-org/GLM-5.3-Flash"
    /** Most thorough model, for the hardest tasks (automatic model choice). */
    const val HARD_CHAT = "zai-org/GLM-5.3"
    /** Model for programming and GitHub (automatic model choice). */
    const val CODE_CHAT = "Qwen/Qwen3.8-27B"

    val known: List<ModelInfo> = listOf(
        // ---- Chat
        ModelInfo(
            id = "deepseek-ai/DeepSeek-V4.1-Flash",
            type = ModelType.CHAT,
            summary = trLater("Schnelles Mixture-of-Experts-Modell von DeepSeek mit 1 Mio. Token Kontext."),
            strengths = trLater("Stark bei Programmierung, Mathematik und Logik; unterstützt Denkmodus, Bilder und Werkzeuge."),
            contextTokens = 1048576, thinking = true, vision = true, tools = true, power = 4, speed = 4,
            label = trLater("DeepSeek V4.1 Flash"), tier = ModelTier.FEATURED,
            useCase = trLater("Für fast alles: Fragen, Texte, Bilder, Websuche und Dokumente"),
        ),
        ModelInfo(
            id = "Qwen/Qwen3.8-27B",
            type = ModelType.CHAT,
            summary = trLater("Kompaktes Allround-Modell von Alibaba mit Denkmodus, Bildverständnis und Werkzeugnutzung."),
            strengths = trLater("Sehr gutes Preis-Leistungs-Verhältnis, stark bei Code, Mathe und mehrsprachigen Texten. Gute Wahl für die GitHub-Werkzeuge."),
            contextTokens = 262144, thinking = true, vision = true, tools = true, power = 4, speed = 4,
            label = "Qwen3.8 27B", tier = ModelTier.FEATURED,
            useCase = trLater("Programmieren und GitHub-Aufgaben – zuverlässig mit Werkzeugen"),
        ),
        ModelInfo(
            id = "zai-org/GLM-5.3",
            type = ModelType.CHAT,
            summary = trLater("Großes Flaggschiff-Modell von Zhipu AI für anspruchsvolle Aufgaben."),
            strengths = trLater("Eines der leistungsstärksten Modelle im Angebot: komplexes Schlussfolgern, lange Dokumente, agentisches Programmieren. Kein Bildverständnis, etwas langsamer."),
            contextTokens = 524288, thinking = true, vision = false, tools = true, power = 5, speed = 2,
            label = "GLM-5.3", tier = ModelTier.FEATURED,
            useCase = trLater("Für die schwierigsten Aufgaben – gründlich, aber langsamer. Versteht keine Bilder."),
        ),
        ModelInfo(
            id = "zai-org/GLM-5.3-Flash",
            type = ModelType.CHAT,
            summary = trLater("Schnelle, leichtere Variante von GLM-5.3 mit riesigem Kontextfenster."),
            strengths = trLater("1 Mio. Token Kontext, Bildverständnis und Werkzeuge – ideal für große Repositories und lange Dokumente bei hoher Geschwindigkeit."),
            contextTokens = 1048576, thinking = true, vision = true, tools = true, power = 4, speed = 5,
            label = trLater("GLM-5.3 Flash"), tier = ModelTier.FEATURED,
            useCase = trLater("Am schnellsten – auch für sehr lange Dokumente und große Projekte"),
        ),
        ModelInfo(
            id = "MiniMaxAI/MiniMax-M3",
            type = ModelType.CHAT,
            summary = trLater("Großes Modell von MiniMax, ausgelegt auf Agenten- und Programmieraufgaben."),
            strengths = trLater("Gut bei mehrstufigen Aufgaben mit vielen Werkzeugaufrufen, langen Kontexten und Bildern."),
            contextTokens = 524288, thinking = true, vision = true, tools = true, power = 5, speed = 3,
            label = "MiniMax M3",
            useCase = trLater("Lange Agenten-Aufgaben mit vielen Werkzeugschritten"),
        ),
        ModelInfo(
            id = "openai/gpt-oss-120b",
            type = ModelType.CHAT,
            summary = trLater("Offenes Reasoning-Modell von OpenAI (120 Mrd. Parameter, MoE)."),
            strengths = trLater("Solides Schlussfolgern und zuverlässige Werkzeugaufrufe. Kein Bildverständnis, 128k Kontext."),
            contextTokens = 131072, thinking = true, vision = false, tools = true, power = 4, speed = 4,
            label = "gpt-oss 120B",
            useCase = trLater("Logisches Schlussfolgern und Rechnen – versteht keine Bilder"),
        ),
        ModelInfo(
            id = "google/gemma-4-26B-A4B-it",
            type = ModelType.CHAT,
            summary = trLater("Effizientes MoE-Modell von Google (26 Mrd. Parameter, ~4 Mrd. aktiv)."),
            strengths = trLater("Sehr schnell, multimodal (Bilder), Denkmodus und Werkzeuge. Gut für Alltagsfragen und Bildanalyse."),
            contextTokens = 262144, thinking = true, vision = true, tools = true, power = 3, speed = 5,
            label = trLater("Gemma 4 26B"),
            useCase = trLater("Sehr schnell für kurze Fragen und Bildbeschreibungen"),
        ),
        // ---- Chat over a Gemini key (Einstellungen › API-Zugang › Weitere Anbieter); best models of the free API plan.
        ModelInfo(
            id = "gemini-3.8-flash",
            type = ModelType.CHAT,
            summary = trLater("Neuestes Flash-Modell von Google – über den eigenen Gemini-API-Key."),
            strengths = trLater("Schnell, versteht Bilder, nutzt Werkzeuge und denkt bei Bedarf nach; 1 Mio. Token Kontext. " +
                "Im kostenlosen API-Plan mit Anfragen pro Minute und Tag begrenzt."),
            contextTokens = 1048576, thinking = true, vision = true, tools = true, power = 4, speed = 5,
            label = trLater("Gemini 3.8 Flash"), tier = ModelTier.FEATURED, badge = "Gemini", via = ProviderKind.GEMINI,
            useCase = trLater("Googles bestes kostenloses Modell – schnell, mit Bildern und Werkzeugen"),
        ),
        ModelInfo(
            id = "gemini-3.6-flash",
            type = ModelType.CHAT,
            summary = trLater("Flash-Modell von Google – über den eigenen Gemini-API-Key."),
            strengths = trLater("Bewährt und schnell, versteht Bilder und nutzt Werkzeuge. Eigenes Kontingent im kostenlosen API-Plan."),
            contextTokens = 1048576, thinking = true, vision = true, tools = true, power = 4, speed = 5,
            label = trLater("Gemini 3.6 Flash"), tier = ModelTier.FEATURED, badge = "Gemini", via = ProviderKind.GEMINI,
            useCase = trLater("Ausweiche, wenn das Tageslimit von 3.8 Flash erreicht ist"),
        ),
        ModelInfo(
            id = "gemini-3.5-flash",
            type = ModelType.CHAT,
            summary = trLater("Älteres Flash-Modell von Google – über den eigenen Gemini-API-Key."),
            strengths = trLater("Schnell, versteht Bilder und nutzt Werkzeuge. Eigenes Kontingent im kostenlosen API-Plan."),
            contextTokens = 1048576, thinking = true, vision = true, tools = true, power = 3, speed = 5,
            label = trLater("Gemini 3.5 Flash"), tier = ModelTier.FEATURED, badge = "Gemini", via = ProviderKind.GEMINI,
            useCase = trLater("Weitere Ausweiche mit eigenem Kontingent"),
        ),
        ModelInfo(
            id = "meta-llama/Llama-3.3-70B-Instruct",
            type = ModelType.CHAT,
            summary = trLater("Bewährtes 70B-Modell von Meta ohne Denkmodus."),
            strengths = trLater("Schnelle, direkte Antworten und gutes Deutsch. Kein Bildverständnis, kleinerer Kontext (64k). Werkzeuge werden unterstützt."),
            contextTokens = 65536, thinking = false, vision = false, tools = true, power = 3, speed = 4,
            label = trLater("Llama 3.3 70B"), tier = ModelTier.HIDDEN,
            useCase = trLater("Älteres Modell – die anderen sind in allem besser"),
        ),
        // ---- Chat aliases
        ModelInfo(
            id = "alias-code", type = ModelType.CHAT, aliasOf = "Qwen/Qwen3.8-27B",
            summary = trLater("Alias für Programmieraufgaben."),
            strengths = trLater("Zeigt immer auf das empfohlene Code-Modell. Werkzeuge zuverlässig verfügbar."),
            contextTokens = 262144, tools = true, power = 4, speed = 4,
        ),
        ModelInfo(
            id = "alias-ha", type = ModelType.CHAT, aliasOf = "meta-llama/Llama-3.3-70B-Instruct",
            summary = trLater("Alias für Hochverfügbarkeit."),
            strengths = trLater("Ziel: möglichst stabile Erreichbarkeit, gut für einfache Chats."),
            contextTokens = 65536, power = 3, speed = 4,
        ),
        ModelInfo(
            id = "alias-huge", type = ModelType.CHAT, aliasOf = "zai-org/GLM-5.3",
            summary = trLater("Alias für das größte verfügbare Modell."),
            strengths = trLater("Maximale Qualität für schwierige Fragen."),
            contextTokens = 524288, power = 5, speed = 2,
        ),
        ModelInfo(
            id = "alias-huge-no-thinking", type = ModelType.CHAT, aliasOf = "zai-org/GLM-5.3",
            summary = trLater("Größtes Modell ohne Denkphase."),
            strengths = trLater("Hohe Qualität, aber schnellere Antworten, da kein Reasoning vorgeschaltet wird."),
            contextTokens = 524288, power = 5, speed = 3,
        ),
        ModelInfo(
            id = "alias-reasoning", type = ModelType.CHAT, aliasOf = "google/gemma-4-26B-A4B-it",
            summary = trLater("Alias für Reasoning (Denkmodus)."),
            strengths = trLater("Zeigt auf ein Modell mit zuverlässigem Denkmodus."),
            contextTokens = 262144, thinking = true, power = 3, speed = 5,
        ),
        ModelInfo(
            id = "alias-vision", type = ModelType.CHAT, aliasOf = "google/gemma-4-26B-A4B-it",
            summary = trLater("Alias für Bildverständnis."),
            strengths = trLater("Nur für Anfragen mit Bildern gedacht – nicht für Werkzeuge verwenden."),
            contextTokens = 262144, vision = true, power = 3, speed = 5,
        ),
        // ---- Audio
        ModelInfo(
            id = "OmniVoice", type = ModelType.TTS,
            summary = trLater("Text-zu-Sprache-Modell."),
            strengths = trLater("Liest Texte vor; in der App über den Lautsprecher-Knopf an Antworten nutzbar."),
        ),
        ModelInfo(
            id = "tts-1-hd", type = ModelType.TTS,
            summary = trLater("Text-zu-Sprache in hoher Qualität (OpenAI-kompatible Schnittstelle)."),
            strengths = trLater("Natürlich klingende Sprachausgabe für Antworten."),
        ),
        ModelInfo(
            id = "alias-stt", type = ModelType.STT, aliasOf = "openai/whisper-large-v3-turbo",
            summary = trLater("Alias für Spracherkennung."),
            strengths = trLater("Wird vom Mikrofon-Knopf im Chat genutzt, um Sprache in Text umzuwandeln."),
        ),
        ModelInfo(
            id = "openai/whisper-large-v3-turbo", type = ModelType.STT,
            summary = trLater("Whisper Large v3 Turbo – schnelle, mehrsprachige Spracherkennung."),
            strengths = trLater("Sehr gute Erkennung auch für Deutsch; transkribiert Sprachaufnahmen."),
        ),
        // ---- Embedding / rerank / evaluation
        ModelInfo(
            id = "google/embeddinggemma-2", type = ModelType.EMBEDDING,
            summary = trLater("Embedding-Modell von Google."),
            strengths = trLater("Wandelt Texte in Vektoren um – für semantische Suche und RAG (nicht zum Chatten)."),
        ),
        ModelInfo(
            id = "Qwen/Qwen3-Embedding-4B", type = ModelType.EMBEDDING,
            summary = trLater("Mehrsprachiges Embedding-Modell von Qwen (4 Mrd. Parameter)."),
            strengths = trLater("Hochwertige Vektoren für Suche, Clustering und RAG."),
        ),
        ModelInfo(
            id = "Qwen/Qwen3-Reranker-4B", type = ModelType.RERANK,
            summary = trLater("Reranker von Qwen."),
            strengths = trLater("Sortiert Suchergebnisse nach Relevanz zu einer Anfrage."),
        ),
        ModelInfo(
            id = "Cloudflare/clef-flash", type = ModelType.EVALUATION,
            summary = "Evaluationsmodell.",
            strengths = trLater("Bewertet bzw. klassifiziert Texte; nicht für normale Chats gedacht."),
        ),
        // ---- Images
        ModelInfo(
            id = "alias-image-generation", type = ModelType.IMAGE, aliasOf = "black-forest-labs/FLUX.2-klein-4B",
            summary = trLater("Alias für Bildgenerierung."),
            strengths = trLater("Schnelles Standard-Modell für Bilder aus Text."),
            power = 3, speed = 5,
        ),
        ModelInfo(
            id = "black-forest-labs/FLUX.2-dev", type = ModelType.IMAGE,
            summary = trLater("FLUX.2 dev – hochwertige Bildgenerierung von Black Forest Labs."),
            strengths = trLater("Detailreiche, fotorealistische Bilder und gute Textdarstellung; langsamer."),
            power = 5, speed = 2,
        ),
        ModelInfo(
            id = "black-forest-labs/FLUX.2-klein-4B", type = ModelType.IMAGE,
            summary = trLater("FLUX.2 klein (4 Mrd. Parameter) – kleine, schnelle Variante."),
            strengths = trLater("Ergebnisse in Sekunden, gut zum Ausprobieren von Ideen."),
            power = 3, speed = 5,
        ),
    )

    private val byId = known.associateBy { it.id.lowercase() }

    /** Models of the main provider (ScaDS.AI). */
    val main: List<ModelInfo> get() = known.filter { it.via == null }

    /** Built-in models of a further provider, shown while its model list cannot be loaded. */
    fun of(kind: ProviderKind): List<ModelInfo> = known.filter { it.via == kind }

    fun info(id: String): ModelInfo =
        byId[id.lowercase()] ?: io.github.thepytales.llmhub.local.LocalCatalog.get(id)?.let(io.github.thepytales.llmhub.local.LocalCatalog::info) ?: guess(id)

    /**
     * A model to answer with when [model] is unreachable: recommended chat models first, preferably from another
     * provider (another server behind the gateway), with tools and – if the chat has images – vision.
     */
    fun fallbackFor(model: String, needsVision: Boolean, needsTools: Boolean, available: Set<String>?): String? {
        val current = info(model)
        return known.asSequence()
            .filter { it.type == ModelType.CHAT && it.tier != ModelTier.HIDDEN && !it.id.equals(model, ignoreCase = true) }
            .filter { !needsTools || it.tools == true }
            .filter { !needsVision || it.vision == true }
            // Models of a further provider only when its server listed them (the key is there and works).
            .filter { if (it.via != null) available?.contains(it.id) == true else available == null || it.id in available }
            .sortedWith(compareBy({ it.tier.ordinal }, { if (it.provider == current.provider) 1 else 0 }))
            .firstOrNull()?.id
    }

    /** Position in the built-in list; unknown models come last. */
    fun rank(id: String): Int = known.indexOfFirst { it.id.equals(id, ignoreCase = true) }.let { if (it < 0) Int.MAX_VALUE else it }

    /** For aliases: fills unknown capabilities and ratings from the model the alias points to. */
    fun effective(m: ModelInfo): ModelInfo {
        val target = m.aliasOf?.let { byId[it.lowercase()] } ?: return m
        return m.copy(
            contextTokens = m.contextTokens ?: target.contextTokens,
            thinking = m.thinking ?: target.thinking,
            vision = m.vision ?: target.vision,
            tools = m.tools ?: target.tools,
            power = m.power ?: target.power,
            speed = m.speed ?: target.speed,
        )
    }

    private fun guess(id: String): ModelInfo {
        val l = id.lowercase()
        if (l.startsWith("gemini-")) return gemini(id)
        val type = when {
            "whisper" in l || "stt" in l || "transcri" in l -> ModelType.STT
            "tts" in l || "voice" in l || "speech" in l -> ModelType.TTS
            "flux" in l || "image" in l || "diffusion" in l -> ModelType.IMAGE
            "embed" in l -> ModelType.EMBEDDING
            "rerank" in l -> ModelType.RERANK
            else -> ModelType.CHAT
        }
        return ModelInfo(
            id = id,
            type = type,
            summary = "Neues Modell – noch keine Beschreibung in der App hinterlegt.",
            strengths = "Siehe Statusseite für die unterstützten Funktionen.",
            useCase = "Neu auf dem Server – noch ohne Beschreibung in der App",
            tier = if (l.startsWith("alias-")) ModelTier.HIDDEN else ModelTier.MORE,
            badge = "Neu",
        )
    }

    /** Further Gemini models of the key's model list: chat models under „Weitere Modelle“, special ones hidden. */
    private fun gemini(id: String): ModelInfo {
        val l = id.lowercase()
        val type = when {
            "embedding" in l -> ModelType.EMBEDDING
            "tts" in l -> ModelType.TTS
            "image" in l -> ModelType.IMAGE
            else -> ModelType.CHAT
        }
        val special = GEMINI_SPECIAL.any { it in l }
        return ModelInfo(
            id = id,
            type = type,
            summary = "Modell von Google – über den eigenen Gemini-API-Key.",
            strengths = "Siehe ai.google.dev für Funktionen und die Grenzen des kostenlosen API-Plans.",
            vision = true.takeIf { type == ModelType.CHAT }, tools = true.takeIf { type == ModelType.CHAT && !special },
            label = id.split('-').joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } },
            useCase = "Weiteres Gemini-Modell",
            tier = if (type == ModelType.CHAT && !special) ModelTier.MORE else ModelTier.HIDDEN,
            badge = "Gemini",
            via = ProviderKind.GEMINI,
        )
    }

    /** Gemini variants that are not general chat models or only previews. */
    private val GEMINI_SPECIAL = listOf("preview", "exp", "live", "audio", "robotics", "computer-use", "aqa", "learnlm")
}
