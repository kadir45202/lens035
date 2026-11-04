package com.lens035.core

import com.lens035.core.color.ColorMath
import com.lens035.core.color.ColorOps
import com.lens035.core.color.CurvePoint
import com.lens035.core.color.ToneCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ColorAndCurveTest {

    @Test
    fun srgbRoundTrip() {
        for (i in 0..100) {
            val v = i / 100f
            assertEquals(v, ColorMath.linearToSrgb(ColorMath.srgbToLinear(v)), 1e-4f)
        }
        assertEquals(0.2140f, ColorMath.srgbToLinear(0.5f), 1e-3f)
    }

    @Test
    fun halfFloatConversion() {
        val values = floatArrayOf(0f, 1f, 0.5f, 0.25f, 0.999f, 1e-3f, 2.5f, -0.75f, 65504f, 6.1035156e-5f, 3e-6f)
        for (v in values) {
            val back = ColorMath.halfToFloat(ColorMath.floatToHalf(v))
            val tol = maxOf(abs(v) * 1e-3f, 6e-8f)
            assertEquals("value $v", v, back, tol)
        }
        assertEquals(0x3C00.toShort(), ColorMath.floatToHalf(1f))
        assertEquals(0x3800.toShort(), ColorMath.floatToHalf(0.5f))
    }

    @Test
    fun hsvRoundTrip() {
        val hsv = FloatArray(3)
        val rgb = FloatArray(3)
        val samples = listOf(floatArrayOf(0.9f, 0.2f, 0.1f), floatArrayOf(0.1f, 0.8f, 0.3f), floatArrayOf(0.2f, 0.3f, 0.95f), floatArrayOf(0.5f, 0.5f, 0.5f))
        for (s in samples) {
            ColorMath.rgbToHsv(s[0], s[1], s[2], hsv)
            ColorMath.hsvToRgb(hsv[0], hsv[1], hsv[2], rgb)
            for (c in 0 until 3) assertEquals(s[c], rgb[c], 1e-4f)
        }
    }

    @Test
    fun bandWeightsArePartitionOfUnity() {
        val w = FloatArray(8)
        var h = 0f
        while (h < 360f) {
            ColorOps.bandWeights(h, w)
            assertEquals("hue $h", 1f, w.sum(), 1e-4f)
            h += 1.7f
        }
        ColorOps.bandWeights(240f, w)
        assertEquals(1f, w[5], 1e-5f) // pure blue band
    }

    @Test
    fun contrastCurveKeepsEndpointsAndPivot() {
        for (c in listOf(-1f, -0.5f, 0.3f, 1f)) {
            assertEquals(0f, ColorOps.contrastCurve(0f, c), 1e-5f)
            assertEquals(1f, ColorOps.contrastCurve(1f, c), 1e-5f)
            assertEquals(0.45f, ColorOps.contrastCurve(0.45f, c), 1e-3f)
        }
        assertTrue(ColorOps.contrastCurve(0.8f, 0.5f) > 0.8f)
        assertTrue(ColorOps.contrastCurve(0.2f, 0.5f) < 0.2f)
        assertTrue(ColorOps.contrastCurve(0.8f, -0.5f) < 0.8f)
    }

    @Test
    fun wbGainsPreserveLuminance() {
        val g = FloatArray(3)
        for (t in listOf(-1f, -0.3f, 0f, 0.6f, 1f)) for (m in listOf(-1f, 0f, 1f)) {
            ColorOps.wbGains(t, m, g)
            assertEquals(1f, ColorMath.luma(g[0], g[1], g[2]), 1e-4f)
        }
        ColorOps.wbGains(0.5f, 0f, g)
        assertTrue("warm boosts red", g[0] > g[2])
    }

    @Test
    fun gamutClipKeepsLuminance() {
        val c = floatArrayOf(1.6f, 0.4f, 0.1f)
        val y = ColorMath.luma(c[0], c[1], c[2])
        ColorOps.gamutClip(c)
        assertEquals(y, ColorMath.luma(c[0], c[1], c[2]), 1e-4f)
        assertTrue(c.all { it in 0f..1.0001f })
    }

    @Test
    fun identityCurve() {
        val curve = ToneCurve(ToneCurve.IDENTITY_POINTS)
        for (i in 0..10) assertEquals(i / 10f, curve.evaluate(i / 10f), 1e-5f)
        assertTrue(ToneCurve.isIdentity(ToneCurve.IDENTITY_POINTS))
    }

    @Test
    fun monotoneCurveDoesNotOvershoot() {
        val pts = listOf(CurvePoint(0f, 0f), CurvePoint(0.25f, 0.05f), CurvePoint(0.3f, 0.6f), CurvePoint(0.8f, 0.65f), CurvePoint(1f, 1f))
        val curve = ToneCurve(pts)
        var prev = -1f
        for (i in 0..1000) {
            val v = curve.evaluate(i / 1000f)
            assertTrue("monotone at $i", v >= prev - 1e-5f)
            prev = v
        }
        for (p in pts) assertEquals(p.y, curve.evaluate(p.x), 1e-4f)
    }

    @Test
    fun sanitizeMergesAndSorts() {
        val s = ToneCurve.sanitize(listOf(CurvePoint(0.5f, 0.5f), CurvePoint(0f, 0f), CurvePoint(0.505f, 0.7f), CurvePoint(1.2f, 1.1f)))
        assertEquals(3, s.size)
        assertEquals(0f, s[0].x, 0f)
        assertEquals(0.6f, s[1].y, 1e-5f)
        assertEquals(1f, s[2].x, 0f)
        assertEquals(1f, s[2].y, 0f)
    }
}
