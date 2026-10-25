package io.github.thepytales.llmhub.ui

import io.github.thepytales.llmhub.desktop.DesktopActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Choosing folders on this PC: storage folders for documents and folders the agent may work in. */
object FolderAccess {
    const val SUPPORTED = true

    /** Asks for a folder; returns its path or null. */
    suspend fun pick(title: String): String? = withContext(Dispatchers.IO) { DesktopActions.pickFolder(title)?.absolutePath }

    fun open(path: String) {
        DesktopActions.open(File(path))
    }
}
