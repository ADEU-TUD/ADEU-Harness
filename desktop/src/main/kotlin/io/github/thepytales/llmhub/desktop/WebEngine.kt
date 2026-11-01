package io.github.thepytales.llmhub.desktop

import io.github.thepytales.llmhub.ui.tr
import io.github.thepytales.llmhub.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.EnumProgress
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter
import org.cef.CefApp
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefBeforeDownloadCallback
import org.cef.callback.CefDownloadItem
import org.cef.callback.CefDownloadItemCallback
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefDownloadHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefCookie
import org.cef.network.CefCookieManager
import java.io.File
import kotlin.coroutines.resume

/**
 * The browser beside the chat (AP-14): Chromium through JCEF – no engine of our own. Chromium is large (about
 * 170 MB), so it is not part of every update but set up once on first use into the local folder
 * (%LOCALAPPDATA%\ADEU LLM\browser, see [install]); its profile there keeps cookies, so a login (e.g. the TU's
 * Shibboleth) stays. [events] report title, address and loading of each browser to the panel.
 */
object WebEngine {
    /** Where Chromium is set up. */
    sealed interface State {
        /** Not set up yet (a link asks once). */
        data object Missing : State

        /** Being downloaded, unpacked or started ([percent] 0..100, or negative when unknown). */
        data class Preparing(val step: String, val percent: Float) : State

        data object Ready : State

        data class Failed(val message: String) : State
    }

    val state = MutableStateFlow<State>(State.Missing)

    private val root get() = File(Paths.local, "browser")
    private val installDir get() = File(root, "chromium")
    private val profileDir get() = File(root, "profil")

    /** Chromium is there (set up earlier); starting it still takes a moment. */
    val installed: Boolean get() = File(installDir, "install.lock").isFile

    /** Written before Chromium starts, removed once it runs: still there at the next start = it hung or crashed. */
    private val pending get() = File(root, "start.pending")

    /** The last start did not finish – the panel asks instead of starting Chromium by itself again. */
    val lastStartFailed: Boolean get() = pending.isFile

    /** JCEF has builds for Windows, Linux and macOS on x64/arm64. */
    val supported: Boolean
        get() {
            val arch = System.getProperty("os.arch").orEmpty().lowercase()
            return arch.contains("64")
        }

    private var app: CefApp? = null
    private var client: CefClient? = null

    /** Changes of a browser: title, address, loading, a link that wants a new tab, a finished download. */
    sealed interface Event {
        val browser: CefBrowser

        data class Title(override val browser: CefBrowser, val title: String) : Event
        data class Address(override val browser: CefBrowser, val url: String) : Event
        data class Loading(override val browser: CefBrowser, val loading: Boolean, val back: Boolean, val forward: Boolean) : Event
        data class Popup(override val browser: CefBrowser, val url: String) : Event
        data class Downloaded(override val browser: CefBrowser, val file: File, val mime: String) : Event
    }

    /** Receives [Event]s (set by the panel). */
    @Volatile
    var events: (Event) -> Unit = {}

