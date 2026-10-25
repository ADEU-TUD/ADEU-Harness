package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.data.PageMarks
import io.github.thepytales.llmhub.docs.AnkiWriter
import io.github.thepytales.llmhub.docs.ChartBlocks
import io.github.thepytales.llmhub.docs.ChartLayout
import io.github.thepytales.llmhub.docs.ChartSpec
import io.github.thepytales.llmhub.docs.LimeSurveyWriter
import io.github.thepytales.llmhub.docs.MathText
import io.github.thepytales.llmhub.docs.QuizFormats
import io.github.thepytales.llmhub.docs.SemesterPlan
import io.github.thepytales.llmhub.docs.XlsxWriter
import io.github.thepytales.llmhub.research.Feeds
import io.github.thepytales.llmhub.transcribe.Codebook
import io.github.thepytales.llmhub.transcribe.Intercoder
import io.github.thepytales.llmhub.transcribe.RefiQda
import io.github.thepytales.llmhub.transcribe.Segment
import io.github.thepytales.llmhub.transcribe.Transcript
import io.github.thepytales.llmhub.transcribe.TranscriptCoding
import io.github.thepytales.llmhub.transcribe.withCoding
import kotlinx.serialization.json.JsonObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormatsAndResearchTest {
    private val out = File("build/test-output").apply { mkdirs() }
    private fun xml(text: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(text.byteInputStream())

    @Test
    fun excelHasOneSheetPerTableAndNumbersStayNumbers() {
        val md = """
            # Auswertung
            ## Noten
            | Name | Note | Anteil |
            | --- | --- | --- |
            | A | 1,7 | 25 % |
            | B | 2.3 | 50 % |
            ## Teilnahme
            | Sitzung | Anwesend |
            | --- | --- |
            | 1 | 24 |
        """.trimIndent()
        val f = File(out, "auswertung.xlsx")
        XlsxWriter.write(f, "Auswertung", md)
        ZipFile(f).use { z ->
            val wb = z.getInputStream(z.getEntry("xl/workbook.xml")).readBytes().toString(Charsets.UTF_8)
            assertTrue("name=\"Noten\"" in wb && "name=\"Teilnahme\"" in wb, wb)
            val sheet = z.getInputStream(z.getEntry("xl/worksheets/sheet1.xml")).readBytes().toString(Charsets.UTF_8)
            xml(sheet)
            assertTrue("<v>1.7</v>" in sheet && "<v>2.3</v>" in sheet && "<v>0.25</v>" in sheet, sheet)
        }
        assertEquals(1.7, XlsxWriter.number("1,7"))
        assertEquals(1234.5, XlsxWriter.number("1.234,5"))
        assertEquals(null, XlsxWriter.number("Klasse 7a"))
        assertTrue(XlsxWriter.csv("A", md).contains("Name;Note;Anteil"))
    }

    private val quiz = """
        # Selbsttest
        ## Allgegenwärtigkeit (1 Punkt)
        Was beschreibt Kounin?
        - [x] Alles im Blick haben
        - [ ] Häufiger Methodenwechsel
        Feedback: withitness
        ## Basisdimensionen (2 Punkte)
        Welche gehören dazu?
        - [x] Klassenführung
        - [x] Kognitive Aktivierung
        - [ ] Hausaufgaben
        ## Begriff
        Wie heißt die Zeit zwischen Frage und Aufruf?
        Antwort: Wartezeit | Wait time
    """.trimIndent()

    @Test
    fun moodleXmlAndGift() {
        val moodle = QuizFormats.moodleXml("Test", quiz)
        val doc = xml(moodle)
        assertEquals(4, doc.getElementsByTagName("question").length)
        assertTrue("fraction=\"50\"" in moodle && "fraction=\"-100\"" in moodle, moodle)
        val gift = QuizFormats.gift("Test", quiz)
        assertTrue("::Allgegenwärtigkeit::Was beschreibt Kounin? { =Alles im Blick haben ~Häufiger Methodenwechsel ####withitness }" in gift, gift)
        assertTrue("~%50%Klassenführung" in gift && "=Wartezeit =Wait time" in gift, gift)
    }

    @Test
    fun ankiCardsFromTableAndLines() {
        val cards = AnkiWriter.cards("| Vorderseite | Rückseite |\n| --- | --- |\n| Klassenführung | Steuerung des Unterrichtsgeschehens |\n\nScaffolding :: Unterstützung, die schrittweise zurückgenommen wird")
        assertEquals(2, cards.size)
        assertTrue(AnkiWriter.write("Didaktik", "A :: B").startsWith("#separator:tab\n#html:true\n#deck:Didaktik"))
    }

    @Test
    fun limeSurveyStructure() {
        val text = LimeSurveyWriter.write("Befragung", """
            # Lehrkräftebefragung
            Danke für Ihre Teilnahme.
            ## Gruppe: Person
            ### Wie alt sind Sie? [zahl]
            ### Welches Fach? [einfach]
            - Mathematik
            - Deutsch
            ## Gruppe: Unterricht
            ### Wie sehr stimmen Sie zu? [matrix] Skala: nein | eher nein | eher ja | ja
            - Ich plane sorgfältig.
            - Ich nutze Feedback.
            ### Anmerkungen [text, optional]
        """.trimIndent())
        val rows = text.trimEnd('\n').lines().map { it.split('\t') }
        assertEquals("class", rows[0][2])
        assertEquals(listOf("G", "G"), rows.filter { it[2] == "G" }.map { it[2] })
        val questions = rows.filter { it[2] == "Q" }
        assertEquals(listOf("N", "L", "F", "T"), questions.map { it[3] })
        assertEquals("N", questions.last()[10])
        assertEquals(4, rows.count { it[2] == "A" && it[6] in setOf("nein", "eher nein", "eher ja", "ja") })
        assertTrue(rows.all { it.size == 14 }, text)
    }

    @Test
    fun chartsBecomeSvgAndTables() {
        val spec = ChartSpec.parse("""{"type":"bar","title":"Teilnahme","labels":["S1","S2","S3"],"series":[{"name":"2025","values":[20,"18",25]},{"name":"2026","values":[22,21,24]}]}""")!!
        assertEquals(ChartSpec.Type.BAR, spec.type)
        assertEquals(listOf(20.0, 18.0, 25.0), spec.series[0].values)
        xml(ChartLayout.svg(spec))
        listOf("box", "histogram", "pie", "line", "scatter", "hbar", "stacked").forEach { t ->
            val s = ChartSpec.parse("""{"type":"$t","labels":["a","b"],"series":[{"name":"x","values":[1,2,3,4,10]},{"name":"y","values":[2,3,4,5,6],"x":[1,2,3,4,5]}]}""")!!
            xml(ChartLayout.svg(s))
        }
        val md = ChartBlocks.asTables("Text\n\n```chart\n{\"type\":\"pie\",\"title\":\"Anteile\",\"labels\":[\"A\",\"B\"],\"values\":[1,3]}\n```\n")
        assertTrue("**Abbildung: Anteile** (Daten)" in md && "| A | 1 |" in md, md)
        assertEquals(listOf(0.0, 5.0, 10.0, 15.0, 20.0, 25.0), ChartLayout.ticks(0.0, 25.0))
    }

    @Test
    fun formulasAsText() {
        assertEquals("x̄ = ∑ xᵢ / n", MathText.toUnicode("\\bar{x} = \\sum x_i / n"))
        assertEquals("α ≤ 0,05", MathText.toUnicode("\\alpha \\leq 0,05"))
        assertEquals("√(s²)", MathText.toUnicode("\\sqrt{s^2}"))
        assertEquals("(a+b)/2", MathText.toUnicode("\\frac{a+b}{2}"))
        assertEquals("Effekt d = (M₁ − M₂)/SD", MathText.inText("Effekt \$d = \\frac{M_1 − M_2}{SD}\$"))
        assertEquals("kostet 5 $ und 10 $", MathText.inText("kostet 5 $ und 10 $"))
    }

    @Test
    fun saxonHolidaysAndSessions() {
        assertEquals(LocalDate.of(2026, 4, 5), SemesterPlan.easter(2026))
        val h = SemesterPlan.holidays(2026)
        assertEquals("Buß- und Bettag", h[LocalDate.of(2026, 11, 18)])
        assertEquals("Reformationstag", h[LocalDate.of(2026, 10, 31)])
        assertEquals("Karfreitag", h[LocalDate.of(2026, 4, 3)])
        val (sessions, skipped) = SemesterPlan.plan(
            DayOfWeek.WEDNESDAY, LocalDate.of(2026, 10, 14), LocalDate.of(2027, 1, 13),
            SemesterPlan.ranges("21.12.2026 bis 03.01.2027"), listOf("Einführung", "Klassenführung"),
        )
        assertTrue(skipped.any { it.date == LocalDate.of(2026, 11, 18) })
        assertTrue(skipped.any { it.date == LocalDate.of(2026, 12, 23) && it.reason == "vorlesungsfrei" })
        assertEquals("Klassenführung", sessions[1].topic)
        assertEquals(LocalDate.of(2026, 10, 14), sessions.first().date)
        assertEquals(java.time.LocalTime.of(13, 0), SemesterPlan.time("4. DS")!!.first)
    }

    private fun transcript() = Transcript(
        id = "t1", title = "Interview 1", created = "2026-10-01", duration = 60.0, language = "de", modelName = "x", device = null,
        speakers = mapOf(0 to "I", 1 to "B1"), segments = listOf(
            Segment(0, 0.0, 5.0, "Wie planen Sie Ihren Unterricht?", 0),
            Segment(1, 5.0, 20.0, "Ich plane mit Lernzielen & Material.", 1, comment = "wichtig"),
            Segment(2, 20.0, 30.0, "Störungen gibt es selten.", 1),
        ),
        audio = null, folder = null, marks = emptyList(), notice = null, raw = JsonObject(emptyMap()),
    )

    @Test
    fun codingIntercoderAndRefiQda() {
        val book = Codebook.parse("KS", "Planung: wie Unterricht geplant wird\n  Lernziele\nKlassenführung: Umgang mit Störungen")
        assertEquals(listOf("Planung", "Lernziele", "Klassenführung"), book.codes.map { it.name })
        assertEquals(book.codes[0].id, book.codes[1].parentId)
        val fromTable = Codebook.parse("T", "| Kategorie | Definition | Ankerbeispiel |\n| --- | --- | --- |\n| Planung | … | „Ich plane …“ |")
        assertEquals("Planung", fromTable.codes.single().name)

        var t = transcript()
        var c = TranscriptCoding(book.id, emptyList())
        val s = t.segments
        c = c.toggle(s[1], book.codes[0].id, "AB").toggle(s[1], book.codes[1].id, "AB").toggle(s[2], book.codes[2].id, "AB")
        c = c.toggle(s[1], book.codes[0].id, "CD").toggle(s[2], book.codes[0].id, "CD")
        t = t.withCoding(c)
        val back = TranscriptCoding.of(t)
        assertEquals(5, back.codings.size)
        assertEquals(listOf("AB", "CD"), back.coders())
        val r = Intercoder.compare(t, back, book, "AB", "CD")
        assertEquals(2, r.passages)
        assertEquals(0.0, r.exactAgreement)
        assertTrue(Intercoder.report(r).contains("| Planung | 1 | 0 | 1 |"), Intercoder.report(r))

        val bytes = ByteArrayOutputStream().also { RefiQda.write(it, t, back, book) }.toByteArray()
        val entries = mutableMapOf<String, String>()
        ZipInputStream(bytes.inputStream()).use { z -> generateSequence { z.nextEntry }.forEach { e -> entries[e.name] = z.readBytes().toString(Charsets.UTF_8) } }
        val qde = entries.getValue("project.qde")
        xml(qde)
        assertEquals(3, Regex("<Code guid").findAll(qde).count())
        assertEquals(5, Regex("<Coding guid").findAll(qde).count())
        val source = entries.entries.first { it.key.startsWith("Sources/") }.value
        val (text, ranges) = RefiQda.sourceText(t)
        assertEquals(source, text)
        assertEquals("Ich plane mit Lernzielen & Material.", String(text.codePoints().toArray(), ranges[1].first, ranges[1].last - ranges[1].first + 1))
        assertTrue("<Note " in qde && "wichtig" in qde)
        assertTrue(RefiQda.csv(t, back, book).contains("\"Planung › Lernziele\""))
    }

    @Test
    fun feedsAndPageMarks() {
        val rss = Feeds.parse("<rss><channel><title>X</title><item><title>Neu: Heft 3</title><link>https://a.example/3</link><pubDate>Mon, 05 Oct 2026</pubDate><description>&lt;p&gt;Inhalt&lt;/p&gt;</description></item></channel></rss>")
        assertEquals("Neu: Heft 3", rss.single().title)
        assertEquals("Inhalt", rss.single().summary)
        val atom = Feeds.parse("<feed xmlns=\"http://www.w3.org/2005/Atom\"><entry><title>A</title><link rel=\"alternate\" href=\"https://b.example\"/><updated>2026-10-01</updated></entry></feed>")
        assertEquals("https://b.example", atom.single().link)
        val text = PageMarks.number("Erste Seite\u000CZweite Seite mit Zitat\u000C")
        assertEquals("[Seite 1]\nErste Seite\n\n[Seite 2]\nZweite Seite mit Zitat", text)
        assertEquals(2, PageMarks.pageAt(text, text.indexOf("Zitat")))
    }
}

class TranscriptToolsTest {
    @Test
    fun anonymizationIsConsistentAndKeepsTimes() {
        val t = Transcript(
            id = "t2", title = "Gespräch mit Frau Müller", created = "", duration = 30.0, language = "de", modelName = "", device = null,
            speakers = mapOf(0 to "I", 1 to "Anna Müller"),
            segments = listOf(
                Segment(0, 0.0, 5.0, "Guten Tag Frau Müller, erreichbar unter anna.mueller@example.org?", 0),
                Segment(1, 5.0, 9.0, "Ja. Anna reicht. Mein Kollege Herr Schmidt kommt später.", 1),
            ),
            audio = null, folder = null, marks = emptyList(), notice = null, raw = kotlinx.serialization.json.JsonObject(emptyMap()),
        )
        val r = io.github.thepytales.llmhub.transcribe.TranscriptAnon.anonymize(t, emptyList())
        val text = r.transcript.segments.joinToString(" ") { it.text }
        assertTrue("Müller" !in text && "Schmidt" !in text && "@" !in text && "Anna" !in text, text)
        assertEquals("Person A", r.transcript.speakers[1])
        assertEquals("I", r.transcript.speakers[0])
        assertEquals(listOf(0.0, 5.0), r.transcript.segments.map { it.start })
        assertTrue(r.mapping.any { it.first == "Anna Müller" && it.second == "Person A" })
        assertTrue(io.github.thepytales.llmhub.transcribe.TranscriptAnon.mappingTable("x", r.mapping).contains("| Anna Müller | Person A |"))
    }

    @Test
    fun translationLinesAndVoiceMatching() {
        assertEquals(listOf("Hello", null, "Bye"), io.github.thepytales.llmhub.transcribe.TranscriptTranslate.parse("1\tHello\n3\tBye\nnoise", 3))
        val p1 = io.github.thepytales.llmhub.transcribe.VoiceProfile(name = "Interviewer", vector = listOf(1f, 0f, 0f))
        val p2 = io.github.thepytales.llmhub.transcribe.VoiceProfile(name = "Anna", vector = listOf(0f, 1f, 0f))
        val m = io.github.thepytales.llmhub.transcribe.Voices.match(mapOf(0 to floatArrayOf(0.1f, 0.9f, 0f), 1 to floatArrayOf(0.95f, 0.05f, 0f), 2 to floatArrayOf(0f, 0f, 1f)), listOf(p1, p2))
        assertEquals(listOf(0 to "Anna", 1 to "Interviewer"), m.map { it.speaker to it.profile.name })
    }

    @Test
    fun seriesDataParsesTablesAndCsv() {
        val sd = io.github.thepytales.llmhub.docs.SeriesData
        val csv = sd.parse("Name;Kurs\n\"Meier, Jana\";Mathe\nLi;Physik")
        assertEquals(listOf(listOf("Name", "Kurs"), listOf("Meier, Jana", "Mathe"), listOf("Li", "Physik")), csv)
        val md = sd.parse("| Name | Kurs |\n| --- | --- |\n| Jana | Mathe |")
        assertEquals(listOf("Jana", "Mathe"), md.last())
        assertEquals("Liebe Jana, Kurs Mathe {{Fehlt}}", sd.fill("Liebe {{ name }}, Kurs {{Kurs|Standard}} {{Fehlt}}", mapOf("Name" to "Jana", "Kurs" to "Mathe")))
    }
}
