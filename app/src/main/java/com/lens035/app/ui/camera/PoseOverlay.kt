package com.lens035.app.ui.camera

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.lens035.core.pose.Joint
import com.lens035.core.pose.Limb
import com.lens035.core.pose.PoseSkeleton
import kotlin.math.hypot
import kotlin.math.max

/** Where the reference frame sits in the view, in pixels, after fit + user scale/offset. */
fun referenceRect(viewW: Float, viewH: Float, refAspect: Float, scale: Float, offsetX: Float, offsetY: Float): Rect {
    if (viewW <= 0f || viewH <= 0f) return Rect(0f, 0f, 1f, 1f)
    val viewAspect = viewW / viewH
    val (fw, fh) = if (refAspect > viewAspect) viewW to viewW / refAspect else viewH * refAspect to viewH
    val w = fw * scale
    val h = fh * scale
    val left = (viewW - w) / 2f + offsetX
    val top = (viewH - h) / 2f + offsetY
    return Rect(left, top, left + w, top + h)
}

/** Maps a skeleton normalised to [rect] (optionally mirrored) into normalised view coordinates. */
fun skeletonInView(s: PoseSkeleton, rect: Rect, viewW: Float, viewH: Float, mirror: Boolean): PoseSkeleton {
    val src = if (mirror) s.mirrored() else s
    return src.transformed(rect.width / viewW, rect.height / viewH, rect.left / viewW, rect.top / viewH)
}

private fun DrawScope.pt(s: PoseSkeleton, j: Joint, minConfidence: Float): Offset? {
    val p = s[j] ?: return null
    if (p.confidence < minConfidence) return null
    return Offset(p.x * size.width, p.y * size.height)
}

/**
 * Draws a soft body silhouette for a pose (in normalised view coordinates): thick limbs with
 * round caps, a filled torso and a head. [matched] tints it green when the pose fits.
 */
fun DrawScope.drawPoseSilhouette(s: PoseSkeleton, opacity: Float, highlight: Color, limbScores: Map<Limb, Float> = emptyMap()) {
    val ls = pt(s, Joint.LEFT_SHOULDER, 0f)
    val rs = pt(s, Joint.RIGHT_SHOULDER, 0f)
    val lh = pt(s, Joint.LEFT_HIP, 0f)
    val rh = pt(s, Joint.RIGHT_HIP, 0f)
    val torso = if (ls != null && rs != null && lh != null && rh != null) {
        hypot((ls.x + rs.x) / 2f - (lh.x + rh.x) / 2f, (ls.y + rs.y) / 2f - (lh.y + rh.y) / 2f)
    } else size.minDimension * 0.25f
    val limbWidth = max(torso * 0.2f, 6f)
    val fill = Color.White.copy(alpha = 0.22f * opacity)
    val line = Color.White.copy(alpha = 0.8f * opacity)

    if (ls != null && rs != null && lh != null && rh != null) {
        val path = Path().apply {
            moveTo(ls.x, ls.y)
            lineTo(rs.x, rs.y)
            lineTo(rh.x, rh.y)
            lineTo(lh.x, lh.y)
            close()
        }
        drawPath(path, fill)
        drawPath(path, line, style = Stroke(2f))
    }
    for (limb in Limb.entries) {
        if (limb == Limb.SHOULDERS || limb == Limb.HIPS || limb == Limb.LEFT_TORSO || limb == Limb.RIGHT_TORSO) continue
        val a = pt(s, limb.a, 0f) ?: continue
        val b = pt(s, limb.b, 0f) ?: continue
        val score = limbScores[limb]
        val c = if (score != null && score > 0.75f) highlight.copy(alpha = 0.45f * opacity) else fill
        drawLine(c, a, b, strokeWidth = limbWidth, cap = StrokeCap.Round)
        drawLine(line, a, b, strokeWidth = 2f, cap = StrokeCap.Round)
    }
    val nose = pt(s, Joint.NOSE, 0f)
    if (nose != null) {
        val shoulderW = if (ls != null && rs != null) hypot(ls.x - rs.x, ls.y - rs.y) else torso * 0.8f
        val headR = max(shoulderW * 0.42f, torso * 0.28f)
        if (ls != null && rs != null) {
            val neck = Offset((ls.x + rs.x) / 2f, (ls.y + rs.y) / 2f)
            drawLine(fill, neck, nose, strokeWidth = limbWidth * 0.8f, cap = StrokeCap.Round)
        }
        drawCircle(fill, headR, nose)
        drawCircle(line, headR, nose, style = Stroke(2f))
    }
}

/** Thin skeleton of the live pose detected in the camera. */
fun DrawScope.drawLiveSkeleton(s: PoseSkeleton, color: Color) {
    for (limb in Limb.entries) {
        val a = pt(s, limb.a, 0.5f) ?: continue
        val b = pt(s, limb.b, 0.5f) ?: continue
        drawLine(color, a, b, strokeWidth = 4f, cap = StrokeCap.Round)
    }
    for (j in Joint.entries) {
        val p = pt(s, j, 0.5f) ?: continue
        drawCircle(color, 6f, p)
    }
}
