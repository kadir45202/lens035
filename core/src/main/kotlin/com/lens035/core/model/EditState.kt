package com.lens035.core.model

import com.lens035.core.color.CurvePoint
import com.lens035.core.color.ToneCurve
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The complete, immutable description of an edit. Every screen reads from and writes to one
 * EditState; undo/redo, saved projects, user presets and copy/paste all serialise this class.
 *
 * Values are stored in the units the UI shows (Lightroom conventions): exposure in EV,
 * most sliders in -100..100 or 0..100. Conversion to shader units happens in
 * [com.lens035.core.render.ShaderParams].
 */

@Serializable
data class LightSettings(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
) {
    val isDefault: Boolean get() = this == LightSettings()
}

@Serializable
data class ColorSettings(
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val vibrance: Float = 0f,
    val saturation: Float = 0f,
) {
    val isDefault: Boolean get() = this == ColorSettings()
}

/** One hue band of the colour mixer. All values -100..100. */
@Serializable
data class MixerBand(
    val hue: Float = 0f,
    val saturation: Float = 0f,
    val luminance: Float = 0f,
) {
    val isDefault: Boolean get() = hue == 0f && saturation == 0f && luminance == 0f
}

/** The eight colour-mixer bands, in the order the UI and the shader use. */
enum class HueBand(val label: String, val centerDegrees: Float) {
    RED("Kırmızı", 0f),
    ORANGE("Turuncu", 30f),
    YELLOW("Sarı", 60f),
    GREEN("Yeşil", 120f),
    AQUA("Camgöbeği", 180f),
    BLUE("Mavi", 240f),
    PURPLE("Mor", 270f),
    MAGENTA("Eflatun", 300f),
}

@Serializable
data class ColorMixer(
    val bands: List<MixerBand> = List(HueBand.entries.size) { MixerBand() },
) {
    val isDefault: Boolean get() = bands.all { it.isDefault }

    fun band(band: HueBand): MixerBand = bands.getOrElse(band.ordinal) { MixerBand() }

    fun withBand(band: HueBand, value: MixerBand): ColorMixer {
        val list = normalized().toMutableList()
        list[band.ordinal] = value
        return ColorMixer(list)
    }

    /** Older or hand-edited files may carry fewer bands; always expose exactly eight. */
    fun normalized(): List<MixerBand> =
        List(HueBand.entries.size) { bands.getOrElse(it) { MixerBand() } }
}

/** A colour-grading wheel: hue 0..360, saturation 0..100, luminance -100..100. */
@Serializable
data class GradeWheel(
    val hue: Float = 0f,
    val saturation: Float = 0f,
    val luminance: Float = 0f,
) {
    val isDefault: Boolean get() = saturation == 0f && luminance == 0f
}

enum class GradeRange(val label: String) {
    SHADOWS("Gölgeler"),
    MIDTONES("Orta tonlar"),
    HIGHLIGHTS("Açık tonlar"),
    GLOBAL("Genel"),
}

@Serializable
data class ColorGrading(
    val shadows: GradeWheel = GradeWheel(),
    val midtones: GradeWheel = GradeWheel(),
    val highlights: GradeWheel = GradeWheel(),
    val global: GradeWheel = GradeWheel(),
    /** 0..100, how much the three ranges overlap. */
    val blending: Float = 50f,
    /** -100..100, shifts the split point between shadows and highlights. */
    val balance: Float = 0f,
) {
    val isDefault: Boolean
        get() = shadows.isDefault && midtones.isDefault && highlights.isDefault && global.isDefault

    fun wheel(range: GradeRange): GradeWheel = when (range) {
        GradeRange.SHADOWS -> shadows
        GradeRange.MIDTONES -> midtones
        GradeRange.HIGHLIGHTS -> highlights
        GradeRange.GLOBAL -> global
    }

    fun withWheel(range: GradeRange, wheel: GradeWheel): ColorGrading = when (range) {
        GradeRange.SHADOWS -> copy(shadows = wheel)
        GradeRange.MIDTONES -> copy(midtones = wheel)
        GradeRange.HIGHLIGHTS -> copy(highlights = wheel)
        GradeRange.GLOBAL -> copy(global = wheel)
    }
}

enum class CurveChannel(val label: String) {
    MASTER("RGB"),
    RED("Kırmızı"),
    GREEN("Yeşil"),
    BLUE("Mavi"),
}

