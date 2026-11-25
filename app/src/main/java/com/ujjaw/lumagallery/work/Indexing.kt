package com.ujjaw.lumagallery.work

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Live indexing state for the UI.
 * True only while a worker is actually processing new items — not merely while a
 * WorkManager request is queued (a no-op run with nothing new finishes in milliseconds
 * and should never flash a spinner).
 */
object Indexing {
    var contentRunning by mutableStateOf(false)
    var faceRunning by mutableStateOf(false)
}
