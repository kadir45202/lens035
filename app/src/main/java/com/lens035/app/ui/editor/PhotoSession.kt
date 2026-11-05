package com.lens035.app.ui.editor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.lens035.app.Lens035App
import com.lens035.app.data.Project
import com.lens035.app.gl.GlException
import com.lens035.app.gl.PersonMaskData
import com.lens035.app.media.BitmapLoader
import com.lens035.app.media.DateStamp
import com.lens035.app.media.ImageFormat
import com.lens035.app.media.ImageMeta
import com.lens035.app.media.MediaSaver
import com.lens035.app.media.PersonSegmenter
import com.lens035.core.analysis.ImageAnalyzer
import com.lens035.core.film.FilmCatalog
import com.lens035.core.model.EditState
import com.lens035.core.model.EffectType
import com.lens035.core.model.GeometryMath
import com.lens035.core.presets.Looks
import com.lens035.core.render.CanvasLayout
import com.lens035.core.render.FilmDefaults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

enum class ExportSize(val label: String, val maxSide: Int) {
    ORIGINAL("Orijinal", 5120),
    LARGE("4096 px", 4096),
    MEDIUM("2048 px", 2048),
    SMALL("1080 px", 1080),
}

data class ExportOptions(val format: ImageFormat, val quality: Int, val size: ExportSize)

/** The state an effect's thumbnail is rendered with: tiny previews need a stronger setting to read. */
internal fun thumbnailEffectState(type: EffectType): EditState {
    val fx = type.defaultInstance(7)
    val boosted = if (type == EffectType.FRAME || type == EffectType.MIRROR) fx else fx.withValue(type.amountParam, maxOf(fx.amount, 85f))
    return EditState().withEffect(boosted)
}

/** Renders the film / effect / look thumbnails for a small source image. */
internal suspend fun renderToolThumbnails(
    app: Lens035App,
    thumb: Bitmap,
    stats: com.lens035.core.analysis.ImageStats,
    dateText: String,
): Triple<Map<String, ImageBitmap>, Map<EffectType, ImageBitmap>, Map<String, ImageBitmap>> {
    val films = FilmCatalog.all
    val filmStates = films.map { EditState(film = FilmDefaults.settingsFor(it)) }
    // The canvas tile shows an icon: a thumbnail cannot show a change of shape.
    val effectTypes = EffectType.entries.filter { it != EffectType.CANVAS }
    val effectStates = effectTypes.map { thumbnailEffectState(it) }
    val lookStates = Looks.all.map { it.state }
    val all = app.offscreen.execute { gl -> gl.renderAll(thumb, filmStates + effectStates + lookStates, stats, dateText) }
    val f = HashMap<String, ImageBitmap>()
    films.forEachIndexed { i, r -> f[r.id] = all[i].asImageBitmap() }
    val e = HashMap<EffectType, ImageBitmap>()
    effectTypes.forEachIndexed { i, t -> e[t] = all[films.size + i].asImageBitmap() }
    val l = HashMap<String, ImageBitmap>()
    Looks.all.forEachIndexed { i, look -> l[look.id] = all[films.size + effectTypes.size + i].asImageBitmap() }
    return Triple(f, e, l)
}

/**
 * One photo edit: the source, the current state (with history, see [EditSession]), image
 * statistics, thumbnails, autosave as a project, and full-resolution export.
 */
