package com.lens035.app.ui.editor

import android.opengl.GLSurfaceView
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lens035.app.gl.PhotoRenderer
import com.lens035.app.gl.PreviewMode
import com.lens035.app.gl.ViewTransform
import com.lens035.core.model.GeometryMath
import com.lens035.core.render.CanvasLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Size of the image the preview currently shows, for a mode (crop mode shows the whole frame). */
fun previewOutputSize(session: PhotoSession, mode: PreviewMode): IntSize {
    val c = previewCanvas(session, mode) ?: return IntSize(1, 1)
    return IntSize(c.width, c.height)
}

/** The preview's output for a mode: canvas size and where the photo sits in it. */
private fun previewCanvas(session: PhotoSession, mode: PreviewMode): CanvasLayout? {
    val bmp = session.preview ?: return null
    val s = PhotoRenderer.effectiveState(session.state, mode)
    val out = GeometryMath.outputSize(bmp.width, bmp.height, s.geometry)
    return CanvasLayout.of(s, out.width, out.height)
}

/** [vt] narrowed to the photo itself when it sits on a canvas (overlays work in photo space). */
fun photoViewTransform(session: PhotoSession, mode: PreviewMode, vt: ViewTransform): ViewTransform {
    val c = previewCanvas(session, mode) ?: return vt
    if (c.isNone) return vt
    return ViewTransform(vt.left + c.innerX * vt.width, vt.top + c.innerY * vt.height, c.innerW * vt.width, c.innerH * vt.height)
}

/**
 * The live GPU preview with pinch-zoom, double-tap zoom and press-and-hold to compare.
 * [split] (0..1 of the width, or null) shows the original left of a draggable divider.
 * [overlay] draws on top in view coordinates (crop handles, masks).
 */
@Composable
fun PhotoCanvas(
    session: PhotoSession,
    mode: PreviewMode,
    gesturesEnabled: Boolean,
    onCompare: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    split: Float? = null,
    onSplit: (Float) -> Unit = {},
    overlay: @Composable BoxScope.(ViewTransform) -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val marginPx = with(density) { 12.dp.toPx() }
    val glView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            setEGLConfigChooser(8, 8, 8, 0, 0, 0)
            preserveEGLContextOnPause = true
        }
    }
    val renderer = remember {
        PhotoRenderer(context, glView).also {
            glView.setRenderer(it)
            glView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> glView.onPause()
                Lifecycle.Event.ON_RESUME -> glView.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glView.queueEvent { renderer.releaseGl() }
        }
    }
    DisposableEffect(renderer) {
        renderer.onError = { t ->
            glView.post { Toast.makeText(context, "Önizleme hatası: ${t.message ?: t.javaClass.simpleName}", Toast.LENGTH_LONG).show() }
        }
        onDispose { renderer.onError = null }
    }

    val preview = session.preview
    LaunchedEffect(preview) {
        if (preview != null) renderer.setSource(preview, session.meta.width, session.meta.height, session.stats, session.dateText)
    }
    // Read the edit in composition so that every change recomposes and reaches the renderer.
    val editState = session.state
    val personMask = session.personMask
    SideEffect {
        renderer.marginPx = marginPx
        renderer.setState(editState)
        renderer.setMode(mode)
        renderer.setPersonMask(personMask)
    }

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val splitPx = split?.let { it * viewSize.width }
    SideEffect { renderer.setSplit(splitPx) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    val outSize = previewOutputSize(session, mode)

    // Zoom resets whenever gestures are handed to an overlay (crop, masks).
    LaunchedEffect(gesturesEnabled) {
        if (!gesturesEnabled) {
            zoom = 1f; panX = 0f; panY = 0f
            renderer.setView(1f, 0f, 0f)
        }
    }

    val scope = rememberCoroutineScope()
    val compareCallback by rememberUpdatedState(onCompare)
    val vt = ViewTransform.fit(viewSize.width, viewSize.height, outSize.width, outSize.height, marginPx, zoom, panX, panY)

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { viewSize = it }
            .pointerInput(gesturesEnabled) {
                if (!gesturesEnabled) return@pointerInput
                var pressJob: Job? = null
                detectTapGestures(
                    onDoubleTap = { tap ->
                        if (zoom > 1.01f) {
                            zoom = 1f; panX = 0f; panY = 0f
                        } else {
                            val target = 2.5f
                            val cx = tap.x - viewSize.width / 2f
                            val cy = tap.y - viewSize.height / 2f
                            panX = -cx * (target - 1f)
                            panY = -cy * (target - 1f)
                            zoom = target
                        }
                        val fitted = ViewTransform.fit(viewSize.width, viewSize.height, outSize.width, outSize.height, marginPx, zoom, panX, panY)
                        panX = fitted.left - (viewSize.width - fitted.width) / 2f
                        panY = fitted.top - (viewSize.height - fitted.height) / 2f
                        renderer.setView(zoom, panX, panY)
                    },
                    onPress = {
                        pressJob?.cancel()
                        pressJob = scope.launch {
                            delay(260)
                            compareCallback(true)
                        }
                        tryAwaitRelease()
                        pressJob?.cancel()
                        compareCallback(false)
                    },
                )
            }
            .pointerInput(gesturesEnabled) {
                if (!gesturesEnabled) return@pointerInput
                detectTransformGestures { centroid: Offset, pan: Offset, zoomChange: Float, _: Float ->
                    val newZoom = ViewTransform.clampZoom(zoom * zoomChange)
                    val cx = centroid.x - viewSize.width / 2f
                    val cy = centroid.y - viewSize.height / 2f
                    var px = cx - (cx - panX) * (newZoom / zoom) + pan.x
                    var py = cy - (cy - panY) * (newZoom / zoom) + pan.y
                    val fitted = ViewTransform.fit(viewSize.width, viewSize.height, outSize.width, outSize.height, marginPx, newZoom, px, py)
                    px = fitted.left - (viewSize.width - fitted.width) / 2f
                    py = fitted.top - (viewSize.height - fitted.height) / 2f
                    zoom = newZoom
                    panX = if (newZoom <= 1.001f) 0f else px
                    panY = if (newZoom <= 1.001f) 0f else py
                    renderer.setView(zoom, panX, panY)
                }
            },
    ) {
        AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())
        if (viewSize.width > 0 && preview != null) {
            overlay(photoViewTransform(session, mode, vt))
            if (split != null && mode == PreviewMode.NORMAL) SplitDivider(split, vt, onSplit)
        }
    }
}
