package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.agent.Memory
import io.github.thepytales.llmhub.agent.MemoryLearner
import io.github.thepytales.llmhub.agent.RepoContext
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.Folder
import io.github.thepytales.llmhub.data.MemoryCategory
import io.github.thepytales.llmhub.data.MemoryEntry
import io.github.thepytales.llmhub.data.PrivacyLevel
import io.github.thepytales.llmhub.data.ModelCatalog
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Files
import java.util.Calendar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The memory goes along with every request, knows projects and end dates, finds earlier chats and learns. */
class MemoryTest {
    private fun <T> withContainer(block: (AppContainer) -> T): T {
        val base = Files.createTempDirectory("adeu-gedaechtnis").toFile()
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(base, "daten").absolutePath)
        return try {
            block(AppContainer(Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() })))
        } finally {
            base.deleteRecursively()
        }
    }

    private fun args(vararg pairs: Pair<String, String>) = JsonObject(pairs.associate { it.first to JsonPrimitive(it.second) })

    @Test
    fun memoryIsInEveryRequestAlsoWithoutTools() = withContainer { c ->
        c.memory.upsert(MemoryEntry(text = "Unterrichtet im Master das Modul Allgemeine Didaktik.", category = MemoryCategory.TEACHING.name))
        c.memory.upsert(MemoryEntry(text = "Mag kurze Absätze und die Sie-Form.", category = MemoryCategory.STYLE.name))
        val conv = Conversation(model = ModelCatalog.DEFAULT_CHAT, messages = listOf(ChatMessage(role = "user", content = "Hallo")))
        fun system(conv: Conversation, tools: Boolean) =
            c.agent.buildApiMessages(conv, RepoContext(mutableListOf()), if (tools) c.agent.providers(conv) else emptyList()).first().content
        for (tools in listOf(true, false)) {
            val s = system(conv, tools)
            assertTrue("Allgemeine Didaktik" in s && "Sie-Form" in s, "Gedächtnis mit Werkzeugen=$tools: $s")
            assertTrue("Lehre:" in s && "Schreibstil:" in s, "nach Bereichen geordnet")
        }
        // Switched off for one chat: nothing goes along.
        assertFalse("Allgemeine Didaktik" in system(conv.copy(memoryOff = true), true))
        // Switched off in the settings: neither.
        c.settings.update { it.copy(memoryMode = "OFF") }
        assertFalse("Allgemeine Didaktik" in system(conv, true))
    }

    @Test
    fun projectEntriesAndEndDates() = withContainer { c ->
        val seminar = Folder(name = "Seminar")
        c.folders.upsert(seminar)
        val yesterday = System.currentTimeMillis() - 24 * 3600_000L
        c.memory.upsert(MemoryEntry(text = "Allgemein bekannt"))
        c.memory.upsert(MemoryEntry(text = "Gilt nur im Seminar", projectId = seminar.id))
        c.memory.upsert(MemoryEntry(text = "Längst vorbei", expiresAt = yesterday))
        assertEquals(listOf("Allgemein bekannt"), Memory.entries(c, null).map { it.text })
        assertEquals(setOf("Allgemein bekannt", "Gilt nur im Seminar"), Memory.entries(c, seminar.id).map { it.text }.toSet())
        val block = Memory.promptFor(c, seminar.id)!!
        assertTrue("Nur für das Projekt „Seminar“" in block, block)
        assertFalse("Längst vorbei" in block)

        // remember: area, project and end date from the model.
        val ctx = RepoContext(mutableListOf(), folderId = seminar.id)
        runBlocking {
            c.assistantTools.run("remember", args("text" to "Antrag bei der DFG läuft", "category" to "PROJECTS", "scope" to "projekt", "valid_until" to "2027-03-31"), ctx)
        }
        val saved = c.memory.current.first { it.text == "Antrag bei der DFG läuft" }
        assertEquals(MemoryCategory.PROJECTS.name, saved.category)
        assertEquals(seminar.id, saved.projectId)
        val end = Calendar.getInstance().apply { timeInMillis = saved.expiresAt!! }
        assertEquals(listOf(2027, 2, 31), listOf(end.get(Calendar.YEAR), end.get(Calendar.MONTH), end.get(Calendar.DAY_OF_MONTH)))
        assertEquals(Memory.parseDate("2027-03-31"), Memory.parseDate("31.03.2027"))
        assertNull(Memory.parseDate("bald"))
    }

    @Test
    fun earlierChatsAreFoundButStrictProjectsStayOut() = withContainer { c ->
        runBlocking { c.chats.awaitLoaded() }
        val local = Folder(name = "Interviews", privacy = PrivacyLevel.LOCAL)
        c.folders.upsert(local)
        fun chat(title: String, text: String, folder: String? = null): Conversation {
            val question = ChatMessage(role = "user", content = text)
            return Conversation(
                title = title, model = ModelCatalog.DEFAULT_CHAT, folderId = folder,
                messages = listOf(question, ChatMessage(role = "assistant", content = "Gern.", parentId = question.id)),
            ).also { c.chats.save(it) }
        }
        val expose = chat("Exposé Feedback", "Gib mir Feedback zum Methodenteil des Exposés von Herrn Weber.")
        chat("Interview Schule", "Methodenteil des Interviews mit der Schulleitung", folder = local.id)
        val current = Conversation(model = ModelCatalog.DEFAULT_CHAT, messages = listOf(ChatMessage(role = "user", content = "Wie letztes Mal beim Methodenteil?")))

        val found = Memory.searchChats(c, current, ModelCatalog.DEFAULT_CHAT, "Methodenteil", null)
        assertTrue("Exposé Feedback" in found, found)
        assertFalse("Interview Schule" in found, "Chats aus „Nur auf diesem Gerät“ gehen nicht an den Server: $found")
        assertTrue("Herrn Weber" in Memory.searchChats(c, current, ModelCatalog.DEFAULT_CHAT, "", expose.id))
        assertTrue("„Exposé Feedback“" in Memory.chatsHint(c, current, ModelCatalog.DEFAULT_CHAT)!!)
        // Without the setting: no hint.
        c.settings.update { it.copy(memoryChats = false) }
        assertNull(Memory.chatsHint(c, current, ModelCatalog.DEFAULT_CHAT))
    }

    @Test
    fun learnerSuggestionsAreParsedAndFiltered() = withContainer { c ->
        val answer = "Hier:\n```json\n[{\"text\": \"Leitet das Seminar Unterrichtsplanung im Master.\", \"category\": \"TEACHING\"}, " +
            "{\"text\": \"Abgabe des Antrags bis 31.01.2027.\", \"category\": \"PROJECTS\", \"scope\": \"projekt\", \"valid_until\": \"2027-01-31\"}, " +
            "{\"text\": \"x\"}]\n```"
        val found = MemoryLearner.parse(answer, "projekt-1")
        assertEquals(2, found.size)
        assertEquals(MemoryCategory.TEACHING.name, found[0].category)
        assertNull(found[0].projectId)
        assertEquals("projekt-1", found[1].projectId)
        assertNotNull(found[1].expiresAt)
        assertTrue(found.all { it.source == "learned" })
        assertTrue(MemoryLearner.parse("Nichts Dauerhaftes.", null).isEmpty())
        // Already known (also with other punctuation) is not suggested again.
        c.memory.upsert(MemoryEntry(text = "Leitet das Seminar Unterrichtsplanung im Master"))
        assertTrue(Memory.known(c, "leitet das Seminar Unterrichtsplanung im Master."))
        assertFalse(Memory.known(c, "Arbeitet an einem DFG-Antrag."))
        val prompt = MemoryLearner.prompt(listOf("Ich leite das Seminar …"), emptyList(), listOf("Leitet das Seminar"), inProject = true)
        assertTrue("Schon bekannt" in prompt.first().content && "scope" in prompt.first().content)
    }
}
