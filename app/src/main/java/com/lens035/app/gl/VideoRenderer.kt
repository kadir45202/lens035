package com.lens035.app.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import com.lens035.app.media.DateStamp
import com.lens035.core.analysis.ImageStats
import com.lens035.core.model.EditState
import com.lens035.core.model.GeometryMath
import com.lens035.core.render.RenderContext
import com.lens035.core.render.ShaderParams
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Live preview for video. ExoPlayer decodes into a SurfaceTexture owned by this renderer;
 * every new frame (and every edit, even while paused) runs through the same [LensPipeline]
 * as photos, so the preview matches the export.
 */
class VideoRenderer(
    private val context: Context,
    private val view: GLSurfaceView,
    /** Called on the main thread with the decoder output surface (or null when it is gone). */
    private val onSurface: (Surface?) -> Unit,
) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private val main = Handler(Looper.getMainLooper())
    private var pipeline: LensPipeline? = null
    private var oesTex = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    private val texMatrix = FloatArray(16)
    private var frameVersion = 0L
    private var frameTimeSeconds = 0f
    private var finalTarget: RenderTarget? = null
    private var viewWidth = 0
    private var viewHeight = 0
    private val background = floatArrayOf(0f, 0f, 0f)

    @Volatile private var frameAvailable = false
    @Volatile private var state = EditState()
    @Volatile private var stats = ImageStats.NEUTRAL
    @Volatile private var videoWidth = 0
    @Volatile private var videoHeight = 0
    @Volatile private var transfer = Transfer.SDR
    @Volatile var dateText: String = ""
    @Volatile var original = false
    @Volatile var cropMode = false
    @Volatile var marginPx = 0f
    @Volatile var onError: ((Throwable) -> Unit)? = null

    fun setVideoSize(width: Int, height: Int) {
        videoWidth = width
        videoHeight = height
        view.requestRender()
    }

    fun setTransfer(value: Int) {
        transfer = value
        view.requestRender()
    }

    fun setState(value: EditState) {
        state = value
        view.requestRender()
    }

    fun setStats(value: ImageStats) {
        stats = value
        view.requestRender()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        pipeline = null
        finalTarget = null
        frameVersion = 0
        try {
            pipeline = LensPipeline(ShaderSources.get(context)).also { it.init() }
            oesTex = GlUtil.createOesTexture()
            val st = SurfaceTexture(oesTex)
            st.setOnFrameAvailableListener(this)
            surfaceTexture?.release()
            surfaceTexture = st
            val s = Surface(st)
            val old = surface
            surface = s
            main.post {
                onSurface(s)
                old?.release()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Video pipeline init failed", t)
            onError?.invoke(t)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
    }

    override fun onFrameAvailable(st: SurfaceTexture?) {
        frameAvailable = true
        view.requestRender()
    }

    override fun onDrawFrame(gl: GL10?) {
        val p = pipeline
        val st = surfaceTexture
        if (p == null || st == null) {
            clear()
            return
        }
        try {
            if (frameAvailable) {
                frameAvailable = false
                st.updateTexImage()
                st.getTransformMatrix(texMatrix)
                frameTimeSeconds = st.timestamp / 1_000_000_000f
                frameVersion++
            }
            val vw = videoWidth
            val vh = videoHeight
            if (frameVersion == 0L || vw <= 0 || vh <= 0) {
                clear()
                return
            }
            // Preview at up to 1280 px on the long side; export uses the full size.
            val scale = minOf(1f, PREVIEW_MAX_SIDE / max(vw, vh).toFloat())
            val workW = max(2, (vw * scale).roundToInt())
            val workH = max(2, (vh * scale).roundToInt())
            var s = state
            if (original) s = EditState(geometry = s.geometry)
            if (cropMode) s = s.copy(geometry = s.geometry.copy(crop = com.lens035.core.model.CropRect.FULL))
            val out = GeometryMath.outputSize(workW, workH, s.geometry)
            var target = finalTarget
            if (target == null || !target.matches(out.width, out.height, false)) {
                target?.release()
                target = RenderTarget(out.width, out.height, false, mipmapped = true)
                finalTarget = target
            }
            val ctx = RenderContext(workW, workH, vw, vh, out.width, out.height, frameTimeSeconds, frameVersion.toInt())
            val params = ShaderParams.build(s, ctx, stats, dateText)
            val res = FrameResources(LutCache.film(s.film?.id), LutCache.custom(s.customLut), { DateStamp.bitmap(it) })
            p.render(
                PipelineInput(oesTex, InputKind.EXTERNAL_OES, workW, workH, frameVersion, texMatrix, transfer),
                params, res, PipelineOutput.Target(target),
            )
            val vt = ViewTransform.fit(viewWidth, viewHeight, target.width, target.height, marginPx)
            p.present(target.texture, viewWidth, viewHeight, vt.toUniform(viewWidth, viewHeight), background)
        } catch (t: Throwable) {
            Log.e(TAG, "Video render failed", t)
            onError?.invoke(t)
        }
    }

    private fun clear() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    }

    /** GL thread: frees GL objects. The surface itself is released on the main thread. */
    fun releaseGl() {
        try {
            finalTarget?.release()
            pipeline?.release()
            surfaceTexture?.release()
            GlUtil.deleteTexture(oesTex)
        } catch (_: Throwable) {
        }
        finalTarget = null
        pipeline = null
        surfaceTexture = null
        oesTex = 0
        val s = surface
        surface = null
        main.post {
            onSurface(null)
            s?.release()
        }
    }

    companion object {
        private const val TAG = "VideoRenderer"
        const val PREVIEW_MAX_SIDE = 1280f
    }
}
