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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.lens035.app.data.UserPreset
import com.lens035.app.ui.components.ChipRow
import com.lens035.app.ui.components.GhostPill
import com.lens035.app.ui.components.LensChip
import com.lens035.app.ui.components.LensSlider
import com.lens035.app.ui.components.PanelHeader
import com.lens035.app.ui.components.SliderBrushes
import com.lens035.app.ui.components.ToolTile
import com.lens035.app.ui.components.ValueFormat
import com.lens035.app.ui.theme.LensColors
import com.lens035.app.ui.theme.LensType
import com.lens035.core.film.FilmCatalog
import com.lens035.core.film.FilmCategory
import com.lens035.core.model.EditState
import com.lens035.core.model.EffectCategory
import com.lens035.core.model.EffectParam
import com.lens035.core.model.EffectType
import com.lens035.core.presets.Look
import com.lens035.core.presets.Looks
import com.lens035.core.render.FilmDefaults

/** A row of tiles with a little air on both sides. */
@Composable
private fun TileRow(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

// ---------------------------------------------------------------------------- films

private val filmFilters: List<FilmCategory?> = listOf(null) + FilmCategory.entries

@Composable
fun FilmsStrip(session: EditSession, onDetails: () -> Unit) {
    var category by remember { mutableStateOf<FilmCategory?>(null) }
    val selectedId = session.state.film?.id
    val films = FilmCatalog.all.filter { category == null || it.category == category }
    val listState = rememberLazyListState()
    LaunchedEffect(category) { listState.scrollToItem(0) }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        ChipRow(filmFilters, category, { it?.label ?: "Tümü" }, { category = it })
        Spacer(Modifier.height(10.dp))
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "none") {
                ToolTile(
                    title = "Orijinal",
                    image = null,
                    selected = selectedId == null,
                    onClick = { session.update { it.copy(film = null) } },
                    onDetails = null,
                    placeholderIcon = Icons.Rounded.Block,
                )
            }
            items(films, key = { it.id }) { film ->
                val selected = film.id == selectedId
                ToolTile(
                    title = film.name,
                    image = session.filmThumbs[film.id],
                    selected = selected,
                    code = film.code,
                    band = Color(film.swatch.toInt()),
                    onClick = {
                        if (selected) onDetails() else session.update { it.copy(film = FilmDefaults.settingsFor(film)) }
                    },
                    onDetails = onDetails,
                )
            }
        }
    }
}

