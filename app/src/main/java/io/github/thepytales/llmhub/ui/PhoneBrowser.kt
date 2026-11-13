package io.github.thepytales.llmhub.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.data.withTool
import io.github.thepytales.llmhub.research.LiteratureSources
import io.github.thepytales.llmhub.research.WebMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import kotlin.coroutines.resume

/**
 * Browser neben dem Chat auf Android (AP-63), die einfache Fassung des PC-Browsers (AP-14): links from the chat, the
 * literature and documents open in a WebView over the app (phones) or beside it (tablets). Under the page: ask about
 * it, summarize it, take it into the literature or the knowledge collection, open its PDF in the PDF-Werkzeugkasten.
 * One page at a time (no tabs); logins stay (cookies of the WebView). Only on Android – the PC has Chromium (JCEF).
 */
object PhoneBrowser {
    /** The page shown; null = closed. */
    var url by mutableStateOf<String?>(null)

    /** The chat the actions hand the page to (the one shown; null = a new chat). */
    @Volatile
    var chatId: String? = null

    val open: Boolean get() = url != null

    fun open(link: String) {
        url = link
    }

    fun close() {
        url = null
    }

    /** Whether [uri] opens in the browser of the app: http(s), the setting is on, no download page of the app. */
    fun handles(uri: String, enabled: Boolean): Boolean =
        enabled && (uri.startsWith("https://", ignoreCase = true) || uri.startsWith("http://", ignoreCase = true)) && !uri.contains("/releases", ignoreCase = true)
}

/** Opens web links in the app's browser, everything else (mail, telephone, files) as before. */
class PhoneUriHandler(private val fallback: UriHandler, private val enabled: () -> Boolean) : UriHandler {
    override fun openUri(uri: String) {
        if (PhoneBrowser.handles(uri, enabled())) PhoneBrowser.open(uri) else fallback.openUri(uri)
    }
}

/** The browser beside the app on tablets: a panel at the right. */
@Composable
fun PhoneBrowserPanel(c: AppContainer) {
    if (!PhoneBrowser.open) return
    Row(Modifier.fillMaxHeight()) {
        VerticalDivider()
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.width(460.dp).fillMaxHeight()) { PhoneBrowserPane(c) }
    }
}

