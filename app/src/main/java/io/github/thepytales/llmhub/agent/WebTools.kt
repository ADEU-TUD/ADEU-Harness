package io.github.thepytales.llmhub.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Lets the model read public web pages as plain text. */
class WebTools(http: OkHttpClient) : ToolProvider {
    private val client = http.newBuilder()
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override val defs = listOf(
        ToolDef(
            "fetch_url",
            "Lädt eine öffentliche Webseite (http/https) und gibt ihren Text zurück. Nützlich für Dokumentationen, " +
                "Artikel oder Links, die der Nutzer nennt.",
            listOf(Param("url", "Vollständige URL")),
        ),
    )

    override suspend fun run(name: String, args: JsonObject, ctx: RepoContext): ToolOutput {
        if (name != "fetch_url") return ToolOutput("FEHLER: Unbekanntes Werkzeug '$name'.")
        val raw = args.req("url").trim()
        val url = (if ("://" in raw) raw else "https://$raw").toHttpUrlOrNull()
            ?: return ToolOutput("FEHLER: Ungültige URL '$raw'.")
        return withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Android) LLMHub")
                .header("Accept", "text/html,text/plain,application/json;q=0.9,*/*;q=0.5")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use ToolOutput("FEHLER: HTTP ${resp.code} für $url")
                val type = resp.header("Content-Type").orEmpty().lowercase()
                if (listOf("image/", "audio/", "video/", "application/pdf", "application/zip", "octet-stream").any { it in type }) {
                    return@use ToolOutput("Die URL liefert keine Textseite ($type).")
                }
                val body = resp.body?.source()?.let { src ->
                    src.request(MAX_BYTES)
                    src.buffer.clone().readUtf8(minOf(src.buffer.size, MAX_BYTES))
                }.orEmpty()
                val text = if ("html" in type || body.trimStart().startsWith("<")) htmlToText(body) else body
                val title = TITLE.find(body)?.groupValues?.get(1)?.let { decodeEntities(it).trim() }
                ToolOutput(
                    buildString {
                        append("URL: ${resp.request.url}\n")
                        if (!title.isNullOrBlank()) append("Titel: $title\n")
                        append('\n')
                        append(text.take(MAX_CHARS))
                        if (text.length > MAX_CHARS) append("\n…[gekürzt]")
                    }
                )
            }
        }
    }

    companion object {
        private const val MAX_BYTES = 3L * 1024 * 1024
        private const val MAX_CHARS = 30_000
        private val TITLE = Regex("<title[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE)
        private val DROP = Regex(
            "<(script|style|noscript|svg|head|nav|footer|iframe)[^>]*>[\\s\\S]*?</\\1>|<!--[\\s\\S]*?-->",
            RegexOption.IGNORE_CASE,
        )
        private val BREAK = Regex("<(br|/p|/div|/li|/tr|/h[1-6]|/section|/article|/pre)[^>]*>", RegexOption.IGNORE_CASE)
        private val ITEM = Regex("<li[^>]*>", RegexOption.IGNORE_CASE)
        private val HEADING = Regex("<h([1-6])[^>]*>", RegexOption.IGNORE_CASE)
        private val TAG = Regex("<[^>]+>")

        const val PROMPT = "Mit dem Werkzeug fetch_url kannst du Webseiten lesen, z. B. wenn der Nutzer einen Link schickt."

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

        private fun codePoint(cp: Int?): String? = cp?.let { runCatching { String(Character.toChars(it)) }.getOrNull() }

        private fun decodeEntities(s: String): String = s
            .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
            .replace(Regex("&#(\\d+);")) { m -> codePoint(m.groupValues[1].toIntOrNull()) ?: m.value }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { m -> codePoint(m.groupValues[1].toIntOrNull(16)) ?: m.value }
            .replace("&amp;", "&")
    }
}
