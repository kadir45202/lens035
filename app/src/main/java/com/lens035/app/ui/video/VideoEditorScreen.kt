package com.lens035.app.ui.video

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.opengl.GLSurfaceView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraRoll
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.lens035.app.gl.Transfer
import com.lens035.app.gl.VideoRenderer
import com.lens035.app.gl.ViewTransform
import com.lens035.app.media.MediaSaver
import com.lens035.app.media.VideoExportRequest
import com.lens035.app.media.VideoExportStatus
import com.lens035.app.media.VideoExporter
import com.lens035.app.ui.components.ChipRow
import com.lens035.app.ui.components.GhostPill
import com.lens035.app.ui.components.LensIconButton
import com.lens035.app.ui.components.LensSheet
import com.lens035.app.ui.components.PrimaryPill
import com.lens035.app.ui.components.ToolTab
import com.lens035.app.Lens035App
import com.lens035.app.data.UserPreset
import com.lens035.app.ui.editor.AdjustPanel
import com.lens035.app.ui.editor.AdjustTool
import com.lens035.app.ui.editor.CropOverlay
import com.lens035.app.ui.editor.CropTool
import com.lens035.app.ui.editor.EffectDetails
import com.lens035.app.ui.editor.EffectsStrip
import com.lens035.app.ui.editor.FilmDetails
import com.lens035.app.ui.editor.FilmsStrip
import com.lens035.app.ui.editor.LookDetails
import com.lens035.app.ui.editor.LooksStrip
import com.lens035.app.ui.editor.PresetDetails
import com.lens035.app.ui.editor.SavePresetDialog
import com.lens035.app.ui.editor.scaledLook
import com.lens035.app.ui.editor.orientedSize
import com.lens035.app.ui.editor.shareMedia
import com.lens035.app.ui.nav.Navigator
import com.lens035.app.ui.nav.Screen
import com.lens035.app.ui.theme.LensColors
import com.lens035.app.ui.theme.LensShapes
import com.lens035.app.ui.theme.LensType
import com.lens035.core.model.CropRect
import com.lens035.core.model.EditState
import com.lens035.core.model.EffectType
import com.lens035.core.presets.Look
import com.lens035.core.presets.Looks
import com.lens035.core.model.GeometryMath
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Photo-only effects: they need person segmentation, which video frames do not get. */
private val VIDEO_EXCLUDED_EFFECTS = setOf(EffectType.BOKEH)

private enum class VideoTool(val label: String, val icon: ImageVector) {
    LOOKS("Görünüm", Icons.Rounded.AutoAwesome),
    FILM("Film", Icons.Rounded.CameraRoll),
    EFFECTS("Efekt", Icons.Rounded.Flare),
    ADJUST("Ayarla", Icons.Rounded.Tune),
    CROP("Kırp", Icons.Rounded.Crop),
    TRIM("Kes", Icons.Rounded.ContentCut),
}

private enum class VideoResolution(val label: String, val maxSide: Int) {
    ORIGINAL("Orijinal", 3840),
    FHD("1080p", 1920),
    HD("720p", 1280),
}

private sealed interface VideoDetail {
    data object Film : VideoDetail
    data class Effect(val type: EffectType) : VideoDetail
    data class LookStrength(val look: Look) : VideoDetail
    data class Preset(val preset: UserPreset) : VideoDetail
}

private sealed interface ExportUi {
    data object Idle : ExportUi
    data class Running(val percent: Int) : ExportUi
    data class Done(val uri: Uri) : ExportUi
    data class Failed(val message: String) : ExportUi
}

