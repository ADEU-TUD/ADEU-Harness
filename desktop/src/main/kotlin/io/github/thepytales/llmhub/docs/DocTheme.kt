package io.github.thepytales.llmhub.docs

import android.util.Base64
import io.github.thepytales.llmhub.desktop.Images

enum class DocStyle(val label: String, val description: String) {
    MODERN("Modern", "Serifenlose Schrift, Akzentleiste und farbige Überschriften."),
    BOLD("Kräftig", "Farbiger Titelblock, farbige Tabellenköpfe und Hinweisboxen."),
    ELEGANT("Elegant", "Serifen-Überschriften in Akzentfarbe, feine Linien, viel Weißraum."),
    CLASSIC("Klassisch", "Serifenschrift in Schwarz, zentrierter Titel – wie ein Brief."),
    MINIMAL("Schlicht", "Schwarz auf Weiß ohne Schmuck."),
    TUD("TU Dresden", "Corporate Design der TU Dresden: TUD-Blau, Logo im Kopf, Briefe mit Anschriftfeld."),
    ;

    companion object {
        fun parse(name: String?): DocStyle? {
            val n = name?.trim()?.lowercase().orEmpty()
            if (n.isEmpty()) return null
            return entries.firstOrNull { it.name.equals(n, true) || it.label.equals(n, true) } ?: when (n) {
                "modern", "business", "sachlich" -> MODERN
                "kraeftig", "farbig", "bunt", "colorful", "vivid" -> BOLD
                "classic", "klassik", "brief", "letter", "serif" -> CLASSIC
                "minimal", "schlicht", "plain", "simple" -> MINIMAL
                "elegant", "edel" -> ELEGANT
                "tud", "tu dresden", "tu-dresden", "tu_dresden", "technische universität dresden", "uni", "universität" -> TUD
                else -> null
            }
        }
    }
}

/** Appearance of generated documents: style, accent color, logo and footer line. */
data class DocTheme(
    val style: DocStyle = DocStyle.MODERN,
    /** Accent color as #RRGGBB. */
    val accent: String = DEFAULT_ACCENT,
    /** Logo as PNG bytes. */
    val logo: ByteArray? = null,
    val footer: String = "",
    /** Organizational unit shown next to the logo in the TU Dresden style, one level per line. */
    val unit: String = "",
) {
    val logoDataUrl: String? get() = logo?.let { "data:image/png;base64," + Base64.encodeToString(it, Base64.NO_WRAP) }

    /** Width/height of the logo in pixels, or null. */
    val logoSize: Pair<Int, Int>? by lazy { logo?.let { Images.size(it) } }

    /** Accent as hex without '#', for Word. */
    val accentHex: String get() = accent.removePrefix("#").uppercase()

    val accentArgb: Int get() = (0xFF000000 or accentHex.toLong(16)).toInt()

    companion object {
        const val DEFAULT_ACCENT = "#1E40AF"

        private val HEX = Regex("^#?([0-9a-fA-F]{6})$")
        private val NAMED = mapOf(
            "blau" to "#1E40AF", "blue" to "#1E40AF", "hellblau" to "#0284C7", "türkis" to "#0D9488", "teal" to "#0D9488",
            "grün" to "#15803D", "green" to "#15803D", "rot" to "#B91C1C", "red" to "#B91C1C", "orange" to "#C2410C",
            "gelb" to "#B45309", "gold" to "#A16207", "lila" to "#6D28D9", "violett" to "#6D28D9", "purple" to "#6D28D9",
            "pink" to "#BE185D", "grau" to "#475569", "gray" to "#475569", "grey" to "#475569", "schwarz" to "#111827",
            "black" to "#111827", "navy" to "#1E3A8A", "dunkelblau" to "#1E3A8A", "bordeaux" to "#7F1D1D",
        )

        /** Normalizes "#abc123", "abc123" or a color name to #RRGGBB; null if unknown. */
        fun parseColor(value: String?): String? {
            val v = value?.trim().orEmpty()
            if (v.isEmpty()) return null
            HEX.find(v)?.let { return "#" + it.groupValues[1].uppercase() }
            Regex("^#?([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])$").find(v)?.let { m ->
                return "#" + m.groupValues.drop(1).joinToString("") { it + it }.uppercase()
            }
            return NAMED[v.lowercase()]
        }
    }
}
