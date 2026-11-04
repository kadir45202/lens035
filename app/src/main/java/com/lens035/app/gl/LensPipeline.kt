package com.lens035.app.gl

import android.graphics.Bitmap
import android.opengl.GLES11Ext
import android.opengl.GLES30
import com.lens035.core.film.Lut3D
import com.lens035.core.render.RenderParams
import com.lens035.core.render.ShaderParams
import kotlin.math.max
import kotlin.math.roundToInt

/** How the pipeline should read its input texture. */
enum class InputKind {
    /** Row 0 is the top of the picture (uploaded bitmaps). Used directly as the source. */
    TEXTURE_TOP_DOWN,

    /** Row 0 is the bottom of the picture (Media3 frames). Copied with a flip first. */
    TEXTURE_BOTTOM_UP,

    /** SurfaceTexture frame; sampled with [PipelineInput.texMatrix]. */
    EXTERNAL_OES,
}

/** HDR transfer of an external video frame, for preview tone mapping. */
object Transfer {
    const val SDR = 0
    const val HLG = 1
    const val PQ = 2
}

class PipelineInput(
    val texture: Int,
    val kind: InputKind,
    /** Working resolution. For TEXTURE_TOP_DOWN this must be the texture's own size. */
    val width: Int,
    val height: Int,
    /** Changes whenever the input pixels change, so cached blur levels are rebuilt. */
    val version: Long,
    val texMatrix: FloatArray? = null,
    val transfer: Int = Transfer.SDR,
)

/** Per-frame resources referenced by [RenderParams]. */
class FrameResources(
    val filmLut: Lut3D?,
    val customLut: Lut3D?,
    /** Supplies the date stamp glyph bitmap when the date effect is active. */
    val dateBitmap: ((text: String) -> Bitmap)? = null,
)

/** Where the effects pass draws. */
sealed class PipelineOutput {
    /** Our own top-down target. */
    class Target(val target: RenderTarget) : PipelineOutput()

    /** A framebuffer owned by someone else (Media3); rows are bottom-up when [flipY]. */
    class External(val framebuffer: Int, val width: Int, val height: Int, val flipY: Boolean) : PipelineOutput()
}

/**
 * The GPU implementation of the editor: import -> blur pyramid -> colour pipeline ->
 * (bloom pyramid) -> effects. One instance belongs to one GL context.
 */
class LensPipeline(private val sources: ShaderSources) {
    private lateinit var importProgram: ShaderProgram
    private lateinit var importOesProgram: ShaderProgram
    private lateinit var downProgram: ShaderProgram
    private lateinit var blurProgram: ShaderProgram
    private lateinit var mainProgram: ShaderProgram
    private lateinit var effectsProgram: ShaderProgram
    private lateinit var presentProgram: ShaderProgram

    private var vao = 0
    private var vbo = 0
    private var hdrTargets = false
    var maxTextureSize = 4096
        private set

    private var dummyLut = 0
    private var dummyCurves = 0
    private var dummyOverlay = 0

    private var lutTex = 0
    private var lutKey: String? = null
    private var lutSize = 2f
    private var lut2Tex = 0
    private var lut2Key: String? = null
    private var lut2Size = 2f
    private var curvesTex = 0
    private var curvesKey: String? = null
    private var overlayTex = 0
    private var overlayKey: String? = null

    private var work: RenderTarget? = null
    private val pyrA = arrayOfNulls<RenderTarget>(LEVELS)
    private val pyrATmp = arrayOfNulls<RenderTarget>(LEVELS)
    private val pyrB = arrayOfNulls<RenderTarget>(LEVELS)
    private val pyrBTmp = arrayOfNulls<RenderTarget>(LEVELS)
    private var graded: RenderTarget? = null
    private var pyrAKey: String? = null

    private var initialized = false

