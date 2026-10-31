package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.Privacy
import io.github.thepytales.llmhub.data.PrivacyLevel
import io.github.thepytales.llmhub.data.Pseudonymizer
import io.github.thepytales.llmhub.data.VaultStore
import io.github.thepytales.llmhub.ui.newVaultChat
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tresor für sensible Forschung (AP-10). */
class VaultTest {
    private fun <T> temp(block: (File) -> T): T {
        val base = Files.createTempDirectory("adeu-tresor").toFile()
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(base, "daten").absolutePath)
        return try { block(base) } finally { base.deleteRecursively() }
    }

    @Test
    fun vaultChatsAreUnreadableWithoutThePin() = temp { base ->
        val vault = VaultStore(base)
        assertEquals(VaultStore.State.NOT_SET_UP, vault.state.value)
        val recovery = vault.setUp("4711")
        assertTrue(recovery.length >= 24)
        val secret = "Interview mit Schülerin Lea Hoffmann aus der Grundschule Pieschen"
        vault.save(Conversation(model = "m", title = "Interview A", vault = true, messages = listOf(ChatMessage(role = "user", content = secret))))
        // On disk: no title, no text – also not in the file names.
        val files = base.walkTopDown().filter { it.isFile }.toList()
        assertTrue(files.none { f -> f.readBytes().decodeToString(throwOnInvalidSequence = false).let { "Hoffmann" in it || "Interview A" in it } })
        vault.lock()
        assertEquals(VaultStore.State.LOCKED, vault.state.value)
        assertTrue(vault.chats.value.isEmpty())
        // A second instance (the app restarted) cannot read without the PIN.
        val again = VaultStore(base)
        assertEquals(VaultStore.State.LOCKED, again.state.value)
        assertTrue(again.unlock("0000") is VaultStore.Unlock.Wrong)
        assertNull(again.get(again.chats.value.firstOrNull()?.id ?: "x"))
        assertEquals(VaultStore.Unlock.Ok, again.unlock("4711"))
        assertEquals(secret, again.chats.value.single().messages.single().content)
    }

    @Test
    fun recoveryKeyResetsThePinAndWrongPinsWait() = temp { base ->
        var now = 1_000_000L
        val vault = VaultStore(base) { now }
        val recovery = vault.setUp("123456")
        vault.save(Conversation(model = "m", title = "Daten", vault = true))
        vault.lock()
        repeat(VaultStore.FREE_ATTEMPTS - 1) { assertEquals(VaultStore.Unlock.Wrong(0), vault.unlock("999999")) }
        val fifth = vault.unlock("999999") as VaultStore.Unlock.Wrong
        assertTrue(fifth.waitSeconds > 0)
        assertTrue(vault.unlock("123456") is VaultStore.Unlock.Blocked, "auch die richtige PIN wartet")
        now += fifth.waitSeconds * 1000 + 1
        assertFalse(vault.resetPin("FALSCH-FALSCH", "2222"))
        assertTrue(vault.resetPin(recovery.lowercase().replace("-", " "), "2222"), "Schreibweise egal")
        assertEquals(1, vault.chats.value.size)
        vault.lock()
        assertTrue(vault.unlock("123456") is VaultStore.Unlock.Wrong)
        assertEquals(VaultStore.Unlock.Ok, vault.unlock("2222"))
    }

    @Test
    fun locksAfterIdleTime() = temp { base ->
        var now = 0L
        val vault = VaultStore(base) { now }
        vault.setUp("1234")
        vault.setLockMinutes(2)
        now += 60_000
        assertFalse(vault.lockIfIdle())
        vault.touch()
        now += 119_000
        assertFalse(vault.lockIfIdle())
        now += 2_000
        assertTrue(vault.lockIfIdle())
        assertEquals(VaultStore.State.LOCKED, vault.state.value)
    }

    @Test
    fun vaultChatsStayOutOfTheChatListAndOnlyUseUniModels() = temp { base ->
        val c = AppContainer(Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
        c.vault.setUp("8888")
        val conv = newVaultChat(c).copy(messages = listOf(ChatMessage(role = "user", content = "vertraulich")))
        assertTrue(conv.vault && conv.memoryOff)
        c.chats.save(conv)
        assertTrue(c.chats.conversations.value.none { it.id == conv.id }, "nicht in der Chatliste")
        assertNotNull(c.chats.get(conv.id))
        assertFalse(File(base, "files/conversations/${conv.id}.json").exists(), "keine Klartextdatei")
        assertEquals(PrivacyLevel.TU, Privacy.level(c, conv))
        // External providers are blocked, the Uni server is fine.
        c.settings.update { it.copy(providers = it.providers) }
        assertNull(Privacy.blockReason(c, conv, conv.model))
        c.vault.lock()
        assertNull(c.chats.get(conv.id), "gesperrt: nicht lesbar")
        c.chats.save(conv.copy(title = "geändert"))
        assertFalse(File(base, "files/conversations/${conv.id}.json").exists(), "auch gesperrt nichts im Klartext")
    }

    @Test
    fun strictPseudonymizationInsideTheVault() {
        val s = AppSettings(pseudonymize = false, pseudonymPublic = true, pseudonymThorough = false, pseudonymOff = "NAMES")
        val strict = Pseudonymizer.strict(s)
        assertTrue(strict.pseudonymize && !strict.pseudonymPublic && strict.pseudonymThorough)
        assertTrue(strict.pseudonymOffFeatures.isEmpty())
        val p = Pseudonymizer(Files.createTempFile("pseudo", ".json").toFile()) { s }
        assertFalse(p.enabled)
        Pseudonymizer.STRICT.set(true)
        try {
            assertTrue(p.enabled, "im Tresor immer an")
        } finally {
            Pseudonymizer.STRICT.remove()
        }
        assertFalse(p.enabled)
    }
}
