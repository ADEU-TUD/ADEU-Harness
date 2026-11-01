package io.github.thepytales.llmhub.local

import io.github.thepytales.llmhub.logFailure
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.ApiProvider
import io.github.thepytales.llmhub.data.ProviderKind
import io.github.thepytales.llmhub.ui.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

/** Whether the program that runs models (llama.cpp) is there. */
enum class RuntimeStatus { READY, MISSING, INSTALLING, UNSUPPORTED }

data class LocalState(
    val installed: Set<String> = emptySet(),
    /** Model id → 0..1 while downloading. */
    val downloads: Map<String, Double> = emptyMap(),
    val runtime: RuntimeStatus = RuntimeStatus.MISSING,
    val runtimeProgress: Double? = null,
    /** Model currently loaded by the server, null if it is stopped. */
    val running: String? = null,
    /** "Wird geladen …" while the server loads a model. */
    val starting: String? = null,
    val message: String? = null,
)

/**
 * Language models on this device with llama.cpp: installs models (one GGUF file each, checked by SHA-256), starts
 * llama-server on 127.0.0.1 with a random key when a chat uses a local model, and stops it after ten idle minutes
 * so the memory is free again. The server speaks the OpenAI API, so chats, tools and documents work as with the Uni
 * server. Platform specifics (hardware, program, runtime download) come from [LlamaHost].
 */
