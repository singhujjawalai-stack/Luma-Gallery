package com.ujjaw.lumagallery.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.ujjaw.lumagallery.data.MediaItem
import com.ujjaw.lumagallery.data.MediaLib
import com.ujjaw.lumagallery.ui.ViewerSession
import com.ujjaw.lumagallery.util.dayLabel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrashScreen(nav: NavHostController) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { MediaLib.refresh(context) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        MediaLib.refresh(context)
    }

    fun launch(pi: android.app.PendingIntent?) {
        if (pi != null) {
            launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } else {
            MediaLib.refresh(context)
        }
    }

    val trashed = MediaLib.trashed
    var dialogItem by remember { mutableStateOf<MediaItem?>(null) }

    fun openViewer(item: MediaItem) {
        val idx = trashed.indexOfFirst { it.uri == item.uri }
        if (idx < 0) return
        ViewerSession.items.clear()
        ViewerSession.items.addAll(trashed)
        nav.navigate("viewer/$idx")
    }

    dialogItem?.let { item ->
        AlertDialog(
            onDismissRequest = { dialogItem = null },
            containerColor = Color(0xFF1B1B24),
            title = { Text("Trashed item", color = Color.White) },
            text = { Text("${item.name}\n\nRestore it back to your library, or delete it forever.", color = Color.Gray) },
            confirmButton = {
                TextButton(onClick = {
                    dialogItem = null
                    launch(MediaLib.restoreRequest(context, listOf(android.net.Uri.parse(item.uri))))
                }) { Text("Restore", color = Color(0xFF82B1FF)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    dialogItem = null
                    launch(MediaLib.deleteForeverRequest(context, listOf(android.net.Uri.parse(item.uri))))
                }) { Text("Delete forever", color = Color(0xFFFF5252)) }
            }
        )
    }

    if (Build.VERSION.SDK_INT < 30) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Text(
                "Trash",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            EmptyView(
                "Trash needs Android 10+",
                "On this Android version, deleted items are removed permanently."
            )
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Trash", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Row {
                TextButton(
                    enabled = trashed.isNotEmpty(),
                    onClick = {
                        launch(MediaLib.restoreRequest(context, trashed.map { android.net.Uri.parse(it.uri) }))
                    }
                ) { Text("Restore all", color = Color(0xFF82B1FF)) }
                TextButton(
                    enabled = trashed.isNotEmpty(),
                    onClick = {
                        launch(MediaLib.deleteForeverRequest(context, trashed.map { android.net.Uri.parse(it.uri) }))
                    }
                ) { Text("Empty", color = Color(0xFFFF5252)) }
            }
        }
        if (trashed.isEmpty()) {
            EmptyView("Trash is empty", "Items deleted on Android 10+ rest here for 30 days.")
        } else {
            Text(
                "Tap to view · Long-press for Restore / Delete forever",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(1.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(trashed, key = { _, it -> it.uri }) { _, m ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(m.uri)
                            .apply { if (m.isVideo) decoderFactory(VideoFrameDecoder.Factory()) }
                            .build(),
                        contentDescription = m.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .combinedClickable(
                                onClick = { openViewer(m) },
                                onLongClick = { dialogItem = m }
                            )
                    )
                }
            }
        }
    }
}
