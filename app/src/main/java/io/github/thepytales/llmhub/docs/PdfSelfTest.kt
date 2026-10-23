package io.github.thepytales.llmhub.docs

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Renders a small designed PDF and writes the outcome to logcat (tag [TAG]). Used by the automated
 * smoke test, also with the app in the background, because that is how answers are generated.
 */
object PdfSelfTest {
    const val TAG = "PdfSelfTest"

    private const val HTML = """<!DOCTYPE html><html lang="de"><head><meta charset="utf-8">
<style>@page{size:A4;margin:15mm}body{font-family:sans-serif}.hero{background:#0f4c5c;color:#fff;padding:24px;border-radius:12px}</style>
</head><body><div class="hero"><h1>Selbsttest</h1><p>Farbige Fläche und Inline-SVG</p></div>
<svg width="200" height="80" viewBox="0 0 200 80"><circle cx="40" cy="40" r="30" fill="#e36414"/><rect x="90" y="15" width="100" height="50" rx="8" fill="#5f0f40"/></svg>
<p>Wenn dieser Text im PDF steht, funktioniert das gestaltete Rendern.</p></body></html>"""

    fun run(context: Context, delayMs: Long) {
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            delay(delayMs)
            val folder = File(app.cacheDir, "selftest")
            val started = System.currentTimeMillis()
            val result = runCatching { DocumentWriter.create(app, folder, "Selbsttest", HTML, DocFormat.PDF) }
            val ms = System.currentTimeMillis() - started
            result.onSuccess {
                val kind = if (it.designed) "gestaltet" else "EINFACH (Fallback)"
                Log.i(TAG, "PDF-SELFTEST OK: ${it.pages} Seite(n), $kind, $ms ms")
            }.onFailure {
                Log.e(TAG, "PDF-SELFTEST FEHLER nach $ms ms: ${it.message}", it)
            }
            folder.deleteRecursively()
        }
    }
}
