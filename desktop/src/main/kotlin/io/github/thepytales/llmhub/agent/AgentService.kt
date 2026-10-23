package io.github.thepytales.llmhub.agent

import android.content.Context

/**
 * On Android a foreground service keeps the app alive while [ChatRuns] generates answers. On Windows the
 * app keeps running in the notification area when the window is closed (see Main.kt), so nothing to do.
 */
object AgentService {
    @Suppress("UNUSED_PARAMETER")
    fun start(context: Context) {}
}
