package com.lens035.core.render

import com.lens035.core.analysis.AutoParams
import com.lens035.core.analysis.ImageAnalyzer
import com.lens035.core.analysis.ImageStats
import com.lens035.core.color.ColorMath
import com.lens035.core.color.ToneCurve
import com.lens035.core.film.FilmCatalog
import com.lens035.core.film.FilmRecipe
import com.lens035.core.model.Curves
import com.lens035.core.model.EditState
import com.lens035.core.model.EffectInstance
import com.lens035.core.model.EffectType
import com.lens035.core.model.FilmSettings
import com.lens035.core.model.GeometryMath
import com.lens035.core.model.GradeWheel
import com.lens035.core.model.HueBand
import com.lens035.core.model.MaskKind
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min

/** Sizes and timing of one render. */
data class RenderContext(
    /** Size of the texture the pipeline works on (preview or export resolution). */
    val workWidth: Int,
    val workHeight: Int,
    /** Size of the original image/video frame. */
    val fullWidth: Int,
    val fullHeight: Int,
    /** Output size of the effects pass. */
    val outWidth: Int,
    val outHeight: Int,
    /** Seconds since the start of the clip (video) or 0. */
    val timeSeconds: Float = 0f,
    /** Frame index for per-frame noise in video, 0 for photos. */
    val frameIndex: Int = 0,
    /** The canvas the photo is placed on (the render target's real size), if any. */
    val canvas: CanvasLayout? = null,
)

/** Everything the GL pipeline needs for one frame, derived from an [EditState]. */
class RenderParams(
    val main: UniformSet,
    val effects: UniformSet,
    /** Film LUT to bind, or null when no film is selected. */
    val film: FilmRecipe?,
    /** Whether the colour pipeline needs the curve table at all. */
    val curvesKey: String,
    val curves: Curves,
    /** Whether the effects pass needs the blurred copy of the graded image. */
    val needsBloomPyramid: Boolean,
    /** Date stamp text to rasterise, or null. */
    val dateStamp: DateStampSpec?,
    /** Whether a person segmentation mask should be bound (when the app has one). */
    val needsPersonMask: Boolean = false,
    /** Whether the portrait blur needs its background-only blur pyramid. */
    val needsBokehPyramid: Boolean = false,
    /** Uniforms of the healing pass, or null when nothing is healed. */
    val heal: UniformSet? = null,
    /** Changes whenever the healing changes (cache key for the healed source). */
    val healKey: String = "",
)

data class DateStampSpec(val text: String, val style: Int)

object FilmDefaults {
    /** Maximum black lift (in display units) of the film "fade" control. */
    const val MAX_FADE = 0.15f

    fun settingsFor(recipe: FilmRecipe): FilmSettings = FilmSettings(
        id = recipe.id,
        strength = 100f,
        grain = recipe.grain,
        halation = recipe.halation,
        bloom = recipe.bloom,
        fade = (recipe.fadeBlack / MAX_FADE * 100f).coerceIn(0f, 100f),
        vignette = recipe.vignette,
        warmth = 0f,
    )
}

/**
 * Converts an [EditState] into uniforms for main.frag and effects.frag. This is the single
 * place where UI units become shader units, which keeps the shaders free of magic numbers
 * about the UI and lets the mapping be unit tested.
 */
object ShaderParams {
    const val MAX_MASKS = 4

    /** Healed spots per photo the GPU pass handles. */
    const val MAX_HEALS = 24

    fun build(
        state: EditState,
        ctx: RenderContext,
        stats: ImageStats = ImageStats.NEUTRAL,
        dateText: String = "",
    ): RenderParams {
        val film = FilmCatalog.find(state.film?.id)
        val auto = if (state.enhance.accent > 0f) ImageAnalyzer.autoParams(stats) else AutoParams()
        val a = state.enhance.accent / 100f
        val main = UniformSet()
        buildMain(main, state, ctx, stats, auto, a, film)
        val fx = UniformSet()
        val needsBloom = buildEffects(fx, state, ctx, film)
        val dateFx = state.effect(EffectType.DATE_STAMP)
        return RenderParams(
            main = main,
            effects = fx,
            film = film,
            curvesKey = curvesKey(state.curves),
            curves = state.curves,
            needsBloomPyramid = needsBloom,
            dateStamp = dateFx?.let { DateStampSpec(dateText, it.value(EffectType.DATE_STAMP.param("style")!!).toInt()) },
            needsPersonMask = state.needsPersonMask,
            needsBokehPyramid = state.hasEffect(EffectType.BOKEH),
            heal = buildHeal(state),
            healKey = if (state.heals.isEmpty()) "" else state.heals.take(MAX_HEALS).hashCode().toString(),
        )
    }

