package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.audio.LocalVoice
import io.github.thepytales.llmhub.audio.LocalVoiceInfo
import java.io.ByteArrayInputStream
import java.io.File
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Sprachausgabe lokal (AP-111). Without the voice: which voice reads, the WAV it gives, the download list. With
 * STIMME (folder with the voice files) and STIMME_NATIVE or sherpa_onnx.native.path (sherpa-onnx of the app): the first sentence of an
 * answer is ready within half a second → desktop/build/stimme/bericht.txt.
 */
class LocalVoiceTest {
    @Test
    fun theRightVoiceReads() {
        // Chosen and loaded → this voice; chosen but not loaded → the system's.
        assertEquals("LOCAL", LocalVoiceInfo.route("LOCAL", deviceOnly = false, localInstalled = true))
        assertEquals("DEVICE", LocalVoiceInfo.route("LOCAL", deviceOnly = false, localInstalled = false))
        // Text that must stay here (project „Nur auf diesem Gerät“, „Nur lokal“): the natural voice if loaded, never the server.
        assertEquals("LOCAL", LocalVoiceInfo.route("SERVER", deviceOnly = true, localInstalled = true))
        assertEquals("DEVICE", LocalVoiceInfo.route("SERVER", deviceOnly = true, localInstalled = false))
        assertEquals("SERVER", LocalVoiceInfo.route("SERVER", deviceOnly = false, localInstalled = true))
        assertEquals("DEVICE", LocalVoiceInfo.route("DEVICE", deviceOnly = false, localInstalled = true))
    }

    @Test
    fun wavIsReadableBySound() {
        val samples = FloatArray(2205) { i -> kotlin.math.sin(i / 10.0).toFloat() * 0.5f }
        val bytes = LocalVoiceInfo.wav(samples, 22050)
        assertEquals("RIFF", String(bytes, 0, 4))
        AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes)).use { a ->
            assertEquals(22050f, a.format.sampleRate)
            assertEquals(16, a.format.sampleSizeInBits)
            assertEquals(2205L, a.frameLength)
        }
    }

    @Test
    fun onlyTheGermanFilesAtAFixedCommit() {
        assertEquals(40, LocalVoiceInfo.COMMIT.length)
        assertTrue(LocalVoiceInfo.FILES.all { it.sha256.length == 64 && it.size > 0 })
        assertTrue(LocalVoiceInfo.MEGABYTES in 60..70, "${LocalVoiceInfo.MEGABYTES} MB")
        assertTrue(LocalVoiceInfo.url(LocalVoiceInfo.FILES.first()).startsWith("https://huggingface.co/csukuangfj/vits-piper-de_DE-thorsten-medium/resolve/da8bed7"))
        assertFalse(LocalVoice(java.nio.file.Files.createTempDirectory("adeu-stimme").toFile()).installed())
    }

    @Test
    fun firstSentenceWithinHalfASecond() {
        val dir = System.getenv("STIMME")?.let(::File)?.takeIf { File(it, LocalVoiceInfo.MODEL).isFile } ?: return
        System.getenv("STIMME_NATIVE")?.let { System.setProperty("sherpa_onnx.native.path", it) }
        if (System.getProperty("sherpa_onnx.native.path").isNullOrBlank()) return
        val voice = LocalVoice(dir)
        assertTrue(voice.installed())
        val t0 = System.nanoTime()
        voice.synth("Gern.")
        val load = (System.nanoTime() - t0) / 1_000_000
        val lines = mutableListOf("Laden und erstes Wort: $load ms")
        // The Sprachmodus speaks the first finished sentence; it must be ready within half a second.
        val firsts = listOf(
            "Die Abrechnung muss innerhalb von sechs Monaten eingereicht werden.",
            "Ja, das geht.",
            "Der Antrag für die Hilfskraft muss vier Wochen vor Beginn vorliegen.",
            "Am Wochenende ist die Bibliothek geschlossen.",
        )
        val times = firsts.map { s ->
            val t = System.nanoTime()
            val wav = voice.synth(s)
            val ms = (System.nanoTime() - t) / 1_000_000
            val seconds = (wav.size - 44) / 2 / 22050.0
            lines += "%4d ms für %.2f s Ton: %s".format(java.util.Locale.ROOT, ms, seconds, s)
            assertTrue(seconds > 0.5, s)
            ms
        }
        File("build/stimme").mkdirs()
        File("build/stimme/bericht.txt").writeText(lines.joinToString("\n") + "\n")
        println(lines.joinToString("\n"))
        assertTrue(times.max() < 500, "erster Satz zu langsam: $times ms")
    }
}
