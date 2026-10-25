package io.github.thepytales.llmhub.net

import io.github.thepytales.llmhub.logFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI

/**
 * The public status page of the Uni server (llm.scads.ai/status): which models answer right now and how fast. Lets
 * the app say "the model is disrupted at the moment" instead of only showing an error, and shows the state in the
 * model overview. Needs no API key; only used for the ScaDS.AI server.
 */
class ServerStatus(private val http: OkHttpClient, private val baseUrl: () -> String) {
    data class ModelState(val name: String, val realName: String, val up: Boolean, val state: String, val seconds: Double?)

    data class Snapshot(val checkedAt: String, val allUp: Boolean, val models: Map<String, ModelState>) {
        /** State of a model by its id or by the model behind an alias. */
        fun of(model: String): ModelState? = models[model.lowercase()]
            ?: models.values.firstOrNull { it.realName.equals(model, ignoreCase = true) }
    }

    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot: StateFlow<Snapshot?> = _snapshot.asStateFlow()
    @Volatile private var fetchedAt = 0L

    /** The status page belongs to llm.scads.ai; other providers have none. */
    private fun statusUrl(): String? {
        val host = runCatching { URI(baseUrl()).host }.getOrNull() ?: return null
        return if (host.endsWith("scads.ai")) "https://$host/status/state.json" else null
    }

    /** Fresh state (at most every [MAX_AGE_MS]); null if the page cannot be reached within a few seconds. */
    suspend fun refresh(force: Boolean = false): Snapshot? {
        val cached = _snapshot.value
        if (!force && cached != null && System.currentTimeMillis() - fetchedAt < MAX_AGE_MS) return cached
        val url = statusUrl() ?: return null
        return withTimeoutOrNull(TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                runCatching {
                    http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                        if (!resp.isSuccessful) return@runCatching null
                        parse(resp.body?.string().orEmpty())
                    }
                }.logFailure("Statusseite laden").getOrNull()
            }
        }?.also {
            _snapshot.value = it
            fetchedAt = System.currentTimeMillis()
        }
    }

    /** One sentence for an error message when the status page reports [model] (or the server) disrupted. */
    suspend fun explain(model: String): String? {
        val s = refresh(force = true) ?: return null
        val m = s.of(model)
        return when {
            m != null && !m.up -> "Laut Statusseite des Uni-Servers ist dieses Modell gerade gestört (${m.state}) – ein anderes Modell wählen oder später erneut versuchen."
            !s.allUp -> "Laut Statusseite des Uni-Servers gibt es gerade Störungen bei einzelnen Modellen."
            else -> null
        }
    }

    companion object {
        private const val MAX_AGE_MS = 120_000L
        private const val TIMEOUT_MS = 5_000L
        private val json = Json { ignoreUnknownKeys = true }

        /** Reads state.json: {"timestamp", "all_up", "models": {group: [{name, real_name, state, time_taken …}]}}. */
        fun parse(text: String): Snapshot {
            val root = json.parseToJsonElement(text) as JsonObject
            val models = (root["models"] as? JsonObject).orEmpty().values.flatMap { (it as? JsonArray).orEmpty() }.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val state = o["state"]?.jsonPrimitive?.contentOrNull.orEmpty()
                ModelState(name, o["real_name"]?.jsonPrimitive?.contentOrNull ?: name, state.equals("up", ignoreCase = true), state, o["time_taken"]?.jsonPrimitive?.doubleOrNull)
            }
            return Snapshot(
                root["timestamp"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                root["all_up"]?.jsonPrimitive?.booleanOrNull ?: models.all { it.up },
                models.associateBy { it.name.lowercase() },
            )
        }
    }
}
