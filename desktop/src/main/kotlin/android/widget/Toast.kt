package android.widget

import android.content.Context
import io.github.thepytales.llmhub.desktop.Toasts

/** Android's Toast on Windows: a short message at the bottom of the window (see Toasts). */
class Toast private constructor(private val text: CharSequence) {
    fun show() {
        Toasts.show(text.toString())
    }

    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1

        @Suppress("UNUSED_PARAMETER")
        fun makeText(context: Context, text: CharSequence, duration: Int) = Toast(text)
    }
}
