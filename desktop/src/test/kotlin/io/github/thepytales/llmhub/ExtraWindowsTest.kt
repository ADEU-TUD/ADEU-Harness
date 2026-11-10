package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.ui.ExtraWindows
import io.github.thepytales.llmhub.ui.ExtraWindows.Kind
import io.github.thepytales.llmhub.ui.ExtraWindows.Spec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Lora in mehreren Fenstern (AP-84). */
class ExtraWindowsTest {
    @Test
    fun aChatIsOnlyEverOpenInOneWindow() = runBlocking {
        val a = Spec(Kind.CHAT, "chat-a", "Gutachten")
        val b = Spec(Kind.CHAT, "chat-b", "Seminar")
        ExtraWindows.show(a)
        ExtraWindows.show(b)
        assertTrue(ExtraWindows.chatOpen("chat-a") && ExtraWindows.chatOpen("chat-b"))
        // The same chat again: no second window, the first comes to the front.
        val focused = launch { assertEquals(a.key, withTimeout(2_000) { ExtraWindows.focus.first() }) }
        kotlinx.coroutines.yield()
        ExtraWindows.show(a.copy(title = "anders"))
        focused.join()
        assertEquals(2, ExtraWindows.open.value.count { it.kind == Kind.CHAT })
        // Closing one window leaves the other.
        ExtraWindows.close(a.key)
        assertFalse(ExtraWindows.chatOpen("chat-a"))
        assertTrue(ExtraWindows.chatOpen("chat-b"))
        ExtraWindows.close(b.key)
        assertTrue(ExtraWindows.open.value.isEmpty())
    }

    @Test
    fun theMainWindowShowsANoteInsteadOfASecondInput() {
        val root = File("src/main/kotlin/io/github/thepytales/llmhub/ui/AppRoot.kt").readText()
        assertTrue("ChatInOtherWindow(id)" in root, "Hauptfenster: Hinweis statt zweitem Eingabefeld")
        val host = File("src/main/kotlin/io/github/thepytales/llmhub/ui/ExtraWindows.kt").readText()
        assertTrue("LocalViewModelStoreOwner provides owner" in host && "owner.viewModelStore.clear()" in host, "eigene ViewModels je Fenster")
        val drawer = File("../app/src/main/java/io/github/thepytales/llmhub/ui/AppDrawer.kt").readText()
        assertTrue("WindowHooks.openChat?.let" in drawer)
    }
}
