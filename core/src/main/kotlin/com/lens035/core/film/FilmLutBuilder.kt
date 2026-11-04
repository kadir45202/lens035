package com.lens035.core.film

import com.lens035.core.color.ColorMath
import com.lens035.core.color.ColorOps
import com.lens035.core.color.ToneCurve
import kotlin.math.abs
import kotlin.math.pow

/** A 3D colour lookup table: [size]^3 RGB entries, red fastest, then green, then blue. */
class Lut3D(val size: Int, val data: FloatArray, val key: String) {
    init {
        require(data.size == size * size * size * 3) { "LUT data has ${data.size} floats, expected ${size * size * size * 3}" }
    }

    /** Trilinear lookup, for CPU previews and tests. Writes the result into [out]. */
    fun sample(r: Float, g: Float, b: Float, out: FloatArray) {
        val n = size - 1
        val fr = ColorMath.clamp01(r) * n
        val fg = ColorMath.clamp01(g) * n
        val fb = ColorMath.clamp01(b) * n
        val r0 = fr.toInt().coerceAtMost(n - 1)
        val g0 = fg.toInt().coerceAtMost(n - 1)
        val b0 = fb.toInt().coerceAtMost(n - 1)
        val tr = fr - r0
        val tg = fg - g0
        val tb = fb - b0
        for (c in 0 until 3) {
            fun at(ri: Int, gi: Int, bi: Int) = data[((bi * size + gi) * size + ri) * 3 + c]
            val c00 = ColorMath.mix(at(r0, g0, b0), at(r0 + 1, g0, b0), tr)
            val c10 = ColorMath.mix(at(r0, g0 + 1, b0), at(r0 + 1, g0 + 1, b0), tr)
            val c01 = ColorMath.mix(at(r0, g0, b0 + 1), at(r0 + 1, g0, b0 + 1), tr)
            val c11 = ColorMath.mix(at(r0, g0 + 1, b0 + 1), at(r0 + 1, g0 + 1, b0 + 1), tr)
            out[c] = ColorMath.mix(ColorMath.mix(c00, c10, tg), ColorMath.mix(c01, c11, tg), tb)
        }
    }

    /** RGBA half-float payload for a GL_RGBA16F 3D texture. */
    fun toRgbaHalf(): ShortArray {
        val count = size * size * size
        val out = ShortArray(count * 4)
        val one = ColorMath.floatToHalf(1f)
        for (i in 0 until count) {
            out[i * 4] = ColorMath.floatToHalf(data[i * 3])
            out[i * 4 + 1] = ColorMath.floatToHalf(data[i * 3 + 1])
            out[i * 4 + 2] = ColorMath.floatToHalf(data[i * 3 + 2])
            out[i * 4 + 3] = one
        }
        return out
    }

    companion object {
        fun identity(size: Int = 17): Lut3D {
            val d = FloatArray(size * size * size * 3)
            val n = (size - 1).toFloat()
            var i = 0
            for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
                d[i++] = r / n; d[i++] = g / n; d[i++] = b / n
            }
            return Lut3D(size, d, "identity$size")
        }
    }
}

/** Evaluates [FilmRecipe]s, either per colour or into a [Lut3D]. */
object FilmLutBuilder {
    const val DEFAULT_SIZE = 33

