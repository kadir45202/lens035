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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.InvertColors
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Rotate90DegreesCcw
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.lens035.core.model.AspectRatio
import com.lens035.core.model.CropRect
import com.lens035.core.model.EditState
import com.lens035.core.model.Geometry
import com.lens035.core.model.GeometryMath
import com.lens035.core.model.LocalAdjust
import com.lens035.core.model.LocalMask
import com.lens035.core.model.MaskKind

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
        geometry = g.copy(
            rotation = ((g.rotation + if (clockwise) 1 else 3) % 4 + 4) % 4,
            crop = rotateCrop(g.crop, clockwise),
            aspect = aspect,
        ),
    )
}

@Composable
fun CropTool(session: EditSession) {
    val g = session.state.geometry
    val (ow, oh) = orientedSize(session)
    var straightenBase by remember { mutableStateOf<CropRect?>(null) }

    fun setAspect(a: AspectRatio) {
        val ratio = GeometryMath.aspectValue(a, ow.toFloat(), oh.toFloat())
        val crop = if (ratio > 0f) {
            GeometryMath.maxCropForAspect(ratio, ow.toFloat(), oh.toFloat(), g.straighten)
        } else {
            GeometryMath.fitCrop(CropRect.FULL, g.straighten, ow.toFloat(), oh.toFloat())
        }
        session.update { it.copy(geometry = it.geometry.copy(aspect = a, crop = crop)) }
    }

    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        ChipRow(AspectRatio.entries, g.aspect, { it.label }, { setAspect(it) })
        Spacer(Modifier.height(4.dp))
        LensSlider(
            label = "Düzleştir",
            value = g.straighten,
            onValueChange = { v ->
                val base = straightenBase ?: g.crop
                val fitted = GeometryMath.fitCrop(base, v, ow.toFloat(), oh.toFloat())
                session.preview(session.state.copy(geometry = session.state.geometry.copy(straighten = v, crop = fitted)))
            },
            min = -45f,
            max = 45f,
            default = 0f,
            format = ValueFormat.DEGREES,
            onGestureStart = {
                session.beginGesture()
                // Remember the crop before straightening so it can grow back.
                straightenBase = if (g.straighten == 0f) g.crop else GeometryMath.maxCropForAspect(
                    GeometryMath.aspectValue(g.aspect, ow.toFloat(), oh.toFloat()).takeIf { it > 0f } ?: (g.crop.width * ow / (g.crop.height * oh)),
                    ow.toFloat(), oh.toFloat(), 0f,
                )
            },
            onGestureEnd = {
                straightenBase = null
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
                    it.copy(geometry = it.geometry.copy(flipH = !it.geometry.flipH, straighten = -it.geometry.straighten, crop = CropRect(1f - c.right, c.top, 1f - c.left, c.bottom)))
                }
            })
            LensIconButton(Icons.Rounded.Flip, "Dikey çevir", {
                session.update {
                    val c = it.geometry.crop
                    it.copy(geometry = it.geometry.copy(flipV = !it.geometry.flipV, straighten = -it.geometry.straighten, crop = CropRect(c.left, 1f - c.bottom, c.right, 1f - c.top)))
                }
            }, modifier = Modifier.rotate(90f))
            LensIconButton(Icons.Rounded.RestartAlt, "Sıfırla", { session.update { it.copy(geometry = Geometry()) } }, tint = LensColors.Muted)
        }
    }
}

// ---------------------------------------------------------------------------- masks

@Composable
fun MaskTool(session: EditSession, selectedId: Long?, onSelect: (Long?) -> Unit) {
    val masks = session.state.masks
    val selected = masks.firstOrNull { it.id == selectedId }

    fun add(kind: MaskKind) {
        if (masks.size >= com.lens035.core.render.ShaderParams.MAX_MASKS) return
        val id = (masks.maxOfOrNull { it.id } ?: 0L) + 1L
        val m = if (kind == MaskKind.LINEAR) LocalMask.newLinear(id) else LocalMask.newRadial(id)
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
                val label = (if (m.kind == MaskKind.LINEAR) "Doğrusal " else "Dairesel ") + (masks.indexOf(m) + 1)
                LensChip(label, m.id == selectedId, { onSelect(m.id) })
            }
            if (masks.size < com.lens035.core.render.ShaderParams.MAX_MASKS) {
                item { GhostPill("Doğrusal", onClick = { add(MaskKind.LINEAR) }, icon = Icons.Rounded.Add) }
                item { GhostPill("Dairesel", onClick = { add(MaskKind.RADIAL) }, icon = Icons.Rounded.Add) }
            }
        }
        if (selected == null) {
            Text(
                if (masks.isEmpty()) "Bölgesel düzenleme için bir maske ekle: gökyüzünü koyulaştır, yüzü aydınlat."
                else "Düzenlemek için bir maske seç.",
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
            if (selected.kind == MaskKind.RADIAL) {
                item { SliderItem("Yumuşaklık", selected.feather, 0f, 100f, 50f, session) { v -> setMask { it.copy(feather = v) } } }
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
