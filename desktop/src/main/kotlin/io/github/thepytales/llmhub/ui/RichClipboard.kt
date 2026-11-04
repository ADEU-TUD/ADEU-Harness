package io.github.thepytales.llmhub.ui

import io.github.thepytales.llmhub.AppContainer
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException

/**
 * Zwischenablage mit Formatierung (AP-43 Stufe c), Windows/macOS/Linux: text with its HTML (Word and the browser read
 * it as formatted text); pasting reads the HTML Word, Google Docs and web pages put there.
 */
internal object RichClipboard {
    private class Rich(private val html: String, private val plain: String) : Transferable, ClipboardOwner {
        private val flavors = arrayOf(DataFlavor.allHtmlFlavor, DataFlavor.fragmentHtmlFlavor, DataFlavor.selectionHtmlFlavor, DataFlavor.stringFlavor)
        override fun getTransferDataFlavors(): Array<DataFlavor> = flavors
        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavors.any { it.equals(flavor) }
        override fun getTransferData(flavor: DataFlavor): Any = when {
            flavor.equals(DataFlavor.stringFlavor) -> plain
            flavors.any { it.equals(flavor) } -> html
            else -> throw UnsupportedFlavorException(flavor)
        }
        override fun lostOwnership(clipboard: Clipboard?, contents: Transferable?) {}
    }

    fun copy(@Suppress("UNUSED_PARAMETER") c: AppContainer, html: String, plain: String): Boolean = runCatching {
        val rich = Rich(html, plain)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(rich, rich)
        true
    }.getOrDefault(false)

    /** The HTML on the clipboard, or null (only plain text, or nothing). */
    fun html(@Suppress("UNUSED_PARAMETER") c: AppContainer): String? = runCatching {
        val clip = Toolkit.getDefaultToolkit().systemClipboard
        if (!clip.isDataFlavorAvailable(DataFlavor.allHtmlFlavor)) return null
        (clip.getData(DataFlavor.allHtmlFlavor) as? String)?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
