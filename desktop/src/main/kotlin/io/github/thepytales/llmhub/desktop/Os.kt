package io.github.thepytales.llmhub.desktop

import io.github.thepytales.llmhub.ui.I18n
import java.io.File

/**
 * Which desktop system the app runs on (AP-31). Windows is the main platform; macOS and Linux get the same app with
 * their own folders, key store, autostart, update package and names in the interface.
 */
object Os {
    private val name = System.getProperty("os.name").orEmpty()
    val windows = name.startsWith("Windows")
    val mac = name.startsWith("Mac") || name.startsWith("Darwin")
    val linux = !windows && !mac
    /** Apple Silicon or ARM Linux. */
    val arm = System.getProperty("os.arch").orEmpty().let { it == "aarch64" || it == "arm64" }

    /** Key of this system in version.json and the release files. */
    val updateKey: String get() = when {
        windows -> "windows"
        mac -> "macos"
        else -> "linux"
    }

    /** Name shown in the interface. */
    val label: String get() = when {
        windows -> "Windows"
        mac -> "macOS"
        else -> "Linux"
    }

    /** The running app as started by its launcher: ADEU LLM.exe, …/ADEU LLM.app/Contents/MacOS/ADEU LLM or …/bin/ADEU LLM. */
    val launcher: File? get() = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }

    /**
     * Folder of the whole app that an update replaces: the folder with ADEU LLM.exe (Windows), the .app bundle (macOS)
     * or the folder with bin/ and lib/ (Linux).
     */
    val appDir: File? get() = launcher?.absoluteFile?.let { exe ->
        when {
            windows -> exe.parentFile
            mac -> exe.parentFile?.parentFile?.parentFile?.takeIf { it.name.endsWith(".app") }
            else -> exe.parentFile?.takeIf { it.name == "bin" }?.parentFile
        }
    }

    /** Shown texts on macOS and Linux: their shortcut keys and file manager (set as [I18n.platformText]). */
    fun adaptText(text: String): String {
        if (windows) return text
        var t = text
        if ("Windows" in t) {
            t = t.replace("Mit Windows starten", "Beim Anmelden starten").replace("Start with Windows", "Start at login")
                .replace("startet mit Windows", "startet beim Anmelden").replace("starts with Windows", "starts at login")
        }
        if (mac) {
            if ("Strg" in t || "Ctrl" in t || "Win+" in t || "Umschalt+" in t || "Shift+" in t) {
                t = t.replace("Win+Umschalt+S", "⇧⌘4").replace("Win+Shift+S", "⇧⌘4")
                    .replace("Strg+Umschalt+", "⇧⌘").replace("Ctrl+Shift+", "⇧⌘")
                    .replace("Strg+Alt+", "⌥⌘").replace("Ctrl+Alt+", "⌥⌘")
                    .replace("Strg+", "⌘").replace("Ctrl+", "⌘")
                    .replace("Umschalt+", "⇧").replace("Shift+", "⇧")
                    .replace(Regex("""\b(Strg|Ctrl)\b"""), "⌘")
            }
            if ("Explorer" in t) t = t.replace("Explorer", "Finder")
            if ("Infobereich" in t) t = t.replace("Infobereich", "Menüleiste")
            if ("notification area" in t) t = t.replace("notification area", "menu bar")
        } else if ("Explorer" in t) {
            val en = I18n.language == "en"
            t = t.replace("in Explorer", "in the file manager").replace("In Explorer", "In the file manager")
                .replace(Regex("^Explorer"), if (en) "File manager" else "Dateimanager")
                .replace("Explorer", if (en) "file manager" else "Dateimanager")
        }
        return t
    }
}
