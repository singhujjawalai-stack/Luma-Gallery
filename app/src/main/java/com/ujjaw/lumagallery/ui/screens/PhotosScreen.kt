package com.ujjaw.lumagallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ujjaw.lumagallery.data.MediaLib
import com.ujjaw.lumagallery.ui.ViewerSession
import com.ujjaw.lumagallery.ui.components.MediaGrid
import com.ujjaw.lumagallery.util.MemoryGroup
import com.ujjaw.lumagallery.util.monthMemories
import com.ujjaw.lumagallery.util.onThisDay

@Composable
fun PhotosScreen(nav: NavHostController) {
    val items = MediaLib.items
    var kind by remember { mutableIntStateOf(0) } // 0 = all, 1 = photos, 2 = videos
    val filtered = remember(items, kind) {
        when (kind) {
            1 -> items.filter { !it.isVideo }
            2 -> items.filter { it.isVideo }
            else -> items
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Photos",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { nav.navigate("search") }) {
                Icon(Icons.Filled.Search, "Search", tint = Color.White)
            }
        }
        // Photo / video filter
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All" to 0, "Photos" to 1, "Videos" to 2).forEach { (label, v) ->
                val sel = kind == v
                Text(
                    label,
                    color = if (sel) Color.Black else Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) Color(0xFF82B1FF) else Color(0xFF1B1B24))
                        .clickable { kind = v }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
        if (filtered.isEmpty()) {
            EmptyView(
                "Nothing here yet",
                when (kind) {
                    1 -> "No photos on the device (videos may exist)."
                    2 -> "No videos on the device (photos may exist)."
                    else -> "Photos and videos on your device will appear here."
                }
            )
        } else {
            val memories = remember(filtered) { onThisDay(filtered) }
            val months = remember(filtered) { monthMemories(filtered) }
            if (memories.isNotEmpty()) {
                MemoryRow("On this day", memories, nav)
            }
            if (months.isNotEmpty()) {
                MemoryRow("Memories", months, nav)
            }
            MediaGrid(
                items = filtered,
                onOpen = { idx ->
                    ViewerSession.items.clear()
                    ViewerSession.items.addAll(filtered)
                    nav.navigate("viewer/$idx")
                },
                modifier = Modifier.weight(1f),
                bottomPad = 96.dp
            )
        }
    }
}

@Composable
private fun MemoryRow(title: String, groups: List<MemoryGroup>, nav: NavHostController) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(groups) { g ->
                val cover = g.items.firstOrNull()?.uri ?: return@items
                Box(
                    Modifier
                        .width(150.dp)
                        .height(110.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            ViewerSession.items.clear()
                            ViewerSession.items.addAll(g.items)
                            nav.navigate("viewer/0")
                        }
                ) {
                    AsyncImage(
                        model = cover,
                        contentDescription = g.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                    startY = 60f
                                )
                            )
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                        Text(g.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(g.subtitle, color = Color(0xFFCCCCCC), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyView(title: String, subtitle: String = "") {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle,
                color = Color.Gray,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
