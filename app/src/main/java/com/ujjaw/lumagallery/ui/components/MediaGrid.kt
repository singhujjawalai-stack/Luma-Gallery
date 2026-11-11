package com.ujjaw.lumagallery.ui.components

import android.app.WallpaperManager
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.ujjaw.lumagallery.data.MediaItem
import com.ujjaw.lumagallery.data.MediaLib
import com.ujjaw.lumagallery.data.SettingsStore
import com.ujjaw.lumagallery.data.db.AppDb
import com.ujjaw.lumagallery.data.db.FavoriteEntity
import com.ujjaw.lumagallery.data.db.HiddenItemEntity
import com.ujjaw.lumagallery.util.applyWallpaper
import com.ujjaw.lumagallery.util.dayLabel
import com.ujjaw.lumagallery.util.formatDuration
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface GridEntry {
    data class Header(val text: String) : GridEntry
    data class Cell(val item: MediaItem, val index: Int) : GridEntry
}

fun buildEntries(items: List<MediaItem>): List<GridEntry> {
    val out = mutableListOf<GridEntry>()
    var current = ""
    items.forEachIndexed { i, m ->
        val d = dayLabel(m.dateTaken)
        if (d != current) {
            out += GridEntry.Header(d)
            current = d
        }
        out += GridEntry.Cell(m, i)
    }
    return out
}

