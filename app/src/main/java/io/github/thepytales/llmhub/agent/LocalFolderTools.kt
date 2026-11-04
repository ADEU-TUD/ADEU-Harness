package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.LocalFolder
import io.github.thepytales.llmhub.data.Privacy
import io.github.thepytales.llmhub.docs.EpubWriter
import io.github.thepytales.llmhub.docs.LatexWriter
import io.github.thepytales.llmhub.docs.OpenDocumentWriter
import io.github.thepytales.llmhub.docs.RtfWriter
import io.github.thepytales.llmhub.docs.XlsxWriter
import io.github.thepytales.llmhub.files.FormatReaders
import io.github.thepytales.llmhub.files.LocalText
import io.github.thepytales.llmhub.files.Notebook
import io.github.thepytales.llmhub.files.OfficeEdit
import io.github.thepytales.llmhub.files.SyntaxCheck
import io.github.thepytales.llmhub.files.TextCodec
import io.github.thepytales.llmhub.files.TextFormats
import io.github.thepytales.llmhub.files.TextStyle
import io.github.thepytales.llmhub.files.ZipText
import io.github.thepytales.llmhub.net.s
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lets the agent work in folders on this PC the user shared (Einstellungen › Lokale Ordner): list, read (Office, PDF,
 * OpenDocument, e-mails, notebooks, ZIP archives …), search, write text files and documents, edit text files keeping
 * their encoding, change Office files in place, transcribe recordings, move and delete. Nothing outside the shared
 * folders can be reached; deleted files go to ".adeu-papierkorb" inside the folder.
 */
class LocalFolderTools(private val c: AppContainer) : ToolProvider {
    private val folderParam = Param("folder", "Name des freigegebenen Ordners (Standard: der einzige bzw. erste)", required = false)

    override val defs = listOf(
        ToolDef(
            "list_local_files", "Listet Dateien und Unterordner in einem freigegebenen Ordner auf diesem PC (mit Größe und Änderungsdatum); bei einer .zip-Datei deren Inhalt.",
            listOf(
                folderParam,
                Param("path", "Unterordner (oder .zip-Datei) relativ zum freigegebenen Ordner (Standard: oberste Ebene)", required = false),
                Param("recursive", "true: auch alle Unterordner (höchstens 500 Einträge)", required = false, type = "boolean"),
            ),
        ),
        ToolDef(
            "read_local_file",
            "Liest eine Datei aus einem freigegebenen Ordner, mit Zeilennummern: Text und Code (alle gängigen Sprachen, Kodierung wird erkannt), " +
                "Markdown, CSV, Word, PowerPoint, Excel (als Markdown), PDF, OpenDocument (.odt/.ods/.odp), RTF, E-Mails (.eml, .msg), " +
                "Kalender (.ics), Kontakte (.vcf), E-Books (.epub), Jupyter-Notebooks (.ipynb, als Zellen), alte Office-Dateien (.doc/.xls/.ppt) " +
                "und ZIP-Archive (Inhaltsliste; einzelne Datei mit Pfad „Archiv.zip/ordner/datei.txt“).",
            listOf(
                folderParam, Param("path", "Dateipfad relativ zum freigegebenen Ordner"),
                Param("start_line", "Erste Zeile (optional)", required = false, type = "integer"),
                Param("end_line", "Letzte Zeile (optional)", required = false, type = "integer"),
            ),
        ),
        ToolDef(
            "search_local_files",
            "Sucht in Dateinamen und im Inhalt eines freigegebenen Ordners (ohne Groß-/Kleinschreibung): Textdateien und Code, Word, " +
                "PowerPoint, Excel, PDF, OpenDocument, RTF, E-Mails und Notebooks. Treffer mit Zeilennummern.",
            listOf(
                folderParam, Param("query", "Suchbegriff (oder regulärer Ausdruck, wenn regex=true)"),
                Param("path", "Nur in diesem Unterordner (optional)", required = false),
                Param("regex", "true: query ist ein regulärer Ausdruck (Java-Syntax), z.B. „def \\w+\\(“", required = false, type = "boolean"),
                Param("file_pattern", "Nur Dateien, deren Name passt, z.B. „*.py“ oder „*.{md,txt}“; mit / gegen den Pfad, z.B. „src/**/*.kt“", required = false),
                Param("context", "Zeilen vor und nach jedem Treffer zeigen (0–10, Standard 0)", required = false, type = "integer"),
            ),
        ),
        ToolDef(
            "write_local_file",
            "Legt eine Datei im freigegebenen Ordner an oder überschreibt sie. Endet path auf .docx, .pdf, .pptx, .xlsx, .html, .odt, .ods, .odp, " +
                ".rtf, .epub oder .tex, ist content Markdown und die App setzt daraus das Dokument (style wie bei create_document, z.B. tud; " +
                "fertiges RTF bzw. LaTeX wird wörtlich gespeichert). .csv: Markdown-Tabellen werden zu CSV mit Semikolon (für Excel). " +
                "Sonst wird content wörtlich gespeichert; beim Überschreiben bleiben Kodierung und Zeilenenden der Datei erhalten. " +
                "JSON, XML, YAML, CSV, Notebooks und Python werden danach auf Syntaxfehler geprüft.",
            listOf(
                folderParam, Param("path", "Dateipfad relativ zum freigegebenen Ordner, z.B. „Berichte/Protokoll.docx“"),
                Param("content", "Vollständiger Inhalt (Text bzw. Markdown)"),
                Param("style", "Nur für Dokumente: modern, kraeftig, elegant, klassisch, schlicht oder tud", required = false),
            ),
            writes = true,
        ),
        ToolDef(
            "edit_local_file",
            "Ändert eine Textdatei des freigegebenen Ordners, Kodierung (z.B. Windows-1252, UTF-8 mit BOM) und Zeilenenden bleiben erhalten. " +
                "Entweder old_text/new_text (exakter, eindeutiger Abschnitt), oder edits für mehrere Ersetzungen auf einmal, oder " +
                "start_line/end_line/new_text, um Zeilen zu ersetzen (Nummern wie bei read_local_file). Für Word/PowerPoint/Excel " +
                "edit_office_file nehmen, für Notebook-Zellen edit_notebook_cell.",
            listOf(
                folderParam, Param("path", "Dateipfad"),
                Param("old_text", "Exakter bisheriger Text (genau einmal vorhanden)", required = false),
                Param("new_text", "Neuer Text (auch für den Zeilenmodus)", required = false),
                Param("edits", "Mehrere Ersetzungen als JSON-Array, nacheinander angewandt: [{\"old_text\": \"…\", \"new_text\": \"…\", \"replace_all\": false}, …]", required = false),
                Param("replace_all", "true: alle Vorkommen von old_text ersetzen statt genau eines", required = false, type = "boolean"),
                Param("start_line", "Zeilenmodus: erste zu ersetzende Zeile", required = false, type = "integer"),
                Param("end_line", "Zeilenmodus: letzte zu ersetzende Zeile (Standard: start_line; start_line − 1 fügt vor start_line ein)", required = false, type = "integer"),
            ),
            writes = true,
        ),
        ToolDef(
            "edit_notebook_cell",
            "Ändert eine Zelle eines Jupyter-Notebooks (.ipynb) im freigegebenen Ordner; alles andere (Metadaten, Ausgaben der übrigen Zellen) bleibt. " +
                "Zellnummern wie bei read_local_file („## Zelle 3“).",
            listOf(
                folderParam, Param("path", "Pfad des Notebooks"),
                Param("cell", "Nummer der Zelle (ab 1); zum Anhängen Anzahl der Zellen + 1 mit action=insert_before", type = "integer"),
                Param("source", "Neuer Inhalt der Zelle (bei delete leer lassen)", required = false),
                Param("action", "replace (Standard), insert_before, insert_after oder delete", required = false),
                Param("cell_type", "code oder markdown (beim Einfügen Standard: code)", required = false),
            ),
            writes = true,
        ),
        ToolDef(
            "edit_office_file",
            "Ersetzt Text in einer Word-, PowerPoint- oder Excel-Datei (.docx, .pptx, .xlsx) direkt in der Datei – Formatierung, Bilder und " +
                "Layout bleiben erhalten (anders als write_local_file, das neu setzt). Der Text darf über mehrere Formatierungen eines Absatzes gehen, " +
                "nicht über Absatz- oder Zellgrenzen.",
            listOf(
                folderParam, Param("path", "Pfad der Datei"),
                Param("old_text", "Bisheriger Text (genau einmal vorhanden, außer replace_all=true)"),
                Param("new_text", "Neuer Text"),
                Param("replace_all", "true: alle Vorkommen ersetzen", required = false, type = "boolean"),
                Param("slide", "Nur PowerPoint: nur auf dieser Folie (Nummer)", required = false, type = "integer"),
            ),
            writes = true,
        ),
        ToolDef(
            "set_excel_cells",
            "Setzt Zellen in einem Blatt einer Excel-Datei (.xlsx) im freigegebenen Ordner, Formatierung und übrige Blätter bleiben. " +
                "Zahlen werden Zahlen, „=…“ Formeln – deutsch wie im Editor (=SUMME(B1:B3); =RUNDEN(A1;2)) oder Excel-Notation (=SUM(B1:B3)); " +
                "Excel rechnet beim Öffnen neu –, " +
                "alles andere Text; ein führendes ' erzwingt Text, \"\" leert die Zelle.",
            listOf(
                folderParam, Param("path", "Pfad der .xlsx-Datei"),
                Param("cells", "JSON-Objekt Zelle → Wert, z.B. {\"B3\": \"12\", \"C4\": \"=SUM(B1:B3)\", \"A1\": \"Name\"}"),
                Param("sheet", "Name oder Nummer (ab 1) des Blatts (Standard: erstes Blatt)", required = false),
            ),
            writes = true,
        ),
        ToolDef(
            "transcribe_local_file",
            "Wandelt eine Audio- oder Videodatei (.mp3, .m4a, .wav, .ogg, .opus, .flac, .mp4, .mov, .webm, .mkv …) aus dem freigegebenen Ordner " +
                "mit der Spracherkennung der App in Text um – je nach Datenschutzstufe des Projekts auf dem Uni-Server oder auf diesem PC. " +
                "Für lange Aufnahmen mit Sprechern und Zeitmarken besser die Transkription der App empfehlen.",
            listOf(
                folderParam, Param("path", "Pfad der Audio- oder Videodatei"),
                Param("language", "Sprache als ISO-Code, z.B. de oder en (optional)", required = false),
                Param("save_as", "Optional: Transkript zusätzlich als Textdatei im Ordner speichern, z.B. „Interview.txt“", required = false),
            ),
        ),
        ToolDef(
            "move_local_file", "Verschiebt oder benennt eine Datei bzw. einen Ordner innerhalb des freigegebenen Ordners um.",
            listOf(folderParam, Param("from", "Bisheriger Pfad"), Param("to", "Neuer Pfad")),
            writes = true,
        ),
        ToolDef(
            "delete_local_file", "Verschiebt eine Datei oder einen Ordner in den Papierkorb des freigegebenen Ordners („.adeu-papierkorb“).",
            listOf(folderParam, Param("path", "Pfad")),
            writes = true,
        ),
        ToolDef(
            "copy_document_to_folder", "Speichert ein im Chat erstelltes Dokument (create_document) in einen freigegebenen Ordner.",
            listOf(folderParam, Param("file", "Dateiname des erstellten Dokuments"), Param("path", "Zielpfad im Ordner (Standard: gleicher Dateiname)", required = false)),
            writes = true,
        ),
    )

