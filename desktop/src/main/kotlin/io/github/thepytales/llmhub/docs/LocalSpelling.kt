package io.github.thepytales.llmhub.docs

import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Rechtschreibprüfung on the PC (AP-16): the spell checker of Windows (ISpellChecker, Windows 8 and later) with its
 * German and English dictionaries – nothing leaves the PC. Android has its own version of this file.
 */
object LocalSpelling {
    data class Error(val start: Int, val length: Int, val word: String, val suggestions: List<String>)

    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")
    private val thread = Executors.newSingleThreadExecutor { r ->
        Thread({ Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_APARTMENTTHREADED); r.run() }, "Rechtschreibung").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    /** A COM interface by its vtable (ISpellChecker & Co. have no IDispatch). */
    private class Vt(p: Pointer) : Unknown(p) {
        fun call(index: Int, vararg args: Any?): Int = _invokeNativeInt(index, arrayOf(pointer, *args))
    }

    private val checkers = HashMap<String, Vt?>()

    private fun checker(lang: String): Vt? = checkers.getOrPut(lang) {
        runCatching {
            val factoryRef = PointerByReference()
            val hr = Ole32.INSTANCE.CoCreateInstance(
                Guid.CLSID("{7AB36653-1796-484B-BDFA-E74F1DB7C1DC}"), null, WTypes.CLSCTX_INPROC_SERVER,
                Guid.IID("{8E018A9D-2415-4677-BF08-794EA61F94BB}"), factoryRef,
            )
            if (hr.toInt() != 0) return@runCatching null
            val factory = Vt(factoryRef.value)
            val supported = IntByReference()
            factory.call(4, WString(lang), supported)
            if (supported.value == 0) { factory.Release(); return@runCatching null }
            val ref = PointerByReference()
            val ok = factory.call(5, WString(lang), ref)
            factory.Release()
            if (ok != 0 || ref.value == null) null else Vt(ref.value)
        }.getOrNull()
    }

    /** Whether the PC has a dictionary for [lang]. */
    suspend fun available(lang: String = "de-DE"): Boolean = windows && withContext(thread) { checker(lang) != null }

    private fun suggestions(c: Vt, word: String): List<String> {
        val ref = PointerByReference()
        if (c.call(5, WString(word), ref) != 0 || ref.value == null) return emptyList()
        val en = Vt(ref.value)
        val out = mutableListOf<String>()
        try {
            while (out.size < 6) {
                val s = PointerByReference()
                val fetched = IntByReference()
                if (en.call(3, 1, s, fetched) != 0 || fetched.value == 0 || s.value == null) break
                out += s.value.getWideString(0)
                Ole32.INSTANCE.CoTaskMemFree(s.value)
            }
        } finally {
            en.Release()
        }
        return out
    }

    /** The misspelled words of [text] with suggestions (empty if there is no dictionary). */
    suspend fun check(text: String, lang: String = "de-DE"): List<Error> {
        if (!windows || text.isBlank()) return emptyList()
        return withContext(thread) {
            val c = checker(lang) ?: return@withContext emptyList()
            val ref = PointerByReference()
            if (c.call(4, WString(text), ref) != 0 || ref.value == null) return@withContext emptyList()
            val errors = Vt(ref.value)
            val out = mutableListOf<Error>()
            try {
                while (out.size < 400) {
                    val e = PointerByReference()
                    if (errors.call(3, e) != 0 || e.value == null) break
                    val err = Vt(e.value)
                    try {
                        val start = IntByReference()
                        val length = IntByReference()
                        val action = IntByReference()
                        err.call(3, start)
                        err.call(4, length)
                        err.call(5, action)
                        val s = start.value
                        val n = length.value
                        if (s < 0 || n <= 0 || s + n > text.length) continue
                        val word = text.substring(s, s + n)
                        val list = when (action.value) {
                            2 -> {
                                val r = PointerByReference()
                                err.call(6, r)
                                listOfNotNull(r.value?.getWideString(0)).also { r.value?.let { p -> Ole32.INSTANCE.CoTaskMemFree(p) } }
                            }
                            1 -> suggestions(c, word)
                            3 -> listOf("")
                            else -> emptyList()
                        }
                        out += Error(s, n, word, list)
                    } finally {
                        err.Release()
                    }
                }
            } finally {
                errors.Release()
            }
            out
        }
    }

    /** Adds [word] to the user's dictionary (Windows keeps it for all programs). */
    suspend fun learn(word: String, lang: String = "de-DE") {
        if (!windows) return
        withContext(thread) { runCatching { checker(lang)?.call(6, WString(word)) } }
    }
}
