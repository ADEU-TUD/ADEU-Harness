package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.data.FilePrefs
import io.github.thepytales.llmhub.data.SECRET_KEYS
import io.github.thepytales.llmhub.data.SplitPrefs
import io.github.thepytales.llmhub.data.SyncGuard
import io.github.thepytales.llmhub.data.migrate
import io.github.thepytales.llmhub.desktop.Secrets
import io.github.thepytales.llmhub.desktop.WebPage
import io.github.thepytales.llmhub.research.RefType
import io.github.thepytales.llmhub.ui.PanelUriHandler
import io.github.thepytales.llmhub.ui.opensInPanel
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Einfacher Sync (AP-12) and the browser beside the chat (AP-14). */
class SyncAndBrowserTest {
    init {
        if (System.getProperty("adeu.data").isNullOrBlank()) {
            System.setProperty("adeu.data", Files.createTempDirectory("adeu-sync-test").toFile().absolutePath)
        }
    }

    private fun folder() = Files.createTempDirectory("adeu-sync").toFile()
    private val office = SyncGuard.Me("BUERO-PC", "a1b2c3d4")
    private val laptop = SyncGuard.Me("LAPTOP", "e5f6a7b8")
    private val t0 = 1_800_000_000_000L

    private fun File.write(rel: String, text: String, at: Long) = File(this, rel).apply { parentFile.mkdirs(); writeText(text); setLastModified(at) }

    @Test
    fun secondPcWaitsWhileTheFirstIsOpen() {
        val data = folder()
        SyncGuard.touch(data, office, openedAt = t0, now = t0 + 60_000)
        val f = SyncGuard.check(data, laptop, now = t0 + 120_000)
        assertIs<SyncGuard.Finding.OpenElsewhere>(f, "Büro-PC ist offen")
        assertEquals("BUERO-PC", f.session.pc)
        // Its own note does not block a PC.
        assertNull(SyncGuard.check(data, office, now = t0 + 120_000))
        // A PC that stopped saying it is alive (crash, sleep) does not block for ever.
        assertNull(SyncGuard.check(data, laptop, now = t0 + 60_000 + SyncGuard.STALE_MS + 1))
        // In the background (notification area) the note says so.
        SyncGuard.touch(data, office, openedAt = t0, background = true, now = t0 + 180_000)
        assertTrue((SyncGuard.check(data, laptop, now = t0 + 200_000) as SyncGuard.Finding.OpenElsewhere).session.background)
    }

    @Test
    fun secondPcWaitsUntilTheChangesArrived() {
        val data = folder()
        // The office PC wrote a chat and the settings in its session, then closed.
        data.write("conversations/c1.json", """{"id":"c1","title":"Antrag"}""", t0 + 10_000)
        data.write("einstellungen.json", "{}", t0 + 20_000)
        data.write("cache/tmp.bin", "x", t0 + 20_000)
        SyncGuard.touch(data, office, openedAt = t0, closed = true, now = t0 + 30_000)
        val note = SyncGuard.sessions(data).single()
        assertEquals(setOf("conversations/c1.json", "einstellungen.json"), note.changed.keys, "geänderte Dateien ohne Cache und Notizen")
        assertNull(SyncGuard.check(data, laptop, now = t0 + 40_000), "alles da")

        // On the laptop the cloud service has not brought the chat yet (here: an older, shorter version).
        val laptopData = folder()
        File(data, "sync").copyRecursively(File(laptopData, "sync"))
        laptopData.write("einstellungen.json", "{}", t0 + 20_000)
        val waiting = SyncGuard.check(laptopData, laptop, now = t0 + 40_000)
        assertIs<SyncGuard.Finding.StillSyncing>(waiting)
        assertEquals(listOf("conversations/c1.json"), waiting.missing)
        laptopData.write("conversations/c1.json", """{"id":"c1"}""", t0 + 10_000)
        assertIs<SyncGuard.Finding.StillSyncing>(SyncGuard.check(laptopData, laptop, now = t0 + 40_000), "Größe stimmt noch nicht")
        laptopData.write("conversations/c1.json", """{"id":"c1","title":"Antrag"}""", t0 + 10_000)
        assertNull(SyncGuard.check(laptopData, laptop, now = t0 + 40_000), "jetzt angekommen")

        // Changes older than the laptop's own last session do not count.
        val later = folder()
        File(data, "sync").copyRecursively(File(later, "sync"))
        SyncGuard.touch(later, laptop, openedAt = t0 + 35_000, closed = true, now = t0 + 50_000)
        assertNull(SyncGuard.check(later, laptop, now = t0 + 60_000))
    }

