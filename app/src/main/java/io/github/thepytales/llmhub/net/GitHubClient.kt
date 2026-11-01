package io.github.thepytales.llmhub.net

import io.github.thepytales.llmhub.logFailure
import android.util.Base64
import io.github.thepytales.llmhub.data.GitHubAccount
import io.github.thepytales.llmhub.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import io.github.thepytales.llmhub.ui.tr
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap

data class RepoSummary(
    val fullName: String,
    val description: String?,
    val isPrivate: Boolean,
    val defaultBranch: String,
    /** ISO time of the last change, for sorting the repositories of several tokens together. */
    val updatedAt: String = "",
) {
    val owner get() = fullName.substringBefore('/')
    val name get() = fullName.substringAfter('/')
}

/**
 * Minimal GitHub REST client authenticated with personal access tokens. Besides the main token further ones can be
 * stored (e.g. a fine-grained token per organization); every request picks the token that reaches the owner of
 * the repository and tries the others when GitHub answers 401/403/404.
 */
class GitHubClient(private val settings: SettingsStore, private val http: OkHttpClient) {
    private val api = "https://api.github.com".toHttpUrl()
    private val jsonType = "application/json".toMediaType()

    /** Owner (lowercase) → token that last reached one of its repositories. */
    private val learned = ConcurrentHashMap<String, String>()

    private data class Credential(val token: String, val owners: List<String>, val user: String)

    /** Main token first, then the further ones. */
    private fun credentials(): List<Credential> {
        val s = settings.current
        return (listOfNotNull(s.githubToken.takeIf { it.isNotBlank() }?.let { Credential(it, emptyList(), s.githubUser) }) +
            s.githubAccounts.filter { it.token.isNotBlank() }.map { Credential(it.token, it.owners, it.user) })
            .distinctBy { it.token }
    }

    /** Tokens to try for a request concerning [owner]: those known to reach it first. */
    private fun candidates(owner: String?): List<String> {
        val all = credentials()
        if (owner == null) return all.map { it.token }
        val o = owner.lowercase()
        return (listOfNotNull(learned[o]) +
            all.filter { c -> c.owners.any { it.equals(o, ignoreCase = true) } }.map { it.token } +
            all.filter { it.user.equals(o, ignoreCase = true) }.map { it.token } +
            all.map { it.token }).distinct().filter { t -> all.any { it.token == t } }
    }

    /** Owner of the repository a request is about: repos/{owner}/… or a code search with repo:{owner}/…. */
    private fun ownerOf(url: HttpUrl): String? {
        val seg = url.pathSegments
        return when {
            seg.size >= 2 && seg[0] == "repos" -> seg[1]
            seg.firstOrNull() == "search" -> url.queryParameter("q")?.let { REPO_QUALIFIER.find(it)?.groupValues?.get(1) }
            else -> null
        }
    }

    fun url(path: String, vararg query: Pair<String, String?>): HttpUrl {
        val b = api.newBuilder().addPathSegments(path.trim('/'))
        query.forEach { (k, v) -> if (v != null) b.addQueryParameter(k, v) }
        return b.build()
    }

    suspend fun call(method: String, url: HttpUrl, body: JsonElement? = null): JsonElement? {
        val owner = ownerOf(url)
        val tokens = candidates(owner)
        if (tokens.isEmpty()) throw ApiException(401, tr("Kein GitHub-Token hinterlegt (Einstellungen)."))
        var first: ApiException? = null
        for (token in tokens) {
            try {
                return call(method, url, body, token).also { if (owner != null) learned[owner.lowercase()] = token }
            } catch (e: ApiException) {
                // Not found or no permission with this token: another one may reach the repository.
                if (e.code !in RETRY_WITH_OTHER_TOKEN) throw e
                if (first == null) first = e
            }
        }
        throw first!!
    }

