package com.ujjaw.lumagallery.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ujjaw.lumagallery.data.MediaLib
import com.ujjaw.lumagallery.ui.screens.AlbumDetailScreen
import com.ujjaw.lumagallery.ui.screens.AlbumsScreen
import com.ujjaw.lumagallery.ui.screens.EditorScreen
import com.ujjaw.lumagallery.ui.screens.FavoritesScreen
import com.ujjaw.lumagallery.ui.screens.HiddenScreen
import com.ujjaw.lumagallery.ui.screens.PersonScreen
import com.ujjaw.lumagallery.ui.screens.PeopleScreen
import com.ujjaw.lumagallery.ui.screens.PhotosScreen
import com.ujjaw.lumagallery.ui.screens.SearchScreen
import com.ujjaw.lumagallery.ui.screens.SettingsScreen
import com.ujjaw.lumagallery.ui.screens.TrashScreen
import com.ujjaw.lumagallery.ui.screens.ViewerScreen

private val MAIN_ROUTES = setOf("photos", "albums", "search", "people")

fun hasMediaPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= 33) {
        val img = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        val vid = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        val partial = Build.VERSION.SDK_INT >= 34 &&
            ContextCompat.checkSelfPermission(context, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == PackageManager.PERMISSION_GRANTED
        img || vid || partial
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun App() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasMediaPermission(context)) }
    if (granted) {
        MainNav()
    } else {
        PermissionScreen { granted = hasMediaPermission(context) }
    }
}

@Composable
private fun PermissionScreen(onResult: () -> Unit) {
    val context = LocalContext.current
    val perms = buildList {
        add(Manifest.permission.READ_MEDIA_IMAGES)
        add(Manifest.permission.READ_MEDIA_VIDEO)
        if (Build.VERSION.SDK_INT >= 34) add("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
        if (Build.VERSION.SDK_INT < 33) add(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }.toTypedArray()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onResult()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.PhotoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Text(
            "Access your photos",
            color = Color.White,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            "Luma Gallery needs permission to read your photos and videos. " +
                "Nothing is uploaded anywhere — everything stays on your phone.",
            color = Color.Gray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(
            onClick = { launcher.launch(perms) },
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text("Allow access")
        }
        TextButton(
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                )
            }
        ) {
            Text("Open settings", color = Color.Gray)
        }
    }
}

@Composable
private fun MainNav() {
    val context = LocalContext.current
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "photos"

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        MediaLib.refresh(context)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) MediaLib.refresh(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        NavHost(nav, startDestination = "photos", modifier = Modifier.fillMaxSize()) {
            composable("photos") { PhotosScreen(nav) }
            composable("albums") { AlbumsScreen(nav) }
            composable("search") { SearchScreen(nav) }
            composable("people") { PeopleScreen(nav) }
            composable("favorites") { FavoritesScreen(nav) }
            composable("trash") { TrashScreen(nav) }
            composable("hidden") { HiddenScreen(nav) }
            composable("settings") { SettingsScreen(nav) }
            composable("album/{bucketId}") { e ->
                AlbumDetailScreen(nav, e.arguments?.getString("bucketId")?.toLongOrNull() ?: 0L)
            }
            composable("person/{id}") { e ->
                PersonScreen(nav, e.arguments?.getString("id")?.toLongOrNull() ?: -1L)
            }
            composable("viewer/{index}") { e ->
                ViewerScreen(nav, e.arguments?.getString("index")?.toIntOrNull() ?: 0)
            }
            composable("editor/{id}") { e ->
                EditorScreen(nav, e.arguments?.getString("id")?.toLongOrNull() ?: -1L)
            }
        }

        if (route in MAIN_ROUTES) {
            NavigationBar(
                containerColor = Color(0xFF101014),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                NavigationBarItem(
                    selected = route == "photos",
                    onClick = { nav.navigate("photos") { launchSingleTop = true; popUpTo("photos") { inclusive = false } } },
                    icon = { Icon(if (route == "photos") Icons.Filled.PhotoLibrary else Icons.Outlined.PhotoLibrary, null) },
                    label = { Text("Photos") }
                )
                NavigationBarItem(
                    selected = route == "albums",
                    onClick = { nav.navigate("albums") { launchSingleTop = true; popUpTo("photos") { saveState = true } } },
                    icon = { Icon(if (route == "albums") Icons.Filled.Collections else Icons.Outlined.Collections, null) },
                    label = { Text("Albums") }
                )
                NavigationBarItem(
                    selected = route == "search",
                    onClick = { nav.navigate("search") { launchSingleTop = true; popUpTo("photos") { saveState = true } } },
                    icon = { Icon(if (route == "search") Icons.Filled.Search else Icons.Outlined.Search, null) },
                    label = { Text("Search") }
                )
                NavigationBarItem(
                    selected = route == "people",
                    onClick = { nav.navigate("people") { launchSingleTop = true; popUpTo("photos") { saveState = true } } },
                    icon = { Icon(if (route == "people") Icons.Filled.People else Icons.Outlined.People, null) },
                    label = { Text("People") }
                )
            }
        }
    }
}
