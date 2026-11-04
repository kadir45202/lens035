package com.lens035.app.ui.video

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.lens035.app.Lens035App
import com.lens035.app.data.Project
import com.lens035.app.gl.VideoRenderer
import com.lens035.app.media.BitmapLoader
import com.lens035.app.media.DateStamp
import com.lens035.app.ui.editor.EditSession
import com.lens035.app.ui.editor.renderToolThumbnails
import com.lens035.core.analysis.ImageAnalyzer
import com.lens035.core.model.EditState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** One video edit: source info, trim/speed/audio, the look (shared with photos) and thumbnails. */
class VideoSession(
    private val context: Context,
    val uri: Uri,
    private val scope: CoroutineScope,
    existingProjectId: String?,
) : EditSession(existingProjectId?.let { (context.applicationContext as Lens035App).projects.get(it)?.state } ?: EditState()) {
    private val app = context.applicationContext as Lens035App
    val projectId: String = existingProjectId ?: app.projects.newId()
    private val existing: Project? = existingProjectId?.let { app.projects.get(it) }

    var videoWidth by mutableStateOf(0)
        private set
    var videoHeight by mutableStateOf(0)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var frameRate by mutableFloatStateOf(30f)
        private set
    var trimStartMs by mutableLongStateOf(0L)
    var trimEndMs by mutableLongStateOf(0L)
    var muted by mutableStateOf(false)
    var speed by mutableFloatStateOf(1f)
    var timeline by mutableStateOf<List<ImageBitmap>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var dateTakenMillis: Long? = null
        private set

    private var thumbSource: Bitmap? = null
    private var saveJob: Job? = null

    /** The preview pipeline works at up to 1280 px; overlays use the same size. */
    override val frameWidth: Int get() = previewSize().first
    override val frameHeight: Int get() = previewSize().second

    val dateText: String get() = DateStamp.text(dateTakenMillis)

    fun previewSize(): Pair<Int, Int> {
        val w = videoWidth
        val h = videoHeight
        if (w <= 0 || h <= 0) return 16 to 9
        val scale = minOf(1f, VideoRenderer.PREVIEW_MAX_SIDE / max(w, h).toFloat())
        return max(2, (w * scale).roundToInt()) to max(2, (h * scale).roundToInt())
    }

    /** Display size reported by the player (rotation already applied). */
    fun setVideoSize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            videoWidth = width
            videoHeight = height
        }
    }

    fun load() {
        scope.launch {
            try {
                val info = withContext(Dispatchers.IO) { readInfo() }
                durationMs = info.duration
                if (trimEndMs <= 0L || trimEndMs > info.duration) trimEndMs = info.duration
                if (videoWidth == 0) {
                    videoWidth = info.width
                    videoHeight = info.height
                }
                frameRate = info.fps
                dateTakenMillis = info.date
                takePersistablePermission()
                val frame = info.frame
                if (frame != null) {
                    stats = withContext(Dispatchers.Default) {
                        val small = BitmapLoader.scaledCopy(frame, 160)
                        val px = IntArray(small.width * small.height)
                        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
                        ImageAnalyzer.analyze(px, small.width, small.height)
                    }
                    val thumb = withContext(Dispatchers.Default) { BitmapLoader.thumbnail(frame, 136, 168) }
                    thumbSource = thumb
                    val (f, e, l) = renderToolThumbnails(app, thumb, stats, dateText)
                    filmThumbs = f
                    effectThumbs = e
                    lookThumbs = l
                }
                timeline = withContext(Dispatchers.IO) { readTimeline(info.duration, 10) }
            } catch (e: Exception) {
                Log.e(TAG, "Video load failed", e)
                error = "Video açılamadı."
            }
        }
    }

    private class Info(val duration: Long, val width: Int, val height: Int, val fps: Float, val date: Long?, val frame: Bitmap?)

    private fun readInfo(): Info {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) {
                val t = w; w = h; h = t
            }
            val fps = if (android.os.Build.VERSION.SDK_INT >= 23) {
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 30f
            } else 30f
            val frame = r.getFrameAtTime(duration * 1000L / 3L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { BitmapLoader.scaledCopy(it.copy(Bitmap.Config.ARGB_8888, false), 720) }
            return Info(duration, w, h, fps.coerceIn(10f, 120f), null, frame)
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun readTimeline(duration: Long, count: Int): List<ImageBitmap> {
        if (duration <= 0L) return emptyList()
        val r = MediaMetadataRetriever()
        val out = ArrayList<ImageBitmap>()
        try {
            r.setDataSource(context, uri)
            for (i in 0 until count) {
                val t = duration * 1000L * (2 * i + 1) / (2 * count)
                val f = r.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
                val small = BitmapLoader.scaledCopy(f.copy(Bitmap.Config.ARGB_8888, false), 160)
                out.add(small.asImageBitmap())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Timeline failed", e)
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
        return out
    }

    private fun takePersistablePermission() {
        if (uri.scheme != "content") return
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
    }

    override suspend fun renderThumbnail(s: EditState): ImageBitmap? {
        val src = thumbSource ?: return null
        return try {
            app.offscreen.execute { gl -> gl.renderAll(src, listOf(s), stats, dateText) }.first().asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    override fun onChanged() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1500)
            saveProject()
        }
    }

    suspend fun saveProject() {
        val src = thumbSource ?: return
        val s = state
        if (s.isDefault && existing == null) return
        try {
            val thumb = app.offscreen.execute { gl -> gl.render(src, s.copy(geometry = com.lens035.core.model.Geometry()), stats, dateText) }
            val now = System.currentTimeMillis()
            app.projects.save(Project(projectId, "video", uri.toString(), existing?.createdAt ?: now, now, s), thumb)
            thumb.recycle()
        } catch (e: Exception) {
            Log.w(TAG, "Saving video project failed", e)
        }
    }

    fun dispose() {
        saveJob?.cancel()
    }

    companion object {
        private const val TAG = "VideoSession"
    }
}
