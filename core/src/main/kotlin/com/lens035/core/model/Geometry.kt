package com.lens035.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Crop rectangle normalised to the oriented (rotated + flipped) frame, 0..1, y down. */
@Serializable
data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val isFull: Boolean get() = left <= 1e-4f && top <= 1e-4f && right >= 1f - 1e-4f && bottom >= 1f - 1e-4f

    companion object {
        val FULL = CropRect()

        fun centered(cx: Float, cy: Float, w: Float, h: Float) =
            CropRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }
}

@Serializable
enum class AspectRatio(val label: String, val ratio: Float) {
    @SerialName("free") FREE("Serbest", 0f),
    @SerialName("original") ORIGINAL("Orijinal", -1f),
    @SerialName("1:1") SQUARE("1:1", 1f),
    @SerialName("4:5") PORTRAIT_4_5("4:5", 4f / 5f),
    @SerialName("3:4") PORTRAIT_3_4("3:4", 3f / 4f),
    @SerialName("2:3") PORTRAIT_2_3("2:3", 2f / 3f),
    @SerialName("9:16") STORY("9:16", 9f / 16f),
    @SerialName("4:3") LANDSCAPE_4_3("4:3", 4f / 3f),
    @SerialName("3:2") LANDSCAPE_3_2("3:2", 3f / 2f),
    @SerialName("16:9") WIDE_16_9("16:9", 16f / 9f),
    @SerialName("1.91:1") WIDE_191("1.91:1", 1.91f),
    @SerialName("2.39:1") CINEMA("2.39:1", 2.39f),
}

/**
 * Orientation and framing. The order of operations from the source image is:
 * rotate by quarter turns -> flip -> straighten around the centre -> crop.
 */
@Serializable
data class Geometry(
    /** Clockwise quarter turns, 0..3. */
    val rotation: Int = 0,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    /** Fine rotation in degrees, -45..45, positive is clockwise. */
    val straighten: Float = 0f,
    val crop: CropRect = CropRect.FULL,
    val aspect: AspectRatio = AspectRatio.FREE,
) {
    val isDefault: Boolean
        get() = rotation % 4 == 0 && !flipH && !flipV && straighten == 0f && crop.isFull

    val quarterTurns: Int get() = ((rotation % 4) + 4) % 4
}

/** Integer pixel size. */
data class PixelSize(val width: Int, val height: Int) {
    val aspect: Float get() = width.toFloat() / height.toFloat()
}

/**
 * Geometry maths shared by the renderer (UV transform), the crop UI (hit testing, limits)
 * and the exporter (output size). All functions are pure.
 */
object GeometryMath {

    /** Size of the frame after quarter turns. */
    fun orientedSize(sourceWidth: Int, sourceHeight: Int, g: Geometry): PixelSize =
        if (g.quarterTurns % 2 == 1) PixelSize(sourceHeight, sourceWidth) else PixelSize(sourceWidth, sourceHeight)

    /** Output pixel size of the cropped frame for a source of the given size. */
    fun outputSize(sourceWidth: Int, sourceHeight: Int, g: Geometry, evenDimensions: Boolean = false): PixelSize {
        val o = orientedSize(sourceWidth, sourceHeight, g)
        var w = max(1, (g.crop.width * o.width).roundToInt())
        var h = max(1, (g.crop.height * o.height).roundToInt())
        if (evenDimensions) {
            w = max(2, w - (w and 1))
            h = max(2, h - (h and 1))
        }
        return PixelSize(w, h)
    }

    /**
     * Affine 3x3 matrix (column-major, as OpenGL expects for mat3 uniforms) that maps an output
     * texture coordinate (u, v) in 0..1 over the crop to the source texture coordinate.
     * Both spaces use v = 0 at the top of the image.
     */
    fun outputToSourceMatrix(sourceWidth: Int, sourceHeight: Int, g: Geometry): FloatArray {
        val m = outputToSourceRowMajor(sourceWidth, sourceHeight, g)
        return floatArrayOf(
            m[0], m[3], m[6],
            m[1], m[4], m[7],
            m[2], m[5], m[8],
        )
    }

