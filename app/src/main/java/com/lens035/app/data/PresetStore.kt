package com.lens035.app.data

import android.content.Context
import android.util.Log
import com.lens035.core.model.EditState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class UserPreset(val id: String, val name: String, val createdAt: Long, val state: EditState)

/** The user's own looks ("Ön ayarlarım") plus the copy/paste clipboard of edit settings. */
class PresetStore(context: Context) {
    private val file = File(context.filesDir, "presets.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val _presets = MutableStateFlow<List<UserPreset>>(emptyList())
    val presets: StateFlow<List<UserPreset>> = _presets.asStateFlow()

    /** Settings copied with "Ayarları kopyala", kept for this app session. */
    @Volatile
    var clipboard: EditState? = null

    suspend fun load() = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext
        try {
            _presets.value = json.decodeFromString(ListSerializer(UserPreset.serializer()), file.readText())
        } catch (e: Exception) {
            Log.w("PresetStore", "Could not read presets", e)
        }
    }

    suspend fun add(name: String, state: EditState) = withContext(Dispatchers.IO) {
        val preset = UserPreset(System.currentTimeMillis().toString(36), name.trim().ifEmpty { "Ön ayar" }, System.currentTimeMillis(), state.lookOnly())
        val list = listOf(preset) + _presets.value
        _presets.value = list
        persist(list)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val list = _presets.value.filter { it.id != id }
        _presets.value = list
        persist(list)
    }

    private fun persist(list: List<UserPreset>) {
        val tmp = File(file.parentFile, "presets.json.tmp")
        tmp.writeText(json.encodeToString(ListSerializer(UserPreset.serializer()), list))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }
}
