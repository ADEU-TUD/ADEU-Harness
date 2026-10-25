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
import kotlin.test.assertTrue

/**
 * A real transcription on this machine with sherpa-onnx: loads Whisper Base (160 MB) through the app, transcribes the
 * built-in recording with four speakers and checks text and speakers. Needs internet and the native libraries:
 * TRANSCRIBE_LIVE=1 ./gradlew -p desktop test --tests '*TranscriptionLiveTest*'
 */
class TranscriptionLiveTest {
    @Test
    fun transcribesWithSpeakers() {
        assumeTrue(System.getenv("TRANSCRIBE_LIVE") == "1", "TRANSCRIBE_LIVE nicht gesetzt – übersprungen")
        val natives = File("build/speech/desktop/" + if (System.getProperty("os.name").startsWith("Windows")) "windows" else "linux")
        assumeTrue(File(natives, "native").isDirectory, "native Bibliotheken fehlen (gradle speechDesktopNatives)")
        System.setProperty("compose.application.resources.dir", natives.absolutePath)
        val dir = File("build/transcription-live-test").apply { mkdirs() }
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", dir.absolutePath)
        val c = AppContainer(Context(Paths.data, Paths.cache))
        val t = c.transcription
        runBlocking { assertTrue(t.checkAvailable(), t.unavailableReason ?: "") }

        // Load the model through the app (from the fixed Hugging Face commit, checked by SHA-256).
        if (t.state.value.models.none { it.id == "base" && it.installed }) t.download("base")
        val deadline = System.currentTimeMillis() + 15 * 60_000
        while (t.state.value.models.none { it.id == "base" && it.installed }) {
            assertTrue(System.currentTimeMillis() < deadline, "Download dauert zu lange")
            Thread.sleep(1000)
        }

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
