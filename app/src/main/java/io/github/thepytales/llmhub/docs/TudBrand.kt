package io.github.thepytales.llmhub.docs

import android.util.Base64

/**
 * Corporate design of TU Dresden for generated documents, from the official CD package (2025): the colours („TUD
 * Farben“ and the Office themes „TUD_2025“), the house font Noto Sans (headings Medium) and the logo (blue for white
 * paper, white for blue areas).
 * Logo and font are bundled as resources (resources/tud), so documents look right offline and on every device.
 */
object TudBrand {
    /** Brillantblau (the primary colour of the CD): logo, title and headings. */
    const val BLUE = "#00008C"
    /** Blau 1. */
    const val MID_BLUE = "#2F57B2"
    /** Blau 2. */
    const val LIGHT_BLUE = "#97C6FF"
    /** Dunkelblau. */
    const val NIGHT_BLUE = "#001450"
    /** Grey of the Office theme (dk2) for secondary text. */
    const val GREY = "#566371"
    /** Grau 100 % of the palette. */
    const val GREY_100 = "#323F4B"

    /**
     * The colours of the CD 2025 („TUD Farben“ of the official presentation template): the primary colours, then the
     * secondary colours in two steps (1 = strong, 2 = light) – name and #RRGGBB.
     */
    val PALETTE: List<Pair<String, String>> = listOf(
        "Brillantblau" to BLUE, "Dunkelblau" to NIGHT_BLUE, "Schwarz" to "#000000", "Grau" to GREY_100, "Weiß" to "#FFFFFF",
        "Blau 1" to MID_BLUE, "Violett 1" to "#7369BE", "Magenta 1" to "#BC1589", "Rot 1" to "#D20F41", "Orange 1" to "#C85000",
        "Gelb 1" to "#FFC700", "Oliv 1" to "#767A23", "Grün 1" to "#007D4B", "Türkis 1" to "#0A777F",
        "Blau 2" to LIGHT_BLUE, "Violett 2" to "#C8C8FF", "Magenta 2" to "#FFB9FF", "Rot 2" to "#FFAAA5", "Orange 2" to "#FFBE78",
        "Gelb 2" to "#FFE483", "Oliv 2" to "#D2DC46", "Grün 2" to "#8CE6AA", "Türkis 2" to "#8CE6D7",
    )

    /** Font of headings in the CD (Office theme „TUD_2025_Noto Sans“: major font Noto Sans Medium, minor Noto Sans). */
    const val HEAD_FONT = "Noto Sans Medium"
    const val BODY_FONT = "Noto Sans"

    /**
     * Colour scheme „TUD_2025_Blau-Türkis“ as DrawingML (the scheme of the official presentation template), for the
     * themes of generated PowerPoint files.
     */
    const val OFFICE_COLOR_SCHEME = "<a:clrScheme name=\"TUD_2025_Blau-Türkis\"><a:dk1><a:srgbClr val=\"000000\"/></a:dk1>" +
        "<a:lt1><a:srgbClr val=\"FFFFFF\"/></a:lt1><a:dk2><a:srgbClr val=\"566371\"/></a:dk2><a:lt2><a:srgbClr val=\"FFFFFF\"/></a:lt2>" +
        "<a:accent1><a:srgbClr val=\"00008C\"/></a:accent1><a:accent2><a:srgbClr val=\"97C6FF\"/></a:accent2>" +
        "<a:accent3><a:srgbClr val=\"2F57B2\"/></a:accent3><a:accent4><a:srgbClr val=\"001450\"/></a:accent4>" +
        "<a:accent5><a:srgbClr val=\"0A777F\"/></a:accent5><a:accent6><a:srgbClr val=\"8CE6D7\"/></a:accent6>" +
        "<a:hlink><a:srgbClr val=\"00008C\"/></a:hlink><a:folHlink><a:srgbClr val=\"2F57B2\"/></a:folHlink></a:clrScheme>"

    /** Font scheme „TUD_2025_Noto Sans“ as DrawingML. */
    const val OFFICE_FONT_SCHEME = "<a:fontScheme name=\"TUD_2025_Noto Sans\"><a:majorFont><a:latin typeface=\"Noto Sans Medium\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:majorFont>" +
        "<a:minorFont><a:latin typeface=\"Noto Sans\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:minorFont></a:fontScheme>"
    const val LOGO_NAME = "TU Dresden"
    const val LOGO_WHITE_NAME = "TU Dresden weiß"
    /** Return line above the address field of letters. */
    const val POSTAL = "Technische Universität Dresden · 01062 Dresden"

    /** The logo without protection zone, 1190 × 400 px (printed 50.9 × 17 mm as in the official templates). */
    val logoBlue: ByteArray by lazy { resource("logo-blau.png") }
    val logoWhite: ByteArray by lazy { resource("logo-weiss.png") }

    /** The short logo „TUD“ of the CD (footer of the slides), 900 × 289 px without protection zone. */
    val logoShortBlue: ByteArray by lazy { resource("logo-kurz-blau.png") }
    val logoShortWhite: ByteArray by lazy { resource("logo-kurz-weiss.png") }
    const val LOGO_SHORT_RATIO = 900.0 / 289.0

