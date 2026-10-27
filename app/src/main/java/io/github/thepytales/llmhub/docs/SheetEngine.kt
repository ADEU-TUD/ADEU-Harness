package io.github.thepytales.llmhub.docs

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * A sheet of the table editor: a Markdown table whose cells may hold formulas like in Excel ("=SUMME(B2:B9)",
 * "=A2*1,19", "=WENN(C2>=50;"bestanden";"nicht bestanden")"). German and English function names both work;
 * [toExcel] gives the English form Excel stores in .xlsx files. Dates ("03.04.2026", "2026-04-03") are serial
 * numbers like in Excel (1900 system), references to other sheets look like `Tabelle2!A1` or `'Noten 7b'!B2:B20`,
 * and EINDEUTIG, FILTER, SORTIEREN and SEQUENZ spill into the empty cells below and to the right.
 */
object SheetEngine {
    /** Cell address like "B12" → (row 11, column 1), zero-based. */
    fun parseRef(ref: String): Pair<Int, Int>? {
        val m = Regex("^\\$?([A-Za-z]{1,3})\\$?(\\d{1,6})$").find(ref.trim()) ?: return null
        val col = m.groupValues[1].uppercase().fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
        val row = m.groupValues[2].toInt() - 1
        return if (row < 0 || col < 0) null else row to col
    }

    /** Range like "B2:C9" (or a single cell) → (top, left, bottom, right), zero-based. */
    fun parseRange(range: String): IntArray? {
        val parts = range.trim().split(':')
        val a = parseRef(parts[0]) ?: return null
        val b = if (parts.size > 1) parseRef(parts[1]) ?: return null else a
        return intArrayOf(minOf(a.first, b.first), minOf(a.second, b.second), maxOf(a.first, b.first), maxOf(a.second, b.second))
    }

