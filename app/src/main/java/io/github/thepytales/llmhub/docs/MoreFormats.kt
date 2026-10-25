package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.ui.Block
import io.github.thepytales.llmhub.ui.inlineSegments
import io.github.thepytales.llmhub.ui.parseBlocks
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private fun plain(text: String) = inlineSegments(text).joinToString("") { it.text }

/** Charts (```chart blocks) become a caption and their data table, for formats that cannot draw them. */
object ChartBlocks {
    private val BLOCK = Regex("```chart\\s*\\n([\\s\\S]*?)\\n```")

    fun asTables(markdown: String): String = BLOCK.replace(markdown) { m ->
        val spec = ChartSpec.parse(m.groupValues[1]) ?: return@replace m.value
        "**Abbildung: ${spec.title.ifBlank { "Diagramm" }}** (Daten)\n\n" + spec.table()
    }
}

/** Markdown tables as Excel workbook: one sheet per table, named after the heading above it; numbers stay numbers. */
object XlsxWriter {
    data class Sheet(val name: String, val rows: List<List<String>>)

    fun sheets(title: String, markdown: String): List<Sheet> {
        val out = mutableListOf<Sheet>()
        var heading = title.ifBlank { "Tabelle" }
        val loose = mutableListOf<List<String>>()
        for (b in parseBlocks(ChartBlocks.asTables(markdown))) {
            when (b) {
                is Block.Heading -> heading = plain(b.text)
                is Block.Table -> out += Sheet(heading, b.rows.map { r -> r.map(::plain) })
                is Block.Paragraph -> loose += listOf(plain(b.text))
                is Block.Item -> loose += listOf(plain(b.text))
                else -> {}
            }
        }
        if (out.isEmpty()) out += Sheet(title.ifBlank { "Text" }, loose.ifEmpty { listOf(listOf("")) })
        // Sheet names: at most 31 characters, unique, without []:*?/\
        val used = mutableSetOf<String>()
        return out.map { s ->
            var n = s.name.replace(Regex("[\\[\\]:*?/\\\\]"), " ").trim().take(28).ifBlank { "Tabelle" }
            var k = 2
            val base = n
            while (!used.add(n.lowercase())) n = "$base ${k++}"
            s.copy(name = n)
        }
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")

    private fun col(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) { val r = (n - 1) % 26; sb.insert(0, ('A' + r)); n = (n - 1) / 26 }
        return sb.toString()
    }

    /** "1.234,5" or "12 %" or "3.5" → number (German and English notation). */
    fun number(s: String): Double? {
        val t = s.trim().removeSuffix("%").trim().replace(" ", "").replace("\u00A0", "")
        if (t.isEmpty() || !Regex("^-?[\\d.,]+$").matches(t)) return null
        val normalized = when {
            ',' in t && '.' in t -> if (t.lastIndexOf(',') > t.lastIndexOf('.')) t.replace(".", "").replace(',', '.') else t.replace(",", "")
            ',' in t -> t.replace(',', '.')
            t.count { it == '.' } > 1 -> t.replace(".", "")
            else -> t
        }
        return normalized.toDoubleOrNull()?.let { if (s.trim().endsWith("%")) it / 100 else it }
    }

