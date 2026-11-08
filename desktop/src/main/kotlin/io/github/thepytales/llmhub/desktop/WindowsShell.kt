package io.github.thepytales.llmhub.desktop

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import io.github.thepytales.llmhub.AppLog
import io.github.thepytales.llmhub.ui.ExternalOffice
import io.github.thepytales.llmhub.ui.tr
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import kotlin.concurrent.thread

/**
 * Windows-Integration (AP-59): the Explorer context menu „Mit Lora öffnen“ / „Lora fragen“ (per user, no admin rights),
 * starts of the app with a file, files out of the app into the Explorer, a mail or another program (dragging and the
 * clipboard), the Windows Papierkorb, and Word/Excel/PowerPoint with the way back.
 */
object ExplorerMenu {
    /** Registry keys (under HKEY_CURRENT_USER) and their default value. */
    data class Key(val path: String, val values: Map<String, String>)

    private const val BASE = "Software\\Classes"

    /** The entries for [exe]: files get „Mit Lora öffnen“ and „Lora fragen“, folders „Frag diesen Ordner“ (AP-58). */
    fun keys(exe: File): List<Key> {
        val e = exe.absolutePath
        fun cmd(flag: String) = "\"$e\" $flag \"%1\""
        return listOf(
            Key("$BASE\\*\\shell\\Lora.Open", mapOf("" to tr("Mit Lora öffnen"), "Icon" to "\"$e\",0")),
            Key("$BASE\\*\\shell\\Lora.Open\\command", mapOf("" to cmd(ShellArgs.OPEN))),
            Key("$BASE\\*\\shell\\Lora.Ask", mapOf("" to tr("Lora fragen"), "Icon" to "\"$e\",0")),
            Key("$BASE\\*\\shell\\Lora.Ask\\command", mapOf("" to cmd(ShellArgs.ASK))),
            Key("$BASE\\Directory\\shell\\Lora.Ask", mapOf("" to tr("Frag diesen Ordner (Lora)"), "Icon" to "\"$e\",0")),
            Key("$BASE\\Directory\\shell\\Lora.Ask\\command", mapOf("" to cmd(ShellArgs.ASK_FOLDER))),
        )
    }

    /** The keys to remove (the parents are enough, their subkeys go with them). */
    private val ROOTS = listOf("$BASE\\*\\shell\\Lora.Open", "$BASE\\*\\shell\\Lora.Ask", "$BASE\\Directory\\shell\\Lora.Ask")

    val supported: Boolean get() = Os.windows && Os.launcher != null

    fun installed(): Boolean = Os.windows && runCatching {
        Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, "$BASE\\*\\shell\\Lora.Ask\\command")
    }.getOrDefault(false)

    /** Writes the entries for the app's own EXE; false where that is not possible (not Windows, started from the IDE). */
    fun install(exe: File? = Os.launcher): Boolean {
        if (!Os.windows || exe == null) return false
        return runCatching {
            keys(exe).forEach { k ->
                Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, k.path)
                k.values.forEach { (name, value) -> Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, k.path, name, value) }
            }
            true
        }.onFailure { AppLog.i("Kontextmenü eintragen: ${it.message}") }.getOrDefault(false)
    }

    fun uninstall(): Boolean {
        if (!Os.windows) return false
        return runCatching {
            ROOTS.forEach { root ->
                val hk = WinReg.HKEY_CURRENT_USER
                if (Advapi32Util.registryKeyExists(hk, "$root\\command")) Advapi32Util.registryDeleteKey(hk, "$root\\command")
                if (Advapi32Util.registryKeyExists(hk, root)) Advapi32Util.registryDeleteKey(hk, root)
            }
            true
        }.onFailure { AppLog.i("Kontextmenü entfernen: ${it.message}") }.getOrDefault(false)
    }

    /** After an update the EXE may have moved: entries that point elsewhere are written again. */
    fun refresh() {
        val exe = Os.launcher ?: return
        if (!installed()) return
        val current = runCatching {
            Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, "$BASE\\*\\shell\\Lora.Ask\\command", "")
        }.getOrNull()
        if (current?.contains(exe.absolutePath) != true) install(exe)
    }
}

/**
 * Starts with a file (from the Explorer's context menu): `--open <Datei>`, `--ask <Datei>`, `--ask-folder <Ordner>`.
 * A second start hands them to the running app (see [SingleInstance]); the app takes them from [requests].
 */
object ShellArgs {
    const val OPEN = "--open"
    const val ASK = "--ask"
    const val ASK_FOLDER = "--ask-folder"

    enum class Kind { OPEN, ASK, ASK_FOLDER }

    data class Request(val kind: Kind, val file: File)

    private val _requests = MutableSharedFlow<Request>(replay = 4, extraBufferCapacity = 8)
    val requests: SharedFlow<Request> = _requests

    /** The request in [args] (the first one), or null. */
    fun parse(args: List<String>): Request? {
        for ((i, a) in args.withIndex()) {
            val kind = when (a) { OPEN -> Kind.OPEN; ASK -> Kind.ASK; ASK_FOLDER -> Kind.ASK_FOLDER; else -> null } ?: continue
            val path = args.getOrNull(i + 1)?.trim('"')?.takeIf { it.isNotBlank() } ?: return null
            return Request(kind, File(path))
        }
        return null
    }

