package io.github.thepytales.llmhub.net

import android.util.Base64
import io.github.thepytales.llmhub.data.SettingsStore
import kotlinx.coroutines.Dispatchers
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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class RepoSummary(
    val fullName: String,
    val description: String?,
    val isPrivate: Boolean,
    val defaultBranch: String,
)

/** Minimal GitHub REST client authenticated with a personal access token. */
class GitHubClient(private val settings: SettingsStore, private val http: OkHttpClient) {
    private val api = "https://api.github.com".toHttpUrl()
    private val jsonType = "application/json".toMediaType()

    fun url(path: String, vararg query: Pair<String, String?>): HttpUrl {
        val b = api.newBuilder().addPathSegments(path.trim('/'))
        query.forEach { (k, v) -> if (v != null) b.addQueryParameter(k, v) }
        return b.build()
    }

    suspend fun call(method: String, url: HttpUrl, body: JsonElement? = null): JsonElement? =
        withContext(Dispatchers.IO) {
            val token = settings.current.githubToken
            if (token.isBlank()) throw ApiException(401, "Kein GitHub-Token hinterlegt (Einstellungen).")
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

    suspend fun me(): String = (get("user") as JsonObject).s("login") ?: "?"

    /**
     * Checks the stored token and remembers its account, which shows the GitHub options in the app.
     * A rejected token (401) hides them again; network errors leave the last result as it was.
     */
    suspend fun verify(): String {
        val token = settings.current.githubToken
        if (token.isBlank()) throw ApiException(401, "Kein GitHub-Token hinterlegt.")
        val login = try {
            me()
        } catch (e: ApiException) {
            if (e.code == 401) settings.update { if (it.githubToken == token) it.copy(githubUser = "") else it }
            throw e
        }
        settings.update { if (it.githubToken == token) it.copy(githubUser = login) else it }
        return login
    }

    suspend fun listRepos(): List<RepoSummary> {
        val all = mutableListOf<RepoSummary>()
        for (page in 1..5) {
            val arr = get(
                "user/repos",
                "per_page" to "100", "page" to page.toString(), "sort" to "updated",
                "affiliation" to "owner,collaborator,organization_member",
            )?.jsonArray ?: break
            arr.forEach { el ->
                val o = el.jsonObject
                all += RepoSummary(
                    fullName = o.s("full_name") ?: return@forEach,
                    description = o.s("description"),
                    isPrivate = o.s("private") == "true",
                    defaultBranch = o.s("default_branch") ?: "main",
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
