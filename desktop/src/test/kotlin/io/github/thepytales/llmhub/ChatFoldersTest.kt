package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.agent.RepoContext
import io.github.thepytales.llmhub.agent.execute
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.LocalFolder
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ganze Ordner am Chat (Windows): Lora liest nur oder schreibt auch – das Recht gilt je Chat und lässt sich ändern. */
class ChatFoldersTest {
    private val base = Files.createTempDirectory("adeu-chatordner").toFile()
    private val dir = File(base, "Seminar").apply { mkdirs(); File(this, "Plan.txt").writeText("Sitzung 1: Einführung") }

    @AfterTest
    fun cleanUp() {
        base.deleteRecursively()
    }

    @Test
    fun theRightOfTheChatCounts() = runBlocking {
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(base, "daten").absolutePath)
        val c = AppContainer(Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
        val folder = LocalFolder(id = "chat-1", name = "Seminar", path = dir.absolutePath, write = false)
        val conv = Conversation(model = "test", folders = listOf(folder))
        c.chats.save(conv)
        // The folder tools come into the chat, the prompt names the folder and its right.
        assertTrue(c.localFolderTools in c.agent.providers(conv))
        val prompt = c.localFolderTools.prompt(null, conv.folders)
        assertTrue(prompt.contains("„Seminar“") && prompt.contains("nur lesen") && prompt.contains("an diesen Chat angehängt"), prompt)
        val ctx = RepoContext(mutableListOf(), chatId = conv.id)
        assertTrue(c.localFolderTools.execute("read_local_file", """{"path":"Plan.txt"}""", ctx).text.contains("Einführung"))
        // Read only: nothing is written – even if the same folder is shared for writing in the settings.
        c.localFolders.upsert(LocalFolder(name = "Global", path = dir.absolutePath, write = true))
        val denied = runCatching { c.localFolderTools.execute("write_local_file", """{"path":"neu.txt","content":"x"}""", ctx).text }
        assertTrue(denied.isFailure || denied.getOrNull().orEmpty().contains("nur zum Lesen"), denied.toString())
        assertFalse(File(dir, "neu.txt").exists())
        // Changed during the chat: now Lora may write.
        c.chats.save(conv.copy(folders = listOf(folder.copy(write = true))))
        c.localFolderTools.execute("write_local_file", """{"path":"neu.txt","content":"Hallo"}""", ctx)
        assertTrue(File(dir, "neu.txt").readText().contains("Hallo"))
        assertTrue(io.github.thepytales.llmhub.ui.chatFolderName(listOf(folder), dir.absolutePath) == "Seminar 2")
    }
}