    override fun risk(name: String, args: JsonObject) = when {
        name == "transcribe_local_file" -> if (args.s("save_as").isNullOrBlank()) null else Risk.EDIT
        defs.firstOrNull { it.name == name }?.writes == true -> Risk.EDIT
        else -> null
    }

    /**
     * New files in the project's "Ausgabe" – and replacing the ones the app put there – need no OK: that is what the folder
     * is for. Everything else that changes files still asks.
     */
    override fun risk(name: String, args: JsonObject, ctx: RepoContext): Risk? {
        val risk = risk(name, args) ?: return null
        if (name != "write_local_file" && name != "copy_document_to_folder") return risk
        val target = runCatching { outputTarget(name, args, ctx) }.getOrNull() ?: return risk
        return if (!target.exists() || WorkFolder.madeByApp(c, target)) null else risk
    }

    /** Where [name] would write if that is inside the project's "Ausgabe", else null. */
    private fun outputTarget(name: String, args: JsonObject, ctx: RepoContext): File? {
        val folder = folder(args.s("folder"), ctx.folderId, chatFolders(ctx.chatId))
        if (!folder.id.startsWith(PROJECT_ID)) return null
        val root = File(folder.path).canonicalFile
        val out = (if (folder.name == PROJECT_OUTPUT) root else WorkFolder.output(root)).canonicalFile
        val path = args.s("path")?.takeIf { it.isNotBlank() }
        val target = when (name) {
            "write_local_file" -> resolve(root, path ?: return null)
            else -> {
                val source = document(args.s("file").orEmpty())
                resolve(root, path ?: source.name).let { if (it.isDirectory) File(it, source.name) else it }
            }
        }
        return target.takeIf { it.path.startsWith(out.path + File.separator) }
    }

    override fun parallelSafe(name: String) = name in setOf("list_local_files", "read_local_file", "search_local_files")

    /**
     * Shared folders plus the project folder of the chat: all of it if the model may read the files there, otherwise only
     * its "Ausgabe".
     */
    private fun available(folderId: String?, chat: List<LocalFolder> = emptyList()): List<LocalFolder> {
        val project = WorkFolder.of(c, folderId)?.let { (f, dir) ->
            if (f.workDirAccess) LocalFolder(id = PROJECT_ID + f.id, name = PROJECT, path = dir.path)
            else LocalFolder(id = PROJECT_ID + f.id, name = PROJECT_OUTPUT, path = WorkFolder.output(dir).apply { mkdirs() }.path)
        }
        // Folders attached to the chat count before the shared ones with the same path (their right wins in this chat).
        val first = listOfNotNull(project) + chat.filter { ch -> project == null || File(ch.path).absoluteFile != File(project.path).absoluteFile }
        return first + c.localFolders.current.filter { g -> first.none { File(it.path).absoluteFile == File(g.path).absoluteFile } }
    }

