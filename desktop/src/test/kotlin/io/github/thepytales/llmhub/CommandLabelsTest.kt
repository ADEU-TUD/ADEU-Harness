package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.agent.LoraCommands
import io.github.thepytales.llmhub.video.VideoLora
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Befehle auf dem Gerät (AP-71, Stufe a): the labels of the command model come from the registers of the app. The test
 * writes them to build/befehle/etiketten.json (copied to tools/befehle/etiketten.json for the training) and keeps the
 * labels the shipped model knows (resources/befehle/labels.json) inside what the code offers.
 */
class CommandLabelsTest {
    private fun catalog(): List<LoraCommands.Entry> {
        VideoLora.register()
        return LoraCommands.catalog(android = false)
    }

    @Test
    fun catalogComesFromTheRegistersOfTheApp() {
        val entries = catalog()
        val labels = entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size, "Etiketten sind eindeutig")
        assertTrue(LoraCommands.LOCAL_ACTIONS.all { id -> entries.any { it.label == LoraCommands.ACT + id } }, "jede lokale Aktion gibt es im Code: " +
            LoraCommands.LOCAL_ACTIONS.filter { id -> entries.none { it.label == LoraCommands.ACT + id } })
        assertTrue(entries.any { it.label == "ort:option.appearance.theme" } && entries.any { it.label == "stell:theme" } && entries.any { it.label == "ort:mode.literature" })
        val json = JsonArray(entries.map { e ->
            buildJsonObject {
                put("label", e.label); put("art", e.kind); put("titel", e.title); put("hinweis", e.hint)
                e.area?.let { put("bereich", it) }
                put("android", JsonPrimitive(e.android))
            }
        })
        File("build/befehle").mkdirs()
        File("build/befehle/etiketten.json").writeText(kotlinx.serialization.json.Json { prettyPrint = true }.encodeToString(JsonArray.serializer(), json))
    }
}
