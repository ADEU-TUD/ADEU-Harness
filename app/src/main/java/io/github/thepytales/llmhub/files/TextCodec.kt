package io.github.thepytales.llmhub.files

import java.io.File
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Encoding and line endings of a text file, so that edits write it back the way it was: UTF-8 (with or without
 * BOM), UTF-16 with BOM or Windows-1252 for older files; CRLF (Windows) or LF.
 */
data class TextStyle(val charset: Charset, val bom: Boolean, val crlf: Boolean) {
    /** "UTF-8 mit BOM, CRLF" for messages. */
    val label: String
        get() = buildString {
            append(if (charset.name().equals("windows-1252", true)) "Windows-1252 (ANSI)" else charset.displayName())
            if (bom) append(" mit BOM")
            append(if (crlf) ", Windows-Zeilenenden (CRLF)" else ", LF-Zeilenenden")
        }

    companion object {
        /** New files: UTF-8 without BOM and LF, as before. */
        val DEFAULT = TextStyle(Charsets.UTF_8, bom = false, crlf = false)
    }
}

/** Text of a file with "\n" line endings and the style it was stored in. */
data class DecodedText(val text: String, val style: TextStyle)

object TextCodec {
    private val CP1252: Charset = Charset.forName("windows-1252")

    fun read(file: File): DecodedText = decode(file.readBytes())

    /** Decodes [bytes]: BOM first, then strict UTF-8, else Windows-1252. Line endings become "\n". */
    fun decode(bytes: ByteArray): DecodedText {
        val (charset, bom, offset) = when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> Triple(Charsets.UTF_8, true, 3)
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Triple(Charsets.UTF_16LE, true, 2)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Triple(Charsets.UTF_16BE, true, 2)
            isUtf8(bytes) -> Triple(Charsets.UTF_8, false, 0)
            else -> Triple(CP1252, false, 0)
        }
        val raw = String(bytes, offset, bytes.size - offset, charset)
        val crlfCount = Regex("\r\n").findAll(raw).count()
        val lfCount = raw.count { it == '\n' } - crlfCount
        val text = raw.replace("\r\n", "\n")
        return DecodedText(text, TextStyle(charset, bom, crlf = crlfCount > 0 && crlfCount >= lfCount))
    }

    /** Text of [bytes] only, for reading. */
    fun text(bytes: ByteArray): String = decode(bytes).text

    private fun isUtf8(bytes: ByteArray): Boolean = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
        true
    } catch (e: CharacterCodingException) {
        false
    }

    /** Whether [text] can be stored in [charset] without losing characters. */
    fun fits(text: String, charset: Charset): Boolean =
        charset == Charsets.UTF_8 || charset == Charsets.UTF_16LE || charset == Charsets.UTF_16BE || charset.newEncoder().canEncode(CharBuffer.wrap(text))

    /** Bytes of [text] ("\n" line endings) in [style]. */
    fun encode(text: String, style: TextStyle): ByteArray {
        val body = text.replace("\r\n", "\n").let { if (style.crlf) it.replace("\n", "\r\n") else it }
        val bytes = body.toByteArray(style.charset)
        val bom = when {
            !style.bom -> ByteArray(0)
            style.charset == Charsets.UTF_8 -> byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
            style.charset == Charsets.UTF_16LE -> byteArrayOf(0xFF.toByte(), 0xFE.toByte())
            style.charset == Charsets.UTF_16BE -> byteArrayOf(0xFE.toByte(), 0xFF.toByte())
            else -> ByteArray(0)
        }
        return bom + bytes
    }

    /**
     * Writes [text] in [style]; if the text has characters the old encoding (Windows-1252) lacks, UTF-8 with BOM is
     * used instead. Returns the style actually written.
     */
    fun write(file: File, text: String, style: TextStyle): TextStyle {
        val used = if (fits(text, style.charset)) style else style.copy(charset = Charsets.UTF_8, bom = true)
        file.writeBytes(encode(text, used))
        return used
    }

    /** Whether the first bytes look like text (no NUL bytes, except in UTF-16 files with BOM). */
    fun looksLikeText(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        if (bytes.size >= 2 && (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() || bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())) return true
        val n = minOf(bytes.size, 4096)
        var control = 0
        for (i in 0 until n) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0) return false
            if (b < 9 || b in 14..31) control++
        }
        return control * 20 < n
    }
}
