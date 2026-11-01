package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.agent.BatchRunner
import io.github.thepytales.llmhub.data.BatchCriterion
import io.github.thepytales.llmhub.data.BatchDecision
import io.github.thepytales.llmhub.data.BatchGrading
import io.github.thepytales.llmhub.data.BatchItem
import io.github.thepytales.llmhub.data.BatchJob
import io.github.thepytales.llmhub.data.BatchStore
import io.github.thepytales.llmhub.data.AppTool
import io.github.thepytales.llmhub.ui.unzip
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stapel und Massenkontrolle (AP-20). */
class BatchTest {
    private fun dir(): File = Files.createTempDirectory("lora-batch").toFile()

    private fun sources(n: Int) = (1..n).map { BatchStore.Source("Arbeit %02d.pdf".format(it), "--- Seite 1 ---\nText der Arbeit $it über Feedback im Unterricht. " + "Satz. ".repeat(40), 1) }

    private suspend fun waitFor(timeoutMs: Long = 20_000, condition: () -> Boolean) = withTimeout(timeoutMs) { while (!condition()) delay(20) }

    @Test
    fun eightyFilesRunThroughWithoutHelp() = runBlocking {
        val base = dir()
        val store = BatchStore(base)
        val calls = AtomicInteger()
        val runner = BatchRunner(store, ask = { _, m -> calls.incrementAndGet(); "Zusammenfassung von " + m.last().content.lines().first() }, retryDelayMs = 0)
        val job = store.create(BatchJob(title = "80 PDFs", instruction = "Fasse zusammen.", model = "m"), sources(80))
        runner.start(job.id)
        waitFor { store.get(job.id)!!.state == BatchJob.DONE }
        val done = store.get(job.id)!!
        assertEquals(80, done.items.count { it.status == BatchItem.DONE })
        assertEquals(80, calls.get(), "jede Datei genau einmal")
        assertTrue(done.items[41].result.contains("Arbeit 42.pdf"))
        // Everything is on disk: a new store (next start of the app) sees the same.
        val again = BatchStore(base)
        assertEquals(80, again.get(job.id)!!.items.count { it.status == BatchItem.DONE })
        assertTrue(BatchGrading.taskResults(done).contains("## Arbeit 80.pdf"))
    }

    @Test
    fun pauseAndRestartLoseNothing() = runBlocking {
        val base = dir()
        val store = BatchStore(base)
        val seen = ConcurrentHashMap<String, Int>()
        val slowAsk: suspend (BatchJob, List<io.github.thepytales.llmhub.data.ChatMessage>) -> String = { _, m ->
            delay(15)
            val name = m.last().content.lines().first()
            seen.merge(name, 1, Int::plus)
            "ok $name"
        }
        val runner = BatchRunner(store, ask = slowAsk, retryDelayMs = 0)
        val job = store.create(BatchJob(title = "Pause", instruction = "x", model = "m"), sources(40))
        runner.start(job.id)
        waitFor { store.get(job.id)!!.done >= 10 }
        runner.pause(job.id)
        waitFor { runner.current.value == null }
        val paused = store.get(job.id)!!
        assertEquals(BatchJob.PAUSED, paused.state)
        assertTrue(paused.items.none { it.status == BatchItem.RUNNING }, "die laufende Datei geht zurück in die Warteschlange")
        val doneAtPause = paused.done
        delay(100)
        assertEquals(doneAtPause, store.get(job.id)!!.done, "pausiert heißt: nichts läuft weiter")

        // A crash while a file runs: it is marked RUNNING on disk; the next start puts it back into the queue.
        store.updateItem(job.id, paused.items.first { it.status == BatchItem.PENDING }.id) { it.copy(status = BatchItem.RUNNING) }
        store.update(job.id) { it.copy(state = BatchJob.RUNNING) }
        val restarted = BatchStore(base)
        val runner2 = BatchRunner(restarted, ask = slowAsk, retryDelayMs = 0)
        runner2.resume()
        waitFor { restarted.get(job.id)!!.state == BatchJob.DONE }
        val done = restarted.get(job.id)!!
        assertEquals(40, done.items.count { it.status == BatchItem.DONE })
        assertEquals(40, done.items.map { it.result }.distinct().size)
        // At most the one file that was cut off by „Pause“ was sent twice.
        assertTrue(seen.values.count { it > 1 } <= 1, "doppelt: " + seen.filterValues { it > 1 })
    }

