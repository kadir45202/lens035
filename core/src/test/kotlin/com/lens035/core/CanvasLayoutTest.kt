package com.lens035.core

import com.lens035.core.model.EditState
import com.lens035.core.model.EffectType
import com.lens035.core.render.CanvasLayout
import com.lens035.core.render.RenderContext
import com.lens035.core.render.ShaderParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasLayoutTest {
    private fun canvas(ratio: Int, padding: Float = 0f, fill: Int = 0): EditState = EditState().withEffect(
        EffectType.CANVAS.defaultInstance()
            .withValue(EffectType.CANVAS.amountParam, padding)
            .withValue(EffectType.CANVAS.param("ratio")!!, ratio.toFloat())
            .withValue(EffectType.CANVAS.param("fill")!!, fill.toFloat()),
    )

    @Test
    fun noCanvasMeansThePhotoIsTheOutput() {
        val c = CanvasLayout.of(EditState(), 1200, 900)
        assertTrue(c.isNone)
        assertEquals(1200, c.width)
        assertEquals(900, c.height)
    }

    @Test
    fun aStoryCanvasKeepsThePhotoWholeAndGrowsAroundIt() {
        val c = CanvasLayout.of(canvas(ratio = 0), 1200, 900)
        assertEquals(1200, c.width)
        assertEquals(2133, c.height)
        assertEquals(9f / 16f, c.width.toFloat() / c.height, 0.002f)
        // Full width, centred vertically, photo aspect preserved.
        assertEquals(0f, c.innerX, 1e-4f)
        assertEquals(1f, c.innerW, 1e-4f)
        assertEquals(0.5f, c.innerY + c.innerH / 2f, 1e-4f)
        assertEquals(1200f / 900f, (c.innerW * c.width) / (c.innerH * c.height), 0.01f)
        // A tall photo on a wide canvas grows sideways instead.
        val wide = CanvasLayout.of(canvas(ratio = 3), 900, 1200)
        assertEquals(1200, wide.height)
        assertEquals(1f, wide.innerH, 1e-4f)
    }

    @Test
    fun marginShrinksThePhotoOnTheCanvasButNotItsPixels() {
        val plain = CanvasLayout.of(canvas(ratio = 2), 1000, 1000)
        val padded = CanvasLayout.of(canvas(ratio = 2, padding = 100f), 1000, 1000)
        assertTrue(padded.innerW < plain.innerW)
        assertEquals(0.65f, padded.innerW, 0.01f)
        assertEquals(1000f, padded.innerW * padded.width, 2f)
    }

    @Test
    fun videoSizesAreEvenAndHugeCanvasesAreCapped() {
        val even = CanvasLayout.of(canvas(ratio = 0, padding = 13f), 1279, 721, evenDimensions = true)
        assertEquals(0, even.width % 2)
        assertEquals(0, even.height % 2)
        val huge = CanvasLayout.of(canvas(ratio = 0), 6000, 4000)
        assertTrue(maxOf(huge.width, huge.height) <= CanvasLayout.MAX_SIDE)
    }

    @Test
    fun shaderGetsThePhotoRectangleAndBlurredFillNeedsThePyramid() {
        val state = canvas(ratio = 1, padding = 20f)
        val c = CanvasLayout.of(state, 800, 600)
        val p = ShaderParams.build(state, RenderContext(800, 600, 800, 600, 800, 600, canvas = c))
        assertEquals(1f, p.effects.vec4("u_canvas")[0], 0f)
        assertEquals(c.innerW, p.effects.vec4("u_canvasInner")[2], 1e-6f)
        assertTrue(p.needsBloomPyramid)
        // A solid fill needs no blur, and without a layout the effect stays off.
        val solid = canvas(ratio = 1, fill = 2)
        val ps = ShaderParams.build(solid, RenderContext(800, 600, 800, 600, 800, 600, canvas = CanvasLayout.of(solid, 800, 600)))
        assertFalse(ps.needsBloomPyramid)
        val off = ShaderParams.build(state, RenderContext(800, 600, 800, 600, 800, 600))
        assertEquals(0f, off.effects.vec4("u_canvas")[0], 0f)
    }
}
