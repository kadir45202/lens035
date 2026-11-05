package com.lens035.app.ui.camera

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Grid3x3
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Timer10
import androidx.compose.material.icons.rounded.Timer3
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.lens035.app.data.HintStore
import com.lens035.app.ui.components.HintHost
import com.lens035.app.ui.components.rememberHintQueue
import com.lens035.app.media.BitmapLoader
import com.lens035.app.media.MediaSaver
import com.lens035.app.ui.components.ChipRow
import com.lens035.app.ui.components.LensIconButton
import com.lens035.app.ui.components.LensSheet
import com.lens035.app.ui.components.LensSlider
import com.lens035.app.ui.components.PrimaryPill
import com.lens035.app.ui.components.ValueFormat
import com.lens035.app.ui.nav.Navigator
import com.lens035.app.ui.nav.Screen
import com.lens035.app.ui.theme.LensColors
import com.lens035.app.ui.theme.LensShapes
import com.lens035.app.ui.theme.LensType
import com.lens035.core.pose.PoseMatch
import com.lens035.core.pose.PoseMatcher
import com.lens035.core.pose.PoseSkeleton
import com.lens035.core.pose.PoseTemplate
import com.lens035.core.pose.PoseTemplates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** What is shown over the camera to pose the subject. */
private sealed interface Reference {
    data object None : Reference
    data class Template(val template: PoseTemplate) : Reference
    data class Photo(val bitmap: Bitmap, val edges: Bitmap?, val skeleton: PoseSkeleton?) : Reference
}

private enum class GhostMode(val label: String) {
    IMAGE("Görsel"),
    OUTLINE("Kontur"),
    BODY("Siluet"),
}

@Composable
fun PoseCameraScreen(navigator: Navigator) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(LensColors.Ground)) {
        if (granted) {
            CameraContent(navigator)
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.AccessibilityNew, null, tint = LensColors.Accent, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(14.dp))
                Text("Poz kamerası için kamera izni gerekiyor.", style = LensType.Title, color = LensColors.Text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Fotoğraflar sadece senin cihazında işlenir ve galerine kaydedilir.",
                    style = LensType.Body, color = LensColors.Faint, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                PrimaryPill(if (asked) "Ayarları aç" else "İzin ver", onClick = {
                    if (asked) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } else {
                        permission.launch(Manifest.permission.CAMERA)
                    }
                })
            }
            LensIconButton(Icons.Rounded.Close, "Kapat", { navigator.pop() }, modifier = Modifier.statusBarsPadding().padding(6.dp))
        }
    }
}