    @Test
    fun aPcThatOpensLaterIsNoticed() {
        val data = folder()
        SyncGuard.touch(data, office, openedAt = t0, now = t0)
        assertNull(SyncGuard.newcomer(data, office, openedAt = t0, now = t0 + 1000))
        SyncGuard.touch(data, laptop, openedAt = t0 + 5000, now = t0 + 5000)
        assertEquals("LAPTOP", SyncGuard.newcomer(data, office, openedAt = t0, now = t0 + 6000)?.pc)
        assertNull(SyncGuard.newcomer(data, laptop, openedAt = t0 + 5000, now = t0 + 6000), "der Spätere sieht keinen Neuen")
    }

    @Test
    fun conflictCopiesAreFoundAndBothVersionsKept() {
        val data = folder()
        data.write("conversations/abc-123.json", """{"id":"abc-123","title":"Antrag DFG","model":"m"}""", t0)
        data.write("conversations/abc-123-LAPTOP.json", """{"id":"abc-123","title":"Antrag DFG","model":"m","messages":[]}""", t0)
        data.write("einstellungen.json", "{}", t0)
        data.write("einstellungen (1).json", "{}", t0)
        data.write("documents/Bericht (conflicted copy 2026-10-30 101500).md", "x", t0)
        data.write("documents/Bericht.md", "y", t0)
        data.write("literature/refs.sync-conflict-20261030-101500-ABCDEFG.json", "[]", t0)
        // Not conflicts: a name with (1) whose original is missing, a hyphen in a normal id.
        data.write("documents/Notizen (1).md", "z", t0)
        data.write("attachments/Foto.jpg", "a", t0)
        data.write("attachments/Foto (1).jpg", "b", t0)
        data.write("conversations/def-456.json", """{"id":"def-456","title":"X","model":"m"}""", t0)
        val found = SyncGuard.conflicts(data, listOf("BUERO-PC", "LAPTOP"))
        assertEquals(
            setOf(
                "conversations/abc-123-LAPTOP.json", "einstellungen (1).json",
                "documents/Bericht (conflicted copy 2026-10-30 101500).md", "literature/refs.sync-conflict-20261030-101500-ABCDEFG.json",
            ),
            found.map { it.path }.toSet(),
        )
        assertEquals("abc-123.json", found.first { it.path.startsWith("conversations") }.original?.name)
        assertEquals("Bericht.md", found.first { it.path.startsWith("documents") }.original?.name)

        val kept = SyncGuard.keepBoth(data, found, now = t0)
        assertEquals(listOf("Antrag DFG (zweite Fassung)"), kept.chats)
        assertEquals(4, kept.setAside.size)
        val chats = File(data, "conversations").listFiles { f -> f.isFile }!!.map { it.readText() }
        assertEquals(3, chats.size, "Original, zweite Fassung und der andere Chat")
        assertTrue(chats.any { "zweite Fassung" in it && "abc-123" !in it }, "neue ID")
        assertTrue(File(data, "sync/konflikte").walkTopDown().count { it.isFile } == 4, "nichts gelöscht: Kopien beiseitegelegt")
        assertTrue(SyncGuard.conflicts(data, listOf("LAPTOP")).isEmpty(), "danach keine Konflikte mehr")
    }

    @Test
    fun keysStayOnThePcAndSettingsSync() {
        val dir = folder()
        val legacy = File(dir, "synced/secure_settings.dat").apply { parentFile.mkdirs() }
        legacy.writeBytes(Secrets.encrypt("""{"apiKey":"sk-geheim","providers":"[]","themeMode":"DARK","userName":"Dominik"}""".toByteArray()))
        val secret = File(dir, "pc/schluessel.dat")
        val plain = File(dir, "synced/einstellungen.json")
        migrate(legacy, secret, plain)
        assertFalse(legacy.exists(), "alte Datei verlässt den synchronisierten Ordner")
        assertTrue("sk-geheim" !in plain.readText() && "DARK" in plain.readText(), "Einstellungen lesbar, ohne Schlüssel")
        val prefs = SplitPrefs(FilePrefs.encrypted(secret), FilePrefs.plain(plain), SECRET_KEYS)
        assertEquals("sk-geheim", prefs.getString("apiKey", null))
        assertEquals("Dominik", prefs.getString("userName", null))
        prefs.edit().putString("apiKey", "sk-neu").putString("userName", "D.").apply()
        assertTrue("sk-neu" !in plain.readText() && "D." in plain.readText())
        assertTrue("sk-neu" !in String(secret.readBytes()), "verschlüsselt")

        // A second PC finds the settings of the first and keeps its own (missing) key empty.
        val legacy2 = File(dir, "synced2/secure_settings.dat").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        migrate(legacy2, File(dir, "pc2/schluessel.dat"), File(dir, "synced2/einstellungen.json"))
        assertTrue(legacy2.exists(), "Datei eines anderen PCs bleibt für diesen liegen")
    }

