package com.lens035.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lens035.app.gl.LensGlEffect
import com.lens035.core.analysis.ImageStats
import com.lens035.core.film.FilmCatalog
import com.lens035.core.model.EditState
import com.lens035.core.model.EffectType
import com.lens035.core.render.FilmDefaults
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exports a short clip through Media3 Transformer with the Lens035 effect on every frame. */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class VideoExportInstrumentedTest {

    @Test
    fun exportsAClipWithFilmAndEffects() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val png = File(context.cacheDir, "frame.png")
        val bmp = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(Color.rgb(120, 140, 160))
        png.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val output = File(context.cacheDir, "export_test.mp4").apply { delete() }

        val state = EditState(film = FilmDefaults.settingsFor(FilmCatalog.find("T800")!!))
            .withEffect(EffectType.GRAIN.defaultInstance())
            .withEffect(EffectType.LIGHT_LEAK.defaultInstance(3))
        val latch = CountDownLatch(1)
        var failure: Exception? = null

        instrumentation.runOnMainSync {
            val edited = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(png)))
                .setDurationUs(1_000_000)
                .setFrameRate(15)
                .setEffects(Effects(emptyList(), listOf(LensGlEffect(state, ImageStats.NEUTRAL, 640, "'98 11 04"))))
                .build()
            Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) = latch.countDown()

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        failure = exportException
                        latch.countDown()
                    }
                })
                .build()
                .start(edited, output.absolutePath)
        }
        assertTrue("export timed out", latch.await(120, TimeUnit.SECONDS))
        assertNull("export failed: $failure", failure)
        assertTrue("empty output", output.length() > 1000)
    }
}
