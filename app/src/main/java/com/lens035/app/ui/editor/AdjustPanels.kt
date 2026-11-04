package com.lens035.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lens035.app.ui.components.ChipRow
import com.lens035.app.ui.components.GhostPill
import com.lens035.app.ui.components.LensChip
import com.lens035.app.ui.components.SliderBrushes
import com.lens035.app.ui.components.ValueFormat
import com.lens035.app.ui.theme.LensColors
import com.lens035.app.ui.theme.LensType
import com.lens035.core.model.ColorSettings
import com.lens035.core.model.CurveChannel
import com.lens035.core.model.DetailSettings
import com.lens035.core.model.EditState
import com.lens035.core.model.EffectsSettings
import com.lens035.core.model.EnhanceSettings
import com.lens035.core.model.GradeRange
import com.lens035.core.model.HueBand
import com.lens035.core.model.LightSettings
import com.lens035.core.model.MixerBand

enum class AdjustPanel(val label: String) {
    SMART("Akıllı"),
    LIGHT("Işık"),
    COLOR("Renk"),
    MIXER("Karışım"),
    GRADING("Renk tonu"),
    CURVE("Eğri"),
    DETAIL("Detay"),
    EFFECTS("Efektler"),
    LUT("LUT"),
}

private val panelHeight = 250.dp

@Composable
fun AdjustTool(
    session: EditSession,
    panel: AdjustPanel,
    onPanel: (AdjustPanel) -> Unit,
    customLuts: List<String>,
    onImportLut: () -> Unit,
    onDeleteLut: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        ChipRow(AdjustPanel.entries, panel, { it.label }, onPanel)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().heightIn(max = panelHeight)) {
            when (panel) {
                AdjustPanel.SMART -> SmartPanel(session)
                AdjustPanel.LIGHT -> LightPanel(session)
                AdjustPanel.COLOR -> ColorPanel(session)
                AdjustPanel.MIXER -> MixerPanel(session)
                AdjustPanel.GRADING -> GradingPanel(session)
                AdjustPanel.CURVE -> CurvePanel(session)
                AdjustPanel.DETAIL -> DetailPanel(session)
                AdjustPanel.EFFECTS -> LrEffectsPanel(session)
                AdjustPanel.LUT -> LutPanel(session, customLuts, onImportLut, onDeleteLut)
            }
        }
    }
}

@Composable
private fun SmartPanel(session: EditSession) {
    val e = session.state.enhance
    fun set(t: (EnhanceSettings) -> EnhanceSettings) = session.previewUpdate { it.copy(enhance = t(it.enhance)) }
    LazyColumn(Modifier.fillMaxWidth()) {
        item { Hint("Fotoğrafını analiz eden tek sürgülü araçlar.") }
        item { SliderItem("Geliştir (otomatik)", e.accent, 0f, 100f, 0f, session) { v -> set { it.copy(accent = v) } } }
        item { SliderItem("Yapı", e.structure, 0f, 100f, 0f, session) { v -> set { it.copy(structure = v) } } }
        item { SliderItem("Gökyüzü", e.sky, 0f, 100f, 0f, session) { v -> set { it.copy(sky = v) } } }
        item { SliderItem("Altın saat", e.goldenHour, 0f, 100f, 0f, session) { v -> set { it.copy(goldenHour = v) } } }
        item { SliderItem("Işıltı", e.glow, 0f, 100f, 0f, session) { v -> set { it.copy(glow = v) } } }
        item { SliderItem("Cilt yumuşatma", e.skin, 0f, 100f, 0f, session) { v -> set { it.copy(skin = v) } } }
    }
}

@Composable
private fun LightPanel(session: EditSession) {
    val l = session.state.light
    fun set(t: (LightSettings) -> LightSettings) = session.previewUpdate { it.copy(light = t(it.light)) }
    LazyColumn(Modifier.fillMaxWidth()) {
        item { SliderItem("Pozlama", l.exposure, -5f, 5f, 0f, session, ValueFormat.EV) { v -> set { it.copy(exposure = v) } } }
        item { SliderItem("Kontrast", l.contrast, -100f, 100f, 0f, session) { v -> set { it.copy(contrast = v) } } }
        item { SliderItem("Açık tonlar", l.highlights, -100f, 100f, 0f, session) { v -> set { it.copy(highlights = v) } } }
        item { SliderItem("Gölgeler", l.shadows, -100f, 100f, 0f, session) { v -> set { it.copy(shadows = v) } } }
        item { SliderItem("Beyazlar", l.whites, -100f, 100f, 0f, session) { v -> set { it.copy(whites = v) } } }
        item { SliderItem("Siyahlar", l.blacks, -100f, 100f, 0f, session) { v -> set { it.copy(blacks = v) } } }
    }
}

