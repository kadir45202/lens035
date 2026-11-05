package com.lens035.app.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.lens035.core.model.EditState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class Project(
    val id: String,
    /** "photo" or "video". */
    val kind: String,
    val uri: String,
    val createdAt: Long,
    val updatedAt: Long,
    val state: EditState,
)

/**
 * Recent edits, so a photo can be reopened exactly where it was left. Each project lives in
 * filesDir/projects/<id>/ with project.json, a small thumbnail and (for photos) a preview copy
 * that keeps the project usable even if the original file's permission expires.
 */
class ProjectStore(context: Context) {
    private val root = File(context.filesDir, "projects").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    fun dir(id: String): File = File(root, id)
    fun thumbFile(id: String): File = File(dir(id), "thumb.jpg")
    fun previewFile(id: String): File = File(dir(id), "preview.jpg")

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val list = root.listFiles()?.mapNotNull { d ->
            val f = File(d, "project.json")
            if (!f.exists()) return@mapNotNull null
            try {
                json.decodeFromString(Project.serializer(), f.readText())
            } catch (e: Exception) {
                Log.w(TAG, "Skipping unreadable project ${d.name}", e)
                null
            }
        }.orEmpty().sortedByDescending { it.updatedAt }
        _projects.value = list
    }

    fun get(id: String): Project? {
        _projects.value.firstOrNull { it.id == id }?.let { return it }
        val f = File(dir(id), "project.json")
        if (!f.exists()) return null
        return try {
            json.decodeFromString(Project.serializer(), f.readText())
        } catch (_: Exception) {
            null
        }
    }

    suspend fun save(project: Project, thumbnail: Bitmap?, preview: Bitmap? = null) = withContext(Dispatchers.IO) {
        val d = dir(project.id).apply { mkdirs() }
        val tmp = File(d, "project.json.tmp")
        tmp.writeText(json.encodeToString(Project.serializer(), project))
        if (!tmp.renameTo(File(d, "project.json"))) {
            File(d, "project.json").writeText(tmp.readText())
            tmp.delete()
        }
        thumbnail?.let { bmp -> thumbFile(project.id).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) } }
        preview?.let { bmp -> previewFile(project.id).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 94, it) } }
        val others = _projects.value.filter { it.id != project.id }
        _projects.value = (listOf(project) + others).sortedByDescending { it.updatedAt }.take(MAX_PROJECTS)
        trim()
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dir(id).deleteRecursively()
        _projects.value = _projects.value.filter { it.id != id }
    }

    private fun trim() {
        val keep = _projects.value.map { it.id }.toSet()
        root.listFiles()?.forEach { d ->
            if (d.isDirectory && d.name !in keep && File(d, "project.json").exists()) {
                val p = try {
                    json.decodeFromString(Project.serializer(), File(d, "project.json").readText())
                } catch (_: Exception) {
                    null
                }
                if (p == null || _projects.value.size >= MAX_PROJECTS) d.deleteRecursively()
            }
        }
    }

    companion object {
        private const val TAG = "ProjectStore"
        const val MAX_PROJECTS = 40
    }
}
