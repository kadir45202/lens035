package com.lens035.app.ui.editor

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import com.lens035.app.ui.theme.LensShapes
import com.lens035.app.ui.theme.LensType
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lens035.app.gl.PersonMaskData
import com.lens035.app.gl.ViewTransform
import com.lens035.app.ui.theme.LensColors
import com.lens035.core.model.CropRect
import com.lens035.core.model.Geometry
import com.lens035.core.model.GeometryMath
import com.lens035.core.model.LocalMask
import com.lens035.core.model.MaskKind
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private enum class CropHandle { NONE, MOVE, L, T, R, B, TL, TR, BL, BR }

/**
 * Crop rectangle editor drawn over the full (uncropped, corrected) frame. Coordinates are
 * normalised to the oriented frame, which in crop mode is exactly the displayed image.
 * [correction] supplies straighten and perspective (its crop is ignored); the crop can never
 * leave the corrected image. [guides] draws a fine grid for lining up verticals.
 */
@Composable
fun CropOverlay(
    vt: ViewTransform,
    crop: CropRect,
    aspect: Float,
    correction: Geometry,
    orientedW: Int,
    orientedH: Int,
    onChange: (CropRect) -> Unit,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
    guides: Boolean = false,
) {
    val density = LocalDensity.current
    val touch = with(density) { 28.dp.toPx() }
    val currentCrop by rememberUpdatedState(crop)
    val currentVt by rememberUpdatedState(vt)
    val change by rememberUpdatedState(onChange)
    val start by rememberUpdatedState(onGestureStart)
    val end by rememberUpdatedState(onGestureEnd)
    var active by remember { mutableStateOf(false) }
    // Only the correction itself restarts the gesture handler, not every crop change.
    val fitGeometry = Geometry(straighten = correction.straighten, vertical = correction.vertical, horizontal = correction.horizontal)

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(aspect, fitGeometry, orientedW, orientedH) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val v = currentVt
                    val c = currentCrop
                    val l = v.left + c.left * v.width
                    val t = v.top + c.top * v.height
                    val r = v.left + c.right * v.width
                    val b = v.top + c.bottom * v.height
                    val p = down.position
                    fun near(a: Float, bb: Float) = abs(a - bb) < touch
                    val handle = when {
                        near(p.x, l) && near(p.y, t) -> CropHandle.TL
                        near(p.x, r) && near(p.y, t) -> CropHandle.TR
                        near(p.x, l) && near(p.y, b) -> CropHandle.BL
                        near(p.x, r) && near(p.y, b) -> CropHandle.BR
                        near(p.x, l) && p.y in t..b -> CropHandle.L
                        near(p.x, r) && p.y in t..b -> CropHandle.R
                        near(p.y, t) && p.x in l..r -> CropHandle.T
                        near(p.y, b) && p.x in l..r -> CropHandle.B
                        p.x in l..r && p.y in t..b -> CropHandle.MOVE
                        else -> CropHandle.NONE
                    }
                    if (handle == CropHandle.NONE) return@awaitEachGesture
                    down.consume()
                    active = true
                    start()
                    var rect = c
                    while (true) {
                        val event = awaitPointerEvent()
                        val pc = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pc.pressed) break
                        val delta = pc.positionChange()
                        pc.consume()
                        val dx = delta.x / v.width
                        val dy = delta.y / v.height
                        val next = adjustCrop(rect, handle, dx, dy, aspect, orientedW.toFloat(), orientedH.toFloat())
                        if (next != null && GeometryMath.cropFits(next, fitGeometry, orientedW.toFloat(), orientedH.toFloat())) {
                            rect = next
                            change(next)
                        }
                    }
                    active = false
                    end()
                }
            },
    ) {
        val l = vt.left + crop.left * vt.width
        val t = vt.top + crop.top * vt.height
        val r = vt.left + crop.right * vt.width
        val b = vt.top + crop.bottom * vt.height
        val dim = Color.Black.copy(alpha = 0.62f)
        drawRect(dim, Offset(0f, 0f), Size(size.width, max(0f, t)))
        drawRect(dim, Offset(0f, b), Size(size.width, max(0f, size.height - b)))
        drawRect(dim, Offset(0f, t), Size(max(0f, l), b - t))
        drawRect(dim, Offset(r, t), Size(max(0f, size.width - r), b - t))
        val lineW = 1.dp.toPx()
        drawRect(Color.White.copy(alpha = 0.9f), Offset(l, t), Size(r - l, b - t), style = Stroke(lineW))
        if (guides) {
            // A fine grid while straightening or fixing perspective: verticals line up against it.
            val n = 8
            for (i in 1 until n) {
                val x = l + (r - l) * i / n
                val y = t + (b - t) * i / n
                drawLine(Color.White.copy(alpha = 0.4f), Offset(x, t), Offset(x, b), lineW)
                drawLine(Color.White.copy(alpha = 0.4f), Offset(l, y), Offset(r, y), lineW)
            }
        } else {
            val gridAlpha = if (active) 0.55f else 0.28f
            for (i in 1..2) {
                val x = l + (r - l) * i / 3f
                val y = t + (b - t) * i / 3f
                drawLine(Color.White.copy(alpha = gridAlpha), Offset(x, t), Offset(x, b), lineW)
                drawLine(Color.White.copy(alpha = gridAlpha), Offset(l, y), Offset(r, y), lineW)
            }
        }
        val arm = 18.dp.toPx()
        val thick = 3.dp.toPx()
        val corners = listOf(Offset(l, t) to Offset(1f, 1f), Offset(r, t) to Offset(-1f, 1f), Offset(l, b) to Offset(1f, -1f), Offset(r, b) to Offset(-1f, -1f))
        for ((pt, dir) in corners) {
            drawLine(Color.White, pt, Offset(pt.x + arm * dir.x, pt.y), thick)
            drawLine(Color.White, pt, Offset(pt.x, pt.y + arm * dir.y), thick)
        }
        val mid = 14.dp.toPx()
        drawLine(Color.White, Offset((l + r) / 2 - mid, t), Offset((l + r) / 2 + mid, t), thick)
        drawLine(Color.White, Offset((l + r) / 2 - mid, b), Offset((l + r) / 2 + mid, b), thick)
        drawLine(Color.White, Offset(l, (t + b) / 2 - mid), Offset(l, (t + b) / 2 + mid), thick)
        drawLine(Color.White, Offset(r, (t + b) / 2 - mid), Offset(r, (t + b) / 2 + mid), thick)
    }
}

