package com.lens035.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

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
 * rotate by quarter turns -> flip -> perspective -> straighten around the centre -> crop.
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
    /** Vertical perspective, -100..100. Negative widens the top (fixes buildings leaning back). */
    val vertical: Float = 0f,
    /** Horizontal perspective, -100..100. Positive enlarges the right side. */
    val horizontal: Float = 0f,
) {
    val isDefault: Boolean
        get() = rotation % 4 == 0 && !flipH && !flipV && !hasCorrection && crop.isFull

    /** True when straighten or perspective warps the frame (and the crop must avoid empty corners). */
    val hasCorrection: Boolean
        get() = straighten != 0f || vertical != 0f || horizontal != 0f

    val hasPerspective: Boolean get() = vertical != 0f || horizontal != 0f

    val quarterTurns: Int get() = ((rotation % 4) + 4) % 4

    /** The same picture after turning the frame a quarter turn (the crop is not touched). */
    fun rotatedCorrection(clockwise: Boolean): Geometry =
        if (clockwise) copy(vertical = horizontal, horizontal = -vertical) else copy(vertical = -horizontal, horizontal = vertical)

    /** The same picture mirrored left-right (the crop is not touched). */
    fun mirroredCorrectionH(): Geometry = copy(straighten = -straighten, horizontal = -horizontal)

    /** The same picture mirrored top-bottom (the crop is not touched). */
    fun mirroredCorrectionV(): Geometry = copy(straighten = -straighten, vertical = -vertical)
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
     * Projective 3x3 matrix (column-major, as OpenGL expects for mat3 uniforms) that maps an
     * output texture coordinate (u, v, 1) in 0..1 over the crop to the source texture coordinate
     * (divide by z; z is 1 without perspective). Both spaces use v = 0 at the top of the image.
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
        // T2: undo straighten and perspective (corrected frame -> oriented pixels)
        m = mul(correctionToOrientedRowMajor(g, ow, oh), m)
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

    /** Applies a row-major 3x3 transform to a point, with the perspective divide. */
    fun apply(m: FloatArray, x: Float, y: Float, out: FloatArray) {
        val w = m[6] * x + m[7] * y + m[8]
        val iw = if (abs(w) < 1e-9f) 1e9f else 1f / w
        out[0] = (m[0] * x + m[1] * y + m[2]) * iw
        out[1] = (m[3] * x + m[4] * y + m[5]) * iw
    }

    /**
     * Pixels of the corrected frame (perspective, then straighten, before the crop) back to
     * oriented pixels, row-major. Undoes straighten first, then perspective.
     */
    fun correctionToOrientedRowMajor(g: Geometry, ow: Float, oh: Float): FloatArray {
        val theta = Math.toRadians(-g.straighten.toDouble())
        val cs = cos(theta).toFloat()
        val sn = sin(theta).toFloat()
        val cx = ow / 2f
        val cy = oh / 2f
        val unStraighten = floatArrayOf(
            cs, -sn, cx - cs * cx + sn * cy,
            sn, cs, cy - sn * cx - cs * cy,
            0f, 0f, 1f,
        )
        if (!g.hasPerspective) return unStraighten
        return mul(invert(perspectiveRowMajor(g.vertical, g.horizontal, ow, oh)), unStraighten)
    }

    /**
     * Perspective as if the camera had been turned: H = K R K^-1 for a typical phone lens
     * (focal length 0.8 x the long side), recentred so the middle of the frame stays put.
     * Maps oriented pixels to corrected pixels, row-major.
     */
    fun perspectiveRowMajor(vertical: Float, horizontal: Float, ow: Float, oh: Float): FloatArray {
        val f = FOCAL_FACTOR * max(ow, oh)
        val cx = ow / 2f
        val cy = oh / 2f
        val k = floatArrayOf(f, 0f, cx, 0f, f, cy, 0f, 0f, 1f)
        val kInv = floatArrayOf(1f / f, 0f, -cx / f, 0f, 1f / f, -cy / f, 0f, 0f, 1f)
        // One tilt about an in-plane axis (Rodrigues), so the result does not depend on applying
        // vertical before horizontal, and turning or mirroring the frame maps the sliders exactly.
        val ax = Math.toRadians((-vertical.coerceIn(-100f, 100f) / 100f * MAX_TILT_DEG).toDouble())
        val ay = Math.toRadians((horizontal.coerceIn(-100f, 100f) / 100f * MAX_TILT_DEG).toDouble())
        val angle = sqrt(ax * ax + ay * ay)
        if (angle < 1e-9) return identity()
        val kx = (ax / angle).toFloat()
        val ky = (ay / angle).toFloat()
        val s = sin(angle).toFloat()
        val c1 = (1.0 - cos(angle)).toFloat()
        val r = floatArrayOf(
            1f - c1 * ky * ky, c1 * kx * ky, s * ky,
            c1 * kx * ky, 1f - c1 * kx * kx, -s * kx,
            -s * ky, s * kx, 1f - c1,
        )
        val h = mul(k, mul(r, kInv))
        val c = FloatArray(2)
        apply(h, cx, cy, c)
        val recentre = floatArrayOf(1f, 0f, cx - c[0], 0f, 1f, cy - c[1], 0f, 0f, 1f)
        return mul(recentre, h)
    }

    /**
     * True when the crop rectangle lies completely inside the corrected (straightened and
     * perspective-corrected) image, i.e. no empty corners would be visible. Tolerance is in
     * oriented pixels.
     */
    fun cropFits(crop: CropRect, g: Geometry, orientedW: Float, orientedH: Float, tolerancePx: Float = 0.5f): Boolean {
        if (!insideUnit(crop)) return false
        if (!g.hasCorrection) return true
        return cropFitsWith(crop, correctionToOrientedRowMajor(g, orientedW, orientedH), orientedW, orientedH, tolerancePx, FloatArray(2))
    }

    /** [cropFits] for straighten alone. */
    fun cropFits(crop: CropRect, straightenDeg: Float, orientedW: Float, orientedH: Float, tolerancePx: Float = 0.5f): Boolean =
        cropFits(crop, Geometry(straighten = straightenDeg), orientedW, orientedH, tolerancePx)

    private fun insideUnit(crop: CropRect) =
        crop.left >= -1e-4f && crop.top >= -1e-4f && crop.right <= 1f + 1e-4f && crop.bottom <= 1f + 1e-4f

    /**
     * Corner test against a precomputed corrected->oriented transform. The image of the frame is
     * convex and so is the crop, so the four corners decide.
     */
    private fun cropFitsWith(crop: CropRect, m: FloatArray, ow: Float, oh: Float, tol: Float, tmp: FloatArray): Boolean {
        if (!insideUnit(crop)) return false
        for (i in 0 until 4) {
            val x = if (i == 1 || i == 2) crop.right else crop.left
            val y = if (i >= 2) crop.bottom else crop.top
            apply(m, x * ow, y * oh, tmp)
            if (tmp[0] < -tol || tmp[1] < -tol || tmp[0] > ow + tol || tmp[1] > oh + tol) return false
        }
        return true
    }

    /**
     * Shrinks (and if necessary recentres) [crop] around its centre, keeping its pixel aspect,
     * until it fits inside the corrected image. Used whenever straighten, perspective or the
     * crop changes so that the result never shows blank corners.
     */
    fun fitCrop(crop: CropRect, g: Geometry, orientedW: Float, orientedH: Float): CropRect {
        var c = clampInside(crop)
        if (!g.hasCorrection) return c
        val m = correctionToOrientedRowMajor(g, orientedW, orientedH)
        val tmp = FloatArray(2)
        fun fits(r: CropRect) = cropFitsWith(r, m, orientedW, orientedH, 0.5f, tmp)
        if (fits(c)) return c
        // Move the centre towards the image centre while shrinking.
        var lo = 0f
        var hi = 1f
        val baseW = c.width
        val baseH = c.height
        repeat(28) {
            val mid = (lo + hi) / 2f
            if (fits(scaledAround(c.centerX, c.centerY, baseW * mid, baseH * mid))) lo = mid else hi = mid
        }
        if (lo > 0.02f) return scaledAround(c.centerX, c.centerY, baseW * lo, baseH * lo)
        // The centre itself is outside the corrected image: fall back to the image centre.
        c = CropRect.centered(0.5f, 0.5f, baseW, baseH)
        lo = 0f; hi = 1f
        repeat(28) {
            val mid = (lo + hi) / 2f
            if (fits(scaledAround(0.5f, 0.5f, baseW * mid, baseH * mid))) lo = mid else hi = mid
        }
        return scaledAround(0.5f, 0.5f, baseW * max(lo, 0.02f), baseH * max(lo, 0.02f))
    }

    /** [fitCrop] for straighten alone. */
    fun fitCrop(crop: CropRect, straightenDeg: Float, orientedW: Float, orientedH: Float): CropRect =
        fitCrop(crop, Geometry(straighten = straightenDeg), orientedW, orientedH)

    /**
     * Largest crop of the given pixel aspect ratio (0 = the frame's own) that fits the corrected
     * image. It sits in the middle when it can; perspective can make the usable area lopsided, and
     * then the crop moves as little as possible off-centre to stay large.
     */
    fun maxCropForAspect(aspect: Float, orientedW: Float, orientedH: Float, g: Geometry): CropRect {
        val frameAspect = orientedW / orientedH
        val a = if (aspect > 0f) aspect else frameAspect
        val (nw, nh) = if (a > frameAspect) 1f to (frameAspect / a) else (a / frameAspect) to 1f
        if (!g.hasCorrection) return CropRect.centered(0.5f, 0.5f, nw, nh)
        val m = correctionToOrientedRowMajor(g, orientedW, orientedH)
        val tmp = FloatArray(2)
        var lo = 0f
        var hi = 1f
        var best = CropRect.centered(0.5f, 0.5f, nw * 0.02f, nh * 0.02f)
        repeat(26) {
            val mid = (lo + hi) / 2f
            val placed = placeNearCentre(nw * mid, nh * mid, m, orientedW, orientedH, tmp)
            if (placed != null) {
                lo = mid
                best = placed
            } else {
                hi = mid
            }
        }
        return best
    }

    /** [maxCropForAspect] for straighten alone. */
    fun maxCropForAspect(aspect: Float, orientedW: Float, orientedH: Float, straightenDeg: Float): CropRect =
        maxCropForAspect(aspect, orientedW, orientedH, Geometry(straighten = straightenDeg))

    /** A w x h crop that fits, as close to the frame centre as a coarse search finds, or null. */
    private fun placeNearCentre(w: Float, h: Float, m: FloatArray, ow: Float, oh: Float, tmp: FloatArray): CropRect? {
        val centred = CropRect.centered(0.5f, 0.5f, w, h)
        if (cropFitsWith(centred, m, ow, oh, 0.5f, tmp)) return centred
        val freeX = max(0f, (1f - w) / 2f)
        val freeY = max(0f, (1f - h) / 2f)
        if (freeX < 1e-4f && freeY < 1e-4f) return null
        var best: CropRect? = null
        var bestD = Float.MAX_VALUE
        val n = 8
        for (i in -n..n) for (j in -n..n) {
            val dx = freeX * i / n
            val dy = freeY * j / n
            val d = dx * dx + dy * dy
            if (d >= bestD) continue
            val r = CropRect.centered(0.5f + dx, 0.5f + dy, w, h)
            if (cropFitsWith(r, m, ow, oh, 0.5f, tmp)) {
                best = r
                bestD = d
            }
        }
        return best
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

    /** Tilt of the virtual camera at ±100 on the perspective sliders. */
    private const val MAX_TILT_DEG = 24f

    /** Focal length relative to the long side (about a 26 mm equivalent phone lens). */
    private const val FOCAL_FACTOR = 0.8f

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
