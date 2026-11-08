package io.github.thepytales.llmhub.tasks

import android.content.Context
import io.github.thepytales.llmhub.data.AppSettings

/**
 * Windows version of the background sync of the phone (AP-63): nothing to plan – the PCs sync the Lora-Ordner with the
 * cloud service's own client (AP-12), the app only waits for it.
 */
object BackgroundSync {
    const val MINUTES = 30L

    fun wanted(s: AppSettings): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun schedule(context: Context, s: AppSettings) = Unit
}
