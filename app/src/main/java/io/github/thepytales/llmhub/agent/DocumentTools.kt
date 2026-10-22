package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.docs.DocFormat
import io.github.thepytales.llmhub.docs.DocumentWriter
import io.github.thepytales.llmhub.net.s
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.File

/** Lets the model create documents (PDF, Word, …) that the user can open, share or save. */
class DocumentTools(filesDir: File) : ToolProvider {
    private val folder = File(filesDir, "documents")

    override val defs = listOf(
        ToolDef(
            "create_document",
            "Erstellt eine Datei (PDF, Word, HTML, Markdown oder Text) aus Markdown-Inhalt. Der Nutzer kann sie direkt " +
                "in der App öffnen, teilen oder speichern. Unterstützt Überschriften, Listen, Tabellen, Codeblöcke, " +
                "**fett**, *kursiv* und Links.",
            listOf(
                Param("title", "Titel des Dokuments (wird auch als Dateiname verwendet)"),
                Param("content", "Vollständiger Inhalt in Markdown"),
                Param("format", "pdf (Standard), docx, html, md oder txt", required = false),
                Param("file_name", "Optionaler Dateiname ohne Endung", required = false),
            ),
        ),
    )

    override suspend fun run(name: String, args: JsonObject, ctx: RepoContext): ToolOutput {
        if (name != "create_document") return ToolOutput("FEHLER: Unbekanntes Werkzeug '$name'.")
        val title = args.s("title")?.trim().orEmpty()
        val content = args.req("content")
        val format = DocFormat.parse(args.s("format"))
        val doc = withContext(Dispatchers.IO) {
            DocumentWriter.create(folder, title, content, format, args.s("file_name"))
        }
        val size = (doc.file.length() + 1023) / 1024
        val pages = doc.pages?.let { ", $it ${if (it == 1) "Seite" else "Seiten"}" }.orEmpty()
        return ToolOutput(
            "Dokument erstellt: ${doc.file.name} (${format.label}$pages, $size KB). " +
                "Es wird dem Nutzer im Chat mit Knöpfen zum Öffnen, Teilen und Speichern angezeigt – nenne keinen Dateipfad.",
            file = doc.file.absolutePath,
        )
    }

    companion object {
        const val PROMPT = "Mit dem Werkzeug create_document kannst du PDF-, Word-, HTML-, Markdown- oder Textdateien erstellen. " +
            "Nutze es, wenn der Nutzer ein Dokument, eine Datei, einen Bericht zum Herunterladen o. ä. möchte."
    }
}
