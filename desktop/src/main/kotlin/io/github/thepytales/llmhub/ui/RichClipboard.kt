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
 * it as formatted text) and RTF (older programs); pasting reads the HTML Word, Google Docs and web pages put there, or
 * the RTF when there is no HTML.
 */
internal object RichClipboard {
    /** RTF on the clipboard („Rich Text Format“ on Windows, public.rtf on macOS). */
    private val RTF = DataFlavor("text/rtf;class=java.io.InputStream")

    private class Rich(private val html: String, private val plain: String, private val rtf: String?) : Transferable, ClipboardOwner {
        private val flavors = listOfNotNull(DataFlavor.allHtmlFlavor, DataFlavor.fragmentHtmlFlavor, DataFlavor.selectionHtmlFlavor, RTF.takeIf { rtf != null }, DataFlavor.stringFlavor).toTypedArray()
        override fun getTransferDataFlavors(): Array<DataFlavor> = flavors
        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavors.any { it.equals(flavor) }
        override fun getTransferData(flavor: DataFlavor): Any = when {
            flavor.equals(DataFlavor.stringFlavor) -> plain
            rtf != null && flavor.equals(RTF) -> java.io.ByteArrayInputStream(rtf.toByteArray(Charsets.US_ASCII))
            flavors.any { it.equals(flavor) } -> html
            else -> throw UnsupportedFlavorException(flavor)
        }
        override fun lostOwnership(clipboard: Clipboard?, contents: Transferable?) {}
    }

    fun copy(@Suppress("UNUSED_PARAMETER") c: AppContainer, html: String, plain: String, rtf: String? = null): Boolean = runCatching {
        val rich = Rich(html, plain, rtf)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(rich, rich)
        true
    }.getOrDefault(false)

    /** The HTML on the clipboard, or null (only plain text, or nothing). */
    fun html(@Suppress("UNUSED_PARAMETER") c: AppContainer): String? = runCatching {
        val clip = Toolkit.getDefaultToolkit().systemClipboard
        if (!clip.isDataFlavorAvailable(DataFlavor.allHtmlFlavor)) return null
        (clip.getData(DataFlavor.allHtmlFlavor) as? String)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /** The RTF on the clipboard, or null. */
    fun rtf(@Suppress("UNUSED_PARAMETER") c: AppContainer): String? = runCatching {
        val clip = Toolkit.getDefaultToolkit().systemClipboard
        if (!clip.isDataFlavorAvailable(RTF)) return null
        (clip.getData(RTF) as? java.io.InputStream)?.use { String(it.readBytes(), Charsets.ISO_8859_1) }?.takeIf { it.startsWith("{\\rtf") }
    }.getOrNull()
}
