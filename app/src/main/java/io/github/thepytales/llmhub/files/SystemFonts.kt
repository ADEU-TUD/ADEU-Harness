package io.github.thepytales.llmhub.files

import java.io.File

/**
 * Fonts of this device for the Schriften-Notfall (AP-91). Android: the families are guessed from the file names in
 * /system/fonts (Roboto, Noto …); embedding and measuring are PC features.
 */
object SystemFonts {
    val families: Set<String> by lazy {
        val names = File("/system/fonts").list().orEmpty().map { it.substringBeforeLast('.').substringBefore('-').replace(Regex("([a-z])([A-Z])"), "$1 $2") }
        (names + listOf("Roboto", "Noto Sans", "Noto Serif", "Droid Sans Mono")).toSet()
    }

    fun installed(name: String): Boolean = families.any { it.equals(name, ignoreCase = true) }

    fun fileOf(family: String): File? = null

    fun widthFactor(family: String): Double? = null
}
