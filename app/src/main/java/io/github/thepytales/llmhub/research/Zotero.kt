package io.github.thepytales.llmhub.research

import io.github.thepytales.llmhub.net.JSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import io.github.thepytales.llmhub.ui.tr
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The Zotero web API (api.zotero.org, version 3): adds works to the user's library or a collection and searches it.
 * Needs the user ID and a key with write access from zotero.org/settings/keys.
 */
class ZoteroClient(private val http: OkHttpClient, private val userId: () -> String, private val apiKey: () -> String) {
    val configured: Boolean get() = userId().isNotBlank() && apiKey().isNotBlank()

    data class Collection(val key: String, val name: String)

    private fun base() = "https://api.zotero.org/users/${userId().trim()}/"

    private suspend fun call(path: String, body: String? = null, query: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        check(configured) { tr("Zotero ist nicht eingerichtet (Einstellungen › Literatur & Zotero).") }
        val url = (base() + path).toHttpUrl().newBuilder().apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val b = Request.Builder().url(url).header("Zotero-API-Key", apiKey().trim()).header("Zotero-API-Version", "3")
        if (body != null) b.post(body.toRequestBody("application/json".toMediaType()))
        http.newCall(b.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            when (resp.code) {
                in 200..299 -> text
                403 -> throw IllegalStateException(tr("Zotero lehnt den Schlüssel ab (HTTP 403) – stimmt die User-ID, und hat der Schlüssel Schreibrechte?"))
                404 -> throw IllegalStateException(tr("Zotero kennt die User-ID {0} nicht (HTTP 404).", userId()))
                429 -> throw IllegalStateException(tr("Zotero: zu viele Anfragen, bitte kurz warten."))
                else -> throw IllegalStateException(tr("Zotero antwortet mit HTTP {0}: {1}", resp.code, text.take(200)))
            }
        }
    }

