package io.github.thepytales.llmhub

import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import io.github.thepytales.llmhub.ui.AppLogo
import io.github.thepytales.llmhub.ui.drawLogo
import org.jetbrains.skia.EncodedImageFormat
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The logo (Lora reading on a light tile): renders every stage of the start animation (build/logo-frames, for
 * checking by eye) and exports the app icons to build/icons – Windows .ico, the PNGs of the window and the tray, and
 * the Android launcher and notification icons. After a change of the logo, copy them over (see README › Logo).
 */
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
                if (i == frames) {
                    val bmp = org.jetbrains.skia.Bitmap.makeFromImage(img)
                    // Lora's bright face shows on the tile (white glass with a faint tint towards its lower edge), the
                    // corners stay transparent (rounded tile).
                    val face = (0 until 240).flatMap { x -> (0 until 240).map { y -> bmp.getColor(x, y) } }
                        .count { (it ushr 24) == 0xFF && (it shr 16 and 0xFF) >= 0xF0 && (it shr 8 and 0xFF) >= 0xF0 && (it and 0xFF) >= 0xF0 }
                    assertTrue(face > 1500, "Loras helles Gesicht: $face Pixel")
                    assertTrue((bmp.getColor(1, 1) ushr 24) == 0, "Ecke transparent")
                }
            }
        }
    }

    @Test
    fun exportIcons() {
        val out = File("build/icons").apply { mkdirs() }
        // Windows: the .ico with the usual sizes, the window icon and the tray icon.
        val icoSizes = listOf(16, 20, 24, 32, 40, 48, 64, 128, 256)
        File(out, "app.ico").writeBytes(ico(icoSizes.map { it to png(render(it)) }))
        ImageIO.write(render(512), "png", File(out, "app.png"))
        ImageIO.write(render(64), "png", File(out, "tray.png"))
        // Android: adaptive icon foreground (Lora in the safe zone, no tile), monochrome layer and notification icon.
        val densities = listOf("mdpi" to 1f, "hdpi" to 1.5f, "xhdpi" to 2f, "xxhdpi" to 3f, "xxxhdpi" to 4f)
        for ((name, f) in densities) {
            val fg = (108 * f).toInt()
            val mipmap = File(out, "android/mipmap-$name").apply { mkdirs() }
            ImageIO.write(render(fg, tile = false, scale = 0.68f), "png", File(mipmap, "ic_launcher_foreground.png"))
            ImageIO.write(monochrome(render(fg, tile = false, scale = 0.68f)), "png", File(mipmap, "ic_launcher_monochrome.png"))
            val drawable = File(out, "android/drawable-$name").apply { mkdirs() }
            ImageIO.write(monochrome(render((24 * f).toInt(), tile = false, scale = 1.25f)), "png", File(drawable, "ic_notification.png"))
        }
        val ico = File(out, "app.ico")
        assertTrue(ico.length() > 10_000, "ico mit allen Größen: ${ico.length()} Bytes")
        val head = ByteBuffer.wrap(ico.readBytes(), 0, 6).order(ByteOrder.LITTLE_ENDIAN)
        assertTrue(head.getShort(0) == 0.toShort() && head.getShort(2) == 1.toShort() && head.getShort(4).toInt() == icoSizes.size, "ico-Kopf")
    }

    private fun render(px: Int, tile: Boolean = true, scale: Float = 1f): BufferedImage {
        val bitmap = ImageBitmap(px, px)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(px.toFloat(), px.toFloat())) {
            drawLogo(1f, tile = tile, scale = scale)
        }
        return bitmap.toAwtImage()
    }

    /** White with the alpha of the drawing; very dark parts (the eyes) stay faint, so the face still shows. */
    private fun monochrome(src: BufferedImage): BufferedImage {
        val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until src.height) for (x in 0 until src.width) {
            val c = src.getRGB(x, y)
            val a = c ushr 24
            val lum = (0.299 * (c shr 16 and 0xFF) + 0.587 * (c shr 8 and 0xFF) + 0.114 * (c and 0xFF)) / 255
            val alpha = (a * if (lum > 0.2) 1.0 else 0.25).toInt().coerceIn(0, 255)
            out.setRGB(x, y, (alpha shl 24) or 0xFFFFFF)
        }
        return out
    }

    private fun png(img: BufferedImage): ByteArray = ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()

    /** An .ico file with PNG images (supported since Windows Vista). */
    private fun ico(images: List<Pair<Int, ByteArray>>): ByteArray {
        val header = ByteBuffer.allocate(6 + 16 * images.size).order(ByteOrder.LITTLE_ENDIAN)
        header.putShort(0).putShort(1).putShort(images.size.toShort())
        var offset = 6 + 16 * images.size
        for ((size, data) in images) {
            val s = if (size >= 256) 0 else size
            header.put(s.toByte()).put(s.toByte()).put(0).put(0).putShort(1).putShort(32).putInt(data.size).putInt(offset)
            offset += data.size
        }
        return ByteArrayOutputStream().apply {
            write(header.array())
            images.forEach { write(it.second) }
        }.toByteArray()
    }
}
