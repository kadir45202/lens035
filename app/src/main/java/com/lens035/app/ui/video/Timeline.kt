package com.lens035.app.ui.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.lens035.app.ui.theme.LensColors
import com.lens035.app.ui.theme.LensShapes
import kotlin.math.abs

/**
 * Filmstrip of frames with trim handles and a playhead. Drag a handle to trim, tap or drag
 * elsewhere to scrub.
 */
@Composable
fun Timeline(
    frames: List<ImageBitmap>,
    durationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    positionMs: Long,
    onTrim: (Long, Long) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val start by rememberUpdatedState(trimStartMs)
    val end by rememberUpdatedState(trimEndMs)
    val duration by rememberUpdatedState(durationMs)
    val trim by rememberUpdatedState(onTrim)
    val seek by rememberUpdatedState(onSeek)
    Box(modifier.height(56.dp).clip(LensShapes.Small).background(LensColors.Surface)) {
        Row(Modifier.fillMaxSize()) {
            frames.forEach { f ->
                Image(f, null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
            }
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val d = duration
                        if (d <= 0L) return@awaitEachGesture
                        val w = size.width.toFloat()
                        val sx = start.toFloat() / d * w
                        val ex = end.toFloat() / d * w
                        val touch = 24.dp.toPx()
                        val mode = when {
                            abs(down.position.x - sx) < touch && abs(down.position.x - sx) <= abs(down.position.x - ex) -> 1
                            abs(down.position.x - ex) < touch -> 2
                            else -> 0
                        }
                        down.consume()
                        fun timeAt(x: Float) = (x / w * d).toLong().coerceIn(0L, d)
                        if (mode == 0) seek(timeAt(down.position.x))
                        val minLen = (d / 20).coerceAtLeast(300L)
                        while (true) {
                            val event = awaitPointerEvent()
                            val pc = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!pc.pressed) break
                            pc.consume()
                            val t = timeAt(pc.position.x)
                            when (mode) {
                                1 -> {
                                    val s = t.coerceAtMost(end - minLen).coerceAtLeast(0L)
                                    trim(s, end)
                                    seek(s)
                                }
                                2 -> {
                                    val e = t.coerceAtLeast(start + minLen).coerceAtMost(d)
                                    trim(start, e)
                                    seek(e)
                                }
                                else -> seek(t.coerceIn(start, end))
                            }
                        }
                    }
                },
        ) {
            if (durationMs <= 0L) return@Canvas
            val w = size.width
            val h = size.height
            val sx = trimStartMs.toFloat() / durationMs * w
            val ex = trimEndMs.toFloat() / durationMs * w
            val shade = Color.Black.copy(alpha = 0.62f)
            drawRect(shade, Offset.Zero, Size(sx, h))
            drawRect(shade, Offset(ex, 0f), Size(w - ex, h))
            val stroke = 2.dp.toPx()
            drawRoundRect(LensColors.Text, Offset(sx, stroke / 2), Size(ex - sx, h - stroke), CornerRadius(4.dp.toPx()), style = Stroke(stroke))
            val handleW = 10.dp.toPx()
            drawRoundRect(LensColors.Text, Offset(sx - handleW / 2, 0f), Size(handleW, h), CornerRadius(3.dp.toPx()))
            drawRoundRect(LensColors.Text, Offset(ex - handleW / 2, 0f), Size(handleW, h), CornerRadius(3.dp.toPx()))
            val grip = Color(0xFF444444)
            drawLine(grip, Offset(sx, h * 0.35f), Offset(sx, h * 0.65f), 2.dp.toPx())
            drawLine(grip, Offset(ex, h * 0.35f), Offset(ex, h * 0.65f), 2.dp.toPx())
            val px = positionMs.toFloat() / durationMs * w
            drawLine(LensColors.Accent, Offset(px, -2f), Offset(px, h + 2f), 2.dp.toPx())
        }
    }
}
