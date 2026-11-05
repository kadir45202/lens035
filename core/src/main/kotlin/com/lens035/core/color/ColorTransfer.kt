package com.lens035.core.color

import com.lens035.core.film.Lut3D
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.sqrt

/**
 * Colour match: moves a photo's colour statistics towards a reference photo's, Reinhard-style,
 * in Oklab (a perceptual space where lightness and colour separate cleanly). Limits keep odd
 * references from wrecking the picture, and lightness moves only part of the way so the photo
 * keeps its own exposure. The transform is baked into a 3D LUT on display-encoded sRGB, the
 * same domain as imported .cube files.
 */
object ColorTransfer {

    /** Mean and standard deviation of L, a, b. */
    class Stats(val mean: FloatArray, val std: FloatArray)

    /** Oklab statistics of packed ARGB pixels (alpha ignored). */
    fun stats(argb: IntArray): Stats {
        require(argb.isNotEmpty())
        val lab = FloatArray(3)
        val sum = DoubleArray(3)
        val sq = DoubleArray(3)
        for (c in argb) {
            toOklab(((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f, lab)
            for (i in 0 until 3) {
                sum[i] += lab[i]
                sq[i] += lab[i].toDouble() * lab[i]
            }
        }
        val n = argb.size.toDouble()
        val mean = FloatArray(3) { (sum[it] / n).toFloat() }
        val std = FloatArray(3) { sqrt((sq[it] / n - (sum[it] / n) * (sum[it] / n)).coerceAtLeast(0.0)).toFloat() }
        return Stats(mean, std)
    }

    /**
     * LUT taking a photo with [target] statistics towards [reference]. [lightness] is how far
     * lightness follows (0 keeps it, 1 matches fully); colour always follows fully.
     */
    fun buildLut(target: Stats, reference: Stats, size: Int = 33, lightness: Float = 0.5f, key: String = "match"): Lut3D {
        val scale = FloatArray(3) { i ->
            val t = target.std[i].coerceAtLeast(1e-4f)
            val (lo, hi) = if (i == 0) 0.7f to 1.4f else 0.5f to 2f
            (reference.std[i] / t).coerceIn(lo, hi)
        }
        // A reference with a strong cast moves the colour mean, but not without limit.
        val shift = FloatArray(3) { i ->
            val d = reference.mean[i] - target.mean[i]
            if (i == 0) d else d.coerceIn(-MAX_CHROMA_SHIFT, MAX_CHROMA_SHIFT)
        }
        val data = FloatArray(size * size * size * 3)
        val n = (size - 1).toFloat()
        val lab = FloatArray(3)
        val rgb = FloatArray(3)
        var o = 0
        for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
            toOklab(r / n, g / n, b / n, lab)
            val l = target.mean[0] + shift[0] + (lab[0] - target.mean[0]) * scale[0]
            lab[0] += (l - lab[0]) * lightness.coerceIn(0f, 1f)
            for (i in 1..2) lab[i] = target.mean[i] + shift[i] + (lab[i] - target.mean[i]) * scale[i]
            fromOklab(lab[0], lab[1], lab[2], rgb)
            data[o++] = ColorMath.clamp01(rgb[0])
            data[o++] = ColorMath.clamp01(rgb[1])
            data[o++] = ColorMath.clamp01(rgb[2])
        }
        return Lut3D(size, data, key)
    }

    /** The LUT as an Adobe/Resolve .cube file. */
    fun toCube(lut: Lut3D, title: String): String {
        val sb = StringBuilder(lut.size * lut.size * lut.size * 26 + 64)
        sb.append("TITLE \"").append(title.replace("\"", "")).append("\"\n")
        sb.append("LUT_3D_SIZE ").append(lut.size).append('\n')
        val d = lut.data
        var i = 0
        while (i < d.size) {
            sb.append(String.format(Locale.US, "%.6f %.6f %.6f\n", d[i], d[i + 1], d[i + 2]))
            i += 3
        }
        return sb.toString()
    }

    /** sRGB (display encoded, 0..1) to Oklab. */
    fun toOklab(r: Float, g: Float, b: Float, out: FloatArray) {
        val lr = ColorMath.srgbToLinear(r)
        val lg = ColorMath.srgbToLinear(g)
        val lb = ColorMath.srgbToLinear(b)
        val l = cbrt(0.4122214708f * lr + 0.5363325363f * lg + 0.0514459929f * lb)
        val m = cbrt(0.2119034982f * lr + 0.6806995451f * lg + 0.1073969566f * lb)
        val s = cbrt(0.0883024619f * lr + 0.2817188376f * lg + 0.6299787005f * lb)
        out[0] = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s
        out[1] = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
        out[2] = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
    }

    /** Oklab to display-encoded sRGB (not clamped). */
    fun fromOklab(lightness: Float, a: Float, b: Float, out: FloatArray) {
        val l0 = lightness + 0.3963377774f * a + 0.2158037573f * b
        val m0 = lightness - 0.1055613458f * a - 0.0638541728f * b
        val s0 = lightness - 0.0894841775f * a - 1.2914855480f * b
        val l = l0 * l0 * l0
        val m = m0 * m0 * m0
        val s = s0 * s0 * s0
        out[0] = encode(4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s)
        out[1] = encode(-1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s)
        out[2] = encode(-0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s)
    }

    private fun encode(linear: Float): Float =
        if (linear < 0f) -ColorMath.linearToSrgb(abs(linear)) else ColorMath.linearToSrgb(linear)

    /** Largest move of the a/b means (Oklab units): a strong tint, not a different photo. */
    private const val MAX_CHROMA_SHIFT = 0.06f
}
