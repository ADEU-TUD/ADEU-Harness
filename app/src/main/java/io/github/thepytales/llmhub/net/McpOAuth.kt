package io.github.thepytales.llmhub.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Login of a MCP server over OAuth (stored with the server, encrypted with the other secrets). */
@Serializable
data class McpOAuthState(
    val clientId: String,
    val clientSecret: String? = null,
    val tokenEndpoint: String,
    val accessToken: String,
    val refreshToken: String? = null,
    /** Milliseconds; 0 = unknown. */
    val expiresAt: Long = 0,
    val redirectUri: String = "",
    val resource: String = "",
) {
    fun expired(now: Long = System.currentTimeMillis()) = expiresAt > 0 && now > expiresAt - 60_000
}

/**
 * OAuth 2.1 for MCP servers (Authorization Code with PKCE, MCP 2025-06-18): find the authorization server through the
 * protected resource metadata, register the app dynamically if the server allows it, open the login page in the browser
 * and receive the code on a port of this device (127.0.0.1), then exchange it for tokens. Works on Windows and Android.
 */
object McpOAuth {
    private val random = SecureRandom()
    private fun b64(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    data class Endpoints(val authorization: String, val token: String, val registration: String?, val scopes: List<String>)

    private suspend fun getJson(http: OkHttpClient, url: String): JsonObject? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).header("Accept", "application/json").header("MCP-Protocol-Version", McpSession.PROTOCOL).build()).execute().use { r ->
                if (!r.isSuccessful) null else JSON.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
            }
        }.getOrNull()
    }

    /** Where to log in for the MCP server at [serverUrl]. */
    suspend fun discover(http: OkHttpClient, serverUrl: String, resourceMetadata: String?): Endpoints {
        val uri = URI(serverUrl)
        val origin = "${uri.scheme}://${uri.authority}"
        val path = uri.rawPath.orEmpty().trimEnd('/')
        val resource = (resourceMetadata?.let { getJson(http, it) })
            ?: getJson(http, "$origin/.well-known/oauth-protected-resource$path")
            ?: getJson(http, "$origin/.well-known/oauth-protected-resource")
        val authServer = resource?.get("authorization_servers")?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull?.trimEnd('/') ?: origin
        val asUri = URI(authServer)
        val asOrigin = "${asUri.scheme}://${asUri.authority}"
        val asPath = asUri.rawPath.orEmpty().trimEnd('/')
        val meta = listOfNotNull(
            "$asOrigin/.well-known/oauth-authorization-server$asPath",
            "$asOrigin/.well-known/openid-configuration$asPath",
            if (asPath.isNotEmpty()) "$authServer/.well-known/openid-configuration" else null,
            if (asPath.isNotEmpty()) "$asOrigin/.well-known/oauth-authorization-server" else null,
        ).firstNotNullOfOrNull { getJson(http, it) }
        fun m(key: String) = meta?.get(key)?.jsonPrimitive?.contentOrNull
        val scopes = (resource?.get("scopes_supported") ?: meta?.get("scopes_supported"))?.let { runCatching { it.jsonArray.mapNotNull { s -> s.jsonPrimitive.contentOrNull } }.getOrNull() }.orEmpty()
        return Endpoints(
            authorization = m("authorization_endpoint") ?: "$asOrigin/authorize",
            token = m("token_endpoint") ?: "$asOrigin/token",
            registration = m("registration_endpoint") ?: if (meta == null) "$asOrigin/register" else null,
            scopes = scopes,
        )
    }

    /** Registers the app as an OAuth client (RFC 7591); the client id and maybe a secret. */
    private suspend fun register(http: OkHttpClient, endpoint: String, redirect: String): Pair<String, String?> = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("client_name", "Lora (TU Dresden)")
            putJsonArray("redirect_uris") { add(kotlinx.serialization.json.JsonPrimitive(redirect)) }
            putJsonArray("grant_types") { add(kotlinx.serialization.json.JsonPrimitive("authorization_code")); add(kotlinx.serialization.json.JsonPrimitive("refresh_token")) }
            putJsonArray("response_types") { add(kotlinx.serialization.json.JsonPrimitive("code")) }
            put("token_endpoint_auth_method", "none")
        }
        http.newCall(Request.Builder().url(endpoint).post(body.toString().toRequestBody("application/json".toMediaType())).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Der Server lässt die App nicht als Anwendung zu (Registrierung: HTTP ${r.code}). Trage stattdessen einen Token ein.")
            val o = JSON.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
            (o["client_id"]?.jsonPrimitive?.contentOrNull ?: throw IOException("Die Registrierung lieferte keine client_id.")) to o["client_secret"]?.jsonPrimitive?.contentOrNull
        }
    }

    /**
     * The whole login: [openUrl] shows the login page (system browser). Waits up to five minutes for the browser to come
     * back to this device. [clientId] is an id the user got from the operator of the server (if it does not register apps).
     */
    suspend fun login(
        http: OkHttpClient,
        serverUrl: String,
        resourceMetadata: String?,
        openUrl: (String) -> Unit,
        clientId: String? = null,
    ): McpOAuthState = withContext(Dispatchers.IO) {
        val ends = discover(http, serverUrl, resourceMetadata)
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { socket ->
            socket.soTimeout = 5 * 60_000
            val redirect = "http://127.0.0.1:${socket.localPort}/callback"
            val (id, secret) = when {
                !clientId.isNullOrBlank() -> clientId.trim() to null
                ends.registration != null -> register(http, ends.registration, redirect)
                else -> throw IOException("Der Server verlangt eine eigene Client-ID. Trage sie beim Server ein oder nutze einen Token.")
            }
            val verifier = b64(ByteArray(48).also { random.nextBytes(it) })
            val challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
            val state = b64(ByteArray(16).also { random.nextBytes(it) })
            val resource = canonical(serverUrl)
            val url = ends.authorization + (if ('?' in ends.authorization) "&" else "?") + listOfNotNull(
                "response_type=code", "client_id=${enc(id)}", "redirect_uri=${enc(redirect)}", "code_challenge=${enc(challenge)}",
                "code_challenge_method=S256", "state=${enc(state)}", "resource=${enc(resource)}",
                ends.scopes.takeIf { it.isNotEmpty() }?.let { "scope=${enc(it.joinToString(" "))}" },
            ).joinToString("&")
            openUrl(url)
            val code = try {
                awaitCode(socket, state)
            } catch (e: SocketTimeoutException) {
                throw IOException("Keine Anmeldung innerhalb von fünf Minuten. Bitte noch einmal versuchen.")
            }
            exchange(http, ends.token, FormBody.Builder()
                .add("grant_type", "authorization_code").add("code", code).add("redirect_uri", redirect)
                .add("client_id", id).add("code_verifier", verifier).add("resource", resource)
                .apply { secret?.let { add("client_secret", it) } }.build(), id, secret, redirect, resource, null)
        }
    }

    /** New tokens with the refresh token; null if the server does not give any (then log in again). */
    suspend fun refresh(http: OkHttpClient, s: McpOAuthState): McpOAuthState? = withContext(Dispatchers.IO) {
        val token = s.refreshToken ?: return@withContext null
        runCatching {
            exchange(http, s.tokenEndpoint, FormBody.Builder()
                .add("grant_type", "refresh_token").add("refresh_token", token).add("client_id", s.clientId)
                .apply { if (s.resource.isNotEmpty()) add("resource", s.resource); s.clientSecret?.let { add("client_secret", it) } }.build(),
                s.clientId, s.clientSecret, s.redirectUri, s.resource, token)
        }.getOrNull()
    }

    private fun exchange(http: OkHttpClient, endpoint: String, form: FormBody, id: String, secret: String?, redirect: String, resource: String, oldRefresh: String?): McpOAuthState =
        http.newCall(Request.Builder().url(endpoint).header("Accept", "application/json").post(form).build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("Der Server gibt keinen Zugang (Token: HTTP ${r.code}) " + text.take(200))
            val o = JSON.parseToJsonElement(text).jsonObject
            McpOAuthState(
                clientId = id, clientSecret = secret, tokenEndpoint = endpoint,
                accessToken = o["access_token"]?.jsonPrimitive?.contentOrNull ?: throw IOException("Antwort ohne access_token."),
                refreshToken = o["refresh_token"]?.jsonPrimitive?.contentOrNull ?: oldRefresh,
                expiresAt = o["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()?.let { System.currentTimeMillis() + it * 1000 } ?: 0,
                redirectUri = redirect, resource = resource,
            )
        }

    /** Waits for the browser's redirect to 127.0.0.1 and answers it with a short page. */
    private fun awaitCode(socket: ServerSocket, state: String): String {
        while (true) {
            socket.accept().use { client ->
                client.soTimeout = 10_000
                val line = client.getInputStream().bufferedReader().readLine().orEmpty()
                val target = line.split(' ').getOrNull(1).orEmpty()
                val query = target.substringAfter('?', "").split('&').filter { '=' in it }
                    .associate { p -> URLDecoder.decode(p.substringBefore('='), "UTF-8") to URLDecoder.decode(p.substringAfter('='), "UTF-8") }
                if (!target.startsWith("/callback")) {
                    client.getOutputStream().write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    return@use
                }
                val ok = query["code"] != null && query["state"] == state
                val html = if (ok) "<h2>Angemeldet</h2><p>Du kannst dieses Fenster schließen und zu Lora zurückkehren.</p>"
                else "<h2>Anmeldung nicht abgeschlossen</h2><p>${query["error_description"] ?: query["error"] ?: "Unbekannte Antwort"}</p>"
                val page = "<!DOCTYPE html><html lang=\"de\"><meta charset=\"utf-8\"><title>Lora</title><body style=\"font-family:sans-serif;margin:3em\">$html</body></html>"
                val bytes = page.toByteArray(Charsets.UTF_8)
                client.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray() + bytes)
                client.getOutputStream().flush()
                if (ok) return query["code"]!!
                if (query["error"] != null) throw IOException("Anmeldung abgelehnt: " + (query["error_description"] ?: query["error"]))
            }
        }
    }

    /** The server address as the „resource“ of the token (RFC 8707): lower-case scheme and host, no fragment. */
    fun canonical(url: String): String {
        val u = URI(url.trim())
        val port = if (u.port == -1) "" else ":${u.port}"
        return "${u.scheme.lowercase()}://${u.host.lowercase()}$port${u.rawPath.orEmpty().trimEnd('/')}"
    }
}
