package io.github.thepytales.llmhub.ui

import androidx.compose.runtime.Composable
import java.io.File

/** Android: files dragged onto the window do not exist (Windows: from the Explorer into the open folder). */
@Composable
fun DroppedFilesEffect(onFiles: (List<File>) -> Unit) {}

/** Moving files by dragging them onto a folder (Windows with the mouse; Android uses „Verschieben nach …“). */
const val DRAG_TO_MOVE = false
