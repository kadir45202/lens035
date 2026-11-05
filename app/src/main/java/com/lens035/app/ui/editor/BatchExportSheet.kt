package com.lens035.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lens035.app.ui.components.GhostPill
import com.lens035.app.ui.components.LensSheet
import com.lens035.app.ui.components.PrimaryPill
import com.lens035.app.ui.theme.LensColors
import com.lens035.app.ui.theme.LensType

/** Where a batch export stands. */
sealed interface BatchUi {
    data class Ready(val count: Int) : BatchUi
    data class Running(val saved: Int, val failed: Int, val total: Int) : BatchUi
    data class Finished(val saved: Int, val failed: Int, val stopped: Boolean) : BatchUi
}

/**
 * Applying the current edit to other photos: confirm, progress (stoppable), result.
 * [summary] describes the output, e.g. "JPEG · Yüksek · Orijinal".
 */
@Composable
fun BatchExportSheet(ui: BatchUi?, summary: String, onStart: () -> Unit, onStop: () -> Unit, onDismiss: () -> Unit) {
    LensSheet(visible = ui != null, onDismiss = onDismiss, dismissible = ui !is BatchUi.Running) {
        Text("Başka fotoğraflara uygula", style = LensType.Heading, color = LensColors.Text, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(6.dp))
        when (ui) {
            is BatchUi.Ready -> {
                Text(
                    "${ui.count} fotoğraf seçildi. Bu düzenleme, kırpma ve maskeler hariç, her birine ayrı ayrı uygulanıp galeriye kaydedilecek. Asıl fotoğraflar değişmez.",
                    style = LensType.Body,
                    color = LensColors.Muted,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Text(summary, style = LensType.Meta, color = LensColors.Faint, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryPill("Başlat", onClick = onStart)
                    GhostPill("Vazgeç", onClick = onDismiss)
                }
            }
            is BatchUi.Running -> {
                val done = ui.saved + ui.failed
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("İşleniyor… $done / ${ui.total}", style = LensType.Body, color = LensColors.Text)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { if (ui.total > 0) done / ui.total.toFloat() else 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = LensColors.Accent,
                        trackColor = LensColors.Active,
                    )
                    Spacer(Modifier.height(14.dp))
                    GhostPill("Durdur", onClick = onStop)
                }
            }
            is BatchUi.Finished -> {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = if (ui.saved > 0) LensColors.Positive else LensColors.Faint, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(
                            if (ui.saved > 0) "${ui.saved} fotoğraf kaydedildi" else "Fotoğraf kaydedilmedi",
                            style = LensType.Title,
                            color = LensColors.Text,
                        )
                        val notes = buildList {
                            if (ui.stopped) add("Durduruldu")
                            if (ui.failed > 0) add("${ui.failed} fotoğraf işlenemedi")
                            if (ui.saved > 0) add("Resimler › Lens035")
                        }
                        if (notes.isNotEmpty()) Text(notes.joinToString(" · "), style = LensType.Meta, color = LensColors.Faint)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    PrimaryPill("Tamam", onClick = onDismiss)
                }
            }
            null -> Unit
        }
    }
}
