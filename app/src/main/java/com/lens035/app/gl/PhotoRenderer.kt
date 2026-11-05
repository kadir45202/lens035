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
import com.lens035.core.model.EffectInstance
import com.lens035.core.model.EffectType
import com.lens035.core.model.Geometry
import com.lens035.core.model.GeometryMath
import com.lens035.core.render.CanvasLayout
import com.lens035.core.render.RenderContext
import com.lens035.core.render.ShaderParams
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.roundToInt

/**
 * Live preview renderer for photos (GLSurfaceView, render-when-dirty). The UI thread pushes
 * state through the setters; the GL thread renders the pipeline into a mipmapped texture and
 * presents it with the current zoom/pan. Pure zoom/pan changes only re-present.
 */
class PhotoRenderer(private val context: Context, private val view: GLSurfaceView) : GLSurfaceView.Renderer {

    private data class Snapshot(
        val source: Bitmap?,
        val sourceVersion: Long,
        val fullWidth: Int,
        val fullHeight: Int,
        val state: EditState,
        val stats: ImageStats,
        val dateText: String,
        val mode: PreviewMode,
        val personMask: PersonMaskData? = null,
    )

    @Volatile
    private var snapshot = Snapshot(null, 0, 0, 0, EditState(), ImageStats.NEUTRAL, "", PreviewMode.NORMAL)

    /** Split compare divider in view pixels (left of it shows the original), or negative when off. */
    @Volatile private var splitPx = -1f

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
    private var renderedMaskKey: String? = null
    private var originalTarget: RenderTarget? = null
    private var originalGeometry: Geometry? = null
    private var originalCanvas: EffectInstance? = null
    private var originalVersion = -1L
    private var viewWidth = 0
    private var viewHeight = 0

    private val background = floatArrayOf(0f, 0f, 0f)

    fun setSource(bitmap: Bitmap, fullWidth: Int, fullHeight: Int, stats: ImageStats, dateText: String) {
        val s = snapshot
        snapshot = s.copy(source = bitmap, sourceVersion = s.sourceVersion + 1, fullWidth = fullWidth, fullHeight = fullHeight, stats = stats, dateText = dateText)
        view.requestRender()
    }

    fun setState(state: EditState) {
        val s = snapshot
        if (s.state == state) return
        snapshot = s.copy(state = state)
        view.requestRender()
    }

    fun setMode(mode: PreviewMode) {
        val s = snapshot
        if (s.mode == mode) return
        snapshot = s.copy(mode = mode)
        view.requestRender()
    }

    fun setStats(stats: ImageStats) {
        snapshot = snapshot.copy(stats = stats)
        view.requestRender()
    }

    /** Person segmentation of the source (same frame as [setSource]), or null if none. */
    fun setPersonMask(mask: PersonMaskData?) {
        val s = snapshot
        if (s.personMask === mask) return
        snapshot = s.copy(personMask = mask)
        view.requestRender()
    }

