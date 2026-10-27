package io.github.thepytales.llmhub.files

import io.github.thepytales.llmhub.agent.WebTools
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale

/** E-mails as readable text: .eml (MIME) and Outlook .msg – sender, recipients, date, subject, text and attachments. */
object MailText {
    private val GERMAN_DATE = DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy HH:mm", Locale.GERMANY)

    // ------------------------------------------------------------------ .eml

    private class Part(val headers: Map<String, String>, val body: ByteArray) {
        fun header(name: String) = headers[name.lowercase()].orEmpty()
        val type get() = header("content-type").substringBefore(';').trim().lowercase().ifEmpty { "text/plain" }
        fun param(header: String, name: String): String? =
            Regex("(?i)(?:^|;)\\s*$name\\*?=\\s*(\"([^\"]*)\"|[^;\\s]+)").find(header(header))?.let { m ->
                (m.groups[2]?.value ?: m.groupValues[1]).let { v -> if (v.contains("''")) decodeRfc2231(v) else decodeWords(v) }
            }
    }

    fun eml(bytes: ByteArray): String {
        val root = part(bytes)
        val texts = mutableListOf<String>()
        val attachments = mutableListOf<String>()
        collect(root, texts, attachments, 0)
        return buildString {
            fun line(label: String, value: String) { if (value.isNotBlank()) append(label).append(": ").append(value.trim()).append('\n') }
            line("Von", decodeWords(root.header("from")))
            line("An", decodeWords(root.header("to")))
            line("Cc", decodeWords(root.header("cc")))
            line("Datum", date(root.header("date")))
            line("Betreff", decodeWords(root.header("subject")))
            if (attachments.isNotEmpty()) line("Anhänge", attachments.joinToString(", "))
            append('\n')
            append(texts.joinToString("\n\n").trim())
        }.trim()
    }

    private fun date(raw: String): String = runCatching {
        ZonedDateTime.parse(raw.trim().replace(Regex("\\s*\\([^)]*\\)$"), "").replace(Regex("\\s+"), " "), DateTimeFormatter.RFC_1123_DATE_TIME)
            .withZoneSameInstant(ZoneId.of("Europe/Berlin")).format(GERMAN_DATE)
    }.getOrDefault(raw.trim())

