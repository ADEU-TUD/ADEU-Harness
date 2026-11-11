package io.github.thepytales.llmhub.files

import io.github.thepytales.llmhub.ui.tr
import io.github.thepytales.llmhub.ui.trLater
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream

/**
 * Archiv-Werkzeug (AP-99): ZIP (also with password and in parts), 7z, TAR, TAR.GZ/TGZ, TAR.BZ2/TAR.XZ and a lone GZ
 * open like folders – list, search, take single files out without unpacking everything – and folders or files are
 * packed into a ZIP with a chosen strength, an AES-256 password (AP-98) and, for mail, split into parts („Teile“: the
 * .zip with .z01, .z02 …, which 7-Zip, WinRAR and Lora put back together). Paths that would land outside the target
 * folder („../“) are refused.
 */
object Archives {
    data class Entry(val path: String, val size: Long, val modified: Long, val dir: Boolean, val encrypted: Boolean = false) {
        val name get() = path.trimEnd('/').substringAfterLast('/')
        val folder get() = path.trimEnd('/').substringBeforeLast('/', "")
    }

    /** Packing strength. */
    enum class Strength(val label: String) {
        STORE(trLater("Ohne Packen (schnell, für Fotos und Videos)")),
        FAST(trLater("Schnell")),
        NORMAL(trLater("Normal (empfohlen)")),
        MAX(trLater("Stark (kleiner, langsamer)")),
    }

    class PasswordNeeded(message: String) : Exception(message)

    enum class Kind { ZIP, SEVEN_Z, TAR, TAR_GZ, TAR_BZ2, TAR_XZ, GZ }

    fun kind(name: String): Kind? {
        val n = name.lowercase()
        return when {
            n.endsWith(".zip") -> Kind.ZIP
            n.endsWith(".7z") -> Kind.SEVEN_Z
            n.endsWith(".tar.gz") || n.endsWith(".tgz") -> Kind.TAR_GZ
            n.endsWith(".tar.bz2") || n.endsWith(".tbz2") -> Kind.TAR_BZ2
            n.endsWith(".tar.xz") || n.endsWith(".txz") -> Kind.TAR_XZ
            n.endsWith(".tar") -> Kind.TAR
            n.endsWith(".gz") -> Kind.GZ
            else -> null
        }
    }

    fun isArchive(name: String) = kind(name) != null

    // ── Reading ──

    /** Whether the ZIP or 7z [file] needs a password to take files out. */
    fun needsPassword(file: File): Boolean = when (kind(file.name)) {
        Kind.ZIP -> runCatching { ZipFile(file).use { z -> z.isEncrypted } }.getOrDefault(false)
        Kind.SEVEN_Z -> runCatching { SevenZFile.builder().setFile(file).get().use { z -> z.entries.count(); false } }.getOrElse { it.javaClass.simpleName.contains("Password") }
        else -> false
    }

    /** All entries of [file], folders included (also those only implied by a file path). */
    fun list(file: File, password: CharArray? = null): List<Entry> {
        val raw = when (kind(file.name)) {
            Kind.ZIP -> ZipFile(file, password).use { z ->
                z.fileHeaders.map { h -> Entry(h.fileName.replace('\\', '/'), h.uncompressedSize, h.lastModifiedTimeEpoch, h.isDirectory, h.isEncrypted) }
            }
            Kind.SEVEN_Z -> sevenZ(file, password) { z ->
                z.entries.map { e -> Entry(e.name.replace('\\', '/') + if (e.isDirectory) "/" else "", e.size, runCatching { e.lastModifiedDate.time }.getOrDefault(0L), e.isDirectory) }
            }
            Kind.GZ -> listOf(Entry(file.name.substringBeforeLast('.'), -1, file.lastModified(), false))
            null -> emptyList()
            else -> tar(file) { input ->
                val out = mutableListOf<Entry>()
                while (true) {
                    val e = input.nextEntry ?: break
                    out += Entry(e.name.removePrefix("./"), e.size, e.lastModifiedDate?.time ?: 0L, e.isDirectory)
                }
                out
            }
        }
        val known = raw.map { it.path.trimEnd('/') }.toMutableSet()
        val implied = mutableListOf<Entry>()
        for (e in raw) {
            var f = e.folder
            while (f.isNotEmpty() && known.add(f)) {
                implied += Entry("$f/", 0, 0, true)
                f = f.substringBeforeLast('/', "")
            }
        }
        return (raw + implied).filter { it.path.trimEnd('/').isNotEmpty() }
    }