    /** Turns split compare on at view x [px], or off with null. */
    fun setSplit(px: Float?) {
        val v = px ?: -1f
        if (v == splitPx) return
        splitPx = v
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
        originalTarget = null
        originalGeometry = null
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
            val mask = if (state.needsPersonMask) snap.personMask else null
            var target = finalTarget
            if (target == null || renderedState != state || renderedVersion != uploadedVersion || renderedMode != snap.mode ||
                renderedMaskKey != mask?.key
            ) {
                val out = GeometryMath.outputSize(bmp.width, bmp.height, state.geometry)
                val canvas = CanvasLayout.of(state, out.width, out.height)
                if (target == null || !target.matches(canvas.width, canvas.height, false)) {
                    target?.release()
                    target = RenderTarget(canvas.width, canvas.height, false, mipmapped = true)
                    finalTarget = target
                }
                val ctx = RenderContext(
                    bmp.width, bmp.height, snap.fullWidth.coerceAtLeast(1), snap.fullHeight.coerceAtLeast(1),
                    out.width, out.height, canvas = canvas,
                )
                val params = ShaderParams.build(state, ctx, snap.stats, snap.dateText)
                val res = FrameResources(
                    filmLut = LutCache.film(state.film?.id),
                    customLut = LutCache.custom(state.customLut),
                    dateBitmap = { DateStamp.bitmap(it) },
                    personMask = mask,
                )
                p.render(
                    PipelineInput(sourceTex, InputKind.TEXTURE_TOP_DOWN, bmp.width, bmp.height, uploadedVersion),
                    params, res, PipelineOutput.Target(target),
                )
                renderedState = state
                renderedVersion = uploadedVersion
                renderedMode = snap.mode
                renderedMaskKey = mask?.key
            }
            val vt = ViewTransform.fit(viewWidth, viewHeight, target.width, target.height, marginPx, zoom, panX, panY)
            val uniform = vt.toUniform(viewWidth, viewHeight)
            val split = splitPx
            if (split >= 0f && snap.mode == PreviewMode.NORMAL) {
                val original = originalFor(p, bmp, snap, state)
                val x = split.roundToInt().coerceIn(0, viewWidth)
                p.present(original.texture, viewWidth, viewHeight, uniform, background, clipLeft = 0, clipRight = x)
                p.present(target.texture, viewWidth, viewHeight, uniform, background, clipLeft = x, clipRight = viewWidth)
            } else {
                if (split < 0f) releaseOriginal()
                p.present(target.texture, viewWidth, viewHeight, uniform, background)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Render failed", t)
            renderedState = null
            onError?.invoke(t)
        }
    }

    /** The unedited photo with the same geometry, so both halves of a split line up exactly. */
    private fun originalFor(p: LensPipeline, bmp: Bitmap, snap: Snapshot, state: EditState): RenderTarget {
        val g = state.geometry
        var t = originalTarget
        if (t != null && originalGeometry == g && originalCanvas == state.effect(EffectType.CANVAS) && originalVersion == uploadedVersion) return t
        val orig = effectiveState(state, PreviewMode.ORIGINAL)
        val out = GeometryMath.outputSize(bmp.width, bmp.height, g)
        val canvas = CanvasLayout.of(orig, out.width, out.height)
        if (t == null || !t.matches(canvas.width, canvas.height, false)) {
            t?.release()
            t = RenderTarget(canvas.width, canvas.height, false, mipmapped = true)
            originalTarget = t
        }
        val ctx = RenderContext(
            bmp.width, bmp.height, snap.fullWidth.coerceAtLeast(1), snap.fullHeight.coerceAtLeast(1),
            out.width, out.height, canvas = canvas,
        )
        p.render(
            PipelineInput(sourceTex, InputKind.TEXTURE_TOP_DOWN, bmp.width, bmp.height, uploadedVersion),
            ShaderParams.build(orig, ctx, snap.stats, snap.dateText),
            FrameResources(filmLut = null, customLut = null),
            PipelineOutput.Target(t),
        )
        originalGeometry = g
        originalCanvas = state.effect(EffectType.CANVAS)
        originalVersion = uploadedVersion
        return t
    }

    private fun releaseOriginal() {
        originalTarget?.release()
        originalTarget = null
        originalGeometry = null
    }

    /** Must be called on the GL thread (queueEvent) before the view goes away. */
    fun releaseGl() {
        try {
            releaseOriginal()
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

        /**
         * What the preview actually shows for a given mode. The original keeps the canvas so it
         * lines up with the edit; crop mode shows the bare frame.
         */
        fun effectiveState(state: EditState, mode: PreviewMode): EditState = when (mode) {
            PreviewMode.NORMAL -> state
            PreviewMode.ORIGINAL -> EditState(geometry = state.geometry, fx = state.fx.filter { it.type == EffectType.CANVAS })
            PreviewMode.CROP -> state.copy(
                geometry = state.geometry.copy(crop = CropRect.FULL),
                fx = state.fx.filter { it.type != EffectType.FRAME && it.type != EffectType.CANVAS },
            )
        }
    }
}
