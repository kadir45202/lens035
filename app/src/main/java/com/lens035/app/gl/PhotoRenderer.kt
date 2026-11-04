package com.lens035.app.gl

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import com.lens035.app.media.DateStamp
import com.lens035.core.analysis.ImageStats
import com.lens035.core.model.CropRect
import com.lens035.core.model.EditState
import com.lens035.core.model.GeometryMath
import com.lens035.core.render.RenderContext
import com.lens035.core.render.ShaderParams
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Live preview renderer for photos (GLSurfaceView, render-when-dirty). The UI thread pushes
 * state through the setters; the GL thread renders the pipeline into a mipmapped texture and
 * presents it with the current zoom/pan. Pure zoom/pan changes only re-present.
 */
class PhotoRenderer(private val context: Context, private val view: GLSurfaceView) : GLSurfaceView.Renderer {

    private class Snapshot(
        val source: Bitmap?,
        val sourceVersion: Long,
        val fullWidth: Int,
        val fullHeight: Int,
        val state: EditState,
        val stats: ImageStats,
        val dateText: String,
        val mode: PreviewMode,
    )

    @Volatile
    private var snapshot = Snapshot(null, 0, 0, 0, EditState(), ImageStats.NEUTRAL, "", PreviewMode.NORMAL)

    @Volatile private var zoom = 1f
    @Volatile private var panX = 0f
    @Volatile private var panY = 0f
    @Volatile var marginPx = 0f

    /** Called on the GL thread when a render failed; the UI shows a message. */
    @Volatile var onError: ((Throwable) -> Unit)? = null

    private var pipeline: LensPipeline? = null
    private var sourceTex = 0
    private var uploadedVersion = -1L
    private var finalTarget: RenderTarget? = null
    private var renderedState: EditState? = null
    private var renderedVersion = -1L
    private var renderedMode: PreviewMode? = null
    private var viewWidth = 0
    private var viewHeight = 0

    private val background = floatArrayOf(0f, 0f, 0f)

    fun setSource(bitmap: Bitmap, fullWidth: Int, fullHeight: Int, stats: ImageStats, dateText: String) {
        val s = snapshot
        snapshot = Snapshot(bitmap, s.sourceVersion + 1, fullWidth, fullHeight, s.state, stats, dateText, s.mode)
        view.requestRender()
    }

    fun setState(state: EditState) {
        val s = snapshot
        if (s.state == state) return
        snapshot = Snapshot(s.source, s.sourceVersion, s.fullWidth, s.fullHeight, state, s.stats, s.dateText, s.mode)
        view.requestRender()
    }

    fun setMode(mode: PreviewMode) {
        val s = snapshot
        if (s.mode == mode) return
        snapshot = Snapshot(s.source, s.sourceVersion, s.fullWidth, s.fullHeight, s.state, s.stats, s.dateText, mode)
        view.requestRender()
    }

    fun setStats(stats: ImageStats) {
        val s = snapshot
        snapshot = Snapshot(s.source, s.sourceVersion, s.fullWidth, s.fullHeight, s.state, stats, s.dateText, s.mode)
        view.requestRender()
    }

    fun setView(zoom: Float, panX: Float, panY: Float) {
        this.zoom = zoom
        this.panX = panX
        this.panY = panY
        view.requestRender()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // A new context: every previous GL object is gone with the old one.
        pipeline = null
        sourceTex = 0
        uploadedVersion = -1
        finalTarget = null
        renderedState = null
        try {
            pipeline = LensPipeline(ShaderSources.get(context)).also { it.init() }
        } catch (t: Throwable) {
            Log.e(TAG, "Pipeline init failed", t)
            onError?.invoke(t)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val p = pipeline
        val snap = snapshot
        val bmp = snap.source
        if (p == null || bmp == null || bmp.isRecycled) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glClearColor(background[0], background[1], background[2], 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            return
        }
        try {
            if (uploadedVersion != snap.sourceVersion || sourceTex == 0) {
                GlUtil.deleteTexture(sourceTex)
                sourceTex = GlUtil.createTexture(bmp)
                uploadedVersion = snap.sourceVersion
                renderedState = null
            }
            val state = effectiveState(snap.state, snap.mode)
            var target = finalTarget
            if (target == null || renderedState != state || renderedVersion != uploadedVersion || renderedMode != snap.mode) {
                val out = GeometryMath.outputSize(bmp.width, bmp.height, state.geometry)
                if (target == null || !target.matches(out.width, out.height, false)) {
                    target?.release()
                    target = RenderTarget(out.width, out.height, false, mipmapped = true)
                    finalTarget = target
                }
                val ctx = RenderContext(bmp.width, bmp.height, snap.fullWidth.coerceAtLeast(1), snap.fullHeight.coerceAtLeast(1), out.width, out.height)
                val params = ShaderParams.build(state, ctx, snap.stats, snap.dateText)
                val res = FrameResources(
                    filmLut = LutCache.film(state.film?.id),
                    customLut = LutCache.custom(state.customLut),
                    dateBitmap = { DateStamp.bitmap(it) },
                )
                p.render(
                    PipelineInput(sourceTex, InputKind.TEXTURE_TOP_DOWN, bmp.width, bmp.height, uploadedVersion),
                    params, res, PipelineOutput.Target(target),
                )
                renderedState = state
                renderedVersion = uploadedVersion
                renderedMode = snap.mode
            }
            val vt = ViewTransform.fit(viewWidth, viewHeight, target.width, target.height, marginPx, zoom, panX, panY)
            p.present(target.texture, viewWidth, viewHeight, vt.toUniform(viewWidth, viewHeight), background)
        } catch (t: Throwable) {
            Log.e(TAG, "Render failed", t)
            renderedState = null
            onError?.invoke(t)
        }
    }

    /** Must be called on the GL thread (queueEvent) before the view goes away. */
    fun releaseGl() {
        try {
            finalTarget?.release()
            GlUtil.deleteTexture(sourceTex)
            pipeline?.release()
        } catch (_: Throwable) {
        }
        finalTarget = null
        sourceTex = 0
        pipeline = null
    }

    companion object {
        private const val TAG = "PhotoRenderer"

        /** What the preview actually shows for a given mode. */
        fun effectiveState(state: EditState, mode: PreviewMode): EditState = when (mode) {
            PreviewMode.NORMAL -> state
            PreviewMode.ORIGINAL -> EditState(geometry = state.geometry)
            PreviewMode.CROP -> state.copy(geometry = state.geometry.copy(crop = CropRect.FULL), fx = state.fx.filter { it.type != com.lens035.core.model.EffectType.FRAME })
        }
    }
}
