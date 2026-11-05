package com.lens035.app.ui.camera

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.lens035.core.pose.Joint
import com.lens035.core.pose.JointPoint
import com.lens035.core.pose.PoseSkeleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** ML Kit landmark id for each of our joints. */
val JOINT_LANDMARKS: Map<Joint, Int> = mapOf(
    Joint.NOSE to PoseLandmark.NOSE,
    Joint.LEFT_SHOULDER to PoseLandmark.LEFT_SHOULDER,
    Joint.RIGHT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
    Joint.LEFT_ELBOW to PoseLandmark.LEFT_ELBOW,
    Joint.RIGHT_ELBOW to PoseLandmark.RIGHT_ELBOW,
    Joint.LEFT_WRIST to PoseLandmark.LEFT_WRIST,
    Joint.RIGHT_WRIST to PoseLandmark.RIGHT_WRIST,
    Joint.LEFT_HIP to PoseLandmark.LEFT_HIP,
    Joint.RIGHT_HIP to PoseLandmark.RIGHT_HIP,
    Joint.LEFT_KNEE to PoseLandmark.LEFT_KNEE,
    Joint.RIGHT_KNEE to PoseLandmark.RIGHT_KNEE,
    Joint.LEFT_ANKLE to PoseLandmark.LEFT_ANKLE,
    Joint.RIGHT_ANKLE to PoseLandmark.RIGHT_ANKLE,
)

/** Converts an ML Kit pose (pixel coordinates in a w x h space) to a normalised skeleton. */
fun Pose.toSkeleton(width: Float, height: Float): PoseSkeleton? {
    if (allPoseLandmarks.isEmpty() || width <= 0f || height <= 0f) return null
    val joints = HashMap<Joint, JointPoint>()
    for ((joint, id) in JOINT_LANDMARKS) {
        val lm = getPoseLandmark(id) ?: continue
        joints[joint] = JointPoint(lm.position.x / width, lm.position.y / height, lm.inFrameLikelihood)
    }
    return if (joints.size >= 6) PoseSkeleton(joints) else null
}

object ReferenceProcessor {

    /** Detects the main person's pose in a still photo, or null when nobody is found. */
    suspend fun detectPose(bitmap: Bitmap): PoseSkeleton? = suspendCancellableCoroutine { cont ->
        val detector = PoseDetection.getClient(
            PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE).build(),
        )
        detector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { pose ->
                val skeleton = pose.toSkeleton(bitmap.width.toFloat(), bitmap.height.toFloat())
                detector.close()
                if (cont.isActive) cont.resume(skeleton)
            }
            .addOnFailureListener {
                detector.close()
                if (cont.isActive) cont.resume(null)
            }
        cont.invokeOnCancellation { detector.close() }
    }

    /**
     * Line drawing of the photo (Sobel edges on luminance) as white strokes on transparency,
     * used as the "outline" ghost. Runs on the CPU at up to [maxSide] px.
     */
    fun edgeMap(source: Bitmap, maxSide: Int = 720): Bitmap {
        val scale = min(1f, maxSide / max(source.width, source.height).toFloat())
        val w = max(8, (source.width * scale).roundToInt())
        val h = max(8, (source.height * scale).roundToInt())
        val small = Bitmap.createScaledBitmap(source, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== source) small.recycle()
        val lum = FloatArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            lum[i] = (((c shr 16) and 0xFF) * 0.299f + ((c shr 8) and 0xFF) * 0.587f + (c and 0xFF) * 0.114f) / 255f
        }
        // Light blur first so that texture noise does not become edges.
        val blurred = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0f
            var n = 0
            for (dy in -1..1) for (dx in -1..1) {
                val xx = (x + dx).coerceIn(0, w - 1)
                val yy = (y + dy).coerceIn(0, h - 1)
                s += lum[yy * w + xx]
                n++
            }
            blurred[y * w + x] = s / n
        }
        val mag = FloatArray(w * h)
        var maxMag = 1e-4f
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            fun l(xx: Int, yy: Int) = blurred[yy * w + xx]
            val gx = -l(x - 1, y - 1) - 2 * l(x - 1, y) - l(x - 1, y + 1) + l(x + 1, y - 1) + 2 * l(x + 1, y) + l(x + 1, y + 1)
            val gy = -l(x - 1, y - 1) - 2 * l(x, y - 1) - l(x + 1, y - 1) + l(x - 1, y + 1) + 2 * l(x, y + 1) + l(x + 1, y + 1)
            val m = sqrt(gx * gx + gy * gy)
            mag[y * w + x] = m
            if (m > maxMag) maxMag = m
        }
        // Normalise by a high percentile instead of the max so a few strong edges don't wash out the rest.
        val sorted = mag.copyOf().also { it.sort() }
        val ref = max(sorted[(sorted.size * 0.97f).toInt().coerceIn(0, sorted.size - 1)], maxMag * 0.15f)
        val out = IntArray(w * h)
        for (i in mag.indices) {
            val v = (mag[i] / ref)
            val a = ((v - 0.25f) / 0.6f).coerceIn(0f, 1f)
            val alpha = (a * 255f).roundToInt()
            out[i] = (alpha shl 24) or 0x00FFFFFF
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Rough check used to tell the user when a reference photo has no detectable person. */
    fun isPlausible(s: PoseSkeleton): Boolean {
        val b = s.bounds()
        return abs(b[2] - b[0]) > 0.05f && abs(b[3] - b[1]) > 0.1f
    }
}
