package io.github.thepytales.llmhub.video

import androidx.compose.ui.graphics.ImageBitmap
import io.github.thepytales.llmhub.creative.pngBytes
import io.github.thepytales.llmhub.creative.renderDesignPage
import io.github.thepytales.llmhub.data.Design
import io.github.thepytales.llmhub.data.DesignBrand
import io.github.thepytales.llmhub.data.DesignElement
import io.github.thepytales.llmhub.data.DesignFormat
import io.github.thepytales.llmhub.data.DesignPage
import io.github.thepytales.llmhub.data.ElementKind
import io.github.thepytales.llmhub.docs.TudBrand

/**
 * Titles, lower thirds and subtitles of the video editor in the TUD design (AP-25), drawn with the renderer of
 * „Erstellen“ (Noto Sans, TUD colours, logo) as transparent pictures that ffmpeg lays over the video – no font or
 * subtitle library of ffmpeg is needed, and the preview shows exactly the same picture.
 */
object VideoTitles {
    private const val MM = DesignFormat.MM_PER_PX

    /** Pixels of a 1920 × 1080 frame scaled to the project, in mm of the design. */
    private fun px(v: Float, scale: Float) = v * scale * MM

    /** Font size in pt for [px] pixels at 1080 lines. */
    private fun pt(px: Float, scale: Float) = px * scale * 0.75f

    private fun text(x: Float, y: Float, w: Float, h: Float, text: String, size: Float, color: String, bold: Boolean = false, align: String = "left", valign: String = "top", fill: String = "", padding: Float = 0f, radius: Float = 0f, lineHeight: Float = 1.2f) =
        DesignElement(kind = ElementKind.TEXT, x = x, y = y, w = w, h = h, text = text, fontSize = size, color = color, bold = bold, align = align, valign = valign, fit = true, fill = fill, padding = padding, radius = radius, lineHeight = lineHeight)

    /** The overlay as a one-page design of the project's size. */
    fun design(o: TextOverlay, width: Int, height: Int): Design {
        val s = height / 1080f
        val w = width * MM
        val h = height * MM
        val els = when (o.style) {
            "titel", "abspann" -> listOfNotNull(
                DesignElement(kind = ElementKind.RECT, x = 0f, y = 0f, w = w, h = h, fill = TudBrand.BLUE),
                DesignElement(kind = ElementKind.IMAGE, src = DesignBrand.LOGO_WHITE, fitMode = "contain", x = px(110f, s), y = px(90f, s), w = px(330f, s), h = px(330f, s) / DesignBrand.LOGO_RATIO),
                text(px(110f, s), px(if (o.style == "titel") 400f else 340f, s), w - px(220f, s), px(360f, s), o.text, pt(96f, s), "#FFFFFF", bold = true, valign = "bottom", lineHeight = 1.08f),
                o.subtext.takeIf { it.isNotBlank() }?.let { text(px(110f, s), px(790f, s), w - px(220f, s), px(200f, s), it, pt(44f, s), TudBrand.LIGHT_BLUE, lineHeight = 1.3f) },
                DesignElement(kind = ElementKind.RECT, x = px(110f, s), y = px(770f, s), w = px(140f, s), h = px(8f, s), fill = TudBrand.LIGHT_BLUE),
            )
            "einblendung" -> listOf(
                text(px(160f, s), h - px(300f, s), w - px(320f, s), px(170f, s), o.text + if (o.subtext.isNotBlank()) "\n" + o.subtext else "", pt(46f, s), "#FFFFFF",
                    bold = false, align = "center", valign = "middle", fill = TudBrand.BLUE, padding = px(24f, s), radius = px(10f, s), lineHeight = 1.25f),
            )
            else -> listOfNotNull( // bauchbinde
                DesignElement(kind = ElementKind.RECT, x = px(90f, s), y = h - px(270f, s), w = px(12f, s), h = px(160f, s), fill = TudBrand.LIGHT_BLUE),
                text(px(102f, s), h - px(270f, s), px(900f, s), px(92f, s), o.text, pt(50f, s), "#FFFFFF", bold = true, valign = "middle", fill = TudBrand.BLUE, padding = px(22f, s)),
                o.subtext.takeIf { it.isNotBlank() }?.let { text(px(102f, s), h - px(178f, s), px(900f, s), px(68f, s), it, pt(34f, s), TudBrand.BLUE, valign = "middle", fill = "#FFFFFF", padding = px(22f, s)) },
            )
        }
        return Design(id = "video-${o.id}", title = o.text, format = "video", width = w, height = h, pages = listOf(DesignPage(background = "", elements = els)))
    }

    /** A subtitle: white text on a dark half-transparent box at the bottom centre. */
    fun subtitleDesign(text: String, width: Int, height: Int): Design {
        val s = height / 1080f
        val w = width * MM
        val h = height * MM
        val e = text(px(240f, s), h - px(250f, s), w - px(480f, s), px(170f, s), text, pt(46f, s), "#FFFFFF", align = "center", valign = "bottom", lineHeight = 1.25f)
        // The box only as big as the text: lay out, then put a background behind exactly those lines.
        val laid = io.github.thepytales.llmhub.creative.DesignText.layout(e)
        val lineW = (0 until laid.layout.lineCount).maxOfOrNull { laid.layout.getLineRight(it) - laid.layout.getLineLeft(it) }?.div(io.github.thepytales.llmhub.creative.DesignText.REF) ?: 0f
        val textH = laid.layout.size.height / io.github.thepytales.llmhub.creative.DesignText.REF
        val pad = px(18f, s)
        val box = DesignElement(
            kind = ElementKind.RECT, x = (w - lineW) / 2 - pad, y = e.y + laid.top - pad * 0.6f, w = lineW + 2 * pad, h = textH + pad * 1.2f,
            fill = "#000000", opacity = 0.62f, radius = px(8f, s),
        )
        return Design(id = "video-sub", title = text, format = "video", width = w, height = h, pages = listOf(DesignPage(background = "", elements = listOf(box, e))))
    }

    fun render(d: Design, width: Int): ImageBitmap = renderDesignPage(d, 0, width, transparent = true)

    fun png(d: Design, width: Int): ByteArray = render(d, width).pngBytes()

    /** Fully transparent picture of the project's size (gaps between subtitles). */
    fun blank(width: Int, height: Int): ByteArray =
        render(Design(id = "video-blank", title = "", width = width * MM, height = height * MM, pages = listOf(DesignPage(background = ""))), width).pngBytes()
}