    /**
     * The Shape of the CD: the two components of the logo as polygons in a square of 600 × 600 (official SVG). It
     * may be rotated in 45° steps, mirrored, scaled, filled with TUD colours or a photo and cut off at the edge –
     * never distorted, never as an outline, several Shapes of a layout the same size and without overlap.
     */
    val SHAPE: List<FloatArray> = listOf(
        floatArrayOf(600f, 356.5f, 393f, 393f, 393f, 207f, 186f, 0f, 424.3f, 0f, 600f, 175.7f),
        floatArrayOf(0f, 243.5f, 207f, 207f, 207f, 393f, 414f, 600f, 175.7f, 600f, 0f, 424.3f),
    )

    /** SVG of the Shape in a box of [side] at ([x], [y]) in [color] (handouts and other HTML). */
    fun shapeSvgPolygons(x: Double, y: Double, side: Double, color: String): String = SHAPE.joinToString("") { poly ->
        val pts = (poly.indices step 2).joinToString(" ") { i ->
            "%.3f,%.3f".format(java.util.Locale.ROOT, x + poly[i] * side / 600.0, y + poly[i + 1] * side / 600.0)
        }
        "<polygon points=\"$pts\" fill=\"$color\"/>"
    }

    /** @font-face rules embedding Noto Sans (Latin, weights 400–700, plus italic) as data URLs. */
    val fontFaces: String by lazy {
        runCatching {
            fun face(file: String, style: String) =
                "@font-face{font-family:'Noto Sans';font-style:$style;font-weight:400 700;" +
                    "src:url(data:font/woff2;base64,${Base64.encodeToString(resource(file), Base64.NO_WRAP)}) format('woff2')}"
            face("NotoSans.woff2", "normal") + face("NotoSans-Italic.woff2", "italic")
        }.getOrDefault("")
    }

    private fun resource(name: String): ByteArray =
        (TudBrand::class.java.getResourceAsStream("/tud/$name") ?: error("Ressource tud/$name fehlt")).use { it.readBytes() }

    /** The built-in logo for a name like "tud", "TU Dresden" or "TU Dresden weiß"; null for other names. */
    fun logo(name: String?): ByteArray? {
        val n = name?.trim()?.lowercase()?.removePrefix("logo:")?.trim()?.replace("-", " ")?.replace("_", " ") ?: return null
        val white = listOf("weiß", "weiss", "white", "negativ", "invers").any { it in n }
        val base = n.replace(Regex("weiß|weiss|white|negativ|invers"), "").trim()
        if (base !in setOf("tud", "tu dresden", "technische universität dresden", "tu")) return null
        return if (white) logoWhite else logoBlue
    }

    /** Applies the design: TUD blue as accent and the blue logo unless another logo was chosen. */
    /** TU Dresden: its blue and logo. Other styles: the accent darkened if it would be too light to read. */
    fun apply(theme: DocTheme): DocTheme =
        if (theme.style != DocStyle.TUD) theme.copy(accent = Contrast.readable(theme.accent))
        else theme.copy(accent = BLUE, logo = theme.logo ?: logoBlue)
}

/**
 * Layout of the slides after the official presentation template of the CD 2025 (16:9, 33.867 × 19.05 cm) – the same
 * positions in the PowerPoint export, the handout and the preview of the slide editor. All values in cm.
 */
object TudSlide {
    const val W = 33.867
    const val H = 19.05
    /** Left edge and width of titles and content. */
    const val X = 1.69
    const val CONTENT_W = 30.48
    /** Slide title: top left, Noto Sans Medium 32 pt, black. */
    const val TITLE_Y = 1.12
    const val TITLE_H = 3.0
    const val TITLE_PT = 32
    const val TITLE_LONG_PT = 26
    /** Content between the protection zones below the title and above the footer. */
    const val BODY_TOP = 4.82
    const val BODY_BOTTOM = 16.23
    /** Footer: the short logo „TUD“ and „Titel • Vortragende • Seite N“ in Brillantblau, 12 pt. */
    const val FOOT_Y = 17.47
    const val FOOT_LOGO_W = 1.43
    const val FOOT_LOGO_H = 0.46
    const val FOOT_TEXT_X = 4.33
    const val FOOT_TEXT_W = 20.1
    const val FOOT_PT = 12
    /** Title slide: white logo top left, subtitle above the title, unit at the bottom. */
    const val LOGO_Y = 1.12
    const val LOGO_H = 1.83
    const val SUBTITLE_Y = 4.82
    const val SUBTITLE_H = 3.9
    const val COVER_TITLE_Y = 9.53
    const val COVER_TITLE_H = 6.1
    const val COVER_TITLE_PT = 44
    const val COVER_FOOT_Y = 17.53
    /** Section and closing slides: a Shape in Blau 2 cut off at the top right. */
    const val SHAPE_X = 20.5
    const val SHAPE_Y = -5.0
    const val SHAPE_SIDE = 15.0

    /** „Titel • Vortragende • Seite N“. */
    fun footer(footer: String, number: Int) = listOf(footer.trim(), "Seite $number").filter { it.isNotEmpty() }.joinToString(" • ")
}

