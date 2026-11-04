package com.lens035.app.ui.home

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.lens035.app.Lens035App
import com.lens035.app.data.Project
import com.lens035.app.ui.nav.Navigator
import com.lens035.app.ui.nav.Screen
import com.lens035.app.ui.theme.LensColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(navigator: Navigator) {
    val context = LocalContext.current
    val app = context.applicationContext as Lens035App
    val scope = rememberCoroutineScope()
    val projects by app.projects.projects.collectAsState()
    var pendingFilm by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<Project?>(null) }

    LaunchedEffect(Unit) { app.projects.refresh() }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) navigator.push(Screen.PhotoEditor(uri, filmId = pendingFilm))
        pendingFilm = null
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) navigator.push(Screen.VideoEditor(uri))
    }
    fun pickPhoto(filmId: String? = null) {
        pendingFilm = filmId
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    HomeContent(
        projects = projects,
        loadThumb = { project ->
            withContext(Dispatchers.IO) {
                val f = app.projects.thumbFile(project.id)
                if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
            }
        },
        onPickPhoto = { filmId -> pickPhoto(filmId) },
        onPickVideo = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
        onCamera = { navigator.push(Screen.PoseCamera) },
        onOpen = { project ->
            val uri = Uri.parse(project.uri)
            if (project.kind == "video") navigator.push(Screen.VideoEditor(uri, project.id))
            else navigator.push(Screen.PhotoEditor(uri, projectId = project.id))
        },
        onLongPress = { toDelete = it },
    )

    toDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Projeyi sil") },
            text = { Text("Bu düzenleme silinsin mi? Galerideki fotoğraflar etkilenmez.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { app.projects.delete(project.id) }
                    toDelete = null
                }) { Text("Sil", color = LensColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Vazgeç") } },
            containerColor = LensColors.Raised,
        )
    }
}
