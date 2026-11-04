package com.lens035.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** Metadata kept alongside a decoded image. */
data class ImageMeta(
    val width: Int,
    val height: Int,
    val dateTakenMillis: Long?,
    val make: String?,
    val model: String?,
)

class DecodedImage(val bitmap: Bitmap, val meta: ImageMeta)

/**
 * Decodes photos for editing: applies EXIF orientation, converts wide-gamut (Display P3)
 * photos to sRGB, always returns a software ARGB_8888 bitmap no larger than [maxSide].
 */
object BitmapLoader {

    fun readMeta(context: Context, uri: Uri): ImageMeta {
        var width = 0
        var height = 0
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, opts)
                width = opts.outWidth
                height = opts.outHeight
            }
        } catch (_: Exception) {
        }
        var date: Long? = null
        var make: String? = null
        var model: String? = null
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                val rotation = exif.rotationDegrees
                if (rotation == 90 || rotation == 270) {
                    val t = width; width = height; height = t
                }
                val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                if (raw != null) {
                    date = try {
                        SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(raw)?.time
                    } catch (_: Exception) {
                        null
                    }
                }
                make = exif.getAttribute(ExifInterface.TAG_MAKE)
                model = exif.getAttribute(ExifInterface.TAG_MODEL)
            }
        } catch (_: Exception) {
        }
        return ImageMeta(width, height, date, make, model)
    }

    @Throws(IOException::class)
    fun decode(context: Context, uri: Uri, maxSide: Int): DecodedImage {
        val meta = readMeta(context, uri)
        val bitmap = if (Build.VERSION.SDK_INT >= 28) decodeWithImageDecoder(context, uri, maxSide) else decodeLegacy(context, uri, maxSide)
        val sized = if (meta.width > 0 && meta.height > 0) meta else meta.copy(width = bitmap.width, height = bitmap.height)
        return DecodedImage(bitmap, sized)
    }

    private fun decodeWithImageDecoder(context: Context, uri: Uri, maxSide: Int): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            val w = info.size.width
            val h = info.size.height
            val longest = max(w, h)
            if (longest > maxSide) {
                val s = maxSide.toFloat() / longest
                decoder.setTargetSize(max(1, (w * s).roundToInt()), max(1, (h * s).roundToInt()))
            }
        }
        return ensureArgb(decoded)
    }

    private fun decodeLegacy(context: Context, uri: Uri, maxSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IOException("Cannot open $uri")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Unsupported image")
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }
        var bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IOException("Cannot decode $uri")
        val longest = max(bitmap.width, bitmap.height)
        if (longest > maxSide) {
            val s = maxSide.toFloat() / longest
            val scaled = Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width * s).roundToInt()), max(1, (bitmap.height * s).roundToInt()), true)
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
        }
        val rotation = try {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        } catch (_: Exception) {
            0
        }
        val flip = try {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).isFlipped } ?: false
        } catch (_: Exception) {
            false
        }
        if (rotation != 0 || flip) {
            val m = Matrix()
            if (flip) m.postScale(-1f, 1f)
            m.postRotate(rotation.toFloat())
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            if (rotated !== bitmap) bitmap.recycle()
            bitmap = rotated
        }
        return ensureArgb(bitmap)
    }

    private fun ensureArgb(bitmap: Bitmap): Bitmap {
        if (bitmap.config == Bitmap.Config.ARGB_8888) return bitmap
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        bitmap.recycle()
        return copy
    }

    /** Center-cropped, small copy for thumbnails (keeps the source untouched). */
    fun thumbnail(source: Bitmap, width: Int, height: Int): Bitmap {
        val r = thumbnailCrop(source.width, source.height, width, height)
        val cropped = Bitmap.createBitmap(source, r.left, r.top, r.width(), r.height())
        val scaled = Bitmap.createScaledBitmap(cropped, width, height, true)
        if (cropped !== source && cropped !== scaled) cropped.recycle()
        return scaled
    }

    /** The centred region of a [srcW] x [srcH] image that [thumbnail] keeps for a [width] x [height] thumbnail. */
    fun thumbnailCrop(srcW: Int, srcH: Int, width: Int, height: Int): Rect {
        val targetAspect = width.toFloat() / height
        val srcAspect = srcW.toFloat() / srcH
        val (cw, ch) = if (srcAspect > targetAspect) {
            (srcH * targetAspect).roundToInt() to srcH
        } else {
            srcW to (srcW / targetAspect).roundToInt()
        }
        val w = cw.coerceIn(1, srcW)
        val h = ch.coerceIn(1, srcH)
        val x = ((srcW - w) / 2).coerceAtLeast(0)
        val y = ((srcH - h) / 2).coerceAtLeast(0)
        return Rect(x, y, x + w, y + h)
    }

    /** Downscaled copy whose longest side is [maxSide] (or the bitmap itself if smaller). */
    fun scaledCopy(source: Bitmap, maxSide: Int): Bitmap {
        val longest = max(source.width, source.height)
        if (longest <= maxSide) return source
        val s = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(source, max(1, (source.width * s).roundToInt()), max(1, (source.height * s).roundToInt()), true)
    }
}
