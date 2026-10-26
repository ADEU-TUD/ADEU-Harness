package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.Folder
import java.io.File
import java.io.IOException
import java.nio.file.AccessDeniedException

/**
 * The project folder of a project (Projekt › Bearbeiten › Projektordner): everything the model creates in the project's
 * chats – also the letter of the form – lands in its subfolder [OUTPUT], so it is in Google Drive, OneDrive, Nextcloud
 * or on a network drive instead of only in the app's data folder. With [Folder.workDirAccess] the model may also see
 * and read the user's files in the folder. The app writes as the logged-in user – what the user may write in Explorer,
 * the app may write too, also on PCs with IT restrictions (they block running programs, not saving files). The
 * exception is Microsoft Defender's "Controlled folder access", which [problem] explains.
 */
object WorkFolder {
    /** Subfolder for everything the app creates; the user's own files stay untouched. */
    const val OUTPUT = "Ausgabe"

    fun of(c: AppContainer, folderId: String?): Pair<Folder, File>? {
        val folder = folderId?.let { c.folders.get(it) } ?: return null
        return folder.workDir.takeIf { it.isNotBlank() }?.let { folder to File(it) }
    }

    fun output(dir: File) = File(dir, OUTPUT)

    /** Tries to write a small file; null if the folder works, otherwise what is wrong in plain words. */
    fun problem(dir: File): String? {
        if (!dir.exists()) return "Der Ordner „${dir.path}“ ist nicht erreichbar – ist das Netzlaufwerk verbunden bzw. Google Drive/OneDrive gestartet?"
        if (!dir.isDirectory) return "„${dir.path}“ ist kein Ordner."
        val probe = File(dir, ".adeu-schreibtest-${System.nanoTime()}.tmp")
        return try {
            probe.writeText("ok")
            null
        } catch (e: Exception) {
            explain(dir, e)
        } finally {
            probe.delete()
        }
    }

    fun explain(dir: File, e: Throwable): String = when {
        e is AccessDeniedException || e.message.orEmpty().contains("Zugriff verweigert", true) || e.message.orEmpty().contains("Access is denied", true) ->
            "Windows verweigert das Schreiben in „${dir.path}“. Auf Dienst-PCs liegt das meist am „Überwachten Ordnerzugriff“ von " +
                "Microsoft Defender (schützt Dokumente, Desktop und Bilder vor nicht freigegebenen Programmen) oder an fehlenden Rechten " +
                "auf dem Netzlaufwerk. Abhilfe: einen Ordner außerhalb dieser geschützten Ordner wählen (z. B. Google Drive „G:\\“, " +
                "OneDrive oder ein Netzlaufwerk) oder die IT bitten, „ADEU LLM.exe“ für den überwachten Ordnerzugriff zuzulassen."
        e is IOException -> "Schreiben in „${dir.path}“ nicht möglich: ${e.message ?: e.javaClass.simpleName}."
        else -> "Schreiben in „${dir.path}“ nicht möglich: ${e.message ?: e.javaClass.simpleName}."
    }

    /**
     * Copies [file] (made by a tool in a chat of [folderId]) into the project's [OUTPUT] folder. Returns the copy and
     * a note for the model, or null if the project has no folder or the file is not one the app made.
     */
    fun deliver(c: AppContainer, folderId: String?, file: File): Pair<File?, String>? {
        val (folder, dir) = of(c, folderId) ?: return null
        if (!file.isFile) return null
        // Only what the app made in its own documents folder; files the agent wrote into a shared folder stay where they are.
        if (!inside(file, c.documentTools.folder)) return null
        val out = output(dir)
        return try {
            if (!dir.isDirectory) throw IOException("Ordner nicht erreichbar (Netzlaufwerk getrennt oder Google Drive/OneDrive nicht gestartet?)")
            out.mkdirs()
            val target = target(c, out, file.name)
            file.copyTo(target, overwrite = true)
            delivered(c, target)
            target to "Die Datei liegt im Projektordner von „${folder.name}“ unter $OUTPUT: ${target.absolutePath}. Erwähne das kurz."
        } catch (e: Exception) {
            null to "Hinweis: In den Projektordner von „${folder.name}“ ließ sich die Datei nicht speichern – ${explain(out, e)} " +
                "Sag das dem Nutzer kurz; die Datei ist in der App trotzdem vorhanden."
        }
    }

    /**
     * [output] of a tool in a chat of [folderId] with its file in the project folder: the chat then shows the copy (open,
     * show in folder), and the document in the editor points to it. Unchanged without a project folder.
     */
    fun place(c: AppContainer, folderId: String?, output: ToolOutput): ToolOutput {
        val made = output.file?.let(::File) ?: return output
        val (copy, note) = deliver(c, folderId, made) ?: return output
        if (copy == null) return output.copy(text = output.text + "\n" + note)
        c.docs.forFile(made.absolutePath)?.let { doc -> c.docs.save(doc.copy(file = copy.absolutePath)) }
        return output.copy(text = output.text + "\n" + note, file = copy.absolutePath)
    }

    /** True if [file] was written by the app into a project folder (so it may be replaced without asking). */
    fun madeByApp(c: AppContainer, file: File): Boolean = file.absolutePath in written(c)

    private fun inside(file: File, dir: File): Boolean = runCatching {
        file.canonicalPath.startsWith(dir.canonicalPath + File.separator)
    }.getOrDefault(false)

    /**
     * Same name as in the app; a file with that name the app did not write itself (the user's own) is never overwritten –
     * then "Name (2).pdf".
     */
    private fun target(c: AppContainer, dir: File, name: String): File {
        val own = written(c)
        var f = File(dir, name)
        var n = 2
        val base = name.substringBeforeLast('.')
        val ext = if ('.' in name) "." + name.substringAfterLast('.') else ""
        while (f.exists() && f.absolutePath !in own) f = File(dir, "$base ($n)$ext").also { n++ }
        return f
    }

    private fun log(c: AppContainer) = File(c.appContext.filesDir, "work_files.txt")

    @Synchronized
    private fun written(c: AppContainer): Set<String> = runCatching { log(c).readLines().toSet() }.getOrDefault(emptySet())

    @Synchronized
    fun delivered(c: AppContainer, f: File) {
        val all = (written(c) + f.absolutePath).toList().takeLast(2000)
        runCatching { log(c).writeText(all.joinToString("\n")) }
    }
}
