package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.data.PiiDetector
import io.github.thepytales.llmhub.data.PiiKind
import io.github.thepytales.llmhub.data.Pseudonymizer
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test collection of the pseudonymization (AP-03): private names must be replaced, authors, public persons and
 * public sources must stay. The rates are printed, so improvements are measurable.
 */
class PseudonymCorpusTest {
    private var settings = AppSettings(pseudonymize = true)

    private fun pseudonymizer(authors: List<String> = emptyList()): Pseudonymizer {
        val file = File.createTempFile("pseudonyms", ".json").apply { delete() }
        return Pseudonymizer(file, publicNames = { authors }) { settings }
    }

    /** Texts with private names (must be replaced) and public names (must stay). */
    private class Sample(val text: String, val private: List<String>, val public: List<String> = emptyList())

    private val corpus = listOf(
        Sample("Gestern sprach ich mit Jolina Kretzschmann über ihre Noten.", listOf("Kretzschmann")),
        Sample("Im Elterngespräch war auch Frau Wojciechowski dabei, die Mutter von Ben.", listOf("Wojciechowski", "Ben")),
        Sample("Schülerin Malina hat gefehlt, ihr Bruder Kjell ebenfalls.", listOf("Malina", "Kjell")),
        Sample("Lukas Brettschneider kommt aus der 4b.", listOf("Lukas", "Brettschneider")),
        Sample("Sie besucht die Grundschule am Park und danach den Hort.", listOf("Grundschule am Park")),
        Sample("Das Interview fand am Goethe-Gymnasium statt.", listOf("Goethe-Gymnasium")),
        Sample("Laut der Schulleiterin in Pirna ist die Lage angespannt.", listOf("Pirna")),
        Sample("Viele Grüße\nAnnegret Zschoche", listOf("Annegret", "Zschoche")),
        Sample("Klafki (1996) beschreibt die kategoriale Bildung, vgl. auch Meyer, H. (2004).", emptyList(), listOf("Klafki", "Meyer")),
        Sample("Nach Helmke (2017) und Lipowsky & Rzejak (2021) wirkt Klarheit.", emptyList(), listOf("Helmke", "Lipowsky", "Rzejak")),
        Sample("Das Buch von Wolfgang Klafki prägt die Didaktik bis heute.", emptyList(), listOf("Wolfgang Klafki")),
        Sample("Hattie et al. (2009) fassen über 800 Metaanalysen zusammen.", emptyList(), listOf("Hattie")),
    )

    @Test
    fun corpusFindsPrivateNamesAndKeepsPublicOnes() {
        val p = pseudonymizer()
        var privateFound = 0
        var privateTotal = 0
        var publicKept = 0
        var publicTotal = 0
        for (s in corpus) {
            val masked = p.mask(s.text)
            s.private.forEach { name ->
                privateTotal++
                if (name !in masked) privateFound++ else println("übersehen: $name in „${s.text}“ → $masked")
            }
            s.public.forEach { name ->
                publicTotal++
                if (name in masked) publicKept++ else println("fälschlich ersetzt: $name in „${s.text}“ → $masked")
            }
        }
        println("Pseudonymisierung: $privateFound/$privateTotal private Namen ersetzt, $publicKept/$publicTotal öffentliche erhalten")
        assertTrue(privateFound >= privateTotal - 1, "private Namen: $privateFound/$privateTotal")
        assertEquals(publicTotal, publicKept, "öffentliche Namen bleiben")
    }

    @Test
    fun webSearchAndBooksStayAsTheyAre() {
        val p = pseudonymizer()
        val messages = listOf(
            ChatMessage(role = "user", content = "Was schreibt Anna Kaltenbach dazu? Fasse das Buch zusammen."),
            ChatMessage(role = "tool", toolName = "web_search", toolCallId = "1", content = "Anna Kaltenbach, Professorin in Leipzig, schreibt …"),
            ChatMessage(
                role = "user", content = "Hier das Buch",
                files = listOf(
                    FileAttachment("Buch.pdf", "application/pdf", "Herausgegeben von Anna Kaltenbach. ISBN 978-3-407-25771-2. Kapitel 1 …"),
                    FileAttachment("Protokoll.docx", "application/msword", "Elterngespräch mit Frau Anna Kaltenbach am Montag."),
                ),
            ),
        )
        val sent = p.maskMessages(messages)
        assertTrue("Anna Kaltenbach" in sent.first { it.role == "tool" }.content, "Websuche bleibt: ${sent[2].content}")
        val files = sent.last().files
        assertTrue("Anna Kaltenbach" in files[0].text, "Buch mit ISBN bleibt öffentlich")
        assertFalse("Kaltenbach" in files[1].text, "vertrauliches Protokoll pseudonymisiert: ${files[1].text}")
        // Marked as confidential, the book is replaced as well; switched off, everything is replaced.
        val confidential = p.maskMessages(listOf(messages[2].copy(files = listOf(messages[2].files[0].copy(public = false))))).last { it.role == "user" }
        assertFalse("Kaltenbach" in confidential.files[0].text)
        settings = settings.copy(pseudonymPublic = false)
        assertFalse("Kaltenbach" in p.maskMessages(messages).first { it.role == "tool" }.content)
        settings = settings.copy(pseudonymPublic = true)
        assertTrue(PiiDetector.looksPublic("doi:10.1007/s11618-020-00933-0"))
        assertFalse(PiiDetector.looksPublic("Protokoll der Klassenkonferenz vom 3. März"))
    }