@Composable
fun FilmDetails(session: EditSession, onDone: () -> Unit) {
    val fs = session.state.film
    val recipe = FilmCatalog.find(fs?.id)
    if (fs == null || recipe == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    fun set(transform: (com.lens035.core.model.FilmSettings) -> com.lens035.core.model.FilmSettings) {
        session.previewUpdate { s -> s.film?.let { s.copy(film = transform(it)) } ?: s }
    }
    Column(Modifier.fillMaxWidth()) {
        PanelHeader(
            title = recipe.name,
            subtitle = "${recipe.code} · ${recipe.category.label} · ISO ${recipe.iso} — ${recipe.description}",
            onReset = { session.update { it.copy(film = FilmDefaults.settingsFor(recipe)) } },
            onDone = onDone,
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
            item { SliderItem("Yoğunluk", fs.strength, 0f, 100f, 100f, session) { v -> set { it.copy(strength = v) } } }
            item { SliderItem("Gren", fs.grain, 0f, 100f, recipe.grain, session) { v -> set { it.copy(grain = v) } } }
            item { SliderItem("Halation", fs.halation, 0f, 100f, recipe.halation, session) { v -> set { it.copy(halation = v) } } }
            item { SliderItem("Parlama", fs.bloom, 0f, 100f, recipe.bloom, session) { v -> set { it.copy(bloom = v) } } }
            item { SliderItem("Solgunluk", fs.fade, 0f, 100f, FilmDefaults.settingsFor(recipe).fade, session) { v -> set { it.copy(fade = v) } } }
            item { SliderItem("Vinyet", fs.vignette, 0f, 100f, recipe.vignette, session) { v -> set { it.copy(vignette = v) } } }
            item {
                SliderItem("Sıcaklık", fs.warmth, -100f, 100f, 0f, session, brush = SliderBrushes.temperature) { v -> set { it.copy(warmth = v) } }
            }
        }
    }
}

/** A slider wired into the session's gesture history. */
@Composable
fun SliderItem(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    default: Float,
    session: EditSession,
    format: ValueFormat = if (min < 0f) ValueFormat.SIGNED else ValueFormat.PLAIN,
    brush: androidx.compose.ui.graphics.Brush? = null,
    onChange: (Float) -> Unit,
) {
    LensSlider(
        label = label,
        value = value,
        onValueChange = onChange,
        min = min,
        max = max,
        default = default,
        format = format,
        trackBrush = brush,
        onGestureStart = { session.beginGesture() },
        onGestureEnd = { session.endGesture() },
    )
}

// ---------------------------------------------------------------------------- effects

private val effectFilters: List<EffectCategory?> = listOf(null) + EffectCategory.entries

/** Effect tiles by category. [exclude] hides effects the medium cannot do (portrait blur in video). */
@Composable
fun EffectsStrip(
    session: EditSession,
    focused: EffectType?,
    exclude: Set<EffectType> = emptySet(),
    onDetails: (EffectType) -> Unit,
) {
    var category by remember { mutableStateOf<EffectCategory?>(null) }
    val types = EffectType.entries.filter { (category == null || it.category == category) && it !in exclude }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        ChipRow(effectFilters, category, { it?.label ?: "Tümü" }, { category = it })
        Spacer(Modifier.height(10.dp))
        TileRow {
            items(types, key = { it.name }) { type ->
                val active = session.state.hasEffect(type)
                ToolTile(
                    title = type.title,
                    image = session.effectThumbs[type],
                    selected = active,
                    active = active,
                    onClick = {
                        if (active) {
                            session.update { it.withoutEffect(type) }
                        } else {
                            session.update { it.withEffect(type.defaultInstance(seedFor(type))) }
                        }
                    },
                    onDetails = { onDetails(type) },
                )
            }
        }
    }
}

private fun seedFor(type: EffectType): Int = if (type.hasSeed) (1..997).random() else 1

@Composable
fun EffectDetails(session: EditSession, type: EffectType, onDone: () -> Unit) {
    val fx = session.state.effect(type)
    if (fx == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    Column(Modifier.fillMaxWidth()) {
        PanelHeader(
            title = type.title,
            subtitle = type.description,
            onReset = { session.update { it.withEffect(type.defaultInstance(fx.seed)) } },
            onDone = onDone,
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
            items(type.params, key = { it.key }) { param ->
                EffectParamControl(param, fx.value(param), session) { v ->
                    session.previewUpdate { s -> s.effect(type)?.let { s.withEffect(it.withValue(param, v)) } ?: s }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (type.hasSeed) {
                        GhostPill("Karıştır", onClick = {
                            session.update { s -> s.effect(type)?.let { s.withEffect(it.copy(seed = (1..997).random())) } ?: s }
                        }, icon = Icons.Rounded.Casino)
                    }
                    GhostPill("Kaldır", onClick = {
                        session.update { it.withoutEffect(type) }
                        onDone()
                    }, icon = Icons.Rounded.DeleteOutline)
                }
            }
        }
    }
}

@Composable
private fun EffectParamControl(param: EffectParam, value: Float, session: EditSession, onChange: (Float) -> Unit) {
    when (param.kind) {
        EffectParam.Kind.CHOICE -> {
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(param.label, style = LensType.Label, color = LensColors.Muted, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(6.dp))
                val index = param.optionIndex(value)
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(param.options.size) { i ->
                        LensChip(param.options[i], i == index, {
                            session.beginGesture()
                            onChange(param.min + i * param.step)
                            session.endGesture()
                        })
                    }
                }
            }
        }
        EffectParam.Kind.HUE -> SliderItem(param.label, value, param.min, param.max, param.default, session, ValueFormat.PLAIN, SliderBrushes.hueFull, onChange)
        EffectParam.Kind.SLIDER -> SliderItem(param.label, value, param.min, param.max, param.default, session, onChange = onChange)
    }
}

// ---------------------------------------------------------------------------- looks