    fun write(file: File, title: String, markdown: String) {
        val sheets = sheets(title, markdown)
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
            entry("[Content_Types].xml", buildString {
                append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
                append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
                append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
                sheets.indices.forEach { append("<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>") }
                append("</Types>")
            })
            entry("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
            entry("xl/_rels/workbook.xml.rels", buildString {
                append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
                sheets.indices.forEach { append("<Relationship Id=\"rId${it + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>") }
                append("<Relationship Id=\"rIdS\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>")
            })
            entry("xl/workbook.xml", buildString {
                append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>")
                sheets.forEachIndexed { i, s -> append("<sheet name=\"${esc(s.name)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>") }
                append("</sheets></workbook>")
            })
            // Style 1: bold head row with light fill.
            entry("xl/styles.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
                "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFDCE6F5\"/></patternFill></fill></fills>" +
                "<borders count=\"1\"><border/></borders><cellStyleXfs count=\"1\"><xf/></cellStyleXfs>" +
                "<cellXfs count=\"2\"><xf fontId=\"0\" fillId=\"0\"/><xf fontId=\"1\" fillId=\"2\" applyFont=\"1\" applyFill=\"1\"/></cellXfs></styleSheet>")
            sheets.forEachIndexed { si, s ->
                entry("xl/worksheets/sheet${si + 1}.xml", buildString {
                    append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                    // Freeze the head row; columns about as wide as their longest text.
                    if (s.rows.size > 1) append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
                    val cols = s.rows.maxOfOrNull { it.size } ?: 1
                    append("<cols>")
                    (0 until cols).forEach { c -> val w = (s.rows.maxOfOrNull { it.getOrElse(c) { "" }.length } ?: 8).coerceIn(8, 60) + 2; append("<col min=\"${c + 1}\" max=\"${c + 1}\" width=\"$w\" customWidth=\"1\"/>") }
                    append("</cols><sheetData>")
                    s.rows.forEachIndexed { r, row ->
                        append("<row r=\"${r + 1}\">")
                        row.forEachIndexed { c, v ->
                            val ref = col(c) + (r + 1)
                            val head = r == 0 && s.rows.size > 1
                            val n = if (head) null else number(v)
                            if (n != null) append("<c r=\"$ref\"><v>$n</v></c>")
                            else append("<c r=\"$ref\" t=\"inlineStr\"${if (head) " s=\"1\"" else ""}><is><t xml:space=\"preserve\">${esc(v)}</t></is></c>")
                        }
                        append("</row>")
                    }
                    append("</sheetData>")
                    if (s.rows.size > 1) append("<autoFilter ref=\"A1:${col(cols - 1)}${s.rows.size}\"/>")
                    append("</worksheet>")
                })
            }
        }
    }

    /** All tables as CSV (semicolon, as German Excel expects), separated by an empty line. */
    fun csv(title: String, markdown: String): String = "\uFEFF" + sheets(title, markdown).joinToString("\r\n\r\n") { s ->
        s.rows.joinToString("\r\n") { r -> r.joinToString(";") { v -> if (v.any { it == ';' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v } }
    }
}

/** Quizzes (the Markdown of [QtiWriter]) for Moodle: Moodle XML and GIFT. */
object QuizFormats {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    private fun cdata(s: String) = "<![CDATA[" + s.replace("]]>", "]]&gt;") + "]]>"

    fun moodleXml(title: String, markdown: String): String {
        val test = QtiWriter.parse(title, markdown)
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<quiz>\n")
            append("<question type=\"category\"><category><text>\$course\$/${esc(test.title)}</text></category></question>\n")
            test.questions.forEach { q ->
                val type = when (q.kind) {
                    QtiWriter.Kind.SINGLE, QtiWriter.Kind.MULTIPLE -> "multichoice"
                    QtiWriter.Kind.TEXT -> "shortanswer"
                    QtiWriter.Kind.ESSAY -> "essay"
                }
                append("<question type=\"$type\">\n<name><text>${esc(q.title)}</text></name>\n")
                append("<questiontext format=\"html\"><text>${cdata(q.prompt.replace("\n", "<br>"))}</text></questiontext>\n")
                append("<defaultgrade>${q.points}</defaultgrade>\n")
                if (q.feedback.isNotBlank()) append("<generalfeedback format=\"html\"><text>${cdata(q.feedback)}</text></generalfeedback>\n")
                when (q.kind) {
                    QtiWriter.Kind.SINGLE, QtiWriter.Kind.MULTIPLE -> {
                        val single = q.kind == QtiWriter.Kind.SINGLE
                        val right = q.options.count { it.correct }.coerceAtLeast(1)
                        val wrong = (q.options.size - right).coerceAtLeast(1)
                        append("<single>$single</single><shuffleanswers>true</shuffleanswers><answernumbering>abc</answernumbering>\n")
                        q.options.forEach { o ->
                            val fraction = if (single) (if (o.correct) 100.0 else 0.0) else if (o.correct) 100.0 / right else -100.0 / wrong
                            append("<answer fraction=\"${"%.5f".format(java.util.Locale.ROOT, fraction).trimEnd('0').trimEnd('.')}\" format=\"html\"><text>${cdata(o.text)}</text></answer>\n")
                        }
                    }
                    QtiWriter.Kind.TEXT -> {
                        append("<usecase>0</usecase>\n")
                        q.answers.forEach { a -> append("<answer fraction=\"100\"><text>${esc(a)}</text></answer>\n") }
                    }
                    QtiWriter.Kind.ESSAY -> {
                        append("<responseformat>editor</responseformat><responsefieldlines>10</responsefieldlines>\n")
                        if (q.expectation.isNotBlank()) append("<graderinfo format=\"html\"><text>${cdata(q.expectation)}</text></graderinfo>\n")
                    }
                }
                append("</question>\n")
            }
            append("</quiz>\n")
        }
    }

    private fun gift(s: String) = s.replace("\\", "\\\\").replace("~", "\\~").replace("=", "\\=").replace("#", "\\#")
        .replace("{", "\\{").replace("}", "\\}").replace(":", "\\:").replace("\n", " ")

    fun gift(title: String, markdown: String): String {
        val test = QtiWriter.parse(title, markdown)
        return buildString {
            append("\$CATEGORY: \$course\$/${test.title.replace("/", "-")}\n\n")
            test.questions.forEach { q ->
                append("::${gift(q.title)}::${gift(q.prompt)} {")
                when (q.kind) {
                    QtiWriter.Kind.SINGLE -> q.options.forEach { o -> append(if (o.correct) " =" else " ~").append(gift(o.text)) }
                    QtiWriter.Kind.MULTIPLE -> {
                        val right = q.options.count { it.correct }.coerceAtLeast(1)
                        val wrong = (q.options.size - right).coerceAtLeast(1)
                        q.options.forEach { o ->
                            val pct = if (o.correct) 100.0 / right else -100.0 / wrong
                            append(" ~%").append("%.5f".format(java.util.Locale.ROOT, pct).trimEnd('0').trimEnd('.')).append("%").append(gift(o.text))
                        }
                    }
                    QtiWriter.Kind.TEXT -> q.answers.forEach { a -> append(" =").append(gift(a)) }
                    QtiWriter.Kind.ESSAY -> {}
                }
                if (q.feedback.isNotBlank()) append(" ####").append(gift(q.feedback))
                append(" }\n\n")
            }
        }
    }
}

