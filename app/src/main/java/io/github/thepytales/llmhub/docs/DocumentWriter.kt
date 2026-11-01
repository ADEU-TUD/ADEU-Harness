package io.github.thepytales.llmhub.docs

import android.content.Context
import io.github.thepytales.llmhub.agent.WebTools
import io.github.thepytales.llmhub.ui.Block
import io.github.thepytales.llmhub.ui.Segment
import io.github.thepytales.llmhub.ui.inlineSegments
import io.github.thepytales.llmhub.ui.parseBlocks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

enum class DocFormat(val ext: String, val mime: String, val label: String, val chatExport: Boolean = true) {
    PDF("pdf", "application/pdf", "PDF"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word (.docx)"),
    PPTX("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "PowerPoint (.pptx)"),
    HTML("html", "text/html", "HTML"),
    MD("md", "text/markdown", "Markdown"),
    TXT("txt", "text/plain", "Text"),
    /** Test for OPAL/ONYX as IMS QTI 2.1 package (docs/QtiWriter); not offered for exporting a chat. */
    QTI("zip", "application/zip", "OPAL-/ONYX-Test (QTI 2.1)", chatExport = false),
    /** Tables of the Markdown as Excel workbook (docs/MoreFormats). */
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel (.xlsx)"),
    CSV("csv", "text/csv", "CSV (Tabellen)"),
    MOODLE("xml", "application/xml", "Moodle-Test (Moodle-XML)", chatExport = false),
    GIFT("txt", "text/plain", "Moodle-Test (GIFT)", chatExport = false),
    ANKI("txt", "text/plain", "Karteikarten für Anki", chatExport = false),
    LIMESURVEY("txt", "text/plain", "LimeSurvey-Fragebogen", chatExport = false),
    ;

    companion object {
        fun parse(name: String?): DocFormat {
            val n = name?.trim()?.lowercase()?.removePrefix(".").orEmpty()
            return entries.firstOrNull { it.ext == n || it.name.equals(n, ignoreCase = true) }
                ?: when (n) {
                    "word", "doc" -> DOCX
                    "powerpoint", "ppt", "praesentation", "präsentation", "folien", "slides" -> PPTX
                    "markdown" -> MD
                    "zip", "opal", "onyx", "test", "quiz", "qti21", "ims" -> QTI
                    "excel", "xls", "tabelle", "spreadsheet" -> XLSX
                    "moodle", "moodlexml", "moodle-xml" -> MOODLE
                    "gift" -> GIFT
                    "anki", "karteikarten", "lernkarten", "flashcards" -> ANKI
                    "limesurvey", "lss", "fragebogen", "survey" -> LIMESURVEY
                    "text", "plain" -> TXT
                    "htm" -> HTML
                    else -> PDF
                }
        }

        fun ofFile(file: File) = entries.firstOrNull { it.ext == file.extension.lowercase() } ?: TXT
    }
}

/** Letter layout: sender block next to the logo, address field and subject (the title). Lines are separated by \n. */
data class Letter(val sender: String, val recipient: String)

/** [designed] is false when the PDF had to fall back to the simple renderer. */
data class CreatedDocument(val file: File, val format: DocFormat, val pages: Int?, val designed: Boolean = true)

/** Creates documents from Markdown or HTML in the app's private storage. */
object DocumentWriter {
    fun safeName(name: String): String =
        name.trim().replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_").replace(Regex("\\s+"), " ").take(80).trim('.', ' ')
            .ifBlank { "Dokument" }

    /** Whether [content] is a complete HTML document (with its own design) instead of Markdown. */
    fun isHtml(content: String): Boolean {
        val t = content.trimStart().take(400).lowercase()
        return t.startsWith("<!doctype html") || t.startsWith("<html") || ("<body" in content.lowercase() && "</" in content)
    }

    /**
     * Writes the document into [folder]; an existing file with the same name is not overwritten.
     * [content] is Markdown, or for PDF/HTML also a full HTML page with CSS.
     */
    suspend fun create(
        context: Context,
        folder: File,
        title: String,
        content: String,
        format: DocFormat,
        fileName: String? = null,
        theme: DocTheme = DocTheme(),
        letter: Letter? = null,
        certificate: Boolean = false,
        /** Replace a file of the same name (filling in placeholders) instead of numbering the new one. */
        overwrite: Boolean = false,
        /** Folder that relative picture paths (![…](Bilder/…)) refer to, e.g. the one of a document of the editor. */
        imageBase: File? = null,
    ): CreatedDocument {
        @Suppress("NAME_SHADOWING") val theme = TudBrand.apply(theme)
        val base = safeName(fileName?.substringBeforeLast('.')?.ifBlank { null } ?: title)
        val file = withContext(Dispatchers.IO) {
            folder.mkdirs()
            var f = File(folder, "$base.${format.ext}")
            var n = 2
            if (overwrite) f.delete()
            while (f.exists()) f = File(folder, "$base ($n).${format.ext}").also { n++ }
            f
        }
        // Formulas ($…$) as readable text in every format (no formula renderer in PDF, Word or slides).
        // Word gets real formulas (LatexOmml, AP-16).
        @Suppress("NAME_SHADOWING") val content = if (isHtml(content)) content else DocMarkup.resolveImages(if (format == DocFormat.DOCX) content else MathText.inText(content), imageBase)
        val html = isHtml(content)
        // Word, Markdown and text cannot keep an HTML design; they get its text structure.
        val markdown = when {
            html -> WebTools.htmlToText(content)
            letter != null -> letterAsMarkdown(letter, content, theme)
            else -> content
        }
        var pages: Int? = null
        var designed = true
        when (format) {
            DocFormat.PDF -> {
                val page = if (html) content else toHtml(title, content, theme, print = true, letter = letter, certificate = certificate)
                pages = try {
                    HtmlPdf.write(context, file, page)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // WebView printing is not available on every device: use the built-in renderer.
                    designed = false
                    withContext(Dispatchers.IO) { MarkdownPdf.write(file, title, markdown, theme) }
                }
                // Accessibility: title, language and window title for screen readers; the PDF stays usable without it.
                val docTitle = title.ifBlank { Regex("^#\\s+(.+)", RegexOption.MULTILINE).find(markdown)?.groupValues?.get(1)?.trim().orEmpty() }
                withContext(Dispatchers.IO) { runCatching { PdfAccessibility.finish(context, file, docTitle) } }
            }
            DocFormat.DOCX -> withContext(Dispatchers.IO) { MarkdownDocx.write(file, title, ChartBlocks.asTables(markdown), theme) }
            DocFormat.PPTX -> withContext(Dispatchers.IO) { PptxWriter.write(file, title, markdown, theme.unit, theme.footer) }
            DocFormat.QTI -> withContext(Dispatchers.IO) { QtiWriter.write(file, title, markdown) }
            DocFormat.HTML -> withContext(Dispatchers.IO) {
                file.writeText(if (html) content else toHtml(title, content, theme, print = false, letter = letter, certificate = certificate))
            }
            DocFormat.MD -> withContext(Dispatchers.IO) { file.writeText(withTitle(title, markdown)) }
            DocFormat.TXT -> withContext(Dispatchers.IO) { file.writeText(toText(title, ChartBlocks.asTables(markdown))) }
            DocFormat.XLSX -> withContext(Dispatchers.IO) { XlsxWriter.write(file, title, markdown) }
            DocFormat.CSV -> withContext(Dispatchers.IO) { file.writeText(XlsxWriter.csv(title, markdown)) }
            DocFormat.MOODLE -> withContext(Dispatchers.IO) { file.writeText(QuizFormats.moodleXml(title, markdown)) }
            DocFormat.GIFT -> withContext(Dispatchers.IO) { file.writeText(QuizFormats.gift(title, markdown)) }
            DocFormat.ANKI -> withContext(Dispatchers.IO) { file.writeText(AnkiWriter.write(title, markdown)) }
            DocFormat.LIMESURVEY -> withContext(Dispatchers.IO) { file.writeText(LimeSurveyWriter.write(title, markdown)) }
        }
        return CreatedDocument(file, format, pages, designed)
    }

    /** Sender, address and date in front of the text, for formats without the letter layout (Word, text). */
    private fun letterAsMarkdown(letter: Letter, body: String, theme: DocTheme): String = buildString {
        fun lines(t: String) = t.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("  \n")
        if (letter.sender.isNotBlank()) append(lines(letter.sender)).append("\n\n")
        if (theme.style == DocStyle.TUD) append("*").append(TudBrand.POSTAL).append("*\n\n")
        if (letter.recipient.isNotBlank()) append(lines(letter.recipient)).append("\n\n")
        append("Dresden, ").append(DateFormat.getDateInstance(DateFormat.LONG, Locale.GERMANY).format(Date())).append("\n\n")
        append(body)
    }

    /** Adds the title as top-level heading unless the Markdown already starts with one. */
    internal fun withTitle(title: String, markdown: String): String =
        if (title.isBlank() || markdown.trimStart().startsWith("# ")) markdown else "# $title\n\n$markdown"

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** Runs of 4+ underscores are fill-in lines of forms; protected from the Markdown parser by private-use marks. */
    private val FILL_LINE = Regex("_{4,}")
    private val FILL_MARK = Regex("\uE000(\\d+)\uE001")

    private fun htmlInline(text: String) =
        inlineSegments(text.replace(FILL_LINE) { "\uE000${it.value.length}\uE001" })
            .joinToString("") { seg -> htmlSeg(seg) }
            .replace(FILL_MARK) { "<span class=\"fill\" style=\"width:${(it.groupValues[1].toInt() * 0.5).coerceIn(3.0, 30.0)}em\"></span>" }

    private val CHECKBOX = Regex("^\\[([ xX])]\\s+")

    private fun htmlSeg(seg: Segment): String {
        var out = esc(seg.text)
        if (seg.code) out = "<code>$out</code>"
        if (seg.bold) out = "<strong>$out</strong>"
        if (seg.italic) out = "<em>$out</em>"
        if (seg.strike) out = "<del>$out</del>"
        if (seg.link != null) out = "<a href=\"${esc(seg.link)}\">$out</a>"
        return out
    }

    private enum class Callout(val label: String, val color: String) {
        NOTE("Hinweis", "#2563EB"), TIP("Tipp", "#15803D"), IMPORTANT("Wichtig", "#7C3AED"),
        WARNING("Achtung", "#B45309"), CAUTION("Vorsicht", "#B91C1C"),
    }

    private val CALLOUT = Regex("^\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION|HINWEIS|TIPP|WICHTIG|ACHTUNG|VORSICHT)]\\s*", RegexOption.IGNORE_CASE)

    private fun callout(text: String): Pair<Callout, String>? {
        val m = CALLOUT.find(text) ?: return null
        val kind = when (m.groupValues[1].uppercase()) {
            "NOTE", "HINWEIS" -> Callout.NOTE
            "TIP", "TIPP" -> Callout.TIP
            "IMPORTANT", "WICHTIG" -> Callout.IMPORTANT
            "WARNING", "ACHTUNG" -> Callout.WARNING
            else -> Callout.CAUTION
        }
        return kind to text.substring(m.range.last + 1)
    }

    private fun cssString(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\""

    /** Header line (left) and date (right) in the top margin of every printed page. */
    private fun StringBuilder.headerBoxes(setup: DocMarkup.PageSetup, font: String, color: String) {
        if (setup.header.isNotBlank()) append("@top-left{content:${cssString(setup.header)};font:$font;color:$color;vertical-align:bottom;padding-bottom:4mm}")
        if (setup.date) append("@top-right{content:${cssString(DocMarkup.today())};font:$font;color:$color;vertical-align:bottom;padding-bottom:4mm}")
    }

    /** A picture of the document: embedded as data URL (local files) or linked (web addresses). */
    private fun figure(img: DocMarkup.Image): String {
        val bytes = DocMarkup.imageBytes(img.src)
        val src = bytes?.let { DocMarkup.dataUrl(it) } ?: img.src.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }
            ?: return "<p class=\"meta\">[Bild: ${esc(img.alt.ifBlank { img.src.substringAfterLast('/') })}]</p>\n"
        val width = img.width?.let { " style=\"width:$it%\"" }.orEmpty()
        return "<figure class=\"img\"><img src=\"$src\" alt=\"${esc(img.alt)}\"$width>" +
            (if (img.alt.isBlank()) "" else "<figcaption>${htmlInline(img.alt)}</figcaption>") + "</figure>\n"
    }

    /** Footnote marks of the text as superscript links to the notes at the end. */
    private fun noteRefs(html: String, numbers: Map<String, Int>): String = if (numbers.isEmpty()) html
    else DocMarkup.NOTE_REF.replace(html) { m ->
        val n = numbers[m.groupValues[1]] ?: return@replace m.value
        "<sup class=\"fnref\"><a href=\"#fn-$n\">$n</a></sup>"
    }

    /** Stylesheet for a document style; [a] is the accent color. Certificates have no footer. */
    private fun css(theme: DocTheme, print: Boolean, certificate: Boolean = false, setup: DocMarkup.PageSetup = DocMarkup.PageSetup()): String {
        val a = theme.accent
        val tint = a + "14"
        val serif = "'Noto Serif', Georgia, 'Times New Roman', serif"
        val sans = "Roboto, 'Helvetica Neue', Arial, sans-serif"
        val s = theme.style
        // Noto Sans is the typeface of the TUD corporate design; it is embedded (TudBrand.fontFaces).
        val tudFont = "'Noto Sans', 'Segoe UI', Roboto, Arial, sans-serif"
        val bodyFont = when (s) {
            DocStyle.CLASSIC -> serif
            DocStyle.TUD -> tudFont
            else -> sans
        }
        val headFont = when (s) {
            DocStyle.CLASSIC, DocStyle.ELEGANT -> serif
            DocStyle.TUD -> tudFont
            else -> sans
        }
        val headColor = when (s) {
            DocStyle.CLASSIC, DocStyle.MINIMAL -> "#111"
            else -> a
        }
        return buildString {
            if (s == DocStyle.TUD) append(TudBrand.fontFaces)
            if (print && certificate) {
                append(if (s == DocStyle.TUD) "@page{size:A4;margin:18mm 25mm 20mm 25mm}" else "@page{size:A4;margin:18mm 20mm 20mm 20mm}")
            } else if (print && s == DocStyle.TUD) {
                // Margins as in DIN 5008 letters; footer with the unit and "Seite x von y".
                val footer = theme.footer.ifBlank {
                    listOfNotNull("Technische Universität Dresden", theme.unit.lines().lastOrNull { it.isNotBlank() }?.trim()).joinToString(" · ")
                }
                append("@page{size:A4;margin:18mm 20mm 22mm 25mm;")
                append("@bottom-left{content:${cssString(footer)};font:7pt $tudFont;color:${TudBrand.GREY};vertical-align:top;padding-top:6mm}")
                if (setup.pageNumbers) append("@bottom-right{content:\"Seite \" counter(page) \" von \" counter(pages);font:7pt $tudFont;color:${TudBrand.GREY};vertical-align:top;padding-top:6mm}")
                headerBoxes(setup, "7pt $tudFont", TudBrand.GREY)
                append("}")
            } else if (print) {
                append("@page{size:A4;margin:18mm 17mm 20mm 17mm;")
                if (setup.pageNumbers) append("@bottom-right{content:\"Seite \" counter(page) \" von \" counter(pages);font:8pt $sans;color:#8a8f98}")
                if (theme.footer.isNotBlank()) append("@bottom-left{content:${cssString(theme.footer)};font:8pt $sans;color:#8a8f98}")
                headerBoxes(setup, "8pt $sans", "#8a8f98")
                append("}")
            } else {
                append("body{max-width:800px;margin:2rem auto;padding:0 1.2rem}")
            }
            // Landscape pages (<!-- quer -->), page breaks, pictures and footnotes of the editor.
            if (print) append("@page quer{size:A4 landscape}section.quer{page:quer;break-before:page;page-break-before:always;break-after:page;page-break-after:always}")
            append(".pagebreak{break-after:page;page-break-after:always;height:0}")
            append("figure.img{margin:10pt 0 12pt;text-align:center;break-inside:avoid;page-break-inside:avoid}figure.img img{max-width:100%;height:auto}")
            append("figure.img figcaption{font-size:9pt;color:#555;font-style:italic;margin-top:4pt}")
            append("sup.fnref{font-size:.7em;line-height:0}sup.fnref a{text-decoration:none}")
            append(".footnotes{margin-top:18pt;font-size:8.5pt;color:#333}.footnotes hr{border:none;border-top:0.6pt solid #999;width:30%;margin:0 0 6pt}")
            append(".footnotes ol{padding-left:14pt;margin:0}.footnotes li{margin:0 0 3pt}")
            append("html{-webkit-print-color-adjust:exact;print-color-adjust:exact}")
            append("body{font-family:$bodyFont;font-size:10.5pt;line-height:1.55;color:#1f2328}")
            append("h1,h2,h3,h4{font-family:$headFont;line-height:1.25;break-after:avoid;page-break-after:avoid}")
            append("h2{font-size:15pt;margin:20pt 0 6pt;color:$headColor}")
            append("h3{font-size:12.5pt;margin:14pt 0 4pt;color:${if (s == DocStyle.BOLD) a else "#1f2328"}}")
            append("h4{font-size:11pt;margin:12pt 0 4pt}")
            append("h1{font-size:20pt;margin:18pt 0 8pt;color:$headColor}")
            append("p{margin:0 0 8pt}ul,ol{margin:0 0 8pt;padding-left:18pt}li{margin:2pt 0}")
            append("a{color:$a;text-decoration:none}strong{font-weight:700}")
            append("code{font-family:'Roboto Mono',monospace;font-size:9pt;background:#f2f4f7;padding:1pt 3pt;border-radius:3pt}")
            append("pre{background:#f6f8fa;border:1px solid #e5e7eb;border-radius:6pt;padding:8pt 10pt;white-space:pre-wrap;font-size:8.5pt;break-inside:avoid}")
            append("pre code{background:none;padding:0}")
            append("table{border-collapse:collapse;width:100%;margin:8pt 0 12pt;font-size:9.5pt;break-inside:auto}")
            append("tr{break-inside:avoid}th,td{padding:5pt 7pt;text-align:left;vertical-align:top}")
            append("hr{border:none;border-top:1px solid #d9dde3;margin:14pt 0}")
            append("blockquote{margin:8pt 0;padding:6pt 12pt;border-left:3pt solid ${if (s == DocStyle.MINIMAL || s == DocStyle.CLASSIC) "#999" else a};background:${if (s == DocStyle.MINIMAL) "transparent" else tint};color:#374151}")
            append(".callout{margin:10pt 0;padding:8pt 12pt;border-radius:6pt;border:1px solid;break-inside:avoid}")
            append(".callout .label{font-weight:700;margin-bottom:2pt;font-size:9.5pt;text-transform:uppercase;letter-spacing:.04em}")
            append(".header{display:flex;align-items:center;justify-content:space-between;gap:16pt;margin-bottom:16pt}")
            append(".header img{max-height:52pt;max-width:170pt;object-fit:contain}")
            append(".meta{color:#6b7280;font-size:9pt;margin-top:3pt}")
            append(".title{font-family:$headFont;font-size:24pt;font-weight:700;line-height:1.2;margin:0}")
            // Forms: checkboxes, fill-in lines and empty table cells to write in.
            append("li.task{list-style:none;margin-left:-14pt}")
            append(".cb{display:inline-block;width:8.5pt;height:8.5pt;border:1pt solid #333;border-radius:1.5pt;margin-right:6pt;vertical-align:-1pt;text-align:center;line-height:8.5pt;font-size:8pt}")
            append(".fill{display:inline-block;border-bottom:0.8pt solid #555;height:1.1em;vertical-align:bottom;max-width:100%}")
            append("td:empty{height:20pt}td:only-child:empty{height:70pt}")
            // Letters
            append(".letter-head{display:flex;justify-content:space-between;align-items:flex-start;gap:16pt;margin-bottom:10pt}")
            append(".letter-head img{max-height:44pt;max-width:170pt;object-fit:contain}")
            append(".sender{text-align:right;font-size:8.5pt;line-height:1.45;color:#333}")
            append(".address{width:85mm;min-height:40mm;margin:16pt 0 18pt;font-size:10.5pt;line-height:1.4}")
            append(".postal{font-size:7pt;color:#666;border-bottom:0.5pt solid #999;padding-bottom:2pt;margin-bottom:6pt}")
            append(".letter-date{text-align:right;margin-bottom:16pt}")
            append(".subject{font-weight:700;font-size:11pt;margin-bottom:16pt}")
            // Room for the handwritten signature between closing formula and name.
            append(".closing{break-inside:avoid;page-break-inside:avoid;margin-top:10pt}.closing p+p{margin-top:13mm}")
            // Certificates (layout of the official TUD template „Zertifikate & Urkunden“): centred main part,
            // small print, date and signatures built up from the bottom of the page.
            append(".cert{position:relative;min-height:240mm}.cert.print{height:255mm;min-height:0}")
            append(".cert-main{text-align:center;padding-top:14mm}")
            append(".cert-kind{font-size:13pt;font-weight:500;letter-spacing:.04em;color:$headColor;margin-bottom:12mm}")
            append(".cert-big{font-size:23pt;line-height:1.25;color:$headColor;margin:2mm 0 4mm}")
            append(".cert-text{font-size:11pt;margin:0 0 3mm}")
            append(".cert-foot{position:absolute;left:0;right:0;bottom:0}")
            append(".cert-small{font-size:9pt;line-height:1.45;margin-bottom:8mm}.cert-small p{margin:0 0 3pt}")
            append(".cert-date{font-size:9pt;margin-bottom:22mm}")
            append(".cert-sign{display:flex;justify-content:space-between;gap:20mm;font-size:9pt;line-height:1.4}")
            append(".cert-sign>div{flex:0 1 70mm}.cert-sign>div:last-child:not(:first-child){text-align:right}")
            append(".cert-sign .line{border-top:0.6pt solid #000;padding-top:2mm}")
            when (s) {
                DocStyle.MODERN -> {
                    append(".bar{height:5pt;background:$a;border-radius:3pt;margin-bottom:14pt}")
                    append(".title{color:#111827}")
                    append("h2{padding-bottom:3pt;border-bottom:1.5pt solid $tint}")
                    append("th{background:$a;color:#fff;font-weight:600}td{border-bottom:1px solid #e5e7eb}tr:nth-child(even) td{background:#f9fafb}")
                }
                DocStyle.BOLD -> {
                    append(".hero{background:$a;color:#fff;border-radius:10pt;padding:18pt 20pt;margin-bottom:18pt;display:flex;align-items:center;justify-content:space-between;gap:16pt}")
                    append(".hero .title{color:#fff}.hero .meta{color:rgba(255,255,255,.8)}")
                    append(".hero img{max-height:48pt;max-width:150pt;background:#fff;border-radius:8pt;padding:6pt}")
                    append("h2{background:$tint;border-left:4pt solid $a;padding:4pt 8pt;border-radius:0 4pt 4pt 0}")
                    append("th{background:$a;color:#fff}td{border:1px solid #e5e7eb}tr:nth-child(even) td{background:$tint}")
                }
                DocStyle.ELEGANT -> {
                    append(".title{color:$a;font-weight:400;letter-spacing:.02em;font-size:26pt}")
                    append(".rule{height:1px;background:$a;margin:6pt 0 18pt}")
                    append("h2{font-weight:400;letter-spacing:.02em}")
                    append("th{border-bottom:1.5pt solid $a;color:$a;font-weight:600}td{border-bottom:1px solid #e5e7eb}")
                }
                DocStyle.CLASSIC -> {
                    append(".classic{text-align:center;margin-bottom:18pt}.classic img{max-height:56pt;max-width:180pt;margin-bottom:8pt}")
                    append(".classic .title{font-size:22pt}.double{border-top:3px double #333;margin-top:10pt}")
                    append("h2{font-variant:small-caps;letter-spacing:.03em}")
                    append("th{border-bottom:1.5pt solid #333;font-weight:700}td{border-bottom:1px solid #ccc}")
                }
                DocStyle.MINIMAL -> {
                    append("th{border-bottom:1px solid #333;font-weight:600}td{border-bottom:1px solid #e5e7eb}")
                }
                DocStyle.TUD -> {
                    // Corporate design 2025: Noto Sans (headings Medium), Brillantblau for title and headings, Blau 2 at
                    // 25 % for table heads and boxes, the grey of the theme for secondary text, thin grey lines.
                    val line = "#BBC1C6"
                    val muted = TudBrand.GREY
                    val tint = "#E5F1FF"
                    val soft = "#F3F8FF"
                    append("body{font-size:10.5pt;line-height:1.5;color:#000}body.letter{font-size:11pt}")
                    // Name and event of certificates in Medium as in the CD's certificate templates.
                    append("h1,h2,h3,h4,.title,.cert-kind,.cert-big{font-weight:500}")
                    // Logo 17 mm high at the top left (25 mm / 18 mm from the edges, as in the official templates),
                    // the organizational unit in small type below it.
                    append(".tud-head{margin-bottom:10mm}")
                    append(".tud-head img{display:block;height:17mm;width:auto;max-width:80mm;object-fit:contain;object-position:left top}")
                    append(".unit{margin-top:3mm;font-size:8.5pt;line-height:1.4;color:#000}.unit b{font-weight:600}")
                    append(".title{color:$a;font-size:17pt;line-height:1.25;margin:0 0 3pt}")
                    append(".meta{color:$muted;font-size:8pt;margin:0 0 14pt;padding-bottom:8pt;border-bottom:0.6pt solid $line}")
                    append("h1{font-size:14pt}h2{font-size:12pt;color:$a;margin:14pt 0 5pt}")
                    append("h3{font-size:10.5pt;color:#000;margin:12pt 0 4pt}")
                    append("table{font-size:9.5pt;margin:4pt 0 12pt}")
                    append("th{background:$tint;color:#000;font-weight:600;border:0.6pt solid $line;font-size:9pt}")
                    append("td{border:0.6pt solid $line}")
                    // Two-column rows are form fields: label on the left (small, tinted), space to write on the right.
                    append("td:first-child:nth-last-child(2):not(:empty){width:36%;background:$soft;font-size:9pt;color:#1a1a1a}")
                    append("td:empty{height:17pt}td:only-child:empty{height:60pt}")
                    append(".cb{border:0.8pt solid #000;border-radius:0;width:9pt;height:9pt;line-height:9pt}")
                    append(".fill{border-bottom-color:#000}")
                    append("blockquote{border-left:2pt solid $a;background:$soft;color:#000}")
                    append(".callout.tud{border:none;border-left:2pt solid $a;background:$soft;border-radius:0;padding:6pt 10pt}")
                    append(".callout.tud .label{color:$a;font-size:7.5pt;letter-spacing:.06em}")
                    // DIN 5008 form B: address field 45 mm from the top edge (return line just above the address),
                    // information block from 125 mm, subject two lines below the address field.
                    append(".tud-head.letter{margin-bottom:0}")
                    append(".letter-row{display:flex;justify-content:space-between;margin:10mm 0 8.5mm}")
                    append(".letter-row .address{width:85mm;min-height:45mm;margin:0;font-size:11pt;line-height:1.35}")
                    append(".postal{font-size:7pt;color:$muted;border-bottom:0.5pt solid $line;padding-bottom:1.5pt;margin:11mm 0 2mm}")
                    append(".info{width:65mm;font-size:8.5pt;line-height:1.5}.info .k{color:$muted}")
                    append(".info .unit{margin:0 0 4mm;font-size:8.5pt}")
                    append(".subject{color:#000;font-weight:600;font-size:11pt;margin-bottom:8.5mm}")
                }
            }
        }
    }

    private fun linesHtml(text: String) =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("<br>") { htmlInline(it) }

    /** The organizational unit, one level per line, the last (most specific) one bold. */
    private fun unitHtml(theme: DocTheme): String {
        val lines = theme.unit.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return ""
        return "<div class=\"unit\">" + lines.mapIndexed { i, l -> if (i == lines.lastIndex) "<b>${esc(l)}</b>" else esc(l) }.joinToString("<br>") + "</div>"
    }

    /** Logo at the top left with the organizational unit below it, as in the official TUD templates. */
    private fun tudHead(theme: DocTheme, withUnit: Boolean = true): String {
        val logo = theme.logoDataUrl?.let { "<img src=\"$it\" alt=\"TU Dresden\">" }.orEmpty()
        return "<div class=\"tud-head${if (withUnit) "" else " letter"}\">$logo${if (withUnit) unitHtml(theme) else ""}</div>"
    }

    private fun tudLetterBlock(title: String, theme: DocTheme, letter: Letter): String {
        val date = DateFormat.getDateInstance(DateFormat.LONG, Locale.GERMANY).format(Date())
        val sender = letter.sender.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val info = sender.mapIndexed { i, l ->
            val k = l.substringBefore(':', "")
            when {
                i == 0 -> "<b>${htmlInline(l)}</b>"
                k.isNotEmpty() && k.length <= 14 -> "<span class=\"k\">${esc(k)}:</span>${htmlInline(l.substringAfter(':'))}"
                else -> htmlInline(l)
            }
        }.joinToString("<br>")
        // The unit moves into the information block, so the address field keeps its place 45 mm from the top.
        return tudHead(theme, withUnit = false) +
            "<div class=\"letter-row\"><div class=\"address\"><div class=\"postal\">${esc(TudBrand.POSTAL)}</div>" +
            "${linesHtml(letter.recipient)}</div><div class=\"info\">${unitHtml(theme)}$info" +
            "${if (info.isEmpty()) "" else "<br><br>"}<span class=\"k\">Datum:</span> $date</div></div>" +
            (if (title.isBlank()) "" else "<div class=\"subject\"><b>${htmlInline(title)}</b></div>")
    }

    private fun letterBlock(title: String, theme: DocTheme, letter: Letter): String {
        if (theme.style == DocStyle.TUD) return tudLetterBlock(title, theme, letter)
        val logo = theme.logoDataUrl?.let { "<img src=\"$it\" alt=\"Logo\">" }.orEmpty()
        val date = DateFormat.getDateInstance(DateFormat.LONG, Locale.GERMANY).format(Date())
        return "<div class=\"letter-head\">$logo<div class=\"sender\">${linesHtml(letter.sender)}</div></div>" +
            "<div class=\"address\">${linesHtml(letter.recipient)}</div>" +
            "<div class=\"letter-date\">Dresden, $date</div>" +
            (if (title.isBlank()) "" else "<div class=\"subject\">${htmlInline(title)}</div>")
    }

    private fun titleBlock(title: String, theme: DocTheme): String {
        if (title.isBlank() && theme.logo == null) return ""
        val logo = theme.logoDataUrl?.let { "<img src=\"$it\" alt=\"Logo\">" }.orEmpty()
        val date = DateFormat.getDateInstance(DateFormat.LONG, Locale.GERMANY).format(Date())
        val t = if (title.isBlank()) "" else "<div class=\"title\">${htmlInline(title)}</div>"
        val meta = "<div class=\"meta\">$date</div>"
        return when (theme.style) {
            DocStyle.BOLD -> "<div class=\"hero\"><div>$t$meta</div>$logo</div>"
            DocStyle.CLASSIC -> "<div class=\"classic\">$logo$t$meta<div class=\"double\"></div></div>"
            DocStyle.ELEGANT -> "<div class=\"header\"><div>$t$meta</div>$logo</div><div class=\"rule\"></div>"
            DocStyle.MODERN -> "<div class=\"bar\"></div><div class=\"header\"><div>$t$meta</div>$logo</div>"
            DocStyle.MINIMAL -> "<div class=\"header\"><div>$t$meta</div>$logo</div>"
            DocStyle.TUD -> tudHead(theme) + t + "<div class=\"meta\">Stand: $date</div>"
        }
    }

    /**
     * Certificate after the official template: "## " lines are the big lines (name, event), paragraphs the
     * centred text, a quote the small print and the last table the signatures (one column per person).
     */
    private fun certificateHtml(title: String, markdown: String, theme: DocTheme, print: Boolean): String {
        val main = StringBuilder()
        val small = StringBuilder()
        var signers: List<List<String>> = emptyList()
        for (b in parseBlocks(markdown)) when (b) {
            is Block.Heading -> main.append("<div class=\"cert-big\">${htmlInline(b.text)}</div>")
            is Block.Paragraph -> main.append("<p class=\"cert-text\">${b.text.lines().joinToString("<br>") { htmlInline(it) }}</p>")
            is Block.Item -> main.append("<p class=\"cert-text\">${htmlInline(b.text)}</p>")
            is Block.Quote -> small.append("<p>${(callout(b.text)?.second ?: b.text).lines().joinToString("<br>") { htmlInline(it) }}</p>")
            is Block.Table -> signers = (0 until b.rows.maxOf { it.size }).map { c ->
                b.rows.mapNotNull { row -> row.getOrNull(c)?.trim()?.takeIf { it.isNotEmpty() } }
            }
            else -> {}
        }
        val date = DateFormat.getDateInstance(DateFormat.LONG, Locale.GERMANY).format(Date())
        val head = if (theme.style == DocStyle.TUD) tudHead(theme)
        else theme.logoDataUrl?.let { "<div class=\"tud-head\"><img src=\"$it\" alt=\"Logo\" style=\"max-height:20mm\"></div>" }.orEmpty()
        val sign = signers.joinToString("") { lines ->
            "<div><div class=\"line\">" + lines.mapIndexed { i, l -> if (i == 0) "<b>${htmlInline(l)}</b>" else htmlInline(l) }.joinToString("<br>") + "</div></div>"
        }
        return "<div class=\"cert${if (print) " print" else ""}\">$head<div class=\"cert-main\">" +
            (if (title.isBlank()) "" else "<div class=\"cert-kind\">${htmlInline(title)}</div>") + main +
            "</div><div class=\"cert-foot\">" + (if (small.isEmpty()) "" else "<div class=\"cert-small\">$small</div>") +
            "<div class=\"cert-date\">Dresden, $date</div>" + (if (sign.isEmpty()) "" else "<div class=\"cert-sign\">$sign</div>") +
            "</div></div>\n"
    }

    /** Converts Markdown into a styled HTML page (also the source for PDFs). */
    fun toHtml(
        title: String,
        markdown: String,
        theme: DocTheme = DocTheme(),
        print: Boolean = false,
        letter: Letter? = null,
        certificate: Boolean = false,
    ): String = buildString {
        @Suppress("NAME_SHADOWING") val theme = TudBrand.apply(theme)
        // A leading "# Heading" becomes the title of the document.
        var body = markdown.trimStart()
        var docTitle = title
        if (letter == null) Regex("^#\\s+(.+)").find(body.lineSequence().firstOrNull().orEmpty())?.let {
            docTitle = it.groupValues[1].trim()
            body = body.substringAfter('\n', "")
        }
        append("<!DOCTYPE html>\n<html lang=\"de\">\n<head>\n<meta charset=\"utf-8\">\n")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        append("<title>${esc(docTitle.ifBlank { "Dokument" })}</title>\n<style>\n")
        append(css(theme, print, certificate, DocMarkup.pageSetup(markdown)))
        append("\n</style>\n</head>\n<body${if (letter != null) " class=\"letter\"" else ""}>\n")
        if (certificate) {
            append(certificateHtml(docTitle, body, theme, print))
            append("</body>\n</html>\n")
            return@buildString
        }
        append(if (letter != null) letterBlock(docTitle, theme, letter) else titleBlock(docTitle, theme))
        val footnotes = DocMarkup.footnotes(DocMarkup.isolate(body))
        val numbers = footnotes.numbers
        fun inl(text: String) = noteRefs(htmlInline(text), numbers)
        var landscape = false
        val blocks = parseBlocks(footnotes.body)
        var openList: String? = null
        fun closeList() {
            openList?.let { append("</$it>\n") }
            openList = null
        }
        // Letters: closing formula and signature stay together on one page.
        val closingAt = if (letter == null) -1 else blocks.indexOfLast { it is Block.Paragraph && LetterBuilder.isClosing(it.text) }
        for ((i, b) in blocks.withIndex()) {
            if (b !is Block.Item) closeList()
            if (i == closingAt) append("<div class=\"closing\">\n")
            when (b) {
                is Block.Heading -> append("<h${b.level}>${inl(b.text)}</h${b.level}>\n")
                is Block.Paragraph -> {
                    val lines = b.text.lines()
                    val turn = DocMarkup.orientation(b.text)
                    val img = DocMarkup.image(b.text)
                    when {
                        turn != null -> if (turn != landscape) {
                            append(if (turn) "<section class=\"quer\">\n" else "</section>\n")
                            landscape = turn
                        }
                        DocMarkup.isPageBreak(b.text) -> append("<div class=\"pagebreak\"></div>\n")
                        DocMarkup.isComment(b.text) -> {}
                        img != null -> append(figure(img))
                        // "Mit freundlichen Grüßen" directly followed by the name: keep room for the signature in between.
                        i == closingAt && lines.size > 1 ->
                            append("<p>${inl(lines.first())}</p>\n<p>${lines.drop(1).joinToString("<br>\n") { inl(it) }}</p>\n")
                        else -> append("<p>${lines.joinToString("<br>\n") { inl(it) }}</p>\n")
                    }
                }
                is Block.Item -> {
                    val tag = if (b.marker.first().isDigit()) "ol" else "ul"
                    if (openList != tag) {
                        closeList()
                        append("<$tag>\n")
                        openList = tag
                    }
                    val style = if (b.indent > 0) " style=\"margin-left:${b.indent * 1.5}em\"" else ""
                    val box = CHECKBOX.find(b.text)
                    if (box == null) append("<li$style>${inl(b.text)}</li>\n")
                    else {
                        val mark = if (box.groupValues[1].isBlank()) "" else "✓"
                        append("<li class=\"task\"$style><span class=\"cb\">$mark</span>${inl(b.text.substring(box.range.last + 1))}</li>\n")
                    }
                }
                is Block.Quote -> {
                    val c = callout(b.text)
                    val lines = (c?.second ?: b.text).lines().joinToString("<br>\n") { inl(it) }
                    if (c == null || theme.style == DocStyle.MINIMAL) append("<blockquote>$lines</blockquote>\n")
                    else if (theme.style == DocStyle.TUD) {
                        append("<div class=\"callout tud\"><div class=\"label\">${c.first.label}</div>$lines</div>\n")
                    } else {
                        val color = c.first.color
                        append("<div class=\"callout\" style=\"border-color:${color}55;background:${color}12\">")
                        append("<div class=\"label\" style=\"color:$color\">${c.first.label}</div>$lines</div>\n")
                    }
                }
                is Block.Code -> {
                    // Charts written as ```chart {…}``` become vector graphics with their data as text alternative.
                    val chart = if (b.lang.equals("chart", ignoreCase = true)) ChartSpec.parse(b.code) else null
                    if (chart != null) {
                        append("<figure class=\"chart\" style=\"margin:1em 0;break-inside:avoid\">").append(ChartLayout.svg(chart))
                        if (chart.title.isNotBlank()) append("<figcaption style=\"font-size:0.85em;color:#555\">Abbildung: ${esc(chart.title)}</figcaption>")
                        append("</figure>\n")
                    } else append("<pre><code>${esc(b.code)}</code></pre>\n")
                }
                is Block.Table -> {
                    append("<table>\n")
                    b.rows.forEachIndexed { r, row ->
                        val cell = if (r == 0) "th scope=\"col\"" else "td"
                        val close = if (r == 0) "th" else "td"
                        append("<tr>")
                        row.forEach { append("<$cell>${inl(it)}</$close>") }
                        append("</tr>\n")
                    }
                    append("</table>\n")
                }
                Block.Rule -> append("<hr>\n")
            }
        }
        closeList()
        if (closingAt >= 0) append("</div>\n")
        if (landscape) append("</section>\n")
        if (footnotes.notes.isNotEmpty()) {
            append("<section class=\"footnotes\" aria-label=\"Fußnoten\"><hr><ol>\n")
            footnotes.notes.forEach { n -> append("<li id=\"fn-${n.number}\">${htmlInline(n.text)}</li>\n") }
            append("</ol></section>\n")
        }
        if (!print && theme.footer.isNotBlank()) append("<hr><p class=\"meta\">${esc(theme.footer)}</p>\n")
        append("</body>\n</html>\n")
    }

    private fun plainInline(text: String) = inlineSegments(text).joinToString("") { s ->
        if (s.link != null && s.link != s.text) "${s.text} (${s.link})" else s.text
    }

    fun toText(title: String, markdown: String): String = buildString {
        var prev: Block? = null
        val footnotes = DocMarkup.footnotes(DocMarkup.isolate(markdown))
        fun plainInline(text: String) = DocMarkup.NOTE_REF.replace(this@DocumentWriter.plainInline(text)) { m ->
            footnotes.numbers[m.groupValues[1]]?.let { "[$it]" } ?: m.value
        }
        for (b in parseBlocks(withTitle(title, footnotes.body))) {
            if (b is Block.Paragraph && (DocMarkup.isComment(b.text) || DocMarkup.isPageBreak(b.text))) continue
            val img = (b as? Block.Paragraph)?.let { DocMarkup.image(it.text) }
            if (img != null) {
                append("[Bild: ").append(img.alt.ifBlank { img.src.substringAfterLast('/') }).append("]\n\n")
                continue
            }
            if (prev is Block.Item && b !is Block.Item) append('\n')
            prev = b
            when (b) {
                is Block.Heading -> {
                    val t = plainInline(b.text)
                    append(t).append('\n')
                    if (b.level <= 2) append((if (b.level == 1) "=" else "-").repeat(t.length.coerceAtMost(60))).append('\n')
                }
                is Block.Paragraph -> append(b.text.lines().joinToString("\n") { plainInline(it) }).append('\n')
                is Block.Item -> append("  ".repeat(b.indent)).append(b.marker).append(' ').append(plainInline(b.text)).append('\n')
                is Block.Quote -> append(b.text.lines().joinToString("\n") { "> " + plainInline(it) }).append('\n')
                is Block.Code -> append(b.code.lines().joinToString("\n") { "    $it" }).append('\n')
                is Block.Table -> b.rows.forEach { row -> append(row.joinToString(" | ") { plainInline(it) }).append('\n') }
                Block.Rule -> append("-".repeat(40)).append('\n')
            }
            if (b !is Block.Item) append('\n')
        }
        if (footnotes.notes.isNotEmpty()) {
            append("\n").append("-".repeat(20)).append('\n')
            footnotes.notes.forEach { n -> append("[${n.number}] ").append(plainInline(n.text)).append('\n') }
        }
    }.trimEnd() + "\n"
}
