package com.lens035.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** A tunable parameter of a creative effect. The first parameter of every effect is its amount. */
data class EffectParam(
    val key: String,
    val label: String,
    val min: Float,
    val max: Float,
    val default: Float,
    /** Step for discrete parameters (e.g. style selectors); 0 = continuous. */
    val step: Float = 0f,
    /** Optional names for discrete values, indexed from [min]. */
    val options: List<String> = emptyList(),
    val kind: Kind = Kind.SLIDER,
) {
    enum class Kind { SLIDER, HUE, CHOICE }

    fun clamp(v: Float): Float {
        val c = v.coerceIn(min, max)
        return if (step > 0f) (min + ((c - min) / step).roundToInt() * step).coerceIn(min, max) else c
    }

    fun optionIndex(v: Float): Int = ((clamp(v) - min) / (if (step > 0f) step else 1f)).roundToInt()
}

enum class EffectCategory(val label: String) {
    FILM("Film"),
    LIGHT("Işık"),
    RETRO("Retro"),
    LENS("Lens"),
    COLOR("Renk"),
    FRAME("Çerçeve"),
}

private fun amount(default: Float = 60f) = EffectParam("amount", "Yoğunluk", 0f, 100f, default)
private fun p(key: String, label: String, default: Float, min: Float = 0f, max: Float = 100f) =
    EffectParam(key, label, min, max, default)
private fun hue(key: String, label: String, default: Float) =
    EffectParam(key, label, 0f, 360f, default, kind = EffectParam.Kind.HUE)
private fun choice(key: String, label: String, options: List<String>, default: Int = 0) =
    EffectParam(key, label, 0f, (options.size - 1).toFloat(), default.toFloat(), step = 1f, options = options, kind = EffectParam.Kind.CHOICE)

/**
 * The creative effects (Prequel-style). The shader implements each one in effects.frag; the
 * parameter keys here are the contract with [com.lens035.core.render.ShaderParams].
 * The enum order is also the order effects are applied.
 */