@Composable
private fun ColorPanel(session: EditSession) {
    val c = session.state.color
    fun set(t: (ColorSettings) -> ColorSettings) = session.previewUpdate { it.copy(color = t(it.color)) }
    LazyColumn(Modifier.fillMaxWidth()) {
        item { SliderItem("Sıcaklık", c.temperature, -100f, 100f, 0f, session, brush = SliderBrushes.temperature) { v -> set { it.copy(temperature = v) } } }
        item { SliderItem("Renk tonu", c.tint, -100f, 100f, 0f, session, brush = SliderBrushes.tint) { v -> set { it.copy(tint = v) } } }
        item { SliderItem("Canlılık", c.vibrance, -100f, 100f, 0f, session) { v -> set { it.copy(vibrance = v) } } }
        item { SliderItem("Doygunluk", c.saturation, -100f, 100f, 0f, session) { v -> set { it.copy(saturation = v) } } }
    }
}

@Composable
private fun MixerPanel(session: EditSession) {
    var band by remember { mutableStateOf(HueBand.RED) }
    val value = session.state.mixer.band(band)
    fun set(t: (MixerBand) -> MixerBand) = session.previewUpdate { it.copy(mixer = it.mixer.withBand(band, t(it.mixer.band(band)))) }
    Column(Modifier.fillMaxWidth()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(HueBand.entries) { b ->
                val selected = b == band
                val changed = !session.state.mixer.band(b).isDefault
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.hsv(b.centerDegrees, 0.8f, 0.92f))
                        .then(if (selected) Modifier.border(2.dp, LensColors.Text, CircleShape) else Modifier)
                        .clickable(role = Role.RadioButton) { band = b },
                    contentAlignment = Alignment.Center,
                ) {
                    if (changed && !selected) Box(Modifier.size(6.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape))
                }
            }
        }
        Text(band.label, style = LensType.Label, color = LensColors.Muted, modifier = Modifier.padding(horizontal = 20.dp))
        LazyColumn(Modifier.fillMaxWidth()) {
            item { SliderItem("Ton", value.hue, -100f, 100f, 0f, session, brush = SliderBrushes.hueAround(band.centerDegrees)) { v -> set { it.copy(hue = v) } } }
            item { SliderItem("Doygunluk", value.saturation, -100f, 100f, 0f, session, brush = SliderBrushes.saturation(band.centerDegrees)) { v -> set { it.copy(saturation = v) } } }
            item { SliderItem("Parlaklık", value.luminance, -100f, 100f, 0f, session, brush = SliderBrushes.luminance(band.centerDegrees)) { v -> set { it.copy(luminance = v) } } }
        }
    }
}

@Composable
private fun GradingPanel(session: EditSession) {
    var range by remember { mutableStateOf(GradeRange.SHADOWS) }
    val g = session.state.grading
    val wheel = g.wheel(range)
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ChipRow(GradeRange.entries, range, { it.label }, { range = it }, modifier = Modifier.padding(vertical = 4.dp))
        }
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
                ColorWheel(
                    hue = wheel.hue,
                    saturation = wheel.saturation,
                    onChange = { h, s -> session.previewUpdate { st -> st.copy(grading = st.grading.withWheel(range, st.grading.wheel(range).copy(hue = h, saturation = s))) } },
                    onGestureStart = { session.beginGesture() },
                    onGestureEnd = { session.endGesture() },
                )
            }
        }
        item {
            SliderItem("Parlaklık", wheel.luminance, -100f, 100f, 0f, session) { v ->
                session.previewUpdate { st -> st.copy(grading = st.grading.withWheel(range, st.grading.wheel(range).copy(luminance = v))) }
            }
        }
        item { SliderItem("Harmanlama", g.blending, 0f, 100f, 50f, session) { v -> session.previewUpdate { it.copy(grading = it.grading.copy(blending = v)) } } }
        item { SliderItem("Denge", g.balance, -100f, 100f, 0f, session) { v -> session.previewUpdate { it.copy(grading = it.grading.copy(balance = v)) } } }
    }
}

@Composable
private fun CurvePanel(session: EditSession) {
    var channel by remember { mutableStateOf(CurveChannel.MASTER) }
    val color = when (channel) {
        CurveChannel.MASTER -> LensColors.Text
        CurveChannel.RED -> Color(0xFFFF5D52)
        CurveChannel.GREEN -> Color(0xFF4CC584)
        CurveChannel.BLUE -> Color(0xFF4F8BFF)
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ChipRow(CurveChannel.entries, channel, { it.label }, { channel = it }, modifier = Modifier.weight(1f))
            GhostPill("Sıfırla", onClick = {
                session.update { it.copy(curves = it.curves.withPoints(channel, com.lens035.core.color.ToneCurve.IDENTITY_POINTS)) }
            }, modifier = Modifier.padding(end = 12.dp))
        }
        Spacer(Modifier.height(6.dp))
        CurveEditor(
            points = session.state.curves.points(channel),
            color = color,
            histogram = session.stats.histogram,
            onChange = { pts -> session.previewUpdate { it.copy(curves = it.curves.withPoints(channel, pts)) } },
            onGestureStart = { session.beginGesture() },
            onGestureEnd = { session.endGesture() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).heightIn(max = 190.dp),
        )
    }
}

