package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.agent.AiUseNote
import io.github.thepytales.llmhub.agent.ChatModes
import io.github.thepytales.llmhub.agent.GameTools
import io.github.thepytales.llmhub.data.AppTool
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.LearningGameStore
import io.github.thepytales.llmhub.data.ToolCall
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Lernspiele (AP-17) and the KI-Nutzungsnachweis (AP-19). */
class GamesAndAiNoteTest {
    private val game = """<!DOCTYPE html><html lang="de"><head><meta charset="utf-8"><title>Zuordnen</title>
        <meta http-equiv="Content-Security-Policy" content="default-src *"></head>
        <body><h1>Zuordnen: Lerntheorien</h1><div id="game"></div>
        <script>const pairs=[["Behaviorismus","Reiz-Reaktion"],["Kognitivismus","Informationsverarbeitung"]];
        document.getElementById('game').textContent = pairs.map(p => p[0]).join(', ');</script></body></html>""".trimIndent()

    @Test
    fun gamesAreKeptWithVersionsAndRunWalledOff() {
        val store = LearningGameStore(Files.createTempDirectory("adeu-games").toFile())
        val g = store.create("Zuordnen: Lerntheorien", "Zuordnen", "Kapitel 3, 1. Semester", game, chatId = "chat-1")
        assertEquals(1, g.versions)
        val v2 = store.update(g.id, game.replace("Lerntheorien", "Lerntheorien (schwer)"))!!
        assertEquals(2, v2.versions)
        assertTrue(store.html(g.id, 1)!!.contains("<h1>Zuordnen: Lerntheorien</h1>"), "Version 1 bleibt")
        assertTrue(store.html(g.id)!!.contains("(schwer)"))
        // Walled off: exactly one policy (the game's own, permissive one is replaced), nothing from the network.
        val safe = LearningGameStore.sandboxed(store.html(g.id)!!)
        assertEquals(1, Regex("Content-Security-Policy").findAll(safe).count())
        assertTrue("default-src 'none'" in safe && "connect-src 'none'" in safe && "default-src *" !in safe)
        assertTrue(LearningGameStore.sandboxed("<p>Hallo</p>").startsWith("<!DOCTYPE html>"), "auch ohne Gerüst")
        // Export for OPAL: a ZIP with index.html.
        val zip = store.writeZip(g.id, Files.createTempFile("spiel", ".zip").toFile())!!
        ZipFile(zip).use { z ->
            assertNotNull(z.getEntry("index.html"))
            assertTrue(z.getInputStream(z.getEntry("index.html")).readBytes().decodeToString().contains("default-src 'none'"))
        }
        assertEquals("Zuordnen Lerntheorien.html", store.fileName(g, "html"))
        store.delete(g.id)
        assertTrue(store.items.value.isEmpty())
    }

    @Test
    fun onlyCompleteGamesWithoutOutsideFilesAreSaved() {
        assertNull(LearningGameStore.problem(game))
        assertNotNull(LearningGameStore.problem("<html>zu kurz</html>"))
        assertNotNull(LearningGameStore.problem(game.replace("<script>", "<script src=\"https://cdn.example.org/lib.js\"></script><script>")), "externes Skript")
        assertNotNull(LearningGameStore.problem(game.replace("<title>", "<link rel=\"stylesheet\" href=\"https://fonts.example.org/a.css\"><title>")))
        assertEquals("1234abcd-0000-1111-2222-333344445555", GameTools.idIn("Lernspiel „X“ gespeichert. Spiel-ID: 1234abcd-0000-1111-2222-333344445555 · Version 1."))
    }

