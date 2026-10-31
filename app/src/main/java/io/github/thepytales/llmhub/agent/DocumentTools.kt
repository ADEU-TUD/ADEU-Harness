package io.github.thepytales.llmhub.agent

import android.content.Context
import android.util.Base64
import io.github.thepytales.llmhub.data.Contact
import io.github.thepytales.llmhub.data.ListStore
import io.github.thepytales.llmhub.data.LogoStore
import io.github.thepytales.llmhub.data.SettingsStore
import io.github.thepytales.llmhub.docs.DocFormat
import io.github.thepytales.llmhub.docs.Contrast
import io.github.thepytales.llmhub.docs.DocStyle
import io.github.thepytales.llmhub.docs.DocTheme
import io.github.thepytales.llmhub.docs.CalendarEvent
import io.github.thepytales.llmhub.docs.CalendarFiles
import io.github.thepytales.llmhub.docs.DocumentSources
import io.github.thepytales.llmhub.docs.DocsStore
import io.github.thepytales.llmhub.docs.LiveDoc
import io.github.thepytales.llmhub.docs.DocumentWriter
import io.github.thepytales.llmhub.docs.Letter
import io.github.thepytales.llmhub.docs.LetterBuilder
import io.github.thepytales.llmhub.docs.MailDraft
import io.github.thepytales.llmhub.docs.MailFiles
import io.github.thepytales.llmhub.docs.Placeholders
import io.github.thepytales.llmhub.docs.PdfForms
import io.github.thepytales.llmhub.docs.PdfText
import io.github.thepytales.llmhub.docs.QtiWriter
import io.github.thepytales.llmhub.docs.TudBrand
import io.github.thepytales.llmhub.docs.describe
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.net.JSON
import io.github.thepytales.llmhub.net.s
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.File

