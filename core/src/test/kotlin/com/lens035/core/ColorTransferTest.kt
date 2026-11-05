package com.lens035.core

import com.lens035.core.color.ColorTransfer
import com.lens035.core.lut.CubeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class ColorTransferTest {
    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /** A varied "photo" around a base colour. */
    private fun photo(r: Int, g: Int, b: Int, seed: Int): IntArray {
        val rnd = Random(seed)
        return IntArray(4000) {
            val k = rnd.nextInt(-60, 61)
            argb((r + k + rnd.nextInt(-15, 16)).coerceIn(0, 255), (g + k + rnd.nextInt(-15, 16)).coerceIn(0, 255), (b + k + rnd.nextInt(-15, 16)).coerceIn(0, 255))
        }
    }

    @Test
    fun oklabRoundTrips() {
        val lab = FloatArray(3)
        val rgb = FloatArray(3)
        for (c in listOf(floatArrayOf(0.2f, 0.5f, 0.8f), floatArrayOf(1f, 1f, 1f), floatArrayOf(0.9f, 0.1f, 0.05f), floatArrayOf(0f, 0f, 0f))) {
            ColorTransfer.toOklab(c[0], c[1], c[2], lab)
            ColorTransfer.fromOklab(lab[0], lab[1], lab[2], rgb)
            for (i in 0 until 3) assertEquals(c[i], rgb[i], 2e-3f)
        }
    }

    @Test
    fun matchingAPhotoToItselfChangesNothing() {
        val p = photo(120, 110, 100, 1)
        val s = ColorTransfer.stats(p)
        val lut = ColorTransfer.buildLut(s, s, size = 17)
        val out = FloatArray(3)
        for (c in listOf(floatArrayOf(0.1f, 0.4f, 0.7f), floatArrayOf(0.8f, 0.6f, 0.3f), floatArrayOf(0.5f, 0.5f, 0.5f))) {
            lut.sample(c[0], c[1], c[2], out)
            for (i in 0 until 3) assertEquals(c[i], out[i], 0.01f)
        }
    }

    @Test
    fun aWarmReferenceWarmsACoolPhoto() {
        val cool = photo(90, 120, 170, 2)
        val warm = photo(190, 140, 80, 3)
        val lut = ColorTransfer.buildLut(ColorTransfer.stats(cool), ColorTransfer.stats(warm), size = 33)
        val out = FloatArray(3)
        var redShift = 0.0
        var blueShift = 0.0
        for (c in cool) {
            val r = ((c shr 16) and 0xFF) / 255f
            val g = ((c shr 8) and 0xFF) / 255f
            val b = (c and 0xFF) / 255f
            lut.sample(r, g, b, out)
            redShift += out[0] - r
            blueShift += out[2] - b
        }
        assertTrue("red should rise: $redShift", redShift / cool.size > 0.03)
        assertTrue("blue should fall: $blueShift", blueShift / cool.size < -0.03)
    }

    @Test
    fun theCubeFileReadsBackAsTheSameLut() {
        val lut = ColorTransfer.buildLut(ColorTransfer.stats(photo(90, 120, 170, 4)), ColorTransfer.stats(photo(190, 140, 80, 5)), size = 9)
        val parsed = CubeParser.parse(ColorTransfer.toCube(lut, "test"), "k")
        assertEquals(lut.size, parsed.size)
        var maxErr = 0f
        for (i in lut.data.indices) maxErr = maxOf(maxErr, abs(lut.data[i] - parsed.data[i]))
        assertTrue("max error $maxErr", maxErr < 1e-5f)
    }
}
