package com.lens035.core.analysis

import com.lens035.core.color.ColorMath
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** Global statistics of an image, computed once from a small (~256 px) downsample. */
data class ImageStats(
    val histogram: IntArray,
    val meanLuma: Float,
    val p01: Float,
    val p05: Float,
    val p50: Float,
    val p95: Float,
    val p99: Float,
    val clippedHighlights: Float,
    val crushedShadows: Float,
    val meanSaturation: Float,
    /** Grey-world colour balance of the midtones in linear light (r, g, b), normalised to g = 1. */
    val midtoneBalance: FloatArray,
    /** Atmospheric light estimate for dehaze, display space. */
    val atmosphere: FloatArray,
    /** 0..1 estimate of haze (bright dark channel). */
    val haze: Float,
    val skyFraction: Float,
    val skinFraction: Float,
) {
    companion object {
        val NEUTRAL = ImageStats(
            histogram = IntArray(256), meanLuma = 0.45f, p01 = 0.02f, p05 = 0.06f, p50 = 0.45f,
            p95 = 0.92f, p99 = 0.97f, clippedHighlights = 0f, crushedShadows = 0f, meanSaturation = 0.3f,
            midtoneBalance = floatArrayOf(1f, 1f, 1f), atmosphere = floatArrayOf(0.95f, 0.95f, 0.95f),
            haze = 0f, skyFraction = 0f, skinFraction = 0f,
        )
    }

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/** Parameters the "Accent" slider applies at 100%. Same units as the edit model. */
data class AutoParams(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val vibrance: Float = 0f,
    val clarity: Float = 0f,
    val dehaze: Float = 0f,
)

object ImageAnalyzer {

    /**
     * Analyses ARGB pixels (as returned by Bitmap.getPixels). Expects a small image; every pixel
     * is visited once.
     */
    fun analyze(argb: IntArray, width: Int, height: Int): ImageStats {
        require(argb.size >= width * height && width > 0 && height > 0)
        val n = width * height
        val hist = IntArray(256)
        var sumL = 0.0
        var sumS = 0.0
        var clipped = 0
        var crushed = 0
        var midR = 0.0
        var midG = 0.0
        var midB = 0.0
        var midCount = 0
        var sky = 0
        var skin = 0
        val dark = FloatArray(n)
        val hsv = FloatArray(3)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                val c = argb[i]
                val r = ((c shr 16) and 0xFF) / 255f
                val g = ((c shr 8) and 0xFF) / 255f
                val b = (c and 0xFF) / 255f
                val l = ColorMath.luma(r, g, b)
                hist[(l * 255f + 0.5f).toInt().coerceIn(0, 255)]++
                sumL += l
                if (max(r, max(g, b)) >= 0.985f) clipped++
                if (l <= 0.02f) crushed++
                ColorMath.rgbToHsv(r, g, b, hsv)
                sumS += hsv[1]
                if (l in 0.2f..0.8f && hsv[1] < 0.6f) {
                    midR += ColorMath.srgbToLinear(r)
                    midG += ColorMath.srgbToLinear(g)
                    midB += ColorMath.srgbToLinear(b)
                    midCount++
                }
                if (y < height * 0.45f && hsv[0] in 185f..250f && hsv[1] > 0.12f && hsv[2] > 0.35f) sky++
                if (hsv[0] in 5f..45f && hsv[1] in 0.15f..0.65f && hsv[2] in 0.25f..0.95f) skin++
                dark[i] = min(r, min(g, b))
            }
        }
        fun percentile(p: Float): Float {
            val target = (n * p).toLong()
            var acc = 0L
            for (i in 0 until 256) {
                acc += hist[i]
                if (acc >= target) return i / 255f
            }
            return 1f
        }

        // Atmospheric light: average colour of the brightest 0.5% of the dark channel.
        val sortedDark = dark.copyOf().also { it.sort() }
        val thresholdIndex = ((n - 1) * 0.995f).toInt().coerceIn(0, n - 1)
        val darkThreshold = sortedDark[thresholdIndex]
        var ar = 0.0
        var ag = 0.0
        var ab = 0.0
        var ac = 0
        for (i in 0 until n) {
            if (dark[i] >= darkThreshold) {
                val c = argb[i]
                ar += ((c shr 16) and 0xFF) / 255.0
                ag += ((c shr 8) and 0xFF) / 255.0
                ab += (c and 0xFF) / 255.0
                ac++
            }
        }
        val atmosphere = if (ac > 0) {
            floatArrayOf((ar / ac).toFloat(), (ag / ac).toFloat(), (ab / ac).toFloat()).map { it.coerceIn(0.6f, 1f) }.toFloatArray()
        } else floatArrayOf(0.95f, 0.95f, 0.95f)
        val medianDark = sortedDark[n / 2]

        val balance = if (midCount > 32 && midG > 1e-6) {
            floatArrayOf((midR / midG).toFloat(), 1f, (midB / midG).toFloat())
        } else floatArrayOf(1f, 1f, 1f)

        return ImageStats(
            histogram = hist,
            meanLuma = (sumL / n).toFloat(),
            p01 = percentile(0.01f),
            p05 = percentile(0.05f),
            p50 = percentile(0.5f),
            p95 = percentile(0.95f),
            p99 = percentile(0.99f),
            clippedHighlights = clipped / n.toFloat(),
            crushedShadows = crushed / n.toFloat(),
            meanSaturation = (sumS / n).toFloat(),
            midtoneBalance = balance,
            atmosphere = atmosphere,
            // Haze = bright dark channel AND washed-out colour; a warm, saturated picture with a
            // high minimum channel is not hazy.
            haze = ColorMath.smoothstep(0.12f, 0.45f, medianDark) *
                (1f - ColorMath.smoothstep(0.15f, 0.42f, (sumS / n).toFloat())),
            skyFraction = sky / n.toFloat(),
            skinFraction = skin / n.toFloat(),
        )
    }

    /** Suggests a balanced, natural edit for the image ("Accent" at 100%). */
    fun autoParams(s: ImageStats): AutoParams {
        // Exposure: bring the median towards mid grey in linear light, gently.
        val medLin = max(ColorMath.srgbToLinear(s.p50), 0.004f)
        var ev = (ln(0.20f / medLin) / ln(2f)) * 0.7f
        // Don't brighten images that already clip a lot.
        if (s.clippedHighlights > 0.02f) ev = min(ev, 0.1f)
        ev = ev.coerceIn(-1.2f, 1.2f)

        val highlights = when {
            s.clippedHighlights > 0.01f -> -(35f + 600f * s.clippedHighlights).coerceAtMost(80f)
            s.p99 > 0.95f -> -25f
            else -> -10f
        }
        val shadows = when {
            s.p05 < 0.04f -> 40f
            s.p05 < 0.10f -> 25f
            else -> 10f
        }
        val whites = if (s.p99 < 0.9f) ((0.96f - s.p99) * 160f).coerceIn(0f, 40f) else 0f
        val blacks = if (s.p01 > 0.05f) (-(s.p01 - 0.02f) * 220f).coerceIn(-35f, 0f) else 0f
        val spread = s.p95 - s.p05
        val contrast = when {
            spread < 0.45f -> 25f
            spread < 0.65f -> 15f
            spread > 0.9f -> -5f
            else -> 6f
        }
        val vibrance = when {
            s.meanSaturation < 0.18f -> 35f
            s.meanSaturation < 0.30f -> 22f
            s.meanSaturation > 0.55f -> 0f
            else -> 12f
        }
        // Partial grey-world white balance correction of the midtones.
        val rb = ln(max(s.midtoneBalance[0], 1e-3f) / max(s.midtoneBalance[2], 1e-3f)) / ln(2f)
        val temperature = (-rb * 45f).coerceIn(-25f, 25f)
        // r and b above g means a magenta cast: correct towards green (negative tint).
        val gm = ln(max((s.midtoneBalance[0] + s.midtoneBalance[2]) / 2f, 1e-3f)) / ln(2f)
        val tint = (-gm * 40f).coerceIn(-15f, 15f)
        val dehaze = (s.haze * 30f).coerceIn(0f, 30f)
        return AutoParams(
            exposure = ev,
            contrast = contrast,
            highlights = highlights,
            shadows = shadows,
            whites = whites,
            blacks = blacks,
            temperature = if (abs(temperature) < 3f) 0f else temperature,
            tint = if (abs(tint) < 3f) 0f else tint,
            vibrance = vibrance,
            clarity = 12f,
            dehaze = dehaze,
        )
    }
}
