package com.lens035.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.lens035.app.ui.theme.LensColors
import com.lens035.app.ui.theme.LensType
import kotlin.math.abs
import kotlin.math.roundToInt

/** How a slider value is printed. */
enum class ValueFormat {
    /** Whole numbers with sign, e.g. +25. */
    SIGNED,

    /** Whole numbers, e.g. 40. */
    PLAIN,

    /** Exposure, e.g. +0.35 EV. */
    EV,

    /** Degrees, e.g. 12.5°. */
    DEGREES,

    /** One decimal, e.g. 1.2. */
    DECIMAL,
}

fun formatValue(v: Float, format: ValueFormat): String = when (format) {
    ValueFormat.SIGNED -> {
        val r = v.roundToInt()
        if (r > 0) "+$r" else r.toString()
    }
    ValueFormat.PLAIN -> v.roundToInt().toString()
    ValueFormat.EV -> (if (v > 0.004f) "+" else "") + String.format(java.util.Locale.US, "%.2f", v)
    ValueFormat.DEGREES -> String.format(java.util.Locale.US, "%.1f°", v)
    ValueFormat.DECIMAL -> String.format(java.util.Locale.US, "%.1f", v)
}

/**
 * Lightroom-style slider row: label and value above a thin track. Drag anywhere on the row,
 * double-tap to reset. Bipolar sliders fill from the default value. Haptic tick when the value
 * passes its default.
 */
@Composable
fun LensSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    min: Float = -100f,
    max: Float = 100f,
    default: Float = 0f,
    format: ValueFormat = if (min < 0f) ValueFormat.SIGNED else ValueFormat.PLAIN,
    trackBrush: Brush? = null,
    enabled: Boolean = true,
    onGestureStart: () -> Unit = {},
    onGestureEnd: () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    var trackWidth by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    val currentValue by rememberUpdatedState(value)
    val change by rememberUpdatedState(onValueChange)
    val start by rememberUpdatedState(onGestureStart)
    val end by rememberUpdatedState(onGestureEnd)
    val changed = abs(value - default) > 1e-4f
    val range = (max - min).coerceAtLeast(1e-6f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = label
                stateDescription = formatValue(value, format)
            }
            .pointerInput(enabled, min, max, default) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onDoubleTap = {
                        start()
                        change(default)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        end()
                    },
                )
            }
            .pointerInput(enabled, min, max, default) {
                if (!enabled) return@pointerInput
                var acc = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        acc = currentValue
                        dragging = true
                        start()
                    },
                    onDragEnd = {
                        dragging = false
                        end()
                    },
                    onDragCancel = {
                        dragging = false
                        end()
                    },
                    onHorizontalDrag = { pointer, dx ->
                        pointer.consume()
                        val before = acc
                        acc = (acc + dx / trackWidth * range).coerceIn(min, max)
                        // Snap gently to the default so it is easy to hit exactly.
                        val snapped = if (abs(acc - default) < range * 0.008f) default else acc
                        if ((before - default) * (acc - default) < 0f || (snapped == default && before != default)) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        change(snapped)
                    },
                )
            }
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = LensType.Label,
                color = if (!enabled) LensColors.Faint else if (dragging || changed) LensColors.Text else LensColors.Muted,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatValue(value, format),
                style = LensType.Value,
                color = if (changed) LensColors.Text else LensColors.Faint,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(18.dp)) {
            Canvas(Modifier.fillMaxWidth().height(18.dp)) {
                trackWidth = size.width
                val cy = size.height / 2f
                val trackH = 2.dp.toPx()
                val t = ((value - min) / range).coerceIn(0f, 1f)
                val d = ((default - min) / range).coerceIn(0f, 1f)
                if (trackBrush != null) {
                    drawRoundRect(trackBrush, Offset(0f, cy - 1.5.dp.toPx()), Size(size.width, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
                } else {
                    drawRoundRect(LensColors.Outline, Offset(0f, cy - trackH / 2), Size(size.width, trackH), CornerRadius(trackH))
                    val x0 = size.width * minOf(t, d)
                    val x1 = size.width * maxOf(t, d)
                    if (x1 - x0 > 0.5f) {
                        drawRoundRect(
                            if (enabled) LensColors.Accent else LensColors.Faint,
                            Offset(x0, cy - trackH / 2), Size(x1 - x0, trackH), CornerRadius(trackH),
                        )
                    }
                    if (d > 0.001f && d < 0.999f) {
                        drawCircle(LensColors.Faint, radius = 2.dp.toPx(), center = Offset(size.width * d, cy))
                    }
                }
                val knobR = if (dragging) 8.dp.toPx() else 6.5.dp.toPx()
                drawCircle(Color.Black.copy(alpha = 0.35f), radius = knobR + 1.5.dp.toPx(), center = Offset(size.width * t, cy))
                drawCircle(if (enabled) LensColors.Text else LensColors.Faint, radius = knobR, center = Offset(size.width * t, cy))
            }
        }
    }
}

/** Track gradient helpers for colour sliders. */
object SliderBrushes {
    fun hueAround(centerHue: Float): Brush {
        val colors = (-4..4).map { i -> Color.hsv(((centerHue + i * 10f) % 360f + 360f) % 360f, 0.75f, 0.9f) }
        return Brush.horizontalGradient(colors)
    }

    fun saturation(hue: Float): Brush = Brush.horizontalGradient(listOf(Color.hsv(hue, 0f, 0.6f), Color.hsv(hue, 0.9f, 0.95f)))

    fun luminance(hue: Float): Brush = Brush.horizontalGradient(listOf(Color.hsv(hue, 0.7f, 0.25f), Color.hsv(hue, 0.35f, 1f)))

    val temperature: Brush = Brush.horizontalGradient(listOf(Color(0xFF3F7BD9), Color(0xFFD8D4CC), Color(0xFFE9A43C)))
    val tint: Brush = Brush.horizontalGradient(listOf(Color(0xFF3FAF57), Color(0xFFD8D4CC), Color(0xFFC94FB3)))
    val hueFull: Brush = Brush.horizontalGradient((0..12).map { Color.hsv(it * 30f % 360f, 0.8f, 0.95f) })
}
