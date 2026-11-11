package com.ujjaw.lumagallery.ui.screens

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ujjaw.lumagallery.data.MediaLib
import com.ujjaw.lumagallery.data.SettingsStore
import com.ujjaw.lumagallery.data.db.AppDb
import com.ujjaw.lumagallery.work.ContentIndexWorker
import kotlinx.coroutines.launch

/** Once unlocked with biometrics, stays unlocked for the current app session. */
object BiometricGate {
    var unlocked = false
}

@Composable
fun SettingsScreen(nav: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hidden by AppDb.db.hiddenDao().all().collectAsState(initial = emptyList())
    val hiddenItems by AppDb.db.hiddenItemDao().all().collectAsState(initial = emptyList())
    val persons by AppDb.db.personDao().all().collectAsState(initial = emptyList())
    val contentRows by AppDb.db.contentDao().all().collectAsState(initial = emptyList())

    val workList by remember {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow("content_index")
    }.collectAsState(initial = emptyList())
    val indexing = workList.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }

    fun gate(onPass: () -> Unit) {
        if (BiometricGate.unlocked) {
            onPass()
            return
        }
        val activity = context as? androidx.fragment.app.FragmentActivity
        if (activity == null) {
            onPass()
            return
        }
        val manager = BiometricManager.from(context)
        val supported = if (Build.VERSION.SDK_INT >= 30) {
            manager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            @Suppress("DEPRECATION")
            manager.canAuthenticate()
        }
        if (supported != BiometricManager.BIOMETRIC_SUCCESS) {
            // No biometrics/lock set — don't lock the user out of their own data
            BiometricGate.unlocked = true
            onPass()
            return
        }
        val info = if (Build.VERSION.SDK_INT >= 30) {
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock private area")
                .setSubtitle("Verify to view trash and hidden items")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
        } else {
            @Suppress("DEPRECATION")
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock private area")
                .setSubtitle("Verify to view trash and hidden items")
                .setDeviceCredentialAllowed(true)
                .build()
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    BiometricGate.unlocked = true
                    onPass()
                }
            }
        )
        prompt.authenticate(info)
    }

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
            Text("Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)
        ) {
            item { SectionHeader("Gallery") }
            item {
                ChipRow(
                    label = "Photo grid columns",
                    options = listOf("3" to 3, "4" to 4, "5" to 5),
                    selected = SettingsStore.gridColumns
                ) { SettingsStore.updateGridColumns(it) }
            }
            item {
                ChipRow(
                    label = "Slideshow speed",
                    options = listOf("1s" to 1000, "3s" to 3000, "5s" to 5000, "10s" to 10000),
                    selected = SettingsStore.slideshowMs.toInt()
                ) { SettingsStore.updateSlideshow(it.toLong()) }
            }

            item { SectionHeader("Privacy") }
            item {
                NavRow(
                    title = "Hidden folders & items",
                    subtitle = if (hidden.isEmpty() && hiddenItems.isEmpty()) "Nothing hidden"
                    else "${hidden.size} folders · ${hiddenItems.size} items — tap to manage"
                ) { gate { nav.navigate("hidden") } }
            }
            item {
                NavRow(
                    title = "Trash",
                    subtitle = if (MediaLib.trashed.isEmpty()) "Trash is empty" else "${MediaLib.trashed.size} items in trash"
                ) { gate { nav.navigate("trash") } }
            }

            item { SectionHeader("On-device data") }
            item {
                DataRow(
                    title = "Content index",
                    subtitle = if (indexing) "Indexing…" else "${contentRows.size} photos indexed",
                    action = if (indexing) null else "Rebuild",
                    onAction = {
                        scope.launch {
                            AppDb.db.contentDao().clear()
                            WorkManager.getInstance(context).enqueueUniqueWork(
                                "content_index",
                                ExistingWorkPolicy.REPLACE,
                                OneTimeWorkRequestBuilder<ContentIndexWorker>().build()
                            )
                        }
                    }
                )
                if (indexing) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF82B1FF))
                }
            }
            item {
                DataRow(
                    title = "People data",
                    subtitle = if (persons.isEmpty()) "No people found" else "${persons.size} people · faces grouped on-device",
                    action = if (persons.isEmpty()) null else "Clear",
                    onAction = {
                        scope.launch {
                            AppDb.db.faceDao().deleteAll()
                            AppDb.db.personDao().deleteAll()
                        }
                    }
                )
            }

            item { SectionHeader("About") }
            item {
                Text(
                    "Luma Gallery v1.0\nEverything runs on your phone — no cloud, no tracking, no internet needed.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        color = Color(0xFF82B1FF),
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun ChipRow(
    label: String,
    options: List<Pair<String, Int>>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(label, color = Color(0xFFBBBBBB), fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            options.forEach { (labelText, value) ->
                val sel = value == selected
                Text(
                    labelText,
                    color = if (sel) Color.Black else Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) Color(0xFF82B1FF) else Color(0xFF1B1B24))
                        .clickable { onSelect(value) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            Text(subtitle, color = Color.Gray, fontSize = 12.sp)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = Color.Gray)
    }
}

@Composable
private fun DataRow(title: String, subtitle: String, action: String?, onAction: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            Text(subtitle, color = Color.Gray, fontSize = 12.sp)
        }
        if (action != null) {
            TextButton(onClick = onAction) { Text(action, color = Color(0xFF82B1FF)) }
        }
        Spacer(Modifier.size(4.dp))
    }
}
