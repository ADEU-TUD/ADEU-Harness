package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.AppContainer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.File

/**
 * Wie oft und wie schnell Lora wo geantwortet hat (AP-71): a small counter on this device – nothing but numbers, no
 * wishes. Shown under Einstellungen › Lora, so one can see what the mixed mode does.
 */
object LoraWays {
    /** [DEVICE]: answered by rules or the command model without any language model; [LOCAL]: a language model on this device; [SERVER]: a server model. */
    enum class Way { DEVICE, LOCAL, SERVER }

    data class Sum(val count: Int = 0, val millis: Long = 0) {
        val average: Long get() = if (count == 0) 0 else millis / count
    }

    private fun file(c: AppContainer) = File(c.appContext.filesDir, "lora-wege.json")

    @Synchronized
    fun read(c: AppContainer): Map<Way, Sum> {
        val o = runCatching { Json.parseToJsonElement(file(c).readText()).jsonObject }.getOrNull() ?: return emptyMap()
        return Way.entries.mapNotNull { w ->
            val e = o[w.name] as? JsonObject ?: return@mapNotNull null
            runCatching { w to Sum(e.getValue("n").jsonPrimitive.int, e.getValue("ms").jsonPrimitive.long) }.getOrNull()
        }.toMap()
    }

    /** Counts one answer of Lora that took [millis] until she replied. */
    @Synchronized
    fun record(c: AppContainer, way: Way, millis: Long) {
        val all = read(c).toMutableMap()
        val old = all[way] ?: Sum()
        all[way] = Sum(old.count + 1, old.millis + millis.coerceIn(0, 600_000))
        runCatching {
            file(c).writeText(buildJsonObject { all.forEach { (w, s) -> put(w.name, buildJsonObject { put("n", s.count); put("ms", s.millis) }) } }.toString())
        }
    }

    @Synchronized
    fun clear(c: AppContainer) { file(c).delete() }

    /** Share of the answers that needed no server, in percent; null before the first answer. */
    fun localShare(sums: Map<Way, Sum>): Int? {
        val total = sums.values.sumOf { it.count }
        if (total == 0) return null
        return 100 * ((sums[Way.DEVICE]?.count ?: 0) + (sums[Way.LOCAL]?.count ?: 0)) / total
    }
}