@Composable
private fun CameraContent(navigator: Navigator) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    var front by remember { mutableStateOf(false) }
    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_OFF) }
    var timer by remember { mutableStateOf(0) }
    var grid by remember { mutableStateOf(true) }
    var reference by remember { mutableStateOf<Reference>(Reference.Template(PoseTemplates.all.first())) }
    var ghostMode by remember { mutableStateOf(GhostMode.BODY) }
    var opacity by remember { mutableFloatStateOf(0.8f) }
    var mirror by remember { mutableStateOf(false) }
    var moveMode by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var autoCapture by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf<PoseSkeleton?>(null) }
    var match by remember { mutableStateOf<PoseMatch?>(null) }
    var countdown by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var matchedSince by remember { mutableLongStateOf(0L) }
    val hints = rememberHintQueue(HintStore.POSE_BASICS)
    val poseMatched = (match?.score ?: 0f) >= 0.85f
    LaunchedEffect(poseMatched) { if (poseMatched) hints.done(HintStore.POSE_BASICS) }
    var lastAuto by remember { mutableLongStateOf(0L) }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            isTapToFocusEnabled = true
            isPinchToZoomEnabled = true
        }
    }
    val detector = remember {
        PoseDetection.getClient(PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build())
    }

    DisposableEffect(lifecycleOwner) {
        previewView.controller = controller
        controller.bindToLifecycle(lifecycleOwner)
        controller.setImageAnalysisAnalyzer(
            mainExecutor,
            MlKitAnalyzer(listOf(detector), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, mainExecutor) { result ->
                val pose = result.getValue(detector)
                val w = previewView.width.toFloat()
                val h = previewView.height.toFloat()
                live = pose?.toSkeleton(w, h)
            },
        )
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            previewView.controller = null
            detector.close()
        }
    }
    LaunchedEffect(front) {
        controller.cameraSelector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
    }
    LaunchedEffect(flashMode) { controller.imageCaptureFlashMode = flashMode }

    // Reference placement in the preview box.
    val viewW = boxSize.width.toFloat()
    val viewH = boxSize.height.toFloat()
    val refAspect = when (val r = reference) {
        is Reference.Photo -> r.bitmap.width.toFloat() / r.bitmap.height
        else -> 3f / 4f
    }
    val rect = referenceRect(viewW, viewH, refAspect, scale, offsetX, offsetY)
    val refSkeleton: PoseSkeleton? = when (val r = reference) {
        is Reference.Template -> r.template.skeleton
        is Reference.Photo -> r.skeleton
        Reference.None -> null
    }
    val refInView = if (refSkeleton != null && viewW > 0f) skeletonInView(refSkeleton, rect, viewW, viewH, mirror) else null

    fun capture() {
        if (busy) return
        busy = true
        scope.launch {
            for (t in timer downTo 1) {
                countdown = t
                delay(1000)
            }
            countdown = null
            flash = true
            takePhoto(context, controller, front, mainExecutor,
                onSaved = { uri ->
                    busy = false
                    flash = false
                    navigator.push(Screen.PhotoEditor(uri))
                },
                onError = { msg ->
                    busy = false
                    flash = false
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                },
            )
        }
    }

    // Compare the live pose with the reference.
    LaunchedEffect(live, refInView) {
        val l = live
        val ref = refInView
        match = if (l != null && ref != null && viewW > 0f) PoseMatcher.match(ref, l, viewW / viewH) else null
        val score = match?.score ?: 0f
        val now = System.currentTimeMillis()
        if (score >= 0.85f) {
            if (matchedSince == 0L) matchedSince = now
            if (autoCapture && !busy && now - matchedSince > 900 && now - lastAuto > 4000) {
                lastAuto = now
                capture()
            }
        } else {
            matchedSince = 0L
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val decoded = withContext(Dispatchers.IO) { BitmapLoader.decode(context, uri, 1280) }
                val edges = withContext(Dispatchers.Default) { ReferenceProcessor.edgeMap(decoded.bitmap) }
                val skeleton = ReferenceProcessor.detectPose(decoded.bitmap)
                reference = Reference.Photo(decoded.bitmap, edges, skeleton?.takeIf { ReferenceProcessor.isPlausible(it) })
                ghostMode = GhostMode.IMAGE
                opacity = 0.45f
                scale = 1f; offsetX = 0f; offsetY = 0f
                if (skeleton == null) Toast.makeText(context, "Fotoğrafta kişi bulunamadı; görüntü hayalet olarak gösterilecek.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Referans fotoğraf açılamadı.", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ------------------------------------------------------------------ top bar
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LensIconButton(Icons.Rounded.Close, "Kapat", { navigator.pop() })
            Spacer(Modifier.weight(1f))
            LensIconButton(
                when (flashMode) {
                    ImageCapture.FLASH_MODE_ON -> Icons.Rounded.FlashOn
                    ImageCapture.FLASH_MODE_AUTO -> Icons.Rounded.FlashAuto
                    else -> Icons.Rounded.FlashOff
                },
                "Flaş",
                {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                },
            )
            LensIconButton(
                when (timer) {
                    3 -> Icons.Rounded.Timer3
                    10 -> Icons.Rounded.Timer10
                    0 -> Icons.Rounded.TimerOff
                    else -> Icons.Rounded.Timer
                },
                "Zamanlayıcı",
                { timer = when (timer) { 0 -> 3; 3 -> 10; else -> 0 } },
            )
            LensIconButton(Icons.Rounded.Grid3x3, "Izgara", { grid = !grid }, tint = if (grid) LensColors.Text else LensColors.Faint)
            LensIconButton(Icons.Rounded.Cameraswitch, "Kamerayı çevir", { front = !front })
        }

        // ------------------------------------------------------------------ viewfinder (3:4)
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .onSizeChanged { boxSize = it }
                .clip(LensShapes.Small),
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            if (grid) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = Color.White.copy(alpha = 0.22f)
                    for (i in 1..2) {
                        drawLine(c, Offset(size.width * i / 3f, 0f), Offset(size.width * i / 3f, size.height), 1.dp.toPx())
                        drawLine(c, Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), 1.dp.toPx())
                    }
                }
            }
            // Reference ghost.
            val r = reference
            if (r is Reference.Photo && (ghostMode == GhostMode.IMAGE || ghostMode == GhostMode.OUTLINE) && viewW > 0f) {
                val bmp = if (ghostMode == GhostMode.OUTLINE) r.edges ?: r.bitmap else r.bitmap
                val image = remember(bmp) { bmp.asImageBitmap() }
                with(density) {
                    Image(
                        image,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        colorFilter = if (ghostMode == GhostMode.OUTLINE) ColorFilter.tint(Color.White) else null,
                        modifier = Modifier
                            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                            .size(rect.width.toDp(), rect.height.toDp())
                            .graphicsLayer { scaleX = if (mirror) -1f else 1f }
                            .alpha(opacity),
                    )
                }
            }
            val score = match?.score
            val matched = (score ?: 0f) >= 0.85f
            Canvas(Modifier.fillMaxSize()) {
                val showBody = refInView != null && (r is Reference.Template || ghostMode == GhostMode.BODY)
                if (showBody) {
                    drawPoseSilhouette(refInView!!, opacity, if (matched) LensColors.Positive else LensColors.Accent, match?.limbScores ?: emptyMap())
                }
                live?.let { if (refInView != null) drawLiveSkeleton(it, if (matched) LensColors.Positive else LensColors.Accent.copy(alpha = 0.9f)) }
                if (moveMode) {
                    drawRect(LensColors.Accent, Offset(rect.left, rect.top), Size(rect.width, rect.height), style = Stroke(2.dp.toPx()))
                }
            }
            if (moveMode) {
                Box(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.3f, 3f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    },
                )
            }
            // Match indicator.
            if (refInView != null) {
                Column(
                    Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
                        .background(Color.Black.copy(alpha = 0.55f), LensShapes.Pill)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val text = when {
                        score == null -> match?.hints?.firstOrNull() ?: "Kişiyi kadraja al"
                        matched -> "Harika! Uyum %${(score * 100).roundToInt()}"
                        else -> "Uyum %${(score * 100).roundToInt()}" + (match?.hints?.firstOrNull()?.let { " · $it" } ?: "")
                    }
                    Text(text, style = LensType.Label, color = if (matched) LensColors.Positive else LensColors.Text, textAlign = TextAlign.Center)
                }
            }
            HintHost(
                queue = hints,
                texts = mapOf(
                    HintStore.POSE_BASICS to "Soluk silüet referansın: kişiyi ona göre yönlendir. Uyum %85'i geçince silüet yeşile döner.",
                ),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                visible = reference !is Reference.None && countdown == null,
            )
            countdown?.let {
                Text("$it", style = LensType.Display.copy(fontSize = LensType.Display.fontSize * 3), color = LensColors.Text, modifier = Modifier.align(Alignment.Center))
            }
            if (flash) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.35f)))
            }
        }

        // ------------------------------------------------------------------ controls
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
            if (reference is Reference.Photo) {
                ChipRow(GhostMode.entries, ghostMode, { it.label }, { mode ->
                    ghostMode = mode
                    opacity = if (mode == GhostMode.IMAGE) 0.45f else 0.85f
                })
            }
            if (reference !is Reference.None) {
                LensSlider("Görünürlük", opacity * 100f, { opacity = it / 100f }, min = 0f, max = 100f, default = 60f, format = ValueFormat.PLAIN)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundAction(Icons.Rounded.PhotoLibrary, "Referans fotoğraf") {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                Box(
                    Modifier
                        .size(76.dp)
                        .border(3.dp, LensColors.Text, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(if (busy) LensColors.Faint else LensColors.Text)
                        .clickable(enabled = !busy, role = Role.Button) { capture() },
                )
                RoundAction(Icons.Rounded.AccessibilityNew, "Poz kütüphanesi") { showLibrary = true }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SmallToggle(Icons.Rounded.Flip, "Aynala", mirror) { mirror = !mirror }
                SmallToggle(Icons.Rounded.OpenWith, "Taşı", moveMode) { moveMode = !moveMode }
                SmallToggle(Icons.Rounded.AutoMode, "Oto çek", autoCapture) { autoCapture = !autoCapture }
                SmallToggle(Icons.Rounded.Close, "Kaldır", false) {
                    reference = Reference.None
                    moveMode = false
                }
            }
        }
    }

    LensSheet(visible = showLibrary, onDismiss = { showLibrary = false }) {
        PoseLibrary(onPick = { t ->
            reference = Reference.Template(t)
            opacity = 0.8f
            scale = 1f; offsetX = 0f; offsetY = 0f
            showLibrary = false
        })
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(LensColors.Raised).clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = LensColors.Text, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(label, style = LensType.Meta, color = LensColors.Faint)
    }
}

