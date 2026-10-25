package io.github.thepytales.llmhub

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import io.github.thepytales.llmhub.desktop.Autostart
import io.github.thepytales.llmhub.desktop.DesktopActions
import io.github.thepytales.llmhub.desktop.DroppedFiles
import io.github.thepytales.llmhub.desktop.Network
import io.github.thepytales.llmhub.desktop.SingleInstance
import io.github.thepytales.llmhub.tasks.Notifier
import io.github.thepytales.llmhub.ui.AppRoot
import io.github.thepytales.llmhub.ui.LlmHubTheme
import io.github.thepytales.llmhub.ui.StartAnimation
import io.github.thepytales.llmhub.ui.Navigation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.awt.datatransfer.DataFlavor
import java.io.File
import kotlin.system.exitProcess

/** Windows entry point. Flags: --minimized (autostart into the notification area), --selftest <folder> (CI). */
fun main(args: Array<String>) {
    val selfTest = args.indexOf("--selftest").takeIf { it >= 0 }?.let { File(args.getOrNull(it + 1) ?: "selftest").absoluteFile }
    if (selfTest != null) {
        selfTest.mkdirs()
        System.setProperty("adeu.data", File(selfTest, "data").absolutePath)
    }
    Network.setup()
    val showRequests = MutableStateFlow(0)
    if (!SingleInstance.acquire { showRequests.update { it + 1 } }) exitProcess(0)
    LlmHubApp.start()
    Autostart.refresh()
    AppLog.i("ADEU LLM ${BuildConfig.VERSION_NAME} gestartet (${System.getProperty("os.name")}, Java ${System.getProperty("java.version")})")
    val minimized = Autostart.FLAG in args && isTraySupported
    application {
        App(startHidden = minimized, selfTest = selfTest, showRequests = showRequests)
    }
}

/** Remembers size and position of the window between starts. */
private object WindowMemory {
    private val prefs by lazy { LlmHubApp.context.getSharedPreferences("ui_state", 0) }
    val width get() = prefs.getInt("windowWidth", 1180)
    val height get() = prefs.getInt("windowHeight", 820)
    val x get() = prefs.getInt("windowX", Int.MIN_VALUE)
    val y get() = prefs.getInt("windowY", Int.MIN_VALUE)
    val maximized get() = prefs.getBoolean("windowMaximized", false)

