package io.github.thepytales.llmhub.update

import android.content.Context
import io.github.thepytales.llmhub.AppLog
import io.github.thepytales.llmhub.LlmHubApp
import io.github.thepytales.llmhub.desktop.Os
import io.github.thepytales.llmhub.desktop.Paths
import io.github.thepytales.llmhub.ui.tr
import java.io.File
import java.util.zip.ZipFile
import kotlin.system.exitProcess

/**
 * Desktop: the update replaces the app where it is, so shortcuts, the pinned taskbar/Dock icon and the autostart keep
 * working, and nothing needs admin rights, cmd.exe or PowerShell (often blocked on work PCs):
 *
 * - Windows: the folder with "ADEU LLM.exe" – in %LOCALAPPDATA%\ADEU LLM\app\… (single-file EXE) or wherever the ZIP
 *   was unpacked (desktop, network drive, USB stick).
 * - macOS (AP-31): the bundle "ADEU LLM.app"; Linux: the unpacked "ADEU LLM" folder (bin/, lib/). Their releases are
 *   .tar.gz, which keeps executable bits and links. A .deb installed into /opt is read-only – there the popup points
 *   to the download page.
 *
 * 1. The archive of the release is downloaded into ".update" next to the app (macOS: into the app's local folder, so
 *    the signed bundle stays untouched) and unpacked there. Files written by Java carry neither the "Mark of the Web"
 *    (SmartScreen) nor the quarantine flag (Gatekeeper).
 * 2. "Jetzt aktualisieren" starts the new launcher from there with [UpdateApply.FLAG] and ends the app.
 * 3. That waits until the app has ended, moves the old files aside, copies the new ones in, starts the app again and
 *    ends itself ([UpdateApply]). The next start removes the leftovers ([cleanup]).
 */
object UpdatePlatform {
    /** Key of this system in version.json. */
    val KEY: String get() = Os.updateKey

    /** Folder of the whole app (null when started from Gradle). */
    private val appDir: File? get() = Os.appDir

    val supported: Boolean get() = appDir != null

    fun downloadDir(@Suppress("UNUSED_PARAMETER") context: Context): File =
        if (Os.mac && appDir != null) File(Paths.local, "update") else File(appDir ?: Paths.local, UpdateApply.STAGING)

    /**
     * Update source the app folder brings along: the first line of "update-quelle.txt" next to ADEU LLM.exe, e.g.
     * "\\server\freigabe\ADEU-Update". So an IT department or whoever hands out the ZIP sets it once for all PCs; an
     * update keeps the file, because it only replaces what the new version contains.
     */
    fun presetSource(): String? = appDir?.let { File(it, "update-quelle.txt") }?.takeIf { it.isFile }
        ?.let { f -> runCatching { f.readLines().firstOrNull { it.isNotBlank() }?.trim() }.getOrNull() }

    private fun staged(context: Context, info: UpdateInfo) = File(downloadDir(context), info.version)

    /** Why the update cannot be installed here, or null. */
    fun problem(context: Context, file: UpdateFile): String? {
        val dir = appDir ?: return tr("Automatische Updates gibt es nur in der installierten App.")
        val probe = File(if (Os.mac) File(dir, "Contents") else dir, ".schreibtest-${System.nanoTime()}")
        val writable = runCatching { probe.writeText("ok"); true }.getOrDefault(false).also { probe.delete() }
        if (!writable) {
            return tr(
                "Der Ordner „{0}“ ist schreibgeschützt (z. B. von der IT oder als Systempaket installiert). Bitte die neue Version von {1} herunterladen oder die IT um das Update bitten.",
                dir.path, Updates.RELEASES_PAGE,
            )
        }
        // Archive, unpacked copy and the copy in the app folder.
        val needed = (if (file.size > 0) file.size else 250L shl 20) * 5
        val free = downloadDir(context).let { d -> generateSequence(d) { it.parentFile }.firstOrNull { it.exists() } }?.usableSpace ?: Long.MAX_VALUE
        if (free in 1 until needed) return tr("Zu wenig freier Speicher für das Update (nötig etwa {0} MB, frei {1} MB).", needed shr 20, free shr 20)
        return null
    }

    /** The unpacked version from an earlier download, if it is complete. */
    fun prepared(context: Context, info: UpdateInfo): File? =
        AppLayout.find(staged(context, info))?.takeIf { File(it, ".fertig").isFile }

    /** Unpacks the ZIP (Windows) or tar.gz (macOS, Linux) next to the app and checks that it is a complete app. */
    fun prepare(context: Context, archive: File, info: UpdateInfo, progress: (Float) -> Unit): File {
        val target = staged(context, info)
        target.deleteRecursively()
        target.mkdirs()
        val name = archive.name.lowercase()
        if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) TarGz.extract(archive, target, progress)
        else unzip(archive, target, progress)
        val app = AppLayout.find(target) ?: error(tr("Das Update-Paket ist unvollständig (keine vollständige App darin)."))
        File(app, ".fertig").writeText(info.version)
        archive.delete()
        return app
    }

    private fun unzip(zip: File, target: File, progress: (Float) -> Unit) {
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
    }

    /** Starts the new version's helper and ends the app; it comes back as the new version. */
    fun install(@Suppress("UNUSED_PARAMETER") context: Context, prepared: File, info: UpdateInfo): String? {
        val dir = appDir ?: error("Kein App-Ordner")
        val layout = AppLayout.of(prepared) ?: error(tr("Das Update-Paket ist unvollständig (keine vollständige App darin)."))
        val helper = File(prepared, layout.launcher)
        AppLog.i("Update auf ${info.version}: ${prepared.path} → ${dir.path}")
        val process = ProcessBuilder(helper.path, UpdateApply.FLAG, ProcessHandle.current().pid().toString(), prepared.path, dir.path)
            .directory(prepared)
            .start()
        // It waits for this app to end; if it is gone already (blocked by IT rules, damaged), the app stays open.
        Thread.sleep(1_500)
        if (!process.isAlive) {
            error(
                tr(
                    "Die neue Version ließ sich nicht starten (Code {0}). Möglicherweise blockiert eine IT-Regel Programme in „{1}“. Die neue Version gibt es auch unter {2}.",
                    process.exitValue(), prepared.parent, Updates.RELEASES_PAGE,
                ),
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
            val newer = f.isDirectory && Versions.newer(f.name, Updates.installed) && AppLayout.find(f)?.let { File(it, ".fertig").isFile } == true
            if (!newer) f.deleteRecursively()
        }
        if (staging.listFiles().isNullOrEmpty()) staging.delete()
    }
}