@Composable
fun MediaGrid(
    items: List<MediaItem>,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
    bottomPad: Dp = 8.dp,
    showDates: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Long-press action sheet
    var actionItem by remember { mutableStateOf<MediaItem?>(null) }
    val favSet by AppDb.db.favoriteDao().all()
        .map { list -> list.map { it.uri }.toSet() }
        .collectAsState(initial = emptySet())
    val delLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        MediaLib.refresh(context)
    }

    // Multi-select
    var selecting by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }

    // Pinch -> columns
    val pinchAcc = remember { mutableFloatStateOf(1f) }
    fun onPinch(zoom: Float) {
        pinchAcc.floatValue *= zoom
        if (pinchAcc.floatValue > 1.18f) {
            pinchAcc.floatValue = 1f
            SettingsStore.updateGridColumns((SettingsStore.gridColumns - 1).coerceIn(2, 6))
        } else if (pinchAcc.floatValue < 0.85f) {
            pinchAcc.floatValue = 1f
            SettingsStore.updateGridColumns((SettingsStore.gridColumns + 1).coerceIn(2, 6))
        }
    }

    actionItem?.let { item ->
        AlertDialog(
            onDismissRequest = { actionItem = null },
            containerColor = Color(0xFF16161E),
            title = {
                Text(item.name, color = Color.White, fontSize = 15.sp, maxLines = 1)
            },
            text = {
                Column {
                    TextButton(onClick = {
                        actionItem = null
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = if (item.isVideo) "video/*" else "image/*"
                            putExtra(Intent.EXTRA_STREAM, android.net.Uri.parse(item.uri))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share"))
                    }) { Text("Share", color = Color.White) }

                    TextButton(onClick = {
                        actionItem = null
                        scope.launch { MediaLib.toggleFavorite(item.uri) }
                    }) {
                        Text(
                            if (MediaLib.norm(item.uri) in favSet) "Remove from favorites" else "Add to favorites",
                            color = Color.White
                        )
                    }

                    if (!item.isVideo) {
                        TextButton(onClick = {
                            actionItem = null
                            scope.launch {
                                val ok = applyWallpaper(
                                    context,
                                    android.net.Uri.parse(item.uri),
                                    WallpaperManager.FLAG_SYSTEM
                                )
                                Toast.makeText(
                                    context,
                                    if (ok) "Home wallpaper set" else "Couldn't set wallpaper",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }) { Text("Set as home wallpaper", color = Color.White) }
                    }

                    TextButton(onClick = {
                        actionItem = null
                        selecting = true
                        if (item.uri !in selected) selected.add(item.uri)
                    }) { Text("Select", color = Color(0xFF82B1FF)) }

                    TextButton(onClick = {
                        actionItem = null
                        scope.launch {
                            AppDb.db.hiddenItemDao().hide(HiddenItemEntity(item.uri))
                            MediaLib.refresh(context)
                            Toast.makeText(context, "Hidden from gallery", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Hide from gallery", color = Color.White) }

                    TextButton(onClick = {
                        actionItem = null
                        val pi = MediaLib.trashRequest(context, listOf(android.net.Uri.parse(item.uri)))
                        if (pi != null) {
                            delLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                        } else {
                            MediaLib.refresh(context)
                        }
                    }) { Text("Delete", color = Color(0xFFFF5252)) }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { actionItem = null }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }

    fun toggleSelect(uri: String) {
        if (uri in selected) selected.remove(uri) else selected.add(uri)
    }

    fun shareSelected() {
        val picked = items.filter { it.uri in selected }
        if (picked.isEmpty()) return
        val allVideo = picked.all { it.isVideo }
        val anyVideo = picked.any { it.isVideo }
        val type = when {
            allVideo -> "video/*"
            anyVideo -> "*/*"
            else -> "image/*"
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            this.type = type
            putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                ArrayList(picked.map { android.net.Uri.parse(it.uri) })
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share"))
    }

    Box(modifier) {
        val entries = if (showDates) buildEntries(items) else items.mapIndexed { i, m -> GridEntry.Cell(m, i) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(SettingsStore.gridColumns),
            modifier = Modifier
                .fillMaxSize()
                .pinchToZoomSteps { onPinch(it) },
            contentPadding = PaddingValues(1.dp, 1.dp, 1.dp, bottomPad),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            itemsIndexed(
                entries,
                span = { _, e -> GridItemSpan(if (e is GridEntry.Header) maxLineSpan else 1) },
                key = { _, e ->
                    when (e) {
                        is GridEntry.Header -> "h_${e.text}"
                        is GridEntry.Cell -> e.item.uri
                    }
                }
            ) { _, entry ->
                when (entry) {
                    is GridEntry.Header -> {
                        Box(Modifier.fillMaxWidth()) {
                            Text(
                                text = entry.text,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = Color.White,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp)
                            )
                        }
                    }
                    is GridEntry.Cell -> {
                        val isSelected = selecting && entry.item.uri in selected
                        Box {
                            MediaThumb(
                                item = entry.item,
                                modifier = Modifier.aspectRatio(1f),
                                onClick = {
                                    if (selecting) toggleSelect(entry.item.uri) else onOpen(entry.index)
                                },
                                onLongClick = {
                                    if (!selecting) actionItem = entry.item else toggleSelect(entry.item.uri)
                                }
                            )
                            if (isSelected) {
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .border(3.dp, Color(0xFF82B1FF))
                                )
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = Color(0xFF82B1FF),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selecting) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF16161E))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    selecting = false
                    selected.clear()
                }) { Icon(Icons.Filled.Close, "Cancel selection", tint = Color.White) }
                Text("${selected.size} selected", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { shareSelected() }) {
                    Icon(Icons.Filled.Share, "Share selected", tint = Color.White)
                }
                IconButton(onClick = {
                    scope.launch {
                        selected.forEach {
                            AppDb.db.favoriteDao().add(FavoriteEntity(it))
                        }
                        Toast.makeText(context, "Added to favorites", Toast.LENGTH_SHORT).show()
                        selecting = false
                        selected.clear()
                    }
                }) { Icon(Icons.Filled.Favorite, "Favorite selected", tint = Color.White) }
                IconButton(onClick = {
                    scope.launch {
                        selected.forEach { AppDb.db.hiddenItemDao().hide(HiddenItemEntity(it)) }
                        MediaLib.refresh(context)
                        Toast.makeText(context, "Hidden from gallery", Toast.LENGTH_SHORT).show()
                        selecting = false
                        selected.clear()
                    }
                }) { Icon(Icons.Filled.VisibilityOff, "Hide selected", tint = Color.White) }
                if (selected.size == 1 && items.firstOrNull { it.uri == selected.first() }?.isVideo == false) {
                    IconButton(onClick = {
                        scope.launch {
                            val ok = applyWallpaper(context, android.net.Uri.parse(selected.first()), WallpaperManager.FLAG_SYSTEM)
                            Toast.makeText(
                                context,
                                if (ok) "Home wallpaper set" else "Couldn't set wallpaper",
                                Toast.LENGTH_SHORT
                            ).show()
                            selecting = false
                            selected.clear()
                        }
                    }) { Icon(Icons.Filled.Wallpaper, "Set wallpaper", tint = Color.White) }
                }
                IconButton(onClick = {
                    val pi = MediaLib.trashRequest(context, selected.map { android.net.Uri.parse(it) })
                    if (pi != null) {
                        delLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    } else {
                        MediaLib.refresh(context)
                    }
                    selecting = false
                    selected.clear()
                }) { Icon(Icons.Filled.Delete, "Delete selected", tint = Color(0xFFFF5252)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaThumb(
    item: MediaItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val context = LocalContext.current
    Box(
        modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.uri)
                // Fixed decode size: keeps Coil's cache stable when the column
                // count changes (pinch), so cells don't re-decode and lag
                .size(512)
                .crossfade(false)
                .apply { if (item.isVideo) decoderFactory(VideoFrameDecoder.Factory()) }
                .build(),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (item.isVideo) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Video",
                    tint = Color.White
                )
                Text(
                    text = formatDuration(item.durationMs),
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
    }
}
