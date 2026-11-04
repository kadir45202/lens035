package com.lens035.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lens035.app.data.Project
import com.lens035.app.ui.theme.LensColors
import com.lens035.app.ui.theme.LensShapes
import com.lens035.app.ui.theme.LensType
import com.lens035.core.film.FilmCatalog
import com.lens035.core.film.FilmRecipe

/** Stateless home screen: everything it shows and every action is passed in. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeContent(
    projects: List<Project>,
    loadThumb: suspend (Project) -> ImageBitmap?,
    onPickPhoto: (filmId: String?) -> Unit,
    onPickVideo: () -> Unit,
    onCamera: () -> Unit,
    onOpen: (Project) -> Unit,
    onLongPress: (Project) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().background(LensColors.Ground),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(top = 18.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LensMark(Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Text("lens035", style = LensType.Display, color = LensColors.Text)
                Spacer(Modifier.weight(1f))
                Text("${FilmCatalog.all.size} film · ${com.lens035.core.model.EffectType.entries.size} efekt", style = LensType.Meta, color = LensColors.Faint)
            }
        }
        item(span = { GridItemSpan(2) }) {
            FrameAction(
                title = "Fotoğraf düzenle",
                subtitle = "Galeriden seç; film, efekt ve Lightroom tarzı ayarlar",
                icon = Icons.Rounded.PhotoLibrary,
                tall = true,
                onClick = { onPickPhoto(null) },
            )
        }
        item {
            FrameAction(
                title = "Video",
                subtitle = "Aynı filmler, kes ve hızlandır",
                icon = Icons.Rounded.Movie,
                onClick = onPickVideo,
            )
        }
        item {
            FrameAction(
                title = "Poz kamerası",
                subtitle = "Hayalet görüntüyle poz verdir",
                icon = Icons.Rounded.AccessibilityNew,
                onClick = onCamera,
            )
        }
        item(span = { GridItemSpan(2) }) {
            Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Text("Film rafı", style = LensType.Title, color = LensColors.Text)
                Text("Bir filme dokun, fotoğrafını seç: o filmle açılır.", style = LensType.Meta, color = LensColors.Faint)
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
                    items(FilmCatalog.all, key = { it.id }) { film -> FilmBox(film) { onPickPhoto(film.id) } }
                }
            }
        }
        item(span = { GridItemSpan(2) }) {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.Bottom) {
                Text("Son projeler", style = LensType.Title, color = LensColors.Text, modifier = Modifier.weight(1f))
                if (projects.isNotEmpty()) Text("${projects.size}", style = LensType.Value, color = LensColors.Faint)
            }
        }
        if (projects.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Text(
                    "Henüz proje yok. Düzenlediğin her fotoğraf burada saklanır; kaldığın yerden devam edebilirsin.",
                    style = LensType.Body,
                    color = LensColors.Faint,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        } else {
            items(projects, key = { it.id }) { project ->
                ProjectCard(
                    project = project,
                    loadThumb = loadThumb,
                    onOpen = { onOpen(project) },
                    onLongPress = { onLongPress(project) },
                )
            }
        }
        item(span = { GridItemSpan(2) }) { Spacer(Modifier.navigationBarsPadding()) }
    }

}

/** The app's mark: a lens ring with a warm halation crescent and a highlight point. */
@Composable
fun LensMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(LensColors.Text, r * 0.92f, c, style = Stroke(r * 0.13f))
        drawArc(LensColors.Accent, 100f, 160f, false, Offset(c.x - r * 0.62f, c.y - r * 0.62f), Size(r * 1.24f, r * 1.24f), style = Stroke(r * 0.2f))
        drawCircle(LensColors.Text, r * 0.16f, Offset(c.x + r * 0.28f, c.y - r * 0.28f))
    }
}

