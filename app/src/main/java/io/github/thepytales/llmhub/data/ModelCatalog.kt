package io.github.thepytales.llmhub.data

enum class ModelType(val label: String) {
    CHAT("Chat"),
    IMAGE("Bildgenerierung"),
    TTS("Sprachausgabe"),
    STT("Spracherkennung"),
    EMBEDDING("Embedding"),
    RERANK("Rerank"),
    EVALUATION("Evaluation"),
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
) {
    val isAlias get() = aliasOf != null
    val displayName get() = id.substringAfter('/')
    val provider get() = if ('/' in id) id.substringBefore('/') else null
}

/**
 * Built-in descriptions of the models offered by the university endpoint (state of the status page
 * at llm.scads.ai/status). Models returned by /v1/models that are unknown here get a generic entry.
 */
object ModelCatalog {
    val known: List<ModelInfo> = listOf(
        // ---- Chat
        ModelInfo(
            id = "Qwen/Qwen3.8-27B",
            type = ModelType.CHAT,
            summary = "Kompaktes Allround-Modell von Alibaba mit Denkmodus, Bildverständnis und Werkzeugnutzung.",
            strengths = "Sehr gutes Preis-Leistungs-Verhältnis, stark bei Code, Mathe und mehrsprachigen Texten. Gute Wahl für die GitHub-Werkzeuge.",
            contextTokens = 262144, thinking = true, vision = true, tools = true, power = 4, speed = 4,
        ),
        ModelInfo(
            id = "zai-org/GLM-5.3",
            type = ModelType.CHAT,
            summary = "Großes Flaggschiff-Modell von Zhipu AI für anspruchsvolle Aufgaben.",
            strengths = "Eines der leistungsstärksten Modelle im Angebot: komplexes Schlussfolgern, lange Dokumente, agentisches Programmieren. Kein Bildverständnis, etwas langsamer.",
            contextTokens = 524288, thinking = true, vision = false, tools = true, power = 5, speed = 2,
        ),
        ModelInfo(
            id = "zai-org/GLM-5.3-Flash",
            type = ModelType.CHAT,
            summary = "Schnelle, leichtere Variante von GLM-5.3 mit riesigem Kontextfenster.",
            strengths = "1 Mio. Token Kontext, Bildverständnis und Werkzeuge – ideal für große Repositories und lange Dokumente bei hoher Geschwindigkeit.",
            contextTokens = 1048576, thinking = true, vision = true, tools = true, power = 4, speed = 5,
        ),
        ModelInfo(
            id = "deepseek-ai/DeepSeek-V4.1-Flash",
            type = ModelType.CHAT,
            summary = "Schnelles Mixture-of-Experts-Modell von DeepSeek mit 1 Mio. Token Kontext.",
            strengths = "Stark bei Programmierung, Mathematik und Logik; unterstützt Denkmodus, Bilder und Werkzeuge.",
            contextTokens = 1048576, thinking = true, vision = true, tools = true, power = 4, speed = 4,
        ),
        ModelInfo(
            id = "MiniMaxAI/MiniMax-M3",
            type = ModelType.CHAT,
            summary = "Großes Modell von MiniMax, ausgelegt auf Agenten- und Programmieraufgaben.",
            strengths = "Gut bei mehrstufigen Aufgaben mit vielen Werkzeugaufrufen, langen Kontexten und Bildern.",
            contextTokens = 524288, thinking = true, vision = true, tools = true, power = 5, speed = 3,
        ),
        ModelInfo(
            id = "openai/gpt-oss-120b",
            type = ModelType.CHAT,
            summary = "Offenes Reasoning-Modell von OpenAI (120 Mrd. Parameter, MoE).",
            strengths = "Solides Schlussfolgern und zuverlässige Werkzeugaufrufe. Kein Bildverständnis, 128k Kontext.",
            contextTokens = 131072, thinking = true, vision = false, tools = true, power = 4, speed = 4,
        ),
        ModelInfo(
            id = "google/gemma-4-26B-A4B-it",
            type = ModelType.CHAT,
            summary = "Effizientes MoE-Modell von Google (26 Mrd. Parameter, ~4 Mrd. aktiv).",
            strengths = "Sehr schnell, multimodal (Bilder), Denkmodus und Werkzeuge. Gut für Alltagsfragen und Bildanalyse.",
            contextTokens = 262144, thinking = true, vision = true, tools = true, power = 3, speed = 5,
        ),
        ModelInfo(
            id = "meta-llama/Llama-3.3-70B-Instruct",
            type = ModelType.CHAT,
            summary = "Bewährtes 70B-Modell von Meta ohne Denkmodus.",
            strengths = "Schnelle, direkte Antworten und gutes Deutsch. Kein Bildverständnis, kleinerer Kontext (64k). Werkzeuge werden unterstützt.",
            contextTokens = 65536, thinking = false, vision = false, tools = true, power = 3, speed = 4,
        ),
        // ---- Chat aliases
        ModelInfo(
            id = "alias-code", type = ModelType.CHAT, aliasOf = "Qwen/Qwen3.8-27B",
            summary = "Alias für Programmieraufgaben.",
            strengths = "Zeigt immer auf das empfohlene Code-Modell. Werkzeuge zuverlässig verfügbar.",
            contextTokens = 262144, tools = true, power = 4, speed = 4,
        ),
        ModelInfo(
            id = "alias-ha", type = ModelType.CHAT, aliasOf = "meta-llama/Llama-3.3-70B-Instruct",
            summary = "Alias für Hochverfügbarkeit.",
            strengths = "Ziel: möglichst stabile Erreichbarkeit, gut für einfache Chats.",
            contextTokens = 65536, power = 3, speed = 4,
        ),
        ModelInfo(
            id = "alias-huge", type = ModelType.CHAT, aliasOf = "zai-org/GLM-5.3",
            summary = "Alias für das größte verfügbare Modell.",
            strengths = "Maximale Qualität für schwierige Fragen.",
            contextTokens = 524288, power = 5, speed = 2,
        ),
        ModelInfo(
            id = "alias-huge-no-thinking", type = ModelType.CHAT, aliasOf = "zai-org/GLM-5.3",
            summary = "Größtes Modell ohne Denkphase.",
            strengths = "Hohe Qualität, aber schnellere Antworten, da kein Reasoning vorgeschaltet wird.",
            contextTokens = 524288, power = 5, speed = 3,
        ),
        ModelInfo(
            id = "alias-reasoning", type = ModelType.CHAT, aliasOf = "google/gemma-4-26B-A4B-it",
            summary = "Alias für Reasoning (Denkmodus).",
            strengths = "Zeigt auf ein Modell mit zuverlässigem Denkmodus.",
            contextTokens = 262144, thinking = true, power = 3, speed = 5,
        ),
        ModelInfo(
            id = "alias-vision", type = ModelType.CHAT, aliasOf = "google/gemma-4-26B-A4B-it",
            summary = "Alias für Bildverständnis.",
            strengths = "Nur für Anfragen mit Bildern gedacht – nicht für Werkzeuge verwenden.",
            contextTokens = 262144, vision = true, power = 3, speed = 5,
        ),
        // ---- Audio
        ModelInfo(
            id = "OmniVoice", type = ModelType.TTS,
            summary = "Text-zu-Sprache-Modell.",
            strengths = "Liest Texte vor; in der App über den Lautsprecher-Knopf an Antworten nutzbar.",
        ),
        ModelInfo(
            id = "tts-1-hd", type = ModelType.TTS,
            summary = "Text-zu-Sprache in hoher Qualität (OpenAI-kompatible Schnittstelle).",
            strengths = "Natürlich klingende Sprachausgabe für Antworten.",
        ),
        ModelInfo(
            id = "alias-stt", type = ModelType.STT, aliasOf = "openai/whisper-large-v3-turbo",
            summary = "Alias für Spracherkennung.",
            strengths = "Wird vom Mikrofon-Knopf im Chat genutzt, um Sprache in Text umzuwandeln.",
        ),
        ModelInfo(
            id = "openai/whisper-large-v3-turbo", type = ModelType.STT,
            summary = "Whisper Large v3 Turbo – schnelle, mehrsprachige Spracherkennung.",
            strengths = "Sehr gute Erkennung auch für Deutsch; transkribiert Sprachaufnahmen.",
        ),
        // ---- Embedding / rerank / evaluation
        ModelInfo(
            id = "google/embeddinggemma-2", type = ModelType.EMBEDDING,
            summary = "Embedding-Modell von Google.",
            strengths = "Wandelt Texte in Vektoren um – für semantische Suche und RAG (nicht zum Chatten).",
        ),
        ModelInfo(
            id = "Qwen/Qwen3-Embedding-4B", type = ModelType.EMBEDDING,
            summary = "Mehrsprachiges Embedding-Modell von Qwen (4 Mrd. Parameter).",
            strengths = "Hochwertige Vektoren für Suche, Clustering und RAG.",
        ),
        ModelInfo(
            id = "Qwen/Qwen3-Reranker-4B", type = ModelType.RERANK,
            summary = "Reranker von Qwen.",
            strengths = "Sortiert Suchergebnisse nach Relevanz zu einer Anfrage.",
        ),
        ModelInfo(
            id = "Cloudflare/clef-flash", type = ModelType.EVALUATION,
            summary = "Evaluationsmodell.",
            strengths = "Bewertet bzw. klassifiziert Texte; nicht für normale Chats gedacht.",
        ),
        // ---- Images
        ModelInfo(
            id = "alias-image-generation", type = ModelType.IMAGE, aliasOf = "black-forest-labs/FLUX.2-klein-4B",
            summary = "Alias für Bildgenerierung.",
            strengths = "Schnelles Standard-Modell für Bilder aus Text.",
            power = 3, speed = 5,
        ),
        ModelInfo(
            id = "black-forest-labs/FLUX.2-dev", type = ModelType.IMAGE,
            summary = "FLUX.2 dev – hochwertige Bildgenerierung von Black Forest Labs.",
            strengths = "Detailreiche, fotorealistische Bilder und gute Textdarstellung; langsamer.",
            power = 5, speed = 2,
        ),
        ModelInfo(
            id = "black-forest-labs/FLUX.2-klein-4B", type = ModelType.IMAGE,
            summary = "FLUX.2 klein (4 Mrd. Parameter) – kleine, schnelle Variante.",
            strengths = "Ergebnisse in Sekunden, gut zum Ausprobieren von Ideen.",
            power = 3, speed = 5,
        ),
    )

    private val byId = known.associateBy { it.id.lowercase() }

    fun info(id: String): ModelInfo = byId[id.lowercase()] ?: guess(id)

    private fun guess(id: String): ModelInfo {
        val l = id.lowercase()
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
        )
    }
}
