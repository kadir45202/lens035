package com.lens035.app.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ImageFormat(val label: String, val mime: String, val extension: String) {
    JPEG("JPEG", "image/jpeg", "jpg"),
    PNG("PNG", "image/png", "png"),
}

/**
 * Saves finished photos and videos into the shared gallery (Pictures/Lens035 and
 * Movies/Lens035). On Android 8–9 the caller must hold WRITE_EXTERNAL_STORAGE.
 */
object MediaSaver {
    private const val TAG = "MediaSaver"
    const val ALBUM = "Lens035"

    fun needsLegacyPermission(): Boolean = Build.VERSION.SDK_INT < 29

    fun newName(prefix: String): String =
        "${prefix}_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    @Throws(IOException::class)
    fun saveImage(context: Context, bitmap: Bitmap, format: ImageFormat, quality: Int, meta: ImageMeta?): Uri {
        val name = newName("LENS") + "." + format.extension
        val compressFormat = if (format == ImageFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val uri = if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, format.mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + ALBUM)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val item = resolver.insert(collection, values) ?: throw IOException("Galeriye kayıt oluşturulamadı")
            try {
                resolver.openOutputStream(item)?.use { out -> writeBitmap(bitmap, compressFormat, quality, out) }
                    ?: throw IOException("Dosya açılamadı")
                if (format == ImageFormat.JPEG) writeExif(context, item, meta)
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(item, values, null, null)
            } catch (e: Exception) {
                resolver.delete(item, null, null)
                throw if (e is IOException) e else IOException(e.message, e)
            }
            item
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
            if (!dir.exists() && !dir.mkdirs()) throw IOException("Klasör oluşturulamadı")
            val file = File(dir, name)
            FileOutputStream(file).use { out -> writeBitmap(bitmap, compressFormat, quality, out) }
            if (format == ImageFormat.JPEG) {
                try {
                    val exif = ExifInterface(file.absolutePath)
                    applyExif(exif, meta)
                    exif.saveAttributes()
                } catch (e: Exception) {
                    Log.w(TAG, "EXIF write failed", e)
                }
            }
            insertLegacy(context, file, format.mime, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        }
        return uri
    }

    @Throws(IOException::class)
    fun saveVideo(context: Context, source: File): Uri {
        val name = newName("LENS") + ".mp4"
        return if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/" + ALBUM)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val item = resolver.insert(collection, values) ?: throw IOException("Galeriye kayıt oluşturulamadı")
            try {
                resolver.openOutputStream(item)?.use { out -> FileInputStream(source).use { it.copyTo(out) } }
                    ?: throw IOException("Dosya açılamadı")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(item, values, null, null)
            } catch (e: Exception) {
                resolver.delete(item, null, null)
                throw if (e is IOException) e else IOException(e.message, e)
            }
            item
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), ALBUM)
            if (!dir.exists() && !dir.mkdirs()) throw IOException("Klasör oluşturulamadı")
            val file = File(dir, name)
            source.copyTo(file, overwrite = true)
            insertLegacy(context, file, "video/mp4", MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        }
    }

    private fun writeBitmap(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int, out: OutputStream) {
        if (!bitmap.compress(format, quality.coerceIn(1, 100), out)) throw IOException("Görüntü kodlanamadı")
    }

    private fun writeExif(context: Context, uri: Uri, meta: ImageMeta?) {
        try {
            context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                applyExif(exif, meta)
                exif.saveAttributes()
            }
        } catch (e: Exception) {
            Log.w(TAG, "EXIF write failed", e)
        }
    }

    private fun applyExif(exif: ExifInterface, meta: ImageMeta?) {
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Lens035")
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        meta?.dateTakenMillis?.let {
            val text = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(it))
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, text)
            exif.setAttribute(ExifInterface.TAG_DATETIME, text)
        }
        meta?.make?.let { exif.setAttribute(ExifInterface.TAG_MAKE, it) }
        meta?.model?.let { exif.setAttribute(ExifInterface.TAG_MODEL, it) }
    }

    @Suppress("DEPRECATION")
    private fun insertLegacy(context: Context, file: File, mime: String, collection: Uri): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DATA, file.absolutePath)
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
        }
        return context.contentResolver.insert(collection, values) ?: Uri.fromFile(file)
    }
}