    @Test
    fun failedFilesAreTriedThreeTimesAndChatsComeFirst() = runBlocking {
        val store = BatchStore(dir())
        val attempts = ConcurrentHashMap<String, Int>()
        var busy = true
        val runner = BatchRunner(
            store,
            ask = { _, m ->
                val name = m.last().content.lines().first()
                val n = attempts.merge(name, 1, Int::plus)!!
                when {
                    "03" in name -> error("Server nicht erreichbar")
                    "02" in name && n < 3 -> error("kurz weg")
                    else -> "ok"
                }
            },
            chatsBusy = { busy },
            retryDelayMs = 0,
        )
        val job = store.create(BatchJob(title = "Fehler", instruction = "x", model = "m"), sources(4))
        runner.start(job.id)
        delay(300)
        assertEquals(0, store.get(job.id)!!.done, "solange ein Chat antwortet, wartet der Stapel")
        assertNotNull(runner.waiting.value)
        busy = false
        waitFor { store.get(job.id)!!.state == BatchJob.DONE }
        val j = store.get(job.id)!!
        assertEquals(BatchItem.DONE, j.items[1].status, "beim dritten Versuch geklappt")
        assertEquals(BatchItem.FAILED, j.items[2].status)
        assertEquals(3, j.items[2].attempts)
        assertTrue(j.items[2].error!!.contains("nicht erreichbar"))
        runner.retryFailed(job.id)
        waitFor { store.get(job.id)!!.state == BatchJob.DONE }
        assertEquals(6, attempts.filterKeys { "03" in it }.values.single())
    }

    private val rubric = listOf(
        BatchCriterion("Fragestellung", 10.0, "klar"),
        BatchCriterion("Theorie", 10.0),
        BatchCriterion("Form", 5.0),
    )

    @Test
    fun gradingAnswersAreReadAndChecked() {
        val answer = """Hier die Bewertung:
            ```json
            {"bewertung":[{"kriterium":"Fragestellung","punkte":8,"begruendung":"Klar formuliert.","belege":["Feedback wirkt, wenn es konkret ist (S. 2)"]},
            {"kriterium":"theorie","punkte":"12,5","begruendung":"Gut."},{"kriterium":"Form","punkte":4.5}],
            "rueckmeldung":"Insgesamt gelungen.","zusammenfassung":"Solide Arbeit."}
            ```""".trimIndent()
        val g = BatchGrading.parse(answer, rubric)!!
        assertEquals(listOf(8.0, 10.0, 4.5), g.scores.map { it.points }, "über dem Höchstwert wird gekappt, Komma geht")
        assertEquals(22.5, g.total)
        assertEquals("Insgesamt gelungen.", g.feedback)
        assertNull(BatchGrading.parse("Leider kann ich das nicht bewerten.", rubric))
        // Passages: found in the text despite line breaks and the page reference; invented ones are not.
        val text = "--- Seite 2 ---\nWie Hattie zeigt, wirkt Feedback, wenn es\nkonkret ist (Hattie 2009)."
        assertTrue(BatchGrading.quoteFound("„wirkt Feedback, wenn es konkret ist“ (S. 2)", text))
        assertFalse(BatchGrading.quoteFound("Feedback wirkt immer", text))
    }

