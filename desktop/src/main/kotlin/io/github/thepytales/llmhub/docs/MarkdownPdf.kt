package io.github.thepytales.llmhub.docs

import java.io.File

/** Markdown → A4 PDF with the built-in renderer (fallback when no browser can print the designed page). */
object MarkdownPdf {
    /** Writes the PDF and returns the number of pages. */
    fun write(file: File, title: String, markdown: String, theme: DocTheme = DocTheme()): Int {
        HtmlPdf.renderBuiltIn(HtmlPdf.withPrintColors(DocumentWriter.toHtml(title, markdown, theme, print = true)), file)
        return HtmlPdf.pageCount(file)
    }
}
