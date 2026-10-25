package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.agent.Param
import io.github.thepytales.llmhub.agent.ToolDef
import io.github.thepytales.llmhub.agent.toSchema
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.Folder
import io.github.thepytales.llmhub.data.Privacy
import io.github.thepytales.llmhub.data.PrivacyLevel
import io.github.thepytales.llmhub.desktop.Paths
import io.github.thepytales.llmhub.local.LocalCatalog
import io.github.thepytales.llmhub.net.ThinkingLevel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A real chat with a model on this device: LLAMA_SERVER=<llama-server> LLAMA_MODELS=<folder with
 * Qwen3.5-2B-Q4_K_M.gguf> ./gradlew -p desktop test --tests '*LocalLlmLiveTest*'. Also checks the data protection
 * levels with the real app container.
 */
class LocalLlmLiveTest {
    private val server = System.getenv("LLAMA_SERVER").orEmpty()
    private val models = System.getenv("LLAMA_MODELS").orEmpty()

    @Test
    fun localChatAndPrivacy() {
        assumeTrue(server.isNotBlank() && models.isNotBlank(), "LLAMA_SERVER/LLAMA_MODELS nicht gesetzt – übersprungen")
        val dir = File("build/local-llm-test").apply { mkdirs() }
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", dir.absolutePath)
        System.setProperty("adeu.llamaServer", server)
        val m = LocalCatalog.get("qwen3.5-2b")!!
        val target = File(Paths.local, "llm/models/${m.file}").apply { parentFile.mkdirs() }
        if (!target.exists()) Files.createLink(target.toPath(), File(models, m.file).toPath())

        val c = AppContainer(Context(Paths.data, Paths.cache))
        assertTrue(m.id in c.localLlm.state.value.installed, "Modell erkannt")
        assertTrue(c.models.state.value.models.any { it.id == m.modelId }, "in der Modellauswahl")

        // Data protection: a "Nur auf diesem Gerät" project only offers local models.
        val project = Folder(name = "Videostudie", privacy = PrivacyLevel.LOCAL)
        c.folders.upsert(project)
        val allowed = Privacy.allowedModels(c, project.id, c.models.state.value.models).map { it.id }
        assertEquals(listOf(m.modelId), allowed)
        assertEquals(m.modelId, Privacy.suitableModel(c, project.id, "deepseek-ai/DeepSeek-V4.1-Flash"))

        runBlocking {
            val r = c.llm.streamChat(m.modelId, listOf(ChatMessage(role = "user", content = "Antworte nur mit dem Wort Hallo.")), null, thinking = ThinkingLevel.OFF) { }
            assertTrue("hallo" in r.content.lowercase(), r.content)
            val tool = listOf(ToolDef("rechne", "Rechnet einen Ausdruck exakt aus.", listOf(Param("ausdruck", "z. B. 17*23")))).toSchema()
            val t = c.llm.streamChat(
                m.modelId,
                listOf(ChatMessage(role = "user", content = "Benutze das Werkzeug rechne, um 17*23 auszurechnen.")),
                tool, thinking = ThinkingLevel.OFF,
            ) { }
            println("Werkzeugaufruf: ${t.toolCalls}")
            assertTrue(t.toolCalls.any { it.name == "rechne" }, "Werkzeug aufgerufen: ${t.content}")
            c.localLlm.stop()
        }
    }
}
