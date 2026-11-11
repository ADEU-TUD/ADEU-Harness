package io.github.thepytales.llmhub.files

import java.awt.Font
import java.awt.GraphicsEnvironment
import java.io.File

/**
 * Fonts of this computer for the Schriften-Notfall (AP-91): the installed families (as Java sees them) and, for
 * embedding into Word files, the font file of a family's regular style (system and user font folders of Windows,
 * macOS and Linux). Android has a version of this file without files.
 */
object SystemFonts {
    val families: Set<String> by lazy {
        runCatching { GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet() }.getOrDefault(emptySet())
    }

    private val folders: List<File>
        get() {
            val home = System.getProperty("user.home").orEmpty()
            return listOfNotNull(
                System.getenv("WINDIR")?.let { File(it, "Fonts") },
                System.getenv("LOCALAPPDATA")?.let { File(it, "Microsoft/Windows/Fonts") },
                File("/Library/Fonts"), File("/System/Library/Fonts"), File("/System/Library/Fonts/Supplemental"), File(home, "Library/Fonts"),
                File("/usr/share/fonts"), File("/usr/local/share/fonts"), File(home, ".fonts"), File(home, ".local/share/fonts"),
            ).filter { it.isDirectory }
        }

    /** Font name (lower case) → file with the regular style, read once from the font folders. */
    private val files: Map<String, File> by lazy {
        val out = HashMap<String, File>()
        for (dir in folders) {
            dir.walkTopDown().maxDepth(4).filter { it.isFile && it.extension.lowercase() in setOf("ttf", "otf") }.forEach { f ->
                runCatching {
                    val font = Font.createFont(Font.TRUETYPE_FONT, f)
                    val family = font.getFamily(java.util.Locale.ROOT).lowercase()
                    val regular = font.fontName.lowercase().let { n -> n == family || n.endsWith(" regular") || n.endsWith("-regular") || n == "$family regular" }
                    if (regular || family !in out) out[family] = f
                    out.putIfAbsent(font.fontName.lowercase(), f)
                }
            }
        }
        out
    }

    fun installed(name: String): Boolean = families.any { it.equals(name, ignoreCase = true) }

    /** The TrueType/OpenType file of [family] on this computer, null if unknown. */
    fun fileOf(family: String): File? = files[family.lowercase()]

    /** Width of a typical text in [family] relative to Arial (1.0), measured here; null if not installed. */
    fun widthFactor(family: String): Double? {
        if (!installed(family)) return null
        val sample = "Die Studierenden reichen ihre Arbeit bis zum 30. November beim Prüfungsamt ein, 2026."
        val img = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        return try {
            val w = g.getFontMetrics(Font(family, Font.PLAIN, 100)).stringWidth(sample).toDouble()
            val arial = g.getFontMetrics(Font(if (installed("Arial")) "Arial" else Font.SANS_SERIF, Font.PLAIN, 100)).stringWidth(sample).toDouble()
            if (arial > 0) w / arial else null
        } finally {
            g.dispose()
        }
    }
}
