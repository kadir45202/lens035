package com.lens035.app.ui.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lens035.app.media.ImageFormat
import com.lens035.app.ui.components.ChipRow
import com.lens035.app.ui.components.GhostPill
import com.lens035.app.ui.components.LensSheet
import com.lens035.app.ui.components.PrimaryPill
import com.lens035.app.ui.theme.LensColors
import com.lens035.app.ui.theme.LensType

sealed interface SaveState {
    data object Idle : SaveState
    data object Working : SaveState
    data class Done(val uri: Uri) : SaveState
    data class Failed(val message: String) : SaveState
}

enum class Quality(val label: String, val value: Int) {
    HIGH("En yüksek", 97),
    STANDARD("Yüksek", 90),
    COMPACT("Küçük dosya", 80),
}

@Composable
fun ExportSheet(
    visible: Boolean,
    options: ExportOptions,
    quality: Quality,
    sourceWidth: Int,
    sourceHeight: Int,
    saveState: SaveState,
    onOptions: (ExportOptions) -> Unit,
    onQuality: (Quality) -> Unit,
    onSave: () -> Unit,
    onShare: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    LensSheet(visible, onDismiss, dismissible = saveState !is SaveState.Working) {
        Text("Dışa aktar", style = LensType.Heading, color = LensColors.Text, modifier = Modifier.padding(horizontal = 20.dp))
        val longest = maxOf(sourceWidth, sourceHeight)
        if (longest > 0) {
            Text(
                "Orijinal: $sourceWidth × $sourceHeight px",
                style = LensType.Meta,
                color = LensColors.Faint,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        when (saveState) {
            is SaveState.Done -> {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = LensColors.Positive, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text("Galeriye kaydedildi", style = LensType.Title, color = LensColors.Text)
                        Text("Resimler › Lens035", style = LensType.Meta, color = LensColors.Faint)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostPill("Paylaş", onClick = { onShare(saveState.uri) }, icon = Icons.Rounded.Share)
                    PrimaryPill("Tamam", onClick = onDismiss)
                }
            }
            SaveState.Working -> {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = LensColors.Accent, strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.size(14.dp))
                    Text("Tam çözünürlükte işleniyor…", style = LensType.Body, color = LensColors.Text)
                }
            }
            else -> {
                SectionTitle("Boyut")
                ChipRow(ExportSize.entries, options.size, { sizeLabel(it, longest) }, { onOptions(options.copy(size = it)) })
                SectionTitle("Biçim")
                ChipRow(ImageFormat.entries, options.format, { it.label }, { onOptions(options.copy(format = it)) })
                if (options.format == ImageFormat.JPEG) {
                    SectionTitle("Kalite")
                    ChipRow(Quality.entries, quality, { it.label }, onQuality)
                }
                if (saveState is SaveState.Failed) {
                    Text(saveState.message, style = LensType.Label, color = LensColors.Danger, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
                Spacer(Modifier.height(16.dp))
                PrimaryPill("Galeriye kaydet", onClick = onSave, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            }
        }
    }
}

private fun sizeLabel(s: ExportSize, longest: Int): String =
    if (s == ExportSize.ORIGINAL && longest > 0) "Orijinal (${minOf(longest, s.maxSide)} px)" else s.label

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = LensType.Label, color = LensColors.Muted, modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 6.dp))
}

fun shareMedia(context: Context, uri: Uri, mime: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Paylaş").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
