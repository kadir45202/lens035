package com.lens035.app.gl

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import com.lens035.app.media.DateStamp
import com.lens035.core.analysis.ImageStats
import com.lens035.core.model.EditState
import com.lens035.core.model.GeometryMath
import com.lens035.core.render.RenderContext
import com.lens035.core.render.ShaderParams
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * A private OpenGL ES 3 context on its own thread, for work that must not disturb the live
 * preview: film/effect thumbnails and full-resolution export. Jobs run one at a time.
 */
class OffscreenRenderer(context: Context) {
    private val appContext = context.applicationContext
    private val thread = HandlerThread("lens-offscreen").apply { start() }
    private val handler = Handler(thread.looper)
    private var egl: EglCore? = null
    private var pipeline: LensPipeline? = null

    /** Runs [block] on the GL thread with a ready pipeline. */
    suspend fun <T> execute(block: (OffscreenGl) -> T): T = suspendCancellableCoroutine { cont ->
        handler.post {
            if (!cont.isActive) return@post
            try {
                val p = ensurePipeline()
                val result = block(OffscreenGl(p))
                cont.resume(result)
            } catch (t: Throwable) {
                cont.resumeWithException(t)
            }
        }
    }

    private fun ensurePipeline(): LensPipeline {
        pipeline?.let { return it }
        val core = EglCore().also { it.create() }
        egl = core
        val p = LensPipeline(ShaderSources.get(appContext))
        p.init()
        pipeline = p
        return p
    }

    fun release() {
        handler.post {
            try {
                pipeline?.release()
            } catch (_: Throwable) {
            }
            pipeline = null
            egl?.release()
            egl = null
            thread.quitSafely()
        }
    }
}

/** Operations available inside [OffscreenRenderer.execute]. */
class OffscreenGl(val pipeline: LensPipeline) {

    val maxTextureSize: Int get() = pipeline.maxTextureSize

    /**
     * Renders [state] applied to [source] and returns the result as a new bitmap.
     * [fullWidth]/[fullHeight] describe the original image (for size-dependent effects);
     * [personMask] is the person segmentation of the same frame, if known.
     */
    fun render(
        source: Bitmap,
        state: EditState,
        stats: ImageStats,
        dateText: String,
        fullWidth: Int = source.width,
        fullHeight: Int = source.height,
        personMask: PersonMaskData? = null,
    ): Bitmap {
        val tex = GlUtil.createTexture(source)
        var target: RenderTarget? = null
        try {
            val out = GeometryMath.outputSize(source.width, source.height, state.geometry)
            target = RenderTarget(out.width, out.height, false)
            val ctx = RenderContext(source.width, source.height, fullWidth, fullHeight, out.width, out.height)
            val params = ShaderParams.build(state, ctx, stats, dateText)
            val res = FrameResources(
                filmLut = LutCache.film(state.film?.id),
                customLut = LutCache.custom(state.customLut),
                dateBitmap = { DateStamp.bitmap(it) },
                personMask = personMask,
            )
            pipeline.render(
                PipelineInput(tex, InputKind.TEXTURE_TOP_DOWN, source.width, source.height, version = nextVersion()),
                params, res, PipelineOutput.Target(target),
            )
            target.bind()
            return GlUtil.readPixels(out.width, out.height)
        } finally {
            target?.release()
            GlUtil.deleteTexture(tex)
        }
    }

    /** Renders many states over the same small source (thumbnails). */
    fun renderAll(
        source: Bitmap,
        states: List<EditState>,
        stats: ImageStats,
        dateText: String,
        personMask: PersonMaskData? = null,
    ): List<Bitmap> {
        val tex = GlUtil.createTexture(source)
        val out = RenderTarget(source.width, source.height, false)
        val version = nextVersion()
        try {
            return states.map { state ->
                val s = state.copy(geometry = com.lens035.core.model.Geometry())
                val ctx = RenderContext(source.width, source.height, source.width, source.height, source.width, source.height)
                val params = ShaderParams.build(s, ctx, stats, dateText)
                val res = FrameResources(LutCache.film(s.film?.id), LutCache.custom(s.customLut), { DateStamp.bitmap(it) }, personMask)
                pipeline.render(PipelineInput(tex, InputKind.TEXTURE_TOP_DOWN, source.width, source.height, version), params, res, PipelineOutput.Target(out))
                out.bind()
                GlUtil.readPixels(source.width, source.height)
            }
        } finally {
            out.release()
            GlUtil.deleteTexture(tex)
        }
    }

    companion object {
        private var counter = 1_000_000L
        @Synchronized
        fun nextVersion(): Long = ++counter
    }
}