    /**
     * Sets Chromium up (once) and starts it; progress goes to [state]. Safe to call again: a running engine is
     * returned at once.
     */
    suspend fun start(): CefClient? = withContext(Dispatchers.IO) {
        client?.let { return@withContext it }
        if (!supported) {
            state.value = State.Failed(tr("Auf diesem Rechner gibt es den eingebauten Browser nicht."))
            return@withContext null
        }
        runCatching {
            state.value = State.Preparing(tr("Wird vorbereitet …"), -1f)
            val builder = CefAppBuilder()
            builder.setInstallDir(installDir)
            builder.setProgressHandler { step, percent ->
                state.value = State.Preparing(
                    when (step) {
                        EnumProgress.LOCATING -> tr("Sucht Chromium …")
                        EnumProgress.DOWNLOADING -> tr("Lädt Chromium herunter (einmalig, ca. 170 MB) …")
                        EnumProgress.EXTRACTING -> tr("Entpackt …")
                        EnumProgress.INSTALL -> tr("Richtet ein …")
                        else -> tr("Startet den Browser …")
                    },
                    if (percent == EnumProgress.NO_ESTIMATION) -1f else percent,
                )
            }
            builder.cefSettings.windowless_rendering_enabled = false
            profileDir.mkdirs()
            builder.cefSettings.root_cache_path = profileDir.absolutePath
            builder.cefSettings.cache_path = profileDir.absolutePath
            builder.cefSettings.persist_session_cookies = true
            builder.cefSettings.locale = "de"
            builder.cefSettings.log_severity = org.cef.CefSettings.LogSeverity.LOGSEVERITY_WARNING
            builder.cefSettings.log_file = File(root, "chromium.log").absolutePath
            // Linux as root (containers, tests): Chromium refuses its sandbox there and has no GPU.
            if (!Paths.windows && System.getProperty("user.name") == "root") builder.addJcefArgs("--no-sandbox", "--disable-gpu")
            builder.setAppHandler(object : MavenCefAppHandlerAdapter() {})
            root.mkdirs()
            pending.writeText(System.currentTimeMillis().toString())
            val a = builder.build()
            val c = a.createClient()
            c.addDisplayHandler(object : CefDisplayHandlerAdapter() {
                override fun onTitleChange(browser: CefBrowser, title: String?) = events(Event.Title(browser, title.orEmpty()))
                override fun onAddressChange(browser: CefBrowser, frame: CefFrame?, url: String?) {
                    if (frame == null || frame.isMain) events(Event.Address(browser, url.orEmpty()))
                }
            })
            c.addLoadHandler(object : CefLoadHandlerAdapter() {
                override fun onLoadingStateChange(browser: CefBrowser, isLoading: Boolean, canGoBack: Boolean, canGoForward: Boolean) =
                    events(Event.Loading(browser, isLoading, canGoBack, canGoForward))
            })
            c.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
                // Links that want a new window open as a tab of the panel instead.
                override fun onBeforePopup(browser: CefBrowser, frame: CefFrame?, targetUrl: String?, targetFrameName: String?): Boolean {
                    targetUrl?.takeIf { it.isNotBlank() }?.let { events(Event.Popup(browser, it)) }
                    return true
                }
            })
            c.addDownloadHandler(object : CefDownloadHandlerAdapter() {
                override fun onBeforeDownload(browser: CefBrowser, item: CefDownloadItem, suggestedName: String?, callback: CefBeforeDownloadCallback): Boolean {
                    val dir = File(File(System.getProperty("user.home")), "Downloads").takeIf { it.isDirectory } ?: Paths.exports.apply { mkdirs() }
                    val name = (suggestedName ?: item.suggestedFileName ?: "download").replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    callback.Continue(uniqueFile(dir, name).absolutePath, false)
                    return true
                }

                override fun onDownloadUpdated(browser: CefBrowser, item: CefDownloadItem, callback: CefDownloadItemCallback) {
                    if (item.isComplete) item.fullPath?.let { events(Event.Downloaded(browser, File(it), item.mimeType.orEmpty())) }
                }
            })
            app = a
            client = c
            pending.delete()
            state.value = State.Ready
            AppLog.i("Browser bereit (Chromium über JCEF)")
            c
        }.getOrElse { e ->
            AppLog.e("Browser starten", e)
            state.value = State.Failed(e.message ?: e.javaClass.simpleName)
            null
        }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        var i = 2
        while (f.exists()) {
            f = File(dir, name.substringBeforeLast('.') + " ($i)" + name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" })
            i++
        }
        return f
    }

    /** A new browser showing [url] (windowed, not off-screen); its component goes into the panel. */
    fun open(url: String): CefBrowser? = client?.createBrowser(url, false, false)

    /** The visible text of the page in [browser] (null after [timeoutMs]). */
    suspend fun text(browser: CefBrowser, timeoutMs: Long = 6000): String? = visit(timeoutMs) { browser.getText(it) }

    /** The HTML of the page in [browser], e.g. for the citation meta tags of a paper. */
    suspend fun source(browser: CefBrowser, timeoutMs: Long = 6000): String? = visit(timeoutMs) { browser.getSource(it) }

    private suspend fun visit(timeoutMs: Long, call: (org.cef.callback.CefStringVisitor) -> Unit): String? = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            call { s -> if (cont.isActive) cont.resume(s) }
        }
    }

    /** The cookies the browser holds for [url] as a Cookie header (to download a PDF behind a login), or null. */
    suspend fun cookieHeader(url: String, timeoutMs: Long = 3000): String? = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            val found = mutableListOf<String>()
            val ok = CefCookieManager.getGlobalManager()?.visitUrlCookies(url, true) { cookie: CefCookie, count: Int, total: Int, _: BoolRef ->
                found += "${cookie.name}=${cookie.value}"
                if (count >= total - 1 && cont.isActive) cont.resume(found.joinToString("; "))
                true
            } ?: false
            if (!ok && cont.isActive) cont.resume(null)
        }
    }

    /** Ends Chromium (when the app ends). */
    fun dispose() {
        runCatching { client?.dispose() }
        runCatching { app?.dispose() }
        client = null
        app = null
    }
}
