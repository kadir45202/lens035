package com.lens035.core.color

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Small, allocation-free colour helpers shared by the LUT builder, the image analyser and
 * the unit tests. All values are in 0..1 unless noted. The GLSL shaders implement the same
 * formulas (see assets/shaders/common.glsl) so CPU and GPU results agree.
 */
object ColorMath {
    /** Rec.709 / sRGB luminance weights (linear light). */
    const val LUMA_R = 0.2126f
    const val LUMA_G = 0.7152f
    const val LUMA_B = 0.0722f

    fun clamp01(v: Float): Float = if (v < 0f) 0f else if (v > 1f) 1f else v

    fun clamp(v: Float, lo: Float, hi: Float): Float = if (v < lo) lo else if (v > hi) hi else v

    fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = clamp01((x - e0) / (e1 - e0))
        return t * t * (3f - 2f * t)
    }

    /** sRGB electro-optical transfer function: encoded 0..1 -> linear light. */
    fun srgbToLinear(c: Float): Float {
        val v = max(c, 0f)
        return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    }

    /** Inverse of [srgbToLinear]. Values above 1 are encoded with the same curve. */
    fun linearToSrgb(c: Float): Float {
        val v = max(c, 0f)
        return if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f
    }

    fun luma(r: Float, g: Float, b: Float): Float = r * LUMA_R + g * LUMA_G + b * LUMA_B

    /**
     * RGB (0..1) to HSV. Hue in degrees 0..360, saturation and value 0..1.
     * Writes into [out] (size >= 3) to avoid allocation.
     */
    fun rgbToHsv(r: Float, g: Float, b: Float, out: FloatArray) {
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = mx - mn
        var h = 0f
        if (d > 1e-6f) {
            h = when (mx) {
                r -> 60f * (((g - b) / d) % 6f)
                g -> 60f * (((b - r) / d) + 2f)
                else -> 60f * (((r - g) / d) + 4f)
            }
        }
        if (h < 0f) h += 360f
        out[0] = h
        out[1] = if (mx <= 1e-6f) 0f else d / mx
        out[2] = mx
    }

    /** HSV (hue degrees, s, v) to RGB written into [out]. */
    fun hsvToRgb(hDeg: Float, s: Float, v: Float, out: FloatArray) {
        val h = ((hDeg % 360f) + 360f) % 360f / 60f
        val i = floor(h).toInt()
        val f = h - i
        val p = v * (1f - s)
        val q = v * (1f - s * f)
        val t = v * (1f - s * (1f - f))
        when (i) {
            0 -> { out[0] = v; out[1] = t; out[2] = p }
            1 -> { out[0] = q; out[1] = v; out[2] = p }
            2 -> { out[0] = p; out[1] = v; out[2] = t }
            3 -> { out[0] = p; out[1] = q; out[2] = v }
            4 -> { out[0] = t; out[1] = p; out[2] = v }
            else -> { out[0] = v; out[1] = p; out[2] = q }
        }
    }

    /** Signed shortest distance between two hues in degrees, in -180..180. */
    fun hueDistance(a: Float, b: Float): Float {
        var d = (a - b) % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    /** Pure, fully saturated colour for a hue, as RGB (used for tint wheels and swatches). */
    fun hueColor(hDeg: Float, out: FloatArray) = hsvToRgb(hDeg, 1f, 1f, out)

    /** IEEE 754 binary16 conversion (round-to-nearest-even), for half-float textures. */
    fun floatToHalf(value: Float): Short {
        val bits = java.lang.Float.floatToRawIntBits(value)
        val sign = (bits ushr 16) and 0x8000
        var exp = ((bits ushr 23) and 0xFF) - 127 + 15
        var mantissa = bits and 0x7FFFFF
        if (((bits ushr 23) and 0xFF) == 0xFF) {
            // NaN or infinity
            return (sign or 0x7C00 or (if (mantissa != 0) 0x200 else 0)).toShort()
        }
        if (exp <= 0) {
            if (exp < -10) return sign.toShort()
            mantissa = mantissa or 0x800000
            val shift = 14 - exp
            var halfMant = mantissa ushr shift
            val roundBit = 1 shl (shift - 1)
            if ((mantissa and roundBit) != 0 && ((mantissa and (roundBit - 1)) != 0 || (halfMant and 1) != 0)) {
                halfMant++
            }
            return (sign or halfMant).toShort()
        }
        if (exp >= 31) return (sign or 0x7C00).toShort()
        var half = sign or (exp shl 10) or (mantissa ushr 13)
        val roundBits = mantissa and 0x1FFF
        if (roundBits > 0x1000 || (roundBits == 0x1000 && (half and 1) != 0)) {
            half++ // may carry into the exponent, which is the correct rounding behaviour
        }
        return half.toShort()
    }

    /** binary16 to float, used by tests to verify [floatToHalf]. */
    fun halfToFloat(h: Short): Float {
        val bits = h.toInt() and 0xFFFF
        val sign = if ((bits and 0x8000) != 0) -1f else 1f
        val exp = (bits ushr 10) and 0x1F
        val mant = bits and 0x3FF
        return when (exp) {
            0 -> sign * (mant / 1024f) * 2f.pow(-14)
            31 -> if (mant == 0) sign * Float.POSITIVE_INFINITY else Float.NaN
            else -> sign * (1f + mant / 1024f) * 2f.pow(exp - 15)
        }
    }

    fun approxEquals(a: Float, b: Float, eps: Float = 1e-4f): Boolean = abs(a - b) <= eps
}
