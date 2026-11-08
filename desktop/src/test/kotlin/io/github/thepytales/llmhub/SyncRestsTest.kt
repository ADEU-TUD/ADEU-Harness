package io.github.thepytales.llmhub

import com.sun.net.httpserver.HttpServer
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.KnowledgeStore
import io.github.thepytales.llmhub.data.PhoneSync
import io.github.thepytales.llmhub.data.SyncGuard
import io.github.thepytales.llmhub.data.VaultStore
import io.github.thepytales.llmhub.net.WebDavClient
import io.github.thepytales.llmhub.research.LiteratureStore
import io.github.thepytales.llmhub.research.RefType
import io.github.thepytales.llmhub.research.Reference
import io.github.thepytales.llmhub.research.WebMeta
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.file.Files
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Sync- und Android-Reste (AP-63): literature and knowledge collection on the phone (read again while the app runs), a
 * sync in the background, the vault on several devices (each with its own PIN box) and its conflict copies, and the page
 * reader of the phone's browser.
 */
class SyncRestsTest {
    private val base = Files.createTempDirectory("sync-reste").toFile()

    @AfterTest
    fun cleanUp() { base.deleteRecursively() }

    /** A small Nextcloud in memory: PROPFIND (depth 1), GET, PUT, MKCOL, DELETE with etags. */
    private class Cloud {
        val files = ConcurrentHashMap<String, ByteArray>()
        val folders: MutableSet<String> = ConcurrentHashMap.newKeySet<String>().apply { add("/") }
        val root = "/remote.php/dav/files/anna"
        private fun etag(b: ByteArray) = MessageDigest.getInstance("MD5").digest(b).joinToString("") { "%02x".format(it) }

        fun put(path: String, bytes: ByteArray) {
            var p = WebDavClient.parent(path)
            while (p != "/") { folders += p; p = WebDavClient.parent(p) }
            files[path] = bytes
        }

        val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { ex ->
                runCatching {
                    val path = URLDecoder.decode(ex.requestURI.rawPath.removePrefix(root).ifEmpty { "/" }, "UTF-8").let { if (it.length > 1) it.trimEnd('/') else it }
                    fun reply(code: Int, body: ByteArray = ByteArray(0)) {
                        ex.sendResponseHeaders(code, if (body.isEmpty()) -1 else body.size.toLong())
                        if (body.isNotEmpty()) ex.responseBody.use { it.write(body) }
                    }
                    when (ex.requestMethod) {
                        "PROPFIND" -> {
                            if (path !in folders && !files.containsKey(path)) return@runCatching reply(404)
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
                }.onFailure { ex.sendResponseHeaders(500, -1) }
                ex.close()
            }
            start()
        }
        val dav = WebDavClient(OkHttpClient(), "http://127.0.0.1:${server.address.port}", "anna", "pw")
    }

