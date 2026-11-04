package com.lens035.app

import androidx.annotation.OptIn
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.transformer.Composition
import androidx.media3.transformer.Transformer
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

/** Temporary: proves the key artifacts resolve and expose the classes we rely on. */
@OptIn(UnstableApi::class)
internal object ApiSmoke {
    val classes: List<Class<*>> = listOf(
        MlKitAnalyzer::class.java,
        LifecycleCameraController::class.java,
        BaseGlShaderProgram::class.java,
        Composition::class.java,
        Transformer::class.java,
        PoseDetection::class.java,
        PoseDetectorOptions::class.java,
    )
}
