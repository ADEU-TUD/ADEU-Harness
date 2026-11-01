package io.github.thepytales.llmhub.desktop

import io.github.thepytales.llmhub.ui.tr
import android.content.Context
import io.github.thepytales.llmhub.AppLog
import io.github.thepytales.llmhub.update.ExtensionFile
import io.github.thepytales.llmhub.update.UpdateInfo
import io.github.thepytales.llmhub.update.Updates
import io.github.thepytales.llmhub.update.Versions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream
import io.github.thepytales.llmhub.ui.trLater

/**
 * Nachladbare Erweiterungen (AP-25): large parts that not everybody needs are not in every update but set up on first
 * use into %LOCALAPPDATA%\ADEU LLM\tools\<id> – downloaded from the same update source as the app (version.json of
 * the release, GitHub or the folder of Einstellungen › Updates), checked by SHA-256, brought up to date with the app's
 * update check and removable under Einstellungen › Werkzeuge. The browser engine of AP-14 (Chromium, set up by JCEF
 * itself) is listed beside them and can be removed the same way.
 */
object Extensions {
    data class Def(val id: String, val name: String, val description: String, val estimateMb: Int)

    /** The Videoeditor-Paket: ffprobe for exact details of videos and the speech model for the noise reduction. */
    val VIDEO = Def(
        "videoeditor", "Videoeditor-Paket",
        trLater("ffprobe für genaue Angaben zu Videos (Länge, Bildrate, Drehung, Tonspuren) und das Sprachmodell der Rauschminderung (RNNoise). " +
            "Ohne das Paket schneidet der Videoeditor mit dem ffmpeg der App, die Rauschminderung ist dann einfacher."),
        40,
    )

    val ALL = listOf(VIDEO)

    sealed interface State {
        data object Missing : State
        data class Installing(val step: String, val progress: Float) : State
        data class Installed(val version: String, val bytes: Long) : State
        data class Failed(val message: String) : State
    }

    private val _states = MutableStateFlow<Map<String, State>>(emptyMap())
    val states: StateFlow<Map<String, State>> = _states.asStateFlow()

    /** Folder of all extensions (tests set it). */
    @Volatile
    var root: File = File(Paths.local, "tools")

    fun dir(id: String) = File(root, id)
    private fun manifest(id: String) = File(dir(id), "erweiterung.json")

    /** Installed version of [id], or null. */
    fun version(id: String): String? = runCatching { JSONObject(manifest(id).readText()).optString("version").ifBlank { null } }.getOrNull()

    fun installed(id: String) = version(id) != null

    /** A file of an installed extension, or null. */
    fun file(id: String, name: String): File? = File(dir(id), name).takeIf { installed(id) && it.isFile }

