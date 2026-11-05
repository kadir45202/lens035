package com.lens035.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.InvertColors
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Rotate90DegreesCcw
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.lens035.app.ui.components.ChipRow
import com.lens035.app.ui.components.GhostPill
import com.lens035.app.ui.components.LensChip
import com.lens035.app.ui.components.LensIconButton
import com.lens035.app.ui.components.LensSlider
import com.lens035.app.ui.components.ValueFormat
import com.lens035.app.ui.theme.LensColors
import com.lens035.app.ui.theme.LensType
import com.lens035.core.analysis.Upright
import com.lens035.core.model.AspectRatio
import com.lens035.core.model.CropRect
import com.lens035.core.model.EditState
import com.lens035.core.model.Geometry
import com.lens035.core.model.GeometryMath
import com.lens035.core.model.LocalAdjust
import com.lens035.core.model.LocalMask
import com.lens035.core.model.MaskKind
import com.lens035.core.model.MaskRange
import com.lens035.core.model.RangeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Oriented frame size of the preview for the current geometry. */
fun orientedSize(session: EditSession): Pair<Int, Int> {
    val o = GeometryMath.orientedSize(session.frameWidth, session.frameHeight, session.state.geometry)
    return o.width to o.height
}

/** Rotates the crop rectangle a quarter turn with the image. */
private fun rotateCrop(c: CropRect, clockwise: Boolean): CropRect =
    if (clockwise) CropRect(1f - c.bottom, c.left, 1f - c.top, c.right)
    else CropRect(c.top, 1f - c.right, c.bottom, 1f - c.left)

fun rotateQuarter(state: EditState, clockwise: Boolean): EditState {
    val g = state.geometry
    val aspect = when (g.aspect) {
        AspectRatio.FREE, AspectRatio.ORIGINAL -> g.aspect
        else -> AspectRatio.FREE
    }
    return state.copy(
        geometry = g.rotatedCorrection(clockwise).copy(
            rotation = ((g.rotation + if (clockwise) 1 else 3) % 4 + 4) % 4,
            crop = rotateCrop(g.crop, clockwise),
            aspect = aspect,
        ),
    )
}

/** The fine corrections the crop tool's slider can drive. */
private enum class Correction(val label: String, val min: Float, val max: Float, val format: ValueFormat) {
    STRAIGHTEN("Düzleştir", -45f, 45f, ValueFormat.DEGREES),
    VERTICAL("Dikey", -100f, 100f, ValueFormat.SIGNED),
    HORIZONTAL("Yatay", -100f, 100f, ValueFormat.SIGNED),
    ;

    fun valueOf(g: Geometry): Float = when (this) {
        STRAIGHTEN -> g.straighten
        VERTICAL -> g.vertical
        HORIZONTAL -> g.horizontal
    }

    fun set(g: Geometry, v: Float): Geometry = when (this) {
        STRAIGHTEN -> g.copy(straighten = v)
        VERTICAL -> g.copy(vertical = v)
        HORIZONTAL -> g.copy(horizontal = v)
    }
}

private fun nearlySame(a: CropRect, b: CropRect) =
    kotlin.math.abs(a.left - b.left) + kotlin.math.abs(a.top - b.top) + kotlin.math.abs(a.right - b.right) + kotlin.math.abs(a.bottom - b.bottom) < 0.01f

/**
 * Aspect, straighten, perspective, quarter turns and flips. [onGuides] reports when a correction
 * slider is being dragged so the canvas can show alignment guides.
 */
