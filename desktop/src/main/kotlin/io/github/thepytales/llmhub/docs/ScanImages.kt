package io.github.thepytales.llmhub.docs

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * Pictures of the Dokumenten-Scanner (AP-62) on Windows: scanner and photo files read into [ScanRaster], results written
 * as JPEG/PNG with ImageIO (formats ImageIO lacks, e.g. WebP or HEIC, go through Skia). The Android version reads EXIF.
 */
object ScanImages {
    fun read(file: File, maxSide: Int): ScanRaster {
        val img = runCatching { ImageIO.read(file) }.getOrNull() ?: run {
            val skia = runCatching { org.jetbrains.skia.Image.makeFromEncoded(file.readBytes()) }.getOrNull()
            val jpeg = skia?.encodeToData(org.jetbrains.skia.EncodedImageFormat.JPEG, 95)?.bytes
            jpeg?.let { ImageIO.read(it.inputStream()) }
        } ?: throw IllegalArgumentException("„${file.name}“ ist kein lesbares Bild.")
        val r = ScanRaster(img.width, img.height)
        img.getRGB(0, 0, img.width, img.height, r.pixels, 0, img.width)
        return r.scaledDown(maxSide)
    }

    private fun image(r: ScanRaster): BufferedImage =
        BufferedImage(r.width, r.height, BufferedImage.TYPE_INT_RGB).also { it.setRGB(0, 0, r.width, r.height, r.pixels, 0, r.width) }

    fun jpeg(r: ScanRaster, quality: Int = 85): ByteArray {
        val out = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        try {
            ImageIO.createImageOutputStream(out).use { stream ->
                writer.output = stream
                val param = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = quality / 100f
                }
                writer.write(null, IIOImage(image(r), null, null), param)
            }
        } finally {
            writer.dispose()
        }
        return out.toByteArray()
    }

    fun png(r: ScanRaster): ByteArray {
        // Black and white as a 1-bit picture: a page is a few KB.
        val img = BufferedImage(r.width, r.height, BufferedImage.TYPE_BYTE_BINARY).also { it.setRGB(0, 0, r.width, r.height, r.pixels, 0, r.width) }
        return ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
    }
}
