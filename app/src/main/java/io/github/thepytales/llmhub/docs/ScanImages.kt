package io.github.thepytales.llmhub.docs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Pictures of the Dokumenten-Scanner (AP-62) on Android: photos of the camera read into [ScanRaster] (already turned
 * as the camera held it, EXIF), results written as JPEG/PNG. The Windows version of this file uses ImageIO.
 */
object ScanImages {
    /** Reads [file] with its longer side at most about [maxSide] (decoded smaller, so a 50-MP photo fits in memory). */
    fun read(file: File, maxSide: Int): ScanRaster {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "„${file.name}“ ist kein lesbares Bild." }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
            ?: throw IllegalArgumentException("„${file.name}“ ist kein lesbares Bild.")
        val raster = try {
            ScanRaster(bmp.width, bmp.height).also { bmp.getPixels(it.pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
        } finally {
            bmp.recycle()
        }
        val quarter = runCatching {
            when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 1
                ExifInterface.ORIENTATION_ROTATE_180 -> 2
                ExifInterface.ORIENTATION_ROTATE_270 -> 3
                else -> 0
            }
        }.getOrDefault(0)
        return raster.rotated(quarter).scaledDown(maxSide)
    }

    private fun bitmap(r: ScanRaster): Bitmap = Bitmap.createBitmap(r.pixels, r.width, r.height, Bitmap.Config.ARGB_8888)

    fun jpeg(r: ScanRaster, quality: Int = 85): ByteArray {
        val bmp = bitmap(r)
        return try {
            ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
        } finally {
            bmp.recycle()
        }
    }

    fun png(r: ScanRaster): ByteArray {
        val bmp = bitmap(r)
        return try {
            ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        } finally {
            bmp.recycle()
        }
    }
}