    @Test
    fun theGameModeShowsOnlyWithTheToolSwitchedOn() {
        val before = ChatModes.enabledTools
        try {
            assertTrue(AppTool.GAMES in AppTool.available(android = true), "einschaltbar, auch auf Android")
            ChatModes.enabledTools = { emptySet() }
            assertFalse(ChatModes.GAMES in ChatModes.visible(), "Standard: ausgeblendet")
            assertTrue(ChatModes.matching("Lernspiel").isEmpty())
            ChatModes.enabledTools = { setOf(AppTool.GAMES.id) }
            assertTrue(ChatModes.GAMES in ChatModes.visible())
            assertEquals(ChatModes.GAMES, ChatModes.matching("Lernspiel").single())
            assertEquals(AppTool.GAMES, AppTool.suggestFor("Mach mir ein Quiz zu Kapitel 2", emptySet(), android = false), "Lora schlägt das Werkzeug vor")
        } finally {
            ChatModes.enabledTools = before
        }
    }

    private fun chat(vararg turns: Pair<String, String>, calls: List<ToolCall> = emptyList(), title: String = "Neuer Chat"): Conversation {
        val msgs = mutableListOf<ChatMessage>()
        var parent: String? = null
        turns.forEachIndexed { i, (q, a) ->
            val u = ChatMessage(role = "user", content = q, parentId = parent, createdAt = 1_800_000_000_000L + i * 3_600_000L)
            val r = ChatMessage(role = "assistant", content = a, parentId = u.id, model = io.github.thepytales.llmhub.data.ModelCatalog.DEFAULT_CHAT, toolCalls = if (i == 0) calls else emptyList(), createdAt = u.createdAt + 60_000)
            msgs += u
            msgs += r
            parent = r.id
        }
        return Conversation(title = title, model = io.github.thepytales.llmhub.data.ModelCatalog.DEFAULT_CHAT, messages = msgs)
    }

    @Test
    fun theDeclarationIsOfferedOnlyForWrittenWork() {
        val long = "Die Einleitung führt in das Thema Klassenführung ein. ".repeat(12)
        val thesis = chat("Hilf mir, die Einleitung meiner Masterarbeit über Klassenführung zu formulieren." to long)
        assertTrue(AiUseNote.shouldOffer(thesis))
        assertEquals("Masterarbeit", AiUseNote.workIn("meine masterarbeit"))
        // No work: an e-mail; a work, but nothing written (a short answer to a question).
        assertFalse(AiUseNote.shouldOffer(chat("Schreib eine Mail an den Hausmeister wegen der Heizung." to long)))
        assertFalse(AiUseNote.shouldOffer(chat("Wann ist die Abgabe der Bachelorarbeit?" to "Am 30.11.")))
        assertFalse(AiUseNote.shouldOffer(chat("Wie stelle ich einen Urlaubsantrag?" to long)), "Verwaltung ist keine Arbeit")
        // A document for a paper counts even without the words.
        val doc = ToolCall("1", "create_document", """{"title":"Diskussion","markdown":"…"}""")
        assertTrue(AiUseNote.shouldOffer(chat("Für mein Paper: die Diskussion bitte" to "Erledigt.", calls = listOf(doc))))

        val u = AiUseNote.usage(listOf(chat(
            "Hilf mir, die Einleitung meiner Masterarbeit zu formulieren." to long,
            "Bitte korrigiere die Rechtschreibung und kürze den zweiten Absatz." to long,
            calls = listOf(doc),
        )))
        assertEquals("Masterarbeit", u.work)
        assertTrue("Formulierungshilfe und Entwürfe" in u.purposes && "Überarbeitung und sprachliche Korrektur" in u.purposes, "${u.purposes}")
        assertEquals(listOf("Diskussion"), u.documents)
        val name = io.github.thepytales.llmhub.data.ModelCatalog.info(io.github.thepytales.llmhub.data.ModelCatalog.DEFAULT_CHAT).displayName
        assertEquals(listOf(name), u.models)
        val exam = AiUseNote.text(u, AiUseNote.Format.EXAM)
        assertTrue("Masterarbeit" in exam && name in exam && "[bitte ergänzen" in exam && "„Diskussion“" in exam, exam)
        assertTrue("TU Dresden" !in exam, "keine Behauptung, eine App der TU zu sein")
        assertTrue(AiUseNote.text(u, AiUseNote.Format.PUBLISHER).contains("take(s) full responsibility"))
        assertTrue(AiUseNote.text(u, AiUseNote.Format.FOOTNOTE).length < 400, "kurz")
    }
}
