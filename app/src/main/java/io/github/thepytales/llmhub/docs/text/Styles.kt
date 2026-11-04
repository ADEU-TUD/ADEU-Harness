package io.github.thepytales.llmhub.docs.text

import io.github.thepytales.llmhub.docs.DocStyle
import io.github.thepytales.llmhub.docs.TudBrand

/**
 * Formatvorlagen (AP-46): how each [ParaKind] looks – font, size, weight, colour, line spacing and space around it.
 * A document uses one [StyleSet] (TU Dresden, Wissenschaftlich, Schlicht or Standard) and may change single
 * Formatvorlagen („Überschrift 1 an Markierung anpassen“). Both live as setting lines in the document Markdown:
 *
 * ```
 * <!-- vorlagen: wissenschaftlich -->
 * <!-- vorlage h1: schrift=Georgia; größe=20; fett; farbe=00008C; zeilen=1.5 -->
 * ```
 *
 * The editor draws the text with them, the Word export writes them as Word styles; switching the set re-formats the
 * whole document, since the paragraphs only name their Formatvorlage.
 */
data class StyleDef(
    /** Font name or null (the set's body font). */
    val font: String? = null,
    /** Size in points. */
    val size: Float = 11f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    /** RRGGBB or null (text colour). */
    val color: String? = null,
    /** Line spacing as multiple. */
    val spacing: Float = 1.15f,
    /** Space before and after in points. */
    val before: Int = 0,
    val after: Int = 6,
)

enum class StyleSet(val key: String, val label: String, val description: String, val docStyle: DocStyle?) {
    STANDARD("standard", "Standard", "Serifenlose Schrift, Überschriften fett", null),
    TUD("tud", "TU Dresden", "Noto Sans, Überschriften in TUD-Blau (CD 2025)", DocStyle.TUD),
    SCIENCE("wissenschaftlich", "Wissenschaftlich", "Times-artige Schrift, 1,5 Zeilen, schwarz", DocStyle.CLASSIC),
    PLAIN("schlicht", "Schlicht", "Arial, schwarz auf weiß, ohne Schmuck", DocStyle.MINIMAL),
    ;

    companion object {
        fun parse(v: String?): StyleSet? {
            val n = v?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.key == n || it.label.lowercase() == n || it.name.lowercase() == n } ?: when (n) {
                "tu dresden", "tu-dresden", "cd" -> TUD
                "science", "akademisch", "hausarbeit", "abschlussarbeit" -> SCIENCE
                "minimal", "plain", "einfach" -> PLAIN
                else -> null
            }
        }

        /** The set that fits the export style of a document (for documents that never chose a set). */
        fun of(style: DocStyle?): StyleSet = when (style) {
            DocStyle.TUD -> TUD
            DocStyle.CLASSIC, DocStyle.ELEGANT -> SCIENCE
            DocStyle.MINIMAL -> PLAIN
            else -> STANDARD
        }
    }
}

/** The Formatvorlagen of one document: the [set] and the ones changed in it. */
data class StyleSheet(val set: StyleSet = StyleSet.STANDARD, val changed: Map<ParaKind, StyleDef> = emptyMap(), val explicit: Boolean = false) {
    fun of(kind: ParaKind): StyleDef = changed[Styles.base(kind)] ?: Styles.defaults(set).getValue(Styles.base(kind))

    /** The body font of the set (for paragraphs whose Formatvorlage names none). */
    val bodyFont: String? get() = of(ParaKind.NORMAL).font
}

object Styles {
    /** Lists use the Formatvorlage of normal text. */
    fun base(kind: ParaKind): ParaKind = if (kind.list) ParaKind.NORMAL else kind

    /** The Formatvorlagen that can be chosen and changed (lists are formats of normal text). */
    val KINDS: List<ParaKind> = ParaKind.entries.filter { !it.list }

