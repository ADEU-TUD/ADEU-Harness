package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.NextcloudService
import io.github.thepytales.llmhub.net.WebDavClient
import io.github.thepytales.llmhub.net.s
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Nextcloud (AP-22): the agent looks into the Nextcloud of the user, reads files from it and saves results there.
 * Offered while Nextcloud is set up and the chat may use the network (not in the vault, not „Nur auf diesem Gerät“).
 */
class NextcloudTools(private val c: AppContainer) : ToolProvider {
    override val defs = listOf(
        ToolDef(
            LIST,
            "Listet Dateien und Ordner in der Nextcloud des Nutzers (TU-Cloudstore). Pfade beginnen mit „/“, z. B. „/“ oder „/Lehre/WiSe 2026“.",
            listOf(Param("path", "Ordner, Standard „/“", required = false)),
        ),
        ToolDef(
            READ,
            "Liest eine Datei aus der Nextcloud als Text (PDF mit Seitenzahlen, Word, PowerPoint, Excel, Text). Für Fragen zu Dateien, die dort liegen.",
            listOf(Param("path", "Pfad der Datei, z. B. „/Forschung/Interview 3.docx“")),
        ),
        ToolDef(
            SAVE,
            "Speichert in der Nextcloud: entweder eine Datei, die in diesem Chat erstellt oder angehängt wurde (file = Dateiname), " +
                "oder neuen Text als Datei (name + content, z. B. Markdown oder CSV). Ohne folder landet sie im Ergebnisordner. " +
                "Eine gleichnamige Datei bleibt erhalten, die neue bekommt „ (2)“.",
            listOf(
                Param("file", "Name einer in diesem Chat erstellten oder angehängten Datei", required = false),
                Param("name", "Dateiname für neuen Text, z. B. „Protokoll.md“", required = false),
                Param("content", "Inhalt für eine neue Textdatei", required = false),
                Param("folder", "Zielordner in der Nextcloud, z. B. „/Lehre“", required = false),
            ),
            writes = true,
        ),
    )

    override fun parallelSafe(name: String) = name != SAVE

    override fun risk(name: String, args: JsonObject): Risk? = if (name == SAVE) Risk.EDIT else null

    override suspend fun run(name: String, args: JsonObject, ctx: RepoContext): ToolOutput {
        val nc = c.nextcloud
        return when (name) {
            LIST -> {
                val path = WebDavClient.normalize(args.s("path")?.ifBlank { null } ?: "/")
                val entries = nc.list(path)
                if (entries.isEmpty()) ToolOutput("Der Ordner $path ist leer.")
                else ToolOutput("Inhalt von $path (${entries.size}):\n" + entries.take(300).joinToString("\n") { e ->
                    if (e.folder) "📁 ${e.path}/"
                    else "📄 ${e.path} · ${NextcloudService.size(e.size)}" + (if (e.modified > 0) " · geändert ${DATE.format(Date(e.modified))}" else "")
                } + if (entries.size > 300) "\n… und ${entries.size - 300} weitere" else "")
            }
            READ -> {
                val path = WebDavClient.normalize(args.req("path"))
                val r = nc.attach(path)
                val f = r.file ?: return ToolOutput("FEHLER: " + (r.error ?: r.note ?: "„$path“ enthält keinen lesbaren Text."))
                ToolOutput("Datei $path aus der Nextcloud" + (f.pages?.let { " ($it Seiten)" } ?: "") + ":\n\n" + f.text)
            }
            else -> {
                val folder = args.s("folder")?.ifBlank { null }?.let { WebDavClient.normalize(it) } ?: nc.resultFolder
                val fileName = args.s("file")?.trim()?.ifEmpty { null }
                val saved = if (fileName != null) {
                    val file = chatFile(ctx, fileName) ?: return ToolOutput("FEHLER: In diesem Chat gibt es keine Datei „$fileName“. Erstelle sie zuerst oder gib name und content an.")
                    nc.save(file, folder)
                } else {
                    val n = args.s("name")?.trim()?.ifEmpty { null } ?: return ToolOutput("FEHLER: Gib file (eine Datei des Chats) oder name und content an.")
                    nc.saveBytes(n, args.req("content").toByteArray(Charsets.UTF_8), folder)
                }
                ToolOutput("In der Nextcloud gespeichert: $saved")
            }
        }
    }

    /** A file created by the tools or attached in the chat [ctx], found by its name (or path). */
    private fun chatFile(ctx: RepoContext, name: String): File? {
        val wanted = name.substringAfterLast('/').substringAfterLast('\\')
        val created = ctx.chatId?.let { id -> c.chats.get(id) }?.messages.orEmpty().mapNotNull { it.file }.asReversed()
        val attached = ctx.files.mapNotNull { it.stored ?: it.path }
        return (created + attached).map { File(it) }.firstOrNull { f -> f.isFile && (f.path == name || f.name.equals(wanted, ignoreCase = true)) }
    }

    companion object {
        const val LIST = "nextcloud_list"
        const val READ = "nextcloud_read"
        const val SAVE = "nextcloud_save"
        private val DATE = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)

        const val PROMPT = "Die Nextcloud des Nutzers ist verbunden: nextcloud_list zeigt Ordner, nextcloud_read liest Dateien, " +
            "nextcloud_save speichert Ergebnisse dort (nur, wenn der Nutzer es möchte)."
    }
}
