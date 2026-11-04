package com.lens035.app.gl

import kotlin.math.max
import kotlin.math.min

/** What the live preview shows. */
enum class PreviewMode {
    /** The full edit. */
    NORMAL,

    /** The untouched photo (press-and-hold compare), with the same framing. */
    ORIGINAL,

    /** The whole frame without the crop, so the crop rectangle can be edited on top. */
    CROP,
}

/**
 * Where the image sits inside the preview view. Shared by the GL presenter and the Compose
 * overlays (crop handles, masks) so that both always agree.
 */
data class ViewTransform(
    /** Image rectangle in view pixels. */
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height

    /** Uniform for present.frag: scale and offset in view UV (v = 0 at the top). */
    fun toUniform(viewWidth: Int, viewHeight: Int): FloatArray = floatArrayOf(
        width / viewWidth, height / viewHeight, left / viewWidth, top / viewHeight,
    )

    /** View pixel -> normalised image coordinate (0..1). */
    fun toImage(x: Float, y: Float, out: FloatArray) {
        out[0] = (x - left) / width
        out[1] = (y - top) / height
    }

    fun toView(u: Float, v: Float, out: FloatArray) {
        out[0] = left + u * width
        out[1] = top + v * height
    }

    companion object {
        /**
         * Fits an image of [imageWidth] x [imageHeight] into the view with [margin] pixels of air,
         * then applies [zoom] around the centre and a pan in view pixels. Pan is clamped so
         * the image cannot be dragged out of view.
         */
        fun fit(
            viewWidth: Int,
            viewHeight: Int,
            imageWidth: Int,
            imageHeight: Int,
            margin: Float = 0f,
            zoom: Float = 1f,
            panX: Float = 0f,
            panY: Float = 0f,
        ): ViewTransform {
            if (viewWidth <= 0 || viewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) return ViewTransform(0f, 0f, 1f, 1f)
            val availW = max(1f, viewWidth - 2 * margin)
            val availH = max(1f, viewHeight - 2 * margin)
            val imgAspect = imageWidth.toFloat() / imageHeight
            val fitW: Float
            val fitH: Float
            if (availW / availH > imgAspect) {
                fitH = availH
                fitW = availH * imgAspect
            } else {
                fitW = availW
                fitH = availW / imgAspect
            }
            val w = fitW * zoom
            val h = fitH * zoom
            val maxPanX = max(0f, (w - viewWidth) / 2f)
            val maxPanY = max(0f, (h - viewHeight) / 2f)
            val px = panX.coerceIn(-maxPanX, maxPanX)
            val py = panY.coerceIn(-maxPanY, maxPanY)
            return ViewTransform((viewWidth - w) / 2f + px, (viewHeight - h) / 2f + py, w, h)
        }

        fun clampZoom(z: Float): Float = min(8f, max(1f, z))
    }
}
