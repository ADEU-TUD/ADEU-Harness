package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.net.LlmClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ModelsState(
    val loading: Boolean = false,
    /** Model ids reported by the server; null until fetched successfully. */
    val liveIds: List<String>? = null,
    val error: String? = null,
) {
    /** All models to show: live list if available, otherwise the built-in catalog. */
    val models: List<ModelInfo>
        get() = (liveIds?.map { ModelCatalog.info(it) } ?: ModelCatalog.known)
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

class ModelsRepository(private val llm: LlmClient) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ModelsState())
    val state: StateFlow<ModelsState> = _state.asStateFlow()

    fun refresh() {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, error = null)
        scope.launch {
            _state.value = try {
                ModelsState(liveIds = llm.listModels())
            } catch (e: Exception) {
                _state.value.copy(loading = false, error = e.message ?: e.javaClass.simpleName)
            }
        }
    }
}