/** Applies a handle drag to a crop; returns null when the result would be invalid. */
private fun adjustCrop(c: CropRect, h: CropHandle, dx: Float, dy: Float, aspect: Float, ow: Float, oh: Float): CropRect? {
    val minSize = 0.06f
    if (h == CropHandle.MOVE) {
        val nx = (c.left + dx).coerceIn(0f, 1f - c.width)
        val ny = (c.top + dy).coerceIn(0f, 1f - c.height)
        return CropRect(nx, ny, nx + c.width, ny + c.height)
    }
    var l = c.left
    var t = c.top
    var r = c.right
    var b = c.bottom
    when (h) {
        CropHandle.L -> l += dx
        CropHandle.R -> r += dx
        CropHandle.T -> t += dy
        CropHandle.B -> b += dy
        CropHandle.TL -> { l += dx; t += dy }
        CropHandle.TR -> { r += dx; t += dy }
        CropHandle.BL -> { l += dx; b += dy }
        CropHandle.BR -> { r += dx; b += dy }
        else -> Unit
    }
    if (aspect > 0f) {
        // Keep the pixel aspect: width_px / height_px == aspect.
        val wantH = (r - l) * ow / aspect / oh
        when (h) {
            CropHandle.T, CropHandle.B -> {
                val wantW = (b - t) * oh * aspect / ow
                val cx = (c.left + c.right) / 2f
                l = cx - wantW / 2f
                r = cx + wantW / 2f
            }
            CropHandle.L, CropHandle.R -> {
                val cy = (c.top + c.bottom) / 2f
                t = cy - wantH / 2f
                b = cy + wantH / 2f
            }
            CropHandle.TL, CropHandle.TR -> t = b - wantH
            CropHandle.BL, CropHandle.BR -> b = t + wantH
            else -> Unit
        }
    }
    if (r - l < minSize || b - t < minSize) return null
    if (l < -1e-4f || t < -1e-4f || r > 1f + 1e-4f || b > 1f + 1e-4f) return null
    return CropRect(max(0f, l), max(0f, t), min(1f, r), min(1f, b))
}

