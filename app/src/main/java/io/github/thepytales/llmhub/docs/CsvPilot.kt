package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.ui.tr
import io.github.thepytales.llmhub.ui.trLater
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * CSV-Lotse (AP-93): recognises how a CSV or text table was written – encoding (UTF-8, Windows-1252, UTF-16, umlauts
 * broken by a wrong conversion like „Ã¤“), separator, decimal sign, thousands separator, date format and title lines
 * above the table – and reads it into rows the sheet understands without ambiguity: numbers as „1234,5“ (German
 * notation without thousands separators, so „1.234“ stays one thousand two hundred thirty-four in a German file and
 * becomes 1,234 in an English one), dates as „03.04.2026“. Also writes CSV in two flavours: Excel-tauglich (semicolon,
 * BOM, decimal comma) and Standard (comma, UTF-8, decimal point). Exports of Excel, OPAL, LimeSurvey and Selma are
 * the typical cases (CsvPilotTest).
 */
object CsvPilot {
    /** Encodings offered in the preview. */
    enum class Encoding(val charset: String, val label: String) {
        UTF8("UTF-8", "UTF-8"),
        WINDOWS_1252("windows-1252", trLater("Windows (ANSI, Windows-1252)")),
        UTF16LE("UTF-16LE", "UTF-16 (Unicode-Text)"),
        UTF16BE("UTF-16BE", "UTF-16 BE"),
        ISO_8859_15("ISO-8859-15", "ISO-8859-15"),
    }

    /** How the dates of the file are written (only the ambiguous slash notation needs a choice). */
    enum class DateStyle(val label: String) {
        DMY(trLater("Tag.Monat.Jahr (31.12.2026, 31/12/2026)")),
        MDY(trLater("Monat/Tag/Jahr (12/31/2026, US)")),
        ISO(trLater("Jahr-Monat-Tag (2026-12-31)")),
    }

    data class Settings(
        val encoding: Encoding = Encoding.UTF8,
        /** Umlauts broken by a double conversion („Ã¤“ instead of „ä“) are repaired. */
        val repair: Boolean = false,
        val separator: Char = ';',
        val decimal: Char = ',',
        /** Thousands separator of numbers, null if the file has none. */
        val thousands: Char? = '.',
        val dates: DateStyle = DateStyle.DMY,
        /** Lines above the table (title, export date …) that are left out. */
        val skip: Int = 0,
    )

    /** What [detect] found, with a line per finding for the preview („Kodierung: Windows-1252“). */
    data class Detected(val settings: Settings, val notes: List<String>)

    val SEPARATORS = listOf(';', ',', '\t', '|')

    fun separatorLabel(c: Char): String = when (c) {
        ';' -> tr("Semikolon ( ; )")
        ',' -> tr("Komma ( , )")
        '\t' -> tr("Tabulator")
        '|' -> tr("Senkrechter Strich ( | )")
        else -> "„$c“"
    }

    // ── Encoding ──

    private val MOJIBAKE = Regex("Ã[¤¶¼„–œŸ©¨ ¡]|â‚¬|â€[žœ“™\u009d]|Ã\u009f")

    /** Whether [text] has umlauts broken by reading UTF-8 as Windows-1252 („Ã¤“, „â‚¬“). */
    fun hasMojibake(text: String) = MOJIBAKE.containsMatchIn(text)

    /** Undoes one wrong conversion UTF-8 → Windows-1252 (repeated once for twice broken texts); unchanged if it cannot. */
    fun repair(text: String): String {
        var t = text
        repeat(2) {
            if (!hasMojibake(t)) return t
            val bytes = runCatching { encodeStrict(t, Charset.forName("windows-1252")) }.getOrNull() ?: return repairPieces(t)
            t = decodeStrict(bytes, Charsets.UTF_8) ?: return repairPieces(t)
        }
        return t
    }

    /** Repairs only the broken pieces when the whole text cannot be converted back (mixed texts). */
    private fun repairPieces(text: String): String = Regex("(?:[ÃÂâ][\u0080-ÿ€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]{1,2})+").replace(text) { m ->
        val bytes = runCatching { encodeStrict(m.value, Charset.forName("windows-1252")) }.getOrNull() ?: return@replace m.value
        decodeStrict(bytes, Charsets.UTF_8) ?: m.value
    }

    private fun encodeStrict(text: String, cs: Charset): ByteArray {
        val enc = cs.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val buf = enc.encode(java.nio.CharBuffer.wrap(text))
        return ByteArray(buf.remaining()).also { buf.get(it) }
    }

    private fun decodeStrict(bytes: ByteArray, cs: Charset): String? = try {
        cs.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun bom(bytes: ByteArray): Pair<Encoding, Int>? = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> Encoding.UTF8 to 3
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Encoding.UTF16LE to 2
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Encoding.UTF16BE to 2
        else -> null
    }

