package com.lens035.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What can be starred: films by id, effects by type name, looks by id. */
enum class FavoriteKind(internal val key: String) { FILM("films"), EFFECT("effects"), LOOK("looks") }

/** Starred films, effects and looks; shown first under "Favoriler" in their strips. */
class FavoritesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("lens035_favorites", Context.MODE_PRIVATE)
    private val flows = FavoriteKind.entries.associateWith { kind ->
        MutableStateFlow(prefs.getStringSet(kind.key, emptySet())?.toSet().orEmpty())
    }

    fun of(kind: FavoriteKind): StateFlow<Set<String>> = flows.getValue(kind).asStateFlow()

    /** Stars or unstars [id]; returns true when it is now a favourite. */
    fun toggle(kind: FavoriteKind, id: String): Boolean {
        val flow = flows.getValue(kind)
        val now = flow.value.let { if (id in it) it - id else it + id }
        flow.value = now
        prefs.edit().putStringSet(kind.key, now).apply()
        return id in now
    }
}
