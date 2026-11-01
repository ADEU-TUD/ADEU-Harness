package io.github.thepytales.llmhub.docs

import android.content.Context
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.ModelTier
import io.github.thepytales.llmhub.data.ModelType
import io.github.thepytales.llmhub.data.Privacy
import io.github.thepytales.llmhub.net.ThinkingLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Base64

/**
 * Umwandeln (AP-15): Word/Excel/PowerPoint, pictures, HTML and text → PDF; PDF → Word, Markdown, text, Excel (its
 * tables) and pictures. Office files go through the installed Microsoft Office when there is one (exact layout), else
 * through the app's own readers (text, headings, lists, tables and pictures – simplified layout).
 */
object PdfConvert {
    /** How a file was converted, for the notice („mit Microsoft Office“). */
    data class Converted(val file: File, val how: String)

    /** Converts the file [name] ([input]) into a PDF in [dir]. */
    suspend fun toPdf(context: Context, input: File, name: String, dir: File): Converted = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val base = DocumentWriter.safeName(name.substringBeforeLast('.'))
        val out = File(dir, "$base-${System.nanoTime()}.pdf")
        when (ToPdfKind.of(name)) {
            ToPdfKind.PDF -> Converted(input.copyTo(out, overwrite = true), "")
            ToPdfKind.IMAGE -> {
                PdfExtras.imagesToPdf(context, listOf(name to input.readBytes()), out)
                Converted(out, "Bild als PDF-Seite")
            }
            ToPdfKind.OFFICE -> {
                if (PdfPlatform.officeToPdf(input, out)) Converted(out, "mit ${PdfPlatform.officeName}")
                else {
                    val bytes = input.readBytes()
                    val imported = OfficeImport.read(name, bytes)
                    val markdown = imported?.tabs?.joinToString("\n\n") { (title, md) -> if (imported.tabs.size > 1) "## $title\n\n$md" else md }
                        ?: io.github.thepytales.llmhub.files.FormatReaders.takeIf { it.handles(name) }?.text(name, bytes)
                        ?: throw IllegalArgumentException("„$name“ lässt sich ohne Microsoft Office nicht umwandeln.")
                    val made = DocumentWriter.create(context, dir, imported?.title ?: base, markdown, DocFormat.PDF, fileName = out.name)
                    Converted(made.file, "mit dem Leser der App (Layout vereinfacht)")
                }
            }
            ToPdfKind.HTML -> {
                val made = DocumentWriter.create(context, dir, base, input.readText(), DocFormat.PDF, fileName = out.name)
                Converted(made.file, "")
            }
            ToPdfKind.TEXT -> {
                val text = input.readText().removePrefix("﻿")
                val ext = name.substringAfterLast('.', "").lowercase()
                val markdown = when (ext) {
                    "md", "markdown" -> text
                    "csv" -> OfficeImport.read(name, text.toByteArray())?.tabs?.firstOrNull()?.second ?: text
                    // Plain text keeps its lines.
                    else -> text.lines().joinToString("  \n")
                }
                val made = DocumentWriter.create(context, dir, base, markdown, DocFormat.PDF, fileName = out.name)
                Converted(made.file, "")
            }
            ToPdfKind.UNSUPPORTED -> throw IllegalArgumentException("„$name“ lässt sich nicht in ein PDF umwandeln (Word, Excel, PowerPoint, Bilder, HTML und Text gehen).")
        }
    }

    /** The PDF as Markdown (see [PdfMarkdown]); empty for scans without text layer. */
    fun markdown(context: Context?, file: File): String {
        val (runs, sizes) = PdfTools.textRuns(context, file)
        return PdfMarkdown.build(runs, sizes)
    }

    fun tables(context: Context?, file: File): List<PdfTable> = PdfTables.detect(PdfTools.textRuns(context, file).first)

    /** Writes the PDF as [format] (Word, Markdown, text or – its tables – Excel) into [dir]. */
    suspend fun fromPdf(context: Context, file: File, title: String, format: DocFormat, dir: File): File = withContext(Dispatchers.IO) {
        val markdown = if (format == DocFormat.XLSX) {
            tables(context, file).takeIf { it.isNotEmpty() }?.let { PdfTables.markdown(it) }
                ?: throw IllegalArgumentException("Keine Tabellen erkannt. Bei Scans erst die Texterkennung laufen lassen – oder „Tabellen mit KI“ versuchen.")
        } else markdown(context, file).ifBlank { throw IllegalArgumentException("Das PDF hat keinen Text – bei einem Scan erst die Texterkennung laufen lassen.") }
        DocumentWriter.create(context, dir, title, markdown, format, fileName = DocumentWriter.safeName(title)).file
    }
}

