package io.github.thepytales.llmhub.net

import io.github.thepytales.llmhub.ui.tr
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * A small IMAP4rev1 client (AP-27): enough for reading folders and mails, moving them, creating folders and saving drafts
 * (APPEND) – the server path of Mail & Kalender. Deliberately without DELETE/EXPUNGE of other mails and without SMTP:
 * the app never deletes and never sends.
 */
class ImapConnection(input: InputStream, private val output: OutputStream, private val closeable: Closeable? = null) : Closeable {
    private val input = BufferedInputStream(input)
    private var tag = 0
    var capabilities: Set<String> = emptySet()
        private set

    /** One response line with its literals ({n} parts) in order; the text has „\u0000n“ where literal n was. */
    class Line(val text: String, val literals: List<ByteArray>) {
        fun items(): List<Any?> = ImapParser.parse(text, literals)
    }

    class Response(val tag: String, val status: String, val text: String, val untagged: List<Line>) {
        val ok: Boolean get() = status == "OK"
    }

    private fun readRawLine(): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) {
                if (out.size() == 0) throw IOException(tr("Der Mailserver hat die Verbindung beendet."))
                break
            }
            if (b == '\n'.code) break
            out.write(b)
        }
        val bytes = out.toByteArray()
        return if (bytes.isNotEmpty() && bytes.last() == '\r'.code.toByte()) bytes.copyOf(bytes.size - 1) else bytes
    }

    /** Reads a whole logical line, including the literals it announces. */
    fun readLine(): Line {
        val text = StringBuilder()
        val literals = mutableListOf<ByteArray>()
        while (true) {
            val part = String(readRawLine(), Charsets.UTF_8)
            val m = LITERAL.find(part)
            if (m == null) {
                text.append(part)
                break
            }
            text.append(part.substring(0, m.range.first)).append('\u0000').append(literals.size)
            val n = m.groupValues[1].toInt()
            val buf = ByteArray(n)
            var read = 0
            while (read < n) {
                val r = input.read(buf, read, n - read)
                if (r < 0) throw IOException(tr("Der Mailserver hat die Verbindung mitten in einer Nachricht beendet."))
                read += r
            }
            literals += buf
        }
        return Line(text.toString(), literals)
    }

    fun greeting() {
        val l = readLine()
        if (!l.text.startsWith("* OK") && !l.text.startsWith("* PREAUTH")) throw IOException(tr("Unerwartete Begrüßung des Mailservers: {0}", l.text.take(120)))
        capabilityFrom(l.text)
    }

    private fun capabilityFrom(text: String) {
        CAPABILITY.find(text)?.let { capabilities = it.groupValues[1].uppercase().split(' ').toSet() }
    }

    private fun send(bytes: ByteArray) {
        output.write(bytes)
        output.flush()
    }

    /** Sends a command and reads until its tagged answer. [literal]: data for a trailing {n} (APPEND), sent after „+“. */
    fun command(command: String, literal: ByteArray? = null): Response {
        val t = "A" + (++tag)
        val untagged = mutableListOf<Line>()
        if (literal == null) send("$t $command\r\n".toByteArray(Charsets.UTF_8))
        else {
            send("$t $command {${literal.size}}\r\n".toByteArray(Charsets.UTF_8))
            while (true) {
                val l = readLine()
                if (l.text.startsWith("+")) break
                if (l.text.startsWith("$t ")) return finish(t, l, untagged)
                untagged += l
            }
            send(literal + "\r\n".toByteArray())
        }
        while (true) {
            val l = readLine()
            if (l.text.startsWith("$t ")) return finish(t, l, untagged)
            if (l.text.startsWith("* CAPABILITY ", ignoreCase = true)) capabilities = l.text.substring(13).uppercase().split(' ').toSet()
            untagged += l
        }
    }

    private fun finish(t: String, l: Line, untagged: List<Line>): Response {
        val rest = l.text.removePrefix("$t ")
        val status = rest.substringBefore(' ').uppercase()
        capabilityFrom(rest)
        return Response(t, status, rest.substringAfter(' ', ""), untagged)
    }

    /** Like [command], but a NO or BAD becomes an [IOException] with the server's words. */
    fun ok(command: String, literal: ByteArray? = null, what: String = command.substringBefore(' ')): Response {
        val r = command(command, literal)
        if (!r.ok) throw IOException("Der Mailserver lehnt „$what“ ab: ${r.text.take(200)}")
        return r
    }

    /**
     * A command with several literals, e.g. a SEARCH for words with umlauts (AP-69): [parts] is the text around them (one
     * more than [literals]); after each announced literal ({n}) the server's „+“ is awaited. A NO or BAD becomes an
     * [IOException].
     */
    fun ok(parts: List<String>, literals: List<ByteArray>, what: String): Response {
        require(parts.size == literals.size + 1)
        val t = "A" + (++tag)
        val untagged = mutableListOf<Line>()
        var text = "$t " + parts[0]
        for ((i, lit) in literals.withIndex()) {
            send("$text{${lit.size}}\r\n".toByteArray(Charsets.UTF_8))
            while (true) {
                val l = readLine()
                if (l.text.startsWith("+")) break
                if (l.text.startsWith("$t ")) return finish(t, l, untagged).also { if (!it.ok) throw IOException("Der Mailserver lehnt „$what“ ab: ${it.text.take(200)}") }
                untagged += l
            }
            send(lit)
            text = parts[i + 1]
        }
        send("$text\r\n".toByteArray(Charsets.UTF_8))
        while (true) {
            val l = readLine()
            if (l.text.startsWith("$t ")) {
                val r = finish(t, l, untagged)
                if (!r.ok) throw IOException("Der Mailserver lehnt „$what“ ab: ${r.text.take(200)}")
                return r
            }
            untagged += l
        }
    }

    /**
     * Sends [commands] at once and then reads their answers, in the order of [commands] (STATUS of many folders without
     * a round trip each, AP-69). Untagged lines belong to the answer that ends after them.
     */
    fun pipeline(commands: List<String>): List<Response> {
        if (commands.isEmpty()) return emptyList()
        val tags = commands.map { "A" + (++tag) }
        send(commands.indices.joinToString("") { "${tags[it]} ${commands[it]}\r\n" }.toByteArray(Charsets.UTF_8))
        val done = HashMap<String, Response>()
        var untagged = mutableListOf<Line>()
        while (done.size < tags.size) {
            val l = readLine()
            val t = tags.firstOrNull { it !in done && l.text.startsWith("$it ") }
            if (t == null) untagged += l
            else {
                done[t] = finish(t, l, untagged)
                untagged = mutableListOf()
            }
        }
        return tags.map { done.getValue(it) }
    }

    fun login(user: String, password: String) {
        if (capabilities.isEmpty()) ok("CAPABILITY")
        val r = command("LOGIN ${ImapParser.quote(user)} ${ImapParser.quote(password)}")
        if (!r.ok) throw IOException(tr("Der Mailserver lehnt die Anmeldung ab. Benutzername und Passwort prüfen (Einstellungen › Verbindungen › Mail über den Server)."))
        if (!r.text.contains("CAPABILITY", ignoreCase = true)) ok("CAPABILITY")
    }

    fun has(capability: String) = capability.uppercase() in capabilities

    override fun close() {
        runCatching { send("Z LOGOUT\r\n".toByteArray()) }
        runCatching { closeable?.close() }
    }

    companion object {
        // Every brace and bracket meant literally is escaped: Android's regex engine (ICU) rejects a bare „}“ after a
        // quantifier (\{(\d+)\+?}$ failed with „Syntax error in regexp pattern near index 11“), the desktop JVM accepts it.
        private val LITERAL = Regex("\\{(\\d+)\\+?\\}$")
        private val CAPABILITY = Regex("\\[CAPABILITY ([^\\]]+)\\]", RegexOption.IGNORE_CASE)

        /** Opens a TLS connection (port 993) or, with [startTls], a plain one that switches to TLS (port 143). */
        fun open(host: String, port: Int, startTls: Boolean = port == 143, timeoutMs: Int = 20_000): ImapConnection {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            if (!startTls) {
                val s = factory.createSocket() as SSLSocket
                s.connect(InetSocketAddress(host, port), timeoutMs)
                s.soTimeout = 60_000
                s.startHandshake()
                return ImapConnection(s.inputStream, s.outputStream, s).apply { greeting() }
            }
            val plain = Socket()
            plain.connect(InetSocketAddress(host, port), timeoutMs)
            plain.soTimeout = 60_000
            val c = ImapConnection(plain.inputStream, plain.outputStream, plain)
            c.greeting()
            c.ok("STARTTLS")
            val s = factory.createSocket(plain, host, port, true) as SSLSocket
            s.startHandshake()
            return ImapConnection(s.inputStream, s.outputStream, s).apply { ok("CAPABILITY") }
        }
    }
}

