package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.agent.ModelRouter
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.ui.SpeechEndDetector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Automatic model choice and the end of speech in the voice mode. */
class VoiceAndAutoModelTest {
    @Test
    fun routerPicksTheFittingModel() {
        fun pick(text: String, images: Int = 0, files: List<FileAttachment> = emptyList(), github: Boolean = false) =
            ModelRouter.suggest(text, images, files, github).model
        assertEquals(ModelCatalog.DEFAULT_CHAT, pick("Schreib eine kurze Mail an das Prüfungsamt."))
        // Everyday words that only contain a programming word stay with the standard model.
        assertEquals(ModelCatalog.DEFAULT_CHAT, pick("Suche aktuelle Förderprogramme für Forschung und Lehre mit Frist und Kurzbeschreibung"))
        assertEquals(ModelCatalog.DEFAULT_CHAT, pick("Fasse mein Vorlesungsskript zusammen"))
        assertEquals(ModelCatalog.DEFAULT_CHAT, pick("Welches Notebook für die Lehre?"))
        assertEquals(ModelCatalog.DEFAULT_CHAT, pick("Was steht auf dem Foto?", images = 1))
        assertEquals(ModelCatalog.CODE_CHAT, pick("Warum wirft mein Python-Skript eine Exception?"))
        assertEquals(ModelCatalog.CODE_CHAT, pick("Ändere die README", github = true))
        assertEquals(ModelCatalog.CODE_CHAT, pick("```\nval x = 1\n```"))
        assertEquals(ModelCatalog.HARD_CHAT, pick("Prüfe die Argumentation meines Gutachtens kritisch und gründlich."))
        val book = FileAttachment("Band.pdf", "application/pdf", "x".repeat(400_000))
        assertEquals(ModelCatalog.FAST_CHAT, pick("Fasse zusammen", files = listOf(book)))
        // Pictures win: only the standard model sees them.
        assertEquals(ModelCatalog.DEFAULT_CHAT, pick("Debugge den Code auf dem Screenshot", images = 1))
        // The user's own standard model counts as the standard.
        assertEquals("mein/Modell", ModelRouter.suggest("Hallo", 0, emptyList(), false, standard = "mein/Modell").model)
        assertTrue(ModelRouter.suggest("Hallo", 0, emptyList(), false).reason.isNotBlank())
    }

    @Test
    fun speechEndsAfterAPauseNotBeforeSpeaking() {
        val d = SpeechEndDetector(silenceMs = 1000)
        // Quiet room: waiting, no end.
        repeat(30) { assertFalse(d.feed(0.01f, 100)) }
        assertFalse(d.heard)
        // Speaking with short breaks between words.
        repeat(8) { assertFalse(d.feed(0.3f, 100)) }
        repeat(4) { assertFalse(d.feed(0.01f, 100)) }
        repeat(5) { assertFalse(d.feed(0.25f, 100)) }
        assertTrue(d.heard)
        // A real pause ends the turn.
        var ended = false
        repeat(12) { if (d.feed(0.01f, 100)) ended = true }
        assertTrue(ended)
    }

    @Test
    fun noisyRoomRaisesTheThreshold() {
        val d = SpeechEndDetector()
        val quiet = d.threshold
        repeat(100) { d.feed(0.04f, 100) }
        assertTrue(d.threshold > quiet, "Rauschen hebt die Schwelle")
        assertFalse(d.heard, "Rauschen ist keine Sprache")
        // A turn never runs forever.
        val long = SpeechEndDetector(maxMs = 2000)
        var ended = false
        repeat(25) { if (long.feed(0.01f, 100)) ended = true }
        assertTrue(ended)
    }
}
