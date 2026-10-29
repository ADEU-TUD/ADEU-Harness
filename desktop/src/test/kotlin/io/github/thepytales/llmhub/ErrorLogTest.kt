package io.github.thepytales.llmhub

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The error log sent with „Problem melden“ keeps the kind of error but nothing personal. */
class ErrorLogTest {
    @Test
    fun anonymizeRemovesPersonalParts() {
        val line = "10:00 Lesen: IOException: C:\\Users\\Anna\\Desktop\\Antrag.pdf und „Brief an Herrn Maier“ " +
            "für anna@tu-dresden.de, https://example.org/x?key=1, Schlüssel sk-abcdefghijklmnopqrstuvwxyz123456"
        val out = ErrorLog.anonymize(line)
        listOf("Anna", "Maier", "tu-dresden", "example.org", "abcdefghijklmnop").forEach { assertFalse(it in out, out) }
        assertEquals("10:00 Lesen: IOException: <Pfad> und „…“ für <E-Mail>, <Adresse> Schlüssel <Schlüssel>", out)
    }
}