    /** Healing pass: destination circle + feather, then source centre, per spot. */
    private fun buildHeal(s: EditState): UniformSet? {
        val spots = s.heals.take(MAX_HEALS)
        if (spots.isEmpty()) return null
        val a = FloatArray(MAX_HEALS * 4)
        val b = FloatArray(MAX_HEALS * 4)
        spots.forEachIndexed { i, h ->
            a[i * 4] = h.x; a[i * 4 + 1] = h.y; a[i * 4 + 2] = h.radius.coerceIn(0.001f, 0.5f); a[i * 4 + 3] = h.feather.coerceIn(0f, 1f)
            b[i * 4] = h.sx; b[i * 4 + 1] = h.sy
        }
        return UniformSet().apply {
            i("u_healCount", spots.size)
            v4a("u_healA", a)
            v4a("u_healB", b)
        }
    }

    private fun buildMain(
        u: UniformSet,
        s: EditState,
        ctx: RenderContext,
        stats: ImageStats,
        auto: AutoParams,
        a: Float,
        film: FilmRecipe?,
    ) {
        val resScale = ctx.workWidth.toFloat() / max(1, ctx.fullWidth).toFloat()
        u.v2("u_texel", 1f / ctx.workWidth, 1f / ctx.workHeight)
        u.f("u_aspect", ctx.workWidth.toFloat() / ctx.workHeight.toFloat())

        val l = s.light
        u.f("u_exposure", (l.exposure + a * auto.exposure).coerceIn(-5f, 5f))
        u.f("u_contrast", norm(l.contrast + a * auto.contrast))
        u.f("u_highlights", norm(l.highlights + a * auto.highlights))
        u.f("u_shadows", norm(l.shadows + a * auto.shadows))
        u.f("u_whites", norm(l.whites + a * auto.whites))
        u.f("u_blacks", norm(l.blacks + a * auto.blacks))

        val c = s.color
        val warmth = (s.film?.warmth ?: 0f) * 0.5f
        val golden = s.enhance.goldenHour / 100f
        u.f("u_temp", norm(c.temperature + a * auto.temperature + warmth))
        u.f("u_tint", norm(c.tint + a * auto.tint))
        u.f("u_vibrance", norm(c.vibrance + a * auto.vibrance))
        u.f("u_saturation", norm(c.saturation))

        val e = s.effects
        u.f("u_texture", norm(e.texture))
        u.f("u_clarity", norm(e.clarity + a * auto.clarity))
        u.f("u_dehaze", norm(e.dehaze + a * auto.dehaze))
        u.v3("u_atmosphere", stats.atmosphere)

        val d = s.detail
        val radiusPx = max(0.5f, d.sharpenRadius * resScale)
        u.v4("u_sharpen", d.sharpening / 100f, radiusPx, d.sharpenDetail / 100f, d.sharpenMasking / 100f)
        u.v2("u_noise", d.noiseReduction / 100f, d.colorNoiseReduction / 100f)

        // Colour mixer: hue shifts up to +-30 degrees, saturation and luminance -1..1.
        val bands = s.mixer.normalized()
        val hue = FloatArray(8) { bands[it].hue / 100f * 30f }
        val sat = FloatArray(8) { bands[it].saturation / 100f }
        val lum = FloatArray(8) { bands[it].luminance / 100f * 0.8f }
        u.v4a("u_mixHue", hue)
        u.v4a("u_mixSat", sat)
        u.v4a("u_mixLum", lum)
        u.f("u_mixEnabled", if (s.mixer.isDefault) 0f else 1f)

        // Colour grading wheels.
        val g = s.grading
        u.v4("u_gradeShadow", wheel(g.shadows))
        u.v4("u_gradeMid", wheel(g.midtones))
        u.v4("u_gradeHigh", wheel(g.highlights))
        u.v4("u_gradeGlobal", wheel(g.global))
        u.v2("u_gradeBalance", g.blending / 100f, g.balance / 100f)
        u.f("u_gradeEnabled", if (g.isDefault) 0f else 1f)

        u.f("u_curvesEnabled", if (s.curves.isDefault) 0f else 1f)

        val fs = s.film
        u.f("u_lutStrength", if (film != null && fs != null) (fs.strength / 100f).coerceIn(0f, 1f) else 0f)
        u.f("u_filmFade", if (film != null && fs != null) (fs.fade / 100f * FilmDefaults.MAX_FADE).coerceIn(0f, FilmDefaults.MAX_FADE) else 0f)
        u.f("u_lut2Strength", if (s.customLut != null) (s.customLutStrength / 100f).coerceIn(0f, 1f) else 0f)

        val en = s.enhance
        u.f("u_structure", (en.structure / 100f + a * 0.15f).coerceIn(0f, 1f))
        u.f("u_sky", en.sky / 100f)
        u.f("u_golden", golden)
        u.f("u_skin", en.skin / 100f)

        // Local masks.
        val masks = s.masks.filter { it.enabled }.take(MAX_MASKS)
        u.i("u_maskCount", masks.size)
        val geo = FloatArray(MAX_MASKS * 8)
        val adj = FloatArray(MAX_MASKS * 12)
        masks.forEachIndexed { i, m ->
            val o = i * 8
            geo[o] = when (m.kind) {
                MaskKind.LINEAR -> 0f
                MaskKind.RADIAL -> 1f
                MaskKind.PERSON -> 2f
            }
            geo[o + 1] = if (m.invert) 1f else 0f
            geo[o + 2] = (m.feather / 100f).coerceIn(0f, 1f)
            geo[o + 3] = m.angle
            geo[o + 4] = m.x0; geo[o + 5] = m.y0; geo[o + 6] = m.x1; geo[o + 7] = m.y1
            val j = i * 12
            val la = m.adjust
            adj[j] = la.exposure.coerceIn(-4f, 4f)
            adj[j + 1] = norm(la.contrast); adj[j + 2] = norm(la.highlights); adj[j + 3] = norm(la.shadows)
            adj[j + 4] = norm(la.whites); adj[j + 5] = norm(la.blacks); adj[j + 6] = norm(la.temperature); adj[j + 7] = norm(la.tint)
            adj[j + 8] = norm(la.saturation); adj[j + 9] = norm(la.clarity); adj[j + 10] = norm(la.dehaze); adj[j + 11] = norm(la.texture)
        }
        u.v4a("u_maskGeo", geo)
        u.v4a("u_maskAdj", adj)
    }

