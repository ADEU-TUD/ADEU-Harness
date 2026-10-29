package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.Folder
import io.github.thepytales.llmhub.files.ProjectTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Android: the model reads the project folder chosen in the system dialog, if the project allows it
 * ([Folder.workDirAccess]). Windows reaches project folders as plain paths with [LocalFolderTools].
 */
class ProjectFolderTools(private val c: AppContainer) : ToolProvider {
    override val defs = listOf(
        ToolDef("list_project_files", "Listet die Dateien im Projektordner auf (mit Unterordnern, höchstens 300, mit Größe).", emptyList()),
        ToolDef(
            "read_project_file",
            "Liest eine Datei aus dem Projektordner: Text, Markdown, CSV, PDF, Word, PowerPoint, Excel, OpenDocument …",
            listOf(Param("path", "Pfad der Datei wie in list_project_files")),
        ),
    )

    fun active(folderId: String?) = folder(folderId) != null

    private fun folder(folderId: String?): Folder? =
        folderId?.let { c.folders.get(it) }?.takeIf { ProjectTree.SUPPORTED && ProjectTree.isTree(it.workDir) && it.workDirAccess }

    override suspend fun run(name: String, args: JsonObject, ctx: RepoContext): ToolOutput {
        val f = folder(ctx.folderId) ?: return ToolOutput("Dieses Projekt hat keinen Projektordner, den du lesen darfst.")
        val files = withContext(Dispatchers.IO) { ProjectTree.list(c.appContext, f.workDir) }
        return when (name) {
            "list_project_files" ->
                ToolOutput(if (files.isEmpty()) "Der Projektordner ist leer." else files.joinToString("\n") { "${it.path} (${(it.size + 1023) / 1024} KB)" })
            "read_project_file" -> {
                val path = args["path"]?.jsonPrimitive?.contentOrNull.orEmpty().trim().trimStart('/')
                val e = files.firstOrNull { it.path == path } ?: files.firstOrNull { it.path.substringAfterLast('/') == path.substringAfterLast('/') }
                    ?: return ToolOutput("„$path“ gibt es im Projektordner nicht – sieh mit list_project_files nach.")
                val r = ProjectTree.read(c.appContext, f.workDir, e)
                ToolOutput(
                    r.file?.let { "Inhalt von „${e.path}“${if (it.truncated) " (gekürzt)" else ""}:\n${it.text}" }
                        ?: r.error ?: "„${e.path}“ enthält keinen lesbaren Text.",
                )
            }
            else -> ToolOutput("Unbekanntes Werkzeug: $name")
        }
    }

    override fun parallelSafe(name: String) = true

    fun prompt(folderId: String?): String? {
        val f = folder(folderId) ?: return null
        return "Das Projekt „${f.name}“ hat einen Projektordner (${ProjectTree.label(f.workDir)}). Beziehe ihn ein: Bevor du für das " +
            "Projekt schreibst, sieh mit list_project_files nach, welche Dateien zum Thema passen, und lies sie mit read_project_file. " +
            "Dokumente aus create_document und edit_document legt die App automatisch unter „${WorkFolder.OUTPUT}“ im Projektordner ab."
    }
}
