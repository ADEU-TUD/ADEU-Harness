package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Eine Hausstil-Regel der Professur (AP-79): ein Ausdruck, der so nicht stehen soll, die gewünschte Schreibweise und
 * ein kurzer Grund – z. B. „Lehrer“ → „Lehrkräfte“ (Gendern), „TU-Dresden“ → „TU Dresden“, „bzgl.“ → „bezüglich“.
 * Leeres [preferred] heißt: nur hinweisen, nichts vorschlagen.
 */
@Serializable
data class HouseRule(
    @SerialName("falsch") val avoid: String,
    @SerialName("richtig") val preferred: String = "",
    @SerialName("hinweis") val note: String = "",
    /** Whole words only (default), or also inside words. */
    @SerialName("ganzesWort") val wholeWord: Boolean = true,
)

/** The own house rules (Einstellungen › Dokumente & Logos › Hausstil), on this device; shared with AP-83. */
object HouseStyle {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun file(c: AppContainer) = File(c.appContext.filesDir, "hausstil.json")
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version
    @Volatile private var cache: List<HouseRule>? = null

    @Synchronized
    fun rules(c: AppContainer): List<HouseRule> = cache ?: runCatching { json.decodeFromString<List<HouseRule>>(file(c).readText()) }
        .getOrDefault(emptyList()).also { cache = it }

    @Synchronized
    fun save(c: AppContainer, rules: List<HouseRule>) {
        val clean = rules.filter { it.avoid.isNotBlank() }.distinctBy { it.avoid.lowercase() }
        runCatching { file(c).apply { parentFile?.mkdirs() }.writeText(json.encodeToString(clean)) }
        cache = clean
        _version.value++
    }

    /** The own rules and those of imported Lora-Pakete (AP-83). */
    fun all(c: AppContainer): List<HouseRule> = (rules(c) + KnowledgePacks.installed(c).flatMap { it.houseRules }).distinctBy { it.avoid.lowercase() }
}
