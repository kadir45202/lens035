package com.lens035.app.gl

import android.graphics.Bitmap
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLUtils
import android.util.Log
import com.lens035.core.color.ColorMath
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

class GlException(message: String) : RuntimeException(message)

/** Thin, explicit OpenGL ES 3.0 helpers. Every function must be called on a GL thread. */
object GlUtil {
    private const val TAG = "LensGL"

    fun checkError(op: String) {
        var first = GLES30.GL_NO_ERROR
        while (true) {
            val error = GLES30.glGetError()
            if (error == GLES30.GL_NO_ERROR) break
            if (first == GLES30.GL_NO_ERROR) first = error
            Log.e(TAG, "$op: glError 0x${Integer.toHexString(error)}")
        }
        if (first != GLES30.GL_NO_ERROR) throw GlException("$op: glError 0x${Integer.toHexString(first)}")
    }

    /** Clears pending errors without throwing (used before handing control back to Media3). */
    fun drainErrors() {
        var guard = 0
        while (GLES30.glGetError() != GLES30.GL_NO_ERROR && guard++ < 32) Unit
    }

    fun compileShader(type: Int, source: String, name: String): Int {
        val shader = GLES30.glCreateShader(type)
        if (shader == 0) throw GlException("glCreateShader failed for $name")
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw GlException("Shader $name failed to compile: $log")
        }
        return shader
    }

    fun linkProgram(vertexSource: String, fragmentSource: String, name: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource, "$name.vert")
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource, "$name.frag")
        val program = GLES30.glCreateProgram()
        if (program == 0) throw GlException("glCreateProgram failed for $name")
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glBindAttribLocation(program, 0, "a_pos")
        GLES30.glLinkProgram(program)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw GlException("Program $name failed to link: $log")
        }
        return program
    }

    private fun setDefaultParams(target: Int, filter: Int) {
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MIN_FILTER, filter)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MAG_FILTER, if (filter == GLES30.GL_NEAREST) GLES30.GL_NEAREST else GLES30.GL_LINEAR)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        if (target == GLES30.GL_TEXTURE_3D) {
            GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)
        }
    }

    fun genTexture(): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        if (ids[0] == 0) throw GlException("glGenTextures failed")
        return ids[0]
    }

    /** Empty 2D texture. [hdr] selects RGBA16F, otherwise RGBA8. */
    fun createTexture(width: Int, height: Int, hdr: Boolean, filter: Int = GLES30.GL_LINEAR): Int {
        val id = genTexture()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        if (hdr) {
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, width, height, 0, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, null)
        } else {
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        }
        setDefaultParams(GLES30.GL_TEXTURE_2D, filter)
        checkError("createTexture ${width}x$height hdr=$hdr")
        return id
    }

    /** Uploads a software ARGB_8888 bitmap as a top-down RGBA8 texture. */
    fun createTexture(bitmap: Bitmap): Int {
        require(bitmap.config == Bitmap.Config.ARGB_8888) { "Bitmap must be ARGB_8888, was ${bitmap.config}" }
        val id = genTexture()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        setDefaultParams(GLES30.GL_TEXTURE_2D, GLES30.GL_LINEAR)
        checkError("createTexture(bitmap ${bitmap.width}x${bitmap.height})")
        return id
    }

    fun updateTexture(id: Int, bitmap: Bitmap) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        checkError("updateTexture")
    }

    /** 3D RGBA16F texture for a colour LUT. [rgbaHalf] holds size^3 * 4 half floats. */
    fun createTexture3D(size: Int, rgbaHalf: ShortArray): Int {
        val id = genTexture()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, id)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        val buffer = ByteBuffer.allocateDirect(rgbaHalf.size * 2).order(ByteOrder.nativeOrder())
        buffer.asShortBuffer().put(rgbaHalf)
        buffer.position(0)
        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGBA16F, size, size, size, 0,
            GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, buffer,
        )
        setDefaultParams(GLES30.GL_TEXTURE_3D, GLES30.GL_LINEAR)
        checkError("createTexture3D($size)")
        return id
    }

    /** 2D RGBA16F texture from float data (width * height * 4 values). */
    fun createFloatTexture(width: Int, height: Int, rgba: FloatArray): Int {
        val half = ShortArray(rgba.size) { ColorMath.floatToHalf(rgba[it]) }
        val id = genTexture()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        val buffer: ShortBuffer = ByteBuffer.allocateDirect(half.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        buffer.put(half)
        buffer.position(0)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, width, height, 0, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, buffer)
        setDefaultParams(GLES30.GL_TEXTURE_2D, GLES30.GL_LINEAR)
        checkError("createFloatTexture")
        return id
    }

    /** Single-channel GL_R8 texture (e.g. a segmentation mask), rows top-down. */
    fun createMaskTexture(width: Int, height: Int, data: ByteArray): Int {
        require(data.size >= width * height)
        val id = genTexture()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        val buffer = ByteBuffer.allocateDirect(width * height).order(ByteOrder.nativeOrder())
        buffer.put(data, 0, width * height)
        buffer.position(0)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, width, height, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, buffer)
        setDefaultParams(GLES30.GL_TEXTURE_2D, GLES30.GL_LINEAR)
        checkError("createMaskTexture")
        return id
    }

    fun createOesTexture(): Int {
        val id = genTexture()
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, id)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        checkError("createOesTexture")
        return id
    }

    fun createFramebuffer(texture: Int): Int {
        val ids = IntArray(1)
        GLES30.glGenFramebuffers(1, ids, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, ids[0])
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, texture, 0)
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            GLES30.glDeleteFramebuffers(1, ids, 0)
            throw GlException("Framebuffer incomplete: 0x${Integer.toHexString(status)}")
        }
        return ids[0]
    }

    fun deleteTexture(id: Int) {
        if (id != 0) GLES30.glDeleteTextures(1, intArrayOf(id), 0)
    }

    fun deleteFramebuffer(id: Int) {
        if (id != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(id), 0)
    }

    fun floatBuffer(data: FloatArray): FloatBuffer {
        val fb = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        fb.put(data)
        fb.position(0)
        return fb
    }

    /** True when the context can render into half-float textures. */
    fun supportsHalfFloatTargets(): Boolean {
        val ext = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: return false
        return ext.contains("GL_EXT_color_buffer_half_float") || ext.contains("GL_EXT_color_buffer_float")
    }

    fun maxTextureSize(): Int {
        val v = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, v, 0)
        return if (v[0] > 0) v[0] else 4096
    }

    /** Reads the currently bound framebuffer into a new ARGB_8888 bitmap (top row first). */
    fun readPixels(width: Int, height: Int): Bitmap {
        val buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 1)
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
        checkError("glReadPixels")
        buffer.position(0)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }
}
