package com.lens035.app.gl

import android.content.Context
import android.opengl.GLES30
import com.lens035.core.render.UniformSet
import com.lens035.core.render.UniformValue

/** A linked program with a uniform-location cache. GL thread only. */
class ShaderProgram(vertexSource: String, fragmentSource: String, val name: String) {
    val id: Int = GlUtil.linkProgram(vertexSource, fragmentSource, name)
    private val locations = HashMap<String, Int>()

    fun use() = GLES30.glUseProgram(id)

    fun location(uniform: String): Int = locations.getOrPut(uniform) { GLES30.glGetUniformLocation(id, uniform) }

    fun set1f(uniform: String, v: Float) {
        val loc = location(uniform)
        if (loc >= 0) GLES30.glUniform1f(loc, v)
    }

    fun set1i(uniform: String, v: Int) {
        val loc = location(uniform)
        if (loc >= 0) GLES30.glUniform1i(loc, v)
    }

    fun set2f(uniform: String, x: Float, y: Float) {
        val loc = location(uniform)
        if (loc >= 0) GLES30.glUniform2f(loc, x, y)
    }

    fun set3f(uniform: String, x: Float, y: Float, z: Float) {
        val loc = location(uniform)
        if (loc >= 0) GLES30.glUniform3f(loc, x, y, z)
    }

    fun set4f(uniform: String, x: Float, y: Float, z: Float, w: Float) {
        val loc = location(uniform)
        if (loc >= 0) GLES30.glUniform4f(loc, x, y, z, w)
    }

    fun setMatrix4(uniform: String, m: FloatArray) {
        val loc = location(uniform)
        if (loc >= 0) GLES30.glUniformMatrix4fv(loc, 1, false, m, 0)
    }

    /** Uploads every uniform of [set]; names the shader does not declare are ignored. */
    fun apply(set: UniformSet) {
        for ((uniform, value) in set.entries) {
            val loc = location(uniform)
            if (loc < 0) continue
            when (value) {
                is UniformValue.F -> GLES30.glUniform1f(loc, value.v)
                is UniformValue.V2 -> GLES30.glUniform2f(loc, value.x, value.y)
                is UniformValue.V3 -> GLES30.glUniform3f(loc, value.x, value.y, value.z)
                is UniformValue.V4 -> GLES30.glUniform4f(loc, value.x, value.y, value.z, value.w)
                is UniformValue.V4Array -> GLES30.glUniform4fv(loc, value.data.size / 4, value.data, 0)
                is UniformValue.M3 -> GLES30.glUniformMatrix3fv(loc, 1, false, value.data, 0)
                is UniformValue.I -> GLES30.glUniform1i(loc, value.v)
            }
        }
    }

    fun bindTexture(uniform: String, unit: Int, target: Int, texture: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(target, texture)
        set1i(uniform, unit)
    }

    fun release() {
        GLES30.glDeleteProgram(id)
        locations.clear()
    }
}

/** A texture with an attached framebuffer. */
class RenderTarget(val width: Int, val height: Int, val hdr: Boolean, private val mipmapped: Boolean = false) {
    val texture: Int = GlUtil.createTexture(width, height, hdr)
    val framebuffer: Int = GlUtil.createFramebuffer(texture)

    init {
        if (mipmapped) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        }
    }

    fun matches(w: Int, h: Int, hdrFormat: Boolean): Boolean = width == w && height == h && hdr == hdrFormat

    fun bind() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
        GLES30.glViewport(0, 0, width, height)
    }

    fun generateMipmaps() {
        if (!mipmapped) return
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
    }

    fun release() {
        GlUtil.deleteFramebuffer(framebuffer)
        GlUtil.deleteTexture(texture)
    }
}

/**
 * Shader sources loaded once from assets, with `//#include common` expanded. Thread-safe;
 * the strings are shared by every GL context in the process.
 */
class ShaderSources private constructor(private val map: Map<String, String>) {
    operator fun get(name: String): String = map[name] ?: throw IllegalArgumentException("Missing shader $name")

    companion object {
        private val NAMES = listOf(
            "quad.vert", "common.glsl", "import2d.frag", "import_oes.frag", "down.frag",
            "blur.frag", "main.frag", "effects.frag", "present.frag",
        )

        @Volatile
        private var instance: ShaderSources? = null

        fun get(context: Context): ShaderSources {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                val raw = NAMES.associateWith { name ->
                    context.applicationContext.assets.open("shaders/$name").bufferedReader().use { it.readText() }
                }
                val common = raw.getValue("common.glsl")
                val expanded = raw.mapValues { (_, src) -> src.replace("//#include common", common) }
                return ShaderSources(expanded).also { instance = it }
            }
        }
    }
}