/** Maps between source-image UVs and view pixels for the current geometry. */
class SourceViewMapper(
    private val vt: ViewTransform,
    private val srcToOut: FloatArray,
    private val outToSrc: FloatArray,
    /** Source width / height. */
    val aspect: Float,
) {
    private val tmp = FloatArray(2)

    fun toView(u: Float, v: Float): Offset {
        GeometryMath.apply(srcToOut, u, v, tmp)
        return Offset(vt.left + tmp[0] * vt.width, vt.top + tmp[1] * vt.height)
    }

    /** Source uv -> view pixels as a row-major 3x3 (projective when perspective is on). */
    fun sourceToViewMatrix(): FloatArray = GeometryMath.mul(
        floatArrayOf(vt.width, 0f, vt.left, 0f, vt.height, vt.top, 0f, 0f, 1f),
        srcToOut,
    )

    fun toSource(p: Offset): Offset {
        val ou = (p.x - vt.left) / vt.width
        val ov = (p.y - vt.top) / vt.height
        GeometryMath.apply(outToSrc, ou, ov, tmp)
        return Offset(tmp[0], tmp[1])
    }
}

private enum class MaskHandle { NONE, CENTER, A, B, RX, RY }

/**
 * Divider of the split compare at [fraction] of the view width: the original is on its left, the
 * edit on its right. Drags that start near the line move it; other touches fall through to the
 * canvas (zoom, pan, hold to compare).
 */
@Composable
fun SplitDivider(fraction: Float, vt: ViewTransform, onChange: (Float) -> Unit) {
    val density = LocalDensity.current
    val touch = with(density) { 36.dp.toPx() }
    val current by rememberUpdatedState(fraction)
    val change by rememberUpdatedState(onChange)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewW = constraints.maxWidth.toFloat()
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (abs(down.position.x - current * size.width) > touch) return@awaitEachGesture
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val pc = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!pc.pressed) break
                            pc.consume()
                            change((pc.position.x / size.width).coerceIn(0.02f, 0.98f))
                        }
                    }
                },
        ) {
            val x = current * size.width
            val top = vt.top.coerceIn(0f, size.height)
            val bottom = vt.bottom.coerceIn(0f, size.height)
            drawLine(Color.White, Offset(x, top), Offset(x, bottom), 2.dp.toPx())
            val cy = (top + bottom) / 2f
            drawCircle(Color.White, 17.dp.toPx(), Offset(x, cy))
            val a = 5.dp.toPx()
            val gap = 3.5.dp.toPx()
            val chevrons = Path().apply {
                moveTo(x - gap, cy - a); lineTo(x - gap - a, cy); lineTo(x - gap, cy + a)
                moveTo(x + gap, cy - a); lineTo(x + gap + a, cy); lineTo(x + gap, cy + a)
            }
            drawPath(chevrons, Color.Black, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // Labels sit in the image's top corners, not the view's (portrait photos leave side bars).
        val labelTop = with(density) { vt.top.coerceAtLeast(0f).toDp() } + 10.dp
        val labelStart = with(density) { vt.left.coerceIn(0f, viewW).toDp() } + 10.dp
        val labelEnd = with(density) { (viewW - vt.right).coerceIn(0f, viewW).toDp() } + 10.dp
        SplitLabel("Önce", Modifier.align(Alignment.TopStart).padding(start = labelStart, top = labelTop))
        SplitLabel("Sonra", Modifier.align(Alignment.TopEnd).padding(end = labelEnd, top = labelTop))
    }
}

