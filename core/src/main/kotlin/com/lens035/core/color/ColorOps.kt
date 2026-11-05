package com.lens035.core.color

import com.lens035.core.model.HueBand
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Colour operations with a 1:1 GLSL counterpart in assets/shaders/common.glsl. Keeping
 * the maths identical means film LUTs (built here on the CPU) and live adjustments (run on the
 * GPU) behave the same way.
 */
object ColorOps {
    private val BAND_CENTERS = FloatArray(HueBand.entries.size) { HueBand.entries[it].centerDegrees }

    /**
     * White balance gains in linear light for temperature/tint in -1..1. Positive temperature
     * warms, positive tint adds magenta. Gains are normalised to keep luminance constant.
     */
    fun wbGains(temp: Float, tint: Float, out: FloatArray) {
        var r = 2f.pow(temp * 0.30f)
        var g = 1f
        var b = 2f.pow(-temp * 0.40f)
        g *= 2f.pow(-tint * 0.26f)
        r *= 2f.pow(tint * 0.06f)
        b *= 2f.pow(tint * 0.06f)
        val y = ColorMath.luma(r, g, b)
        out[0] = r / y; out[1] = g / y; out[2] = b / y
    }

    /**
     * Contrast S-curve in display space for c in -1..1, pivoting around [pivot] and always
     * mapping 0 -> 0 and 1 -> 1.
     */
    fun contrastCurve(x: Float, c: Float, pivot: Float = 0.45f): Float {
        if (abs(c) < 1e-5f) return x
        val v = ColorMath.clamp01(x)
        val gExp = ln(0.5f) / ln(pivot)
        val t = v.pow(gExp)
        val k = exp(c * 0.9f)
        val a = t.pow(k)
        val b = (1f - t).pow(k)
        val s = if (a + b <= 1e-12f) t else a / (a + b)
        return s.pow(1f / gExp)
    }

    /**
     * Partition-of-unity weights of the eight hue bands for [hueDeg]. Neighbouring bands blend
     * with a smoothstep so that the sum is always 1.
     */
    fun bandWeights(hueDeg: Float, out: FloatArray) {
        val n = BAND_CENTERS.size
        for (i in 0 until n) {
            val c = BAND_CENTERS[i]
            val next = BAND_CENTERS[(i + 1) % n] + if (i == n - 1) 360f else 0f
            val prev = BAND_CENTERS[(i + n - 1) % n] - if (i == 0) 360f else 0f
            val d = ColorMath.hueDistance(hueDeg, c)
            val width = if (d >= 0f) next - c else c - prev
            val t = ColorMath.clamp01(1f - abs(d) / width)
            out[i] = t * t * (3f - 2f * t)
        }
    }

    /**
     * Applies per-band hue/saturation/luminance shifts to a display-space colour in place.
     * hue shifts are degrees, sat/lum are -1..1.
     */
    fun applyHsl(rgb: FloatArray, hue: FloatArray, sat: FloatArray, lum: FloatArray, tmp: FloatArray, weights: FloatArray) {
        ColorMath.rgbToHsv(rgb[0], rgb[1], rgb[2], tmp)
        val h = tmp[0]
        val s = tmp[1]
        val v = tmp[2]
        val chromaW = ColorMath.smoothstep(0.02f, 0.22f, s) * ColorMath.smoothstep(0.0f, 0.12f, v)
        if (chromaW <= 0f) return
        bandWeights(h, weights)
        var dh = 0f
        var ds = 0f
        var dl = 0f
        for (i in weights.indices) {
            dh += weights[i] * hue[i]
            ds += weights[i] * sat[i]
            dl += weights[i] * lum[i]
        }
        val nh = h + dh * chromaW
        val ns = ColorMath.clamp01(s * (1f + ds * chromaW))
        ColorMath.hsvToRgb(nh, ns, v, rgb)
        if (abs(dl) > 1e-5f) {
            val gain = 2f.pow(dl * chromaW * s)
            for (i in 0 until 3) {
                rgb[i] = ColorMath.linearToSrgb(ColorMath.srgbToLinear(rgb[i]) * gain)
            }
        }
    }

    /**
     * Saturation (-1..1) and vibrance (-1..1) on a linear-light colour in place, preserving
     * luminance. Vibrance favours muted colours and protects skin tones.
     */
    fun applySaturation(lin: FloatArray, saturation: Float, vibrance: Float, tmp: FloatArray) {
        if (abs(saturation) < 1e-5f && abs(vibrance) < 1e-5f) return
        val y = ColorMath.luma(lin[0], lin[1], lin[2])
        var factor = 1f + saturation
        if (abs(vibrance) > 1e-5f) {
            ColorMath.rgbToHsv(
                ColorMath.linearToSrgb(lin[0]), ColorMath.linearToSrgb(lin[1]), ColorMath.linearToSrgb(lin[2]), tmp,
            )
            val s = tmp[1]
            val skin = 1f - 0.6f * ColorMath.smoothstep(25f, 0f, abs(ColorMath.hueDistance(tmp[0], 28f)))
            val muted = (1f - s) * (1f - s)
            factor *= 1f + vibrance * muted * (if (vibrance > 0f) skin else 1f) * 1.2f
        }
        for (i in 0 until 3) lin[i] = y + (lin[i] - y) * factor
        gamutClip(lin)
    }

    /**
     * Brings an out-of-range linear colour back into 0..1 while preserving its luminance and
     * hue: chroma is scaled down towards the grey of the same luminance.
     */
    fun gamutClip(lin: FloatArray) {
        val y = ColorMath.luma(lin[0], lin[1], lin[2])
        if (y >= 1f) { lin[0] = 1f; lin[1] = 1f; lin[2] = 1f; return }
        if (y <= 0f) { lin[0] = 0f; lin[1] = 0f; lin[2] = 0f; return }
        val mx = max(lin[0], max(lin[1], lin[2]))
        val mn = min(lin[0], min(lin[1], lin[2]))
        var k = 1f
        if (mx > 1f) k = min(k, (1f - y) / (mx - y))
        if (mn < 0f) k = min(k, y / (y - mn))
        if (k < 1f) for (i in 0 until 3) lin[i] = y + (lin[i] - y) * k
    }

    /** Adds a split-tone tint in display space weighted by luminance region. */
    fun applySplitTone(rgb: FloatArray, shadow: FloatArray?, shadowAmt: Float, highlight: FloatArray?, highlightAmt: Float) {
        val l = ColorMath.luma(rgb[0], rgb[1], rgb[2])
        val ws = 1f - ColorMath.smoothstep(0.0f, 0.55f, l)
        val wh = ColorMath.smoothstep(0.45f, 1.0f, l)
        if (shadow != null && shadowAmt > 0f) {
            val sy = ColorMath.luma(shadow[0], shadow[1], shadow[2])
            for (i in 0 until 3) rgb[i] += (shadow[i] - sy) * shadowAmt * ws
        }
        if (highlight != null && highlightAmt > 0f) {
            val hy = ColorMath.luma(highlight[0], highlight[1], highlight[2])
            for (i in 0 until 3) rgb[i] += (highlight[i] - hy) * highlightAmt * wh
        }
    }
}
