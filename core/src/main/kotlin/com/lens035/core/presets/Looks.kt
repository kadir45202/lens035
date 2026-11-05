package com.lens035.core.presets

import com.lens035.core.film.FilmCatalog
import com.lens035.core.model.ColorSettings
import com.lens035.core.model.EditState
import com.lens035.core.model.EffectInstance
import com.lens035.core.model.EffectType
import com.lens035.core.model.EffectsSettings
import com.lens035.core.model.EnhanceSettings
import com.lens035.core.model.LightSettings
import com.lens035.core.render.FilmDefaults

/** A curated one-tap look: a film plus effects and light/colour touches. */
data class Look(
    val id: String,
    val name: String,
    val description: String,
    val swatch: Long,
    val state: EditState,
)

object Looks {
    private fun film(id: String, strength: Float = 100f, tweak: (com.lens035.core.model.FilmSettings) -> com.lens035.core.model.FilmSettings = { it }) =
        FilmCatalog.find(id)?.let { tweak(FilmDefaults.settingsFor(it).copy(strength = strength)) }

    private fun fx(type: EffectType, vararg values: Pair<String, Float>, seed: Int = 7) =
        EffectInstance(type, values.toMap(), seed)

    private fun state(
        filmId: String?,
        light: LightSettings = LightSettings(),
        color: ColorSettings = ColorSettings(),
        effects: EffectsSettings = EffectsSettings(),
        enhance: EnhanceSettings = EnhanceSettings(),
        fx: List<EffectInstance> = emptyList(),
        filmStrength: Float = 100f,
    ) = EditState(
        film = filmId?.let { film(it, filmStrength) },
        light = light, color = color, effects = effects, enhance = enhance,
        fx = fx.sortedBy { it.type.ordinal },
    )

    val all: List<Look> = listOf(
        Look(
            "summer98", "Yaz '98", "Altın tonlar, ışık sızıntısı ve tarih damgası.", 0xFFF2B24E,
            state(
                "G200",
                light = LightSettings(exposure = 0.15f, shadows = 15f),
                fx = listOf(fx(EffectType.LIGHT_LEAK, "amount" to 45f), fx(EffectType.DATE_STAMP)),
            ),
        ),
        Look(
            "velvet", "Kadife", "Kremsi portre tonları ve yumuşak odak.", 0xFFEDB79A,
            state(
                "P400",
                light = LightSettings(highlights = -20f, shadows = 15f),
                enhance = EnhanceSettings(skin = 35f),
                fx = listOf(fx(EffectType.SOFT_FOCUS, "amount" to 30f)),
            ),
        ),
        Look(
            "neon_night", "Neon Gece", "Serin gece tonları, kırmızı hale ve parıltı.", 0xFF2C8FA3,
            state(
                "T800",
                light = LightSettings(contrast = 10f, blacks = -10f),
                fx = listOf(fx(EffectType.GLOW, "amount" to 35f)),
            ),
        ),
        Look(
            "misty", "Sisli Sabah", "Düz sinema renkleri ve hafif pus.", 0xFF8FA39C,
            state(
                "ET",
                effects = EffectsSettings(dehaze = -18f, clarity = -10f),
                light = LightSettings(exposure = 0.2f),
            ),
        ),
        Look(
            "y2k", "Y2K Flaş", "Canlı renkler, yıldız ışıltıları ve RGB kayması.", 0xFFE0457B,
            state(
                "U100",
                light = LightSettings(exposure = 0.1f, contrast = 15f),
                fx = listOf(
                    fx(EffectType.KIRA, "amount" to 55f),
                    fx(EffectType.RGB_SPLIT, "amount" to 18f),
                    fx(EffectType.GLOW, "amount" to 25f),
                ),
            ),
        ),
        Look(
            "memory", "Anı", "Anlık fotoğraf çerçevesi ve tozlu kare.", 0xFFE6D3A3,
            state(
                "I600",
                fx = listOf(fx(EffectType.DUST, "amount" to 35f), fx(EffectType.FRAME, "amount" to 45f, "style" to 0f)),
            ),
        ),
        Look(
            "cinematic", "Sinematik", "Turkuaz-turuncu sinema karesi.", 0xFF1F7A7A,
            state(
                "V500",
                light = LightSettings(contrast = 8f, highlights = -25f, shadows = 10f),
                effects = EffectsSettings(vignette = -18f, clarity = 8f),
            ),
        ),
        Look(
            "drama_bw", "Dramatik S/B", "Kontrastlı siyah beyaz ve derin köşeler.", 0xFFD9D9D9,
            state(
                "TX400",
                light = LightSettings(contrast = 15f, whites = 10f, blacks = -15f),
                effects = EffectsSettings(clarity = 20f, vignette = -25f),
            ),
        ),
        Look(
            "home_video", "Ev Videosu", "VHS kaseti, soluk renkler ve tarih.", 0xFF6B7FD6,
            state(
                "CP200",
                fx = listOf(fx(EffectType.VHS, "amount" to 55f), fx(EffectType.DATE_STAMP, "style" to 3f)),
            ),
        ),
        Look(
            "golden", "Altın Saat", "Gün batımı ışığı ve hafif parıltı.", 0xFFF29A4E,
            state(
                "P160",
                enhance = EnhanceSettings(goldenHour = 55f, glow = 25f),
                light = LightSettings(highlights = -15f),
            ),
        ),
        Look(
            "dream", "Rüya", "Pastel tonlar ve prizma yansımaları.", 0xFF9ED9C8,
            state(
                "H400",
                fx = listOf(fx(EffectType.SOFT_FOCUS, "amount" to 35f), fx(EffectType.PRISM, "amount" to 30f)),
            ),
        ),
        Look(
            "sky", "Gökyüzü", "Derin maviler ve net bulutlar.", 0xFF2F6FD6,
            state(
                "E100", filmStrength = 70f,
                enhance = EnhanceSettings(sky = 60f, structure = 25f),
                light = LightSettings(highlights = -30f),
            ),
        ),
        Look(
            "street", "Sokak", "Serin yeşiller ve belirgin gren.", 0xFF4FA36B,
            state(
                "C400",
                effects = EffectsSettings(clarity = 15f),
                fx = listOf(fx(EffectType.GRAIN, "amount" to 30f)),
            ),
        ),
        Look(
            "auto", "Otomatik", "Görüntüyü analiz edip dengeler.", 0xFFF2EEE8,
            state(null, enhance = EnhanceSettings(accent = 70f)),
        ),
    )

    fun find(id: String): Look? = all.firstOrNull { it.id == id }
}
