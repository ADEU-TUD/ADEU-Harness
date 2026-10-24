package io.github.thepytales.llmhub.docs

import android.content.Context
import java.io.File
import java.util.zip.ZipFile

/** What the PDF check found (see PdfAccessibility.inspect). */
data class PdfAccessInfo(
    val pages: Int,
    val tagged: Boolean,
    val language: String,
    val title: String,
    val displayTitle: Boolean,
    /** Characters of extractable text on the first pages; near 0 means a scan without text layer. */
    val textChars: Int,
    val bookmarks: Boolean,
    val fontsNotEmbedded: List<String>,
    val images: Int,
    val formFields: Int,
    val fieldsWithoutTooltip: Int,
    val encrypted: Boolean,
)

/**
 * Checks documents for the most important points of accessibility (PDF/UA, WCAG and the accessibility check of
 * Office): text layer, tags, language, title, headings, alternative texts, table heads, slide titles. It does not
 * replace a full test (e.g. with PAC 2024), but finds what most often keeps screen-reader users out.
 */
object AccessibilityCheck {
    data class Finding(val ok: Boolean, val text: String, val fix: String = "")

    fun check(context: Context, file: File): List<Finding> = when (file.extension.lowercase()) {
        "pdf" -> pdf(PdfAccessibility.inspect(context, file))
        "docx" -> docx(file)
        "pptx" -> pptx(file)
        "html", "htm" -> html(file.readText())
        else -> listOf(Finding(false, "Dieses Format (.${file.extension}) kann die App nicht prüfen.", "PDF, Word, PowerPoint oder HTML verwenden."))
    }

    fun pdf(i: PdfAccessInfo): List<Finding> = buildList {
        add(
            if (i.textChars >= 40 || i.pages == 0) Finding(true, "Text ist auswählbar (keine reine Bilddatei).")
            else Finding(false, "Kein Text erkennbar – vermutlich ein Scan.", "Mit Texterkennung (OCR) neu erzeugen oder aus der Quelldatei exportieren.")
        )
        add(
            if (i.tagged) Finding(true, "Getaggtes PDF (Struktur für Screenreader vorhanden).")
            else Finding(false, "Nicht getaggt: Screenreader kennen weder Überschriften noch Lesereihenfolge.", "In Word/PowerPoint mit „Barrierefreies PDF“ bzw. „Dokumentstrukturtags“ exportieren.")
        )
        add(
            if (i.language.isNotBlank()) Finding(true, "Dokumentsprache: ${i.language}.")
            else Finding(false, "Keine Dokumentsprache angegeben.", "Sprache (z.B. de-DE) in den Dokumenteigenschaften setzen.")
        )
        add(
            if (i.title.isNotBlank() && i.displayTitle) Finding(true, "Titel „${i.title}“ wird im Fenster angezeigt.")
            else if (i.title.isNotBlank()) Finding(false, "Titel vorhanden, aber das Fenster zeigt den Dateinamen.", "Unter Dokumenteigenschaften › Ansicht „Dokumenttitel anzeigen“ wählen.")
            else Finding(false, "Kein Dokumenttitel.", "Titel in den Dokumenteigenschaften eintragen.")
        )
        if (i.pages > 4) add(
            if (i.bookmarks) Finding(true, "Lesezeichen zur Navigation vorhanden.")
            else Finding(false, "Keine Lesezeichen bei ${i.pages} Seiten.", "Beim Export „Lesezeichen aus Überschriften erstellen“ wählen.")
        )
        if (i.fontsNotEmbedded.isNotEmpty()) add(Finding(false, "Nicht eingebettete Schriften: ${i.fontsNotEmbedded.take(5).joinToString(", ")}.", "Schriften beim Export einbetten."))
        else add(Finding(true, "Alle Schriften eingebettet."))
        if (i.formFields > 0) add(
            if (i.fieldsWithoutTooltip == 0) Finding(true, "Alle ${i.formFields} Formularfelder haben eine Beschreibung.")
            else Finding(false, "${i.fieldsWithoutTooltip} von ${i.formFields} Formularfeldern ohne Beschreibung (QuickInfo).", "Jedem Feld eine QuickInfo geben, die vorgelesen wird.")
        )
        if (i.images > 0 && !i.tagged) add(Finding(false, "${i.images} Bild(er) ohne Strukturtags – Alternativtexte fehlen damit sicher.", "Bilder in der Quelldatei mit Alternativtext versehen und getaggt exportieren."))
        if (i.encrypted) add(Finding(false, "Das PDF ist verschlüsselt; Hilfsprogramme dürfen den Text eventuell nicht lesen.", "Ohne Kennwortschutz bzw. mit erlaubtem Zugriff für Hilfstechnologien speichern."))
    }

