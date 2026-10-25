package io.github.thepytales.llmhub.ui

/** Rules of the navigation, kept apart from the screens so they can be tested (see StorageAndSafetyTest). */

/**
 * Whether the screen with back stack id [from] may still act: only the screen on top does. A second tap on "Zurück"
 * while a screen slides away (the chat's menu button sits where the back arrow was) must not remove the chat too.
 */
fun isOnTop(currentId: String?, from: String) = currentId == from

/** Screens of the side menu: there the menu also opens with a swipe from the left. */
fun isTopLevel(route: String?) =
    route == null || route.startsWith("chat") || route in setOf("transcripts", "templates", "tasks", "images")