@Composable
private fun SplitLabel(text: String, modifier: Modifier) {
    Text(
        text,
        style = LensType.Label,
        color = LensColors.Text,
        modifier = modifier.background(Color.Black.copy(alpha = 0.55f), LensShapes.Pill).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * Briefly tints what a person mask selects (the detected person, or everything else when
 * [inverted]) so the selection is visible; it fades out on its own when [visible] turns false.
 */
@Composable
fun PersonMaskTint(mapper: SourceViewMapper, vt: ViewTransform, mask: PersonMaskData, inverted: Boolean, visible: Boolean) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (visible) 160 else 650),
        label = "personTint",
    )
    if (alpha <= 0.001f) return
    val tint = remember(mask.key, inverted) { personTintBitmap(mask, inverted).asImageBitmap() }
    val matrix = remember { Matrix() }
    Canvas(Modifier.fillMaxSize()) {
        // Tint pixels -> source uv -> view, through the full (possibly projective) edit geometry.
        val m = GeometryMath.mul(
            mapper.sourceToViewMatrix(),
            floatArrayOf(1f / tint.width, 0f, 0f, 0f, 1f / tint.height, 0f, 0f, 0f, 1f),
        )
        matrix.reset()
        matrix.values[Matrix.ScaleX] = m[0]
        matrix.values[Matrix.SkewX] = m[1]
        matrix.values[Matrix.TranslateX] = m[2]
        matrix.values[Matrix.SkewY] = m[3]
        matrix.values[Matrix.ScaleY] = m[4]
        matrix.values[Matrix.TranslateY] = m[5]
        matrix.values[Matrix.Perspective0] = m[6]
        matrix.values[Matrix.Perspective1] = m[7]
        matrix.values[Matrix.Perspective2] = m[8]
        clipRect(vt.left, vt.top, vt.right, vt.bottom) {
            withTransform({ transform(matrix) }) {
                drawImage(tint, alpha = alpha * 0.6f)
            }
        }
    }
}

private fun personTintBitmap(mask: PersonMaskData, inverted: Boolean): Bitmap {
    val rgb = LensColors.Accent.toArgb() and 0xFFFFFF
    val px = IntArray(mask.width * mask.height)
    for (i in px.indices) {
        val c = mask.data[i].toInt() and 0xFF
        val a = if (inverted) 255 - c else c
        px[i] = (a shl 24) or rgb
    }
    return Bitmap.createBitmap(px, mask.width, mask.height, Bitmap.Config.ARGB_8888)
}

/** On-canvas editor for one local mask. */
@Composable
fun MaskOverlay(
    mapper: SourceViewMapper,
    mask: LocalMask,
    onChange: (LocalMask) -> Unit,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
) {
    val density = LocalDensity.current
    val touch = with(density) { 30.dp.toPx() }
    val currentMask by rememberUpdatedState(mask)
    val currentMapper by rememberUpdatedState(mapper)
    val change by rememberUpdatedState(onChange)
    val start by rememberUpdatedState(onGestureStart)
    val end by rememberUpdatedState(onGestureEnd)

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val m = currentMask
                    val map = currentMapper
                    val handle = hitMask(m, map, down.position, touch)
                    if (handle == MaskHandle.NONE) return@awaitEachGesture
                    down.consume()
                    start()
                    var cur = m
                    var last = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val pc = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pc.pressed) break
                        val pos = pc.position
                        pc.consume()
                        cur = dragMask(cur, handle, map, last, pos)
                        last = pos
                        change(cur)
                    }
                    end()
                }
            },
    ) {
        val stroke = 1.5.dp.toPx()
        val handleR = 7.dp.toPx()
        val white = Color.White
        if (mask.kind == MaskKind.RADIAL) {
            val path = Path()
            val steps = 72
            for (i in 0..steps) {
                val a = i / steps.toFloat() * 2f * Math.PI.toFloat()
                val p = radialPoint(mask, mapper.aspect, cos(a), sin(a))
                val v = mapper.toView(p.x, p.y)
                if (i == 0) path.moveTo(v.x, v.y) else path.lineTo(v.x, v.y)
            }
            drawPath(path, white.copy(alpha = 0.9f), style = Stroke(stroke))
            val inner = Path()
            val f = 1f - (mask.feather / 100f).coerceIn(0f, 1f)
            for (i in 0..steps) {
                val a = i / steps.toFloat() * 2f * Math.PI.toFloat()
                val p = radialPoint(mask, mapper.aspect, cos(a) * f, sin(a) * f)
                val v = mapper.toView(p.x, p.y)
                if (i == 0) inner.moveTo(v.x, v.y) else inner.lineTo(v.x, v.y)
            }
            drawPath(inner, white.copy(alpha = 0.45f), style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
            val c = mapper.toView(mask.x0, mask.y0)
            val rx = radialPoint(mask, mapper.aspect, 1f, 0f).let { mapper.toView(it.x, it.y) }
            val ry = radialPoint(mask, mapper.aspect, 0f, 1f).let { mapper.toView(it.x, it.y) }
            drawCircle(LensColors.Accent, handleR, c)
            drawCircle(white, handleR, c, style = Stroke(stroke))
            drawCircle(white, handleR * 0.8f, rx)
            drawCircle(white, handleR * 0.8f, ry)
        } else {
            val a = mapper.toView(mask.x0, mask.y0)
            val b = mapper.toView(mask.x1, mask.y1)
            val dir = Offset(b.x - a.x, b.y - a.y)
            val len = max(1f, hypot(dir.x, dir.y))
            val n = Offset(-dir.y / len, dir.x / len)
            val span = size.width + size.height
            fun line(p: Offset, alpha: Float, dashed: Boolean) {
                drawLine(
                    white.copy(alpha = alpha), Offset(p.x - n.x * span, p.y - n.y * span), Offset(p.x + n.x * span, p.y + n.y * span),
                    stroke, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 10f)) else null,
                )
            }
            line(a, 0.9f, false)
            line(Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f), 0.6f, true)
            line(b, 0.5f, true)
            drawCircle(LensColors.Accent, handleR, a)
            drawCircle(white, handleR, a, style = Stroke(stroke))
            drawCircle(white, handleR * 0.8f, b)
        }
    }
}

