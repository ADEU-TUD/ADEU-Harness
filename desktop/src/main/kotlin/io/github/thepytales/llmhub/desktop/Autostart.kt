package io.github.thepytales.llmhub.desktop

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import io.github.thepytales.llmhub.AppLog
import java.io.File

/**
 * Starts the app with the system (minimized to the notification area), so scheduled tasks also run when the app was
 * not opened. No admin rights needed anywhere: Windows HKEY_CURRENT_USER\…\Run, macOS a LaunchAgent in
 * ~/Library/LaunchAgents, Linux an entry in ~/.config/autostart (AP-31).
 */
object Autostart {
    private const val KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val NAME = "ADEU LLM"
    const val FLAG = "--minimized"

    /** The ADEU LLM.exe / launcher that is running (set by the launcher of the packaged app). */
    private val exe: String? get() = Os.launcher?.path?.takeIf { !Os.windows || it.endsWith(".exe", true) }

    private val home = File(System.getProperty("user.home"))
    private val macAgent get() = File(home, "Library/LaunchAgents/de.tu-dresden.adeu.lora.plist")
    private val linuxEntry get() = File(System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() } ?: File(home, ".config").path, "autostart/adeu-lora.desktop")

    val supported: Boolean get() = exe != null

    val enabled: Boolean
        get() = when {
            Os.windows -> runCatching { Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, KEY, NAME) }.getOrDefault(false)
            Os.mac -> macAgent.isFile
            else -> linuxEntry.isFile
        }

    fun set(on: Boolean) {
        runCatching {
            when {
                Os.windows -> {
                    if (on) Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, KEY, NAME, "\"${exe ?: return}\" $FLAG")
                    else if (enabled) Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, KEY, NAME)
                }
                Os.mac -> if (on) {
                    macAgent.parentFile.mkdirs()
                    macAgent.writeText(
                        """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                        <plist version="1.0">
                        <dict>
                          <key>Label</key><string>de.tu-dresden.adeu.lora</string>
                          <key>ProgramArguments</key><array><string>${xml(exe ?: return)}</string><string>$FLAG</string></array>
                          <key>RunAtLoad</key><true/>
                        </dict>
                        </plist>
                        """.trimIndent() + "\n",
                    )
                } else macAgent.delete()
                else -> if (on) {
                    linuxEntry.parentFile.mkdirs()
                    linuxEntry.writeText(
                        "[Desktop Entry]\nType=Application\nName=Lora\nExec=\"${exe ?: return}\" $FLAG\nX-GNOME-Autostart-enabled=true\nTerminal=false\n",
                    )
                } else linuxEntry.delete()
            }
        }.onFailure { AppLog.e("Autostart", it) }
    }

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** A new version may live in a new folder: keep the entry pointing at the running one. */
    fun refresh() {
        if (supported && enabled) set(true)
    }
}