    /** The line a second start sends to the running app. */
    fun line(r: Request): String = "${r.kind.name}\t${r.file.path}"

    fun fromLine(line: String): Request? {
        val kind = runCatching { Kind.valueOf(line.substringBefore('\t')) }.getOrNull() ?: return null
        val path = line.substringAfter('\t', "").takeIf { it.isNotBlank() } ?: return null
        return Request(kind, File(path))
    }

    fun emit(r: Request) { _requests.tryEmit(r) }

    /** Tests: forget what was asked. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun reset() = _requests.resetReplayCache()
}

/** Files to the Explorer, Outlook and other programs: as a file list (Explorer, Outlook) and as plain paths (text fields). */
class FileListTransferable(private val files: List<File>) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor, DataFlavor.stringFlavor)
    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.javaFileListFlavor || flavor == DataFlavor.stringFlavor
    override fun getTransferData(flavor: DataFlavor): Any = when (flavor) {
        DataFlavor.javaFileListFlavor -> files.map { it.absoluteFile }
        DataFlavor.stringFlavor -> files.joinToString("\n") { it.absolutePath }
        else -> throw UnsupportedFlavorException(flavor)
    }
}

object FileTransfer {
    /** Puts [files] on the clipboard as files: Strg+V in the Explorer copies them, in an Outlook mail attaches them. */
    fun copy(files: List<File>): Boolean = runCatching {
        if (files.isEmpty()) return false
        Toolkit.getDefaultToolkit().systemClipboard.setContents(FileListTransferable(files), null)
        true
    }.getOrDefault(false)

    /**
     * Moves [file] into the Papierkorb of Windows (or macOS) instead of the Lora-Papierkorb (AP-59); false where the
     * system has none, the file stays then.
     */
    fun moveToSystemTrash(file: File): Boolean = runCatching {
        val d = java.awt.Desktop.getDesktop()
        d.isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH) && d.moveToTrash(file)
    }.getOrDefault(false)

    val systemTrashSupported: Boolean
        get() = runCatching { java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH) }.getOrDefault(false)
}

/**
 * In Word/Excel/PowerPoint öffnen und zurück (AP-59): a file opened from Lora in its Office program is watched –
 * „in Word geöffnet“ while Office holds its owner file („~$Bericht.docx“), and a save there comes back into the app at
 * once (within a second instead of the 4 s round of the store).
 */
object OfficeHandoff {
    private data class Watch(val file: File, var stamp: Long, var seenLock: Boolean, val since: Long = System.currentTimeMillis())

    private val watched = java.util.concurrent.ConcurrentHashMap<String, Watch>()
    @Volatile
    private var loop: Thread? = null

    /** Called when a watched file was saved in Office (the app reloads its document). */
    @Volatile
    var onSaved: ((File) -> Unit)? = null

    /** Office's owner files of [f]: „~$“ in front, or in place of the first two letters (Word with longer names). */
    internal fun lockFiles(f: File): List<File> {
        val dir = f.parentFile ?: return emptyList()
        val n = f.name
        return listOf(File(dir, "~$$n")) + if (n.length > 2) listOf(File(dir, "~$" + n.drop(2))) else emptyList()
    }

    fun isOpenInOffice(f: File): Boolean = lockFiles(f).any { it.exists() }

    /** Opens [f] in its program and watches it; false if the system could not open it. */
    fun open(f: File): Boolean {
        val ok = DesktopActions.open(f)
        if (ok) watch(f)
        return ok
    }

    fun watch(f: File) {
        watched[f.absolutePath] = Watch(f, f.lastModified(), false)
        publish()
        if (loop == null) synchronized(this) {
            if (loop == null) loop = thread(isDaemon = true, name = "Office-Rückweg") { run() }
        }
    }

    private fun publish() {
        ExternalOffice.open.value = watched.values.filter { w -> w.seenLock || System.currentTimeMillis() - w.since < 20_000 }.map { it.file.absolutePath }.toSet()
    }

    /** One round: saves, and the end of the editing (owner file gone after it was there, or never there after 2 minutes). */
    internal fun tick() {
        for (w in watched.values.toList()) {
            val stamp = w.file.lastModified()
            if (stamp != w.stamp && stamp != 0L) {
                w.stamp = stamp
                runCatching { onSaved?.invoke(w.file) }.onFailure { AppLog.i("Office-Rückweg: ${it.message}") }
            }
            val lock = isOpenInOffice(w.file)
            if (lock) w.seenLock = true
            val age = System.currentTimeMillis() - w.since
            if ((w.seenLock && !lock) || (!w.seenLock && age > 120_000) || !w.file.exists()) watched.remove(w.file.absolutePath)
        }
        publish()
    }

    private fun run() {
        while (true) {
            try { Thread.sleep(1000) } catch (_: InterruptedException) { break }
            if (watched.isEmpty()) continue
            tick()
        }
    }

    /** Tests. */
    internal fun clear() { watched.clear(); publish() }
}
