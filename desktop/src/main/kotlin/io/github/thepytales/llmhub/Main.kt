package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.ui.Notices
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
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
import androidx.compose.ui.input.key.isShiftPressed
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
import io.github.thepytales.llmhub.update.UpdateApply
import io.github.thepytales.llmhub.update.Updates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.awt.datatransfer.DataFlavor
import java.io.File
import kotlin.system.exitProcess

/**
 * Windows entry point. Flags: --minimized (autostart into the notification area), --selftest <folder> (CI),
 * --apply-update … (second half of an update, see UpdateApply).
 */
fun main(args: Array<String>) {
    if (args.firstOrNull() == UpdateApply.FLAG) UpdateApply.run(args)
    val selfTest = args.indexOf("--selftest").takeIf { it >= 0 }?.let { File(args.getOrNull(it + 1) ?: "selftest").absoluteFile }
    if (selfTest != null) {
        Updates.disabled = true
        selfTest.mkdirs()
        System.setProperty("adeu.data", File(selfTest, "data").absolutePath)
    }
    // JNA unpacks its DLL to %TEMP% by default; IT rules (AppLocker, WDAC, virus scanners) often block DLLs there.
    // The app's own folder in %LOCALAPPDATA% is allowed wherever the app itself may run.
    if (System.getProperty("jna.tmpdir") == null) {
        runCatching { System.setProperty("jna.tmpdir", File(io.github.thepytales.llmhub.desktop.Paths.local, "jna").apply { mkdirs() }.absolutePath) }
    }
    Network.setup()
    val showRequests = MutableStateFlow(0)
    if (!SingleInstance.acquire { showRequests.update { it + 1 } }) exitProcess(0)
    // Einfacher Sync (AP-12): before anything is loaded, make sure the synced data folder is not open on another PC and
    // that PC's changes have arrived; then this PC keeps its note there.
    if (selfTest == null && io.github.thepytales.llmhub.desktop.SyncSession.active) {
        if (!io.github.thepytales.llmhub.ui.runSyncGate()) exitProcess(0)
        io.github.thepytales.llmhub.desktop.SyncSession.start()
    }
    io.github.thepytales.llmhub.data.MemoryMeter.nonHeapBytes = { java.lang.management.ManagementFactory.getMemoryMXBean().nonHeapMemoryUsage.used }
    // AP-04: on Windows the working set of the whole process (Java + native parts) for the memory display.
    if (System.getProperty("os.name").orEmpty().startsWith("Windows")) {
        io.github.thepytales.llmhub.data.MemoryMeter.processBytes = { io.github.thepytales.llmhub.desktop.ProcessMemory.workingSet() }
    }
    LlmHubApp.start()
    Autostart.refresh()
    AppLog.i("Lora ${BuildConfig.VERSION_NAME} gestartet (${System.getProperty("os.name")}, Java ${System.getProperty("java.version")})")
    val minimized = Autostart.FLAG in args && isTraySupported
    // Measuring point once the app has started (AP-04), a few seconds later when the first screen is up.
    Thread {
        Thread.sleep(8000)
        io.github.thepytales.llmhub.data.MemoryMeter.mark("Start").let { m ->
            AppLog.i("Speicher beim Start: Java ${m.heapUsed / 1_000_000} MB von ${m.heapMax / 1_000_000} MB, Prozess ${m.process?.let { "${it / 1_000_000} MB" } ?: "?"}")
        }
    }.apply { isDaemon = true }.start()
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
        io.github.thepytales.llmhub.desktop.WebEngine.dispose()
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
    // AP-12: the note says whether Lora only waits in the notification area (another PC may then open the folder).
    val runs by c.runs.runs.collectAsState()
    val idle = runs.isEmpty()
    SideEffect { io.github.thepytales.llmhub.desktop.SyncSession.background = { !visible && idle } }
    LaunchedEffect(Unit) {
        io.github.thepytales.llmhub.desktop.SyncSession.kept.value?.let { k ->
            if (k.chats.isNotEmpty()) Notices.info("${k.chats.size} Chat(s) gab es nach der Synchronisation in zwei Fassungen – beide sind da („zweite Fassung“).")
            else if (k.setAside.isNotEmpty()) Notices.info("${k.setAside.size} Konfliktkopie(n) des Cloud-Dienstes beiseitegelegt (Einstellungen › Synchronisation).")
        }
    }

    if (tray) {
        Tray(
            icon = painterResource("tray.png"),
            state = trayState,
            tooltip = "Lora",
            // Double-click on the icon or click on a notification.
            onAction = {
                val recent = lastNotified?.takeIf { System.currentTimeMillis() - it.second < 10 * 60_000 }?.first
                lastNotified = null
                show(recent?.removePrefix("chat:")?.takeIf { recent.startsWith("chat:") })
                recent?.takeIf { it.startsWith("route:") }?.let { Navigation.openRoute.value = it.removePrefix("route:") }
            },
            menu = {
                Item("Lora öffnen", onClick = { show() })
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
                            "Lora läuft weiter",
                            "Antworten und geplante Aufgaben laufen im Hintergrund. Öffnen oder beenden über das Symbol im Infobereich.",
                            Notification.Type.Info,
                        )
                    )
                }
            } else quit()
        },
        visible = visible,
        state = windowState,
        title = "Lora",
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
            io.github.thepytales.llmhub.ui.SyncNewcomerDialog(visible = visible, idle = idle) { note ->
                if (note != null && tray) trayState.sendNotification(Notification("Lora", note, Notification.Type.Info))
                quit()
            }
            Box(
                Modifier.fillMaxSize().dragAndDropTarget(shouldStartDragAndDrop = { true }, target = drop)
                    // Strg+Mausrad zooms; seen before any scrollable list gets the wheel.
                    .onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { e ->
                        if (!e.keyboardModifiers.isCtrlPressed) return@onPointerEvent
                        val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                        if (dy != 0f) zoom(if (dy < 0) 1 else -1)
                        e.changes.forEach { it.consume() }
                    }
            ) {
                StartAnimation(enabled = settings.startAnimation) {
                    AppRoot(c, Navigation.openChat, Navigation.openRoute)
                }
            }
        }
    }
}