/**
 * Texterkennung (OCR) for scans (AP-15): on Windows with the OCR built into Windows (nothing leaves the PC), otherwise –
 * after asking – with a vision model allowed for this privacy level. The text goes into the PDF as an invisible layer:
 * search, copy, „Frag das PDF“ and the chat find it afterwards.
 */
object PdfOcr {
    /** Pages (0-based) without real text: scans. */
    fun scanned(texts: List<String>): List<Int> = texts.indices.filter { i -> texts[i].count { it.isLetterOrDigit() } < 15 }

    /** A vision model this privacy level allows (the default model if it can see pictures); null if there is none. */
    fun visionModel(c: AppContainer): String? {
        val level = Privacy.level(c, null as String?)
        val candidates = listOf(c.settings.current.defaultModel) +
            ModelCatalog.known.filter { it.type == ModelType.CHAT && it.vision == true && it.tier != ModelTier.HIDDEN }.sortedBy { it.tier.ordinal }.map { it.id }
        return candidates.firstOrNull { ModelCatalog.info(it).vision == true && Privacy.allows(c, level, it) }
    }

    data class Result(val pages: List<OcrPage>, val failed: List<Int>, val engine: String)

    private const val PROMPT = "Du bist eine Texterkennung (OCR). Gib den vollständigen Text dieser gescannten Seite exakt so wieder, wie er " +
        "dasteht: Zeile für Zeile, in der Originalsprache, ohne Übersetzung, ohne Erklärungen, ohne Markdown und ohne Einleitung. " +
        "Tabellenzeilen mit Tabulator zwischen den Zellen. Unleserliches als [unleserlich]. Eine leere Seite: gib nichts aus."

    /**
     * Recognizes [pages] of [file]; [model] null = on the device ([PdfPlatform.localOcr]). [progress] gets done and
     * total pages.
     */
    suspend fun recognize(c: AppContainer, context: Context, file: File, pages: List<Int>, model: String?, work: File, progress: (Int, Int) -> Unit): Result = withContext(Dispatchers.IO) {
        val done = mutableListOf<OcrPage>()
        val failed = mutableListOf<Int>()
        work.mkdirs()
        if (model == null) {
            val engine = PdfPlatform.localOcr ?: throw IllegalStateException("Auf diesem Gerät gibt es keine eingebaute Texterkennung.")
            pages.chunked(4).forEach { chunk ->
                val images = PdfExtras.pageImages(context, file, chunk, 300, work)
                val results = engine.recognize(images)
                chunk.forEachIndexed { k, page ->
                    val r = results.getOrNull(k)
                    if (r == null) failed += page else done += r.copy(page = page)
                }
                images.forEach { it.delete() }
                progress(done.size + failed.size, pages.size)
            }
            Result(done, failed, engine.name)
        } else {
            pages.forEach { page ->
                val image = PdfExtras.pageImages(context, file, listOf(page), 150, work, jpeg = true).first()
                val url = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(image.readBytes())
                image.delete()
                val text = runCatching {
                    c.llm.streamChat(model, listOf(ChatMessage(role = "user", content = PROMPT, images = listOf(url))), tools = null, thinking = ThinkingLevel.OFF) {}.content
                }.getOrNull()
                if (text == null) failed += page else done += OcrPage.plain(page, text.replace('\t', ' '))
                progress(done.size + failed.size, pages.size)
            }
            Result(done, failed, ModelCatalog.info(model).displayName)
        }
    }
}