@OptIn(UnstableApi::class)
@Composable
fun VideoEditorScreen(screen: Screen.VideoEditor, navigator: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val marginPx = with(density) { 12.dp.toPx() }
    val session = remember(screen) { VideoSession(context, screen.uri, scope, screen.projectId) }
    var tool by remember { mutableStateOf(VideoTool.FILM) }
    var detail by remember { mutableStateOf<VideoDetail?>(null) }
    var adjustPanel by remember { mutableStateOf(AdjustPanel.LIGHT) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var comparing by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var resolution by remember { mutableStateOf(VideoResolution.FHD) }
    var exportUi by remember { mutableStateOf<ExportUi>(ExportUi.Idle) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var cropGuides by remember { mutableStateOf(false) }
    var selectedLook by remember { mutableStateOf<String?>(null) }
    var lookBase by remember { mutableStateOf<EditState?>(null) }
    var lookStrength by remember { mutableStateOf(100f) }
    var showPresetDialog by remember { mutableStateOf(false) }
    val app = context.applicationContext as Lens035App
    val userPresets by app.presets.presets.collectAsState()

    val playerReleased = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(screen.uri))
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
        }
    }
    val exporter = remember { VideoExporter(context) }
    val glView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            setEGLConfigChooser(8, 8, 8, 0, 0, 0)
            preserveEGLContextOnPause = true
        }
    }
    val renderer = remember {
        VideoRenderer(context, glView) { surface ->
            if (!playerReleased.get()) {
                if (surface != null) player.setVideoSurface(surface) else player.clearVideoSurface()
            }
        }.also {
            glView.setRenderer(it)
            glView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
    }

    LaunchedEffect(session) {
        session.load()
        app.presets.load()
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val w = (videoSize.width * videoSize.pixelWidthHeightRatio).toInt()
                    val h = videoSize.height
                    val rotated = videoSize.unappliedRotationDegrees == 90 || videoSize.unappliedRotationDegrees == 270
                    val dw = if (rotated) h else w
                    val dh = if (rotated) w else h
                    session.setVideoSize(dw, dh)
                    renderer.setVideoSize(dw, dh)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val transfer = when (player.videoFormat?.colorInfo?.colorTransfer) {
                        C.COLOR_TRANSFER_HLG -> Transfer.HLG
                        C.COLOR_TRANSFER_ST2084 -> Transfer.PQ
                        else -> Transfer.SDR
                    }
                    renderer.setTransfer(transfer)
                }
                if (playbackState == Player.STATE_ENDED) {
                    player.seekTo(session.trimStartMs)
                    player.pause()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(context, "Video oynatılamıyor (${error.errorCodeName})", Toast.LENGTH_LONG).show()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    player.pause()
                    glView.onPause()
                }
                Lifecycle.Event.ON_RESUME -> glView.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exporter.cancel()
            player.clearVideoSurface()
            playerReleased.set(true)
            player.release()
            glView.queueEvent { renderer.releaseGl() }
            kotlinx.coroutines.MainScope().launch {
                session.saveProject()
                session.dispose()
            }
        }
    }
    // Keep playback inside the trimmed range and track the playhead.
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            if (session.trimEndMs > 0 && player.isPlaying && position >= session.trimEndMs) {
                player.seekTo(session.trimStartMs)
            }
            delay(40)
        }
    }
    // Read everything the renderer/player needs here, in composition, so changes recompose.
    val editState = session.state
    val editStats = session.stats
    val muted = session.muted
    val speed = session.speed
    val dateText = session.dateText
    val cropMode = tool == VideoTool.CROP
    SideEffect {
        renderer.marginPx = marginPx
        renderer.dateText = dateText
        renderer.original = comparing
        renderer.cropMode = cropMode
        renderer.setState(editState)
        renderer.setStats(editStats)
        if (!playerReleased.get()) {
            player.volume = if (muted) 0f else 1f
            player.playbackParameters = PlaybackParameters(speed)
        }
    }

    BackHandler(enabled = detail != null) { detail = null }
    BackHandler(enabled = detail == null) { navigator.pop() }

    fun startExport() {
        exportUi = ExportUi.Running(0)
        player.pause()
        exporter.start(
            VideoExportRequest(
                uri = screen.uri,
                state = session.state,
                stats = session.stats,
                trimStartMs = session.trimStartMs,
                trimEndMs = session.trimEndMs,
                durationMs = session.durationMs,
                muted = session.muted,
                speed = session.speed,
                maxSide = resolution.maxSide,
                frameRate = session.frameRate,
                dateText = session.dateText,
            ),
        ) { status ->
            exportUi = when (status) {
                is VideoExportStatus.Progress -> ExportUi.Running(status.percent)
                is VideoExportStatus.Done -> ExportUi.Done(status.uri)
                is VideoExportStatus.Failed -> ExportUi.Failed(status.message)
            }
        }
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startExport() else Toast.makeText(context, "Galeriye kaydetmek için depolama izni gerekli.", Toast.LENGTH_LONG).show()
    }

    Column(Modifier.fillMaxSize().background(LensColors.Ground)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            LensIconButton(Icons.Rounded.Close, "Kapat", { navigator.pop() })
            LensIconButton(Icons.AutoMirrored.Rounded.Undo, "Geri al", { session.undo() }, enabled = session.canUndo)
            LensIconButton(Icons.AutoMirrored.Rounded.Redo, "Yinele", { session.redo() }, enabled = session.canRedo)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(44.dp).pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        comparing = true
                        tryAwaitRelease()
                        comparing = false
                    })
                },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Compare, "Orijinali göster", tint = LensColors.Text, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(6.dp))
            PrimaryPill("Kaydet", onClick = {
                exportUi = ExportUi.Idle
                showExport = true
            }, enabled = session.durationMs > 0)
            Spacer(Modifier.width(6.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { viewSize = it }) {
            AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())
            if (tool == VideoTool.CROP && viewSize.width > 0) {
                val (pw, ph) = session.previewSize()
                val full = GeometryMath.outputSize(pw, ph, session.state.geometry.copy(crop = CropRect.FULL))
                val vt = ViewTransform.fit(viewSize.width, viewSize.height, full.width, full.height, marginPx)
                val g = session.state.geometry
                val (ow, oh) = orientedSize(session)
                CropOverlay(
                    vt = vt,
                    crop = g.crop,
                    aspect = GeometryMath.aspectValue(g.aspect, ow.toFloat(), oh.toFloat()),
                    correction = g,
                    orientedW = ow,
                    orientedH = oh,
                    onChange = { c -> session.preview(session.state.copy(geometry = session.state.geometry.copy(crop = c))) },
                    onGestureStart = { session.beginGesture() },
                    onGestureEnd = { session.endGesture() },
                    guides = cropGuides,
                )
            } else {
                Box(
                    Modifier.align(Alignment.Center).size(64.dp).clip(CircleShape)
                        .background(if (playing) Color.Transparent else Color.Black.copy(alpha = 0.45f))
                        .clickable(role = Role.Button) { if (player.isPlaying) player.pause() else player.play() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (!playing) Icon(Icons.Rounded.PlayArrow, "Oynat", tint = LensColors.Text, modifier = Modifier.size(34.dp))
                }
            }
            session.error?.let { Text(it, style = LensType.Body, color = LensColors.Muted, modifier = Modifier.align(Alignment.Center)) }
            if (comparing) {
                Text(
                    "Orijinal", style = LensType.Label, color = LensColors.Text,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
                        .background(Color.Black.copy(alpha = 0.55f), LensShapes.Pill).padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }

        PlaybackControls(
            playing = playing,
            position = { position },
            session = session,
            onPlayPause = {
                if (player.isPlaying) player.pause() else {
                    if (position < session.trimStartMs || position >= session.trimEndMs) player.seekTo(session.trimStartMs)
                    player.play()
                }
            },
            onSeek = { player.seekTo(it) },
        )

        AnimatedContent(targetState = detail to tool, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, label = "vtool") { (d, t) ->
            Box(Modifier.fillMaxWidth()) {
                when {
                    d is VideoDetail.Film -> FilmDetails(session) { detail = null }
                    d is VideoDetail.Effect -> EffectDetails(session, d.type) { detail = null }
                    d is VideoDetail.LookStrength -> LookDetails(d.look, lookStrength, session, { v ->
                        lookStrength = v
                        session.preview(scaledLook(lookBase ?: session.state, d.look.state, v / 100f))
                    }) { detail = null }
                    d is VideoDetail.Preset -> PresetDetails(d.preset, onDelete = {
                        scope.launch { app.presets.delete(d.preset.id) }
                        selectedLook = null
                        detail = null
                    }) { detail = null }
                    t == VideoTool.LOOKS -> LooksStrip(
                        session = session,
                        selectedLook = selectedLook,
                        userPresets = userPresets,
                        onSelectLook = { look ->
                            lookBase = session.state
                            lookStrength = 100f
                            selectedLook = look.id
                            session.update { scaledLook(it, look.state, 1f) }
                        },
                        onSelectPreset = { p ->
                            selectedLook = "u_" + p.id
                            session.update { it.applyLook(p.state) }
                        },
                        onPresetDetails = { p -> detail = VideoDetail.Preset(p) },
                        onSavePreset = { showPresetDialog = true },
                        onDetails = { Looks.find(selectedLook ?: "")?.let { detail = VideoDetail.LookStrength(it) } },
                    )
                    t == VideoTool.FILM -> FilmsStrip(session) { detail = VideoDetail.Film }
                    t == VideoTool.EFFECTS -> EffectsStrip(session, null, exclude = VIDEO_EXCLUDED_EFFECTS) { detail = VideoDetail.Effect(it) }
                    t == VideoTool.ADJUST -> AdjustTool(
                        session = session,
                        panel = adjustPanel,
                        onPanel = { adjustPanel = it },
                        customLuts = emptyList(),
                        onImportLut = { Toast.makeText(context, "LUT'ları fotoğraf düzenleyicisinden içe aktarabilirsin.", Toast.LENGTH_SHORT).show() },
                        onDeleteLut = {},
                    )
                    t == VideoTool.CROP -> CropTool(session, onGuides = { cropGuides = it })
                    t == VideoTool.TRIM -> TrimTool(session)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 4.dp)) {
            VideoTool.entries.forEach { t ->
                ToolTab(t.label, t.icon, t == tool && detail == null, onClick = {
                    detail = null
                    tool = t
                }, modifier = Modifier.weight(1f))
            }
        }
    }

    if (showPresetDialog) {
        SavePresetDialog(onDismiss = { showPresetDialog = false }) { name ->
            scope.launch { app.presets.add(name, session.state) }
            showPresetDialog = false
            Toast.makeText(context, "Ön ayar kaydedildi", Toast.LENGTH_SHORT).show()
        }
    }

    LensSheet(visible = showExport, onDismiss = { if (exportUi !is ExportUi.Running) showExport = false }, dismissible = exportUi !is ExportUi.Running) {
        Text("Videoyu dışa aktar", style = LensType.Heading, color = LensColors.Text, modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            "${session.videoWidth} × ${session.videoHeight} · ${formatTime(session.trimEndMs - session.trimStartMs)}" +
                if (session.speed != 1f) " · ${session.speed}x" else "",
            style = LensType.Meta, color = LensColors.Faint, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )
        Spacer(Modifier.height(12.dp))
        when (val ui = exportUi) {
            is ExportUi.Running -> {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("İşleniyor… %${ui.percent}", style = LensType.Body, color = LensColors.Text)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { ui.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = LensColors.Accent,
                        trackColor = LensColors.Active,
                    )
                    Spacer(Modifier.height(14.dp))
                    GhostPill("İptal", onClick = {
                        exporter.cancel()
                        exportUi = ExportUi.Idle
                    })
                }
            }
            is ExportUi.Done -> {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = LensColors.Positive, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text("Galeriye kaydedildi", style = LensType.Title, color = LensColors.Text)
                        Text("Filmler › Lens035", style = LensType.Meta, color = LensColors.Faint)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostPill("Paylaş", onClick = { shareMedia(context, ui.uri, "video/mp4") }, icon = Icons.Rounded.Share)
                    PrimaryPill("Tamam", onClick = { showExport = false })
                }
            }
            else -> {
                Text("Çözünürlük", style = LensType.Label, color = LensColors.Muted, modifier = Modifier.padding(start = 20.dp, bottom = 6.dp))
                ChipRow(VideoResolution.entries, resolution, { it.label }, { resolution = it })
                if (ui is ExportUi.Failed) {
                    Text(ui.message, style = LensType.Label, color = LensColors.Danger, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
                Spacer(Modifier.height(16.dp))
                PrimaryPill("Galeriye kaydet", onClick = {
                    val needsPermission = MediaSaver.needsLegacyPermission() &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    if (needsPermission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else startExport()
                }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            }
        }
    }
}

/** Reads the fast-changing playhead in its own scope so the rest of the screen stays put. */
@Composable
private fun PlaybackControls(
    playing: Boolean,
    position: () -> Long,
    session: VideoSession,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val pos = position()
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        LensIconButton(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Duraklat" else "Oynat", onPlayPause)
        Text(
            "${formatTime(pos)} / ${formatTime(session.durationMs)}",
            style = LensType.Value,
            color = LensColors.Muted,
            modifier = Modifier.weight(1f),
        )
        LensIconButton(
            if (session.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
            if (session.muted) "Sesi aç" else "Sesi kapat",
            { session.muted = !session.muted },
        )
    }
    Timeline(
        frames = session.timeline,
        durationMs = session.durationMs,
        trimStartMs = session.trimStartMs,
        trimEndMs = session.trimEndMs,
        positionMs = pos,
        onTrim = { s, e ->
            session.trimStartMs = s
            session.trimEndMs = e
        },
        onSeek = onSeek,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun TrimTool(session: VideoSession) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Hız", style = LensType.Label, color = LensColors.Muted, modifier = Modifier.padding(start = 20.dp, bottom = 6.dp))
        val speeds = listOf(0.5f, 0.75f, 1f, 1.5f, 2f, 3f)
        ChipRow(speeds, session.speed, { if (it == 1f) "Normal" else "${it}x" }, { session.speed = it })
        Spacer(Modifier.height(10.dp))
        Text(
            "Kırpmak için zaman çizelgesindeki tutamaçları sürükle. Kesilen bölüm: ${formatTime(session.trimEndMs - session.trimStartMs)}",
            style = LensType.Meta,
            color = LensColors.Faint,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L)
    val m = total / 60
    val s = total % 60
    val tenth = (ms.coerceAtLeast(0L) % 1000L) / 100L
    return String.format(java.util.Locale.US, "%d:%02d.%d", m, s, tenth)
}
