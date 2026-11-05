package com.lens035.core

import com.lens035.core.analysis.HealSource
import com.lens035.core.model.EditState
import com.lens035.core.model.HealSpot
import com.lens035.core.model.HistoryLabels
import com.lens035.core.render.RenderContext
import com.lens035.core.render.ShaderParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class HealTest {
    private val w = 200
    private val h = 150

    /** Left-to-right gradient with dark dots at the given pixel centres. */
    private fun scene(vararg dots: Pair<Int, Int>): FloatArray = FloatArray(w * h) { i ->
        val x = i % w
        val y = i / w
        if (dots.any { (dx, dy) -> hypot((x - dx).toFloat(), (y - dy).toFloat()) < 4f }) 0.1f else 0.3f + 0.5f * x / w
    }

    @Test
    fun theSourceKeepsTheSameSurroundings() {
        // Along a horizontal gradient only vertical moves keep the surroundings identical.
        val (sx, sy) = HealSource.pick(scene(100 to 75), w, h, 0.5f, 0.5f, 0.03f)
        assertTrue("source should be straight above or below, got $sx", abs(sx - 0.5f) < 0.02f)
        assertTrue(abs(sy - 0.5f) > 0.05f)
    }

    @Test
    fun anotherBlemishIsNotCopiedOver() {
        val r = 0.03f
        val step = (2.4f * r * h).toInt()
        // A second dot exactly where the nearest clean candidate above would be.
        val (sx, sy) = HealSource.pick(scene(100 to 75, 100 to 75 - step), w, h, 0.5f, 0.5f, r)
        val dx = (sx * w - 100f)
        val dy = (sy * h - (75f - step))
        assertTrue("picked the other blemish at ($sx, $sy)", hypot(dx, dy) > 6f)
    }

    @Test
    fun askingAgainOffersAnotherSource() {
        val first = HealSource.pick(scene(100 to 75), w, h, 0.5f, 0.5f, 0.03f)
        val second = HealSource.pick(scene(100 to 75), w, h, 0.5f, 0.5f, 0.03f, avoid = listOf(first))
        assertTrue(hypot((first.first - second.first) * w, (first.second - second.second) * h) > 0.03f * h)
    }

    @Test
    fun sourcesStayInsideTheFrameNearEdges() {
        val (sx, sy) = HealSource.pick(scene(4 to 4), w, h, 0.02f, 0.03f, 0.03f)
        assertTrue(sx in 0f..1f && sy in 0f..1f)
    }

    @Test
    fun healsReachTheShaderAndHistory() {
        val spot = HealSpot(1, 0.4f, 0.5f, 0.02f, 0.4f, 0.6f)
        val state = EditState(heals = listOf(spot))
        val p = ShaderParams.build(state, RenderContext(100, 100, 100, 100, 100, 100))
        assertEquals(1, (p.heal!!["u_healCount"] as com.lens035.core.render.UniformValue.I).v)
        assertTrue(p.healKey.isNotEmpty())
        assertNull(ShaderParams.build(EditState(), RenderContext(100, 100, 100, 100, 100, 100)).heal)
        assertEquals("Leke giderildi", HistoryLabels.describe(EditState(), state))
        // Healing belongs to one photo: not copied with a look, dropped for video.
        assertTrue(state.lookOnly().heals.isEmpty())
        assertTrue(state.forVideo().heals.isEmpty())
        assertEquals(state.heals, EditState().copy(heals = state.heals).applyLook(EditState()).heals)
    }
}
