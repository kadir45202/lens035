package com.lens035.core

import com.lens035.core.model.EditState
import com.lens035.core.model.LocalMask
import com.lens035.core.model.MaskRange
import com.lens035.core.model.RangeMode
import com.lens035.core.render.RenderContext
import com.lens035.core.render.ShaderParams
import com.lens035.core.render.UniformValue
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MaskRangeTest {
    private val ctx = RenderContext(100, 100, 100, 100, 100, 100)

    private fun range(state: EditState, mask: Int) =
        (ShaderParams.build(state, ctx).main["u_maskRange"] as UniformValue.V4Array).data.copyOfRange(mask * 4, mask * 4 + 4)

    @Test
    fun rangesReachTheShader() {
        val lum = LocalMask.newRadial(1).copy(range = MaskRange(RangeMode.LUMINANCE, low = 80f, high = 20f, softness = 30f))
        val color = LocalMask.newLinear(2).copy(range = MaskRange(RangeMode.COLOR, hue = -30f, tolerance = 40f))
        val s = EditState(masks = listOf(lum, color, LocalMask.newRadial(3)))
        // Brightness band is ordered low..high whatever the slider order.
        val r0 = range(s, 0)
        assertEquals(1f, r0[0], 0f)
        assertEquals(0.2f, r0[1], 1e-6f)
        assertEquals(0.8f, r0[2], 1e-6f)
        assertEquals(0.3f, r0[3], 1e-6f)
        // Hue wraps into 0..1.
        val r1 = range(s, 1)
        assertEquals(2f, r1[0], 0f)
        assertEquals(330f / 360f, r1[1], 1e-5f)
        assertEquals(0.4f, r1[2], 1e-6f)
        // No range means mode 0.
        assertEquals(0f, range(s, 2)[0], 0f)
    }

    @Test
    fun oldProjectsWithoutRangesStillLoad() {
        val json = Json { ignoreUnknownKeys = true }
        val old = """{"masks":[{"id":1,"kind":"radial","x0":0.5,"y0":0.5,"x1":0.3,"y1":0.3}]}"""
        val s = json.decodeFromString(EditState.serializer(), old)
        assertEquals(RangeMode.NONE, s.masks[0].range.mode)
        assertFalse(s.masks[0].invert)
    }
}
