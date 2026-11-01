package io.github.thepytales.llmhub.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Messages for the user that should not vanish like a toast: errors with "Erneut versuchen", deletions with
 * "Rückgängig". Shown as a banner by AppRoot (Android and Windows).
 */
object Notices {
    data class Notice(
        val text: String,
        val action: String? = null,
        val onAction: (() -> Unit)? = null,
        /** Called when the banner disappears without the action (e.g. to delete for good). */
        val onTimeout: (() -> Unit)? = null,
        val error: Boolean = false,
    )

    private val _flow = MutableSharedFlow<Notice>(extraBufferCapacity = 16)
    val flow: SharedFlow<Notice> = _flow

    fun show(notice: Notice) {
        if (!_flow.tryEmit(notice)) notice.onTimeout?.invoke()
    }

    fun info(text: String) = show(Notice(text))

    fun error(text: String, retry: (() -> Unit)? = null) =
        show(Notice(text, action = retry?.let { tr("Erneut versuchen") }, onAction = retry, error = true))

    /** "… gelöscht – Rückgängig": [restore] on the button, otherwise [commit] once the banner is gone. */
    fun undo(text: String, restore: () -> Unit, commit: () -> Unit = {}) =
        show(Notice(text, action = tr("Rückgängig"), onAction = restore, onTimeout = commit))
}
