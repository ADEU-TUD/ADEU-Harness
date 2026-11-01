package io.github.thepytales.llmhub.desktop

import java.util.ServiceLoader
import javax.accessibility.AccessibilityProvider

/**
 * Screen readers (AP-30). Compose Desktop describes every button, field and heading through Java's accessibility
 * interface. macOS reads it with VoiceOver out of the box. On Windows, NVDA and JAWS reach it through the Java Access
 * Bridge, which Java ships switched off (normally "jabswitch -enable" for all Java programs, with admin rights). Here it
 * is switched on for this app only – and only if the bundled Java contains it, because an assistive technology that is
 * named but missing would stop the app at startup. Must run before anything touches AWT.
 */
object Accessibility {
    private const val BRIDGE = "com.sun.java.accessibility.AccessBridge"
    private const val PROPERTY = "javax.accessibility.assistive_technologies"

    fun enableScreenReaders() {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return
        // Switched on already (jabswitch, accessibility.properties) or deliberately off.
        if (System.getProperty(PROPERTY) != null || System.getenv("LORA_NO_SCREENREADER") != null) return
        val available = runCatching {
            ServiceLoader.load(AccessibilityProvider::class.java, ClassLoader.getSystemClassLoader()).any { it.name == BRIDGE }
        }.getOrDefault(false)
        if (available) System.setProperty(PROPERTY, BRIDGE)
    }
}
