package io.github.thepytales.llmhub.data

import com.sun.jna.Platform

/** Windows: the classic Outlook over COM (AP-13); other systems have none. */
object OutlookPlatform {
    val supported: Boolean = Platform.isWindows()
    val bridge: OutlookBridge? get() = if (supported) io.github.thepytales.llmhub.desktop.ComOutlook else null
}
