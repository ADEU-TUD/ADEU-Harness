package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.ui.tr
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Eigene Modelle aktualisieren (AP-117): one version („Fassung“) of Lora Namensschutz or Lora Befehle, recognized on the
 * device by the SHA-256 of the unpacked file – not by its name and size, so a retrained model with the same name or the
 * same size is still told apart. Each version brings what the app needs to run it: threshold (and for Lora Befehle the
 * bound of the question with two buttons), labels and vocabulary as resources of the app.
 */
data class ModelVersion(
    /** Shown in the settings, e.g. „1.0.66 (Nachtraining 10.11.2026)“. */
    val name: String,
    /** File name on the device. */
    val file: String,
    val size: Long,
    val sha256: String,
    val threshold: Float,
    /** Lora Befehle: probability of the two likeliest commands from which Lora asks (AP-73); 0 for Lora Namensschutz. */
    val bound: Float = 0f,
    /** Resource with the labels (JSON list) and the WordPiece vocabulary of this version. */
    val labels: String,
    val vocab: String,
    /** Where the version is published (only the newest one needs it). */
    val download: ModelDownload? = null,
)

/**
 * A packed version (`.onnx.gz`) on its own branch of the repository at a fixed commit ([commit]). GitHub takes no file
 * over 100 MB: a larger one is split into [parts] below [OwnModels.PART_LIMIT], each with its own SHA-256, plus the
 * SHA-256 of the whole packed file; today every version is one part.
 */
data class ModelDownload(val commit: String, val parts: List<ModelPart>, val gzSize: Long, val gzSha256: String) {
    /** Download size shown to the user (MB, rounded up). */
    val megabytes: Int get() = ((gzSize + 999_999) / 1_000_000).toInt()
}

data class ModelPart(val url: String, val size: Long, val sha256: String)

/**
 * The files of one own model on this device ([dir], [versions] newest first): which known version is there, the
 * download of the newest one (in parts, checked part by part, as a whole and unpacked) and cleaning up. The SHA-256 of
 * an installed file is computed once and kept in a small file next to it (`<file>.fassung`: size, time, SHA-256).
 */
class OwnModels(private val dir: () -> File, val versions: List<ModelVersion>) {
    init { require(versions.isNotEmpty()) }

    /** The version the app offers for download (the newest). */
    val current: ModelVersion get() = versions.first()

    data class Found(val version: ModelVersion, val file: File) {
        /** An older version is there: it keeps working, the newest one is offered as an update. */
        fun outdated(current: ModelVersion) = version.sha256 != current.sha256
    }

    /**
     * The newest known version on this device, or null. A file with a known name but an unknown SHA-256 (broken,
     * changed, of a build that is not known here) does not count. Hashes the file the first time (about a second).
     */
    fun detect(): Found? {
        val d = dir()
        return versions.map { it.file }.distinct().mapNotNull { name ->
            val f = File(d, name)
            if (!f.isFile) return@mapNotNull null
            val sha = runCatching { sha256Of(f) }.getOrNull() ?: return@mapNotNull null
            versions.firstOrNull { it.file == name && it.sha256 == sha && it.size == f.length() }?.let { Found(it, f) }
        }.minByOrNull { versions.indexOf(it.version) }  // the newest when several are there (an interrupted clean-up)
    }