@Composable
fun CropTool(session: EditSession, onGuides: (Boolean) -> Unit = {}) {
    val g = session.state.geometry
    val (ow, oh) = orientedSize(session)
    val owf = ow.toFloat()
    val ohf = oh.toFloat()
    var correction by remember { mutableStateOf(Correction.STRAIGHTEN) }
    // How the crop follows a correction drag, decided when the drag starts: an untouched crop
    // stays the largest one that fits, a hand-made crop keeps its framing and only shrinks.
    var dragBase by remember { mutableStateOf<CropRect?>(null) }
    var dragKeepsLargest by remember { mutableStateOf(false) }
    var dragAspect by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    var autoBusy by remember { mutableStateOf(false) }
    var autoMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(autoMessage) {
        if (autoMessage != null) {
            delay(2600)
            autoMessage = null
        }
    }

    fun cropAspect(geo: Geometry): Float =
        GeometryMath.aspectValue(geo.aspect, owf, ohf).takeIf { it > 0f } ?: (geo.crop.width * owf / (geo.crop.height * ohf))

    /** Levels the picture and fixes converging verticals from the straight lines it finds. */
    fun autoUpright() {
        if (autoBusy) return
        autoBusy = true
        scope.launch {
            val px = session.analysisPixels()
            val geo = session.state.geometry
            val result = px?.let { withContext(Dispatchers.Default) { Upright.analyze(it.argb, it.width, it.height, geo) } }
            autoBusy = false
            when {
                result == null -> autoMessage = "Belirgin düz çizgi bulunamadı."
                result.isNoChange && !geo.hasCorrection -> autoMessage = "Zaten düz görünüyor."
                else -> {
                    session.update { st ->
                        val ng = st.geometry.copy(straighten = result.straighten, vertical = result.vertical, horizontal = 0f)
                        val ratio = GeometryMath.aspectValue(ng.aspect, owf, ohf)
                        st.copy(geometry = ng.copy(crop = GeometryMath.maxCropForAspect(ratio, owf, ohf, ng)))
                    }
                    correction = if (result.vertical != 0f) Correction.VERTICAL else Correction.STRAIGHTEN
                    autoMessage = if (result.isNoChange) "Düzeltmeler sıfırlandı." else null
                }
            }
        }
    }

    fun setAspect(a: AspectRatio) {
        val ratio = GeometryMath.aspectValue(a, owf, ohf)
        val crop = GeometryMath.maxCropForAspect(ratio, owf, ohf, g)
        session.update { it.copy(geometry = it.geometry.copy(aspect = a, crop = crop)) }
    }

    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        ChipRow(AspectRatio.entries, g.aspect, { it.label }, { setAspect(it) })
        Spacer(Modifier.height(2.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item {
                GhostPill(
                    if (autoBusy) "Bakılıyor…" else "Otomatik",
                    onClick = { autoUpright() },
                    icon = Icons.Rounded.AutoFixHigh,
                    enabled = !autoBusy,
                )
            }
            items(Correction.entries) { c ->
                val v = c.valueOf(g)
                val text = if (v == 0f) c.label else c.label + " " + com.lens035.app.ui.components.formatValue(v, c.format)
                LensChip(text, c == correction, { correction = c })
            }
        }
        autoMessage?.let {
            Text(it, style = LensType.Meta, color = LensColors.Faint, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        }
        LensSlider(
            label = correction.label,
            value = correction.valueOf(g),
            onValueChange = { v ->
                val cur = session.state.geometry
                val next = correction.set(cur, v)
                val crop = if (dragKeepsLargest) {
                    GeometryMath.maxCropForAspect(dragAspect, owf, ohf, next)
                } else {
                    GeometryMath.fitCrop(dragBase ?: cur.crop, next, owf, ohf)
                }
                session.preview(session.state.copy(geometry = next.copy(crop = crop)))
            },
            min = correction.min,
            max = correction.max,
            default = 0f,
            format = correction.format,
            onGestureStart = {
                session.beginGesture()
                val cur = session.state.geometry
                dragAspect = cropAspect(cur)
                dragKeepsLargest = nearlySame(cur.crop, GeometryMath.maxCropForAspect(dragAspect, owf, ohf, cur))
                dragBase = cur.crop
                onGuides(true)
            },
            onGestureEnd = {
                dragBase = null
                dragKeepsLargest = false
                onGuides(false)
                session.endGesture()
            },
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LensIconButton(Icons.Rounded.Rotate90DegreesCcw, "Sola döndür", { session.update { rotateQuarter(it, false) } })
            LensIconButton(Icons.Rounded.Rotate90DegreesCw, "Sağa döndür", { session.update { rotateQuarter(it, true) } })
            LensIconButton(Icons.Rounded.Flip, "Yatay çevir", {
                session.update {
                    val c = it.geometry.crop
                    it.copy(geometry = it.geometry.mirroredCorrectionH().copy(flipH = !it.geometry.flipH, crop = CropRect(1f - c.right, c.top, 1f - c.left, c.bottom)))
                }
            })
            LensIconButton(Icons.Rounded.Flip, "Dikey çevir", {
                session.update {
                    val c = it.geometry.crop
                    it.copy(geometry = it.geometry.mirroredCorrectionV().copy(flipV = !it.geometry.flipV, crop = CropRect(c.left, 1f - c.bottom, c.right, 1f - c.top)))
                }
            }, modifier = Modifier.rotate(90f))
            LensIconButton(Icons.Rounded.RestartAlt, "Sıfırla", { session.update { it.copy(geometry = Geometry()) } }, tint = LensColors.Muted)
        }
    }
}

// ---------------------------------------------------------------------------- masks

/** Display name of a mask in the chip row: kind plus its position. */
fun maskLabel(m: LocalMask, index: Int): String = when (m.kind) {
    MaskKind.LINEAR -> "Doğrusal"
    MaskKind.RADIAL -> "Dairesel"
    MaskKind.PERSON -> "Kişi"
} + " " + (index + 1)

private fun rangeLabel(mode: RangeMode): String = when (mode) {
    RangeMode.NONE -> "Tümü"
    RangeMode.LUMINANCE -> "Parlaklık"
    RangeMode.COLOR -> "Renk"
}

/** One line on the state of person detection, or null when there is nothing to say. */
fun personStatusText(status: PersonStatus?): String? = when (status) {
    PersonStatus.SEARCHING, PersonStatus.UNKNOWN -> "Kişi aranıyor…"
    PersonStatus.NOT_FOUND -> "Fotoğrafta kişi bulunamadı"
    PersonStatus.FAILED -> "Kişi algılanamadı"
    PersonStatus.FOUND, null -> null
}

/**
 * Local adjustments. [personStatus] is null where person masks are not available (video);
 * otherwise it is the progress of person detection for the current photo.
 */
@Composable
fun MaskTool(session: EditSession, selectedId: Long?, onSelect: (Long?) -> Unit, personStatus: PersonStatus? = null) {
    val masks = session.state.masks
    val selected = masks.firstOrNull { it.id == selectedId }

    fun add(kind: MaskKind) {
        if (masks.size >= com.lens035.core.render.ShaderParams.MAX_MASKS) return
        val id = (masks.maxOfOrNull { it.id } ?: 0L) + 1L
        val m = when (kind) {
            MaskKind.LINEAR -> LocalMask.newLinear(id)
            MaskKind.RADIAL -> LocalMask.newRadial(id)
            MaskKind.PERSON -> LocalMask.newPerson(id)
        }
        session.update { it.copy(masks = it.masks + m) }
        onSelect(id)
    }

    fun setMask(t: (LocalMask) -> LocalMask) {
        val id = selected?.id ?: return
        session.previewUpdate { s -> s.copy(masks = s.masks.map { if (it.id == id) t(it) else it }) }
    }

    fun setAdjust(t: (LocalAdjust) -> LocalAdjust) = setMask { it.copy(adjust = t(it.adjust)) }

    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(masks, key = { it.id }) { m ->
                LensChip(maskLabel(m, masks.indexOf(m)), m.id == selectedId, { onSelect(m.id) })
            }
            if (masks.size < com.lens035.core.render.ShaderParams.MAX_MASKS) {
                if (personStatus != null) {
                    item {
                        GhostPill(
                            "Kişi",
                            onClick = { add(MaskKind.PERSON) },
                            icon = Icons.Rounded.Person,
                            enabled = personStatus != PersonStatus.NOT_FOUND && personStatus != PersonStatus.FAILED,
                        )
                    }
                }
                item { GhostPill("Doğrusal", onClick = { add(MaskKind.LINEAR) }, icon = Icons.Rounded.Add) }
                item { GhostPill("Dairesel", onClick = { add(MaskKind.RADIAL) }, icon = Icons.Rounded.Add) }
            }
        }
        if (selected == null) {
            val text = when {
                masks.isNotEmpty() -> "Düzenlemek için bir maske seç."
                personStatus == PersonStatus.NOT_FOUND || personStatus == PersonStatus.FAILED ->
                    "Bölgesel düzenleme için bir maske ekle. " + personStatusText(personStatus) + ", doğrusal ya da dairesel maske kullanabilirsin."
                personStatus != null -> "Bölgesel düzenleme için bir maske ekle: kişiyi aydınlat, gökyüzünü koyulaştır."
                else -> "Bölgesel düzenleme için bir maske ekle: gökyüzünü koyulaştır, yüzü aydınlat."
            }
            Text(
                text,
                style = LensType.Meta,
                color = LensColors.Faint,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            return
        }
        val a = selected.adjust
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 230.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostPill(if (selected.invert) "Ters çevrildi" else "Ters çevir", onClick = {
                        session.update { s -> s.copy(masks = s.masks.map { if (it.id == selected.id) it.copy(invert = !it.invert) else it }) }
                    }, icon = Icons.Rounded.InvertColors)
                    GhostPill("Sil", onClick = {
                        session.update { s -> s.copy(masks = s.masks.filter { it.id != selected.id }) }
                        onSelect(null)
                    }, icon = Icons.Rounded.DeleteOutline)
                }
            }
            if (selected.kind == MaskKind.PERSON) {
                personStatusText(personStatus)?.let { msg ->
                    item {
                        Text(msg, style = LensType.Meta, color = LensColors.Faint, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    }
                }
                item { SliderItem("Kenar yumuşaklığı", selected.feather, 0f, 100f, 40f, session) { v -> setMask { it.copy(feather = v) } } }
            }
            if (selected.kind == MaskKind.RADIAL) {
                item { SliderItem("Yumuşaklık", selected.feather, 0f, 100f, 50f, session) { v -> setMask { it.copy(feather = v) } } }
            }
            // Range: limit the mask to a brightness band or a colour (Lightroom's range mask).
            item {
                Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text("Aralık", style = LensType.Label, color = LensColors.Muted, modifier = Modifier.padding(start = 20.dp, bottom = 4.dp))
                    ChipRow(RangeMode.entries, selected.range.mode, { rangeLabel(it) }, { mode ->
                        session.update { s -> s.copy(masks = s.masks.map { if (it.id == selected.id) it.copy(range = it.range.copy(mode = mode)) else it }) }
                    })
                }
            }
            val range = selected.range
            fun setRange(t: (MaskRange) -> MaskRange) = setMask { it.copy(range = t(it.range)) }
            when (range.mode) {
                RangeMode.LUMINANCE -> {
                    item { SliderItem("Alt sınır", range.low, 0f, 100f, 0f, session, ValueFormat.PLAIN) { v -> setRange { it.copy(low = v) } } }
                    item { SliderItem("Üst sınır", range.high, 0f, 100f, 100f, session, ValueFormat.PLAIN) { v -> setRange { it.copy(high = v) } } }
                    item { SliderItem("Aralık yumuşaklığı", range.softness, 0f, 100f, 50f, session, ValueFormat.PLAIN) { v -> setRange { it.copy(softness = v) } } }
                }
                RangeMode.COLOR -> {
                    item {
                        SliderItem("Ton", range.hue, 0f, 360f, 210f, session, ValueFormat.PLAIN, brush = com.lens035.app.ui.components.SliderBrushes.hueFull) { v ->
                            setRange { it.copy(hue = v) }
                        }
                    }
                    item { SliderItem("Tolerans", range.tolerance, 0f, 100f, 30f, session, ValueFormat.PLAIN) { v -> setRange { it.copy(tolerance = v) } } }
                    item { SliderItem("Aralık yumuşaklığı", range.softness, 0f, 100f, 50f, session, ValueFormat.PLAIN) { v -> setRange { it.copy(softness = v) } } }
                }
                RangeMode.NONE -> Unit
            }
            item { SliderItem("Pozlama", a.exposure, -4f, 4f, 0f, session, ValueFormat.EV) { v -> setAdjust { it.copy(exposure = v) } } }
            item { SliderItem("Kontrast", a.contrast, -100f, 100f, 0f, session) { v -> setAdjust { it.copy(contrast = v) } } }
            item { SliderItem("Açık tonlar", a.highlights, -100f, 100f, 0f, session) { v -> setAdjust { it.copy(highlights = v) } } }
            item { SliderItem("Gölgeler", a.shadows, -100f, 100f, 0f, session) { v -> setAdjust { it.copy(shadows = v) } } }
            item { SliderItem("Beyazlar", a.whites, -100f, 100f, 0f, session) { v -> setAdjust { it.copy(whites = v) } } }
            item { SliderItem("Siyahlar", a.blacks, -100f, 100f, 0f, session) { v -> setAdjust { it.copy(blacks = v) } } }
            item { SliderItem("Sıcaklık", a.temperature, -100f, 100f, 0f, session, brush = com.lens035.app.ui.components.SliderBrushes.temperature) { v -> setAdjust { it.copy(temperature = v) } } }
            item { SliderItem("Renk tonu", a.tint, -100f, 100f, 0f, session, brush = com.lens035.app.ui.components.SliderBrushes.tint) { v -> setAdjust { it.copy(tint = v) } } }
            item { SliderItem("Doygunluk", a.saturation, -100f, 100f, 0f, session) { v -> setAdjust { it.copy(saturation = v) } } }
            item { SliderItem("Netlik", a.clarity, -100f, 100f, 0f, session) { v -> setAdjust { it.copy(clarity = v) } } }
            item { SliderItem("Pus giderme", a.dehaze, -100f, 100f, 0f, session) { v -> setAdjust { it.copy(dehaze = v) } } }
            item { SliderItem("Doku", a.texture, -100f, 100f, 0f, session) { v -> setAdjust { it.copy(texture = v) } } }
        }
    }
}
