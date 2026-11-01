package io.github.thepytales.llmhub.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

/** A file or folder in a WebDAV storage; [path] is relative to the user's root and starts with „/“. */
data class DavEntry(
    val path: String,
    val name: String,
    val folder: Boolean,
    val size: Long = 0,
    val modified: Long = 0,
    val mime: String? = null,
)

/**
 * Nextcloud over WebDAV (AP-22): list folders (PROPFIND), download, upload, create folders. Works the same on Android and
 * Windows (OkHttp), with the login name and an app password (Nextcloud › Einstellungen › Sicherheit, or [NextcloudLogin]).
 * [server] is the address of the Nextcloud, e.g. https://cloudstore.zih.tu-dresden.de; a WebDAV address copied from
 * Nextcloud („…/remote.php/dav/files/<name>/“) works as well.
 */
class WebDavClient(
    private val http: OkHttpClient,
    server: String,
    private val user: String,
    private val password: String,
) {
    /** Root of the user's files, without a slash at the end. */
    val root: String = davRoot(server, user)

    private fun auth() = Credentials.basic(user, password, Charsets.UTF_8)

    fun url(path: String): String = root + encodePath(path)

    private fun request(path: String) = Request.Builder().url(url(path)).header("Authorization", auth()).header("OCS-APIRequest", "true")

    /** Files and folders directly in [path] (folders first, then by name). */
    suspend fun list(path: String = "/"): List<DavEntry> = withContext(Dispatchers.IO) {
        val folder = normalize(path).let { if (it.endsWith("/")) it else "$it/" }
        val req = request(folder)
            .header("Depth", "1")
            .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML))
            .build()
        http.newCall(req).execute().use { resp ->
            if (resp.code == 404) throw IOException("Der Ordner „$folder“ existiert in Nextcloud nicht.")
            check(resp)
            parse(resp.body?.string().orEmpty(), rootPath()).filter { normalize(it.path).trimEnd('/') != folder.trimEnd('/') }
                .sortedWith(compareBy<DavEntry>({ !it.folder }, { it.name.lowercase() }))
        }
    }

    /** Whether the login works; the name of the root folder's owner or an error text. */
    suspend fun test(): Result<Int> = runCatching { list("/").size }

    suspend fun download(path: String, maxBytes: Long = MAX_DOWNLOAD): ByteArray = withContext(Dispatchers.IO) {
        http.newCall(request(path).get().build()).execute().use { resp ->
            check(resp)
            val body = resp.body ?: throw IOException("Leere Antwort von Nextcloud.")
            if (body.contentLength() > maxBytes) throw IOException("Die Datei ist größer als ${maxBytes / 1_000_000} MB.")
            body.bytes()
        }
    }

    /** Uploads [bytes] to [path] (an existing file is replaced, Nextcloud keeps its earlier version). */
    suspend fun upload(path: String, bytes: ByteArray, mime: String = "application/octet-stream") = withContext(Dispatchers.IO) {
        mkdirs(parent(path))
        http.newCall(request(path).put(bytes.toRequestBody(mime.toMediaType())).build()).execute().use { check(it) }
    }

    /** Creates [path] and the folders above it (existing ones are fine). */
    suspend fun mkdirs(path: String) = withContext(Dispatchers.IO) {
        val parts = normalize(path).trim('/').split('/').filter { it.isNotEmpty() }
        var current = ""
        for (p in parts) {
            current += "/$p"
            http.newCall(request("$current/").method("MKCOL", null).build()).execute().use { resp ->
                // 405: exists already.
                if (!resp.isSuccessful && resp.code != 405) check(resp)
            }
        }
    }

    suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        http.newCall(request(path).header("Depth", "0").method("PROPFIND", PROPFIND_BODY.toRequestBody(XML)).build()).execute().use { it.isSuccessful }
    }

    /** The path of the user's root as it appears in the server's answers (to make them relative). */
    private fun rootPath(): String = root.substringAfter("://").substringAfter('/', "").let { "/$it" }

    private fun check(resp: okhttp3.Response) {
        if (resp.isSuccessful) return
        throw IOException(
            when (resp.code) {
                401 -> "Nextcloud lehnt die Anmeldung ab. Benutzername und App-Passwort prüfen (Einstellungen › Verbindungen › Nextcloud)."
                403 -> "Nextcloud erlaubt diesen Zugriff nicht (HTTP 403)."
                404 -> "Nicht gefunden in Nextcloud (HTTP 404)."
                507 -> "Der Speicherplatz in Nextcloud ist voll."
                else -> "Nextcloud antwortet mit HTTP ${resp.code}."
            }
        )
    }

    companion object {
        private val XML = "application/xml; charset=utf-8".toMediaType()
        const val MAX_DOWNLOAD = 200L * 1024 * 1024

        private const val PROPFIND_BODY = """<?xml version="1.0" encoding="UTF-8"?>
<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/><d:getcontenttype/></d:prop></d:propfind>"""

        /** Root of the user's files from the server address or a copied WebDAV address. */
        fun davRoot(server: String, user: String): String {
            val s = server.trim().trimEnd('/').let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
            val dav = s.indexOf("/remote.php/")
            if (dav >= 0) {
                val base = s.substring(0, dav)
                val rest = s.substring(dav)
                // „…/remote.php/dav/files/<name>“ is already the root; the old „…/remote.php/webdav“ is the same place.
                if (rest.startsWith("/remote.php/dav/files/")) return s
                return "$base/remote.php/dav/files/${segment(user)}"
            }
            return "$s/remote.php/dav/files/${segment(user)}"
        }

        /** The server address without a WebDAV part (for the login flow and links). */
        fun serverBase(server: String): String = server.trim().trimEnd('/').let { s ->
            val full = if (s.startsWith("http://") || s.startsWith("https://")) s else "https://$s"
            full.substringBefore("/remote.php/").substringBefore("/index.php").trimEnd('/')
        }

        private fun segment(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        fun encodePath(path: String): String {
            val n = normalize(path)
            val trailing = n.endsWith("/") && n.length > 1
            return "/" + n.trim('/').split('/').filter { it.isNotEmpty() }.joinToString("/") { segment(it) } + if (trailing) "/" else ""
        }

        /** „a//b/../c“ → „/a/c“; always with a leading slash. */
        fun normalize(path: String): String {
            val trailing = path.endsWith("/")
            val out = ArrayDeque<String>()
            for (p in path.replace('\\', '/').split('/')) when (p) {
                "", "." -> Unit
                ".." -> out.removeLastOrNull()
                else -> out.addLast(p)
            }
            val joined = "/" + out.joinToString("/")
            return if (trailing && joined != "/") "$joined/" else joined
        }

        fun parent(path: String): String = normalize(path).trimEnd('/').substringBeforeLast('/', "").ifEmpty { "/" }

        fun join(folder: String, name: String): String = normalize(normalize(folder).trimEnd('/') + "/" + name)

        private val HTTP_DATE = ThreadLocal.withInitial { SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US) }

        /**
         * The entries of a PROPFIND answer (multistatus), with paths relative to [rootPath]. Prefix-agnostic, so „d:“,
         * „D:“ or no prefix all work.
         */
        fun parse(xml: String, rootPath: String): List<DavEntry> {
            fun tag(name: String) = Regex("<(?:[A-Za-z0-9_-]+:)?$name(?:\\s[^>]*)?>(.*?)</(?:[A-Za-z0-9_-]+:)?$name>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            val response = tag("response")
            val href = tag("href")
            val length = tag("getcontentlength")
            val modified = tag("getlastmodified")
            val type = tag("getcontenttype")
            val collection = Regex("<(?:[A-Za-z0-9_-]+:)?collection\\s*/?>", RegexOption.IGNORE_CASE)
            val root = URLDecoder.decode(rootPath.replace("+", "%2B"), "UTF-8").trimEnd('/')
            return response.findAll(xml).mapNotNull { m ->
                val block = m.groupValues[1]
                val raw = href.find(block)?.groupValues?.get(1)?.trim() ?: return@mapNotNull null
                val text = xmlText(raw)
                val pathPart = if (text.contains("://")) "/" + text.substringAfter("://").substringAfter('/', "") else text
                val decoded = URLDecoder.decode(pathPart.replace("+", "%2B"), "UTF-8")
                val rel = decoded.removePrefix(root).ifEmpty { "/" }
                val folder = collection.containsMatchIn(block)
                val name = rel.trimEnd('/').substringAfterLast('/')
                DavEntry(
                    path = normalize(rel).trimEnd('/').ifEmpty { "/" },
                    name = name,
                    folder = folder,
                    size = length.find(block)?.groupValues?.get(1)?.trim()?.toLongOrNull() ?: 0,
                    modified = modified.find(block)?.groupValues?.get(1)?.trim()?.let { d -> runCatching { HTTP_DATE.get()!!.parse(d)?.time }.getOrNull() } ?: 0,
                    mime = type.find(block)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() },
                )
            }.toList()
        }

        private fun xmlText(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
    }
}