    /** Returns true when the bloom pyramid of the graded image is required. */
    private fun buildEffects(u: UniformSet, s: EditState, ctx: RenderContext, film: FilmRecipe?): Boolean {
        val geometryMatrix = GeometryMath.outputToSourceMatrix(ctx.fullWidth, ctx.fullHeight, s.geometry)
        u.m3("u_geo", geometryMatrix)
        u.v2("u_outSize", ctx.outWidth.toFloat(), ctx.outHeight.toFloat())
        u.v2("u_srcSize", ctx.workWidth.toFloat(), ctx.workHeight.toFloat())
        u.f("u_time", ctx.timeSeconds)
        u.f("u_frameIndex", (ctx.frameIndex % 4096).toFloat())

        val fs = s.film
        val filmOn = film != null && fs != null
        val e = s.effects

        // Vignette: Lightroom post-crop vignette plus the film's own.
        val filmVig = if (filmOn) fs!!.vignette / 100f * 0.8f else 0f
        u.v4(
            "u_vignette",
            (e.vignette / 100f - filmVig).coerceIn(-1f, 1f),
            e.vignetteMidpoint / 100f,
            e.vignetteRoundness / 100f,
            e.vignetteFeather / 100f,
        )
        u.f("u_vignetteHL", e.vignetteHighlights / 100f)

        // Grain: Lightroom grain + film grain + creative grain, mixed by amount.
        val grainFx = s.effect(EffectType.GRAIN)
        val g1 = e.grain / 100f
        val g2 = if (filmOn) fs!!.grain / 100f * 0.85f else 0f
        val g3 = grainFx?.let { it.amount / 100f } ?: 0f
        val gt = g1 + g2 + g3
        val size = if (gt > 0f) {
            (g1 * e.grainSize / 100f + g2 * (film?.grainSize ?: 30f) / 100f + g3 * (grainFx?.v("size") ?: 0.3f)) / gt
        } else 0.3f
        val rough = if (gt > 0f) {
            (g1 * e.grainRoughness / 100f + g2 * (film?.grainRoughness ?: 50f) / 100f + g3 * (grainFx?.v("roughness") ?: 0.5f)) / gt
        } else 0.5f
        val colorGrain = if (gt > 0f && grainFx != null) grainFx.v("color") * g3 / gt else 0f
        u.v4("u_grain", min(gt, 1f), size, rough, colorGrain)

        // Halation: film + creative.
        val halFx = s.effect(EffectType.HALATION)
        val halAmount = (if (filmOn) fs!!.halation / 100f * 0.8f else 0f) + (halFx?.let { it.amount / 100f } ?: 0f)
        val halThreshold = halFx?.v("threshold") ?: 0.70f
        val halRadius = halFx?.v("radius") ?: 0.5f
        val halHue = halFx?.raw("hue") ?: 8f
        u.v4("u_halation", min(halAmount, 1.5f), halThreshold, halRadius, 0f)
        u.v3("u_halationColor", hsv(halHue, 0.9f, 1f))

        // Orton-style glow from the smart tools works on the graded image, so it lives here.
        val orton = s.enhance.glow / 100f
        u.f("u_orton", orton)

        // Glow / bloom: film + creative.
        val glowFx = s.effect(EffectType.GLOW)
        val glowAmount = (if (filmOn) fs!!.bloom / 100f * 0.6f else 0f) + (glowFx?.let { it.amount / 100f } ?: 0f)
        u.v4(
            "u_glow",
            min(glowAmount, 1.5f),
            glowFx?.v("threshold") ?: 0.65f,
            glowFx?.v("radius") ?: 0.6f,
            (glowFx?.raw("warmth") ?: 0f) / 100f,
        )

        fun fx(type: EffectType): EffectInstance? = s.effect(type)
        fun amt(type: EffectType): Float = fx(type)?.let { it.amount / 100f } ?: 0f

        fx(EffectType.MIRROR).let { u.v4("u_fxMirror", amt(EffectType.MIRROR), it?.raw("mode") ?: 0f, 0f, 0f) }
        fx(EffectType.FISHEYE).let { u.v4("u_fxFisheye", amt(EffectType.FISHEYE), it?.v("zoom") ?: 0.3f, 0f, 0f) }
        fx(EffectType.GLITCH).let {
            u.v4("u_fxGlitch", amt(EffectType.GLITCH), it?.v("blocks") ?: 0.5f, it?.v("shift") ?: 0.5f, seed(it))
        }
        fx(EffectType.VHS).let {
            u.v4("u_fxVhs", amt(EffectType.VHS), it?.v("jitter") ?: 0.4f, it?.v("noise") ?: 0.45f, it?.v("bleed") ?: 0.5f)
            u.f("u_fxVhsSeed", seed(it))
        }
        u.v4("u_fxPixel", amt(EffectType.PIXELATE), 0f, 0f, 0f)
        fx(EffectType.ZOOM_BLUR).let {
            u.v4("u_fxZoom", amt(EffectType.ZOOM_BLUR), it?.v("x") ?: 0.5f, it?.v("y") ?: 0.5f, it?.v("clear") ?: 0.3f)
        }
        fx(EffectType.BOKEH).let {
            u.v4("u_fxBokeh", amt(EffectType.BOKEH), it?.v("glow") ?: 0.3f, it?.v("edge") ?: 0.45f, 0f)
        }
        fx(EffectType.TILT_SHIFT).let {
            u.v4(
                "u_fxTilt", amt(EffectType.TILT_SHIFT), it?.v("position") ?: 0.5f, it?.v("width") ?: 0.25f,
                Math.toRadians((it?.raw("angle") ?: 0f).toDouble()).toFloat(),
            )
        }
        fx(EffectType.RGB_SPLIT).let {
            u.v4(
                "u_fxRgb", amt(EffectType.RGB_SPLIT),
                Math.toRadians((it?.raw("angle") ?: 0f).toDouble()).toFloat(), it?.v("radial") ?: 0.3f, 0f,
            )
        }
        fx(EffectType.PRISM).let {
            u.v4(
                "u_fxPrism", amt(EffectType.PRISM), it?.v("spread") ?: 0.4f,
                Math.toRadians((it?.raw("angle") ?: 30f).toDouble()).toFloat(), 0f,
            )
        }
        fx(EffectType.SOFT_FOCUS).let {
            u.v4("u_fxSoft", amt(EffectType.SOFT_FOCUS), it?.v("radius") ?: 0.5f, it?.v("brightness") ?: 0.3f, 0f)
        }
        fx(EffectType.KIRA).let {
            val rays = when ((it?.raw("rays") ?: 0f).toInt()) { 1 -> 6f; 2 -> 8f; else -> 4f }
            u.v4("u_fxKira", amt(EffectType.KIRA), it?.v("threshold") ?: 0.78f, it?.v("size") ?: 0.5f, rays)
            u.f("u_fxKiraAngle", Math.toRadians((it?.raw("angle") ?: 0f).toDouble()).toFloat())
        }
        fx(EffectType.SUN_RAYS).let {
            u.v4("u_fxRays", amt(EffectType.SUN_RAYS), it?.v("x") ?: 0.72f, it?.v("y") ?: 0.12f, it?.v("length") ?: 0.6f)
            u.f("u_fxRaysWarmth", it?.v("warmth") ?: 0.5f)
        }
        fx(EffectType.LIGHT_LEAK).let {
            u.v4("u_fxLeak", amt(EffectType.LIGHT_LEAK), it?.raw("hue") ?: 22f, it?.v("position") ?: 0.3f, it?.v("size") ?: 0.55f)
            u.f("u_fxLeakSeed", seed(it))
        }
        fx(EffectType.RAINBOW).let {
            u.v4(
                "u_fxRainbow", amt(EffectType.RAINBOW),
                Math.toRadians((it?.raw("angle") ?: 45f).toDouble()).toFloat(), it?.v("scale") ?: 0.5f, 0f,
            )
        }
        fx(EffectType.DUOTONE).let {
            u.v4("u_fxDuotone", amt(EffectType.DUOTONE), it?.v("contrast") ?: 0.5f, 0f, 0f)
            u.v3("u_duoShadow", hsv(it?.raw("shadow") ?: 235f, 0.85f, 0.32f))
            u.v3("u_duoHighlight", hsv(it?.raw("highlight") ?: 38f, 0.35f, 1f))
        }
        u.v4("u_fxPoster", amt(EffectType.POSTERIZE), 0f, 0f, 0f)
        fx(EffectType.SCANLINES).let { u.v4("u_fxScan", amt(EffectType.SCANLINES), it?.v("density") ?: 0.5f, 0f, 0f) }
        fx(EffectType.DUST).let {
            u.v4("u_fxDust", amt(EffectType.DUST), it?.v("scratches") ?: 0.4f, it?.v("size") ?: 0.5f, seed(it))
        }

        // Frame: inner photo rectangle and how the photo is fitted into it.
        val frameFx = fx(EffectType.FRAME)
        val layout = FrameLayout.compute(
            ctx.outWidth, ctx.outHeight,
            frameFx?.let { it.amount / 100f } ?: 0f,
            frameFx?.raw("style")?.toInt() ?: 0,
            frameFx != null,
        )
        u.v4("u_frameFx", if (frameFx != null) 1f else 0f, layout.style.toFloat(), layout.cornerRadius, 0f)
        u.v4("u_frameInner", layout.innerX, layout.innerY, layout.innerW, layout.innerH)
        u.v4("u_frameUv", layout.uvScaleX, layout.uvScaleY, layout.uvOffsetX, layout.uvOffsetY)

        // Date stamp: rectangle (inside the photo area) and colour.
        val dateFx = fx(EffectType.DATE_STAMP)
        if (dateFx != null) {
            val style = dateFx.raw("style").toInt()
            val corner = dateFx.raw("corner").toInt()
            val rect = FrameLayout.dateRect(ctx.outWidth, ctx.outHeight, layout, corner)
            u.v4("u_fxDate", dateFx.amount / 100f, style.toFloat(), 0f, 0f)
            u.v4("u_dateRect", rect[0], rect[1], rect[2], rect[3])
            u.v3("u_dateColor", DATE_COLORS[style.coerceIn(0, DATE_COLORS.size - 1)])
        } else {
            u.v4("u_fxDate", 0f, 0f, 0f, 0f)
            u.v4("u_dateRect", 0f, 0f, 0f, 0f)
            u.v3("u_dateColor", DATE_COLORS[0])
        }

        // Canvas: where the photo sits in the larger target, and what fills the rest.
        val canvasFx = fx(EffectType.CANVAS)
        val canvas = ctx.canvas
        val canvasOn = canvasFx != null && canvas != null && !canvas.isNone
        val fill = canvasFx?.raw("fill")?.roundToInt()?.coerceIn(0, CanvasLayout.FILLS.size - 1) ?: 0
        if (canvasOn) {
            u.v4("u_canvas", 1f, fill.toFloat(), (canvasFx!!.v("corner")).coerceIn(0f, 1f), 0f)
            u.v4("u_canvasInner", canvas!!.innerX, canvas.innerY, canvas.innerW, canvas.innerH)
            u.v2("u_canvasSize", canvas.width.toFloat(), canvas.height.toFloat())
        } else {
            u.v4("u_canvas", 0f, 0f, 0f, 0f)
            u.v4("u_canvasInner", 0f, 0f, 1f, 1f)
            u.v2("u_canvasSize", ctx.outWidth.toFloat(), ctx.outHeight.toFloat())
        }
        u.v3("u_canvasColor", CanvasLayout.FILLS[fill])

        return halAmount > 0f || glowAmount > 0f || orton > 0f ||
            s.hasEffect(EffectType.KIRA) || s.hasEffect(EffectType.SOFT_FOCUS) || s.hasEffect(EffectType.TILT_SHIFT) ||
            // The blurred fill is the bloom pyramid's widest level.
            (canvasOn && fill == 0)
    }

