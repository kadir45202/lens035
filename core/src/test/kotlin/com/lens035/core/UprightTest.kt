package com.lens035.core

import com.lens035.core.analysis.Upright
import com.lens035.core.model.Geometry
import com.lens035.core.model.GeometryMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class UprightTest {
    private val w = 320
    private val h = 240

    /** Upright scene: dark vertical posts and a horizon on a light ground. */
    private fun upright(x: Float, y: Float): Float {
        val posts = floatArrayOf(0.12f, 0.27f, 0.41f, 0.58f, 0.73f, 0.88f)
        for (p in posts) if (abs(x - p * w) < 3.5f) return 0.15f
        if (abs(y - h * 0.62f) < 2.5f) return 0.25f
        return 0.85f
    }

    /** What a camera tilted by the inverse of [g] would have recorded (oriented == source here). */
    private fun photographed(g: Geometry): IntArray {
        val f = GeometryMath.invert(GeometryMath.correctionToOrientedRowMajor(g, w.toFloat(), h.toFloat()))
        val q = FloatArray(2)
        return IntArray(w * h) { i ->
            GeometryMath.apply(f, (i % w) + 0.5f, (i / w) + 0.5f, q)
            val v = (upright(q[0], q[1]) * 255f).roundToInt().coerceIn(0, 255)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
    }

    @Test
    fun recoversLevelAndVerticalPerspectiveFromLines() {
        val truth = Geometry(straighten = 3.5f, vertical = -40f)
        val back = GeometryMath.correctionToOrientedRowMajor(truth, w.toFloat(), h.toFloat())
        val a = FloatArray(2)
        val b = FloatArray(2)
        val lines = listOf(0.15f, 0.3f, 0.45f, 0.6f, 0.8f).map { fx ->
            GeometryMath.apply(back, fx * w, 0f, a)
            GeometryMath.apply(back, fx * w, h.toFloat(), b)
            Upright.Line(a[0], a[1], b[0], b[1], 100f)
        }
        val r = Upright.estimate(lines, w, h)
        assertNotNull(r)
        assertEquals(3.5f, r!!.straighten, 0.3f)
        assertEquals(-40f * 0.8f, r.vertical, 3f)
    }

    @Test
    fun findsTheCorrectionInARealImage() {
        val truth = Geometry(straighten = -2.5f, vertical = 45f)
        val r = Upright.analyze(photographed(truth), w, h, Geometry())
        assertNotNull(r)
        assertTrue("found ${r!!.verticalLines} vertical lines", r.verticalLines >= 3)
        assertEquals(-2.5f, r.straighten, 0.6f)
        assertEquals(45f * 0.8f, r.vertical, 6f)
    }

    @Test
    fun aStraightPhotoNeedsNothing() {
        val r = Upright.analyze(photographed(Geometry()), w, h, Geometry())
        assertNotNull(r)
        assertTrue("expected no change, got $r", r!!.isNoChange)
    }

    @Test
    fun featurelessImageGivesNoAdvice() {
        val flat = IntArray(w * h) { 0xFF808080.toInt() }
        assertEquals(null, Upright.analyze(flat, w, h, Geometry()))
    }

    @Test
    fun orientMatchesTheGeometryChain() {
        val sw = 5
        val sh = 3
        val luma = FloatArray(sw * sh) { it.toFloat() }
        for (g in listOf(Geometry(rotation = 1), Geometry(rotation = 2), Geometry(rotation = 3, flipH = true), Geometry(flipV = true))) {
            val (o, ow, oh) = Upright.orient(luma, sw, sh, g)
            val m = GeometryMath.outputToSourceRowMajor(sw, sh, g)
            val p = FloatArray(2)
            for (oy in 0 until oh) for (ox in 0 until ow) {
                GeometryMath.apply(m, (ox + 0.5f) / ow, (oy + 0.5f) / oh, p)
                val sx = (p[0] * sw).toInt().coerceIn(0, sw - 1)
                val sy = (p[1] * sh).toInt().coerceIn(0, sh - 1)
                assertEquals("$g at $ox,$oy", luma[sy * sw + sx], o[oy * ow + ox], 0f)
            }
        }
    }
}
