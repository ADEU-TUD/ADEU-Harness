package io.github.thepytales.llmhub.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.AppLog
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.desktop.DesktopActions
import io.github.thepytales.llmhub.desktop.Paths
import io.github.thepytales.llmhub.desktop.WebEngine
import io.github.thepytales.llmhub.desktop.WebPage
import io.github.thepytales.llmhub.research.LiteratureSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.cef.browser.CefBrowser
import java.awt.BorderLayout
import java.awt.CardLayout
import java.io.File
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * The browser beside the chat (AP-14), like in Google Antigravity: links from the chat, the literature and documents
 * open as tabs in a panel at the right of the app – the chat stays readable beside it. Under the page: ask about it
 * (answered with evidence in the Quellenmodus, AP-08), summarize it, take it into the literature or the knowledge
 * collection, open its PDF in the PDF-Werkzeugkasten. Chromium comes through JCEF ([WebEngine]).
 */
object Browser {
    /** One tab: what it shows; [browser] lives while the panel is on screen. */
    class Tab(val id: Long, url: String) {
        var url by mutableStateOf(url)
        var title by mutableStateOf("")
        var loading by mutableStateOf(true)
        var canBack by mutableStateOf(false)
        var canForward by mutableStateOf(false)

        @Volatile
        var browser: CefBrowser? = null
    }

    private var nextId = 1L
    val tabs = mutableStateListOf<Tab>()
    var active by mutableLongStateOf(0L)
    var open by mutableStateOf(false)

    /** The chat the actions hand the page to (the one shown; null = a new chat). */
    @Volatile
    var chatId: String? = null

    val current: Tab? get() = tabs.firstOrNull { it.id == active } ?: tabs.lastOrNull()

    /** Opens [url] in a new tab (or shows the tab that has it already) and the panel. */
    fun open(url: String) {
        val existing = tabs.firstOrNull { it.url == url }
        val tab = existing ?: Tab(nextId++, url).also { tabs += it }
        active = tab.id
        open = true
    }

    fun close(tab: Tab) {
        tab.browser?.let { b -> runCatching { b.close(true) } }
        tab.browser = null
        tabs.remove(tab)
        if (active == tab.id) active = tabs.lastOrNull()?.id ?: 0L
        if (tabs.isEmpty()) open = false
    }

    /** Applies a change reported by Chromium (on its thread) to the tab of that browser. */
    fun onEvent(e: WebEngine.Event) = SwingUtilities.invokeLater {
        val tab = tabs.firstOrNull { it.browser === e.browser }
        when (e) {
            is WebEngine.Event.Title -> tab?.title = e.title
            is WebEngine.Event.Address -> tab?.url = e.url
            is WebEngine.Event.Loading -> tab?.let { it.loading = e.loading; it.canBack = e.back; it.canForward = e.forward }
            is WebEngine.Event.Popup -> open(e.url)
            is WebEngine.Event.Downloaded -> {
                if (e.mime.contains("pdf") || e.file.name.lowercase().endsWith(".pdf")) {
                    Notices.info(tr("PDF gespeichert: {0} – öffnet im PDF-Werkzeugkasten.", e.file.name))
                    PdfHandoff.open(e.file.absolutePath, name = e.file.name)
                } else Notices.info(tr("Gespeichert unter Downloads: {0}", e.file.name))
            }
        }
    }
}

/** Whether a link should open in the panel: http(s), the setting is on, and it is no download page of the app. */
internal fun opensInPanel(uri: String, enabled: Boolean): Boolean =
    enabled && WebEngine.supported && (uri.startsWith("https://", ignoreCase = true) || uri.startsWith("http://", ignoreCase = true)) &&
        !uri.contains("/releases", ignoreCase = true)

/** Opens web links in the browser beside the chat, everything else (mail, files) as before. */
class PanelUriHandler(private val fallback: UriHandler, private val enabled: () -> Boolean) : UriHandler {
    override fun openUri(uri: String) {
        if (opensInPanel(uri, enabled())) Browser.open(uri) else fallback.openUri(uri)
    }
}

private val DEFAULT_WIDTH = 560.dp

