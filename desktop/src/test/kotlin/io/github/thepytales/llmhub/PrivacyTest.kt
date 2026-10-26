package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.NameList
import io.github.thepytales.llmhub.data.PiiDetector
import io.github.thepytales.llmhub.data.PiiFeature
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
    private fun pseudonymizer(terms: String = "", auto: Boolean = true, off: Set<PiiFeature> = emptySet(), ignore: String = ""): Pseudonymizer {
        val file = File.createTempFile("pseudonyms", ".json").apply { delete() }
        return Pseudonymizer(file) {
            AppSettings(pseudonymize = true, pseudonymAuto = auto, pseudonymTerms = terms, pseudonymIgnore = ignore, pseudonymOff = off.joinToString("\n") { it.name })
        }
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

    @Test
    fun webSearchesKeepThePlaceholders() {
        val p = pseudonymizer()
        val sent = p.maskMessages(listOf(ChatMessage(role = "user", content = "Was weiß das Netz über Herrn Becker?")))
        val name = Regex("\\[Name \\d+]").find(sent.last().content)!!.value
        val search = ToolCall("1", "web_search", "{\"query\":\"$name Dresden\"}")
        val doc = ToolCall("2", "create_document", "{\"content\":\"Herr $name\"}")
        val back = p.unmask(StreamUpdate("", "", listOf(search, doc)))
        assertTrue(name in back.toolCalls[0].arguments && "Becker" !in back.toolCalls[0].arguments, back.toString())
        assertTrue("Herr Becker" in back.toolCalls[1].arguments, back.toString())
        // Switched off: searches get the real name.
        val open = pseudonymizer(off = setOf(PiiFeature.EXTERNAL_TOOLS))
        val n2 = Regex("\\[Name \\d+]").find(open.mask("Herr Becker"))!!.value
        assertTrue("Becker" in open.unmask(StreamUpdate("", "", listOf(ToolCall("1", "web_search", "{\"query\":\"$n2\"}")))).toolCalls[0].arguments)
    }

    @Test
    fun inflectedNamesAndOtherSpellingsAreReplaced() {
        val p = pseudonymizer(terms = "Jonte Vogelsang\nMüller")
        val masked = p.mask("Jontes Heft lag bei MÜLLER. Mueller und müller wissen es, Vogelsangs Mutter auch. Lukas' Tasche, Annas Brief.")
        listOf("Jonte", "MÜLLER", "Mueller", "müller", "Vogelsang", "Lukas", "Anna").forEach { assertFalse(it in masked, "$it in $masked") }
        assertTrue(Regex("\\[Name \\d+]s Heft").containsMatchIn(masked), masked)
        // All spellings go back to the entry of the list.
        val back = p.unmask(masked)
        assertTrue("Jontes Heft" in back && "Annas Brief" in back && "Lukas' Tasche" in back, back)
        assertEquals(3, Regex("Müller").findAll(back).count(), back)
        // Without the switch the inflected form stays.
        assertTrue("Jontes" in pseudonymizer(terms = "Jonte", off = setOf(PiiFeature.INFLECTION)).mask("Jontes Heft"))
    }

    @Test
    fun moreKindsOfDetailsAreFound() {
        val text = "Anschrift: Bergstraße 12, 01069 Dresden. Steuer-ID: 12 345 678 901, SV-Nummer 65 170839 J 003, " +
            "Personalausweis T22000129, Kennzeichen DD-AB 1234, Kreditkarte 4111 1111 1111 1111, BIC: COBADEFFXXX, " +
            "Schülernummer: S-2024-117, Profil: https://www.linkedin.com/in/anna-kaltenbach, Server 192.168.10.24, Instagram @lena_m, " +
            "* 03.04.2012, geb. 3. Mai 2011"
        val hits = PiiDetector.find(text)
        fun has(kind: PiiKind, value: String) = assertTrue(hits.any { it.kind == kind && it.value == value }, "$kind $value fehlt: $hits")
        has(PiiKind.POSTCODE, "01069 Dresden")
        has(PiiKind.ID, "12 345 678 901")
        has(PiiKind.ID, "65 170839 J 003")
        has(PiiKind.ID, "T22000129")
        has(PiiKind.PLATE, "DD-AB 1234")
        has(PiiKind.CARD, "4111 1111 1111 1111")
        has(PiiKind.BIC, "COBADEFFXXX")
        has(PiiKind.ID, "S-2024-117")
        has(PiiKind.LINK, "https://www.linkedin.com/in/anna-kaltenbach")
        has(PiiKind.IP, "192.168.10.24")
        has(PiiKind.HANDLE, "@lena_m")
        has(PiiKind.BIRTHDATE, "03.04.2012")
        has(PiiKind.BIRTHDATE, "3. Mai 2011")
        // Ordinary numbers stay: amounts, chapters, salary scales, code annotations, list bullets.
        val plain = PiiDetector.find(
            "Das kostet 10000 Euro, siehe Kapitel 3.2.1.1 und TV-L 13. Die Stadt hat 50000 Einwohner.\n@param name der Name\n* 12.05.2026 Elternabend"
        )
        assertTrue(plain.isEmpty(), plain.toString())
    }

    @Test
    fun namesFromRolesGreetingsSignaturesAndMailHeaders() {
        val text = "Von: Kowalczyk, Marta <m.kowalczyk@example.org>\nAn: Sekretariat <sekretariat@example.org>\n\nLiebe Jolina,\n" +
            "die Schülerin Lilou Brandt und Kollege Wiesenhütter kommen. Der Schüler Mathematik-Leistungskurs nicht.\n\nViele Grüße\nRoswitha Quandel"
        val values = PiiDetector.find(text).map { it.value }
        listOf("Kowalczyk", "Marta", "Jolina", "Lilou", "Brandt", "Wiesenhütter", "Roswitha", "Quandel").forEach { assertTrue(it in values, "$it fehlt: $values") }
        assertFalse("Sekretariat" in values || "Mathematik" in values || "Grüße" in values, values.toString())
        // Roles become role placeholders.
        val p = pseudonymizer()
        val masked = p.mask("Die Schülerin Lilou kommt mit ihrer Mutter Dragana.")
        assertTrue("[Schüler/in 1]" in masked && "[Elternteil 1]" in masked, masked)
        assertEquals("Die Schülerin Lilou kommt mit ihrer Mutter Dragana.", p.unmask(masked))
        val plain = pseudonymizer(off = setOf(PiiFeature.ROLE_PLACEHOLDERS)).mask("Die Schülerin Lilou kommt.")
        assertTrue("[Name 1]" in plain, plain)
        // "Liebe Eltern," and "Viele Grüße\nIhr Team" are no names.
        assertTrue(PiiDetector.find("Liebe Eltern,\nbitte denken Sie daran.\n\nViele Grüße\nSchulleitung").none { it.kind.person }, "keine Namen")
    }

    @Test
    fun namesComeFromEmailAddressesFileNamesAndTables() {
        val p = pseudonymizer()
        val masked = p.mask("Mail an anna.kowalczyk@schule.de: Kowalczyk soll das Protokoll Elterngespräch_Kowalczyk.docx lesen.")
        assertFalse("Kowalczyk" in masked, masked)
        val table = "| Nachname | Vorname | Geburtsdatum | Note |\n| --- | --- | --- | --- |\n| Quandel | Jolin | 14.02.2013 | 2 |\n| Wiesenhütter | Ylvi | 01.09.2012 | 1 |"
        val t = p.mask(table)
        listOf("Quandel", "Jolin", "14.02.2013", "Wiesenhütter", "Ylvi").forEach { assertFalse(it in t, "$it in $t") }
        assertTrue("| 2 |" in t && "Note" in t, t)
        val csv = "Name;Klasse;Anschrift\nMüller, Aurelio;7b;Lindenweg 3\nTsoukalas, Eleni;7b;Am Hang 5"
        val c = p.mask(csv)
        listOf("Aurelio", "Tsoukalas", "Eleni", "Lindenweg 3", "Am Hang 5").forEach { assertFalse(it in c, "$it in $c") }
        // A table of tools with a column "Name" is no list of persons.
        val tools = "| Name | Beschreibung |\n| --- | --- |\n| Moodle | Lernplattform |\n| Opal | Lernplattform |"
        assertEquals(tools, p.mask(tools))
    }

    @Test
    fun switchesIgnoreListAndRobustPlaceholders() {
        val p = pseudonymizer(ignore = "Frank")
        assertTrue("Frank" in p.mask("Frank und frei sagt Frank Weber."), "nie ersetzen")
        val off = pseudonymizer(off = setOf(PiiFeature.BIRTHDATE, PiiFeature.EMAIL))
        val m = off.mask("geb. 03.04.2002, a@b.de")
        assertTrue("03.04.2002" in m && "a@b.de" in m, m)
        val q = pseudonymizer()
        val masked = q.mask("Herr Becker ruft an.")
        val n = Regex("\\[Name (\\d+)]").find(masked)!!.groupValues[1]
        assertEquals("Becker, Becker, Becker, Becker und **Becker**", q.unmask("Name $n, [name $n], [Name$n], [Namen $n] und **[Name $n]**"))
        assertEquals("Name 1 bis Name 2", pseudonymizer().unmask("Name 1 bis Name 2"))
    }

    @Test
    fun placeholdersPerChat() {
        val p = pseudonymizer()
        val a = p.mask("Herr Becker", scope = "chat-a")
        val b = p.mask("Frau Quandel", scope = "chat-b")
        assertEquals("Herr [Name 1]", a)
        assertEquals("Frau [Name 1]", b)
        assertEquals("Becker", p.unmask("[Name 1]", scope = "chat-a"))
        assertEquals("Quandel", p.unmask("[Name 1]", scope = "chat-b"))
        val shared = pseudonymizer(off = setOf(PiiFeature.PER_CHAT))
        shared.mask("Herr Becker", scope = "chat-a")
        assertEquals("Frau [Name 2]", shared.mask("Frau Quandel", scope = "chat-b"))
    }

    @Test
    fun suspectsAndClassListImport() {
        val s = PiiDetector.suspects("Der Schüler Jolin und Anna Quandel kamen zu spät.", known = listOf("Anna"))
        assertTrue("Jolin" in s && "Quandel" in s, s.toString())
        assertEquals(listOf("Jolin Quandel", "Ylvi Wiesenhütter"), NameList.parse("Vorname;Nachname;Klasse\nJolin;Quandel;7b\nYlvi;Wiesenhütter;7b"))
        assertEquals(listOf("Aurelio Müller", "Eleni Tsoukalas"), NameList.parse("| Name | Note |\n|---|---|\n| Müller, Aurelio | 2 |\n| Tsoukalas, Eleni | 1 |"))
        assertEquals(listOf("Jolin Quandel", "Ylvi Wiesenhütter"), NameList.parse("1. Jolin Quandel\n2. Ylvi Wiesenhütter\n\n"))
        assertEquals("A\nB", NameList.merge("A", listOf("a", "B")))
    }

    @Test
    fun noFalseAlarmsInEverydayTexts() {
        listOf(
            "Max Weber prägte den Begriff der Bürokratie. Laut Piaget entwickeln Kinder Schemata. Im Mai 2025 war die Tagung.",
            "fun main() {\n    @Suppress(\"UNUSED\")\n    val x = listOf(1, 2, 3)\n}\n@dataclass\nclass Foo: pass",
            "- Termin am 12.03.2026 um 14:00 Uhr in Raum 0.12\n- Budget: 1.234,56 €\n- Version 2.1.3, Abschnitt 2.4.10.1",
            "Liebe Eltern,\nam Freitag ist Wandertag.\n\nMit freundlichen Grüßen\nIhre Klassenleitung",
            "Heute hat die Schülerin die Aufgabe gelöst. Der Lehrer Mathematik unterrichtet. ISBN 978-3-16-148410-0, DOI 10.1000/182.",
        ).forEach { t -> assertTrue(PiiDetector.find(t).isEmpty(), "$t → ${PiiDetector.find(t)}") }
        val hits = PiiDetector.find("Anna-Lena und Ben-Luca haben Ole geholfen. Emma Schulz-Wagner kommt.").map { it.value }
        listOf("Anna-Lena", "Ben-Luca", "Ole", "Emma Schulz-Wagner").forEach { assertTrue(it in hits, "$it fehlt: $hits") }
    }
}
