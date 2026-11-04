package com.lens035.core.color

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.sqrt

/** One control point of a tone curve, both coordinates in 0..1. */
@Serializable
data class CurvePoint(val x: Float, val y: Float)

/**
 * A smooth, non-overshooting tone curve through user control points.
 *
 * Uses monotone cubic Hermite interpolation (Fritsch–Carlson) when the points are monotone,
 * which is what photo editors do so that dragging one point never makes the curve ring.
 * Non-monotone point sets (e.g. a curve that inverts) fall back to a clamped Catmull-Rom
 * style tangent so the curve still passes through every point.
 */
class ToneCurve(points: List<CurvePoint>) {
    private val xs: FloatArray
    private val ys: FloatArray
    private val ms: FloatArray

    init {
        val sorted = sanitize(points)
        xs = FloatArray(sorted.size) { sorted[it].x }
        ys = FloatArray(sorted.size) { sorted[it].y }
        ms = tangents(xs, ys)
    }

    fun evaluate(x: Float): Float {
        val n = xs.size
        if (x <= xs[0]) return ColorMath.clamp01(ys[0])
        if (x >= xs[n - 1]) return ColorMath.clamp01(ys[n - 1])
        var i = 0
        while (i < n - 2 && x > xs[i + 1]) i++
        val h = xs[i + 1] - xs[i]
        if (h <= 1e-6f) return ColorMath.clamp01(ys[i])
        val t = (x - xs[i]) / h
        val t2 = t * t
        val t3 = t2 * t
        val h00 = 2f * t3 - 3f * t2 + 1f
        val h10 = t3 - 2f * t2 + t
        val h01 = -2f * t3 + 3f * t2
        val h11 = t3 - t2
        val y = h00 * ys[i] + h10 * h * ms[i] + h01 * ys[i + 1] + h11 * h * ms[i + 1]
        return ColorMath.clamp01(y)
    }

    /** Samples the curve into a lookup table of [size] entries covering 0..1 inclusive. */
    fun toTable(size: Int = 256): FloatArray =
        FloatArray(size) { evaluate(it / (size - 1).toFloat()) }

    companion object {
        val IDENTITY_POINTS: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))

        fun isIdentity(points: List<CurvePoint>): Boolean {
            if (points.size < 2) return true
            return points.all { abs(it.x - it.y) < 1e-4f }
        }

        /**
         * Sorts by x, clamps to the unit square, merges points that are closer than 1% on x,
         * and guarantees at least two points.
         */
        fun sanitize(points: List<CurvePoint>): List<CurvePoint> {
            val clamped = points
                .map { CurvePoint(ColorMath.clamp01(it.x), ColorMath.clamp01(it.y)) }
                .sortedBy { it.x }
            val merged = ArrayList<CurvePoint>(clamped.size)
            for (p in clamped) {
                val last = merged.lastOrNull()
                if (last != null && p.x - last.x < 0.01f) {
                    merged[merged.size - 1] = CurvePoint(last.x, (last.y + p.y) / 2f)
                } else {
                    merged.add(p)
                }
            }
            return when (merged.size) {
                0 -> IDENTITY_POINTS
                1 -> listOf(CurvePoint(0f, merged[0].y), CurvePoint(1f, merged[0].y))
                else -> merged
            }
        }

        private fun tangents(xs: FloatArray, ys: FloatArray): FloatArray {
            val n = xs.size
            val m = FloatArray(n)
            if (n == 2) {
                val s = (ys[1] - ys[0]) / maxOf(xs[1] - xs[0], 1e-6f)
                m[0] = s; m[1] = s
                return m
            }
            val d = FloatArray(n - 1) { (ys[it + 1] - ys[it]) / maxOf(xs[it + 1] - xs[it], 1e-6f) }
            m[0] = d[0]
            m[n - 1] = d[n - 2]
            for (i in 1 until n - 1) {
                m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
            }
            val monotone = d.all { it >= 0f } || d.all { it <= 0f }
            if (monotone) {
                // Fritsch–Carlson limiter keeps the interpolant monotone.
                for (i in 0 until n - 1) {
                    if (abs(d[i]) < 1e-6f) {
                        m[i] = 0f; m[i + 1] = 0f
                        continue
                    }
                    val a = m[i] / d[i]
                    val b = m[i + 1] / d[i]
                    val s = a * a + b * b
                    if (s > 9f) {
                        val tau = 3f / sqrt(s)
                        m[i] = tau * a * d[i]
                        m[i + 1] = tau * b * d[i]
                    }
                }
            }
            return m
        }
    }
}
