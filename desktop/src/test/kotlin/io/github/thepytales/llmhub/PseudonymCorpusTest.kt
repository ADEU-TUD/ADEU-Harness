package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.data.PiiDetector
import io.github.thepytales.llmhub.data.PiiKind
import io.github.thepytales.llmhub.data.Pseudonymizer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test collection of the pseudonymization (AP-03, AP-67): private names must be replaced, authors, public persons and
 * public sources must stay. The rates on the Messkorpus (tools/pii/messkorpus) are printed, so improvements are measurable.
 */
class PseudonymCorpusTest {
    private var settings = AppSettings(pseudonymize = true)

    private fun pseudonymizer(authors: List<String> = emptyList()): Pseudonymizer {
        val file = File.createTempFile("pseudonyms", ".json").apply { delete() }
        return Pseudonymizer(file, publicNames = { authors }) { settings }
    }

    /**
     * Messkorpus (AP-67, Stufe a): the rules alone on the test part and the development part of tools/pii/messkorpus,
     * each sample with a fresh pseudonymizer. The report with everything missed and wrongly replaced goes to
     * build/pii/regeln.txt. The limits keep the rules from getting worse; the goal of AP-67 (rules + model: at least
     * 98 % of the private details found, at most 2 % of the public ones replaced) is checked once the model is there.
     *
     * With PII_GRUENDLICH_URL (an OpenAI-compatible server with the local model, e.g. llama-server: http://localhost:8080/v1)
     * and PII_GRUENDLICH_MODELL the same is measured with „Gründlich“ (build/pii/gruendlich.txt).
     */
    @Test
    fun corpusOfTheRules() {
        val out = File("build/pii").apply { mkdirs() }
        val report = StringBuilder()
        val scores = listOf("pruef", "entwicklung").associateWith { section ->
            PseudonymCorpus.Score().also { score ->
                for (s in PseudonymCorpus.load(section)) score.add(s, PseudonymCorpus.replacedWords(s.text, pseudonymizer().mask(s.text)))
            }
        }
        scores.forEach { (section, score) -> report.append(details("Regeln, $section", score)) }
        File(out, "regeln.txt").writeText(report.toString())
        println(report)
        val test = scores.getValue("pruef")
        val (found, total) = test.recall()
        val (wrong, public) = test.falseRate()
        assertTrue(found * 100 >= total * RULES_MIN_FOUND, "Regeln finden weniger private Angaben: $found/$total")
        assertTrue(wrong * 100 <= public * RULES_MAX_WRONG, "Regeln ersetzen mehr öffentliche Angaben: $wrong/$public")

        val url = System.getenv("PII_GRUENDLICH_URL")?.takeIf { it.isNotBlank() } ?: return
        val model = System.getenv("PII_GRUENDLICH_MODELL").orEmpty()
        settings = settings.copy(pseudonymThorough = true)
        val thorough = StringBuilder()
        for (section in listOf("pruef", "entwicklung")) {
            val score = PseudonymCorpus.Score()
            for (s in PseudonymCorpus.load(section)) {
                val p = pseudonymizer().apply { thoroughFinder = localModel(url, model) }
                runBlocking { p.prepareThorough(listOf(ChatMessage(role = "user", content = s.text))) }
                score.add(s, PseudonymCorpus.replacedWords(s.text, p.mask(s.text)))
            }
            thorough.append(details("Regeln + Gründlich ($model), $section", score))
        }
        settings = settings.copy(pseudonymThorough = false)
        File(out, "gruendlich.txt").writeText(thorough.toString())
        println(thorough)
    }

    private fun details(title: String, score: PseudonymCorpus.Score) = buildString {
        append(score.report(title))
        appendLine("  übersehen:")
        score.missed.forEach { appendLine("    $it") }
        appendLine("  fälschlich ersetzt:")
        score.falseAlarms.forEach { appendLine("    $it") }
        appendLine()
    }

    /** „Gründlich“ like in the app (AppContainer), but with a model behind an OpenAI-compatible server. */
    private fun localModel(url: String, model: String): suspend (String) -> List<String> = { text ->
        val body = buildJsonObject {
            put("model", model)
            put("temperature", 0)
            put("chat_template_kwargs", buildJsonObject { put("enable_thinking", false) })
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", Pseudonymizer.THOROUGH_PROMPT) })
                add(buildJsonObject { put("role", "user"); put("content", text) })
            })
        }
        val request = HttpRequest.newBuilder(URI.create(url.trimEnd('/') + "/chat/completions"))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build()
        val answer = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body()
        val content = Json.parseToJsonElement(answer).jsonObject["choices"]!!.jsonArray[0].jsonObject["message"]!!
            .jsonObject["content"]!!.jsonPrimitive.content
        Pseudonymizer.parseThorough(content)
    }

    private companion object {
        /** Measured on the test part on 09.11.2026 (rules of 1.0.61: 78.2 % found, 9.9 % replaced), with a little room. */
        const val RULES_MIN_FOUND = 77
        const val RULES_MAX_WRONG = 11
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
