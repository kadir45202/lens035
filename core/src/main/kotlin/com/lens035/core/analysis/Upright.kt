package com.lens035.core.analysis

import com.lens035.core.model.Geometry
import com.lens035.core.model.GeometryMath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Automatic levelling and vertical perspective ("upright"): finds long straight edges and picks
 * the straighten angle and vertical perspective that make the near-vertical ones vertical.
 * Works on the oriented frame (after quarter turns and flips), like the corrections it returns.
 */
object Upright {

    /** A straight edge through the frame, in oriented pixels, weighted by its length. */
    data class Line(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val weight: Float)

    /** Suggested corrections, or why there are none. */
    data class Result(val straighten: Float, val vertical: Float, val verticalLines: Int, val horizontalLines: Int) {
        val isNoChange: Boolean get() = straighten == 0f && vertical == 0f
    }

    /** Share of the estimated vertical perspective that is applied: full correction looks top-heavy. */
    private const val VERTICAL_SHARE = 0.8f

    /**
     * Rotates/flips a row-major luma image from source to oriented orientation (quarter turns,
     * then flips), matching [GeometryMath]'s order of operations.
     */
    fun orient(luma: FloatArray, w: Int, h: Int, g: Geometry): Triple<FloatArray, Int, Int> {
        val turns = g.quarterTurns
        val ow = if (turns % 2 == 1) h else w
        val oh = if (turns % 2 == 1) w else h
        val out = FloatArray(ow * oh)
        for (oy in 0 until oh) for (ox in 0 until ow) {
            // Undo flips (oriented space), then the quarter turns (back to source).
            val fx = if (g.flipH) ow - 1 - ox else ox
            val fy = if (g.flipV) oh - 1 - oy else oy
            val (sx, sy) = when (turns) {
                1 -> fy to (h - 1 - fx)
                2 -> (w - 1 - fx) to (h - 1 - fy)
                3 -> (w - 1 - fy) to fx
                else -> fx to fy
            }
            out[oy * ow + ox] = luma[sy * w + sx]
        }
        return Triple(out, ow, oh)
    }

    /**
     * Long straight edges via a gradient-guided Hough transform. Only near-vertical (within 30°)
     * and near-horizontal (within 20°) lines are kept; those are the ones levelling cares about.
     */
    fun detectLines(input: FloatArray, w: Int, h: Int): List<Line> {
        if (w < 16 || h < 16) return emptyList()
        // A light blur first: on stair-stepped edges the raw Sobel direction snaps towards the
        // pixel grid, which would bias every slanted line towards vertical.
        val luma = blur3(input, w, h)
        val mag = FloatArray(w * h)
        val dir = FloatArray(w * h)
        var maxMag = 0f
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val gx = (luma[i - w + 1] + 2f * luma[i + 1] + luma[i + w + 1]) - (luma[i - w - 1] + 2f * luma[i - 1] + luma[i + w - 1])
            val gy = (luma[i + w - 1] + 2f * luma[i + w] + luma[i + w + 1]) - (luma[i - w - 1] + 2f * luma[i - w] + luma[i - w + 1])
            val m = hypot(gx, gy)
            mag[i] = m
            dir[i] = atan2(gy, gx)
            if (m > maxMag) maxMag = m
        }
        if (maxMag < 1e-4f) return emptyList()
        val threshold = max(0.12f, edgeThreshold(mag))