    suspend fun collections(): List<Collection> {
        val arr = JSON.parseToJsonElement(call("collections", query = mapOf("limit" to "100"))) as? JsonArray ?: return emptyList()
        return arr.mapNotNull { e ->
            val data = (e as? JsonObject)?.get("data") as? JsonObject ?: return@mapNotNull null
            Collection((data["key"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null, (data["name"] as? JsonPrimitive)?.contentOrNull.orEmpty())
        }.sortedBy { it.name.lowercase() }
    }

    /** Creates the collection [name] if it does not exist yet; returns its key. */
    suspend fun collection(name: String): String {
        collections().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.let { return it.key }
        val body = buildJsonArray { add(buildJsonObject { put("name", name.trim()) }) }.toString()
        val resp = JSON.parseToJsonElement(call("collections", body)) as? JsonObject
        val key = ((resp?.get("successful") as? JsonObject)?.get("0") as? JsonObject)?.get("key") as? JsonPrimitive
        return key?.contentOrNull ?: throw IllegalStateException(tr("Zotero hat die Sammlung „{0}“ nicht angelegt.", name))
    }

    /** Adds [refs] (in steps of 50, the API's limit); returns how many were saved and the errors. */
    suspend fun add(refs: List<Reference>, collectionKey: String? = null): Pair<Int, List<String>> {
        var ok = 0
        val errors = mutableListOf<String>()
        refs.chunked(50).forEach { chunk ->
            val body = buildJsonArray { chunk.forEach { add(item(it, collectionKey)) } }.toString()
            val resp = JSON.parseToJsonElement(call("items", body)) as? JsonObject
            ok += (resp?.get("successful") as? JsonObject)?.size ?: 0
            (resp?.get("failed") as? JsonObject)?.forEach { (i, v) ->
                val msg = ((v as? JsonObject)?.get("message") as? JsonPrimitive)?.contentOrNull.orEmpty()
                errors += "${chunk.getOrNull(i.toIntOrNull() ?: -1)?.short()?.take(60) ?: i}: $msg"
            }
        }
        return ok to errors
    }

    suspend fun search(query: String, limit: Int = 25): List<Reference> {
        val arr = JSON.parseToJsonElement(call("items", query = mapOf("q" to query, "limit" to limit.toString(), "itemType" to "-attachment || note"))) as? JsonArray
            ?: return emptyList()
        return arr.mapNotNull { e -> ((e as? JsonObject)?.get("data") as? JsonObject)?.let(::fromItem) }
    }

    companion object {
        fun itemType(t: RefType) = when (t) {
            RefType.ARTICLE -> "journalArticle"
            RefType.BOOK, RefType.EDITED_BOOK -> "book"
            RefType.CHAPTER -> "bookSection"
            RefType.CONFERENCE -> "conferencePaper"
            RefType.THESIS -> "thesis"
            RefType.REPORT -> "report"
            RefType.WEB -> "webpage"
            RefType.OTHER -> "document"
        }

        /** A Zotero item as JSON (only fields the item type knows, otherwise Zotero rejects it). */
        fun item(r: Reference, collectionKey: String?): JsonObject = buildJsonObject {
            val type = itemType(r.type)
            put("itemType", type)
            put("title", r.title.ifBlank { r.raw })
            putJsonArray("creators") {
                r.authors.forEach { p -> add(creator(p, "author")) }
                r.editors.forEach { p -> add(creator(p, "editor")) }
            }
            if (r.year.isNotBlank()) put("date", r.year)
            if (r.abstract.isNotBlank()) put("abstractNote", r.abstract)
            if (r.url.isNotBlank()) put("url", r.url)
            if (r.accessed.isNotBlank()) put("accessDate", r.accessed)
            if (r.language.isNotBlank()) put("language", r.language)
            when (type) {
                "journalArticle" -> {
                    put("publicationTitle", r.container); put("volume", r.volume); put("issue", r.issue); put("pages", r.pages)
                    put("DOI", r.doi); put("ISSN", r.issn)
                }
                "book" -> {
                    put("publisher", r.publisher); put("place", r.place); put("edition", r.edition); put("ISBN", r.isbn)
                }
                "bookSection" -> {
                    put("bookTitle", r.container); put("publisher", r.publisher); put("place", r.place); put("pages", r.pages)
                    put("edition", r.edition); put("ISBN", r.isbn)
                }
                "conferencePaper" -> {
                    put("proceedingsTitle", r.container); put("publisher", r.publisher); put("place", r.place); put("pages", r.pages); put("DOI", r.doi)
                }
                "thesis" -> {
                    put("university", r.institution); put("thesisType", r.genre); put("place", r.place)
                }
                "report" -> {
                    put("institution", r.institution.ifBlank { r.publisher }); put("reportNumber", r.genre); put("place", r.place)
                }
                "webpage" -> put("websiteTitle", r.container)
                else -> put("publisher", r.publisher)
            }
            if (r.note.isNotBlank() || r.doi.isNotBlank() && type !in setOf("journalArticle", "conferencePaper")) {
                put("extra", listOfNotNull(r.doi.takeIf { it.isNotBlank() && type !in setOf("journalArticle", "conferencePaper") }?.let { "DOI: $it" }, r.note.ifBlank { null }).joinToString("\n"))
            }
            if (collectionKey != null) putJsonArray("collections") { add(JsonPrimitive(collectionKey)) }
            putJsonArray("tags") { r.lists.forEach { l -> add(buildJsonObject { put("tag", l) }) } }
        }

        private fun creator(p: Person, role: String) = buildJsonObject {
            put("creatorType", role)
            if (p.literal || p.given.isBlank()) put("name", p.family) else {
                put("firstName", p.given)
                put("lastName", p.family)
            }
        }

        fun fromItem(d: JsonObject): Reference {
            fun s(k: String) = (d[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val creators = (d["creators"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            fun people(role: String) = creators.filter { (it["creatorType"] as? JsonPrimitive)?.contentOrNull == role }.map { c ->
                val name = (c["name"] as? JsonPrimitive)?.contentOrNull
                if (name != null) Person(name, literal = true)
                else Person((c["lastName"] as? JsonPrimitive)?.contentOrNull.orEmpty(), (c["firstName"] as? JsonPrimitive)?.contentOrNull.orEmpty())
            }
            val authors = people("author")
            val editors = people("editor")
            val type = when (s("itemType")) {
                "journalArticle", "magazineArticle", "newspaperArticle" -> RefType.ARTICLE
                "book" -> if (authors.isEmpty() && editors.isNotEmpty()) RefType.EDITED_BOOK else RefType.BOOK
                "bookSection", "encyclopediaArticle", "dictionaryEntry" -> RefType.CHAPTER
                "conferencePaper" -> RefType.CONFERENCE
                "thesis" -> RefType.THESIS
                "report" -> RefType.REPORT
                "webpage", "blogPost" -> RefType.WEB
                else -> RefType.OTHER
            }
            return Reference(
                type = type, authors = authors, editors = editors, year = Regex("\\d{4}").find(s("date"))?.value.orEmpty(), title = s("title"),
                container = s("publicationTitle").ifBlank { s("bookTitle") }.ifBlank { s("proceedingsTitle") }.ifBlank { s("websiteTitle") },
                volume = s("volume"), issue = s("issue"), pages = s("pages").replace("-", "–"), edition = s("edition"),
                publisher = s("publisher"), place = s("place"), doi = Reference.cleanDoi(s("DOI").ifBlank { Regex("DOI:\\s*(\\S+)").find(s("extra"))?.groupValues?.get(1).orEmpty() }),
                url = s("url"), isbn = s("ISBN"), issn = s("ISSN"), accessed = s("accessDate").take(10),
                institution = s("university").ifBlank { s("institution") }, genre = s("thesisType").ifBlank { s("reportNumber") },
                abstract = s("abstractNote"), language = s("language"), source = "Zotero",
            )
        }
    }
}