    fun save(w: Int, h: Int, x: Int, y: Int, maximized: Boolean) {
        prefs.edit().putBoolean("windowMaximized", maximized).apply {
            if (!maximized) putInt("windowWidth", w).putInt("windowHeight", h).putInt("windowX", x).putInt("windowY", y)
        }.apply()
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun ApplicationScope.App(startHidden: Boolean, selfTest: File?, showRequests: StateFlow<Int>) {
    val c = LlmHubApp.container
    val settings by c.settings.settings.collectAsState()
    var visible by remember { mutableStateOf(!startHidden) }
    val windowState = rememberWindowState(
        size = DpSize(WindowMemory.width.dp, WindowMemory.height.dp),
        position = if (WindowMemory.x == Int.MIN_VALUE) WindowPosition.PlatformDefault else WindowPosition(WindowMemory.x.dp, WindowMemory.y.dp),
        placement = if (WindowMemory.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
    )
    val tray = isTraySupported
    val trayState = rememberTrayState()
    /** Chat ("chat:<id>") or screen ("route:<r>") of the last notification; clicking the notification opens it. */
    var lastNotified by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var toldAboutTray by remember { mutableStateOf(false) }

    fun rememberWindow() {
        val s = windowState.size
        val p = windowState.position
        WindowMemory.save(s.width.value.toInt(), s.height.value.toInt(), p.x.value.toInt(), p.y.value.toInt(), windowState.placement == WindowPlacement.Maximized)
    }

    fun show(chatId: String? = null) {
        visible = true
        windowState.isMinimized = false
        chatId?.let { Navigation.openChat.value = it }
    }

    fun quit() {
        rememberWindow()
        c.runs.stopAll()
        c.speaker.stop()
        c.localLlm.shutdown()
        exitApplication()
    }

    LaunchedEffect(Unit) {
        Notifier.notifications.collect { n ->
            if (tray) trayState.sendNotification(Notification(n.title, n.text, Notification.Type.Info))
            lastNotified = (n.route?.let { "route:$it" } ?: n.chatId?.let { "chat:$it" })?.let { it to System.currentTimeMillis() }
        }
    }
    LaunchedEffect(Unit) {
        showRequests.collect { if (it > 0) show() }
    }

    if (tray) {
        Tray(
            icon = painterResource("tray.png"),
            state = trayState,
            tooltip = "ADEU LLM",
            // Double-click on the icon or click on a notification.
            onAction = {
                val recent = lastNotified?.takeIf { System.currentTimeMillis() - it.second < 10 * 60_000 }?.first
                lastNotified = null
                show(recent?.removePrefix("chat:")?.takeIf { recent.startsWith("chat:") })
                recent?.takeIf { it.startsWith("route:") }?.let { Navigation.openRoute.value = it.removePrefix("route:") }
            },
            menu = {
                Item("ADEU LLM öffnen", onClick = { show() })
                Item("Neuer Chat", onClick = {
                    show()
                    Navigation.openRoute.value = "chat"
                })
                Separator()
                Item("Beenden", onClick = ::quit)
            },
        )
    }

    Window(
        onCloseRequest = {
            if (settings.closeToTray && tray && selfTest == null) {
                rememberWindow()
                visible = false
                if (!toldAboutTray) {
                    toldAboutTray = true
                    trayState.sendNotification(
                        Notification(
                            "ADEU LLM läuft weiter",
                            "Antworten und geplante Aufgaben laufen im Hintergrund. Öffnen oder beenden über das Symbol im Infobereich.",
                            Notification.Type.Info,
                        )
                    )
                }
            } else quit()
        },
        visible = visible,
        state = windowState,
        title = "ADEU LLM",
        icon = painterResource("icon.png"),
        onPreviewKeyEvent = ::shortcut,
        onKeyEvent = { e ->
            // Escape after dialogs and menus had their chance: close the side menu or go back.
            if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) {
                Navigation.openRoute.value = "back"
                true
            } else false
        },
    ) {
        DesktopActions.window = window
        window.minimumSize = java.awt.Dimension(420, 560)
        val focused = LocalWindowInfo.current.isWindowFocused
        SideEffect { AppState.foreground = visible && focused }
        LaunchedEffect(visible) {
            if (visible) {
                window.toFront()
                window.requestFocus()
            }
        }
        if (selfTest != null) LaunchedEffect(Unit) { SelfTest.run(window, selfTest) }
        val drop = remember {
            object : DragAndDropTarget {
                override fun onDrop(event: DragAndDropEvent): Boolean {
                    val t = event.awtTransferable
                    if (!t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false
                    @Suppress("UNCHECKED_CAST")
                    val files = runCatching { t.getTransferData(DataFlavor.javaFileListFlavor) as List<File> }.getOrDefault(emptyList())
                    DroppedFiles.drop(files.filter { it.isFile })
                    return files.isNotEmpty()
                }
            }
        }
        LlmHubTheme(settings) {
            Box(Modifier.fillMaxSize().dragAndDropTarget(shouldStartDragAndDrop = { true }, target = drop)) {
                StartAnimation(enabled = settings.startAnimation) {
                    AppRoot(c, Navigation.openChat, Navigation.openRoute)
                }
            }
        }
    }
}

/** Strg+N new chat, Strg+, settings, Strg+B side menu, Strg+K command palette, Strg+/ shortcut overview (see ShortcutsDialog). */
private fun shortcut(e: KeyEvent): Boolean {
    if (e.type != KeyEventType.KeyDown || !e.isCtrlPressed) return false
    Navigation.openRoute.value = when (e.key) {
        Key.N -> "chat"
        Key.Comma -> "settings"
        Key.B -> "drawer"
        Key.K -> "palette"
        Key.Slash, Key.NumPadDivide -> "shortcuts"
        Key.L -> "literature"
        Key.D -> "docs"
        Key.T -> "transcripts"
        else -> return false
    }
    return true
}
