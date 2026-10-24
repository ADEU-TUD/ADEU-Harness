package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.net.LlmClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ModelsState(
    val loading: Boolean = false,
    /** Model ids reported by the servers (main provider first); null until one was fetched successfully. */
    val liveIds: List<String>? = null,
    val error: String? = null,
    /** Model id (lowercase) → id of the further provider that serves it; models of the main provider are not in here. */
    val owners: Map<String, String> = emptyMap(),
    /** Built-in models shown for servers whose list is missing (no key yet, offline). */
    val fallback: List<ModelInfo> = ModelCatalog.main,
) {
    /** All models to show: the live lists plus the built-in entries of the servers that could not be asked. */
    val models: List<ModelInfo>
        get() = ((liveIds?.map { ModelCatalog.info(it) } ?: emptyList()) + fallback)
            .distinctBy { it.id.lowercase() }
            .sortedWith(compareBy<ModelInfo>({ it.type.ordinal }, { it.tier.ordinal }, { ModelCatalog.rank(it.id) }, { it.id.lowercase() }))

    fun ofType(type: ModelType) = models.filter { it.type == type }

    /** Chat models that are offered in the picker, best first (see [ModelTier]). */
    val pickable: List<ModelInfo>
        get() = pickerModels(ofType(ModelType.CHAT))
}

/** Leaves out hidden models and sorts by the order of the built-in catalog. */
fun pickerModels(models: List<ModelInfo>): List<ModelInfo> =
    models.filter { it.type == ModelType.CHAT && it.tier != ModelTier.HIDDEN }
        .sortedWith(compareBy<ModelInfo>({ ModelCatalog.rank(it.id) }, { it.id.lowercase() }))

/**
 * Model lists of the main provider (ScaDS.AI) and of the further ones (e.g. Gemini). Tells [LlmClient] which
 * provider serves which model.
 */
class ModelsRepository(private val llm: LlmClient, private val settings: SettingsStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ModelsState(fallback = fallbackFor(settings.current, mainFailed = true, failed = settings.current.providers)))
    val state: StateFlow<ModelsState> = _state.asStateFlow()

    fun refresh() {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, error = null)
        scope.launch {
            val s = settings.current
            val extra = s.providers.filter { it.usable }
            val main = if (s.hasApiKey || extra.isEmpty()) async { runCatching { llm.listModels() } } else null
            val others = extra.map { p -> p to async { runCatching { llm.listModels(p) } } }
            val mainResult = main?.await()
            val mainIds = mainResult?.getOrNull()
            val otherResults = others.map { (p, r) -> p to r.await() }
            val mainSet = mainIds.orEmpty().map { it.lowercase() }.toSet()
            val owners = buildMap {
                otherResults.forEach { (p, r) -> r.getOrNull()?.forEach { id -> if (id.lowercase() !in mainSet) putIfAbsent(id.lowercase(), p.id) } }
            }
            llm.owners = owners
            val live = (mainIds.orEmpty() + otherResults.flatMap { it.second.getOrNull().orEmpty() }).distinctBy { it.lowercase() }
            val anyLive = mainIds != null || otherResults.any { it.second.isSuccess }
            val errors = listOfNotNull(mainResult?.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }) +
                otherResults.mapNotNull { (p, r) -> r.exceptionOrNull()?.let { "${p.name}: ${it.message ?: it.javaClass.simpleName}" } }
            _state.value = ModelsState(
                liveIds = live.takeIf { anyLive },
                error = errors.joinToString("\n").ifEmpty { null },
                owners = owners,
                fallback = fallbackFor(
                    s,
                    mainFailed = main != null && mainIds == null,
                    failed = otherResults.filter { it.second.isFailure }.map { it.first },
                ),
            )
        }
    }

    /**
     * Built-in entries for the servers without a live list: the main catalog while ScaDS.AI cannot be asked
     * (also before a key is entered, unless only further providers are set up) and e.g. the Gemini models.
     */
    private fun fallbackFor(s: AppSettings, mainFailed: Boolean, failed: List<ApiProvider>): List<ModelInfo> =
        (if (mainFailed && (s.hasApiKey || s.providers.none { it.usable })) ModelCatalog.main else emptyList()) +
            failed.filter { it.usable }.flatMap { ModelCatalog.of(it.providerKind) }
}