    /** Same transform as [outputToSourceMatrix] but row-major, for CPU use. */
    fun outputToSourceRowMajor(sourceWidth: Int, sourceHeight: Int, g: Geometry): FloatArray {
        val w = sourceWidth.toFloat()
        val h = sourceHeight.toFloat()
        val o = orientedSize(sourceWidth, sourceHeight, g)
        val ow = o.width.toFloat()
        val oh = o.height.toFloat()
        val c = g.crop

        // T1: output uv -> oriented pixels
        var m = floatArrayOf(
            c.width * ow, 0f, c.left * ow,
            0f, c.height * oh, c.top * oh,
            0f, 0f, 1f,
        )
        // T2: undo straighten (rotate by -theta around the oriented centre)
        val theta = Math.toRadians(-g.straighten.toDouble())
        val cs = cos(theta).toFloat()
        val sn = sin(theta).toFloat()
        val cx = ow / 2f
        val cy = oh / 2f
        val rot = floatArrayOf(
            cs, -sn, cx - cs * cx + sn * cy,
            sn, cs, cy - sn * cx - cs * cy,
            0f, 0f, 1f,
        )
        m = mul(rot, m)
        // T3: undo flips (in oriented pixel space)
        val flip = floatArrayOf(
            if (g.flipH) -1f else 1f, 0f, if (g.flipH) ow else 0f,
            0f, if (g.flipV) -1f else 1f, if (g.flipV) oh else 0f,
            0f, 0f, 1f,
        )
        m = mul(flip, m)
        // T4: undo quarter turns: oriented pixels -> source pixels
        val unrot = when (g.quarterTurns) {
            1 -> floatArrayOf( // x = y', y = H - x'
                0f, 1f, 0f,
                -1f, 0f, h,
                0f, 0f, 1f,
            )
            2 -> floatArrayOf( // x = W - x', y = H - y'
                -1f, 0f, w,
                0f, -1f, h,
                0f, 0f, 1f,
            )
            3 -> floatArrayOf( // x = W - y', y = x'
                0f, -1f, w,
                1f, 0f, 0f,
                0f, 0f, 1f,
            )
            else -> identity()
        }
        m = mul(unrot, m)
        // T5: source pixels -> source uv
        val norm = floatArrayOf(
            1f / w, 0f, 0f,
            0f, 1f / h, 0f,
            0f, 0f, 1f,
        )
        return mul(norm, m)
    }

    /** Inverse of [outputToSourceRowMajor]: source uv -> output uv (row-major). */
    fun sourceToOutputRowMajor(sourceWidth: Int, sourceHeight: Int, g: Geometry): FloatArray =
        invert(outputToSourceRowMajor(sourceWidth, sourceHeight, g))

    fun apply(m: FloatArray, x: Float, y: Float, out: FloatArray) {
        out[0] = m[0] * x + m[1] * y + m[2]
        out[1] = m[3] * x + m[4] * y + m[5]
    }

    /**
     * True when the crop rectangle lies completely inside the straightened image, i.e. no
     * empty corners would be visible. Tolerance is in oriented pixels.
     */
    fun cropFits(crop: CropRect, straightenDeg: Float, orientedW: Float, orientedH: Float, tolerancePx: Float = 0.5f): Boolean {
        if (crop.left < -1e-4f || crop.top < -1e-4f || crop.right > 1f + 1e-4f || crop.bottom > 1f + 1e-4f) return false
        if (abs(straightenDeg) < 1e-4f) return true
        val theta = Math.toRadians(-straightenDeg.toDouble())
        val cs = cos(theta).toFloat()
        val sn = sin(theta).toFloat()
        val cx = orientedW / 2f
        val cy = orientedH / 2f
        val xs = floatArrayOf(crop.left, crop.right, crop.right, crop.left)
        val ys = floatArrayOf(crop.top, crop.top, crop.bottom, crop.bottom)
        for (i in 0 until 4) {
            val px = xs[i] * orientedW - cx
            val py = ys[i] * orientedH - cy
            val qx = cs * px - sn * py + cx
            val qy = sn * px + cs * py + cy
            if (qx < -tolerancePx || qy < -tolerancePx || qx > orientedW + tolerancePx || qy > orientedH + tolerancePx) return false
        }
        return true
    }

