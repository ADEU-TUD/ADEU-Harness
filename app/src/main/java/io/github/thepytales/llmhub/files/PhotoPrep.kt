package io.github.thepytales.llmhub.files

import io.github.thepytales.llmhub.ui.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Fotos vom Handy aufbereiten (AP-100): in one step – HEIC/HEIF (iPhone) and other formats to JPG, turned upright as
 * the camera noted (EXIF/HEIF orientation), named by the time they were taken („2026-11-10_14-32_Exkursion.jpg“),
 * without location and other metadata (the picture is written anew, nothing is copied over), smaller on request. The
 * originals stay where they are; the results go into a folder of their own next to them.
 */
object PhotoPrep {
    val PHOTO_EXTS = setOf("jpg", "jpeg", "heic", "heif", "png", "webp", "avif", "tif", "tiff", "bmp")

    data class Options(
        /** Added to the date in the name, e.g. „Exkursion“ (empty: only date and time). */
        val label: String = "",
        /** Longest side in pixels, null = keep the size. */
        val maxSide: Int? = null,
        val quality: Int = 90,
        val rename: Boolean = true,
        /** Folder for the results, null = „aufbereitet“ next to the first photo. */
        val target: File? = null,
    )

    /** One photo: where it comes from, what it will be called and what happens to it. */
    data class Item(val source: File, val name: String, val info: Exif.Info, val turn: Boolean, val readable: Boolean)

    data class Result(val made: List<File>, val failed: List<Pair<File, String>>, val folder: File)

    private val NAME_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")

    fun isPhoto(f: File) = f.isFile && f.extension.lowercase() in PHOTO_EXTS

    /** The photos of [files]; folders contribute the photos directly inside them. */
    fun photos(files: List<File>): List<File> = files.flatMap { f ->
        if (f.isDirectory) f.listFiles()?.filter(::isPhoto)?.sortedBy { it.name.lowercase() }.orEmpty() else listOf(f).filter(::isPhoto)
    }.distinct()

    fun defaultTarget(photos: List<File>): File {
        val dir = photos.firstOrNull()?.parentFile ?: File(".")
        return File(dir, "aufbereitet")
    }

    /** A file name part from [label]: no characters Windows forbids, spaces as „-“. */
    fun cleanLabel(label: String): String = label.trim().replace(Regex("[\\\\/:*?\"<>|]"), "").replace(Regex("\\s+"), "-").take(40)

    /** What [prepare] will do with [photos] (names already unique within the batch and the target folder). */
    fun plan(photos: List<File>, o: Options): List<Item> {
        val target = o.target ?: defaultTarget(photos)
        val used = target.listFiles()?.map { it.name.lowercase() }?.toMutableSet() ?: mutableSetOf()
        val label = cleanLabel(o.label)
        val infos = photos.map { f -> f to runCatching { Exif.read(head(f)) }.getOrDefault(Exif.Info()) }
        return infos.sortedWith(compareBy({ it.second.taken ?: modified(it.first) }, { it.first.name })).map { (f, info) ->
            val base = if (o.rename) {
                (info.taken ?: modified(f)).format(NAME_TIME) + if (label.isNotEmpty()) "_$label" else ""
            } else f.nameWithoutExtension
            var name = "$base.jpg"
            var n = 2
            while (!used.add(name.lowercase())) name = "${base}_${n++}.jpg"
            Item(f, name, info, info.orientation != 1, PhotoCodec.readable(f.extension))
        }
    }

    private fun modified(f: File): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(f.lastModified()), ZoneId.systemDefault())

    /** The first part of [f] – enough for the metadata of JPEG and HEIC (HEIC keeps it at the start). */
    private fun head(f: File): ByteArray = f.inputStream().use { input ->
        val buf = ByteArray(minOf(f.length(), 4L * 1024 * 1024).toInt())
        var read = 0
        while (read < buf.size) {
            val n = input.read(buf, read, buf.size - read)
            if (n < 0) break
            read += n
        }
        if (read < buf.size) buf.copyOf(read) else buf
    }

    /** Short line for the preview: „3 gedreht · 5 mit Ortsdaten · 2 HEIC“. */
    fun summary(items: List<Item>): String = listOfNotNull(
        items.count { it.turn }.takeIf { it > 0 }?.let { tr("{0} werden gedreht", it) },
        items.count { it.info.hasGps }.takeIf { it > 0 }?.let { tr("{0} mit Ortsdaten", it) },
        items.count { it.source.extension.lowercase() in setOf("heic", "heif") }.takeIf { it > 0 }?.let { tr("{0} HEIC", it) },
        items.count { !it.readable }.takeIf { it > 0 }?.let { tr("{0} hier nicht lesbar", it) },
    ).joinToString(" · ")

    /** Prepares [photos]; [progress] gets (done, total). */
    suspend fun prepare(photos: List<File>, o: Options, progress: (Int, Int) -> Unit = { _, _ -> }): Result = withContext(Dispatchers.IO) {
        val target = o.target ?: defaultTarget(photos)
        target.mkdirs()
        val items = plan(photos, o)
        val made = mutableListOf<File>()
        val failed = mutableListOf<Pair<File, String>>()
        items.forEachIndexed { i, item ->
            progress(i, items.size)
            runCatching {
                if (!item.readable) error(tr("„{0}“ kann auf diesem Gerät nicht gelesen werden.", item.source.extension.uppercase()))
                var pic = PhotoCodec.read(item.source) ?: error(tr("Das Bild ließ sich nicht lesen."))
                pic = PhotoCodec.orient(pic, item.info.orientation)
                o.maxSide?.let { pic = PhotoCodec.fit(pic, it) }
                val out = File(target, item.name)
                PhotoCodec.write(pic, out, "jpg", o.quality)
                // The file time follows the photo, so sorting by date in the Explorer fits too.
                item.info.taken?.let { t -> out.setLastModified(t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
                out
            }.onSuccess { made += it }.onFailure { failed += item.source to (it.message ?: it.toString()) }
        }
        progress(items.size, items.size)
        Result(made, failed, target)
    }
}
