package io.github.thepytales.llmhub.ui

import android.content.Context
import io.github.thepytales.llmhub.desktop.DesktopActions
import io.github.thepytales.llmhub.desktop.Images
import java.io.File

/** Windows parts of "Bilder erzeugen". */

/** Saves the picture as PNG in Pictures\Lora and shows it in Explorer. */
@Suppress("UNUSED_PARAMETER")
fun saveImageToDevice(context: Context, file: File) {
    runCatching {
        val home = File(System.getProperty("user.home"))
        val dir = File(File(home, "Pictures").takeIf { it.isDirectory } ?: home, "Lora").apply { mkdirs() }
        val target = File(dir, "adeu_llm_${System.currentTimeMillis()}.png")
        val bytes = file.readBytes()
        target.writeBytes(Images.decode(bytes)?.let { Images.png(it) } ?: bytes)
        target
    }.onSuccess {
        Notices.info("Gespeichert in Bilder\\Lora.")
        DesktopActions.showInFolder(it)
    }.onFailure { Notices.error("Speichern fehlgeschlagen.") }
}

/** Windows has no share sheet: the picture is shown in Explorer, from where it can be dragged into mail or Teams. */
@Suppress("UNUSED_PARAMETER")
fun shareImage(context: Context, file: File) {
    if (DesktopActions.showInFolder(file)) Notices.info("Im Explorer markiert – von dort in Mail oder Teams ziehen.")
    else Notices.error("Explorer ließ sich nicht öffnen.")
}

const val SAVE_IMAGE_LABEL = "Speichern"

/** The picture as a downscaled JPEG data URL, to attach it to a chat. */
@Suppress("UNUSED_PARAMETER")
suspend fun imageDataUrl(context: Context, file: File): String? = encodeImage(file)