/** Parses IMAP data: atoms, "quoted" strings, literals, NIL and (lists). */
object ImapParser {
    fun parse(text: String, literals: List<ByteArray> = emptyList()): List<Any?> {
        var i = 0
        fun list(): MutableList<Any?> {
            val out = mutableListOf<Any?>()
            while (i < text.length) {
                val ch = text[i]
                when {
                    ch == ' ' -> i++
                    ch == '(' -> { i++; out.add(list()) }
                    ch == ')' -> { i++; return out }
                    ch == '"' -> {
                        val sb = StringBuilder()
                        i++
                        while (i < text.length && text[i] != '"') {
                            if (text[i] == '\\' && i + 1 < text.length) i++
                            sb.append(text[i]); i++
                        }
                        i++
                        out += sb.toString()
                    }
                    ch == '\u0000' -> {
                        i++
                        val start = i
                        while (i < text.length && text[i].isDigit()) i++
                        out += literals.getOrNull(text.substring(start, i).toInt())
                    }
                    else -> {
                        val start = i
                        // An atom; „BODY[HEADER.FIELDS (FROM)]“ keeps its brackets with the spaces inside.
                        var depth = 0
                        while (i < text.length) {
                            val c = text[i]
                            if (c == '[') depth++
                            if (c == ']') depth--
                            if (depth <= 0 && (c == ' ' || c == '(' || c == ')')) { if (c == '(' && depth < 0) depth = 0; break }
                            i++
                        }
                        val atom = text.substring(start, i)
                        out += if (atom.equals("NIL", ignoreCase = true)) null else atom
                    }
                }
            }
            return out
        }
        return list()
    }

