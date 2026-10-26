package io.github.thepytales.llmhub.update

import android.content.Context
import io.github.thepytales.llmhub.AppLog
import io.github.thepytales.llmhub.LlmHubApp
import io.github.thepytales.llmhub.desktop.Paths
import java.io.File
import java.util.zip.ZipFile
import kotlin.system.exitProcess

/**
 * Windows: the update replaces the app where it is – in %LOCALAPPDATA%\ADEU LLM\app\… (single-file EXE) or wherever the
 * ZIP was unpacked (desktop, network drive, USB stick). So shortcuts, the pinned taskbar icon and "Mit Windows starten"
 * keep working, and nothing needs admin rights, cmd.exe or PowerShell (often blocked on work PCs):
 *
 * 1. The ZIP of the release is downloaded into "<App-Ordner>\.update" (written by Java, so without the "Mark of the
 *    Web" that makes SmartScreen block downloaded programs) and unpacked there.
 * 2. "Jetzt aktualisieren" starts the new "ADEU LLM.exe" from there with [UpdateApply.FLAG] and ends the app.
 * 3. That waits until the app has ended, moves the old files aside, copies the new ones in, starts the app again
 *    and ends itself ([UpdateApply]). The next start removes the leftovers ([cleanup]).
 */
object UpdatePlatform {
    const val KEY = "windows"

    /** The running "ADEU LLM.exe" (set by the launcher of the packaged app; missing when started from Gradle). */
    private val exe: File? get() = System.getProperty("jpackage.app-path")?.takeIf { it.endsWith(".exe", true) }?.let(::File)

    /** Folder with "ADEU LLM.exe", "app" and "runtime". */
    private val appDir: File? get() = exe?.absoluteFile?.parentFile

    val supported: Boolean get() = Paths.windows && exe != null

    fun downloadDir(@Suppress("UNUSED_PARAMETER") context: Context) = File(appDir ?: Paths.local, UpdateApply.STAGING)

    /**
     * Update source the app folder brings along: the first line of "update-quelle.txt" next to ADEU LLM.exe, e.g.
     * "\\server\freigabe\ADEU-Update". So an IT department or whoever hands out the ZIP sets it once for all PCs; an
     * update keeps the file, because it only replaces what the new version contains.
     */
    fun presetSource(): String? = appDir?.let { File(it, "update-quelle.txt") }?.takeIf { it.isFile }
        ?.let { f -> runCatching { f.readLines().firstOrNull { it.isNotBlank() }?.trim() }.getOrNull() }

    private fun staged(context: Context, info: UpdateInfo) = File(File(downloadDir(context), info.version), APP_FOLDER)

    /** Why the update cannot be installed here, or null. */
    fun problem(context: Context, file: UpdateFile): String? {
        val dir = appDir ?: return "Automatische Updates gibt es nur in der installierten Windows-Version."
        val probe = File(dir, ".schreibtest-${System.nanoTime()}")
        val writable = runCatching { probe.writeText("ok"); true }.getOrDefault(false).also { probe.delete() }
        if (!writable) {
            return "Der Ordner „${dir.path}“ ist schreibgeschützt (z. B. von der IT installiert). " +
                "Bitte die neue Version von ${Updates.RELEASES_PAGE} herunterladen oder die IT um das Update bitten."
        }
        // ZIP, unpacked copy and the copy in the app folder.
        val needed = (if (file.size > 0) file.size else 250L shl 20) * 5
        val free = downloadDir(context).let { d -> generateSequence(d) { it.parentFile }.firstOrNull { it.exists() } }?.usableSpace ?: Long.MAX_VALUE
        if (free in 1 until needed) return "Zu wenig freier Speicher für das Update (nötig etwa ${needed shr 20} MB, frei ${free shr 20} MB)."
        return null
    }

    /** The unpacked version from an earlier download, if it is complete. */
    fun prepared(context: Context, info: UpdateInfo): File? =
        staged(context, info).takeIf { File(it, ".fertig").isFile && File(it, EXE).isFile }

    /** Unpacks the ZIP next to the app and checks that it is a complete app folder. */
    fun prepare(context: Context, zip: File, info: UpdateInfo, progress: (Float) -> Unit): File {
        val target = File(downloadDir(context), info.version)
        target.deleteRecursively()
        target.mkdirs()
        val base = target.canonicalPath + File.separator
        ZipFile(zip).use { z ->
            val entries = z.entries().toList()
            val total = entries.sumOf { it.size.coerceAtLeast(0) }.coerceAtLeast(1)
            var done = 0L
            for (e in entries) {
                // Older ZIP tools of Windows write "\\" between folders.
                val name = e.name.replace('\\', '/')
                val out = File(target, name)
                // Nothing outside the target folder ("zip slip").
                if (!out.canonicalPath.startsWith(base)) continue
                if (e.isDirectory || name.endsWith("/")) { out.mkdirs(); continue }
                out.parentFile?.mkdirs()
                z.getInputStream(e).use { input -> out.outputStream().use { input.copyTo(it) } }
                done += e.size.coerceAtLeast(0)
                progress(done.toFloat() / total)
            }
        }
        val app = File(target, APP_FOLDER)
        check(File(app, EXE).isFile && File(app, "app").isDirectory && File(app, "runtime").isDirectory) {
            "Das Update-Paket ist unvollständig (kein „$APP_FOLDER\\$EXE“ mit app und runtime)."
        }
        File(app, ".fertig").writeText(info.version)
        zip.delete()
        return app
    }

    /** Starts the new version's helper and ends the app; it comes back as the new version. */
    fun install(@Suppress("UNUSED_PARAMETER") context: Context, prepared: File, info: UpdateInfo): String? {
        val dir = appDir ?: error("Kein App-Ordner")
        val helper = File(prepared, EXE)
        AppLog.i("Update auf ${info.version}: ${prepared.path} → ${dir.path}")
        val process = ProcessBuilder(helper.path, UpdateApply.FLAG, ProcessHandle.current().pid().toString(), prepared.path, dir.path)
            .directory(prepared)
            .start()
        // It waits for this app to end; if it is gone already (blocked by IT rules, damaged), the app stays open.
        Thread.sleep(1_500)
        if (!process.isAlive) {
            error(
                "Die neue Version ließ sich nicht starten (Code ${process.exitValue()}). Möglicherweise blockiert eine IT-Regel " +
                    "Programme in „${prepared.parent}“. Die neue Version gibt es auch unter ${Updates.RELEASES_PAGE}.",
            )
        }
        runCatching {
            val c = LlmHubApp.container
            c.runs.stopAll()
            c.speaker.stop()
            c.localLlm.shutdown()
        }
        exitProcess(0)
    }

    /** Removes the old version and the downloaded files of an update that is installed now. */
    fun cleanup(context: Context) {
        val dir = appDir ?: return
        File(dir, UpdateApply.BACKUP).takeIf { it.exists() }?.deleteRecursively()
        val staging = downloadDir(context)
        staging.listFiles().orEmpty().forEach { f ->
            // Keeps a newer version that is downloaded but not installed yet.
            val newer = f.isDirectory && Versions.newer(f.name, Updates.installed) && File(f, "$APP_FOLDER/.fertig").isFile
            if (!newer) f.deleteRecursively()
        }
        if (staging.listFiles().isNullOrEmpty()) staging.delete()
    }

    private const val APP_FOLDER = "ADEU LLM"
    private const val EXE = "ADEU LLM.exe"
}
