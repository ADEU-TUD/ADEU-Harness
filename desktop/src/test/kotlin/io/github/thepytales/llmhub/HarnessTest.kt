package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.agent.ArgRepair
import io.github.thepytales.llmhub.agent.ContextManager
import io.github.thepytales.llmhub.agent.Param
import io.github.thepytales.llmhub.agent.ToolDef
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.net.RateLimiter
import io.github.thepytales.llmhub.net.s
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HarnessTest {
    private fun ok(json: String) = assertIs<ArgRepair.Result.Ok>(ArgRepair.parse(json)).args

    @Test
    fun brokenToolArgumentsAreRepaired() {
        assertEquals("a", ok("{\"x\": \"a\"}").s("x"))
        assertEquals("a", ok("```json\n{\"x\": \"a\"}\n```").s("x"))
        assertEquals("Zeile 1\nZeile 2", ok("{\"x\": \"Zeile 1\nZeile 2\"}").s("x"))
        assertEquals("a", ok("{\"x\": \"a\",}").s("x"))
        assertEquals("abgeschnitt", ok("{\"x\": \"abgeschnitt").s("x"))
        assertEquals("[1,2]", ok("{\"x\": [1, 2").get("x").toString())
        assertEquals("a", ok("{'x': 'a'}").s("x"))
        assertTrue(ok("").isEmpty())
        assertIs<ArgRepair.Result.Error>(ArgRepair.parse("kein json"))
    }

    @Test
    fun missingParametersAndTyposAreExplained() {
        val def = ToolDef("create_document", "", listOf(Param("title", "Titel"), Param("content", "Inhalt"), Param("format", "", required = false)))
        val msg = ArgRepair.missing(def, ok("{\"title\": \"\"}"))
        assertNotNull(msg)
        assertTrue("content (Inhalt)" in msg && "title" !in msg.substringAfter("fehlt:"), msg)
        assertNull(ArgRepair.missing(def, ok("{\"title\": \"T\", \"content\": \"C\"}")))
        assertEquals("create_document", ArgRepair.closest("create_documents", listOf("edit_document", "create_document", "web_search")))
        assertNull(ArgRepair.closest("lösche_alles", listOf("create_document")))
    }

    @Test
    fun rateLimiterLearnsFromRefusals() {
        var now = 0L
        val limiter = RateLimiter({ 0 }, { now })
        repeat(10) { assertEquals(0L, limiter.reserve()) }
        assertNull(limiter.limit())
        limiter.onRateLimited()
        assertEquals(9, limiter.limit())
        now = 30_000
        assertTrue(limiter.reserve() > 0, "volles Fenster muss warten")
        now = 61_000
        assertEquals(0L, limiter.reserve())
        now = 61_000 + 16 * 60_000L
        assertNull(limiter.limit(), "gelernte Grenze läuft ab")
        val fixed = RateLimiter({ 2 }, { now })
        assertEquals(0L, fixed.reserve()); assertEquals(0L, fixed.reserve())
        assertTrue(fixed.reserve() > 0)
    }

    @Test
    fun oldToolResultsAreShortened() {
        val long = "x".repeat(5_000)
        val msgs = listOf(
            ChatMessage(role = "user", content = "1"),
            ChatMessage(role = "tool", content = long, toolCallId = "a"),
            ChatMessage(role = "user", content = "2"),
            ChatMessage(role = "tool", content = long, toolCallId = "b"),
        )
        val out = ContextManager.trimOldToolResults(msgs)
        assertTrue(out[1].content.length < 2_000)
        assertEquals(long, out[3].content)
        assertEquals(-1, ContextManager.cutIndex(msgs))
        val longer = msgs + ChatMessage(role = "user", content = "3")
        assertEquals(2, ContextManager.cutIndex(longer))
        assertTrue(ContextManager.budget(ModelCatalog.DEFAULT_CHAT) <= ContextManager.MAX_BUDGET)
    }

    @Test
    fun fallbackIsAnotherCapableModel() {
        val fb = ModelCatalog.fallbackFor(ModelCatalog.DEFAULT_CHAT, needsVision = true, needsTools = true, available = null)
        assertNotNull(fb)
        assertNotEquals(ModelCatalog.DEFAULT_CHAT, fb)
        val info = ModelCatalog.info(fb)
        assertTrue(info.tools == true && info.vision == true)
        assertNull(ModelCatalog.fallbackFor(ModelCatalog.DEFAULT_CHAT, true, true, available = setOf(ModelCatalog.DEFAULT_CHAT)))
    }
}
