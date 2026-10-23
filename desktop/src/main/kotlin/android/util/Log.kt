package android.util

import io.github.thepytales.llmhub.AppLog

/** Android's Log on top of the app's log file. */
object Log {
    fun d(tag: String, msg: String): Int = AppLog.write("D", tag, msg, null)
    fun i(tag: String, msg: String): Int = AppLog.write("I", tag, msg, null)
    fun w(tag: String, msg: String, tr: Throwable? = null): Int = AppLog.write("W", tag, msg, tr)
    fun e(tag: String, msg: String, tr: Throwable? = null): Int = AppLog.write("E", tag, msg, tr)
}
