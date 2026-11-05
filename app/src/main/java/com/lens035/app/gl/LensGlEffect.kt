package com.lens035.app.gl

import android.content.Context
import android.opengl.GLES30
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.lens035.app.media.DateStamp
import com.lens035.core.analysis.ImageStats
import com.lens035.core.model.EditState
import com.lens035.core.model.GeometryMath
import com.lens035.core.render.CanvasLayout
import com.lens035.core.render.RenderContext
import com.lens035.core.render.ShaderParams
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Media3 video effect that runs the complete Lens035 pipeline on every frame during export.
 * Frames arrive from Media3 as SDR, bottom-up textures; the output is written back bottom-up.
 */
@OptIn(UnstableApi::class)
class LensGlEffect(
    private val state: EditState,
    private val stats: ImageStats,
    /** Longest side of the exported video; larger sources are scaled down. */
    private val maxOutputSide: Int,
    private val dateText: String,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        // Video frames have no person segmentation: portrait blur and person masks are photo-only.
        LensShaderProgram(context.applicationContext, state.forVideo(), stats, maxOutputSide, dateText)

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = false
}

@OptIn(UnstableApi::class)
private class LensShaderProgram(
    private val context: Context,
    private val state: EditState,
    private val stats: ImageStats,
    private val maxOutputSide: Int,
    private val dateText: String,
) : BaseGlShaderProgram(/* useHighPrecisionColorComponents= */ false, /* texturePoolCapacity= */ 1) {

    private var pipeline: LensPipeline? = null
    private var workW = 0
    private var workH = 0
    private var outW = 0
    private var outH = 0
    private var canvas = CanvasLayout.none(2, 2)
    private var frameIndex = 0L
    private val filmLut = LutCache.film(state.film?.id)
    private val customLut = LutCache.custom(state.customLut)
    private val framebufferBinding = IntArray(1)

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        try {
            if (pipeline == null) pipeline = LensPipeline(ShaderSources.get(context)).also { it.init() }
            // The limit applies to the final frame, which a canvas makes larger than the video.
            var scale = minOf(1f, maxOutputSide / max(inputWidth, inputHeight).toFloat())
            for (attempt in 0 until 3) {
                workW = even((inputWidth * scale).roundToInt())
                workH = even((inputHeight * scale).roundToInt())
                val out = GeometryMath.outputSize(workW, workH, state.geometry, evenDimensions = true)
                outW = out.width
                outH = out.height
                canvas = CanvasLayout.of(state, outW, outH, evenDimensions = true)
                val longest = max(canvas.width, canvas.height)
                if (longest <= maxOutputSide) break
                scale *= maxOutputSide / longest.toFloat()
            }
            return Size(canvas.width, canvas.height)
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val p = pipeline ?: throw VideoFrameProcessingException("Pipeline not configured")
        try {
            // Media3 has already bound the output texture's framebuffer.
            GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING, framebufferBinding, 0)
            val fbo = framebufferBinding[0]
            frameIndex++
            val ctx = RenderContext(workW, workH, workW, workH, outW, outH, presentationTimeUs / 1_000_000f, frameIndex.toInt(), canvas)
            val params = ShaderParams.build(state, ctx, stats, dateText)
            val res = FrameResources(filmLut, customLut, { DateStamp.bitmap(it) })
            p.render(
                PipelineInput(inputTexId, InputKind.TEXTURE_BOTTOM_UP, workW, workH, frameIndex),
                params, res, PipelineOutput.External(fbo, canvas.width, canvas.height, flipY = true),
            )
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
            GLES30.glViewport(0, 0, canvas.width, canvas.height)
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            pipeline?.release()
        } catch (_: Exception) {
        }
        pipeline = null
    }

    private fun even(v: Int): Int = max(2, v - (v and 1))
}