    private val DATE_COLORS = arrayOf(
        floatArrayOf(1.0f, 0.56f, 0.16f),
        floatArrayOf(1.0f, 0.24f, 0.16f),
        floatArrayOf(1.0f, 0.83f, 0.25f),
        floatArrayOf(0.97f, 0.96f, 0.93f),
    )

    /** Grading wheel -> (chroma offset rgb, luminance offset) in display units. */
    private fun wheel(w: GradeWheel): FloatArray {
        val col = hsv(w.hue, 1f, 1f)
        val y = ColorMath.luma(col[0], col[1], col[2])
        val s = (w.saturation / 100f).coerceIn(0f, 1f) * 0.22f
        return floatArrayOf((col[0] - y) * s, (col[1] - y) * s, (col[2] - y) * s, (w.luminance / 100f) * 0.22f)
    }

    private fun hsv(h: Float, s: Float, v: Float): FloatArray = FloatArray(3).also { ColorMath.hsvToRgb(h, s, v, it) }

    private fun norm(v: Float): Float = (v / 100f).coerceIn(-1f, 1f)

    private fun seed(fx: EffectInstance?): Float = ((fx?.seed ?: 1) % 997) * 0.6180339f % 1f * 100f

    /** Parameter value normalised to 0..1 over its range. */
    private fun EffectInstance.v(key: String): Float {
        val p = type.param(key) ?: return 0f
        val range = p.max - p.min
        return if (range <= 0f) 0f else (value(p) - p.min) / range
    }

    /** Parameter value in its own units. */
    private fun EffectInstance.raw(key: String): Float {
        val p = type.param(key) ?: return 0f
        return value(p)
    }

    fun curvesKey(c: Curves): String = if (c.isDefault) "identity" else c.hashCode().toString()

    /**
     * Composite curve table, 256 entries x RGBA: R = red∘master, G = green∘master,
     * B = blue∘master, A = master. Values 0..1.
     */
    fun curveTable(c: Curves, size: Int = 256): FloatArray {
        val m = ToneCurve(c.master)
        val r = ToneCurve(c.red)
        val g = ToneCurve(c.green)
        val b = ToneCurve(c.blue)
        val out = FloatArray(size * 4)
        for (i in 0 until size) {
            val x = i / (size - 1).toFloat()
            val mv = m.evaluate(x)
            out[i * 4] = r.evaluate(mv)
            out[i * 4 + 1] = g.evaluate(mv)
            out[i * 4 + 2] = b.evaluate(mv)
            out[i * 4 + 3] = mv
        }
        return out
    }

    /** Bands in shader order, for documentation/tests. */
    val bandOrder: List<HueBand> = HueBand.entries
}
