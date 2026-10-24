package io.github.thepytales.llmhub

import androidx.compose.ui.awt.ComposeWindow
import io.github.thepytales.llmhub.docs.Browser
import io.github.thepytales.llmhub.docs.DocFormat
import io.github.thepytales.llmhub.docs.DocStyle
import io.github.thepytales.llmhub.docs.DocTheme
import io.github.thepytales.llmhub.docs.DocumentWriter
import io.github.thepytales.llmhub.docs.HtmlPdf
import io.github.thepytales.llmhub.files.AttachmentReader
import io.github.thepytales.llmhub.ui.Navigation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import javax.imageio.ImageIO
import kotlin.system.exitProcess

/**
 * Automated start test for CI (like the emulator test of the APK): opens every screen, takes screenshots,
 * creates documents and reads them back. Writes selftest.txt and exits with 1 if anything failed.
 */
object SelfTest {
    private val routes = listOf(
        "models", "images", "compare", "templates", "tasks", "task/new", "usage", "environments", "environment/new",
        "settings", "settings/api", "settings/github", "settings/chat", "settings/appearance", "settings/voice",
        "settings/documents", "settings/windows", "settings/memory", "settings/knowledge", "settings/backup",
        // A new chat that opens the form of the letter template.
        "chat?persona=b_tud_letter",
    )

    private const val HTML = """<!DOCTYPE html><html lang="de"><head><meta charset="utf-8">
<style>@page{size:A4;margin:15mm}body{font-family:sans-serif}.hero{background:#0f4c5c;color:#fff;padding:24px;border-radius:12px;display:flex;justify-content:space-between}</style>
</head><body><div class="hero"><h1>Selbsttest</h1><p>Farbige Fläche und Inline-SVG</p></div>
<svg width="200" height="80" viewBox="0 0 200 80"><circle cx="40" cy="40" r="30" fill="#e36414"/><rect x="90" y="15" width="100" height="50" rx="8" fill="#5f0f40"/></svg>
<p>Wenn dieser Text im PDF steht, funktioniert das gestaltete Rendern. Umlaute: äöüß €</p></body></html>"""

    private const val MARKDOWN = "# Bericht\n\nEin **Test** mit Liste:\n\n- eins\n- zwei\n\n| A | B |\n|---|---|\n| 1 | 2 |\n\n> [!TIP]\n> Umlaute: äöüß"