    private fun <T> sevenZ(file: File, password: CharArray?, block: (SevenZFile) -> T): T {
        val b = SevenZFile.builder().setFile(file)
        if (password != null) b.setPassword(password)
        return try {
            b.get().use(block)
        } catch (e: Exception) {
            if (e.javaClass.simpleName.contains("Password")) throw PasswordNeeded(tr("Das Archiv ist mit einem Passwort geschützt."))
            throw e
        }
    }

    private fun <T> tar(file: File, block: (ArchiveInputStream<*>) -> T): T {
        val raw: InputStream = BufferedInputStream(file.inputStream())
        val stream: InputStream = when (kind(file.name)) {
            Kind.TAR_GZ -> GzipCompressorInputStream(raw)
            Kind.TAR_BZ2 -> BZip2CompressorInputStream(raw)
            Kind.TAR_XZ -> XZCompressorInputStream(raw)
            else -> raw
        }
        return TarArchiveInputStream(stream).use(block)
    }

    /** The folders and files directly in [folder] ("" = top level) of [entries]. */
    fun inFolder(entries: List<Entry>, folder: String): Pair<List<Entry>, List<Entry>> {
        val here = entries.filter { it.folder == folder.trim('/') }
        return here.filter { it.dir }.sortedBy { it.name.lowercase() } to here.filter { !it.dir }.sortedBy { it.name.lowercase() }
    }