@Composable
private fun SmallToggle(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(LensShapes.Small).clickable(role = Role.Switch, onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (on) LensColors.Accent else LensColors.Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = LensType.Meta, color = if (on) LensColors.Text else LensColors.Faint)
    }
}

private val poseCategories = listOf("Tümü") + PoseTemplates.all.map { it.category }.distinct()

@Composable
private fun PoseLibrary(onPick: (PoseTemplate) -> Unit) {
    var category by remember { mutableStateOf("Tümü") }
    Text("Poz kütüphanesi", style = LensType.Heading, color = LensColors.Text, modifier = Modifier.padding(horizontal = 20.dp))
    Text("Bir poz seç; kişiye siluete göre yerleşmesini söyle.", style = LensType.Meta, color = LensColors.Faint, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
    Spacer(Modifier.height(10.dp))
    ChipRow(poseCategories, category, { it }, { category = it })
    val items = PoseTemplates.all.filter { category == "Tümü" || it.category == category }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().height(380.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { it.id }) { t ->
            Column(
                Modifier.clip(LensShapes.Thumb).background(LensColors.Raised).clickable(role = Role.Button) { onPick(t) }.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
                    drawPoseSilhouette(t.skeleton, 1f, LensColors.Accent)
                }
                Spacer(Modifier.height(6.dp))
                Text(t.name, style = LensType.Meta, color = LensColors.Text, maxLines = 1)
                Text(t.tip, style = LensType.Meta.copy(fontSize = LensType.Meta.fontSize * 0.9f), color = LensColors.Faint, maxLines = 2)
            }
        }
    }
}

