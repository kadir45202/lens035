package com.lens035.core.model

import com.lens035.core.film.FilmCatalog
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Short names for history steps, in the words the editor's panels use: what changed between
 * two edits, with the value when a single slider moved ("Pozlama +0.35", "Film: Portre 400").
 */
object HistoryLabels {
    /** Name of the first entry: the photo as it was opened. */
    const val ORIGINAL = "Açılış"

    fun describe(before: EditState, after: EditState): String {
        val parts = listOfNotNull(
            film(before.film, after.film),
            light(before.light, after.light),
            color(before.color, after.color),
            if (before.mixer != after.mixer) "Renk karışımı" else null,
            if (before.grading != after.grading) "Tonlama" else null,
            if (before.curves != after.curves) "Eğri" else null,
            detail(before.detail, after.detail),
            effects(before.effects, after.effects),
            enhance(before.enhance, after.enhance),
            fx(before.fx, after.fx),
            geometry(before.geometry, after.geometry),
            masks(before.masks, after.masks),
            lut(before, after),
        )
        // Undoing a single change back to the original is still that change, not a reset.
        if (after.isDefault && parts.size > 1) return "Tümü sıfırlandı"
        return when (parts.size) {
            0 -> "Düzenleme"
            1 -> parts[0]
            2 -> parts[0] + ", " + parts[1].replaceFirstChar { it.lowercase(TR) }
            // Many sections at once: a look, a preset or pasted settings.
            else -> "Görünüm"
        }
    }

    private val TR = Locale.forLanguageTag("tr")

    private fun signed(v: Float): String {
        val r = v.roundToInt()
        return if (r > 0) "+$r" else r.toString()
    }

    private fun ev(v: Float): String = (if (v > 0.004f) "+" else "") + String.format(Locale.US, "%.2f", v)

    /** One field changed: its name and new value; several: the section name. */
    private fun single(section: String, changes: List<Pair<String, String?>>): String? = when (changes.size) {
        0 -> null
        1 -> changes[0].let { (name, value) -> if (value == null) name else "$name $value" }
        else -> section
    }

    private fun film(a: FilmSettings?, b: FilmSettings?): String? {
        if (a == b) return null
        if (b == null) return "Film kaldırıldı"
        if (a == null || a.id != b.id) return "Film: " + (FilmCatalog.find(b.id)?.name ?: b.id)
        return if (a.copy(strength = b.strength) == b) "Film yoğunluğu ${b.strength.roundToInt()}" else "Film ayarı"
    }

    private fun light(a: LightSettings, b: LightSettings): String? = single(
        "Işık",
        listOfNotNull(
            if (a.exposure != b.exposure) "Pozlama" to ev(b.exposure) else null,
            if (a.contrast != b.contrast) "Kontrast" to signed(b.contrast) else null,
            if (a.highlights != b.highlights) "Açık tonlar" to signed(b.highlights) else null,
            if (a.shadows != b.shadows) "Gölgeler" to signed(b.shadows) else null,
            if (a.whites != b.whites) "Beyazlar" to signed(b.whites) else null,
            if (a.blacks != b.blacks) "Siyahlar" to signed(b.blacks) else null,
        ),
    )

    private fun color(a: ColorSettings, b: ColorSettings): String? = single(
        "Renk",
        listOfNotNull(
            if (a.temperature != b.temperature) "Sıcaklık" to signed(b.temperature) else null,
            if (a.tint != b.tint) "Renk tonu" to signed(b.tint) else null,
            if (a.vibrance != b.vibrance) "Canlılık" to signed(b.vibrance) else null,
            if (a.saturation != b.saturation) "Doygunluk" to signed(b.saturation) else null,
        ),
    )

    private fun detail(a: DetailSettings, b: DetailSettings): String? {
        val sharpen = a.sharpening != b.sharpening || a.sharpenRadius != b.sharpenRadius ||
            a.sharpenDetail != b.sharpenDetail || a.sharpenMasking != b.sharpenMasking
        return single(
            "Detay",
            listOfNotNull(
                if (sharpen) "Keskinleştirme" to (if (a.sharpening != b.sharpening) b.sharpening.roundToInt().toString() else null) else null,
                if (a.noiseReduction != b.noiseReduction) "Parazit azaltma" to b.noiseReduction.roundToInt().toString() else null,
                if (a.colorNoiseReduction != b.colorNoiseReduction) "Renk paraziti" to b.colorNoiseReduction.roundToInt().toString() else null,
            ),
        )
    }