@Composable
fun LooksStrip(
    session: EditSession,
    selectedLook: String?,
    userPresets: List<UserPreset>,
    onSelectLook: (Look) -> Unit,
    onSelectPreset: (UserPreset) -> Unit,
    onPresetDetails: (UserPreset) -> Unit,
    onSavePreset: () -> Unit,
    onDetails: () -> Unit,
) {
    val presetThumbs = remember { mutableStateMapOf<String, ImageBitmap>() }
    LaunchedEffect(userPresets, session.lookThumbs) {
        for (p in userPresets) {
            if (!presetThumbs.containsKey(p.id)) session.renderThumbnail(p.state)?.let { presetThumbs[p.id] = it }
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Tek dokunuşla görünümler", style = LensType.Label, color = LensColors.Muted, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        TileRow {
            item(key = "save") {
                ToolTile(title = "Kaydet", image = null, selected = false, onClick = onSavePreset, onDetails = null, placeholderIcon = Icons.Rounded.Add)
            }
            items(userPresets, key = { "u_" + it.id }) { preset ->
                val selected = selectedLook == "u_" + preset.id
                ToolTile(
                    title = preset.name,
                    image = presetThumbs[preset.id],
                    selected = selected,
                    onClick = { if (selected) onPresetDetails(preset) else onSelectPreset(preset) },
                    onDetails = { onPresetDetails(preset) },
                    placeholderIcon = Icons.Rounded.Person,
                )
            }
            items(Looks.all, key = { it.id }) { look ->
                val selected = selectedLook == look.id
                ToolTile(
                    title = look.name,
                    image = session.lookThumbs[look.id],
                    selected = selected,
                    band = Color(look.swatch.toInt()),
                    onClick = { if (selected) onDetails() else onSelectLook(look) },
                    onDetails = onDetails,
                )
            }
        }
    }
}

@Composable
fun LookDetails(look: Look, strength: Float, session: EditSession, onStrength: (Float) -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        PanelHeader(title = look.name, subtitle = look.description, onReset = { onStrength(100f) }, onDone = onDone)
        SliderItem("Yoğunluk", strength, 0f, 100f, 100f, session) { onStrength(it) }
        Text(
            "Görünüm; film, efekt ve ışık ayarlarını birlikte uygular. Her birini kendi sekmesinden ayrıca değiştirebilirsin.",
            style = LensType.Meta,
            color = LensColors.Faint,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun PresetDetails(preset: UserPreset, onDelete: () -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        PanelHeader(title = preset.name, subtitle = "Kendi ön ayarın", onReset = null, onDone = onDone)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostPill("Ön ayarı sil", onClick = onDelete, icon = Icons.Rounded.DeleteOutline)
        }
        Text(
            "Ön ayarlar kırpma ve maskeler hariç tüm görünümü saklar; başka fotoğraflara tek dokunuşla uygulanır.",
            style = LensType.Meta, color = LensColors.Faint, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }
}

/** Scales a look towards "no look" (strength 0..1), keeping the base edit's geometry and masks. */
fun scaledLook(base: EditState, look: EditState, strength: Float): EditState {
    val k = strength.coerceIn(0f, 1f)
    val l = look.light
    val c = look.color
    val e = look.effects
    val en = look.enhance
    return base.applyLook(
        look.copy(
            film = look.film?.let { it.copy(strength = it.strength * k) },
            light = l.copy(
                exposure = l.exposure * k, contrast = l.contrast * k, highlights = l.highlights * k,
                shadows = l.shadows * k, whites = l.whites * k, blacks = l.blacks * k,
            ),
            color = c.copy(temperature = c.temperature * k, tint = c.tint * k, vibrance = c.vibrance * k, saturation = c.saturation * k),
            effects = e.copy(
                texture = e.texture * k, clarity = e.clarity * k, dehaze = e.dehaze * k,
                vignette = e.vignette * k, grain = e.grain * k,
            ),
            enhance = en.copy(
                accent = en.accent * k, structure = en.structure * k, sky = en.sky * k,
                goldenHour = en.goldenHour * k, glow = en.glow * k, skin = en.skin * k,
            ),
            fx = look.fx.map { fx -> fx.withValue(fx.type.amountParam, fx.amount * k) },
        ),
    )
}
