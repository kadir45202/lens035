package com.lens035.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Remembers which one-time hints have been shown, so each appears once per install. */
class HintStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("lens035_hints", Context.MODE_PRIVATE)

    /** The hints from [ids] that have not been seen yet, in order. Reads off the main thread. */
    suspend fun pending(ids: List<String>): List<String> = withContext(Dispatchers.IO) {
        ids.filter { !prefs.getBoolean(it, false) }
    }

    fun markSeen(id: String) {
        prefs.edit().putBoolean(id, true).apply()
    }

    companion object {
        const val PHOTO_STRIP = "photo_strip"
        const val PHOTO_COMPARE = "photo_compare"
        const val VIDEO_BASICS = "video_basics"
        const val POSE_BASICS = "pose_basics"
    }
}
