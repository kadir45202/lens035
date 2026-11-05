package com.lens035.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lens035.app.ui.components.GhostPill
import com.lens035.app.ui.components.ValueFormat
import com.lens035.app.ui.theme.LensColors
import com.lens035.app.ui.theme.LensType
import com.lens035.core.model.HealSpot

/** Brush size for new spots, as a fraction of the photo's shorter side. */
const val DEFAULT_HEAL_RADIUS = 0.03f

/**
 * Retouch panel. With a spot selected the sliders edit that spot; otherwise "Fırça boyutu" sets
 * the size of the next one. [onRepick] asks for another clean source for a spot.
 */
@Composable
fun RetouchTool(
    session: EditSession,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    brush: Float,
    onBrush: (Float) -> Unit,
    onRepick: (HealSpot) -> Unit,
) {
    val heals = session.state.heals
    val selected = heals.firstOrNull { it.id == selectedId }

    fun editSelected(t: (HealSpot) -> HealSpot) {
        val id = selected?.id ?: return
        session.previewUpdate { s -> s.copy(heals = s.heals.map { if (it.id == id) t(it) else it }) }
    }

    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(
            when {
                heals.isEmpty() -> "Lekeye dokun; yakındaki temiz doku ile kapatılır. Küçük lekeler için iki parmakla yakınlaştır."
                selected != null -> "Kesikli daireyi sürükleyerek dokunun nereden alınacağını değiştir."
                else -> "${heals.size} leke giderildi. Düzenlemek için birine dokun, yeni leke için boş yere dokun."
            },
            style = LensType.Meta,
            color = LensColors.Faint,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        // The slider works in thousandths of the shorter side: 5 .. 100 (0.5% .. 10%).
        SliderItem("Fırça boyutu", (selected?.radius ?: brush) * 1000f, 5f, 100f, DEFAULT_HEAL_RADIUS * 1000f, session, ValueFormat.PLAIN) { v ->
            if (selected != null) editSelected { it.copy(radius = v / 1000f) } else onBrush(v / 1000f)
        }
        if (selected != null) {
            SliderItem("Yumuşaklık", selected.feather * 100f, 0f, 100f, 50f, session, ValueFormat.PLAIN) { v ->
                editSelected { it.copy(feather = v / 100f) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostPill("Başka kaynak", onClick = { onRepick(selected) }, icon = Icons.Rounded.AutoFixHigh)
                GhostPill("Sil", onClick = {
                    session.update { s -> s.copy(heals = s.heals.filter { it.id != selected.id }) }
                    onSelect(null)
                }, icon = Icons.Rounded.DeleteOutline)
            }
        } else if (heals.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                GhostPill("Tümünü temizle", onClick = {
                    session.update { it.copy(heals = emptyList()) }
                    onSelect(null)
                }, icon = Icons.Rounded.DeleteOutline)
            }
        }
    }
}
