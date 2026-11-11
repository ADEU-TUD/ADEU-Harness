package io.github.thepytales.llmhub.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.os.Build
import java.io.File

/**
 * Reading, turning and writing pictures for the photo preparation (AP-100) and the format converter (AP-90). Android
 * side (PC: ImageIO with ffmpeg): BitmapFactory reads JPEG, PNG, GIF, BMP, WebP and – from Android 9 – HEIC/HEIF;
 * pixels come unturned, the orientation of the file is applied by [orient]; the output carries no metadata.
 */
object PhotoCodec {
    class Picture(val bitmap: Bitmap) {
        val width get() = bitmap.width
        val height get() = bitmap.height
    }

    fun readable(ext: String): Boolean = when (ext.lowercase()) {
        "jpg", "jpeg", "png", "gif", "bmp", "webp" -> true
        "heic", "heif" -> Build.VERSION.SDK_INT >= 28
        "avif" -> Build.VERSION.SDK_INT >= 31
        else -> false
    }

    fun writable(ext: String): Boolean = ext.lowercase() in setOf("jpg", "jpeg", "png", "webp")

    fun read(file: File): Picture? = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()?.let(::Picture)

    fun hasAlpha(p: Picture): Boolean {
        if (!p.bitmap.hasAlpha()) return false
        val stepX = maxOf(1, p.width / 64)
        val stepY = maxOf(1, p.height / 64)
        for (y in 0 until p.height step stepY) for (x in 0 until p.width step stepX) if (Color.alpha(p.bitmap.getPixel(x, y)) < 250) return true
        return false
    }

    /** Turns/mirrors [p] so it stands upright for EXIF orientation [o] (1–8). */
    fun orient(p: Picture, o: Int): Picture {
        if (o !in 2..8) return p
        val m = Matrix()
        when (o) {
            2 -> m.setScale(-1f, 1f)
            3 -> m.setRotate(180f)
            4 -> m.setScale(1f, -1f)
            5 -> { m.setRotate(90f); m.postScale(-1f, 1f) }
            6 -> m.setRotate(90f)
            7 -> { m.setRotate(-90f); m.postScale(-1f, 1f) }
            8 -> m.setRotate(-90f)
        }
        return Picture(Bitmap.createBitmap(p.bitmap, 0, 0, p.width, p.height, m, true))
    }

    fun fit(p: Picture, max: Int): Picture {
        val longer = maxOf(p.width, p.height)
        if (longer <= max) return p
        val s = max.toFloat() / longer
        return Picture(Bitmap.createScaledBitmap(p.bitmap, maxOf(1, (p.width * s).toInt()), maxOf(1, (p.height * s).toInt()), true))
    }

    @Suppress("DEPRECATION")
    fun write(p: Picture, out: File, format: String, quality: Int = 90) {
        out.parentFile?.mkdirs()
        val (fmt, bmp) = when (format.lowercase()) {
            "jpg", "jpeg" -> {
                val flat = Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888)
                Canvas(flat).apply { drawColor(Color.WHITE); drawBitmap(p.bitmap, 0f, 0f, null) }
                Bitmap.CompressFormat.JPEG to flat
            }
            "png" -> Bitmap.CompressFormat.PNG to p.bitmap
            "webp" -> (if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP) to p.bitmap
            else -> error(format)
        }
        out.outputStream().use { bmp.compress(fmt, quality, it) }
    }
}