    fun init() {
        if (initialized) return
        val vert = sources["quad.vert"]
        importProgram = ShaderProgram(vert, sources["import2d.frag"], "import2d")
        importOesProgram = ShaderProgram(vert, sources["import_oes.frag"], "import_oes")
        downProgram = ShaderProgram(vert, sources["down.frag"], "down")
        blurProgram = ShaderProgram(vert, sources["blur.frag"], "blur")
        mainProgram = ShaderProgram(vert, sources["main.frag"], "main")
        effectsProgram = ShaderProgram(vert, sources["effects.frag"], "effects")
        presentProgram = ShaderProgram(vert, sources["present.frag"], "present")

        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0)
        vao = ids[0]
        GLES30.glGenBuffers(1, ids, 0)
        vbo = ids[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        val quad = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quad.size * 4, GlUtil.floatBuffer(quad), GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        hdrTargets = GlUtil.supportsHalfFloatTargets()
        maxTextureSize = GlUtil.maxTextureSize()
        dummyLut = GlUtil.createTexture3D(2, Lut3D.identity(2).toRgbaHalf())
        dummyCurves = GlUtil.createFloatTexture(256, 1, ShaderParams.curveTable(com.lens035.core.model.Curves()))
        dummyOverlay = GlUtil.createTexture(1, 1, false)
        GlUtil.checkError("LensPipeline.init")
        initialized = true
    }

    fun release() {
        if (!initialized) return
        listOf(importProgram, importOesProgram, downProgram, blurProgram, mainProgram, effectsProgram, presentProgram).forEach { it.release() }
        GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
        listOf(dummyLut, dummyCurves, dummyOverlay, lutTex, lut2Tex, curvesTex, overlayTex).forEach { GlUtil.deleteTexture(it) }
        lutTex = 0; lut2Tex = 0; curvesTex = 0; overlayTex = 0
        lutKey = null; lut2Key = null; curvesKey = null; overlayKey = null
        releaseTargets()
        initialized = false
    }

    private fun releaseTargets() {
        work?.release(); work = null
        graded?.release(); graded = null
        for (i in 0 until LEVELS) {
            pyrA[i]?.release(); pyrA[i] = null
            pyrATmp[i]?.release(); pyrATmp[i] = null
            pyrB[i]?.release(); pyrB[i] = null
            pyrBTmp[i]?.release(); pyrBTmp[i] = null
        }
        pyrAKey = null
    }

    private fun ensure(current: RenderTarget?, w: Int, h: Int, hdr: Boolean, mip: Boolean = false): RenderTarget {
        if (current != null && current.matches(w, h, hdr)) return current
        current?.release()
        return RenderTarget(w, h, hdr, mip)
    }

    private fun drawQuad(program: ShaderProgram, flipY: Boolean) {
        program.set1f("u_flipY", if (flipY) 1f else 0f)
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    private fun resetState() {
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_STENCIL_TEST)
    }

