package com.ujjaw.lumagallery.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

/**
 * Reports pinch (two-finger) zoom factors; single-finger scrolls pass through
 * untouched so grid scrolling keeps working.
 */
fun Modifier.pinchToZoomSteps(onStep: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            if (event.changes.size > 1) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) {
                    onStep(zoom)
                    event.changes.forEach { c ->
                        if (c.positionChange() != Offset.Zero) c.consume()
                    }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
