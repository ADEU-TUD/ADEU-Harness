package android.content

import io.github.thepytales.llmhub.data.FilePrefs
import io.github.thepytales.llmhub.desktop.Paths
import java.io.File

/**
 * Minimal stand-in for Android's Context, so code shared with the Android app compiles unchanged on
 * Windows. It only knows the two folders the shared code uses.
 */
open class Context(val filesDir: File, val cacheDir: File) {
    open val applicationContext: Context get() = this

    /**
     * Plain (unencrypted) preferences in [filesDir]; used for harmless UI state. The state of this PC (window, update
     * state) stays on the PC ([Paths.machine]) when the data folder is synced (AP-12); taken over once from [filesDir].
     */
    fun getSharedPreferences(name: String, @Suppress("UNUSED_PARAMETER") mode: Int): SharedPreferences {
        val shared = File(filesDir, "$name.prefs.json")
        if (name !in PER_PC || filesDir.canonicalFile != Paths.data.canonicalFile) return FilePrefs.plain(shared)
        val own = File(Paths.machine, "$name.prefs.json")
        if (!own.exists() && shared.isFile && own != shared) runCatching { shared.copyTo(own) }
        return FilePrefs.plain(own)
    }

    companion object {
        const val MODE_PRIVATE = 0

        /** Preferences that belong to this PC, not to the (possibly synced) data folder. */
        private val PER_PC = setOf("ui_state", "updates")
    }
}