/** The panel at the right of the app (wide windows); the app gets narrower instead of covered. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowserSidePanel(c: AppContainer) {
    var width by remember { mutableStateOf(DEFAULT_WIDTH) }
    val density = LocalDensity.current
    AnimatedVisibility(Browser.open, enter = expandHorizontally(tween(240)) + fadeIn(tween(240)), exit = shrinkHorizontally(tween(200)) + fadeOut(tween(160))) {
        Row(Modifier.fillMaxHeight()) {
            // Drag handle: the panel gets wider or narrower (380–1100 dp).
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(6.dp)
                    .pointerHoverIcon(PointerIcon(java.awt.Cursor(java.awt.Cursor.W_RESIZE_CURSOR)))
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta -> width = (width - with(density) { delta.toDp() }).coerceIn(380.dp, 1100.dp) },
                    ),
                contentAlignment = Alignment.Center,
            ) { VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)) }
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.width(width).fillMaxHeight()) {
                BrowserPane(c)
            }
        }
    }
}

/** The panel over the whole app (narrow windows). */
@Composable
fun BrowserOverlay(c: AppContainer) {
    if (!Browser.open) return
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) { BrowserPane(c) }
}

@Composable
private fun BrowserPane(c: AppContainer) {
    val engine by WebEngine.state.collectAsStateWithLifecycle()
    val tab = Browser.current
    // Chromium already set up: start it right away; otherwise the user decides once.
    LaunchedEffect(Unit) {
        WebEngine.events = Browser::onEvent
        if (WebEngine.installed && !WebEngine.lastStartFailed && engine !is WebEngine.State.Ready) WebEngine.start()
    }
    Column(Modifier.fillMaxSize()) {
        TabsRow()
        if (tab != null) AddressRow(tab)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val e = engine) {
                is WebEngine.State.Ready -> if (tab != null) BrowserHost()
                is WebEngine.State.Preparing -> Preparing(e)
                is WebEngine.State.Failed -> Problem(e.message, tab)
                WebEngine.State.Missing -> when {
                    WebEngine.installed && WebEngine.lastStartFailed -> Problem(tr("Beim letzten Mal ist der Browser nicht fertig gestartet."), tab)
                    WebEngine.installed -> Preparing(WebEngine.State.Preparing(tr("Startet den Browser …"), -1f))
                    else -> SetUp(c, tab)
                }
            }
        }
        if (tab != null && engine is WebEngine.State.Ready) PageActions(c, tab)
    }
}

@Composable
private fun TabsRow() {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Browser.tabs.forEach { t ->
                val selected = t.id == Browser.current?.id
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) LocalChatLook.current.brand.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { Browser.active = t.id },
                ) {
                    Row(Modifier.padding(start = 10.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            t.title.ifBlank { t.url.substringAfter("://").substringBefore('/') },
                            style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 160.dp),
                        )
                        IconButton(onClick = { Browser.close(t) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = tr("Tab schließen"), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            IconButton(onClick = { Browser.open("https://duckduckgo.com/") }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = tr("Neuer Tab"), modifier = Modifier.size(18.dp))
            }
        }
        Browser.current?.let { t ->
            IconButton(onClick = { DesktopActions.browse(t.url) }) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = tr("Im normalen Browser öffnen"))
            }
        }
        IconButton(onClick = { Browser.open = false }) { Icon(Icons.Outlined.Close, contentDescription = tr("Browser schließen")) }
    }
}

@Composable
private fun AddressRow(tab: Browser.Tab) {
    var text by remember(tab.id, tab.url) { mutableStateOf(tab.url) }
    fun go() {
        val t = text.trim()
        if (t.isBlank()) return
        val url = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            ' ' !in t && '.' in t -> "https://$t"
            else -> "https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(t, "UTF-8")
        }
        tab.url = url
        tab.browser?.loadURL(url)
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { tab.browser?.goBack() }, enabled = tab.canBack, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = tr("Zurück"), modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = { tab.browser?.goForward() }, enabled = tab.canForward, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = tr("Vor"), modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = { tab.browser?.reload() }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Outlined.Refresh, contentDescription = tr("Neu laden"), modifier = Modifier.size(18.dp))
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.weight(1f).height(34.dp),
        ) {
            Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { go() }),
                    modifier = Modifier.fillMaxWidth().onEnter(::go),
                )
            }
        }
    }
    if (tab.loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
}

/** Enter in the address field (Compose Desktop sends no IME action for a hardware Enter). */
private fun Modifier.onEnter(action: () -> Unit): Modifier = onPreviewKeyEvent { e ->
    if (e.type == KeyEventType.KeyDown && e.key == Key.Enter) {
        action()
        true
    } else false
}

/**
 * The browsers of all tabs in one Swing panel (one card per tab), so switching tabs keeps each page as it is. When the
 * panel leaves the screen the browsers are closed; the tabs stay and load again when it comes back.
 */
