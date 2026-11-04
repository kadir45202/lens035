package com.lens035.app.ui.editor

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CameraRoll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lens035.app.Lens035App
import com.lens035.app.gl.LutCache
import com.lens035.app.gl.PreviewMode
import com.lens035.app.media.ImageFormat
import com.lens035.app.media.MediaSaver
import com.lens035.app.ui.components.LensIconButton
import com.lens035.app.ui.components.PrimaryPill
import com.lens035.app.ui.components.ToolTab
import com.lens035.app.ui.nav.Navigator
import com.lens035.app.ui.nav.Screen
import com.lens035.app.ui.theme.LensColors
import com.lens035.app.ui.theme.LensShapes
import com.lens035.app.ui.theme.LensType
import com.lens035.core.lut.CubeParser
import com.lens035.core.model.EffectType
import com.lens035.core.model.GeometryMath
import com.lens035.core.model.MaskKind
import com.lens035.core.presets.Look
import com.lens035.core.presets.Looks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class EditorTool(val label: String, val icon: ImageVector) {
    LOOKS("Görünüm", Icons.Rounded.AutoAwesome),
    FILM("Film", Icons.Rounded.CameraRoll),
    EFFECTS("Efekt", Icons.Rounded.Flare),
    ADJUST("Ayarla", Icons.Rounded.Tune),
    CROP("Kırp", Icons.Rounded.Crop),
    MASKS("Maske", Icons.Rounded.Gradient),
}

/** Which fine-tuning panel is open over the tool strip. */
private sealed interface Detail {
    data object Film : Detail
    data class Effect(val type: EffectType) : Detail
    data class LookStrength(val look: Look) : Detail
    data class Preset(val preset: com.lens035.app.data.UserPreset) : Detail
}

