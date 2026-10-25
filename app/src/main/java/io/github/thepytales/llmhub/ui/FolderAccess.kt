package io.github.thepytales.llmhub.ui

/** Choosing folders on the device (Windows has its own version of this file; Android has no plain folder paths). */
object FolderAccess {
    const val SUPPORTED = false

    /** Asks for a folder; returns its path or null. */
    suspend fun pick(title: String): String? = null

    fun open(path: String) {}
}
