package com.lens035.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lens035.app.Lens035App
import com.lens035.app.data.HintStore
import com.lens035.app.ui.theme.LensColors
import com.lens035.app.ui.theme.LensShapes
import com.lens035.app.ui.theme.LensType

/** The one-time hints of a screen that have not been seen yet, shown one after another. */
@Stable
class HintQueue internal constructor(private val store: HintStore) {
    private var queue: List<String> = emptyList()

    /** The hint on screen, or null. */
    var current by mutableStateOf<String?>(null)
        private set
    var step by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set

    internal suspend fun load(ids: List<String>) {
        queue = store.pending(ids)
        total = queue.size
        step = 0
        current = queue.firstOrNull()
    }

    /** Dismisses the current hint for good and shows the next one. */
    fun next() {
        val id = current ?: return
        store.markSeen(id)
        step++
        current = queue.getOrNull(step)
    }

    /** Dismisses [id] if it is showing, e.g. because the person just did what it explains. */
    fun done(id: String) {
        if (current == id) next()
    }
}

@Composable
fun rememberHintQueue(vararg ids: String): HintQueue {
    val app = LocalContext.current.applicationContext as Lens035App
    val queue = remember { HintQueue(app.hints) }
    val key = ids.joinToString(",")
    LaunchedEffect(key) { queue.load(ids.toList()) }
    return queue
}

/** Shows the queue's current hint (text from [texts]) with a soft fade between hints. */
@Composable
fun HintHost(queue: HintQueue, texts: Map<String, String>, modifier: Modifier = Modifier, visible: Boolean = true) {
    val target = queue.current?.takeIf { visible }?.let { Triple(it, queue.step + 1, queue.total) }
    AnimatedContent(
        targetState = target,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
        contentAlignment = Alignment.BottomCenter,
        label = "hint",
        modifier = modifier,
    ) { t ->
        if (t == null) {
            Box(Modifier)
        } else {
            CoachHint(texts[t.first].orEmpty(), t.second, t.third, onNext = { queue.next() })
        }
    }
}

/** A quiet bubble with one tip. Tapping it moves on; nothing else on screen is blocked. */
@Composable
fun CoachHint(text: String, step: Int, steps: Int, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 440.dp)
            .clip(LensShapes.Card)
            .background(LensColors.Raised.copy(alpha = 0.97f))
            .border(1.dp, LensColors.Outline, LensShapes.Card)
            .clickable(role = Role.Button, onClick = onNext)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 14.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = LensColors.Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = LensType.Body, color = LensColors.Text, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(if (step < steps) "İleri" else "Tamam", style = LensType.Label, color = LensColors.Accent)
            if (steps > 1) Text("$step/$steps", style = LensType.Meta, color = LensColors.Faint)
        }
    }
}
