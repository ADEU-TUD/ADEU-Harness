package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.net.JSON
import io.github.thepytales.llmhub.net.s
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/** Lets the model search the web, find news and read web pages or call web APIs. */
class WebTools(http: OkHttpClient) : ToolProvider {
    private val client = http.newBuilder()
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override val defs = listOf(
        ToolDef(
            "web_search",
            "Sucht im Web (DuckDuckGo) und liefert Titel, Link und Kurztext der besten Treffer. Danach mit fetch_url " +
                "die interessantesten Seiten lesen.",
            listOf(
                Param("query", "Suchbegriffe"),
                Param("max_results", "Anzahl Treffer (Standard 8, max. 20)", required = false, type = "integer"),
            ),
        ),
        ToolDef(
            "news_search",
            "Sucht aktuelle Nachrichten zu einem Thema und liefert Schlagzeile, Quelle, Datum und Link.",
            listOf(
                Param("query", "Thema oder Suchbegriffe"),
                Param("language", "Sprache/Region: de (Standard) oder en", required = false),
                Param("max_results", "Anzahl Meldungen (Standard 12, max. 30)", required = false, type = "integer"),
            ),
        ),
        ToolDef(
            "fetch_url",
            "Lädt eine Webseite (http/https) und gibt ihren Text zurück, oder ruft eine Web-API auf. In URL, Headern und " +
                "Body kannst du Umgebungsvariablen als \${NAME} verwenden.",
            listOf(
                Param("url", "Vollständige URL"),
                Param("method", "HTTP-Methode: GET (Standard), POST, PUT, PATCH oder DELETE", required = false),
                Param("headers", "Optionale Header als JSON-Objekt, z.B. {\"Authorization\": \"Bearer \${API_KEY}\"}", required = false),
                Param("body", "Optionaler Request-Body (z.B. JSON)", required = false),
            ),
        ),
    )

    override fun risk(name: String, args: JsonObject) =
        if (name == "fetch_url" && (args.s("method")?.uppercase() ?: "GET") !in setOf("GET", "HEAD")) Risk.PUBLIC else null

    // Searches and page reads are independent of each other; writing requests are never run in parallel (they have a risk).
    override fun parallelSafe(name: String) = true

    override suspend fun run(name: String, args: JsonObject, ctx: RepoContext): ToolOutput {
        val out = when (name) {
            "fetch_url" -> fetch(args, ctx)
            "web_search" -> webSearch(args.req("query"), (args.int("max_results") ?: 8).coerceIn(1, 20), ctx)
            "news_search" -> newsSearch(
                args.req("query"), args.s("language")?.lowercase()?.take(2) ?: "de",
                (args.int("max_results") ?: 12).coerceIn(1, 30), ctx,
            )
            else -> ToolOutput("FEHLER: Unbekanntes Werkzeug '$name'.")
        }
        return ctx.env?.let { env -> out.copy(text = env.mask(out.text)) } ?: out
    }

    private fun checkAllowed(url: HttpUrl, ctx: RepoContext): String? {
        val env = ctx.env ?: return null
        return if (env.allowsHost(url.host)) null
        else "FEHLER: Die Umgebung „${env.name}“ erlaubt keinen Zugriff auf ${url.host}."
    }

