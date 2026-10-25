package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.net.ServerStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The public status page of llm.scads.ai (format of status/state.json as of October 2026). */
class ServerStatusTest {
    private val sample = """
        {"timestamp": "2026-10-25T14:40:00+00:00", "all_up": false, "models": {
          "Chat": [
            {"name": "alias-huge", "real_name": "zai-org/GLM-5.3", "state": "up", "time_taken": 0.79},
            {"name": "zai-org/GLM-5.3", "real_name": "zai-org/GLM-5.3", "state": "up", "time_taken": 0.61},
            {"name": "MiniMaxAI/MiniMax-M3", "real_name": "MiniMaxAI/MiniMax-M3", "state": "down", "time_taken": null}
          ],
          "Rerank": [{"name": "Qwen/Qwen3-Reranker-4B", "real_name": "Qwen/Qwen3-Reranker-4B", "state": "up", "time_taken": 0.09}]
        }}
    """.trimIndent()

    /** Against the real status page: `LIVE_WEB=1 ./gradlew -p desktop test --tests '*ServerStatusTest*'`. */
    @Test
    fun liveStatusPage() {
        if (System.getenv("LIVE_WEB") != "1") return
        val status = ServerStatus(okhttp3.OkHttpClient()) { "https://llm.scads.ai/v1" }
        val s = kotlinx.coroutines.runBlocking { status.refresh(force = true) }
        assertTrue(s != null && s.models.isNotEmpty(), "Statusseite nicht lesbar")
        assertTrue(s.of("Qwen/Qwen3-Reranker-4B") != null)
    }

    @Test
    fun readsStatesByIdAndAlias() {
        val s = ServerStatus.parse(sample)
        assertFalse(s.allUp)
        assertEquals(4, s.models.size)
        assertTrue(s.of("zai-org/GLM-5.3")!!.up)
        assertEquals(0.79, s.of("ALIAS-HUGE")!!.seconds)
        assertFalse(s.of("MiniMaxAI/MiniMax-M3")!!.up)
        assertEquals("down", s.of("MiniMaxAI/MiniMax-M3")!!.state)
        assertEquals(null, s.of("unbekannt"))
    }
}