    /** The folders attached to the chat [chatId] („+“ › Ordner). */
    private fun chatFolders(chatId: String?): List<LocalFolder> =
        chatId?.let { id -> (c.runs.get(id)?.conversation?.value ?: c.chats.get(id))?.folders }.orEmpty()

    fun prompt(folderId: String? = null, chat: List<LocalFolder> = emptyList()): String = buildString {
        append("Du kannst in Ordnern auf diesem PC arbeiten: mit list_local_files, read_local_file und search_local_files ")
        append("lesen, mit write_local_file, edit_local_file, move_local_file, delete_local_file und copy_document_to_folder schreiben. ")
        append("Verschaffe dir zuerst einen Überblick, bevor du etwas änderst, und nenne am Ende, welche Dateien du angelegt oder geändert hast. ")
        append("read_local_file liest auch PDF, OpenDocument, RTF, E-Mails (.eml/.msg), Kalender (.ics), Kontakte (.vcf), E-Books, Jupyter-Notebooks, ")
        append("alte Office-Dateien und ZIP-Archive („Archiv.zip/datei.txt“). Bestehende Word-, PowerPoint- und Excel-Dateien änderst du mit ")
        append("edit_office_file bzw. set_excel_cells (Formatierung bleibt), Notebook-Zellen mit edit_notebook_cell, Textdateien mit edit_local_file ")
        append("(mehrere Stellen auf einmal mit edits; Kodierung und Zeilenenden bleiben). Audio und Video: transcribe_local_file. ")
        append("Ordner:\n")
        available(folderId, chat).forEach { f ->
            append("- „${f.name}“ (${f.path})").append(if (f.write) "" else " – nur lesen")
            if (chat.any { it.id == f.id }) append(" – an diesen Chat angehängt")
            append('\n')
        }
        if (chat.isNotEmpty()) {
            append("An diesen Chat angehängte Ordner sind der Standard, wenn der Nutzer keinen anderen nennt; bei Fragen zu ihrem Inhalt ")
            append("verschaffe dir zuerst mit list_local_files einen Überblick. In „nur lesen“-Ordnern schreibst du nichts – der Nutzer kann ")
            append("das Recht über den Ordner-Chip über dem Eingabefeld ändern.\n")
        }
        WorkFolder.of(c, folderId)?.let { (f, dir) ->
            val out = WorkFolder.OUTPUT
            if (f.workDirAccess) {
                append("„$PROJECT“ ist der Ordner des Projekts „${f.name}“ und der Standard, wenn der Nutzer keinen anderen nennt. ")
                append("Beziehe ihn ein: Bevor du für das Projekt schreibst (Briefe, Texte, Zusammenfassungen, Antworten zu Inhalten), ")
                append("sieh nach, welche Dateien zum Thema passen, lies sie mit read_local_file und nutze sie. ")
                append("Ändere, verschiebe oder lösche Dateien des Nutzers nur, wenn er das ausdrücklich will.\n")
                overview(dir)?.let { append("Inhalt von „$PROJECT“:\n").append(it) }
                append("Dokumente aus create_document und edit_document legt die App automatisch unter „$out“ im Projektordner ab – ")
                append("nicht noch einmal kopieren. Eigene Dateien (Text, CSV …) mit write_local_file unter „$out/…“ speichern.\n")
            } else {
                append("„$PROJECT_OUTPUT“ ist der Ordner „$out“ im Projektordner von „${f.name}“; die übrigen Dateien dort sind für dich nicht freigegeben. ")
                append("Dokumente aus create_document und edit_document legt die App automatisch dort ab – nicht noch einmal kopieren. ")
                append("Eigene Dateien (Text, CSV …) mit write_local_file dort speichern.\n")
            }
        }
    }

    /** Up to [MAX_OVERVIEW] files and folders of [dir] for the system prompt; null if it cannot be read. */
    private fun overview(dir: File): String? = runCatching {
        val root = dir.canonicalFile
        val entries = root.walkTopDown().maxDepth(3).filter { it != root && !hidden(root, it) }.take(MAX_OVERVIEW + 1).toList()
        buildString {
            if (entries.isEmpty()) append("(noch leer)\n")
            entries.take(MAX_OVERVIEW).sortedBy { rel(root, it).lowercase() }.forEach { f ->
                append("- ").append(rel(root, f)).append(if (f.isDirectory) "/" else "").append('\n')
            }
            if (entries.size > MAX_OVERVIEW) append("- … weitere Einträge, mit list_local_files nachsehen\n")
        }
    }.getOrNull()