    fun build(recipe: FilmRecipe, size: Int = DEFAULT_SIZE): Lut3D {
        val eval = Evaluator(recipe)
        val data = FloatArray(size * size * size * 3)
        val n = (size - 1).toFloat()
        val rgb = FloatArray(3)
        var i = 0
        for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
            rgb[0] = r / n; rgb[1] = g / n; rgb[2] = b / n
            eval.apply(rgb)
            data[i++] = rgb[0]; data[i++] = rgb[1]; data[i++] = rgb[2]
        }
        return Lut3D(size, data, "film:${recipe.id}:$size")
    }

    /** Stateful evaluator: caches curve tables and tint colours for one recipe. */
    class Evaluator(private val recipe: FilmRecipe) {
        private val curveMaster = recipe.curve?.let { ToneCurve(it) }
        private val curveR = recipe.curveR?.let { ToneCurve(it) }
        private val curveG = recipe.curveG?.let { ToneCurve(it) }
        private val curveB = recipe.curveB?.let { ToneCurve(it) }
        private val gains = FloatArray(3).also { ColorOps.wbGains(recipe.temp, recipe.tint, it) }
        private val shadowColor = recipe.shadowTint?.let { FloatArray(3).also { c -> ColorMath.hueColor(it.hue, c) } }
        private val highlightColor = recipe.highlightTint?.let { FloatArray(3).also { c -> ColorMath.hueColor(it.hue, c) } }
        private val monoTone = recipe.mono?.takeIf { it.toneAmount > 0f }?.let { FloatArray(3).also { c -> ColorMath.hueColor(it.toneHue, c) } }
        private val hasHsl = recipe.hueShift.any { it != 0f } || recipe.satShift.any { it != 0f } || recipe.lumShift.any { it != 0f }
        private val expGain = 2f.pow(recipe.exposure)
        private val tmp = FloatArray(3)
        private val weights = FloatArray(8)

        fun apply(rgb: FloatArray) {
            // 1. linear light: exposure, white balance and channel crosstalk
            val lin = rgb
            for (c in 0 until 3) lin[c] = ColorMath.srgbToLinear(rgb[c]) * expGain * gains[c]
            recipe.matrix?.let { m ->
                val r = lin[0]; val g = lin[1]; val b = lin[2]
                lin[0] = m[0] * r + m[1] * g + m[2] * b
                lin[1] = m[3] * r + m[4] * g + m[5] * b
                lin[2] = m[6] * r + m[7] * g + m[8] * b
            }
            ColorOps.gamutClip(lin)
            // 2. saturation behaviour of the stock
            ColorOps.applySaturation(lin, recipe.saturation, recipe.vibrance, tmp)

            // 3. black & white conversion happens in linear light
            val mono = recipe.mono
            if (mono != null) {
                val sum = mono.r + mono.g + mono.b
                val y = (lin[0] * mono.r + lin[1] * mono.g + lin[2] * mono.b) / (if (abs(sum) < 1e-6f) 1f else sum)
                lin[0] = y; lin[1] = y; lin[2] = y
            }

            // 4. display space: contrast, curves, hue work, split toning, fade
            for (c in 0 until 3) rgb[c] = ColorMath.linearToSrgb(lin[c])
            if (recipe.contrast != 0f) {
                for (c in 0 until 3) rgb[c] = ColorOps.contrastCurve(rgb[c], recipe.contrast)
            }
            curveMaster?.let { for (c in 0 until 3) rgb[c] = it.evaluate(rgb[c]) }
            curveR?.let { rgb[0] = it.evaluate(rgb[0]) }
            curveG?.let { rgb[1] = it.evaluate(rgb[1]) }
            curveB?.let { rgb[2] = it.evaluate(rgb[2]) }
            if (hasHsl && mono == null) {
                ColorOps.applyHsl(rgb, recipe.hueShift, recipe.satShift, recipe.lumShift, tmp, weights)
            }
            if (mono == null) {
                ColorOps.applySplitTone(
                    rgb,
                    shadowColor, recipe.shadowTint?.amount ?: 0f,
                    highlightColor, recipe.highlightTint?.amount ?: 0f,
                )
            } else if (monoTone != null) {
                // Toning is strongest in the midtones and fades out in pure black and white.
                val l = rgb[0]
                val w = 4f * l * (1f - l) * mono.toneAmount
                val ty = ColorMath.luma(monoTone[0], monoTone[1], monoTone[2])
                for (c in 0 until 3) rgb[c] += (monoTone[c] - ty) * w
            }
            // Black-level fade is user-adjustable and applied by the shader (see FilmSettings.fade);
            // only the subtle white roll-off is baked into the table.
            val range = 1f - recipe.fadeWhite
            for (c in 0 until 3) rgb[c] = ColorMath.clamp01(ColorMath.clamp01(rgb[c]) * range)
        }
    }
}