@Stable
class PhotoSession(
    private val context: Context,
    val sourceUri: Uri,
    private val scope: CoroutineScope,
    existingProjectId: String?,
    private val initialFilmId: String?,
) : EditSession(
    existingProjectId?.let { (context.applicationContext as Lens035App).projects.get(it)?.state } ?: EditState(),
) {
    private val app = context.applicationContext as Lens035App
    val projectId: String = existingProjectId ?: app.projects.newId()
    private val existing: Project? = existingProjectId?.let { app.projects.get(it) }

    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var preview by mutableStateOf<Bitmap?>(null)
        private set
    var meta by mutableStateOf(ImageMeta(0, 0, null, null, null))
        private set

    override val frameWidth: Int get() = preview?.width ?: 1
    override val frameHeight: Int get() = preview?.height ?: 1

    val dateText: String get() = DateStamp.text(meta.dateTakenMillis)

    /** Where the person is in [preview] (null until found, or when there is nobody). */
    var personMask by mutableStateOf<PersonMaskData?>(null)
        private set
    var personStatus by mutableStateOf(PersonStatus.UNKNOWN)
        private set

    private var thumbSource: Bitmap? = null
    private var thumbMask: PersonMaskData? = null
    private var histSource: Bitmap? = null
    private var saveJob: Job? = null
    private var personJob: Job? = null
    private val thumbnailsDone = CompletableDeferred<Unit>()

    fun load() {
        scope.launch {
            loading = true
            try {
                val decoded = withContext(Dispatchers.IO) {
                    try {
                        BitmapLoader.decode(context, sourceUri, PREVIEW_MAX_SIDE)
                    } catch (e: Exception) {
                        // The original may no longer be readable; fall back to the project's copy.
                        val copy = app.projects.previewFile(projectId)
                        if (copy.exists()) BitmapLoader.decode(context, Uri.fromFile(copy), PREVIEW_MAX_SIDE) else throw e
                    }
                }
                meta = decoded.meta
                stats = withContext(Dispatchers.Default) {
                    val small = BitmapLoader.scaledCopy(decoded.bitmap, 160)
                    val px = IntArray(small.width * small.height)
                    small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
                    val s = ImageAnalyzer.analyze(px, small.width, small.height)
                    if (small !== decoded.bitmap) small.recycle()
                    s
                }
                preview = decoded.bitmap
                if (existing == null && initialFilmId != null) {
                    FilmCatalog.find(initialFilmId)?.let { startWith(state.copy(film = FilmDefaults.settingsFor(it))) }
                }
                takePersistablePermission()
                loading = false
                renderThumbnails(decoded.bitmap)
                // Cheap (one small inference), and makes portrait blur and the person mask instant.
                ensurePersonMask()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Load failed", e)
                error = "Fotoğraf açılamadı. Dosya silinmiş ya da erişim izni kalkmış olabilir."
                loading = false
            }
        }
    }

    private fun takePersistablePermission() {
        if (sourceUri.scheme != "content") return
        try {
            context.contentResolver.takePersistableUriPermission(sourceUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
            // Not every provider grants persistable access; the project keeps a preview copy.
        }
    }

    private suspend fun renderThumbnails(source: Bitmap) {
        try {
            val thumb = withContext(Dispatchers.Default) { BitmapLoader.thumbnail(source, THUMB_W, THUMB_H) }
            thumbSource = thumb
            val (f, e, l) = renderToolThumbnails(app, thumb, stats, dateText)
            filmThumbs = f
            effectThumbs = e
            lookThumbs = l
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Thumbnails failed", e)
        } finally {
            thumbnailsDone.complete(Unit)
        }
    }

    override suspend fun renderThumbnail(s: EditState): ImageBitmap? {
        val src = thumbSource ?: return null
        return try {
            app.offscreen.execute { gl -> gl.renderAll(src, listOf(s), stats, dateText, thumbMask) }.first().asImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun analysisPixels(): AnalysisPixels? {
        val bmp = preview ?: return null
        return withContext(Dispatchers.Default) {
            val small = BitmapLoader.scaledCopy(bmp, ANALYSIS_MAX_SIDE)
            val px = IntArray(small.width * small.height)
            small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
            if (small !== bmp) small.recycle()
            AnalysisPixels(px, small.width, small.height)
        }
    }

    /** Finds the person in the photo once, on the device. Later calls reuse the result. */
    fun ensurePersonMask() {
        val bmp = preview ?: return
        if (personJob != null) return
        personJob = scope.launch {
            personStatus = PersonStatus.SEARCHING
            val mask = try {
                val small = withContext(Dispatchers.Default) { BitmapLoader.scaledCopy(bmp, SEGMENT_MAX_SIDE) }
                val m = PersonSegmenter.segment(small)
                if (small !== bmp) small.recycle()
                m
            } catch (e: CancellationException) {
                personJob = null
                personStatus = PersonStatus.UNKNOWN
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Person detection failed", e)
                null
            }
            if (mask == null) {
                personStatus = PersonStatus.FAILED
                return@launch
            }
            val coverage = withContext(Dispatchers.Default) { mask.coverage }
            if (coverage < MIN_PERSON_COVERAGE) {
                personStatus = PersonStatus.NOT_FOUND
                return@launch
            }
            personMask = mask
            personStatus = PersonStatus.FOUND
            // Separate job: waiting for exports on this one must not also wait for thumbnails.
            scope.launch { refreshPortraitThumbnail(bmp, mask) }
        }
    }

    /** Waits for person detection when [s] depends on it (export must not miss the blur). */
    private suspend fun personMaskFor(s: EditState): PersonMaskData? {
        if (!s.needsPersonMask) return null
        ensurePersonMask()
        personJob?.join()
        return personMask
    }

    /** Re-renders the portrait thumbnail with the person mask cropped like the thumbnail itself. */
    private suspend fun refreshPortraitThumbnail(source: Bitmap, mask: PersonMaskData) {
        thumbnailsDone.await()
        val thumb = thumbSource ?: return
        val r = BitmapLoader.thumbnailCrop(source.width, source.height, THUMB_W, THUMB_H)
        val tm = withContext(Dispatchers.Default) {
            mask.cropped(
                r.left / source.width.toFloat(), r.top / source.height.toFloat(),
                r.right / source.width.toFloat(), r.bottom / source.height.toFloat(),
            )
        }
        thumbMask = tm
        try {
            val img = app.offscreen.execute { gl ->
                gl.renderAll(thumb, listOf(thumbnailEffectState(EffectType.BOKEH)), stats, dateText, tm)
            }.first()
            effectThumbs = effectThumbs + (EffectType.BOKEH to img.asImageBitmap())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Portrait thumbnail failed", e)
        }
    }

    /** RGB + luminance histograms (4 x 256 bins) of a small render of [s]. */
    suspend fun histogram(edit: EditState): IntArray? {
        val bmp = preview ?: return null
        val src = histSource ?: withContext(Dispatchers.Default) { BitmapLoader.scaledCopy(bmp, 220) }.also { histSource = it }
        // The photo's tones only: a canvas fill would swamp the histogram.
        val s = edit.withoutEffect(EffectType.CANVAS)
        return try {
            val mask = if (s.needsPersonMask) personMask else null
            val out = app.offscreen.execute { gl -> gl.render(src, s, stats, dateText, meta.width, meta.height, mask) }
            withContext(Dispatchers.Default) {
                val px = IntArray(out.width * out.height)
                out.getPixels(px, 0, out.width, 0, 0, out.width, out.height)
                out.recycle()
                val h = IntArray(256 * 4)
                for (c in px) {
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    h[r]++
                    h[256 + g]++
                    h[512 + b]++
                    h[768 + ((r * 54 + g * 183 + b * 19) shr 8).coerceIn(0, 255)]++
                }
                h
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    override fun onChanged() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1200)
            saveProject()
        }
    }

    suspend fun saveProject() {
        val bmp = preview ?: return
        val s = state
        if (s.isDefault && existing == null) return
        try {
            val mask = personMaskFor(s)
            val small = withContext(Dispatchers.Default) { BitmapLoader.scaledCopy(bmp, 480) }
            val thumb = app.offscreen.execute { gl -> gl.render(small, s, stats, dateText, meta.width, meta.height, mask) }
            if (small !== bmp) small.recycle()
            val now = System.currentTimeMillis()
            val project = Project(projectId, "photo", sourceUri.toString(), existing?.createdAt ?: now, now, s)
            val previewCopy = if (!app.projects.previewFile(projectId).exists()) bmp else null
            app.projects.save(project, thumb, previewCopy)
            thumb.recycle()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Saving project failed", e)
        }
    }

    /** Renders at the requested size and saves into the gallery. Returns the new image's Uri. */
    suspend fun export(options: ExportOptions): Uri {
        val s = state
        val mask = personMaskFor(s)
        val maxTex = app.offscreen.execute { it.maxTextureSize }
        val longest = max(meta.width, meta.height)
        var side = minOf(options.size.maxSide, maxTex, if (longest > 0) longest else options.size.maxSide)
        // A canvas grows around the photo: keep the whole canvas within the GPU's texture limit.
        if (longest > 0 && s.hasEffect(EffectType.CANVAS)) {
            val k = side / longest.toFloat()
            val photo = GeometryMath.outputSize((meta.width * k).roundToInt().coerceAtLeast(1), (meta.height * k).roundToInt().coerceAtLeast(1), s.geometry)
            val canvas = CanvasLayout.of(s, photo.width, photo.height)
            val canvasLongest = max(canvas.width, canvas.height)
            if (canvasLongest > maxTex) side = (side * maxTex / canvasLongest.toFloat()).roundToInt().coerceAtLeast(512)
        }
        var lastError: Throwable? = null
        repeat(3) {
            try {
                val decoded = withContext(Dispatchers.IO) { decodeForExport(side) }
                val rendered = try {
                    app.offscreen.execute { gl ->
                        gl.render(decoded.bitmap, s, stats, dateText, decoded.bitmap.width, decoded.bitmap.height, mask)
                    }
                } finally {
                    decoded.bitmap.recycle()
                }
                try {
                    return withContext(Dispatchers.IO) {
                        MediaSaver.saveImage(context, rendered, options.format, options.quality, meta)
                    }
                } finally {
                    rendered.recycle()
                }
            } catch (oom: OutOfMemoryError) {
                lastError = oom
            } catch (e: GlException) {
                lastError = e
            }
            // Retry smaller on memory or GPU limits.
            side = (side * 0.7f).roundToInt().coerceAtLeast(1080)
        }
        throw IllegalStateException("Görüntü bu boyutta işlenemedi.", lastError)
    }

    private fun decodeForExport(side: Int) = try {
        BitmapLoader.decode(context, sourceUri, side)
    } catch (e: Exception) {
        val copy: File = app.projects.previewFile(projectId)
        if (copy.exists()) BitmapLoader.decode(context, Uri.fromFile(copy), side) else throw e
    }

    fun dispose() {
        saveJob?.cancel()
        personJob?.cancel()
        thumbSource = null
        thumbMask = null
        histSource = null
    }

    companion object {
        private const val TAG = "PhotoSession"
        const val PREVIEW_MAX_SIDE = 2048
        const val THUMB_W = 136
        const val THUMB_H = 168

        /** Long side used for line analysis (auto upright). */
        const val ANALYSIS_MAX_SIDE = 320

        /** Segmentation input size: the model itself works at 256 px, so more is wasted time. */
        const val SEGMENT_MAX_SIDE = 512

        /** Below this share of the frame the "person" is noise (a poster, a reflection). */
        const val MIN_PERSON_COVERAGE = 0.004f
    }
}
