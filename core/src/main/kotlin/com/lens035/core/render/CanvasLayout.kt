package com.lens035.core.render

import com.lens035.core.model.EditState
import com.lens035.core.model.EffectType
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Final output of an edit with the canvas effect: the canvas size in pixels and where the photo
 * sits in it (normalised, v down). Without a canvas the photo fills the output.
 */
data class CanvasLayout(
    val width: Int,
    val height: Int,
    val innerX: Float,
    val innerY: Float,
    val innerW: Float,
    val innerH: Float,
) {
    val isNone: Boolean get() = innerX == 0f && innerY == 0f && innerW == 1f && innerH == 1f

    companion object {
        /** Canvas aspect ratios, in the order of the effect's "Oran" choice. */
        val RATIOS = floatArrayOf(9f / 16f, 4f / 5f, 1f, 16f / 9f)

        /** Fill colours for the solid choices (index 0 is the blurred photo). */
        val FILLS = arrayOf(floatArrayOf(0f, 0f, 0f), floatArrayOf(0.02f, 0.02f, 0.02f), floatArrayOf(1f, 1f, 1f), floatArrayOf(0.937f, 0.910f, 0.863f))

        /** Longest canvas side; larger canvases are scaled down (the photo with them). */
        const val MAX_SIDE = 8192

        fun none(width: Int, height: Int) = CanvasLayout(width, height, 0f, 0f, 1f, 1f)

        /**
         * Layout for a photo of [photoW] x [photoH] (the cropped output) under [state]'s canvas
         * effect. The photo keeps its full resolution; the canvas grows around it.
         */
        fun of(state: EditState, photoW: Int, photoH: Int, evenDimensions: Boolean = false): CanvasLayout {
            val fx = state.effect(EffectType.CANVAS) ?: return none(photoW, photoH)
            val ratio = RATIOS[fx.value(EffectType.CANVAS.param("ratio")!!).roundToInt().coerceIn(0, RATIOS.size - 1)]
            val padding = (fx.amount / 100f).coerceIn(0f, 1f)
            val pw = photoW.toFloat()
            val ph = photoH.toFloat()
            var cw: Float
            var ch: Float
            if (pw / ph > ratio) {
                cw = pw
                ch = pw / ratio
            } else {
                ch = ph
                cw = ph * ratio
            }
            // Margin: the photo covers less of the canvas (down to 65% of the fit).
            val grow = 1f / (1f - 0.35f * padding)
            cw *= grow
            ch *= grow
            val innerW = pw / cw
            val innerH = ph / ch
            val k = minOf(1f, MAX_SIDE / max(cw, ch))
            var w = max(1, (cw * k).roundToInt())
            var h = max(1, (ch * k).roundToInt())
            if (evenDimensions) {
                w = max(2, w - (w and 1))
                h = max(2, h - (h and 1))
            }
            return CanvasLayout(w, h, (1f - innerW) / 2f, (1f - innerH) / 2f, innerW, innerH)
        }
    }
}
