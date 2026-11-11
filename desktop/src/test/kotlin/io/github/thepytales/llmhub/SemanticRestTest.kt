package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.agent.FolderAsk
import io.github.thepytales.llmhub.data.ChatFilter
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.SemanticSearch
import io.github.thepytales.llmhub.data.Term
import io.github.thepytales.llmhub.data.Vocabulary
import io.github.thepytales.llmhub.files.AttachmentBudget
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Suche nach Bedeutung, Rest (AP-107): Frag diesen Ordner, the chat search, Wissenspakete and large attachments find
 * what is meant in other words when the model on this device is there – and stay word searches without it. The model
 * is stood in for by a small one that knows a few meanings (the real one is measured in LocalEmbeddingTest).
 */
class SemanticRestTest {
    private val dir = createTempDirectory("lora-bedeutung").toFile()
    private var calls = 0

    /** Meanings as axes: words of the same meaning land on the same axis. */
    private val axes = listOf(
        listOf("dienstreise", "reisekosten", "fahrtkosten", "fahrkarte", "erstattet", "erstattung", "hotel", "reise"),
        listOf("urlaub", "erholung", "freie tage", "ferien"),
        listOf("prüfung", "klausur", "examen", "note"),
        listOf("drucker", "toner", "papierstau"),
    )

    private fun vector(text: String): FloatArray {
        val t = text.lowercase()
        val v = FloatArray(axes.size + 1) { i -> if (i < axes.size) axes[i].count { it in t }.toFloat() else 0.3f }
        return v
    }

    @BeforeTest
    fun connect() {
        SemanticSearch.attach(File(dir, "cache"), { true }) { texts, _ -> calls += texts.size; texts.map(::vector) }
    }

    @AfterTest
    fun disconnect() = SemanticSearch.detach()

    @Test
    fun folderFindsPassagesInOtherWords() {
        val texts = mapOf(
            File(dir, "Merkblatt Reisen.txt") to "Die Reisekostenstelle erstattet Fahrkarten und Hotel nach Vorlage der Belege innerhalb von sechs Monaten.",
            File(dir, "Urlaubsantrag.txt") to "Urlaub wird über das Portal beantragt; Resturlaub verfällt am 31. März.",
            File(dir, "Drucker.txt") to "Bei Papierstau den Toner herausnehmen und die Klappe öffnen.",
        )
        val q = "Wer bezahlt meine Dienstreise zurück?"
        // Without the model: no word of the question is in the files.
        assertTrue(FolderAsk.rank(texts, q, meaning = false).isEmpty())
        val hits = FolderAsk.rank(texts, q)
        assertEquals("Merkblatt Reisen.txt", hits.first().file)
        // Vectors are kept: asking again computes nothing anew.
        calls = 0
        FolderAsk.rank(texts, q)
        assertEquals(0, calls)
    }

    @Test
    fun chatSearchShowsChatsAboutTheSameThing() {
        fun chat(id: String, title: String, text: String) = Conversation(id = id, title = title, model = "m", messages = listOf(ChatMessage(role = "user", content = text)))
        val list = listOf(
            chat("a", "Abrechnung Tagung Leipzig", "Wie rechne ich die Fahrkarte und das Hotel der Tagung ab?"),
            chat("b", "Klausur Statik", "Bitte Aufgaben für die Klausur entwerfen."),
            chat("c", "Drucker kaputt", "Der Drucker hat Papierstau."),
            chat("d", "Ferienplanung", "Wann sind die Ferien?"),
        )
        val words = ChatFilter.search(list, "Reisekosten", ChatFilter())
        assertTrue(words.isEmpty())
        val similar = ChatFilter.similar(list, "Reisekosten", ChatFilter(), found = emptySet())
        assertEquals(listOf("a"), similar.map { it.first.id })
        assertTrue(ChatFilter.similar(list, "Reisekosten", ChatFilter(), found = setOf("a")).isEmpty())
    }

    @Test
    fun packEntriesAreFoundByMeaning() {
        val terms = listOf(
            Term("ZRK", "Zentrale Reisekostenstelle – erstattet Dienstreisen und Fahrtkosten", source = "https://tu-dresden.de/x", date = "2026-01-01") to "TU Verwaltung",
            Term("PA", "Prüfungsamt – Klausur und Note", source = "https://tu-dresden.de/y", date = "2026-01-01") to "TU Verwaltung",
            Term("Eigenes", "Etwas Eigenes zur Dienstreise") to null,
        )
        val found = Vocabulary.meaning("Wer bezahlt die Fahrkarte meiner Dienstreise?", terms)
        assertEquals(listOf("ZRK"), found?.map { it.term })
        SemanticSearch.detach()
        assertNull(Vocabulary.meaning("Wer bezahlt die Fahrkarte meiner Dienstreise?", terms))
    }

    @Test
    fun largeAttachmentsKeepThePassagesThatAnswer() {
        val filler = (1..60).joinToString("\n\n") { "Absatz $it über das Leitbild der Fakultät, ihre Geschichte und Gremien. ".repeat(6) }
        val answer = "Die Erstattung von Fahrtkosten und Hotel beantragen Beschäftigte binnen sechs Monaten bei der zuständigen Stelle."
        val text = filler.substringBefore("Absatz 40") + answer + "\n\nAbsatz 40" + filler.substringAfter("Absatz 40")
        val (picked, _) = AttachmentBudget.passages(text, "Wie bekomme ich mein Geld für die Dienstreise zurück?", 6_000)
        assertTrue(answer in picked, picked.take(300))
    }
}
