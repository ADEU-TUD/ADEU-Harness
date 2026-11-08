package io.github.thepytales.llmhub.ui

import androidx.compose.runtime.Composable
import java.io.File

/** Android: files dragged onto the window do not exist (Windows: from the Explorer into the open folder). */
@Composable
fun DroppedFilesEffect(onFiles: (List<File>) -> Unit) {}

/** Moving files by dragging them onto a folder (Windows with the mouse; Android uses „Verschieben nach …“). */
const val DRAG_TO_MOVE = false

/** Dragging files out of the app into other programs (AP-59): only in the Windows app. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun FileDragHandle(files: () -> List<File>) {}

/** Files onto the clipboard as files (AP-59): only in the Windows app. */
const val CAN_COPY_FILES = false

@Suppress("UNUSED_PARAMETER")
fun copyFilesToClipboard(files: List<File>): Boolean = false