@Composable
private fun BrowserHost() {
    val cards = remember { CardLayout() }
    val host = remember { JPanel(cards) }
    val tab = Browser.current
    DisposableEffect(Unit) {
        onDispose {
            Browser.tabs.forEach { t -> t.browser?.let { runCatching { it.close(true) } }; t.browser = null }
            host.removeAll()
        }
    }
    LaunchedEffect(tab?.id, Browser.tabs.size) {
        // Tabs that were closed lose their card.
        host.components.filter { comp -> Browser.tabs.none { it.id.toString() == comp.name } }.forEach(host::remove)
        if (tab != null) {
            if (tab.browser == null) {
                val b = WebEngine.open(tab.url)
                if (b != null) {
                    tab.browser = b
                    val ui = b.uiComponent
                    val card = JPanel(BorderLayout()).apply { name = tab.id.toString(); add(ui, BorderLayout.CENTER) }
                    host.add(card, tab.id.toString())
                }
            }
            cards.show(host, tab.id.toString())
            host.revalidate()
            host.repaint()
        }
    }
    SwingPanel(factory = { host }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun Preparing(p: WebEngine.State.Preparing) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        LoraFace(Modifier.size(64.dp), label = "Lora", director = rememberLoraDirector(listOf(LoraAction.GLOBE, LoraAction.SEARCH), LoraAction.GLOBE))
        Spacer(Modifier.size(12.dp))
        Text(p.step, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.size(10.dp))
        if (p.percent >= 0f) LinearProgressIndicator(progress = { p.percent / 100f }, modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth())
        else LinearProgressIndicator(Modifier.widthIn(max = 320.dp).fillMaxWidth())
    }
}

@Composable
private fun SetUp(c: AppContainer, tab: Browser.Tab?) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.TravelExplore, contentDescription = null, tint = LocalChatLook.current.brand, modifier = Modifier.size(48.dp))
        Spacer(Modifier.size(12.dp))
        Text(tr("Links neben dem Chat öffnen"), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(8.dp))
        Text(
            tr("Lora richtet dafür einmalig einen Browser ein (Chromium, ca. 170 MB, ohne Admin-Rechte). Danach öffnen sich Links aus Chat, Literatur und Dokumenten hier als Tabs – mit Fragen zur Seite, Zusammenfassung und Übernahme in die Literatur. Anmeldungen (z. B. über die TU) bleiben erhalten."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.size(16.dp))
        Button(onClick = { scope.launch { WebEngine.start() } }) { Text(tr("Browser einrichten")) }
        if (tab != null) TextButton(onClick = { DesktopActions.browse(tab.url); Browser.close(tab) }) { Text(tr("Diesmal im normalen Browser öffnen")) }
        TextButton(onClick = {
            c.settings.update { it.copy(browserPanel = false) }
            tab?.let { DesktopActions.browse(it.url) }
            Browser.tabs.toList().forEach(Browser::close)
        }) { Text(tr("Links immer im normalen Browser öffnen")) }
    }
}

@Composable
private fun Problem(message: String, tab: Browser.Tab?) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(tr("Der Browser ließ sich nicht starten."), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(6.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { scope.launch { WebEngine.start() } }) { Text(tr("Erneut versuchen")) }
            if (tab != null) Button(onClick = { DesktopActions.browse(tab.url) }) { Text(tr("Im normalen Browser öffnen")) }
        }
    }
}

/** Reads the page of [tab]: Chromium's text and HTML, else the address fetched directly. */
private suspend fun readPage(c: AppContainer, tab: Browser.Tab): WebPage? {
    val b = tab.browser
    val text = b?.let { WebEngine.text(it) }.orEmpty()
    val html = b?.let { WebEngine.source(it) }.orEmpty()
    if (text.isNotBlank()) return WebPage(tab.url, tab.title, text, html)
    return withContext(Dispatchers.IO) {
        runCatching {
            c.llm.http.newCall(Request.Builder().url(tab.url).build()).execute().use { r ->
                val body = r.body?.string().orEmpty()
                WebPage(tab.url, tab.title, WebPage.textOf(body, tab.url), body)
            }
        }.getOrNull()
    }
}

