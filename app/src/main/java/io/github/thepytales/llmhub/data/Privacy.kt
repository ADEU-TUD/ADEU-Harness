package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.AppContainer

/**
 * Enforces the data protection level of a project ([PrivacyLevel]): which models a chat may use and whether data
 * may leave the device. Used by the model picker, the agent, knowledge search and speech input.
 */
object Privacy {
    /** Models that run on this device (llama.cpp); see local/LocalCatalog. */
    const val LOCAL_PREFIX = "local/"

    fun level(c: AppContainer, folderId: String?): PrivacyLevel =
        folderId?.let { id -> c.folders.items.value.firstOrNull { it.id == id }?.privacy } ?: PrivacyLevel.OPEN

    /** Level of a chat: in the vault (AP-10) at least „Nur TU Dresden“ – only the Uni server or local models. */
    fun level(c: AppContainer, conv: Conversation): PrivacyLevel {
        val project = level(c, conv.folderId)
        return if (conv.vault && project == PrivacyLevel.OPEN) PrivacyLevel.TU else project
    }

    /** Why [model] may not be used in the chat [conv], null if it may. */
    fun blockReason(c: AppContainer, conv: Conversation, model: String): String? {
        if (conv.vault && !allows(c, level(c, conv), model)) {
            return "Im Tresor sind nur der Uni-Server und Modelle auf diesem Gerät erlaubt – ${ModelCatalog.info(model).displayName} läuft bei " +
                "einem externen Anbieter. Bitte ein Modell des Uni-Servers wählen."
        }
        return blockReason(c, conv.folderId, model)
    }

    fun isLocal(model: String) = model.startsWith(LOCAL_PREFIX)

    /** Served by a provider outside the TU Dresden (e.g. Gemini or an own OpenAI-compatible server). */
    fun isExternal(c: AppContainer, model: String): Boolean =
        !isLocal(model) && (ModelCatalog.info(model).via != null || c.llm.providerFor(model) != null)

    fun allows(c: AppContainer, level: PrivacyLevel, model: String): Boolean = when (level) {
        PrivacyLevel.OPEN -> true
        PrivacyLevel.TU -> !isExternal(c, model)
        PrivacyLevel.LOCAL -> isLocal(model)
    }

    fun allowedModels(c: AppContainer, folderId: String?, models: List<ModelInfo>): List<ModelInfo> {
        val level = level(c, folderId)
        return if (level == PrivacyLevel.OPEN) models else models.filter { allows(c, level, it.id) }
    }

    /**
     * [model] if the project allows it, else a model that fits its level: the strongest one installed on this device
     * ("Nur auf diesem Gerät") or the default model of the Uni server ("Nur TU Dresden").
     */
    fun suitableModel(c: AppContainer, folderId: String?, model: String): String {
        val level = level(c, folderId)
        if (allows(c, level, model)) return model
        return when (level) {
            PrivacyLevel.LOCAL -> c.localLlm.preferredModel() ?: model
            PrivacyLevel.TU -> c.settings.current.defaultModel.takeIf { allows(c, level, it) } ?: ModelCatalog.DEFAULT_CHAT
            PrivacyLevel.OPEN -> model
        }
    }

    /** Why [model] may not be used in this project, null if it may. */
    fun blockReason(c: AppContainer, folderId: String?, model: String): String? {
        val level = level(c, folderId)
        if (allows(c, level, model)) return null
        return when (level) {
            PrivacyLevel.LOCAL -> "Dieses Projekt hat die Datenschutzstufe „Nur auf diesem Gerät“. Bitte ein Modell auf diesem Gerät wählen " +
                "(Einstellungen › Modelle auf diesem Gerät) – ${ModelCatalog.info(model).displayName} läuft auf einem Server."
            PrivacyLevel.TU -> "Dieses Projekt hat die Datenschutzstufe „Nur TU Dresden“. ${ModelCatalog.info(model).displayName} läuft bei einem " +
                "externen Anbieter – bitte ein Modell des Uni-Servers oder eines auf diesem Gerät wählen."
            PrivacyLevel.OPEN -> null
        }
    }
}