/** Takes a picture into the gallery (or the cache when the gallery is not writable). */
private fun takePhoto(
    context: android.content.Context,
    controller: LifecycleCameraController,
    front: Boolean,
    executor: java.util.concurrent.Executor,
    onSaved: (Uri) -> Unit,
    onError: (String) -> Unit,
) {
    val metadata = ImageCapture.Metadata().apply { isReversedHorizontal = front }
    val canUseGallery = Build.VERSION.SDK_INT >= 29 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    val cacheFile = File(File(context.cacheDir, "camera").apply { mkdirs() }, "${MediaSaver.newName("LENS")}.jpg")
    val options = if (canUseGallery) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, MediaSaver.newName("LENS"))
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/" + MediaSaver.ALBUM)
        }
        ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            .setMetadata(metadata).build()
    } else {
        ImageCapture.OutputFileOptions.Builder(cacheFile).setMetadata(metadata).build()
    }
    try {
        controller.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onSaved(outputFileResults.savedUri ?: Uri.fromFile(cacheFile))
            }

            override fun onError(exception: ImageCaptureException) {
                onError("Fotoğraf çekilemedi: ${exception.message ?: exception.imageCaptureError}")
            }
        })
    } catch (e: IllegalStateException) {
        onError("Kamera henüz hazır değil, tekrar dene.")
    }
}
