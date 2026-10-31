package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.ChatRepository
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.data.MemoryMeter
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Less working memory (AP-04): the chat list keeps no long texts, saving a light copy loses nothing, the meter measures. */
class WorkingMemoryTest {
    @Test
    fun chatListKeepsNoLongTextsButSavingLosesNothing() = runBlocking {
        val dir = Files.createTempDirectory("chats").toFile()
        val repo = ChatRepository(Context(dir, File(dir, "cache")))
        repo.awaitLoaded()
        val doc = "Ein langer Anhang. ".repeat(6000)
        val tool = "Ergebnis ".repeat(2000)
        val conv = Conversation(
            id = "c1", model = "m",
            messages = listOf(
                ChatMessage(id = "u", role = "user", content = "Fasse zusammen", files = listOf(FileAttachment("a.pdf", "application/pdf", doc))),
                ChatMessage(id = "t", role = "tool", toolName = "read_file", toolCallId = "1", content = tool),
                ChatMessage(id = "a", role = "assistant", content = "Kurz gesagt …", reasoning = "Überlegung ".repeat(1000)),
            ),
        )
        repo.save(conv)
        val light = repo.conversations.value.single()
        assertTrue(light.messages[0].files.single().text.length < 2000, "Anhang in der Liste gekürzt")
        assertTrue(light.messages[1].content.length < 2000, "Werkzeugergebnis gekürzt")
        assertTrue(light.messages[2].reasoning!!.length < 1000, "Denkweg gekürzt")
        assertEquals("Fasse zusammen", light.messages[0].content, "Fragen und Antworten bleiben ganz (Suche, Gedächtnis)")
        // The full chat comes from get(), and saving the light copy (renaming in the side menu) keeps everything.
        assertEquals(doc, repo.get("c1")!!.messages[0].files.single().text)
        repo.save(light.copy(title = "Umbenannt"))
        repo.awaitWrites()
        val again = ChatRepository(Context(dir, File(dir, "cache")))
        again.awaitLoaded()
        val full = again.get("c1")!!
        assertEquals("Umbenannt", full.title)
        assertEquals(doc, full.messages[0].files.single().text)
        assertEquals(tool, full.messages[1].content)
        assertTrue(again.conversations.value.single().messages[0].files.single().text.length < 2000, "auch nach dem Laden schlank")
    }

    @Test
    fun meterMeasuresAndKeepsPoints() {
        val m = MemoryMeter.mark("Test")
        assertTrue(m.heapUsed in 1..m.heapMax)
        assertEquals("Test", MemoryMeter.samples.value.last().label)
        assertTrue(MemoryMeter.release().heapCommitted > 0)
    }
}
