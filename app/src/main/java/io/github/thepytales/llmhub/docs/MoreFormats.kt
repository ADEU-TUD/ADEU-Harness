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
    /** [title] is the name formulas of other sheets use (the tab title; [name] may be shortened for Excel). */
    data class Sheet(val name: String, val rows: List<List<String>>, val meta: SheetMeta = SheetMeta(), val title: String = name)

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

    fun write(file: File, title: String, markdown: String) = writeSheets(file, sheets(title, markdown))

    /** Sheet tabs (title → Markdown with its formats) as workbook; sheet names are made valid for Excel. */
    fun writeTabs(file: File, tabs: List<Pair<String, String>>) {
        val used = mutableSetOf<String>()
        writeSheets(file, tabs.map { (title, md) ->
            var n = title.replace(Regex("[\\[\\]:*?/\\\\]"), " ").trim().trim('\'').take(31).ifBlank { "Tabelle" }
            val base = n.take(28)
            var k = 2
            while (!used.add(n.lowercase())) n = "$base ${k++}"
            Sheet(n, SheetEngine.grid(md), SheetMeta.parse(md), title)
        })
    }

    /** Cell formats of a workbook (styles.xml): fonts, fills, number formats, cell formats and those of rules. */
    private class Styles {
        val fonts = mutableListOf("<font><sz val=\"11\"/><name val=\"Calibri\"/></font>")
        val fills = mutableListOf("<fill><patternFill patternType=\"none\"/></fill>", "<fill><patternFill patternType=\"gray125\"/></fill>")
        val numFmts = LinkedHashMap<String, Int>()
        val xfs = mutableListOf("<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>")
        val dxfs = mutableListOf<String>()
        private val xfIndex = HashMap<String, Int>()

        init { xf(CellStyle(), head = true, date = false) }

        fun argb(hex: String) = "FF" + hex.trim().removePrefix("#").takeLast(6).uppercase()

        private fun <T> index(list: MutableList<T>, v: T): Int = list.indexOf(v).takeIf { it >= 0 } ?: run { list += v; list.size - 1 }

        fun numFmt(nf: String?): Int = when (nf) {
            null, "" -> 0
            "0" -> 1
            "0.00" -> 2
            "#,##0.00" -> 4
            "0%" -> 9
            "0.00%" -> 10
            else -> numFmts.getOrPut(
                when (nf) { "date" -> "dd.mm.yyyy"; "eur" -> "#,##0.00\\ \"€\""; else -> nf },
            ) { 164 + numFmts.size }
        }

        /** Index of the cell format for [style] (head row bold with light fill; dates as dd.mm.yyyy). */
        fun xf(style: CellStyle, head: Boolean, date: Boolean): Int {
            val bold = style.b || head
            val fillColor = style.fill ?: if (head) "#DCE6F5" else null
            val nf = numFmt(style.nf ?: if (date) "date" else null)
            if (!bold && !style.i && style.color == null && fillColor == null && nf == 0 && style.align == null) return 0
            val key = "$bold|${style.i}|${style.color}|$fillColor|$nf|${style.align}"
            return xfIndex.getOrPut(key) {
                val font = index(fonts, "<font>" + (if (bold) "<b/>" else "") + (if (style.i) "<i/>" else "") + "<sz val=\"11\"/>" +
                    (style.color?.let { "<color rgb=\"${argb(it)}\"/>" } ?: "") + "<name val=\"Calibri\"/></font>")
                val fill = fillColor?.let { index(fills, "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"${argb(it)}\"/><bgColor indexed=\"64\"/></patternFill></fill>") } ?: 0
                val align = when (style.align) { "l" -> "left"; "c" -> "center"; "r" -> "right"; else -> null }
                xfs += "<xf numFmtId=\"$nf\" fontId=\"$font\" fillId=\"$fill\" borderId=\"0\" xfId=\"0\"" +
                    (if (nf != 0) " applyNumberFormat=\"1\"" else "") + (if (font != 0) " applyFont=\"1\"" else "") + (if (fill != 0) " applyFill=\"1\"" else "") +
                    (if (align != null) " applyAlignment=\"1\"><alignment horizontal=\"$align\"/></xf>" else "/>")
                xfs.size - 1
            }
        }

        fun dxf(fill: String?, color: String?): Int = index(
            dxfs,
            "<dxf>" + (color?.let { "<font><color rgb=\"${argb(it)}\"/></font>" } ?: "") +
                (fill?.let { "<fill><patternFill patternType=\"solid\"><bgColor rgb=\"${argb(it)}\"/></patternFill></fill>" } ?: "") + "</dxf>",
        )

        fun xml(): String = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
            if (numFmts.isNotEmpty()) {
                append("<numFmts count=\"${numFmts.size}\">")
                numFmts.forEach { (code, id) -> append("<numFmt numFmtId=\"$id\" formatCode=\"${esc(code)}\"/>") }
                append("</numFmts>")
            }
            append("<fonts count=\"${fonts.size}\">").append(fonts.joinToString("")).append("</fonts>")
            append("<fills count=\"${fills.size}\">").append(fills.joinToString("")).append("</fills>")
            append("<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>")
            append("<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>")
            append("<cellXfs count=\"${xfs.size}\">").append(xfs.joinToString("")).append("</cellXfs>")
            append("<cellStyles count=\"1\"><cellStyle name=\"Standard\" xfId=\"0\" builtinId=\"0\"/></cellStyles>")
            append("<dxfs count=\"${dxfs.size}\">").append(dxfs.joinToString("")).append("</dxfs>")
            append("</styleSheet>")
        }
    }

    /** A number for XML (no "1.0E7", "." as decimal point). */
    private fun xmlNum(d: Double): String = if (d == Math.floor(d) && Math.abs(d) < 1e15) d.toLong().toString() else d.toString()

    /** A value of a rule as Excel formula: number (dates as serial numbers) or text in quotes. */
    private fun ruleValue(s: String): String = SheetEngine.numberOrDate(s)?.let(::xmlNum) ?: ("\"" + s.replace("\"", "\"\"") + "\"")

    /** German error codes of the engine → Excel's. */
    private fun excelError(code: String): String = when (code) {
        "#NV" -> "#N/A"
        "#WERT!" -> "#VALUE!"
        "#BEZUG!" -> "#REF!"
        "#ZAHL!" -> "#NUM!"
        "#ÜBERLAUF!" -> "#SPILL!"
        "#KALK!" -> "#CALC!"
        "#DIV/0!", "#NAME?" -> code
        else -> "#VALUE!"
    }

    /**
     * Writes [sheets] as they are; cells starting with "=" become Excel formulas (with their computed value), dates
     * date numbers. The formats, conditional formatting, filter buttons and the frozen head row of [Sheet.meta] come
     * along; EINDEUTIG, FILTER, SORTIEREN and SEQUENZ become dynamic arrays that spill in Excel too.
     */
    fun writeSheets(file: File, sheets: List<Sheet>) {
        val styles = Styles()
        val workbook = SheetEngine.Workbook(sheets.map { it.title to it.rows })
        val rename: (String) -> String = { n -> sheets.firstOrNull { it.title.trim().equals(n.trim(), true) || it.name.equals(n.trim(), true) }?.name ?: n }
        var dynamic = false
        // Echte Excel-Diagramme (AP-48): each chart of a sheet as chart part whose series point at the sheet's cells.
        val placed = mutableListOf<PlacedChart>()
        sheets.forEachIndexed { si, s ->
            if (s.meta.charts.isEmpty()) return@forEachIndexed
            val engine = workbook.sheet(si)
            s.meta.charts.forEach { ch ->
                val spec = SheetTools.chartSpec(ch, engine) ?: return@forEach
                val d = OfficeCharts.drawable(spec) ?: return@forEach
                val refs = SheetTools.chartRefs(ch, engine, s.name)?.takeIf { it.size == d.series.size }.orEmpty()
                placed += PlacedChart(si, placed.size + 1, OfficeCharts.xml(d, refs, boxBase = spec.type == ChartSpec.Type.BOX), OfficeCharts.altText(spec, ch.range), ch.id)
            }
        }
        val drawingOf = placed.map { it.sheet }.distinct().withIndex().associate { (k, si) -> si to k + 1 }
        val sheetXml = sheets.mapIndexed { si, s ->
            buildString {
                append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
                // Frozen rows and columns (the head row by default); columns as wide as set or as their longest text.
                val ySplit = if (s.rows.size > 1) s.meta.topRows.coerceAtMost(s.rows.size - 1) else 0
                val xSplit = s.meta.frozenCols
                if (ySplit > 0 || xSplit > 0) {
                    val pane = if (ySplit > 0 && xSplit > 0) "bottomRight" else if (ySplit > 0) "bottomLeft" else "topRight"
                    append("<sheetViews><sheetView workbookViewId=\"0\"><pane")
                    if (xSplit > 0) append(" xSplit=\"$xSplit\"")
                    if (ySplit > 0) append(" ySplit=\"$ySplit\"")
                    append(" topLeftCell=\"${col(xSplit)}${ySplit + 1}\" activePane=\"$pane\" state=\"frozen\"/></sheetView></sheetViews>")
                } else append("<sheetViews><sheetView workbookViewId=\"0\"/></sheetViews>")
                val cols = s.rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
                append("<cols>")
                (0 until cols).forEach { c ->
                    val set = s.meta.widths[SheetEngine.colName(c)]
                    val w = if (set != null) Math.round((set - 5) / 7.0 * 100) / 100.0 else ((s.rows.maxOfOrNull { it.getOrElse(c) { "" }.length } ?: 8).coerceIn(8, 60) + 2).toDouble()
                    // Only widths set in the editor count as custom (so they come back as such, the others stay automatic).
                    append("<col min=\"${c + 1}\" max=\"${c + 1}\" width=\"${xmlNum(w.coerceAtLeast(1.0))}\"" + (if (set != null) " customWidth=\"1\"" else "") + "/>")
                }
                append("</cols><sheetData>")
                val engine = workbook.sheet(si)
                val spilled = engine.spilledCells()
                val height = maxOf(s.rows.size, (spilled.keys.maxOfOrNull { it.first } ?: -1) + 1)
                for (r in 0 until height) {
                    val row = s.rows.getOrNull(r).orEmpty()
                    val columns = (row.indices + spilled.keys.filter { it.first == r }.map { it.second }).toSortedSet()
                    if (columns.isEmpty()) continue
                    append("<row r=\"${r + 1}\">")
                    for (c in columns) append(cellXml(engine, styles, s, r, c, row.getOrNull(c).orEmpty(), spilled[r to c], rename) { dynamic = true })
                    append("</row>")
                }
                append("</sheetData>")
                if (s.rows.size > 1) append("<autoFilter ref=\"A1:${col(cols - 1)}${s.rows.size}\"/>")
                val merges = s.meta.mergeRanges
                if (merges.isNotEmpty()) {
                    append("<mergeCells count=\"${merges.size}\">")
                    merges.forEach { g -> append("<mergeCell ref=\"${SheetEngine.rangeRef(g[0], g[1], g[2], g[3])}\"/>") }
                    append("</mergeCells>")
                }
                var priority = 1
                s.meta.rules.forEach { rule ->
                    val g = SheetEngine.parseRange(rule.range) ?: return@forEach
                    val sqref = SheetEngine.rangeRef(g[0], g[1], g[2], g[3])
                    val first = SheetEngine.ref(g[0], g[1])
                    val cf = when (rule.op) {
                        "lt", "gt", "eq" -> "<cfRule type=\"cellIs\" dxfId=\"${styles.dxf(rule.fill, rule.color)}\" priority=\"${priority++}\" operator=\"" +
                            mapOf("lt" to "lessThan", "gt" to "greaterThan", "eq" to "equal")[rule.op] + "\"><formula>${esc(ruleValue(rule.a))}</formula></cfRule>"
                        "between" -> "<cfRule type=\"cellIs\" dxfId=\"${styles.dxf(rule.fill, rule.color)}\" priority=\"${priority++}\" operator=\"between\">" +
                            "<formula>${esc(ruleValue(rule.a))}</formula><formula>${esc(ruleValue(rule.b))}</formula></cfRule>"
                        "contains" -> "<cfRule type=\"containsText\" dxfId=\"${styles.dxf(rule.fill, rule.color)}\" priority=\"${priority++}\" operator=\"containsText\" text=\"${esc(rule.a)}\">" +
                            "<formula>${esc("NOT(ISERROR(SEARCH(\"" + rule.a.replace("\"", "\"\"") + "\"," + first + ")))")}</formula></cfRule>"
                        "scale" -> "<cfRule type=\"colorScale\" priority=\"${priority++}\"><colorScale><cfvo type=\"min\"/><cfvo type=\"max\"/>" +
                            "<color rgb=\"${styles.argb(rule.fill)}\"/><color rgb=\"${styles.argb(rule.fill2 ?: "#63BE7B")}\"/></colorScale></cfRule>"
                        else -> null
                    } ?: return@forEach
                    append("<conditionalFormatting sqref=\"$sqref\">$cf</conditionalFormatting>")
                }
                val checks = s.meta.checks.mapNotNull { k ->
                    val g = SheetEngine.parseRange(k.range) ?: return@mapNotNull null
                    val f = if (k.list.isNotEmpty()) "\"" + k.list.joinToString(",") { it.replace("\"", "\"\"") }.take(250) + "\""
                    else k.source?.let { src ->
                        val bang = src.lastIndexOf('!')
                        if (bang > 0) SheetEngine.quoteSheet(rename(src.substring(0, bang).trim('\'').replace("''", "'"))) + "!" + src.substring(bang + 1) else src
                    } ?: return@mapNotNull null
                    "<dataValidation type=\"list\" allowBlank=\"1\" showInputMessage=\"1\" showErrorMessage=\"${if (k.strict) 1 else 0}\"" +
                        (if (k.strict) "" else " errorStyle=\"information\"") + " sqref=\"${SheetEngine.rangeRef(g[0], g[1], g[2], g[3])}\"><formula1>${esc(f)}</formula1></dataValidation>"
                }
                if (checks.isNotEmpty()) append("<dataValidations count=\"${checks.size}\">").append(checks.joinToString("")).append("</dataValidations>")
                append("<pageMargins left=\"0.7\" right=\"0.7\" top=\"0.787\" bottom=\"0.787\" header=\"0.3\" footer=\"0.3\"/>")
                if (si in drawingOf) append("<drawing r:id=\"rIdDr\"/>")
                append("</worksheet>")
            }
        }
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
            entry("[Content_Types].xml", buildString {
                append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
                append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
                append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
                if (dynamic) append("<Override PartName=\"/xl/metadata.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheetMetadata+xml\"/>")
                sheets.indices.forEach { append("<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>") }
                drawingOf.values.forEach { append("<Override PartName=\"/xl/drawings/drawing$it.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>") }
                placed.forEach { append("<Override PartName=\"/xl/charts/chart${it.number}.xml\" ContentType=\"${OfficeCharts.CONTENT_TYPE}\"/>") }
                append("</Types>")
            })
            entry("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
            entry("xl/_rels/workbook.xml.rels", buildString {
                append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
                sheets.indices.forEach { append("<Relationship Id=\"rId${it + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>") }
                if (dynamic) append("<Relationship Id=\"rIdM\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sheetMetadata\" Target=\"metadata.xml\"/>")
                append("<Relationship Id=\"rIdS\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>")
            })
            entry("xl/workbook.xml", buildString {
                append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>")
                sheets.forEachIndexed { i, s -> append("<sheet name=\"${esc(s.name)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>") }
                append("</sheets>")
                // Hidden names of the filter ranges, as Excel writes them.
                val filters = sheets.mapIndexedNotNull { i, s ->
                    if (s.rows.size > 1) i to "${SheetEngine.quoteSheet(s.name)}!\$A\$1:\$${col((s.rows.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1) - 1)}\$${s.rows.size}" else null
                }
                if (filters.isNotEmpty()) {
                    append("<definedNames>")
                    filters.forEach { (i, ref) -> append("<definedName name=\"_xlnm._FilterDatabase\" localSheetId=\"$i\" hidden=\"1\">${esc(ref)}</definedName>") }
                    append("</definedNames>")
                }
                append("<calcPr calcId=\"191029\" fullCalcOnLoad=\"1\"/></workbook>")
            })
            entry("xl/styles.xml", styles.xml())
            if (dynamic) {
                entry(
                    "xl/metadata.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<metadata xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:xda=\"http://schemas.microsoft.com/office/spreadsheetml/2017/dynamicarray\">" +
                        "<metadataTypes count=\"1\"><metadataType name=\"XLDAPR\" minSupportedVersion=\"120000\" copy=\"1\" pasteAll=\"1\" pasteValues=\"1\" merge=\"1\" splitFirst=\"1\" rowColShift=\"1\" clearFormats=\"1\" clearComments=\"1\" assign=\"1\" coerce=\"1\" cellMeta=\"1\"/></metadataTypes>" +
                        "<futureMetadata name=\"XLDAPR\" count=\"1\"><bk><extLst><ext uri=\"{bdbb8cdc-fa1e-496e-a857-3c3f30c029c3}\"><xda:dynamicArrayProperties fDynamic=\"1\" fCollapsed=\"0\"/></ext></extLst></bk></futureMetadata>" +
                        "<cellMetadata count=\"1\"><bk><rc t=\"1\" v=\"0\"/></bk></cellMetadata></metadata>",
                )
            }
            sheetXml.forEachIndexed { i, x -> entry("xl/worksheets/sheet${i + 1}.xml", x) }
            val rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            drawingOf.forEach { (si, k) ->
                val charts = placed.filter { it.sheet == si }
                entry("xl/worksheets/_rels/sheet${si + 1}.xml.rels", rels + "<Relationship Id=\"rIdDr\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing\" Target=\"../drawings/drawing$k.xml\"/></Relationships>")
                entry("xl/drawings/_rels/drawing$k.xml.rels", rels + charts.joinToString("") { "<Relationship Id=\"rIdC${it.number}\" Type=\"${OfficeCharts.REL_CHART}\" Target=\"../charts/chart${it.number}.xml\"/>" } + "</Relationships>")
                // Right of the data, one below the other (16 rows high, 8 columns wide).
                val left = (sheets[si].rows.maxOfOrNull { it.size } ?: 1) + 1
                entry("xl/drawings/drawing$k.xml", buildString {
                    append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<xdr:wsDr xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">")
                    charts.forEachIndexed { i, ch ->
                        val top = 1 + i * 18
                        append("<xdr:twoCellAnchor editAs=\"oneCell\"><xdr:from><xdr:col>$left</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>$top</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from>")
                        append("<xdr:to><xdr:col>${left + 8}</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>${top + 16}</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to>")
                        append("<xdr:graphicFrame macro=\"\"><xdr:nvGraphicFramePr><xdr:cNvPr id=\"${i + 2}\" name=\"${esc(if (ch.id.isNotBlank()) "Lora-Diagramm ${ch.id}" else "Diagramm ${i + 1}")}\" descr=\"${esc(ch.alt)}\"/><xdr:cNvGraphicFramePr/></xdr:nvGraphicFramePr>")
                        append("<xdr:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/></xdr:xfrm>").append(OfficeCharts.graphic("rIdC${ch.number}"))
                        append("</xdr:graphicFrame><xdr:clientData/></xdr:twoCellAnchor>")
                    }
                    append("</xdr:wsDr>")
                })
            }
            placed.forEach { entry("xl/charts/chart${it.number}.xml", it.xml) }
        }
    }

    /** A chart of a sheet as it goes into the workbook: number of its part, chart XML, alternative text and its id. */
    private class PlacedChart(val sheet: Int, val number: Int, val xml: String, val alt: String, val id: String)

    /** One `<c>` element: formula (dynamic array if it spills), date, number, text or a value spilled into it. */
    private fun cellXml(
        engine: SheetEngine.Sheet, styles: Styles, s: Sheet, r: Int, c: Int, v: String, spilled: SheetEngine.Value?,
        rename: (String) -> String, onDynamic: () -> Unit,
    ): String {
        val ref = col(c) + (r + 1)
        val head = r == 0 && s.rows.size > 1
        val style = s.meta.style(r, c) ?: CellStyle()
        fun st(date: Boolean): String = styles.xf(style, head, date).let { if (it == 0) "" else " s=\"$it\"" }
        fun inline(text: String) =
            if (CellRich.isRich(text)) "<c r=\"$ref\" t=\"inlineStr\"${st(false)}><is>${CellRich.xlsxRuns(text, bold = style.b || head, italic = style.i)}</is></c>"
            else "<c r=\"$ref\" t=\"inlineStr\"${st(false)}><is><t xml:space=\"preserve\">${esc(text)}</t></is></c>"
        fun plain(value: SheetEngine.Value): String = when (value) {
            is SheetEngine.Value.Num -> "<c r=\"$ref\"${st(value.date)}><v>${xmlNum(value.v)}</v></c>"
            is SheetEngine.Value.Bool -> "<c r=\"$ref\" t=\"b\"${st(false)}><v>${if (value.v) 1 else 0}</v></c>"
            is SheetEngine.Value.Text -> if (value.v.isEmpty()) "" else inline(value.v)
            else -> "<c r=\"$ref\" t=\"str\"${st(false)}><v>${esc(SheetEngine.format(value))}</v></c>"
        }
        val t = v.trim()
        return when {
            t.startsWith("=") && t.length > 1 -> {
                val f = esc(SheetEngine.toExcel(v, rename))
                val result = engine.value(r, c)
                val fn = Regex("^=\\s*([\\p{L}_.]+)\\s*\\(").find(t)?.groupValues?.get(1)?.let(SheetEngine::excelName)
                val size = engine.spillSize(r, c)?.takeIf { fn in SheetEngine.DYNAMIC }
                if (size != null) onDynamic()
                val formula = if (size != null) "<f t=\"array\" ref=\"$ref:${col(c + size.second - 1)}${r + size.first}\">$f</f>" else "<f>$f</f>"
                val cm = if (size != null) " cm=\"1\"" else ""
                when (result) {
                    is SheetEngine.Value.Num -> "<c r=\"$ref\"${st(result.date)}$cm>$formula<v>${xmlNum(result.v)}</v></c>"
                    is SheetEngine.Value.Bool -> "<c r=\"$ref\" t=\"b\"${st(false)}$cm>$formula<v>${if (result.v) 1 else 0}</v></c>"
                    is SheetEngine.Value.Err -> "<c r=\"$ref\" t=\"e\"${st(false)}$cm>$formula<v>${esc(excelError(result.code))}</v></c>"
                    else -> "<c r=\"$ref\" t=\"str\"${st(false)}$cm>$formula<v>${esc(SheetEngine.format(result))}</v></c>"
                }
            }
            t.isEmpty() -> spilled?.let(::plain) ?: if (!style.isEmpty) "<c r=\"$ref\"${st(false)}/>" else ""
            else -> {
                val lit = SheetEngine.literal(t)
                // The head row keeps numbers as text (column names), but dates become dates.
                if (lit is SheetEngine.Value.Text || head && !(lit is SheetEngine.Value.Num && lit.date)) inline(v) else plain(lit)
            }
        }
    }

    /** All tables as CSV (semicolon, as German Excel expects), separated by an empty line. */
    /** The sheets of [markdown] as CSV in the [flavour] of the CSV-Lotse (AP-93); several sheets one after the other. */
    fun csv(title: String, markdown: String, flavour: CsvPilot.Flavour = CsvPilot.Flavour.EXCEL): String =
        sheets(title, markdown).joinToString("\r\n\r\n") { s -> CsvPilot.write(s.rows, flavour).removePrefix("\uFEFF").removeSuffix("\r\n") }
            .let { if (flavour == CsvPilot.Flavour.EXCEL) "\uFEFF" + it else it }
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
        append("#separator:tab\n#html:true\n#deck:${title.ifBlank { "Lora" }.replace('\t', ' ')}\n#columns:Vorderseite\tRückseite\n")
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

/** Data of a series of documents: CSV (comma or semicolon), tab-separated text or a Markdown table. */
object SeriesData {
    fun parse(text: String): List<List<String>> {
        val lines = text.trim().lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        if (lines.first().trim().startsWith("|")) return DocBlocks.tableRows(lines.joinToString("\n"))
        val sep = listOf('\t', ';', ',').maxByOrNull { c -> lines.first().count { it == c } } ?: ';'
        return lines.map { line -> split(line, sep) }
    }

    /** One CSV line with quoted fields ("a;b" stays one field, "" is a quote). */
    fun split(line: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                quoted && ch == '"' && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                ch == sep && !quoted -> { out += cur.toString().trim(); cur.clear() }
                else -> cur.append(ch)
            }
            i++
        }
        out += cur.toString().trim()
        return out
    }

    /** {{Spalte}} (case-insensitive) by the value of the row. */
    fun fill(text: String, values: Map<String, String>): String =
        Regex("\\{\\{\\s*([^{}|]+?)\\s*(?:\\|[^{}]*)?\\}\\}").replace(text) { m ->
            values.entries.firstOrNull { it.key.equals(m.groupValues[1].trim(), ignoreCase = true) }?.value ?: m.value
        }
}