/** Point on the radial mask's ellipse (unit circle coordinates cx, cy) in source UV. */
private fun radialPoint(m: LocalMask, aspect: Float, cx: Float, cy: Float): Offset {
    val ca = cos(m.angle)
    val sa = sin(m.angle)
    // Ellipse in aspect-corrected space (x scaled by aspect), rotated by angle.
    val ex = cx * m.x1
    val ey = cy * m.y1
    val rx = ca * ex - sa * ey
    val ry = sa * ex + ca * ey
    return Offset(m.x0 + rx / aspect, m.y0 + ry)
}

private fun hitMask(m: LocalMask, map: SourceViewMapper, p: Offset, touch: Float): MaskHandle {
    fun dist(a: Offset) = hypot(a.x - p.x, a.y - p.y)
    return if (m.kind == MaskKind.RADIAL) {
        val c = map.toView(m.x0, m.y0)
        val rx = radialPoint(m, map.aspect, 1f, 0f).let { map.toView(it.x, it.y) }
        val ry = radialPoint(m, map.aspect, 0f, 1f).let { map.toView(it.x, it.y) }
        when {
            dist(rx) < touch -> MaskHandle.RX
            dist(ry) < touch -> MaskHandle.RY
            dist(c) < touch * 1.6f -> MaskHandle.CENTER
            else -> MaskHandle.NONE
        }
    } else {
        val a = map.toView(m.x0, m.y0)
        val b = map.toView(m.x1, m.y1)
        val mid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
        when {
            dist(a) < touch -> MaskHandle.A
            dist(b) < touch -> MaskHandle.B
            dist(mid) < touch * 2f -> MaskHandle.CENTER
            else -> MaskHandle.NONE
        }
    }
}

private fun dragMask(m: LocalMask, h: MaskHandle, map: SourceViewMapper, from: Offset, to: Offset): LocalMask {
    val s0 = map.toSource(from)
    val s1 = map.toSource(to)
    val du = s1.x - s0.x
    val dv = s1.y - s0.y
    return when (h) {
        MaskHandle.CENTER -> if (m.kind == MaskKind.RADIAL) {
            m.copy(x0 = (m.x0 + du).coerceIn(-0.5f, 1.5f), y0 = (m.y0 + dv).coerceIn(-0.5f, 1.5f))
        } else {
            m.copy(x0 = m.x0 + du, y0 = m.y0 + dv, x1 = m.x1 + du, y1 = m.y1 + dv)
        }
        MaskHandle.A -> m.copy(x0 = s1.x, y0 = s1.y)
        MaskHandle.B -> m.copy(x1 = s1.x, y1 = s1.y)
        MaskHandle.RX -> {
            // Radius along the ellipse's x axis and its rotation follow the finger.
            val dx = (s1.x - m.x0) * map.aspect
            val dy = s1.y - m.y0
            val r = hypot(dx, dy).coerceIn(0.02f, 2f)
            m.copy(x1 = r, angle = atan2(dy, dx))
        }
        MaskHandle.RY -> {
            val dx = (s1.x - m.x0) * map.aspect
            val dy = s1.y - m.y0
            // Project onto the ellipse's y axis.
            val ax = -sin(m.angle)
            val ay = cos(m.angle)
            val r = abs(dx * ax + dy * ay).coerceIn(0.02f, 2f)
            m.copy(y1 = r)
        }
        MaskHandle.NONE -> m
    }
}
