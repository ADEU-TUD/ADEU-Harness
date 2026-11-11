package io.github.thepytales.llmhub.update

import android.content.Context
import io.github.thepytales.llmhub.BuildConfig
import io.github.thepytales.llmhub.ErrorLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import io.github.thepytales.llmhub.ui.tr

/**
 * Automatic updates for colleagues without technical background: at every start (and every few hours while the app
 * runs in the notification area) the app reads version.json of the newest release in the public repository
 * ADEU-TUD/ADEU-Harness-Updater – a plain download, no GitHub API and no account. A newer version shows a popup
 * (UpdatePrompt); "Jetzt aktualisieren" downloads the file for this platform, checks its SHA-256 and installs it
 * ([UpdatePlatform]: Windows swaps the app folder and restarts, Android opens the system installer).
 *
 * For PCs that cannot reach GitHub, Einstellungen › Updates takes a folder (e.g. on a network drive) with the files of
 * a release instead.
 */
object Updates {
    const val REPOSITORY = "ADEU-TUD/ADEU-Harness-Updater"
    const val DEFAULT_SOURCE = "https://github.com/$REPOSITORY/releases/latest/download/version.json"
    const val RELEASES_PAGE = "https://github.com/$REPOSITORY/releases/latest"
    private const val PERIOD = 6 * 3_600_000L

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val info: UpdateInfo) : State
        data class Downloading(val info: UpdateInfo, val progress: Float, val step: String) : State
        /** Downloaded and checked; [prepared] is what [UpdatePlatform.install] needs. [note] says what is missing (Android: permission). */
        data class Ready(val info: UpdateInfo, val prepared: File, val note: String? = null) : State
        data class Failed(val info: UpdateInfo?, val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Version whose popup was put off with "Später" – asked again at the next start. */
    private val _postponed = MutableStateFlow<String?>(null)
    val postponed: StateFlow<String?> = _postponed.asStateFlow()

    /** The download runs on without its dialog ("Im Hintergrund"); then the app asks before restarting. */
    private val _background = MutableStateFlow(false)
    val background: StateFlow<Boolean> = _background.asStateFlow()

    fun toBackground() {
        _background.value = true
    }

    /** Set for the start test of CI: no popups, no network. */
    @Volatile
    var disabled = false

    private var autoJob: Job? = null
    private var downloadJob: Job? = null

    /** Set by [cancelDownload]: the blocking download loop checks it. */
    @Volatile
    private var stopDownload = false

    private fun prefs(context: Context) = context.getSharedPreferences("updates", 0)

    val installed: String get() = BuildConfig.VERSION_NAME

    fun autoCheck(context: Context): Boolean = prefs(context).getBoolean("auto", true)

    fun setAutoCheck(context: Context, on: Boolean) = prefs(context).edit().putBoolean("auto", on).apply()

    /**
     * Where version.json comes from: empty = GitHub, otherwise a folder (network drive) or another address. Without an
     * own setting the one the app folder brings along ("update-quelle.txt" next to ADEU LLM.exe, see UpdatePlatform).
     */
    fun source(context: Context): String =
        prefs(context).getString("source", "").orEmpty().ifBlank { UpdatePlatform.presetSource().orEmpty() }

    /** What was entered in the settings (without the preset of the app folder). */
    fun ownSource(context: Context): String = prefs(context).getString("source", "").orEmpty()

    fun setSource(context: Context, value: String) = prefs(context).edit().putString("source", value.trim()).apply()

    /**
     * Called when the window is first shown: removes what an earlier update left behind, tells once that the app was
     * updated (with the notes of that version, kept at install), then checks now and every few hours.
     */
    fun start(context: Context, onUpdated: (version: String, notes: String?) -> Unit) {
        if (disabled || !UpdatePlatform.supported) return
        val p = prefs(context)
        val last = p.getString("last_version", null)
        if (last != null && Versions.newer(installed, last)) {
            onUpdated(installed, p.getString("notes", null)?.takeIf { p.getString("notes_version", null) == installed && it.isNotBlank() })
        }
        if (last != installed) p.edit().putString("last_version", installed).apply()
        if (autoJob != null) return
        autoJob = scope.launch {
            // Lets the app finish starting (and the helper of an update end) before anything is deleted.
            delay(20_000)
            runCatching { UpdatePlatform.cleanup(context) }.onFailure { ErrorLog.record("Update aufräumen", it) }
        }
        scope.launch {
            delay(3_000)
            while (true) {
                if (autoCheck(context)) check(context, manual = false)
                delay(PERIOD)
            }
        }
    }

    /** Called when the update check reached its source (set by the app container, AP-112). */
    @Volatile var onInternet: (() -> Unit)? = null

    /** Looks for a newer version; [manual] (Einstellungen › Updates) also shows the popup again after "Später". */
    suspend fun check(context: Context, manual: Boolean): State = withContext(Dispatchers.IO) {
        if (disabled || !UpdatePlatform.supported) return@withContext _state.value
        val busy = _state.value
        if (busy is State.Downloading || busy is State.Checking) return@withContext busy
        if (manual) _postponed.value = null
        if (manual || busy !is State.Ready) _state.value = State.Checking
        val result = try {
            val info = UpdateInfo.parse(readText(context, source(context)))
            // Rückfall (AP-112): the Internet works – a Uni-Server that does not answer meanwhile is really gone.
            onInternet?.invoke()
            // Reloadable extensions (AP-25) come with the same version.json and are kept up to date here.
            onInfo?.let { hook -> runCatching { hook(context, info) }.onFailure { ErrorLog.record("Erweiterungen prüfen", it) } }
            when {
                !Versions.newer(info.version, installed) -> State.UpToDate
                info.files[UpdatePlatform.KEY] == null -> State.Failed(info, tr("Version {0} enthält keine Datei für dieses Gerät.", info.version))
                // Downloaded before ("Später" after the download): ready right away.
                else -> UpdatePlatform.prepared(context, info)?.let { State.Ready(info, it) } ?: State.Available(info)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ErrorLog.record("Update-Prüfung", e)
            // Without network nobody needs to know at the start; the settings show it.
            if (manual) State.Failed(null, explain(e)) else (busy.takeIf { it is State.Ready } ?: State.Idle)
        }
        _state.value = result
        result
    }

    /** "Später": no popup for this version until the next start. */
    fun postpone() {
        (_state.value as? State.Available)?.info?.version?.let { _postponed.value = it }
        (_state.value as? State.Ready)?.info?.version?.let { _postponed.value = it }
        (_state.value as? State.Failed)?.let { f -> _postponed.value = f.info?.version ?: installed }
    }

    /** Downloads, checks and prepares the update, then hands it to [onReady] (which usually installs it). */
    fun download(context: Context, onReady: (State.Ready) -> Unit) {
        val info = when (val s = _state.value) {
            is State.Available -> s.info
            is State.Failed -> s.info ?: return
            is State.Ready -> return onReady(s)
            else -> return
        }
        val file = info.files[UpdatePlatform.KEY] ?: return
        downloadJob?.cancel()
        stopDownload = false
        _background.value = false
        downloadJob = scope.launch {
            try {
                // Already downloaded and unpacked (e.g. "Erneut versuchen" after a failed restart).
                UpdatePlatform.prepared(context, info)?.let { done ->
                    val ready = State.Ready(info, done)
                    _state.value = ready
                    withContext(Dispatchers.Main) { onReady(ready) }
                    return@launch
                }
                UpdatePlatform.problem(context, file)?.let { throw UpdateProblem(it) }
                val dir = UpdatePlatform.downloadDir(context).apply { mkdirs() }
                val target = File(dir, file.name)
                if (!(target.isFile && target.length() == file.size && sha256(target) == file.sha256)) {
                    val tmp = File(dir, file.name + ".part")
                    _state.value = State.Downloading(info, 0f, tr("Wird heruntergeladen …"))
                    fetch(context, file, tmp, { stopDownload }) { got ->
                        val total = file.size.takeIf { it > 0 } ?: got
                        _state.value = State.Downloading(info, (got.toFloat() / total).coerceIn(0f, 1f), tr("Wird heruntergeladen …"))
                    }
                    _state.value = State.Downloading(info, 1f, tr("Wird geprüft …"))
                    if (sha256(tmp) != file.sha256) {
                        tmp.delete()
                        throw UpdateProblem(tr("Die heruntergeladene Datei ist unvollständig oder verändert (Prüfsumme stimmt nicht). Bitte noch einmal versuchen."))
                    }
                    target.delete()
                    if (!tmp.renameTo(target)) throw IOException("Datei konnte nicht umbenannt werden: ${tmp.path}")
                }
                val prepared = UpdatePlatform.prepare(context, target, info) { p ->
                    _state.value = State.Downloading(info, p, tr("Wird vorbereitet …"))
                }
                val ready = State.Ready(info, prepared)
                _state.value = ready
                withContext(Dispatchers.Main) { onReady(ready) }
            } catch (e: CancellationException) {
                _state.value = State.Available(info)
                throw e
            } catch (e: Exception) {
                ErrorLog.record("Update herunterladen", e)
                _state.value = State.Failed(info, explain(e))
            }
        }
    }

    /**
     * Called with every version.json read by [check] (Windows: brings installed extensions up to date, AP-25). Runs on
     * the IO thread of the check.
     */
    @Volatile
    var onInfo: ((Context, UpdateInfo) -> Unit)? = null

    /** version.json of the update source right now (GitHub or the folder of Einstellungen › Updates). */
    fun readInfo(context: Context): UpdateInfo = UpdateInfo.parse(readText(context, source(context)))

    /**
     * Downloads [file] of the update source into [dest] (an extension, AP-25) and checks its SHA-256; [cancelled] stops
     * it. Throws with a readable message.
     */
    fun downloadFile(context: Context, file: UpdateFile, dest: File, cancelled: () -> Boolean = { false }, progress: (Long) -> Unit) {
        val tmp = File(dest.path + ".part")
        try {
            fetch(context, file, tmp, cancelled, progress)
            if (sha256(tmp) != file.sha256) throw UpdateProblem(tr("Die heruntergeladene Datei ist unvollständig oder verändert (Prüfsumme stimmt nicht). Bitte noch einmal versuchen."))
            dest.delete()
            if (!tmp.renameTo(dest)) throw IOException("Datei konnte nicht umbenannt werden: ${tmp.path}")
        } catch (e: Exception) {
            tmp.delete()
            if (e is CancellationException) throw e
            throw IOException(explain(e), e)
        }
    }

    fun cancelDownload() {
        stopDownload = true
        downloadJob?.cancel()
    }

    /** Installs a downloaded update (Windows: ends the app and restarts it). */
    fun install(context: Context) {
        val ready = _state.value as? State.Ready ?: return
        prefs(context).edit().putString("notes_version", ready.info.version).putString("notes", ready.info.notes).apply()
        val note = runCatching { UpdatePlatform.install(context, ready.prepared, ready.info) }.getOrElse { e ->
            ErrorLog.record("Update installieren", e)
            _state.value = State.Failed(ready.info, explain(e))
            return
        }
        _state.value = ready.copy(note = note)
    }

    private class UpdateProblem(message: String) : Exception(message)

    private fun explain(e: Throwable): String = when (e) {
        is UpdateProblem -> e.message.orEmpty()
        is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException ->
            tr("Keine Verbindung zum Update-Server (github.com). Ist das Internet erreichbar? Auf Dienst-PCs kann die IT GitHub sperren – " +
                "dann lässt sich unter Einstellungen › Updates ein Ordner auf dem Netzlaufwerk als Update-Quelle eintragen.")
        is javax.net.ssl.SSLException -> tr("Sichere Verbindung zum Update-Server nicht möglich ({0}). Möglicherweise prüft ein Firmen-Proxy die Verbindung.", e.message)
        else -> e.message ?: e.javaClass.simpleName
    }

    // ── Reading from GitHub or from a folder ──

    private fun isFolder(source: String) = source.isNotBlank() && !source.startsWith("http://", true) && !source.startsWith("https://", true)

    /** version.json of [source]: the default address, another address, a folder or the file itself. */
    private fun readText(context: Context, source: String): String {
        if (isFolder(source)) {
            val f = File(source).let { if (it.isDirectory) File(it, "version.json") else it }
            if (!f.isFile) throw UpdateProblem(tr("In „{0}“ liegt keine version.json – ist das Netzlaufwerk verbunden?", f.parent))
            return f.readText()
        }
        val url = source.ifBlank { DEFAULT_SOURCE }
        val bytes = java.io.ByteArrayOutputStream()
        open(url, 15_000) { input, _ -> input.copyTo(bytes) }
        return bytes.toString(Charsets.UTF_8.name())
    }

    private fun fetch(context: Context, file: UpdateFile, dest: File, cancelled: () -> Boolean, progress: (Long) -> Unit) {
        val source = source(context)
        if (isFolder(source)) {
            val dir = File(source).let { if (it.isDirectory) it else it.parentFile }
            val from = File(dir, file.name)
            if (!from.isFile) throw UpdateProblem(tr("„{0}“ fehlt in „{1}“.", file.name, dir.path))
            from.inputStream().use { copy(it, dest, cancelled, progress) }
            return
        }
        val url = file.url ?: (source.ifBlank { DEFAULT_SOURCE }.substringBeforeLast('/') + "/" + file.name)
        open(url, 30_000) { input, _ -> copy(input, dest, cancelled, progress) }
    }

    private fun copy(input: java.io.InputStream, dest: File, cancelled: () -> Boolean, progress: (Long) -> Unit) {
        dest.outputStream().use { out ->
            val buf = ByteArray(1 shl 16)
            var got = 0L
            var shown = 0L
            while (true) {
                if (cancelled()) throw CancellationException("Abgebrochen")
                val n = input.read(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
                got += n
                if (got - shown > 256 * 1024) { progress(got); shown = got }
            }
            progress(got)
        }
    }

    /** GET with redirects (github.com → its download server), the system proxy and the certificates of Windows. */
    private fun open(url: String, timeout: Int, read: (java.io.InputStream, Long) -> Unit) {
        var u = url
        var hops = 0
        while (true) {
            val con = URL(u).openConnection() as HttpURLConnection
            con.instanceFollowRedirects = false
            con.connectTimeout = timeout
            con.readTimeout = 60_000
            con.setRequestProperty("User-Agent", "ADEU-LLM/$installed")
            con.setRequestProperty("Cache-Control", "no-cache")
            try {
                val code = con.responseCode
                if (code in 300..399 && hops++ < 10) {
                    u = URL(URL(u), con.getHeaderField("Location")).toString()
                    continue
                }
                if (code == 404) throw UpdateProblem(tr("Auf dem Update-Server ist noch keine Version veröffentlicht ({0}).", u))
                if (code != 200) throw IOException(tr("Update-Server antwortet mit HTTP {0} ({1})", code, u))
                con.inputStream.use { read(it, con.contentLengthLong) }
                return
            } finally {
                con.disconnect()
            }
        }
    }

    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i ->
            val b = ByteArray(1 shl 20)
            while (true) {
                val n = i.read(b)
                if (n <= 0) break
                md.update(b, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