@Serializable
data class Curves(
    val master: List<CurvePoint> = ToneCurve.IDENTITY_POINTS,
    val red: List<CurvePoint> = ToneCurve.IDENTITY_POINTS,
    val green: List<CurvePoint> = ToneCurve.IDENTITY_POINTS,
    val blue: List<CurvePoint> = ToneCurve.IDENTITY_POINTS,
) {
    val isDefault: Boolean
        get() = ToneCurve.isIdentity(master) && ToneCurve.isIdentity(red) &&
            ToneCurve.isIdentity(green) && ToneCurve.isIdentity(blue)

    fun points(channel: CurveChannel): List<CurvePoint> = when (channel) {
        CurveChannel.MASTER -> master
        CurveChannel.RED -> red
        CurveChannel.GREEN -> green
        CurveChannel.BLUE -> blue
    }

    fun withPoints(channel: CurveChannel, points: List<CurvePoint>): Curves = when (channel) {
        CurveChannel.MASTER -> copy(master = points)
        CurveChannel.RED -> copy(red = points)
        CurveChannel.GREEN -> copy(green = points)
        CurveChannel.BLUE -> copy(blue = points)
    }
}

@Serializable
data class DetailSettings(
    /** 0..150 */
    val sharpening: Float = 0f,
    /** 0.5..3.0 pixels */
    val sharpenRadius: Float = 1f,
    /** 0..100, higher keeps more fine detail (less halo damping) */
    val sharpenDetail: Float = 25f,
    /** 0..100, higher restricts sharpening to edges */
    val sharpenMasking: Float = 0f,
    /** 0..100 */
    val noiseReduction: Float = 0f,
    /** 0..100 */
    val colorNoiseReduction: Float = 0f,
) {
    val isDefault: Boolean get() = sharpening == 0f && noiseReduction == 0f && colorNoiseReduction == 0f
}

/** Lightroom's "Effects" panel. */
@Serializable
data class EffectsSettings(
    val texture: Float = 0f,
    val clarity: Float = 0f,
    val dehaze: Float = 0f,
    /** -100..100, negative darkens corners */
    val vignette: Float = 0f,
    val vignetteMidpoint: Float = 50f,
    /** -100..100 */
    val vignetteRoundness: Float = 0f,
    val vignetteFeather: Float = 50f,
    val vignetteHighlights: Float = 0f,
    val grain: Float = 0f,
    val grainSize: Float = 25f,
    val grainRoughness: Float = 50f,
) {
    val isDefault: Boolean
        get() = texture == 0f && clarity == 0f && dehaze == 0f && vignette == 0f && grain == 0f
}

/** Smart one-slider tools inspired by Luminar. All 0..100. */
@Serializable
data class EnhanceSettings(
    /** Image-aware auto enhance (exposure, tone, colour and local contrast). */
    val accent: Float = 0f,
    /** Edge-aware local contrast that brings out detail without halos. */
    val structure: Float = 0f,
    /** Deepens and clarifies blue skies. */
    val sky: Float = 0f,
    /** Warm, low sun light in highlights and midtones. */
    val goldenHour: Float = 0f,
    /** Orton style dreamy glow. */
    val glow: Float = 0f,
    /** Smooths skin tones while keeping edges. */
    val skin: Float = 0f,
) {
    val isDefault: Boolean get() = this == EnhanceSettings()
}

/**
 * The selected film stock and the user's fine-tuning of it. Values 0..100 except warmth
 * (-100..100). Created from the recipe defaults when a film is picked.
 */
@Serializable
data class FilmSettings(
    val id: String,
    val strength: Float = 100f,
    val grain: Float = 0f,
    val halation: Float = 0f,
    val bloom: Float = 0f,
    val fade: Float = 0f,
    val vignette: Float = 0f,
    val warmth: Float = 0f,
)

/** A creative effect in the stack. [params] holds values keyed by [EffectParam.key]. */
@Serializable
data class EffectInstance(
    val type: EffectType,
    val params: Map<String, Float> = emptyMap(),
    val seed: Int = 1,
) {
    fun value(param: EffectParam): Float = params[param.key] ?: param.default

    fun withValue(param: EffectParam, value: Float): EffectInstance =
        copy(params = params + (param.key to param.clamp(value)))

    val amount: Float get() = value(type.params.first())
}

@Serializable
data class LocalAdjust(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val saturation: Float = 0f,
    val clarity: Float = 0f,
    val dehaze: Float = 0f,
    val texture: Float = 0f,
) {
    val isDefault: Boolean get() = this == LocalAdjust()
}