    /**
     * Shrinks (and if necessary recentres) [crop] around its centre, keeping its pixel aspect,
     * until it fits inside the straightened image. Used whenever the straighten angle or the
     * crop changes so that the result never shows blank corners.
     */
    fun fitCrop(crop: CropRect, straightenDeg: Float, orientedW: Float, orientedH: Float): CropRect {
        var c = clampInside(crop)
        if (cropFits(c, straightenDeg, orientedW, orientedH)) return c
        // Move the centre towards the image centre while shrinking.
        var lo = 0f
        var hi = 1f
        val baseW = c.width
        val baseH = c.height
        repeat(28) {
            val mid = (lo + hi) / 2f
            val candidate = scaledAround(c.centerX, c.centerY, baseW * mid, baseH * mid)
            if (cropFits(candidate, straightenDeg, orientedW, orientedH)) lo = mid else hi = mid
        }
        if (lo > 0.02f) return scaledAround(c.centerX, c.centerY, baseW * lo, baseH * lo)
        // The centre itself is outside the rotated image: fall back to the image centre.
        c = CropRect.centered(0.5f, 0.5f, baseW, baseH)
        lo = 0f; hi = 1f
        repeat(28) {
            val mid = (lo + hi) / 2f
            val candidate = scaledAround(0.5f, 0.5f, baseW * mid, baseH * mid)
            if (cropFits(candidate, straightenDeg, orientedW, orientedH)) lo = mid else hi = mid
        }
        return scaledAround(0.5f, 0.5f, baseW * max(lo, 0.02f), baseH * max(lo, 0.02f))
    }

    /** Largest crop of the given pixel aspect ratio centred in the oriented frame. */
    fun maxCropForAspect(aspect: Float, orientedW: Float, orientedH: Float, straightenDeg: Float): CropRect {
        if (aspect <= 0f) return fitCrop(CropRect.FULL, straightenDeg, orientedW, orientedH)
        val frameAspect = orientedW / orientedH
        val (nw, nh) = if (aspect > frameAspect) {
            1f to (frameAspect / aspect)
        } else {
            (aspect / frameAspect) to 1f
        }
        return fitCrop(CropRect.centered(0.5f, 0.5f, nw, nh), straightenDeg, orientedW, orientedH)
    }

    /** Resolves [AspectRatio.ORIGINAL] / FREE to a concrete pixel aspect (0 = unconstrained). */
    fun aspectValue(aspect: AspectRatio, orientedW: Float, orientedH: Float): Float = when (aspect) {
        AspectRatio.FREE -> 0f
        AspectRatio.ORIGINAL -> orientedW / orientedH
        else -> aspect.ratio
    }

    private fun scaledAround(cx: Float, cy: Float, w: Float, h: Float): CropRect {
        val hw = min(w, 1f) / 2f
        val hh = min(h, 1f) / 2f
        val x = cx.coerceIn(hw, 1f - hw)
        val y = cy.coerceIn(hh, 1f - hh)
        return CropRect(x - hw, y - hh, x + hw, y + hh)
    }

    private fun clampInside(c: CropRect): CropRect {
        val w = c.width.coerceIn(0.02f, 1f)
        val h = c.height.coerceIn(0.02f, 1f)
        return scaledAround(c.centerX, c.centerY, w, h)
    }

    fun identity() = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    /** Row-major 3x3 multiply a * b. */
    fun mul(a: FloatArray, b: FloatArray): FloatArray {
        val r = FloatArray(9)
        for (i in 0 until 3) for (j in 0 until 3) {
            r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j]
        }
        return r
    }

    fun invert(m: FloatArray): FloatArray {
        val det = m[0] * (m[4] * m[8] - m[5] * m[7]) -
            m[1] * (m[3] * m[8] - m[5] * m[6]) +
            m[2] * (m[3] * m[7] - m[4] * m[6])
        if (abs(det) < 1e-12f) return identity()
        val inv = 1f / det
        return floatArrayOf(
            (m[4] * m[8] - m[5] * m[7]) * inv,
            (m[2] * m[7] - m[1] * m[8]) * inv,
            (m[1] * m[5] - m[2] * m[4]) * inv,
            (m[5] * m[6] - m[3] * m[8]) * inv,
            (m[0] * m[8] - m[2] * m[6]) * inv,
            (m[2] * m[3] - m[0] * m[5]) * inv,
            (m[3] * m[7] - m[4] * m[6]) * inv,
            (m[1] * m[6] - m[0] * m[7]) * inv,
            (m[0] * m[4] - m[1] * m[3]) * inv,
        )
    }
}