/**
 * Flash cards for Anki (and other apps that import tab-separated text): from a table "| Vorderseite | Rückseite |",
 * from lines "Frage :: Antwort" or from "## Frage" with the answer below.
 */
object AnkiWriter {
    fun cards(markdown: String): List<Pair<String, String>> {
        val cards = mutableListOf<Pair<String, String>>()
        val blocks = parseBlocks(markdown)
        blocks.filterIsInstance<Block.Table>().forEach { t -> t.rows.drop(1).forEach { r -> if (r.size >= 2 && r[0].isNotBlank()) cards += r[0] to r[1] } }
        markdown.lines().filter { "::" in it }.forEach { l ->
            val q = l.substringBefore("::").trim().removePrefix("-").removePrefix("*").trim()
            val a = l.substringAfter("::").trim()
            if (q.isNotEmpty() && a.isNotEmpty()) cards += q to a
        }
        if (cards.isEmpty()) {
            var q: String? = null
            val a = StringBuilder()
            fun flush() { q?.let { if (a.isNotBlank()) cards += it to a.toString().trim() }; a.clear() }
            blocks.forEach { b ->
                when (b) {
                    is Block.Heading -> if (b.level >= 2) { flush(); q = b.text } else {}
                    is Block.Paragraph -> if (q != null) a.append(b.text).append("<br>")
                    is Block.Item -> if (q != null) a.append("• ").append(b.text).append("<br>")
                    else -> {}
                }
            }
            flush()
        }
        return cards
    }

    private fun html(md: String) = inlineSegments(md).joinToString("") { s ->
        var t = s.text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        if (s.bold) t = "<b>$t</b>"
        if (s.italic) t = "<i>$t</i>"
        t
    }.replace("&lt;br&gt;", "<br>")

    fun write(title: String, markdown: String): String = buildString {
        append("#separator:tab\n#html:true\n#deck:${title.ifBlank { "ADEU LLM" }.replace('\t', ' ')}\n#columns:Vorderseite\tRückseite\n")
        cards(markdown).forEach { (q, a) -> append(html(q).replace('\t', ' ')).append('\t').append(html(a).replace('\t', ' ')).append('\n') }
    }
}

/**
 * Questionnaires for LimeSurvey as tab-separated survey structure (.txt, import under „Umfrage erstellen ›
 * Importieren“). The model writes:
 * ```
 * # Titel
 * Einleitungstext
 * ## Gruppe: Angaben zur Person
 * ### Wie alt sind Sie? [zahl]
 * ### Welches Fach unterrichten Sie? [einfach]
 * - Mathematik
 * - Deutsch
 * ### Wie sehr stimmen Sie zu? [matrix] Skala: trifft nicht zu | eher nicht | eher | trifft zu
 * - Ich plane meinen Unterricht sorgfältig.
 * ### Anmerkungen [text, optional]
 * ```
 * Types: einfach, mehrfach, dropdown, text, kurz, zahl, janein, likert5, matrix. Questions are mandatory unless "optional".
 */
object LimeSurveyWriter {
    data class Question(val code: String, val text: String, val type: String, val options: List<String>, val scale: List<String>, val mandatory: Boolean)
    data class Group(val name: String, val questions: MutableList<Question> = mutableListOf())
    data class Survey(val title: String, val intro: String, val groups: List<Group>)

    private val Q_HEAD = Regex("^#{3}\\s+(.+?)\\s*(?:\\[([^\\]]+)])?\\s*(?:Skala:\\s*(.+))?$")

