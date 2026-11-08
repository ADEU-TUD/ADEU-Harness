package io.github.thepytales.llmhub.desktop

import com.sun.jna.Native
import io.github.thepytales.llmhub.AppLog
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

/**
 * Tastenkürzel in ganz Windows (AP-56): while a screen recording runs, Strg+Umschalt+F9 pauses or continues it and
 * Strg+Umschalt+F10 stops it – also when another program has the focus (RegisterHotKey on a thread of its own with a
 * message loop). Nothing on macOS and Linux (there the recording bar and the tray stop it).
 */
object GlobalHotkeys {
    const val PAUSE = 1
    const val STOP = 2
    private const val MOD_CONTROL = 0x0002
    private const val MOD_SHIFT = 0x0004
    private const val MOD_NOREPEAT = 0x4000
    private const val VK_F9 = 0x78
    private const val VK_F10 = 0x79
    private const val WM_HOTKEY = 0x0312
    private const val WM_QUIT = 0x0012

    @Volatile
    private var thread: Thread? = null
    @Volatile
    private var threadId = 0

    /** Registers the two keys; [onKey] gets [PAUSE] or [STOP] (on the hotkey thread). False where it is not possible. */
    @Synchronized
    fun register(onKey: (Int) -> Unit): Boolean {
        if (!Os.windows || thread != null) return false
        val ready = java.util.concurrent.CountDownLatch(1)
        var ok = false
        thread = Thread({
            runCatching {
                threadId = Kernel32.INSTANCE.GetCurrentThreadId()
                val u = User32.INSTANCE
                ok = u.RegisterHotKey(null, PAUSE, MOD_CONTROL or MOD_SHIFT or MOD_NOREPEAT, VK_F9) and
                    u.RegisterHotKey(null, STOP, MOD_CONTROL or MOD_SHIFT or MOD_NOREPEAT, VK_F10)
                ready.countDown()
                val msg = WinUser.MSG()
                while (u.GetMessage(msg, null, 0, 0) > 0) {
                    if (msg.message == WM_HOTKEY) runCatching { onKey(msg.wParam.toInt()) }
                }
                u.UnregisterHotKey(null, PAUSE)
                u.UnregisterHotKey(null, STOP)
            }.onFailure { AppLog.i("Tastenkürzel der Aufnahme: ${it.message}"); ready.countDown() }
        }, "Tastenkürzel der Aufnahme").apply { isDaemon = true; start() }
        ready.await(2, java.util.concurrent.TimeUnit.SECONDS)
        return ok
    }

    @Synchronized
    fun unregister() {
        val t = thread ?: return
        thread = null
        runCatching { User32.INSTANCE.PostThreadMessage(threadId, WM_QUIT, WinDef.WPARAM(0), WinDef.LPARAM(0)) }
        t.join(1000)
    }

    private interface Affinity : StdCallLibrary {
        fun SetWindowDisplayAffinity(hwnd: WinDef.HWND, affinity: Int): Boolean
    }

    /**
     * Keeps the window [hwnd] out of screen captures (Windows 10 2004 and later, WDA_EXCLUDEFROMCAPTURE): the recording
     * bar is visible on the screen but not in the recording. False where Windows does not offer it.
     */
    fun excludeFromCapture(window: java.awt.Window): Boolean {
        if (!Os.windows) return false
        return runCatching {
            val lib = Native.load("user32", Affinity::class.java, W32APIOptions.DEFAULT_OPTIONS)
            val hwnd = WinDef.HWND(Native.getWindowPointer(window))
            lib.SetWindowDisplayAffinity(hwnd, 0x11)
        }.getOrDefault(false)
    }

    /** Titles of the visible top-level windows (for „Fenster aufnehmen“). */
    fun windowTitles(): List<String> {
        if (!Os.windows) return emptyList()
        return runCatching {
            val out = mutableListOf<String>()
            val u = User32.INSTANCE
            u.EnumWindows({ hwnd, _ ->
                if (u.IsWindowVisible(hwnd)) {
                    val buf = CharArray(512)
                    val n = u.GetWindowText(hwnd, buf, buf.size)
                    if (n > 0) out += String(buf, 0, n)
                }
                true
            }, null)
            out.filter { it.isNotBlank() && it != "Program Manager" }.distinct()
        }.getOrDefault(emptyList())
    }
}