    @Test
    fun authorsOfTheCollectionAndStoredNamesInCitations() {
        val p = pseudonymizer(authors = listOf("Kunter", "Mareike Kunter"))
        // „Müller“ was replaced earlier as a parent …
        assertFalse("Müller" in p.mask("Frau Müller hat angerufen."))
        // … but in a citation it is an author and stays.
        val cited = p.mask("Müller (2020) zeigt, dass Feedback wirkt.")
        assertTrue("Müller (2020)" in cited, cited)
        // Authors of the literature collection are never replaced.
        assertTrue("Mareike Kunter" in p.mask("Wie sieht Mareike Kunter das?"))
        assertEquals(setOf("Lipowsky", "Rzejak"), PiiDetector.citedAuthors("Lipowsky & Rzejak (2021)").filter { ' ' !in it }.toSet())
    }

    @Test
    fun oneClickCorrectionsHoldForTheNextChat() {
        val p = pseudonymizer()
        val text = "Morgen kommt Jolina Kretzschmann vorbei, sagt Frank."
        assertTrue(p.replacements(text).any { it.first == "Kretzschmann" })
        // „nicht ersetzen“
        p.neverReplace("Kretzschmann") { t -> settings = t(settings) }
        assertTrue("Kretzschmann" in p.mask(text, scope = "neuer-chat"), "nach der Korrektur nicht mehr ersetzt")
        // „auch ersetzen“
        p.alwaysReplace("Zwiebelmann") { t -> settings = t(settings) }
        assertFalse("Zwiebelmann" in p.mask("Herr Zwiebelmann? Nein: Zwiebelmann kommt.", scope = "noch-ein-chat"))
        assertTrue(settings.pseudonymTerms.contains("Zwiebelmann") && settings.pseudonymIgnore.contains("Kretzschmann"))
    }

    @Test
    fun schoolsAndPlacesInDescriptions() {
        val hits = PiiDetector.find("Die 32. Grundschule und die Kita „Sonnenschein“ liegen nah; der Klassenlehrer aus Meißen kommt mit.")
        assertTrue(hits.any { it.kind == PiiKind.SCHOOL && "Grundschule" in it.value }, hits.toString())
        assertTrue(hits.any { it.kind == PiiKind.SCHOOL && "Sonnenschein" in it.value }, hits.toString())
        assertTrue(hits.any { it.kind == PiiKind.PLACE && it.value == "Meißen" }, hits.toString())
        // A school as a word without a name, a country and a "Grundschule" alone stay.
        val none = PiiDetector.find("In der Grundschule lernen Kinder in Deutschland lesen; der Lehrer aus Sachsen erklärt es.")
        assertTrue(none.none { it.kind == PiiKind.SCHOOL || it.kind == PiiKind.PLACE }, none.toString())
    }

    @Test
    fun thoroughRecognitionWithALocalModel() = runBlocking {
        settings = settings.copy(pseudonymThorough = true)
        val p = pseudonymizer()
        var calls = 0
        p.thoroughFinder = { _ -> calls++; listOf("Quirin", "Wolkenkuckucksheim-Schule") }
        val messages = listOf(ChatMessage(role = "user", content = "quirin war heute still. Quirin geht in die Wolkenkuckucksheim-Schule."))
        p.prepareThorough(messages)
        p.prepareThorough(messages)
        assertEquals(1, calls, "jeder Text nur einmal")
        val masked = p.maskMessages(messages).last().content
        assertFalse("Quirin" in masked || "Wolkenkuckucksheim" in masked, masked)
        assertEquals(listOf("Anna", "Ben"), Pseudonymizer.parseThorough("Hier: ```json\n[\"Anna\", \"Ben\"]\n```"))
        settings = settings.copy(pseudonymThorough = false)
    }
}
