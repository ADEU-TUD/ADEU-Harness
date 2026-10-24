package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.PiiDetector
import io.github.thepytales.llmhub.data.PiiKind
import io.github.thepytales.llmhub.data.Pseudonymizer
import io.github.thepytales.llmhub.data.ToolCall
import io.github.thepytales.llmhub.net.StreamUpdate
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivacyTest {
    private fun pseudonymizer(terms: String = "", auto: Boolean = true): Pseudonymizer {
        val file = File.createTempFile("pseudonyms", ".json").apply { delete() }
        return Pseudonymizer(file) { AppSettings(pseudonymize = true, pseudonymAuto = auto, pseudonymTerms = terms) }
    }

    @Test
    fun contactDetailsAreFound() {
        val text = "Bitte an anna.mueller@tu-dresden.de schreiben oder 0351 463-34567 anrufen. Matrikelnummer: 4711234, " +
            "geb. am 03.04.2002, IBAN DE89 3704 0044 0532 0130 00, wohnhaft Bergstraße 12 in 01069 Dresden."
        val hits = PiiDetector.find(text)
        fun has(kind: PiiKind, value: String) = assertTrue(hits.any { it.kind == kind && it.value == value }, "$kind $value fehlt: $hits")
        has(PiiKind.EMAIL, "anna.mueller@tu-dresden.de")
        has(PiiKind.PHONE, "0351 463-34567")
        has(PiiKind.MATRIKEL, "4711234")
        has(PiiKind.BIRTHDATE, "03.04.2002")
        has(PiiKind.IBAN, "DE89 3704 0044 0532 0130 00")
        has(PiiKind.ADDRESS, "Bergstraße 12")
        // Postal codes, years, dates and amounts are no phone numbers.
        assertFalse(hits.any { it.kind == PiiKind.PHONE && ("01069" in it.value || "0044" in it.value) }, hits.toString())
        assertTrue(PiiDetector.find("Am 03.11.2026 um 14:00 kostet es 0,50 €, Raum 0.12, Jahr 2026.").none { it.kind == PiiKind.PHONE })
    }

    @Test
    fun namesAfterFormsOfAddressAndFrequentNamesAreFound() {
        val hits = PiiDetector.find("Sehr geehrte Frau Dr. Anna Kaltenbach, ich habe mit Herrn Prof. Weißgerber gesprochen. " +
            "Lukas Schmidt und Mia haben gestört. Frau Müller Bescheid geben.").map { it.value }
        listOf("Anna", "Kaltenbach", "Weißgerber", "Lukas", "Schmidt", "Mia", "Müller").forEach { assertTrue(it in hits, "$it fehlt: $hits") }
        assertFalse("Bescheid" in hits, hits.toString())
        assertFalse("Sehr" in hits || "Frau" in hits || "Herrn" in hits, hits.toString())
    }

    @Test
    fun ownListCatchesNamesWithoutAnyClue() {
        val p = pseudonymizer(terms = "Jonte Vogelsang\nGymnasium Dresden-Plauen")
        val masked = p.mask("Jonte war heute unruhig. Vogelsang hat am Gymnasium Dresden-Plauen gefehlt.")
        assertFalse("Jonte" in masked || "Vogelsang" in masked || "Plauen" in masked, masked)
        // Institution words of the list are only replaced as a whole.
        assertTrue("Gymnasium" !in masked && "[Begriff 1]" in masked, masked)
        assertEquals("Jonte war heute unruhig. Vogelsang hat am Gymnasium Dresden-Plauen gefehlt.", p.unmask(masked))
    }

    @Test
    fun placeholdersAreStableAndAnswersGetTheRealNamesBack() {
        val p = pseudonymizer()
        val first = p.mask("Frau Müller (mueller@example.org) fragt nach dem Gutachten.")
        val second = p.mask("Schreib Frau Müller, dass das Gutachten fertig ist.")
        assertTrue("Müller" !in first && "mueller@example.org" !in first, first)
        val placeholder = Regex("\\[Name \\d+]").find(first)!!.value
        assertTrue(placeholder in second, "dieselbe Person, derselbe Platzhalter: $first / $second")
        val answer = "Liebe Frau $placeholder, das Gutachten ist fertig. Kopie an [E-Mail 1]."
        assertEquals("Liebe Frau Müller, das Gutachten ist fertig. Kopie an mueller@example.org.", p.unmask(answer))
        // Placeholders the app did not make stay untouched (e.g. gaps in documents).
        assertEquals("[Datum] und [Name 99]", p.unmask("[Datum] und [Name 99]"))
    }

    @Test
    fun requestsAreMaskedAndToolArgumentsUnmasked() {
        val p = pseudonymizer()
        val messages = listOf(
            ChatMessage(role = "system", content = "Du bist hilfreich."),
            ChatMessage(role = "user", content = "Erstelle einen Brief an Herrn Becker, Tel. 0351 46312345."),
        )
        val sent = p.maskMessages(messages)
        assertTrue(sent.none { "Becker" in it.content || "46312345" in it.content }, sent.toString())
        assertTrue(Pseudonymizer.MODEL_NOTE in sent.first().content)
        val name = Regex("\\[Name \\d+]").find(sent[1].content)!!.value
        val call = ToolCall("1", "create_document", "{\"title\":\"Brief an Herrn $name\",\"content\":\"Sehr geehrter Herr $name\"}")
        val back = p.unmask(StreamUpdate("Erledigt für Herrn $name.", "", listOf(call)))
        assertEquals("Erledigt für Herrn Becker.", back.content)
        assertTrue("Herrn Becker" in back.toolCalls.single().arguments && "[Name" !in back.toolCalls.single().arguments)
    }

    @Test
    fun ordinaryTextStaysUntouched() {
        val p = pseudonymizer()
        val text = "Die Lernziele der Sitzung: Klassenführung, kognitive Aktivierung und konstruktive Unterstützung (Kunter & Trautwein, 2013)."
        assertEquals(text, p.mask(text))
        assertEquals("Formuliere Lernziele für die 8. Klasse in Mathematik.", p.mask("Formuliere Lernziele für die 8. Klasse in Mathematik."))
    }
}
