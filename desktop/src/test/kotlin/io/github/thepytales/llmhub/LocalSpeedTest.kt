package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.local.LocalCatalog
import io.github.thepytales.llmhub.local.LocalSpeed
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Sprachmodell-Empfehlung nach Messung (AP-76): numbers, what they mean, and the mixed mode only takes fast models. */
class LocalSpeedTest {
    private fun result(id: String, wps: Double, first: Long = 800) = LocalSpeed.Result("local/$id", wps, first, gpu = false)

    @Test
    fun wordsPerSecondCountWordsAfterTheFirst() {
        val text = (1..61).joinToString(" ") { "Wort$it" }
        val run = LocalSpeed.speed(text, firstNs = 1_200_000_000, writeNs = 5_000_000_000)
        assertEquals(12.0, run.wordsPerSec)
        assertEquals(1200, run.firstWordMs)
        // Punctuation alone is not a word.
        assertEquals(1.0, LocalSpeed.speed("Hallo – Welt !", 0, 1_000_000_000).wordsPerSec)
    }

    @Test
    fun theAdviceSaysWhatTheNumbersMean() {
        val twelve = LocalSpeed.advice(result("a", 12.4))
        assertTrue("schafft etwa 12 Wörter je Sekunde" in twelve.text, twelve.text)
        assertTrue(twelve.short && twelve.mail && twelve.confidential)
        val eight = LocalSpeed.advice(result("b", 8.0))
        assertTrue("gut für kurze Aufgaben, zu langsam für lange Mails" in eight.text, eight.text)
        assertTrue(eight.short && !eight.mail)
        val slowStart = LocalSpeed.advice(result("c", 9.0, first = 9_000))
        assertFalse(slowStart.short, "ein erstes Wort nach 9 s ist für kurze Aufgaben zu spät")
        val crawl = LocalSpeed.advice(result("d", 1.5))
        assertFalse(crawl.confidential)
        assertTrue("zu langsam" in crawl.text)
    }

    @Test
    fun picksTakeTheStrongestModelThatIsFastEnough() {
        val small = LocalCatalog.get("qwen3.5-2b")!!
        val mid = LocalCatalog.get("gemma-4-e4b")!!
        val big = LocalCatalog.get("qwen3.5-9b")!!
        val results = mapOf(
            small.modelId to result("qwen3.5-2b", 25.0),
            mid.modelId to result("gemma-4-e4b", 9.0),
            big.modelId to result("qwen3.5-9b", 3.5),
        )
        val p = LocalSpeed.picks(listOf(small, mid, big), results)
        assertEquals(small, p.mail)
        assertEquals(mid, p.short)
        assertEquals(big, p.confidential)
        assertNull(LocalSpeed.picks(listOf(big), mapOf(big.modelId to result("qwen3.5-9b", 1.0))).short)
    }

    @Test
    fun theMixedModeUsesTheMeasurement() {
        val src = File("../app/src/main/java/io/github/thepytales/llmhub/agent/LoraCommands.kt").readText()
        assertTrue("LocalSpeed.goodForShort(c, it, hw)" in src)
    }
}
