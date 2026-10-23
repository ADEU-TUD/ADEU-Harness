package io.github.thepytales.llmhub.desktop

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/** Image helpers (Android: Bitmap/BitmapFactory). */
object Images {
    fun decode(bytes: ByteArray): BufferedImage? = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()

    /** Width and height without decoding the whole picture. */
    fun size(bytes: ByteArray): Pair<Int, Int>? = runCatching {
        ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
            val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: return null
            try {
                reader.input = input
                reader.getWidth(0) to reader.getHeight(0)
            } finally {
                reader.dispose()
            }
        }
    }.getOrNull()

    /** Scales down so the longer side is at most [max] pixels (never up). */
    fun fit(src: BufferedImage, max: Int, opaque: Boolean = false): BufferedImage {
        val scale = minOf(1.0, max.toDouble() / maxOf(src.width, src.height))
        val w = maxOf(1, (src.width * scale).toInt())
        val h = maxOf(1, (src.height * scale).toInt())
        if (scale >= 1.0 && !opaque) return src
        val out = BufferedImage(w, h, if (opaque) BufferedImage.TYPE_INT_RGB else BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        if (opaque) {
            g.color = Color.WHITE
            g.fillRect(0, 0, w, h)
        }
        g.drawImage(src, 0, 0, w, h, null)
        g.dispose()
        return out
    }

    fun png(img: BufferedImage): ByteArray = ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()

    fun jpeg(img: BufferedImage, quality: Float = 0.85f): ByteArray {
        val rgb = if (img.type == BufferedImage.TYPE_INT_RGB) img else fit(img, maxOf(img.width, img.height), opaque = true)
        val out = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        ImageIO.createImageOutputStream(out).use { stream ->
            writer.output = stream
            val param = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = quality
            }
            writer.write(null, IIOImage(rgb, null, null), param)
            writer.dispose()
        }
        return out.toByteArray()
    }

    /** JPEG data URL for vision models, at most 1280 px like on Android. */
    fun jpegDataUrl(img: BufferedImage, max: Int = 1280): String =
        "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg(fit(img, max, opaque = true)))

    fun dataUrl(bytes: ByteArray, max: Int = 1280): String? = decode(bytes)?.let { jpegDataUrl(it, max) }

    /** Bytes of a data URL ("data:image/png;base64,…"). */
    fun fromDataUrl(url: String): ByteArray = Base64.getMimeDecoder().decode(url.substringAfter(","))
}
