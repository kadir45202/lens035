package com.lens035.app

import android.app.Application
import com.lens035.app.data.FavoritesStore
import com.lens035.app.data.HintStore
import com.lens035.app.data.PresetStore
import com.lens035.app.data.ProjectStore
import com.lens035.app.gl.LutCache
import com.lens035.app.gl.OffscreenRenderer

class Lens035App : Application() {
    /** Shared offscreen GL thread for thumbnails and exports. */
    val offscreen: OffscreenRenderer by lazy { OffscreenRenderer(this) }
    val projects: ProjectStore by lazy { ProjectStore(this) }
    val presets: PresetStore by lazy { PresetStore(this) }
    val hints: HintStore by lazy { HintStore(this) }
    val favorites: FavoritesStore by lazy { FavoritesStore(this) }

    override fun onCreate() {
        super.onCreate()
        LutCache.init(this)
    }
}
