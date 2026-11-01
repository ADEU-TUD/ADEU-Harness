package io.github.thepytales.llmhub

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.thepytales.llmhub.data.PhoneSync
import io.github.thepytales.llmhub.data.SyncGuard
import io.github.thepytales.llmhub.net.WebDavClient
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Sync für Android (AP-28) gegen eine kleine Nextcloud im Speicher. */
class PhoneSyncTest {
    /** WebDAV with etags, PROPFIND depth 1, GET, PUT, MKCOL, DELETE. */
    private class Cloud {
        val files = ConcurrentHashMap<String, ByteArray>()
        val folders: MutableSet<String> = ConcurrentHashMap.newKeySet<String>().apply { add("/") }
        val root = "/remote.php/dav/files/anna"
        private fun etag(b: ByteArray) = MessageDigest.getInstance("MD5").digest(b).joinToString("") { "%02x".format(it) }

        fun put(path: String, text: String) {
            var p = WebDavClient.parent(path)
            while (p != "/") { folders += p; p = WebDavClient.parent(p) }
            files[path] = text.toByteArray()
        }

        fun handle(ex: HttpExchange) {
            val path = URLDecoder.decode(ex.requestURI.rawPath.removePrefix(root).ifEmpty { "/" }, "UTF-8").let { if (it.length > 1) it.trimEnd('/') else it }
            fun reply(code: Int, body: ByteArray = ByteArray(0)) {
                ex.sendResponseHeaders(code, if (body.isEmpty()) -1 else body.size.toLong())
                if (body.isNotEmpty()) ex.responseBody.use { it.write(body) }
            }
            when (ex.requestMethod) {
                "PROPFIND" -> {
                    if (path !in folders && !files.containsKey(path)) return reply(404)
                    fun entry(p: String, folder: Boolean) = "<d:response><d:href>$root${WebDavClient.encodePath(p)}${if (folder && p != "/") "/" else ""}</d:href><d:propstat><d:prop>" +
                        (if (folder) "<d:resourcetype><d:collection/></d:resourcetype>" else "<d:resourcetype/><d:getcontentlength>${files[p]!!.size}</d:getcontentlength><d:getetag>\"${etag(files[p]!!)}\"</d:getetag>") +
                        "</d:prop></d:propstat></d:response>"
                    val kids = if (ex.requestHeaders.getFirst("Depth") == "0" || files.containsKey(path)) emptyList() else
                        folders.filter { it != path && WebDavClient.parent(it) == path }.map { it to true } + files.keys.filter { WebDavClient.parent(it) == path }.map { it to false }
                    reply(207, ("<d:multistatus xmlns:d=\"DAV:\">" + entry(path, path in folders) + kids.joinToString("") { entry(it.first, it.second) } + "</d:multistatus>").toByteArray())
                }
                "GET" -> files[path]?.let { reply(200, it) } ?: reply(404)
                "PUT" -> if (WebDavClient.parent(path) !in folders) reply(409) else { files[path] = ex.requestBody.readBytes(); reply(201) }
                "MKCOL" -> if (path in folders) reply(405) else { folders += path; reply(201) }
                "DELETE" -> { files.remove(path); reply(204) }
                else -> reply(405)
            }
        }
    }

