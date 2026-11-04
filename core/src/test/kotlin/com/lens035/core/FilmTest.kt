package com.lens035.core

import com.lens035.core.color.ColorMath
import com.lens035.core.film.FilmCatalog
import com.lens035.core.film.FilmCategory
import com.lens035.core.film.FilmLutBuilder
import com.lens035.core.film.Lut3D
import com.lens035.core.lut.CubeParseException
import com.lens035.core.lut.CubeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs

class FilmTest {

    @Test
    fun catalogHasAtLeastTwentyUniqueFilms() {
        val films = FilmCatalog.all
        assertTrue("expected >= 20 films, got ${films.size}", films.size >= 20)
        assertEquals(films.size, films.map { it.id }.toSet().size)
        assertEquals(films.size, films.map { it.name }.toSet().size)
        for (f in films) assertTrue("${f.id} needs a description", f.description.isNotBlank())
    }

    @Test
    fun everyFilmProducesAValidLut() {
        for (film in FilmCatalog.all) {
            val lut = FilmLutBuilder.build(film, 17)
            for (v in lut.data) {
                assertFalse("${film.id} produced NaN", v.isNaN())
                assertTrue("${film.id} out of range: $v", v in 0f..1f)
            }
        }
    }

    @Test
    fun greyRampStaysMonotoneForNonCreativeFilms() {
        val eval = FloatArray(3)
        for (film in FilmCatalog.all.filter { it.category != FilmCategory.CREATIVE }) {
            val e = FilmLutBuilder.Evaluator(film)
            var prev = -1f
            for (i in 0..64) {
                val v = i / 64f
                eval[0] = v; eval[1] = v; eval[2] = v
                e.apply(eval)
                val y = ColorMath.luma(eval[0], eval[1], eval[2])
                assertTrue("${film.id} grey ramp not monotone at $v ($y < $prev)", y >= prev - 2e-3f)
                prev = y
            }
        }
    }

    @Test
    fun monoFilmsAreNeutralUnlessToned() {
        val c = FloatArray(3)
        for (film in FilmCatalog.all.filter { it.mono != null && it.mono!!.toneAmount == 0f }) {
            val e = FilmLutBuilder.Evaluator(film)
            c[0] = 0.8f; c[1] = 0.3f; c[2] = 0.1f
            e.apply(c)
            assertEquals(film.id, c[0], c[1], 1e-4f)
            assertEquals(film.id, c[1], c[2], 1e-4f)
        }
    }

    @Test
    fun lutSamplingMatchesDirectEvaluationOnGrid() {
        val film = FilmCatalog.find("P400")!!
        val lut = FilmLutBuilder.build(film, 33)
        val direct = FloatArray(3)
        val sampled = FloatArray(3)
        val e = FilmLutBuilder.Evaluator(film)
        val r = 12 / 32f; val g = 20 / 32f; val b = 5 / 32f
        direct[0] = r; direct[1] = g; direct[2] = b
        e.apply(direct)
        lut.sample(r, g, b, sampled)
        for (i in 0 until 3) assertEquals(direct[i], sampled[i], 1e-5f)
    }

    @Test
    fun identityLutIsIdentity() {
        val lut = Lut3D.identity(17)
        val out = FloatArray(3)
        lut.sample(0.3f, 0.6f, 0.9f, out)
        assertEquals(0.3f, out[0], 1e-5f)
        assertEquals(0.6f, out[1], 1e-5f)
        assertEquals(0.9f, out[2], 1e-5f)
        assertEquals(17 * 17 * 17 * 4, lut.toRgbaHalf().size)
    }

    @Test
    fun parsesCubeFiles() {
        val text = buildString {
            appendLine("# test")
            appendLine("TITLE \"Identity\"")
            appendLine("LUT_3D_SIZE 2")
            appendLine("DOMAIN_MIN 0 0 0")
            appendLine("DOMAIN_MAX 1 1 1")
            for (b in 0..1) for (g in 0..1) for (r in 0..1) appendLine("$r.0 $g.0 $b.0")
        }
        val lut = CubeParser.parse(text, "t")
        assertEquals(2, lut.size)
        val out = FloatArray(3)
        lut.sample(0.25f, 0.5f, 0.75f, out)
        assertEquals(0.25f, out[0], 1e-5f)
        assertEquals(0.5f, out[1], 1e-5f)
        assertEquals(0.75f, out[2], 1e-5f)
    }

    @Test
    fun cubeDomainIsNormalised() {
        val text = buildString {
            appendLine("LUT_3D_SIZE 2")
            appendLine("DOMAIN_MIN 0 0 0")
            appendLine("DOMAIN_MAX 2 2 2")
            for (b in 0..1) for (g in 0..1) for (r in 0..1) appendLine("${r * 2} ${g * 2} ${b * 2}")
        }
        val lut = CubeParser.parse(text, "t")
        assertTrue(lut.data.all { abs(it) < 1e-6f || abs(it - 1f) < 1e-6f })
    }

    @Test
    fun rejectsBrokenCubes() {
        val bad = listOf(
            "LUT_1D_SIZE 4\n0 0 0",
            "0 0 0\n1 1 1",
            "LUT_3D_SIZE 2\n0 0 0\n1 1 1",
            "LUT_3D_SIZE 99",
        )
        for (t in bad) {
            try {
                CubeParser.parse(t, "x")
                fail("expected failure for: $t")
            } catch (expected: CubeParseException) {
                assertTrue(expected.message!!.isNotBlank())
            }
        }
    }
}