    fun sizeOnDisk(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Current state of every extension (reads the disk). */
    fun refresh() {
        _states.update { old ->
            ALL.associate { d ->
                val busy = old[d.id] as? State.Installing
                d.id to (busy ?: version(d.id)?.let { State.Installed(it, sizeOnDisk(dir(d.id))) } ?: (old[d.id] as? State.Failed) ?: State.Missing)
            }
        }
    }

    /**
     * The entry of [id] in version.json of the update source (null when the release has none). Packages are made for
     * one system (AP-31): "videoeditor" is the Windows package; macOS/Linux get "videoeditor-macos"/"-linux" once there
     * are such packages.
     */
    fun catalog(context: Context, id: String): ExtensionFile? = forThisSystem(Updates.readInfo(context).extensions, id)

    private fun forThisSystem(all: List<ExtensionFile>, id: String): ExtensionFile? =
        all.firstOrNull { it.id == "$id-${Os.updateKey}" } ?: all.firstOrNull { it.id == id }?.takeIf { Os.windows }

    /** The id an entry of version.json is installed under on this system, or null if it is for another system. */
    private fun baseId(e: ExtensionFile): String? {
        val suffix = "-${Os.updateKey}"
        return when {
            e.id.endsWith(suffix) -> e.id.removeSuffix(suffix)
            listOf("-windows", "-macos", "-linux").any { e.id.endsWith(it) } -> null
            Os.windows -> e.id
            else -> null
        }
    }

    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    /**
     * Downloads and sets up [id] (or brings it up to date with [entry]); progress goes to [states]. Returns null when it
     * worked, otherwise a readable message.
     */
    suspend fun install(context: Context, id: String, entry: ExtensionFile? = null): String? = withContext(Dispatchers.IO) {
        cancelled = false
        fun step(text: String, p: Float) = _states.update { it + (id to State.Installing(text, p)) }
        try {
            step(tr("Sucht das Paket …"), -1f)
            val e = entry ?: catalog(context, id)
                ?: throw IllegalStateException(
                    if (Os.windows) tr("Die Update-Quelle bietet das Paket (noch) nicht an. Der Videoeditor funktioniert trotzdem – mit dem ffmpeg der App.")
                    else tr("Das Paket gibt es für {0} noch nicht. Der Videoeditor funktioniert trotzdem – mit dem ffmpeg des Systems.", Os.label),
                )
            val downloads = File(root, ".download").apply { mkdirs() }
            val zip = File(downloads, e.file.name)
            if (!(zip.isFile && zip.length() == e.file.size && Updates.sha256(zip) == e.file.sha256)) {
                Updates.downloadFile(context, e.file, zip, { cancelled }) { got ->
                    val total = e.file.size.takeIf { it > 0 } ?: got
                    step(tr("Lädt herunter ({0} von {1} MB) …", got shr 20, total shr 20), (got.toFloat() / total).coerceIn(0f, 1f))
                }
            }
            step(tr("Entpackt …"), -1f)
            unpack(zip, id, e.version)
            zip.delete()
            AppLog.i("Erweiterung $id ${e.version} eingerichtet")
            _states.update { it + (id to State.Installed(version(id) ?: e.version, sizeOnDisk(dir(id)))) }
            null
        } catch (x: CancellationException) {
            _states.update { it + (id to (version(id)?.let { v -> State.Installed(v, sizeOnDisk(dir(id))) } ?: State.Missing)) }
            tr("Abgebrochen.")
        } catch (x: Exception) {
            AppLog.e("Erweiterung $id einrichten", x)
            val msg = x.message ?: x.javaClass.simpleName
            // An older version keeps working; the message is shown where the setup was started.
            _states.update { it + (id to (version(id)?.let { v -> State.Installed(v, sizeOnDisk(dir(id))) } ?: State.Failed(msg))) }
            msg
        }
    }

    /** Unpacks [zip] next to the folder of [id] and swaps it in; paths outside the folder are refused. */
    fun unpack(zip: File, id: String, version: String) {
        val target = dir(id)
        val fresh = File(root, "$id.neu").apply { deleteRecursively(); mkdirs() }
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            var entry = z.nextEntry
            while (entry != null) {
                val out = File(fresh, entry.name).canonicalFile
                if (!out.path.startsWith(fresh.canonicalPath + File.separator)) throw IllegalStateException("Ungültiger Pfad im Paket: ${entry.name}")
                if (entry.isDirectory) out.mkdirs() else {
                    out.parentFile.mkdirs()
                    out.outputStream().use { z.copyTo(it) }
                    if (out.name.endsWith(".exe") || !out.name.contains('.')) out.setExecutable(true)
                }
                entry = z.nextEntry
            }
        }
        File(fresh, "erweiterung.json").writeText(JSONObject().put("id", id).put("version", version).put("eingerichtet", System.currentTimeMillis()).toString(2))
        val old = File(root, "$id.alt").apply { deleteRecursively() }
        if (target.exists() && !target.renameTo(old)) {
            // A file is in use (Windows): the new version takes over at the next start.
            fresh.renameTo(File(root, "$id.wartet").apply { deleteRecursively() })
            return
        }
        if (!fresh.renameTo(target)) throw IllegalStateException("Ordner ließ sich nicht anlegen: ${target.path}")
        old.deleteRecursively()
    }

    /** Removes [id]; files in use are removed at the next start. */
    fun remove(id: String) {
        val d = dir(id)
        if (!d.deleteRecursively() && d.exists()) File(root, "$id.entfernen").writeText("1")
        _states.update { it + (id to State.Missing) }
        refresh()
    }

    /** At the start: finishes swaps and removals that waited for files in use; removes the browser engine if asked. */
    fun startUp() {
        runCatching {
            ALL.forEach { d ->
                if (File(root, "${d.id}.entfernen").isFile) { dir(d.id).deleteRecursively(); File(root, "${d.id}.entfernen").delete() }
                val waiting = File(root, "${d.id}.wartet")
                if (waiting.isDirectory) {
                    dir(d.id).deleteRecursively()
                    waiting.renameTo(dir(d.id))
                }
                File(root, "${d.id}.alt").deleteRecursively()
                File(root, "${d.id}.neu").deleteRecursively()
            }
            if (browserRemovalAsked.isFile) {
                File(Paths.local, "browser/chromium").deleteRecursively()
                browserRemovalAsked.delete()
            }
        }.onFailure { AppLog.e("Erweiterungen beim Start", it) }
        refresh()
    }

    /** Hook of the update check: installed extensions with a newer version in version.json are brought up to date. */
    fun onUpdateInfo(context: Context, info: UpdateInfo) {
        info.extensions.forEach { e ->
            val id = baseId(e) ?: return@forEach
            val have = version(id) ?: return@forEach
            if (Versions.newer(e.version, have) && _states.value[id] !is State.Installing) {
                kotlinx.coroutines.runBlocking { install(context, id, e) }
            }
        }
    }

    // ── Browser engine (AP-14) ──

    private val browserRemovalAsked get() = File(Paths.local, "browser/entfernen")

    /** Chromium of the browser beside the chat: size on disk, or null when not set up. */
    fun browserBytes(): Long? = File(Paths.local, "browser/chromium").takeIf { it.isDirectory && WebEngine.installed }?.let(::sizeOnDisk)

    val browserRemovalPending: Boolean get() = browserRemovalAsked.isFile

    /** Chromium is in use while the app runs: removed at the next start (it is set up again on the next link). */
    fun removeBrowser(): Boolean {
        if (WebEngine.state.value is WebEngine.State.Ready) {
            browserRemovalAsked.parentFile.mkdirs()
            browserRemovalAsked.writeText("1")
            return false
        }
        File(Paths.local, "browser/chromium").deleteRecursively()
        return true
    }
}
