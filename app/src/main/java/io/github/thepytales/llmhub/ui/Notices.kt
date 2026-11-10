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

    /**
     * An error in plain words (AP-74): raw exceptions become a sentence from [ErrorHelp]; without [retry] the matching
     * step („Schlüssel prüfen“, „Werkzeuge einschalten“ …) is the button.
     */
    fun error(text: String, retry: (() -> Unit)? = null) = error(ErrorHelp.explain(text), retry)

    fun error(e: Throwable, retry: (() -> Unit)? = null) = error(ErrorHelp.explain(e), retry)

    private fun error(plain: ErrorHelp.Plain, retry: (() -> Unit)?) {
        plain.technical?.let { io.github.thepytales.llmhub.ErrorLog.record("Hinweis ${plain.id}", IllegalStateException(it)) }
        val fix = plain.fix?.takeIf { it.route != null }
        val (action, onAction) = when {
            retry != null -> tr("Erneut versuchen") to retry
            fix != null -> tr(fix.label) to { LoraHost.go(fix.route!!) }
            else -> null to null
        }
        show(Notice(plain.text, action = action, onAction = onAction, error = true))
    }

    /** "… gelöscht – Rückgängig": [restore] on the button, otherwise [commit] once the banner is gone. */
    fun undo(text: String, restore: () -> Unit, commit: () -> Unit = {}) =
        show(Notice(text, action = tr("Rückgängig"), onAction = restore, onTimeout = commit))
}
