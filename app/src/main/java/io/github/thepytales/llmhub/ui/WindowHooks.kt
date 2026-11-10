package io.github.thepytales.llmhub.ui

/**
 * Lora in mehreren Fenstern (AP-84, nur am PC): what the menus offer as „In neuem Fenster öffnen“. Set by the desktop
 * app (ExtraWindows); null on Android, where the entries do not appear.
 */
object WindowHooks {
    /** Opens chat [id] (title [title]) in a window of its own, or brings that window to the front. */
    @Volatile var openChat: ((id: String, title: String) -> Unit)? = null

    /** Opens document [id] in a window of its own. */
    @Volatile var openDoc: ((id: String, title: String) -> Unit)? = null

    /** Lora's panel as a window of its own. */
    @Volatile var openLora: (() -> Unit)? = null

    /** Whether chat [id] is open in a window of its own (the main window then shows a note instead of a second input). */
    @Volatile var chatInWindow: (String) -> Boolean = { false }

    /** Brings the window of chat [id] to the front. */
    @Volatile var focusChat: (String) -> Unit = {}

    /** Closes the window of chat [id] (it then opens in the main window again). */
    @Volatile var closeChat: (String) -> Unit = {}
}
