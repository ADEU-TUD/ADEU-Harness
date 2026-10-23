package io.github.thepytales.llmhub.docs

import android.content.Context
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import io.github.thepytales.llmhub.AppLog
import io.github.thepytales.llmhub.desktop.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.jsoup.Jsoup
import org.jsoup.helper.W3CDom
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Renders HTML/CSS into an A4 PDF. On Windows with Microsoft Edge (installed on every Windows 10/11 PC,
 * no admin rights needed) or Chrome in headless mode – the same Chromium engine as Android's WebView, so
 * colors, shapes, fonts, flexbox and SVG look the same. Without a browser the built-in renderer is used.
 */
object HtmlPdf {
    /** A4 unless the page says otherwise; browsers print background colors and images. Scripts stay off. */
    private const val PRINT_CSS = "<meta http-equiv=\"Content-Security-Policy\" content=\"script-src 'none'\">" +
        "<style>@page{size:A4}html,body{-webkit-print-color-adjust:exact;print-color-adjust:exact}</style>"

    private const val BROWSER_TIMEOUT = 60L

    /** One document at a time (like the WebView on Android). */
    private val lock = Mutex()

    /** Which renderer made the last PDF: the browser's name or "eingebaut" (shown by the self-test). */
    @Volatile
    var lastEngine: String = ""
        private set

    fun withPrintColors(html: String): String {
        val head = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
        return if (head != null) html.substring(0, head.range.last + 1) + PRINT_CSS + html.substring(head.range.last + 1)
        else PRINT_CSS + html
    }

    /**
     * Writes the PDF and returns the number of pages. Fails with a normal exception when no renderer
     * delivers, so callers can fall back to the simple renderer.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun write(context: Context, file: File, html: String): Int = lock.withLock {
        withContext(Dispatchers.IO) {
            val page = withPrintColors(html)
            val viaBrowser = Browser.find()?.let { exe ->
                runCatching { printWithBrowser(exe, page, file) }
                    .onFailure { AppLog.e("PDF mit ${exe.name} fehlgeschlagen", it) }
                    .isSuccess
            } == true
            if (!viaBrowser) renderBuiltIn(page, file)
            lastEngine = if (viaBrowser) Browser.find()?.name.orEmpty() else "eingebaut"
            val pages = pageCount(file)
            if (pages <= 0) throw IllegalStateException("Die PDF-Datei ist leer.")
            pages
        }
    }

    private suspend fun printWithBrowser(exe: File, html: String, out: File) {
        val work = File(Paths.cache, "print-${System.nanoTime()}").apply { mkdirs() }
        try {
            val source = File(work, "page.html").apply { writeText(html) }
            val target = File(work, "out.pdf")
            val cmd = listOfNotNull(
                exe.absolutePath,
                // Only for Linux test machines that run as root; Windows keeps the browser sandbox.
                "--no-sandbox".takeIf { !Paths.windows && System.getProperty("user.name") == "root" },
                "--headless=new", "--disable-gpu", "--no-first-run", "--no-default-browser-check",
                "--disable-extensions", "--disable-sync", "--mute-audio", "--hide-scrollbars",
                // Own profile, so an open browser window of the user is not touched.
                "--user-data-dir=${File(work, "profile").absolutePath}",
                "--no-pdf-header-footer", "--print-to-pdf-no-header",
                "--virtual-time-budget=8000", "--run-all-compositor-stages-before-draw",
                "--print-to-pdf=${target.absolutePath}",
                source.toURI().toString(),
            )
            val process = ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(File(work, "browser.log")).start()
            try {
                val finished = runInterruptible { process.waitFor(BROWSER_TIMEOUT, TimeUnit.SECONDS) }
                if (!finished) throw IllegalStateException("Der Browser hat das PDF nicht rechtzeitig erstellt.")
            } finally {
                if (process.isAlive) process.descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly()
            }
            if (!target.exists() || target.length() < 100 || pageCount(target) <= 0) {
                throw IllegalStateException("Der Browser hat kein PDF geschrieben.")
            }
            target.copyTo(out, overwrite = true)
        } finally {
            work.deleteRecursively()
        }
    }

    /** openhtmltopdf: CSS 2.1 with paged media (page numbers in @page), no flexbox. */
    fun renderBuiltIn(html: String, out: File) {
        val doc = W3CDom().fromJsoup(Jsoup.parse(html))
        out.outputStream().use { stream ->
            PdfRendererBuilder().apply {
                useFastMode()
                withW3cDocument(doc, "file:///")
                Fonts.register(this)
                toStream(stream)
            }.run()
        }
    }

