package io.github.thepytales.llmhub

import com.sun.jna.platform.win32.COM.COMException
import io.github.thepytales.llmhub.desktop.ComErrors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** COM errors are recognized by their HRESULT – also when JNA put it only into the message (seen on a PC without Outlook). */
class ComErrorsTest {
    @Test
    fun hresultFromTheMessage() {
        assertEquals(0x800401F3.toInt(), ComErrors.hresult(COMException("Invalid class string(HRESULT: 800401f3)")))
        assertEquals(0x80040154.toInt(), ComErrors.hresult(COMException("Class not registered (HRESULT: 0x80040154)")))
        assertNull(ComErrors.hresult(COMException("etwas anderes")))
        assertNull(ComErrors.hresult(IllegalStateException("HRESULT: 80040154")), "nur COM-Fehler")
    }
}