    private fun effects(a: EffectsSettings, b: EffectsSettings): String? {
        val vignette = a.vignette != b.vignette || a.vignetteMidpoint != b.vignetteMidpoint ||
            a.vignetteRoundness != b.vignetteRoundness || a.vignetteFeather != b.vignetteFeather ||
            a.vignetteHighlights != b.vignetteHighlights
        val grain = a.grain != b.grain || a.grainSize != b.grainSize || a.grainRoughness != b.grainRoughness
        return single(
            "Efektler",
            listOfNotNull(
                if (a.texture != b.texture) "Doku" to signed(b.texture) else null,
                if (a.clarity != b.clarity) "Netlik" to signed(b.clarity) else null,
                if (a.dehaze != b.dehaze) "Pus giderme" to signed(b.dehaze) else null,
                if (vignette) "Vinyet" to (if (a.vignette != b.vignette) signed(b.vignette) else null) else null,
                if (grain) "Gren" to (if (a.grain != b.grain) b.grain.roundToInt().toString() else null) else null,
            ),
        )
    }

    private fun enhance(a: EnhanceSettings, b: EnhanceSettings): String? = single(
        "Akıllı ayarlar",
        listOfNotNull(
            if (a.accent != b.accent) "Otomatik geliştir" to b.accent.roundToInt().toString() else null,
            if (a.structure != b.structure) "Yapı" to signed(b.structure) else null,
            if (a.sky != b.sky) "Gökyüzü" to b.sky.roundToInt().toString() else null,
            if (a.goldenHour != b.goldenHour) "Altın saat" to b.goldenHour.roundToInt().toString() else null,
            if (a.glow != b.glow) "Işıltı" to b.glow.roundToInt().toString() else null,
            if (a.skin != b.skin) "Cilt yumuşatma" to b.skin.roundToInt().toString() else null,
        ),
    )

    private fun fx(a: List<EffectInstance>, b: List<EffectInstance>): String? {
        if (a == b) return null
        val before = a.associateBy { it.type }
        val after = b.associateBy { it.type }
        val added = after.keys - before.keys
        val removed = before.keys - after.keys
        val changed = after.keys.filter { it in before && before[it] != after[it] }
        val total = added.size + removed.size + changed.size
        return when {
            total > 1 -> "Efektler"
            added.size == 1 -> added.first().title + " eklendi"
            removed.size == 1 -> removed.first().title + " kaldırıldı"
            changed.size == 1 -> changed.first().title
            else -> null
        }
    }

    private fun geometry(a: Geometry, b: Geometry): String? {
        if (a == b) return null
        val names = LinkedHashSet<String>()
        if (a.rotation != b.rotation) names.add("Döndür")
        if (a.flipH != b.flipH || a.flipV != b.flipV) names.add("Çevir")
        val turnedOrFlipped = names.isNotEmpty()
        // Turning and flipping carry straighten/perspective along; those are not separate steps.
        if (!turnedOrFlipped) {
            if (a.straighten != b.straighten) names.add("Düzleştir " + String.format(Locale.US, "%.1f°", b.straighten))
            if (a.vertical != b.vertical || a.horizontal != b.horizontal) names.add("Perspektif")
        }
        // A crop that only followed a rotation or correction is part of that step.
        if (names.isEmpty() && (a.crop != b.crop || a.aspect != b.aspect)) {
            names.add(if (a.aspect != b.aspect) "Kırp " + b.aspect.label else "Kırp")
        }
        return when (names.size) {
            0 -> "Kırp"
            1 -> names.first()
            else -> if (!turnedOrFlipped && a.straighten != b.straighten && (a.vertical != b.vertical || a.horizontal != b.horizontal)) {
                "Perspektif ve düzleştirme"
            } else {
                "Geometri"
            }
        }
    }

    private fun maskName(kind: MaskKind) = when (kind) {
        MaskKind.LINEAR -> "Doğrusal maske"
        MaskKind.RADIAL -> "Dairesel maske"
        MaskKind.PERSON -> "Kişi maskesi"
    }

    private fun masks(a: List<LocalMask>, b: List<LocalMask>): String? {
        if (a == b) return null
        val before = a.associateBy { it.id }
        val after = b.associateBy { it.id }
        val added = after.keys - before.keys
        val removed = before.keys - after.keys
        val changed = after.keys.filter { it in before && before[it] != after[it] }
        return when {
            added.size + removed.size + changed.size > 1 -> "Maskeler"
            added.size == 1 -> maskName(after.getValue(added.first()).kind) + " eklendi"
            removed.size == 1 -> maskName(before.getValue(removed.first()).kind) + " silindi"
            changed.size == 1 -> {
                val m0 = before.getValue(changed.first())
                val m1 = after.getValue(changed.first())
                if (m0.invert != m1.invert) maskName(m1.kind) + " ters çevrildi" else maskName(m1.kind)
            }
            else -> "Maskeler"
        }
    }

    private fun lut(a: EditState, b: EditState): String? = when {
        a.customLut != b.customLut -> if (b.customLut == null) "LUT kaldırıldı" else "LUT: " + b.customLut.removeSuffix(".cube")
        a.customLutStrength != b.customLutStrength -> "LUT yoğunluğu ${b.customLutStrength.roundToInt()}"
        else -> null
    }
}
