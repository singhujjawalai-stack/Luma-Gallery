package com.ujjaw.lumagallery.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ujjaw.lumagallery.data.MediaLib
import com.ujjaw.lumagallery.data.db.AppDb
import com.ujjaw.lumagallery.data.db.FaceEntity
import com.ujjaw.lumagallery.data.db.PersonEntity
import com.ujjaw.lumagallery.ui.ViewerSession
import com.ujjaw.lumagallery.ui.components.MediaGrid
import com.ujjaw.lumagallery.util.decodeScaled
import com.ujjaw.lumagallery.work.FaceIndexWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.launch

@Composable
fun PeopleScreen(nav: NavHostController) {
    val context = LocalContext.current
    val persons by AppDb.db.personDao().all().collectAsState(initial = emptyList())

    val workList by remember {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow("face_index")
    }.collectAsState(initial = emptyList())
    val running = workList.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }

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
                "People",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                modifier = Modifier.weight(1f)
            )
            if (running) {
                androidx.compose.material3.CircularProgressIndicator(
                    Modifier.size(22.dp),
                    color = Color(0xFF82B1FF),
                    strokeWidth = 2.dp
                )
            }
            IconButton(onClick = {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "face_index",
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<FaceIndexWorker>().build()
                )
            }) {
                Icon(Icons.Filled.Face, "Scan faces", tint = Color.White)
            }
        }
        if (persons.isEmpty()) {
            EmptyView(
                if (running) "Scanning your photos…" else "No people found yet",
                "Tap the face icon to scan your photos on-device. Faces are grouped automatically — nothing leaves your phone."
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 96.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(persons, key = { it.id }) { person ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(Color(0xFF1B1B24))
                                .clickable { nav.navigate("person/${person.id}") }
                        ) {
                            if (person.coverUri != null) {
                                AsyncImage(
                                    model = person.coverUri,
                                    contentDescription = person.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Text(person.name, color = Color.White, fontSize = 13.sp, maxLines = 1)
                        Text("${person.count} photos", color = Color.Gray, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FaceThumb(face: FaceEntity, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bmp by remember(face.mediaUri, face.hash) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(face.mediaUri, face.hash) {
        bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val src = decodeScaled(context, android.net.Uri.parse(face.mediaUri), 640) ?: return@withContext null
                val left = (face.l * src.width).toInt().coerceIn(0, src.width - 1)
                val top = (face.t * src.height).toInt().coerceIn(0, src.height - 1)
                val w = ((face.r - face.l) * src.width).toInt().coerceIn(1, src.width - left)
                val h = ((face.b - face.t) * src.height).toInt().coerceIn(1, src.height - top)
                try {
                    Bitmap.createBitmap(src, left, top, w, h)
                } catch (e: Exception) {
                    null
                } ?: src
            } catch (e: Exception) {
                null
            }
        }
    }
    Box(
        modifier
            .clip(CircleShape)
            .background(Color(0xFF1B1B24))
    ) {
        bmp?.let {
            androidx.compose.foundation.Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Face",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersonScreen(nav: NavHostController, id: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val persons by AppDb.db.personDao().all().collectAsState(initial = emptyList())
    val person = persons.firstOrNull { it.id == id }
    val faces by AppDb.db.faceDao().forPerson(id).collectAsState(initial = emptyList())

    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var moveFace by remember { mutableStateOf<FaceEntity?>(null) }
    var fixMode by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }

    suspend fun recountPerson(pid: Long) {
        val n = AppDb.db.faceDao().forPersonOnce(pid).size
        val p = AppDb.db.personDao().byId(pid) ?: return
        if (n == 0) AppDb.db.personDao().delete(pid)
        else AppDb.db.personDao().update(p.copy(count = n))
    }

    fun moveFaceTo(face: FaceEntity, target: Long?) {
        scope.launch {
            when (target) {
                null -> AppDb.db.faceDao().deleteFace(face.id)
                -1L -> {
                    val persons = AppDb.db.personDao().allOnce()
                    val newId = AppDb.db.personDao().insert(
                        PersonEntity(
                            name = "Person ${persons.size + 1}",
                            centroid = face.hash,
                            count = 1,
                            coverUri = face.mediaUri
                        )
                    )
                    AppDb.db.faceDao().reassign(face.id, newId)
                    recountPerson(face.personId)
                }
                else -> {
                    AppDb.db.faceDao().reassign(face.id, target)
                    recountPerson(target)
                    recountPerson(face.personId)
                }
            }
            moveFace = null
        }
    }

    if (person == null) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
            EmptyView("Person not found")
        }
        return
    }

    val media = faces.map { it.mediaUri }.distinct().mapNotNull { uri -> MediaLib.find(uri) }

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
                person.name,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                newName = person.name
                renaming = true
            }) { Icon(Icons.Filled.Edit, "Rename", tint = Color.White) }
            IconButton(onClick = { deleting = true }) {
                Icon(Icons.Filled.Delete, "Delete person", tint = Color.White)
            }
        }
        if (media.isEmpty()) {
            EmptyView("No photos", "Rescan to find more photos of this person.")
        } else {
            Column(Modifier.weight(1f)) {
                // Fix-grouping entry point
                if (!fixMode) {
                    TextButton(
                        onClick = { fixMode = true },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text("✎  Fix grouping", color = Color(0xFF82B1FF), fontSize = 14.sp)
                    }
                } else {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            "Fix grouping",
                            color = Color(0xFF82B1FF),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            "1. Tap a wrong face below → tell the app who it really is.\n2. Or merge this whole person into another person.",
                            color = Color(0xFFBBBBBB),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                    if (faces.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(faces, key = { it.id }) { f ->
                                FaceThumb(
                                    face = f,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .combinedClickable(
                                            onClick = { moveFace = f },
                                            onLongClick = { moveFace = f }
                                        )
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = { merging = true },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text("⇄  Merge this person into another…", color = Color(0xFF82B1FF), fontSize = 14.sp)
                    }
                    TextButton(
                        onClick = { fixMode = false },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text("Done", color = Color(0xFFBBBBBB), fontSize = 14.sp)
                    }
                }
                MediaGrid(
                    items = media,
                    onOpen = { idx ->
                        ViewerSession.items.clear()
                        ViewerSession.items.addAll(media)
                        nav.navigate("viewer/$idx")
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // Manual grouping correction
    moveFace?.let { face ->
        AlertDialog(
            onDismissRequest = { moveFace = null },
            containerColor = Color(0xFF1B1B24),
            title = { Text("Who is this face?", color = Color.White) },
            text = {
                Column {
                    Text(
                        "The app currently groups this face with \"${person.name}\". Choose the right person, or create a new one.",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                    persons.filter { it.id != face.personId }.forEach { p ->
                        TextButton(onClick = { moveFaceTo(face, p.id) }) {
                            Text("→ ${p.name}", color = Color.White)
                        }
                    }
                    TextButton(onClick = { moveFaceTo(face, -1L) }) {
                        Text("＋ New person", color = Color(0xFF82B1FF))
                    }
                    TextButton(onClick = { moveFaceTo(face, null) }) {
                        Text("Not a person — remove", color = Color(0xFFFF5252))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { moveFace = null }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }

    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = Color(0xFF1B1B24),
            title = { Text("Rename", color = Color.White) },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF82B1FF)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    renaming = false
                    scope.launch {
                        if (newName.isNotBlank()) AppDb.db.personDao().rename(id, newName.trim())
                    }
                }) { Text("Save", color = Color(0xFF82B1FF)) }
            },
            dismissButton = {
                TextButton(onClick = { renaming = false }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }

    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            containerColor = Color(0xFF1B1B24),
            title = { Text("Remove this person?", color = Color.White) },
            text = { Text("Their photos stay untouched — only the grouping is removed.", color = Color.Gray) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    scope.launch {
                        AppDb.db.faceDao().deleteForPerson(id)
                        AppDb.db.personDao().delete(id)
                        nav.popBackStack()
                    }
                }) { Text("Remove", color = Color(0xFFFF5252)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }

    if (merging) {
        val others = persons.filter { it.id != id }
        AlertDialog(
            onDismissRequest = { merging = false },
            containerColor = Color(0xFF1B1B24),
            title = { Text("Merge \"${person.name}\" into…", color = Color.White) },
            text = {
                Column {
                    Text(
                        "All ${media.size} photos of this group will join the other person. Photos themselves are not touched.",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                    others.forEach { p ->
                        TextButton(onClick = {
                            merging = false
                            fixMode = false
                            scope.launch {
                                AppDb.db.faceDao().moveFaces(id, p.id)
                                AppDb.db.personDao().delete(id)
                                recountPerson(p.id)
                                nav.popBackStack()
                            }
                        }) { Text("${p.name} (${p.count})", color = Color.White) }
                    }
                    if (others.isEmpty()) {
                        Text("No other people yet.", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { merging = false }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }
}
