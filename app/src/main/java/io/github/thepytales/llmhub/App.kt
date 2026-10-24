package io.github.thepytales.llmhub

/** The app's container for code without access to the Android context (shared with the Windows version). */
object App {
    lateinit var container: AppContainer
        internal set
}