    private class Setup {
        val cloud = Cloud()
        val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { ex -> runCatching { cloud.handle(ex) }.onFailure { it.printStackTrace(); ex.sendResponseHeaders(500, -1) }; ex.close() }
            start()
        }
        val base: File = Files.createTempDirectory("lora-handy").toFile()
        val data = File(base, "files").apply { mkdirs() }
        val visible = File(data, "Lora").apply { mkdirs() }
        var now = 1_800_000_000_000L
        val dav = WebDavClient(OkHttpClient(), "http://127.0.0.1:${server.address.port}", "anna", "pw")
        val sync = PhoneSync({ dav }, data, visible, SyncGuard.Me("Handy Pixel", "handy-1"), clock = { now })
        fun stop() = server.stop(0)
    }

    private fun chat(id: String, title: String) = """{"id":"$id","title":"$title","model":"m","messages":[]}"""

    @Test
    fun phoneFetchesUploadsAndKeepsBothVersions() = runBlocking {
        val s = Setup()
        try {
            s.cloud.put("/Lora/.lora/conversations/a.json", chat("a", "Vom PC"))
            s.cloud.put("/Lora/.lora/memory.json", "[]")
            s.cloud.put("/Lora/.lora/einstellungen.json", "{}")
            s.cloud.put("/Lora/Erstellt/Brief.md", "# Brief")
            s.cloud.put("/Lora/.lora/sync/geoeffnet-PC-pc1.json", """{"pc":"Büro-PC","id":"pc1","openedAt":1,"aliveAt":2,"closedAt":3}""")
            assertTrue(s.sync.looksLikeLoraFolder("/Lora"))

            val first = s.sync.run("/Lora")
            assertNull(first.waiting)
            assertEquals(setOf("d:conversations/a.json", "d:memory.json", "v:Erstellt/Brief.md"), first.down.toSet())
            assertTrue(File(s.data, "conversations/a.json").readText().contains("Vom PC"))
            assertTrue(File(s.visible, "Erstellt/Brief.md").isFile)
            assertFalse(File(s.data, "einstellungen.json").exists(), "Einstellungen bleiben je Gerät")
            // The phone's note is in the folder, like a PC's.
            val note = s.cloud.files.keys.single { it.startsWith("/Lora/.lora/sync/geoeffnet-Handy") }
            assertTrue(String(s.cloud.files[note]!!).contains("\"pc\": \"Handy Pixel\"") || String(s.cloud.files[note]!!).contains("\"pc\":\"Handy Pixel\""))

            // Nothing changed: nothing moves.
            s.now += 60_000
            val idle = s.sync.run("/Lora")
            assertTrue(idle.down.isEmpty() && idle.up.isEmpty())

            // A new chat and a file on the phone go up.
            File(s.data, "conversations/b.json").writeText(chat("b", "Vom Handy"))
            File(s.visible, "Hochgeladen").mkdirs()
            File(s.visible, "Hochgeladen/Foto.txt").writeText("Tafelbild")
            s.now += 60_000
            val up = s.sync.run("/Lora")
            assertEquals(setOf("d:conversations/b.json", "v:Hochgeladen/Foto.txt"), up.up.toSet())
            assertEquals("Tafelbild", String(s.cloud.files["/Lora/Hochgeladen/Foto.txt"]!!))
            assertTrue(String(s.cloud.files[note]!!).contains("conversations/b.json"), "die Notiz nennt, was das Handy geändert hat")

            // Changed on the PC only: comes down. Changed on both: both versions stay.
            s.cloud.put("/Lora/.lora/memory.json", "[{\"id\":\"1\"}]")
            s.cloud.put("/Lora/.lora/conversations/a.json", chat("a", "Am PC weiter"))
            File(s.data, "conversations/a.json").apply { writeText(chat("a", "Am Handy weiter")); setLastModified(System.currentTimeMillis() + 5000) }
            s.now += 60_000
            val both = s.sync.run("/Lora")
            assertTrue("d:memory.json" in both.down)
            assertEquals(listOf("d:conversations/a.json"), both.conflicts)
            assertTrue(File(s.data, "conversations/a.json").readText().contains("Am PC weiter"))
            val second = File(s.data, "conversations").listFiles()!!.map { it.readText() }.firstOrNull { "Am Handy weiter" in it && "zweite Fassung" in it }
            assertNotNull(second, "die Fassung vom Handy wird ein eigener Chat")

            // Deleted on the PC: deleted on the phone.
            s.cloud.files.remove("/Lora/.lora/conversations/b.json")
            s.now += 60_000
            val gone = s.sync.run("/Lora")
            assertEquals(1, gone.deletedHere)
            assertFalse(File(s.data, "conversations/b.json").exists())
        } finally {
            s.stop()
        }
    }

    @Test
    fun waitsWhileThePcHasTheFolderOpen() = runBlocking {
        val s = Setup()
        try {
            s.cloud.put("/Lora/.lora/conversations/a.json", chat("a", "x"))
            s.cloud.put("/Lora/.lora/sync/geoeffnet-PC-pc1.json", """{"pc":"Büro-PC","id":"pc1","openedAt":${s.now - 1000},"aliveAt":${s.now - 500}}""")
            val r = s.sync.run("/Lora")
            assertTrue(r.waiting!!.contains("Büro-PC"))
            assertFalse(File(s.data, "conversations/a.json").exists())
            // A PC whose changes have not arrived in the Nextcloud yet: wait as well.
            s.cloud.put("/Lora/.lora/sync/geoeffnet-PC-pc1.json", """{"pc":"Büro-PC","id":"pc1","openedAt":1,"aliveAt":${s.now - 100},"closedAt":${s.now - 50},"changed":{"conversations/neu.json":120}}""")
            assertTrue(s.sync.run("/Lora").waiting!!.contains("unterwegs"))
        } finally {
            s.stop()
        }
    }

    @Test
    fun manyDeletionsStopTheSync() = runBlocking {
        val s = Setup()
        try {
            (1..30).forEach { s.cloud.put("/Lora/.lora/conversations/c$it.json", chat("c$it", "Chat $it")) }
            assertEquals(30, s.sync.run("/Lora").down.size)
            (1..30).forEach { s.cloud.files.remove("/Lora/.lora/conversations/c$it.json") }
            s.now += 60_000
            val r = s.sync.run("/Lora")
            assertTrue(r.waiting!!.contains("ungewöhnlich viel"))
            assertEquals(30, File(s.data, "conversations").listFiles()!!.size, "nichts gelöscht")
        } finally {
            s.stop()
        }
    }
}
