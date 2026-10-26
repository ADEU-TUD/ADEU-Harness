package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.Folder
import java.io.File
import java.io.IOException
import java.nio.file.AccessDeniedException

/**
 * The working folder of a project (Projekt › Anweisungen & Dateien › Arbeitsordner): documents the model creates in
 * the project's chats are copied there under their own name, so they land in Google Drive, OneDrive, Nextcloud or on
 * a network drive instead of only in the app's data folder. The app writes as the logged-in user – what the user may
 * write in Explorer, the app may write too, also on PCs with IT restrictions (they block running programs, not saving
 * files). The exception is Microsoft Defender's "Controlled folder access", which [problem] explains.
 */
object WorkFolder {
    fun of(c: AppContainer, folderId: String?): Pair<Folder, File>? {
        val folder = folderId?.let { c.folders.get(it) } ?: return null
        return folder.workDir.takeIf { it.isNotBlank() }?.let { folder to File(it) }
    }

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
     * Copies [file] (made by a tool in a chat of [folderId]) into the project's working folder. Returns the copy and
     * a note for the model, or null if the project has no working folder or the file is already in it.
     */
    fun deliver(c: AppContainer, folderId: String?, file: File): Pair<File?, String>? {
        val (folder, dir) = of(c, folderId) ?: return null
        if (!file.isFile) return null
        // Only what the app made in its own documents folder; files the agent wrote into a shared folder stay where they are.
        if (!inside(file, c.documentTools.folder)) return null
        return try {
            if (!dir.isDirectory) throw IOException("Ordner nicht erreichbar (Netzlaufwerk getrennt oder Google Drive/OneDrive nicht gestartet?)")
            val target = target(c, dir, file.name)
            file.copyTo(target, overwrite = true)
            delivered(c, target)
            target to "Die Datei liegt außerdem im Arbeitsordner des Projekts „${folder.name}“: ${target.absolutePath}. Erwähne das kurz."
        } catch (e: Exception) {
            null to "Hinweis: In den Arbeitsordner des Projekts „${folder.name}“ ließ sich die Datei nicht speichern – ${explain(dir, e)} " +
                "Sag das dem Nutzer kurz; die Datei ist in der App trotzdem vorhanden."
        }
    }

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
    private fun delivered(c: AppContainer, f: File) {
        val all = (written(c) + f.absolutePath).toList().takeLast(2000)
        runCatching { log(c).writeText(all.joinToString("\n")) }
    }
}
