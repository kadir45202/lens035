package com.lens035.app.media

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.lens035.app.gl.LensGlEffect
import com.lens035.core.analysis.ImageStats
import com.lens035.core.model.EditState
import java.io.File

/** What to export. Times are in milliseconds of the source clip. */
data class VideoExportRequest(
    val uri: Uri,
    val state: EditState,
    val stats: ImageStats,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val durationMs: Long,
    val muted: Boolean,
    val speed: Float,
    val maxSide: Int,
    val frameRate: Float,
    val dateText: String,
)

sealed interface VideoExportStatus {
    data class Progress(val percent: Int) : VideoExportStatus
    data class Done(val uri: Uri) : VideoExportStatus
    data class Failed(val message: String) : VideoExportStatus
}

/**
 * Wraps Media3 Transformer: trims, changes speed, removes audio and runs [LensGlEffect] on every
 * frame, then copies the result into the gallery. Must be used from the main thread.
 */
@OptIn(UnstableApi::class)
class VideoExporter(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var transformer: Transformer? = null
    private var output: File? = null
    private var progressRunnable: Runnable? = null

    val isRunning: Boolean get() = transformer != null

    fun start(request: VideoExportRequest, onStatus: (VideoExportStatus) -> Unit) {
        cancel()
        val dir = File(appContext.cacheDir, "export").apply { mkdirs() }
        val file = File(dir, "lens035_${System.currentTimeMillis()}.mp4")
        output = file

        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(request.trimStartMs.coerceAtLeast(0))
            .setEndPositionMs(if (request.trimEndMs >= request.durationMs || request.trimEndMs <= 0) C.TIME_END_OF_SOURCE else request.trimEndMs)
            .build()
        val mediaItem = MediaItem.Builder().setUri(request.uri).setClippingConfiguration(clipping).build()

        val videoEffects = ArrayList<Effect>()
        val audioProcessors = ArrayList<AudioProcessor>()
        if (request.speed != 1f) {
            videoEffects.add(SpeedChangeEffect(request.speed))
            if (!request.muted) {
                audioProcessors.add(SonicAudioProcessor().apply { setSpeed(request.speed) })
            }
        }
        videoEffects.add(LensGlEffect(request.state, request.stats, request.maxSide, request.dateText))

        val edited = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(request.muted)
            .setEffects(Effects(audioProcessors, videoEffects))
            .build()
        val compositionBuilder = Composition.Builder(EditedMediaItemSequence(edited))
        if (Build.VERSION.SDK_INT >= 29) {
            compositionBuilder.setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
        }
        val composition = compositionBuilder.build()

        val (w, h) = estimateSize(request)
        val bitrate = (w.toLong() * h * request.frameRate.coerceIn(15f, 60f) * 0.16f).toLong()
            .coerceIn(2_000_000L, 60_000_000L).toInt()
        val encoderFactory = DefaultEncoderFactory.Builder(appContext)
            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build())
            .setEnableFallback(true)
            .build()

        val t = Transformer.Builder(appContext)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoderFactory)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    stopProgress()
                    transformer = null
                    try {
                        val uri = MediaSaver.saveVideo(appContext, file)
                        onStatus(VideoExportStatus.Done(uri))
                    } catch (e: Exception) {
                        Log.e(TAG, "Saving video failed", e)
                        onStatus(VideoExportStatus.Failed(e.message ?: "Video kaydedilemedi"))
                    } finally {
                        file.delete()
                    }
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    stopProgress()
                    transformer = null
                    file.delete()
                    Log.e(TAG, "Export failed: ${exportException.errorCodeName}", exportException)
                    onStatus(VideoExportStatus.Failed(readableError(exportException)))
                }
            })
            .build()
        transformer = t
        try {
            t.start(composition, file.absolutePath)
        } catch (e: Exception) {
            transformer = null
            file.delete()
            onStatus(VideoExportStatus.Failed(e.message ?: "Dışa aktarma başlatılamadı"))
            return
        }
        val holder = ProgressHolder()
        val runnable = object : Runnable {
            override fun run() {
                val current = transformer ?: return
                if (current.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onStatus(VideoExportStatus.Progress(holder.progress.coerceIn(0, 100)))
                }
                main.postDelayed(this, 250)
            }
        }
        progressRunnable = runnable
        main.post(runnable)
    }

    fun cancel() {
        stopProgress()
        transformer?.cancel()
        transformer = null
        output?.delete()
        output = null
    }

    private fun stopProgress() {
        progressRunnable?.let { main.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun estimateSize(request: VideoExportRequest): Pair<Int, Int> {
        val side = request.maxSide.coerceAtLeast(320)
        return side to (side * 9 / 16)
    }

    private fun readableError(e: ExportException): String = when (e.errorCode) {
        ExportException.ERROR_CODE_ENCODER_INIT_FAILED, ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED ->
            "Cihazın video kodlayıcısı bu çözünürlüğü desteklemiyor. Daha düşük bir çözünürlük deneyin."
        ExportException.ERROR_CODE_DECODER_INIT_FAILED, ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
            "Bu video biçimi cihazda çözülemiyor."
        ExportException.ERROR_CODE_IO_FILE_NOT_FOUND, ExportException.ERROR_CODE_IO_NO_PERMISSION ->
            "Video dosyası okunamadı."
        else -> "Dışa aktarma başarısız oldu (${e.errorCodeName})."
    }

    companion object {
        private const val TAG = "VideoExporter"
    }
}