/** The things to do with the page under it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageActions(c: AppContainer, tab: Browser.Tab) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    val pdf = remember(tab.url) { WebPage(tab.url, tab.title, "").pdfUrl }
    fun act(label: String, block: suspend (WebPage) -> Unit) {
        if (busy != null) return
        busy = label
        scope.launch {
            val page = readPage(c, tab)
            if (page == null || (page.text.isBlank() && !page.isPdf)) Notices.error(tr("Die Seite ließ sich nicht lesen."))
            else runCatching { block(page) }.onFailure { e -> AppLog.e("Browser: $label", e); Notices.error(e.message ?: label) }
            busy = null
        }
    }
    fun toChat(page: WebPage, text: String, send: Boolean) {
        val (body, cut) = page.asAttachment()
        val file = FileAttachment(
            name = "Webseite – " + page.title.ifBlank { page.host }.take(60) + ".txt", mime = "text/plain", text = body, truncated = cut, public = true,
        )
        ChatHandoff.pending.value = ChatHandoff.Content(
            text, listOf(file), chatId = Browser.chatId,
            mode = io.github.thepytales.llmhub.agent.ChatModes.SOURCES.id.takeIf { Browser.chatId == null }, send = send,
        )
        ChatHandoff.open.value = Browser.chatId.orEmpty()
    }
    Column {
        if (busy != null) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ActionChip(Icons.Outlined.QuestionAnswer, tr("Frage zur Seite")) {
                act(tr("Frage")) { page -> toChat(page, tr("Frage zur Seite „{0}“: ", page.title.ifBlank { page.host }), send = false) }
            }
            ActionChip(Icons.Outlined.Summarize, tr("Zusammenfassen")) {
                act(tr("Zusammenfassen")) { page ->
                    toChat(page, tr("Fasse die angehängte Webseite kurz zusammen – die wichtigsten Aussagen mit Belegen aus dem Text."), send = true)
                }
            }
            ActionChip(Icons.Outlined.MenuBook, tr("In die Literatur")) {
                act(tr("Literatur")) { page ->
                    val fromDoi = page.doi?.let { doi ->
                        withContext(Dispatchers.IO) { runCatching { LiteratureSources(c.llm.http).crossrefDoi(doi) }.getOrNull() }
                    }
                    val ref = fromDoi?.copy(url = fromDoi.url.ifBlank { page.url }, openAccessUrl = fromDoi.openAccessUrl.ifBlank { page.pdfUrl.orEmpty() }) ?: page.reference()
                    c.literature.addAll(listOf(ref))
                    Notices.info(tr("In die Literatur übernommen: ") + ref.title.take(80))
                }
            }
            ActionChip(Icons.Outlined.LibraryBooks, tr("In die Wissenssammlung")) {
                act(tr("Wissen")) { page ->
                    val (body, _) = page.asAttachment()
                    withContext(Dispatchers.IO) { c.knowledge.add(page.title.ifBlank { page.host }.take(80), body, "") }
                    Notices.info(tr("In der Wissenssammlung: ") + page.title.ifBlank { page.host }.take(80))
                }
            }
            if (pdf != null || tab.url.lowercase().contains(".pdf")) {
                ActionChip(Icons.Outlined.PictureAsPdf, tr("PDF im Werkzeugkasten")) {
                    act("PDF") { page ->
                        val url = page.pdfUrl ?: tab.url
                        val file = downloadPdf(c, url, page.title)
                        PdfHandoff.open(file.absolutePath, name = file.name)
                    }
                }
            }
        }
    }
}

/** Downloads the PDF at [url] with the browser's cookies (papers behind the TU login) into the cache. */
private suspend fun downloadPdf(c: AppContainer, url: String, title: String): File = withContext(Dispatchers.IO) {
    val cookies = WebEngine.cookieHeader(url)
    val request = Request.Builder().url(url).apply { if (!cookies.isNullOrBlank()) header("Cookie", cookies) }.build()
    c.llm.http.newCall(request).execute().use { r ->
        if (!r.isSuccessful) error(tr("PDF nicht erreichbar (HTTP {0})", r.code))
        val bytes = r.body?.bytes() ?: error(tr("PDF leer"))
        if (!(bytes.size > 4 && String(bytes, 0, 4) == "%PDF")) error(tr("Unter der Adresse liegt kein PDF (vielleicht erst anmelden)."))
        val name = (title.ifBlank { url.substringAfterLast('/').substringBefore('?') }).replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80).removeSuffix(".pdf") + ".pdf"
        File(File(Paths.cache, "browser").apply { mkdirs() }, name).apply { writeBytes(bytes) }
    }
}

@Composable
private fun ActionChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = LocalChatLook.current.card,
        border = boxBorder(),
    ) {
        Row(Modifier.padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = LocalChatLook.current.brand, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