    fun pageCount(file: File): Int = runCatching { Loader.loadPDF(file).use { it.numberOfPages } }.getOrDefault(0)
}

/** Finds Microsoft Edge or Google Chrome for printing PDFs. */
object Browser {
    private var cached: File? = null

    fun find(): File? {
        cached?.takeIf { it.exists() }?.let { return it }
        val env = System.getenv()
        val candidates = buildList {
            System.getProperty("adeu.browser")?.let { add(it) }
            env["ADEU_BROWSER"]?.let { add(it) }
            if (Paths.windows) {
                for (base in listOfNotNull(env["ProgramFiles(x86)"], env["ProgramFiles"], env["LOCALAPPDATA"])) {
                    add("$base\\Microsoft\\Edge\\Application\\msedge.exe")
                    add("$base\\Google\\Chrome\\Application\\chrome.exe")
                }
            } else {
                addAll(listOf("/usr/bin/microsoft-edge", "/usr/bin/google-chrome", "/usr/bin/chromium", "/usr/bin/chromium-browser"))
                File("/opt/pw-browsers").listFiles()?.sortedDescending()?.forEach { dir ->
                    add("${dir.path}/chrome-linux/chrome")
                    add("${dir.path}/chrome-linux64/chrome")
                }
            }
        }
        return candidates.map(::File).firstOrNull { it.isFile && it.canExecute() }.also { cached = it }
    }
}

/** Fonts of the PC for the built-in PDF renderer (it only knows a few base fonts otherwise). */
object Fonts {
    private data class Face(val file: String, val family: String, val weight: Int, val italic: Boolean)

    private val windowsFaces = listOf(
        Face("arial.ttf", "sans-serif", 400, false), Face("arialbd.ttf", "sans-serif", 700, false),
        Face("ariali.ttf", "sans-serif", 400, true), Face("arialbi.ttf", "sans-serif", 700, true),
        Face("georgia.ttf", "serif", 400, false), Face("georgiab.ttf", "serif", 700, false),
        Face("georgiai.ttf", "serif", 400, true), Face("georgiaz.ttf", "serif", 700, true),
        Face("consola.ttf", "monospace", 400, false), Face("consolab.ttf", "monospace", 700, false),
    )

    private val linuxFaces = listOf(
        Face("dejavu/DejaVuSans.ttf", "sans-serif", 400, false), Face("dejavu/DejaVuSans-Bold.ttf", "sans-serif", 700, false),
        Face("dejavu/DejaVuSerif.ttf", "serif", 400, false), Face("dejavu/DejaVuSerif-Bold.ttf", "serif", 700, false),
        Face("dejavu/DejaVuSansMono.ttf", "monospace", 400, false),
    )

    /** Families the app's CSS uses, mapped to the generic ones. */
    private val aliases = mapOf(
        "sans-serif" to listOf("sans-serif", "Roboto", "Arial", "Helvetica", "Helvetica Neue", "system-ui", "Segoe UI", "Inter"),
        "serif" to listOf("serif", "Georgia", "Noto Serif", "Times New Roman", "Times"),
        "monospace" to listOf("monospace", "Roboto Mono", "Consolas", "Courier New", "Courier"),
    )

    fun register(builder: PdfRendererBuilder) {
        val (dir, faces) = if (Paths.windows) {
            File(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts") to windowsFaces
        } else File("/usr/share/fonts/truetype") to linuxFaces
        for (face in faces) {
            val file = File(dir, face.file)
            if (!file.isFile) continue
            val style = if (face.italic) com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle.ITALIC
            else com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle.NORMAL
            for (family in aliases[face.family].orEmpty()) builder.useFont(file, family, face.weight, style, true)
        }
    }
}