    /** Files of [entries] whose path contains every word of [query]. */
    fun search(entries: List<Entry>, query: String): List<Entry> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return entries.filter { e -> !e.dir && words.all { it in e.path.lowercase() } }
    }

    /** [path] inside [dest], refused when it would lead outside („../“, absolute paths). */
    private fun safeTarget(dest: File, path: String): File {
        val clean = path.replace('\\', '/').trimStart('/')
        val target = File(dest, clean)
        val base = dest.canonicalFile
        if (!target.canonicalFile.path.startsWith(base.path + File.separator) && target.canonicalFile != base) {
            throw SecurityException(tr("„{0}“ würde außerhalb des Zielordners landen und wird übersprungen.", path))
        }
        return target
    }

    /**
     * Takes the entries [paths] (files; a folder path takes everything below it; null = all) of [file] out into
     * [dest]; returns the files written. [progress] gets (done, total).
     */
    fun extract(file: File, dest: File, paths: Collection<String>? = null, password: CharArray? = null, progress: (Int, Int) -> Unit = { _, _ -> }): List<File> {
        dest.mkdirs()
        fun wanted(p: String): Boolean = paths == null || paths.any { w -> p == w || (w.endsWith("/") && p.startsWith(w)) || p.startsWith(w.trimEnd('/') + "/") }
        val out = mutableListOf<File>()
        when (kind(file.name)) {
            Kind.ZIP -> ZipFile(file, password).use { z ->
                if (z.isEncrypted && password == null) throw PasswordNeeded(tr("Das Archiv ist mit einem Passwort geschützt."))
                val headers = z.fileHeaders.filter { !it.isDirectory && wanted(it.fileName.replace('\\', '/')) }
                headers.forEachIndexed { i, h ->
                    progress(i, headers.size)
                    val target = safeTarget(dest, h.fileName)
                    target.parentFile?.mkdirs()
                    try {
                        z.getInputStream(h).use { input -> target.outputStream().use { input.copyTo(it) } }
                    } catch (e: net.lingala.zip4j.exception.ZipException) {
                        target.delete()
                        if (e.type == net.lingala.zip4j.exception.ZipException.Type.WRONG_PASSWORD) throw PasswordNeeded(tr("Das Passwort stimmt nicht."))
                        throw e
                    }
                    if (h.lastModifiedTimeEpoch > 0) target.setLastModified(h.lastModifiedTimeEpoch)
                    out += target
                }
            }
            Kind.SEVEN_Z -> sevenZ(file, password) { z ->
                val all = z.entries.filter { !it.isDirectory && wanted(it.name.replace('\\', '/')) }.map { it.name }.toSet()
                var i = 0
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory || e.name !in all) continue
                    progress(i++, all.size)
                    val target = safeTarget(dest, e.name)
                    target.parentFile?.mkdirs()
                    z.getInputStream(e).use { input -> target.outputStream().use { input.copyTo(it) } }
                    out += target
                }
            }
            Kind.GZ -> {
                val target = safeTarget(dest, file.name.substringBeforeLast('.'))
                GzipCompressorInputStream(BufferedInputStream(file.inputStream())).use { input -> target.outputStream().use { input.copyTo(it) } }
                out += target
            }
            null -> error(tr("„{0}“ ist kein Archiv, das Lora öffnen kann.", file.name))
            else -> tar(file) { input ->
                var i = 0
                while (true) {
                    val e = input.nextEntry ?: break
                    val p = e.name.removePrefix("./")
                    if (e.isDirectory || !wanted(p)) continue
                    progress(i++, -1)
                    val target = safeTarget(dest, p)
                    target.parentFile?.mkdirs()
                    target.outputStream().use { input.copyTo(it) }
                    out += target
                }
            }
        }
        return out
    }

    // ── Packing ──

    /**
     * Packs [sources] (files and folders, folders with their content) into the ZIP [out]. With [password] AES-256,
     * with [partMb] in parts of that size (out.zip, out.z01, out.z02 …). Returns the files written (all parts).
     */
    fun pack(sources: List<File>, out: File, strength: Strength = Strength.NORMAL, password: CharArray? = null, partMb: Int? = null): List<File> {
        out.parentFile?.mkdirs()
        require(!out.exists()) { tr("„{0}“ gibt es schon.", out.name) }
        val params = ZipParameters().apply {
            compressionMethod = if (strength == Strength.STORE) CompressionMethod.STORE else CompressionMethod.DEFLATE
            compressionLevel = when (strength) {
                Strength.FAST -> CompressionLevel.FASTEST
                Strength.MAX -> CompressionLevel.ULTRA
                else -> CompressionLevel.NORMAL
            }
            if (password != null && password.isNotEmpty()) {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }
        }
        val zip = if (password != null && password.isNotEmpty()) ZipFile(out, password) else ZipFile(out)
        zip.use { z ->
            if (partMb != null && partMb > 0) {
                val bytes = partMb.toLong() * 1024 * 1024
                val files = sources.filter { it.isFile }
                val dirs = sources.filter { it.isDirectory }
                when {
                    dirs.size == 1 && files.isEmpty() -> z.createSplitZipFileFromFolder(dirs[0], params, true, bytes)
                    dirs.isEmpty() && files.isNotEmpty() -> z.createSplitZipFile(files, params, true, bytes)
                    else -> throw IllegalArgumentException(tr("In Teile packen geht für einen Ordner oder für einzelne Dateien. Bitte alles zuerst in einen Ordner legen."))
                }
            } else {
                for (s in sources) {
                    if (s.isDirectory) z.addFolder(s, ZipParameters(params)) else z.addFile(s, ZipParameters(params))
                }
            }
            return z.splitZipFiles.ifEmpty { listOf(out) }
        }
    }

    /** A name for the new archive next to [sources]: the folder's or file's name, or „Archiv“; numbered if taken. */
    fun outFile(sources: List<File>): File {
        val dir = sources.firstOrNull()?.parentFile ?: File(".")
        val base = if (sources.size == 1) sources[0].let { if (it.isDirectory) it.name else it.nameWithoutExtension } else "Archiv"
        var f = File(dir, "$base.zip")
        var n = 2
        while (f.exists()) f = File(dir, "$base ($n).zip").also { n++ }
        return f
    }

    /** ZIP with a password from AP-98 („Passwort setzen“ for a ZIP): repacks [file] with AES-256 next to itself. */
    fun protect(file: File, password: CharArray, out: File) {
        val tmp = File(out.parentFile, ".lora-entpackt-${System.nanoTime()}")
        try {
            extract(file, tmp)
            ZipFile(out, password).use { z ->
                val params = ZipParameters().apply {
                    isEncryptFiles = true
                    encryptionMethod = EncryptionMethod.AES
                    aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                }
                tmp.listFiles()?.sortedBy { it.name }?.forEach { f -> if (f.isDirectory) z.addFolder(f, ZipParameters(params)) else z.addFile(f, ZipParameters(params)) }
            }
        } finally {
            tmp.deleteRecursively()
        }
    }

    /** The ZIP [file] without its password (repacked next to itself as [out]). */
    fun unprotect(file: File, password: CharArray, out: File) {
        val tmp = File(out.parentFile, ".lora-entpackt-${System.nanoTime()}")
        try {
            extract(file, tmp, password = password)
            ZipFile(out).use { z -> tmp.listFiles()?.sortedBy { it.name }?.forEach { f -> if (f.isDirectory) z.addFolder(f) else z.addFile(f) } }
        } finally {
            tmp.deleteRecursively()
        }
    }
}
