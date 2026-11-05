package com.lens035.core

import com.lens035.core.film.FilmCatalog
import com.lens035.core.model.AspectRatio
import com.lens035.core.model.ColorSettings
import com.lens035.core.model.CropRect
import com.lens035.core.model.EditState
import com.lens035.core.model.EffectType
import com.lens035.core.model.Geometry
import com.lens035.core.model.HistoryLabels
import com.lens035.core.model.LightSettings
import com.lens035.core.model.LocalMask
import com.lens035.core.presets.Looks
import com.lens035.core.render.FilmDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryLabelsTest {
    private val base = EditState()

    private fun label(after: EditState, before: EditState = base) = HistoryLabels.describe(before, after)

    @Test
    fun singleSlidersCarryTheirValue() {
        assertEquals("Pozlama +0.35", label(base.copy(light = LightSettings(exposure = 0.35f))))
        assertEquals("Kontrast -20", label(base.copy(light = LightSettings(contrast = -20f))))
        assertEquals("Sıcaklık +12", label(base.copy(color = ColorSettings(temperature = 12f))))
        // Two fields of one section: the section's name.
        assertEquals("Işık", label(base.copy(light = LightSettings(exposure = 0.2f, shadows = 30f))))
    }

    @Test
    fun filmsEffectsAndMasksAreNamed() {
        val p400 = FilmCatalog.find("P400")!!
        val withFilm = base.copy(film = FilmDefaults.settingsFor(p400))
        assertEquals("Film: " + p400.name, label(withFilm))
        assertEquals("Film yoğunluğu 60", label(withFilm.copy(film = withFilm.film!!.copy(strength = 60f)), withFilm))
        assertEquals("Film kaldırıldı", label(base, withFilm))

        val grain = base.withEffect(EffectType.GRAIN.defaultInstance())
        assertEquals(EffectType.GRAIN.title + " eklendi", label(grain))
        assertEquals(EffectType.GRAIN.title + " kaldırıldı", label(base, grain))

        val person = base.copy(masks = listOf(LocalMask.newPerson(1)))
        assertEquals("Kişi maskesi eklendi", label(person))
        assertEquals("Kişi maskesi ters çevrildi", label(person.copy(masks = listOf(person.masks[0].copy(invert = true))), person))
    }

    @Test
    fun geometryStepsHideTheCropTheyCarry() {
        val straight = base.copy(geometry = Geometry(straighten = 2.5f, crop = CropRect(0.05f, 0.05f, 0.95f, 0.95f)))
        assertEquals("Düzleştir 2.5°", label(straight))
        val upright = base.copy(geometry = Geometry(straighten = 1.4f, vertical = -17f, crop = CropRect(0.04f, 0.1f, 0.96f, 1f)))
        assertEquals("Perspektif ve düzleştirme", label(upright))
        assertEquals("Döndür", label(base.copy(geometry = Geometry(rotation = 1))))
        assertEquals("Kırp 1:1", label(base.copy(geometry = Geometry(aspect = AspectRatio.SQUARE, crop = CropRect(0.1f, 0f, 0.9f, 1f)))))
    }

    @Test
    fun looksAndResetsAreSummarised() {
        val look = Looks.all.first { it.state.fx.isNotEmpty() && it.state.film != null }.state
        assertEquals("Görünüm", label(look))
        assertEquals("Tümü sıfırlandı", label(base, look))
        assertEquals("Film: " + FilmCatalog.find("P400")!!.name + ", pozlama +0.50", label(
            base.copy(light = LightSettings(exposure = 0.5f), film = FilmDefaults.settingsFor(FilmCatalog.find("P400")!!)),
        ))
    }
}
