package io.github.thepytales.llmhub.desktop

import io.github.thepytales.llmhub.AppLog
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI

/** Desktop integration (Windows, macOS, Linux): files, folders, links and the clipboard. */
object DesktopActions {
    /** Main window, so dialogs open in front of it (set by Main.kt). */
    @Volatile
    var window: Frame? = null

    fun open(file: File): Boolean = runCatching {
        when {
            Paths.windows -> ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", file.absolutePath).start()
            Os.mac -> ProcessBuilder("open", file.absolutePath).start()
            // Linux (AP-31): AWT's Desktop only works with GNOME libraries; xdg-open with every desktop.
            else -> runCatching { Desktop.getDesktop().open(file) }.getOrElse { ProcessBuilder("xdg-open", file.absolutePath).start() }
        }
        true
    }.getOrElse {
        AppLog.e("Öffnen von ${file.name}", it)
        false
    }

    /** Opens Explorer (macOS: Finder) with [file] selected; Linux opens its folder. */
    fun showInFolder(file: File): Boolean = runCatching {
        when {
            Paths.windows -> ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
            Os.mac -> ProcessBuilder("open", "-R", file.absolutePath).start()
            else -> {
                val dir = if (file.isDirectory) file else file.parentFile
                runCatching { Desktop.getDesktop().open(dir) }.getOrElse { ProcessBuilder("xdg-open", dir.absolutePath).start() }
            }
        }
        true
    }.getOrDefault(false)

    fun browse(url: String): Boolean = runCatching {
        when {
            Paths.windows -> ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start()
            Os.mac -> ProcessBuilder("open", url).start()
            else -> runCatching { Desktop.getDesktop().browse(URI(url)) }.getOrElse { ProcessBuilder("xdg-open", url).start() }
        }
        true
    }.getOrDefault(false)

    fun copy(text: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
    }

    /** Text "shared" from the app goes to the clipboard, ready to paste into mail, Teams … */
    fun share(text: String) {
        copy(text)
        Toasts.show(io.github.thepytales.llmhub.ui.tr("In die Zwischenablage kopiert – zum Teilen einfach einfügen (Strg+V)."))
    }

    /** Image in the clipboard (e.g. a screenshot with Win+Umschalt+S), or null. */
    fun clipboardImage(): BufferedImage? = runCatching {
        val clip = Toolkit.getDefaultToolkit().systemClipboard
        if (!clip.isDataFlavorAvailable(DataFlavor.imageFlavor)) return null
        val img = clip.getData(DataFlavor.imageFlavor) as? java.awt.Image ?: return null
        if (img is BufferedImage) img else {
            val b = BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB)
            b.createGraphics().apply { drawImage(img, 0, 0, null); dispose() }
            b
        }
    }.getOrNull()

    /** Files copied in Explorer (Strg+C), or empty. */
    fun clipboardFiles(): List<File> = runCatching {
        val clip = Toolkit.getDefaultToolkit().systemClipboard
        if (!clip.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) return emptyList()
        @Suppress("UNCHECKED_CAST")
        (clip.getData(DataFlavor.javaFileListFlavor) as List<File>)
    }.getOrDefault(emptyList())

    /** Native Windows dialog to pick files; [extensions] like "pdf" (empty = all). */
    fun pickFiles(title: String, multiple: Boolean = true, extensions: List<String> = emptyList()): List<File> {
        val dialog = FileDialog(window, title, FileDialog.LOAD)
        dialog.isMultipleMode = multiple
        if (extensions.isNotEmpty()) {
            // Windows only uses the file pattern, other systems the filter.
            dialog.file = extensions.joinToString(";") { "*.$it" }
            dialog.setFilenameFilter { _, name -> extensions.any { name.lowercase().endsWith(".$it") } }
        }
        dialog.isVisible = true
        return dialog.files.orEmpty().toList()
    }

    /** Dialog to choose a folder (e.g. a storage folder in Google Drive); null if cancelled. */
    fun pickFolder(title: String, start: File? = null): File? {
        // Swing dialogs belong on the event thread.
        if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
            var result: File? = null
            javax.swing.SwingUtilities.invokeAndWait { result = pickFolder(title, start) }
            return result
        }
        if (Os.mac) {
            // The Finder's own dialog instead of Swing's (AP-31).
            System.setProperty("apple.awt.fileDialogForDirectories", "true")
            try {
                val dialog = FileDialog(window, title, FileDialog.LOAD)
                start?.let { dialog.directory = it.absolutePath }
                dialog.isVisible = true
                val name = dialog.file ?: return null
                return File(dialog.directory ?: "", name)
            } finally {
                System.setProperty("apple.awt.fileDialogForDirectories", "false")
            }
        }
        val chooser = javax.swing.JFileChooser(start ?: File(System.getProperty("user.home")))
        chooser.dialogTitle = title
        chooser.fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        chooser.isAcceptAllFileFilterUsed = false
        return if (chooser.showOpenDialog(window) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    /** Native "Speichern unter" dialog; returns the chosen file (with [extension]) or null. */
    fun pickSaveFile(title: String, suggested: String, extension: String? = null): File? {
        val dialog = FileDialog(window, title, FileDialog.SAVE)
        val folder = Paths.exports.apply { mkdirs() }
        dialog.directory = folder.absolutePath
        dialog.file = suggested
        dialog.isVisible = true
        val name = dialog.file ?: return null
        var file = File(dialog.directory ?: folder.absolutePath, name)
        if (extension != null && !file.name.lowercase().endsWith(".${extension.lowercase()}")) {
            file = File(file.parentFile, "${file.name}.$extension")
        }
        return file
    }
}
