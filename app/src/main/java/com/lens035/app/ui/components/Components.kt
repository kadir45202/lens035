package com.lens035.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lens035.app.ui.theme.LensColors
import com.lens035.app.ui.theme.LensShapes
import com.lens035.app.ui.theme.LensType

/** Plain icon button with a 44dp target. */
@Composable
fun LensIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LensColors.Text,
    size: Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (enabled) tint else LensColors.Outline, modifier = Modifier.size(22.dp))
    }
}

/** Filled accent pill (the one primary action on a screen). */
@Composable
fun PrimaryPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(LensShapes.Pill)
            .background(if (enabled) LensColors.Accent else LensColors.Active)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = LensType.BodyStrong, color = if (enabled) LensColors.AccentInk else LensColors.Faint)
    }
}

/** Outlined neutral pill. */
@Composable
fun GhostPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(LensShapes.Pill)
            .border(BorderStroke(1.dp, LensColors.Outline), LensShapes.Pill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) LensColors.Text else LensColors.Faint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = LensType.Label, color = if (enabled) LensColors.Text else LensColors.Faint)
    }
}

/** A selectable text chip; selection is shown by brightness and a filled background. */
@Composable
fun LensChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(LensShapes.Pill)
            .background(if (selected) LensColors.Active else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = LensType.Label, color = if (selected) LensColors.Text else LensColors.Faint, maxLines = 1)
    }
}

@Composable
fun <T> ChipRow(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(items) { item -> LensChip(label(item), item == selected, { onSelect(item) }) }
    }
}

/**
 * Thumbnail tile used for looks, films and effects. Tapping applies; when [selected], a small
 * round arrow appears in the corner that opens the fine-tuning panel.
 */
@Composable
fun ToolTile(
    title: String,
    image: ImageBitmap?,
    selected: Boolean,
    onClick: () -> Unit,
    onDetails: (() -> Unit)?,
    modifier: Modifier = Modifier,
    code: String? = null,
    band: Color? = null,
    active: Boolean = selected,
    placeholderIcon: ImageVector? = null,
) {
    Column(modifier = modifier.width(68.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = 68.dp, height = 84.dp)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(LensShapes.Thumb)
                    .background(LensColors.Raised)
                    .then(if (selected) Modifier.border(2.dp, LensColors.Accent, LensShapes.Thumb) else Modifier)
                    .clickable(role = Role.Button, onClick = onClick),
            ) {
                if (image != null) {
                    Image(image, title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else if (placeholderIcon != null) {
                    Icon(placeholderIcon, null, tint = LensColors.Faint, modifier = Modifier.align(Alignment.Center).size(26.dp))
                }
                if (band != null || code != null) {
                    Row(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (band != null) {
                            Box(Modifier.size(width = 3.dp, height = 10.dp).background(band, LensShapes.Small))
                            Spacer(Modifier.width(4.dp))
                        }
                        if (code != null) {
                            Text(code, style = LensType.Code, color = LensColors.Text, maxLines = 1)
                        }
                    }
                }
                if (active && !selected) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(7.dp)
                            .background(LensColors.Accent, CircleShape),
                    )
                }
            }
            if (selected && onDetails != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(LensColors.Accent)
                        .clickable(role = Role.Button, onClick = onDetails),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.KeyboardArrowUp, "Ayrıntılar", tint = LensColors.AccentInk, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            style = LensType.Meta,
            color = if (selected || active) LensColors.Text else LensColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Header of the fine-tuning panel: title, optional subtitle, reset and done. */
@Composable
fun PanelHeader(
    title: String,
    subtitle: String?,
    onReset: (() -> Unit)?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = LensType.Title, color = LensColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = LensType.Meta, color = LensColors.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onReset != null) LensIconButton(Icons.Rounded.Refresh, "Sıfırla", onReset, tint = LensColors.Muted)
        LensIconButton(Icons.Rounded.Check, "Tamam", onDone, tint = LensColors.Accent)
    }
}

/** Bottom tab of the editor: icon over a short label. */
@Composable
fun ToolTab(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (selected) LensColors.Text else LensColors.Faint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = LensType.Meta, color = if (selected) LensColors.Text else LensColors.Faint, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Box(Modifier.size(width = 14.dp, height = 2.dp).background(if (selected) LensColors.Accent else Color.Transparent, LensShapes.Pill))
    }
}

/** Centered empty/loading message. */
@Composable
fun CenterNote(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = LensType.Body, color = LensColors.Faint, textAlign = TextAlign.Center)
    }
}
