package io.github.thepytales.llmhub.docs

import android.util.Base64

/**
 * Corporate design of TU Dresden for generated documents, from the official CD package (2025): colours of the
 * Office theme „TUD_2025“, the house font Noto Sans and the logo (blue for white paper, white for blue areas).
 * Logo and font are bundled as resources (resources/tud), so documents look right offline and on every device.
 */
object TudBrand {
    /** Dunkelblau: logo, title and headings. */
    const val BLUE = "#00008C"
    const val MID_BLUE = "#2F57B2"
    const val LIGHT_BLUE = "#97C6FF"
    const val NIGHT_BLUE = "#001450"
    /** Grey of the theme (dk2) for secondary text. */
    const val GREY = "#566371"
    const val LOGO_NAME = "TU Dresden"
    const val LOGO_WHITE_NAME = "TU Dresden weiß"
    /** Return line above the address field of letters. */
    const val POSTAL = "Technische Universität Dresden · 01062 Dresden"

    /** The logo without protection zone, 1190 × 400 px (printed 50.9 × 17 mm as in the official templates). */
    val logoBlue: ByteArray by lazy { resource("logo-blau.png") }
    val logoWhite: ByteArray by lazy { resource("logo-weiss.png") }

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
    fun apply(theme: DocTheme): DocTheme =
        if (theme.style != DocStyle.TUD) theme else theme.copy(accent = BLUE, logo = theme.logo ?: logoBlue)
}