class LocalLlm(private val c: AppContainer) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val root = LlamaHost.root(c).apply { mkdirs() }
    private val modelsDir = File(root, "models").apply { mkdirs() }
    private val serverMutex = Mutex()

    private val _state = MutableStateFlow(LocalState())
    val state: StateFlow<LocalState> = _state.asStateFlow()

    private val _hardware = MutableStateFlow<Hardware?>(null)
    val hardware: StateFlow<Hardware?> = _hardware.asStateFlow()

    private val cancel = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private var process: Process? = null
    private var provider: ApiProvider? = null
    @Volatile private var lastUse = 0L
    private var idleWatch: Job? = null

    init {
        refresh()
        scope.launch { _hardware.value = detectHardware() }
        // Leftover server of an earlier run (app killed): stop it.
        scope.launch { LlamaHost.killStale(root) }
    }

    fun file(m: LocalModel) = File(modelsDir, m.file)

    /**
     * The full check (Windows: registry and system functions via JNA) runs in the background and gets
     * [HARDWARE_TIMEOUT_MS]; on PCs where IT rules block or slow it down, the basic details of Java are used instead,
     * so the screen never stays at "wird geprüft".
     */
    private suspend fun detectHardware(): Hardware {
        val full = scope.async { runCatching { LlamaHost.hardware(c, root) }.logFailure("Hardware erkennen").getOrNull() }
        return withTimeoutOrNull(HARDWARE_TIMEOUT_MS) { full.await() }
            ?: runCatching { LlamaHost.basicHardware(c, root) }.logFailure("Hardware (einfach) erkennen").getOrNull()
            ?: Hardware(phone = false, ramGb = 8.0, cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1), limited = true)
    }

    private fun refresh() {
        val installed = LocalCatalog.models.filter { file(it).let { f -> f.isFile && f.length() == it.size } }.map { it.id }.toSet()
        _state.value = _state.value.copy(installed = installed, runtime = LlamaHost.runtimeStatus(c, root))
        c.models.setLocal(LocalCatalog.models.filter { it.id in installed }.map { LocalCatalog.info(it) })
    }

    fun installedModels(): List<LocalModel> = LocalCatalog.models.filter { it.id in _state.value.installed }

    /** The local model for new chats in projects that keep data on the device: the strongest one installed. */
    fun preferredModel(): String? = installedModels().maxByOrNull { it.needGb }?.modelId

    // ------------------------------------------------------------------ installing

    /** Loads the model (resumable) and, if needed, the program that runs it. */
    fun install(id: String) {
        val m = LocalCatalog.get(id) ?: return
        if (_state.value.downloads.containsKey(m.id)) return
        cancel[m.id] = false
        _state.value = _state.value.copy(downloads = _state.value.downloads + (m.id to 0.0), message = null)
        scope.launch {
            try {
                if (LlamaHost.runtimeStatus(c, root) == RuntimeStatus.MISSING) installRuntime()
                val dst = file(m)
                val part = File(dst.path + ".part")
                download(m.url, part, m.size, { cancel[m.id] == true }) { p -> _state.value = _state.value.copy(downloads = _state.value.downloads + (m.id to p)) }
                _state.value = _state.value.copy(downloads = _state.value.downloads + (m.id to 1.0), message = tr("{0} wird geprüft …", m.name))
                verify(part, m)
                if (!part.renameTo(dst)) error(tr("Datei konnte nicht gespeichert werden"))
                _state.value = _state.value.copy(message = tr("{0} ist bereit.", m.name))
            } catch (e: InterruptedException) {
                _state.value = _state.value.copy(message = tr("Download abgebrochen – beim nächsten Mal wird fortgesetzt."))
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    message = tr("Download von {0} fehlgeschlagen: {1}. Im Firmennetz gesperrt? Die Datei im Browser laden und mit „Datei einspielen“ übernehmen.", m.name, e.message),
                )
            } finally {
                _state.value = _state.value.copy(downloads = _state.value.downloads - m.id)
                refresh()
            }
        }
    }

    fun cancelInstall(id: String) { cancel[id] = true }

    /** Windows: the llama.cpp program (CPU, with a graphics card also Vulkan); Android has it built in. */
    suspend fun installRuntime() {
        _state.value = _state.value.copy(runtime = RuntimeStatus.INSTALLING, runtimeProgress = 0.0)
        try {
            LlamaHost.installRuntime(c, root, gpu = (_hardware.value?.vramGb ?: 0.0) >= 4) { p -> _state.value = _state.value.copy(runtimeProgress = p) }
        } finally {
            _state.value = _state.value.copy(runtime = LlamaHost.runtimeStatus(c, root), runtimeProgress = null)
        }
    }

    fun delete(id: String) {
        val m = LocalCatalog.get(id) ?: return
        scope.launch {
            if (_state.value.running == m.modelId) stop()
            file(m).delete()
            File(file(m).path + ".part").delete()
            refresh()
        }
    }

    /** A model or runtime file loaded by hand (browser, USB stick); recognized by its name. */
    suspend fun importFile(name: String, input: InputStream): String = withContext(Dispatchers.IO) {
        if (name.endsWith(".zip", ignoreCase = true)) {
            input.use { LlamaHost.importRuntime(c, root, it) }
            refresh()
            return@withContext tr("Programm für lokale Modelle eingespielt.")
        }
        val m = LocalCatalog.models.firstOrNull { it.file.equals(name, ignoreCase = true) }
            ?: error(tr("„{0}“ gehört zu keinem Modell der Liste.", name))
        val part = File(file(m).path + ".import")
        input.use { i -> part.outputStream().use { i.copyTo(it, 1 shl 20) } }
        try { verify(part, m) } catch (e: Exception) { part.delete(); throw e }
        file(m).delete()
        part.renameTo(file(m))
        refresh()
        tr("{0} ist bereit.", m.name)
    }

    private fun download(url: String, part: File, total: Long, cancelled: () -> Boolean, progress: (Double) -> Unit) {
        var have = if (part.isFile) part.length() else 0L
        if (have >= total) return
        var u = url
        var con: HttpURLConnection
        var hops = 0
        while (true) {
            con = URL(u).openConnection() as HttpURLConnection
            con.instanceFollowRedirects = false
            con.connectTimeout = 20_000
            con.readTimeout = 60_000
            con.setRequestProperty("User-Agent", "ADEU-LLM")
            if (have > 0) con.setRequestProperty("Range", "bytes=$have-")
            val code = con.responseCode
            if (code in 300..399 && hops++ < 10) { u = URL(URL(u), con.getHeaderField("Location")).toString(); con.disconnect(); continue }
            if (code == 416) { part.delete(); have = 0; con.disconnect(); continue }
            if (code != 200 && code != 206) { con.disconnect(); error("HTTP $code") }
            if (code == 200) have = 0
            break
        }
        con.inputStream.use { input ->
            java.io.FileOutputStream(part, have > 0).use { out ->
                val buf = ByteArray(1 shl 16)
                var got = have
                var last = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    got += n
                    if (cancelled()) throw InterruptedException()
                    val now = System.currentTimeMillis()
                    if (now - last > 400) { last = now; progress((got.toDouble() / total).coerceAtMost(0.999)) }
                }
            }
        }
    }

    private fun verify(f: File, m: LocalModel) {
        if (f.length() != m.size) error(tr("Datei unvollständig ({0} statt {1} Bytes)", f.length(), m.size))
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i -> val b = ByteArray(1 shl 20); while (true) { val n = i.read(b); if (n <= 0) break; md.update(b, 0, n) } }
        val sha = md.digest().joinToString("") { "%02x".format(it) }
        if (sha != m.sha256) error(tr("Prüfsumme stimmt nicht – die Datei ist beschädigt"))
    }

    // ------------------------------------------------------------------ server

    /**
     * The OpenAI-compatible endpoint for [modelId]: starts llama-server with this model (stopping another one)
     * and waits until it has loaded. Called by the LLM client before each request to a local model.
     */
    suspend fun provider(modelId: String): ApiProvider = serverMutex.withLock {
        lastUse = System.currentTimeMillis()
        val m = LocalCatalog.get(modelId) ?: error(tr("Unbekanntes lokales Modell: {0}", modelId))
        provider?.takeIf { _state.value.running == m.modelId && process?.isAlive == true }?.let { return it }
        stopLocked()
        if (m.id !in _state.value.installed) error(tr("{0} ist nicht installiert (Einstellungen › Modelle auf diesem Gerät).", m.name))
        val bin = LlamaHost.binary(c, root) ?: error(tr("Das Programm für lokale Modelle fehlt (Einstellungen › Modelle auf diesem Gerät › Installieren)."))
        val hw = _hardware.value ?: detectHardware().also { _hardware.value = it }
        val port = ServerSocket(0).use { it.localPort }
        val key = UUID.randomUUID().toString()
        val gpu = hw.vramGb >= 4 && m.needGb <= hw.vramGb * 0.92
        val threads = (if (hw.phone) (hw.cores - 2) else (hw.cores / 2)).coerceIn(2, 8)
        val args = listOf(
            bin.absolutePath, "-m", file(m).absolutePath, "--host", "127.0.0.1", "--port", port.toString(),
            "--api-key", key, "--jinja", "-c", m.context.toString(), "-t", threads.toString(), "-np", "1",
            "-ngl", if (gpu) "999" else "0",
        )
        _state.value = _state.value.copy(starting = m.name, message = null)
        try {
            val p = withContext(Dispatchers.IO) {
                ProcessBuilder(args).directory(bin.parentFile).redirectErrorStream(true)
                    .redirectOutput(File(root, "server.log")).also { LlamaHost.environment(c, it.environment()) }.start()
            }
            process = p
            LlamaHost.rememberPid(root, p)
            val base = "http://127.0.0.1:$port"
            // Loading takes a few seconds to a minute (large models, slow disks).
            val deadline = System.currentTimeMillis() + 5 * 60_000
            while (true) {
                if (!p.isAlive) error(tr("Der Modell-Server hat sich beendet:\n") + File(root, "server.log").readLines().takeLast(6).joinToString("\n"))
                if (System.currentTimeMillis() > deadline) error(tr("Das Modell lädt zu lange."))
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        (URL("$base/health").openConnection() as HttpURLConnection).run {
                            connectTimeout = 1000; readTimeout = 2000
                            responseCode.also { disconnect() } == 200
                        }
                    }.getOrDefault(false)
                }
                if (ok) break
                delay(500)
            }
            val prov = ApiProvider(id = LOCAL_PROVIDER_ID, kind = ProviderKind.OPENAI.name, name = "Auf diesem Gerät", baseUrl = "$base/v1", apiKey = key)
            provider = prov
            _state.value = _state.value.copy(running = m.modelId, starting = null)
            watchIdle()
            prov
        } catch (e: Exception) {
            stopLocked()
            _state.value = _state.value.copy(starting = null, message = e.message)
            throw e
        }
    }

    private fun watchIdle() {
        idleWatch?.cancel()
        idleWatch = scope.launch {
            while (isActive) {
                delay(30_000)
                if (System.currentTimeMillis() - lastUse > IDLE_MS) { stop(); break }
            }
        }
    }

    /** Called during a long answer so the idle timer does not stop the server. */
    fun touch() { lastUse = System.currentTimeMillis() }

    suspend fun stop() = serverMutex.withLock { stopLocked() }

    private fun stopLocked() {
        idleWatch?.cancel()
        process?.let { p -> runCatching { p.destroy(); if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly() } }
        process = null
        provider = null
        LlamaHost.forgetPid(root)
        _state.value = _state.value.copy(running = null, starting = null)
    }

    /** Synchronous stop when the app ends (Windows: closing via the tray). */
    fun shutdown() {
        runCatching { process?.destroyForcibly() }
        LlamaHost.forgetPid(root)
    }

    companion object {
        const val LOCAL_PROVIDER_ID = "local-device"
        private const val IDLE_MS = 10 * 60_000L
        private const val HARDWARE_TIMEOUT_MS = 8_000L
    }
}
