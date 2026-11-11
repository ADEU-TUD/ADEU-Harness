package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.ui.trLater
import java.net.URI

/**
 * Wohin die Daten gehen (AP-119, Wunsch des Kollegen): next to every model and server „lokal“ (green – stays on this
 * device), „TU-Server“ (yellow – the Uni server of the TU Dresden, ScaDS.AI) or „Cloud“ (red – a provider outside the
 * TU, e.g. Google Gemini). Only presentation: the facts come from [Privacy] and the provider settings.
 */
enum class DataPlace(val label: String, val explanation: String) {
    LOCAL(trLater("lokal"), trLater("Die Daten bleiben auf diesem Gerät.")),
    TU(trLater("TU-Server"), trLater("Die Daten gehen an den Uni-Server der TU Dresden (ScaDS.AI).")),
    CLOUD(trLater("Cloud"), trLater("Die Daten gehen an einen Anbieter außerhalb der TU Dresden.")),
    ;

    companion object {
        /** Hosts of the TU Dresden and its centre ScaDS.AI (Uni server). */
        private val TU_HOSTS = listOf("tu-dresden.de", "scads.ai", "scads.de", "zih.tu-dresden.de")

        /** Where requests to [url] go: this device (localhost), the TU or elsewhere. Unknown or empty: Cloud. */
        fun ofUrl(url: String?): DataPlace {
            val host = runCatching { URI(url?.trim().orEmpty()).host }.getOrNull()?.lowercase()?.trim('[', ']') ?: return CLOUD
            return when {
                host == "localhost" || host == "::1" || host.startsWith("127.") || host == "0.0.0.0" -> LOCAL
                TU_HOSTS.any { host == it || host.endsWith(".$it") } -> TU
                else -> CLOUD
            }
        }

        /** Where the chat model [id] runs: on this device, at the main provider (its address decides) or elsewhere. */
        fun ofModel(c: AppContainer, id: String): DataPlace {
            if (Privacy.isLocal(id)) return LOCAL
            val provider = runCatching { c.llm.providerFor(id) }.getOrNull()
            if (provider != null) return ofUrl(provider.baseUrl)
            if (ModelCatalog.info(id).via != null) return CLOUD
            return main(c)
        }

        /** The main provider (Einstellungen › API-Zugang, normally ScaDS.AI). */
        fun main(c: AppContainer): DataPlace = ofUrl(c.settings.current.baseUrl).let { if (it == CLOUD && c.settings.current.baseUrl.isBlank()) TU else it }

        /** An MCP server: a program on this computer (command) is local, a web address by its host. */
        fun ofServer(url: String?, command: String?): DataPlace = if (!command.isNullOrBlank() && url.isNullOrBlank()) LOCAL else ofUrl(url)
    }
}