    /** Encoding of [bytes]: byte order mark, then UTF-16 without mark (every other byte zero), valid UTF-8, else Windows. */
    fun encodingOf(bytes: ByteArray): Encoding {
        bom(bytes)?.let { return it.first }
        val sample = bytes.copyOf(minOf(bytes.size, 4000))
        if (sample.size >= 8) {
            val evenZero = sample.indices.count { it % 2 == 0 && sample[it] == 0.toByte() }
            val oddZero = sample.indices.count { it % 2 == 1 && sample[it] == 0.toByte() }
            if (oddZero > sample.size / 4 && evenZero < sample.size / 20) return Encoding.UTF16LE
            if (evenZero > sample.size / 4 && oddZero < sample.size / 20) return Encoding.UTF16BE
        }
        return if (decodeStrict(bytes, Charsets.UTF_8) != null) Encoding.UTF8 else Encoding.WINDOWS_1252
    }

    /** The text of [bytes] as [settings] say (byte order mark removed, broken umlauts repaired if asked). */
    fun decode(bytes: ByteArray, settings: Settings): String {
        val skip = bom(bytes)?.takeIf { it.first == settings.encoding }?.second ?: 0
        val text = String(bytes, skip, bytes.size - skip, Charset.forName(settings.encoding.charset))
        return if (settings.repair) repair(text) else text
    }

    // ── Separator and rows ──

