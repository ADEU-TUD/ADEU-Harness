package io.github.thepytales.llmhub.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** One download of a release: the Windows ZIP or the Android APK. [url] is missing in a folder on a network drive. */
data class UpdateFile(val name: String, val url: String?, val sha256: String, val size: Long)

/**
 * A reloadable extension of the Windows app (AP-25), e.g. the Videoeditor-Paket: a ZIP that is set up on first use
 * into %LOCALAPPDATA%\ADEU LLM\tools\<id>, comes with the releases of the app and can be removed again.
 */
data class ExtensionFile(val id: String, val name: String, val version: String, val description: String, val file: UpdateFile)

/**
 * What version.json of a release in the update repository says (written by .github/scripts/publish-update.sh):
 * ```
 * {"version": "1.0.37", "published": "2026-10-26", "notes": "Was ist neu …",
 *  "windows": {"file": "ADEU-LLM-1.0.37-Windows.zip", "url": "https://…", "sha256": "…", "size": 123},
 *  "android": {"file": "ADEU-LLM-1.0.37.apk", "url": "https://…", "sha256": "…", "size": 123},
 *  "extensions": [{"id": "videoeditor", "name": "Videoeditor-Paket", "version": "1", "description": "…",
 *                  "file": "Lora-Videoeditor-1.zip", "url": "https://…", "sha256": "…", "size": 123}]}
 * ```
 */
data class UpdateInfo(
    val version: String,
    val notes: String,
    val published: String?,
    val files: Map<String, UpdateFile>,
    val extensions: List<ExtensionFile> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): UpdateInfo {
            val o = json.parseToJsonElement(text).jsonObject
            fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            val version = o.str("version") ?: error("version.json enthält keine Versionsnummer.")
            val files = listOf("windows", "android").mapNotNull { key ->
                val f = (o[key] as? JsonObject) ?: return@mapNotNull null
                val name = f.str("file") ?: return@mapNotNull null
                val sha = f.str("sha256")?.lowercase() ?: return@mapNotNull null
                key to UpdateFile(name, f.str("url"), sha, f["size"]?.jsonPrimitive?.longOrNull ?: 0)
            }.toMap()
            val extensions = (o["extensions"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { el ->
                val e = el as? JsonObject ?: return@mapNotNull null
                val id = e.str("id") ?: return@mapNotNull null
                val name = e.str("file") ?: return@mapNotNull null
                val sha = e.str("sha256")?.lowercase() ?: return@mapNotNull null
                ExtensionFile(
                    id, e.str("name") ?: id, e.str("version") ?: "1", e.str("description").orEmpty(),
                    UpdateFile(name, e.str("url"), sha, e["size"]?.jsonPrimitive?.longOrNull ?: 0),
                )
            }
            return UpdateInfo(version, o.str("notes").orEmpty(), o.str("published"), files, extensions)
        }
    }
}

object Versions {
    /** "1.0.37" → [1, 0, 37]; a suffix like "-dev" is ignored. */
    private fun parts(v: String) = v.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }

    /** Negative if [a] is older than [b], 0 if equal, positive if newer. */
    fun compare(a: String, b: String): Int {
        val x = parts(a)
        val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }

    fun newer(candidate: String, installed: String) = compare(candidate, installed) > 0
}
