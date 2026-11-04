package com.lens035.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lens035.app.gl.EglCore
import com.lens035.app.gl.LensPipeline
import com.lens035.app.gl.OffscreenGl
import com.lens035.app.gl.PersonMaskData
import com.lens035.app.gl.ShaderSources
import com.lens035.core.analysis.ImageStats
import com.lens035.core.film.FilmCatalog
import com.lens035.core.model.EditState
import com.lens035.core.model.EffectType
import com.lens035.core.model.Geometry
import com.lens035.core.model.GeometryMath
import com.lens035.core.model.LightSettings
import com.lens035.core.model.LocalAdjust
import com.lens035.core.model.LocalMask
import com.lens035.core.presets.Looks
import com.lens035.core.render.FilmDefaults
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Runs the real GLES 3 pipeline on the device/emulator GPU: every film, effect and look must
 * compile, render without GL errors and produce a sensible image, and the orientation chain
 * (upload -> passes -> readback) must keep the picture upright.
 */
@RunWith(AndroidJUnit4::class)
class PipelineInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var egl: EglCore
    private lateinit var pipeline: LensPipeline
    private lateinit var gl: OffscreenGl
    private lateinit var source: Bitmap

    @Before
    fun setUp() {
        egl = EglCore().also { it.create() }
        pipeline = LensPipeline(ShaderSources.get(context))
        pipeline.init()
        gl = OffscreenGl(pipeline)
        source = testImage(240, 180)
    }

    @After
    fun tearDown() {
        pipeline.release()
        egl.release()
    }

    /** Top half warm red, bottom half blue, with a grey ramp across the middle. */
    private fun testImage(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint()
        p.color = Color.rgb(200, 60, 40)
        c.drawRect(0f, 0f, w.toFloat(), h / 2f, p)
        p.color = Color.rgb(40, 70, 190)
        c.drawRect(0f, h / 2f, w.toFloat(), h.toFloat(), p)
        for (x in 0 until w) {
            val v = x * 255 / (w - 1)
            p.color = Color.rgb(v, v, v)
            c.drawRect(x.toFloat(), h * 0.45f, x + 1f, h * 0.55f, p)
        }
        return bmp
    }

    private fun render(state: EditState): Bitmap = gl.render(source, state, ImageStats.NEUTRAL, "'98 11 04")

    private fun meanLuma(b: Bitmap): Float {
        var sum = 0.0
        for (y in 0 until b.height step 4) for (x in 0 until b.width step 4) {
            val c = b.getPixel(x, y)
            sum += (Color.red(c) * 0.2126 + Color.green(c) * 0.7152 + Color.blue(c) * 0.0722)
        }
        return (sum / ((b.width / 4) * (b.height / 4))).toFloat()
    }

    @Test
    fun neutralEditReproducesTheSource() {
        val out = render(EditState())
        assertEquals(source.width, out.width)
        assertEquals(source.height, out.height)
        var maxDiff = 0
        for (y in 0 until out.height step 3) for (x in 0 until out.width step 3) {
            val a = source.getPixel(x, y)
            val b = out.getPixel(x, y)
            maxDiff = maxOf(maxDiff, abs(Color.red(a) - Color.red(b)), abs(Color.green(a) - Color.green(b)), abs(Color.blue(a) - Color.blue(b)))
        }
        assertTrue("neutral render differs by $maxDiff", maxDiff <= 3)
    }

    @Test
    fun pictureStaysUpright() {
        val out = render(EditState())
        val top = out.getPixel(out.width / 2, out.height / 8)
        val bottom = out.getPixel(out.width / 2, out.height * 7 / 8)
        assertTrue("top should be red", Color.red(top) > Color.blue(top))
        assertTrue("bottom should be blue", Color.blue(bottom) > Color.red(bottom))
    }

    @Test
    fun exposureBrightens() {
        val base = meanLuma(render(EditState()))
        val bright = meanLuma(render(EditState(light = LightSettings(exposure = 1f))))
        assertTrue("$bright <= $base", bright > base + 10f)
    }

    @Test
    fun everyFilmRenders() {
        for (film in FilmCatalog.all) {
            val out = render(EditState(film = FilmDefaults.settingsFor(film)))
            val l = meanLuma(out)
            assertTrue("${film.id} produced a blank image ($l)", l in 5f..250f)
        }
    }

    @Test
    fun everyEffectRenders() {
        for (type in EffectType.entries) {
            val out = render(EditState().withEffect(type.defaultInstance(5)))
            val l = meanLuma(out)
            assertTrue("${type.name} produced a blank image ($l)", l in 3f..252f)
        }
    }

    @Test
    fun everyLookRenders() {
        for (look in Looks.all) {
            val l = meanLuma(render(look.state))
            assertTrue("${look.id} produced a blank image ($l)", l in 3f..252f)
        }
    }

    /** Person on the left half of the frame, background on the right. */
    private fun leftHalfPerson(): PersonMaskData {
        val w = 60
        val h = 45
        val data = ByteArray(w * h) { i -> if (i % w < w / 2) 255.toByte() else 0 }
        return PersonMaskData(w, h, data, "test-left-half")
    }

    private fun pixelDiff(a: Bitmap, b: Bitmap, x: Int, y: Int): Int {
        val ca = a.getPixel(x, y)
        val cb = b.getPixel(x, y)
        return maxOf(abs(Color.red(ca) - Color.red(cb)), abs(Color.green(ca) - Color.green(cb)), abs(Color.blue(ca) - Color.blue(cb)))
    }

    @Test
    fun portraitBlurKeepsThePersonAndNeedsAMask() {
        val portrait = EditState().withEffect(EffectType.BOKEH.defaultInstance().withValue(EffectType.BOKEH.amountParam, 100f))
        val stats = ImageStats.NEUTRAL
        // The grey ramp band in the middle has hard edges above and below: blur smears it.
        val y = (source.height * 0.5f).toInt()
        val personX = (source.width * 0.2f).toInt()
        val backgroundX = (source.width * 0.8f).toInt()

        val blurred = gl.render(source, portrait, stats, "", personMask = leftHalfPerson())
        assertTrue("person must stay sharp", pixelDiff(blurred, source, personX, y) <= 6)
        assertTrue("background must be blurred", pixelDiff(blurred, source, backgroundX, y) > 12)

        // Without segmentation there is no subject to keep, so the effect stays off.
        val noMask = gl.render(source, portrait, stats, "")
        assertTrue(pixelDiff(noMask, source, backgroundX, y) <= 3)
    }

    @Test
    fun personMaskAdjustsOnlyThePerson() {
        val state = EditState(masks = listOf(LocalMask.newPerson(1).copy(adjust = LocalAdjust(exposure = 1.5f))))
        val out = gl.render(source, state, ImageStats.NEUTRAL, "", personMask = leftHalfPerson())
        val y = (source.height * 0.2f).toInt()
        val left = out.getPixel((source.width * 0.2f).toInt(), y)
        val leftSrc = source.getPixel((source.width * 0.2f).toInt(), y)
        assertTrue("person side should be brighter", Color.green(left) > Color.green(leftSrc) + 10)
        assertTrue("background side should be untouched", pixelDiff(out, source, (source.width * 0.8f).toInt(), y) <= 3)
    }

    private fun luma(c: Int): Float = Color.red(c) * 0.2126f + Color.green(c) * 0.7152f + Color.blue(c) * 0.0722f

    @Test
    fun perspectiveCropLeavesNoEmptyCorners() {
        val g = Geometry(vertical = -60f, horizontal = 20f)
        val crop = GeometryMath.maxCropForAspect(0f, source.width.toFloat(), source.height.toFloat(), g)
        val out = render(EditState(geometry = g.copy(crop = crop)))
        val corners = listOf(1 to 1, out.width - 2 to 1, 1 to out.height - 2, out.width - 2 to out.height - 2)
        for ((x, y) in corners) assertTrue("corner ($x, $y) is empty", luma(out.getPixel(x, y)) > 8f)
        // Uncropped, the narrowed bottom leaves empty corners: the projective path really ran.
        val full = render(EditState(geometry = g))
        assertTrue(luma(full.getPixel(1, full.height - 2)) < 8f)
    }

    @Test
    fun geometryAndMasksRender() {
        val rotated = render(EditState(geometry = Geometry(rotation = 1)))
        assertEquals(source.height, rotated.width)
        assertEquals(source.width, rotated.height)
        val masked = render(EditState(masks = listOf(LocalMask.newRadial(1), LocalMask.newLinear(2))))
        assertTrue(meanLuma(masked) > 3f)
    }
}
