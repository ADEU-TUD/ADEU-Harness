package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.audio.SpeechStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Sprachmodus: reading starts with the first finished sentence while the answer is still streamed. */
class SpeechStreamTest {
    @Test
    fun firstSentenceComesAloneThenLargerParts() {
        val s = SpeechStream(max = 40)
        assertNull(s.next("Hallo, ich bin", false))
        assertEquals("Hallo, ich bin Lora.", s.next("Hallo, ich bin Lora. Heute ist", false))
        assertNull(s.next("Hallo, ich bin Lora. Heute ist", false))
        val full = "Hallo, ich bin Lora. Heute ist Montag. Es regnet. Morgen wird es sonnig und warm."
        assertEquals("Heute ist Montag. Es regnet.", s.next(full, false))
        assertEquals("Morgen wird es sonnig und warm.", s.next(full, true))
        assertNull(s.next(full, true))
    }

    @Test
    fun holdsBackOpenCodeBlock() {
        val s = SpeechStream()
        assertEquals("Beispiel:", s.next("Beispiel:\n```\nx = 1. y = 2.\n", false))
        assertNull(s.next("Beispiel:\n```\nx = 1. y = 2.\n", false))
        assertEquals(listOf("Eins.", "Zwei. Drei."), SpeechStream().all("Eins. Zwei. Drei."))
    }
}
