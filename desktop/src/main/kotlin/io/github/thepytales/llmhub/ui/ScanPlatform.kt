package io.github.thepytales.llmhub.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Scanner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.thepytales.llmhub.LlmHubApp
import io.github.thepytales.llmhub.desktop.DesktopActions
import io.github.thepytales.llmhub.desktop.WiaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Where the pages of the Dokumenten-Scanner (AP-62) come from: a button with icon and what it starts. */
class ScanSource(val label: String, val icon: ImageVector, val start: () -> Unit)

/** Windows: the scanner over WIA or pictures; the phone scans in its Lora app and the sync brings the PDF here. */
val SCAN_HINT: String get() = tr("Handy als Scanner: In Lora auf dem Handy › Dateien › + › Scannen – das PDF kommt über den Sync in den Ordner „Scans“.")

/** Folder for the pages of a scan. */
fun scanWorkDir(context: android.content.Context): File = File(context.cacheDir, "scan").apply { mkdirs() }

/** The sources of a scan on Windows: the scanner (WIA dialog of Windows) and picture files (photos of the phone, …). */
@Composable
fun rememberScanSources(onImages: (List<File>) -> Unit): List<ScanSource> {
    val scope = rememberCoroutineScope()
    return remember {
        buildList {
            if (WiaScanner.available) add(ScanSource(tr("Vom Scanner"), Icons.Outlined.Scanner) {
                scope.launch {
                    val page = withContext(Dispatchers.IO) {
                        runCatching { WiaScanner.scan(scanWorkDir(LlmHubApp.context)) }.onFailure { Notices.error(it.message ?: tr("Scannen hat nicht geklappt")) }.getOrNull()
                    }
                    if (page != null) onImages(listOf(page))
                }
            })
            add(ScanSource(tr("Bilder öffnen …"), Icons.Outlined.Image) {
                scope.launch {
                    val files = withContext(Dispatchers.IO) {
                        DesktopActions.pickFiles(tr("Bilder der Seiten öffnen"), multiple = true, extensions = listOf("jpg", "jpeg", "png", "bmp", "gif", "webp", "tif", "tiff", "heic"))
                    }
                    if (files.isNotEmpty()) onImages(files)
                }
            })
        }
    }
}
