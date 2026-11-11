package io.github.thepytales.llmhub.files

import de.lokal.whisper.desktop.DesktopSpeechPlatform
import io.github.thepytales.llmhub.desktop.Images
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

/**
 * Reading, turning and writing pictures for the photo preparation (AP-100) and the format converter (AP-90). PC side
 * (Android: Bitmap): Java's ImageIO for JPEG, PNG, GIF, BMP and TIFF; HEIC/HEIF (iPhone), AVIF and WebP through ffmpeg
 * (in the app on Windows, the system's on macOS/Linux) or macOS's „sips“; WebP is written with ffmpeg. Pixels come
 * unturned – the orientation of the file is applied by [orient] – and the output carries no metadata.
 */
object PhotoCodec {
    class Picture(val image: BufferedImage) {
        val width get() = image.width
        val height get() = image.height
    }

    private val IMAGEIO = setOf("jpg", "jpeg", "png", "gif", "bmp", "tif", "tiff")
    private val EXTERNAL = setOf("heic", "heif", "avif", "webp")

    private fun ffmpeg(): String? = runCatching { DesktopSpeechPlatform.ffmpeg() }.getOrNull()
    private val mac = System.getProperty("os.name").orEmpty().startsWith("Mac")

    fun readable(ext: String): Boolean = ext.lowercase().let { it in IMAGEIO || (it in EXTERNAL && (ffmpeg() != null || mac)) }

    fun writable(ext: String): Boolean = ext.lowercase().let { it in setOf("jpg", "jpeg", "png", "bmp") || (it == "webp" && ffmpeg() != null) }

    /** The picture of [file], null if it cannot be read here. */
    fun read(file: File): Picture? {
        val ext = file.extension.lowercase()
        if (ext !in EXTERNAL) runCatching { ImageIO.read(file) }.getOrNull()?.let { return Picture(it) }
        val tmp = File.createTempFile("lora-bild", ".png")
        try {
            ffmpeg()?.let { exe ->
                if (run(listOf(exe, "-v", "error", "-y", "-noautorotate", "-i", file.absolutePath, "-frames:v", "1", tmp.absolutePath)) && tmp.length() > 0) {
                    ImageIO.read(tmp)?.let { return Picture(it) }
                }
            }
            if (mac && run(listOf("sips", "-s", "format", "png", file.absolutePath, "--out", tmp.absolutePath)) && tmp.length() > 0) {
                ImageIO.read(tmp)?.let { return Picture(it) }
            }
            return null
        } finally {
            tmp.delete()
        }
    }

    private fun run(cmd: List<String>): Boolean = runCatching {
        val p = ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        if (!p.waitFor(120, TimeUnit.SECONDS)) { p.destroyForcibly(); false } else p.exitValue() == 0
    }.getOrDefault(false)

    fun hasAlpha(p: Picture): Boolean {
        if (!p.image.colorModel.hasAlpha()) return false
        val img = p.image
        val stepX = maxOf(1, img.width / 64)
        val stepY = maxOf(1, img.height / 64)
        for (y in 0 until img.height step stepY) for (x in 0 until img.width step stepX) if ((img.getRGB(x, y) ushr 24) < 250) return true
        return false
    }

    /** Turns/mirrors [p] so it stands upright for EXIF orientation [o] (1–8). */
    fun orient(p: Picture, o: Int): Picture {
        if (o !in 2..8) return p
        val w = p.width.toDouble()
        val h = p.height.toDouble()
        val swap = o in 5..8
        val t = AffineTransform()
        when (o) {
            2 -> { t.translate(w, 0.0); t.scale(-1.0, 1.0) }
            3 -> { t.translate(w, h); t.rotate(Math.PI) }
            4 -> { t.translate(0.0, h); t.scale(1.0, -1.0) }
            5 -> { t.rotate(Math.PI / 2); t.scale(1.0, -1.0) }
            6 -> { t.translate(h, 0.0); t.rotate(Math.PI / 2) }
            7 -> { t.scale(-1.0, 1.0); t.translate(-h, 0.0); t.translate(0.0, w); t.rotate(-Math.PI / 2) }
            8 -> { t.translate(0.0, w); t.rotate(-Math.PI / 2) }
        }
        val out = BufferedImage(if (swap) p.height else p.width, if (swap) p.width else p.height, if (p.image.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        g.drawImage(p.image, t, null)
        g.dispose()
        return Picture(out)
    }

    /** Scaled down so the longer side has at most [max] pixels (never up). */
    fun fit(p: Picture, max: Int): Picture =
        if (maxOf(p.width, p.height) <= max) p else Picture(Images.fit(p.image, max, opaque = !p.image.colorModel.hasAlpha()))

    /** Writes [p] as [format] (jpg, png, bmp, webp) into [out]; JPEG gets a white background where it was transparent. */
    fun write(p: Picture, out: File, format: String, quality: Int = 90) {
        out.parentFile?.mkdirs()
        when (format.lowercase()) {
            "jpg", "jpeg" -> out.writeBytes(Images.jpeg(p.image, quality / 100f))
            "png" -> ImageIO.write(p.image, "png", out)
            "bmp" -> ImageIO.write(Images.fit(p.image, maxOf(p.width, p.height), opaque = true), "bmp", out)
            "webp" -> {
                val exe = ffmpeg() ?: error("WebP braucht ffmpeg")
                val tmp = File.createTempFile("lora-bild", ".png")
                try {
                    ImageIO.write(p.image, "png", tmp)
                    out.delete()
                    if (!run(listOf(exe, "-v", "error", "-y", "-i", tmp.absolutePath, "-c:v", "libwebp", "-quality", quality.toString(), "-map_metadata", "-1", out.absolutePath)) || !out.exists()) {
                        error("WebP ließ sich nicht schreiben")
                    }
                } finally {
                    tmp.delete()
                }
            }
            else -> error(format)
        }
    }
}
