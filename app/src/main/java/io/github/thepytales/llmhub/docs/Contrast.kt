package io.github.thepytales.llmhub.docs

/** Color contrast after WCAG 2.1, so colored headings, links and table heads in documents stay readable. */
object Contrast {
    /** WCAG 2.1 AA for normal text: at least 4.5:1. */
    const val MIN = 4.5

    private fun channel(v: Int): Double {
        val c = v / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    private fun luminance(hex: String): Double {
        val v = hex.removePrefix("#").toLong(16).toInt()
        return 0.2126 * channel(v shr 16 and 0xFF) + 0.7152 * channel(v shr 8 and 0xFF) + 0.0722 * channel(v and 0xFF)
    }

    /** Contrast ratio of two #RRGGBB colors (1…21). */
    fun ratio(a: String, b: String): Double {
        val (l1, l2) = luminance(a) to luminance(b)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    /**
     * [accent] darkened just enough to be readable as text on white and as background of white text; colors that
     * already reach [MIN] stay as they are.
     */
    fun readable(accent: String): String {
        if (!Regex("^#[0-9A-Fa-f]{6}$").matches(accent)) return accent
        var hex = accent.uppercase()
        var steps = 0
        while (ratio(hex, "#FFFFFF") < MIN && steps < 40) {
            val v = hex.removePrefix("#").toLong(16).toInt()
            fun dark(c: Int) = (c * 0.93).toInt().coerceIn(0, 255)
            hex = "#%02X%02X%02X".format(dark(v shr 16 and 0xFF), dark(v shr 8 and 0xFF), dark(v and 0xFF))
            steps++
        }
        return hex
    }
}
