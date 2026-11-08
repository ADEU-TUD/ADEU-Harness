package io.github.thepytales.llmhub.ui

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.desktop.ShellArgs
import io.github.thepytales.llmhub.docs.OfficeImport
import io.github.thepytales.llmhub.files.AttachmentReader
import io.github.thepytales.llmhub.files.TextFormats
import io.github.thepytales.llmhub.video.VideoLora
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Files Lora opens in its own editors (the rest goes to a chat). */
private val EDITABLE = setOf("docx", "pptx", "xlsx", "xlsm", "md", "markdown", "odt", "ods", "odp", "txt", "csv")

/**
 * „Mit Lora öffnen“ / „Lora fragen“ / „Frag diesen Ordner“ from the Explorer (AP-59): a PDF opens in the
 * PDF-Werkzeugkasten, Office files and Markdown in the editor (a file in the Lora-Ordner as itself, others as a linked
 * copy), videos as a project in the video editor; everything else – and „Lora fragen“ – becomes a new chat with the file
 * attached. A folder becomes a chat in the Quellenmodus over its files (AP-58).
 */
suspend fun handleShellRequest(c: AppContainer, r: ShellArgs.Request) {
    val f = r.file
    if (!f.exists()) { Notices.error(tr("„{0}“ gibt es nicht (mehr).", f.name)); return }
    when {
        r.kind == ShellArgs.Kind.ASK_FOLDER || f.isDirectory -> askFolder(c, if (f.isDirectory) f else f.parentFile)
        r.kind == ShellArgs.Kind.ASK -> askFile(c, f)
        else -> openFile(c, f)
    }
}

/** Where a file goes for „Mit Lora öffnen“: "pdf", "doc", "video" or "chat". */
internal fun shellTarget(f: File): String {
    val ext = f.extension.lowercase()
    return when {
        ext == "pdf" -> "pdf"
        ext in EDITABLE -> "doc"
        ext in TextFormats.MEDIA && ext !in setOf("mp3", "wav", "m4a", "ogg", "opus", "flac", "aac", "wma") -> "video"
        else -> "chat"
    }
}

private suspend fun openFile(c: AppContainer, f: File) {
    when (shellTarget(f)) {
        "pdf" -> PdfHandoff.open(f.absolutePath, 1, f.name)
        "doc" -> {
            val doc = withContext(Dispatchers.IO) {
                runCatching {
                    if (c.drive.inside(f)) {
                        val imported = OfficeImport.read(f.name, f.readBytes()) ?: return@runCatching null
                        c.docs.openReal(f, imported).also { d -> DocsHandoff.saveMedia(c, d, imported) }
                    } else DocsHandoff.importFile(c, f)
                }.getOrNull()
            }
            if (doc != null) DocsHandoff.request.value = doc.id else askFile(c, f)
        }
        "video" -> {
            val p = withContext(Dispatchers.IO) { projectFrom(listOf(f)) }
            if (p == null) { askFile(c, f); return }
            val saved = VideoHub.store.save(p)
            VideoLora.lastId = saved.id
            VideoLora.openRequest.value = saved.id
            Navigation.openRoute.value = "video"
        }
        else -> askFile(c, f)
    }
}

/** A new chat with [f] in the input field (read on the device; pictures go to the picture model). */
private suspend fun askFile(c: AppContainer, f: File) {
    val read = AttachmentReader.read(f)
    read.error?.let { Notices.error(it); return }
    ChatHandoff.pending.value = ChatHandoff.Content("", listOfNotNull(read.file), images = read.images)
    ChatHandoff.open.value = ""
}

private suspend fun askFolder(c: AppContainer, dir: File) {
    Notices.info(tr("Lora liest den Ordner „{0}“ …", dir.name))
    val files = withContext(Dispatchers.IO) { runCatching { io.github.thepytales.llmhub.agent.FolderAsk.prepare(c, dir) { _, _ -> } }.getOrDefault(emptyList()) }
    if (files.isEmpty()) { Notices.info(tr("Im Ordner „{0}“ gibt es keine lesbaren Dateien.", dir.name)); return }
    ChatHandoff.pending.value = ChatHandoff.Content(
        "", emptyList(), mode = io.github.thepytales.llmhub.agent.ChatModes.SOURCES.id,
        instructions = io.github.thepytales.llmhub.agent.FolderAsk.instructions(dir, files),
        handoff = tr("Frag diesen Ordner: {0} ({1} Dateien)", dir.name, files.size),
    )
    ChatHandoff.open.value = ""
}
