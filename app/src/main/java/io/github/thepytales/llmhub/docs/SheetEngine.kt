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
        /** Cells of a range row by row; [cols] is its width (for SVERWEIS, INDEX …). */
        data class Range(val values: List<Value>, val cols: Int = 1) : Value {
            val rows: List<List<Value>> get() = values.chunked(cols.coerceAtLeast(1))
        }
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
        "SVERWEIS" to "VLOOKUP", "WVERWEIS" to "HLOOKUP", "XVERWEIS" to "XLOOKUP", "INDEX" to "INDEX", "VERGLEICH" to "MATCH",
        "WENNFEHLER" to "IFERROR", "WENNNV" to "IFNA", "WENNS" to "IFS", "ZÄHLENWENNS" to "COUNTIFS", "ZAEHLENWENNS" to "COUNTIFS",
        "SUMMEWENNS" to "SUMIFS", "MITTELWERTWENN" to "AVERAGEIF", "MITTELWERTWENNS" to "AVERAGEIFS", "LINKS" to "LEFT",
        "RECHTS" to "RIGHT", "TEIL" to "MID", "GLÄTTEN" to "TRIM", "GLAETTEN" to "TRIM", "WECHSELN" to "SUBSTITUTE", "FINDEN" to "FIND",
        "SUCHEN" to "SEARCH", "TEXTVERKETTEN" to "TEXTJOIN", "GROSS2" to "PROPER", "WIEDERHOLEN" to "REPT", "WERT" to "VALUE",
        "PRODUKT" to "PRODUCT", "RANG" to "RANK", "RANG.GLEICH" to "RANK", "KGRÖSSTE" to "LARGE", "KGROESSTE" to "LARGE", "KKLEINSTE" to "SMALL",
        "VARIANZ" to "VAR", "VAR.S" to "VAR", "STABW.N" to "STDEV.P", "ANZAHLLEEREZELLEN" to "COUNTBLANK", "SUMMENPRODUKT" to "SUMPRODUCT",
        "ISTLEER" to "ISBLANK", "ISTZAHL" to "ISNUMBER", "ISTTEXT" to "ISTEXT", "ISTFEHLER" to "ISERROR", "JETZT" to "NOW",
        "MODALWERT" to "MODE", "KORREL" to "CORREL", "QUANTIL" to "PERCENTILE", "VERKETTEN2" to "CONCAT",
    )

    /** Functions newer than Excel 2007: in .xlsx files they carry the prefix "_xlfn.". */
    private val FUTURE = setOf("XLOOKUP", "IFS", "TEXTJOIN", "IFNA", "CONCAT", "STDEV.P")

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
                    out.append(if (next == '(') (NAMES[word.uppercase()] ?: word.uppercase()).let { if (it in FUTURE) "_xlfn.$it" else it } else word)
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
            // An empty cell counts as 0 when compared with a number (as in Excel: =WENN(B5=0;…)).
            fun blankAsZero(v: Value, other: Value) = if (v is Value.Text && v.v.isEmpty() && other is Value.Num) Value.Num(0.0) else v
            val x = blankAsZero(scalar(a), scalar(b))
            val y = blankAsZero(scalar(b), scalar(a))
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
                        do { args += argument() } while (eat(";") || eat(","))
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
                    return Value.Range(values, abs(to.second - from.second) + 1)
                }
                return sheet.value(from.first, from.second)
            }
            throw EvalError("#FEHLER!")
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
            "NOW" -> Value.Text(java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))
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
                val n = num(args[0])
                val pattern = text(args[1]).trim()
                val pct = pattern.endsWith("%")
                val body = pattern.removeSuffix("%").trim()
                val sep = body.indexOfLast { it == ',' || it == '.' }
                val decimals = if (sep >= 0 && body.substring(sep + 1).all { it == '0' || it == '#' }) body.length - sep - 1 else 0
                val grouped = Regex("#[.,]##").containsMatchIn(body)
                val v = if (pct) n * 100 else n
                Value.Text(String.format(Locale.GERMANY, if (grouped) "%,.${decimals}f" else "%.${decimals}f", v) + if (pct) " %" else "")
            }
            "PRODUCT" -> Value.Num(numbers(args).fold(1.0) { a, b -> a * b })
            "SUMPRODUCT" -> {
                val lists = args.map { flat(listOf(it)) }
                val n = lists.minOfOrNull { it.size } ?: 0
                Value.Num((0 until n).sumOf { i -> lists.fold(1.0) { acc, l -> acc * ((l[i] as? Value.Num)?.v ?: 0.0) } })
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
                val hi = kotlin.math.ceil(k).toInt()
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
        "LINKS" to "Erste Zeichen eines Texts, z.B. =LINKS(A2;3)",
        "RECHTS" to "Letzte Zeichen eines Texts",
        "TEIL" to "Teil eines Texts: =TEIL(A2;Start;Anzahl)",
        "GLÄTTEN" to "Überzählige Leerzeichen entfernen",
        "WECHSELN" to "Text ersetzen: =WECHSELN(A2;\"alt\";\"neu\")",
        "TEXTVERKETTEN" to "Texte mit Trennzeichen: =TEXTVERKETTEN(\", \";WAHR;A2:A9)",
        "TEXT" to "Zahl formatiert, z.B. =TEXT(B2;\"0,00\") oder =TEXT(B2;\"0%\")",
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
        "HEUTE" to "Heutiges Datum",
    )

    /**
     * The formula moved by [dRows] rows and [dCols] columns, as when it is filled down or right in Excel: references
     * without "$" move along, "$B$2" stays.
     */
    fun shift(formula: String, dRows: Int, dCols: Int): String {
        if (!formula.trimStart().startsWith("=")) return formula
        val out = StringBuilder()
        var i = 0
        var inString = false
        val ref = Regex("^(\\$?)([A-Za-z]{1,3})(\\$?)(\\d{1,6})")
        while (i < formula.length) {
            val ch = formula[i]
            if (ch == '"') { inString = !inString; out.append(ch); i++; continue }
            val prev = formula.getOrNull(i - 1)
            val m = if (!inString && (prev == null || !(prev.isLetterOrDigit() || prev == '_' || prev == '.'))) ref.find(formula.substring(i)) else null
            val after = m?.let { formula.getOrNull(i + it.value.length) }
            if (m != null && after != '(' && (after == null || !after.isLetterOrDigit())) {
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

    /** Rounds for display in charts and summaries. */
    fun short(d: Double): String = if (abs(d - d.roundToLong()) < 1e-9) d.roundToLong().toString() else formatNumber((d * 100).roundToLong() / 100.0)
}
