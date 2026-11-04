package com.lens035.app.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lens035.app.ui.theme.LensColors
import com.lens035.core.color.CurvePoint
import com.lens035.core.color.ToneCurve
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Hue/saturation wheel for colour grading. Angle = hue (0° at the right, counter-clockwise),
 * distance from the centre = saturation 0..100.
 */
@Composable
fun ColorWheel(
    hue: Float,
    saturation: Float,
    onChange: (hue: Float, saturation: Float) -> Unit,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 150.dp,
) {
    val change by rememberUpdatedState(onChange)
    val start by rememberUpdatedState(onGestureStart)
    val end by rememberUpdatedState(onGestureEnd)
    Canvas(
        modifier
            .size(diameter)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    start()
                    change(0f, 0f)
                    end()
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    start()
                    fun update(p: Offset) {
                        val r = min(size.width, size.height) / 2f
                        val dx = p.x - size.width / 2f
                        val dy = size.height / 2f - p.y
                        val dist = (hypot(dx, dy) / r).coerceIn(0f, 1f)
                        var h = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                        if (h < 0f) h += 360f
                        change(h, dist * 100f)
                    }
                    update(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val pc = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pc.pressed) break
                        pc.consume()
                        update(pc.position)
                    }
                    end()
                }
            },
    ) {
        val r = min(size.width, size.height) / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        val sweep = Brush.sweepGradient(
            listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { Color.hsv((360f - it) % 360f, 0.85f, 0.95f) },
            center = c,
        )
        drawCircle(sweep, r, c)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF8A8A8A), Color(0x008A8A8A)), center = c, radius = r), r, c)
        drawCircle(LensColors.Line, r, c, style = Stroke(1.dp.toPx()))
        drawLine(Color.White.copy(alpha = 0.18f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
        drawLine(Color.White.copy(alpha = 0.18f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx())
        val a = Math.toRadians(hue.toDouble())
        val d = (saturation / 100f).coerceIn(0f, 1f) * r
        val knob = Offset(c.x + (cos(a) * d).toFloat(), c.y - (sin(a) * d).toFloat())
        drawCircle(Color.Black.copy(alpha = 0.4f), 10.dp.toPx(), knob)
        drawCircle(Color.White, 8.dp.toPx(), knob, style = Stroke(2.5.dp.toPx()))
    }
}

/**
 * Interactive tone curve: drag points, tap to add, double-tap a point to remove it.
 * Draws the luminance histogram behind the curve when given.
 */
@Composable
fun CurveEditor(
    points: List<CurvePoint>,
    color: Color,
    histogram: IntArray?,
    onChange: (List<CurvePoint>) -> Unit,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentPoints by rememberUpdatedState(points)
    val change by rememberUpdatedState(onChange)
    val start by rememberUpdatedState(onGestureStart)
    val end by rememberUpdatedState(onGestureEnd)
    Canvas(
        modifier
            .aspectRatio(1.6f)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { pos ->
                    val pts = currentPoints
                    val idx = nearestIndex(pts, pos, size.width.toFloat(), size.height.toFloat(), 28.dp.toPx())
                    if (idx > 0 && idx < pts.size - 1) {
                        start()
                        change(pts.filterIndexed { i, _ -> i != idx })
                        end()
                    }
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    var pts = ToneCurve.sanitize(currentPoints).toMutableList()
                    var idx = nearestIndex(pts, down.position, w, h, 28.dp.toPx())
                    start()
                    if (idx < 0) {
                        // Add a new point on the curve under the finger.
                        val x = (down.position.x / w).coerceIn(0.02f, 0.98f)
                        val curve = ToneCurve(pts)
                        pts.add(CurvePoint(x, curve.evaluate(x)))
                        pts = pts.sortedBy { it.x }.toMutableList()
                        idx = pts.indexOfFirst { it.x == x }
                        change(pts.toList())
                    }
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val pc = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pc.pressed) break
                        pc.consume()
                        val px = (pc.position.x / w).coerceIn(0f, 1f)
                        val py = (1f - pc.position.y / h).coerceIn(0f, 1f)
                        val lo = if (idx > 0) pts[idx - 1].x + 0.02f else 0f
                        val hi = if (idx < pts.size - 1) pts[idx + 1].x - 0.02f else 1f
                        val nx = when (idx) {
                            0 -> min(px, if (pts.size > 1) pts[1].x - 0.02f else 1f).coerceAtLeast(0f)
                            pts.size - 1 -> px.coerceIn(lo, 1f)
                            else -> px.coerceIn(lo, hi)
                        }
                        pts[idx] = CurvePoint(nx, py)
                        change(pts.toList())
                    }
                    end()
                }
            },
    ) {
        val w = size.width
        val h = size.height
        if (histogram != null) {
            val maxV = (histogram.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
            val path = Path().apply {
                moveTo(0f, h)
                for (i in histogram.indices) {
                    val x = i / (histogram.size - 1f) * w
                    val y = h - (histogram[i] / maxV) * h * 0.9f
                    lineTo(x, y)
                }
                lineTo(w, h)
                close()
            }
            drawPath(path, Color.White.copy(alpha = 0.07f))
        }
        val grid = Color.White.copy(alpha = 0.1f)
        for (i in 1..3) {
            drawLine(grid, Offset(w * i / 4f, 0f), Offset(w * i / 4f, h), 1.dp.toPx())
            drawLine(grid, Offset(0f, h * i / 4f), Offset(w, h * i / 4f), 1.dp.toPx())
        }
        drawRect(LensColors.Line, Offset.Zero, Size(w, h), style = Stroke(1.dp.toPx()))
        drawLine(Color.White.copy(alpha = 0.15f), Offset(0f, h), Offset(w, 0f), 1.dp.toPx())
        val curve = ToneCurve(points)
        val path = Path()
        val steps = 96
        for (i in 0..steps) {
            val x = i / steps.toFloat()
            val y = curve.evaluate(x)
            if (i == 0) path.moveTo(x * w, (1f - y) * h) else path.lineTo(x * w, (1f - y) * h)
        }
        drawPath(path, color, style = Stroke(2.dp.toPx()))
        for (p in ToneCurve.sanitize(points)) {
            val c = Offset(p.x * w, (1f - p.y) * h)
            drawCircle(Color.Black, 7.dp.toPx(), c)
            drawCircle(color, 5.5.dp.toPx(), c)
        }
    }
}

private fun nearestIndex(points: List<CurvePoint>, pos: Offset, w: Float, h: Float, radius: Float): Int {
    var best = -1
    var bestD = radius
    ToneCurve.sanitize(points).forEachIndexed { i, p ->
        val d = hypot(p.x * w - pos.x, (1f - p.y) * h - pos.y)
        if (d < bestD) {
            bestD = d
            best = i
        }
    }
    return best
}
