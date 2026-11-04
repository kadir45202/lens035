package com.lens035.core

import com.lens035.core.model.CropRect
import com.lens035.core.model.Geometry
import com.lens035.core.model.GeometryMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {
    private val out = FloatArray(2)

    private fun map(g: Geometry, u: Float, v: Float, w: Int = 400, h: Int = 300): FloatArray {
        val m = GeometryMath.outputToSourceRowMajor(w, h, g)
        GeometryMath.apply(m, u, v, out)
        return out.copyOf()
    }

    private fun assertPoint(expectedX: Float, expectedY: Float, p: FloatArray) {
        assertEquals(expectedX, p[0], 1e-4f)
        assertEquals(expectedY, p[1], 1e-4f)
    }

    @Test
    fun identityGeometry() {
        assertPoint(0f, 0f, map(Geometry(), 0f, 0f))
        assertPoint(1f, 1f, map(Geometry(), 1f, 1f))
        assertPoint(0.3f, 0.7f, map(Geometry(), 0.3f, 0.7f))
    }

    @Test
    fun quarterTurnClockwise() {
        val g = Geometry(rotation = 1)
        // After a clockwise turn the top-left of the output is the bottom-left of the source.
        assertPoint(0f, 1f, map(g, 0f, 0f))
        assertPoint(0f, 0f, map(g, 1f, 0f))
        assertPoint(1f, 1f, map(g, 0f, 1f))
        assertEquals(300, GeometryMath.outputSize(400, 300, g).width)
        assertEquals(400, GeometryMath.outputSize(400, 300, g).height)
    }

    @Test
    fun halfTurnAndThreeQuarterTurn() {
        assertPoint(1f, 1f, map(Geometry(rotation = 2), 0f, 0f))
        val g3 = Geometry(rotation = 3)
        assertPoint(1f, 0f, map(g3, 0f, 0f))
        assertPoint(0f, 0f, map(g3, 0f, 1f))
    }

    @Test
    fun flips() {
        assertPoint(1f, 0f, map(Geometry(flipH = true), 0f, 0f))
        assertPoint(0f, 1f, map(Geometry(flipV = true), 0f, 0f))
    }

    @Test
    fun cropMapsCorners() {
        val g = Geometry(crop = CropRect(0.25f, 0.1f, 0.75f, 0.6f))
        assertPoint(0.25f, 0.1f, map(g, 0f, 0f))
        assertPoint(0.75f, 0.6f, map(g, 1f, 1f))
        val size = GeometryMath.outputSize(400, 300, g)
        assertEquals(200, size.width)
        assertEquals(150, size.height)
    }

    @Test
    fun straightenKeepsCentreAndInverseRoundTrips() {
        val g = Geometry(straighten = 12f, crop = CropRect(0.2f, 0.2f, 0.8f, 0.8f))
        assertPoint(0.5f, 0.5f, map(g, 0.5f, 0.5f))
        val m = GeometryMath.outputToSourceRowMajor(400, 300, g)
        val inv = GeometryMath.sourceToOutputRowMajor(400, 300, g)
        val p = FloatArray(2)
        GeometryMath.apply(m, 0.1f, 0.9f, p)
        GeometryMath.apply(inv, p[0], p[1], p)
        assertPoint(0.1f, 0.9f, p)
    }

    @Test
    fun columnMajorMatrixMatchesRowMajor() {
        val g = Geometry(rotation = 1, flipH = true, straighten = -7f, crop = CropRect(0.1f, 0.2f, 0.9f, 0.7f))
        val r = GeometryMath.outputToSourceRowMajor(640, 480, g)
        val c = GeometryMath.outputToSourceMatrix(640, 480, g)
        for (i in 0 until 3) for (j in 0 until 3) assertEquals(r[i * 3 + j], c[j * 3 + i], 0f)
    }

    @Test
    fun fitCropRemovesEmptyCorners() {
        val w = 400f
        val h = 300f
        assertFalse(GeometryMath.cropFits(CropRect.FULL, 10f, w, h))
        val fitted = GeometryMath.fitCrop(CropRect.FULL, 10f, w, h)
        assertTrue(GeometryMath.cropFits(fitted, 10f, w, h))
        assertTrue(fitted.width < 1f)
        // aspect preserved
        assertEquals(1f, fitted.width / fitted.height, 1e-3f)
        val fitted2 = GeometryMath.maxCropForAspect(1f, w, h, 25f)
        assertTrue(GeometryMath.cropFits(fitted2, 25f, w, h))
        assertEquals(1f, fitted2.width * w / (fitted2.height * h), 1e-2f)
    }
}