@Composable
fun PhotoEditorScreen(screen: Screen.PhotoEditor, navigator: Navigator) {
    val context = LocalContext.current
    val app = context.applicationContext as Lens035App
    val scope = rememberCoroutineScope()
    val session = remember(screen) { PhotoSession(context, screen.uri, scope, screen.projectId, screen.filmId) }
    LaunchedEffect(session) {
        session.load()
        app.presets.load()
    }
    DisposableEffect(session) {
        onDispose {
            // Finish saving the project even though this screen is gone.
            kotlinx.coroutines.MainScope().launch {
                session.saveProject()
                session.dispose()
            }
        }
    }

    var tool by remember { mutableStateOf(EditorTool.FILM) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    var adjustPanel by remember { mutableStateOf(AdjustPanel.LIGHT) }
    var comparing by remember { mutableStateOf(false) }
    var selectedMask by remember { mutableStateOf<Long?>(null) }
    var cropGuides by remember { mutableStateOf(false) }
    var selectedLook by remember { mutableStateOf<String?>(null) }
    var lookBase by remember { mutableStateOf<com.lens035.core.model.EditState?>(null) }
    var lookStrength by remember { mutableStateOf(100f) }
    var menuOpen by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var saveState by remember { mutableStateOf<SaveState>(SaveState.Idle) }
    var exportOptions by remember { mutableStateOf(ExportOptions(ImageFormat.JPEG, Quality.HIGH.value, ExportSize.ORIGINAL)) }
    var quality by remember { mutableStateOf(Quality.HIGH) }
    var showPresetDialog by remember { mutableStateOf(false) }
    var showHistogram by remember { mutableStateOf(false) }
    var histogram by remember { mutableStateOf<IntArray?>(null) }
    val customLuts = remember { mutableStateListOf<String>() }
    val userPresets by app.presets.presets.collectAsState()

    LaunchedEffect(Unit) {
        val names = withContext(Dispatchers.IO) {
            LutCache.directory()?.listFiles()?.filter { it.name.endsWith(".cube", true) }?.map { it.name }?.sorted().orEmpty()
        }
        customLuts.clear()
        customLuts.addAll(names)
    }
    LaunchedEffect(showHistogram, session.state, session.preview) {
        if (!showHistogram) return@LaunchedEffect
        delay(220)
        histogram = session.histogram(session.state)
    }
    // Portrait blur, skin smoothing and person masks need to know where the person is.
    val needsPerson = session.state.needsPersonMask
    LaunchedEffect(needsPerson, session.preview) {
        if (needsPerson && session.preview != null) session.ensurePersonMask()
    }
    // Show what a person mask selects for a moment whenever it is picked or inverted.
    val activeMask = session.state.masks.firstOrNull { it.id == selectedMask }
    var personTintVisible by remember { mutableStateOf(false) }
    LaunchedEffect(tool, activeMask?.id, activeMask?.invert, session.personMask) {
        if (tool == EditorTool.MASKS && activeMask?.kind == MaskKind.PERSON && session.personMask != null) {
            personTintVisible = true
            delay(1400)
        }
        personTintVisible = false
    }

    fun doExport() {
        saveState = SaveState.Working
        scope.launch {
            saveState = try {
                SaveState.Done(session.export(exportOptions.copy(quality = quality.value)))
            } catch (e: Throwable) {
                SaveState.Failed(e.message ?: "Kaydedilemedi")
            }
        }
    }

    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) doExport() else Toast.makeText(context, "Galeriye kaydetmek için depolama izni gerekli.", Toast.LENGTH_LONG).show()
    }
    val lutPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { importLut(context, uri) }
            if (result.isSuccess) {
                val name = result.getOrThrow()
                if (name !in customLuts) customLuts.add(name)
                LutCache.invalidateCustom(name)
                session.update { it.copy(customLut = name, customLutStrength = 100f) }
                Toast.makeText(context, "LUT eklendi", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, result.exceptionOrNull()?.message ?: "LUT okunamadı", Toast.LENGTH_LONG).show()
            }
        }
    }

    BackHandler(enabled = detail != null) { detail = null }
    BackHandler(enabled = detail == null) { navigator.pop() }

    val mode = when {
        comparing -> PreviewMode.ORIGINAL
        tool == EditorTool.CROP -> PreviewMode.CROP
        else -> PreviewMode.NORMAL
    }

    Column(Modifier.fillMaxSize().background(LensColors.Ground)) {
        // ------------------------------------------------------------------ top bar
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LensIconButton(Icons.Rounded.Close, "Kapat", { navigator.pop() })
            LensIconButton(Icons.AutoMirrored.Rounded.Undo, "Geri al", { session.undo() }, enabled = session.canUndo)
            LensIconButton(Icons.AutoMirrored.Rounded.Redo, "Yinele", { session.redo() }, enabled = session.canRedo)
            Spacer(Modifier.weight(1f))
            CompareButton(onCompare = { comparing = it })
            Box {
                LensIconButton(Icons.Rounded.MoreHoriz, "Diğer", { menuOpen = true })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Histogram " + if (showHistogram) "gizle" else "göster") }, onClick = {
                        showHistogram = !showHistogram
                        menuOpen = false
                    }, leadingIcon = { Icon(Icons.Rounded.BarChart, null) })
                    DropdownMenuItem(text = { Text("Ayarları kopyala") }, onClick = {
                        app.presets.clipboard = session.state.lookOnly()
                        Toast.makeText(context, "Ayarlar kopyalandı", Toast.LENGTH_SHORT).show()
                        menuOpen = false
                    })
                    DropdownMenuItem(text = { Text("Ayarları yapıştır") }, enabled = app.presets.clipboard != null, onClick = {
                        app.presets.clipboard?.let { look -> session.update { it.applyLook(look) } }
                        menuOpen = false
                    })
                    DropdownMenuItem(text = { Text("Ön ayar olarak kaydet") }, onClick = {
                        showPresetDialog = true
                        menuOpen = false
                    })
                    DropdownMenuItem(text = { Text("Tümünü sıfırla") }, onClick = {
                        session.resetAll()
                        selectedLook = null
                        menuOpen = false
                    })
                }
            }
            Spacer(Modifier.width(6.dp))
            PrimaryPill("Kaydet", onClick = {
                saveState = SaveState.Idle
                showExport = true
            }, enabled = session.preview != null)
            Spacer(Modifier.width(6.dp))
        }

        // ------------------------------------------------------------------ canvas
        Box(Modifier.weight(1f).fillMaxWidth()) {
            PhotoCanvas(
                session = session,
                mode = mode,
                gesturesEnabled = tool != EditorTool.CROP && tool != EditorTool.MASKS,
                onCompare = { comparing = it },
            ) { vt ->
                when (tool) {
                    EditorTool.CROP -> {
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
                    }
                    EditorTool.MASKS -> {
                        val mask = activeMask
                        val bmp = session.preview
                        if (mask != null && bmp != null && !comparing) {
                            val g = session.state.geometry
                            val mapper = SourceViewMapper(
                                vt,
                                GeometryMath.sourceToOutputRowMajor(bmp.width, bmp.height, g),
                                GeometryMath.outputToSourceRowMajor(bmp.width, bmp.height, g),
                                bmp.width.toFloat() / bmp.height,
                            )
                            if (mask.kind == MaskKind.PERSON) {
                                // Nothing to drag: the selection comes from detection. Show it instead.
                                session.personMask?.let { pm -> PersonMaskTint(mapper, vt, pm, mask.invert, personTintVisible) }
                            } else {
                                MaskOverlay(
                                    mapper = mapper,
                                    mask = mask,
                                    onChange = { m -> session.preview(session.state.copy(masks = session.state.masks.map { if (it.id == m.id) m else it })) },
                                    onGestureStart = { session.beginGesture() },
                                    onGestureEnd = { session.endGesture() },
                                )
                            }
                        }
                    }
                    else -> Unit
                }
            }
            if (session.loading) {
                CircularProgressIndicator(color = LensColors.Accent, strokeWidth = 2.dp, modifier = Modifier.align(Alignment.Center).size(30.dp))
            }
            session.error?.let { msg ->
                Text(msg, style = LensType.Body, color = LensColors.Muted, modifier = Modifier.align(Alignment.Center).padding(32.dp))
            }
            val usesPerson = session.state.hasEffect(EffectType.BOKEH) ||
                session.state.masks.any { it.enabled && it.kind == MaskKind.PERSON }
            val personMessage = if (usesPerson && !session.loading) personStatusText(session.personStatus) else null
            if (!comparing && personMessage != null) {
                Text(
                    personMessage,
                    style = LensType.Label,
                    color = LensColors.Text,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
                        .background(Color.Black.copy(alpha = 0.55f), LensShapes.Pill).padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
            if (comparing) {
                Text(
                    "Orijinal",
                    style = LensType.Label,
                    color = LensColors.Text,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
                        .background(Color.Black.copy(alpha = 0.55f), LensShapes.Pill).padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
            if (showHistogram) {
                histogram?.let { Histogram(it, Modifier.align(Alignment.TopStart).padding(12.dp)) }
            }
        }

        // ------------------------------------------------------------------ tool area
        Column(Modifier.fillMaxWidth().background(LensColors.Ground)) {
            AnimatedContent(
                targetState = detail to tool,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                label = "tool",
            ) { (d, t) ->
                Box(Modifier.fillMaxWidth()) {
                    when {
                        d is Detail.Film -> FilmDetails(session) { detail = null }
                        d is Detail.Effect -> EffectDetails(session, d.type) { detail = null }
                        d is Detail.LookStrength -> LookDetails(d.look, lookStrength, session, { v ->
                            lookStrength = v
                            val base = lookBase ?: session.state
                            session.preview(scaledLook(base, d.look.state, v / 100f))
                        }) { detail = null }
                        d is Detail.Preset -> PresetDetails(d.preset, onDelete = {
                            scope.launch { app.presets.delete(d.preset.id) }
                            selectedLook = null
                            detail = null
                        }) { detail = null }
                        t == EditorTool.LOOKS -> LooksStrip(
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
                            onPresetDetails = { p -> detail = Detail.Preset(p) },
                            onSavePreset = { showPresetDialog = true },
                            onDetails = { Looks.find(selectedLook ?: "")?.let { detail = Detail.LookStrength(it) } },
                        )
                        t == EditorTool.FILM -> FilmsStrip(session) { detail = Detail.Film }
                        t == EditorTool.EFFECTS -> EffectsStrip(session, null) { type -> detail = Detail.Effect(type) }
                        t == EditorTool.ADJUST -> AdjustTool(
                            session = session,
                            panel = adjustPanel,
                            onPanel = { adjustPanel = it },
                            customLuts = customLuts,
                            onImportLut = { lutPicker.launch(arrayOf("*/*")) },
                            onDeleteLut = { name ->
                                scope.launch {
                                    withContext(Dispatchers.IO) { LutCache.directory()?.let { File(it, name).delete() } }
                                    customLuts.remove(name)
                                    LutCache.invalidateCustom(name)
                                }
                            },
                        )
                        t == EditorTool.CROP -> CropTool(session, onGuides = { cropGuides = it })
                        t == EditorTool.MASKS -> MaskTool(session, selectedMask, { selectedMask = it }, personStatus = session.personStatus)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 4.dp)) {
                EditorTool.entries.forEach { t ->
                    ToolTab(t.label, t.icon, t == tool && detail == null, onClick = {
                        detail = null
                        tool = t
                    }, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    ExportSheet(
        visible = showExport,
        options = exportOptions,
        quality = quality,
        sourceWidth = session.meta.width,
        sourceHeight = session.meta.height,
        saveState = saveState,
        onOptions = { exportOptions = it },
        onQuality = { quality = it },
        onSave = {
            val needsPermission = MediaSaver.needsLegacyPermission() &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else doExport()
        },
        onShare = { uri -> shareMedia(context, uri, exportOptions.format.mime) },
        onDismiss = {
            showExport = false
            saveState = SaveState.Idle
        },
    )

    if (showPresetDialog) {
        SavePresetDialog(onDismiss = { showPresetDialog = false }) { name ->
            scope.launch { app.presets.add(name, session.state) }
            showPresetDialog = false
            Toast.makeText(context, "Ön ayar kaydedildi", Toast.LENGTH_SHORT).show()
        }
    }
}

/** Press-and-hold button that shows the original while held. */
@Composable
private fun CompareButton(onCompare: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .pointerInputCompare(onCompare),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Compare, "Orijinali göster (basılı tut)", tint = LensColors.Text, modifier = Modifier.size(22.dp))
    }
}

private fun Modifier.pointerInputCompare(onCompare: (Boolean) -> Unit): Modifier =
    this.then(
        Modifier.pointerInput(Unit) {
            detectTapGestures(onPress = {
                onCompare(true)
                tryAwaitRelease()
                onCompare(false)
            })
        },
    )

/** Small luminance + RGB histogram drawn over the canvas. */
@Composable
private fun Histogram(data: IntArray, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 132.dp, height = 64.dp).background(Color.Black.copy(alpha = 0.5f), LensShapes.Small)) {
        val bins = 256
        if (data.size < bins * 4) return@Canvas
        val maxV = (0 until bins * 4).maxOf { data[it] }.coerceAtLeast(1).toFloat()
        val colors = listOf(Color(0x99FF5D52), Color(0x994CC584), Color(0x994F8BFF), Color(0xCCF3F1ED))
        for (ch in 0 until 4) {
            val path = Path()
            path.moveTo(0f, size.height)
            for (i in 0 until bins) {
                val v = data[ch * bins + i] / maxV
                path.lineTo(i / (bins - 1f) * size.width, size.height - v * size.height * 0.92f)
            }
            path.lineTo(size.width, size.height)
            path.close()
            if (ch < 3) drawPath(path, colors[ch]) else drawPath(path, colors[ch], style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
        drawLine(Color.White.copy(alpha = 0.15f), Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f))
    }
}

/** Copies a .cube file into the app's LUT folder after validating it. Returns the stored name. */
private fun importLut(context: android.content.Context, uri: Uri): Result<String> = try {
    val text = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
        ?: throw IllegalArgumentException("Dosya açılamadı")
    CubeParser.parse(text, "validate")
    val rawName = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "lut"
    val base = rawName.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9ğüşöçıİĞÜŞÖÇ _-]"), "").take(32).ifBlank { "lut" }
    val dir = LutCache.directory() ?: throw IllegalStateException("LUT klasörü yok")
    var name = "$base.cube"
    var i = 2
    while (File(dir, name).exists()) name = "$base $i.cube".also { i++ }
    File(dir, name).writeText(text)
    Result.success(name)
} catch (e: Exception) {
    Result.failure(IllegalArgumentException(e.message ?: "Geçersiz LUT dosyası"))
}
