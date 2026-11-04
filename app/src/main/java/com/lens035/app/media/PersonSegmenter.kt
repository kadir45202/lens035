package com.lens035.app.media

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.lens035.app.gl.PersonMaskData
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * On-device person segmentation (ML Kit selfie segmentation, bundled model). Pass a small copy of
 * the photo (about 512 px): the returned confidence mask has exactly that size and covers the whole
 * frame, and the GPU upsamples it smoothly. Nothing leaves the device.
 */
object PersonSegmenter {
    private const val TAG = "PersonSegmenter"

    suspend fun segment(bitmap: Bitmap): PersonMaskData? = suspendCancellableCoroutine { cont ->
        val segmenter = Segmentation.getClient(
            SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .build(),
        )
        cont.invokeOnCancellation { segmenter.close() }
        segmenter.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { mask ->
                val result = try {
                    val w = mask.width
                    val h = mask.height
                    val buffer = mask.buffer
                    buffer.rewind()
                    val bytes = ByteArray(w * h)
                    for (i in 0 until w * h) {
                        val c = buffer.float.coerceIn(0f, 1f)
                        bytes[i] = (c * 255f + 0.5f).toInt().toByte()
                    }
                    PersonMaskData(w, h, bytes, "person:${System.nanoTime()}")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not read mask", e)
                    null
                }
                segmenter.close()
                if (cont.isActive) cont.resume(result)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Segmentation failed", e)
                segmenter.close()
                if (cont.isActive) cont.resume(null)
            }
    }
}