/** Lets the model create documents (PDF, Word, …) that the user can open, share or save. */
class DocumentTools(
    private val context: Context,
    private val settings: SettingsStore,
    private val logos: LogoStore,
    /** Address book: recipients of letters are offered again in the templates. */
    private val contacts: ListStore<Contact>,
    /** Living documents (editor with versions and comments) the Markdown documents are kept in. */
    val docs: DocsStore,
) : ToolProvider {
    val folder = File(context.filesDir, "documents")

    /** Storage location (vault id) for editor documents made in a chat of this project – its working folder, if it has one. */
    var projectVault: (folderId: String?) -> String? = { null }

    override val defs = listOf(
        ToolDef(
            "create_document",
            "Erstellt eine Datei (PDF, Word, HTML, Markdown oder Text). Der Nutzer kann sie direkt in der App öffnen, " +
                "teilen oder speichern. content ist entweder Markdown (Überschriften, Listen, Tabellen, Codeblöcke, " +
                "**fett**, *kursiv*, Links, Hinweisboxen mit „> [!NOTE]“, „> [!TIP]“, „> [!WARNING]“) – dann gestaltet " +
                "die App das Dokument nach style, accent_color und logo – oder für PDF/HTML eine vollständige HTML-Seite " +
                "mit eigenem CSS für freie Gestaltung (Farben, Flächen, Formen, Schriften, Spalten, Inline-SVG).",
            listOf(
                Param("title", "Titel des Dokuments (wird auch als Dateiname verwendet)"),
                Param("content", "Vollständiger Inhalt in Markdown oder als HTML-Seite (<!DOCTYPE html>…)"),
                Param("format", "pdf (Standard), docx, pptx (PowerPoint im TUD-Design), xlsx (Tabellen als Excel), csv, qti (Test für OPAL/ONYX), " +
                    "moodle (Moodle-XML-Test), gift (Moodle-GIFT), anki (Karteikarten), limesurvey (Fragebogen), html, md oder txt", required = false),
                Param("style", "Gestaltung bei Markdown: modern, kraeftig, elegant, klassisch, schlicht oder tud (Corporate Design der TU Dresden)", required = false),
                Param("accent_color", "Akzentfarbe als #RRGGBB oder Name (z.B. blau, grün, bordeaux)", required = false),
                Param("logo", "Name eines gespeicherten Logos, „standard“ für das Standardlogo oder „kein“", required = false),
                Param("file_name", "Optionaler Dateiname ohne Endung", required = false),
                Param("letter_sender", "Nur für Briefe: Absenderblock, Zeilen mit \\n getrennt (Einrichtung, Name, Telefon, E-Mail). title ist dann der Betreff.", required = false),
                Param("letter_recipient", "Nur für Briefe: Anschrift des Empfängers, Zeilen mit \\n getrennt", required = false),
                Param("layout", "Optional „urkunde“ für Teilnahmebescheinigungen, Zertifikate und Urkunden (zentriert, nach TUD-Vorlage)", required = false),
            ),
        ),
        ToolDef(
            "edit_document",
            "Ändert ein bereits erstelltes Dokument (auch einen von der App erstellten Brief), ohne es neu zu schreiben: " +
                "einzelne Textstellen ersetzen (replacements) oder den Inhalt komplett austauschen (content). Es entsteht eine " +
                "neue Version; die alte bleibt erhalten. Auch zum Umwandeln in ein anderes Format (format=docx …).",
            listOf(
                Param("file", "Dateiname des Dokuments (Standard: das zuletzt erstellte)", required = false),
                Param(
                    "replacements", "JSON-Liste [{\"alt\": \"genauer bisheriger Text\", \"neu\": \"neuer Text\"}] – " +
                        "alt muss wörtlich im Dokument vorkommen (Markdown-Quelltext)", required = false,
                ),
                Param("content", "Optional: kompletter neuer Inhalt (Markdown) statt einzelner Ersetzungen", required = false),
                Param("title", "Optional: neuer Titel bzw. Betreff", required = false),
                Param("letter_recipient", "Optional: neue Anschrift (Briefe)", required = false),
                Param("letter_sender", "Optional: neuer Infoblock (Briefe)", required = false),
                Param("format", "Optional: anderes Format (pdf, docx, html, md, txt)", required = false),
                Param("tab", "Optional: Name des Tabs, dessen Inhalt content ersetzt (bei Dokumenten mit mehreren Tabs)", required = false),
            ),
        ),
        ToolDef(
            "create_chart",
            "Zeichnet ein Diagramm aus Daten (z.B. Ergebnissen von run_javascript): bar, stacked, hbar, line, scatter, pie, box " +
                "(Boxplot aus Rohdaten je Gruppe) oder histogram. Es erscheint im Chat; für ein Dokument den zurückgegebenen ```chart-Block " +
                "in den Markdown-Inhalt von create_document übernehmen (PDF/HTML zeichnen ihn, Word und PowerPoint zeigen die Datentabelle).",
            listOf(
                Param(
                    "spec", "JSON-Objekt {\"type\": \"bar\", \"title\": \"…\", \"x_label\": \"…\", \"y_label\": \"…\", \"labels\": [\"A\",\"B\"], " +
                        "\"series\": [{\"name\": \"…\", \"values\": [1,2]}]} – bei scatter je Reihe zusätzlich \"x\": […], bei box/histogram " +
                        "\"values\" = Rohdaten (box: eine Reihe je Gruppe)", type = "object",
                ),
            ),
        ),
        ToolDef(
            "read_document",
            "Liest den aktuellen Stand eines Dokuments aus dem Dokument-Editor (alle Tabs als Markdown) und die offenen " +
                "Kommentare mit ihrer id. Vor Änderungen aufrufen: Der Nutzer kann das Dokument selbst bearbeitet haben.",
            listOf(Param("file", "Dateiname oder Titel des Dokuments (Standard: das zuletzt bearbeitete dieses Chats)", required = false)),
        ),
        ToolDef(
            "reply_to_comment",
            "Antwortet auf einen Kommentar im Dokument-Editor (nach der Änderung mit edit_document): kurz, was du geändert " +
                "hast und warum – oder warum nicht. Standardmäßig wird der Kommentar damit erledigt.",
            listOf(
                Param("comment_id", "id des Kommentars"),
                Param("text", "Antwort, höchstens zwei Sätze"),
                Param("resolve", "false, wenn der Kommentar offen bleiben soll (z.B. bei einer Rückfrage)", required = false, type = "boolean"),
            ),
        ),
        ToolDef(
            "create_email_draft",
            "Legt einen E-Mail-Entwurf an, den der Nutzer in seiner Mail-App öffnet, prüft und selbst sendet (die App sendet " +
                "nichts). Für fertig formulierte Mails an Studierende, Kolleg:innen oder Ämter.",
            listOf(
                Param("to", "Empfängeradresse(n), mit Komma getrennt (leer lassen, wenn unbekannt)", required = false),
                Param("subject", "Betreff"),
                Param("body", "Text der Mail mit Anrede und Gruß, ohne Markdown"),
                Param("cc", "Kopie an (optional)", required = false),
            ),
        ),
        ToolDef(
            "create_calendar_event",
            "Erstellt einen Kalendertermin (.ics), den der Nutzer mit einem Tipp in seinen Kalender übernimmt.",
            listOf(
                Param("title", "Titel des Termins"),
                Param("start", "Beginn, z.B. 2026-11-03 14:00 (nur Datum = ganztägig)"),
                Param("end", "Ende, z.B. 2026-11-03 15:30 (Standard: eine Stunde nach Beginn)", required = false),
                Param("location", "Ort oder Link (optional)", required = false),
                Param("description", "Beschreibung (optional)", required = false),
                Param("reminder_minutes", "Erinnerung so viele Minuten vorher (optional)", required = false, type = "integer"),
            ),
        ),
        ToolDef(
            "create_series",
            "Serienbrief bzw. Serien-Dokumente: aus einer Vorlage mit Platzhaltern {{Spalte}} und einer Tabelle (CSV, Tabulator oder " +
                "Markdown-Tabelle mit Kopfzeile) entsteht je Zeile ein Dokument – z.B. Teilnahmebescheinigungen, Einladungen, Briefe. " +
                "Ergebnis: alle Dateien in einem ZIP, das der Nutzer öffnen oder weitergeben kann.",
            listOf(
                Param("template", "Markdown der Vorlage mit Platzhaltern wie {{Vorname}} {{Nachname}}; auch im Titel möglich"),
                Param("data", "Die Daten: Kopfzeile mit den Spaltennamen, darunter je Zeile ein Datensatz"),
                Param("title", "Titel bzw. Dateiname je Dokument, mit Platzhaltern, z.B. \"Bescheinigung {{Nachname}}\""),
                Param("format", "pdf (Standard) oder docx", required = false),
                Param("style", "Gestaltung, z.B. tud", required = false),
                Param("layout", "urkunde für Teilnahmebescheinigungen und Zertifikate (optional)", required = false),
                Param("letter_recipient", "Nur für Briefe: Anschrift mit Platzhaltern, Zeilen mit \\n getrennt (optional)", required = false),
                Param("letter_sender", "Nur für Briefe: Absenderblock (optional)", required = false),
            ),
        ),
        ToolDef(
            "create_semester_plan",
            "Legt alle Sitzungen einer wöchentlichen Lehrveranstaltung als Kalenderdatei (.ics) an – ohne gesetzliche Feiertage in " +
                "Sachsen (inkl. Reformationstag und Buß- und Bettag) und ohne angegebene vorlesungsfreie Zeiten, mit Thema je Sitzung.",
            listOf(
                Param("title", "Titel der Veranstaltung"),
                Param("weekday", "Wochentag, z.B. Dienstag"),
                Param("time", "Uhrzeit, z.B. 13:00–14:30 oder 4. DS"),
                Param("first_date", "Erster möglicher Termin, z.B. 2026-10-13"),
                Param("last_date", "Letzter möglicher Termin, z.B. 2027-02-02"),
                Param("location", "Ort (optional)", required = false),
                Param("breaks", "Vorlesungsfreie Zeiten, z.B. 2026-12-21..2027-01-03; mehrere mit ; getrennt (optional)", required = false),
                Param("topics", "Themen der Sitzungen in Reihenfolge, je Zeile eines (optional)", required = false),
                Param("reminder_minutes", "Erinnerung so viele Minuten vorher (optional)", required = false, type = "integer"),
            ),
        ),
        ToolDef(
            "read_pdf_form",
            "Liest ein vom Nutzer angehängtes PDF-Formular: die ausfüllbaren Felder mit Typ, erlaubten Werten und " +
                "Beschriftung – oder bei PDFs ohne Felder die Textzeilen mit Position, um Text daneben zu setzen. Vor fill_pdf aufrufen.",
            listOf(Param("file", "Dateiname des angehängten PDFs (Standard: das zuletzt angehängte)", required = false)),
        ),
        ToolDef(
            "fill_pdf",
            "Füllt ein angehängtes PDF aus und speichert es als neue Datei, die der Nutzer öffnen, teilen und speichern kann. " +
                "Formularfelder über fields; bei PDFs ohne Felder Text über texts an Positionen setzen.",
            listOf(
                Param("file", "Dateiname des angehängten PDFs (Standard: das zuletzt angehängte)", required = false),
                Param(
                    "fields", "Formularfelder als JSON-Objekt {\"Feldname\": \"Wert\"}; Ankreuzfelder mit \"ja\"/\"nein\", " +
                        "Options- und Auswahlfelder mit einem der erlaubten Werte", required = false, type = "object",
                ),
                Param(
                    "texts", "Nur für PDFs ohne Felder: JSON-Liste [{\"page\": 1, \"x\": 70, \"y\": 52, \"text\": \"…\", " +
                        "\"size\": 10}], x/y in mm von oben links, y = Grundlinie", required = false,
                ),
                Param("flatten", "true: Felder danach nicht mehr änderbar (Standard false)", required = false, type = "boolean"),
                Param("file_name", "Optionaler Dateiname ohne Endung", required = false),
            ),
        ),
        ToolDef(
            "make_pdf_fillable",
            "Macht ein angehängtes Papierformular (PDF ohne Formularfelder) ausfüllbar: erkennt, wo Eingaben hingehören (Linien " +
                "„____“, „Name:“ mit Platz dahinter, Kästchen ☐), und setzt Text-, Datums- und Ankreuzfelder. Optional eigene " +
                "Felder über fields. Ergebnis: ein neues PDF, das sich am PC ausfüllen lässt; im PDF-Werkzeugkasten kann der " +
                "Nutzer die Felder noch verschieben, in der Größe ändern oder umbenennen.",
            listOf(
                Param("file", "Dateiname des angehängten PDFs (Standard: das zuletzt angehängte)", required = false),
                Param(
                    "fields", "Optional statt der Erkennung: JSON-Liste [{\"name\": \"Name\", \"page\": 1, \"x\": 70, \"y\": 52, " +
                        "\"width\": 80, \"height\": 7, \"type\": \"text|multiline|checkbox|date|signature\"}], Maße in mm von oben links",
                    required = false,
                ),
                Param("file_name", "Optionaler Dateiname ohne Endung", required = false),
            ),
        ),
        ToolDef(
            "check_accessibility",
            "Prüft ein Dokument auf Barrierefreiheit: auswählbarer Text statt Scan, Tags, Sprache, Titel, Überschriften, " +
                "Alternativtexte, Tabellenköpfe, Folientitel, Formularfeld-Beschreibungen. Für angehängte PDFs und alle von " +
                "der App erstellten Dokumente (PDF, Word, PowerPoint, HTML).",
            listOf(Param("file", "Dateiname (Standard: die neueste angehängte PDF bzw. das zuletzt erstellte Dokument)", required = false)),
        ),
        ToolDef(
            "save_logo",
            "Speichert ein Bild aus der letzten Nachricht des Nutzers als Logo für Dokumente.",
            listOf(
                Param("name", "Name des Logos, z.B. Firmenname"),
                Param("image_index", "Nummer des Bildes in der Nachricht (1 = erstes, Standard)", required = false, type = "integer"),
                Param("make_default", "true, um es als Standardlogo zu verwenden", required = false, type = "boolean"),
            ),
        ),
    )

    override suspend fun run(name: String, args: JsonObject, ctx: RepoContext): ToolOutput = when (name) {
        "create_document" -> create(args, ctx = ctx)
        "edit_document" -> edit(args, ctx)
        "read_document" -> readDocument(args, ctx)
        "create_chart" -> createChart(args)
        "create_semester_plan" -> semesterPlan(args)
        "create_series" -> series(args, ctx)
        "reply_to_comment" -> replyToComment(args)
        "create_email_draft" -> emailDraft(args)
        "create_calendar_event" -> calendarEvent(args)
        "save_logo" -> saveLogo(args, ctx)
        "read_pdf_form" -> readPdfForm(args, ctx)
        "fill_pdf" -> fillPdf(args, ctx)
        "make_pdf_fillable" -> makeFillable(args, ctx)
        "check_accessibility" -> checkAccessibility(args, ctx)
        else -> ToolOutput("FEHLER: Unbekanntes Werkzeug '$name'.")
    }

    private fun theme(args: JsonObject): Pair<DocTheme, String?> {
        val s = settings.current
        val style = DocStyle.parse(args.s("style")) ?: DocStyle.parse(s.docStyle) ?: DocStyle.MODERN
        val accent = DocTheme.parseColor(args.s("accent_color")) ?: DocTheme.parseColor(s.docAccent) ?: DocTheme.DEFAULT_ACCENT
        val wanted = args.s("logo")?.trim()
        val noLogo = wanted != null && wanted.lowercase() in setOf("kein", "keins", "none", "nein", "false", "ohne")
        var warning: String? = null
        val logo: ByteArray? = when {
            noLogo -> null
            !wanted.isNullOrEmpty() -> (TudBrand.logo(wanted) ?: logos.find(wanted)?.let { logos.pngBytes(it) }).also {
                if (it == null) warning = "Logo „$wanted“ nicht gefunden – Dokument ohne Logo erstellt."
            }
            // The TU Dresden design always carries its own logo.
            style == DocStyle.TUD -> TudBrand.logoBlue
            s.autoLogo -> logos.default?.let { logos.pngBytes(it) }
            else -> null
        }
        return DocTheme(style, accent, logo, s.docFooter, s.tudUnit.trim()) to warning
    }

    /** Theme from the app settings (used for manual exports). */
    fun settingsTheme(style: DocStyle? = null): DocTheme =
        theme(JsonObject(emptyMap())).first.let { if (style != null) it.copy(style = style) else it }

    private fun author(ctx: RepoContext?) = "KI" + (ctx?.model?.let { " · " + io.github.thepytales.llmhub.data.ModelCatalog.info(it).displayName } ?: "")

    /**
     * Keeps a Markdown document in the editor: a new living document, or a new version of the one in [args] ("doc_id").
     * With [tabsDone] the tabs were already changed by edit_document and only the file is updated.
     */
    private fun keepLive(args: JsonObject, title: String, content: String, file: File, ctx: RepoContext?, tabsDone: Boolean): LiveDoc? {
        if (DocumentWriter.isHtml(content) || file.extension.lowercase() in setOf("zip", "eml", "ics")) return null
        val existing = docs.get(args.s("doc_id"))
        val doc = if (existing != null) {
            if (tabsDone) existing.copy(file = file.absolutePath, title = title.ifBlank { existing.title })
            else existing.change(author(ctx), "Von der KI überarbeitet") { d ->
                d.copy(title = title.ifBlank { d.title }, tabs = if (d.tabs.size == 1) listOf(d.tabs[0].copy(markdown = content)) else d.tabs, file = file.absolutePath)
            }
        } else {
            val kind = io.github.thepytales.llmhub.docs.DocKind.forFile(file.name)
            val created = LiveDoc.create(title, content, author(ctx), ctx?.chatId, args.s("style"), kind).copy(file = file.absolutePath, vault = projectVault(ctx?.folderId) ?: docs.defaultVault)
            if (kind != io.github.thepytales.llmhub.docs.DocKind.SHEET) created
            else {
                // Tables: one tab per table, as in the Excel file.
                val sheets = io.github.thepytales.llmhub.docs.XlsxWriter.sheets(title, content)
                val tabs = sheets.map { sh -> io.github.thepytales.llmhub.docs.DocTab(title = sh.name, markdown = io.github.thepytales.llmhub.docs.SheetEngine.markdown(sh.rows)) }
                created.copy(tabs = tabs, versions = listOf(created.versions.first().copy(tabs = tabs)))
            }
        }
        docs.save(doc)
        return doc
    }

    private suspend fun create(args: JsonObject, overwrite: Boolean = false, checks: Boolean = true, ctx: RepoContext? = null, tabsDone: Boolean = false): ToolOutput {
        val title = args.s("title")?.trim().orEmpty()
        var content = args.req("content")
        val format = DocFormat.parse(args.s("format"))
        val (theme, warning) = theme(args)
        if (DocumentWriter.isHtml(content)) content = insertLogos(content, theme)
        val sender = args.s("letter_sender")?.replace("\\n", "\n")?.trim().orEmpty()
        val recipient = args.s("letter_recipient")?.replace("\\n", "\n")?.trim().orEmpty()
        val letter = if (sender.isNotEmpty() || recipient.isNotEmpty()) Letter(sender, recipient) else null
        val certificate = letter == null && args.s("layout")?.trim()?.lowercase() in CERTIFICATE_LAYOUTS
        // Pictures of a document of the editor are relative to its folder.
        val imageBase = docs.get(args.s("doc_id"))?.let { docs.baseDir(it) }
        val doc = DocumentWriter.create(context, folder, title, content, format, args.s("file_name"), theme, letter, certificate, overwrite, imageBase)
        val live = withContext(Dispatchers.IO) { keepLive(args, title, content, doc.file, ctx, tabsDone) }
        // The source stays next to the document for edit_document and for filling in open placeholders.
        withContext(Dispatchers.IO) {
            val extra = mutableMapOf<String, kotlinx.serialization.json.JsonElement>("file_name" to JsonPrimitive(doc.file.nameWithoutExtension))
            if (live != null) extra["doc_id"] = JsonPrimitive(live.id)
            DocumentSources.save(doc.file, JsonObject(args + extra))
        }
        if (recipient.isNotEmpty()) rememberContact(recipient)
        val size = withContext(Dispatchers.IO) { (doc.file.length() + 1023) / 1024 }
        val pages = doc.pages?.let { ", $it ${if (it == 1) "Seite" else "Seiten"}" }
            ?: if (format == DocFormat.QTI) ", " + QtiWriter.summary(QtiWriter.parse(title, content)) else ""
        val readable = Contrast.readable(theme.accent)
        val notes = listOfNotNull(
            warning,
            if (theme.style != DocStyle.TUD && readable != theme.accent) {
                "Hinweis: Die Akzentfarbe ${theme.accent} wäre als Schrift auf Weiß zu hell (Kontrast unter 4,5:1) und wurde " +
                    "für die Barrierefreiheit auf $readable abgedunkelt. Erwähne das kurz."
            } else null,
            if (!doc.designed) {
                "Hinweis: Das gestaltete PDF ließ sich gerade nicht rendern, deshalb wurde ein einfaches PDF mit dem " +
                    "gleichen Text erstellt. Versuche es nicht erneut; sag dem Nutzer, dass das Layout vereinfacht ist, " +
                    "und biete bei Bedarf dieselbe Gestaltung zusätzlich als format=html an."
            } else null,
        )
        val gaps = Placeholders.find(DocumentSources.texts(args))
        val review = buildList {
            if (gaps.isNotEmpty()) {
                add(
                    "Offene Platzhalter: ${gaps.joinToString(", ") { "[$it]" }}. Nenne sie dem Nutzer kurz – er kann sie in der " +
                        "App über „Ausfüllen“ am Dokument eintragen oder dir die Angaben schreiben (dann edit_document)."
                )
            }
            if (checks) {
                add(
                    "Selbstprüfung vor deiner Antwort: Enthält das Dokument alles, worum der Nutzer gebeten hat, und nichts " +
                        "Erfundenes? Stimmen Namen, Daten und Zahlen mit seinen Angaben überein? Falls nicht, korrigiere es mit " +
                        "edit_document, sonst antworte kurz (ohne den Inhalt zu wiederholen)."
                )
            }
        }
        return ToolOutput(
            "Dokument erstellt: ${doc.file.name} (${format.label}$pages, $size KB). " +
                "Es wird dem Nutzer im Chat mit Knöpfen zum Öffnen, Teilen und Speichern angezeigt – nenne keinen Dateipfad." +
                (if (live != null) " Der Nutzer kann es im Dokument-Editor direkt bearbeiten und kommentieren." else "") +
                (notes + review).joinToString("") { "\n$it" },
            file = doc.file.absolutePath,
        )
    }

    private fun rememberContact(address: String) {
        val lines = LetterBuilder.addressLines(address)
        if (lines.isEmpty() || Placeholders.find(address).size >= lines.size) return
        val text = lines.joinToString("\n")
        val existing = contacts.current.firstOrNull { it.label.equals(lines.first(), ignoreCase = true) }
        contacts.upsert(existing?.copy(address = text, lastUsed = System.currentTimeMillis()) ?: Contact(address = text))
        // The address book keeps the 60 addresses used last.
        val keep = contacts.current.sortedByDescending { it.lastUsed }.take(60).map { it.id }.toSet()
        if (keep.size < contacts.current.size) contacts.replaceAll(contacts.current.filter { it.id in keep })
    }

    /**
     * Builds the document of a template marked as direct (the letter) from its fields, without the model.
     * Returns the arguments (shown in the chat as create_document call, so the model can change it later) and the result.
     */
    suspend fun createDirect(kind: String, values: Map<String, String>, ctx: RepoContext? = null): Pair<JsonObject, ToolOutput> {
        require(kind == "letter") { "Unbekannte Vorlage: $kind" }
        fun v(vararg keys: String) = keys.firstNotNullOfOrNull { k -> values[k]?.trim()?.takeIf { it.isNotEmpty() } }.orEmpty()
        val built = LetterBuilder.build(
            LetterBuilder.Input(
                sender = v("Absender").ifEmpty { settings.current.senderInfo },
                recipient = v("Empfänger"),
                subject = v("Betreff"),
                reference = v("Bezug"),
                salutation = v("Anrede"),
                body = v("Brieftext", "Inhalt"),
            )
        )
        val args = buildJsonObject {
            put("title", built.title)
            put("content", built.content)
            put("format", "pdf")
            put("style", "tud")
            put("letter_sender", built.letter.sender)
            put("letter_recipient", built.letter.recipient)
            if (built.title.isNotEmpty()) put("file_name", "Brief – " + built.title.take(60))
        }
        return args to create(args, checks = false, ctx = ctx)
    }

    internal fun newFile(base: String, ext: String): File {
        folder.mkdirs()
        val name = DocumentWriter.safeName(base)
        var f = File(folder, "$name.$ext")
        var n = 2
        while (f.exists()) f = File(folder, "$name ($n).$ext").also { n++ }
        return f
    }

    private suspend fun emailDraft(args: JsonObject): ToolOutput {
        val draft = MailDraft(args.s("to").orEmpty(), args.s("cc").orEmpty(), args.req("subject"), args.req("body"))
        val file = newFile("E-Mail – " + draft.subject.take(60), "eml")
        withContext(Dispatchers.IO) { MailFiles.write(file, draft) }
        return ToolOutput(
            "E-Mail-Entwurf erstellt (${file.name}). Der Nutzer öffnet ihn mit einem Tipp in seiner Mail-App und sendet selbst – " +
                "sag ihm das kurz, ohne den Text zu wiederholen.",
            file = file.absolutePath,
        )
    }

    private suspend fun calendarEvent(args: JsonObject): ToolOutput {
        val (start, allDay) = CalendarFiles.parse(args.req("start"))
            ?: return ToolOutput("FEHLER: Beginn „${args.s("start")}“ nicht verstanden. Format: 2026-11-03 14:00 oder 2026-11-03.")
        val end = args.s("end")?.let { CalendarFiles.parse(it)?.first } ?: if (allDay) start else start.plusHours(1)
        if (end.isBefore(start)) return ToolOutput("FEHLER: Das Ende liegt vor dem Beginn.")
        val event = CalendarEvent(
            args.req("title"), start, end, allDay, args.s("location").orEmpty(), args.s("description").orEmpty(), args.int("reminder_minutes"),
        )
        val file = newFile("Termin – " + event.title.take(60), "ics")
        withContext(Dispatchers.IO) { CalendarFiles.write(file, event) }
        val day = java.time.format.DateTimeFormatter.ofPattern(if (allDay) "EEEE, d. MMMM yyyy" else "EEEE, d. MMMM yyyy, HH:mm 'Uhr'", java.util.Locale.GERMANY)
        return ToolOutput(
            "Termin erstellt: ${event.title}, ${start.format(day)}${if (!allDay) " bis " + end.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) + " Uhr" else ""}. " +
                "Der Nutzer übernimmt ihn mit einem Tipp in seinen Kalender.",
            file = file.absolutePath,
        )
    }

    /** The newest document with a stored source, or the one named [name]. */
    private fun sourceFile(name: String?): File {
        val docs = folder.listFiles().orEmpty().filter { it.isFile && DocumentSources.load(it) != null }
        val wanted = name?.trim()?.takeIf { it.isNotEmpty() }
        return (
            if (wanted == null) docs.maxByOrNull { it.lastModified() }
            else docs.firstOrNull { it.name.equals(wanted, ignoreCase = true) }
                ?: docs.filter { it.name.contains(wanted.substringBeforeLast('.'), ignoreCase = true) }.maxByOrNull { it.lastModified() }
            ) ?: throw IllegalArgumentException(
            if (docs.isEmpty()) "Es gibt noch kein Dokument, das sich ändern lässt – erstelle es mit create_document."
            else "Dokument „$wanted“ nicht gefunden. Vorhanden: ${docs.sortedByDescending { it.lastModified() }.take(8).joinToString(", ") { it.name }}."
        )
    }

    /** A living document by file name, title or id; without a name the newest one of the chat. */
    private fun findLive(name: String?, ctx: RepoContext?): LiveDoc? {
        val wanted = name?.trim()?.takeIf { it.isNotEmpty() }
        val all = docs.current
        if (wanted == null) return all.filter { ctx?.chatId != null && it.chatId == ctx.chatId }.maxByOrNull { it.updatedAt }
        return all.firstOrNull { it.id == wanted }
            ?: all.firstOrNull { it.file?.let { f -> File(f).name.equals(wanted, true) } == true }
            ?: all.firstOrNull { it.title.equals(wanted.substringBeforeLast('.'), true) }
            ?: all.filter { it.title.contains(wanted.substringBeforeLast('.'), true) }.maxByOrNull { it.updatedAt }
    }

    /** Applies the replacements and new content of edit_document to the tabs of [doc]. */
    private fun editTabs(doc: LiveDoc, args: JsonObject, missing: MutableList<String>): List<io.github.thepytales.llmhub.docs.DocTab> {
        var tabs = doc.tabs
        (jsonArg(args["replacements"]) as? JsonArray).orEmpty().forEach { e ->
            val o = e as? JsonObject ?: return@forEach
            val old = o.s("alt") ?: o.s("old") ?: return@forEach
            val new = o.s("neu") ?: o.s("new") ?: ""
            if (tabs.none { old in it.markdown }) missing += old
            else tabs = tabs.map { t -> t.copy(markdown = t.markdown.replace(old, new)) }
        }
        args.s("content")?.takeIf { it.isNotBlank() }?.let { content ->
            val wanted = args.s("tab")?.trim()
            val index = tabs.indexOfFirst { wanted != null && it.title.equals(wanted, true) }.takeIf { it >= 0 } ?: 0
            tabs = if (tabs.size == 1 || wanted != null) tabs.mapIndexed { i, t -> if (i == index) t.copy(markdown = content) else t }
            else listOf(tabs[0].copy(markdown = content))
        }
        return tabs
    }

    private suspend fun edit(args: JsonObject, ctx: RepoContext? = null): ToolOutput {
        val file = runCatching { sourceFile(args.s("file")) }.getOrElse { e ->
            // A document that so far only lives in the editor (no file yet).
            val live = findLive(args.s("file"), ctx) ?: throw e
            val missing = mutableListOf<String>()
            val tabs = editTabs(live, args, missing)
            if (tabs == live.tabs && args.s("title").isNullOrBlank()) {
                return ToolOutput("FEHLER: Nichts geändert" + if (missing.isNotEmpty()) " – nicht gefunden: ${missing.joinToString(" | ") { "„${it.take(80)}“" }}" else ".")
            }
            docs.save(live.change(author(ctx), "Von der KI überarbeitet") { it.copy(tabs = tabs, title = args.s("title")?.takeIf { t -> t.isNotBlank() } ?: it.title) })
            val format = args.s("format")?.takeIf { it.isNotBlank() }
            if (format == null) {
                return ToolOutput("Dokument „${live.title}“ im Editor geändert (neue Version)." +
                    if (missing.isNotEmpty()) " Nicht gefunden: ${missing.joinToString(" | ") { "„${it.take(80)}“" }}" else "")
            }
            val updated = docs.get(live.id)!!
            return create(JsonObject(mapOf("title" to JsonPrimitive(updated.title), "content" to JsonPrimitive(updated.markdown()), "format" to JsonPrimitive(format),
                "doc_id" to JsonPrimitive(updated.id))), ctx = ctx, tabsDone = true)
        }
        var source = DocumentSources.load(file)!!
        val live = docs.get(source.s("doc_id"))
        if (live != null) {
            // The editor holds the current text (the user may have changed it there).
            val missing = mutableListOf<String>()
            val tabs = editTabs(live, args, missing)
            val replacements = (jsonArg(args["replacements"]) as? JsonArray)?.size ?: 0
            if (tabs == live.tabs && missing.size == replacements && replacements > 0 && listOf("title", "letter_recipient", "letter_sender", "format").all { args.s(it).isNullOrBlank() }) {
                return ToolOutput("FEHLER: Diese Textstellen kommen im Dokument nicht wörtlich vor: ${missing.joinToString(" | ") { "„${it.take(80)}“" }}. Lies den aktuellen Stand mit read_document. Nichts geändert.")
            }
            val changed = live.change(author(ctx), "Von der KI überarbeitet") { it.copy(tabs = tabs) }
            docs.save(changed)
            val others = listOf("title", "letter_recipient", "letter_sender", "format").mapNotNull { k ->
                args.s(k)?.takeIf { it.isNotBlank() }?.let { k to JsonPrimitive(if (k.startsWith("letter_")) it.replace("\\n", "\n") else it) }
            }
            source = JsonObject(source + others + ("content" to JsonPrimitive(changed.markdown())))
            val out = create(source, ctx = ctx, tabsDone = true)
            return if (missing.isEmpty()) out else out.copy(text = out.text + "\nNicht gefunden und daher nicht geändert: ${missing.joinToString(" | ") { "„${it.take(80)}“" }}")
        }
        val missing = mutableListOf<String>()
        (jsonArg(args["replacements"]) as? JsonArray).orEmpty().forEach { e ->
            val o = e as? JsonObject ?: return@forEach
            val old = o.s("alt") ?: o.s("old") ?: return@forEach
            val new = o.s("neu") ?: o.s("new") ?: ""
            if (DocumentSources.texts(source).none { old in it }) missing += old
            else source = DocumentSources.mapTexts(source) { it.replace(old, new) }
        }
        val changes = listOf("content", "title", "letter_recipient", "letter_sender", "format").mapNotNull { k ->
            args.s(k)?.takeIf { it.isNotBlank() }?.let { k to JsonPrimitive(if (k.startsWith("letter_")) it.replace("\\n", "\n") else it) }
        }
        source = JsonObject(source + changes)
        if (missing.isNotEmpty() && changes.isEmpty() && missing.size == (jsonArg(args["replacements"]) as? JsonArray)?.size) {
            return ToolOutput("FEHLER: Diese Textstellen kommen im Dokument nicht wörtlich vor: ${missing.joinToString(" | ") { "„${it.take(80)}“" }}. Nichts geändert.")
        }
        val out = create(source, ctx = ctx)
        return if (missing.isEmpty()) out
        else out.copy(text = out.text + "\nNicht gefunden und daher nicht geändert: ${missing.joinToString(" | ") { "„${it.take(80)}“" }}")
    }

    private suspend fun series(args: JsonObject, ctx: RepoContext): ToolOutput {
        val rows = io.github.thepytales.llmhub.docs.SeriesData.parse(args.req("data"))
        if (rows.size < 2) return ToolOutput("FEHLER: Keine Daten erkannt – erste Zeile Spaltennamen, darunter die Datensätze.")
        if (rows.size > 501) return ToolOutput("FEHLER: Höchstens 500 Datensätze auf einmal.")
        val head = rows.first()
        val template = args.req("template")
        val missing = io.github.thepytales.llmhub.data.TemplateFields.of(template + args.s("title").orEmpty() + args.s("letter_recipient").orEmpty())
            .map { it.label }.filter { f -> head.none { it.equals(f, ignoreCase = true) } }
        if (missing.isNotEmpty()) return ToolOutput("FEHLER: Diese Platzhalter fehlen in den Spalten (${head.joinToString()}): ${missing.joinToString()}.")
        val dir = File(context.cacheDir, "series/" + System.currentTimeMillis()).apply { mkdirs() }
        val made = mutableListOf<File>()
        for (row in rows.drop(1)) {
            val values = head.indices.associate { i -> head[i] to row.getOrElse(i) { "" } }
            fun fill(t: String) = io.github.thepytales.llmhub.docs.SeriesData.fill(t, values)
            val one = buildMap<String, kotlinx.serialization.json.JsonElement> {
                put("title", JsonPrimitive(fill(args.req("title"))))
                put("content", JsonPrimitive(fill(template)))
                put("format", JsonPrimitive(args.s("format") ?: "pdf"))
                args.s("style")?.let { put("style", JsonPrimitive(it)) }
                args.s("layout")?.let { put("layout", JsonPrimitive(it)) }
                args.s("letter_recipient")?.let { put("letter_recipient", JsonPrimitive(fill(it))) }
                args.s("letter_sender")?.let { put("letter_sender", JsonPrimitive(fill(it))) }
            }
            val a = JsonObject(one)
            val (theme, _) = theme(a)
            val sender = a.s("letter_sender")?.replace("\\n", "\n")?.trim().orEmpty()
            val recipient = a.s("letter_recipient")?.replace("\\n", "\n")?.trim().orEmpty()
            val letter = if (sender.isNotEmpty() || recipient.isNotEmpty()) io.github.thepytales.llmhub.docs.Letter(sender, recipient) else null
            val certificate = letter == null && a.s("layout")?.trim()?.lowercase() in CERTIFICATE_LAYOUTS
            made += DocumentWriter.create(context, dir, a.req("title"), a.req("content"), DocFormat.parse(a.s("format")), null, theme, letter, certificate).file
        }
        val zip = newFile(DocumentWriter.safeName(fill(args.req("title"), emptyMap()).ifBlank { "Serie" }).take(50) + " (${made.size} Dokumente)", "zip")
        withContext(Dispatchers.IO) {
            java.util.zip.ZipOutputStream(zip.outputStream()).use { z ->
                made.forEach { f -> z.putNextEntry(java.util.zip.ZipEntry(f.name)); f.inputStream().use { it.copyTo(z) }; z.closeEntry() }
            }
            dir.deleteRecursively()
        }
        return ToolOutput(
            "${made.size} Dokumente erstellt und als ZIP zusammengefasst (${made.take(5).joinToString { it.name }}${if (made.size > 5) " …" else ""}). " +
                "Der Nutzer öffnet oder teilt das ZIP mit einem Tipp.",
            file = zip.absolutePath,
        )
    }

    private fun fill(t: String, values: Map<String, String>) = io.github.thepytales.llmhub.docs.SeriesData.fill(t, values)

    private suspend fun semesterPlan(args: JsonObject): ToolOutput {
        val plan = io.github.thepytales.llmhub.docs.SemesterPlan
        val day = plan.weekday(args.req("weekday")) ?: return ToolOutput("FEHLER: Wochentag „${args.s("weekday")}“ nicht verstanden.")
        val (from, to) = plan.time(args.req("time")) ?: return ToolOutput("FEHLER: Uhrzeit „${args.s("time")}“ nicht verstanden (z.B. 13:00–14:30 oder 4. DS).")
        val first = CalendarFiles.parse(args.req("first_date"))?.first?.toLocalDate() ?: return ToolOutput("FEHLER: first_date nicht verstanden.")
        val last = CalendarFiles.parse(args.req("last_date"))?.first?.toLocalDate() ?: return ToolOutput("FEHLER: last_date nicht verstanden.")
        if (last.isBefore(first) || java.time.temporal.ChronoUnit.DAYS.between(first, last) > 370) return ToolOutput("FEHLER: Zeitraum ungültig (höchstens ein Jahr).")
        val topics = args.s("topics").orEmpty().lines().map { it.trim().replace(Regex("^\\d+[.)]\\s*|^[-*]\\s*"), "") }.filter { it.isNotEmpty() }
        // ".." or "–" between two dates is a range; ranges() reads two dates within one part.
        val breaks = plan.ranges(args.s("breaks").orEmpty().replace("..", " bis "))
        val (sessions2, skipped2) = plan.plan(day, first, last, breaks, topics)
        if (sessions2.isEmpty()) return ToolOutput("FEHLER: In diesem Zeitraum gibt es keinen freien Termin.")
        val title = args.req("title")
        val events = sessions2.map { s ->
            CalendarEvent(
                title + if (s.topic.isNotBlank()) ": ${s.topic}" else " (${s.number}. Sitzung)",
                s.date.atTime(from), s.date.atTime(to), false, args.s("location").orEmpty(),
                "${s.number}. Sitzung" + if (s.topic.isNotBlank()) " – ${s.topic}" else "", args.int("reminder_minutes"),
            )
        }
        val file = newFile("Termine – " + title.take(60), "ics")
        withContext(Dispatchers.IO) { CalendarFiles.writeAll(file, events) }
        val fmt = java.time.format.DateTimeFormatter.ofPattern("EE dd.MM.yyyy", java.util.Locale.GERMANY)
        return ToolOutput(
            buildString {
                append("${sessions2.size} Termine erstellt (${from}–${to} Uhr). Der Nutzer übernimmt alle mit einem Tipp in seinen Kalender.\n")
                sessions2.forEach { s -> append("- ${s.number}. ${s.date.format(fmt)}${if (s.topic.isNotBlank()) ": ${s.topic}" else ""}\n") }
                if (skipped2.isNotEmpty()) append("Ausgelassen: " + skipped2.joinToString(", ") { "${it.date.format(fmt)} (${it.reason})" } + "\n")
                if (topics.size > sessions2.size) append("Achtung: ${topics.size - sessions2.size} Themen haben keinen Termin mehr.\n")
            },
            file = file.absolutePath,
        )
    }

    private suspend fun createChart(args: JsonObject): ToolOutput {
        val raw = jsonArg(args["spec"])?.toString() ?: return ToolOutput("FEHLER: spec fehlt.")
        val spec = io.github.thepytales.llmhub.docs.ChartSpec.parse(raw)
            ?: return ToolOutput("FEHLER: Diagramm nicht verstanden. Erwartet: {\"type\": \"bar\", \"labels\": [...], \"series\": [{\"name\": \"…\", \"values\": [...]}]}")
        val file = newFile("Diagramm – " + spec.title.ifBlank { "ohne Titel" }.take(50), "svg")
        withContext(Dispatchers.IO) { file.writeText(io.github.thepytales.llmhub.docs.ChartLayout.svg(spec)) }
        return ToolOutput(
            "Diagramm erstellt und im Chat angezeigt (auch als SVG-Datei). Für ein Dokument diesen Block unverändert in den Markdown-Inhalt übernehmen:\n" +
                "```chart\n$raw\n```\nDatentabelle (für Word/PowerPoint und Barrierefreiheit):\n" + spec.table(),
            file = file.absolutePath,
        )
    }

    private fun readDocument(args: JsonObject, ctx: RepoContext): ToolOutput {
        val doc = findLive(args.s("file"), ctx) ?: return ToolOutput(
            "FEHLER: Kein Dokument im Editor gefunden. Vorhanden: ${docs.current.take(8).joinToString(", ") { "„${it.title}“" }.ifEmpty { "keine" }}."
        )
        return ToolOutput(buildString {
            val kind = doc.docKind
            append("${kind.label} „${doc.title}“ (id ${doc.id}, zuletzt geändert von ${doc.lastAuthor}, ${doc.tabs.size} Tab(s), ${doc.words} Wörter")
            doc.file?.let { append(", Datei ${File(it).name}") }
            append(")\n")
            when (kind) {
                io.github.thepytales.llmhub.docs.DocKind.SLIDES -> append("Folien durch „---“ getrennt; „Notizen:“ = Sprechernotizen, „|||“ = rechte Spalte.\n")
                io.github.thepytales.llmhub.docs.DocKind.SHEET -> append("Jeder Tab ist ein Tabellenblatt; Zeile 1 = Spaltenköpfe, Spalten A, B, C …; Formeln beginnen mit =.\n")
                else -> {}
            }
            doc.tabs.forEach { t ->
                if (doc.tabs.size > 1) append("\n=== Tab „${t.title}“ ===\n")
                append(t.markdown.replace("\u200B", "").take(40_000)).append("\n")
                if (kind == io.github.thepytales.llmhub.docs.DocKind.SHEET && t.markdown.contains("| =")) {
                    // With the other sheets, so references like 'Tabelle 2'!A1 are computed too.
                    val computed = io.github.thepytales.llmhub.docs.SheetTools.workbook(doc.tabs).sheet(doc.tabs.indexOf(t)).computed()
                    append("Berechnete Werte:\n").append(io.github.thepytales.llmhub.docs.SheetEngine.markdown(computed).take(20_000)).append("\n")
                }
            }
            val open = doc.comments.filter { !it.resolved }
            if (open.isNotEmpty()) {
                append("\nOffene Kommentare:\n")
                open.forEach { c -> append("- [${c.id}] zu „${c.quote.take(120)}“: ${c.text}").append(if (c.replies.isNotEmpty()) " (${c.replies.size} Antworten)" else "").append("\n") }
            }
        })
    }

    private fun replyToComment(args: JsonObject): ToolOutput {
        val id = args.req("comment_id").trim().removePrefix("[").removeSuffix("]")
        val doc = docs.current.firstOrNull { d -> d.comments.any { it.id == id } } ?: return ToolOutput("FEHLER: Kommentar $id nicht gefunden.")
        val resolve = args.s("resolve")?.lowercase() != "false"
        docs.save(doc.copy(comments = doc.comments.map { c ->
            if (c.id == id) c.copy(replies = c.replies + io.github.thepytales.llmhub.docs.DocReply("KI", args.req("text"), ai = true), resolved = resolve) else c
        }))
        return ToolOutput("Antwort im Kommentar gespeichert" + if (resolve) " (erledigt)." else ".")
    }

    /** Fills in open placeholders of [file] with the user's values and renders it again in place. */
    suspend fun fillPlaceholders(file: File, values: Map<String, String>): File {
        val origin = origin(file)
        val source = DocumentSources.load(origin) ?: throw IllegalStateException("Zu diesem Dokument ist keine Quelle gespeichert.")
        val filled = DocumentSources.mapTexts(source) { Placeholders.fill(it, values) }
        val format = DocFormat.ofFile(origin).ext
        val out = create(JsonObject(filled + mapOf("file_name" to JsonPrimitive(origin.nameWithoutExtension), "format" to JsonPrimitive(format))), overwrite = true, checks = false)
        val made = File(out.file!!)
        // The copy in a project folder gets the filled-in version too.
        if (made.absoluteFile != file.absoluteFile) withContext(Dispatchers.IO) { made.copyTo(file, overwrite = true) }
        return file
    }

    /** Open placeholders of a document created with a stored source. */
    fun openPlaceholders(file: File): List<String> = DocumentSources.load(origin(file))?.let { Placeholders.find(DocumentSources.texts(it)) }.orEmpty()

    /** The app's own file behind a copy in a project folder (same name, with stored source); else [file] itself. */
    private fun origin(file: File): File {
        if (DocumentSources.load(file) != null) return file
        return File(folder, file.name).takeIf { it.isFile && DocumentSources.load(it) != null } ?: file
    }

    /** Replaces src="logo" / src="logo:Name" in HTML with the image data. */
    private fun insertLogos(html: String, theme: DocTheme): String = LOGO_SRC.replace(html) { m ->
        val name = m.groupValues[2].ifEmpty { null }
        val builtIn = TudBrand.logo(name)?.let { "data:image/png;base64," + Base64.encodeToString(it, Base64.NO_WRAP) }
        val logo = if (builtIn == null) logos.find(name) else null
        val url = builtIn ?: logo?.let { logos.dataUrl(it) } ?: theme.logoDataUrl
        if (url != null) "src=${m.groupValues[1]}$url${m.groupValues[1]}" else m.value
    }

    private suspend fun saveLogo(args: JsonObject, ctx: RepoContext): ToolOutput {
        if (ctx.images.isEmpty()) return ToolOutput("FEHLER: In der letzten Nachricht des Nutzers ist kein Bild. Bitte ihn, das Logo als Bild anzuhängen.")
        val index = ((args.int("image_index") ?: 1) - 1).coerceIn(0, ctx.images.lastIndex)
        val bytes = Base64.decode(ctx.images[index].substringAfter(","), Base64.DEFAULT)
        val logo = withContext(Dispatchers.IO) { logos.add(args.req("name"), bytes) }
        val makeDefault = args.s("make_default")?.lowercase() == "true"
        if (makeDefault) logos.setDefault(logo.id)
        val isDefault = makeDefault || logos.default?.id == logo.id
        return ToolOutput(
            "Logo „${logo.name}“ gespeichert" + (if (isDefault) " (Standardlogo)." else ".") +
                " Es kann mit logo=\"${logo.name}\" in create_document verwendet werden. Hinweis: Das Bild wurde als JPEG " +
                "übertragen; für transparente Logos kann der Nutzer es unter Einstellungen › Dokumente & Logos direkt hinzufügen."
        )
    }

    /** The attached PDF named in [args] (or the newest one) whose copy still exists. */
    private fun pdf(args: JsonObject, ctx: RepoContext): Pair<FileAttachment, File> {
        val wanted = args.s("file")?.trim()?.takeIf { it.isNotEmpty() }
        val att = if (wanted == null) ctx.pdfs.firstOrNull()
        else ctx.pdfs.firstOrNull { it.name.equals(wanted, ignoreCase = true) } ?: ctx.pdfs.firstOrNull { it.name.contains(wanted, ignoreCase = true) }
        att ?: throw IllegalArgumentException(
            if (ctx.pdfs.isEmpty()) "In diesem Chat ist kein PDF angehängt. Bitte den Nutzer, das Formular als PDF anzuhängen."
            else "PDF „$wanted“ nicht gefunden. Angehängt: ${ctx.pdfs.joinToString(", ") { it.name }}."
        )
        val file = File(att.path!!)
        if (!file.exists()) throw IllegalStateException("Die Kopie von „${att.name}“ ist nicht mehr vorhanden – bitte das PDF erneut anhängen.")
        return att to file
    }

    private suspend fun readPdfForm(args: JsonObject, ctx: RepoContext): ToolOutput {
        val (att, file) = pdf(args, ctx)
        val info = withContext(Dispatchers.IO) { PdfForms.inspect(context, file) }
        return ToolOutput(info.describe(att.name))
    }

    /** Formulare ausfüllbar machen (AP-11): detected (or given) fields are written into a copy of the PDF. */
    private suspend fun makeFillable(args: JsonObject, ctx: RepoContext): ToolOutput {
        val (att, file) = pdf(args, ctx)
        val given = (jsonArg(args["fields"]) as? JsonArray).orEmpty()
        val (out, fields) = withContext(Dispatchers.IO) {
            val info = io.github.thepytales.llmhub.docs.PdfTools.info(context, file)
            val existing = io.github.thepytales.llmhub.docs.PdfTools.formFields(context, file)
            val specs = if (given.isNotEmpty()) given.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val page = ((o.s("page")?.toDoubleOrNull()?.toInt() ?: 1) - 1).coerceIn(0, info.pages - 1)
                val (pw, ph) = info.sizes[page]
                val mm = 72f / 25.4f
                fun num(k: String) = o.s(k)?.toFloatOrNull()
                val x = num("x") ?: return@mapNotNull null
                val y = num("y") ?: return@mapNotNull null
                val w = num("width") ?: 60f
                val h = num("height") ?: 7f
                val type = when (o.s("type")?.lowercase()) {
                    "checkbox", "ankreuzfeld" -> io.github.thepytales.llmhub.docs.PdfFieldType.CHECKBOX
                    "multiline", "mehrzeilig" -> io.github.thepytales.llmhub.docs.PdfFieldType.MULTILINE
                    "date", "datum" -> io.github.thepytales.llmhub.docs.PdfFieldType.DATE
                    "signature", "unterschrift" -> io.github.thepytales.llmhub.docs.PdfFieldType.SIGNATURE
                    else -> io.github.thepytales.llmhub.docs.PdfFieldType.TEXT
                }
                io.github.thepytales.llmhub.docs.PdfFieldSpec(
                    o.s("name") ?: "Feld", page,
                    io.github.thepytales.llmhub.docs.PdfBox(x * mm / pw, y * mm / ph, w * mm / pw, h * mm / ph), type, o.s("name").orEmpty(),
                )
            } else io.github.thepytales.llmhub.docs.PdfTools.detectFields(context, file)
            val base = DocumentWriter.safeName(args.s("file_name")?.substringBeforeLast('.')?.ifBlank { null } ?: (att.name.substringBeforeLast('.') + " (ausfüllbar)"))
            folder.mkdirs()
            var f = File(folder, "$base.pdf")
            var n = 2
            while (f.exists()) f = File(folder, "$base ($n).pdf").also { n++ }
            if (specs.isNotEmpty()) io.github.thepytales.llmhub.docs.PdfTools.setFormFields(context, file, existing + specs, f)
            f to specs
        }
        if (fields.isEmpty()) {
            return ToolOutput(
                "FEHLER: Keine Eingabestellen erkannt (keine Linien „____“, kein „Name:“ mit Platz, keine Kästchen). Lies das Formular mit " +
                    "read_pdf_form und gib die Felder über fields an (Maße in mm von oben links).",
            )
        }
        return ToolOutput(
            buildString {
                append("PDF ausfüllbar gemacht: ${out.name} mit ${fields.size} Feldern (")
                append(fields.groupBy { it.type.label }.entries.joinToString { "${it.value.size} × ${it.key}" }).append("). ")
                append("Felder: ").append(fields.take(30).joinToString { "„${it.name}“ (S. ${it.page + 1})" }).append(". ")
                append("Es wird dem Nutzer im Chat mit Knöpfen angezeigt – nenne keinen Dateipfad. Weise darauf hin, dass sich die Felder im ")
                append("PDF-Werkzeugkasten (Formular) verschieben, in der Größe ändern und umbenennen lassen.")
            },
            file = out.absolutePath,
        )
    }

    private suspend fun fillPdf(args: JsonObject, ctx: RepoContext): ToolOutput {
        val (att, file) = pdf(args, ctx)
        val values = jsonArg(args["fields"])?.let { it as? JsonObject }.orEmpty().mapValues { (_, v) ->
            (v as? JsonPrimitive)?.contentOrNull ?: v.toString()
        }
        val texts = (jsonArg(args["texts"]) as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val text = o.s("text") ?: return@mapNotNull null
            PdfText(
                page = o.s("page")?.toDoubleOrNull()?.toInt() ?: 1,
                xMm = o.s("x")?.toFloatOrNull() ?: return@mapNotNull null,
                yMm = o.s("y")?.toFloatOrNull() ?: return@mapNotNull null,
                text = text,
                size = (o.s("size")?.toFloatOrNull() ?: 10f).coerceIn(5f, 30f),
            )
        }
        if (values.isEmpty() && texts.isEmpty()) return ToolOutput("FEHLER: Weder fields noch texts angegeben. Lies das Formular zuerst mit read_pdf_form.")
        val base = DocumentWriter.safeName(args.s("file_name")?.substringBeforeLast('.')?.ifBlank { null } ?: (att.name.substringBeforeLast('.') + " (ausgefüllt)"))
        val (out, report) = withContext(Dispatchers.IO) {
            folder.mkdirs()
            var f = File(folder, "$base.pdf")
            var n = 2
            while (f.exists()) f = File(folder, "$base ($n).pdf").also { n++ }
            f to PdfForms.fill(context, file, f, values, texts, args.s("flatten")?.lowercase() == "true")
        }
        return ToolOutput(
            buildString {
                append("PDF ausgefüllt: ${out.name} (${report.filled.size} Angaben eingetragen). ")
                append("Es wird dem Nutzer im Chat mit Knöpfen zum Öffnen, Teilen und Speichern angezeigt – nenne keinen Dateipfad.")
                if (report.problems.isNotEmpty()) append("\nProbleme:\n").append(report.problems.joinToString("\n") { "- $it" })
            },
            file = out.absolutePath,
        )
    }

    private suspend fun checkAccessibility(args: JsonObject, ctx: RepoContext): ToolOutput {
        val wanted = args.s("file")?.trim()?.takeIf { it.isNotEmpty() }
        val attached = ctx.pdfs.mapNotNull { a -> a.path?.let { File(it) }?.takeIf { it.exists() }?.let { a.name to it } }
        val created = folder.listFiles().orEmpty().filter { it.isFile && it.extension.lowercase() in setOf("pdf", "docx", "pptx", "html") }
            .map { it.name to it }
        val all = attached + created
        val (name, file) = (
            if (wanted == null) all.maxByOrNull { it.second.lastModified() }
            else all.firstOrNull { it.first.equals(wanted, ignoreCase = true) } ?: all.firstOrNull { it.first.contains(wanted.substringBeforeLast('.'), ignoreCase = true) }
            ) ?: return ToolOutput(
            if (all.isEmpty()) "FEHLER: Es gibt kein Dokument zum Prüfen. Bitte den Nutzer, das PDF anzuhängen."
            else "FEHLER: „$wanted“ nicht gefunden. Vorhanden: ${all.sortedByDescending { it.second.lastModified() }.take(8).joinToString(", ") { it.first }}."
        )
        val findings = withContext(Dispatchers.IO) { io.github.thepytales.llmhub.docs.AccessibilityCheck.check(context, file) }
        return ToolOutput(io.github.thepytales.llmhub.docs.AccessibilityCheck.report(name, findings))
    }

    /** Tool arguments that should be JSON may also arrive as a string containing JSON. */
    private fun jsonArg(e: JsonElement?): JsonElement? = when {
        e == null || e is JsonNull -> null
        e is JsonPrimitive && e.isString -> runCatching { JSON.parseToJsonElement(e.content) }.getOrNull()
        else -> e
    }

    /** Instructions with the logos currently available. */
    fun prompt(): String = buildString {
        append("Mit create_document kannst du PDF-, Word-, HTML-, Markdown- oder Textdateien erstellen. Nutze es, wenn der ")
        append("Nutzer ein Dokument, eine Datei oder einen Bericht zum Herunterladen möchte. Für einfache Dokumente reicht Markdown ")
        append("(die App gestaltet es mit style und accent_color). Wünscht der Nutzer eine besondere Gestaltung (Farben, Formen, ")
        append("Flyer, Rechnung, Lebenslauf, Urkunde …), schreibe für format=pdf eine vollständige HTML-Seite mit eigenem CSS ")
        append("(A4: @page { size: A4; margin: 15mm }, keine externen Skripte). ")
        append("Für Dokumente der TU Dresden (Briefe, Protokolle, Formulare, Merkblätter …) nimm Markdown mit style=tud: ")
        append("die App setzt TUD-Blau (${TudBrand.BLUE}), Logo, Einrichtung, Datum und Fußzeile automatisch – das ist schnell ")
        append("und einheitlich; schreibe dafür kein eigenes HTML und wiederhole Logo, Einrichtung oder Datum nicht im Text. ")
        append("Ein erstelltes Dokument änderst du mit edit_document (einzelne Stellen ersetzen) statt es neu zu erstellen. ")
        append("Markdown-Dokumente stehen dem Nutzer auch im Dokument-Editor zur Verfügung, wo er sie selbst ändert und kommentiert: ")
        append("lies vor einer Änderung mit read_document den aktuellen Stand; auf Kommentare antwortest du nach der Änderung mit ")
        append("reply_to_comment. ")
        append("Fehlen für ein Dokument wesentliche Angaben (Empfänger, Datum, Beträge, Namen), frage vor dem Erstellen kurz ")
        append("und gezielt nach (höchstens drei Fragen auf einmal); nebensächliche Lücken lässt du weg. Platzhalter wie [Datum] ")
        append("nur, wenn der Nutzer ausdrücklich eine Vorlage zum Ausfüllen möchte. ")
        append("Ton: sachlich und förmlich, keine Emojis. Für Briefe letter_sender (Infoblock: Name, Telefon: …, E-Mail: …) und ")
        append("letter_recipient angeben, title ist der Betreff. Formulare: zweispaltige Tabellen „| Bezeichnung | |“ mit leerer ")
        append("rechter Zelle werden zu Eingabefeldern, „- [ ] Option“ zu Ankreuzkästchen, „________“ zu einer Linie. ")
        append("E-Mails, die der Nutzer selbst senden soll: create_email_draft; Termine: create_calendar_event. ")
        append("Teilnahmebescheinigungen, Zertifikate und Urkunden: Markdown mit layout=urkunde. ")
        append("Barrierefreiheit: Überschriften lückenlos hierarchisch (#, ##, ###), Tabellen mit Kopfzeile, Links mit sprechendem ")
        append("Text statt „hier“, keine Information nur über Farbe, Abkürzungen beim ersten Vorkommen ausschreiben. Die App setzt ")
        append("Sprache, Titel, Überschriften-Formatvorlagen, Alternativtexte und Folientitel selbst. Mit check_accessibility prüfst ")
        append("du ein Dokument (auch ein angehängtes PDF) und nennst die Mängel mit Abhilfe. ")
        append("Word/PDF-Extras im Markdown: ").append(io.github.thepytales.llmhub.ui.DOC_MARKUP_HELP)
        append(PPTX_HELP)
        append(io.github.thepytales.llmhub.ui.SLIDE_LAYOUT_HELP)
        append(QtiWriter.HELP)
        append(MORE_FORMATS_HELP)
        append(io.github.thepytales.llmhub.docs.LimeSurveyWriter.HELP)
        append("Hängt der Nutzer ein PDF-Formular an (im Anhang als ausfuellbar markiert) und möchte es ausgefüllt haben: erst ")
        append("read_pdf_form, dann fill_pdf mit den Angaben aus dem Chat; fehlen Pflichtangaben, frage vorher nach, und ")
        append("erfinde keine persönlichen Daten. Nenne danach kurz, was eingetragen wurde und was offen ist. ")
        append("Eingebaute Logos für eigenes HTML: <img src=\"logo:${TudBrand.LOGO_NAME}\"> ")
        append("(blau) und <img src=\"logo:${TudBrand.LOGO_WHITE_NAME}\"> (weiß, für blaue Flächen). ")
        val all = logos.current
        if (all.isEmpty()) {
            append("Es sind keine Logos gespeichert; schickt der Nutzer ein Logo als Bild, kannst du es mit save_logo speichern.")
        } else {
            append("Gespeicherte Logos: ")
            append(all.joinToString(", ") { if (it.isDefault) "${it.name} (Standard)" else it.name })
            append(". Bei Markdown über den Parameter logo einbinden; im HTML mit <img src=\"logo:NAME\"> bzw. <img src=\"logo\"> für das Standardlogo.")
            if (settings.current.autoLogo) append(" Das Standardlogo wird automatisch in jedes Dokument eingefügt; binde es auch in eigenes HTML ein.")
        }
    }

    companion object {
        /** How the model writes slides for format=pptx (see docs/PptxWriter). */
        const val PPTX_HELP = "PowerPoint-Präsentationen: create_document mit format=pptx; content ist Markdown, Folien durch eine " +
            "Zeile „---“ getrennt. Erste Folie = Titelfolie: „# Titel“, „## Untertitel“, darunter eine Zeile mit Vortragenden " +
            "und Datum. Jede weitere Folie beginnt mit „# Folientitel“, darunter kurze Listen (höchstens 6 Punkte mit je höchstens " +
            "12 Wörtern, eine Kernaussage pro Folie), nummerierte Listen, ein kurzer Absatz oder eine Tabelle. Eine Folie nur mit " +
            "Überschrift (und optional einem Satz) wird zur Abschnittsfolie. Zwei Spalten: eine Zeile „|||“ trennt links und " +
            "rechts. Zitat-Folie: „> Zitat“ und „> — Quelle“. Sprechernotizen am Ende der Folie nach „Notizen:“ – dort steht, " +
            "was die Vortragenden sagen. Logo, Einrichtung, Fußzeile und Foliennummern setzt die App im TUD-Design. "
        const val MORE_FORMATS_HELP = "Weitere Formate: xlsx bzw. csv schreiben die Markdown-Tabellen als Excel-Tabelle (ein Blatt je Tabelle, " +
            "benannt nach der Überschrift darüber; Zahlen bleiben Zahlen) – z.B. Notenübersichten, Auswertungen, Literaturlisten. " +
            "moodle und gift erzeugen aus demselben Markdown wie qti einen Moodle-Test. anki erzeugt Karteikarten: Tabelle " +
            "„| Vorderseite | Rückseite |“ oder Zeilen „Frage :: Antwort“. Diagramme: create_chart, im Dokument als ```chart-Block. " +
            "Formeln schreibst du in LaTeX zwischen \$…\$; die App setzt sie als lesbaren Text. "
        private val CERTIFICATE_LAYOUTS = setOf("urkunde", "zertifikat", "certificate", "bescheinigung", "teilnahmebescheinigung")
        private val LOGO_SRC = Regex("src\\s*=\\s*([\"'])logo(?::([^\"']*))?\\1", RegexOption.IGNORE_CASE)
    }
}
