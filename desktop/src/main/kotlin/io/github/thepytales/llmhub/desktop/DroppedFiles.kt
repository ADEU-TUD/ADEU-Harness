package io.github.thepytales.llmhub.desktop

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.File

/** Files dragged from Explorer onto the window; the open chat attaches them. */
object DroppedFiles {
    private val _files = MutableSharedFlow<List<File>>(extraBufferCapacity = 4)
    val files: SharedFlow<List<File>> = _files

    fun drop(files: List<File>) {
        if (files.isNotEmpty()) _files.tryEmit(files)
    }
}