        val thetaBins = 180
        val cx = w / 2f
        val cy = h / 2f
        val maxRho = hypot(cx, cy)
        // 2 px wide offset bins: Sobel edges are about two pixels thick and lines are rarely
        // exactly on a 1° step, so 1 px bins would split each line's votes.
        val rhoStep = 2f
        val rhoBins = (2 * maxRho / rhoStep).roundToInt() + 1
        val acc = IntArray(thetaBins * rhoBins)
        val cosT = FloatArray(thetaBins) { cos(it * PI / thetaBins).toFloat() }
        val sinT = FloatArray(thetaBins) { sin(it * PI / thetaBins).toFloat() }
        val allowed = BooleanArray(thetaBins) { isVerticalBin(it) || isHorizontalBin(it) }

        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            if (mag[i] < threshold) continue
            // The gradient is the line's normal; vote only for nearby angles.
            var phi = dir[i]
            if (phi < 0f) phi += PI.toFloat()
            val centre = (phi / PI.toFloat() * thetaBins).roundToInt()
            val px = x - cx
            val py = y - cy
            for (d in -4..4) {
                val t = ((centre + d) % thetaBins + thetaBins) % thetaBins
                if (!allowed[t]) continue
                val rho = px * cosT[t] + py * sinT[t]
                val r = ((rho + maxRho) / rhoStep).roundToInt()
                if (r in 0 until rhoBins) acc[t * rhoBins + r]++
            }
        }

        // Peaks: local maxima that are long enough for their orientation.
        // Façades break verticals into window-sized pieces: the votes along one line add up, and
        // refine() below still insists on a long, consistent edge.
        val minVertical = (h * 0.12f).roundToInt()
        val minHorizontal = (w * 0.15f).roundToInt()
        data class Peak(val t: Int, val r: Int, val votes: Int)
        val peaks = ArrayList<Peak>()
        for (t in 0 until thetaBins) {
            if (!allowed[t]) continue
            val minVotes = if (isVerticalBin(t)) minVertical else minHorizontal
            for (r in 0 until rhoBins) {
                val v = acc[t * rhoBins + r]
                if (v < minVotes) continue
                var isMax = true
                loop@ for (dt in -3..3) for (dr in -3..3) {
                    if (dt == 0 && dr == 0) continue
                    val tt = ((t + dt) % thetaBins + thetaBins) % thetaBins
                    val rr = if (t + dt < 0 || t + dt >= thetaBins) rhoBins - 1 - (r + dr) else r + dr
                    if (rr !in 0 until rhoBins) continue
                    val o = acc[tt * rhoBins + rr]
                    if (o > v || (o == v && (dt < 0 || (dt == 0 && dr < 0)))) {
                        isMax = false
                        break@loop
                    }
                }
                if (isMax) peaks.add(Peak(t, r, v))
            }
        }
        peaks.sortByDescending { it.votes }

        // Edge pixels once, for refining each Hough line to sub-degree accuracy.
        val edges = ArrayList<Int>()
        for (i in mag.indices) if (mag[i] >= threshold) edges.add(i)

        val lines = ArrayList<Line>()
        for (p in peaks.take(24)) {
            val theta = p.t * PI / thetaBins
            val c = cos(theta).toFloat()
            val s = sin(theta).toFloat()
            val rho = p.r * rhoStep - maxRho
            val minExtent = if (isVerticalBin(p.t)) h * 0.15f else w * 0.15f
            val fit = refine(edges, mag, dir, w, cx, cy, c, s, rho, minExtent) ?: continue
            val (px, py, dx, dy, support) = fit
            val line = if (isVerticalBin(p.t)) {
                // Through (px, py) along (dx, dy), clipped to the top and bottom rows.
                val xTop = px + (0f - py) * dx / dy
                val xBottom = px + (h - py) * dx / dy
                Line(xTop, 0f, xBottom, h.toFloat(), support)
            } else {
                val yLeft = py + (0f - px) * dy / dx
                val yRight = py + (w - px) * dy / dx
                Line(0f, yLeft, w.toFloat(), yRight, support)
            }
            // Two Hough peaks can refine onto the same edge; count it once.
            val duplicate = lines.any { abs(it.x0 - line.x0) + abs(it.y0 - line.y0) + abs(it.x1 - line.x1) + abs(it.y1 - line.y1) < 4f }
            if (!duplicate) lines.add(line)
        }
        return lines
    }

    private data class Fit(val px: Float, val py: Float, val dx: Float, val dy: Float, val support: Float)

    /**
     * Total least squares fit of the edge pixels within 1.5 px of a Hough line (normal (c, s),
     * offset rho from the centre) whose gradient agrees with that normal. Null when the support
     * is too small or too short (a blob of pixels has no reliable direction).
     */
    private fun refine(
        edges: List<Int>, mag: FloatArray, dir: FloatArray, w: Int,
        cx: Float, cy: Float, c: Float, s: Float, rho: Float, minExtent: Float,
    ): Fit? {
        // The Hough line is only a few degrees close: fit, then refit around the better line.
        // Each round the band follows the real edge further, so the angle converges.
        var nc = c
        var ns = s
        var nrho = rho
        var fit: Fit? = null
        for (round in 0 until 4) {
            val f = fitBand(edges, mag, dir, w, cx, cy, nc, ns, nrho, if (round == 0) 3f else 1.8f, minExtent) ?: return fit
            fit = f
            // The new normal is perpendicular to the fitted direction, through the fitted centroid.
            nc = f.dy
            ns = -f.dx
            if (nc * c + ns * s < 0f) {
                nc = -nc
                ns = -ns
            }
            nrho = (f.px - cx) * nc + (f.py - cy) * ns
        }
        return fit
    }

    private fun fitBand(
        edges: List<Int>, mag: FloatArray, dir: FloatArray, w: Int,
        cx: Float, cy: Float, c: Float, s: Float, rho: Float, band: Float, minExtent: Float,
    ): Fit? {
        val cosLimit = cos(Math.toRadians(15.0)).toFloat()
        // Pixels in the band whose gradient agrees with the normal, split by polarity: the two
        // sides of a post (dark-to-light and light-to-dark) must not be fitted as one line.
        val candidates = ArrayList<Int>()
        var positive = 0f
        var negative = 0f
        for (i in edges) {
            val x = (i % w).toFloat()
            val y = (i / w).toFloat()
            val d = (x - cx) * c + (y - cy) * s - rho
            if (abs(d) > band) continue
            val a = dir[i]
            val dot = cos(a) * c + sin(a) * s
            if (abs(dot) < cosLimit) continue
            candidates.add(if (dot > 0f) i else -i - 1)
            if (dot > 0f) positive += mag[i] else negative += mag[i]
        }
        val keepPositive = positive >= negative
        var sw = 0f
        var sx = 0f
        var sy = 0f
        var n = 0
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        val ws = ArrayList<Float>()
        for (code in candidates) {
            if ((code >= 0) != keepPositive) continue
            val i = if (code >= 0) code else -code - 1
            val x = (i % w).toFloat()
            val y = (i / w).toFloat()
            val m = mag[i]
            xs.add(x); ys.add(y); ws.add(m)
            sw += m; sx += m * x; sy += m * y; n++
        }
        if (n < 12 || sw <= 0f) return null
        val mx = sx / sw
        val my = sy / sw
        var sxx = 0f
        var syy = 0f
        var sxy = 0f
        for (k in xs.indices) {
            val ex = xs[k] - mx
            val ey = ys[k] - my
            sxx += ws[k] * ex * ex
            syy += ws[k] * ey * ey
            sxy += ws[k] * ex * ey
        }
        // Principal direction of the weighted scatter.
        val angle = 0.5f * atan2(2f * sxy, sxx - syy)
        val dx = cos(angle)
        val dy = sin(angle)
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (k in xs.indices) {
            val t = (xs[k] - mx) * dx + (ys[k] - my) * dy
            if (t < lo) lo = t
            if (t > hi) hi = t
        }
        if (hi - lo < minExtent) return null
        return Fit(mx, my, dx, dy, n.toFloat())
    }

    /** 3x3 binomial blur (1 2 1) with clamped borders. */
    private fun blur3(src: FloatArray, w: Int, h: Int): FloatArray {
        val tmp = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val l = src[y * w + max(0, x - 1)]
            val r = src[y * w + min(w - 1, x + 1)]
            tmp[y * w + x] = (l + 2f * src[y * w + x] + r) * 0.25f
        }
        val out = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val u = tmp[max(0, y - 1) * w + x]
            val d = tmp[min(h - 1, y + 1) * w + x]
            out[y * w + x] = (u + 2f * tmp[y * w + x] + d) * 0.25f
        }
        return out
    }

    /** Normal angle near 0° or 180°: the line itself is within 30° of vertical. */
    private fun isVerticalBin(t: Int) = t <= 30 || t >= 150

    /** Normal angle near 90°: the line is within 20° of horizontal. */
    private fun isHorizontalBin(t: Int) = t in 70..110

    /** The 92nd percentile of gradient magnitude: edges, not texture. */
    private fun edgeThreshold(mag: FloatArray): Float {
        val hist = IntArray(256)
        var maxM = 0f
        for (m in mag) if (m > maxM) maxM = m
        if (maxM <= 0f) return 1f
        for (m in mag) hist[min(255, (m / maxM * 255f).toInt())]++
        val target = (mag.size * 0.92f).toInt()
        var sum = 0
        for (b in 0 until 256) {
            sum += hist[b]
            if (sum >= target) return (b + 1) / 255f * maxM
        }
        return maxM
    }

    /**
     * Straighten (degrees) and vertical perspective (slider units) for a [w] x [h] oriented
     * frame. Needs a few near-vertical lines on both sides of the middle for perspective; with
     * fewer it only levels, from horizontal lines if need be. Returns null when the picture has
     * no usable lines.
     */
    fun estimate(lines: List<Line>, w: Int, h: Int): Result? {
        val ow = w.toFloat()
        val oh = h.toFloat()
        val verticals = lines.filter { abs(it.x1 - it.x0) <= abs(it.y1 - it.y0) }
        val horizontals = lines - verticals.toSet()
        val leftCount = verticals.count { (it.x0 + it.x1) / 2f < ow * 0.45f }
        val rightCount = verticals.count { (it.x0 + it.x1) / 2f > ow * 0.55f }
        val canDoPerspective = verticals.size >= 3 && leftCount >= 1 && rightCount >= 1

        if (verticals.size < 2) {
            if (horizontals.size < 2) return null
            // Level only, from horizontal lines, and only when they agree: lines receding into
            // the distance (a façade seen at an angle) say nothing about the horizon.
            val tilts = horizontals.map { Math.toDegrees(atan2((it.y1 - it.y0).toDouble(), (it.x1 - it.x0).toDouble())).toFloat() to it.weight }
            val median = weightedMedian(tilts)
            val spread = weightedMedian(tilts.map { abs(it.first - median) to it.second })
            if (abs(median) > 8f || spread > 1.5f) return null
            return Result(roundStraighten(-median), 0f, verticals.size, horizontals.size)
        }

        val totalWeight = verticals.sumOf { it.weight.toDouble() }.toFloat()
        fun loss(straighten: Float, vertical: Float): Float {
            val g = Geometry(straighten = straighten, vertical = vertical)
            val f = GeometryMath.invert(GeometryMath.correctionToOrientedRowMajor(g, ow, oh))
            val a = FloatArray(2)
            val b = FloatArray(2)
            val cap = Math.toRadians(4.0).toFloat()
            var sum = 0f
            for (l in verticals) {
                GeometryMath.apply(f, l.x0, l.y0, a)
                GeometryMath.apply(f, l.x1, l.y1, b)
                val dev = atan2(b[0] - a[0], b[1] - a[1])
                sum += l.weight * min(dev * dev, cap * cap)
            }
            // A little pull towards no change, so weak evidence does not swing the result.
            val reg = 0.0005f * ((vertical / 100f) * (vertical / 100f) + (straighten / 45f) * (straighten / 45f))
            return sum / totalWeight + reg
        }

        val vRange = if (canDoPerspective) 100f else 0f
        var bestS = 0f
        var bestV = 0f
        var best = Float.MAX_VALUE
        var s = -12f
        while (s <= 12.001f) {
            var v = -vRange
            while (v <= vRange + 0.001f) {
                val e = loss(s, v)
                if (e < best) {
                    best = e
                    bestS = s
                    bestV = v
                }
                v += 5f
            }
            s += 0.5f
        }
        // Refine around the coarse optimum.
        val s0 = bestS
        val v0 = bestV
        for (i in -5..5) for (j in -5..5) {
            val ss = s0 + i * 0.1f
            val vv = if (canDoPerspective) (v0 + j).coerceIn(-100f, 100f) else 0f
            val e = loss(ss, vv)
            if (e < best) {
                best = e
                bestS = ss
                bestV = vv
            }
        }
        val vertical = (bestV * VERTICAL_SHARE).roundToInt().toFloat().let { if (abs(it) < 3f) 0f else it }
        return Result(roundStraighten(bestS), vertical, verticals.size, horizontals.size)
    }

    /** Luma (0..1) of packed ARGB pixels. */
    fun luma(argb: IntArray): FloatArray = FloatArray(argb.size) { i ->
        val c = argb[i]
        (((c shr 16) and 0xFF) * 0.2126f + ((c shr 8) and 0xFF) * 0.7152f + (c and 0xFF) * 0.0722f) / 255f
    }

    /** Whole analysis on packed ARGB source pixels for an edit's orientation. */
    fun analyze(argb: IntArray, w: Int, h: Int, g: Geometry): Result? {
        val (l, ow, oh) = orient(luma(argb), w, h, g)
        return estimate(detectLines(l, ow, oh), ow, oh)
    }

    private fun roundStraighten(v: Float): Float {
        val r = (v * 10f).roundToInt() / 10f
        return if (abs(r) < 0.5f) 0f else r.coerceIn(-45f, 45f)
    }

    private fun weightedMedian(values: List<Pair<Float, Float>>): Float {
        val sorted = values.sortedBy { it.first }
        val total = sorted.sumOf { it.second.toDouble() }
        var acc = 0.0
        for ((v, wgt) in sorted) {
            acc += wgt
            if (acc >= total / 2) return v
        }
        return sorted.last().first
    }
}