@Composable
private fun DetailPanel(session: EditSession) {
    val d = session.state.detail
    fun set(t: (DetailSettings) -> DetailSettings) = session.previewUpdate { it.copy(detail = t(it.detail)) }
    LazyColumn(Modifier.fillMaxWidth()) {
        item { SliderItem("Keskinleştirme", d.sharpening, 0f, 150f, 0f, session) { v -> set { it.copy(sharpening = v) } } }
        item { SliderItem("Yarıçap", d.sharpenRadius, 0.5f, 3f, 1f, session, ValueFormat.DECIMAL) { v -> set { it.copy(sharpenRadius = v) } } }
        item { SliderItem("Ayrıntı", d.sharpenDetail, 0f, 100f, 25f, session) { v -> set { it.copy(sharpenDetail = v) } } }
        item { SliderItem("Maskeleme", d.sharpenMasking, 0f, 100f, 0f, session) { v -> set { it.copy(sharpenMasking = v) } } }
        item { SliderItem("Parazit azaltma", d.noiseReduction, 0f, 100f, 0f, session) { v -> set { it.copy(noiseReduction = v) } } }
        item { SliderItem("Renk paraziti", d.colorNoiseReduction, 0f, 100f, 0f, session) { v -> set { it.copy(colorNoiseReduction = v) } } }
    }
}

@Composable
private fun LrEffectsPanel(session: EditSession) {
    val e = session.state.effects
    fun set(t: (EffectsSettings) -> EffectsSettings) = session.previewUpdate { it.copy(effects = t(it.effects)) }
    LazyColumn(Modifier.fillMaxWidth()) {
        item { SliderItem("Doku", e.texture, -100f, 100f, 0f, session) { v -> set { it.copy(texture = v) } } }
        item { SliderItem("Netlik", e.clarity, -100f, 100f, 0f, session) { v -> set { it.copy(clarity = v) } } }
        item { SliderItem("Pus giderme", e.dehaze, -100f, 100f, 0f, session) { v -> set { it.copy(dehaze = v) } } }
        item { SectionLabel("Vinyet") }
        item { SliderItem("Miktar", e.vignette, -100f, 100f, 0f, session) { v -> set { it.copy(vignette = v) } } }
        item { SliderItem("Orta nokta", e.vignetteMidpoint, 0f, 100f, 50f, session) { v -> set { it.copy(vignetteMidpoint = v) } } }
        item { SliderItem("Yuvarlaklık", e.vignetteRoundness, -100f, 100f, 0f, session) { v -> set { it.copy(vignetteRoundness = v) } } }
        item { SliderItem("Yumuşaklık", e.vignetteFeather, 0f, 100f, 50f, session) { v -> set { it.copy(vignetteFeather = v) } } }
        item { SliderItem("Açık tonları koru", e.vignetteHighlights, 0f, 100f, 0f, session) { v -> set { it.copy(vignetteHighlights = v) } } }
        item { SectionLabel("Gren") }
        item { SliderItem("Miktar", e.grain, 0f, 100f, 0f, session) { v -> set { it.copy(grain = v) } } }
        item { SliderItem("Boyut", e.grainSize, 0f, 100f, 25f, session) { v -> set { it.copy(grainSize = v) } } }
        item { SliderItem("Pürüz", e.grainRoughness, 0f, 100f, 50f, session) { v -> set { it.copy(grainRoughness = v) } } }
    }
}

@Composable
private fun LutPanel(session: EditSession, luts: List<String>, onImport: () -> Unit, onDelete: (String) -> Unit) {
    val current = session.state.customLut
    LazyColumn(Modifier.fillMaxWidth()) {
        item { Hint("Kendi .cube LUT dosyalarını içe aktar; filmlerin üzerine uygulanır.") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { LensChip("Yok", current == null, { session.update { it.copy(customLut = null) } }) }
                items(luts) { name ->
                    LensChip(name.removeSuffix(".cube"), current == name, { session.update { it.copy(customLut = name, customLutStrength = 100f) } })
                }
            }
        }
        if (current != null) {
            item { SliderItem("Yoğunluk", session.state.customLutStrength, 0f, 100f, 100f, session) { v -> session.previewUpdate { it.copy(customLutStrength = v) } } }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostPill("LUT içe aktar", onClick = onImport, icon = Icons.Rounded.FileOpen)
                if (current != null) {
                    GhostPill("Sil", onClick = {
                        session.update { it.copy(customLut = null) }
                        onDelete(current)
                    }, icon = Icons.Rounded.DeleteOutline)
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = LensType.Meta, color = LensColors.Faint, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = LensType.Label, color = LensColors.Text, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 2.dp))
}

/** Whether any setting of a panel differs from neutral (used for the dot on panel chips). */
fun AdjustPanel.isChanged(s: EditState): Boolean = when (this) {
    AdjustPanel.SMART -> !s.enhance.isDefault
    AdjustPanel.LIGHT -> !s.light.isDefault
    AdjustPanel.COLOR -> !s.color.isDefault
    AdjustPanel.MIXER -> !s.mixer.isDefault
    AdjustPanel.GRADING -> !s.grading.isDefault
    AdjustPanel.CURVE -> !s.curves.isDefault
    AdjustPanel.DETAIL -> !s.detail.isDefault
    AdjustPanel.EFFECTS -> !s.effects.isDefault
    AdjustPanel.LUT -> s.customLut != null
}
