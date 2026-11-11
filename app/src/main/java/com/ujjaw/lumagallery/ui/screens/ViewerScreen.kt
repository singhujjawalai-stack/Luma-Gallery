package com.ujjaw.lumagallery.ui.screens

import android.content.Intent
import android.app.WallpaperManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.ujjaw.lumagallery.data.MediaLib
import com.ujjaw.lumagallery.data.SettingsStore
import com.ujjaw.lumagallery.data.db.AppDb
import com.ujjaw.lumagallery.ui.ViewerSession
import com.ujjaw.lumagallery.ui.components.VideoPlayer
import com.ujjaw.lumagallery.ui.components.ZoomableImage
import com.ujjaw.lumagallery.util.decodeScaled
import com.ujjaw.lumagallery.util.formatDuration
import com.ujjaw.lumagallery.util.formatSize
import com.ujjaw.lumagallery.util.fullDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(nav: NavHostController, startIndex: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = ViewerSession.items

    if (items.isEmpty()) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, items.size - 1),
        pageCount = { items.size }
    )
    var scrollEnabled by remember { mutableStateOf(true) }
    var chromeVisible by remember { mutableStateOf(true) }
    var slideshow by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var wallpaperDialog by remember { mutableStateOf(false) }
    var wallpaperBusy by remember { mutableStateOf(false) }
    var pendingUris by remember { mutableStateOf<List<String>>(emptyList()) }
    val favSet by AppDb.db.favoriteDao().all()
        .map { list -> list.map { MediaLib.norm(it.uri) }.toSet() }
        .collectAsState(initial = emptySet())

    // Slideshow auto-advance
    LaunchedEffect(slideshow, pagerState.currentPage, items.size) {
        if (slideshow && items.isNotEmpty()) {
            delay(SettingsStore.slideshowMs)
            val next = pagerState.currentPage + 1
            if (next >= items.size) slideshow = false
            else pagerState.animateScrollToPage(next)
        }
    }

    // Keep index valid if items are removed (e.g. deleted)
    LaunchedEffect(items.size) {
        if (items.isEmpty()) nav.popBackStack()
        else if (pagerState.currentPage >= items.size) pagerState.scrollToPage(items.size - 1)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        // Only treat as deleted when the user actually confirmed the system dialog
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            MediaLib.refresh(context)
            val gone = pendingUris.toSet()
            items.removeAll { it.uri in gone }
        }
        pendingUris = emptyList()
    }

    fun current(): com.ujjaw.lumagallery.data.MediaItem? = items.getOrNull(pagerState.currentPage)

    fun share(item: com.ujjaw.lumagallery.data.MediaItem) {
        val uri = android.net.Uri.parse(item.uri)
        val type = if (item.isVideo) "video/*" else "image/*"
        val intent = Intent(Intent.ACTION_SEND).apply {
            this.type = type
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share"))
    }

    fun trash(item: com.ujjaw.lumagallery.data.MediaItem) {
        pendingUris = listOf(item.uri)
        val pi = MediaLib.trashRequest(context, listOf(android.net.Uri.parse(item.uri)))
        if (pi != null) launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        else {
            MediaLib.refresh(context)
            items.remove(item)
        }
    }

    val zoomStates = remember { androidx.compose.runtime.mutableStateMapOf<Int, com.ujjaw.lumagallery.ui.components.ZoomState>() }

    fun setWallpaper(which: Int) {
        val item = current() ?: return
        if (item.isVideo) {
            android.widget.Toast.makeText(context, "Videos can't be wallpapers", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        wallpaperDialog = false
        wallpaperBusy = true
        scope.launch {
            // If the user zoomed/panned, crop the wallpaper to exactly what they see
            var crop: android.graphics.RectF? = null
            zoomStates[pagerState.currentPage]?.let { zs ->
                if (zs.zoomed && zs.imageWidth > 0 && zs.imageHeight > 0) {
                    crop = com.ujjaw.lumagallery.util.visibleCropFraction(
                        zs.scale,
                        zs.offset.x,
                        zs.offset.y,
                        zs.container.width,
                        zs.container.height,
                        zs.imageWidth,
                        zs.imageHeight
                    )
                }
            }
            val ok = com.ujjaw.lumagallery.util.applyWallpaper(
                context,
                android.net.Uri.parse(item.uri),
                which,
                crop
            )
            wallpaperBusy = false
            android.widget.Toast.makeText(
                context,
                if (ok) "Wallpaper set" else "Couldn't set wallpaper",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = scrollEnabled,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val m = items.getOrNull(page) ?: return@HorizontalPager
            if (m.isVideo) {
                // Keep player controls visible above the bottom action bar
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = 96.dp)
                ) {
                    androidx.compose.runtime.key(m.uri) {
                        VideoPlayer(
                            uri = m.uri,
                            active = page == pagerState.currentPage,
                            onControllerVisible = { vis -> chromeVisible = vis }
                        )
                    }
                }
            } else {
                ZoomableImage(
                    uri = m.uri,
                    onZoomChanged = { scrollEnabled = !it },
                    onTap = { chromeVisible = !chromeVisible },
                    onState = { zs -> zoomStates[page] = zs }
                )
            }
        }

        // Top bar
        androidx.compose.animation.AnimatedVisibility(
            visible = chromeVisible,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    val m = items.getOrNull(pagerState.currentPage)
                    Text(
                        fullDate(m?.dateTaken ?: 0L),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("${pagerState.currentPage + 1} / ${items.size}", color = Color.Gray, fontSize = 11.sp)
                }
                IconButton(onClick = { showInfo = true }) {
                    Icon(Icons.Filled.Info, "Info", tint = Color.White)
                }
            }
        }

        // Bottom actions
        val item = items.getOrNull(pagerState.currentPage)
        androidx.compose.animation.AnimatedVisibility(
            visible = chromeVisible,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f))
                .navigationBarsPadding()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (item != null) {
                ActionIcon(Icons.Filled.Share, "Share") { share(item) }
                if (!item.isVideo) {
                    ActionIcon(Icons.Filled.Edit, "Edit") {
                        nav.navigate("editor/${item.id}")
                    }
                }
                ActionIcon(
                    if (MediaLib.norm(item.uri) in favSet) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    "Favorite"
                ) {
                    scope.launch { MediaLib.toggleFavorite(item.uri) }
                }
                ActionIcon(
                    if (slideshow) Icons.Filled.Pause else Icons.Filled.Slideshow,
                    if (slideshow) "Stop slideshow" else "Slideshow"
                ) { slideshow = !slideshow }
                ActionIcon(Icons.Filled.Wallpaper, if (wallpaperBusy) "Setting…" else "Wallpaper") {
                    if (!wallpaperBusy) wallpaperDialog = true
                }
                ActionIcon(Icons.Filled.Delete, "Delete") { trash(item) }
            }
            }
        }

        if (wallpaperDialog) {
            AlertDialog(
                onDismissRequest = { wallpaperDialog = false },
                containerColor = Color(0xFF16161E),
                title = { Text("Set as wallpaper", color = Color.White) },
                text = {
                    Column {
                        Text("Choose where to apply this wallpaper.", color = Color.Gray, fontSize = 13.sp)
                        TextButton(onClick = { setWallpaper(WallpaperManager.FLAG_SYSTEM) }) {
                            Text("Home screen", color = Color(0xFF82B1FF))
                        }
                        TextButton(onClick = { setWallpaper(WallpaperManager.FLAG_LOCK) }) {
                            Text("Lock screen", color = Color(0xFF82B1FF))
                        }
                        TextButton(onClick = {
                            setWallpaper(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                        }) {
                            Text("Both", color = Color(0xFF82B1FF))
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { wallpaperDialog = false }) { Text("Cancel", color = Color.Gray) }
                }
            )
        }

        if (showInfo) {
            val m = current()
            ModalBottomSheet(onDismissRequest = { showInfo = false }, containerColor = Color(0xFF16161E)) {
                if (m != null) {
                    Column(Modifier.padding(24.dp)) {
                        Text(m.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        InfoRow("Type", if (m.isVideo) "Video" else "Photo")
                        InfoRow("Date", fullDate(m.dateTaken))
                        InfoRow("Folder", m.bucketName)
                        if (m.width > 0 && m.height > 0) InfoRow("Resolution", "${m.width} × ${m.height}")
                        if (m.isVideo && m.durationMs > 0) InfoRow("Duration", formatDuration(m.durationMs))
                        InfoRow("Size", formatSize(m.size))
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(icon, label, tint = Color.White)
        }
        Text(label, color = Color(0xFFBBBBBB), fontSize = 10.sp)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp)
    }
}