    /**
     * Loads [current]: all parts (resumable), each checked, joined, the whole checked, unpacked and checked again. Only
     * then [beforeReplace] (unload the running model – Windows keeps a loaded file locked) and the file takes the place of
     * the old one; the files of older versions go. Until that moment the old version keeps working.
     */
    fun install(cancelled: () -> Boolean, progress: (Double) -> Unit, beforeReplace: () -> Unit): Found {
        val v = current
        val dl = v.download ?: error(tr("Für diese Version gibt es keinen Download."))
        val d = dir().apply { mkdirs() }
        val gz = File(d, v.file + ".gz.part")
        val total = dl.parts.sumOf { it.size }.coerceAtLeast(1)
        var done = 0L
        val pieces = dl.parts.mapIndexed { i, p ->
            val piece = if (dl.parts.size == 1) gz else File(d, v.file + ".gz.part$i")
            val start = done
            if (!(piece.isFile && piece.length() == p.size && FileDownload.sha256(piece) == p.sha256)) {
                FileDownload.fetch(p.url, piece, p.size, cancelled) { x -> progress((start + x * p.size) / total) }
                if (piece.length() != p.size || FileDownload.sha256(piece) != p.sha256) {
                    piece.delete()
                    error(tr("Prüfsumme stimmt nicht – die Datei ist beschädigt"))
                }
            }
            done += p.size
            piece
        }
        progress(1.0)
        if (pieces.size > 1) {
            gz.delete()
            gz.outputStream().buffered(1 shl 20).use { out -> pieces.forEach { p -> p.inputStream().use { it.copyTo(out, 1 shl 20) } } }
        }
        if (gz.length() != dl.gzSize || FileDownload.sha256(gz) != dl.gzSha256) {
            gz.delete()
            pieces.forEach { it.delete() }
            error(tr("Prüfsumme stimmt nicht – die Datei ist beschädigt"))
        }
        val part = File(d, v.file + ".part")
        GZIPInputStream(gz.inputStream().buffered(1 shl 20)).use { i -> part.outputStream().use { o -> i.copyTo(o, 1 shl 20) } }
        val sha = FileDownload.sha256(part)
        if (part.length() != v.size || sha != v.sha256) {
            part.delete()
            error(tr("Prüfsumme stimmt nicht – die Datei ist beschädigt"))
        }
        beforeReplace()
        val target = File(d, v.file)
        target.delete()
        memo(target).delete()
        if (!part.renameTo(target)) error(tr("Datei konnte nicht gespeichert werden"))
        remember(target, sha)
        gz.delete()
        pieces.forEach { it.delete() }
        versions.map { it.file }.distinct().filter { it != v.file }.forEach { File(d, it).delete(); memo(File(d, it)).delete() }
        return Found(v, target)
    }

    /** Removes every version and the unfinished downloads. */
    fun deleteAll() {
        val d = dir()
        versions.map { it.file }.distinct().forEach { name ->
            File(d, name).delete()
            memo(File(d, name)).delete()
            d.listFiles { f -> f.name.startsWith("$name.gz.part") || f.name == "$name.part" }?.forEach { it.delete() }
        }
    }

    private fun memo(f: File) = File(f.path + ".fassung")

    /** SHA-256 of [f], from the memo if size and time still match. */
    fun sha256Of(f: File): String {
        val m = memo(f)
        runCatching { m.readText().trim().split('\t') }.getOrNull()?.takeIf { it.size == 3 }?.let { (size, time, sha) ->
            if (size.toLongOrNull() == f.length() && time.toLongOrNull() == f.lastModified() && sha.length == 64) return sha
        }
        return FileDownload.sha256(f).also { remember(f, it) }
    }

    private fun remember(f: File, sha: String) {
        runCatching { memo(f).writeText("${f.length()}\t${f.lastModified()}\t$sha\n") }
    }

    companion object {
        /** Largest part of a download: below GitHub's 100 MB per file, with room to spare. */
        const val PART_LIMIT = 95_000_000L

        /** Raw file of [path] on the model branch at [commit] (the repository was „ADEU-Harness“, GitHub forwards). */
        fun rawUrl(commit: String, path: String) = "https://raw.githubusercontent.com/ADEU-TUD/Lora/$commit/$path"

        private val resources = java.util.concurrent.ConcurrentHashMap<String, Any>()

        /** Labels of a version (a JSON list in the resources of the app). */
        @Suppress("UNCHECKED_CAST")
        fun labels(resource: String): List<String> = resources.getOrPut("labels:$resource") {
            val text = OwnModels::class.java.getResourceAsStream(resource)?.bufferedReader(Charsets.UTF_8)?.readText() ?: error("$resource fehlt")
            kotlinx.serialization.json.Json.parseToJsonElement(text).let { it as kotlinx.serialization.json.JsonArray }.map { (it as kotlinx.serialization.json.JsonPrimitive).content }
        } as List<String>

        /** WordPiece vocabulary of a version (one token per line, in the resources of the app). */
        @Suppress("UNCHECKED_CAST")
        fun vocab(resource: String): Map<String, Int> = resources.getOrPut("vocab:$resource") {
            val stream = OwnModels::class.java.getResourceAsStream(resource) ?: error("$resource fehlt")
            stream.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.withIndex().associate { (i, t) -> t to i } }
        } as Map<String, Int>

        /**
         * Whether the update to [current] was declined with „Nicht mehr fragen“: [declinedSha] is the version it was
         * declined for. Before AP-117 only a switch was kept ([ask] false, nothing stored) – that meant the version
         * [legacySha] that was new then; a newer one is offered again.
         */
        fun updateDeclined(ask: Boolean, declinedSha: String, current: ModelVersion, legacySha: String): Boolean = when {
            declinedSha.isNotBlank() -> declinedSha == current.sha256
            else -> !ask && current.sha256 == legacySha
        }
    }
}