    private fun part(bytes: ByteArray): Part {
        val raw = String(bytes, Charsets.ISO_8859_1)
        val split = Regex("\r?\n\r?\n").find(raw)
        val headText = if (split == null) raw else raw.substring(0, split.range.first)
        val bodyStart = split?.range?.last?.plus(1) ?: raw.length
        val headers = LinkedHashMap<String, String>()
        var last: String? = null
        for (line in headText.split(Regex("\r?\n"))) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && last != null) {
                headers[last] = headers[last] + " " + line.trim()
            } else {
                val name = line.substringBefore(':', "").trim().lowercase()
                if (name.isEmpty()) continue
                if (name !in headers) headers[name] = rawHeader(line.substringAfter(':').trim())
                last = name
            }
        }
        return Part(headers, bytes.copyOfRange(bodyStart.coerceAtMost(bytes.size), bytes.size))
    }

    /** Raw 8-bit header values (UTF-8 without encoded words). */
    private fun rawHeader(s: String): String {
        val b = s.toByteArray(Charsets.ISO_8859_1)
        return if (b.any { it < 0 }) TextCodec.text(b) else s
    }

    private fun collect(p: Part, texts: MutableList<String>, attachments: MutableList<String>, depth: Int) {
        if (depth > 10) return
        val type = p.type
        val disposition = p.header("content-disposition").lowercase()
        val fileName = p.param("content-disposition", "filename") ?: p.param("content-type", "name")
        when {
            type.startsWith("multipart/") -> {
                val boundary = p.param("content-type", "boundary") ?: return
                val parts = splitMultipart(p.body, boundary).map(::part)
                if (type == "multipart/alternative") {
                    val best = parts.firstOrNull { it.type == "text/plain" && decodeBody(it).isNotBlank() } ?: parts.lastOrNull { it.type == "text/html" } ?: parts.firstOrNull()
                    // Attachments inside alternatives are rare; nested multiparts (related) are followed.
                    best?.let { collect(it, texts, attachments, depth + 1) }
                } else parts.forEach { collect(it, texts, attachments, depth + 1) }
            }
            disposition.startsWith("attachment") || fileName != null && !type.startsWith("text/") -> {
                val size = transferDecoded(p).size
                attachments += "${fileName ?: "Anhang"} (${kb(size)})"
            }
            type == "message/rfc822" -> {
                val inner = part(p.body)
                attachments += "weitergeleitete Nachricht „${decodeWords(inner.header("subject"))}“"
                texts += "--- Weitergeleitete Nachricht ---\n" + eml(p.body)
            }
            type == "text/plain" -> decodeBody(p).takeIf { it.isNotBlank() }?.let(texts::add)
            type == "text/html" -> WebTools.htmlToText(decodeBody(p)).takeIf { it.isNotBlank() }?.let(texts::add)
        }
    }

    private fun kb(size: Int) = if (size < 1024) "$size B" else "${size / 1024} KB"

    private fun splitMultipart(body: ByteArray, boundary: String): List<ByteArray> {
        val raw = String(body, Charsets.ISO_8859_1)
        val marker = "--$boundary"
        val out = mutableListOf<ByteArray>()
        var pos = raw.indexOf(marker)
        while (pos >= 0) {
            var start = pos + marker.length
            if (raw.startsWith("--", start)) break
            start = raw.indexOf('\n', start).let { if (it < 0) raw.length else it + 1 }
            val next = raw.indexOf("\n$marker", start).let { if (it < 0) raw.length else it }
            val end = if (next > start && raw[next - 1] == '\r') next - 1 else next
            out += raw.substring(start, maxOf(start, end)).toByteArray(Charsets.ISO_8859_1)
            pos = if (next >= raw.length) -1 else next + 1
        }
        return out
    }

    private fun transferDecoded(p: Part): ByteArray = when (p.header("content-transfer-encoding").trim().lowercase()) {
        "base64" -> runCatching { Base64.getMimeDecoder().decode(String(p.body, Charsets.ISO_8859_1).replace(Regex("[^A-Za-z0-9+/=]"), "")) }.getOrDefault(p.body)
        "quoted-printable" -> quotedPrintable(String(p.body, Charsets.ISO_8859_1), underscore = false)
        else -> p.body
    }

    private fun decodeBody(p: Part): String {
        val bytes = transferDecoded(p)
        val cs = p.param("content-type", "charset")?.let { runCatching { Charset.forName(it) }.getOrNull() }
        return (if (cs != null) String(bytes, cs) else TextCodec.text(bytes)).replace("\r\n", "\n")
    }

    internal fun quotedPrintable(s: String, underscore: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        val text = s.replace(Regex("=\r?\n"), "")
        while (i < text.length) {
            val c = text[i]
            if (c == '=' && i + 2 < text.length && text[i + 1].isHex() && text[i + 2].isHex()) {
                out.write(text.substring(i + 1, i + 3).toInt(16))
                i += 3
                continue
            }
            out.write(if (underscore && c == '_') ' '.code else c.code and 0xFF)
            i++
        }
        return out.toByteArray()
    }

    private fun Char.isHex() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    /** Encoded words (RFC 2047) like =?UTF-8?Q?Gr=C3=BC=C3=9Fe?= in headers. */
    fun decodeWords(s: String): String = s.replace(Regex("\\?=\\s+=\\?"), "?==?").replace(Regex("=\\?([^?]+)\\?([bBqQ])\\?([^?]*)\\?=")) { m ->
        val cs = runCatching { Charset.forName(m.groupValues[1].substringBefore('*')) }.getOrDefault(Charsets.UTF_8)
        val bytes = if (m.groupValues[2].equals("b", true)) runCatching { Base64.getDecoder().decode(m.groupValues[3]) }.getOrDefault(ByteArray(0))
        else quotedPrintable(m.groupValues[3], underscore = true)
        String(bytes, cs)
    }.trim().removeSurrounding("\"")

    private fun decodeRfc2231(v: String): String {
        val cs = runCatching { Charset.forName(v.substringBefore("'")) }.getOrDefault(Charsets.UTF_8)
        return runCatching { java.net.URLDecoder.decode(v.substringAfter("''").replace("+", "%2B"), cs.name()) }.getOrDefault(v)
    }

    // ------------------------------------------------------------------ Outlook .msg

    fun msg(bytes: ByteArray): String {
        val cfb = Cfb.open(bytes)
        fun prop(storage: Cfb.Entry, id: String): String? {
            cfb.child(storage, "__substg1.0_${id}001F")?.let { return String(cfb.read(it), Charsets.UTF_16LE).trimEnd('\u0000') }
            cfb.child(storage, "__substg1.0_${id}001E")?.let { return String(cfb.read(it), Charset.forName("windows-1252")).trimEnd('\u0000') }
            return null
        }
        val root = cfb.root
        val subject = prop(root, "0037")
        val senderName = prop(root, "0C1A")
        val senderMail = prop(root, "5D01") ?: prop(root, "0C1F")?.takeIf { '@' in it }
        val attachments = cfb.children(root).filter { it.isStorage && it.name.startsWith("__attach_version1.0_") }.mapNotNull { a ->
            val name = prop(a, "3707") ?: prop(a, "3704") ?: prop(a, "3001") ?: return@mapNotNull "Anhang"
            val size = cfb.child(a, "__substg1.0_37010102")?.size
            if (size != null) "$name (${kb(size.toInt())})" else name
        }
        val body = prop(root, "1000")?.takeIf { it.isNotBlank() }
            ?: cfb.child(root, "__substg1.0_10130102")?.let { html -> WebTools.htmlToText(TextCodec.text(cfb.read(html))) }
            ?: if (cfb.child(root, "__substg1.0_10090102") != null) "[Der Text liegt nur als komprimiertes RTF vor und kann nicht gelesen werden.]" else ""
        return buildString {
            fun line(label: String, value: String?) { if (!value.isNullOrBlank()) append(label).append(": ").append(value.trim()).append('\n') }
            line("Von", listOfNotNull(senderName, senderMail?.let { "<$it>" }).joinToString(" "))
            line("An", prop(root, "0E04"))
            line("Cc", prop(root, "0E03"))
            line("Datum", sentAt(cfb))
            line("Betreff", subject)
            if (attachments.isNotEmpty()) line("Anhänge", attachments.joinToString(", "))
            append('\n')
            append(body.replace("\r\n", "\n").trim())
        }.trim()
    }

    /** Client submit time (0x0039) or delivery time (0x0E06) from the fixed-size property stream. */
    private fun sentAt(cfb: Cfb): String? {
        val props = cfb.stream("__properties_version1.0") ?: return null
        val b = ByteBuffer.wrap(props).order(ByteOrder.LITTLE_ENDIAN)
        var found: Long? = null
        var off = 32
        while (off + 16 <= props.size) {
            val tag = b.getInt(off)
            val type = tag and 0xFFFF
            val id = tag ushr 16
            if (type == 0x0040 && (id == 0x0039 || id == 0x0E06 && found == null)) found = b.getLong(off + 8)
            off += 16
        }
        val ft = found ?: return null
        val instant = Instant.ofEpochSecond(ft / 10_000_000 - 11_644_473_600L)
        return instant.atZone(ZoneId.of("Europe/Berlin")).format(GERMAN_DATE)
    }
}
