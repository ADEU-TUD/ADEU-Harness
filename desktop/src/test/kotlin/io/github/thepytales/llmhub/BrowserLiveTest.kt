package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.desktop.WebEngine
import io.github.thepytales.llmhub.desktop.WebPage
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The browser beside the chat (AP-14) with the real Chromium: sets it up (downloads about 150–170 MB once), shows a
 * page in a window, reads its text and HTML, follows its title. Only with ADEU_BROWSER_TEST=1 and a display, so
 * normal test runs stay fast and offline – meant for Windows (in Linux containers CEF may hang while it initializes).
 */
class BrowserLiveTest {
    @Test
    fun showsAPageAndReadsIt() {
        if (System.getenv("ADEU_BROWSER_TEST") != "1") return
        if (System.getProperty("adeu.data").isNullOrBlank()) {
            System.setProperty("adeu.data", (System.getenv("ADEU_BROWSER_DIR")?.let(::File) ?: Files.createTempDirectory("adeu-browser").toFile()).absolutePath)
        }
        val server = com.sun.net.httpserver.HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/paper") { ex ->
                val html = """<html><head><title>Testseite Lora</title><meta name="citation_doi" content="10.1234/abc.5678"></head>
                    <body><h1>Hallo aus dem Browser</h1><p>Unterrichtsqualität und Klassenführung.</p></body></html>""".toByteArray()
                ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                ex.sendResponseHeaders(200, html.size.toLong())
                ex.responseBody.use { it.write(html) }
            }
            start()
        }
        val url = "http://127.0.0.1:${server.address.port}/paper"
        val titles = mutableListOf<String>()
        var loaded = false
        WebEngine.events = { e ->
            when (e) {
                is WebEngine.Event.Title -> titles += e.title
                is WebEngine.Event.Loading -> if (!e.loading) loaded = true
                else -> Unit
            }
        }
        runBlocking {
            val client = WebEngine.start()
            assertTrue(client != null, "Browser startet: ${WebEngine.state.value}")
            assertTrue(WebEngine.installed, "einmal eingerichtet")
            val browser = WebEngine.open(url)!!
            val frame = JFrame("Lora Browser-Test")
            SwingUtilities.invokeAndWait {
                frame.contentPane.add(browser.uiComponent, BorderLayout.CENTER)
                frame.setSize(800, 600)
                frame.isVisible = true
            }
            val until = System.currentTimeMillis() + 30_000
            while (!loaded && System.currentTimeMillis() < until) Thread.sleep(200)
            assertTrue(loaded, "Seite geladen")
            val text = WebEngine.text(browser)
            val html = WebEngine.source(browser)
            assertTrue(text?.contains("Hallo aus dem Browser") == true, "Text: $text")
            val page = WebPage(url, titles.lastOrNull().orEmpty(), text.orEmpty(), html.orEmpty())
            assertEquals("10.1234/abc.5678", page.doi)
            assertTrue("Testseite Lora" in titles, "Titel: $titles")
            browser.close(true)
            SwingUtilities.invokeAndWait { frame.dispose() }
        }
        WebEngine.dispose()
        server.stop(0)
    }
}
