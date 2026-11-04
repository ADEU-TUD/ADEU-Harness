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

    /** [medium]: Noto Sans Medium, the weight of headings in the CD. */
    private fun text(x: Float, y: Float, w: Float, h: Float, text: String, size: Float, color: String, medium: Boolean = false, align: String = "left", valign: String = "top", fill: String = "", padding: Float = 0f, radius: Float = 0f, lineHeight: Float = 1.2f) =
        DesignElement(kind = ElementKind.TEXT, x = x, y = y, w = w, h = h, text = text, fontSize = size, color = color, medium = medium, align = align, valign = valign, fit = true, fill = fill, padding = padding, radius = radius, lineHeight = lineHeight)

    /**
     * Scale of the 1080-line layout: the shorter side counts, so a portrait (9:16) or square frame gets the sizes of a
     * 1080-line picture instead of 1.8 times them (titles and lower thirds used to run out of the picture there).
     */
    private fun scale(width: Int, height: Int) = minOf(width, height) / 1080f

    /** The overlay as a one-page design of the project's size. */
    fun design(o: TextOverlay, width: Int, height: Int): Design {
        val s = scale(width, height)
        val w = width * MM
        val h = height * MM
        // Text never wider than the picture minus the margins (1:1 and 9:16 are narrower than the 1920 of the layout).
        fun wide(x: Float, want: Float) = minOf(px(want, s), w - x - px(60f, s))
        // Portrait: the title block moves down out of the Shape in the upper right corner.
        val dy = ((h - px(1080f, s)) / 2).coerceAtLeast(0f)
        val els = when (o.style) {
            "titel", "abspann" -> listOfNotNull(
                DesignElement(kind = ElementKind.RECT, x = 0f, y = 0f, w = w, h = h, fill = TudBrand.BLUE),
                DesignElement(kind = ElementKind.IMAGE, src = DesignBrand.LOGO_WHITE, fitMode = "contain", x = px(110f, s), y = px(90f, s), w = px(330f, s), h = px(330f, s) / DesignBrand.LOGO_RATIO),
                // The Shape of the CD in Blau 2, cut off at the top right; the text stays left of it. In a square frame
                // the title takes two lines and reaches higher: the Shape goes further up out of the picture.
                DesignElement(kind = ElementKind.TUD_SHAPE, x = w - px(620f, s), y = -px(if (dy == 0f && width < height * 1.5f) 400f else 140f, s), w = px(760f, s), h = px(760f, s), fill = TudBrand.LIGHT_BLUE),
                text(px(110f, s), dy + px(if (o.style == "titel") 400f else 340f, s), wide(px(110f, s), 1150f), px(360f, s), o.text, pt(96f, s), "#FFFFFF", medium = true, valign = "bottom", lineHeight = 1.08f),
                o.subtext.takeIf { it.isNotBlank() }?.let { text(px(110f, s), dy + px(790f, s), wide(px(110f, s), 1150f), px(200f, s), it, pt(44f, s), TudBrand.LIGHT_BLUE, lineHeight = 1.3f) },
            )
            "einblendung" -> listOf(
                text(px(160f, s), h - px(300f, s), w - px(320f, s), px(170f, s), o.text + if (o.subtext.isNotBlank()) "\n" + o.subtext else "", pt(46f, s), "#FFFFFF",
                    align = "center", valign = "middle", fill = TudBrand.BLUE, padding = px(24f, s), radius = px(10f, s), lineHeight = 1.25f),
            )
            else -> listOfNotNull( // bauchbinde
                DesignElement(kind = ElementKind.RECT, x = px(90f, s), y = h - px(270f, s), w = px(12f, s), h = px(160f, s), fill = TudBrand.LIGHT_BLUE),
                text(px(102f, s), h - px(270f, s), wide(px(102f, s), 900f), px(92f, s), o.text, pt(50f, s), "#FFFFFF", medium = true, valign = "middle", fill = TudBrand.BLUE, padding = px(22f, s)),
                o.subtext.takeIf { it.isNotBlank() }?.let { text(px(102f, s), h - px(178f, s), wide(px(102f, s), 900f), px(68f, s), it, pt(34f, s), TudBrand.BLUE, valign = "middle", fill = "#FFFFFF", padding = px(22f, s)) },
            )
        }
        return Design(id = "video-${o.id}", title = o.text, format = "video", width = w, height = h, pages = listOf(DesignPage(background = "", elements = els)))
    }

    /**
     * A subtitle: white text on a dark half-transparent box at the bottom centre; [raised] above a lower third or a
     * sentence at the bottom that is on at the same time (see [VideoProject.subtitleRaised]).
     */
    fun subtitleDesign(text: String, width: Int, height: Int, raised: Boolean = false): Design {
        val s = scale(width, height)
        val w = width * MM
        val h = height * MM
        val side = minOf(px(240f, s), w / 8)
        val e = text(side, h - px(if (raised) 490f else 250f, s), w - 2 * side, px(170f, s), text, pt(46f, s), "#FFFFFF", align = "center", valign = "bottom", lineHeight = 1.25f)
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
