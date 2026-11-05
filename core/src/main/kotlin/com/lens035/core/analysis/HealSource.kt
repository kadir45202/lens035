package com.lens035.core.analysis

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Picks where a heal takes its clean texture from: among nearby patches, the one whose
 * surroundings line up best with the blemish's surroundings (so the edge is seamless) and whose
 * inside is as calm as those surroundings (so it does not bring another blemish along).
 */
object HealSource {

    /**
     * [luma] is a small source-orientation copy (0..1, [w] x [h]); ([x], [y]) is the blemish
     * centre in uv and [radius] a fraction of the shorter side. Returns the source centre in uv.
     * Candidates near the points in [avoid] (uv) are skipped, to offer an alternative source.
     */
    fun pick(luma: FloatArray, w: Int, h: Int, x: Float, y: Float, radius: Float, avoid: List<Pair<Float, Float>> = emptyList()): Pair<Float, Float> {
        val short = min(w, h).toFloat()
        val r = (radius * short).coerceAtLeast(1f)
        val cx = x * w
        val cy = y * h
        val ringR = r * 1.35f
        val n = RING
        val dstRing = FloatArray(n) { k -> sample(luma, w, h, cx + ringR * cosOf(k, n), cy + ringR * sinOf(k, n)) }
        val ringMean = dstRing.average().toFloat()
        val ringVar = dstRing.fold(0f) { acc, v -> acc + (v - ringMean) * (v - ringMean) } / n

        var best: Pair<Float, Float>? = null
        var bestCost = Float.MAX_VALUE
        for (dist in DISTANCES) for (k in 0 until DIRECTIONS) {
            val sx = cx + cosOf(k, DIRECTIONS) * dist * r
            val sy = cy + sinOf(k, DIRECTIONS) * dist * r
            if (sx - ringR < 0f || sy - ringR < 0f || sx + ringR > w - 1f || sy + ringR > h - 1f) continue
            if (avoid.any { (ax, ay) -> hypot(ax * w - sx, ay * h - sy) < 1.5f * r }) continue
            var cost = 0f
            for (j in 0 until n) {
                val d = sample(luma, w, h, sx + ringR * cosOf(j, n), sy + ringR * sinOf(j, n)) - dstRing[j]
                cost += d * d
            }
            cost /= n
            // Inside of the candidate: centre plus an inner ring.
            var mean = sample(luma, w, h, sx, sy)
            val inner = FloatArray(8) { j -> sample(luma, w, h, sx + 0.6f * r * cosOf(j, 8), sy + 0.6f * r * sinOf(j, 8)) }
            mean = (mean + inner.sum()) / 9f
            var innerVar = (sample(luma, w, h, sx, sy) - mean).let { it * it }
            for (v in inner) innerVar += (v - mean) * (v - mean)
            innerVar /= 9f
            cost += 2f * abs(innerVar - ringVar) + 0.5f * (mean - ringMean) * (mean - ringMean)
            // Nearer is better when everything else is equal.
            cost += 1e-5f * dist
            if (cost < bestCost) {
                bestCost = cost
                best = sx / w to sy / h
            }
        }
        return best ?: fallback(x, y, r / w, r / h)
    }

    /** No candidate fits (a tiny image or a huge spot): mirror across the centre of the frame. */
    private fun fallback(x: Float, y: Float, rx: Float, ry: Float): Pair<Float, Float> {
        val sx = if (x < 0.5f) (x + 3f * rx).coerceAtMost(1f) else (x - 3f * rx).coerceAtLeast(0f)
        return sx to y.coerceIn(ry, 1f - ry)
    }

    private fun sample(l: FloatArray, w: Int, h: Int, px: Float, py: Float): Float {
        val x = px.coerceIn(0f, w - 1f)
        val y = py.coerceIn(0f, h - 1f)
        val x0 = x.toInt().coerceAtMost(w - 2).coerceAtLeast(0)
        val y0 = y.toInt().coerceAtMost(h - 2).coerceAtLeast(0)
        val tx = x - x0
        val ty = y - y0
        val a = l[y0 * w + x0] + (l[y0 * w + x0 + 1] - l[y0 * w + x0]) * tx
        val b = l[(y0 + 1) * w + x0] + (l[(y0 + 1) * w + x0 + 1] - l[(y0 + 1) * w + x0]) * tx
        return a + (b - a) * ty
    }

    private fun cosOf(k: Int, n: Int) = cos(2.0 * PI * k / n).toFloat()
    private fun sinOf(k: Int, n: Int) = sin(2.0 * PI * k / n).toFloat()

    private const val RING = 16
    private const val DIRECTIONS = 24
    private val DISTANCES = floatArrayOf(2.4f, 3.1f, 3.9f)
}
