package com.lens035.core.film

import com.lens035.core.color.CurvePoint

enum class FilmCategory(val label: String) {
    NEGATIVE("Renkli negatif"),
    SLIDE("Diya"),
    CINEMA("Sinema"),
    INSTANT("Anlık"),
    MONO("Siyah beyaz"),
    CREATIVE("Deneysel"),
}

/** A split-tone tint: hue in degrees, amount 0..1 (display-space chroma added). */
data class Tint(val hue: Float, val amount: Float)

/** Black-and-white conversion: channel weights (normalised internally) and optional toning. */
data class Mono(
    val r: Float,
    val g: Float,
    val b: Float,
    val toneHue: Float = 0f,
    val toneAmount: Float = 0f,
)

/**
 * A parametric, display-referred film model. [FilmLutBuilder] evaluates it on a 3D grid; the
 * shader then applies the resulting LUT with the user's strength. Physical effects (grain,
 * halation, bloom, vignette) are not baked into the LUT; their defaults live here and are
 * rendered by the effects pass so the user can tune them.
 *
 * Hue/sat/lum shifts are indexed like [com.lens035.core.model.HueBand]:
 * red, orange, yellow, green, aqua, blue, purple, magenta.
 */
data class FilmRecipe(
    val id: String,
    val name: String,
    val category: FilmCategory,
    val description: String,
    /** Identity colour of the stock (ARGB), shown as the band under the thumbnail. */
    val swatch: Long,
    val iso: Int,
    val exposure: Float = 0f,
    val temp: Float = 0f,
    val tint: Float = 0f,
    /** Row-major 3x3 linear-light channel mix, or null for identity. */
    val matrix: FloatArray? = null,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val curve: List<CurvePoint>? = null,
    val curveR: List<CurvePoint>? = null,
    val curveG: List<CurvePoint>? = null,
    val curveB: List<CurvePoint>? = null,
    /** Per-band hue shift in degrees. */
    val hueShift: FloatArray = FloatArray(8),
    /** Per-band saturation scale, -1..1. */
    val satShift: FloatArray = FloatArray(8),
    /** Per-band luminance shift, -1..1 (stops at full saturation). */
    val lumShift: FloatArray = FloatArray(8),
    val shadowTint: Tint? = null,
    val highlightTint: Tint? = null,
    /** Output black lift, 0..0.25. */
    val fadeBlack: Float = 0f,
    /** Output white reduction, 0..0.25. */
    val fadeWhite: Float = 0f,
    val mono: Mono? = null,
    // Physical-effect defaults, 0..100.
    val grain: Float = 0f,
    val grainSize: Float = 30f,
    val grainRoughness: Float = 50f,
    val halation: Float = 0f,
    val bloom: Float = 0f,
    val vignette: Float = 0f,
) {
    val code: String get() = id

    override fun equals(other: Any?): Boolean = other is FilmRecipe && other.id == id
    override fun hashCode(): Int = id.hashCode()
}

/** Builder helpers to keep the catalogue readable. */
internal fun pts(vararg xy: Float): List<CurvePoint> {
    require(xy.size % 2 == 0)
    return (xy.indices step 2).map { CurvePoint(xy[it], xy[it + 1]) }
}

/** Band array from named entries: r, o, y, g, a, b, p, m. */
internal fun bands(
    r: Float = 0f, o: Float = 0f, y: Float = 0f, g: Float = 0f,
    a: Float = 0f, b: Float = 0f, p: Float = 0f, m: Float = 0f,
): FloatArray = floatArrayOf(r, o, y, g, a, b, p, m)
