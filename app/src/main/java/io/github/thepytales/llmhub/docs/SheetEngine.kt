package io.github.thepytales.llmhub.docs

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * A sheet of the table editor: a Markdown table whose cells may hold formulas like in Excel ("=SUMME(B2:B9)",
 * "=A2*1,19", "=WENN(C2>=50;"bestanden";"nicht bestanden")"). German and English function names both work;
 * [toExcel] gives the English form Excel stores in .xlsx files.
 */
object SheetEngine {
    /** Cell address like "B12" → (row 11, column 1), zero-based. */
    fun parseRef(ref: String): Pair<Int, Int>? {
        val m = Regex("^\\$?([A-Za-z]{1,3})\\$?(\\d{1,6})$").find(ref.trim()) ?: return null
        val col = m.groupValues[1].uppercase().fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
        val row = m.groupValues[2].toInt() - 1
        return if (row < 0 || col < 0) null else row to col
    }

    fun colName(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) { val r = (n - 1) % 26; sb.insert(0, 'A' + r); n = (n - 1) / 26 }
        return sb.toString()
    }

    fun ref(row: Int, col: Int) = colName(col) + (row + 1)

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
        data class Num(val v: Double) : Value
        data class Text(val v: String) : Value
        data class Bool(val v: Boolean) : Value
        data class Err(val code: String) : Value
        data class Range(val values: List<Value>) : Value
    }

    /** Evaluates all cells of [rows]; formulas give their result, other cells stay as they are. */
    class Sheet(val rows: List<List<String>>) {
        private val cache = HashMap<Pair<Int, Int>, Value>()
        private val visiting = HashSet<Pair<Int, Int>>()

        fun raw(r: Int, c: Int): String = rows.getOrNull(r)?.getOrNull(c).orEmpty()

        fun value(r: Int, c: Int): Value {
            val key = r to c
            cache[key]?.let { return it }
            if (!visiting.add(key)) return Value.Err("#ZIRKEL!")
            val raw = raw(r, c).trim()
            val v = when {
                raw.startsWith("=") && raw.length > 1 -> runCatching { Parser(raw.substring(1), this).parse() }.getOrElse { Value.Err(if (it is EvalError) it.code else "#FEHLER!") }
                raw.isEmpty() -> Value.Text("")
                else -> XlsxWriter.number(raw)?.let { Value.Num(it) } ?: Value.Text(raw)
            }
            visiting.remove(key)
            cache[key] = v
            return v
        }

        /** What the cell shows: the result of its formula, otherwise its text. */
        fun display(r: Int, c: Int): String {
            val raw = raw(r, c)
            if (!raw.trimStart().startsWith("=")) return raw
            return format(value(r, c))
        }

        /** All rows with formulas replaced by their results (for PDF, Word and the model). */
        fun computed(): List<List<String>> = rows.mapIndexed { r, row -> row.indices.map { c -> display(r, c) } }
    }

    fun format(v: Value): String = when (v) {
        is Value.Num -> formatNumber(v.v)
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

    private class EvalError(val code: String) : RuntimeException(code)

    /** Function names: German → Excel (English). */
    private val NAMES = mapOf(
        "SUMME" to "SUM", "MITTELWERT" to "AVERAGE", "ANZAHL" to "COUNT", "ANZAHL2" to "COUNTA", "RUNDEN" to "ROUND",
        "WENN" to "IF", "UND" to "AND", "ODER" to "OR", "NICHT" to "NOT", "ABRUNDEN" to "ROUNDDOWN", "AUFRUNDEN" to "ROUNDUP",
        "WURZEL" to "SQRT", "POTENZ" to "POWER", "ZÄHLENWENN" to "COUNTIF", "ZAEHLENWENN" to "COUNTIF", "SUMMEWENN" to "SUMIF",
        "STABW" to "STDEV", "STABW.S" to "STDEV", "VERKETTEN" to "CONCATENATE", "LÄNGE" to "LEN", "LAENGE" to "LEN",
        "GROSS" to "UPPER", "KLEIN" to "LOWER", "GANZZAHL" to "INT", "REST" to "MOD", "MEDIAN" to "MEDIAN", "MIN" to "MIN", "MAX" to "MAX",
        "ABS" to "ABS", "HEUTE" to "TODAY",
    )

    /** The formula as Excel stores it: English function names, "," between arguments, "." as decimal point. */
    fun toExcel(formula: String): String {
        val body = formula.trim().removePrefix("=")
        val out = StringBuilder()
        var i = 0
        var inString = false
        while (i < body.length) {
            val ch = body[i]
            when {
                ch == '"' -> { inString = !inString; out.append(ch); i++ }
                inString -> { out.append(ch); i++ }
                ch.isLetter() -> {
                    var j = i
                    while (j < body.length && (body[j].isLetterOrDigit() || body[j] == '.' || body[j] == '_')) j++
                    val word = body.substring(i, j)
                    val next = body.getOrNull(j)
                    out.append(if (next == '(') NAMES[word.uppercase()] ?: word.uppercase() else word)
                    i = j
                }
                ch == ';' -> { out.append(','); i++ }
                ch == ',' && body.getOrNull(i - 1)?.isDigit() == true && body.getOrNull(i + 1)?.isDigit() == true -> { out.append('.'); i++ }
                else -> { out.append(ch); i++ }
            }
        }
        return out.toString()
    }

    /** Recursive-descent parser that evaluates while it reads. */
    private class Parser(val src: String, val sheet: Sheet) {
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

        fun comparison(): Value {
            var a = concat()
            while (true) {
                val op = listOf("<=", ">=", "<>", "=", "<", ">").firstOrNull { eat(it) } ?: return a
                val b = concat()
                a = Value.Bool(compare(a, b, op))
            }
        }

        fun compare(a: Value, b: Value, op: String): Boolean {
            val x = scalar(a)
            val y = scalar(b)
            val c = if (x is Value.Num && y is Value.Num) x.v.compareTo(y.v) else text(x).compareTo(text(y), ignoreCase = true)
            return when (op) { "<=" -> c <= 0; ">=" -> c >= 0; "<>" -> c != 0; "=" -> c == 0; "<" -> c < 0; else -> c > 0 }
        }

        fun concat(): Value {
            var a = additive()
            while (eat("&")) a = Value.Text(text(a) + text(additive()))
            return a
        }

        fun additive(): Value {
            var a = term()
            while (true) {
                a = when {
                    eat("+") -> Value.Num(num(a) + num(term()))
                    eat("-") -> Value.Num(num(a) - num(term()))
                    else -> return a
                }
            }
        }

        fun term(): Value {
            var a = power()
            while (true) {
                a = when {
                    eat("*") -> Value.Num(num(a) * num(power()))
                    eat("/") -> { val d = num(power()); if (d == 0.0) throw EvalError("#DIV/0!"); Value.Num(num(a) / d) }
                    else -> return a
                }
            }
        }

        fun power(): Value {
            val a = unary()
            return if (eat("^")) Value.Num(num(a).pow(num(power()))) else a
        }

        fun unary(): Value = when {
            eat("-") -> Value.Num(-num(unary()))
            eat("+") -> unary()
            else -> percent(primary())
        }

        fun percent(v: Value): Value = if (eat("%")) Value.Num(num(v) / 100) else v

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
            if (ch.isDigit() || ch == '.' || ch == ',') {
                val start = pos
                while (pos < src.length && (src[pos].isDigit() || src[pos] == '.' || src[pos] == ',' && src.getOrNull(pos + 1)?.isDigit() == true)) pos++
                val text = src.substring(start, pos)
                return Value.Num(XlsxWriter.number(text) ?: throw EvalError("#ZAHL!"))
            }
            if (ch.isLetter() || ch == '$') {
                val start = pos
                while (pos < src.length && (src[pos].isLetterOrDigit() || src[pos] == '$' || src[pos] == '.' || src[pos] == '_')) pos++
                val word = src.substring(start, pos)
                if (peek() == '(') {
                    pos++
                    val args = mutableListOf<Value>()
                    if (!eat(")")) {
                        do { args += comparison() } while (eat(";") || eat(","))
                        if (!eat(")")) throw EvalError("#FEHLER!")
                    }
                    return call(NAMES[word.uppercase()] ?: word.uppercase(), args)
                }
                when (word.uppercase()) {
                    "WAHR", "TRUE" -> return Value.Bool(true)
                    "FALSCH", "FALSE" -> return Value.Bool(false)
                }
                val from = parseRef(word) ?: throw EvalError("#NAME?")
                if (eat(":")) {
                    val start2 = pos
                    while (pos < src.length && (src[pos].isLetterOrDigit() || src[pos] == '$')) pos++
                    val to = parseRef(src.substring(start2, pos)) ?: throw EvalError("#BEZUG!")
                    val values = mutableListOf<Value>()
                    for (r in minOf(from.first, to.first)..maxOf(from.first, to.first)) {
                        for (c in minOf(from.second, to.second)..maxOf(from.second, to.second)) values += sheet.value(r, c)
                    }
                    return Value.Range(values)
                }
                return sheet.value(from.first, from.second)
            }
            throw EvalError("#FEHLER!")
        }

        fun scalar(v: Value): Value = when (v) {
            is Value.Range -> v.values.firstOrNull() ?: Value.Text("")
            is Value.Err -> throw EvalError(v.code)
            else -> v
        }

        fun num(v: Value): Double = when (val s = scalar(v)) {
            is Value.Num -> s.v
            is Value.Bool -> if (s.v) 1.0 else 0.0
            is Value.Text -> if (s.v.isBlank()) 0.0 else XlsxWriter.number(s.v) ?: throw EvalError("#WERT!")
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

        fun matches(v: Value, criterion: Value): Boolean {
            val c = text(criterion).trim()
            val op = listOf("<=", ">=", "<>", "=", "<", ">").firstOrNull { c.startsWith(it) }
            val rest = if (op != null) c.removePrefix(op) else c
            val target = XlsxWriter.number(rest)?.let { Value.Num(it) } ?: Value.Text(rest)
            return compare(v, target, op ?: "=")
        }

        fun round(x: Double, digits: Int, mode: Int): Double {
            val f = 10.0.pow(digits)
            val y = x * f
            val r = when (mode) { 0 -> Math.round(y).toDouble(); -1 -> if (y < 0) kotlin.math.ceil(y) else floor(y); else -> if (y < 0) floor(y) else kotlin.math.ceil(y) }
            return r / f
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
            "TODAY" -> Value.Text(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")))
            else -> throw EvalError("#NAME?")
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
        "WENN" to "Bedingung, z.B. =WENN(B2>=50;\"bestanden\";\"nicht bestanden\")",
        "ZÄHLENWENN" to "Zählen mit Bedingung, z.B. =ZÄHLENWENN(C2:C30;\"ja\")",
        "SUMMEWENN" to "Summe mit Bedingung",
        "VERKETTEN" to "Texte verbinden",
    )

    /** Rounds for display in charts and summaries. */
    fun short(d: Double): String = if (abs(d - d.roundToLong()) < 1e-9) d.roundToLong().toString() else formatNumber((d * 100).roundToLong() / 100.0)
}
