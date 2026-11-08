package io.github.thepytales.llmhub.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import io.github.thepytales.llmhub.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Where the pages of the Dokumenten-Scanner (AP-62) come from: a button with icon and what it starts. */
class ScanSource(val label: String, val icon: ImageVector, val start: () -> Unit)

/** Android: the scan works on the phone itself (camera, photos); the PDF reaches the PC through the sync. */
val SCAN_HINT: String get() = tr("Seite flach hinlegen, von oben fotografieren – Lora findet die Kanten. Über den Sync landet das PDF auch auf dem PC.")

/** Folder for the photos of a scan (the camera writes there through the FileProvider). */
fun scanWorkDir(context: android.content.Context): File = File(context.cacheDir, "camera/scan").apply { mkdirs() }

/**
 * The sources of a scan on Android: the camera (system camera app, one photo per tap, no camera permission needed) and
 * photos from the gallery. [onImages] gets the files in [scanWorkDir].
 */
@Composable
fun rememberScanSources(onImages: (List<File>) -> Unit): List<ScanSource> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<File?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pending
        pending = null
        if (ok && f != null && f.length() > 0) onImages(listOf(f)) else f?.delete()
    }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val files = withContext(Dispatchers.IO) {
                uris.mapIndexedNotNull { i, uri ->
                    runCatching {
                        val f = File(scanWorkDir(context), "foto-${System.currentTimeMillis()}-$i.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
                        f.takeIf { it.length() > 0 }
                    }.getOrNull()
                }
            }
            if (files.isNotEmpty()) onImages(files)
        }
    }
    return remember {
        listOf(
            ScanSource(tr("Foto aufnehmen"), Icons.Outlined.CameraAlt) {
                try {
                    val f = File(scanWorkDir(context), "seite-${System.currentTimeMillis()}.jpg")
                    pending = f
                    camera.launch(FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", f))
                } catch (e: ActivityNotFoundException) {
                    Notices.error(tr("Keine Kamera-App gefunden."))
                }
            },
            ScanSource(tr("Aus Fotos"), Icons.Outlined.PhotoLibrary) {
                photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
    }
}