    suspend fun run(window: ComposeWindow, dir: File) {
        val log = StringBuilder()
        var ok = true
        fun line(s: String) {
            log.appendLine(s)
            AppLog.i("SELFTEST $s")
        }
        fun check(name: String, condition: Boolean, detail: String = "") {
            if (!condition) ok = false
            line((if (condition) "OK     " else "FEHLER ") + name + if (detail.isNotEmpty()) " – $detail" else "")
        }
        val errors = mutableListOf<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            errors += e
            previous?.uncaughtException(t, e)
        }
        try {
            line("ADEU LLM ${BuildConfig.VERSION_NAME} · ${System.getProperty("os.name")} · Java ${System.getProperty("java.version")}")
            delay(3000)
            shot(window, dir, "00-chat")
            routes.forEachIndexed { i, route ->
                Navigation.openRoute.value = route
                delay(1200)
                shot(window, dir, "%02d-%s".format(i + 1, route.replace('/', '-').replace('?', '-').replace('=', '-')))
                Navigation.openRoute.value = "back"
                delay(400)
            }
            Navigation.openRoute.value = "chat"
            delay(800)
            check("Alle Bildschirme geöffnet", errors.isEmpty(), errors.firstOrNull()?.toString().orEmpty())

            val folder = File(dir, "docs")
            line("Browser für PDF: ${Browser.find()?.absolutePath ?: "keiner (eingebauter Renderer)"}")
            val pdf = runCatching { DocumentWriter.create(LlmHubApp.context, folder, "Selbsttest", HTML, DocFormat.PDF) }
            check("Gestaltetes PDF", pdf.isSuccess && (pdf.getOrNull()?.pages ?: 0) > 0,
                pdf.fold({ "${it.pages} Seite(n), ${if (it.designed) "gestaltet" else "EINFACH"}" }, { it.toString() }))
            if (Browser.find() != null) check("PDF über den Browser gerendert", HtmlPdf.lastEngine != "eingebaut", HtmlPdf.lastEngine)
            val md = runCatching { DocumentWriter.create(LlmHubApp.context, folder, "Bericht", MARKDOWN, DocFormat.PDF) }
            check("Markdown-PDF", md.isSuccess && (md.getOrNull()?.pages ?: 0) > 0, md.exceptionOrNull()?.toString().orEmpty())
            val tud = runCatching {
                DocumentWriter.create(LlmHubApp.context, folder, "TUD", MARKDOWN, DocFormat.PDF, theme = DocTheme(style = DocStyle.TUD))
            }
            check("PDF im TUD-Design", tud.isSuccess && (tud.getOrNull()?.pages ?: 0) > 0, tud.exceptionOrNull()?.toString().orEmpty())
            val fallback = runCatching {
                val f = File(folder, "fallback.pdf")
                io.github.thepytales.llmhub.docs.MarkdownPdf.write(f, "Fallback", MARKDOWN)
            }
            check("Eingebauter PDF-Renderer", (fallback.getOrNull() ?: 0) > 0, fallback.exceptionOrNull()?.toString().orEmpty())
            val letter = runCatching {
                c().documentTools.createDirect(
                    "letter",
                    mapOf("Absender" to "Erika Muster\nReferentin", "Empfänger" to "Frau Dr. Anna Müller, Hauptstr. 1, 01069 Dresden", "Betreff" to "Selbsttest", "Brieftext" to "Dies ist der Brieftext."),
                ).second
            }
            check("Brief direkt aus den Feldern", letter.getOrNull()?.file?.let { File(it).length() > 0 } == true, letter.exceptionOrNull()?.toString().orEmpty())
            val slides = runCatching {
                DocumentWriter.create(LlmHubApp.context, folder, "Folien", "# Titel\n## Untertitel\n---\n# Folie\n- Punkt\nNotizen: Text", DocFormat.PPTX)
            }
            check("PowerPoint im TUD-Design", slides.getOrNull()?.file?.let { it.length() > 10_000 } == true, slides.exceptionOrNull()?.toString().orEmpty())
            val script = io.github.thepytales.llmhub.agent.ScriptRunner.run("print(6 * 7)")
            check("JavaScript-Sandbox", script.output.trim() == "42", script.error ?: script.output)
            for (format in listOf(DocFormat.DOCX, DocFormat.HTML, DocFormat.MD, DocFormat.TXT)) {
                val r = runCatching { DocumentWriter.create(LlmHubApp.context, folder, "Bericht", MARKDOWN, format) }
                check("Dokument ${format.label}", r.getOrNull()?.file?.let { it.length() > 0 } == true, r.exceptionOrNull()?.toString().orEmpty())
            }
            pdf.getOrNull()?.file?.let { file ->
                val read = AttachmentReader.read(file)
                check("PDF als Anhang lesen", read.file?.text?.contains("Selbsttest") == true, read.error.orEmpty())
            }
            md.getOrNull()?.let { doc ->
                val docx = File(folder, "Bericht.docx")
                if (docx.exists()) {
                    val read = AttachmentReader.read(docx)
                    check("Word als Anhang lesen", read.file?.text?.contains("Test") == true, read.error.orEmpty())
                }
                check("Seitenzahl Markdown-PDF", (doc.pages ?: 0) >= 1)
            }
            check("Daten verschlüsselt gespeichert", runCatching {
                c().settings.update { it.copy(userName = "Test") }
                File(LlmHubApp.context.filesDir, "secure_settings.dat").let { it.exists() && !it.readText(Charsets.ISO_8859_1).contains("Test") }
            }.getOrDefault(false))
            // Information only: depends on the PC, not on the app.
            val https = withContext(Dispatchers.IO) {
                runCatching {
                    c().llm.http.newCall(okhttp3.Request.Builder().url("https://llm.scads.ai/v1/models").build()).execute().use { "HTTP ${it.code}" }
                }.getOrElse { it.toString() }
            }
            line("Info: Verbindung zu llm.scads.ai: $https")
            line("Info: ${c().speaker.deviceVoices().size} Windows-Stimme(n), Autostart ${if (io.github.thepytales.llmhub.desktop.Autostart.supported) "möglich" else "nicht möglich"}, " +
                "Infobereich ${if (androidx.compose.ui.window.isTraySupported) "ja" else "nein"}")
            check("Keine Abstürze", errors.isEmpty(), errors.joinToString { it.toString() })
        } catch (e: Throwable) {
            ok = false
            line("FEHLER Selbsttest abgebrochen: ${e.stackTraceToString()}")
        }
        line(if (ok) "ERGEBNIS: OK" else "ERGEBNIS: FEHLER")
        withContext(Dispatchers.IO) { File(dir, "selftest.txt").writeText(log.toString()) }
        exitProcess(if (ok) 0 else 1)
    }

    private fun c() = LlmHubApp.container

    private suspend fun shot(window: ComposeWindow, dir: File, name: String) = withContext(Dispatchers.IO) {
        runCatching {
            val b = window.bounds
            val img = Robot().createScreenCapture(Rectangle(b.x, b.y, b.width, b.height))
            ImageIO.write(img, "png", File(dir, "$name.png"))
        }.onFailure { AppLog.e("Screenshot $name", it) }
    }
}
