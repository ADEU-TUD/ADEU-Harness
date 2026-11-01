package io.github.thepytales.llmhub.data

import android.content.Context
import io.github.thepytales.llmhub.desktop.Paths
import java.io.File

/** The visible Lora-Ordner (AP-16) on Windows: the folder the user chose, with the app's data hidden in its „.lora“. */
object LoraRoot {
    fun dir(context: Context): File =
        if (runCatching { context.filesDir.canonicalFile == Paths.data.canonicalFile }.getOrDefault(false)) Paths.root else File(context.filesDir, "Lora")

    const val HOLDS_DATA = true
}