@Serializable
enum class EffectType(
    val title: String,
    val category: EffectCategory,
    val description: String,
    val params: List<EffectParam>,
    /** Whether the effect has a random layout that can be re-rolled. */
    val hasSeed: Boolean = false,
) {
    @SerialName("mirror")
    MIRROR(
        "Ayna", EffectCategory.LENS, "Görüntüyü ortadan yansıtır.",
        listOf(amount(100f), choice("mode", "Yön", listOf("Sol → Sağ", "Sağ → Sol", "Üst → Alt", "Kaleydoskop"))),
    ),

    @SerialName("fisheye")
    FISHEYE(
        "Balıkgözü", EffectCategory.LENS, "Geniş açılı, bombeli lens.",
        listOf(amount(55f), p("zoom", "Yakınlaştırma", 30f)),
    ),

    @SerialName("glitch")
    GLITCH(
        "Glitch", EffectCategory.RETRO, "Dijital bozulma blokları.",
        listOf(amount(50f), p("blocks", "Blok", 50f), p("shift", "Renk kayması", 50f)),
        hasSeed = true,
    ),

    @SerialName("vhs")
    VHS(
        "VHS", EffectCategory.RETRO, "Eski video kaseti görüntüsü.",
        listOf(amount(65f), p("jitter", "Titreme", 40f), p("noise", "Parazit", 45f), p("bleed", "Renk taşması", 50f)),
        hasSeed = true,
    ),

    @SerialName("pixelate")
    PIXELATE(
        "Piksel", EffectCategory.RETRO, "Mozaik piksel blokları.",
        listOf(amount(35f)),
    ),

    @SerialName("zoom_blur")
    ZOOM_BLUR(
        "Zoom", EffectCategory.LENS, "Merkezden dışa hareket bulanıklığı.",
        listOf(amount(40f), p("x", "Merkez X", 50f), p("y", "Merkez Y", 50f), p("clear", "Net alan", 30f)),
    ),

    @SerialName("tilt_shift")
    TILT_SHIFT(
        "Tilt-Shift", EffectCategory.LENS, "Minyatür etkisi için seçici odak.",
        listOf(amount(70f), p("position", "Konum", 50f), p("width", "Net bant", 25f), p("angle", "Açı", 0f, -90f, 90f)),
    ),

    @SerialName("rgb_split")
    RGB_SPLIT(
        "RGB Kayma", EffectCategory.RETRO, "Renk kanallarını ayırır.",
        listOf(amount(40f), hue("angle", "Yön", 0f), p("radial", "Kenarlara doğru", 30f)),
    ),

    @SerialName("prism")
    PRISM(
        "Prizma", EffectCategory.LIGHT, "Gökkuşağı kenarlı hayalet yansımalar.",
        listOf(amount(50f), p("spread", "Yayılma", 40f), hue("angle", "Açı", 30f)),
    ),

    @SerialName("soft_focus")
    SOFT_FOCUS(
        "Rüya", EffectCategory.LENS, "Yumuşak odak ve hafif parlama.",
        listOf(amount(50f), p("radius", "Yarıçap", 50f), p("brightness", "Parlaklık", 30f)),
    ),

    @SerialName("glow")
    GLOW(
        "Parıltı", EffectCategory.LIGHT, "Parlak alanlardan yayılan ışık.",
        listOf(amount(50f), p("threshold", "Eşik", 60f), p("radius", "Yarıçap", 60f), p("warmth", "Sıcaklık", 0f, -100f, 100f)),
    ),

    @SerialName("halation")
    HALATION(
        "Halation", EffectCategory.FILM, "Işık kaynaklarının etrafında kırmızı film halesi.",
        listOf(amount(55f), p("threshold", "Eşik", 70f), p("radius", "Yarıçap", 50f), hue("hue", "Renk", 8f)),
    ),

    @SerialName("kira")
    KIRA(
        "Kira", EffectCategory.LIGHT, "Parlak noktalarda yıldız ışıltıları.",
        listOf(
            amount(60f), p("threshold", "Eşik", 78f), p("size", "Boyut", 50f),
            choice("rays", "Işın", listOf("4", "6", "8")), p("angle", "Açı", 0f, 0f, 90f),
        ),
    ),

    @SerialName("sun_rays")
    SUN_RAYS(
        "Güneş Işını", EffectCategory.LIGHT, "Bir noktadan süzülen ışık huzmeleri.",
        listOf(amount(60f), p("x", "Konum X", 72f), p("y", "Konum Y", 12f), p("length", "Uzunluk", 60f), p("warmth", "Sıcaklık", 50f)),
    ),

    @SerialName("light_leak")
    LIGHT_LEAK(
        "Işık Sızıntısı", EffectCategory.FILM, "Filme sızan sıcak ışık.",
        listOf(amount(60f), hue("hue", "Renk", 22f), p("position", "Konum", 30f), p("size", "Boyut", 55f)),
        hasSeed = true,
    ),

    @SerialName("rainbow")
    RAINBOW(
        "Holo", EffectCategory.LIGHT, "Holografik gökkuşağı yansıması.",
        listOf(amount(35f), hue("angle", "Açı", 45f), p("scale", "Ölçek", 50f)),
    ),

    @SerialName("duotone")
    DUOTONE(
        "Duotone", EffectCategory.COLOR, "İki renkli baskı görünümü.",
        listOf(amount(100f), hue("shadow", "Gölge rengi", 235f), hue("highlight", "Işık rengi", 38f), p("contrast", "Kontrast", 50f)),
    ),

    @SerialName("posterize")
    POSTERIZE(
        "Poster", EffectCategory.COLOR, "Sınırlı ton sayısıyla baskı.",
        listOf(amount(50f)),
    ),

    @SerialName("scanlines")
    SCANLINES(
        "CRT", EffectCategory.RETRO, "Tüplü ekran tarama çizgileri.",
        listOf(amount(50f), p("density", "Sıklık", 50f)),
    ),

    @SerialName("dust")
    DUST(
        "Toz", EffectCategory.FILM, "Tozlu, çizik film karesi.",
        listOf(amount(60f), p("scratches", "Çizikler", 40f), p("size", "Boyut", 50f)),
        hasSeed = true,
    ),

    @SerialName("grain")
    GRAIN(
        "Grain", EffectCategory.FILM, "Analog film greni.",
        listOf(amount(45f), p("size", "Boyut", 30f), p("roughness", "Pürüz", 50f), p("color", "Renkli gren", 0f)),
    ),

    @SerialName("date_stamp")
    DATE_STAMP(
        "Tarih", EffectCategory.FRAME, "Eski kompakt makine tarih damgası.",
        listOf(
            amount(100f), choice("style", "Renk", listOf("Turuncu", "Kırmızı", "Sarı", "Beyaz")),
            choice("corner", "Köşe", listOf("Sağ alt", "Sol alt", "Sağ üst", "Sol üst")),
        ),
    ),

    @SerialName("frame")
    FRAME(
        "Çerçeve", EffectCategory.FRAME, "Anlık fotoğraf ve film çerçeveleri.",
        listOf(
            EffectParam("amount", "Kalınlık", 0f, 100f, 50f),
            choice("style", "Stil", listOf("Anlık", "İnce beyaz", "Siyah film", "Yuvarlak köşe")),
        ),
    );

    val amountParam: EffectParam get() = params.first()

    fun param(key: String): EffectParam? = params.firstOrNull { it.key == key }

    fun defaultInstance(seed: Int = 1): EffectInstance = EffectInstance(this, emptyMap(), seed)
}
