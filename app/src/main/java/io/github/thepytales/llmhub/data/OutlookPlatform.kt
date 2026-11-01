package io.github.thepytales.llmhub.data

/** Android has no classic Outlook (AP-13 is for Windows); mail on Android comes later over the server (AP-27). */
object OutlookPlatform {
    const val supported = false
    val bridge: OutlookBridge? = null
}
