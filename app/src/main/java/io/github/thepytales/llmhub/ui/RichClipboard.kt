package io.github.thepytales.llmhub.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import io.github.thepytales.llmhub.AppContainer

/**
 * Zwischenablage mit Formatierung (AP-43 Stufe c), Android: text with its HTML, so Docs, Word and mail keep the
 * formatting; pasting reads the HTML other apps put there. The Windows version has a file of the same name.
 */
internal object RichClipboard {
    private fun manager(c: AppContainer) = c.appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    fun copy(c: AppContainer, html: String, plain: String, @Suppress("UNUSED_PARAMETER") rtf: String? = null): Boolean = runCatching {
        manager(c)?.setPrimaryClip(ClipData.newHtmlText("Lora", plain, html)) ?: return false
        true
    }.getOrDefault(false)

    /** The HTML on the clipboard, or null (only plain text, or nothing). */
    fun html(c: AppContainer): String? = runCatching {
        manager(c)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.htmlText?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /** RTF is not passed between Android apps. */
    fun rtf(@Suppress("UNUSED_PARAMETER") c: AppContainer): String? = null
}