    override suspend fun run(name: String, args: JsonObject, ctx: RepoContext): ToolOutput = withContext(Dispatchers.IO) {
        val folder = folder(args.s("folder"), ctx.folderId, chatFolders(ctx.chatId))
        val root = File(folder.path).canonicalFile
        if (!root.isDirectory) throw IllegalArgumentException("Der Ordner „${folder.name}“ (${folder.path}) ist nicht erreichbar.")
        if (defs.first { it.name == name }.writes && !folder.write) throw IllegalArgumentException("Der Ordner „${folder.name}“ ist nur zum Lesen freigegeben.")
        when (name) {
            "list_local_files" -> {
                val path = args.s("path").orEmpty()
                val dir = resolve(root, path)
                val inZip = if (dir.exists()) null else zipPath(root, path)
                ToolOutput(inZip?.let { (zip, inner) -> zipListing(root, zip, inner) } ?: list(root, dir, args.s("recursive") == "true"))
            }
            "read_local_file" -> ToolOutput(read(root, args))
            "search_local_files" -> ToolOutput(search(root, resolve(root, args.s("path").orEmpty()), args))
            "write_local_file" -> write(root, args, ctx).also { out ->
                val target = resolve(root, args.s("path").orEmpty())
                if (folder.id.startsWith(PROJECT_ID) && out.text.startsWith("Angelegt")) WorkFolder.delivered(c, target)
            }
            "edit_local_file" -> ToolOutput(edit(root, args))
            "edit_notebook_cell" -> ToolOutput(editNotebook(root, args))
            "edit_office_file" -> ToolOutput(editOffice(root, args))
            "set_excel_cells" -> ToolOutput(setCells(root, args))
            "transcribe_local_file" -> transcribe(root, folder, args, ctx)
            "move_local_file" -> {
                val from = existing(root, args.s("from").orEmpty())
                val to = resolve(root, args.s("to").orEmpty())
                if (to.exists()) throw IllegalArgumentException("„${rel(root, to)}“ gibt es schon.")
                to.parentFile?.mkdirs()
                if (!from.renameTo(to)) throw IllegalStateException("Verschieben nicht möglich (Datei geöffnet?).")
                ToolOutput("Verschoben: ${rel(root, from)} → ${rel(root, to)}")
            }
            "delete_local_file" -> {
                val target = existing(root, args.s("path").orEmpty())
                if (target == root) throw IllegalArgumentException("Der freigegebene Ordner selbst kann nicht gelöscht werden.")
                val trash = File(root, TRASH).apply { mkdirs() }
                val stamp = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.GERMANY).format(Date())
                val dest = File(trash, "$stamp ${target.name}")
                if (!target.renameTo(dest)) throw IllegalStateException("Löschen nicht möglich (Datei geöffnet?).")
                ToolOutput("„${rel(root, target)}“ liegt jetzt im Papierkorb des Ordners ($TRASH) und lässt sich von dort zurückholen.")
            }
            "copy_document_to_folder" -> {
                val source = document(args.s("file").orEmpty())
                val target = resolve(root, args.s("path")?.takeIf { it.isNotBlank() } ?: source.name).let { if (it.isDirectory) File(it, source.name) else it }
                val existed = target.exists()
                target.parentFile?.mkdirs()
                source.copyTo(target, overwrite = true)
                if (!existed && folder.id.startsWith(PROJECT_ID)) WorkFolder.delivered(c, target)
                ToolOutput("Gespeichert: ${rel(root, target)} (${target.absolutePath})", file = target.absolutePath)
            }
            else -> ToolOutput("FEHLER: Unbekanntes Werkzeug $name")
        }
    }

    /** A document the app created (create_document) by its file name, or the newest one whose name contains it. */
    private fun document(wanted: String): File {
        val name = wanted.trim()
        val files = c.documentTools.folder.listFiles().orEmpty().filter { it.isFile && !it.name.endsWith(".json") }
        return files.firstOrNull { it.name.equals(name, true) }
            ?: files.filter { it.name.contains(name.substringBeforeLast('.'), true) }.maxByOrNull { it.lastModified() }
            ?: throw IllegalArgumentException("Dokument „$name“ nicht gefunden.")
    }

    private fun folder(name: String?, folderId: String?, chat: List<LocalFolder> = emptyList()): LocalFolder {
        val all = available(folderId, chat)
        if (all.isEmpty()) throw IllegalArgumentException("Es ist kein Ordner freigegeben (Einstellungen › Lokale Ordner oder Projektordner des Projekts).")
        val wanted = name?.trim()?.takeIf { it.isNotEmpty() } ?: return all.first()
        return all.firstOrNull { it.name.equals(wanted, true) } ?: all.firstOrNull { it.path.equals(wanted, true) || File(it.path).name.equals(wanted, true) }
            ?: throw IllegalArgumentException("Ordner „$wanted“ ist nicht freigegeben. Freigegeben: ${all.joinToString { "„${it.name}“" }}.")
    }

    private suspend fun write(root: File, args: JsonObject, ctx: RepoContext): ToolOutput {
        val target = resolve(root, args.s("path").orEmpty())
        if (target.isDirectory) throw IllegalArgumentException("„${rel(root, target)}“ ist ein Ordner.")
        val content = args.s("content").orEmpty()
        val ext = target.extension.lowercase()
        target.parentFile?.mkdirs()
        val existed = target.exists()
        val verb = if (existed) "Überschrieben" else "Angelegt"
        if (ext in DOCUMENTS) {
            val made = c.documentTools.run(
                "create_document",
                JsonObject(
                    buildMap {
                        put("title", JsonPrimitive(target.nameWithoutExtension))
                        put("content", JsonPrimitive(content))
                        put("format", JsonPrimitive(ext))
                        args.s("style")?.let { put("style", JsonPrimitive(it)) }
                    },
                ),
                ctx,
            )
            val file = made.file?.let(::File) ?: throw IllegalStateException(made.text)
            save(target) { file.copyTo(target, overwrite = true) }
            return ToolOutput("$verb: ${rel(root, target)} (${target.absolutePath})", file = target.absolutePath)
        }
        val title = target.nameWithoutExtension
        val document: ByteArray? = when (ext) {
            "odt" -> OpenDocumentWriter.odt(title, content)
            "ods" -> OpenDocumentWriter.ods(title, content)
            "odp" -> OpenDocumentWriter.odp(title, content)
            "epub" -> EpubWriter.write(title, content)
            "rtf" -> if (content.trimStart().startsWith("{\\rtf")) null else RtfWriter.write(title, content).toByteArray(Charsets.US_ASCII)
            "tex" -> if (Regex("\\\\(documentclass|begin\\{|section|chapter|usepackage)").containsMatchIn(content)) null
            else LatexWriter.write(title, content).toByteArray(Charsets.UTF_8)
            "csv" -> if (content.lines().count { it.trimStart().startsWith("|") } >= 2) XlsxWriter.csv(title, content).toByteArray(Charsets.UTF_8) else null
            else -> null
        }
        if (document != null) {
            save(target) { target.writeBytes(document) }
            val what = when (ext) { "rtf" -> "RTF"; "tex" -> "LaTeX"; "csv" -> "CSV aus den Markdown-Tabellen"; "epub" -> "E-Book"; else -> "OpenDocument" }
            return ToolOutput("$verb: ${rel(root, target)} ($what, ${ZipText.size(target.length())}) (${target.absolutePath})" + warning(target, ext), file = target.absolutePath)
        }
        // Plain text: an existing file keeps its encoding and line endings; new files get what Windows expects for their type.
        val before = if (existed && plainText(target)) runCatching { TextCodec.read(target).style }.getOrNull() else null
        val style = before ?: when (ext) {
            "csv" -> TextStyle(Charsets.UTF_8, bom = true, crlf = true)
            "bat", "cmd", "vbs", "reg", "sln" -> TextStyle(Charsets.UTF_8, bom = false, crlf = true)
            "ps1", "psm1", "psd1" -> TextStyle(Charsets.UTF_8, bom = true, crlf = true)
            else -> TextStyle.DEFAULT
        }
        var used = style
        save(target) { used = TextCodec.write(target, content, style) }
        val kept = when {
            before != null && used != before -> " – Achtung: Die Datei war ${before.label}; der neue Text enthält Zeichen, die es darin nicht gibt, daher jetzt ${used.label}"
            before != null && before != TextStyle.DEFAULT -> " – ${before.label} beibehalten"
            else -> ""
        }
        return ToolOutput("$verb: ${rel(root, target)} (${content.lines().size} Zeilen)$kept" + warning(target, ext))
    }

    /** Syntax warning for the result of a write, empty if the file looks fine. */
    private fun warning(target: File, ext: String): String {
        if (ext !in setOf("json", "geojson", "ipynb", "xml", "svg", "xaml", "csproj", "vbproj", "props", "targets", "resx", "yaml", "yml", "csv", "tsv", "py")) return ""
        val text = runCatching { TextCodec.read(target).text }.getOrNull() ?: return ""
        val problem = SyntaxCheck.check(target, text) ?: return ""
        return "\nAchtung – Syntaxprüfung: $problem (Die Datei ist trotzdem gespeichert; bitte prüfen und korrigieren.)"
    }

    /** Runs [block]; a file opened in another program (Word, Excel) gives a clear message. */
    private inline fun save(target: File, block: () -> Unit) {
        try {
            block()
        } catch (e: java.io.IOException) {
            throw IllegalStateException("„${target.name}“ lässt sich nicht speichern – ist die Datei in einem anderen Programm geöffnet? (${e.message})")
        }
    }

    private data class Replacement(val old: String, val new: String, val all: Boolean)

    /** Replacements of edit_local_file: the edits array (JSON array or string) plus old_text/new_text. */
    private fun replacements(args: JsonObject): List<Replacement> {
        val list = mutableListOf<Replacement>()
        val raw = args["edits"]
        val array = when (raw) {
            is JsonArray -> raw
            is JsonPrimitive -> raw.contentOrNull?.takeIf { it.isNotBlank() }?.let {
                runCatching { Json.parseToJsonElement(it) as? JsonArray }.getOrNull()
                    ?: throw IllegalArgumentException("edits ist kein gültiges JSON-Array: [{\"old_text\": \"…\", \"new_text\": \"…\"}]")
            }
            else -> null
        }
        array?.forEachIndexed { i, el ->
            val o = el as? JsonObject ?: throw IllegalArgumentException("edits[${i + 1}] ist kein Objekt mit old_text und new_text.")
            val old = o.s("old_text") ?: o.s("old") ?: throw IllegalArgumentException("edits[${i + 1}]: old_text fehlt.")
            list += Replacement(old, o.s("new_text") ?: o.s("new").orEmpty(), o.s("replace_all") == "true")
        }
        args.s("old_text")?.takeIf { it.isNotEmpty() }?.let { list += Replacement(it, args.s("new_text").orEmpty(), args.s("replace_all") == "true") }
        return list
    }

    private fun edit(root: File, args: JsonObject): String {
        val target = existing(root, args.s("path").orEmpty())
        if (target.isDirectory) throw IllegalArgumentException("„${rel(root, target)}“ ist ein Ordner.")
        val ext = target.extension.lowercase()
        if (ext in OFFICE) throw IllegalArgumentException("„${target.name}“ ist eine Office-Datei – Text darin mit edit_office_file ändern (Formatierung bleibt) oder mit write_local_file neu schreiben.")
        if (!editable(target)) throw IllegalArgumentException("„${target.name}“ ist keine Textdatei – mit write_local_file neu schreiben.")
        val decoded = TextCodec.read(target)
        var text = decoded.text
        val start = args.int("start_line")
        val summary: String
        if (start != null) {
            if (!plainText(target)) throw IllegalArgumentException("Der Zeilenmodus geht nur bei reinen Textdateien – hier old_text/new_text verwenden" + (if (ext == "ipynb") " oder edit_notebook_cell." else "."))
            val lines = text.split('\n').toMutableList()
            val end = args.int("end_line") ?: start
            if (start < 1 || start > lines.size + 1 || end < start - 1 || end > lines.size) {
                throw IllegalArgumentException("Zeilen $start–$end gibt es nicht – die Datei hat ${lines.size} Zeilen.")
            }
            val replacement = args.s("new_text").orEmpty().replace("\r\n", "\n").removeSuffix("\n")
            val newLines = if (replacement.isEmpty() && args.s("new_text").isNullOrEmpty()) emptyList() else replacement.split('\n')
            repeat(end - start + 1) { lines.removeAt(start - 1) }
            lines.addAll(start - 1, newLines)
            text = lines.joinToString("\n")
            summary = when {
                end < start -> "${newLines.size} Zeilen vor Zeile $start eingefügt"
                newLines.isEmpty() -> "Zeilen $start–$end gelöscht"
                else -> "Zeilen $start–$end ersetzt durch ${newLines.size} Zeilen"
            }
        } else {
            val edits = replacements(args)
            if (edits.isEmpty()) throw IllegalArgumentException("Es fehlt, was geändert werden soll: old_text/new_text, edits oder start_line.")
            var total = 0
            edits.forEachIndexed { i, e ->
                val label = if (edits.size > 1) "Änderung ${i + 1}: " else ""
                val old = e.old.replace("\r\n", "\n")
                if (old.isEmpty()) throw IllegalArgumentException("${label}old_text ist leer.")
                val count = occurrences(text, old)
                when {
                    count == 0 -> throw IllegalArgumentException(
                        "${label}old_text kommt in „${target.name}“ nicht vor" + (if (i > 0) " (nach den vorherigen Änderungen)" else "") +
                            ". Nichts wurde geändert. Lies die Datei mit read_local_file." + hint(text, old),
                    )
                    count > 1 && !e.all -> throw IllegalArgumentException("${label}old_text kommt $count-mal vor – mehr Kontext angeben oder replace_all=true. Nichts wurde geändert.")
                }
                text = if (e.all) text.replace(old, e.new.replace("\r\n", "\n")) else text.replaceFirst(old, e.new.replace("\r\n", "\n"))
                total += count
            }
            summary = if (total == 1) "1 Ersetzung" else "$total Ersetzungen"
        }
        var used = decoded.style
        save(target) { used = TextCodec.write(target, text, decoded.style) }
        val style = when {
            used != decoded.style -> " – Achtung: Die Datei war ${decoded.style.label}; der neue Text enthält Zeichen, die es darin nicht gibt, daher jetzt ${used.label}"
            decoded.style != TextStyle.DEFAULT -> " – ${decoded.style.label} beibehalten"
            else -> ""
        }
        return "Geändert: ${rel(root, target)} ($summary)$style" + warning(target, ext)
    }

    private fun occurrences(text: String, old: String): Int {
        var n = 0
        var i = text.indexOf(old)
        while (i >= 0) { n++; i = text.indexOf(old, i + old.length) }
        return n
    }

    /** Why old_text was not found, if it is only a matter of spaces or case. */
    private fun hint(text: String, old: String): String {
        fun squeeze(s: String) = s.lines().joinToString("\n") { it.trim() }.replace(Regex("[ \\t]+"), " ")
        return when {
            squeeze(text).contains(squeeze(old)) -> " Hinweis: Der Text kommt mit anderen Leerzeichen bzw. Einrückungen vor – genau wie in der Datei angeben."
            text.contains(old, ignoreCase = true) -> " Hinweis: Der Text kommt mit anderer Groß-/Kleinschreibung vor."
            else -> ""
        }
    }

    private fun editNotebook(root: File, args: JsonObject): String {
        val target = existing(root, args.s("path").orEmpty())
        if (target.extension.lowercase() != "ipynb") throw IllegalArgumentException("„${target.name}“ ist kein Jupyter-Notebook (.ipynb).")
        val decoded = TextCodec.read(target)
        val mode = when (args.s("action")?.trim()?.lowercase()) {
            null, "", "replace", "ersetzen" -> Notebook.Mode.REPLACE
            "insert_before", "insert", "einfügen" -> Notebook.Mode.INSERT_BEFORE
            "insert_after" -> Notebook.Mode.INSERT_AFTER
            "delete", "löschen" -> Notebook.Mode.DELETE
            else -> throw IllegalArgumentException("action muss replace, insert_before, insert_after oder delete sein.")
        }
        val cell = args.int("cell") ?: throw IllegalArgumentException("cell fehlt.")
        val next = Notebook.edit(decoded.text, cell, args.s("source").orEmpty().replace("\r\n", "\n"), mode, args.s("cell_type")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() })
        save(target) { TextCodec.write(target, next, decoded.style) }
        val cells = runCatching { (Json.parseToJsonElement(next) as JsonObject)["cells"].let { (it as JsonArray).size } }.getOrDefault(0)
        val what = when (mode) {
            Notebook.Mode.REPLACE -> "Zelle $cell ersetzt"
            Notebook.Mode.INSERT_BEFORE -> "Neue Zelle $cell eingefügt"
            Notebook.Mode.INSERT_AFTER -> "Neue Zelle ${cell + 1} eingefügt"
            Notebook.Mode.DELETE -> "Zelle $cell gelöscht"
        }
        return "$what: ${rel(root, target)} (jetzt $cells Zellen; Ausgaben der übrigen Zellen unverändert)" + warning(target, "ipynb")
    }

    private fun editOffice(root: File, args: JsonObject): String {
        val target = existing(root, args.s("path").orEmpty())
        val ext = target.extension.lowercase()
        if (ext !in OFFICE) throw IllegalArgumentException("edit_office_file ändert .docx, .pptx und .xlsx – für Textdateien edit_local_file nehmen.")
        val r = OfficeEdit.replace(ext, target.readBytes(), args.s("old_text").orEmpty(), args.s("new_text").orEmpty(), args.s("replace_all") == "true", args.int("slide"))
        save(target) { target.writeBytes(r.bytes) }
        val where = r.parts.map { p ->
            Regex("slides/slide(\\d+)").find(p)?.let { "Folie ${it.groupValues[1]}" }
                ?: Regex("notesSlide(\\d+)").find(p)?.let { "Notizen zu Folie ${it.groupValues[1]}" }
                ?: when {
                    "header" in p -> "Kopfzeile"; "footer" in p -> "Fußzeile"; "footnotes" in p -> "Fußnoten"; "endnotes" in p -> "Endnoten"
                    "comments" in p -> "Kommentare"; "sharedStrings" in p || "worksheets" in p -> "Zellen"; else -> "Text"
                }
        }.distinct().joinToString(", ")
        return "Geändert: ${rel(root, target)} (${if (r.count == 1) "1 Ersetzung" else "${r.count} Ersetzungen"} – $where; Formatierung unverändert)"
    }

    private fun setCells(root: File, args: JsonObject): String {
        val target = existing(root, args.s("path").orEmpty())
        if (target.extension.lowercase() !in setOf("xlsx", "xlsm")) throw IllegalArgumentException("set_excel_cells geht nur mit .xlsx-Dateien.")
        val raw = args["cells"]
        val obj = when (raw) {
            is JsonObject -> raw
            is JsonPrimitive -> runCatching { Json.parseToJsonElement(raw.content) as JsonObject }.getOrNull()
            else -> null
        } ?: throw IllegalArgumentException("cells muss ein JSON-Objekt sein, z.B. {\"B3\": \"12\", \"C4\": \"=SUM(B1:B3)\"}.")
        val values = LinkedHashMap<String, String>()
        obj.forEach { (k, v) -> values[k] = (v as? JsonPrimitive)?.contentOrNull ?: if (v is kotlinx.serialization.json.JsonNull) "" else v.toString() }
        val r = OfficeEdit.setCells(target.readBytes(), args.s("sheet"), values)
        save(target) { target.writeBytes(r.bytes) }
        val formulas = values.values.count { it.startsWith("=") }
        return "In „${r.sheet}“ von ${rel(root, target)} ${if (r.cells == 1) "1 Zelle" else "${r.cells} Zellen"} gesetzt: ${values.keys.take(20).joinToString(", ")}" +
            (if (values.size > 20) ", …" else "") + "." + (if (formulas > 0) " Formeln berechnet Excel beim Öffnen; andere Programme zeigen sie evtl. erst nach Neuberechnung an." else "")
    }

    private suspend fun transcribe(root: File, folder: LocalFolder, args: JsonObject, ctx: RepoContext): ToolOutput {
        val f = existing(root, args.s("path").orEmpty())
        val ext = f.extension.lowercase()
        if (ext !in TextFormats.MEDIA) throw IllegalArgumentException("„${f.name}“ ist keine Audio- oder Videodatei.")
        val saveAs = args.s("save_as")?.trim()?.takeIf { it.isNotEmpty() }
        if (saveAs != null && !folder.write) throw IllegalArgumentException("Der Ordner „${folder.name}“ ist nur zum Lesen freigegeben – ohne save_as aufrufen.")
        val onDevice = !Privacy.level(c, ctx.folderId).allowsUniServer
        val serverOk = !onDevice && c.settings.current.hasApiKey && f.length() <= 25L * 1024 * 1024 && ext in SERVER_AUDIO
        val text = if (serverOk) {
            c.llm.transcribe(c.settings.current.sttModel, f, args.s("language")?.trim()?.takeIf { it.isNotEmpty() }, mimeOf(ext))
        } else {
            c.transcription.recognizeShort(f) ?: throw IllegalArgumentException(
                if (onDevice) "In diesem Projekt bleibt Sprache auf dem Gerät – dafür unter Einstellungen › Transkription ein Erkennungsmodell laden."
                else "Die Datei kann nicht an den Uni-Server gehen (kein API-Schlüssel, größer als 25 MB oder Videoformat) und auf diesem PC ist kein " +
                    "Erkennungsmodell geladen (Einstellungen › Transkription). Alternativ die Datei in der Transkription der App öffnen.",
            )
        }.trim()
        if (text.isEmpty()) return ToolOutput("In „${rel(root, f)}“ wurde keine Sprache erkannt.")
        val saved = saveAs?.let { name ->
            val target = resolve(root, if ('/' in name || '\\' in name) name else (rel(root, f).substringBeforeLast('/', "").let { if (it.isEmpty()) name else "$it/$name" }))
            if (target.isDirectory) throw IllegalArgumentException("„$name“ ist ein Ordner.")
            target.parentFile?.mkdirs()
            save(target) { TextCodec.write(target, text + "\n", TextStyle.DEFAULT) }
            target
        }
        val where = if (serverOk) "Uni-Server" else "auf diesem PC"
        return ToolOutput(
            "Transkript von ${rel(root, f)} ($where, ${text.length} Zeichen)" + (saved?.let { ", gespeichert als ${rel(root, it)}" } ?: "") + ":\n\n" + text,
            file = saved?.absolutePath,
        )
    }

    /** ZIP archive and path inside it for "Archiv.zip/ordner/datei.txt"; null if [path] does not lead into an archive. */
    private fun zipPath(root: File, path: String): Pair<File, String>? {
        val parts = path.trim().replace('\\', '/').trim('/').split('/')
        for (i in 1 until parts.size) {
            if (!parts[i - 1].lowercase().endsWith(".zip")) continue
            val zip = resolve(root, parts.take(i).joinToString("/"))
            if (zip.isFile) return zip to parts.drop(i).joinToString("/")
        }
        return null
    }

    private fun zipBytes(zip: File): ByteArray {
        if (zip.length() > 200L * 1024 * 1024) throw IllegalArgumentException("„${zip.name}“ ist zu groß (max. 200 MB).")
        return zip.readBytes()
    }

    private suspend fun readZipEntry(root: File, zip: File, inner: String, args: JsonObject): String {
        val bytes = zipBytes(zip)
        val entry = ZipText.entry(bytes, inner)
        if (entry == null) {
            if (ZipText.isDirectory(bytes, inner)) return zipListing(root, zip, inner)
            throw IllegalArgumentException("„$inner“ gibt es in „${zip.name}“ nicht. Mit list_local_files auf „${rel(root, zip)}“ nachsehen.")
        }
        val name = inner.substringAfterLast('/')
        val text = LocalText.read(name, entry)
        return numbered("${rel(root, zip)}/$inner (aus dem ZIP-Archiv, nur lesen)", text, args, TextFormats.language(name))
    }

    private fun zipListing(root: File, zip: File, inner: String = ""): String {
        val bytes = zipBytes(zip)
        if (inner.isEmpty()) return ZipText.listing(rel(root, zip), bytes)
        val prefix = inner.trim('/') + "/"
        val entries = ZipText.entries(bytes).filter { it.name.startsWith(prefix, true) }
        return buildString {
            append("Inhalt von ${rel(root, zip)}/$prefix:\n")
            entries.take(500).forEach { e -> append(if (e.directory) "[Ordner] ${e.name}" else "${e.name} · ${ZipText.size(e.size)}").append('\n') }
        }
    }

    /** Text with line numbers and a header (path, number of lines, language). */
    private fun numbered(header: String, text: String, args: JsonObject, language: String?, style: TextStyle? = null): String {
        val lines = text.lines()
        val from = (args.int("start_line") ?: 1).coerceAtLeast(1)
        val to = (args.int("end_line") ?: lines.size).coerceAtMost(lines.size)
        return buildString {
            append(header).append(" (").append(lines.size).append(" Zeilen")
            language?.let { append(" · ").append(it) }
            if (style != null && style != TextStyle.DEFAULT) append(" · ").append(style.label)
            append(")\n")
            for (i in from..to) append(i).append(": ").append(lines[i - 1]).append('\n')
            if (from > to && lines.isNotEmpty()) append("(keine Zeilen in diesem Bereich)\n")
        }
    }

    private suspend fun read(root: File, args: JsonObject): String {
        val path = args.s("path").orEmpty()
        if (!resolve(root, path).exists()) zipPath(root, path)?.let { (zip, inner) -> return readZipEntry(root, zip, inner, args) }
        val f = existing(root, path)
        if (f.isDirectory) return list(root, f, false)
        if (f.extension.lowercase() == "zip") return zipListing(root, f)
        if (f.length() > 25L * 1024 * 1024 && !(f.extension.lowercase() in TextFormats.MEDIA)) throw IllegalArgumentException("„${f.name}“ ist zu groß (max. 25 MB).")
        if (plainText(f)) {
            val decoded = TextCodec.read(f)
            return numbered(rel(root, f), decoded.text, args, TextFormats.language(f.name), decoded.style)
        }
        if (f.extension.lowercase() in TextFormats.MEDIA) {
            return "[Audio-/Videodatei „${rel(root, f)}“, ${size(f.length())}]\n" +
                (if (LocalText.SUPPORTED) "Mit transcribe_local_file lässt sie sich in Text umwandeln (Spracherkennung der App)." else "")
        }
        return numbered(rel(root, f), LocalText.read(f), args, TextFormats.language(f.name))
    }

    private fun list(root: File, dir: File, recursive: Boolean): String {
        if (dir.isFile && dir.extension.lowercase() == "zip") return zipListing(root, dir)
        if (!dir.isDirectory) throw IllegalArgumentException("„${rel(root, dir)}“ ist kein Ordner.")
        val entries = (if (recursive) dir.walkTopDown().filter { it != dir } else dir.listFiles().orEmpty().asSequence())
            .filterNot { hidden(root, it) }.take(MAX_LIST + 1).toList()
        val df = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
        return buildString {
            append("Inhalt von ${rel(root, dir).ifEmpty { root.name }}:\n")
            if (entries.isEmpty()) append("(leer)\n")
            entries.take(MAX_LIST).sortedWith(compareBy<File>({ !it.isDirectory }, { rel(root, it).lowercase() })).forEach { f ->
                if (f.isDirectory) append("[Ordner] ").append(rel(root, f)).append("/\n")
                else append(rel(root, f)).append(" · ").append(size(f.length())).append(" · ").append(df.format(Date(f.lastModified()))).append('\n')
            }
            if (entries.size > MAX_LIST) append("… weitere Einträge – Unterordner einzeln auflisten.\n")
        }
    }

    /** First bytes of [f], to tell text from binary files. */
    private fun head(f: File): ByteArray = runCatching {
        f.inputStream().use { input ->
            val b = ByteArray(4096)
            val n = input.read(b)
            if (n <= 0) ByteArray(0) else b.copyOf(n)
        }
    }.getOrDefault(ByteArray(0))

    /** Plain text shown as it is (code, configuration, Markdown, CSV, SVG …), so lines can be edited by number. */
    private fun plainText(f: File): Boolean {
        val ext = f.extension.lowercase()
        if (ext == "svg") return true
        if (ext in OFFICE || ext in NOT_TEXT || FormatReaders.handles(f.name) || ext in TextFormats.IMAGES || ext in TextFormats.MEDIA) return false
        return TextFormats.isText(f.name) || TextCodec.looksLikeText(head(f))
    }

    /** Text formats edit_local_file may change as text (also RTF, e-mails, calendars and notebooks as raw text). */
    private fun editable(f: File): Boolean {
        val ext = f.extension.lowercase()
        if (ext in setOf("rtf", "eml", "ics", "vcf", "ipynb", "svg")) return true
        return plainText(f)
    }

    /** Text of [f] for searching, null for binary files and files that are too large. */
    private suspend fun searchable(f: File): String? {
        val ext = f.extension.lowercase()
        if (plainText(f)) return if (f.length() > 5L * 1024 * 1024) null else runCatching { TextCodec.read(f).text }.getOrNull()
        val limit = if (ext == "pdf") 40L * 1024 * 1024 else 15L * 1024 * 1024
        if (ext !in SEARCH_DOCUMENTS || f.length() > limit) return null
        val key = f.absolutePath
        val stamp = f.lastModified() * 31 + f.length()
        synchronized(searchCache) { searchCache[key]?.let { (s, text) -> if (s == stamp) return text } }
        val text = LocalText.searchText(f) ?: ""
        synchronized(searchCache) {
            searchCache[key] = stamp to text
            var total = searchCache.values.sumOf { it.second.length.toLong() }
            val it = searchCache.entries.iterator()
            while (total > 30_000_000 && it.hasNext()) { total -= it.next().value.second.length; it.remove() }
        }
        return text
    }

    private suspend fun search(root: File, dir: File, args: JsonObject): String {
        val query = args.s("query").orEmpty()
        if (query.isBlank()) throw IllegalArgumentException("query fehlt.")
        val pattern = if (args.s("regex") == "true") {
            try {
                Regex(query, RegexOption.IGNORE_CASE)
            } catch (e: Exception) {
                throw IllegalArgumentException("Ungültiger regulärer Ausdruck: ${e.message?.lines()?.first()}")
            }
        } else null
        fun hit(s: String) = pattern?.containsMatchIn(s) ?: s.contains(query, ignoreCase = true)
        val glob = args.s("file_pattern")?.trim()?.takeIf { it.isNotEmpty() }
        val globRegex = glob?.let(::globToRegex)
        val context = (args.int("context") ?: 0).coerceIn(0, 10)
        val out = StringBuilder()
        var matches = 0
        var files = 0
        var truncated = false
        for (f in dir.walkTopDown().onEnter { it == dir || !hidden(root, it) }.filter { it.isFile && !hidden(root, it) }) {
            if (matches >= MAX_HITS || out.length > 40_000) { truncated = true; break }
            val relPath = rel(root, f)
            if (globRegex != null && !globRegex.matches(if ('/' in glob) relPath else f.name)) continue
            val nameHit = hit(f.name)
            val lines = searchable(f)?.lines().orEmpty()
            val found = lines.indices.filter { hit(lines[it]) }
            if (!nameHit && found.isEmpty()) continue
            files++
            if (nameHit && found.isEmpty()) { out.append(relPath).append(" (Dateiname)\n"); matches++; continue }
            if (context == 0) {
                if (nameHit) out.append(relPath).append(" (Dateiname)\n")
                for (i in found) {
                    if (matches >= MAX_HITS) break
                    out.append(relPath).append(':').append(i + 1).append(": ").append(lines[i].trim().take(200)).append('\n')
                    matches++
                }
            } else {
                out.append("== ").append(relPath).append(if (nameHit) " (auch im Dateinamen)" else "").append(" ==\n")
                var last = -1
                for (i in found) {
                    if (matches >= MAX_HITS) break
                    val from = maxOf(i - context, last + 1)
                    if (last >= 0 && from > last + 1) out.append("--\n")
                    for (k in from..minOf(lines.lastIndex, i + context)) {
                        if (k <= last) continue
                        out.append(k + 1).append(if (hit(lines[k])) ": " else "- ").append(lines[k].take(200)).append('\n')
                        last = k
                    }
                    matches++
                }
            }
        }
        if (out.isEmpty()) return "Keine Treffer für „$query“" + (glob?.let { " in Dateien „$it“" } ?: "") + "."
        return "$matches Treffer in $files ${if (files == 1) "Datei" else "Dateien"}" + (if (truncated) " (gekürzt – Suche eingrenzen)" else "") + ":\n" + out
    }

    private fun hidden(root: File, f: File) = rel(root, f).split('/').any { it.startsWith(".") || it == "node_modules" }

    companion object {
        const val TRASH = ".adeu-papierkorb"
        /** Name of the project folder for the model (with file access) … */
        const val PROJECT = "Projektordner"
        /** … and of its "Ausgabe" when the model may not read the rest. */
        const val PROJECT_OUTPUT = "Ausgabe des Projekts"
        private const val PROJECT_ID = "project-"
        private const val MAX_LIST = 500
        private const val MAX_OVERVIEW = 60
        private val DOCUMENTS = setOf("docx", "pdf", "pptx", "xlsx", "html")
        private const val MAX_HITS = 100
        private val OFFICE = setOf("docx", "docm", "pptx", "pptm", "xlsx", "xlsm")
        /** Binary files that are never read as text. */
        private val NOT_TEXT = setOf(
            "pdf", "zip", "7z", "rar", "gz", "tgz", "tar", "exe", "dll", "msi", "jar", "class", "so", "bin", "iso", "db", "sqlite",
            "mdb", "accdb", "pst", "ost", "woff", "woff2", "ttf", "otf", "eot", "pyc", "o", "obj", "lib",
        )
        /** Documents search_local_files looks into (besides plain text). */
        private val SEARCH_DOCUMENTS = setOf("docx", "pptx", "xlsx", "xlsm", "pdf", "odt", "ods", "odp", "rtf", "eml", "msg", "ipynb", "epub", "doc", "xls", "ppt", "ics", "vcf")
        /** Formats the Uni server's speech recognition accepts. */
        private val SERVER_AUDIO = setOf("mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac", "webm", "mp4", "mpeg", "mpg")

        /** Text of searched documents by path, with modification time and size (PDFs take a while). */
        private val searchCache = object : LinkedHashMap<String, Pair<Long, String>>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, String>>?) = size > 300
        }

        private fun mimeOf(ext: String) = when (ext) {
            "mp3", "mpeg", "mpg" -> "audio/mpeg"
            "m4a", "aac" -> "audio/mp4"
            "wav" -> "audio/wav"
            "ogg", "oga", "opus" -> "audio/ogg"
            "flac" -> "audio/flac"
            "webm" -> "audio/webm"
            "mp4" -> "video/mp4"
            else -> "application/octet-stream"
        }

        /** "*.py", "*.{md,txt}", "src/**/*.kt" as regular expression (case-insensitive). */
        fun globToRegex(glob: String): Regex {
            val sb = StringBuilder()
            var i = 0
            var brace = 0
            while (i < glob.length) {
                val ch = glob[i]
                when {
                    ch == '*' && glob.getOrNull(i + 1) == '*' -> {
                        if (glob.getOrNull(i + 2) == '/') { sb.append("(?:.*/)?"); i += 2 } else { sb.append(".*"); i++ }
                    }
                    ch == '*' -> sb.append("[^/]*")
                    ch == '?' -> sb.append("[^/]")
                    ch == '{' -> { sb.append("(?:"); brace++ }
                    ch == '}' && brace > 0 -> { sb.append(')'); brace-- }
                    ch == ',' && brace > 0 -> sb.append('|')
                    ch == '\\' -> sb.append('/')
                    ch.isLetterOrDigit() || ch == '/' || ch == '_' || ch == '-' || ch == ' ' -> sb.append(ch)
                    else -> sb.append('\\').append(ch)
                }
                i++
            }
            return Regex(sb.toString(), RegexOption.IGNORE_CASE)
        }

        /** [path] inside [root]; anything that leads outside (.., absolute paths, links) is refused. */
        fun resolve(root: File, path: String): File {
            val clean = path.trim().replace('\\', '/').trimStart('/')
            val f = (if (clean.isEmpty() || clean == ".") root else File(root, clean)).canonicalFile
            if (f != root && !f.path.startsWith(root.path + File.separator)) throw IllegalArgumentException("„$path“ liegt außerhalb des freigegebenen Ordners.")
            return f
        }

        fun rel(root: File, f: File): String = f.canonicalFile.path.removePrefix(root.path).trimStart(File.separatorChar).replace(File.separatorChar, '/')

        private fun existing(root: File, path: String): File = resolve(root, path).also { if (!it.exists()) throw IllegalArgumentException("„$path“ gibt es nicht. Mit list_local_files nachsehen.") }

        private fun size(b: Long) = when {
            b < 1024 -> "$b B"
            b < 1024 * 1024 -> "${b / 1024} KB"
            else -> String.format(Locale.GERMANY, "%.1f MB", b / 1048576.0)
        }
    }
}