    @Test
    fun theSettingsStoreKeepsTheKeyOutOfTheSyncedFile() {
        // The same way the Windows self-test checks it: the key is encrypted on the PC, the name readable in the data folder.
        val dir = folder()
        val store = io.github.thepytales.llmhub.data.SettingsStore(android.content.Context(File(dir, "files").apply { mkdirs() }, File(dir, "cache").apply { mkdirs() }))
        val before = store.current
        val marker = "sk-test-" + System.nanoTime()
        store.update { it.copy(apiKey = marker, userName = "Name ok") }
        val secret = File(io.github.thepytales.llmhub.desktop.Paths.machine, io.github.thepytales.llmhub.data.SECRET_FILE)
        val plain = File(io.github.thepytales.llmhub.desktop.Paths.data, io.github.thepytales.llmhub.data.PLAIN_FILE)
        try {
            assertTrue(secret.exists() && marker !in secret.readText(Charsets.ISO_8859_1), "Schlüssel verschlüsselt")
            assertTrue(plain.exists() && marker !in plain.readText() && "Name ok" in plain.readText(), "Name lesbar, Schlüssel nicht")
            assertEquals(marker, store.current.apiKey)
        } finally {
            store.update { before }
        }
    }

    @Test
    fun paperPagesBecomeReferences() {
        val html = """
            <html><head><title>Classroom management – Springer</title>
            <meta name="citation_title" content="Classroom management in German schools">
            <meta name="citation_author" content="Müller, Hans">
            <meta name="citation_author" content="Anna Schmidt">
            <meta name="citation_journal_title" content="Zeitschrift für Erziehungswissenschaft">
            <meta name="citation_publication_date" content="2023/05/12">
            <meta name="citation_volume" content="26"><meta name="citation_issue" content="3">
            <meta name="citation_firstpage" content="611"><meta name="citation_lastpage" content="634">
            <meta name="citation_doi" content="10.1007/s11618-023-01170-3">
            <meta name="citation_pdf_url" content="/content/pdf/10.1007/s11618-023-01170-3.pdf">
            </head><body><p>Abstract text</p></body></html>
        """.trimIndent()
        val page = WebPage("https://link.springer.com/article/10.1007/s11618-023-01170-3", "Classroom management – Springer", "Abstract text", html)
        assertEquals("10.1007/s11618-023-01170-3", page.doi)
        assertEquals("https://link.springer.com/content/pdf/10.1007/s11618-023-01170-3.pdf", page.pdfUrl)
        val ref = page.reference(LocalDate.of(2026, 10, 31))
        assertEquals(RefType.ARTICLE, ref.type)
        assertEquals(listOf("Müller", "Schmidt"), ref.authors.map { it.family })
        assertEquals("2023", ref.year)
        assertEquals("611–634", ref.pages)
        assertEquals("", ref.accessed, "Artikel brauchen kein Abrufdatum")

        // An ordinary page: a web source with the date it was read.
        val web = WebPage("https://www.tu-dresden.de/studium", "Studium – TU Dresden", "Text").reference(LocalDate.of(2026, 10, 31))
        assertEquals(RefType.WEB, web.type)
        assertEquals("tu-dresden.de", web.container)
        assertEquals("2026-10-31", web.accessed)
        val (body, cut) = WebPage("https://x.de/a.pdf", "A", "x".repeat(50)).asAttachment(max = 10)
        assertTrue(cut && body.startsWith("Webseite: A\nAdresse: https://x.de/a.pdf"))
        assertNotNull(WebPage("https://x.de/a.pdf", "A", "").pdfUrl, "ein PDF ist sein eigenes PDF")
    }

    @Test
    fun webLinksOpenBesideTheChat() {
        assertTrue(opensInPanel("https://www.dfg.de/foerderung", enabled = true))
        assertFalse(opensInPanel("https://www.dfg.de/foerderung", enabled = false), "Einstellung aus")
        assertFalse(opensInPanel("mailto:a@b.de", enabled = true))
        assertFalse(opensInPanel("https://github.com/ADEU-TUD/ADEU-Harness-Updater/releases/latest", enabled = true), "Downloads der App extern")
        val opened = mutableListOf<String>()
        val handler = PanelUriHandler(object : androidx.compose.ui.platform.UriHandler {
            override fun openUri(uri: String) { opened += uri }
        }) { false }
        handler.openUri("https://example.org")
        assertEquals(listOf("https://example.org"), opened, "ohne Panel wie bisher")
    }
}