    fun quote(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /** The text of an item: a string, a literal decoded as UTF-8, or null. */
    fun text(v: Any?): String? = when (v) {
        is String -> v
        is ByteArray -> String(v, Charsets.UTF_8)
        else -> null
    }

    /** Values of a FETCH answer („* 12 FETCH (UID 5 FLAGS (\Seen) …)“) by name, upper case. */
    fun fetch(line: ImapConnection.Line): Map<String, Any?>? {
        val items = line.items()
        if (items.size < 4 || items[0] != "*" || (items[2] as? String)?.uppercase() != "FETCH") return null
        val l = items[3] as? List<*> ?: return null
        val out = linkedMapOf<String, Any?>()
        var k = 0
        while (k + 1 < l.size) {
            val name = (l[k] as? String)?.uppercase() ?: break
            out[name] = l[k + 1]
            k += 2
        }
        return out
    }

    /** UIDs as a short set: 1, 2, 3, 7 → „1:3,7“ (any order in, ascending ranges out). */
    fun uidSet(uids: Collection<Long>): String {
        val sorted = uids.toSortedSet().toList()
        if (sorted.isEmpty()) return ""
        val out = StringBuilder()
        var start = sorted[0]
        var prev = start
        fun flush() {
            if (out.isNotEmpty()) out.append(',')
            out.append(start)
            if (prev != start) out.append(':').append(prev)
        }
        for (u in sorted.drop(1)) {
            if (u == prev + 1) prev = u else {
                flush()
                start = u
                prev = u
            }
        }
        flush()
        return out.toString()
    }

    /** The UIDs of a set as the server writes it („4:6,9“ → 4, 5, 6, 9), in its order; ranges may run downwards. */
    fun uids(set: String): List<Long> = set.split(',').filter { it.isNotBlank() }.flatMap { part ->
        val a = part.substringBefore(':').trim().toLongOrNull() ?: return@flatMap emptyList()
        val b = part.substringAfter(':', "").trim().toLongOrNull() ?: return@flatMap listOf(a)
        if (a <= b) (a..b).toList() else (a downTo b).toList()
    }

    /**
     * How many attachments a BODYSTRUCTURE shows: parts with disposition „attachment“, other non-text parts with a file
     * name, and forwarded messages – the same parts the mail's text lists as „Anhänge“ (MailText).
     */
    fun attachmentCount(structure: Any?, depth: Int = 0): Int {
        val l = structure as? List<*> ?: return 0
        if (depth > 12) return 0
        // Multipart: its parts (lists) come first, then the subtype.
        if (l.firstOrNull() is List<*>) return l.takeWhile { it is List<*> }.sumOf { attachmentCount(it, depth + 1) }
        val type = (l.getOrNull(0) as? String)?.lowercase() ?: return 0
        val sub = (l.getOrNull(1) as? String)?.lowercase().orEmpty()
        if (type == "message" && sub == "rfc822") return 1
        fun named(params: Any?, key: String) = (params as? List<*>)?.chunked(2)?.any { (it.getOrNull(0) as? String)?.lowercase()?.removeSuffix("*") == key } == true
        // The disposition („attachment“ or „inline“ with its parameters) is in the extension data after the size.
        val disposition = l.drop(7).filterIsInstance<List<*>>().firstOrNull { (it.firstOrNull() as? String)?.lowercase() in setOf("attachment", "inline") }
        val kind = (disposition?.firstOrNull() as? String)?.lowercase()
        return when {
            kind == "attachment" -> 1
            type != "text" && (named(l.getOrNull(2), "name") || named(disposition?.getOrNull(1), "filename")) -> 1
            else -> 0
        }
    }

    // --- Modified UTF-7 of folder names (RFC 3501 5.1.3): „Entw&APw-rfe“ = „Entwürfe“. ---

    fun decodeFolder(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '&') { out.append(c); i++; continue }
            val end = s.indexOf('-', i)
            if (end < 0) { out.append(s.substring(i)); break }
            if (end == i + 1) out.append('&')
            else {
                val b64 = s.substring(i + 1, end).replace(',', '/')
                val padded = b64 + "=".repeat((4 - b64.length % 4) % 4)
                val bytes = runCatching { java.util.Base64.getDecoder().decode(padded) }.getOrNull()
                if (bytes == null) out.append(s, i, end + 1) else out.append(String(bytes, Charsets.UTF_16BE))
            }
            i = end + 1
        }
        return out.toString()
    }

    fun encodeFolder(s: String): String {
        val out = StringBuilder()
        val pending = StringBuilder()
        fun flush() {
            if (pending.isEmpty()) return
            val b64 = java.util.Base64.getEncoder().withoutPadding().encodeToString(pending.toString().toByteArray(Charsets.UTF_16BE)).replace('/', ',')
            out.append('&').append(b64).append('-')
            pending.clear()
        }
        for (c in s) {
            if (c.code in 0x20..0x7e) {
                flush()
                if (c == '&') out.append("&-") else out.append(c)
            } else pending.append(c)
        }
        flush()
        return out.toString()
    }
}