    /** Splits [text] into rows of cells; quoted cells may hold separators, quotes ("") and line breaks. */
    fun split(text: String, sep: Char): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var wasQuoted = false
        var i = 0
        val n = text.length
        while (i < n) {
            val ch = text[i]
            when {
                quoted && ch == '"' && i + 1 < n && text[i + 1] == '"' -> { cell.append('"'); i++ }
                quoted && ch == '"' -> quoted = false
                quoted -> cell.append(ch)
                ch == '"' && cell.isBlank() && !wasQuoted -> { quoted = true; wasQuoted = true; cell.setLength(0) }
                ch == sep -> { row += if (wasQuoted) cell.toString() else cell.toString().trim(); cell.setLength(0); wasQuoted = false }
                ch == '\r' || ch == '\n' -> {
                    if (ch == '\r' && i + 1 < n && text[i + 1] == '\n') i++
                    row += if (wasQuoted) cell.toString() else cell.toString().trim()
                    cell.setLength(0)
                    wasQuoted = false
                    rows += row
                    row = ArrayList()
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty() || wasQuoted) {
            row += if (wasQuoted) cell.toString() else cell.toString().trim()
            rows += row
        }
        // Empty lines (also at the end) are no rows.
        return rows.filter { r -> !(r.size == 1 && r[0].isEmpty()) }
    }

    /**
     * Separator of [text]: the candidate that splits the lines most evenly into more than one cell; the number of
     * lines above the table (title lines with fewer cells) comes along.
     */
    fun separatorOf(text: String): Pair<Char, Int> {
        val head = text.lineSequence().take(60).joinToString("\n")
        var best: Triple<Char, Double, Int>? = null
        for (sep in SEPARATORS) {
            val counts = split(head, sep).map { it.size }
            if (counts.isEmpty()) continue
            val mode = counts.groupingBy { it }.eachCount().filterKeys { it > 1 }.maxWithOrNull(compareBy({ it.value }, { it.key }))?.key ?: continue
            // Title lines: the leading rows with another number of cells.
            val skip = counts.indexOfFirst { it == mode }.coerceAtLeast(0)
            val rest = counts.drop(skip)
            val even = rest.count { it == mode }.toDouble() / rest.size
            val score = even * 10 + minOf(mode, 30) * 0.1
            if (best == null || score > best.second) best = Triple(sep, score, skip)
        }
        return best?.let { it.first to it.third } ?: (';' to 0)
    }

    // ── Numbers and dates ──

    private val DE_THOUSANDS = Regex("^[-+]?\\d{1,3}(\\.\\d{3})+(,\\d+)?$")
    private val EN_THOUSANDS = Regex("^[-+]?\\d{1,3}(,\\d{3})+(\\.\\d+)?$")
    private val COMMA_DECIMAL = Regex("^[-+]?\\d*,\\d+$")
    private val DOT_DECIMAL = Regex("^[-+]?\\d*\\.\\d+$")
    private val SLASH_DATE = Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{2}|\\d{4})(?:[ T,]+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?$")
    private val DOT_DATE = Regex("^(\\d{1,2})\\.(\\d{1,2})\\.(\\d{2}|\\d{4})(?:[ ,]+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?$")
    private val ISO_DATE = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})(?:[ T](\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?:\\.\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})?)?$")

    /** Decimal sign and thousands separator from the number cells of [rows]. */
    private fun numberStyle(rows: List<List<String>>, sep: Char): Pair<Char, Char?> {
        var comma = 0
        var dot = 0
        var deThousands = 0
        var enThousands = 0
        for (r in rows) for (raw in r) {
            val c = cleanNumber(raw) ?: continue
            when {
                DE_THOUSANDS.matches(c) && ',' in c -> { comma += 3; deThousands += 3 }
                EN_THOUSANDS.matches(c) && '.' in c -> { dot += 3; enThousands += 3 }
                COMMA_DECIMAL.matches(c) -> comma += if (c.substringAfter(',').length == 3) 1 else 2
                DOT_DECIMAL.matches(c) -> dot += if (c.substringAfter('.').length == 3) 1 else 2
                DE_THOUSANDS.matches(c) -> deThousands++
                EN_THOUSANDS.matches(c) -> enThousands++
            }
        }
        val decimal = when {
            comma > dot -> ','
            dot > comma -> '.'
            sep == ',' -> '.'
            else -> ','
        }
        val thousands = if (decimal == ',') '.'.takeIf { deThousands > 0 } else ','.takeIf { enThousands > 0 && sep != ',' || enThousands > 2 }
        return decimal to thousands
    }

    /** A cell without currency, percent and spaces if it could be a number, else null. */
    private fun cleanNumber(raw: String): String? {
        val t = raw.trim().removeSuffix("%").removeSuffix("€").removeSuffix("EUR").removePrefix("€").trim()
            .replace(" ", "").replace(" ", "")
        val c = if (Regex("^[-+]?\\d{1,3}( \\d{3})+([.,]\\d+)?$").matches(t)) t.replace(" ", "") else t
        return c.takeIf { it.isNotEmpty() && it.any(Char::isDigit) && Regex("^[-+]?[\\d.,]+$").matches(it) }
    }

    private fun dateStyle(rows: List<List<String>>): DateStyle {
        var dmy = 0
        var mdy = 0
        var iso = 0
        for (r in rows) for (c in r) {
            val t = c.trim()
            SLASH_DATE.find(t)?.let { m ->
                val a = m.groupValues[1].toInt()
                val b = m.groupValues[2].toInt()
                if (a > 12 && b <= 12) dmy += 2 else if (b > 12 && a <= 12) mdy += 2
            }
            if (DOT_DATE.matches(t)) dmy++
            if (ISO_DATE.matches(t)) iso++
        }
        return when {
            mdy > dmy -> DateStyle.MDY
            iso > dmy && iso > mdy -> DateStyle.ISO
            else -> DateStyle.DMY
        }
    }

    /** Finds the settings of [bytes]; [hint] is the file name (".tsv" means tabs). */
    fun detect(bytes: ByteArray, hint: String = ""): Detected {
        val encoding = encodingOf(bytes)
        val plain = decode(bytes, Settings(encoding = encoding))
        val broken = hasMojibake(plain)
        val text = if (broken) repair(plain) else plain
        val (sep, skip) = if (hint.endsWith(".tsv", true)) '\t' to 0 else separatorOf(text)
        val rows = split(text, sep).drop(skip).take(400)
        val (decimal, thousands) = numberStyle(rows.drop(1), sep)
        val dates = dateStyle(rows)
        val s = Settings(encoding, broken, sep, decimal, thousands, dates, skip)
        val notes = buildList {
            add(tr("Kodierung: {0}", tr(encoding.label)) + if (broken) " – " + tr("kaputte Umlaute („Ã¤“) werden repariert") else "")
            add(tr("Trennzeichen: {0}", separatorLabel(sep)))
            add(tr("Zahlen: {0}", numberExample(decimal, thousands)))
            if (rows.any { r -> r.any { c -> SLASH_DATE.matches(c.trim()) || DOT_DATE.matches(c.trim()) || ISO_DATE.matches(c.trim()) } }) add(tr("Datum: {0}", tr(dates.label)))
            if (skip > 0) add(if (skip == 1) tr("Eine Titelzeile über der Tabelle wird übersprungen.") else tr("{0} Titelzeilen über der Tabelle werden übersprungen.", skip))
        }
        return Detected(s, notes)
    }

    fun numberExample(decimal: Char, thousands: Char?): String =
        (if (thousands != null) "1${thousands}234" else "1234") + "${decimal}56"

    /** [raw] as the sheet reads it without doubt: numbers „1234,5“, dates „31.12.2026“ (with time if there is one). */
    fun normalize(raw: String, s: Settings): String {
        val t = raw.trim()
        if (t.isEmpty()) return raw
        date(t, s)?.let { return it }
        val c = cleanNumber(t) ?: return raw
        // Whole numbers without separators stay as written (postcodes, student numbers with leading zeros).
        if (c.none { it == '.' || it == ',' }) return raw
        var body = c
        if (s.thousands != null) {
            val group = Regex("^[-+]?\\d{1,3}(" + Regex.escape(s.thousands.toString()) + "\\d{3})+(" + Regex.escape(s.decimal.toString()) + "\\d+)?$")
            if (group.matches(body)) body = body.replace(s.thousands.toString(), "")
        }
        val other = if (s.decimal == ',') '.' else ','
        if (other in body) return raw
        if (body.count { it == s.decimal } > 1) return raw
        val number = body.replace(s.decimal, ',')
        val suffix = when {
            t.endsWith("%") -> " %"
            t.endsWith("€") || t.endsWith("EUR") || t.startsWith("€") -> " €"
            else -> ""
        }
        return number + suffix
    }

    private fun two(n: Int) = n.toString().padStart(2, '0')

    private fun year(y: String) = y.toInt().let { if (y.length == 2) it + if (it < 30) 2000 else 1900 else it }

    private fun dateText(d: Int, m: Int, y: Int, h: String, min: String, sec: String): String? {
        if (m !in 1..12 || d !in 1..31) return null
        runCatching { java.time.LocalDate.of(y, m, d) }.getOrNull() ?: return null
        val day = "${two(d)}.${two(m)}.$y"
        return when {
            h.isEmpty() -> day
            // The sheet reads „31.12.2026 14:30“; with seconds the ISO form keeps them.
            sec.isEmpty() || sec == "00" -> "$day ${two(h.toInt())}:$min"
            else -> "$y-${two(m)}-${two(d)} ${two(h.toInt())}:$min:$sec"
        }
    }

    private fun date(t: String, s: Settings): String? {
        SLASH_DATE.find(t)?.groupValues?.let { g ->
            val (a, b) = g[1].toInt() to g[2].toInt()
            val (d, m) = if (s.dates == DateStyle.MDY) b to a else a to b
            return dateText(d, m, year(g[3]), g[4], g[5], g[6])
        }
        DOT_DATE.find(t)?.groupValues?.let { g ->
            if (g[4].isEmpty()) return null
            return dateText(g[1].toInt(), g[2].toInt(), year(g[3]), g[4], g[5], g[6])
        }
        ISO_DATE.find(t)?.groupValues?.let { g ->
            if (g[4].isEmpty() && t.length == 10) return null
            return dateText(g[3].toInt(), g[2].toInt(), g[1].toInt(), g[4], g[5], g[6])
        }
        return null
    }

    /** The rows of [bytes] read with [s], cells normalised. */
    fun rows(bytes: ByteArray, s: Settings): List<List<String>> =
        split(decode(bytes, s), s.separator).drop(s.skip).map { r -> r.map { normalize(it, s) } }

    /** The rows of [bytes] read with [s] as Markdown table for the sheet. */
    fun markdown(bytes: ByteArray, s: Settings): String = SheetEngine.markdown(rows(bytes, s).ifEmpty { listOf(listOf("")) })

    /** Reads [bytes] with the settings found by [detect] (used wherever a CSV is opened without the preview). */
    fun read(bytes: ByteArray, name: String = ""): String = markdown(bytes, detect(bytes, name).settings)

    // ── Writing ──

    /** Flavours of CSV when saving. */
    enum class Flavour(val label: String) {
        EXCEL(trLater("Excel-tauglich (Semikolon, Dezimalkomma, mit BOM)")),
        STANDARD(trLater("Standard (Komma, Dezimalpunkt, UTF-8)")),
    }

    /** [rows] (as the sheet shows them) as CSV; Standard turns „1234,5“ into „1234.5“. */
    fun write(rows: List<List<String>>, flavour: Flavour): String {
        val sep = if (flavour == Flavour.EXCEL) ';' else ','
        val body = rows.joinToString("\r\n") { r ->
            r.joinToString(sep.toString()) { raw ->
                val v = if (flavour == Flavour.STANDARD) standardNumber(raw) else raw
                if (v.any { it == sep || it == '"' || it == '\n' || it == '\r' } || v != v.trim()) "\"" + v.replace("\"", "\"\"") + "\"" else v
            }
        } + "\r\n"
        return if (flavour == Flavour.EXCEL) "﻿" + body else body
    }

    private val GERMAN_NUMBER = Regex("^[-+]?(\\d{1,3}(\\.\\d{3})+|\\d+)(,\\d+)?$")

    /** „1.234,5“ → „1234.5“; anything else unchanged. */
    private fun standardNumber(v: String): String {
        val t = v.trim()
        if (!GERMAN_NUMBER.matches(t) || (',' !in t && '.' !in t)) return v
        return t.replace(".", "").replace(',', '.')
    }
}
