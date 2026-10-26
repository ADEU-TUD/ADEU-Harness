package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.LocalFolder
import io.github.thepytales.llmhub.files.LocalText
import io.github.thepytales.llmhub.net.s
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lets the agent work in folders on this PC the user shared (Einstellungen › Lokale Ordner): list, read (also Word,
 * PowerPoint, Excel, PDF), search, write text files and documents, edit, move and delete. Nothing outside the shared
 * folders can be reached; deleted files go to ".adeu-papierkorb" inside the folder.
 */
class LocalFolderTools(private val c: AppContainer) : ToolProvider {
    private val folderParam = Param("folder", "Name des freigegebenen Ordners (Standard: der einzige bzw. erste)", required = false)

    override val defs = listOf(
        ToolDef(
            "list_local_files", "Listet Dateien und Unterordner in einem freigegebenen Ordner auf diesem PC (mit Größe und Änderungsdatum).",
            listOf(
                folderParam,
                Param("path", "Unterordner relativ zum freigegebenen Ordner (Standard: oberste Ebene)", required = false),
                Param("recursive", "true: auch alle Unterordner (höchstens 500 Einträge)", required = false, type = "boolean"),
            ),
        ),
        ToolDef(
            "read_local_file", "Liest eine Datei aus einem freigegebenen Ordner: Text, Code, Markdown, CSV, Word, PowerPoint, Excel (als Markdown) und PDF. Mit Zeilennummern.",
            listOf(
                folderParam, Param("path", "Dateipfad relativ zum freigegebenen Ordner"),
                Param("start_line", "Erste Zeile (optional)", required = false, type = "integer"),
                Param("end_line", "Letzte Zeile (optional)", required = false, type = "integer"),
            ),
        ),
        ToolDef(
            "search_local_files", "Sucht in Dateinamen und im Inhalt der Textdateien eines freigegebenen Ordners (ohne Groß-/Kleinschreibung).",
            listOf(folderParam, Param("query", "Suchbegriff"), Param("path", "Nur in diesem Unterordner (optional)", required = false)),
        ),
        ToolDef(
            "write_local_file",
            "Legt eine Datei im freigegebenen Ordner an oder überschreibt sie. Endet path auf .docx, .pdf, .pptx, .xlsx oder .html, " +
                "ist content Markdown und die App setzt daraus das Dokument (style wie bei create_document, z.B. tud). Sonst wird content wörtlich gespeichert.",
            listOf(
                folderParam, Param("path", "Dateipfad relativ zum freigegebenen Ordner, z.B. „Berichte/Protokoll.docx“"),
                Param("content", "Vollständiger Inhalt (Text bzw. Markdown)"),
                Param("style", "Nur für Dokumente: modern, kraeftig, elegant, klassisch, schlicht oder tud", required = false),
            ),
            writes = true,
        ),
        ToolDef(
            "edit_local_file", "Ersetzt einen exakt passenden, eindeutigen Abschnitt in einer Textdatei des freigegebenen Ordners.",
            listOf(folderParam, Param("path", "Dateipfad"), Param("old_text", "Exakter bisheriger Text (genau einmal vorhanden)"), Param("new_text", "Neuer Text")),
            writes = true,
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

    override fun risk(name: String, args: JsonObject) = if (defs.firstOrNull { it.name == name }?.writes == true) Risk.EDIT else null

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
        val folder = folder(args.s("folder"), ctx.folderId)
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
    private fun available(folderId: String?): List<LocalFolder> {
        val project = WorkFolder.of(c, folderId)?.let { (f, dir) ->
            if (f.workDirAccess) LocalFolder(id = PROJECT_ID + f.id, name = PROJECT, path = dir.path)
            else LocalFolder(id = PROJECT_ID + f.id, name = PROJECT_OUTPUT, path = WorkFolder.output(dir).apply { mkdirs() }.path)
        }
        return listOfNotNull(project) + c.localFolders.current.filter { project == null || File(it.path).absoluteFile != File(project.path).absoluteFile }
    }

    fun prompt(folderId: String? = null): String = buildString {
        append("Du kannst in Ordnern auf diesem PC arbeiten: mit list_local_files, read_local_file und search_local_files ")
        append("lesen, mit write_local_file, edit_local_file, move_local_file, delete_local_file und copy_document_to_folder schreiben. ")
        append("Verschaffe dir zuerst einen Überblick, bevor du etwas änderst, und nenne am Ende, welche Dateien du angelegt oder geändert hast. ")
        append("Ordner:\n")
        available(folderId).forEach { f -> append("- „${f.name}“ (${f.path})").append(if (f.write) "" else " – nur lesen").append('\n') }
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
        val folder = folder(args.s("folder"), ctx.folderId)
        val root = File(folder.path).canonicalFile
        if (!root.isDirectory) throw IllegalArgumentException("Der Ordner „${folder.name}“ (${folder.path}) ist nicht erreichbar.")
        if (defs.first { it.name == name }.writes && !folder.write) throw IllegalArgumentException("Der Ordner „${folder.name}“ ist nur zum Lesen freigegeben.")
        when (name) {
            "list_local_files" -> ToolOutput(list(root, resolve(root, args.s("path").orEmpty()), args.s("recursive") == "true"))
            "read_local_file" -> ToolOutput(read(root, args))
            "search_local_files" -> ToolOutput(search(root, resolve(root, args.s("path").orEmpty()), args.s("query").orEmpty()))
            "write_local_file" -> write(root, args, ctx).also { out ->
                val target = resolve(root, args.s("path").orEmpty())
                if (folder.id.startsWith(PROJECT_ID) && out.text.startsWith("Angelegt")) WorkFolder.delivered(c, target)
            }
            "edit_local_file" -> ToolOutput(edit(root, args))
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

    private fun folder(name: String?, folderId: String?): LocalFolder {
        val all = available(folderId)
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
            file.copyTo(target, overwrite = true)
            return ToolOutput("${if (existed) "Überschrieben" else "Angelegt"}: ${rel(root, target)} (${target.absolutePath})", file = target.absolutePath)
        }
        target.writeText(content)
        return ToolOutput("${if (existed) "Überschrieben" else "Angelegt"}: ${rel(root, target)} (${content.lines().size} Zeilen)")
    }

    private fun edit(root: File, args: JsonObject): String {
        val target = existing(root, args.s("path").orEmpty())
        if (target.extension.lowercase() in BINARY) throw IllegalArgumentException("„${target.name}“ ist keine Textdatei – mit write_local_file neu schreiben.")
        val text = target.readText()
        val old = args.s("old_text").orEmpty()
        val count = if (old.isEmpty()) 0 else generateSequence(text.indexOf(old)) { i -> text.indexOf(old, i + 1).takeIf { it >= 0 } }.takeWhile { it >= 0 }.take(2).count()
        when {
            count == 0 -> throw IllegalArgumentException("old_text kommt in „${target.name}“ nicht vor. Lies die Datei mit read_local_file.")
            count > 1 -> throw IllegalArgumentException("old_text kommt ${count}-mal vor – mehr Kontext angeben.")
        }
        target.writeText(text.replace(old, args.s("new_text").orEmpty()))
        return "Geändert: ${rel(root, target)}"
    }

    private suspend fun read(root: File, args: JsonObject): String {
        val f = existing(root, args.s("path").orEmpty())
        if (f.isDirectory) return list(root, f, false)
        if (f.length() > 25L * 1024 * 1024) throw IllegalArgumentException("„${f.name}“ ist zu groß (max. 25 MB).")
        val lines = LocalText.read(f).lines()
        val from = (args.s("start_line")?.toIntOrNull() ?: 1).coerceAtLeast(1)
        val to = (args.s("end_line")?.toIntOrNull() ?: lines.size).coerceAtMost(lines.size)
        return buildString {
            append("${rel(root, f)} (${lines.size} Zeilen)\n")
            for (i in from..to) append(i).append(": ").append(lines[i - 1]).append('\n')
        }
    }

    private fun list(root: File, dir: File, recursive: Boolean): String {
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

    private suspend fun search(root: File, dir: File, query: String): String {
        if (query.isBlank()) throw IllegalArgumentException("query fehlt.")
        val hits = mutableListOf<String>()
        for (f in dir.walkTopDown().filter { it.isFile && !hidden(root, it) }) {
            if (hits.size >= 80) break
            if (f.name.contains(query, true)) hits += "${rel(root, f)} (Dateiname)"
            if (f.length() > 3L * 1024 * 1024) continue
            val text = if (f.extension.lowercase() in BINARY) {
                if (f.extension.lowercase() in setOf("docx", "pptx", "xlsx")) runCatching { LocalText.read(f) }.getOrNull() else null
            } else runCatching { f.readText() }.getOrNull()
            text?.lines()?.forEachIndexed { i, line ->
                if (hits.size < 80 && line.contains(query, true)) hits += "${rel(root, f)}:${i + 1}: ${line.trim().take(160)}"
            }
        }
        return if (hits.isEmpty()) "Keine Treffer für „$query“." else hits.joinToString("\n")
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
        private val BINARY = setOf("docx", "pptx", "xlsx", "xls", "doc", "ppt", "pdf", "zip", "png", "jpg", "jpeg", "gif", "mp3", "mp4", "wav", "exe", "dll", "odt", "ods", "odp")

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