    /** Full render of one frame. */
    fun render(input: PipelineInput, params: RenderParams, res: FrameResources, output: PipelineOutput) {
        check(initialized) { "LensPipeline.init() not called" }
        resetState()
        val w = input.width
        val h = input.height

        // 1. Source texture in top-down orientation.
        val srcTex: Int = when (input.kind) {
            InputKind.TEXTURE_TOP_DOWN -> input.texture
            InputKind.TEXTURE_BOTTOM_UP, InputKind.EXTERNAL_OES -> {
                val t = ensure(work, w, h, false).also { work = it }
                t.bind()
                if (input.kind == InputKind.EXTERNAL_OES) {
                    importOesProgram.use()
                    importOesProgram.bindTexture("u_tex", 0, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, input.texture)
                    importOesProgram.setMatrix4("u_texMatrix", input.texMatrix ?: IDENTITY4)
                    importOesProgram.set1i("u_transfer", input.transfer)
                    drawQuad(importOesProgram, false)
                } else {
                    importProgram.use()
                    importProgram.bindTexture("u_tex", 0, GLES30.GL_TEXTURE_2D, input.texture)
                    importProgram.set1f("u_flipIn", 1f)
                    drawQuad(importProgram, false)
                }
                t.texture
            }
        }

        // 2. Blur pyramid of the source (cached while the input is unchanged).
        val key = "${input.version}:$w:$h:$srcTex"
        if (key != pyrAKey) {
            buildPyramid(srcTex, w, h, pyrA, pyrATmp, firstLevelLuma = true)
            pyrAKey = key
        }

        // 3. Colour pipeline.
        val g = ensure(graded, w, h, false).also { graded = it }
        g.bind()
        mainProgram.use()
        mainProgram.apply(params.main)
        mainProgram.bindTexture("u_src", 0, GLES30.GL_TEXTURE_2D, srcTex)
        mainProgram.bindTexture("u_pyr1", 1, GLES30.GL_TEXTURE_2D, pyrA[0]!!.texture)
        mainProgram.bindTexture("u_pyr2", 2, GLES30.GL_TEXTURE_2D, pyrA[1]!!.texture)
        mainProgram.bindTexture("u_pyr3", 3, GLES30.GL_TEXTURE_2D, pyrA[2]!!.texture)
        mainProgram.bindTexture("u_pyr4", 4, GLES30.GL_TEXTURE_2D, pyrA[3]!!.texture)
        bindLut(res.filmLut)
        mainProgram.bindTexture("u_lut", 5, GLES30.GL_TEXTURE_3D, if (res.filmLut != null) lutTex else dummyLut)
        mainProgram.set1f("u_lutSize", if (res.filmLut != null) lutSize else 2f)
        bindLut2(res.customLut)
        mainProgram.bindTexture("u_lut2", 6, GLES30.GL_TEXTURE_3D, if (res.customLut != null) lut2Tex else dummyLut)
        mainProgram.set1f("u_lut2Size", if (res.customLut != null) lut2Size else 2f)
        mainProgram.bindTexture("u_curves", 7, GLES30.GL_TEXTURE_2D, curvesTexture(params))
        drawQuad(mainProgram, false)

        // 4. Bloom pyramid of the graded image, only when an effect needs it.
        if (params.needsBloomPyramid) {
            buildPyramid(g.texture, w, h, pyrB, pyrBTmp, firstLevelLuma = false)
        }

        // 5. Effects into the output.
        val flip: Boolean
        when (output) {
            is PipelineOutput.Target -> {
                output.target.bind()
                flip = false
            }
            is PipelineOutput.External -> {
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, output.framebuffer)
                GLES30.glViewport(0, 0, output.width, output.height)
                flip = output.flipY
            }
        }
        effectsProgram.use()
        effectsProgram.apply(params.effects)
        effectsProgram.bindTexture("u_graded", 0, GLES30.GL_TEXTURE_2D, g.texture)
        val bloom = params.needsBloomPyramid
        effectsProgram.bindTexture("u_bloomH", 1, GLES30.GL_TEXTURE_2D, if (bloom) pyrB[0]!!.texture else g.texture)
        effectsProgram.bindTexture("u_bloomA", 2, GLES30.GL_TEXTURE_2D, if (bloom) pyrB[1]!!.texture else g.texture)
        effectsProgram.bindTexture("u_bloomB", 3, GLES30.GL_TEXTURE_2D, if (bloom) pyrB[2]!!.texture else g.texture)
        effectsProgram.bindTexture("u_bloomC", 4, GLES30.GL_TEXTURE_2D, if (bloom) pyrB[3]!!.texture else g.texture)
        effectsProgram.bindTexture("u_overlay", 5, GLES30.GL_TEXTURE_2D, overlayTexture(params, res))
        drawQuad(effectsProgram, flip)
        if (output is PipelineOutput.Target) output.target.generateMipmaps()
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GlUtil.checkError("LensPipeline.render")
    }

    /**
     * Draws a finished top-down texture into the current default framebuffer, letterboxed
     * and transformed by [view] (scale x/y, offset x/y in view UV with v = 0 at the top).
     */
    fun present(texture: Int, viewWidth: Int, viewHeight: Int, view: FloatArray, background: FloatArray) {
        resetState()
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, viewWidth, viewHeight)
        presentProgram.use()
        presentProgram.bindTexture("u_tex", 0, GLES30.GL_TEXTURE_2D, texture)
        presentProgram.set4f("u_view", view[0], view[1], view[2], view[3])
        presentProgram.set3f("u_bg", background[0], background[1], background[2])
        drawQuad(presentProgram, true)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    }

    private fun buildPyramid(
        base: Int,
        w: Int,
        h: Int,
        levels: Array<RenderTarget?>,
        temps: Array<RenderTarget?>,
        firstLevelLuma: Boolean,
    ) {
        var prevTex = base
        var pw = w
        var ph = h
        for (i in 0 until LEVELS) {
            val lw = max(1, (pw / 2f).roundToInt())
            val lh = max(1, (ph / 2f).roundToInt())
            val t = ensure(levels[i], lw, lh, hdrTargets).also { levels[i] = it }
            t.bind()
            downProgram.use()
            downProgram.bindTexture("u_tex", 0, GLES30.GL_TEXTURE_2D, prevTex)
            downProgram.set2f("u_texel", 1f / pw, 1f / ph)
            downProgram.set1f("u_lumaAlpha", if (i == 0 && firstLevelLuma) 1f else 0f)
            drawQuad(downProgram, false)
            if (i >= 1) {
                val tmp = ensure(temps[i], lw, lh, hdrTargets).also { temps[i] = it }
                tmp.bind()
                blurProgram.use()
                blurProgram.bindTexture("u_tex", 0, GLES30.GL_TEXTURE_2D, t.texture)
                blurProgram.set2f("u_dir", 1f / lw, 0f)
                drawQuad(blurProgram, false)
                t.bind()
                blurProgram.bindTexture("u_tex", 0, GLES30.GL_TEXTURE_2D, tmp.texture)
                blurProgram.set2f("u_dir", 0f, 1f / lh)
                drawQuad(blurProgram, false)
            }
            prevTex = t.texture
            pw = lw
            ph = lh
        }
    }

    private fun bindLut(lut: Lut3D?) {
        if (lut == null || lut.key == lutKey) return
        GlUtil.deleteTexture(lutTex)
        lutTex = GlUtil.createTexture3D(lut.size, lut.toRgbaHalf())
        lutKey = lut.key
        lutSize = lut.size.toFloat()
    }

    private fun bindLut2(lut: Lut3D?) {
        if (lut == null || lut.key == lut2Key) return
        GlUtil.deleteTexture(lut2Tex)
        lut2Tex = GlUtil.createTexture3D(lut.size, lut.toRgbaHalf())
        lut2Key = lut.key
        lut2Size = lut.size.toFloat()
    }

    private fun curvesTexture(params: RenderParams): Int {
        if (params.curves.isDefault) return dummyCurves
        if (params.curvesKey != curvesKey || curvesTex == 0) {
            GlUtil.deleteTexture(curvesTex)
            curvesTex = GlUtil.createFloatTexture(256, 1, ShaderParams.curveTable(params.curves))
            curvesKey = params.curvesKey
        }
        return curvesTex
    }

    private fun overlayTexture(params: RenderParams, res: FrameResources): Int {
        val spec = params.dateStamp ?: return dummyOverlay
        val provider = res.dateBitmap ?: return dummyOverlay
        val key = spec.text
        if (key != overlayKey || overlayTex == 0) {
            val bmp = provider(spec.text)
            if (overlayTex == 0) {
                overlayTex = GlUtil.createTexture(bmp)
            } else {
                GlUtil.updateTexture(overlayTex, bmp)
            }
            overlayKey = key
        }
        return overlayTex
    }

    companion object {
        const val LEVELS = 4
        private val IDENTITY4 = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
    }
}
