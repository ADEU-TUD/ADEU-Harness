package io.github.thepytales.llmhub.data

import kotlinx.serialization.Serializable
import java.util.UUID

/** A folder on this PC the agent may work in (Einstellungen › Lokale Ordner). */
@Serializable
data class LocalFolder(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String,
    val path: String,
    /** false: the agent may only read. */
    val write: Boolean = true,
)