/** Sprocket holes along the top and bottom edges: the 35 mm frame motif of the app. */
private fun DrawScope.sprockets(color: Color) {
    val holeW = 7.dp.toPx()
    val holeH = 5.dp.toPx()
    val gap = 9.dp.toPx()
    val inset = 6.dp.toPx()
    var x = 10.dp.toPx()
    while (x + holeW < size.width - 6.dp.toPx()) {
        drawRoundRect(color, Offset(x, inset), Size(holeW, holeH), CornerRadius(1.5.dp.toPx()))
        drawRoundRect(color, Offset(x, size.height - inset - holeH), Size(holeW, holeH), CornerRadius(1.5.dp.toPx()))
        x += holeW + gap
    }
}

@Composable
private fun FrameAction(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, tall: Boolean = false) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (tall) 148.dp else 132.dp)
            .clip(LensShapes.Card)
            .background(LensColors.Surface)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) { sprockets(LensColors.Ground) }
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 24.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = if (tall) LensColors.Accent else LensColors.Text, modifier = Modifier.size(if (tall) 28.dp else 24.dp))
            Column {
                Text(title, style = if (tall) LensType.Heading else LensType.Title, color = LensColors.Text)
                Text(subtitle, style = LensType.Meta, color = LensColors.Faint, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A film box on the shelf: identity colour, code and ISO like a real film carton. */
@Composable
private fun FilmBox(film: FilmRecipe, onClick: () -> Unit) {
    val swatch = Color(film.swatch.toInt())
    Column(
        Modifier
            .width(76.dp)
            .clip(LensShapes.Thumb)
            .background(LensColors.Surface)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(46.dp).background(swatch)) {
            Text(
                film.code,
                style = LensType.Code,
                color = if (swatchIsLight(film.swatch)) Color(0xFF111111) else Color(0xFFF7F5F0),
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            )
        }
        Column(Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
            Text(film.name, style = LensType.Meta, color = LensColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("ISO ${film.iso}", style = LensType.Code, color = LensColors.Faint)
        }
    }
}

private fun swatchIsLight(argb: Long): Boolean {
    val r = ((argb shr 16) and 0xFFL) / 255f
    val g = ((argb shr 8) and 0xFFL) / 255f
    val b = (argb and 0xFFL) / 255f
    return 0.2126f * r + 0.7152f * g + 0.0722f * b > 0.6f
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCard(project: Project, loadThumb: suspend (Project) -> ImageBitmap?, onOpen: () -> Unit, onLongPress: () -> Unit) {
    val thumb by produceState<ImageBitmap?>(null, project.id, project.updatedAt) {
        value = loadThumb(project)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(LensShapes.Thumb)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.82f).clip(LensShapes.Thumb).background(LensColors.Surface)) {
            thumb?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            val film = FilmCatalog.find(project.state.film?.id)
            if (film != null) {
                Row(
                    Modifier.align(Alignment.BottomStart).padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.55f), LensShapes.Small).padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(width = 3.dp, height = 10.dp).background(Color(film.swatch.toInt())))
                    Spacer(Modifier.width(4.dp))
                    Text(film.code, style = LensType.Code, color = LensColors.Text)
                }
            }
            if (project.kind == "video") {
                Icon(Icons.Rounded.Movie, null, tint = LensColors.Text, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(18.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            relativeTime(project.updatedAt, System.currentTimeMillis()),
            style = LensType.Meta,
            color = LensColors.Faint,
        )
    }
}

/** "az önce", "12 dk önce", "3 saat önce", "dün", "4 gün önce", then a date; always Turkish. */
fun relativeTime(then: Long, now: Long): String {
    val diff = (now - then).coerceAtLeast(0L)
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diff < minute -> "az önce"
        diff < hour -> "${diff / minute} dk önce"
        diff < day -> "${diff / hour} saat önce"
        diff < 2 * day -> "dün"
        diff < 7 * day -> "${diff / day} gün önce"
        else -> java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale("tr")).format(java.util.Date(then))
    }
}
