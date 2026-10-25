package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.data.AtomicFiles
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.ChatRepository
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.SecretBox
import io.github.thepytales.llmhub.net.NetErrors
import io.github.thepytales.llmhub.ui.isOnTop
import io.github.thepytales.llmhub.ui.isTopLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Safe saving, the trash, encrypted backup keys and the navigation rules behind the "dark screen" fix. */
class StorageAndSafetyTest {
    private val out = File("build/test-output/storage-${System.nanoTime()}").apply { mkdirs() }

    @Test
    fun atomicWriteKeepsBackupAndRecoversAnInterruptedSave() {
        val f = File(out, "a/data.json")
        AtomicFiles.write(f, "eins")
        AtomicFiles.write(f, "zwei", keepBackup = true)
        assertEquals("zwei", f.readText())
        assertEquals("eins", AtomicFiles.backupOf(f).readText())
        assertFalse(File(f.parentFile, "data.json.tmp").exists(), "keine Temp-Datei übrig")
        // Crash after writing the temporary file but before the rename: the finished new version is used.
        f.delete()
        File(f.parentFile, "data.json.tmp").writeText("drei")
        assertEquals("drei", AtomicFiles.read(f))
        assertNull(AtomicFiles.read(File(out, "fehlt.json")))
    }

    @Test
    fun brokenFilesAreSetAsideNotLost() {
        val f = File(out, "b/kaputt.json").apply { parentFile.mkdirs(); writeText("{nicht json") }
        val moved = AtomicFiles.quarantine(f, IllegalStateException("test"))
        assertNotNull(moved)
        assertFalse(f.exists())
        assertEquals("{nicht json", moved.readText())
        assertTrue(moved.parentFile.name == "defekt")
        assertTrue("kaputt.json" in ErrorLog.recentText())
    }

    @Test
    fun chatListIsLightButSavingNeverLosesImages() = runBlocking {
        val dir = File(out, "chats").apply { mkdirs() }
        val repo = ChatRepository(Context(dir, File(dir, "cache")))
        repo.awaitLoaded()
        val image = "data:image/png;base64," + "A".repeat(10_000)
        val msg = ChatMessage(role = "user", content = "Was zeigt das Bild?", images = listOf(image))
        repo.save(Conversation(id = "c1", model = "m", messages = listOf(msg)))
        // The list only holds a placeholder, the full chat comes from get().
        assertEquals(ChatRepository.IMAGE_STUB, repo.conversations.value.single().messages.single().images.single())
        assertEquals(image, repo.get("c1")!!.messages.single().images.single())
        // Saving the light copy (e.g. after renaming from the side menu) keeps the picture.
        repo.save(repo.conversations.value.single().copy(title = "Umbenannt"))
        delay(300)
        val again = ChatRepository(Context(dir, File(dir, "cache")))
        again.awaitLoaded()
        assertEquals("Umbenannt", again.get("c1")!!.title)
        assertEquals(image, again.get("c1")!!.messages.single().images.single())
    }

    @Test
    fun deletedChatsGoToTheTrashAndComeBack() = runBlocking {
        val dir = File(out, "trash").apply { mkdirs() }
        val repo = ChatRepository(Context(dir, File(dir, "cache")))
        repo.awaitLoaded()
        repo.save(Conversation(id = "c2", model = "m", title = "Wichtig", messages = listOf(ChatMessage(role = "user", content = "Hallo"))))
        delay(200)
        repo.delete("c2")
        delay(200)
        assertNull(repo.get("c2"))
        assertEquals(listOf("Wichtig"), repo.trashed().map { it.first.title })
        repo.restore("c2")
        delay(300)
        assertEquals("Wichtig", repo.get("c2")?.title)
        assertTrue(repo.trashed().isEmpty())
    }

    @Test
    fun brokenChatFileDoesNotTakeTheOthersDown() = runBlocking {
        val dir = File(out, "broken").apply { mkdirs() }
        File(dir, "conversations").mkdirs()
        File(dir, "conversations/x.json").writeText("{kaputt")
        val repo = ChatRepository(Context(dir, File(dir, "cache")))
        repo.awaitLoaded()
        assertTrue(repo.conversations.value.isEmpty())
        assertTrue(File(dir, "conversations/defekt").listFiles().orEmpty().any { it.name.endsWith("x.json") })
    }

    @Test
    fun backupKeysNeedThePassword() {
        val box = SecretBox.seal("""{"apiKey":"sk-geheim"}""", "richtig-123")
        assertFalse("sk-geheim" in box.data, "nicht im Klartext")
        assertEquals("""{"apiKey":"sk-geheim"}""", box.open("richtig-123"))
        assertNull(box.open("falsch-1234"))
        assertTrue(runCatching { SecretBox.seal("x", "kurz") }.isFailure, "zu kurzes Passwort")
    }

    @Test
    fun backOnlyActsFromTheScreenOnTop() {
        // Two taps on the back arrow: the second comes from the screen that is already leaving.
        assertTrue(isOnTop("transcripts", "transcripts"))
        assertFalse(isOnTop("chat", "transcripts"))
        assertFalse(isOnTop(null, "transcripts"))
        assertTrue(isTopLevel("chat?id={id}&model={model}&persona={persona}"))
        assertTrue(isTopLevel("transcripts"))
        assertFalse(isTopLevel("transcript/{id}"))
        assertFalse(isTopLevel("settings/{section}"))
    }

    @Test
    fun networkErrorsAreExplained() {
        assertEquals(NetErrors.OFFLINE, NetErrors.describe(UnknownHostException("llm.scads.ai")))
        assertTrue("Zeitüberschreitung" in NetErrors.describe(java.net.SocketTimeoutException()))
    }
}