    fun colName(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) { val r = (n - 1) % 26; sb.insert(0, 'A' + r); n = (n - 1) / 26 }
        return sb.toString()
    }

    fun ref(row: Int, col: Int) = colName(col) + (row + 1)

    /** "B2:D9" for the given corners (a single cell is just "B2"). */
    fun rangeRef(top: Int, left: Int, bottom: Int, right: Int) =
        if (top == bottom && left == right) ref(top, left) else ref(top, left) + ":" + ref(bottom, right)

    /** A sheet name as formulas need it: quoted with ' when it has spaces, special characters or looks like a cell. */
    fun quoteSheet(name: String): String =
        if (Regex("^[A-Za-z_][A-Za-z0-9_.]*$").matches(name) && parseRef(name) == null && !name.equals("TRUE", true) && !name.equals("FALSE", true)) name
        else "'" + name.replace("'", "''") + "'"

    /** Rows of the first table in [markdown] (head row included); an empty sheet has 1 × 1 cell. */
    fun grid(markdown: String): List<List<String>> =
        DocBlocks.split(markdown).firstOrNull { DocBlocks.kindOf(it) == DocBlocks.Kind.TABLE }?.let { DocBlocks.tableRows(it) }
            ?.map { r -> r.map { it.replace("<br>", "\n") } }
            ?.takeIf { it.isNotEmpty() } ?: listOf(listOf(""))

    /** Markdown of a sheet: one table (cells with "|" or line breaks are escaped). */
    fun markdown(rows: List<List<String>>): String {
        val cols = rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
        val clean = rows.map { r -> (0 until cols).map { c -> r.getOrElse(c) { "" }.replace("\n", "<br>") } }
        return DocBlocks.table(clean) + "\n"
    }

    /** A new, empty sheet with column heads A, B, C … and [rows] rows. */
    fun blank(cols: Int = 5, rows: Int = 12): String =
        markdown(listOf(List(cols) { "Spalte ${colName(it)}" }) + List(rows) { List(cols) { "" } })

    sealed interface Value {
        /** A number; [date] marks a date (serial number like in Excel), shown as "03.04.2026". */
        data class Num(val v: Double, val date: Boolean = false) : Value
        data class Text(val v: String) : Value
        data class Bool(val v: Boolean) : Value
        data class Err(val code: String) : Value
        /** Cells of a range row by row; [cols] is its width (for SVERWEIS, INDEX …). */
        data class Range(val values: List<Value>, val cols: Int = 1) : Value {
            val rows: List<List<Value>> get() = values.chunked(cols.coerceAtLeast(1))
        }
    }

    // ── Dates ──

    private val EPOCH: LocalDate = LocalDate.of(1899, 12, 30)
    private val DE_DATE = Regex("^(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4}|\\d{2})(?:,?\\s+(\\d{1,2}):(\\d{2}))?$")
    private val ISO_DATE = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})(?:[T ](\\d{1,2}):(\\d{2})(?::\\d{2})?)?$")
    private val WEEKDAYS = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")
    private val MONTHS = listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember")

    /** Excel's serial number of [d] (1900 system, with Excel's 29.02.1900 that never was). */
    fun serial(d: LocalDate): Double {
        val n = ChronoUnit.DAYS.between(EPOCH, d)
        return (if (n < 61) n - 1 else n).toDouble()
    }

    /** The day of an Excel serial number. */
    fun dateOf(serial: Double): LocalDate {
        val n = floor(serial).toLong()
        return EPOCH.plusDays(if (n < 61) n + 1 else n)
    }

    /** "03.04.2026", "3.4.26", "2026-04-03" (optionally with "14:30") → serial number; null if it is no date. */
    fun parseDate(s: String): Double? {
        val t = s.trim()
        if (t.length < 6 || t.length > 20) return null
        val g = DE_DATE.find(t)?.groupValues?.let { listOf(it[1], it[2], it[3], it[4], it[5]) }
            ?: ISO_DATE.find(t)?.groupValues?.let { listOf(it[3], it[2], it[1], it[4], it[5]) } ?: return null
        var year = g[2].toInt()
        if (g[2].length == 2) year += if (year < 30) 2000 else 1900
        if (year < 1900) return null
        val date = runCatching { LocalDate.of(year, g[1].toInt(), g[0].toInt()) }.getOrNull() ?: return null
        val time = if (g[3].isNotEmpty()) {
            val h = g[3].toInt()
            val m = g[4].toInt()
            if (h > 23 || m > 59) return null
            (h * 60 + m) / 1440.0
        } else 0.0
        return serial(date) + time
    }

    /** "03.04.2026" (with " 14:30" if the serial number has a time). */
    fun dateText(serial: Double): String {
        if (serial < 0 || serial > 2_958_465) return formatNumber(serial)
        val d = dateOf(serial)
        val minutes = Math.round((serial - floor(serial)) * 1440).toInt()
        val day = String.format(Locale.ROOT, "%02d.%02d.%04d", d.dayOfMonth, d.monthValue, d.year)
        return if (minutes in 1 until 1440) day + String.format(Locale.ROOT, " %02d:%02d", minutes / 60, minutes % 60) else day
    }

    /** A cell text as number: dates become serial numbers, "1.234,5" and "12 %" numbers. */
    fun numberOrDate(s: String): Double? = parseDate(s) ?: XlsxWriter.number(s)

    /** The value a typed cell text stands for (no formula). */
    fun literal(raw: String): Value {
        val t = raw.trim()
        if (t.isEmpty()) return Value.Text("")
        parseDate(t)?.let { return Value.Num(it, date = true) }
        return XlsxWriter.number(t)?.let { Value.Num(it) } ?: Value.Text(raw)
    }

    /** Formulas whose result may fill several cells. */
    private val SPILLING = Regex("(?i)(EINDEUTIG|UNIQUE|FILTER|SORTIEREN|SORT|SEQUENZ|SEQUENCE)\\s*\\(")

    /**
     * Evaluates all cells of [rows]; formulas give their result, other cells stay as they are. [resolver] gives the
     * other sheets of the workbook for references like `Tabelle2!A1` (see [Workbook]).
     */
    class Sheet(val rows: List<List<String>>, private val resolver: ((String) -> Sheet?)? = null) {
        private val cache = HashMap<Pair<Int, Int>, Value>()
        private val visiting = HashSet<Pair<Int, Int>>()
        private val probing = HashSet<Pair<Int, Int>>()
        /** Whole results of formulas that spill (keyed by their cell). */
        private val spills = HashMap<Pair<Int, Int>, Value.Range>()
        private val blockedCache = HashMap<Pair<Int, Int>, Boolean>()
        private val anchors: List<Pair<Int, Int>> by lazy {
            rows.flatMapIndexed { r, row -> row.indices.filter { c -> row[c].trimStart().startsWith("=") && SPILLING.containsMatchIn(row[c]) }.map { r to it } }
        }
        private val anchorSet by lazy { anchors.toHashSet() }

        fun raw(r: Int, c: Int): String = rows.getOrNull(r)?.getOrNull(c).orEmpty()

        /** Another sheet of the workbook (null if there is none with this name). */
        fun other(name: String): Sheet? = resolver?.invoke(name)

        fun value(r: Int, c: Int): Value {
            val key = r to c
            cache[key]?.let { return it }
            val raw = raw(r, c).trim()
            if (raw.isEmpty()) {
                // An empty cell may show a part of a spilling formula above or to the left.
                if (anchors.isEmpty() || !probing.add(key)) return Value.Text("")
                val v = spilled(r, c) ?: Value.Text("")
                probing.remove(key)
                cache[key] = v
                return v
            }
            if (!visiting.add(key)) return Value.Err("#ZIRKEL!")
            val v = when {
                raw.startsWith("=") && raw.length > 1 -> {
                    val res = runCatching { Parser(raw.substring(1), this, r, c).parse() }.getOrElse { Value.Err(if (it is EvalError) it.code else "#FEHLER!") }
                    if (res is Value.Range && key in anchorSet && res.values.size > 1) {
                        spills[key] = res
                        if (blocked(r, c, res)) Value.Err("#ÜBERLAUF!") else res.values.first()
                    } else res
                }
                else -> literal(raw)
            }
            visiting.remove(key)
            cache[key] = v
            return v
        }

        private fun blocked(ar: Int, ac: Int, range: Value.Range): Boolean = blockedCache.getOrPut(ar to ac) {
            val h = range.rows.size
            (ar until ar + h).any { r -> (ac until ac + range.cols).any { c -> !(r == ar && c == ac) && raw(r, c).isNotBlank() } }
        }

        private fun spilled(r: Int, c: Int): Value? {
            for ((ar, ac) in anchors) {
                if (ar > r || ac > c || (ar to ac) in visiting) continue
                value(ar, ac)
                val range = spills[ar to ac] ?: continue
                if (r - ar < range.rows.size && c - ac < range.cols && !blocked(ar, ac, range)) return range.rows[r - ar].getOrNull(c - ac) ?: Value.Text("")
            }
            return null
        }

        /** The whole result of the formula in (r, c) – its spill range for `C2#` – or its value. */
        fun spillRange(r: Int, c: Int): Value {
            val v = value(r, c)
            return spills[r to c]?.takeIf { !blocked(r, c, it) } ?: v
        }

        /** Cells filled by spilling formulas (not the formula cells themselves), e.g. for the .xlsx export. */
        fun spilledCells(): Map<Pair<Int, Int>, Value> {
            val out = LinkedHashMap<Pair<Int, Int>, Value>()
            anchors.forEach { (ar, ac) ->
                value(ar, ac)
                val range = spills[ar to ac] ?: return@forEach
                if (blocked(ar, ac, range)) return@forEach
                range.rows.forEachIndexed { i, row -> row.forEachIndexed { j, v -> if (i != 0 || j != 0) out[(ar + i) to (ac + j)] = v } }
            }
            return out
        }

        /** Size of the spill range of a formula cell (rows, columns), or null if it does not spill. */
        fun spillSize(r: Int, c: Int): Pair<Int, Int>? {
            value(r, c)
            val range = spills[r to c]?.takeIf { !blocked(r, c, it) } ?: return null
            return range.rows.size to range.cols
        }

        /** What the cell shows: the result of its formula, otherwise its text. */
        fun display(r: Int, c: Int): String {
            val raw = raw(r, c)
            if (raw.isBlank()) return if (anchors.isEmpty()) raw else format(value(r, c))
            if (!raw.trimStart().startsWith("=")) return raw
            return format(value(r, c))
        }

        /** All rows with formulas replaced by their results (for PDF, Word and the model). */
        fun computed(): List<List<String>> = rows.mapIndexed { r, row -> row.indices.map { c -> display(r, c) } }
    }

    /** All sheets of a document by name, so formulas can refer to each other's cells. */
    class Workbook(private val tabs: List<Pair<String, List<List<String>>>>) {
        private val sheets = HashMap<Int, Sheet>()

        fun index(name: String): Int = tabs.indexOfFirst { it.first.trim().equals(name.trim(), ignoreCase = true) }

        fun sheet(name: String): Sheet? = index(name).takeIf { it >= 0 }?.let(::sheet)

        fun sheet(i: Int): Sheet = sheets.getOrPut(i) { Sheet(tabs[i].second) { n -> sheet(n) } }
    }

    fun format(v: Value): String = when (v) {
        is Value.Num -> if (v.date) dateText(v.v) else formatNumber(v.v)
        is Value.Text -> v.v
        is Value.Bool -> if (v.v) "WAHR" else "FALSCH"
        is Value.Err -> v.code
        is Value.Range -> v.values.firstOrNull()?.let(::format).orEmpty()
    }

    /** German notation, at most 10 decimals, no "1.0E7". */
    fun formatNumber(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return "#ZAHL!"
        if (d == floor(d) && abs(d) < 1e15) return d.toLong().toString()
        val s = String.format(Locale.ROOT, "%.10f", d).trimEnd('0').trimEnd('.')
        return s.replace('.', ',')
    }

    /**
     * A number in a format like Excel's TEXT: "0", "0,00", "#.##0,00", "0 %", "0,0%", "0,00 €" (German) or
     * "#,##0.00" (English); text around the number stays.
     */
    fun formatPattern(n: Double, pattern: String): String {
        val m = Regex("[#0][#0.,]*").find(pattern) ?: return pattern.replace("\"", "")
        val core = m.value.trimEnd('.', ',')
        val pct = '%' in pattern
        val lastSep = core.indexOfLast { it == ',' || it == '.' }
        val decIdx = when {
            lastSep < 0 -> -1
            ',' in core && '.' in core -> lastSep
            core.getOrNull(lastSep - 1) == '#' && core.substring(lastSep + 1) == "##0" -> -1
            core.count { it == core[lastSep] } > 1 -> -1
            else -> lastSep
        }
        val decimals = if (decIdx >= 0) core.length - decIdx - 1 else 0
        val grouped = Regex("#[.,]##").containsMatchIn(if (decIdx >= 0) core.substring(0, decIdx) else core)
        val v = if (pct) n * 100 else n
        val number = String.format(Locale.GERMANY, if (grouped) "%,.${decimals}f" else "%.${decimals}f", v)
        val prefix = pattern.substring(0, m.range.first).replace("\"", "").replace("\\", "")
        val suffix = pattern.substring(m.range.first + m.value.length).replace("\"", "").replace("\\", "").let { if (pct) it.replace(Regex("^\\s*%"), " %") else it }
        return prefix + number + suffix
    }

    /** A date in a format like "TT.MM.JJJJ", "DD.MM.YYYY", "TTTT", "MMMM JJJJ" or "hh:mm" (German and English letters). */
    fun formatDate(serial: Double, pattern: String): String {
        val d = dateOf(serial)
        val secs = Math.round((serial - floor(serial)) * 86400).toInt().coerceIn(0, 86399)
        val out = StringBuilder()
        var i = 0
        var afterHour = false
        fun pad(n: Int, w: Int) = if (w >= 2) String.format(Locale.ROOT, "%02d", n) else n.toString()
        while (i < pattern.length) {
            val ch = pattern[i]
            if (ch == '"') {
                val e = pattern.indexOf('"', i + 1).let { if (it < 0) pattern.length else it }
                out.append(pattern, i + 1, e)
                i = e + 1
                continue
            }
            val u = ch.uppercaseChar()
            if (u in "TDMJYHS") {
                var j = i
                while (j < pattern.length && pattern[j].uppercaseChar() == u) j++
                val n = j - i
                out.append(
                    when (u) {
                        'T', 'D' -> when { n >= 4 -> WEEKDAYS[d.dayOfWeek.value - 1]; n == 3 -> WEEKDAYS[d.dayOfWeek.value - 1].take(2); else -> pad(d.dayOfMonth, n) }
                        'M' -> if (afterHour) pad(secs / 60 % 60, n) else when { n >= 4 -> MONTHS[d.monthValue - 1]; n == 3 -> MONTHS[d.monthValue - 1].take(3); else -> pad(d.monthValue, n) }
                        'J', 'Y' -> if (n <= 2) pad(d.year % 100, 2) else d.year.toString()
                        'H' -> pad(secs / 3600, n)
                        else -> pad(secs % 60, n)
                    },
                )
                afterHour = u == 'H' || (afterHour && u == 'M')
                i = j
            } else {
                if (ch != ':' && ch != ' ') afterHour = false
                out.append(ch)
                i++
            }
        }
        return out.toString()
    }

    private class EvalError(val code: String) : RuntimeException(code)

    /** Function names: German → Excel (English). */
    private val NAMES = mapOf(
        "SUMME" to "SUM", "MITTELWERT" to "AVERAGE", "ANZAHL" to "COUNT", "ANZAHL2" to "COUNTA", "RUNDEN" to "ROUND",
        "WENN" to "IF", "UND" to "AND", "ODER" to "OR", "NICHT" to "NOT", "ABRUNDEN" to "ROUNDDOWN", "AUFRUNDEN" to "ROUNDUP",
        "WURZEL" to "SQRT", "POTENZ" to "POWER", "ZÄHLENWENN" to "COUNTIF", "ZAEHLENWENN" to "COUNTIF", "SUMMEWENN" to "SUMIF",
        "STABW" to "STDEV", "STABW.S" to "STDEV", "VERKETTEN" to "CONCATENATE", "LÄNGE" to "LEN", "LAENGE" to "LEN",
        "GROSS" to "UPPER", "KLEIN" to "LOWER", "GANZZAHL" to "INT", "REST" to "MOD", "MEDIAN" to "MEDIAN", "MIN" to "MIN", "MAX" to "MAX",
        "ABS" to "ABS", "HEUTE" to "TODAY",
        "SVERWEIS" to "VLOOKUP", "WVERWEIS" to "HLOOKUP", "XVERWEIS" to "XLOOKUP", "INDEX" to "INDEX", "VERGLEICH" to "MATCH",
        "WENNFEHLER" to "IFERROR", "WENNNV" to "IFNA", "WENNS" to "IFS", "ZÄHLENWENNS" to "COUNTIFS", "ZAEHLENWENNS" to "COUNTIFS",
        "SUMMEWENNS" to "SUMIFS", "MITTELWERTWENN" to "AVERAGEIF", "MITTELWERTWENNS" to "AVERAGEIFS", "LINKS" to "LEFT",
        "RECHTS" to "RIGHT", "TEIL" to "MID", "GLÄTTEN" to "TRIM", "GLAETTEN" to "TRIM", "WECHSELN" to "SUBSTITUTE", "FINDEN" to "FIND",
        "SUCHEN" to "SEARCH", "TEXTVERKETTEN" to "TEXTJOIN", "GROSS2" to "PROPER", "WIEDERHOLEN" to "REPT", "WERT" to "VALUE",
        "PRODUKT" to "PRODUCT", "RANG" to "RANK", "RANG.GLEICH" to "RANK", "KGRÖSSTE" to "LARGE", "KGROESSTE" to "LARGE", "KKLEINSTE" to "SMALL",
        "VARIANZ" to "VAR", "VAR.S" to "VAR", "STABW.N" to "STDEV.P", "ANZAHLLEEREZELLEN" to "COUNTBLANK", "SUMMENPRODUKT" to "SUMPRODUCT",
        "ISTLEER" to "ISBLANK", "ISTZAHL" to "ISNUMBER", "ISTTEXT" to "ISTEXT", "ISTFEHLER" to "ISERROR", "JETZT" to "NOW",
        "MODALWERT" to "MODE", "KORREL" to "CORREL", "QUANTIL" to "PERCENTILE", "VERKETTEN2" to "CONCAT",
        // Dates
        "DATUM" to "DATE", "TAG" to "DAY", "MONAT" to "MONTH", "JAHR" to "YEAR", "WOCHENTAG" to "WEEKDAY", "DATEDIF" to "DATEDIF",
        "NETTOARBEITSTAGE" to "NETWORKDAYS", "EDATUM" to "EDATE", "ARBEITSTAG" to "WORKDAY", "KALENDERWOCHE" to "WEEKNUM",
        "ISOKALENDERWOCHE" to "ISOWEEKNUM", "MONATSENDE" to "EOMONTH", "TAGE" to "DAYS",
        // Dynamic arrays and more
        "EINDEUTIG" to "UNIQUE", "FILTER" to "FILTER", "SORTIEREN" to "SORT", "SEQUENZ" to "SEQUENCE", "ZEILE" to "ROW",
        "SPALTE" to "COLUMN", "ZEILEN" to "ROWS", "SPALTEN" to "COLUMNS", "VRUNDEN" to "MROUND", "OBERGRENZE" to "CEILING",
        "UNTERGRENZE" to "FLOOR",
    )

    /** Functions newer than Excel 2007 carry a prefix in .xlsx files. */
    private val PREFIX = mapOf(
        "XLOOKUP" to "_xlfn.", "IFS" to "_xlfn.", "TEXTJOIN" to "_xlfn.", "IFNA" to "_xlfn.", "CONCAT" to "_xlfn.", "STDEV.P" to "_xlfn.",
        "UNIQUE" to "_xlfn.", "FILTER" to "_xlfn._xlws.", "SORT" to "_xlfn._xlws.", "SEQUENCE" to "_xlfn.", "ISOWEEKNUM" to "_xlfn.",
        "DAYS" to "_xlfn.",
    )

    /** Excel functions whose result spills (dynamic arrays): the .xlsx export marks their cells. */
    val DYNAMIC = setOf("UNIQUE", "FILTER", "SORT", "SEQUENCE")

    /** English name of a function as Excel stores it (without prefix). */
    fun excelName(name: String): String = name.uppercase().removePrefix("_XLFN.").removePrefix("_XLWS.").let { NAMES[it] ?: it }

    /**
     * Inserts [arg] as second argument into calls of the functions [names] that have only one argument
     * (KALENDERWOCHE counts ISO weeks here, Excel's WEEKNUM without type does not).
     */
    fun withDefaultArg(formula: String, names: Set<String>, arg: String, sep: Char): String {
        val sb = StringBuilder(formula)
        val inserts = mutableListOf<Int>()
        var inString = false
        var i = 0
        while (i < formula.length) {
            val ch = formula[i]
            if (ch == '"') inString = !inString
            if (!inString && (ch.isLetter() || ch == '_') && (i == 0 || !(formula[i - 1].isLetterOrDigit() || formula[i - 1] == '.' || formula[i - 1] == '_'))) {
                var j = i
                while (j < formula.length && (formula[j].isLetterOrDigit() || formula[j] == '.' || formula[j] == '_')) j++
                if (formula.getOrNull(j) == '(' && excelName(formula.substring(i, j)) in names) {
                    var depth = 0
                    var k = j
                    var args = 1
                    var str = false
                    var empty = true
                    while (k < formula.length) {
                        val c = formula[k]
                        if (c == '"') str = !str
                        if (!str) {
                            if (c == '(') depth++
                            else if (c == ')') { depth--; if (depth == 0) break }
                            else if (depth == 1 && (c == ';' || c == ',' && !(formula.getOrNull(k - 1)?.isDigit() == true && formula.getOrNull(k + 1)?.isDigit() == true))) args++
                            else if (depth >= 1 && !c.isWhitespace()) empty = false
                        }
                        k++
                    }
                    if (k < formula.length && args == 1 && !empty) inserts += k
                }
                i = j
                continue
            }
            i++
        }
        inserts.sortedDescending().forEach { sb.insert(it, "$sep$arg") }
        return sb.toString()
    }

    /**
     * The formula as Excel stores it: English function names, "," between arguments, "." as decimal point, WAHR and
     * FALSCH as TRUE and FALSE; sheet names are quoted as Excel needs them ([sheetName] may rename them).
     */
    fun toExcel(formula: String, sheetName: (String) -> String = { it }): String {
        val body = withDefaultArg(formula.trim().removePrefix("="), setOf("WEEKNUM"), "21", ';')
        val out = StringBuilder()
        var i = 0
        var inString = false
        while (i < body.length) {
            val ch = body[i]
            when {
                ch == '"' -> { inString = !inString; out.append(ch); i++ }
                inString -> { out.append(ch); i++ }
                ch == '\'' -> {
                    // 'Sheet name'!A1
                    val sb = StringBuilder()
                    var j = i + 1
                    while (j < body.length) {
                        if (body[j] == '\'') { if (body.getOrNull(j + 1) == '\'') { sb.append('\''); j += 2; continue }; break }
                        sb.append(body[j++])
                    }
                    if (body.getOrNull(j + 1) == '!') { out.append(quoteSheet(sheetName(sb.toString()))).append('!'); i = j + 2 } else { out.append(ch); i++ }
                }
                ch.isLetter() || ch == '_' -> {
                    var j = i
                    while (j < body.length && (body[j].isLetterOrDigit() || body[j] == '.' || body[j] == '_')) j++
                    val word = body.substring(i, j)
                    val next = body.getOrNull(j)
                    when {
                        next == '!' -> { out.append(quoteSheet(sheetName(word))).append('!'); j++ }
                        next == '(' -> excelName(word).let { out.append(PREFIX[it].orEmpty()).append(it) }
                        word.equals("WAHR", true) -> out.append("TRUE")
                        word.equals("FALSCH", true) -> out.append("FALSE")
                        else -> out.append(word)
                    }
                    i = j
                }
                ch == ';' -> { out.append(','); i++ }
                ch == ',' && body.getOrNull(i - 1)?.isDigit() == true && body.getOrNull(i + 1)?.isDigit() == true -> { out.append('.'); i++ }
                else -> { out.append(ch); i++ }
            }
        }
        return out.toString()
    }

    private val ROWCOL_ARG = Regex("^\\s*(?:'(?:[^']|'')*'!|[\\p{L}\\d_.]+!)?(\\$?[A-Za-z]{1,3}\\$?\\d{1,6})(?::\\$?[A-Za-z]{1,3}\\$?\\d{1,6})?\\s*\\)")

    /** Recursive-descent parser that evaluates while it reads; [row] and [col] are the cell of the formula. */
    private class Parser(val src: String, val sheet: Sheet, val row: Int, val col: Int) {
        var pos = 0

        fun parse(): Value {
            val v = comparison()
            skip()
            if (pos < src.length) throw EvalError("#FEHLER!")
            return v
        }

        fun skip() { while (pos < src.length && src[pos].isWhitespace()) pos++ }

        fun peek(): Char? { skip(); return src.getOrNull(pos) }

        fun eat(s: String): Boolean {
            skip()
            if (src.startsWith(s, pos)) { pos += s.length; return true }
            return false
        }

        /** [f] for two values; with a range on either side element by element (like array formulas in Excel). */
        fun lift(a: Value, b: Value, f: (Value, Value) -> Value): Value {
            val ra = a as? Value.Range
            val rb = b as? Value.Range
            if (ra == null && rb == null) return f(a, b)
            val wide = listOfNotNull(ra, rb).maxBy { it.values.size }
            return Value.Range(
                wide.values.indices.map { i ->
                    val x = if (ra != null) ra.values.getOrElse(i) { Value.Err("#NV") } else a
                    val y = if (rb != null) rb.values.getOrElse(i) { Value.Err("#NV") } else b
                    try { f(x, y) } catch (e: EvalError) { Value.Err(e.code) }
                },
                wide.cols,
            )
        }

        fun comparison(): Value {
            var a = concat()
            while (true) {
                val op = listOf("<=", ">=", "<>", "=", "<", ">").firstOrNull { eat(it) } ?: return a
                val b = concat()
                a = lift(a, b) { x, y -> Value.Bool(compare(x, y, op)) }
            }
        }

        fun compare(a: Value, b: Value, op: String): Boolean {
            // An empty cell counts as 0 when compared with a number (as in Excel: =WENN(B5=0;…)).
            fun blankAsZero(v: Value, other: Value) = if (v is Value.Text && v.v.isEmpty() && other is Value.Num) Value.Num(0.0) else v
            val x = blankAsZero(scalar(a), scalar(b))
            val y = blankAsZero(scalar(b), scalar(a))
            val c = if (x is Value.Num && y is Value.Num) x.v.compareTo(y.v) else text(x).compareTo(text(y), ignoreCase = true)
            return when (op) { "<=" -> c <= 0; ">=" -> c >= 0; "<>" -> c != 0; "=" -> c == 0; "<" -> c < 0; else -> c > 0 }
        }

        fun concat(): Value {
            var a = additive()
            while (eat("&")) { val b = additive(); a = lift(a, b) { x, y -> Value.Text(text(x) + text(y)) } }
            return a
        }

        fun isDate(v: Value) = (v as? Value.Num)?.date == true

        fun additive(): Value {
            var a = term()
            while (true) {
                a = when {
                    // Date + days is a date, date − date a number of days.
                    eat("+") -> { val b = term(); lift(a, b) { x, y -> Value.Num(num(x) + num(y), isDate(x) != isDate(y)) } }
                    eat("-") -> { val b = term(); lift(a, b) { x, y -> Value.Num(num(x) - num(y), isDate(x) && !isDate(y)) } }
                    else -> return a
                }
            }
        }

        fun term(): Value {
            var a = power()
            while (true) {
                a = when {
                    eat("*") -> { val b = power(); lift(a, b) { x, y -> Value.Num(num(x) * num(y)) } }
                    eat("/") -> { val b = power(); lift(a, b) { x, y -> val d = num(y); if (d == 0.0) throw EvalError("#DIV/0!"); Value.Num(num(x) / d) } }
                    else -> return a
                }
            }
        }

        fun power(): Value {
            val a = unary()
            return if (eat("^")) { val b = power(); lift(a, b) { x, y -> Value.Num(num(x).pow(num(y))) } } else a
        }

        fun unary(): Value = when {
            eat("-") -> unary().let { v -> if (v is Value.Range) lift(v, Value.Num(-1.0)) { x, y -> Value.Num(num(x) * num(y)) } else Value.Num(-num(v)) }
            eat("+") -> unary()
            else -> percent(primary())
        }

        fun percent(v: Value): Value = if (eat("%")) Value.Num(num(v) / 100) else v

        fun readRefWord(): String {
            val s = pos
            while (pos < src.length && (src[pos].isLetterOrDigit() || src[pos] == '$')) pos++
            return src.substring(s, pos)
        }

        /** A cell, a range ("B2:B9") or a spill range ("C2#") of [target] (this sheet or another one). */
        fun reference(target: Sheet, word: String): Value {
            val from = parseRef(word) ?: throw EvalError(if (target === sheet) "#NAME?" else "#BEZUG!")
            if (src.getOrNull(pos) == '#') { pos++; return target.spillRange(from.first, from.second) }
            if (eat(":")) {
                skip()
                val to = parseRef(readRefWord()) ?: throw EvalError("#BEZUG!")
                val values = mutableListOf<Value>()
                for (r in minOf(from.first, to.first)..maxOf(from.first, to.first)) {
                    for (c in minOf(from.second, to.second)..maxOf(from.second, to.second)) values += target.value(r, c)
                }
                return Value.Range(values, abs(to.second - from.second) + 1)
            }
            return target.value(from.first, from.second)
        }

        fun primary(): Value {
            val ch = peek() ?: throw EvalError("#FEHLER!")
            if (ch == '(') {
                pos++
                val v = comparison()
                if (!eat(")")) throw EvalError("#FEHLER!")
                return v
            }
            if (ch == '"') {
                pos++
                val sb = StringBuilder()
                while (pos < src.length) {
                    if (src[pos] == '"') {
                        if (src.getOrNull(pos + 1) == '"') { sb.append('"'); pos += 2; continue }
                        break
                    }
                    sb.append(src[pos++])
                }
                if (!eat("\"")) throw EvalError("#FEHLER!")
                return Value.Text(sb.toString())
            }
            if (ch == '\'') {
                // 'Noten 7b'!B2:B20
                pos++
                val sb = StringBuilder()
                while (pos < src.length) {
                    if (src[pos] == '\'') { if (src.getOrNull(pos + 1) == '\'') { sb.append('\''); pos += 2; continue }; break }
                    sb.append(src[pos++])
                }
                if (pos >= src.length || src.getOrNull(pos + 1) != '!') throw EvalError("#FEHLER!")
                pos += 2
                return reference(sheet.other(sb.toString()) ?: throw EvalError("#BEZUG!"), readRefWord())
            }
            if (ch.isDigit() || ch == '.' || ch == ',') {
                val start = pos
                while (pos < src.length && (src[pos].isDigit() || src[pos] == '.' || src[pos] == ',' && src.getOrNull(pos + 1)?.isDigit() == true)) pos++
                val text = src.substring(start, pos)
                return Value.Num(XlsxWriter.number(text) ?: throw EvalError("#ZAHL!"))
            }
            if (ch.isLetter() || ch == '$' || ch == '_') {
                val start = pos
                while (pos < src.length && (src[pos].isLetterOrDigit() || src[pos] == '$' || src[pos] == '.' || src[pos] == '_')) pos++
                val word = src.substring(start, pos)
                if (src.getOrNull(pos) == '!') {
                    pos++
                    return reference(sheet.other(word) ?: throw EvalError("#BEZUG!"), readRefWord())
                }
                if (peek() == '(') {
                    pos++
                    val name = excelName(word)
                    if (name == "ROW" || name == "COLUMN") return rowOrColumn(name)
                    val args = mutableListOf<Value>()
                    if (!eat(")")) {
                        do { args += argument() } while (eat(";") || eat(","))
                        if (!eat(")")) throw EvalError("#FEHLER!")
                    }
                    return call(name, args)
                }
                when (word.uppercase()) {
                    "WAHR", "TRUE" -> return Value.Bool(true)
                    "FALSCH", "FALSE" -> return Value.Bool(false)
                }
                return reference(sheet, word)
            }
            throw EvalError("#FEHLER!")
        }

        /** ZEILE(B5) = 5, SPALTE(B5) = 2; without argument the cell of the formula. */
        fun rowOrColumn(name: String): Value {
            val m = ROWCOL_ARG.find(src.substring(pos))
            if (m != null) {
                pos += m.value.length
                val ref = parseRef(m.groupValues[1]) ?: throw EvalError("#BEZUG!")
                return Value.Num((if (name == "ROW") ref.first else ref.second) + 1.0)
            }
            if (eat(")")) return Value.Num((if (name == "ROW") row else col) + 1.0)
            throw EvalError("#WERT!")
        }

        /**
         * One argument of a function. An error inside it becomes an error value instead of ending the whole formula, so
         * =WENN(B2=0;0;A2/B2) and =WENNFEHLER(A2/B2;"–") work like in Excel.
         */
        fun argument(): Value {
            val start = pos
            return try {
                comparison()
            } catch (e: EvalError) {
                pos = start
                skipArgument()
                Value.Err(e.code)
            }
        }

        /** Moves to the end of the current argument (the next ";", "," or ")" outside of brackets and strings). */
        fun skipArgument() {
            var depth = 0
            var inString = false
            while (pos < src.length) {
                val c = src[pos]
                when {
                    c == '"' -> inString = !inString
                    inString -> {}
                    c == '(' -> depth++
                    c == ')' -> if (depth == 0) return else depth--
                    depth == 0 && c == ';' -> return
                    depth == 0 && c == ',' && !(src.getOrNull(pos - 1)?.isDigit() == true && src.getOrNull(pos + 1)?.isDigit() == true) -> return
                }
                pos++
            }
        }

        fun scalar(v: Value): Value = when (v) {
            is Value.Range -> v.values.firstOrNull()?.let { if (it is Value.Err) throw EvalError(it.code) else it } ?: Value.Text("")
            is Value.Err -> throw EvalError(v.code)
            else -> v
        }

        fun num(v: Value): Double = when (val s = scalar(v)) {
            is Value.Num -> s.v
            is Value.Bool -> if (s.v) 1.0 else 0.0
            is Value.Text -> if (s.v.isBlank()) 0.0 else numberOrDate(s.v) ?: throw EvalError("#WERT!")
            else -> throw EvalError("#WERT!")
        }

        fun text(v: Value): String = format(scalar(v))

        fun truthy(v: Value): Boolean = when (val s = scalar(v)) {
            is Value.Bool -> s.v
            is Value.Num -> s.v != 0.0
            is Value.Text -> s.v.equals("wahr", true) || s.v.equals("true", true)
            else -> false
        }

        /** Numbers of all arguments (text in ranges is skipped, like in Excel). */
        fun numbers(args: List<Value>): List<Double> = args.flatMap { a ->
            when (a) {
                is Value.Range -> a.values.mapNotNull { v ->
                    when (v) {
                        is Value.Num -> v.v
                        is Value.Err -> throw EvalError(v.code)
                        else -> null
                    }
                }
                else -> listOf(num(a))
            }
        }

        fun flat(args: List<Value>): List<Value> = args.flatMap { if (it is Value.Range) it.values else listOf(it) }

        fun range(v: Value): Value.Range = v as? Value.Range ?: Value.Range(listOf(scalarOrErr(v)))

        fun transpose(r: Value.Range): Value.Range {
            val rows = r.rows
            return Value.Range((0 until r.cols).flatMap { c -> rows.map { it.getOrElse(c) { Value.Text("") } } }, rows.size.coerceAtLeast(1))
        }

        fun matches(v: Value, criterion: Value): Boolean {
            val c = text(criterion).trim()
            val op = listOf("<=", ">=", "<>", "=", "<", ">").firstOrNull { c.startsWith(it) }
            val rest = if (op != null) c.removePrefix(op) else c
            val target = parseDate(rest)?.let { Value.Num(it, true) } ?: XlsxWriter.number(rest)?.let { Value.Num(it) } ?: Value.Text(rest)
            return compare(v, target, op ?: "=")
        }

        fun round(x: Double, digits: Int, mode: Int): Double {
            val f = 10.0.pow(digits)
            val y = x * f
            val r = when (mode) { 0 -> Math.round(y).toDouble(); -1 -> if (y < 0) ceil(y) else floor(y); else -> if (y < 0) floor(y) else ceil(y) }
            return r / f
        }

        fun date(v: Value): LocalDate {
            val n = num(v)
            if (n < 0 || n > 2_958_465) throw EvalError("#ZAHL!")
            return dateOf(n)
        }

        fun dateNum(d: LocalDate) = Value.Num(serial(d), date = true)

        fun holidays(v: Value?): Set<Long> = v?.let { h -> flat(listOf(h)).mapNotNull { x -> (x as? Value.Num)?.v ?: (x as? Value.Text)?.v?.let(::numberOrDate) }.map { floor(it).toLong() }.toSet() }.orEmpty()

        fun workday(d: LocalDate, hol: Set<Long>) = d.dayOfWeek.value <= 5 && serial(d).toLong() !in hol

        /** Sort order of cells: numbers, then text, then truth values; empty cells last. */
        fun compareCells(a: Value, b: Value): Int {
            fun rank(v: Value) = when { v is Value.Num -> 0; v is Value.Text && v.v.isEmpty() -> 4; v is Value.Text -> 1; v is Value.Bool -> 2; else -> 3 }
            val ra = rank(a)
            val rb = rank(b)
            if (ra != rb) return ra.compareTo(rb)
            return when {
                a is Value.Num && b is Value.Num -> a.v.compareTo(b.v)
                a is Value.Bool && b is Value.Bool -> a.v.compareTo(b.v)
                else -> format(a).compareTo(format(b), ignoreCase = true)
            }
        }

        fun key(v: Value): String = when (v) {
            is Value.Num -> "n" + v.v
            is Value.Text -> "t" + v.v.lowercase()
            is Value.Bool -> "b" + v.v
            is Value.Err -> "e" + v.code
            is Value.Range -> "r" + v.values.joinToString("|") { key(it) }
        }

        fun call(name: String, args: List<Value>): Value = when (name) {
            "SUM" -> Value.Num(numbers(args).sum())
            "AVERAGE" -> numbers(args).let { if (it.isEmpty()) throw EvalError("#DIV/0!") else Value.Num(it.average()) }
            "MIN" -> Value.Num(numbers(args).minOrNull() ?: 0.0)
            "MAX" -> Value.Num(numbers(args).maxOrNull() ?: 0.0)
            "COUNT" -> Value.Num(flat(args).count { it is Value.Num }.toDouble())
            "COUNTA" -> Value.Num(flat(args).count { !(it is Value.Text && it.v.isEmpty()) }.toDouble())
            "MEDIAN" -> numbers(args).sorted().let { l -> if (l.isEmpty()) throw EvalError("#ZAHL!") else Value.Num(if (l.size % 2 == 1) l[l.size / 2] else (l[l.size / 2 - 1] + l[l.size / 2]) / 2) }
            "STDEV" -> numbers(args).let { l ->
                if (l.size < 2) throw EvalError("#DIV/0!")
                val m = l.average()
                Value.Num(sqrt(l.sumOf { (it - m) * (it - m) } / (l.size - 1)))
            }
            "ROUND" -> Value.Num(round(num(args[0]), args.getOrNull(1)?.let { num(it).toInt() } ?: 0, 0))
            "ROUNDDOWN" -> Value.Num(round(num(args[0]), args.getOrNull(1)?.let { num(it).toInt() } ?: 0, -1))
            "ROUNDUP" -> Value.Num(round(num(args[0]), args.getOrNull(1)?.let { num(it).toInt() } ?: 0, 1))
            "INT" -> Value.Num(floor(num(args[0])))
            "MOD" -> { val d = num(args[1]); if (d == 0.0) throw EvalError("#DIV/0!"); val a = num(args[0]); Value.Num(a - d * floor(a / d)) }
            "ABS" -> Value.Num(abs(num(args[0])))
            "SQRT" -> num(args[0]).let { if (it < 0) throw EvalError("#ZAHL!") else Value.Num(sqrt(it)) }
            "POWER" -> Value.Num(num(args[0]).pow(num(args[1])))
            "IF" -> if (truthy(args[0])) args.getOrElse(1) { Value.Bool(true) } else args.getOrElse(2) { Value.Bool(false) }
            "AND" -> Value.Bool(flat(args).all(::truthy))
            "OR" -> Value.Bool(flat(args).any(::truthy))
            "NOT" -> Value.Bool(!truthy(args[0]))
            "COUNTIF" -> Value.Num(flat(listOf(args[0])).count { matches(it, args[1]) }.toDouble())
            "SUMIF" -> {
                val cells = flat(listOf(args[0]))
                val sums = if (args.size > 2) flat(listOf(args[2])) else cells
                Value.Num(cells.indices.filter { matches(cells[it], args[1]) }.sumOf { i -> (sums.getOrNull(i) as? Value.Num)?.v ?: 0.0 })
            }
            "CONCATENATE", "CONCAT" -> Value.Text(flat(args).joinToString("") { text(it) })
            "LEN" -> Value.Num(text(args[0]).length.toDouble())
            "UPPER" -> Value.Text(text(args[0]).uppercase())
            "LOWER" -> Value.Text(text(args[0]).lowercase())
            "TODAY" -> dateNum(LocalDate.now())
            "NOW" -> LocalDateTime.now().let { t -> Value.Num(serial(t.toLocalDate()) + t.toLocalTime().toSecondOfDay() / 86400.0, date = true) }
            "IFERROR" -> if (args.getOrNull(0).let { it == null || it is Value.Err || (it is Value.Range && it.values.firstOrNull() is Value.Err) }) args.getOrElse(1) { Value.Text("") } else args[0]
            "IFNA" -> if ((args.getOrNull(0) as? Value.Err)?.code == "#NV") args.getOrElse(1) { Value.Text("") } else args[0]
            "IFS" -> args.chunked(2).firstOrNull { it.size == 2 && truthy(it[0]) }?.get(1) ?: throw EvalError("#NV")
            "ISBLANK" -> Value.Bool(scalarOrErr(args[0]).let { it is Value.Text && it.v.isEmpty() })
            "ISNUMBER" -> Value.Bool(scalarOrErr(args[0]) is Value.Num)
            "ISTEXT" -> Value.Bool(scalarOrErr(args[0]).let { it is Value.Text && it.v.isNotEmpty() })
            "ISERROR" -> Value.Bool(scalarOrErr(args[0]) is Value.Err)
            "VLOOKUP", "HLOOKUP" -> {
                val table = args[1] as? Value.Range ?: throw EvalError("#BEZUG!")
                val lines = if (name == "VLOOKUP") table.rows else (0 until table.cols).map { c -> table.rows.map { it.getOrElse(c) { Value.Text("") } } }
                val index = num(args[2]).toInt()
                val exact = args.size > 3 && !truthy(args[3])
                val hit = lookup(scalar(args[0]), lines.map { it.firstOrNull() ?: Value.Text("") }, exact)
                lines[hit].getOrNull(index - 1) ?: throw EvalError("#BEZUG!")
            }
            "XLOOKUP" -> {
                val keys = flat(listOf(args[1]))
                val results = flat(listOf(args[2]))
                val hit = runCatching { lookup(scalar(args[0]), keys, exact = true) }.getOrNull()
                if (hit == null) args.getOrNull(3) ?: throw EvalError("#NV") else results.getOrNull(hit) ?: throw EvalError("#BEZUG!")
            }
            "MATCH" -> {
                val type = args.getOrNull(2)?.let { num(it).toInt() } ?: 1
                Value.Num(lookup(scalar(args[0]), flat(listOf(args[1])), exact = type == 0) + 1.0)
            }
            "INDEX" -> {
                val t = args[0] as? Value.Range
                val r = num(args[1]).toInt()
                val c = args.getOrNull(2)?.let { num(it).toInt() }
                val v = when {
                    t == null -> args[0].takeIf { r == 1 }
                    c == null && (t.cols == 1 || t.rows.size == 1) -> t.values.getOrNull(r - 1)
                    else -> t.rows.getOrNull(r - 1)?.getOrNull((c ?: 1) - 1)
                }
                v ?: throw EvalError("#BEZUG!")
            }
            "COUNTIFS", "SUMIFS", "AVERAGEIF", "AVERAGEIFS" -> {
                // SUMIFS/AVERAGEIFS: values first, then pairs of range and criterion; AVERAGEIF: range, criterion, [values].
                val (values, pairs) = when (name) {
                    "COUNTIFS" -> null to args.chunked(2)
                    "AVERAGEIF" -> flat(listOf(args.getOrElse(2) { args[0] })) to listOf(args.take(2))
                    else -> flat(listOf(args[0])) to args.drop(1).chunked(2)
                }
                val cells = pairs.filter { it.size == 2 }.map { flat(listOf(it[0])) to it[1] }
                val n = cells.maxOfOrNull { it.first.size } ?: 0
                val hits = (0 until n).filter { i -> cells.all { (range, crit) -> range.getOrNull(i)?.let { matches(it, crit) } == true } }
                when (name) {
                    "COUNTIFS" -> Value.Num(hits.size.toDouble())
                    "SUMIFS" -> Value.Num(hits.sumOf { (values!!.getOrNull(it) as? Value.Num)?.v ?: 0.0 })
                    else -> hits.mapNotNull { (values!!.getOrNull(it) as? Value.Num)?.v }.let { if (it.isEmpty()) throw EvalError("#DIV/0!") else Value.Num(it.average()) }
                }
            }
            "LEFT" -> Value.Text(text(args[0]).take(args.getOrNull(1)?.let { num(it).toInt() } ?: 1))
            "RIGHT" -> Value.Text(text(args[0]).takeLast(args.getOrNull(1)?.let { num(it).toInt() } ?: 1))
            "MID" -> text(args[0]).let { t -> val from = (num(args[1]).toInt() - 1).coerceIn(0, t.length); Value.Text(t.substring(from, (from + num(args[2]).toInt()).coerceIn(from, t.length))) }
            "TRIM" -> Value.Text(text(args[0]).trim().replace(Regex(" +"), " "))
            "SUBSTITUTE" -> Value.Text(text(args[0]).replace(text(args[1]), text(args[2])))
            "FIND", "SEARCH" -> {
                val i = text(args[1]).indexOf(text(args[0]), (args.getOrNull(2)?.let { num(it).toInt() } ?: 1) - 1, ignoreCase = name == "SEARCH")
                if (i < 0) throw EvalError("#WERT!") else Value.Num(i + 1.0)
            }
            "TEXTJOIN" -> Value.Text(flat(args.drop(2)).map { text(it) }.filter { !truthy(args[1]) || it.isNotEmpty() }.joinToString(text(args[0])))
            "PROPER" -> Value.Text(text(args[0]).lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } })
            "REPT" -> Value.Text(text(args[0]).repeat(num(args[1]).toInt().coerceIn(0, 1000)))
            "VALUE" -> Value.Num(num(Value.Text(text(args[0]).trim())))
            "TEXT" -> {
                val pattern = text(args[1])
                val v = scalar(args[0])
                when {
                    v is Value.Text && numberOrDate(v.v) == null -> v
                    Regex("[TtJjDdYyHh]|[Mm]{1,4}(?![0#])").containsMatchIn(pattern.replace(Regex("\"[^\"]*\""), "")) -> Value.Text(formatDate(num(v), pattern))
                    else -> Value.Text(formatPattern(num(v), pattern))
                }
            }
            "PRODUCT" -> Value.Num(numbers(args).fold(1.0) { a, b -> a * b })
            "SUMPRODUCT" -> {
                val lists = args.map { flat(listOf(it)) }
                val n = lists.minOfOrNull { it.size } ?: 0
                Value.Num((0 until n).sumOf { i -> lists.fold(1.0) { acc, l -> acc * when (val x = l[i]) { is Value.Num -> x.v; is Value.Bool -> if (x.v) 1.0 else 0.0; else -> 0.0 } } })
            }
            "RANK" -> {
                val x = num(args[0])
                val list = numbers(listOf(args[1]))
                val asc = args.getOrNull(2)?.let { num(it) != 0.0 } == true
                if (x !in list) throw EvalError("#NV")
                Value.Num(1.0 + list.count { if (asc) it < x else it > x })
            }
            "LARGE", "SMALL" -> {
                val list = numbers(listOf(args[0])).let { if (name == "LARGE") it.sortedDescending() else it.sorted() }
                list.getOrNull(num(args[1]).toInt() - 1)?.let { Value.Num(it) } ?: throw EvalError("#ZAHL!")
            }
            "VAR", "STDEV.P" -> numbers(args).let { l ->
                val sample = name == "VAR"
                if (l.size < (if (sample) 2 else 1)) throw EvalError("#DIV/0!")
                val m = l.average()
                val v = l.sumOf { (it - m) * (it - m) } / (if (sample) l.size - 1 else l.size)
                Value.Num(if (sample) v else sqrt(v))
            }
            "MODE" -> numbers(args).groupingBy { it }.eachCount().filterValues { it > 1 }.maxByOrNull { it.value }?.let { Value.Num(it.key) } ?: throw EvalError("#NV")
            "PERCENTILE" -> {
                val l = numbers(listOf(args[0])).sorted()
                if (l.isEmpty()) throw EvalError("#ZAHL!")
                val k = num(args[1]).coerceIn(0.0, 1.0) * (l.size - 1)
                val lo = floor(k).toInt()
                val hi = ceil(k).toInt()
                Value.Num(l[lo] + (l[hi] - l[lo]) * (k - lo))
            }
            "CORREL" -> {
                val a = flat(listOf(args[0]))
                val b = flat(listOf(args[1]))
                val pairs = a.indices.mapNotNull { i -> val x = (a[i] as? Value.Num)?.v; val y = (b.getOrNull(i) as? Value.Num)?.v; if (x != null && y != null) x to y else null }
                if (pairs.size < 2) throw EvalError("#DIV/0!")
                val mx = pairs.map { it.first }.average()
                val my = pairs.map { it.second }.average()
                val cov = pairs.sumOf { (x, y) -> (x - mx) * (y - my) }
                val sx = sqrt(pairs.sumOf { (x, _) -> (x - mx) * (x - mx) })
                val sy = sqrt(pairs.sumOf { (_, y) -> (y - my) * (y - my) })
                if (sx == 0.0 || sy == 0.0) throw EvalError("#DIV/0!") else Value.Num(cov / (sx * sy))
            }
            "COUNTBLANK" -> Value.Num(flat(args).count { it is Value.Text && it.v.isEmpty() }.toDouble())
            "PI" -> Value.Num(Math.PI)
            // ── Dates ──
            "DATE" -> {
                val y = num(args[0]).toInt().let { if (it in 0..1899) it + 1900 else it }
                if (y !in 1900..9999) throw EvalError("#ZAHL!")
                dateNum(LocalDate.of(y, 1, 1).plusMonths(floor(num(args[1])).toLong() - 1).plusDays(floor(num(args[2])).toLong() - 1))
            }
            "DAY" -> Value.Num(date(args[0]).dayOfMonth.toDouble())
            "MONTH" -> Value.Num(date(args[0]).monthValue.toDouble())
            "YEAR" -> Value.Num(date(args[0]).year.toDouble())
            "WEEKDAY" -> {
                val dow = date(args[0]).dayOfWeek.value // 1 = Monday … 7 = Sunday
                val type = args.getOrNull(1)?.let { num(it).toInt() } ?: 1
                Value.Num(
                    when (type) {
                        1, 17 -> dow % 7 + 1
                        2, 11 -> dow
                        3 -> dow - 1
                        in 12..16 -> (dow - (type - 10) + 7) % 7 + 1
                        else -> throw EvalError("#ZAHL!")
                    }.toDouble(),
                )
            }
            "DATEDIF" -> {
                val a = date(args[0])
                val b = date(args[1])
                if (b.isBefore(a)) throw EvalError("#ZAHL!")
                val p = Period.between(a, b)
                Value.Num(
                    when (text(args[2]).trim().uppercase()) {
                        "Y" -> p.years.toLong()
                        "M" -> ChronoUnit.MONTHS.between(a, b)
                        "D" -> ChronoUnit.DAYS.between(a, b)
                        "MD" -> p.days.toLong()
                        "YM" -> p.months.toLong()
                        "YD" -> {
                            var s = a.withYear(b.year)
                            if (s.isAfter(b)) s = a.withYear(b.year - 1)
                            ChronoUnit.DAYS.between(s, b)
                        }
                        else -> throw EvalError("#ZAHL!")
                    }.toDouble(),
                )
            }
            "DAYS" -> Value.Num(floor(num(args[0])) - floor(num(args[1])))
            "NETWORKDAYS" -> {
                val a = date(args[0])
                val b = date(args[1])
                val hol = holidays(args.getOrNull(2))
                val (from, to) = if (b.isBefore(a)) b to a else a to b
                if (ChronoUnit.DAYS.between(from, to) > 100_000) throw EvalError("#ZAHL!")
                var n = 0
                var d = from
                while (!d.isAfter(to)) { if (workday(d, hol)) n++; d = d.plusDays(1) }
                Value.Num(if (b.isBefore(a)) -n.toDouble() else n.toDouble())
            }
            "WORKDAY" -> {
                var d = date(args[0])
                var left = num(args[1]).toLong()
                if (abs(left) > 50_000) throw EvalError("#ZAHL!")
                val hol = holidays(args.getOrNull(2))
                val step = if (left < 0) -1L else 1L
                while (left != 0L) { d = d.plusDays(step); if (workday(d, hol)) left -= step }
                dateNum(d)
            }
            "EDATE" -> dateNum(date(args[0]).plusMonths(num(args[1]).toLong()))
            "EOMONTH" -> date(args[0]).plusMonths(num(args[1]).toLong()).let { dateNum(it.withDayOfMonth(it.lengthOfMonth())) }
            "ISOWEEKNUM" -> Value.Num(date(args[0]).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR).toDouble())
            "WEEKNUM" -> {
                val d = date(args[0])
                // Without type ISO weeks (as German calendars count them); Excel's types 1, 2 and 11–17 count from 1 January.
                val type = args.getOrNull(1)?.let { num(it).toInt() } ?: 21
                if (type == 21) Value.Num(d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR).toDouble())
                else {
                    val start = when (type) { 1, 17 -> 7; 2, 11 -> 1; in 12..16 -> type - 10; else -> throw EvalError("#ZAHL!") }
                    val jan1 = d.withDayOfYear(1)
                    val offset = (jan1.dayOfWeek.value - start + 7) % 7
                    Value.Num(((d.dayOfYear - 1 + offset) / 7 + 1).toDouble())
                }
            }
            // ── Rounding ──
            "MROUND" -> {
                val x = num(args[0])
                val m = num(args[1])
                when {
                    m == 0.0 -> Value.Num(0.0)
                    x != 0.0 && (x < 0) != (m < 0) -> throw EvalError("#ZAHL!")
                    else -> Value.Num(Math.round(abs(x / m) + 1e-12) * abs(m) * if (x < 0) -1 else 1)
                }
            }
            "CEILING", "FLOOR" -> {
                val x = num(args[0])
                val m = args.getOrNull(1)?.let { num(it) } ?: 1.0
                if (m == 0.0) { if (name == "FLOOR") throw EvalError("#DIV/0!") else Value.Num(0.0) }
                else {
                    val q = x / m
                    val r = if (name == "CEILING") ceil(q - 1e-12) else floor(q + 1e-12)
                    Value.Num(r * m)
                }
            }
            // ── Ranges and dynamic arrays ──
            "ROWS" -> Value.Num((args[0] as? Value.Range)?.rows?.size?.toDouble() ?: 1.0)
            "COLUMNS" -> Value.Num((args[0] as? Value.Range)?.cols?.toDouble() ?: 1.0)
            "UNIQUE" -> {
                val byCol = args.getOrNull(1)?.let(::truthy) == true
                val once = args.getOrNull(2)?.let(::truthy) == true
                val src = range(args[0]).let { if (byCol) transpose(it) else it }
                val groups = LinkedHashMap<String, MutableList<List<Value>>>()
                src.rows.forEach { r -> groups.getOrPut(r.joinToString("\u0001") { key(it) }) { mutableListOf() }.add(r) }
                val kept = groups.values.filter { !once || it.size == 1 }.map { it.first() }
                if (kept.isEmpty()) throw EvalError("#KALK!")
                Value.Range(kept.flatten(), src.cols).let { if (byCol) transpose(it) else it }
            }
            "FILTER" -> {
                val src = range(args[0])
                val include = flat(listOf(args[1]))
                val rows = src.rows
                val kept: Value.Range = when {
                    include.size == rows.size -> Value.Range(rows.filterIndexed { i, _ -> truthy(include[i]) }.flatten(), src.cols)
                    include.size == src.cols -> {
                        val cols = (0 until src.cols).filter { truthy(include[it]) }
                        Value.Range(rows.flatMap { r -> cols.map { r[it] } }, cols.size.coerceAtLeast(1))
                    }
                    else -> throw EvalError("#WERT!")
                }
                if (kept.values.isEmpty()) args.getOrNull(2) ?: throw EvalError("#KALK!") else kept
            }
            "SORT" -> {
                val byCol = args.getOrNull(3)?.let(::truthy) == true
                val src = range(args[0]).let { if (byCol) transpose(it) else it }
                val index = (args.getOrNull(1)?.let { num(it).toInt() } ?: 1) - 1
                val desc = args.getOrNull(2)?.let { num(it) < 0 } == true
                if (index !in 0 until src.cols) throw EvalError("#WERT!")
                val sorted = src.rows.sortedWith { a, b -> compareCells(a[index], b[index]).let { if (desc) -it else it } }
                Value.Range(sorted.flatten(), src.cols).let { if (byCol) transpose(it) else it }
            }
            "SEQUENCE" -> {
                val rows = num(args[0]).toInt()
                val cols = args.getOrNull(1)?.let { num(it).toInt() } ?: 1
                val start = args.getOrNull(2)?.let { num(it) } ?: 1.0
                val step = args.getOrNull(3)?.let { num(it) } ?: 1.0
                if (rows < 1 || cols < 1 || rows * cols > 100_000) throw EvalError("#WERT!")
                Value.Range((0 until rows * cols).map { Value.Num(start + it * step) }, cols)
            }
            else -> throw EvalError("#NAME?")
        }

        /** Like [scalar], but an error is a value (for ISTFEHLER). */
        fun scalarOrErr(v: Value): Value = when (v) {
            is Value.Range -> v.values.firstOrNull() ?: Value.Text("")
            else -> v
        }

        /**
         * Position of [key] in [keys]: exact (case-insensitive for text) or, like Excel's approximate match, the last
         * key that is not greater than [key] in a sorted column.
         */
        fun lookup(key: Value, keys: List<Value>, exact: Boolean): Int {
            fun same(a: Value, b: Value) = if (a is Value.Num && b is Value.Num) a.v == b.v else format(a).equals(format(b), ignoreCase = true)
            val i = keys.indexOfFirst { same(it, key) }
            if (i >= 0 || exact) return if (i >= 0) i else throw EvalError("#NV")
            var best = -1
            keys.forEachIndexed { j, k -> if (k is Value.Num && key is Value.Num && k.v <= key.v) best = j }
            if (best < 0) throw EvalError("#NV")
            return best
        }
    }

    /** Formulas offered in the editor (German names, like German Excel). */
    val FUNCTIONS = listOf(
        "SUMME" to "Summe eines Bereichs, z.B. =SUMME(B2:B10)",
        "MITTELWERT" to "Durchschnitt, z.B. =MITTELWERT(B2:B10)",
        "MIN" to "Kleinster Wert",
        "MAX" to "Größter Wert",
        "ANZAHL" to "Anzahl der Zahlen im Bereich",
        "MEDIAN" to "Median",
        "STABW" to "Standardabweichung (Stichprobe)",
        "RUNDEN" to "Runden, z.B. =RUNDEN(B2;1)",
        "VRUNDEN" to "Auf ein Vielfaches runden, z.B. =VRUNDEN(B2;0,5)",
        "OBERGRENZE" to "Aufrunden auf ein Vielfaches, z.B. =OBERGRENZE(B2;5)",
        "UNTERGRENZE" to "Abrunden auf ein Vielfaches",
        "WENN" to "Bedingung, z.B. =WENN(B2>=50;\"bestanden\";\"nicht bestanden\")",
        "ZÄHLENWENN" to "Zählen mit Bedingung, z.B. =ZÄHLENWENN(C2:C30;\"ja\")",
        "SUMMEWENN" to "Summe mit Bedingung",
        "VERKETTEN" to "Texte verbinden",
        "SVERWEIS" to "Nachschlagen, z.B. =SVERWEIS(A2;E2:F20;2;FALSCH)",
        "XVERWEIS" to "Nachschlagen: =XVERWEIS(Suchwert;Suchbereich;Ergebnisbereich;\"nicht gefunden\")",
        "INDEX" to "Wert an Position, z.B. =INDEX(B2:D10;3;2)",
        "VERGLEICH" to "Position eines Werts, z.B. =VERGLEICH(\"Max\";A2:A30;0)",
        "WENNFEHLER" to "Ersatz bei Fehler, z.B. =WENNFEHLER(B2/C2;0)",
        "WENNS" to "Mehrere Bedingungen, z.B. =WENNS(B2>=90;1;B2>=75;2;WAHR;3)",
        "ZÄHLENWENNS" to "Zählen mit mehreren Bedingungen",
        "SUMMEWENNS" to "Summe mit mehreren Bedingungen: =SUMMEWENNS(Summe;Bereich1;Krit1;…)",
        "MITTELWERTWENN" to "Mittelwert mit Bedingung",
        "EINDEUTIG" to "Liste ohne Doppelte, füllt die Zellen darunter: =EINDEUTIG(A2:A30)",
        "FILTER" to "Passende Zeilen, füllt die Zellen darunter: =FILTER(A2:C30;C2:C30>=4)",
        "SORTIEREN" to "Sortierte Liste: =SORTIEREN(A2:B30;2;-1) (nach Spalte 2 absteigend)",
        "SEQUENZ" to "Zahlenfolge: =SEQUENZ(10) ergibt 1 bis 10",
        "ZEILE" to "Zeilennummer, z.B. =ZEILE() oder =ZEILE(B5)",
        "SPALTE" to "Spaltennummer, z.B. =SPALTE(C1)",
        "LINKS" to "Erste Zeichen eines Texts, z.B. =LINKS(A2;3)",
        "RECHTS" to "Letzte Zeichen eines Texts",
        "TEIL" to "Teil eines Texts: =TEIL(A2;Start;Anzahl)",
        "GLÄTTEN" to "Überzählige Leerzeichen entfernen",
        "WECHSELN" to "Text ersetzen: =WECHSELN(A2;\"alt\";\"neu\")",
        "TEXTVERKETTEN" to "Texte mit Trennzeichen: =TEXTVERKETTEN(\", \";WAHR;A2:A9)",
        "TEXT" to "Zahl oder Datum formatiert, z.B. =TEXT(B2;\"0,00\"), =TEXT(B2;\"0 %\") oder =TEXT(A2;\"TT.MM.JJJJ\")",
        "PRODUKT" to "Produkt",
        "SUMMENPRODUKT" to "Summe der Produkte, z.B. gewichtete Noten",
        "RANG" to "Rangplatz, z.B. =RANG(B2;B$2:B$30)",
        "KGRÖSSTE" to "k-größter Wert",
        "KKLEINSTE" to "k-kleinster Wert",
        "VARIANZ" to "Varianz (Stichprobe)",
        "STABW.N" to "Standardabweichung (Grundgesamtheit)",
        "MODALWERT" to "Häufigster Wert",
        "QUANTIL" to "Quantil, z.B. =QUANTIL(B2:B30;0,25)",
        "KORREL" to "Korrelation (Pearson) zweier Spalten",
        "ANZAHLLEEREZELLEN" to "Leere Zellen zählen",
        "ISTLEER" to "Ist die Zelle leer?",
        "ISTZAHL" to "Ist es eine Zahl?",
        "HEUTE" to "Heutiges Datum, z.B. =HEUTE()+14",
        "DATUM" to "Datum aus Jahr, Monat, Tag: =DATUM(2026;4;3)",
        "TAG" to "Tag eines Datums",
        "MONAT" to "Monat eines Datums",
        "JAHR" to "Jahr eines Datums",
        "WOCHENTAG" to "Wochentag: =WOCHENTAG(A2;2) (Montag = 1)",
        "KALENDERWOCHE" to "Kalenderwoche (nach ISO), z.B. =KALENDERWOCHE(A2)",
        "DATEDIF" to "Abstand zweier Daten: =DATEDIF(A2;B2;\"Y\") (Jahre), \"M\", \"D\"",
        "NETTOARBEITSTAGE" to "Arbeitstage zwischen zwei Daten: =NETTOARBEITSTAGE(A2;B2;Feiertage)",
        "ARBEITSTAG" to "Datum nach n Arbeitstagen: =ARBEITSTAG(A2;10)",
        "EDATUM" to "Datum n Monate später: =EDATUM(A2;6)",
        "MONATSENDE" to "Letzter Tag des Monats: =MONATSENDE(A2;0)",
    )

    /**
     * The formula moved by [dRows] rows and [dCols] columns, as when it is filled down or right in Excel: references
     * without "$" move along, "$B$2" stays; text in quotes and sheet names stay as they are.
     */
    fun shift(formula: String, dRows: Int, dCols: Int): String {
        if (!formula.trimStart().startsWith("=")) return formula
        val out = StringBuilder()
        var i = 0
        var inString = false
        var inSheet = false
        val ref = Regex("^(\\$?)([A-Za-z]{1,3})(\\$?)(\\d{1,6})")
        while (i < formula.length) {
            val ch = formula[i]
            if (ch == '"' && !inSheet) { inString = !inString; out.append(ch); i++; continue }
            if (ch == '\'' && !inString) { inSheet = !inSheet; out.append(ch); i++; continue }
            val prev = formula.getOrNull(i - 1)
            val m = if (!inString && !inSheet && (prev == null || !(prev.isLetterOrDigit() || prev == '_' || prev == '.'))) ref.find(formula.substring(i)) else null
            val after = m?.let { formula.getOrNull(i + it.value.length) }
            if (m != null && after != '(' && after != '!' && (after == null || !(after.isLetterOrDigit() || after == '_' || after == '.'))) {
                val (colAbs, col, rowAbs, row) = m.destructured
                val c = if (colAbs.isEmpty()) colName((parseRef(col + "1")!!.second + dCols).coerceAtLeast(0)) else col.uppercase()
                val r = if (rowAbs.isEmpty()) (row.toInt() + dRows).coerceAtLeast(1).toString() else row
                out.append(colAbs).append(c).append(rowAbs).append(r)
                i += m.value.length
            } else {
                out.append(ch)
                i++
            }
        }
        return out.toString()
    }

    /** Same as [shift]: the formula copied [dRows] rows down and [dCols] columns to the right. */
    fun shiftFormula(formula: String, dRows: Int, dCols: Int): String = shift(formula, dRows, dCols)

    /** Rounds for display in charts and summaries. */
    fun short(d: Double): String = if (abs(d - d.roundToLong()) < 1e-9) d.roundToLong().toString() else formatNumber((d * 100).roundToLong() / 100.0)
}
