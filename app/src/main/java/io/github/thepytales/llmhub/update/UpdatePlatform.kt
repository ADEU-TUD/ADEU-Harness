package io.github.thepytales.llmhub.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import io.github.thepytales.llmhub.BuildConfig
import java.io.File

/**
 * Android: the APK is downloaded into the cache and handed to the system installer. Android always asks once more
 * ("Aktualisieren"), and the first time the app needs the permission to install apps (Einstellungen › Apps aus
 * dieser Quelle zulassen). Chats, settings and the API key stay, because the APK is signed with the same key.
 * Windows has its own version of this file.
 */
object UpdatePlatform {
    const val KEY = "android"

    /** Not for debug builds (other package name) or builds without a version number. */
    val supported: Boolean get() = !BuildConfig.DEBUG && !BuildConfig.VERSION_NAME.contains('-')

    fun downloadDir(context: Context) = File(context.cacheDir, "updates")

    /** Only Windows knows an update source given by the app folder. */
    fun presetSource(): String? = null

    @Suppress("UNUSED_PARAMETER")
    fun problem(context: Context, file: UpdateFile): String? {
        val free = context.cacheDir.usableSpace
        val needed = file.size * 2
        return if (free in 1 until needed) "Zu wenig freier Speicher für das Update (nötig etwa ${needed shr 20} MB)." else null
    }

    /** The APK from an earlier download, if it is complete. */
    fun prepared(context: Context, info: UpdateInfo): File? {
        val f = info.files[KEY] ?: return null
        return File(downloadDir(context), f.name).takeIf { it.isFile && it.length() == f.size && Updates.sha256(it) == f.sha256 }
    }

    @Suppress("UNUSED_PARAMETER")
    fun prepare(context: Context, apk: File, info: UpdateInfo, progress: (Float) -> Unit): File = apk

    /** Opens the system installer; returns what the user has to do first if the app may not install yet. */
    @Suppress("UNUSED_PARAMETER")
    fun install(context: Context, prepared: File, info: UpdateInfo): String? {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return "Einmalig nötig: In den Einstellungen „Dieser Quelle vertrauen“ bzw. „Apps aus dieser Quelle zulassen“ " +
                "einschalten, zurückkehren und noch einmal auf „Installieren“ tippen."
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", prepared)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return null
    }

    /** Removes APKs of versions that are installed by now. */
    fun cleanup(context: Context) {
        downloadDir(context).listFiles().orEmpty().forEach { f ->
            val version = Regex("(\\d+\\.\\d+\\.\\d+)").find(f.name)?.value
            if (version == null || !Versions.newer(version, Updates.installed)) f.delete()
        }
    }
}
