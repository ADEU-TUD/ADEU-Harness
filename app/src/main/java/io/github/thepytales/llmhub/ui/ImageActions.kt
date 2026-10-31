package io.github.thepytales.llmhub.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import io.github.thepytales.llmhub.BuildConfig
import java.io.File

/** Android parts of "Bilder erzeugen" (Windows has its own version of this file). */

/** Copies a generated picture into the gallery (Pictures/Lora). */
fun saveImageToDevice(context: Context, file: File) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        Notices.info("Speichern in die Galerie geht erst ab Android 10 – „Teilen“ funktioniert trotzdem.")
        return
    }
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "adeu_llm_${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Lora")
    }
    val ok = runCatching {
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        context.contentResolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
    }.isSuccess
    if (ok) Notices.info("In der Galerie gespeichert (Bilder › Lora).") else Notices.error("Speichern fehlgeschlagen.")
}

/** Share sheet for a generated picture (mail, messenger, other apps). */
fun shareImage(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", file)
    val intent = Intent(Intent.ACTION_SEND).setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "Bild teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Notices.error("Keine App zum Teilen gefunden.") }
}

const val SAVE_IMAGE_LABEL = "In Galerie"

/** The picture as a downscaled JPEG data URL, to attach it to a chat. */
suspend fun imageDataUrl(context: Context, file: File): String? = encodeImage(context, android.net.Uri.fromFile(file))
