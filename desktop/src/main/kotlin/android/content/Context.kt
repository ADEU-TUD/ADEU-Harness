package android.content

import io.github.thepytales.llmhub.data.FilePrefs
import java.io.File

/**
 * Minimal stand-in for Android's Context, so code shared with the Android app compiles unchanged on
 * Windows. It only knows the two folders the shared code uses.
 */
open class Context(val filesDir: File, val cacheDir: File) {
    open val applicationContext: Context get() = this

    /** Plain (unencrypted) preferences in [filesDir]; used for harmless UI state. */
    fun getSharedPreferences(name: String, @Suppress("UNUSED_PARAMETER") mode: Int): SharedPreferences =
        FilePrefs.plain(File(filesDir, "$name.prefs.json"))

    companion object {
        const val MODE_PRIVATE = 0
    }
}
