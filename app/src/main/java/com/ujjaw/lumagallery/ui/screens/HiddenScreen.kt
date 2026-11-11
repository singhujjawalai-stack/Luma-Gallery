package com.ujjaw.lumagallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.ujjaw.lumagallery.data.MediaLib
import com.ujjaw.lumagallery.data.db.AppDb
import com.ujjaw.lumagallery.data.db.HiddenItemEntity
import com.ujjaw.lumagallery.ui.ViewerSession
import com.ujjaw.lumagallery.util.formatDuration
import kotlinx.coroutines.launch

@Composable
fun HiddenScreen(nav: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hiddenFolders by AppDb.db.hiddenDao().all().collectAsState(initial = emptyList())
    val hiddenItems by AppDb.db.hiddenItemDao().all().collectAsState(initial = emptyList())

    // Media items that still exist on the device, in list order
    val hiddenMedia = remember(hiddenItems) {
        hiddenItems.mapNotNull { h -> MediaLib.allItems.firstOrNull { MediaLib.norm(it.uri) == MediaLib.norm(h.uri) } }
    }

    fun openViewer(item: com.ujjaw.lumagallery.data.MediaItem) {
        val idx = hiddenMedia.indexOfFirst { it.uri == item.uri }
        if (idx < 0) return
        ViewerSession.items.clear()
        ViewerSession.items.addAll(hiddenMedia)
        nav.navigate("viewer/$idx")
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Text(
            "Hidden folders & items",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (hiddenItems.isNotEmpty()) {
            Text(
                "Tap a photo or video to view / play it right here.",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        if (hiddenFolders.isEmpty() && hiddenItems.isEmpty()) {
            EmptyView(
                "Nothing hidden",
                "Long-press any photo to hide it, or open an album and tap the hide icon."
            )
        } else {            LazyColumn(
                Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)
            ) {
                if (hiddenFolders.isNotEmpty()) {
                    item {
                        Text(
                            "Hidden folders",
                            color = Color(0xFF82B1FF),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                    items(hiddenFolders, key = { "f_${it.bucketId}" }) { h ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                h.name,
                                color = Color.White,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                scope.launch {
                                    AppDb.db.hiddenDao().unhide(h.bucketId)
                                    MediaLib.refresh(context)
                                }
                            }) { Text("Unhide", color = Color(0xFF82B1FF)) }
                        }
                    }
                }

                if (hiddenItems.isNotEmpty()) {
                    item {
                        Text(
                            "Hidden photos & videos",
                            color = Color(0xFF82B1FF),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                    items(hiddenItems, key = { it.uri }) { hidden ->
                        val item = MediaLib.allItems.firstOrNull { it.uri == hidden.uri }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = item != null) { if (item != null) openViewer(item) }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1B1B24))
                            ) {
                                if (item != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(item.uri)
                                            .apply {
                                                if (item.isVideo) decoderFactory(VideoFrameDecoder.Factory())
                                            }
                                            .build(),
                                        contentDescription = item.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (item.isVideo) {
                                        Text(
                                            formatDuration(item.durationMs),
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .background(Color.Black.copy(alpha = 0.6f))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item?.name ?: hidden.uri.substringAfterLast('/'),
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    maxLines = 1
                                )
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    AppDb.db.hiddenItemDao().unhide(hidden.uri)
                                    MediaLib.refresh(context)
                                }
                            }) { Text("Unhide", color = Color(0xFF82B1FF)) }
                        }
                    }
                }
            }
        }
    }
}
