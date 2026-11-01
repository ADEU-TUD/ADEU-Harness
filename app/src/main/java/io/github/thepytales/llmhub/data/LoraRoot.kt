package io.github.thepytales.llmhub.data

import android.content.Context
import java.io.File

/**
 * The visible Lora-Ordner (AP-16) – Android: inside the app („Lora“ in its files); a folder chosen in the system dialog
 * gets a copy of everything in it (see files/LoraMirror). Windows has its own version of this file (the chosen folder,
 * with the app's data hidden in its „.lora“).
 */
object LoraRoot {
    fun dir(context: Context): File = File(context.filesDir, "Lora")

    /** Whether the app's data lies inside the Lora-Ordner (Windows) – then it can be moved as a whole. */
    const val HOLDS_DATA = false
}
