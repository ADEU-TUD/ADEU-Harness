package io.github.thepytales.llmhub.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import io.github.thepytales.llmhub.files.ProjectTree

/** Choosing folders on the device (Windows has its own version of this file; Android has no plain folder paths). */
object FolderAccess {
    const val SUPPORTED = false

    /** Asks for a folder; returns its path or null. */
    suspend fun pick(title: String): String? = null

    fun open(path: String) {}
}

/** Asks for a project folder in the system dialog (also Nextcloud, Drive …, if their app offers it) and keeps the permission. */
@Composable
fun rememberProjectFolderPicker(onPicked: (String) -> Unit): (title: String) -> Unit {
    val context = LocalContext.current
    val current by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { ProjectTree.keep(context, uri.toString()) }
            current(uri.toString())
        }
    }
    return { _ -> launcher.launch(null) }
}
