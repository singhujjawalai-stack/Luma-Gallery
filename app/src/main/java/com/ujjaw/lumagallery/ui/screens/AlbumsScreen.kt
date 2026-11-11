package com.ujjaw.lumagallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.ujjaw.lumagallery.data.Album
import com.ujjaw.lumagallery.data.MediaLib
import com.ujjaw.lumagallery.data.SettingsStore
import com.ujjaw.lumagallery.data.db.AppDb
import com.ujjaw.lumagallery.data.db.HiddenFolderEntity
import com.ujjaw.lumagallery.ui.ViewerSession
import com.ujjaw.lumagallery.ui.components.MediaGrid
import com.ujjaw.lumagallery.ui.components.pinchToZoomSteps
import kotlinx.coroutines.launch

@Composable
fun AlbumsScreen(nav: NavHostController) {
    val albums = remember(MediaLib.items) { MediaLib.albumsOf(MediaLib.items) }
    val favs by AppDb.db.favoriteDao().all().collectAsState(initial = emptyList())
    val hidden by AppDb.db.hiddenDao().all().collectAsState(initial = emptyList())

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Albums",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { nav.navigate("search") }) {
                Icon(Icons.Filled.Search, "Search", tint = Color.White)
            }
            IconButton(onClick = { nav.navigate("settings") }) {
                Icon(Icons.Filled.Settings, "Settings", tint = Color.White)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            PinCard("Favorites", favs.size, Icons.Filled.Favorite, Modifier.weight(1f)) { nav.navigate("favorites") }
        }
        if (albums.isEmpty()) {
            EmptyView("No albums", "Albums are created from the folders on your device.")
        } else {
            val pinchAcc = remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
            LazyVerticalGrid(
                columns = GridCells.Fixed((SettingsStore.gridColumns - 1).coerceIn(2, 4)),
                modifier = Modifier
                    .weight(1f)
                    .pinchToZoomSteps { z ->
                        pinchAcc.floatValue *= z
                        if (pinchAcc.floatValue > 1.18f) {
                            pinchAcc.floatValue = 1f
                            SettingsStore.updateGridColumns((SettingsStore.gridColumns - 1).coerceIn(2, 6))
                        } else if (pinchAcc.floatValue < 0.85f) {
                            pinchAcc.floatValue = 1f
                            SettingsStore.updateGridColumns((SettingsStore.gridColumns + 1).coerceIn(2, 6))
                        }
                    },
                contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(albums, key = { it.bucketId }) { album ->
                    AlbumCard(album) { nav.navigate("album/${album.bucketId}") }
                }
            }
        }
    }
}

@Composable
private fun PinCard(label: String, count: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1B1B24))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color(0xFF82B1FF), modifier = Modifier.size(18.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
            Text("$label ($count)", color = Color.White, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(album.coverUri)
                .apply { if (album.coverIsVideo) decoderFactory(VideoFrameDecoder.Factory()) }
                .build(),
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                        startY = 120f
                    )
                )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(album.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
            Text("${album.count} items", color = Color(0xFFCCCCCC), fontSize = 12.sp)
        }
    }
}

@Composable
fun AlbumDetailScreen(nav: NavHostController, bucketId: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = MediaLib.items.filter { it.bucketId == bucketId }
    val name = items.firstOrNull()?.bucketName ?: "Album"

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
            Text(
                "$name · ${items.size}",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                scope.launch {
                    AppDb.db.hiddenDao().hide(HiddenFolderEntity(bucketId, name))
                    MediaLib.refresh(context)
                    nav.popBackStack()
                }
            }) {
                Icon(Icons.Filled.VisibilityOff, "Hide album", tint = Color.White)
            }
        }
        if (items.isEmpty()) {
            EmptyView("Empty album")
        } else {
            MediaGrid(
                items = items,
                onOpen = { idx ->
                    ViewerSession.items.clear()
                    ViewerSession.items.addAll(items)
                    nav.navigate("viewer/$idx")
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
