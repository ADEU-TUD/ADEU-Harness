package io.github.thepytales.llmhub.desktop

import java.io.File

/**
 * Folders of the Windows app. Data lives per Windows user in %APPDATA%\ADEU LLM, so it survives updates
 * (a new version is just started instead of the old one) and needs no admin rights.
 */
object Paths {
    val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")
    private val home = File(System.getProperty("user.home"))

    /** Chats, settings, documents … (Android: filesDir). Can be changed with -Dadeu.data=… for tests. */
    val data: File by lazy {
        val custom = System.getProperty("adeu.data")?.takeIf { it.isNotBlank() }
        val dir = when {
            custom != null -> File(custom)
            windows -> File(System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: File(home, "AppData/Roaming").path, "ADEU LLM")
            else -> File(home, ".adeu-llm")
        }
        dir.apply { mkdirs() }
    }

    /** Temporary files (Android: cacheDir). */
    val cache: File by lazy { File(data, "cache").apply { mkdirs() } }

    /** Default folder for saving files the user exports (Documents\ADEU LLM, created on demand). */
    val exports: File get() = File(File(home, "Documents").takeIf { it.isDirectory } ?: home, "ADEU LLM")
}