/** The browser over the whole app on phones. */
@Composable
fun PhoneBrowserOverlay(c: AppContainer) {
    if (!PhoneBrowser.open) return
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.statusBarsPadding().navigationBarsPadding().imePadding()) { PhoneBrowserPane(c) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhoneBrowserPane(c: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(PhoneBrowser.url.orEmpty()) }
    var title by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    /** The address the WebView was last told to load (so a new link is loaded once). */
    var requested by remember { mutableStateOf(PhoneBrowser.url) }
    val web = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            CookieManager.getInstance().setAcceptCookie(true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val u = request.url.toString()
                    if (u.startsWith("http://") || u.startsWith("https://")) return false
                    // mailto:, tel:, intent: … go to the apps made for them.
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    return true
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    address = url
                    requested = url
                    PhoneBrowser.url = url
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                    address = url
                    canBack = view.canGoBack()
                    canForward = view.canGoForward()
                }

                override fun onPageFinished(view: WebView, url: String) {
                    canBack = view.canGoBack()
                    canForward = view.canGoForward()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress = newProgress
                }

                override fun onReceivedTitle(view: WebView, t: String?) {
                    title = t.orEmpty()
                }
            }
            setDownloadListener { url, _, disposition, mime, _ ->
                if (mime.contains("pdf") || url.substringBefore('?').lowercase().endsWith(".pdf") || disposition.orEmpty().lowercase().contains(".pdf")) {
                    scope.launch {
                        runCatching { openPdf(c, context.cacheDir, url, title) }.onFailure { Notices.error(it.message ?: tr("Das PDF ließ sich nicht laden.")) }
                    }
                } else runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            loadUrl(PhoneBrowser.url.orEmpty())
        }
    }
    DisposableEffect(web) {
        onDispose {
            web.stopLoading()
            web.destroy()
        }
    }
    // A new link while the browser is open (from the chat beside it on tablets).
    LaunchedEffect(PhoneBrowser.url) {
        val wanted = PhoneBrowser.url ?: return@LaunchedEffect
        if (wanted != requested && wanted != web.url) {
            requested = wanted
            address = wanted
            web.loadUrl(wanted)
        }
    }
    PlatformBackHandler(enabled = true) { if (web.canGoBack()) web.goBack() else PhoneBrowser.close() }

    suspend fun read(): WebMeta? = suspendCancellableCoroutine { cont ->
        web.evaluateJavascript(WebMeta.READ_SCRIPT) { result -> cont.resume(WebMeta.fromScript(web.url ?: address, result)) }
    }
    fun act(label: String, block: suspend (WebMeta) -> Unit) {
        if (busy != null) return
        busy = label
        scope.launch {
            val page = read()
            if (page == null || page.text.isBlank() && !page.isPdf) Notices.error(tr("Die Seite ließ sich nicht lesen."))
            else runCatching { block(page) }.onFailure { e -> Notices.error(e.message ?: label) }
            busy = null
        }
    }
    fun toChat(page: WebMeta, text: String, send: Boolean) {
        val (body, cut) = page.asAttachment()
        val file = FileAttachment(name = "Webseite – " + page.title.ifBlank { page.host }.take(60) + ".txt", mime = "text/plain", text = body, truncated = cut, public = true)
        ChatHandoff.pending.value = ChatHandoff.Content(
            text, listOf(file), chatId = PhoneBrowser.chatId,
            mode = io.github.thepytales.llmhub.agent.ChatModes.SOURCES.id.takeIf { PhoneBrowser.chatId == null }, send = send,
        )
        ChatHandoff.open.value = PhoneBrowser.chatId.orEmpty()
        // On a phone the chat is under the browser: it goes away so the answer can be seen.
        PhoneBrowser.close()
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { PhoneBrowser.close() }) { Icon(Icons.Outlined.Close, contentDescription = tr("Browser schließen")) }
            IconButton(onClick = { web.goBack() }, enabled = canBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = tr("Zurück")) }
            IconButton(onClick = { web.goForward() }, enabled = canForward) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = tr("Vor")) }
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.weight(1f).height(38.dp)) {
                Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                    fun go() {
                        val t = address.trim()
                        if (t.isBlank()) return
                        val u = when {
                            t.startsWith("http://") || t.startsWith("https://") -> t
                            ' ' !in t && '.' in t -> "https://$t"
                            else -> "https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(t, "UTF-8")
                        }
                        requested = u
                        PhoneBrowser.url = u
                        web.loadUrl(u)
                    }
                    BasicTextField(
                        value = address, onValueChange = { address = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { go() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            IconButton(onClick = { web.reload() }) { Icon(Icons.Outlined.Refresh, contentDescription = tr("Neu laden")) }
            IconButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web.url ?: address)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = tr("Im normalen Browser öffnen")) }
        }
        if (progress in 1..99 || busy != null) LinearProgressIndicator(progress = { if (busy != null) 0.5f else progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp))
        AndroidView(factory = { web }, modifier = Modifier.weight(1f).fillMaxWidth())
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = { act(tr("Frage")) { p -> toChat(p, tr("Frage zur Seite „{0}“: ", p.title.ifBlank { p.host }), send = false) } },
                label = { Text(tr("Frage zur Seite")) }, leadingIcon = { Icon(Icons.Outlined.QuestionAnswer, null, Modifier.size(16.dp)) })
            AssistChip(onClick = {
                act(tr("Zusammenfassen")) { p -> toChat(p, tr("Fasse die angehängte Webseite kurz zusammen – die wichtigsten Aussagen mit Belegen aus dem Text."), send = true) }
            }, label = { Text(tr("Zusammenfassen")) }, leadingIcon = { Icon(Icons.Outlined.Summarize, null, Modifier.size(16.dp)) })
            AssistChip(onClick = {
                act(tr("Literatur")) { p ->
                    val fromDoi = p.doi?.let { doi -> withContext(Dispatchers.IO) { runCatching { LiteratureSources(c.llm.http).crossrefDoi(doi) }.getOrNull() } }
                    val ref = fromDoi?.copy(url = fromDoi.url.ifBlank { p.url }, openAccessUrl = fromDoi.openAccessUrl.ifBlank { p.pdfUrl.orEmpty() }) ?: p.reference()
                    c.literature.addAll(listOf(ref))
                    Notices.info(tr("In die Literatur übernommen: ") + ref.title.take(80))
                }
            }, label = { Text(tr("In die Literatur")) }, leadingIcon = { Icon(Icons.Outlined.MenuBook, null, Modifier.size(16.dp)) })
            AssistChip(onClick = {
                act(tr("Wissen")) { p ->
                    val (body, _) = p.asAttachment()
                    withContext(Dispatchers.IO) { c.knowledge.add(p.title.ifBlank { p.host }.take(80), body, "") }
                    Notices.info(tr("In der Wissenssammlung: ") + p.title.ifBlank { p.host }.take(80))
                }
            }, label = { Text(tr("In die Wissenssammlung")) }, leadingIcon = { Icon(Icons.Outlined.LibraryBooks, null, Modifier.size(16.dp)) })
            if (address.substringBefore('?').lowercase().endsWith(".pdf")) AssistChip(onClick = {
                act("PDF") { p -> openPdf(c, context.cacheDir, p.pdfUrl ?: p.url, p.title) }
            }, label = { Text(tr("PDF im Werkzeugkasten")) }, leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null, Modifier.size(16.dp)) })
        }
    }
}

/** Loads the PDF at [url] with the browser's cookies (papers behind the TU login) and opens it in the PDF-Werkzeugkasten. */
private suspend fun openPdf(c: AppContainer, cache: File, url: String, title: String) {
    val file = withContext(Dispatchers.IO) {
        val cookies = CookieManager.getInstance().getCookie(url)
        val request = Request.Builder().url(url).apply { if (!cookies.isNullOrBlank()) header("Cookie", cookies) }.build()
        c.llm.http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) error(tr("PDF nicht erreichbar (HTTP {0})", r.code))
            val bytes = r.body?.bytes() ?: error(tr("PDF leer"))
            if (!(bytes.size > 4 && String(bytes, 0, 4) == "%PDF")) error(tr("Unter der Adresse liegt kein PDF (vielleicht erst anmelden)."))
            val name = title.ifBlank { url.substringAfterLast('/').substringBefore('?') }.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80).removeSuffix(".pdf") + ".pdf"
            File(File(cache, "browser").apply { mkdirs() }, name).apply { writeBytes(bytes) }
        }
    }
    if (!c.settings.current.toolOn(io.github.thepytales.llmhub.data.AppTool.PDF.id)) {
        c.settings.update { s -> s.withTool(io.github.thepytales.llmhub.data.AppTool.PDF, true) }
        Notices.info(tr("PDF-Werkzeugkasten eingeschaltet"))
    }
    PhoneBrowser.close()
    PdfHandoff.open(file.absolutePath, name = file.name)
}
