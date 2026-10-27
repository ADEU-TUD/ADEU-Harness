package io.github.thepytales.llmhub.files

/**
 * Formats both platforms read the same way (attachments and local folders): RTF, OpenDocument, e-mails, calendars,
 * contacts, e-books, notebooks, old Office files, ZIP archives and SVG.
 */
object FormatReaders {
    val EXTENSIONS = setOf("rtf", "odt", "ods", "odp", "eml", "msg", "ics", "ical", "vcs", "vcf", "vcard", "epub", "ipynb", "doc", "dot", "xls", "xlt", "ppt", "pps", "pot", "zip", "svg")

    fun handles(name: String) = name.substringAfterLast('.', "").lowercase() in EXTENSIONS

    /** Text of [bytes] of the file [name], null if the format is not one of these. */
    fun text(name: String, bytes: ByteArray): String? = when (name.substringAfterLast('.', "").lowercase()) {
        "rtf" -> if (RtfText.isRtf(bytes)) RtfText.text(bytes) else TextCodec.text(bytes)
        "odt", "ods", "odp" -> OdfText.markdown(bytes)
        "eml" -> MailText.eml(bytes)
        "msg" -> if (Cfb.isCfb(bytes)) MailText.msg(bytes) else MailText.eml(bytes)
        "ics", "ical", "vcs" -> CalendarText.ics(TextCodec.text(bytes))
        "vcf", "vcard" -> CalendarText.vcf(TextCodec.text(bytes))
        "epub" -> EpubText.text(bytes)
        "ipynb" -> Notebook.text(TextCodec.text(bytes))
        // Saved as .doc but really RTF or HTML (Word does that): read as such.
        "doc", "dot", "xls", "xlt", "ppt", "pps", "pot" -> when {
            RtfText.isRtf(bytes) -> RtfText.text(bytes)
            !Cfb.isCfb(bytes) && TextCodec.looksLikeText(bytes) -> TextCodec.text(bytes).let { if ("<html" in it.take(2000).lowercase()) io.github.thepytales.llmhub.agent.WebTools.htmlToText(it) else it }
            else -> LegacyOffice.text(name, bytes)
        }
        "zip" -> ZipText.listing(name, bytes)
        "svg" -> TextCodec.text(bytes)
        else -> null
    }
}
