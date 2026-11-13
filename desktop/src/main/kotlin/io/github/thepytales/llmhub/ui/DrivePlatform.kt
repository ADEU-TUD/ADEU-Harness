package io.github.thepytales.llmhub.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import io.github.thepytales.llmhub.desktop.DroppedFiles
import io.github.thepytales.llmhub.desktop.FileListTransferable
import io.github.thepytales.llmhub.desktop.FileTransfer
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import java.io.File

/** Files dragged from the Explorer onto the window while the Dateien tab is open go into its open folder (AP-16). */
@Composable
fun DroppedFilesEffect(onFiles: (List<File>) -> Unit) {
    val current by rememberUpdatedState(onFiles)
    LaunchedEffect(Unit) { DroppedFiles.files.collect { current(it) } }
}

/** Moving files by dragging them onto a folder with the mouse (AP-16). */
const val DRAG_TO_MOVE = true

/**
 * Out of Lora into the Explorer, an Outlook mail or another program (AP-59): a handle to drag the file (or the
 * selection) by; it arrives there as a copy.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun FileDragHandle(files: () -> List<File>) {
    val current by rememberUpdatedState(files)
    Icon(
        androidx.compose.material.icons.Icons.Outlined.DragIndicator,
        contentDescription = tr("In den Explorer oder eine Mail ziehen"),
        tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = androidx.compose.ui.Modifier.padding(horizontal = 4.dp).size(20.dp)
            .pointerHoverIcon(androidx.compose.ui.input.pointer.PointerIcon.Hand)
            .dragAndDropSource(drawDragDecoration = {}) {
                detectDragGestures(onDragStart = {
                    val list = current().filter { it.exists() }
                    if (list.isNotEmpty()) startTransfer(
                        androidx.compose.ui.draganddrop.DragAndDropTransferData(
                            androidx.compose.ui.draganddrop.DragAndDropTransferable(FileListTransferable(list)),
                            supportedActions = listOf(androidx.compose.ui.draganddrop.DragAndDropTransferAction.Copy),
                        ),
                    )
                }) { change, _ -> change.consume() }
            },
    )
}

/** Files onto the clipboard as files (AP-59): Strg+V in the Explorer copies them, in an Outlook mail attaches them. */
const val CAN_COPY_FILES = true

fun copyFilesToClipboard(files: List<File>): Boolean = FileTransfer.copy(files)
