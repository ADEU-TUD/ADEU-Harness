package io.github.thepytales.llmhub.desktop

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import io.github.thepytales.llmhub.AppLog

/**
 * Starts the app with Windows (minimized to the notification area), so scheduled tasks also run when the
 * app was not opened. Uses HKEY_CURRENT_USER\…\Run – no admin rights needed.
 */
object Autostart {
    private const val KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val NAME = "ADEU LLM"
    const val FLAG = "--minimized"

    /** The ADEU LLM.exe that is running (set by the launcher of the packaged app). */
    private val exe: String? get() = System.getProperty("jpackage.app-path")?.takeIf { it.endsWith(".exe", true) }

    val supported: Boolean get() = Paths.windows && exe != null

    val enabled: Boolean
        get() = Paths.windows && runCatching { Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, KEY, NAME) }.getOrDefault(false)

    fun set(on: Boolean) {
        if (!Paths.windows) return
        runCatching {
            if (on) Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, KEY, NAME, "\"${exe ?: return}\" $FLAG")
            else if (enabled) Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, KEY, NAME)
        }.onFailure { AppLog.e("Autostart", it) }
    }

    /** A new version lives in a new folder: keep the entry pointing at the running one. */
    fun refresh() {
        if (supported && enabled) set(true)
    }
}
