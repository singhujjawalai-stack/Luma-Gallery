package com.ujjaw.lumagallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ujjaw.lumagallery.data.MediaLib
import com.ujjaw.lumagallery.data.db.AppDb
import com.ujjaw.lumagallery.data.db.ContentEntity
import com.ujjaw.lumagallery.ui.ViewerSession
import com.ujjaw.lumagallery.ui.components.MediaGrid
import com.ujjaw.lumagallery.work.ContentIndexWorker

/** Every query word must appear in the image's detected labels or recognized text. */
private fun contentMatch(row: ContentEntity, query: String): Boolean {
    val hay = (row.labels + " " + row.text).lowercase()
    val tokens = query.lowercase().trim().split(Regex("\\s+")).filter { it.length >= 2 }
    if (tokens.isEmpty()) return false
    return tokens.all { hay.contains(it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(nav: NavHostController) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 = names, 1 = content

    val contentRows by AppDb.db.contentDao().all().collectAsState(initial = emptyList())
    val workList by remember {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow("content_index")
    }.collectAsState(initial = emptyList())
    val indexing = workList.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }

    val nameResults = remember(query, MediaLib.items) {
        val q = query.trim()
        if (q.isEmpty()) MediaLib.items
        else MediaLib.items.filter { m ->
            m.name.contains(q, ignoreCase = true) || m.bucketName.contains(q, ignoreCase = true)
        }
    }

    val contentResults = remember(query, contentRows, MediaLib.items) {
        if (query.isBlank()) emptyList()
        else contentRows
            .filter { contentMatch(it, query) }
            .mapNotNull { MediaLib.find(it.mediaUri) }
    }

    val results = if (mode == 0) nameResults else contentResults

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Text(
            "Search",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = {
                Text(
                    if (mode == 0) "Search file names and folders" else "Search what's in your photos…",
                    color = Color.Gray
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = Color.Gray)
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1B1B24),
                unfocusedContainerColor = Color(0xFF1B1B24),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Color(0xFF82B1FF),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("Names" to 0, "Content" to 1).forEach { (label, v) ->
                FilterChip(
                    selected = mode == v,
                    onClick = { mode = v },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF82B1FF),
                        selectedLabelColor = Color.Black
                    )
                )
            }
            if (mode == 1 && contentRows.isNotEmpty()) {
                Text("${contentRows.size} indexed", color = Color(0xFF777777), fontSize = 12.sp)
            }
            if (indexing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF82B1FF))
            }
        }

        if (mode == 1 && contentRows.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (indexing) {
                    CircularProgressIndicator(color = Color(0xFF82B1FF))
                    Text(
                        "Building content index…",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        "Reading text and objects from your photos, right on this device.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                } else {
                    Text(
                        "Search inside your photos",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )
                    Text(
                        "Find photos by what they contain — like \"red ball\" or any text in a picture. Everything is analyzed on your phone; nothing goes online.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Button(
                        onClick = {
                            WorkManager.getInstance(context).enqueueUniqueWork(
                                "content_index",
                                ExistingWorkPolicy.REPLACE,
                                OneTimeWorkRequestBuilder<ContentIndexWorker>().build()
                            )
                        },
                        modifier = Modifier.padding(top = 20.dp)
                    ) { Text("Index photos") }
                }
            }
        } else if (results.isEmpty()) {
            if (mode == 1 && query.isBlank() && contentRows.isNotEmpty()) {
                EmptyView(
                    "Search inside your photos",
                    "Type a word — objects, scenes, or text you remember in a picture."
                )
            } else {
                EmptyView(
                    "No results",
                    if (mode == 1) "Try another word, or check that indexing has finished."
                    else "Try a different search term."
                )
            }
        } else {
            MediaGrid(
                items = results,
                onOpen = { idx ->
                    ViewerSession.items.clear()
                    ViewerSession.items.addAll(results)
                    nav.navigate("viewer/$idx")
                },
                modifier = Modifier.weight(1f),
                bottomPad = 96.dp
            )
        }
    }
}
