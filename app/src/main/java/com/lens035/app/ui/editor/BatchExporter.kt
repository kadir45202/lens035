package com.lens035.app.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.lens035.app.Lens035App
import com.lens035.app.gl.GlException
import com.lens035.app.gl.PersonMaskData
import com.lens035.app.media.BitmapLoader
import com.lens035.app.media.DateStamp
import com.lens035.app.media.MediaSaver
import com.lens035.app.media.PersonSegmenter
import com.lens035.core.analysis.ImageAnalyzer
import com.lens035.core.analysis.ImageStats
import com.lens035.core.model.EditState
import com.lens035.core.model.GeometryMath
import com.lens035.core.render.CanvasLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Applies one look to many photos and saves each to the gallery, strictly one at a time so
 * memory stays flat. Every photo gets its own analysis (auto enhance) and, when the look needs
 * it, its own person detection.
 */
class BatchExporter(private val context: Context) {
    private val app = context.applicationContext as Lens035App

    data class Result(val saved: Int, val failed: Int)

    suspend fun run(uris: List<Uri>, look: EditState, options: ExportOptions, onProgress: (saved: Int, failed: Int) -> Unit): Result {
        val maxTex = app.offscreen.execute { it.maxTextureSize }
        var saved = 0
        var failed = 0
        for (uri in uris) {
            coroutineContext.ensureActive()
            if (exportOne(uri, look, options, maxTex)) saved++ else failed++
            onProgress(saved, failed)
        }
        return Result(saved, failed)
    }

    /** True when the photo was saved. Retries smaller once on memory or GPU limits. */
    private suspend fun exportOne(uri: Uri, look: EditState, options: ExportOptions, maxTex: Int): Boolean {
        var side = minOf(options.size.maxSide, maxTex)
        repeat(2) {
            try {
                val decoded = withContext(Dispatchers.IO) { BitmapLoader.decode(context, uri, side) }
                var source = decoded.bitmap
                try {
                    source = fitCanvasToGpu(source, look, maxTex)
                    val stats = withContext(Dispatchers.Default) { analyze(source) }
                    val mask = if (look.needsPersonMask) personMask(source) else null
                    val dateText = DateStamp.text(decoded.meta.dateTakenMillis)
                    val rendered = app.offscreen.execute { gl ->
                        gl.render(source, look, stats, dateText, source.width, source.height, mask)
                    }
                    try {
                        withContext(Dispatchers.IO) { MediaSaver.saveImage(context, rendered, options.format, options.quality, decoded.meta) }
                    } finally {
                        rendered.recycle()
                    }
                    return true
                } finally {
                    if (source !== decoded.bitmap) source.recycle()
                    decoded.bitmap.recycle()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: OutOfMemoryError) {
                // Retry smaller below.
            } catch (_: GlException) {
                // Retry smaller below.
            } catch (e: Exception) {
                Log.w(TAG, "Batch export failed for $uri", e)
                return false
            }
            side = (side * 0.6f).roundToInt().coerceAtLeast(1080)
        }
        return false
    }

    /** Scales [bmp] down when the look's canvas would not fit the GPU's texture limit. */
    private fun fitCanvasToGpu(bmp: Bitmap, look: EditState, maxTex: Int): Bitmap {
        val photo = GeometryMath.outputSize(bmp.width, bmp.height, look.geometry)
        val canvas = CanvasLayout.of(look, photo.width, photo.height)
        val longest = max(canvas.width, canvas.height)
        if (longest <= maxTex) return bmp
        val k = maxTex / longest.toFloat()
        return Bitmap.createScaledBitmap(bmp, max(1, (bmp.width * k).roundToInt()), max(1, (bmp.height * k).roundToInt()), true)
    }

    private fun analyze(bmp: Bitmap): ImageStats {
        val small = BitmapLoader.scaledCopy(bmp, 160)
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        if (small !== bmp) small.recycle()
        return ImageAnalyzer.analyze(px, small.width, small.height)
    }

    private suspend fun personMask(bmp: Bitmap): PersonMaskData? {
        val small = withContext(Dispatchers.Default) { BitmapLoader.scaledCopy(bmp, PhotoSession.SEGMENT_MAX_SIDE) }
        val mask = PersonSegmenter.segment(small)
        if (small !== bmp) small.recycle()
        if (mask == null) return null
        val coverage = withContext(Dispatchers.Default) { mask.coverage }
        return if (coverage < PhotoSession.MIN_PERSON_COVERAGE) null else mask
    }

    companion object {
        private const val TAG = "BatchExporter"

        /** Photos per batch; the system photo picker caps selections too. */
        const val MAX_PHOTOS = 50
    }
}
