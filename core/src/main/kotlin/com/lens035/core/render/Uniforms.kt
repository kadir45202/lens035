package com.lens035.core.render

/** A typed uniform value; the GL layer switches on the subtype to upload it. */
sealed class UniformValue {
    class F(val v: Float) : UniformValue()
    class V2(val x: Float, val y: Float) : UniformValue()
    class V3(val x: Float, val y: Float, val z: Float) : UniformValue()
    class V4(val x: Float, val y: Float, val z: Float, val w: Float) : UniformValue()
    /** An array of vec4, [data].size is a multiple of 4. */
    class V4Array(val data: FloatArray) : UniformValue()
    /** Column-major 3x3 matrix. */
    class M3(val data: FloatArray) : UniformValue()
    class I(val v: Int) : UniformValue()
}

/** Ordered collection of named uniforms for one shader program. */
class UniformSet {
    private val values = LinkedHashMap<String, UniformValue>()

    val entries: Set<Map.Entry<String, UniformValue>> get() = values.entries

    operator fun get(name: String): UniformValue? = values[name]

    fun f(name: String, v: Float) { values[name] = UniformValue.F(v) }
    fun v2(name: String, x: Float, y: Float) { values[name] = UniformValue.V2(x, y) }
    fun v3(name: String, x: Float, y: Float, z: Float) { values[name] = UniformValue.V3(x, y, z) }
    fun v3(name: String, a: FloatArray) { values[name] = UniformValue.V3(a[0], a[1], a[2]) }
    fun v4(name: String, x: Float, y: Float, z: Float, w: Float) { values[name] = UniformValue.V4(x, y, z, w) }
    fun v4(name: String, a: FloatArray) { values[name] = UniformValue.V4(a[0], a[1], a[2], a[3]) }
    fun v4a(name: String, data: FloatArray) {
        require(data.size % 4 == 0)
        values[name] = UniformValue.V4Array(data)
    }
    fun m3(name: String, data: FloatArray) {
        require(data.size == 9)
        values[name] = UniformValue.M3(data)
    }
    fun i(name: String, v: Int) { values[name] = UniformValue.I(v) }

    fun float(name: String): Float = (values[name] as? UniformValue.F)?.v ?: error("no float uniform $name")

    fun vec4(name: String): FloatArray = when (val u = values[name]) {
        is UniformValue.V4 -> floatArrayOf(u.x, u.y, u.z, u.w)
        else -> error("no vec4 uniform $name")
    }

    /** JSON for the WebGL validation harness: {"name": {"t": "f", "v": [...]}, ...}. */
    fun toJson(): String = buildString {
        append('{')
        var first = true
        for ((name, u) in values) {
            if (!first) append(',')
            first = false
            append('"').append(name).append("\":")
            val (type, data) = when (u) {
                is UniformValue.F -> "f" to floatArrayOf(u.v)
                is UniformValue.V2 -> "v2" to floatArrayOf(u.x, u.y)
                is UniformValue.V3 -> "v3" to floatArrayOf(u.x, u.y, u.z)
                is UniformValue.V4 -> "v4" to floatArrayOf(u.x, u.y, u.z, u.w)
                is UniformValue.V4Array -> "v4a" to u.data
                is UniformValue.M3 -> "m3" to u.data
                is UniformValue.I -> "i" to floatArrayOf(u.v.toFloat())
            }
            append("{\"t\":\"").append(type).append("\",\"v\":[")
            data.forEachIndexed { i, f ->
                if (i > 0) append(',')
                append(if (f.isFinite()) f.toString() else "0")
            }
            append("]}")
        }
        append('}')
    }
}