    fun parse(title: String, markdown: String): Survey {
        var t = title
        val intro = StringBuilder()
        val groups = mutableListOf<Group>()
        var q: Question? = null
        var n = 0
        fun flush() { q?.let { groups.lastOrNull()?.questions?.add(it) }; q = null }
        for (raw in markdown.lines()) {
            val line = raw.trimEnd()
            when {
                line.startsWith("# ") -> t = line.removePrefix("# ").trim()
                line.startsWith("## ") -> { flush(); groups += Group(line.removePrefix("## ").removePrefix("Gruppe:").trim()) }
                line.startsWith("### ") -> {
                    flush()
                    if (groups.isEmpty()) groups += Group("Fragen")
                    val m = Q_HEAD.find(line)
                    val text = m?.groupValues?.get(1)?.trim() ?: line.removePrefix("### ").trim()
                    val spec = m?.groupValues?.get(2).orEmpty().lowercase()
                    val scale = m?.groupValues?.get(3)?.split('|')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
                    val type = listOf("mehrfach", "dropdown", "einfach", "text", "kurz", "zahl", "janein", "ja/nein", "likert5", "likert", "matrix").firstOrNull { it in spec } ?: "text"
                    q = Question("Q${++n}", text, type, emptyList(), scale, "optional" !in spec)
                }
                Regex("^\\s*[-*]\\s+").containsMatchIn(line) && q != null -> q = q!!.copy(options = q!!.options + line.replace(Regex("^\\s*[-*]\\s+"), "").trim())
                line.isNotBlank() && groups.isEmpty() -> intro.append(line).append('\n')
            }
        }
        flush()
        return Survey(t.ifBlank { "Fragebogen" }, intro.toString().trim(), groups)
    }

    private fun typeCode(type: String) = when (type) {
        "einfach" -> "L"
        "mehrfach" -> "M"
        "dropdown" -> "!"
        "kurz" -> "S"
        "zahl" -> "N"
        "janein", "ja/nein" -> "Y"
        "likert5", "likert" -> "5"
        "matrix" -> "F"
        else -> "T"
    }

    fun write(title: String, markdown: String): String {
        val s = parse(title, markdown)
        val cols = listOf("id", "related_id", "class", "type/scale", "name", "relevance", "text", "help", "language", "validation", "mandatory", "other", "default", "same_default")
        val rows = mutableListOf(cols)
        var id = 1
        fun row(cls: String, type: String = "", name: String = "", relevance: String = "", text: String = "", mandatory: String = "", rid: String = "") =
            listOf((id++).toString(), rid, cls, type, name, relevance, text.replace('\t', ' ').replace("\n", "<br>"), "", if (cls in setOf("S")) "" else "de", "", mandatory, "", "", "")
        rows += row("S", name = "language", text = "de")
        rows += row("S", name = "format", text = "G")
        rows += row("SL", name = "surveyls_title", text = s.title)
        if (s.intro.isNotBlank()) rows += row("SL", name = "surveyls_welcometext", text = s.intro)
        s.groups.forEach { g ->
            rows += row("G", name = g.name, relevance = "1")
            g.questions.forEach { q ->
                val type = typeCode(q.type)
                rows += row("Q", type = type, name = q.code, relevance = "1", text = q.text, mandatory = if (q.mandatory) "Y" else "N")
                when (type) {
                    "L", "!" -> q.options.forEachIndexed { i, o -> rows += row("A", type = "0", name = "A${i + 1}", text = o) }
                    "M" -> q.options.forEachIndexed { i, o -> rows += row("SQ", type = "0", name = "SQ${i + 1}", text = o, relevance = "1") }
                    "F" -> {
                        q.options.forEachIndexed { i, o -> rows += row("SQ", type = "0", name = "SQ${i + 1}", text = o, relevance = "1") }
                        q.scale.ifEmpty { listOf("trifft nicht zu", "trifft eher nicht zu", "trifft eher zu", "trifft zu") }
                            .forEachIndexed { i, o -> rows += row("A", type = "0", name = "A${i + 1}", text = o) }
                    }
                }
            }
        }
        return rows.joinToString("\n") { it.joinToString("\t") } + "\n"
    }

    const val HELP = "Fragebögen für LimeSurvey: create_document mit format=limesurvey; content: „# Titel“, Einleitung, „## Gruppe: Name“ " +
        "und je Frage „### Fragetext [typ]“ mit Typ einfach, mehrfach, dropdown, text, kurz, zahl, janein, likert5 oder matrix " +
        "(„, optional“ für Kann-Fragen); Antwortoptionen bzw. Matrix-Items als Liste „- …“, bei matrix die Skala hinter der Frage: " +
        "„Skala: trifft nicht zu | eher nicht | eher | trifft zu“. Die Datei wird in LimeSurvey unter Umfrage importieren eingelesen. "
}
