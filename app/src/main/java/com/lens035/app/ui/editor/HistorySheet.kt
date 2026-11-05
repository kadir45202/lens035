package com.lens035.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lens035.app.ui.components.LensSheet
import com.lens035.app.ui.theme.LensColors
import com.lens035.app.ui.theme.LensType

/**
 * Lightroom-style history: every step by name, newest first. Tapping a step shows the edit as
 * it was then; the steps after it stay (dimmed) until a new change replaces them.
 */
@Composable
fun HistorySheet(visible: Boolean, session: EditSession, onDismiss: () -> Unit) {
    LensSheet(visible = visible, onDismiss = onDismiss) {
        val entries = session.history
        val current = session.historyIndex
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Geçmiş", style = LensType.Heading, color = LensColors.Text, modifier = Modifier.weight(1f))
            Text("${entries.size - 1} adım", style = LensType.Meta, color = LensColors.Faint)
        }
        Text(
            "Bir adıma dokun, düzenleme o hâline döner. Yeni bir değişiklik yapana kadar sonraki adımlar kaybolmaz.",
            style = LensType.Meta,
            color = LensColors.Faint,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        val listState = rememberLazyListState()
        // Newest first; keep the current step in view when the sheet opens.
        LaunchedEffect(visible) {
            if (visible) listState.scrollToItem((entries.lastIndex - current).coerceAtLeast(0))
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            items(entries.size, key = { k -> entries.lastIndex - k }) { k ->
                val i = entries.lastIndex - k
                HistoryRow(entries[i].label, isCurrent = i == current, undone = i > current) { session.jumpTo(i) }
            }
        }
    }
}

@Composable
private fun HistoryRow(label: String, isCurrent: Boolean, undone: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { selected = isCurrent }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val dot = Modifier.size(9.dp).clip(CircleShape)
        Box(
            when {
                isCurrent -> dot.background(LensColors.Accent)
                undone -> dot.border(1.dp, LensColors.Outline, CircleShape)
                else -> dot.background(LensColors.Outline)
            },
        )
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = LensType.Body,
            color = when {
                isCurrent -> LensColors.Text
                undone -> LensColors.Faint.copy(alpha = 0.6f)
                else -> LensColors.Muted
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isCurrent) Text("Şu an", style = LensType.Meta, color = LensColors.Accent)
    }
}
