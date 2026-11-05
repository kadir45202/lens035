package com.lens035.app.gl

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.lens035.core.film.FilmCatalog
import com.lens035.core.film.FilmLutBuilder
import com.lens035.core.film.Lut3D
import com.lens035.core.lut.CubeParser
import java.io.File

/**
 * Process-wide cache of colour LUTs. Film LUTs are generated from their recipe on first use
 * (about 40 ms each); imported .cube files are parsed from the app's private LUT folder.
 * Thread-safe: used by the preview GL thread, the export thread and Media3's GL thread.
 */
object LutCache {
    private const val TAG = "LutCache"
    private val films = LruCache<String, Lut3D>(10)
    private val customs = LruCache<String, Lut3D>(4)
    @Volatile
    private var lutDir: File? = null

    fun init(context: Context) {
        lutDir = File(context.filesDir, "luts").apply { mkdirs() }
    }

    fun directory(): File? = lutDir

    fun film(id: String?): Lut3D? {
        if (id == null) return null
        synchronized(films) { films.get(id)?.let { return it } }
        val recipe = FilmCatalog.find(id) ?: return null
        val lut = FilmLutBuilder.build(recipe)
        synchronized(films) { films.put(id, lut) }
        return lut
    }

    fun custom(name: String?): Lut3D? {
        if (name == null) return null
        synchronized(customs) { customs.get(name)?.let { return it } }
        val file = File(lutDir ?: return null, name)
        if (!file.exists()) return null
        return try {
            val lut = CubeParser.parse(file.readText(), "cube:$name:${file.lastModified()}")
            synchronized(customs) { customs.put(name, lut) }
            lut
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse $name", e)
            null
        }
    }

    fun invalidateCustom(name: String) {
        synchronized(customs) { customs.remove(name) }
    }
}
