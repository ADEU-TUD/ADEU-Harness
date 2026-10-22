package io.github.thepytales.llmhub.data

enum class ModelSort(val label: String) {
    DEFAULT("Standard"),
    POWER("Leistung"),
    SPEED("Tempo"),
    CONTEXT("Kontext"),
    NAME("Name"),
}

/** Search and filter options for the model lists. */
data class ModelFilter(
    val query: String = "",
    /** null = all types. */
    val type: ModelType? = null,
    val thinking: Boolean = false,
    val vision: Boolean = false,
    val tools: Boolean = false,
    val minPower: Int = 0,
    val minSpeed: Int = 0,
    val hideAliases: Boolean = false,
    val sort: ModelSort = ModelSort.DEFAULT,
) {
    val isActive: Boolean
        get() = query.isNotBlank() || type != null || thinking || vision || tools ||
            minPower > 0 || minSpeed > 0 || hideAliases || sort != ModelSort.DEFAULT

    fun matches(m: ModelInfo): Boolean {
        val e = ModelCatalog.effective(m)
        val q = query.trim()
        if (q.isNotEmpty()) {
            val haystack = listOfNotNull(m.id, m.aliasOf, m.summary, m.strengths, m.type.label).joinToString(" ")
            if (q.split(Regex("\\s+")).any { !haystack.contains(it, ignoreCase = true) }) return false
        }
        if (type != null && m.type != type) return false
        if (thinking && e.thinking != true) return false
        if (vision && e.vision != true) return false
        if (tools && e.tools != true) return false
        if (minPower > 0 && (e.power ?: 0) < minPower) return false
        if (minSpeed > 0 && (e.speed ?: 0) < minSpeed) return false
        if (hideAliases && m.isAlias) return false
        return true
    }

    fun apply(models: List<ModelInfo>): List<ModelInfo> {
        val filtered = models.filter(::matches)
        return when (sort) {
            ModelSort.DEFAULT -> filtered
            ModelSort.POWER -> filtered.sortedWith(compareByDescending<ModelInfo> { ModelCatalog.effective(it).power ?: 0 }
                .thenByDescending { ModelCatalog.effective(it).speed ?: 0 })
            ModelSort.SPEED -> filtered.sortedWith(compareByDescending<ModelInfo> { ModelCatalog.effective(it).speed ?: 0 }
                .thenByDescending { ModelCatalog.effective(it).power ?: 0 })
            ModelSort.CONTEXT -> filtered.sortedByDescending { ModelCatalog.effective(it).contextTokens ?: 0 }
            ModelSort.NAME -> filtered.sortedBy { it.displayName.lowercase() }
        }
    }
}