    private suspend fun get(url: HttpUrl, ctx: RepoContext): String? {
        checkAllowed(url, ctx)?.let { throw IllegalStateException(it.removePrefix("FEHLER: ")) }
        return withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url)
                .header("User-Agent", BROWSER_UA)
                .header("Accept-Language", "de-DE,de;q=0.9,en;q=0.8")
                .build()
            client.newCall(req).execute().use { resp -> if (resp.isSuccessful) resp.body?.string() else null }
        }
    }

    private suspend fun fetch(args: JsonObject, ctx: RepoContext): ToolOutput {
        val env = ctx.env
        val raw = (env?.substitute(args.req("url")) ?: args.req("url")).trim()
        val url = (if ("://" in raw) raw else "https://$raw").toHttpUrlOrNull()
            ?: return ToolOutput("FEHLER: Ungültige URL '${args.req("url")}'.")
        checkAllowed(url, ctx)?.let { return ToolOutput(it) }
        val method = args.s("method")?.uppercase()?.trim().orEmpty().ifEmpty { "GET" }
        val headers = args.s("headers")?.let { h ->
            val obj = runCatching { JSON.parseToJsonElement(h) as? JsonObject }.getOrNull()
                ?: return ToolOutput("FEHLER: headers muss ein JSON-Objekt sein.")
            obj.mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull ?: v.toString() }
        } ?: (args["headers"] as? JsonObject)?.mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull ?: v.toString() }
        val body = args.s("body")?.let { env?.substitute(it) ?: it }
        return withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Android) ADEU-LLM")
                .header("Accept", "text/html,text/plain,application/json;q=0.9,*/*;q=0.5")
            headers.orEmpty().forEach { (k, v) -> builder.header(k, env?.substitute(v) ?: v) }
            val type = (headers.orEmpty().entries.firstOrNull { it.key.equals("Content-Type", true) }?.value
                ?: if (body?.trimStart()?.startsWith("{") == true || body?.trimStart()?.startsWith("[") == true) "application/json" else "text/plain")
                .toMediaTypeOrNull()
            when (method) {
                "GET", "HEAD" -> builder.method(method, null)
                else -> builder.method(method, (body ?: "").toRequestBody(type))
            }
            client.newCall(builder.build()).execute().use { resp ->
                val contentType = resp.header("Content-Type").orEmpty().lowercase()
                if (listOf("image/", "audio/", "video/", "application/pdf", "application/zip", "octet-stream").any { it in contentType }) {
                    return@use ToolOutput("HTTP ${resp.code}: Die URL liefert keine Textseite ($contentType).")
                }
                val text = resp.body?.source()?.let { src ->
                    src.request(MAX_BYTES)
                    src.buffer.clone().readUtf8(minOf(src.buffer.size, MAX_BYTES))
                }.orEmpty()
                val readable = if ("html" in contentType || text.trimStart().startsWith("<!") || text.trimStart().startsWith("<html", true)) htmlToText(text) else text
                val title = TITLE.find(text)?.groupValues?.get(1)?.let { decodeEntities(it).trim() }
                ToolOutput(
                    buildString {
                        if (!resp.isSuccessful) append("HTTP ${resp.code} ${resp.message}\n")
                        append("URL: ${resp.request.url}\n")
                        if (!title.isNullOrBlank()) append("Titel: $title\n")
                        append('\n')
                        append(readable.take(MAX_CHARS))
                        if (readable.length > MAX_CHARS) append("\n…[gekürzt]")
                    }
                )
            }
        }
    }

    private suspend fun webSearch(query: String, max: Int, ctx: RepoContext): ToolOutput {
        val url = "https://html.duckduckgo.com/html/".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("q", query).addQueryParameter("kl", "de-de").build()
        val html = get(url, ctx) ?: return ToolOutput("FEHLER: Die Websuche ist gerade nicht erreichbar.")
        val results = parseDuckDuckGo(html).take(max)
        if (results.isEmpty()) return ToolOutput("Keine Treffer für „$query“.")
        return ToolOutput(
            "Suchergebnisse für „$query“:\n\n" + results.mapIndexed { i, r ->
                "${i + 1}. ${r.title}\n   ${r.url}" + if (r.snippet.isNotBlank()) "\n   ${r.snippet}" else ""
            }.joinToString("\n\n")
        )
    }

    private suspend fun newsSearch(query: String, language: String, max: Int, ctx: RepoContext): ToolOutput {
        val de = language != "en"
        val items = runCatching {
            val url = "https://www.bing.com/news/search".toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("q", query).addQueryParameter("format", "rss")
                .addQueryParameter("setlang", if (de) "de-DE" else "en-US")
                .addQueryParameter("cc", if (de) "DE" else "US")
                .build()
            get(url, ctx)?.let { parseRss(it) }.orEmpty()
        }.getOrDefault(emptyList()).ifEmpty {
            val url = "https://news.google.com/rss/search".toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("q", "$query when:7d")
                .addQueryParameter("hl", if (de) "de" else "en-US")
                .addQueryParameter("gl", if (de) "DE" else "US")
                .addQueryParameter("ceid", if (de) "DE:de" else "US:en")
                .build()
            get(url, ctx)?.let { parseRss(it) }.orEmpty()
        }.take(max)
        if (items.isEmpty()) return ToolOutput("Keine aktuellen Nachrichten zu „$query“ gefunden.")
        return ToolOutput(
            "Nachrichten zu „$query“:\n\n" + items.mapIndexed { i, n ->
                buildString {
                    append("${i + 1}. ${n.title}")
                    val meta = listOfNotNull(n.source, n.date).joinToString(", ")
                    if (meta.isNotEmpty()) append(" ($meta)")
                    append("\n   ${n.url}")
                    if (n.snippet.isNotBlank()) append("\n   ${n.snippet}")
                }
            }.joinToString("\n\n")
        )
    }

    data class SearchResult(val title: String, val url: String, val snippet: String)

    data class NewsItem(val title: String, val url: String, val source: String?, val date: String?, val snippet: String)

    companion object {
        private const val MAX_BYTES = 3L * 1024 * 1024
        private const val MAX_CHARS = 30_000
        private const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
        private val TITLE = Regex("<title[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE)
        private val DROP = Regex(
            "<(script|style|noscript|svg|head|nav|footer|iframe)[^>]*>[\\s\\S]*?</\\1>|<!--[\\s\\S]*?-->",
            RegexOption.IGNORE_CASE,
        )
        private val BREAK = Regex("<(br|/p|/div|/li|/tr|/h[1-6]|/section|/article|/pre)[^>]*>", RegexOption.IGNORE_CASE)
        private val ITEM = Regex("<li[^>]*>", RegexOption.IGNORE_CASE)
        private val HEADING = Regex("<h([1-6])[^>]*>", RegexOption.IGNORE_CASE)
        private val TAG = Regex("<[^>]+>")
        private val DDG_LINK = Regex("<a([^>]*class=\"[^\"]*result__a[^\"]*\"[^>]*)>([\\s\\S]*?)</a>", RegexOption.IGNORE_CASE)
        private val DDG_SNIPPET = Regex("class=\"[^\"]*result__snippet[^\"]*\"[^>]*>([\\s\\S]*?)</(a|div|td)>", RegexOption.IGNORE_CASE)
        private val HREF = Regex("href=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        private val RSS_ITEM = Regex("<item>([\\s\\S]*?)</item>", RegexOption.IGNORE_CASE)

        const val PROMPT = "Mit web_search kannst du im Web suchen, mit news_search aktuelle Nachrichten finden und mit " +
            "fetch_url Webseiten lesen (z. B. Links des Nutzers). Nenne bei Fakten aus dem Web die Quelle mit Link."

        fun htmlToText(html: String): String {
            val s = html
                .replace(DROP, " ")
                .replace(HEADING) { "\n\n" + "#".repeat(it.groupValues[1].toInt()) + " " }
                .replace(ITEM, "\n- ")
                .replace(BREAK, "\n")
                .replace(TAG, " ")
            return decodeEntities(s)
                .lines()
                .map { it.replace(Regex("[ \\t\\u00A0]+"), " ").trim() }
                .joinToString("\n")
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()
        }

        private fun inlineText(html: String) = decodeEntities(html.replace(TAG, "")).replace(Regex("\\s+"), " ").trim()

        private fun decodeUrl(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

        /** Parses the result list of html.duckduckgo.com. */
        fun parseDuckDuckGo(html: String): List<SearchResult> {
            val snippets = DDG_SNIPPET.findAll(html).map { inlineText(it.groupValues[1]) }.toList()
            return DDG_LINK.findAll(html).mapIndexedNotNull { i, m ->
                var href = decodeEntities(HREF.find(m.groupValues[1])?.groupValues?.get(1) ?: return@mapIndexedNotNull null)
                if ("uddg=" in href) href = decodeUrl(href.substringAfter("uddg=").substringBefore('&'))
                if (href.startsWith("//")) href = "https:$href"
                // Ads point to duckduckgo.com/y.js.
                if (!href.startsWith("http") || "duckduckgo.com/y.js" in href) return@mapIndexedNotNull null
                SearchResult(inlineText(m.groupValues[2]), href, snippets.getOrNull(i).orEmpty())
            }.distinctBy { it.url }.toList()
        }

        private fun tag(xml: String, name: String): String? =
            Regex("<$name[^>]*>([\\s\\S]*?)</$name>", RegexOption.IGNORE_CASE).find(xml)?.groupValues?.get(1)
                ?.replace("<![CDATA[", "")?.replace("]]>", "")?.trim()

        /** Parses RSS feeds of Bing News and Google News. */
        fun parseRss(xml: String): List<NewsItem> = RSS_ITEM.findAll(xml).mapNotNull { m ->
            val item = m.groupValues[1]
            val title = tag(item, "title")?.let { inlineText(decodeEntities(it)) } ?: return@mapNotNull null
            var link = decodeEntities(tag(item, "link") ?: return@mapNotNull null)
            // Bing wraps the article link: …/apiclick.aspx?…&url=https%3a%2f%2f…
            Regex("[?&]url=([^&]+)").find(link)?.let { link = decodeUrl(it.groupValues[1]) }
            val source = tag(item, "News:Source") ?: tag(item, "source")
            NewsItem(
                title = title,
                url = link,
                source = source?.let { inlineText(decodeEntities(it)) },
                date = tag(item, "pubDate"),
                snippet = tag(item, "description")?.let { inlineText(decodeEntities(it)) }?.take(300).orEmpty(),
            )
        }.toList()

        private fun codePoint(cp: Int?): String? = cp?.let { runCatching { String(Character.toChars(it)) }.getOrNull() }

        fun decodeEntities(s: String): String = s
            .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
            .replace(Regex("&#(\\d+);")) { m -> codePoint(m.groupValues[1].toIntOrNull()) ?: m.value }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { m -> codePoint(m.groupValues[1].toIntOrNull(16)) ?: m.value }
            .replace("&amp;", "&")
    }
}