/**
 * Login Flow v2 of Nextcloud: the app asks the server for a login page, the user signs in there in the browser (also with
 * the TU login and second factor), and the app receives an app password of its own – no password is typed into the app.
 */
object NextcloudLogin {
    data class Start(val loginUrl: String, val pollUrl: String, val token: String)
    data class Done(val server: String, val loginName: String, val appPassword: String)

    suspend fun start(http: OkHttpClient, server: String): Start = withContext(Dispatchers.IO) {
        val base = WebDavClient.serverBase(server)
        val req = Request.Builder().url("$base/index.php/login/v2").header("User-Agent", "Lora (TU Dresden)")
            .post(ByteArray(0).toRequestBody(null)).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException(
                if (resp.code == 404) "Unter $base antwortet keine Nextcloud (Anmeldung über den Browser nicht gefunden)."
                else "Nextcloud antwortet mit HTTP ${resp.code}."
            )
            val o = JSON.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
            val poll = o["poll"]?.jsonObject ?: throw IOException("Unerwartete Antwort von Nextcloud.")
            Start(o.s("login") ?: throw IOException("Unerwartete Antwort von Nextcloud."), poll.s("endpoint")!!, poll.s("token")!!)
        }
    }

    /** One poll: the credentials once the user has signed in, null while still waiting. */
    suspend fun poll(http: OkHttpClient, start: Start): Done? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(start.pollUrl)
            .post(okhttp3.FormBody.Builder().add("token", start.token).build()).build()
        http.newCall(req).execute().use { resp ->
            if (resp.code == 404) return@withContext null
            if (!resp.isSuccessful) throw IOException("Nextcloud antwortet mit HTTP ${resp.code}.")
            val o: JsonObject = JSON.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
            Done(o.s("server") ?: return@withContext null, o.s("loginName") ?: return@withContext null, o.s("appPassword") ?: return@withContext null)
        }
    }
}
