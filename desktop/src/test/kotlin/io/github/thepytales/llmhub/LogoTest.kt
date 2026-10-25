package io.github.thepytales.llmhub

import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import io.github.thepytales.llmhub.ui.AppLogo
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders the logo at every stage of the start animation (frames in build/logo-frames, for checking by eye). */
class LogoTest {
    @Test
    fun logoBuildsUp() {
        val dir = File("build/logo-frames").apply { mkdirs() }
        val frames = 24
        for (i in 0..frames) {
            val t = i / frames.toFloat()
            ImageComposeScene(240, 240, Density(2f)) { AppLogo(Modifier.size(120.dp), reveal = t) }.use { scene ->
                val img = scene.render()
                val png = img.encodeToData(EncodedImageFormat.PNG)!!.bytes
                File(dir, "frame%02d.png".format(i)).writeBytes(png)
                // The finished logo has its white points: the pixel at the top point must be white.
                if (i == frames) {
                    val bmp = org.jetbrains.skia.Bitmap.makeFromImage(img)
                    val c = bmp.getColor(120, 56)
                    assertTrue((c and 0xFFFFFF) == 0xFFFFFF, "oberer Punkt weiß: ${Integer.toHexString(c)}")
                }
            }
        }
    }
}
