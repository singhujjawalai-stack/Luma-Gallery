package com.ujjaw.lumagallery.ui.components

import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * ExoPlayer-backed video view.
 *  - `active` pauses playback when the page scrolls out of the pager viewport
 *    (fixes audio bleeding from neighbouring videos).
 *  - `onControllerVisible` mirrors the player's own controller taps so the
 *    screen chrome (top/bottom bars) can follow: tap video → controls + chrome
 *    show together, tap again → both hide.
 */
@Composable
fun VideoPlayer(
    uri: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    onControllerVisible: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }
    LaunchedEffect(active) {
        if (active) exoPlayer.play() else exoPlayer.pause()
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                setControllerVisibilityListener(object : PlayerView.ControllerVisibilityListener {
                    override fun onVisibilityChanged(visibility: Int) {
                        onControllerVisible(visibility == View.VISIBLE)
                    }
                })
            }
        },
        update = { view ->
            if (view.player != exoPlayer) view.player = exoPlayer
        },
        modifier = modifier.fillMaxSize()
    )
}