    private suspend fun call(method: String, url: HttpUrl, body: JsonElement?, token: String): JsonElement? =
        withContext(Dispatchers.IO) {
            val reqBody = body?.toString()?.toRequestBody(jsonType)
                ?: if (method == "GET") null else "".toRequestBody(jsonType)
            val req = Request.Builder().url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "ADEU-LLM-Android")
                .method(method, reqBody)
                .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = runCatching {
                        (JSON.parseToJsonElement(text).jsonObject["message"] as? JsonPrimitive)?.contentOrNull
                    }.getOrNull() ?: text.take(300)
                    throw ApiException(resp.code, "GitHub HTTP ${resp.code}: $msg")
                }
                if (text.isBlank()) null else JSON.parseToJsonElement(text)
            }
        }

    suspend fun get(path: String, vararg query: Pair<String, String?>) = call("GET", url(path, *query))

    private suspend fun me(token: String): String = (call("GET", url("user"), null, token) as JsonObject).s("login") ?: "?"

    /**
     * Checks the main token and remembers its account, which shows the GitHub options in the app.
     * A rejected token (401) hides them again; network errors leave the last result as it was.
     */
    suspend fun verify(): String {
        val token = settings.current.githubToken
        if (token.isBlank()) throw ApiException(401, "Kein GitHub-Token hinterlegt.")
        val login = try {
            me(token)
        } catch (e: ApiException) {
            if (e.code == 401) settings.update { if (it.githubToken == token) it.copy(githubUser = "") else it }
            throw e
        }
        settings.update { if (it.githubToken == token) it.copy(githubUser = login) else it }
        return login
    }

    /**
     * Checks a further token: its account and the owners of the repositories it reaches (for a fine-grained token
     * the one user or organization it was created for). Returns a line for the settings.
     */
    suspend fun verifyAccount(id: String): String {
        val token = settings.current.githubAccounts.firstOrNull { it.id == id }?.token
        if (token.isNullOrBlank()) throw ApiException(401, "Kein Token eingetragen.")
        fun store(transform: (GitHubAccount) -> GitHubAccount) = settings.update { s ->
            s.copy(githubAccounts = s.githubAccounts.map { if (it.id == id && it.token == token) transform(it) else it })
        }
        val login = try {
            me(token)
        } catch (e: ApiException) {
            if (e.code == 401) store { it.copy(user = "", owners = emptyList()) }
            throw e
        }
        val owners = runCatching { reposOf(token) }.getOrNull()?.map { it.owner }?.distinctBy { it.lowercase() }.orEmpty()
        store { it.copy(user = login, owners = owners) }
        owners.forEach { learned[it.lowercase()] = token }
        return accessLine(login, owners)
    }

    /** Re-checks all tokens at the start, so revoked ones hide the GitHub options again. */
    suspend fun verifyAll() {
        if (settings.current.githubToken.isNotBlank()) runCatching { verify() }.logFailure("GitHub-Token prüfen")
        settings.current.githubAccounts.filter { it.token.isNotBlank() }.forEach { a -> runCatching { verifyAccount(a.id) }.logFailure("GitHub-Konto prüfen") }
    }

    /** Repositories of all tokens, most recently changed first. */
    suspend fun listRepos(): List<RepoSummary> = coroutineScope {
        val tokens = credentials().map { it.token }
        if (tokens.isEmpty()) throw ApiException(401, tr("Kein GitHub-Token hinterlegt (Einstellungen)."))
        val results = tokens.map { t -> async { t to runCatching { reposOf(t) } } }.awaitAll()
        if (results.all { it.second.isFailure }) throw results.first().second.exceptionOrNull()!!
        val all = linkedMapOf<String, RepoSummary>()
        results.forEach { (token, res) ->
            res.getOrNull()?.forEach { r ->
                learned.putIfAbsent(r.owner.lowercase(), token)
                all.putIfAbsent(r.fullName.lowercase(), r)
            }
        }
        all.values.sortedByDescending { it.updatedAt }
    }

    private suspend fun reposOf(token: String): List<RepoSummary> {
        val all = mutableListOf<RepoSummary>()
        for (page in 1..5) {
            val arr = call(
                "GET",
                url(
                    "user/repos",
                    "per_page" to "100", "page" to page.toString(), "sort" to "updated",
                    "affiliation" to "owner,collaborator,organization_member",
                ),
                null,
                token,
            )?.jsonArray ?: break
            arr.forEach { el ->
                val o = el.jsonObject
                all += RepoSummary(
                    fullName = o.s("full_name") ?: return@forEach,
                    description = o.s("description"),
                    isPrivate = o.s("private") == "true",
                    defaultBranch = o.s("default_branch") ?: "main",
                    updatedAt = o.s("pushed_at") ?: o.s("updated_at") ?: "",
                )
            }
            if (arr.size < 100) break
        }
        return all
    }

    suspend fun defaultBranch(repo: String): String =
        (get("repos/$repo") as JsonObject).s("default_branch") ?: "main"

    /** Returns decoded file text and blob sha, or null if the file does not exist. */
    suspend fun readFile(repo: String, path: String, ref: String?): Pair<String, String>? {
        val res = try {
            get("repos/$repo/contents/${path.trim('/')}", "ref" to ref)
        } catch (e: ApiException) {
            if (e.code == 404) return null else throw e
        }
        if (res is JsonArray) throw ApiException(400, "'$path' ist ein Verzeichnis, keine Datei.")
        val o = res as JsonObject
        val sha = o.s("sha") ?: ""
        val encoded = o.s("content")
        val text = if (encoded != null && encoded.isNotEmpty()) {
            String(Base64.decode(encoded.replace("\n", ""), Base64.DEFAULT), Charsets.UTF_8)
        } else {
            // Files > 1 MB: fall back to the blob API.
            val blob = get("repos/$repo/git/blobs/$sha") as JsonObject
            String(Base64.decode((blob.s("content") ?: "").replace("\n", ""), Base64.DEFAULT), Charsets.UTF_8)
        }
        return text to sha
    }

    suspend fun writeFile(repo: String, path: String, content: String, message: String, branch: String): String {
        val existing = readFile(repo, path, branch)
        val body = buildJsonObject {
            put("message", message)
            put("content", Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            put("branch", branch)
            existing?.let { put("sha", it.second) }
        }
        val res = call("PUT", url("repos/$repo/contents/${path.trim('/')}"), body) as JsonObject
        val commit = (res["commit"] as? JsonObject)?.s("sha")?.take(7)
        return (if (existing == null) "Erstellt" else "Aktualisiert") + ": $path (Commit $commit auf $branch)"
    }

    suspend fun deleteFile(repo: String, path: String, message: String, branch: String): String {
        val existing = readFile(repo, path, branch) ?: return "Datei $path existiert nicht."
        val body = buildJsonObject {
            put("message", message)
            put("sha", existing.second)
            put("branch", branch)
        }
        call("DELETE", url("repos/$repo/contents/${path.trim('/')}"), body)
        return "Gelöscht: $path auf $branch"
    }

    suspend fun createBranch(repo: String, name: String, from: String): String {
        val ref = get("repos/$repo/git/ref/heads/$from") as JsonObject
        val sha = (ref["object"] as JsonObject).s("sha")
        call("POST", url("repos/$repo/git/refs"), buildJsonObject {
            put("ref", "refs/heads/$name")
            put("sha", sha)
        })
        return "Branch '$name' von '$from' erstellt."
    }
}

fun JsonObject.s(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/** HTTP codes after which a request is tried again with another token. */
private val RETRY_WITH_OTHER_TOKEN = setOf(401, 403, 404)
private val REPO_QUALIFIER = Regex("repo:([A-Za-z0-9-]+)/")

/** "thepytales · Zugriff auf ADEU-TUD" – for the GitHub settings. */
fun accessLine(user: String, owners: List<String>): String =
    "✓ Angemeldet als $user" + if (owners.isEmpty()) "" else " · Zugriff auf ${owners.joinToString(", ")}"
