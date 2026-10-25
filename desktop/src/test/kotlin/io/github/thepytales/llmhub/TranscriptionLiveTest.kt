package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.desktop.Paths
import io.github.thepytales.llmhub.transcribe.StartOptions
import io.github.thepytales.llmhub.transcribe.TranscriptExport
import io.github.thepytales.llmhub.transcribe.TranscriptionMode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A real transcription on this machine with sherpa-onnx: loads Whisper Base (160 MB) through the app, transcribes the
 * built-in recording with four speakers and checks text and speakers. Needs internet and the native libraries:
 * TRANSCRIBE_LIVE=1 ./gradlew -p desktop test --tests '*TranscriptionLiveTest*'
 */
class TranscriptionLiveTest {
    companion object {
        /** One app (and engine) for all tests of this class. */
        private val app by lazy {
            val natives = File("build/speech/desktop/" + if (System.getProperty("os.name").startsWith("Windows")) "windows" else "linux")
            System.setProperty("compose.application.resources.dir", natives.absolutePath)
            val dir = File("build/transcription-live-test").apply { mkdirs() }
            if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", dir.absolutePath)
            AppContainer(Context(Paths.data, Paths.cache))
        }
    }

    private fun ready(): AppContainer {
        assumeTrue(System.getenv("TRANSCRIBE_LIVE") == "1", "TRANSCRIBE_LIVE nicht gesetzt – übersprungen")
        val natives = File("build/speech/desktop/" + if (System.getProperty("os.name").startsWith("Windows")) "windows" else "linux")
        assumeTrue(File(natives, "native").isDirectory, "native Bibliotheken fehlen (gradle speechDesktopNatives)")
        runBlocking { assertTrue(app.transcription.checkAvailable(), app.transcription.unavailableReason ?: "") }
        return app
    }

    /** Loads a model through the app (from the fixed Hugging Face commit, checked by SHA-256). */
    private fun model(c: AppContainer, id: String) {
        val t = c.transcription
        if (t.state.value.models.none { it.id == id && it.installed }) t.download(id)
        val deadline = System.currentTimeMillis() + 20 * 60_000
        while (t.state.value.models.none { it.id == id && it.installed }) {
            assertTrue(System.currentTimeMillis() < deadline, "Download dauert zu lange")
            Thread.sleep(1000)
        }
    }

    private fun waitForJobs(c: AppContainer, done: () -> Boolean) {
        val t = c.transcription
        val deadline = System.currentTimeMillis() + 30 * 60_000
        while (t.state.value.jobs.any { it.active } || !done()) {
            assertTrue(System.currentTimeMillis() < deadline, "Dauert zu lange: ${t.state.value.jobs}")
            t.state.value.jobs.firstOrNull { it.status == "error" }?.let { error("Fehler: ${it.error}") }
            Thread.sleep(1000)
        }
    }

    /**
     * Whisper text, word times measured with Parakeet (de.lokal.whisper.WordAlign), then measured again for an existing
     * transcript. Needs a recording of clear speech (the built-in calibration recording is mostly overlapping voices):
     * TRANSCRIBE_LIVE=1 TRANSCRIBE_WAV=interview.wav TRANSCRIBE_LANG=de ./gradlew -p desktop test --tests '*measuresWordTimes*'
     */
    @Test
    fun measuresWordTimesForWhisper() {
        val c = ready()
        val wav = File(System.getenv("TRANSCRIBE_WAV") ?: "")
        assumeTrue(wav.isFile, "TRANSCRIBE_WAV (Aufnahme mit deutlicher Sprache) nicht gesetzt – übersprungen")
        val t = c.transcription
        model(c, "base")
        model(c, "parakeet")
        val title = "Wortzeiten-Test " + System.currentTimeMillis()
        runBlocking { t.import(wav.name, wav.length(), wav.inputStream()) }
        val token = t.incoming.value.first().token
        t.start(token, StartOptions(mode = TranscriptionMode.LOCAL, model = "base", language = System.getenv("TRANSCRIBE_LANG") ?: "de", speakers = "off", title = title))
        waitForJobs(c) { t.transcripts.value.any { it.title == title } }
        val id = t.transcripts.value.first { it.title == title }.id
        val tr = runBlocking { t.load(id) }!!
        println("Gemessen: ${Math.round(tr.measuredShare * 100)} % von ${tr.wordCount} Wörtern, ${tr.wordTiming}")
        assertEquals("Parakeet v3", tr.wordTiming?.source)
        assertTrue(tr.measuredShare > 0.5, "Anteil gemessen: ${tr.measuredShare}")
        assertTrue(tr.segments.all { s -> s.words.isEmpty() || s.words.size == s.text.trim().split(Regex("\\s+")).size }, "Wörter passen zum Text")

        // Measure again for an existing transcript (e.g. from before this version)
        runBlocking { t.save(tr.copy(segments = tr.segments.map { it.copy(words = emptyList()) })) }
        t.measureWords(id)
        waitForJobs(c) { runBlocking { t.load(id) }!!.measuredShare > 0 }
        val again = runBlocking { t.load(id) }!!
        assertEquals(tr.measuredShare, again.measuredShare, 0.02)
    }

    @Test
    fun transcribesWithSpeakers() {
        val c = ready()
        val t = c.transcription
        model(c, "base")
        val deadline = System.currentTimeMillis() + 15 * 60_000

        val wav = File("../speech/models/cal-4speakers.wav")
        runBlocking { t.import("Vier Sprecher.wav", wav.length(), wav.inputStream()) }
        val token = t.incoming.value.first().token
        t.start(token, StartOptions(mode = TranscriptionMode.LOCAL, model = "base", language = "en", speakers = "auto", title = "Live-Test"))
        while (t.state.value.jobs.any { it.active } || t.transcripts.value.none { it.title == "Live-Test" }) {
            assertTrue(System.currentTimeMillis() < deadline, "Transkription dauert zu lange: ${t.state.value.jobs}")
            t.state.value.jobs.firstOrNull { it.status == "error" }?.let { error("Fehler: ${it.error}") }
            Thread.sleep(1000)
        }
        val id = t.transcripts.value.first { it.title == "Live-Test" }.id
        val tr = runBlocking { t.load(id) }!!
        println("Transkript: ${tr.segments.size} Abschnitte, ${tr.speakers.size} Sprecher\n" + TranscriptExport.plainText(tr).take(800))
        assertTrue(tr.wordCount > 40, "Text erkannt: ${tr.wordCount} Wörter")
        assertTrue(tr.speakers.size >= 2, "Sprecher erkannt: ${tr.speakers.size}")
        assertTrue(TranscriptExport.srt(tr).contains("-->"))
    }
}