    private fun zipText(file: File, filter: (String) -> Boolean): Map<String, String> = ZipFile(file).use { zip ->
        zip.entries().asSequence().filter { !it.isDirectory && filter(it.name) }.associate { e -> e.name to zip.getInputStream(e).bufferedReader().readText() }
    }

    fun docx(file: File): List<Finding> {
        val parts = zipText(file) { it == "word/document.xml" || it == "word/styles.xml" || it == "docProps/core.xml" }
        val doc = parts["word/document.xml"].orEmpty()
        val styles = parts["word/styles.xml"].orEmpty()
        val core = parts["docProps/core.xml"].orEmpty()
        val headingStyles = Regex("<w:pStyle w:val=\"(Heading|berschrift|Überschrift)\\d", RegexOption.IGNORE_CASE).findAll(doc).count()
        val pictures = Regex("<wp:docPr\\b[^>]*>").findAll(doc).toList()
        val noAlt = pictures.count { !Regex("descr=\"[^\"]+\"").containsMatchIn(it.value) && !it.value.contains("decorative") }
        val tables = Regex("<w:tbl>").findAll(doc).count()
        val tableHeads = Regex("<w:tblHeader/>").findAll(doc).count()
        val lang = Regex("<w:lang w:val=\"([^\"]+)\"").find(styles + doc)?.groupValues?.get(1)
        val title = Regex("<dc:title>([^<]+)</dc:title>").find(core)?.groupValues?.get(1)
        return buildList {
            add(if (headingStyles > 0) Finding(true, "$headingStyles Überschriften mit Formatvorlage (Navigation möglich).") else Finding(false, "Keine Überschriften-Formatvorlagen.", "Überschriften mit „Überschrift 1/2/3“ formatieren statt nur fett."))
            if (pictures.isNotEmpty()) add(if (noAlt == 0) Finding(true, "Alle ${pictures.size} Bilder haben Alternativtext.") else Finding(false, "$noAlt von ${pictures.size} Bildern ohne Alternativtext.", "Rechtsklick › Alternativtext bearbeiten."))
            if (tables > 0) add(if (tableHeads >= tables) Finding(true, "Tabellen haben Kopfzeilen.") else Finding(false, "${tables - tableHeads} Tabelle(n) ohne markierte Kopfzeile.", "Tabelleneigenschaften › Zeile › „Gleiche Kopfzeile auf jeder Seite wiederholen“."))
            add(if (lang != null) Finding(true, "Dokumentsprache: $lang.") else Finding(false, "Keine Dokumentsprache.", "Überprüfen › Sprache › Sprache für die Korrekturhilfen festlegen."))
            add(if (!title.isNullOrBlank()) Finding(true, "Titel: „$title“.") else Finding(false, "Kein Dokumenttitel.", "Datei › Informationen › Titel eintragen."))
        }
    }

