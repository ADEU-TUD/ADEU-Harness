package io.github.thepytales.llmhub

import android.content.Context
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.thepytales.llmhub.agent.McpTools
import io.github.thepytales.llmhub.agent.Risk
import io.github.thepytales.llmhub.agent.execute
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.McpPolicy
import io.github.thepytales.llmhub.data.McpServer
import io.github.thepytales.llmhub.data.McpToolInfo
import io.github.thepytales.llmhub.data.PrivacyLevel
import io.github.thepytales.llmhub.net.JSON
import io.github.thepytales.llmhub.net.McpHttpTransport
import io.github.thepytales.llmhub.net.McpOAuth
import io.github.thepytales.llmhub.net.McpSession
import io.github.thepytales.llmhub.net.McpStdio
import io.github.thepytales.llmhub.net.McpTransport
import io.github.thepytales.llmhub.net.WebDavClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import java.io.File
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Nextcloud über WebDAV (AP-22) und der MCP-Client (AP-23). */
class ConnectionsTest {
    private val http = OkHttpClient()

    private fun server(handler: (HttpExchange) -> Unit): HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { ex -> runCatching { handler(ex) }.onFailure { it.printStackTrace(); ex.sendResponseHeaders(500, -1) }; ex.close() }
        start()
    }

    private fun HttpExchange.reply(code: Int, body: String = "", type: String = "application/json", headers: Map<String, String> = emptyMap()) {
        headers.forEach { (k, v) -> responseHeaders.add(k, v) }
        val bytes = body.toByteArray()
        responseHeaders.add("Content-Type", type)
        sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) responseBody.use { it.write(bytes) }
    }

    @Test
    fun webDavAddressesAndPaths() {
        assertEquals("https://cloudstore.zih.tu-dresden.de/remote.php/dav/files/anna%40tu-dresden.de", WebDavClient.davRoot("cloudstore.zih.tu-dresden.de/", "anna@tu-dresden.de"))
        assertEquals("https://nc.example.org/remote.php/dav/files/bob", WebDavClient.davRoot("https://nc.example.org/remote.php/dav/files/bob/", "bob"))
        assertEquals("https://nc.example.org/remote.php/dav/files/bob", WebDavClient.davRoot("https://nc.example.org/remote.php/webdav/", "bob"))
        assertEquals("https://nc.example.org", WebDavClient.serverBase("https://nc.example.org/index.php/apps/files/"))
        assertEquals("/Lehre/WiSe%2026/%C3%9Cbung.pdf", WebDavClient.encodePath("Lehre//WiSe 26/./Übung.pdf"))
        assertEquals("/a/c", WebDavClient.normalize("/a/b/../c"))
        assertEquals("/Lehre", WebDavClient.parent("/Lehre/x.pdf"))
        assertEquals("/", WebDavClient.parent("/x.pdf"))
    }

    /** A small Nextcloud: PROPFIND, GET, PUT and MKCOL on files kept in memory. */
    private class FakeCloud {
        val files = ConcurrentHashMap<String, ByteArray>()
        val folders = ConcurrentHashMap.newKeySet<String>().apply { add("/") }
        var auth: String? = null

        fun handle(ex: HttpExchange, reply: (Int, String) -> Unit) {
            auth = ex.requestHeaders.getFirst("Authorization")
            val root = "/remote.php/dav/files/anna%40tu-dresden.de"
            val raw = ex.requestURI.rawPath
            if (!raw.startsWith(root)) return reply(404, "")
            val path = URLDecoder.decode(raw.removePrefix(root).ifEmpty { "/" }, "UTF-8").let { if (it.length > 1) it.trimEnd('/') else it }
            when (ex.requestMethod) {
                "PROPFIND" -> {
                    if (path !in folders && !files.containsKey(path)) return reply(404, "")
                    val depth0 = ex.requestHeaders.getFirst("Depth") == "0"
                    fun entry(p: String, folder: Boolean) = "<d:response><d:href>$root${WebDavClient.encodePath(p)}${if (folder && p != "/") "/" else ""}</d:href><d:propstat><d:prop>" +
                        (if (folder) "<d:resourcetype><d:collection/></d:resourcetype>" else "<d:resourcetype/><d:getcontentlength>${files[p]!!.size}</d:getcontentlength><d:getcontenttype>application/pdf</d:getcontenttype>") +
                        "<d:getlastmodified>Sun, 01 Nov 2026 10:00:00 GMT</d:getlastmodified></d:prop></d:propstat></d:response>"
                    val children = if (depth0 || files.containsKey(path)) emptyList() else (folders.filter { it != path && WebDavClient.parent(it) == path }.map { it to true } +
                        files.keys.filter { WebDavClient.parent(it) == path }.map { it to false })
                    reply(207, "<?xml version=\"1.0\"?><d:multistatus xmlns:d=\"DAV:\">" + entry(path, path in folders) + children.joinToString("") { entry(it.first, it.second) } + "</d:multistatus>")
                }
                "GET" -> files[path]?.let { reply(200, String(it)) } ?: reply(404, "")
                "PUT" -> {
                    if (WebDavClient.parent(path) !in folders) return reply(409, "")
                    files[path] = ex.requestBody.readBytes(); reply(201, "")
                }
                "MKCOL" -> if (path in folders) reply(405, "") else { folders += path; reply(201, "") }
                else -> reply(405, "")
            }
        }
    }

    @Test
    fun nextcloudListsDownloadsAndSavesWithoutOverwriting() = runBlocking {
        val cloud = FakeCloud()
        cloud.folders += "/Lehre"
        cloud.files["/Lehre/Übung 1.pdf"] = "Hallo PDF".toByteArray()
        val srv = server { ex -> cloud.handle(ex) { code, body -> ex.reply(code, body, "application/xml") } }
        try {
            val base = Files.createTempDirectory("lora-nc").toFile()
            val c = AppContainer(Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
            c.settings.update { it.copy(nextcloudServer = "http://127.0.0.1:${srv.address.port}", nextcloudUser = "anna@tu-dresden.de", nextcloudPassword = "app-pw", nextcloudFolder = "/Lora/Ergebnisse") }
            val list = c.nextcloud.list("/")
            assertEquals(listOf("Lehre"), list.map { it.name })
            assertTrue(list.single().folder)
            val inLehre = c.nextcloud.list("/Lehre")
            assertEquals("/Lehre/Übung 1.pdf", inLehre.single().path)
            assertEquals(9L, inLehre.single().size)
            assertTrue(inLehre.single().modified > 0)
            assertTrue(cloud.auth!!.startsWith("Basic "))
            assertEquals("Hallo PDF", String(c.nextcloud.client().download("/Lehre/Übung 1.pdf")))
            // Saving creates the result folder; a second file of the same name gets „ (2)“.
            val f = File(base, "Protokoll.md").apply { writeText("# Protokoll") }
            assertEquals("/Lora/Ergebnisse/Protokoll.md", c.nextcloud.save(f))
            assertEquals("/Lora/Ergebnisse/Protokoll (2).md", c.nextcloud.save(f))
            assertTrue("/Lora" in cloud.folders && "/Lora/Ergebnisse" in cloud.folders)
            // The agent's tools: list and save text.
            val ctx = io.github.thepytales.llmhub.agent.RepoContext(mutableListOf())
            val listed = c.nextcloudTools.execute("nextcloud_list", """{"path":"/Lehre"}""", ctx).text
            assertTrue("Übung 1.pdf" in listed, listed)
            val saved = c.nextcloudTools.execute("nextcloud_save", """{"name":"Notiz.md","content":"Hallo"}""", ctx).text
            assertTrue("/Lora/Ergebnisse/Notiz.md" in saved, saved)
            assertEquals(Risk.EDIT, c.nextcloudTools.risk("nextcloud_save", JsonObject(emptyMap())))
            assertTrue(c.webLink().contains("/index.php/apps/files/?dir=%2FLehre"))
        } finally {
            srv.stop(0)
        }
    }

    private fun AppContainer.webLink() = nextcloud.webLink("/Lehre/x.pdf")

    @Test
    fun propfindAnswersWithOtherPrefixes() {
        val xml = """<?xml version="1.0"?><multistatus xmlns="DAV:"><response><href>https://h/remote.php/dav/files/u/Ordner%20A/</href>
            <propstat><prop><resourcetype><collection/></resourcetype></prop></propstat></response>
            <D:response xmlns:D="DAV:"><D:href>/remote.php/dav/files/u/Ordner%20A/b%2Bc.docx</D:href><D:propstat><D:prop><D:resourcetype/><D:getcontentlength>12</D:getcontentlength></D:prop></D:propstat></D:response></multistatus>"""
        val e = WebDavClient.parse(xml, "/remote.php/dav/files/u")
        assertEquals(listOf("/Ordner A", "/Ordner A/b+c.docx"), e.map { it.path })
        assertEquals(listOf(true, false), e.map { it.folder })
        assertEquals(12L, e[1].size)
    }

    /** A MCP server in memory: two pages of tools, a tool that answers, one that fails. */
    private class FakeMcp : McpTransport {
        val methods = mutableListOf<String>()
        override suspend fun send(message: JsonObject, expectResponse: Boolean): JsonObject? {
            val method = message["method"]!!.jsonPrimitive.content
            methods += method
            if (!expectResponse) return null
            val result = when (method) {
                "initialize" -> """{"protocolVersion":"2025-06-18","serverInfo":{"name":"Wiki"},"instructions":"Ein Wiki."}"""
                "tools/list" -> if ((message["params"] as JsonObject)["cursor"] == null)
                    """{"tools":[{"name":"search pages","description":"Sucht Seiten","inputSchema":{"type":"object","properties":{"query":{"type":"string","description":"Suchwort"},"limit":{"type":"integer"}},"required":["query"]},"annotations":{"readOnlyHint":true}}],"nextCursor":"2"}"""
                else """{"tools":[{"name":"delete_page","inputSchema":{"type":"object","properties":{"id":{"type":"string"}}}}]}"""
                "tools/call" -> {
                    val p = message["params"]!!.jsonObject
                    if (p["name"]!!.jsonPrimitive.content == "delete_page") """{"content":[{"type":"text","text":"nicht erlaubt"}],"isError":true}"""
                    else """{"content":[{"type":"text","text":"Treffer zu ${p["arguments"]!!.jsonObject["query"]!!.jsonPrimitive.content}"},{"type":"image","mimeType":"image/png","data":"AA=="}]}"""
                }
                else -> return buildJsonObject { put("jsonrpc", "2.0"); put("id", message["id"]!!); put("error", buildJsonObject { put("code", -32601); put("message", "unbekannt") }) }
            }
            return buildJsonObject { put("jsonrpc", "2.0"); put("id", message["id"]!!); put("result", JSON.parseToJsonElement(result)) }
        }
    }

    @Test
    fun mcpSessionListsAndCallsTools() = runBlocking {
        val t = FakeMcp()
        val s = McpSession(t)
        val tools = s.tools()
        assertEquals(listOf("initialize", "notifications/initialized", "tools/list", "tools/list"), t.methods)
        assertEquals(listOf("search pages", "delete_page"), tools.map { it.name })
        assertTrue(tools[0].readOnly)
        assertEquals("Wiki", s.serverName)
        val out = s.call("search pages", buildJsonObject { put("query", "Feedback") })
        assertTrue(out.startsWith("Treffer zu Feedback") && "[Bild (image/png)" in out, out)
        assertTrue(s.call("delete_page", JsonObject(emptyMap())).startsWith("FEHLER: nicht erlaubt"))
    }

    @Test
    fun mcpToolsKeepPoliciesAndPrivacy() {
        val server = McpServer(
            name = "Wiki der Professur", url = "https://wiki.example.org/mcp",
            tools = listOf(
                McpToolInfo("search pages", "Sucht", """{"type":"object","properties":{"query":{"type":"string","description":"Suchwort"}},"required":["query"]}""", readOnly = true),
                McpToolInfo("delete_page", "Löscht"),
                McpToolInfo("secret", "Gesperrt"),
            ),
            policies = mapOf("search pages" to McpPolicy.ALLOW.name, "secret" to McpPolicy.BLOCK.name),
        )
        val base = Files.createTempDirectory("lora-mcp").toFile()
        val c = AppContainer(Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
        val tools = McpTools(c.mcp, listOf(server))
        assertEquals(listOf("mcp_wiki_der_professur_search_pages", "mcp_wiki_der_professur_delete_page"), tools.defs.map { it.name }, "gesperrte Werkzeuge sieht das Modell nicht")
        val search = tools.defs.first()
        assertEquals("query", search.params.single().name)
        assertTrue(search.params.single().required)
        assertNull(tools.risk("mcp_wiki_der_professur_search_pages", JsonObject(emptyMap())), "Erlauben: ohne Rückfrage")
        assertEquals(Risk.PUBLIC, tools.risk("mcp_wiki_der_professur_delete_page", JsonObject(emptyMap())), "Fragen: über die Freigaben")
        assertTrue(tools.parallelSafe("mcp_wiki_der_professur_search_pages"))
        assertTrue(McpTools.toolName("Ä".repeat(30), "t".repeat(80)).length <= 64)
        // Privacy: only standard projects, unless the server runs at the TU; never in projects „Nur auf diesem Gerät“ or the vault.
        assertTrue(server.allowedAt(PrivacyLevel.OPEN))
        assertFalse(server.allowedAt(PrivacyLevel.TU))
        assertTrue(server.copy(internal = true).allowedAt(PrivacyLevel.TU))
        assertFalse(server.copy(internal = true).allowedAt(PrivacyLevel.LOCAL))
        c.mcpServers.upsert(server)
        assertEquals(1, c.mcp.usable(Conversation(title = "x", model = "m")).size)
        assertTrue(c.mcp.usable(Conversation(title = "x", model = "m", vault = true)).isEmpty())
        assertTrue(c.mcp.usable(Conversation(title = "x", model = "m")).isNotEmpty())
        c.mcpServers.update(server.id) { it.copy(enabled = false) }
        assertTrue(c.mcp.usable(Conversation(title = "x", model = "m")).isEmpty())
        assertEquals(listOf("npx", "-y", "server", "C:\\Mit Leerzeichen"), McpStdio.split("npx -y server \"C:\\Mit Leerzeichen\""))
    }

    @Test
    fun streamableHttpKeepsTheSessionAndReadsEventStreams() = runBlocking {
        val sessions = mutableListOf<String?>()
        val tokens = mutableListOf<String?>()
        val srv = server { ex ->
            val body = JSON.parseToJsonElement(ex.requestBody.readBytes().decodeToString()).jsonObject
            sessions += ex.requestHeaders.getFirst("Mcp-Session-Id")
            tokens += ex.requestHeaders.getFirst("Authorization")
            val id = body["id"]
            when (body["method"]!!.jsonPrimitive.content) {
                "initialize" -> ex.reply(200, """{"jsonrpc":"2.0","id":$id,"result":{"protocolVersion":"2025-06-18","serverInfo":{"name":"S"}}}""", headers = mapOf("Mcp-Session-Id" to "abc"))
                "notifications/initialized" -> ex.reply(202)
                else -> ex.reply(200, "event: message\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"}\n\nevent: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":$id,\n" +
                    "data: \"result\":{\"tools\":[{\"name\":\"x\",\"inputSchema\":{\"type\":\"object\"}}]}}\n\n", type = "text/event-stream")
            }
        }
        try {
            val s = McpSession(McpHttpTransport(http, "http://127.0.0.1:${srv.address.port}/mcp") { mapOf("Authorization" to "Bearer t0k") })
            assertEquals(listOf("x"), s.tools().map { it.name })
            assertEquals(listOf(null, "abc", "abc"), sessions)
            assertTrue(tokens.all { it == "Bearer t0k" })
        } finally {
            srv.stop(0)
        }
        // 401 asks for a login and names the metadata of the server.
        val locked = server { ex -> ex.reply(401, "", headers = mapOf("WWW-Authenticate" to "Bearer resource_metadata=\"https://x/.well-known/oauth-protected-resource\"")) }
        try {
            val e = assertFailsWith<io.github.thepytales.llmhub.net.McpAuthRequired> { McpSession(McpHttpTransport(http, "http://127.0.0.1:${locked.address.port}/mcp") { emptyMap() }).tools() }
            assertEquals("https://x/.well-known/oauth-protected-resource", e.resourceMetadata)
        } finally {
            locked.stop(0)
        }
    }

    @Test
    fun oauthLoginWithPkceAndDynamicRegistration() = runBlocking {
        var registered: JsonObject? = null
        var tokenForm: Map<String, String> = emptyMap()
        var challenge: String? = null
        lateinit var srv: HttpServer
        srv = server { ex ->
            val origin = "http://127.0.0.1:${srv.address.port}"
            when (ex.requestURI.path) {
                "/.well-known/oauth-protected-resource/mcp" -> ex.reply(200, """{"resource":"$origin/mcp","authorization_servers":["$origin/auth"],"scopes_supported":["wiki.read"]}""")
                "/.well-known/oauth-authorization-server/auth" -> ex.reply(200, """{"authorization_endpoint":"$origin/auth/authorize","token_endpoint":"$origin/auth/token","registration_endpoint":"$origin/auth/register"}""")
                "/auth/register" -> { registered = JSON.parseToJsonElement(ex.requestBody.readBytes().decodeToString()).jsonObject; ex.reply(201, """{"client_id":"lora-123"}""") }
                "/auth/token" -> {
                    tokenForm = ex.requestBody.readBytes().decodeToString().split('&').associate { URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
                    ex.reply(200, """{"access_token":"acc","refresh_token":"ref","expires_in":3600,"token_type":"Bearer"}""")
                }
                else -> ex.reply(404)
            }
        }
        try {
            val origin = "http://127.0.0.1:${srv.address.port}"
            val state = McpOAuth.login(http, "$origin/mcp", null, openUrl = { url ->
                // The „browser“: signs in and comes back to the app with the code.
                val q = URI(url).rawQuery.split('&').associate { URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
                assertEquals("lora-123", q["client_id"])
                assertEquals("S256", q["code_challenge_method"])
                assertEquals("wiki.read", q["scope"])
                assertEquals("$origin/mcp", q["resource"])
                challenge = q["code_challenge"]
                Thread {
                    http.newCall(okhttp3.Request.Builder().url(q["redirect_uri"] + "?code=c0de&state=" + java.net.URLEncoder.encode(q["state"], "UTF-8")).build()).execute().use { r ->
                        assertTrue(r.body!!.string().contains("Angemeldet"))
                    }
                }.start()
            })
            assertEquals("acc", state.accessToken)
            assertEquals("ref", state.refreshToken)
            assertEquals("lora-123", state.clientId)
            assertFalse(state.expired())
            assertEquals("none", registered!!["token_endpoint_auth_method"]!!.jsonPrimitive.content)
            assertEquals("c0de", tokenForm["code"])
            // PKCE: the verifier sent with the code fits the challenge of the login page.
            val verifier = tokenForm["code_verifier"]!!
            assertEquals(challenge, Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())))
            assertEquals("authorization_code", tokenForm["grant_type"])
            assertEquals("https://wiki.example.org/mcp", McpOAuth.canonical("HTTPS://Wiki.Example.org/mcp/"))
        } finally {
            srv.stop(0)
        }
    }
}