/** Steps the interface zoom by 10 % (0 resets it) and briefly shows the new level. */
private fun zoom(step: Int) {
    val store = LlmHubApp.container.settings
    val tenths = if (step == 0) 10 else (Math.round(store.settings.value.uiZoom * 10) + step).coerceIn(8, 20)
    store.update { it.copy(uiZoom = tenths / 10f) }
    Notices.info("Zoom ${tenths * 10} %")
}

/**
 * Strg+N new chat, Strg+, settings, Strg++/Strg+−/Strg+0 zoom, Strg+B side menu, Strg+K command palette, Strg+L Lora,
 * Strg+Umschalt+L literature, Strg+/ shortcut overview (see ShortcutsDialog).
 */
private fun shortcut(e: KeyEvent): Boolean {
    if (e.type != KeyEventType.KeyDown || !e.isCtrlPressed) return false
    when (e.key) {
        Key.Plus, Key.Equals, Key.NumPadAdd -> { zoom(1); return true }
        Key.Minus, Key.NumPadSubtract -> { zoom(-1); return true }
        Key.Zero, Key.NumPad0 -> { zoom(0); return true }
    }
    Navigation.openRoute.value = when (e.key) {
        Key.N -> "chat"
        Key.Comma -> "settings"
        Key.B -> "drawer"
        Key.K -> "palette"
        Key.Slash, Key.NumPadDivide -> "shortcuts"
        Key.L -> if (e.isShiftPressed) "literature" else "lora"
        Key.D -> "docs"
        Key.T -> "transcripts"
        else -> return false
    }
    return true
}
