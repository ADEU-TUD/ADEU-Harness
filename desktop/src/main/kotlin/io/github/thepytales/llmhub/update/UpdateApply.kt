package io.github.thepytales.llmhub.update

import io.github.thepytales.llmhub.desktop.Os
import io.github.thepytales.llmhub.desktop.Paths
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.swing.JOptionPane
import kotlin.system.exitProcess

/**
 * Second half of an update (see [UpdatePlatform]). The NEW launcher runs from the unpacked update as
 * `ADEU LLM.exe --apply-update <pid> <new app folder> <app folder>` (macOS/Linux: its own launcher, AP-31): it waits
 * until the old app (pid) has ended, moves its files into [BACKUP], copies the new ones in and starts the app again. If
 * anything fails, the old files go back and the old version starts, with a message. Its log is update.log in the local
 * folder of the app (Windows: %LOCALAPPDATA%\ADEU LLM).
 */
object UpdateApply {
    const val FLAG = "--apply-update"
    /** Downloaded and unpacked updates inside the app folder. */
    const val STAGING = ".update"
    /** The replaced files until the next start of the new version. */
    const val BACKUP = ".update-alt"

    fun run(args: Array<String>): Nothing {
        val pid = args.getOrNull(1)?.toLongOrNull()
        val source = File(args.getOrNull(2) ?: exitProcess(2))
        val target = File(args.getOrNull(3) ?: exitProcess(2))
        val log = runCatching { File(Paths.local, "update.log") }.getOrElse { File(target, "update.log") }
        fun note(s: String) {
            runCatching { log.parentFile?.mkdirs(); log.appendText("${LocalDateTime.now()} $s\n") }
        }
        note("Update von ${source.path} nach ${target.path}")
        val code = try {
            pid?.let { ProcessHandle.of(it).ifPresent { h -> runCatching { h.onExit().get(90, TimeUnit.SECONDS) } } }
            // Windows releases the files of an ended process a moment later.
            Thread.sleep(1_000)
            swap(source, target, ::note)
            note("Fertig, starte ${target.path}")
            start(target)
            0
        } catch (e: Throwable) {
            note("FEHLER: ${e.stackTraceToString()}")
            runCatching { start(target) }
            runCatching {
                JOptionPane.showMessageDialog(
                    null,
                    "Das Update konnte nicht installiert werden:\n\n${e.message}\n\n" +
                        "Lora startet in der bisherigen Version. Einfach später noch einmal versuchen.\nProtokoll: ${log.path}",
                    "Lora – Update",
                    JOptionPane.WARNING_MESSAGE,
                )
            }
            1
        }
        exitProcess(code)
    }

    /**
     * Moves the files of [target] that [source] replaces into [BACKUP] and copies the new ones in. Files of [target] the
     * update does not contain stay. On failure everything goes back, then the error is thrown.
     */
    internal fun swap(
        source: File,
        target: File,
        note: (String) -> Unit = {},
        copy: (File, File) -> Unit = ::copyTree,
    ) {
        val names = source.listFiles().orEmpty().map { it.name }.filterNot { it.startsWith(".") }.sorted()
        require(AppLayout.ofNames(names) != null) { "Das Update-Paket ist unvollständig." }
        val backup = File(target, BACKUP)
        backup.deleteRecursively()
        backup.mkdirs()
        val moved = mutableListOf<String>()
        val copied = mutableListOf<String>()
        try {
            for (n in names) {
                val old = File(target, n)
                if (!old.exists()) continue
                retry(n) { Files.move(old.toPath(), File(backup, n).toPath()) }
                moved += n
            }
            for (n in names) {
                copied += n
                copy(File(source, n), File(target, n))
            }
        } catch (e: Exception) {
            note("Rückgängig: ${e.message}")
            for (n in copied) File(target, n).deleteRecursively()
            for (n in moved) runCatching { Files.move(File(backup, n).toPath(), File(target, n).toPath()) }.onFailure { note("Nicht zurück: $n – ${it.message}") }
            throw e
        }
    }

    /** Virus scanners and Explorer hold files briefly; up to 30 s. */
    private fun retry(name: String, action: () -> Unit) {
        var last: Exception? = null
        repeat(60) {
            try {
                action()
                return
            } catch (e: Exception) {
                last = e
                Thread.sleep(500)
            }
        }
        throw IOException("„$name“ ist noch in Benutzung – läuft Lora noch, oder ist eine Datei daraus geöffnet? (${last?.message})")
    }

    /**
     * Copies a folder of the new version. Windows: plain copy. macOS and Linux: `cp -a`, which keeps executable bits and
     * the links inside the Java runtime (Java's copy would turn them into files without rights).
     */
    private fun copyTree(from: File, to: File) {
        if (Os.windows) {
            from.copyRecursively(to, overwrite = true)
            return
        }
        val p = ProcessBuilder("cp", "-a", from.path, to.path).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (p.waitFor() != 0) throw IOException("Kopieren von ${from.name} fehlgeschlagen: $out")
    }

    /** Starts the app in [appDir] (the folder with ADEU LLM.exe, the .app bundle or the folder with bin/). */
    private fun start(appDir: File) {
        val layout = AppLayout.of(appDir) ?: AppLayout.current
        when (layout) {
            AppLayout.MAC -> ProcessBuilder("open", "-n", appDir.path).start()
            else -> File(appDir, layout.launcher).let { ProcessBuilder(it.path).directory(appDir).start() }
        }
    }
}
