package com.ujjaw.lumagallery.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.ujjaw.lumagallery.data.MediaLib
import com.ujjaw.lumagallery.data.db.AppDb
import com.ujjaw.lumagallery.ui.ViewerSession
import com.ujjaw.lumagallery.ui.components.MediaGrid
import kotlinx.coroutines.flow.map

@Composable
fun FavoritesScreen(nav: NavHostController) {
    val favSet by AppDb.db.favoriteDao().all()
        .map { list -> list.map { MediaLib.norm(it.uri) }.toSet() }
        .collectAsState(initial = emptySet())

    val items = MediaLib.items.filter { MediaLib.norm(it.uri) in favSet }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Text(
            "Favorites",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (items.isEmpty()) {
            EmptyView("No favorites yet", "Tap the heart on a photo to add it here.")
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
