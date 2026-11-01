package io.github.thepytales.llmhub.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import io.github.thepytales.llmhub.desktop.DroppedFiles
import java.io.File

/** Files dragged from the Explorer onto the window while the Dateien tab is open go into its open folder (AP-16). */
@Composable
fun DroppedFilesEffect(onFiles: (List<File>) -> Unit) {
    val current by rememberUpdatedState(onFiles)
    LaunchedEffect(Unit) { DroppedFiles.files.collect { current(it) } }
}

/** Moving files by dragging them onto a folder with the mouse (AP-16). */
const val DRAG_TO_MOVE = true
