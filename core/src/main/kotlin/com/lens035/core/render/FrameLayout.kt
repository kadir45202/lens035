package com.lens035.core.render

import kotlin.math.max
import kotlin.math.min

/**
 * Layout of the frame effect inside the output image (all values in output UV, 0..1, v down)
 * and how the photo is fitted ("cover") into the inner rectangle without distortion.
 */
data class FrameLayout(
    val style: Int,
    val innerX: Float,
    val innerY: Float,
    val innerW: Float,
    val innerH: Float,
    /** photo uv = innerUv * scale + offset, where innerUv is 0..1 over the inner rectangle */
    val uvScaleX: Float,
    val uvScaleY: Float,
    val uvOffsetX: Float,
    val uvOffsetY: Float,
    /** Corner radius in units of the inner rectangle's shorter side. */
    val cornerRadius: Float,
) {
    companion object {
        val NONE = FrameLayout(0, 0f, 0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f)

        /**
         * @param thickness 0..1 user control
         * @param style 0 instant, 1 thin white, 2 black film, 3 rounded corners
         */
        fun compute(outW: Int, outH: Int, thickness: Float, style: Int, enabled: Boolean): FrameLayout {
            if (!enabled) return NONE
            val w = outW.toFloat()
            val h = outH.toFloat()
            val shortSide = min(w, h)
            val t = thickness.coerceIn(0f, 1f)
            // Border sizes in pixels.
            val (side, top, bottom, radius) = when (style) {
                0 -> { // instant print: even sides, deep bottom margin
                    val s = shortSide * (0.035f + 0.06f * t)
                    Quad(s, s, s * 3.2f, 0.004f)
                }
                1 -> { // thin white border
                    val s = shortSide * (0.01f + 0.04f * t)
                    Quad(s, s, s, 0f)
                }
                2 -> { // black film border with softly rounded window
                    val s = shortSide * (0.02f + 0.05f * t)
                    Quad(s, s, s, 0.03f)
                }
                else -> { // rounded corners on white
                    val s = shortSide * (0.015f + 0.04f * t)
                    Quad(s, s, s, 0.05f + 0.1f * t)
                }
            }
            val innerX = side / w
            val innerY = top / h
            val innerW = max(0.05f, (w - 2f * side) / w)
            val innerH = max(0.05f, (h - top - bottom) / h)
            // Fit the photo (aspect outW/outH) into the inner rect (aspect iw/ih) by cropping.
            val photoAspect = w / h
            val innerAspect = (innerW * w) / (innerH * h)
            var sx = 1f
            var sy = 1f
            if (innerAspect > photoAspect) {
                sy = photoAspect / innerAspect
            } else {
                sx = innerAspect / photoAspect
            }
            return FrameLayout(
                style = style,
                innerX = innerX, innerY = innerY, innerW = innerW, innerH = innerH,
                uvScaleX = sx, uvScaleY = sy,
                uvOffsetX = (1f - sx) / 2f, uvOffsetY = (1f - sy) / 2f,
                cornerRadius = radius,
            )
        }

        /**
         * Rectangle of the date stamp (x, y, w, h in output uv) inside the photo area.
         * The text bitmap is rendered at a 5:1 aspect ratio.
         */
        fun dateRect(outW: Int, outH: Int, layout: FrameLayout, corner: Int): FloatArray {
            val photoWpx = layout.innerW * outW
            val photoHpx = layout.innerH * outH
            val heightPx = min(photoWpx, photoHpx) * 0.055f
            val widthPx = heightPx * 5f
            val marginX = photoWpx * 0.05f
            val marginY = photoHpx * 0.045f
            val right = corner == 0 || corner == 2
            val bottom = corner == 0 || corner == 1
            val x = layout.innerX * outW + if (right) photoWpx - marginX - widthPx else marginX
            val y = layout.innerY * outH + if (bottom) photoHpx - marginY - heightPx else marginY
            return floatArrayOf(x / outW, y / outH, widthPx / outW, heightPx / outH)
        }
    }

    private data class Quad(val side: Float, val top: Float, val bottom: Float, val radius: Float)
}
