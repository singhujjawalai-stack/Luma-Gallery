package com.ujjaw.lumagallery.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest

class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var container by mutableStateOf(IntSize.Zero)
    var imageWidth by mutableIntStateOf(0)
    var imageHeight by mutableIntStateOf(0)
    val zoomed: Boolean get() = scale > 1.02f

    fun clampPan() {
        val maxX = (scale - 1f) * container.width / 2f
        val maxY = (scale - 1f) * container.height / 2f
        offset = Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }
}

@Composable
fun ZoomableImage(
    uri: String,
    modifier: Modifier = Modifier,
    onZoomChanged: (Boolean) -> Unit = {},
    onTap: () -> Unit = {},
    onState: (ZoomState) -> Unit = {}
) {
    val state = remember(uri) { ZoomState() }
    LaunchedEffect(state.scale) { onZoomChanged(state.zoomed) }
    LaunchedEffect(state) { onState(state) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(uri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            onState = { painterState ->
                if (painterState is AsyncImagePainter.State.Success) {
                    val d = painterState.result.drawable
                    state.imageWidth = d.intrinsicWidth.coerceAtLeast(0)
                    state.imageHeight = d.intrinsicHeight.coerceAtLeast(0)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged {
                    state.container = it
                    onState(state)
                }
                .graphicsLayer {
                    scaleX = state.scale
                    scaleY = state.scale
                    translationX = state.offset.x
                    translationY = state.offset.y
                }
                .pointerInput(uri) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        while (true) {
                            val event = awaitPointerEvent()
                            val interactive = event.changes.size > 1 || state.zoomed
                            if (interactive) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                if (zoom != 1f || pan != Offset.Zero) {
                                    val newScale = (state.scale * zoom).coerceIn(1f, 8f)
                                    if (newScale == 1f) {
                                        state.offset = Offset.Zero
                                    } else {
                                        state.offset += pan
                                    }
                                    state.scale = newScale
                                    state.clampPan()
                                    event.changes.forEach {
                                        if (it.positionChange() != Offset.Zero) it.consume()
                                    }
                                }
                            }
                            if (event.changes.none { it.pressed }) break
                        }
                    }
                }
                .pointerInput(uri) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = {
                            if (state.zoomed) {
                                state.scale = 1f
                                state.offset = Offset.Zero
                            } else {
                                state.scale = 2.5f
                                state.clampPan()
                            }
                        }
                    )
                }
        )
    }
}
