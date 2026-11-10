package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.data.LoraHistory
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Lora-Verlauf mit „Nochmal“ (AP-75): the last 20 wishes, Pfeil hoch, pinned commands in Strg+K. */
class LoraHistoryTest {
    @Test
    fun keepsTheLastTwentyNewestFirstAndEachOnlyOnce() {
        var list = emptyList<LoraHistory.Entry>()
        for (i in 1..25) list = LoraHistory.add(list, "Wunsch $i", at = i.toLong())
        assertEquals(LoraHistory.MAX, list.size)
        assertEquals("Wunsch 25", list.first().text)
        assertTrue(list.none { it.text == "Wunsch 5" })
        // The same wish again moves to the front instead of appearing twice.
        list = LoraHistory.add(list, "  wunsch 10 ", at = 100)
        assertEquals("wunsch 10", list.first().text)
        assertEquals(1, list.count { it.text.equals("wunsch 10", true) })
    }

    @Test
    fun pinnedWishesStayAndAreFoundByName() {
        var list = listOf(LoraHistory.Entry("Dunkles Design einschalten", at = 0, name = "Nachtmodus"))
        for (i in 1..30) list = LoraHistory.add(list, "Wunsch $i", at = i.toLong())
        assertTrue(list.any { it.pinned && it.name == "Nachtmodus" }, "angeheftet bleibt auch nach 30 neuen Wünschen")
        assertEquals(LoraHistory.MAX, list.count { !it.pinned })
        // Sending a pinned wish again keeps its name.
        list = LoraHistory.add(list, "Dunkles Design einschalten", at = 99)
        assertEquals("Nachtmodus", list.first().name)
        val hits = LoraHistory.match(list.filter { it.pinned }, "nacht")
        assertEquals(listOf("Dunkles Design einschalten"), hits.map { it.text })
        assertEquals(1, LoraHistory.match(list.filter { it.pinned }, "dunkles").size)
        assertTrue(LoraHistory.match(list.filter { it.pinned }, "tabelle").isEmpty())
    }

    @Test
    fun arrowKeysBrowseAndReturnToTheOwnText() {
        // −1 is the own (empty) text, 0 the newest wish.
        assertEquals(0, LoraHistory.browse(size = 3, index = -1, up = true))
        assertEquals(2, LoraHistory.browse(size = 3, index = 1, up = true))
        assertNull(LoraHistory.browse(size = 3, index = 2, up = true))
        assertEquals(-1, LoraHistory.browse(size = 3, index = 0, up = false))
        assertNull(LoraHistory.browse(size = 3, index = -1, up = false))
        assertNull(LoraHistory.browse(size = 0, index = -1, up = true))
    }

    @Test
    fun theTresorAndTheSwitchAreRespected() {
        val src = File("../app/src/main/java/io/github/thepytales/llmhub/data/LoraHistory.kt").readText()
        assertTrue("if (vault || !c.settings.current.loraHistory) return false" in src)
        val panel = File("../app/src/main/java/io/github/thepytales/llmhub/ui/LoraHelper.kt").readText()
        assertTrue("LoraHistory.record(c, wish, c.drive.isVaultChat(LoraHelper.chatId))" in panel)
        val palette = File("../app/src/main/java/io/github/thepytales/llmhub/ui/CommandPalette.kt").readText()
        assertTrue("Key.DirectionUp -> browse(up = true)" in palette && "LoraHistory.search(c, query)" in palette)
    }
}
