package io.github.thepytales.llmhub.files

import android.content.Context
import java.io.File

/** Android's project folders from the system dialog; on Windows project folders are plain paths (see WorkFolder). */
object ProjectTree {
    const val SUPPORTED = false

    data class Entry(val path: String, val id: String, val dir: Boolean, val size: Long)

    fun isTree(path: String) = false

    fun label(path: String) = path

    fun keep(context: Context, path: String) {}

    fun problem(context: Context, path: String): String? = null

    fun deliver(context: Context, path: String, file: File, own: Set<String>): String = error("Nur auf Android")

    fun list(context: Context, path: String, max: Int = 300): List<Entry> = emptyList()

    suspend fun read(context: Context, path: String, entry: Entry): ReadResult = ReadResult(error = "Nur auf Android")

    fun open(context: Context, path: String) {}
}