    fun defaults(set: StyleSet): Map<ParaKind, StyleDef> = when (set) {
        StyleSet.STANDARD -> mapOf(
            ParaKind.NORMAL to StyleDef(size = 11f),
            ParaKind.TITLE to StyleDef(size = 26f, spacing = 1.0f, after = 3),
            ParaKind.SUBTITLE to StyleDef(size = 15f, color = "666666", spacing = 1.0f, after = 16),
            ParaKind.H1 to StyleDef(size = 20f, bold = true, spacing = 1.0f, before = 20, after = 6),
            ParaKind.H2 to StyleDef(size = 16f, bold = true, spacing = 1.0f, before = 18, after = 6),
            ParaKind.H3 to StyleDef(size = 14f, bold = true, color = "434343", spacing = 1.0f, before = 16, after = 4),
            ParaKind.H4 to StyleDef(size = 12f, bold = true, color = "666666", spacing = 1.0f, before = 14, after = 4),
            ParaKind.QUOTE to StyleDef(size = 11f, italic = true, color = "595959"),
            ParaKind.CAPTION to StyleDef(size = 9f, italic = true, color = "595959", after = 10),
            ParaKind.CODE to StyleDef(font = "Courier New", size = 10f, spacing = 1.0f, after = 0),
            ParaKind.LITERATURE to StyleDef(size = 11f, after = 6),
        )
        StyleSet.TUD -> {
            val blue = TudBrand.BLUE.removePrefix("#")
            val body = TudBrand.BODY_FONT
            val head = TudBrand.HEAD_FONT
            mapOf(
                ParaKind.NORMAL to StyleDef(font = body, size = 10.5f, spacing = 1.2f),
                ParaKind.TITLE to StyleDef(font = head, size = 26f, color = blue, spacing = 1.0f, after = 4),
                ParaKind.SUBTITLE to StyleDef(font = body, size = 14f, color = TudBrand.GREY.removePrefix("#"), spacing = 1.0f, after = 16),
                ParaKind.H1 to StyleDef(font = head, size = 18f, color = blue, spacing = 1.0f, before = 20, after = 6),
                ParaKind.H2 to StyleDef(font = head, size = 14f, color = blue, spacing = 1.0f, before = 16, after = 6),
                ParaKind.H3 to StyleDef(font = head, size = 12f, color = blue, spacing = 1.0f, before = 14, after = 4),
                ParaKind.H4 to StyleDef(font = head, size = 10.5f, color = TudBrand.GREY_100.removePrefix("#"), spacing = 1.0f, before = 12, after = 4),
                ParaKind.QUOTE to StyleDef(font = body, size = 10.5f, italic = true, color = TudBrand.GREY.removePrefix("#"), spacing = 1.2f),
                ParaKind.CAPTION to StyleDef(font = body, size = 8.5f, color = TudBrand.GREY.removePrefix("#"), after = 10),
                ParaKind.CODE to StyleDef(font = "Courier New", size = 9.5f, spacing = 1.0f, after = 0),
                ParaKind.LITERATURE to StyleDef(font = body, size = 10.5f, spacing = 1.2f),
            )
        }
        StyleSet.SCIENCE -> {
            val serif = "Times New Roman"
            mapOf(
                ParaKind.NORMAL to StyleDef(font = serif, size = 12f, spacing = 1.5f, after = 6),
                ParaKind.TITLE to StyleDef(font = serif, size = 20f, bold = true, spacing = 1.0f, after = 6),
                ParaKind.SUBTITLE to StyleDef(font = serif, size = 14f, spacing = 1.0f, after = 18),
                ParaKind.H1 to StyleDef(font = serif, size = 16f, bold = true, spacing = 1.0f, before = 24, after = 12),
                ParaKind.H2 to StyleDef(font = serif, size = 14f, bold = true, spacing = 1.0f, before = 18, after = 6),
                ParaKind.H3 to StyleDef(font = serif, size = 12f, bold = true, spacing = 1.0f, before = 12, after = 6),
                ParaKind.H4 to StyleDef(font = serif, size = 12f, bold = true, italic = true, spacing = 1.0f, before = 12, after = 6),
                ParaKind.QUOTE to StyleDef(font = serif, size = 11f, spacing = 1.0f, after = 12),
                ParaKind.CAPTION to StyleDef(font = serif, size = 10f, italic = true, spacing = 1.0f, after = 12),
                ParaKind.CODE to StyleDef(font = "Courier New", size = 10f, spacing = 1.0f, after = 0),
                ParaKind.LITERATURE to StyleDef(font = serif, size = 12f, spacing = 1.0f, after = 6),
            )
        }
        StyleSet.PLAIN -> {
            val sans = "Arial"
            mapOf(
                ParaKind.NORMAL to StyleDef(font = sans, size = 11f, spacing = 1.15f),
                ParaKind.TITLE to StyleDef(font = sans, size = 22f, bold = true, spacing = 1.0f, after = 6),
                ParaKind.SUBTITLE to StyleDef(font = sans, size = 13f, spacing = 1.0f, after = 14),
                ParaKind.H1 to StyleDef(font = sans, size = 16f, bold = true, spacing = 1.0f, before = 18, after = 6),
                ParaKind.H2 to StyleDef(font = sans, size = 13f, bold = true, spacing = 1.0f, before = 14, after = 4),
                ParaKind.H3 to StyleDef(font = sans, size = 11f, bold = true, spacing = 1.0f, before = 12, after = 4),
                ParaKind.H4 to StyleDef(font = sans, size = 11f, italic = true, spacing = 1.0f, before = 10, after = 4),
                ParaKind.QUOTE to StyleDef(font = sans, size = 11f, italic = true),
                ParaKind.CAPTION to StyleDef(font = sans, size = 9f, after = 10),
                ParaKind.CODE to StyleDef(font = "Courier New", size = 10f, spacing = 1.0f, after = 0),
                ParaKind.LITERATURE to StyleDef(font = sans, size = 11f),
            )
        }
    }

    // ── Setting lines ────────────────────────────────────────────────────────────────────────────

    private val SET_LINE = Regex("^\\s*<!--\\s*vorlagen\\s*:\\s*(.*?)\\s*-->\\s*$", RegexOption.IGNORE_CASE)
    private val DEF_LINE = Regex("^\\s*<!--\\s*vorlage\\s+([a-z0-9]+)\\s*:\\s*(.*?)\\s*-->\\s*$", RegexOption.IGNORE_CASE)