@Serializable
enum class MaskKind {
    @SerialName("linear") LINEAR,
    @SerialName("radial") RADIAL,

    /** The person found by on-device segmentation (photos only). Geometry fields are unused. */
    @SerialName("person") PERSON,
}

/**
 * A local adjustment mask. Geometry is in normalised SOURCE image coordinates (x right, y
 * down, both 0..1) so it stays attached to the content when the crop changes.
 *
 * Linear: (x0, y0) is where the effect is full, (x1, y1) where it has faded out.
 * Radial: (x0, y0) is the centre, x1/y1 are the radii in units of image height, [angle] in
 * radians, [feather] 0..100 softens the edge inwards.
 */
@Serializable
data class LocalMask(
    val id: Long,
    val kind: MaskKind,
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
    val angle: Float = 0f,
    val feather: Float = 50f,
    val invert: Boolean = false,
    val enabled: Boolean = true,
    val adjust: LocalAdjust = LocalAdjust(),
) {
    companion object {
        fun newLinear(id: Long) = LocalMask(
            id = id, kind = MaskKind.LINEAR,
            x0 = 0.5f, y0 = 0.15f, x1 = 0.5f, y1 = 0.55f,
            adjust = LocalAdjust(exposure = -0.5f),
        )

        fun newRadial(id: Long) = LocalMask(
            id = id, kind = MaskKind.RADIAL,
            x0 = 0.5f, y0 = 0.5f, x1 = 0.28f, y1 = 0.28f, feather = 60f,
            adjust = LocalAdjust(exposure = 0.4f),
        )

        fun newPerson(id: Long) = LocalMask(
            id = id, kind = MaskKind.PERSON,
            x0 = 0f, y0 = 0f, x1 = 0f, y1 = 0f, feather = 40f,
            adjust = LocalAdjust(exposure = 0.25f, shadows = 15f),
        )
    }
}

@Serializable
data class EditState(
    val version: Int = CURRENT_VERSION,
    val film: FilmSettings? = null,
    val light: LightSettings = LightSettings(),
    val color: ColorSettings = ColorSettings(),
    val mixer: ColorMixer = ColorMixer(),
    val grading: ColorGrading = ColorGrading(),
    val curves: Curves = Curves(),
    val detail: DetailSettings = DetailSettings(),
    val effects: EffectsSettings = EffectsSettings(),
    val enhance: EnhanceSettings = EnhanceSettings(),
    val fx: List<EffectInstance> = emptyList(),
    val geometry: Geometry = Geometry(),
    val masks: List<LocalMask> = emptyList(),
    /** Optional imported .cube look, referenced by file name in the app's LUT folder. */
    val customLut: String? = null,
    val customLutStrength: Float = 100f,
) {
    val isDefault: Boolean get() = this.copy(version = CURRENT_VERSION) == EditState()

    fun effect(type: EffectType): EffectInstance? = fx.firstOrNull { it.type == type }

    fun hasEffect(type: EffectType): Boolean = fx.any { it.type == type }

    fun withEffect(instance: EffectInstance): EditState {
        val list = fx.filter { it.type != instance.type } + instance
        return copy(fx = list.sortedBy { it.type.ordinal })
    }

    fun withoutEffect(type: EffectType): EditState = copy(fx = fx.filter { it.type != type })

    /** The look-only part of an edit, used for presets and "paste settings". */
    fun lookOnly(): EditState = copy(geometry = Geometry(), masks = emptyList())

    /** Applies a preset/look on top of this edit while keeping crop and masks. */
    fun applyLook(look: EditState): EditState = look.copy(geometry = geometry, masks = masks)

    /** True when the edit uses the on-device person segmentation (person mask, portrait blur, skin). */
    val needsPersonMask: Boolean
        get() = hasEffect(EffectType.BOKEH) || enhance.skin > 0f ||
            masks.any { it.enabled && it.kind == MaskKind.PERSON }

    /** Drops the photo-only features (person masks, portrait blur) for video rendering. */
    fun forVideo(): EditState =
        if (!hasEffect(EffectType.BOKEH) && masks.none { it.kind == MaskKind.PERSON }) this
        else withoutEffect(EffectType.BOKEH).copy(masks = masks.filter { it.kind != MaskKind.PERSON })

    companion object {
        const val CURRENT_VERSION = 1
    }
}
