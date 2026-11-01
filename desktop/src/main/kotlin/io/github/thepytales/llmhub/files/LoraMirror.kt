package io.github.thepytales.llmhub.files

import android.content.Context
import java.io.File

/** Android's copy of the Lora-Ordner in a folder of the system dialog; Windows uses the folder itself (AP-16). */
object LoraMirror {
    const val SUPPORTED = false

    data class Result(val up: Int, val down: Int, val removed: Int)

    fun keep(context: Context, tree: String) {}

    fun label(tree: String) = tree

    fun sync(context: Context, root: File, tree: String): Result = Result(0, 0, 0)
}
