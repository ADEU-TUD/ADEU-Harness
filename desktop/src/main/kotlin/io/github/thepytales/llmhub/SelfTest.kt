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
        "settings/documents", "settings/windows", "settings/privacy", "settings/memory", "settings/knowledge", "settings/backup",
        "settings/transcription", "settings/updates", "local-models", "transcripts",
        // AP-07 to AP-11: Lora, tools, vault, PDF toolbox.
        "settings/lora", "settings/tools", "vault", "pdf",
        // AP-12, AP-17.
        "settings/sync", "games",
        // AP-13: without Outlook on the runner the screen must explain that instead of failing.
        "mail",
        // AP-18: the Dateiverwaltung.
        "files",
        // AP-24, AP-25: „Erstellen“ with the design templates (the route of the former Kreativbereich) and the video editor.
        "creative", "video",
        // AP-20 to AP-23: Stapel, Antrags-Werkstatt, Verbindungen (Nextcloud, MCP).
        "batches", "proposals", "settings/connections",
        // A new chat that opens the form of the letter template.
        "chat?persona=b_tud_letter",
        // AP-78: Inhalte zum Nachladen.
        "settings/downloads",
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
            line("Lora ${BuildConfig.VERSION_NAME} · ${System.getProperty("os.name")} · Java ${System.getProperty("java.version")}")
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

            // Transcription: a sample transcript (speakers, word times), its screen and every export format.
            val sample = runCatching { sampleTranscript() }
            check("Beispiel-Transkript angelegt", sample.isSuccess, sample.exceptionOrNull()?.toString().orEmpty())
            sample.getOrNull()?.let { t ->
                Navigation.openRoute.value = "transcript/${t.id}"
                delay(1500)
                shot(window, dir, "50-transcript")
                Navigation.openRoute.value = "back"
                delay(400)
                for (f in io.github.thepytales.llmhub.transcribe.TranscriptFormat.entries) {
                    val text = io.github.thepytales.llmhub.transcribe.TranscriptExport.content(t, f)
                    check("Export ${f.label}", text.isNotBlank() && "Wir beginnen" in text, text.take(80).replace("\n", " "))
                }
                val dp = runCatching {
                    DocumentWriter.create(LlmHubApp.context, File(dir, "docs"), t.title,
                        io.github.thepytales.llmhub.transcribe.TranscriptExport.dresingPehl(t), DocFormat.DOCX, fileName = "Dresing-Pehl")
                }
                check("Dresing/Pehl als Word", dp.getOrNull()?.file?.let { it.length() > 0 } == true, dp.exceptionOrNull()?.toString().orEmpty())
            }
            // The looks: start page of the transcription and settings in each businesslike style.
            val style = c().settings.current.appStyle
            io.github.thepytales.llmhub.ui.AppStyle.entries.filter { it.recommended }.forEach { st ->
                c().settings.update { it.copy(appStyle = st.name) }
                Navigation.openRoute.value = "transcripts"
                delay(900)
                shot(window, dir, "60-style-${st.name.lowercase()}-transcripts")
                Navigation.openRoute.value = "back"
                Navigation.openRoute.value = "settings"
                delay(900)
                shot(window, dir, "61-style-${st.name.lowercase()}-settings")
                Navigation.openRoute.value = "back"
                delay(300)
            }
            c().settings.update { it.copy(appStyle = style) }
            check("Transkription und Stile geöffnet", errors.isEmpty(), errors.firstOrNull()?.toString().orEmpty())

            val folder = File(dir, "docs")
            line("Browser für PDF: ${Browser.find()?.absolutePath ?: "keiner (eingebauter Renderer)"}")
            val pdf = runCatching { DocumentWriter.create(LlmHubApp.context, folder, "Selbsttest", HTML, DocFormat.PDF) }
            check("Gestaltetes PDF", pdf.isSuccess && (pdf.getOrNull()?.pages ?: 0) > 0,
                pdf.fold({ "${it.pages} Seite(n), ${if (it.designed) "gestaltet" else "EINFACH"}" }, { it.toString() }))
            // Windows renders with Edge; on macOS/Linux (AP-31) the built-in renderer is the intended fallback, so a
            // browser that does not start there (e.g. a runner without a desktop session) is only reported.
            if (Browser.find() != null) {
                if (io.github.thepytales.llmhub.desktop.Os.windows) check("PDF über den Browser gerendert", HtmlPdf.lastEngine != "eingebaut", HtmlPdf.lastEngine)
                else line("Info: PDF über den Browser: ${HtmlPdf.lastEngine}")
            }
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
            // Server-Terminal (AP-118): SSH works in the packaged runtime (jlink modules, crypto) – against a server on this PC.
            val ssh = runCatching { sshSelfTest(folder) }
            check("SSH (Server-Terminal)", ssh.getOrNull() == "lora-ok", ssh.exceptionOrNull()?.toString() ?: ssh.getOrNull().orEmpty())
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
            // AP-12: API keys and tokens are encrypted on this PC (schluessel.dat), the other settings lie readable in the
            // (possibly synced) data folder – the key must be in neither file in plain text.
            check("Schlüssel verschlüsselt gespeichert", runCatching {
                val before = c().settings.current.apiKey
                val marker = "sk-selbsttest-" + System.nanoTime()
                c().settings.update { it.copy(apiKey = marker) }
                val secret = File(io.github.thepytales.llmhub.desktop.Paths.machine, io.github.thepytales.llmhub.data.SECRET_FILE)
                val plain = File(io.github.thepytales.llmhub.desktop.Paths.data, io.github.thepytales.llmhub.data.PLAIN_FILE)
                val ok = secret.exists() && !secret.readText(Charsets.ISO_8859_1).contains(marker) &&
                    (!plain.exists() || !plain.readText().contains(marker)) && c().settings.current.apiKey == marker
                c().settings.update { it.copy(apiKey = before) }
                ok
            }.getOrDefault(false))
            // AP-15: a scan gets a text layer, a PDF a signature; Windows' text recognition reads a picture of text
            // (only information where the runner has no OCR language), Office → PDF only as information.
            val scan = withContext(Dispatchers.IO) {
                runCatching {
                    val img = java.awt.image.BufferedImage(1240, 400, java.awt.image.BufferedImage.TYPE_INT_RGB)
                    img.createGraphics().apply {
                        color = java.awt.Color.WHITE
                        fillRect(0, 0, img.width, img.height)
                        color = java.awt.Color.BLACK
                        font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 64)
                        drawString("Selbsttest Texterkennung", 60, 200)
                        dispose()
                    }
                    val png = File(folder, "ocr.png").also { javax.imageio.ImageIO.write(img, "png", it) }
                    val scanPdf = File(folder, "scan.pdf")
                    io.github.thepytales.llmhub.docs.PdfExtras.imagesToPdf(LlmHubApp.context, listOf("ocr.png" to png.readBytes()), scanPdf)
                    val layer = io.github.thepytales.llmhub.docs.OcrPage(0, listOf(io.github.thepytales.llmhub.docs.OcrLine(listOf(
                        io.github.thepytales.llmhub.docs.OcrWord("Selbsttest", io.github.thepytales.llmhub.docs.PdfBox(0.1f, 0.4f, 0.3f, 0.05f)),
                    ))))
                    val searchable = File(folder, "scan-ocr.pdf")
                    io.github.thepytales.llmhub.docs.PdfExtras.addTextLayer(LlmHubApp.context, scanPdf, listOf(layer), searchable)
                    val signed = File(folder, "unterschrieben.pdf")
                    val sig = io.github.thepytales.llmhub.docs.Signature.fromPad(listOf(listOf(0f to 0f, 40f to 20f, 80f to 0f)))!!
                    io.github.thepytales.llmhub.docs.PdfExtras.sign(LlmHubApp.context, searchable, 0, io.github.thepytales.llmhub.docs.PdfBox(0.6f, 0.8f, 0.3f, 0.1f), sig, "Dresden", signed)
                    png to io.github.thepytales.llmhub.docs.PdfExtras.pageTexts(LlmHubApp.context, signed).first()
                }
            }
            check("PDF: Textebene und Unterschrift", scan.getOrNull()?.second?.let { "Selbsttest" in it && "Dresden" in it } == true, scan.exceptionOrNull()?.toString().orEmpty())
            scan.getOrNull()?.first?.let { png ->
                val ocr = withContext(Dispatchers.IO) { runCatching { io.github.thepytales.llmhub.desktop.WindowsOcr.recognize(listOf(png)).firstOrNull() } }
                ocr.fold(
                    { page -> check("Texterkennung von Windows", page?.text?.contains("Selbsttest", ignoreCase = true) == true, (page?.text?.take(80) ?: "nichts erkannt") + " · ${io.github.thepytales.llmhub.desktop.WindowsOcr.language}") },
                    { line("Info: Texterkennung von Windows nicht verfügbar: ${it.message}") },
                )
            }
            File(folder, "Bericht.docx").takeIf { it.exists() }?.let { docx ->
                val office = withContext(Dispatchers.IO) { runCatching { io.github.thepytales.llmhub.desktop.OfficePdf.convert(docx, File(folder, "Bericht-office.pdf")) }.getOrDefault(false) }
                line("Info: Microsoft Office für Word → PDF: ${if (office) "ja" else "nein (eigener Leser der App)"}")
            }
            // AP-13: Outlook only as information (the runner has none) – it must answer quickly with a clear reason.
            val outlook = withContext(Dispatchers.IO) { runCatching { io.github.thepytales.llmhub.desktop.ComOutlook.connect() } }
            line("Info: Outlook: " + outlook.fold({ "verbunden ($it)" }, { it.message.orEmpty() }))

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

    /** Starts an SSH server on 127.0.0.1, trusts its key, logs in with a password and runs one command. */
    private fun sshSelfTest(folder: File): String {
        val hostKey = org.apache.sshd.common.config.keys.KeyUtils.generateKeyPair(org.apache.sshd.common.keyprovider.KeyPairProvider.ECDSA_SHA2_NISTP256, 256)
        val server = org.apache.sshd.server.SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = org.apache.sshd.common.keyprovider.KeyPairProvider.wrap(hostKey)
            setPasswordAuthenticator { u, p, _ -> u == "selbsttest" && p == "selbsttest-geheim" }
            setCommandFactory { _, command ->
                object : org.apache.sshd.server.command.Command {
                    private lateinit var out: java.io.OutputStream
                    private lateinit var exit: org.apache.sshd.server.ExitCallback
                    override fun setInputStream(`in`: java.io.InputStream) {}
                    override fun setOutputStream(out: java.io.OutputStream) { this.out = out }
                    override fun setErrorStream(err: java.io.OutputStream) {}
                    override fun setExitCallback(callback: org.apache.sshd.server.ExitCallback) { exit = callback }
                    override fun destroy(channel: org.apache.sshd.server.channel.ChannelSession) {}
                    override fun start(channel: org.apache.sshd.server.channel.ChannelSession, env: org.apache.sshd.server.Environment) {
                        Thread { out.write((if (command.endsWith("echo lora-ok")) "lora-ok" else "?").toByteArray()); out.flush(); exit.onExit(0) }.start()
                    }
                }
            }
            start()
        }
        try {
            val known = io.github.thepytales.llmhub.ssh.KnownHosts(File(folder, "known_hosts"))
            known.accept("127.0.0.1", server.port, hostKey.public)
            val profile = io.github.thepytales.llmhub.ssh.SshProfile(host = "127.0.0.1", port = server.port, user = "selbsttest", auth = io.github.thepytales.llmhub.ssh.SshAuth.PASSWORD)
            return io.github.thepytales.llmhub.ssh.SshConnection.connect(profile, "selbsttest-geheim", known).use { it.exec("echo lora-ok").output.trim() }
        } finally {
            server.stop(true)
        }
    }

    /** Writes a small interview transcript (two speakers, word times) like the speech engine does. */
    private suspend fun sampleTranscript(): io.github.thepytales.llmhub.transcribe.Transcript {
        val e = c().transcription.engine()
        val id = "selftest01"
        val dir = File(e.transcripts, id).apply { mkdirs() }
        File(dir, "transcript.json").writeText(
            """{"id":"$id","title":"Interview Selbsttest","created":"2026-10-25T10:15:00","duration":21.5,"language":"de",
            |"model_name":"Parakeet v3","device":"pc","diarization":true,"speakers":{"0":"Sprecher 1","1":"Sprecher 2"},
            |"segments":[{"id":0,"start":0.4,"end":3.2,"text":"Wir beginnen mit der ersten Frage.","speaker":0,
            |"words":[[0.4,0.7,"Wir"],[0.7,1.2,"beginnen"],[1.2,1.4,"mit"],[1.4,1.6,"der"],[1.6,2.1,"ersten"],[2.1,3.2,"Frage."]]},
            |{"id":1,"start":3.4,"end":6.0,"text":"Wie haben Sie den Unterricht erlebt?","speaker":0},
            |{"id":2,"start":8.5,"end":15.0,"text":"Sehr abwechslungsreich, vor allem die Gruppenarbeit hat mir gefallen, weil alle etwas beitragen konnten.","speaker":1},
            |{"id":3,"start":15.4,"end":21.0,"text":"Danke. Und was würden Sie beim nächsten Mal anders machen?","speaker":0}],
            |"audio":null,"folder":null,"marks":[{"t":8.0,"label":"Wichtig"}],"version":3}""".trimMargin()
        )
        e.indexInvalidate(id)
        c().transcription.refreshList()
        return c().transcription.load(id) ?: error("Transkript nicht lesbar")
    }

    private suspend fun shot(window: ComposeWindow, dir: File, name: String) = withContext(Dispatchers.IO) {
        runCatching {
            val b = window.bounds
            val img = Robot().createScreenCapture(Rectangle(b.x, b.y, b.width, b.height))
            ImageIO.write(img, "png", File(dir, "$name.png"))
        }.onFailure { AppLog.e("Screenshot $name", it) }
    }
}
