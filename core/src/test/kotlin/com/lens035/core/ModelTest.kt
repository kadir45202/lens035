package com.lens035.core

import com.lens035.core.analysis.ImageAnalyzer
import com.lens035.core.color.CurvePoint
import com.lens035.core.film.FilmCatalog
import com.lens035.core.model.AspectRatio
import com.lens035.core.model.CropRect
import com.lens035.core.model.Curves
import com.lens035.core.model.EditState
import com.lens035.core.model.EffectType
import com.lens035.core.model.Geometry
import com.lens035.core.model.HueBand
import com.lens035.core.model.LightSettings
import com.lens035.core.model.LocalMask
import com.lens035.core.model.MixerBand
import com.lens035.core.pose.PoseMatcher
import com.lens035.core.pose.PoseTemplates
import com.lens035.core.presets.Looks
import com.lens035.core.render.FilmDefaults
import com.lens035.core.render.RenderContext
import com.lens035.core.render.ShaderParams
import com.lens035.core.render.UniformValue
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val ctx = RenderContext(1000, 750, 4000, 3000, 1000, 750)

    @Test
    fun editStateSerialisationRoundTrip() {
        val film = FilmDefaults.settingsFor(FilmCatalog.find("P400")!!)
        val state = EditState(
            film = film,
            light = LightSettings(exposure = 0.5f, highlights = -40f),
            curves = Curves(master = listOf(CurvePoint(0f, 0.05f), CurvePoint(0.5f, 0.55f), CurvePoint(1f, 1f))),
            geometry = Geometry(rotation = 1, straighten = 3f, crop = CropRect(0.1f, 0.1f, 0.9f, 0.8f), aspect = AspectRatio.CINEMA),
            masks = listOf(LocalMask.newRadial(1), LocalMask.newLinear(2)),
        ).withEffect(EffectType.GRAIN.defaultInstance()).withEffect(EffectType.KIRA.defaultInstance(3))
            .copy(mixer = com.lens035.core.model.ColorMixer().withBand(HueBand.BLUE, MixerBand(10f, -20f, 5f)))
        val text = json.encodeToString(EditState.serializer(), state)
        val back = json.decodeFromString(EditState.serializer(), text)
        assertEquals(state, back)
    }

    @Test
    fun defaultStateIsNeutral() {
        val p = ShaderParams.build(EditState(), ctx)
        assertEquals(0f, p.main.float("u_exposure"), 0f)
        assertEquals(0f, p.main.float("u_lutStrength"), 0f)
        assertEquals(0f, p.main.float("u_mixEnabled"), 0f)
        assertEquals(0f, p.main.float("u_curvesEnabled"), 0f)
        assertEquals(0f, p.effects.vec4("u_grain")[0], 0f)
        assertEquals(0f, p.effects.vec4("u_vignette")[0], 0f)
        assertFalse(p.needsBloomPyramid)
        assertNull(p.film)
        assertTrue(EditState().isDefault)
    }

    @Test
    fun filmAndEffectsMapToUniforms() {
        val recipe = FilmCatalog.find("T800")!!
        val state = EditState(film = FilmDefaults.settingsFor(recipe).copy(strength = 50f))
            .withEffect(EffectType.GLITCH.defaultInstance())
        val p = ShaderParams.build(state, ctx)
        assertEquals(0.5f, p.main.float("u_lutStrength"), 1e-6f)
        assertTrue("T800 has halation", p.effects.vec4("u_halation")[0] > 0f)
        assertTrue(p.needsBloomPyramid)
        assertEquals(0.5f, p.effects.vec4("u_fxGlitch")[0], 1e-6f)
        assertEquals(0f, p.effects.vec4("u_fxVhs")[0], 0f)
        val geo = p.effects["u_geo"] as UniformValue.M3
        assertEquals(1f, geo.data[0], 1e-6f)
    }

    @Test
    fun everyEffectProducesItsUniform() {
        for (type in EffectType.entries) {
            val p = ShaderParams.build(EditState().withEffect(type.defaultInstance()), ctx, dateText = "'98 11 04")
            assertNotNull(p)
            if (type == EffectType.DATE_STAMP) assertNotNull(p.dateStamp)
        }
    }

    @Test
    fun masksAreLimitedAndPacked() {
        val masks = (1..6).map { LocalMask.newRadial(it.toLong()) }
        val p = ShaderParams.build(EditState(masks = masks), ctx)
        assertEquals(ShaderParams.MAX_MASKS, (p.main["u_maskCount"] as UniformValue.I).v)
        assertEquals(ShaderParams.MAX_MASKS * 8, (p.main["u_maskGeo"] as UniformValue.V4Array).data.size)
        assertEquals(ShaderParams.MAX_MASKS * 12, (p.main["u_maskAdj"] as UniformValue.V4Array).data.size)
    }

    @Test
    fun curveTableIsIdentityByDefault() {
        val t = ShaderParams.curveTable(Curves())
        for (i in 0 until 256) {
            val x = i / 255f
            for (c in 0 until 4) assertEquals(x, t[i * 4 + c], 1e-5f)
        }
    }

    @Test
    fun looksReferenceRealFilms() {
        for (look in Looks.all) {
            val id = look.state.film?.id ?: continue
            assertNotNull("${look.id} -> $id", FilmCatalog.find(id))
        }
    }

    @Test
    fun effectStackStaysSortedAndUnique() {
        var s = EditState()
        s = s.withEffect(EffectType.GRAIN.defaultInstance())
        s = s.withEffect(EffectType.MIRROR.defaultInstance())
        s = s.withEffect(EffectType.GRAIN.defaultInstance().withValue(EffectType.GRAIN.amountParam, 10f))
        assertEquals(listOf(EffectType.MIRROR, EffectType.GRAIN), s.fx.map { it.type })
        assertEquals(10f, s.effect(EffectType.GRAIN)!!.amount, 0f)
        s = s.withoutEffect(EffectType.MIRROR)
        assertEquals(1, s.fx.size)
    }

    @Test
    fun autoEnhanceBrightensDarkImages() {
        val w = 32
        val h = 32
        val dark = IntArray(w * h) { 0xFF000000.toInt() or (30 shl 16) or (28 shl 8) or 25 }
        val stats = ImageAnalyzer.analyze(dark, w, h)
        val auto = ImageAnalyzer.autoParams(stats)
        assertTrue("exposure ${auto.exposure}", auto.exposure > 0.3f)
        val bright = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        val autoBright = ImageAnalyzer.autoParams(ImageAnalyzer.analyze(bright, w, h))
        assertTrue(autoBright.highlights < -30f)
    }

    @Test
    fun poseMatcherScoresIdenticalAndMirroredPosesHigh() {
        val ref = PoseTemplates.find("hand_hip")!!.skeleton
        val same = PoseMatcher.match(ref, ref, 0.75f)
        assertEquals(1f, same.score!!, 1e-4f)
        val mirrored = PoseMatcher.match(ref, ref.mirrored(), 0.75f)
        assertTrue(mirrored.score!! > 0.95f)
        val other = PoseMatcher.match(ref, PoseTemplates.find("jump")!!.skeleton, 0.75f)
        assertTrue("different pose should score lower: ${other.score}", other.score!! < 0.8f)
    }

    @Test
    fun poseMatcherAsksToComeCloser() {
        val ref = PoseTemplates.find("classic")!!.skeleton
        val small = ref.transformed(0.6f, 0.6f, 0.2f, 0.2f)
        val m = PoseMatcher.match(ref, small, 0.75f)
        assertTrue(m.hints.any { it.contains("yaklaş", ignoreCase = true) })
    }
}