    fun pptx(file: File): List<Finding> {
        val slides = zipText(file) { Regex("ppt/slides/slide\\d+\\.xml").matches(it) }
        val core = zipText(file) { it == "docProps/core.xml" }.values.firstOrNull().orEmpty()
        val withoutTitle = slides.filterValues { !Regex("<p:ph type=\"(title|ctrTitle)\"").containsMatchIn(it) }.keys
            .map { it.substringAfterLast("slide").substringBefore(".xml").toIntOrNull() ?: 0 }.sorted()
        val pics = slides.values.flatMap { Regex("<p:nvPicPr><p:cNvPr\\b[^>]*>").findAll(it).map { m -> m.value }.toList() }
        val noAlt = pics.count { !Regex("descr=\"[^\"]+\"").containsMatchIn(it) }
        val title = Regex("<dc:title>([^<]+)</dc:title>").find(core)?.groupValues?.get(1)
        return buildList {
            add(if (withoutTitle.isEmpty()) Finding(true, "Alle ${slides.size} Folien haben einen Folientitel.") else Finding(false, "Folien ohne Titel: ${withoutTitle.joinToString(", ")}.", "Jeder Folie einen (ggf. ausgeblendeten) Titel geben."))
            if (pics.isNotEmpty()) add(if (noAlt == 0) Finding(true, "Alle ${pics.size} Bilder haben Alternativtext.") else Finding(false, "$noAlt Bild(er) ohne Alternativtext.", "Rechtsklick › Alternativtext bearbeiten."))
            add(if (!title.isNullOrBlank()) Finding(true, "Titel: „$title“.") else Finding(false, "Kein Präsentationstitel.", "Datei › Informationen › Titel eintragen."))
        }
    }

    fun html(text: String): List<Finding> = buildList {
        add(if (Regex("<html[^>]*\\blang=", RegexOption.IGNORE_CASE).containsMatchIn(text)) Finding(true, "Sprache angegeben.") else Finding(false, "Kein lang-Attribut.", "<html lang=\"de\"> setzen."))
        add(if (Regex("<title>[^<]+</title>", RegexOption.IGNORE_CASE).containsMatchIn(text)) Finding(true, "Seitentitel vorhanden.") else Finding(false, "Kein Seitentitel.", "<title> ergänzen."))
        val imgs = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(text).toList()
        if (imgs.isNotEmpty()) {
            val noAlt = imgs.count { !Regex("\\balt=", RegexOption.IGNORE_CASE).containsMatchIn(it.value) }
            add(if (noAlt == 0) Finding(true, "Alle Bilder haben alt-Text.") else Finding(false, "$noAlt Bild(er) ohne alt-Text.", "alt=\"…\" ergänzen (bei Schmuckbildern alt=\"\")."))
        }
        add(if (Regex("<h[1-3]\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) Finding(true, "Überschriften-Struktur vorhanden.") else Finding(false, "Keine Überschriften (h1–h3).", "Gliederung mit Überschriften-Elementen."))
    }

    fun report(name: String, findings: List<Finding>): String = buildString {
        val problems = findings.count { !it.ok }
        append("Barrierefreiheit von „$name“: ")
        append(if (problems == 0) "keine Probleme bei den geprüften Punkten." else "$problems Punkt(e) zu verbessern.")
        append("\n\n")
        findings.forEach { f ->
            append(if (f.ok) "✓ " else "✗ ").append(f.text)
            if (!f.ok && f.fix.isNotBlank()) append(" → ").append(f.fix)
            append('\n')
        }
        append(
            "\nGeprüft wurden die häufigsten Hürden (Text, Tags, Sprache, Titel, Überschriften, Alternativtexte, Tabellen). Eine " +
                "vollständige Prüfung nach PDF/UA bzw. WCAG (z.B. mit PAC) ersetzt das nicht. Die App erstellt Word- und " +
                "PowerPoint-Dateien mit Überschriften, Sprache, Titel und Alternativtexten; für ein getaggtes PDF das Word-" +
                "Dokument in Word als PDF speichern (Optionen: „Dokumentstrukturtags für Barrierefreiheit“)."
        )
    }
}