    @Test
    fun rasterOutliersTableAndCalibration() {
        val parsed = BatchGrading.parseRubric(
            """
            | Kriterium | Punkte | Beschreibung |
            | --- | --- | --- |
            | Fragestellung | 10 | klar und begründet |
            Theorie; 15 P.; Forschungsstand
            Form und Sprache (5 Punkte)
            """.trimIndent()
        )
        assertEquals(listOf("Fragestellung", "Theorie", "Form und Sprache"), parsed.map { it.name })
        assertEquals(listOf(10.0, 15.0, 5.0), parsed.map { it.max })
        assertEquals("klar und begründet", parsed[0].description)

        val store = BatchStore(dir())
        var job = store.create(BatchJob(title = "Hausarbeiten", kind = BatchJob.GRADING, instruction = "Umfang 12 Seiten", model = "m", rubric = rubric), sources(8))
        // Two works graded by hand beforehand: examples for the AI, not sent for grading.
        store.decide(job.id, job.items[0].id, BatchDecision(listOf(9.0, 8.0, 5.0), "sehr gut", calibration = true), "Kalibrierung")
        job = store.get(job.id)!!
        assertEquals(BatchItem.SKIPPED, job.items[0].status)
        val runner = BatchRunner(store, ask = { _, _ -> "" })
        val system = runner.messages(job, job.items[1], "Text").first().content
        assertTrue("KALIBRIERUNG" in system && "Fragestellung = 9" in system && "sehr gut" in system)
        assertTrue("0–10 Punkte" in system && "Gesamt höchstens 25" in system && "Umfang 12 Seiten" in system)
        assertTrue("Ein Mensch prüft jeden Vorschlag" in system)

        // Suggestions: one work far off the others is an outlier.
        val totals = listOf(18.0, 19.0, 17.5, 18.5, 19.5, 18.0, 2.0)
        job.items.drop(1).zip(totals).forEach { (i, t) ->
            store.updateItem(job.id, i.id) { it.copy(status = BatchItem.DONE, grade = io.github.thepytales.llmhub.data.BatchGrade(listOf(
                io.github.thepytales.llmhub.data.BatchScore("Fragestellung", t / 2), io.github.thepytales.llmhub.data.BatchScore("Theorie", t / 2), io.github.thepytales.llmhub.data.BatchScore("Form", 0.0),
            ), feedback = "Rückmeldung $t")) }
        }
        job = store.get(job.id)!!
        val outliers = BatchGrading.outliers(job)
        assertEquals(setOf(job.items[7].id), outliers)
        // Decisions go into the protocol; a big difference to the suggestion is flagged too.
        store.decide(job.id, job.items[1].id, BatchDecision(listOf(9.0, 9.0, 0.0), accepted = true), "Vorschlag übernommen")
        store.decide(job.id, job.items[2].id, BatchDecision(listOf(2.0, 2.0, 0.0), "Plagiatsverdacht"), "angepasst")
        job = store.get(job.id)!!
        assertTrue(job.items[2].id in BatchGrading.outliers(job))
        assertEquals(3, job.log.size)
        val table = BatchGrading.resultTable(job)
        assertTrue("| Datei | Fragestellung (KI) |" in table && "übernommen" in table && "angepasst" in table && "Ausreißer" in table && "von Hand (Kalibrierung)" in table)
        val feedback = BatchGrading.feedback(job, job.items[2])
        assertTrue("**4 / 25**" in feedback && "Plagiatsverdacht" in feedback)
        assertTrue(BatchGrading.protocol(job).contains("Plagiatsverdacht"))
        // Export to Excel and Word works.
        io.github.thepytales.llmhub.docs.XlsxWriter.write(File(dir(), "t.xlsx").also { assertTrue(it.parentFile.exists()) }, "Ergebnisse", table)
    }

    @Test
    fun zipsOfSubmissionsAreUnpackedPerPerson() = runBlocking {
        fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        }.toByteArray()
        val inner = zip(mapOf("Hausarbeit.txt" to "Text von Anna über Lernen und Lehren an der Schule.".toByteArray()))
        val bytes = zip(
            mapOf(
                "Abgaben/Mueller_Ben/Hausarbeit.md" to "# Hausarbeit\nText von Ben über Unterricht und Didaktik.".toByteArray(),
                "Abgaben/Schulz_Anna.zip" to inner,
                "__MACOSX/._x" to byteArrayOf(0, 1),
                "Abgaben/.DS_Store" to byteArrayOf(0),
            )
        )
        val (read, failed) = unzip(bytes)
        assertEquals(0, failed)
        assertEquals(listOf("Mueller_Ben/Hausarbeit.md", "Schulz_Anna/Hausarbeit.txt"), read.map { it.name })
        assertTrue(read[1].text.contains("Anna"))
    }

    @Test
    fun theToolIsSwitchable() {
        val tool = AppTool.byId("batch")!!
        assertEquals("batches", tool.route)
        assertEquals(tool, AppTool.suggestFor("Bitte die 60 Hausarbeiten nach meinem Bewertungsraster vorbewerten", emptySet(), android = false))
        assertEquals(AppTool.PROPOSALS, AppTool.suggestFor("Ich will einen DFG-Antrag stellen", emptySet(), android = false))
    }
}