    /** A device key of its own (as Keystore or DPAPI): what one device seals, another cannot open. */
    private class Device {
        private val key = ByteArray(32).also(SecureRandom()::nextBytes)
        fun seal(plain: ByteArray): ByteArray? {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
            return c.iv + c.doFinal(plain)
        }
        fun open(data: ByteArray): ByteArray? = runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, data.copyOfRange(0, 12)))
            c.doFinal(data.copyOfRange(12, data.size))
        }.getOrNull()
    }

    private fun vault(root: File, d: Device) = VaultStore(root, seal = d::seal, unseal = d::open)

    /** Fertig, wenn: ein am PC angelegter Literatureintrag erscheint ohne Öffnen der App auf dem Handy. */
    @Test
    fun literatureFromThePcArrivesOnThePhoneInTheBackground() = runBlocking {
        val cloud = Cloud()
        try {
            // The PC: its data folder with a new literature entry and a document of the knowledge collection.
            val pc = File(base, "pc/.lora").apply { mkdirs() }
            val pcLit = LiteratureStore(pc)
            pcLit.addAll(listOf(Reference(type = RefType.ARTICLE, title = "Lernen mit KI im Hörsaal", year = "2026")))
            val pcKnow = KnowledgeStore(pc)
            pcKnow.add("Modulhandbuch", "Das Modul Statistik I hat 5 Leistungspunkte.\n\nPrüfung: Klausur 90 Minuten.", "")
            pc.walkTopDown().filter { it.isFile }.forEach { f -> cloud.put("/Lora/.lora/" + f.relativeTo(pc).path.replace(File.separatorChar, '/'), f.readBytes()) }

            // The phone: the app is closed – the background sync (WorkManager) runs it with the note „im Hintergrund“.
            val data = File(base, "handy").apply { mkdirs() }
            val lit = LiteratureStore(data)
            val know = KnowledgeStore(data)
            assertTrue(lit.current.isEmpty())
            val sync = PhoneSync({ cloud.dav }, data, File(data, "Lora").apply { mkdirs() }, SyncGuard.Me("Handy Pixel", "handy-1"))
            val r = sync.run("/Lora", open = false)
            assertTrue("d:literature.json" in r.down, r.down.toString())
            assertTrue(r.down.any { it.startsWith("d:knowledge/") }, r.down.toString())
            // Read again while the app runs (PhoneSyncService.apply): the entry and the document are there.
            lit.reload()
            know.reload()
            assertEquals("Lernen mit KI im Hörsaal", lit.current.single().title)
            val doc = know.docs.value.single()
            assertTrue(know.chunks(doc).any { "Leistungspunkte" in it.text })
            // The phone's note says it synced in the background, so a PC may open the folder.
            val note = String(cloud.files.entries.single { it.key.contains("/sync/geoeffnet-Handy") }.value)
            assertTrue(note.contains("\"background\": true") || note.contains("\"background\":true"), note)
            assertTrue(note.contains("closedAt"), note)

            // A note of the phone in the background does not keep a PC from opening the folder (AP-12).
            val pcData = File(base, "pc-sync").apply { mkdirs() }
            File(pcData, SyncGuard.FOLDER).mkdirs()
            File(File(pcData, SyncGuard.FOLDER), "geoeffnet-Handy.json").writeText(note)
            assertEquals(null, SyncGuard.check(pcData, SyncGuard.Me("Büro-PC", "pc1")))
        } finally {
            cloud.server.stop(0)
        }
    }

    @Test
    fun theVaultOpensOnEveryDeviceWithItsOwnPin() {
        val a = Device()
        val b = Device()
        val pcRoot = File(base, "pc").apply { mkdirs() }
        val va = vault(pcRoot, a)
        val recovery = va.setUp("1234")
        va.save(Conversation(id = "v1", title = "Interview 3", model = "m", messages = listOf(ChatMessage(role = "user", content = "vertraulich"))))
        va.lock()

        // Synced to the phone (a copy of the folder): its key opens no box yet.
        val phoneRoot = File(base, "handy").apply { mkdirs() }
        File(pcRoot, "vault").copyRecursively(File(phoneRoot, "vault"))
        val vb = vault(phoneRoot, b)
        assertEquals(VaultStore.State.LOCKED, vb.state.value)
        assertFalse(vb.deviceKeyWorks())
        assertTrue(vb.unlock("1234") is VaultStore.Unlock.Wrong, "die PIN des PCs allein öffnet den Tresor auf einem fremden Gerät nicht")
        // Once with the recovery key: a PIN box of its own for the phone.
        assertTrue(vb.resetPin(recovery, "5678"))
        assertEquals("Interview 3", vb.chats.value.single().title)
        vb.lock()
        assertTrue(vb.deviceKeyWorks())
        assertEquals(VaultStore.Unlock.Ok, vb.unlock("5678"))
        vb.lock()

        // Back on the PC (the file came through the sync): the PC's PIN still works there.
        File(phoneRoot, "vault/vault.json").copyTo(File(pcRoot, "vault/vault.json"), overwrite = true)
        val pcAgain = vault(pcRoot, a)
        assertTrue(pcAgain.deviceKeyWorks())
        assertEquals(VaultStore.Unlock.Ok, pcAgain.unlock("1234"))
        // A new PIN on the PC changes only the PC's box.
        assertTrue(pcAgain.changePin("1111"))
        pcAgain.lock()
        File(pcRoot, "vault/vault.json").copyTo(File(phoneRoot, "vault/vault.json"), overwrite = true)
        assertEquals(VaultStore.Unlock.Ok, vault(phoneRoot, b).unlock("5678"))
        assertEquals(VaultStore.Unlock.Ok, vault(pcRoot, a).unlock("1111"))
        assertTrue(vault(pcRoot, a).unlock("1234") is VaultStore.Unlock.Wrong)
    }

    @Test
    fun vaultChatsChangedOnTwoDevicesBecomeTwoVaultChats() = runBlocking {
        val a = Device()
        val root = File(base, "handy").apply { mkdirs() }
        val v = vault(root, a)
        v.setUp("1234")
        v.save(Conversation(id = "v1", title = "Interview 3", model = "m", messages = listOf(ChatMessage(role = "user", content = "Fassung Handy"))))
        v.lock()
        // The sync found the chat changed on both sides: the phone's version is kept as conflict copy (PhoneSync).
        val chats = File(root, "vault/chats")
        File(chats, "v1.bin").copyTo(File(chats, "v1 (Konflikt Handy).bin"))
        val kept = SyncGuard.keepBoth(root, SyncGuard.conflicts(root, emptyList()))
        assertEquals(listOf("vault/chats/v1 (Konflikt Handy).bin"), kept.setAside)
        assertTrue(File(root, SyncGuard.VAULT_CONFLICTS).listFiles().orEmpty().single().name.contains("Konflikt"))
        assertFalse(File(root, "sync/konflikte").exists(), "verschlüsselte Fassungen bleiben im Tresor")
        // Found again later: the vault's own conflict folder is no new conflict.
        assertTrue(SyncGuard.conflicts(root, emptyList()).isEmpty())
        // The phone sync leaves the vault's conflict folder on the device.
        File(root, "vault/konflikte/x (Konflikt Handy).bin").writeText("x")

        // Opened, the vault makes a second chat of it.
        assertEquals(VaultStore.Unlock.Ok, v.unlock("1234"))
        val titles = v.chats.value.map { it.title }.sorted()
        assertEquals(listOf("Interview 3", "Interview 3 (zweite Fassung)"), titles)
        assertEquals(1, File(root, SyncGuard.VAULT_CONFLICTS).listFiles().orEmpty().size, "nur die nicht lesbare Datei bleibt liegen")
    }

    @Test
    fun theVaultIsSyncedEncryptedButNotItsConflictFolder() = runBlocking {
        val cloud = Cloud()
        try {
            val data = File(base, "handy").apply { mkdirs() }
            val v = vault(data, Device())
            v.setUp("1234")
            v.save(Conversation(id = "v1", title = "Geheim", model = "m", messages = listOf(ChatMessage(role = "user", content = "Klarname Müller"))))
            File(data, SyncGuard.VAULT_CONFLICTS).mkdirs()
            File(data, SyncGuard.VAULT_CONFLICTS + "/v1 (Konflikt Handy).bin").writeText("alt")
            // The PC's Lora-Ordner in the Nextcloud.
            cloud.put("/Lora/.lora/folders.json", "[]".toByteArray())
            val sync = PhoneSync({ cloud.dav }, data, File(data, "Lora").apply { mkdirs() }, SyncGuard.Me("Handy", "h1"))
            val r = sync.run("/Lora")
            assertTrue("d:vault/vault.json" in r.up && "d:vault/chats/v1.bin" in r.up, r.up.toString())
            assertTrue(r.up.none { it.contains("konflikte") }, r.up.toString())
            // Nothing of the chat in the clear in the Nextcloud.
            assertTrue(cloud.files.values.none { String(it, Charsets.ISO_8859_1).contains("Klarname") })
        } finally {
            cloud.server.stop(0)
        }
    }

    @Test
    fun thePhoneBrowserReadsTitleTextAndCitationTags() {
        // As WebView.evaluateJavascript returns it: the JSON of the script as a JSON string.
        val inner = """{"title":"Lernen mit KI | Zeitschrift","text":"Abstract ... doi 10.1234/abc.567 ...","meta":{"citation_title":["Lernen mit KI"],""" +
            """"citation_author":["Müller, Anna","Schmidt, Ben"],"citation_publication_date":["2025/03/01"],"citation_journal_title":["Hochschullehre"],""" +
            """"citation_doi":["10.1234/abc.567"],"citation_pdf_url":["/pdf/567.pdf"]}}"""
        val result = kotlinx.serialization.json.JsonPrimitive(inner).toString()
        val page = assertNotNull(WebMeta.fromScript("https://journal.example.org/article/567", result))
        assertEquals("10.1234/abc.567", page.doi)
        assertEquals("https://journal.example.org/pdf/567.pdf", page.pdfUrl)
        val ref = page.reference(LocalDate.of(2026, 11, 8))
        assertEquals(RefType.ARTICLE, ref.type)
        assertEquals("Lernen mit KI", ref.title)
        assertEquals("2025", ref.year)
        assertEquals("Hochschullehre", ref.container)
        assertEquals(2, ref.authors.size)
        // Without tags: the page as a web source with the day it was read.
        val plain = WebMeta("https://www.tu-dresden.de/news", "Neuigkeiten", "Text").reference(LocalDate.of(2026, 11, 8))
        assertEquals(RefType.WEB, plain.type)
        assertEquals("tu-dresden.de", plain.container)
        assertEquals("2026-11-08", plain.accessed)
        assertTrue(WebMeta("https://x.org/a.pdf", "", "").isPdf)
    }
}