    /** Whether [line] is one of the setting lines of the Formatvorlagen. */
    fun isSetting(line: String): Boolean = SET_LINE.matches(line) || DEF_LINE.matches(line)

    /** Short name of a Formatvorlage in the setting lines. */
    fun key(kind: ParaKind): String = when (kind) {
        ParaKind.NORMAL -> "standard"
        ParaKind.H1 -> "h1"; ParaKind.H2 -> "h2"; ParaKind.H3 -> "h3"; ParaKind.H4 -> "h4"
        ParaKind.QUOTE -> "zitat"
        else -> kind.mark ?: kind.name.lowercase()
    }

    private fun kindOf(key: String): ParaKind? = KINDS.firstOrNull { key(it) == key.lowercase() }

    /** The Formatvorlagen of [markdown]; without setting lines those of [fallback]. */
    fun read(markdown: String, fallback: StyleSet = StyleSet.STANDARD): StyleSheet {
        var set: StyleSet? = null
        val changed = LinkedHashMap<ParaKind, StyleDef>()
        val lines = markdown.lineSequence().toList()
        for (line in lines) SET_LINE.find(line)?.let { set = StyleSet.parse(it.groupValues[1]) ?: set }
        val s = set ?: fallback
        for (line in lines) {
            val m = DEF_LINE.find(line) ?: continue
            val kind = kindOf(m.groupValues[1]) ?: continue
            changed[kind] = parseDef(m.groupValues[2], defaults(s).getValue(kind))
        }
        return StyleSheet(s, changed, explicit = set != null)
    }

    /** [markdown] with [sheet] as setting lines (at the end, where the page setup is too). */
    fun write(markdown: String, sheet: StyleSheet): String {
        val body = markdown.lines().filterNot { isSetting(it) }.joinToString("\n").trimEnd()
        val lines = buildList {
            add("<!-- vorlagen: ${sheet.set.key} -->")
            sheet.changed.forEach { (k, d) -> add("<!-- vorlage ${key(k)}: ${formatDef(d)} -->") }
        }
        return body + "\n\n" + lines.joinToString("\n\n") + "\n"
    }

    fun formatDef(d: StyleDef): String = buildList {
        d.font?.let { add("schrift=${it.replace(";", "").replace("-->", "")}") }
        add("größe=" + num(d.size))
        if (d.bold) add("fett")
        if (d.italic) add("kursiv")
        d.color?.let { add("farbe=$it") }
        add("zeilen=" + num(d.spacing))
        add("vor=${d.before}")
        add("nach=${d.after}")
    }.joinToString("; ")

    private fun num(f: Float) = if (f == f.toInt().toFloat()) f.toInt().toString() else f.toString()

    fun parseDef(text: String, base: StyleDef): StyleDef {
        var d = base.copy(bold = false, italic = false)
        for (part in text.split(';').map { it.trim() }.filter { it.isNotEmpty() }) {
            val k = part.substringBefore('=').trim().lowercase()
            val v = part.substringAfter('=', "").trim()
            when (k) {
                "schrift", "font" -> d = d.copy(font = v.ifBlank { null })
                "größe", "groesse", "size" -> v.replace(',', '.').toFloatOrNull()?.takeIf { it in 4f..96f }?.let { d = d.copy(size = it) }
                "fett", "bold" -> d = d.copy(bold = true)
                "kursiv", "italic" -> d = d.copy(italic = true)
                "farbe", "color" -> d = d.copy(color = v.removePrefix("#").uppercase().takeIf { it.matches(Regex("[0-9A-F]{6}")) })
                "zeilen", "spacing" -> v.replace(',', '.').toFloatOrNull()?.takeIf { it in 0.5f..4f }?.let { d = d.copy(spacing = it) }
                "vor", "before" -> v.toIntOrNull()?.let { d = d.copy(before = it.coerceIn(0, 120)) }
                "nach", "after" -> v.toIntOrNull()?.let { d = d.copy(after = it.coerceIn(0, 120)) }
            }
        }
        return d
    }

    /**
     * „Überschrift 1 an Markierung anpassen“: the Formatvorlage [def] taking font, size, weight and colour of the
     * marked text [f] (what the marked text does not set stays) and the line spacing of its paragraph [p].
     */
    fun adapt(def: StyleDef, f: CharFormat, p: ParaFormat): StyleDef = def.copy(
        font = f.font ?: def.font,
        size = f.size?.toFloat() ?: def.size,
        // A Formatvorlage's bold cannot be switched off by the text (it has no „not bold“), so only more comes in.
        bold = def.bold || f.bold,
        italic = def.italic || f.italic,
        color = f.color ?: def.color,
        spacing = p.spacing ?: def.spacing,
    )

    /** The default of new documents (Einstellungen): the setting lines of a sheet, without the document. */
    fun defaultLines(sheet: StyleSheet): String = write("", sheet).trim()
}
